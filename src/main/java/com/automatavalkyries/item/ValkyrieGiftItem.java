package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 兵姫の好物（追加拡張仕様書 §12.1「好物プレゼント」+5%）。
 * 兵姫へ渡す処理は兵姫のエンティティ側で行う。ここはプレイヤーも食べられる小さなおやつとしての中身と説明文だけ。
 */
public class ValkyrieGiftItem extends Item {

    public ValkyrieGiftItem(Properties properties) {
        super(properties);
    }

    /** ルミナルキャンディ: 少し腹持ち、すぐ食べられ、短い移動速度上昇 I（5 秒） */
    public static FoodProperties candyFood() {
        return new FoodProperties.Builder().nutrition(2).saturationMod(0.3F).fast().alwaysEat()
                .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0), 1.0F).build();
    }

    /** 冷却アイス: 少し腹持ち、すぐ食べられ、耐火 10 秒 */
    public static FoodProperties iceFood() {
        return new FoodProperties.Builder().nutrition(2).saturationMod(0.3F).fast().alwaysEat()
                .effect(() -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0), 1.0F).build();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_gift.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_gift.tooltip.how").withStyle(ChatFormatting.GRAY));
    }
}
