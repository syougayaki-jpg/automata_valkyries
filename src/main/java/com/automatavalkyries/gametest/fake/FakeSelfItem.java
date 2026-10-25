package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 試験用の「他 MOD のアイテム」自身の処理（皆殺しの剣の inventoryTick のように、持ち主の持ち物を書き換える。攻撃ではないので通す） */
public final class FakeSelfItem extends Item {

    private FakeSelfItem() {
        super(new Item.Properties());
    }

    /** アイテムのクラスの中から、持ち主の持ち物を書き換える */
    public static void rewriteOwnSlot(Player player, int slot) {
        player.getInventory().setItem(slot, new ItemStack(Items.STICK));
    }
}
