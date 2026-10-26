package com.automatavalkyries.item.armor;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 固有金属の防具の一式の便利効果（戦闘には関わらない。2026-10-09 ユーザー指定）。4 部位とも同じ金属のときだけ効く:
 * <ul>
 *   <li>アストライト（採掘・建築）: 掘る速さ +30%・暗視・ブロックに届く距離 +1.5</li>
 *   <li>フォノニウム（移動）: 移動速度 +20%・1 ブロックの段差を歩いて上る・落下ダメージ無し</li>
 *   <li>セルリアス（水中・環境）: 水中呼吸・水中でも地上と同じ速さで掘れる・泳ぐ速さ +50%・燃えにくい（燃える時間は 1 秒まで、炎と溶岩のダメージ半分）</li>
 * </ul>
 * 上位合金の一式（2026-10-26。合金の性質に合わせて、こちらは戦いにも効く）:
 * <ul>
 *   <li>ルミナル・スターリング合金（超伝導）: 炎・溶岩・雷のダメージを受けず燃えない・暗視・攻撃の速さ +20%</li>
 *   <li>クロノ・フォートレス鋼（重装甲・衝撃吸収）: ノックバックを受けない・受けるダメージ -15%（爆発は半分）・最大 HP +4</li>
 *   <li>タキオン・アクセル合金（慣性相殺・超機動）: 移動速度 +35%・跳躍力上昇 II・落下ダメージ無し・1 ブロックの段差を歩いて上る</li>
 *   <li>エクリプス・バイオメタル（形状記憶）: 5 秒ごとに HP 1 回復と防具の耐久が戻る・毒・衰弱（ウィザー）・空腹が効かない</li>
 * </ul>
 * 属性の上乗せは保存しない一時の補正で付け外しする（脱げばすぐ消える）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class MetalArmorSets {

    private static final UUID REACH = UUID.fromString("6a1f2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID SPEED = UUID.fromString("7b2a3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e");
    private static final UUID STEP = UUID.fromString("8c3b4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f");
    private static final UUID SWIM = UUID.fromString("9d4c5f6a-7b8c-4d9e-9fa0-2b3c4d5e6f70");
    private static final UUID ATTACK_SPEED = UUID.fromString("ae5d6a7b-8c9d-4eaf-80b1-3c4d5e6f7081");
    private static final UUID KNOCKBACK = UUID.fromString("bf6e7b8c-9dae-4fb0-91c2-4d5e6f708192");
    private static final UUID HEALTH = UUID.fromString("c07f8c9d-aebf-40c1-a2d3-5e6f708192a3");
    private static final UUID TACHYON_SPEED = UUID.fromString("d1809dae-bfc0-41d2-b3e4-6f708192a3b4");
    private static final UUID TACHYON_STEP = UUID.fromString("e291aebf-c0d1-42e3-84f5-708192a3b4c5");

    private MetalArmorSets() {}

    /** 4 部位とも同じ固有金属ならその金属（違えば null） */
    @Nullable
    public static MetalArmorMaterial fullSet(LivingEntity entity) {
        MetalArmorMaterial set = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof MetalArmorItem armor)) return null;
            if (set == null) set = armor.metal();
            else if (set != armor.metal()) return null;
        }
        return set;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide) return;
        MetalArmorMaterial set = fullSet(player);
        modifier(player, ForgeMod.BLOCK_REACH.get(), REACH, "metal armor reach", 1.5, AttributeModifier.Operation.ADDITION, set == MetalArmorMaterial.ASTRITE);
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED, "metal armor speed", 0.2, AttributeModifier.Operation.MULTIPLY_BASE, set == MetalArmorMaterial.PHONONIUM);
        modifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), STEP, "metal armor step", 0.5, AttributeModifier.Operation.ADDITION, set == MetalArmorMaterial.PHONONIUM);
        modifier(player, ForgeMod.SWIM_SPEED.get(), SWIM, "metal armor swim", 0.5, AttributeModifier.Operation.MULTIPLY_BASE, set == MetalArmorMaterial.CERULEAS);
        modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED, "alloy armor attack speed", 0.2, AttributeModifier.Operation.MULTIPLY_BASE, set == MetalArmorMaterial.LUMINAL_STERLING);
        modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, "alloy armor knockback", 1.0, AttributeModifier.Operation.ADDITION, set == MetalArmorMaterial.CHRONO_FORTRESS);
        modifier(player, Attributes.MAX_HEALTH, HEALTH, "alloy armor health", 4.0, AttributeModifier.Operation.ADDITION, set == MetalArmorMaterial.CHRONO_FORTRESS);
        modifier(player, Attributes.MOVEMENT_SPEED, TACHYON_SPEED, "alloy armor speed", 0.35, AttributeModifier.Operation.MULTIPLY_BASE, set == MetalArmorMaterial.TACHYON_ACCEL);
        modifier(player, ForgeMod.STEP_HEIGHT_ADDITION.get(), TACHYON_STEP, "alloy armor step", 0.5, AttributeModifier.Operation.ADDITION, set == MetalArmorMaterial.TACHYON_ACCEL);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());   // 脱いで最大 HP が戻ったとき
        if (set == MetalArmorMaterial.LUMINAL_STERLING && player.isOnFire()) player.clearFire();
        if (set == null || player.tickCount % 20 != 0) return;
        switch (set) {
            case ASTRITE, LUMINAL_STERLING -> keepEffect(player, MobEffects.NIGHT_VISION);
            case CERULEAS -> keepEffect(player, MobEffects.WATER_BREATHING);
            case TACHYON_ACCEL -> keepEffect(player, MobEffects.JUMP, 1);
            case ECLIPSE_BIOMETAL -> {
                player.removeEffect(MobEffects.POISON);
                player.removeEffect(MobEffects.WITHER);
                player.removeEffect(MobEffects.HUNGER);
                if (player.tickCount % 100 == 0) {                                                // 形状記憶: 体と防具がゆっくり元に戻る
                    if (player.getHealth() < player.getMaxHealth()) player.heal(1.0F);
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        var piece = player.getItemBySlot(slot);
                        if (piece.isDamaged()) piece.setDamageValue(Math.max(0, piece.getDamageValue() - 2));
                    }
                }
            }
            default -> {
            }
        }
    }

    /** セルリアス: 燃える時間を 1 秒までにする（火の熱を逃がす） */
    @SubscribeEvent
    public static void onFireTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.getRemainingFireTicks() > 20 && fullSet(event.player) == MetalArmorMaterial.CERULEAS) event.player.setRemainingFireTicks(20);
    }

    /** アストライト: 掘る速さ +30%。セルリアス: 水中の遅さ（目が水の中・足が地面に着いていない、それぞれ 1/5）を打ち消す */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        MetalArmorMaterial set = fullSet(player);
        if (set == MetalArmorMaterial.ASTRITE) {
            event.setNewSpeed(event.getNewSpeed() * 1.3F);
        } else if (set == MetalArmorMaterial.CERULEAS && player.isInWater()) {
            float speed = event.getNewSpeed();
            if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) speed *= 5.0F;
            if (!player.onGround()) speed *= 5.0F;
            event.setNewSpeed(speed);
        }
    }

    /** フォノニウム・タキオン・アクセル合金: 落下ダメージ無し */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && (fullSet(player) == MetalArmorMaterial.PHONONIUM || fullSet(player) == MetalArmorMaterial.TACHYON_ACCEL)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    /** ルミナル・スターリング合金: 炎・溶岩・雷のダメージを受けない */
    @SubscribeEvent
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && fullSet(player) == MetalArmorMaterial.LUMINAL_STERLING
                && (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_LIGHTNING))) {
            event.setCanceled(true);
        }
    }

    /** セルリアス: 炎・溶岩のダメージ半分。クロノ・フォートレス鋼: 受けるダメージ -15%・爆発は半分 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        MetalArmorMaterial set = fullSet(player);
        if (event.getSource().is(DamageTypeTags.IS_FIRE) && set == MetalArmorMaterial.CERULEAS) event.setAmount(event.getAmount() * 0.5F);
        if (set == MetalArmorMaterial.CHRONO_FORTRESS) event.setAmount(event.getAmount() * (event.getSource().is(DamageTypeTags.IS_EXPLOSION) ? 0.5F : 0.85F));   // 衝撃を吸う
    }

    /** 一時の補正を付け外しする（保存しない） */
    private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        boolean has = instance.getModifier(id) != null;
        if (on && !has) instance.addTransientModifier(new AttributeModifier(id, name, amount, op));
        else if (!on && has) instance.removeModifier(id);
    }

    /** 効果を切らさない（残りが 11 秒を切ったら 15 秒に。点滅しないよう余裕を持たせる。粒は出さない） */
    private static void keepEffect(Player player, MobEffect effect) {
        keepEffect(player, effect, 0);
    }

    private static void keepEffect(Player player, MobEffect effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 220 || current.getAmplifier() < amplifier) player.addEffect(new MobEffectInstance(effect, 300, amplifier, true, false, true));
    }
}
