package com.automatavalkyries.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** 兵装強化台（2026-10-04 ユーザー指定）: 右クリックで、エンチャントテーブルのように兵装を強化する画面を開く（{@link UpgradeStationMenu}）。中身は持たない */
public class UpgradeStationBlock extends Block {

    public UpgradeStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, p) -> new UpgradeStationMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.automata_valkyries.upgrade_station")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
