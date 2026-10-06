package com.automatavalkyries.block;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.item.ProtoCoreItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 損傷したインターフェース（追加拡張仕様書 §8.2。2026-10-07 ユーザー指定「目覚めの端末」）: 墜落ポッドの端末。
 * 満充電のプロトコアを持って右クリックすると、近く（6 ブロック）の深い休眠状態の兵姫が目覚め、使った人が持ち主になる。
 * プロトコアの電力は兵姫へ移る（プロトコアは使い切る）。火花が散る
 */
public class DamagedInterfaceBlock extends HorizontalDirectionalBlock {

    public static final double REACH = 6.0;

    public DamagedInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean easy = com.automatavalkyries.config.ValkyrieConfig.easyPodWake(); // 序盤の簡易化（設定。既定はオフ）
        if (!(held.getItem() instanceof ProtoCoreItem) && !easy) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.pod.need_core").withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.CONSUME;
        }
        boolean core = held.getItem() instanceof ProtoCoreItem;
        int charge = core ? ItemEnergy.get(held) : 0;
        if (easy) charge = Math.max(charge, com.automatavalkyries.config.ValkyrieConfig.easyPodEnergy());
        else if (charge < ProtoCoreItem.CAPACITY) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.pod.no_charge", charge, ProtoCoreItem.CAPACITY).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        ValkyrieEntity sleeper = null;
        for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, new AABB(pos).inflate(REACH), ValkyrieEntity::isPodSleeper)) {
            if (sleeper == null || v.distanceToSqr(pos.getCenter()) < sleeper.distanceToSqr(pos.getCenter())) sleeper = v;
        }
        if (sleeper == null) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.pod.no_valkyrie").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (!sleeper.wakeFromPod(player, charge)) return InteractionResult.CONSUME; // 登録の上限など（兵姫の側でメッセージ）
        if (core && !player.getAbilities().instabuild) held.shrink(1);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.pod.woke", sleeper.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    /** 火花（クライアント） */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() > 0.3F) return;
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.52, y = pos.getY() + 0.3 + random.nextDouble() * 0.5, z = pos.getZ() + 0.5 + f.getStepZ() * 0.52;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, f.getStepX() * 0.05, 0.02, f.getStepZ() * 0.05);
    }
}
