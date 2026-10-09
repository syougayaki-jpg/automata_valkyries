package com.automatavalkyries.item.armor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 固有金属の防具の 1 部位。説明に一式の便利効果を出す（一式を着ていれば明るく） */
public class MetalArmorItem extends ArmorItem {

    private final MetalArmorMaterial metal;

    public MetalArmorItem(MetalArmorMaterial metal, Type type, Properties properties) {
        super(metal, type, properties);
        this.metal = metal;
    }

    public MetalArmorMaterial metal() {
        return metal;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 自分が着ているか（クライアントのクラスは別のクラスに分ける。共通のクラスから直接触ると専用サーバーで読み込めない）
        boolean worn = FMLEnvironment.dist == Dist.CLIENT && com.automatavalkyries.client.MetalArmorClient.wornSet() == metal;
        ChatFormatting head = worn ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        ChatFormatting body = worn ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
        tooltip.add(Component.translatable("item.automata_valkyries.metal_armor.set", Component.translatable("item.automata_valkyries.metal_armor.set." + metal.id)).withStyle(head));
        for (int i = 1; i <= 4; i++) {
            String key = "item.automata_valkyries.metal_armor.set." + metal.id + "." + i;
            if (net.minecraft.locale.Language.getInstance().has(key)) tooltip.add(Component.literal("  ").append(Component.translatable(key)).withStyle(body));
        }
    }
}
