package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

/**
 * 電力ケーブル（パイプは無く、電線だけ）。隣のケーブルと、電力を持つブロック（FE）に自動でつながる。
 * 電力の移動はつながったケーブル全体（ネットワーク）で CableBlockEntity が行う。
 */
public class CableBlock extends BaseEntityBlock {

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape[] ARMS = {
            Block.box(6, 0, 6, 10, 6, 10),    // DOWN
            Block.box(6, 10, 6, 10, 16, 10),  // UP
            Block.box(6, 6, 0, 10, 10, 6),    // NORTH
            Block.box(6, 6, 10, 10, 10, 16),  // SOUTH
            Block.box(0, 6, 6, 6, 10, 10),    // WEST
            Block.box(10, 6, 6, 16, 10, 10)}; // EAST

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) shape = Shapes.or(shape, ARMS[side.get3DDataValue()]);
        }
        return shape;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(level, neighborPos, side));
    }

    private static BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), connects(level, pos.relative(side), side));
        }
        return state;
    }

    /** 隣がケーブルか、電力を持つブロックならつながる */
    static boolean connects(BlockGetter level, BlockPos neighborPos, Direction side) {
        if (level.getBlockState(neighborPos).getBlock() instanceof CableBlock) return true;
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        return neighbor != null && neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).isPresent();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModMachines.CABLE_ENTITY.get(), CableBlockEntity::serverTick);
    }
}
