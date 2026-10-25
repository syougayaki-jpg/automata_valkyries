package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 試験用の「他 MOD」の持ち物の差し替え（{@code CallerAttribution.overrideForTest} でこのパッケージを別の MOD と見なす）。
 * Madness Entities と同じ 2 つの道: 持ち物の一覧への直接の書き込み（全プレイヤーの持ち物のランダム化）と、setItemSlot（Notch の本への置き換え）
 */
public final class FakeItemSwapper {

    private FakeItemSwapper() {}

    /** 持ち物の一覧へ直接書き込む（{@code inventory.items.set}） */
    public static void listSwap(Player player, int slot) {
        player.getInventory().items.set(slot, new ItemStack(Items.BOOK));
    }

    /** 持ち物を全部落として消す（Madness の倒す処理 killTarget・状態異常と同じ dropAll → clearContent） */
    public static void dropAndClear(Player player) {
        player.getInventory().dropAll();
        player.getInventory().clearContent();
    }

    /** 持ち物の束そのものを書き換える（Madness のランダム化の 2 周目と同じ: 数・NBT・名前） */
    public static void editInPlace(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        stack.setCount(1);
        stack.setTag(new net.minecraft.nbt.CompoundTag());
        stack.setHoverName(net.minecraft.network.chat.Component.literal("broken"));
    }

    /** 主手を本に置き換える（{@code Player.setItemSlot}） */
    public static void slotSwap(Player player) {
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOOK));
    }
}
