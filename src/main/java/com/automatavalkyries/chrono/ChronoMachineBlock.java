package com.automatavalkyries.chrono;

import com.automatavalkyries.item.ChronoTunerItem;
import com.automatavalkyries.item.UpgradeKitItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * クロノリンク・マシンのブロック。ティアはブロックの状態 tier=1..4（外装の見た目もティアごと）。
 * 次のティアのアップデートキットを持って右クリックすると、中身・リンク・電力を保ったまま昇格する（設計書 §3.1）。
 */
public class ChronoMachineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 1, 4);

    private final ChronoMachineType type;
    private final BiFunction<BlockPos, BlockState, ? extends ChronoMachineBlockEntity> factory;

    public ChronoMachineBlock(Properties properties, ChronoMachineType type,
                              BiFunction<BlockPos, BlockState, ? extends ChronoMachineBlockEntity> factory) {
        super(properties);
        this.type = type;
        this.factory = factory;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(LIT, false).setValue(TIER, 1));
    }

    public ChronoMachineType type() { return type; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return factory.apply(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof ChronoMachineBlockEntity machine) ChronoMachineBlockEntity.serverTick(lvl, pos, st, machine);
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ChronoTunerItem) return InteractionResult.PASS; // チューナーが登録する
        if (held.getItem() instanceof UpgradeKitItem frame) {
            int current = state.getValue(TIER);
            if (frame.tier().level != current + 1) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.automata_valkyries.chrono.upgrade_wrong",
                        Component.translatable("tier.automata_valkyries." + ChronoTier.of(current + 1 > 4 ? 4 : current + 1).id)), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (frame.tier() == ChronoTier.NEVERLAST && !T4Unlock.unlocked(player)) {
                if (!level.isClientSide) player.displayClientMessage(T4Unlock.lockedMessage(), true);   // T4 はタイム・ルーラーを倒してから（2026-10-31）
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(TIER, current + 1), 3);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.8F + current * 0.2F);
                ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.08);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.chrono.upgraded",
                        Component.translatable("tier.automata_valkyries." + frame.tier().id)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof ChronoMachineBlockEntity machine) {
            NetworkHooks.openScreen(serverPlayer, machine, machine::writeMenuData);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChronoMachineBlockEntity machine) {
            machine.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
