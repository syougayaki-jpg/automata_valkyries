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

    /** ティアの機能（2026-10-29）: T2 で重ならない物が 16、T3 で何でも 64 まで重なる・整理・取り出しはふつうの 1 スタックまで */
    @GameTest(template = TEMPLATE)
    public static void pocketTierStacking(GameTestHelper helper) {
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
        helper.assertTrue(ItemPocketItem.limit(1, potion) == 1 && ItemPocketItem.limit(2, potion) == 16 && ItemPocketItem.limit(3, potion) == 64, "potion limits");
        helper.assertTrue(ItemPocketItem.limit(2, new ItemStack(Items.ENDER_PEARL)) == 16 && ItemPocketItem.limit(3, new ItemStack(Items.ENDER_PEARL)) == 64, "pearl limits");
        helper.assertTrue(ItemPocketItem.limit(4, new ItemStack(Items.DIAMOND_SWORD)) == 1, "damageable items must not stack");
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        for (int i = 0; i < 20; i++) ItemPocketItem.insert(items, potion.copy(), 2);
        helper.assertTrue(items.get(0).getCount() == 16 && items.get(1).getCount() == 4, "T2 potions did not stack to 16: " + items.get(0).getCount() + "/" + items.get(1).getCount());
        items.set(5, new ItemStack(Items.TOTEM_OF_UNDYING));
        items.set(9, new ItemStack(Items.TOTEM_OF_UNDYING));
        ItemPocketItem.sort(items, 2);
        long used = items.stream().filter(st -> !st.isEmpty()).count();
        helper.assertTrue(used == 3, "sort did not merge: " + used + " slots used");
        // 取り出し: 中で 16 重なっていても 1 本ずつ
        FakePlayer player = player(helper);
        ItemStack pocket = new ItemStack(ModItems.ITEM_POCKET_T2.get());
        NonNullList<ItemStack> stored = ItemPocketItem.load(pocket);
        stored.set(0, potion.copyWithCount(16));
        ItemPocketItem.save(pocket, stored);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pocket);
        var menu = new com.automatavalkyries.menu.ItemPocketMenu(0, player.getInventory(), net.minecraft.world.InteractionHand.MAIN_HAND);
        ItemStack taken = menu.getSlot(0).remove(64);
        helper.assertTrue(taken.getCount() == 1 && menu.getSlot(0).getItem().getCount() == 15, "took " + taken.getCount() + " potions at once");
        helper.succeed();
    }

    /** T3 の自動回収: 入っている種類の物を拾うとポケットへ。T4 の補充: 手の物を使い切るとポケットから */
    @GameTest(template = TEMPLATE)
    public static void pocketCollectAndRefill(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack pocket = new ItemStack(ModItems.ITEM_POCKET_T4.get());
        NonNullList<ItemStack> stored = ItemPocketItem.load(pocket);
        stored.set(0, new ItemStack(Items.COBBLESTONE, 10));
        ItemPocketItem.save(pocket, stored);
        player.getInventory().setItem(9, pocket);
        var drop = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.COBBLESTONE, 5));
        helper.getLevel().addFreshEntity(drop);
        var event = new net.minecraftforge.event.entity.player.EntityItemPickupEvent(player, drop);
        com.automatavalkyries.item.PocketPerks.onPickup(event);
        helper.assertTrue(event.isCanceled() && ItemPocketItem.load(pocket).get(0).getCount() == 15, "cobblestone was not collected into the pocket");
        var dirt = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIRT, 3));
        helper.getLevel().addFreshEntity(dirt);
        var event2 = new net.minecraftforge.event.entity.player.EntityItemPickupEvent(player, dirt);
        com.automatavalkyries.item.PocketPerks.onPickup(event2);
        helper.assertFalse(event2.isCanceled(), "an item type not in the pocket was collected");
        dirt.discard();
        // 補充: 手の丸石を使い切った
        helper.assertTrue(com.automatavalkyries.item.PocketPerks.refill(player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 1)), "hand was not refilled");
        helper.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE) && player.getMainHandItem().getCount() == 15, "refill amount " + player.getMainHandItem());
        helper.succeed();
    }

    /** T4 のチート級の機能（2026-10-29）: 磁石・死んでも残る・どこでもリポジトリのリンク */
    @GameTest(template = TEMPLATE)
    public static void pocketNeverlastPerks(GameTestHelper helper) {
        FakePlayer player = player(helper);
        ItemStack pocket = new ItemStack(ModItems.ITEM_POCKET_T4.get());
        player.getInventory().setItem(9, pocket);
        // 磁石: 10 ブロック先の物が足元へ
        var far = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX() + 10, player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        far.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(far);
        com.automatavalkyries.item.PocketPerks.magnet(player);
        helper.assertTrue(far.distanceToSqr(player) < 1.0, "magnet did not pull the item: " + far.distanceTo(player));
        far.discard();
        // 死んでも残る: 控えを取り、落とし物から消し、生き返ると戻る
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        player.experienceLevel = 30;
        com.automatavalkyries.item.PocketPerks.onDeath(new net.minecraftforge.event.entity.living.LivingDeathEvent(player, helper.getLevel().damageSources().generic()));
        java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
        drops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND_SWORD)));
        drops.add(new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.APPLE)));
        var dropsEvent = new net.minecraftforge.event.entity.living.LivingDropsEvent(player, helper.getLevel().damageSources().generic(), drops, 0, true);
        com.automatavalkyries.item.PocketPerks.onDrops(dropsEvent);
        helper.assertTrue(dropsEvent.getDrops().size() == 1 && dropsEvent.getDrops().iterator().next().getItem().is(Items.APPLE), "kept items were still dropped: " + dropsEvent.getDrops().size());
        FakePlayer reborn = player(helper);
        com.automatavalkyries.item.PocketPerks.onClone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(reborn, player, true));
        helper.assertTrue(reborn.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && reborn.getInventory().getItem(9).getItem() == ModItems.ITEM_POCKET_T4.get(), "inventory was not restored");
        helper.assertTrue(reborn.experienceLevel == 30, "experience was not restored: " + reborn.experienceLevel);
        // どこでもリポジトリ: リンクの読み書き
        var at = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), new net.minecraft.core.BlockPos(100, 64, -200));
        ItemPocketItem.link(pocket, at);
        helper.assertTrue(at.equals(ItemPocketItem.repository(pocket)), "repository link was not kept: " + ItemPocketItem.repository(pocket));
        helper.succeed();
    }
}
