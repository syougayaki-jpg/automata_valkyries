package com.automatavalkyries.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 上位合金の貯蔵ブロック（9 個 ↔ 1 ブロック）。表面からその合金の粒子を出す（クライアントの見た目だけ）。
 * 明るさは Properties.lightLevel で合金ごとに決める。
 */
public class EffectStorageBlock extends Block {

    private final List<ParticleOptions> particles;
    /** 1 Tick あたりに粒子を出す確率（0〜1） */
    private final float chance;

    public EffectStorageBlock(Properties properties, List<ParticleOptions> particles, float chance) {
        super(properties);
        this.particles = particles;
        this.chance = chance;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= chance) return;
        ParticleOptions particle = particles.get(random.nextInt(particles.size()));
        // ブロックの少し外側のどこかから出す
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        level.addParticle(particle, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.02D, (random.nextDouble() - 0.5) * 0.02);
    }
}
