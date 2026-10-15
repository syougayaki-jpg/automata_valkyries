package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.FlightWingItem;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 光翼の飛翔機（2026-10-15）: 持てば飛べ、飛ぶ間は電力を使い、手放すと飛べなくなる。クリエイティブの飛行には手を出さない（世界に頼らず、毎 Tick の処理を直に呼ぶ） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class FlightWingGameTests {

    @GameTest(template = "empty", batch = "flight_wing", timeoutTicks = 20)
    public static void flightWingGrantsAndRevokesFlight(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "flight_wing_test"));
        player.setGameMode(GameType.SURVIVAL);
        ItemStack wing = ((FlightWingItem) ModItems.TOOL_FLIGHT_WING.get()).charged();
        player.setItemInHand(InteractionHand.OFF_HAND, wing); // オフハンドでも効く

        FlightWingItem.update(player);
        helper.assertTrue(player.getAbilities().mayfly && FlightWingItem.grantedTo(player), "holding a charged wing did not allow flight");
        player.getAbilities().flying = true;
        int before = ItemEnergy.get(wing);
        FlightWingItem.update(player);
        helper.assertTrue(ItemEnergy.get(wing) < before, "flying did not use power: " + ItemEnergy.get(wing) + " / " + before);

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        FlightWingItem.update(player);
        helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying || FlightWingItem.grantedTo(player), "letting go of the wing did not take flight away");

        player.setItemInHand(InteractionHand.MAIN_HAND, wing);
        ItemEnergy.set(wing, 0);
        FlightWingItem.update(player);
        helper.assertFalse(player.getAbilities().mayfly, "an empty wing allowed flight");

        player.setGameMode(GameType.CREATIVE); // クリエイティブの飛行は取り上げない
        player.getAbilities().flying = true;
        FlightWingItem.update(player);
        helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying, "the wing took creative flight away");
        helper.succeed();
    }
}
