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
 * 属性の上乗せは保存しない一時の補正で付け外しする（脱げばすぐ消える）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class MetalArmorSets {

    private static final UUID REACH = UUID.fromString("6a1f2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID SPEED = UUID.fromString("7b2a3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e");
    private static final UUID STEP = UUID.fromString("8c3b4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f");
    private static final UUID SWIM = UUID.fromString("9d4c5f6a-7b8c-4d9e-9fa0-2b3c4d5e6f70");

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
        if (set == null || player.tickCount % 20 != 0) return;
        switch (set) {
            case ASTRITE -> keepEffect(player, MobEffects.NIGHT_VISION);
            case CERULEAS -> keepEffect(player, MobEffects.WATER_BREATHING);
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

    /** フォノニウム: 落下ダメージ無し */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && fullSet(player) == MetalArmorMaterial.PHONONIUM) event.setDamageMultiplier(0.0F);
    }

    /** セルリアス: 炎・溶岩のダメージ半分 */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && event.getSource().is(DamageTypeTags.IS_FIRE) && fullSet(player) == MetalArmorMaterial.CERULEAS) {
            event.setAmount(event.getAmount() * 0.5F);
        }
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
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getDuration() < 220) player.addEffect(new MobEffectInstance(effect, 300, 0, true, false, true));
    }
}
