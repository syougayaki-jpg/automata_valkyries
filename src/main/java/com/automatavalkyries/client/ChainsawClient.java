package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.MiningToolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * チェーンソーで切っている最中の演出（クライアントだけ、自分の画面だけ）: 削っている面に回る光の刃を出し続け、
 * 刃から火花を散らす。ブロックが壊れた瞬間と相手を切った瞬間の刃はサーバーから全員に出す（{@link MiningToolItem}）。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ChainsawClient {

    private ChainsawClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused() || !mc.gameMode.isDestroying()) return;
        if (!(player.getMainHandItem().getItem() instanceof MiningToolItem tool) || tool.kind() != MiningToolItem.Kind.CHAINSAW) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 at = hit.getLocation().add(normal.scale(0.06));
        int tier = tool.tier();
        int k = player.tickCount;
        if (k % 2 == 0) {
            mc.level.addParticle(new FxOptions(ModParticles.SAW.get(), MiningToolItem.colorOf(tier, k / 2), 0.45F + tier * 0.06F, 5, 0F), at.x, at.y, at.z, 0, 0, 0);
        }
        // 刃から接線の向きへ火花を飛ばす
        var r = player.getRandom();
        for (int i = 0; i < 2; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.12).add(normal.scale(0.12));
            mc.level.addParticle(new FxOptions(ModParticles.SPARK.get(), MiningToolItem.colorOf(tier, k + i), 0.12F, 6, 0F), at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }
}
