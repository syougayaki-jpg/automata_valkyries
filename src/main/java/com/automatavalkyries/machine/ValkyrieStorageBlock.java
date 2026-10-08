package com.automatavalkyries.machine;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.StorageListPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 兵姫の収納テレポーター（2026-10-09 ユーザー指定）: 右クリックで一覧を開き、近く（半径 {@link ValkyrieStorageBlockEntity#RANGE}）の自分の兵姫を
 * 中へしまい（{@link ValkyrieStorageBlockEntity#CAPACITY} 体まで）、しまった兵姫を前へ出す。壊すと中身を持ったまま落ちる（シュルカーボックスと同じ）
 */
public class ValkyrieStorageBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ValkyrieStorageBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValkyrieStorageBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ValkyrieStorageBlockEntity storage) {
            sendList(sp, storage, "");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 一覧（近くの自分の兵姫と、しまった兵姫）を送る。message は結果の翻訳キー（空なら無し） */
    public static void sendList(ServerPlayer player, ValkyrieStorageBlockEntity storage, String message) {
        List<StorageListPacket.Row> rows = storage.rows(player);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new StorageListPacket(storage.getBlockPos(), rows, storage.storedCount(), ValkyrieStorageBlockEntity.CAPACITY, message));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.automata_valkyries.valkyrie_storage.desc").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        int n = tag != null ? tag.getList(ValkyrieStorageBlockEntity.TAG, 10).size() : 0;
        if (n > 0) tooltip.add(Component.translatable("block.automata_valkyries.valkyrie_storage.count", n, ValkyrieStorageBlockEntity.CAPACITY).withStyle(ChatFormatting.AQUA));
    }
}
