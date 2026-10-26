package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.ItemPocketItem;
import com.automatavalkyries.item.PocketAutoUse;
import com.automatavalkyries.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** アイテムポケット（2026-10-26）: 容量・自動使用（トーテム・回復ポーション・食べ物）・分類の切り替え・ポケットはポケットに入らない */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ItemPocketGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pocket_test"));
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return player;
    }

    private static ItemStack pocketWith(ItemStack... contents) {
        ItemStack pocket = new ItemStack(ModItems.ITEM_POCKET_T1.get());
        NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
        for (int i = 0; i < contents.length; i++) items.set(i, contents[i]);
        ItemPocketItem.save(pocket, items);
        return pocket;
    }

    @GameTest(template = TEMPLATE)
    public static void pocketCapacitiesAndNesting(GameTestHelper helper) {
        helper.assertTrue(ItemPocketItem.capacityOf(new ItemStack(ModItems.ITEM_POCKET_T1.get())) == 27, "T1 capacity");
        helper.assertTrue(ItemPocketItem.capacityOf(new ItemStack(ModItems.ITEM_POCKET_T2.get())) == 54, "T2 capacity");
        helper.assertTrue(ItemPocketItem.capacityOf(new ItemStack(ModItems.ITEM_POCKET_T3.get())) == 108, "T3 capacity");
        helper.assertTrue(ItemPocketItem.capacityOf(new ItemStack(ModItems.ITEM_POCKET_T4.get())) == 216, "T4 capacity");
        helper.assertFalse(ItemPocketItem.canStore(new ItemStack(ModItems.ITEM_POCKET_T1.get())), "a pocket fits in a pocket");
        helper.assertFalse(ItemPocketItem.canStore(new ItemStack(Items.SHULKER_BOX)), "a shulker box fits in a pocket");
        helper.assertTrue(ItemPocketItem.canStore(new ItemStack(Items.TOTEM_OF_UNDYING)), "a totem does not fit");
        ItemStack big = new ItemStack(ModItems.ITEM_POCKET_T4.get());
        NonNullList<ItemStack> items = ItemPocketItem.load(big);
        items.set(215, new ItemStack(Items.BREAD, 5));
        ItemPocketItem.save(big, items);
        helper.assertTrue(ItemPocketItem.load(big).get(215).getCount() == 5, "the last slot of a T4 pocket was not kept");
        helper.succeed();
    }

    /** 死ぬ時にポケットのトーテムが身代わりになる（切ってあれば使わない） */
    @GameTest(template = TEMPLATE)
    public static void pocketTotemSavesFromDeath(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack pocket = pocketWith(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
        player.getInventory().setItem(20, pocket);
        ItemPocketItem.toggle(pocket, ItemPocketItem.Category.TOTEM);
        helper.assertFalse(PocketAutoUse.saveWithTotem(player, helper.getLevel().damageSources().generic()), "used a totem with the category off");
        ItemPocketItem.toggle(pocket, ItemPocketItem.Category.TOTEM);
        helper.assertFalse(PocketAutoUse.saveWithTotem(player, helper.getLevel().damageSources().fellOutOfWorld()), "a totem saved from the void");
        helper.assertTrue(PocketAutoUse.saveWithTotem(player, helper.getLevel().damageSources().generic()), "the pocket totem did not save the player");
        helper.assertTrue(player.getHealth() == 1.0F, "health after the totem: " + player.getHealth());
        helper.assertTrue(ItemPocketItem.load(pocket).get(0).isEmpty(), "the totem was not used up");
        helper.succeed();
    }

    /** HP 40% 以下で治癒のポーションを飲み（空き瓶はポケットへ）、満腹度 14 以下でパンを食べる。毒のある物は食べない */
    @GameTest(template = TEMPLATE)
    public static void pocketDrinksAndEats(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack pocket = pocketWith(new ItemStack(Items.ROTTEN_FLESH, 8), PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING), new ItemStack(Items.BREAD, 4));
        player.getInventory().setItem(9, pocket);
        player.setHealth(5.0F);
        player.getFoodData().setFoodLevel(6);
        PocketAutoUse.tick(player);
        NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
        helper.assertTrue(player.getHealth() > 5.0F, "the healing potion was not drunk: " + player.getHealth());
        helper.assertTrue(items.get(1).is(Items.GLASS_BOTTLE), "the empty bottle did not go back: " + items.get(1));
        helper.assertTrue(items.get(2).getCount() == 3, "the bread was not eaten: " + items.get(2));
        helper.assertTrue(items.get(0).getCount() == 8, "ate rotten flesh");
        helper.assertTrue(player.getFoodData().getFoodLevel() > 6, "food level " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }
}
