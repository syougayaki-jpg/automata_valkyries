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
import net.minecraft.world.entity.LivingEntity;
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
    /** バイトコード整合性の定期点検間隔（Tick: 60秒 = 1200 ticks） */
    private static final int BYTECODE_INTEGRITY_CHECK_INTERVAL = 1200;

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

    /** 指定エンティティが生存中の保護対象の半径 radius ブロック以内にいるか */
    public boolean isNearActiveProtected(Entity entity, double radius) {
        if (entity == null || protectedEntities.isEmpty()) return false;
        double rSq = radius * radius;
        for (Eprotect protect : protectedEntities.values()) {
            if (protect == null || !protect.isProtectionActive()) continue;
            LivingEntity owner = protect.getOwner();
            if (owner == null || !owner.isAlive() || owner.level() != entity.level()) continue;
            if (owner.distanceToSqr(entity) <= rSq) return true;
        }
        return false;
    }

    /** 指定エンティティが保護対象の乗り物・同乗者（ツリー全体）であるか */
    public boolean isVehicleOrPassengerOfProtected(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof IEprotected) return true;
        if (!EprotectConfig.isProtectVehiclesAndPassengersEnabled()) {
            return false;
        }
        Entity vehicle = entity.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof IEprotected) return true;
            vehicle = vehicle.getVehicle();
        }
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof IEprotected) return true;
            if (isVehicleOrPassengerOfProtected(passenger)) return true;
        }
        return false;
    }

    /** 登録されている保護対象のいずれかで強化防護が稼働中か */
    public boolean isAnyProtectedReinforced() {
        if (com.automatavalkyries.protect.plus.EprotectPlusLink.isReinforcedActive()) return true;
        for (Eprotect protect : protectedEntities.values()) {
            if (protect != null && protect.isReinforcedActive()) return true;
        }
        return false;
    }

    /** 強化モード（結界発動中）において、オーラ範囲内の周囲エンティティとして防護対象であるか */
    public boolean isProtectedByAura(Entity entity) {
        if (!EprotectConfig.isReinforcedAuraEnabled()) {
            return false;
        }
        double radius = EprotectConfig.get(EprotectConfig.REINFORCED_AURA_RADIUS);
        return isAnyProtectedReinforced() && isNearActiveProtected(entity, radius);
    }

    /**
     * エンティティが Eprotect+ の保護範囲（直接対象、搭乗関係、結界オーラ範囲）に含まれるか。
     * 設定（EprotectConfig.isExpandedScopeEnabled() 等）が無効の場合は保護対象単体のみ防護。
     */
    public boolean isUnderProtectionScope(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof IEprotected provider) {
            Eprotect p = provider.getEprotect();
            return p != null && p.isProtectionActive();
        }
        if (!EprotectConfig.isExpandedScopeEnabled()
                && !EprotectConfig.isProtectVehiclesAndPassengersEnabled()
                && !EprotectConfig.isReinforcedAuraEnabled()) {
            return false;
        }
        return isVehicleOrPassengerOfProtected(entity) || isProtectedByAura(entity);
    }

    /**
     * 本体の Tick が止まっていても（消去フラグが立って Tick 対象から外れた、ワールドから外された等）
     * 周期的に修復・復元する。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ++tickCounter;
        if (tickCounter % ATTACK_LOG_FLUSH_INTERVAL == 0) {
            AttackLog.flush();
        }
        if (tickCounter % BYTECODE_INTEGRITY_CHECK_INTERVAL == 0) {
            com.automatavalkyries.protect.plus.EprotectPlusLink.enforceBytecodeIntegrity();
        }
        EventBusGuardian.ensureEventBusIntegrity();
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
        long now = level.getServer().getTickCount(); // 時間停止でゲーム時刻が止まっても窓が明けるよう、サーバーの Tick 数で測る
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
                float allowed = protect.capFinalDamage(event.getAmount(), event.getSource());
                event.setAmount(allowed);
                protect.notePendingDamage(allowed);
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        GameTestCleanup.resetAtStart(event.getServer());
        EprotectWatchdogAgent.getInstance().start(event.getServer());
        com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().start(event.getServer());
        ObserverLink.logStatus();
        // 他 MOD のエージェントは起動の途中で変換器を足すので、ワールドの開始時にもう一度いちばん外側へ並べ直す
        LifeGuardLink.ensureLast();
        if (LifeGuardLink.isInstalled()) com.automatavalkyries.AutomataValkyries.LOGGER.info("[eprotect] {}", LifeGuardLink.status());
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
