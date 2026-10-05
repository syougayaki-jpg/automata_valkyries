package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.network.DualWieldPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.weapon.DualWield;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 二刀流のクリックの置き換え（{@link DualWield}、2026-10-06 ユーザー指定）。両手に武器を持っている間だけ:
 * シフト＋左クリック = 右手の技、右クリック = 左手の攻撃（攻撃が無い物は技）、シフト＋右クリック = 左手の技。
 * 左クリック（シフト無し）はいつもどおり右手で攻撃・採掘。ブロックを狙った右クリック（チェストを開ける等）はいつもどおり
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class DualWieldClient {

    /** 左手の攻撃の間隔（Tick、左手の物の攻撃速度から） */
    private static long nextOffAttack;

    private DualWieldClient() {}

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !DualWield.isDual(player)) return;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        boolean shift = player.isShiftKeyDown();
        if (event.isAttack()) {
            if (shift && DualWield.hasSkill(main)) {
                cancel(event);
                ModNetwork.CHANNEL.sendToServer(new DualWieldPacket(DualWieldPacket.MAIN_SKILL, -1));
                player.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        HitResult hit = mc.hitResult;
        if (!shift && hit != null && hit.getType() == HitResult.Type.BLOCK) return; // ブロックへの右クリックはいつもどおり
        if (shift ? DualWield.hasSkill(off) : (!DualWield.hasAttack(off) && DualWield.hasSkill(off))) {
            cancel(event);
            ModNetwork.CHANNEL.sendToServer(new DualWieldPacket(DualWieldPacket.OFF_SKILL, -1));
            player.swing(InteractionHand.OFF_HAND);
        } else if (!shift && DualWield.hasAttack(off)) {
            cancel(event);
            long now = mc.level.getGameTime();
            if (now < nextOffAttack) return;
            double speed = off.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                    .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).stream().mapToDouble(m -> m.getAmount()).sum() + 4.0;
            nextOffAttack = now + Math.max(2, Math.round(20.0 / Math.max(0.5, speed)));
            int target = hit instanceof EntityHitResult eh ? eh.getEntity().getId() : -1;
            ModNetwork.CHANNEL.sendToServer(new DualWieldPacket(DualWieldPacket.OFF_ATTACK, target));
            player.swing(InteractionHand.OFF_HAND);
        }
    }

    private static void cancel(InputEvent.InteractionKeyMappingTriggered event) {
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
