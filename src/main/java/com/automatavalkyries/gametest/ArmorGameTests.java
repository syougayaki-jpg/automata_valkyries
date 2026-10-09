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
}
