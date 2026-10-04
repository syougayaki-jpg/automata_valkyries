package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Overload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * オーバーロードの兵装を持っている者のまわりの粒子（クライアントだけ。2026-10-04 ユーザー指定）: 持つ手から紅と金の火花が散り、
 * ときどき体のまわりを紅い放電が走り、足元から熱の光が立ちのぼる。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class OverloadClient {

    private OverloadClient() {}

    private static int autoTicks;

    /**
     * オート連打（2026-10-04 ユーザー指定「クリック長押しでオート連打」）: オーバーロードの兵装を持って攻撃ボタンを押し続けている間、
     * 攻撃の溜め（攻撃速度）が満ちるたびに（最短 2 Tick ごと）自動で振る。照準が敵ならその敵を攻撃、何も無ければ空振り（T4 の通常攻撃が出る）。
     * ブロックを狙っているときは、いつもどおり掘る
     */
    private static void autoAttack(Minecraft mc) {
        var player = mc.player;
        if (mc.screen != null || mc.gameMode == null || !mc.options.keyAttack.isDown() || !Overload.holding(player) || player.isUsingItem()) {
            autoTicks = 0;
            return;
        }
        if (++autoTicks < 2 || player.getAttackStrengthScale(0.5F) < 1.0F) return;
        var hit = mc.hitResult;
        if (hit instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            mc.gameMode.attack(player, entityHit.getEntity());
        } else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            player.resetAttackStrengthTicker();
            net.minecraftforge.common.ForgeHooks.onEmptyLeftClick(player);
        } else {
            return; // ブロック: 掘るのに任せる
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        autoTicks = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        autoAttack(mc);
        RandomSource r = mc.level.random;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity e) || e.isInvisible() || e.distanceToSqr(mc.player) > 48 * 48 || !Overload.holding(e)) continue;
            float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            double side = e.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            Vec3 hand = e.position().add(0, e.getBbHeight() * 0.55, 0).add(right.scale(0.38 * side)).add(fwd.scale(0.35));
            // 手から散る火花
            if (r.nextInt(2) == 0) {
                Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() + 0.5, r.nextGaussian()).scale(0.04);
                mc.level.addParticle(new FxOptions(ModParticles.SPARK.get(), r.nextBoolean() ? Overload.CRIMSON : Overload.GOLD, 0.1F, 8, 0F), hand.x, hand.y, hand.z, v.x, v.y, v.z);
            }
            // 足元から立ちのぼる熱の光
            if (e.tickCount % 3 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 p = e.position().add(Math.cos(a) * 0.6, 0.1, Math.sin(a) * 0.6);
                mc.level.addParticle(new FxOptions(ModParticles.GLOW.get(), Overload.CRIMSON, 0.12F, 22, 0F), p.x, p.y, p.z, 0, 0.04, 0);
            }
            // 体のまわりを走る紅い放電
            if (e.tickCount % 9 == 0) {
                double a0 = r.nextDouble() * Math.PI * 2, y0 = r.nextDouble() * e.getBbHeight();
                Vec3 prev = e.position().add(Math.cos(a0) * 0.55, y0, Math.sin(a0) * 0.55);
                for (int k = 1; k <= 5; k++) {
                    double a = a0 + k * 0.35;
                    Vec3 p = e.position().add(Math.cos(a) * 0.55, Mth.clamp(y0 + (r.nextDouble() - 0.5) * 0.4, 0, e.getBbHeight()), Math.sin(a) * 0.55);
                    for (int s = 0; s < 3; s++) {
                        Vec3 q = prev.lerp(p, s / 3.0);
                        mc.level.addParticle(new FxOptions(ModParticles.GLOW.get(), s == 0 ? Overload.HOT : Overload.CRIMSON, 0.05F, 4, 0F), q.x, q.y, q.z, 0, 0, 0);
                    }
                    prev = p;
                }
            }
        }
    }
}
