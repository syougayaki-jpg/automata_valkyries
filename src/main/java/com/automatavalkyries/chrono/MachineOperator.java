package com.automatavalkyries.chrono;

import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 兵姫のオペレーター（2026-10-29 ユーザー指定「戦術兵姫を機械の強化のピースにして、ティアに応じて強化限界を決めて」。
 * 質問で: そばに配置する・速さと電力の節約・個性で変わる・強さは同調率。自動化はそのまま（兵姫がいなくても機械は動く）。数値は【仮】）。
 * <ul>
 *   <li>待機にした兵姫が、クロノリンク・マシンから {@value #REACH} ブロック以内にいると、いちばん近いマシンのオペレーターになる（1 台に 1 人、いちばん効く兵姫）</li>
 *   <li>強さは同調率（0〜100%）。上限はマシンのティア: 速さ +{@link #SPEED_CAP}・電力の節約 {@link #ENERGY_CAP}（T1〜T4）</li>
 *   <li>個性（体の型）で伸びる方が変わる: V 素直は両方そこそこ、S 元気は速さ、H 寡黙・C 知的は電力の節約、Z 零号機は両方。
 *       型の重みが大きいほど、低い同調率でも上限に届く</li>
 *   <li>オペレーターは機械の方を向き、動いている間は時々手を動かす</li>
 * </ul>
 */
public final class MachineOperator {

    public static final double REACH = 2.5;
    /** ティアごとの上限（添字 = ティア。速さの上乗せ・電力の節約の割合） */
    public static final float[] SPEED_CAP = {0F, 0.25F, 0.5F, 1.0F, 2.0F};
    public static final float[] ENERGY_CAP = {0F, 0.10F, 0.20F, 0.30F, 0.40F};

    /** 強化（速さの倍率 ≥ 1、電力の倍率 ≤ 1） */
    public record Boost(@Nullable ValkyrieEntity valkyrie, float speed, float energy) {
        public static final Boost NONE = new Boost(null, 1.0F, 1.0F);

        float score() {
            return (speed - 1.0F) + (1.0F - energy) * 2.0F;
        }
    }

    private MachineOperator() {}

    /** 体の型ごとの重み（速さ・電力の節約） */
    static float[] weights(ValkyrieBody body) {
        return switch (body) {
            case S -> new float[]{1.4F, 0.6F};
            case H -> new float[]{0.8F, 1.3F};
            case C -> new float[]{0.7F, 1.5F};
            case Z -> new float[]{1.3F, 1.3F};
            default -> new float[]{1.0F, 1.0F};
        };
    }

    /** この兵姫がこのティアのマシンを動かしたときの強化 */
    public static Boost boost(ValkyrieEntity v, ChronoTier tier) {
        float sync = Math.max(0.0F, Math.min(100.0F, v.getSyncRate())) / 100.0F;
        float[] w = weights(v.getBody());
        float speed = 1.0F + SPEED_CAP[tier.level] * Math.min(1.0F, sync * w[0]);
        float energy = 1.0F - ENERGY_CAP[tier.level] * Math.min(1.0F, sync * w[1]);
        return new Boost(v, speed, energy);
    }

    /** オペレーターになれる状態か（待機・起きている・戦っていない） */
    static boolean ready(ValkyrieEntity v) {
        return v.isAlive() && !v.isShutdown() && !v.isAsleep() && v.getMode() == ValkyrieEntity.Mode.WAIT && v.getTarget() == null && !v.isPassenger();
    }

    /** マシンのオペレーターを探す（いちばん効く兵姫。その兵姫にとっていちばん近いマシンがこれのときだけ） */
    static Boost find(ChronoMachineBlockEntity machine) {
        if (!(machine.getLevel() instanceof ServerLevel level)) return Boost.NONE;
        BlockPos pos = machine.getBlockPos();
        Boost best = Boost.NONE;
        for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, new AABB(pos).inflate(REACH), MachineOperator::ready)) {
            if (!pos.equals(nearestMachine(level, v))) continue;
            Boost b = boost(v, machine.tier());
            if (b.score() > best.score()) best = b;
        }
        return best;
    }

    @Nullable
    private static BlockPos nearestMachine(ServerLevel level, ValkyrieEntity v) {
        BlockPos me = v.blockPosition(), best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-3, -2, -3), me.offset(3, 2, 3))) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof ChronoMachineBlockEntity)) continue;
            double d = Vec3.atCenterOf(p).distanceToSqr(v.position());
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        return best;
    }

    /** オペレーターの仕草（20 Tick ごと）: 機械の方を向き、動いている間は時々手を動かして光の粒 */
    static void animate(ChronoMachineBlockEntity machine, Boost b, boolean active, boolean started) {
        ValkyrieEntity v = b.valkyrie();
        if (v == null || !(machine.getLevel() instanceof ServerLevel level)) return;
        Vec3 c = Vec3.atCenterOf(machine.getBlockPos());
        v.getLookControl().setLookAt(c.x, c.y, c.z);
        if (started) v.talk("operate_start");
        if (active && v.getRandom().nextInt(3) == 0) {
            v.swing(InteractionHand.MAIN_HAND, true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.6, c.z, 4, 0.3, 0.2, 0.3, 0.02);
        }
    }
}
