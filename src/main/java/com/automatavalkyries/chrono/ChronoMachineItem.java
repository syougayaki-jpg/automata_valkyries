package com.automatavalkyries.chrono;

import com.automatavalkyries.item.ChronoTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * クロノリンク・マシンのアイテム。ティアは BlockStateTag.tier に持ち（壊したときのルートで写す）、置くとそのティアになる。
 * T4 のヴォイド・リポジトリは因果固定: 落ちていても消えず、壊されず、奈落から戻る。
 */
public class ChronoMachineItem extends BlockItem {

    public ChronoMachineItem(ChronoMachineBlock block, Properties properties) {
        super(block, properties);
    }

    public static ChronoTier tierOf(ItemStack stack) {
        CompoundTag state = stack.getTagElement("BlockStateTag");
        if (state == null || !state.contains("tier")) return ChronoTier.HIGH_END;
        try {
            return ChronoTier.of(Integer.parseInt(state.getString("tier")));
        } catch (NumberFormatException e) {
            return ChronoTier.HIGH_END;
        }
    }

    public static ItemStack withTier(ItemStack stack, ChronoTier tier) {
        stack.getOrCreateTagElement("BlockStateTag").putString("tier", Integer.toString(tier.level));
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return tierOf(stack).level >= 3; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ChronoTier tier = tierOf(stack);
        tooltip.add(Component.translatable("tier.automata_valkyries." + tier.id).withStyle(ChronoTiers.color(tier)));
        tooltip.add(Component.translatable("block.automata_valkyries." + ((ChronoMachineBlock) getBlock()).type().id + ".desc").withStyle(ChatFormatting.GRAY));
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        if (be != null && be.contains("Contents")) {
            tooltip.add(Component.translatable("block.automata_valkyries.void_repository.stored",
                    be.getList("Contents", 10).size()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (((ChronoMachineBlock) getBlock()).type() == ChronoMachineType.VOID_REPOSITORY && tierOf(stack) == ChronoTier.NEVERLAST) {
            entity.setUnlimitedLifetime();
            entity.setInvulnerable(true);
            if (entity.getY() < entity.level().getMinBuildHeight() + 1) {
                entity.setDeltaMovement(0, 0, 0);
                entity.setPos(entity.getX(), entity.level().getMinBuildHeight() + 64, entity.getZ());
            }
        }
        return false;
    }
}
