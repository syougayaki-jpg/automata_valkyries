package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * オーバーロード化した T1〜T3 の兵装の「技の変化」（2026-10-05 ユーザー指定「オーバーロード化でティア１から３の攻撃が派手に変化、ブラックロータスが半径 15 ブロックの
 * 花吹雪に拡大するなど、とにかく目立つように」。兵装ごとに専用の変化・当たりも大きく・道具とモジュールも対象）。
 * <p>各兵装は {@link #active} で変化するかを見て、自分の技を大きく・派手にする。ここは共通の判定と、変化した技を放つときの紅と金の演出だけ。
 * T4 兵装は別（{@link Overload#holdingT4}、範囲 ×2.5 と即死）</p>
 */
public final class OverloadArts {

    private OverloadArts() {}

    /** その者がオーバーロード化した T1〜T3 の兵装・道具・モジュールを利き手に持っているか（兵姫も） */
    public static boolean active(@Nullable LivingEntity user) {
        return user != null && Overload.holding(user) && !Overload.isT4(user.getMainHandItem().getItem());
    }

    /** 変化しているなら big、していなければ normal */
    public static double pick(@Nullable LivingEntity user, double normal, double big) {
        return active(user) ? big : normal;
    }

    public static int pick(@Nullable LivingEntity user, int normal, int big) {
        return active(user) ? big : normal;
    }

    /** 変化した技を放つ瞬間の共通の演出: 足元の紅と金の二重の衝撃波・立ちのぼる光・紅い火花（技の色の演出に重ねる） */
    public static void flourish(ServerLevel level, LivingEntity user, float radius) {
        Vec3 feet = user.position().add(0, 0.05, 0);
        Fx.shockwave(level, feet, Overload.CRIMSON, radius, 16);
        Fx.shockwave(level, feet, Overload.GOLD, radius * 0.6F, 12);
        Fx.mandala(level, feet.add(0, 0.02, 0), Overload.GOLD, Math.min(radius * 0.5F, 8.0F), 30, false, true);
        Vec3 c = user.position().add(0, user.getBbHeight() * 0.5, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.CRIMSON, 0.4F, 14), c, 24, 0.6);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), Overload.GOLD, 0.3F, 20), c, 16, 0.6, 1.0, 0.6, 0.15);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** 変化した技が当たった所の小さな紅と金の閃き（当たりが多い技は数を絞って使う） */
    public static void impact(ServerLevel level, Vec3 at) {
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Overload.GOLD, 0.3F, 10), at, 6, 0.35);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), Overload.CRIMSON, 0.35F, 10), at, 3, 0.2, 0.02);
    }
}
