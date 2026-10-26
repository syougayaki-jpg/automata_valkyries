package com.automatavalkyries.menu;

import com.automatavalkyries.item.armor.StellaArmorItem;
import com.automatavalkyries.registry.ModArmor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * ステラ・クロノダイムの防具の機能を選ぶ画面（{@link StellaArmorItem}）。枠は無く、ボタン（バニラの画面のボタンの送り）で機能の段を回す。
 * 今の段は 1 つの数（機能ごとに 2 bit）で描く側へ送る
 */
public class StellaTuneMenu extends AbstractContainerMenu {

    final ItemStack armor;
    final InteractionHand hand;
    public final List<StellaArmorItem.Tune> tunes;
    final DataSlot levels = DataSlot.standalone();

    public StellaTuneMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public StellaTuneMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModArmor.TUNE_MENU.get(), id);
        this.hand = hand;
        this.armor = inv.player.getItemInHand(hand);
        this.tunes = armor.getItem() instanceof ArmorItem a ? StellaArmorItem.Tune.of(a.getType()) : List.of();
        if (!inv.player.level().isClientSide) levels.set(pack());
        addDataSlot(levels);
    }

    private int pack() {
        int v = 0;
        for (int i = 0; i < tunes.size(); i++) v |= StellaArmorItem.level(armor, tunes.get(i)) << (i * 2);
        return v;
    }

    /** i 番目の機能の今の段（描く側はサーバーから届いた数から） */
    public int level(int i) {
        return (levels.get() >> (i * 2)) & 3;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= tunes.size()) return false;
        StellaArmorItem.cycle(armor, tunes.get(id));
        levels.set(pack());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand) == armor;
    }
}
