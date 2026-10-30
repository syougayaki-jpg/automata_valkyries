package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.armor.MetalArmorMaterial;
import com.automatavalkyries.item.armor.MetalArmorSets;
import com.automatavalkyries.registry.ModArmor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** プレイヤー用の固有金属の防具（2026-10-09）: 4 部位そろえたときだけ一式効果が付き、脱ぐと消える */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ArmorGameTests {

    private static void wear(Player player, String metal) {
        String[] parts = {"helmet", "chestplate", "leggings", "boots"};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            String id = metal + "_" + parts[i];
            var item = ModArmor.ALL.stream().filter(r -> r.getId().getPath().equals(id)).findFirst().orElseThrow().get();
            player.setItemSlot(slots[i], new ItemStack(item));
        }
    }

    /** 着ている防具の防御の合計（試験の人形は Tick しないので、装備の属性はまだ付いていない。品物から数える） */
    private static int defense(Player player) {
        int sum = 0;
        for (ItemStack s : player.getArmorSlots()) if (s.getItem() instanceof net.minecraft.world.item.ArmorItem a) sum += a.getDefense();
        return sum;
    }

    private static float toughness(Player player) {
        float sum = 0;
        for (ItemStack s : player.getArmorSlots()) if (s.getItem() instanceof net.minecraft.world.item.ArmorItem a) sum += a.getToughness();
        return sum;
    }

    private static void tick(Player player) {
        MetalArmorSets.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    @GameTest(template = "empty", batch = "metal_armor")
    public static void metalArmorSetBonusesNeedTheFullSet(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        wear(player, "phononium");
        helper.assertTrue(MetalArmorSets.fullSet(player) == MetalArmorMaterial.PHONONIUM, "full phononium set was not detected");
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        tick(player);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 1.15, "phononium set did not raise movement speed");
        helper.assertTrue(player.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()) >= 0.5, "phononium set did not raise step height");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(MetalArmorSets.fullSet(player) == null, "an incomplete set still counts");
        tick(player);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - base) < 1.0E-6, "speed bonus stayed after taking a piece off");
        wear(player, "astrite");
        tick(player);
        helper.assertTrue(player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) >= 5.9, "astrite set did not raise block reach");
        wear(player, "ceruleas");
        tick(player);
        helper.assertTrue(player.getAttributeValue(ForgeMod.SWIM_SPEED.get()) >= 1.49, "ceruleas set did not raise swim speed");
        helper.assertTrue(player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) < 5.0, "astrite reach stayed after switching sets");
        helper.succeed();
    }

    /** 上位合金の防具（2026-10-26）: ダイヤと同じ防御 20・防具強度 2.5 で、一式効果が合金ごとに付く */
    @GameTest(template = "empty", batch = "metal_armor")
    public static void alloyArmorIsStrongerAndHasSetBonuses(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        wear(player, "chrono_fortress");
        helper.assertTrue(MetalArmorSets.fullSet(player) == MetalArmorMaterial.CHRONO_FORTRESS, "full chrono fortress set was not detected");
        helper.assertTrue(defense(player) == 20, "alloy armor value " + defense(player));
        helper.assertTrue(toughness(player) >= 9.9F, "alloy toughness " + toughness(player));
        tick(player);
        helper.assertTrue(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "chrono fortress set did not stop knockback");
        helper.assertTrue(player.getMaxHealth() >= 24.0F, "chrono fortress set did not raise max health: " + player.getMaxHealth());
        wear(player, "tachyon_accel");
        double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        tick(player);
        helper.assertTrue(player.getMaxHealth() <= 20.0F, "max health bonus stayed after switching sets");
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > base * 1.3, "tachyon set did not raise movement speed");
        wear(player, "luminal_sterling");
        tick(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_SPEED) > 4.5, "luminal sterling set did not raise attack speed");
        helper.succeed();
    }

    /** ステラ・クロノダイムの防具（2026-10-26）: ネザライトと同じ強さで、選んだ機能だけがその部位を着ている間に効き、切ると消える */
    @GameTest(template = "empty", batch = "metal_armor")
    public static void stellaArmorTunesPerPiece(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        wear(player, "stellachronodyme");
        helper.assertTrue(defense(player) == 20, "stella armor value " + defense(player));
        helper.assertTrue(toughness(player) >= 11.9F, "stella toughness " + toughness(player));
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD), legs = player.getItemBySlot(EquipmentSlot.LEGS), boots = player.getItemBySlot(EquipmentSlot.FEET);
        com.automatavalkyries.item.armor.StellaArmorItem.apply(player);
        helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "night vision without choosing it");
        com.automatavalkyries.item.armor.StellaArmorItem.cycle(helmet, com.automatavalkyries.item.armor.StellaArmorItem.Tune.NIGHT_VISION);
        com.automatavalkyries.item.armor.StellaArmorItem.cycle(legs, com.automatavalkyries.item.armor.StellaArmorItem.Tune.SPEED);
        com.automatavalkyries.item.armor.StellaArmorItem.cycle(legs, com.automatavalkyries.item.armor.StellaArmorItem.Tune.SPEED);
        com.automatavalkyries.item.armor.StellaArmorItem.cycle(boots, com.automatavalkyries.item.armor.StellaArmorItem.Tune.STEP);
        player.tickCount = 0;
        com.automatavalkyries.item.armor.StellaArmorItem.apply(player);
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "night vision was not given");
        var speed = player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 1, "speed II was not given: " + speed);
        helper.assertTrue(player.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()) >= 0.5, "step assist was not given");
        com.automatavalkyries.item.armor.StellaArmorItem.cycle(helmet, com.automatavalkyries.item.armor.StellaArmorItem.Tune.NIGHT_VISION);   // 入 → 切
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        com.automatavalkyries.item.armor.StellaArmorItem.apply(player);
        helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "night vision stayed after turning it off");
        helper.assertTrue(player.getAttributeValue(ForgeMod.STEP_HEIGHT_ADDITION.get()) < 0.5, "step assist stayed after taking the boots off");
        helper.succeed();
    }

    /**
     * ステラの防具は自分が付けた効果だけを外す（2026-10-31「ティア４兵装を持った時に一瞬火炎耐性がでたりきえたりする」）:
     * T4 兵装の加護（粒の出ない環境の効果、60 Tick）の火炎耐性を、火炎耐性の機能を選んでいない防具が外さない
     */
    @GameTest(template = "empty", batch = "metal_armor")
    public static void stellaArmorKeepsWeaponBlessings(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        wear(player, "stellachronodyme");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
        for (int i = 0; i < 3; i++) {
            player.tickCount = i * 10;
            com.automatavalkyries.item.armor.StellaArmorItem.apply(player);
        }
        helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "stella armor removed a weapon blessing (fire resistance)");
        helper.succeed();
    }
}
