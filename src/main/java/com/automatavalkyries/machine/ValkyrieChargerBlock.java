package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 兵姫用充電台のブロック: 低い台座＋座面＋背もたれ（暗い金属にシアンの光の線）。前（FACING）が置いた人の方を向く。
 * lit = 兵姫が座って充電している間（光が強くなる）。右クリックで蓄えた FE をアクションバーに出す。
 */
public class ValkyrieChargerBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** 北向きの形（台座・座面・背もたれ）。他の向きは回して作る */
    private static final double[][] NORTH_BOXES = {
            {0, 0, 0, 16, 2, 16},
            {2, 2, 2, 14, 6, 13},
            {2, 2, 13, 14, 16, 15},
    };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : NORTH_BOXES) shape = Shapes.or(shape, rotated(b, dir));
            SHAPES.put(dir, shape.optimize());
        }
    }

    /** 北向きの箱を dir 向きへ回す（ブロックステートの y 回転と同じ時計回り） */
    private static VoxelShape rotated(double[] b, Direction dir) {
        double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
        return switch (dir) {
            case EAST -> box(16 - z2, b[1], x1, 16 - z1, b[4], x2);
            case SOUTH -> box(16 - x2, b[1], 16 - z2, 16 - x1, b[4], 16 - z1);
            case WEST -> box(z1, b[1], 16 - x2, z2, b[4], 16 - x1);
            default -> box(x1, b[1], z1, x2, b[4], z2);
        };
    }

    public ValkyrieChargerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValkyrieChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModMachines.VALKYRIE_CHARGER_ENTITY.get(), ValkyrieChargerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ValkyrieChargerBlockEntity charger) {
            player.displayClientMessage(energyLine(charger.stored()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** アイテムの説明: 蓄えた FE（壊すと中身ごと落ちる）と用途 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        var tag = stack.getTagElement("BlockEntityTag");
        tooltip.add(energyLine(tag == null ? 0 : tag.getInt("energy")));
        tooltip.add(Component.translatable("block.automata_valkyries.valkyrie_charger.desc").withStyle(ChatFormatting.GRAY));
    }

    private static Component energyLine(int stored) {
        NumberFormat format = NumberFormat.getIntegerInstance();
        return Component.translatable("gui.automata_valkyries.energy", format.format(stored),
                format.format(ValkyrieChargerBlockEntity.CAPACITY)).withStyle(ChatFormatting.AQUA);
    }
}
