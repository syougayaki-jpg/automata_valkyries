package com.automatavalkyries.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Omni-Mobs の {@code LevelUtil.explode} の置き換え（{@code mixin/compat/OmniMobsStabilityMixins} から呼ぶ。2026-10-05）。
 * 元は光線の向きを決めるためだけに「強さ × 100」体の Husk を作り、当たったブロックごとに {@code setblock ~ ~ ~ air} のコマンドを実行していた。
 * 同じ結果（同じ乱数の向き・長さ 100 の光線・壊すかの判定 {@code random * 200 > 硬さ}・岩盤は残す）を、Husk を作らず直接ブロックを消して出す。
 * Mixin のパッケージの外に置く（Mixin のパッケージのクラスは実行中に読み込めない）
 */
public final class OmniMobsExplosion {

    /** 元の Husk の目の高さ（{@code Zombie#getStandingEyeHeight}、子供でないとき） */
    private static final double PROBE_EYE_HEIGHT = 1.74;

    private OmniMobsExplosion() {}

    public static void explode(double x, double y, double z, float power, ServerLevel level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 eye = new Vec3(x, y + PROBE_EYE_HEIGHT, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < power * 100.0F; i++) {
            float yaw = (float) random.nextDouble(-360.01, 360.1);
            float pitch = (float) random.nextDouble(-360.01, 360.1);
            Vec3 end = eye.add(view(pitch, yaw).scale(100.0));
            pos.set(level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)).getBlockPos());
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.BEDROCK) && Math.random() * 200.0 > state.getDestroySpeed(level, pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** {@code Entity#calculateViewVector} と同じ式 */
    private static Vec3 view(float pitch, float yaw) {
        float f = pitch * Mth.DEG_TO_RAD;
        float f1 = -yaw * Mth.DEG_TO_RAD;
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
    }
}
