package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackLogFile;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ワールド全体の保護対象を監視するマネージャー。
 * すべてサーバースレッド上（Forge のイベント）で動く。{@code MinecraftForge.EVENT_BUS} に登録すること。
 */
public class EprotectManager {

    private static final EprotectManager INSTANCE = new EprotectManager();
    /** 監視周期（Tick: 毎Tick監視） */
    private static final int SCAN_INTERVAL = 1;
    /** 攻撃の記録の増分をまとめて出す間隔（Tick） */
    private static final int ATTACK_LOG_FLUSH_INTERVAL = 100;

    private final Map<UUID, Eprotect> protectedEntities = new HashMap<>();
    private int tickCounter = 0;

    private EprotectManager() {}

    public static EprotectManager getInstance() {
        return INSTANCE;
    }

    public void register(Eprotect protect) {
        UUID uuid = protect.getOwner().getUUID();
        if (protectedEntities.get(uuid) != protect) {
            protectedEntities.put(uuid, protect);
        }
    }

    public void unregister(Eprotect protect) {
        protectedEntities.remove(protect.getOwner().getUUID(), protect);
    }

    public boolean isRegistered(Eprotect protect) {
        return protectedEntities.get(protect.getOwner().getUUID()) == protect;
    }

    /**
     * 本体の Tick が止まっていても（消去フラグが立って Tick 対象から外れた、ワールドから外された等）
     * 周期的に修復・復元する。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ++tickCounter;
        if (tickCounter % ATTACK_LOG_FLUSH_INTERVAL == 0) AttackLog.flush();
        if (tickCounter % REGISTRY_SCAN_INTERVAL == 0 && event.getServer().isRunning()) {
            restoreFromRegistries(event.getServer());
        }
        if (tickCounter % SCAN_INTERVAL != 0 || protectedEntities.isEmpty()) return;

        // repair() が addFreshEntity 経由で register() を呼んでも壊れないようコピーを回す
        for (Eprotect protect : protectedEntities.values().toArray(new Eprotect[0])) {
            if (!protect.isGuardingRemoval()) {
                unregister(protect);
                continue;
            }
            protect.repair();
        }
    }

    // ---- ワールドデータの登録簿からの復元 ----------------------------------------

    /** 登録簿を見回る間隔（Tick） */
    private static final int REGISTRY_SCAN_INTERVAL = 20;
    /** 1 体を立て続けに作り直す上限（この Tick 数の間に） */
    private static final int RESTORE_WINDOW = 1200;
    private static final int MAX_RESTORES_IN_WINDOW = 10;

    /**
     * 最後の砦: エンティティのオブジェクトごと失われ（どの Eprotect も見張っていない）、ワールドのどこにも居ない個体を、
     * ワールドデータの登録簿の写しから作り直す。
     * <ul>
     *   <li>まだ見張っている Eprotect がある個体は、そちらの入り直し・差し替えに任せる</li>
     *   <li>全ディメンションのどこかに同じ UUID があれば作らない（複製防止。次元移動・読み込み途中を含む）</li>
     *   <li>写しの座標のチャンクのエンティティ読み込みが済むまで待つ（ディスクから普通に読み込まれる途中なら作らない）</li>
     * </ul>
     */
    private void restoreFromRegistries(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            EprotectRegistry registry = EprotectRegistry.get(level);
            for (EprotectRegistry.Entry entry : registry.entries().toArray(new EprotectRegistry.Entry[0])) {
                if (entry.state() != EprotectRegistry.State.ACTIVE) continue;
                Eprotect watcher = protectedEntities.get(entry.uuid());
                if (watcher != null && watcher.isGuardingRemoval()) continue;
                if (existsAnywhere(server, entry.uuid())) continue;
                long chunk = ChunkPos.asLong(BlockPos.containing(entry.safePos()));
                if (!level.areEntitiesLoaded(chunk)) continue;
                if (!allowRestore(level, entry)) continue;

                Entity restored = EprotectRegistry.recreate(level, entry);
                if (restored != null && level.addFreshEntity(restored)) {
                    AutomataValkyries.LOGGER.warn("[eprotect] Restored {} from the world registry", restored);
                } else {
                    AutomataValkyries.LOGGER.error("[eprotect] Could not restore {} from the world registry", entry.uuid());
                }
            }
        }
    }

    private static boolean existsAnywhere(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) != null) return true;
            if (((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager().isLoaded(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** 作り直しの連発を抑える。何かが作り直すたびに消し続けているなら、それ以上は追わない */
    private static boolean allowRestore(ServerLevel level, EprotectRegistry.Entry entry) {
        long now = level.getGameTime();
        if (now - entry.restoreWindowStart > RESTORE_WINDOW) {
            entry.restoreWindowStart = now;
            entry.restoresInWindow = 0;
        }
        if (entry.restoresInWindow >= MAX_RESTORES_IN_WINDOW) return false;
        entry.restoresInWindow++;
        if (entry.restoresInWindow == MAX_RESTORES_IN_WINDOW) {
            AutomataValkyries.LOGGER.error("[eprotect] {} keeps disappearing; giving up restoring it for now", entry.uuid());
        }
        return true;
    }

    /**
     * HP に反映される直前の最終ダメージを制限する。
     * 他 MOD が LivingHurtEvent 等でダメージを増幅していても、最後にここで抑える。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect != null) {
                event.setAmount(protect.capFinalDamage(event.getAmount(), event.getSource()));
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        EprotectWatchdogAgent.getInstance().start(event.getServer());
        com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().start(event.getServer());
        ObserverLink.logStatus();
        if (EprotectConfig.get(EprotectConfig.ATTACK_LOG_WRITE_FILE)) AttackLogFile.open();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        EprotectWatchdogAgent.getInstance().stop();
        com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().stop();
        ShutdownDiagnostics.onStopping();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        ShutdownDiagnostics.onStopped();
        protectedEntities.clear();
        tickCounter = 0;
        AttackLog.flush();
        AttackLog.clear();
        ModBlacklist.resetAutoCounters();
        AttackLogFile.close();
    }

    /**
     * ワールド読み込み時に前回セッションのチャンクロックを破棄する。
     * 保護対象はロードされた時点で自分のいるチャンクを改めてロックする。
     */
    public static void registerChunkLoadingCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(AutomataValkyries.MODID, (level, ticketHelper) -> {
            for (UUID owner : ticketHelper.getEntityTickets().keySet().toArray(new UUID[0])) {
                ticketHelper.removeAllTickets(owner);
            }
        });
    }
}
