package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * プレイヤーのリスポーン時・切断時の状態クリーンアップハンドラ。
 * <p>
 * 保護機能（無敵化・即死拒絶・ダメージキャンセル等）は持たず、
 * 他 MOD（The Last Sword, Epic Core API 等）の残存拘束（位置固定・移動不能バグ）のみを安全に解除する。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class PlayerRespawnHandler {

    private PlayerRespawnHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        // ラストソードを手放した場合の位置固定を安全に解除
        if (!EcaLastSwordHelper.hasDefenseSword(player)) {
            EcaLastSwordHelper.clearLocationLock(player.getUUID());
        }
    }

    /** 死亡後のクローン作成時（リスポーン時）、古い個体の位置固定を解除し、拘束をクリアする */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        EcaLastSwordHelper.cleanupPlayerRestraints(event.getOriginal());
        EcaLastSwordHelper.cleanupPlayerRestraints(player);
        EcaLastSwordHelper.clearLocationLock(event.getOriginal().getUUID());
    }

    /** リスポーン完了イベントでも確実に拘束を解除する */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        EcaLastSwordHelper.cleanupPlayerRestraints(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EcaLastSwordHelper.clearLocationLock(event.getEntity().getUUID());
    }
}
