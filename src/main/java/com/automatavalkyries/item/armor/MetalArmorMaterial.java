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
 * <p>上位合金 4 種（2026-10-26 ユーザー指定「他の合金のインゴットの防具も追加して」。質問で上位合金 4 種）は一段上: 防御の合計 20（ダイヤと同じ）・
 * 防具強度 2.5・ノックバック耐性 0.05（1 部位ごと）・耐久は鉄の約 2.3 倍。一式効果は合金の性質（超伝導・重装甲・超機動・形状記憶）に合わせる</p>
 * <p>ステラ・クロノダイム（2026-10-26 ユーザー指定「一番強いがバニラを逸脱しない範囲で利便性でカスタム出来るように」）は一番上だが、強さはネザライトと同じ
 * （防御 20・防具強度 3・ノックバック耐性 0.1）で耐久だけ少し多い（ネザライト 37 → 40）。一式効果は無く、部位ごとの機能を画面で選ぶ（{@link StellaArmorItem}）</p>
 */
public enum MetalArmorMaterial implements ArmorMaterial {
    ASTRITE("astrite", ModItems.INGOT_ASTRITE, false),
    PHONONIUM("phononium", ModItems.INGOT_PHONONIUM, false),
    CERULEAS("ceruleas", ModItems.INGOT_CERULEAS, false),
    LUMINAL_STERLING("luminal_sterling", ModItems.ALLOY_LUMINAL_STERLING, true),
    CHRONO_FORTRESS("chrono_fortress", ModItems.ALLOY_CHRONO_FORTRESS, true),
    TACHYON_ACCEL("tachyon_accel", ModItems.ALLOY_TACHYON_ACCEL, true),
    ECLIPSE_BIOMETAL("eclipse_biometal", ModItems.ALLOY_ECLIPSE_BIOMETAL, true),
    STELLACHRONODYME("stellachronodyme", ModItems.INGOT_STELLACHRONODYME, true);

    /** 部位ごとの耐久の元（バニラと同じ）と、その倍率（鉄 15・ダイヤ 33。固有金属 25・上位合金 35） */
    private static final int[] DURABILITY_BASE = {13, 15, 16, 11};
    /** 部位ごとの防御（ブーツ・レギンス・チェストプレート・ヘルメット）。固有金属と上位合金 */
    private static final int[] DEFENSE = {2, 6, 7, 2}, ALLOY_DEFENSE = {3, 6, 8, 3};

    public final String id;
    /** 上位合金の一段上の防具か（ステラ・クロノダイムも） */
    public final boolean alloy;
    /** ステラ・クロノダイム（ネザライトと同じ強さ、部位ごとに機能を選ぶ）か */
    public final boolean stella;
    private final Supplier<? extends net.minecraft.world.item.Item> ingot;

    MetalArmorMaterial(String id, Supplier<? extends net.minecraft.world.item.Item> ingot, boolean alloy) {
        this.id = id;
        this.ingot = ingot;
        this.alloy = alloy;
        this.stella = "stellachronodyme".equals(id);
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
        return DURABILITY_BASE[index(type)] * (stella ? 40 : alloy ? 35 : 25);
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return (alloy ? ALLOY_DEFENSE : DEFENSE)[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return alloy ? 15 : 12;
    }

    @Override
    public SoundEvent getEquipSound() {
        return alloy ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_IRON;
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
        return stella ? 3.0F : alloy ? 2.5F : 1.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return stella ? 0.1F : alloy ? 0.05F : 0.0F;
    }
}
