package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
        if (++tickCounter % SCAN_INTERVAL != 0) return;
        if (protectedEntities.isEmpty()) return;

        // repair() が addFreshEntity 経由で register() を呼んでも壊れないようコピーを回す
        for (Eprotect protect : protectedEntities.values().toArray(new Eprotect[0])) {
            if (!protect.isGuardingRemoval()) {
                unregister(protect);
                continue;
            }
            protect.repair();
        }
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
                event.setAmount(protect.capFinalDamage(event.getAmount()));
            }
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        protectedEntities.clear();
        tickCounter = 0;
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
