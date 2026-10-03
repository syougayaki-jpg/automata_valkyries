package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ボス素材（boss_drop_*）。ツールチップに落とすボスの名前と、2 行のフレーバーテキストを出す
 * （{@code boss.automata_valkyries.<boss>}・{@code item.automata_valkyries.<id>.flavor1} / {@code flavor2}）。
 */
public class BossDropItem extends EffectMaterialItem {

    private final String boss;

    public BossDropItem(Properties properties, List<ParticleOptions> particles, int interval, String boss) {
        super(properties, particles, interval, true);
        this.boss = boss;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String id = ForgeRegistries.ITEMS.getKey(this).getPath();
        tooltip.add(Component.translatable("gui.automata_valkyries.dropped_by",
                Component.translatable("boss.automata_valkyries." + boss)).withStyle(ChatFormatting.DARK_PURPLE));
        for (int i = 1; i <= 2; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries." + id + ".flavor" + i).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
