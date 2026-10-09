package com.automatavalkyries.item.armor;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * このMOD の固有金属のプレイヤー用防具の素材（2026-10-09 ユーザー指定）。硬さは 3 種とも鉄とダイヤの間（防御の合計 17・防具強度 1・耐久は鉄の約 1.7 倍。
 * 鉄 15 / ダイヤ 20）。違いは一式の便利効果（{@link MetalArmorSets}）。防具の模様は {@code textures/models/armor/<名前>_layer_1/2.png}
 */
public enum MetalArmorMaterial implements ArmorMaterial {
    ASTRITE("astrite", ModItems.INGOT_ASTRITE),
    PHONONIUM("phononium", ModItems.INGOT_PHONONIUM),
    CERULEAS("ceruleas", ModItems.INGOT_CERULEAS);

    /** 部位ごとの耐久の元（バニラと同じ）と、その倍率（鉄 15・ダイヤ 33） */
    private static final int[] DURABILITY_BASE = {13, 15, 16, 11};
    private static final int DURABILITY_MULTIPLIER = 25;
    /** 部位ごとの防御（ブーツ・レギンス・チェストプレート・ヘルメット） */
    private static final int[] DEFENSE = {2, 6, 7, 2};

    public final String id;
    private final Supplier<? extends net.minecraft.world.item.Item> ingot;

    MetalArmorMaterial(String id, Supplier<? extends net.minecraft.world.item.Item> ingot) {
        this.id = id;
        this.ingot = ingot;
    }

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return DURABILITY_BASE[index(type)] * DURABILITY_MULTIPLIER;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return DEFENSE[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_IRON;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ingot.get());
    }

    @Override
    public String getName() {
        return AutomataValkyries.MODID + ":" + id;
    }

    @Override
    public float getToughness() {
        return 1.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
