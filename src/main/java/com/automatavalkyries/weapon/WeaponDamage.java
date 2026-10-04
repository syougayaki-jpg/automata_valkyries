package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/** 兵装のダメージの種類と計算 */
public final class WeaponDamage {

    /** 徹甲（防具を通らない種類。防具の一部だけ効かせる分は {@link #armorPiercing} で先に引く）。飛び道具の扱い */
    public static final ResourceKey<DamageType> ARMOR_PIERCING =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "armor_piercing"));

    /** 重粒子収束砲の光線（ガリアフォートの「ダメージ 1 固定の場」を通す目印にする） */
    public static final ResourceKey<DamageType> PARTICLE_BEAM =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "particle_beam"));
    /** カタストロフ・バスターの空間崩壊（防具・エンチャント・状態異常・耐性を通らない。999 固定） */
    public static final ResourceKey<DamageType> CATASTROPHE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "catastrophe"));

    /** クアンタムブレードの三日月の斬撃 */
    public static final ResourceKey<DamageType> QUANTUM_SLASH =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "quantum_slash"));

    /** パニッシュメント・レールガンのレール（防具・エンチャント・状態異常・耐性を通らない。カンストダメージ） */
    public static final ResourceKey<DamageType> PUNISHMENT =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "punishment"));

    /** アバランチの冷凍ミサイル（飛び道具の扱い） */
    public static final ResourceKey<DamageType> FROST_MISSILE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "frost_missile"));

    /** 電撃（アークランサーの稲妻・サンダージャベリンの落雷） */
    public static final ResourceKey<DamageType> ARC =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "arc"));
    /** エネルギーの弾・刃（散弾・チャクラム・プリズムの光線・時間の矢・投げ槍） */
    public static final ResourceKey<DamageType> ENERGY =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "energy"));
    /** ジェネシス・ヘイローの軌道砲撃（防具・エンチャント・状態異常・耐性を通らない） */
    public static final ResourceKey<DamageType> ORBITAL =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "orbital"));

    /** ラグナロク・エッジの大地を割る斬撃（防具・エンチャント・状態異常・耐性を通らない） */
    public static final ResourceKey<DamageType> RAGNAROK =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "ragnarok"));

    /** オーバーロードの T4 兵装の通常攻撃（ウィザーの出現直後の無敵などの「無敵」を通る。2026-10-04 ユーザー指定） */
    public static final ResourceKey<DamageType> OVERLOAD_PIERCE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "overload_pierce"));

    /** オーバーロードの T4 兵装の通常攻撃の割合ダメージ（最大 HP の 1%。無敵・防具・エンチャント・状態異常・耐性を通り、強化の倍率は掛けない） */
    public static final ResourceKey<DamageType> OVERLOAD_REND =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "overload_rend"));

    private WeaponDamage() {}

    public static DamageSource source(Entity context, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing) {
        return new DamageSource(context.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), direct, causing);
    }

    /**
     * 防具の ignore（0〜1）の割合を無視したときのダメージ。防具を通らない種類（{@link #ARMOR_PIERCING}）と組み合わせて使う。
     * 残りの防具・防具強度はバニラと同じ式で効かせる（エンチャントの軽減はそのまま効く）。
     */
    public static float armorPiercing(LivingEntity target, float damage, float ignore) {
        float keep = 1.0F - ignore;
        return CombatRules.getDamageAfterAbsorb(damage, target.getArmorValue() * keep,
                (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * keep);
    }
}
