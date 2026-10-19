package com.automatavalkyries.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 浮遊要塞の奥の「眠れる時の門」（2026-10-19 ユーザー指定。質問で: 眠ったポータルを置く）。総司令機タイムルーラーへの門だが、
 * タイムルーラーはまだ居ないので、今は触れると「まだ時が満ちていない」と返すだけ。タイムルーラーを作る時に、ここから向こうへ送るようにする。壊せない
 */
public class RulerPortalBlock extends Block {

    public RulerPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.displayClientMessage(Component.translatable("message.automata_valkyries.ruler_portal.dormant").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.5F);
        return InteractionResult.CONSUME;
    }

    /** 眠っている見た目: ゆっくり昇る時計の粒 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.PORTAL, pos.getX() + rand.nextDouble(), pos.getY() + rand.nextDouble(), pos.getZ() + rand.nextDouble(), 0, 0.02, 0);
        if (rand.nextInt(6) == 0) level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rand.nextGaussian() * 0.3, 0.2, rand.nextGaussian() * 0.3);
    }
}
