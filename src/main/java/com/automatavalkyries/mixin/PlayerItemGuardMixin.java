package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.PlayerItemGuard;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.NonNullList;
import org.jetbrains.annotations.Nullable;

/** 保護中のプレイヤーの持ち物の差し替え（{@link PlayerItemGuard}）の入口 */
public final class PlayerItemGuardMixin {

    private PlayerItemGuardMixin() {}

    @Mixin(Player.class)
    public abstract static class Slot {
        @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardSlot(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
            Player self = (Player) (Object) this;
            if (PlayerItemGuard.shouldBlock(self, self.getItemBySlot(slot), stack, "setItemSlot:" + slot.getName())) ci.cancel();
        }
    }

    /**
     * 持ち物の一覧への直接の書き込み（{@code inventory.items.set(...)}）の入口。Madness Entities は setItem を通らずに一覧へ直接
     * ランダムなアイテムを書き込む（2026-10-24 の調べ）。持ち主の印が付いた一覧だけ見るので、他の一覧への書き込みは素通し
     */
    @Mixin(NonNullList.class)
    public abstract static class ListSet implements com.automatavalkyries.protect.OwnedItemList {
        @Unique
        @Nullable
        private Player automata_valkyries$owner;

        @Override
        public Player automata_valkyries$getOwner() {
            return automata_valkyries$owner;
        }

        @Override
        public void automata_valkyries$setOwner(Player owner) {
            automata_valkyries$owner = owner;
        }

        @Inject(method = "set", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardListSet(int index, Object element, CallbackInfoReturnable<Object> cir) {
            Player owner = automata_valkyries$owner;
            if (owner == null || !(element instanceof ItemStack incoming)) return;
            NonNullList<?> self = (NonNullList<?>) (Object) this;
            if (index < 0 || index >= self.size() || !(self.get(index) instanceof ItemStack current)) return;
            if (PlayerItemGuard.shouldBlock(owner, current, incoming, "inventory_list")) cir.setReturnValue(current);
        }
    }

    /** 持ち物の束そのものの書き換え（数・NBT・名前）の入口。敵対する MOD の攻撃の直後だけ確かめる（{@link PlayerItemGuard#shouldBlockStackEdit}） */
    @Mixin(ItemStack.class)
    public abstract static class Stack {
        @Inject(method = "setCount", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardCount(int count, CallbackInfo ci) {
            if (PlayerItemGuard.shouldBlockStackEdit((ItemStack) (Object) this, "stack_count")) ci.cancel();
        }

        @Inject(method = "setTag", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardTag(@Nullable net.minecraft.nbt.CompoundTag tag, CallbackInfo ci) {
            if (PlayerItemGuard.shouldBlockStackEdit((ItemStack) (Object) this, "stack_tag")) ci.cancel();
        }

        @Inject(method = "setHoverName", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardName(@Nullable net.minecraft.network.chat.Component name, CallbackInfoReturnable<ItemStack> cir) {
            if (PlayerItemGuard.shouldBlockStackEdit((ItemStack) (Object) this, "stack_name")) cir.setReturnValue((ItemStack) (Object) this);
        }
    }

    @Mixin(Inventory.class)
    public abstract static class Inv {
        @Shadow
        public Player player;

        @Shadow
        @Final
        public NonNullList<ItemStack> items;

        @Shadow
        @Final
        public NonNullList<ItemStack> armor;

        @Shadow
        @Final
        public NonNullList<ItemStack> offhand;

        /** 持ち物の一覧に持ち主の印を付ける（一覧への直接の書き込みを見張るため） */
        @Inject(method = "<init>", at = @At("TAIL"))
        private void automata_valkyries$tagLists(Player owner, CallbackInfo ci) {
            for (NonNullList<ItemStack> list : java.util.List.of(items, armor, offhand)) {
                if ((Object) list instanceof com.automatavalkyries.protect.OwnedItemList owned) owned.automata_valkyries$setOwner(owner);
            }
        }

        /** 持ち物を全部落とす（Madness Entities の倒す処理・状態異常） */
        @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardDropAll(CallbackInfo ci) {
            if (PlayerItemGuard.shouldBlockBulk(this.player, ((Inventory) (Object) this).isEmpty(), "dropAll")) ci.cancel();
        }

        /** 持ち物を全部消す */
        @Inject(method = "clearContent", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardClear(CallbackInfo ci) {
            if (PlayerItemGuard.shouldBlockBulk(this.player, ((Inventory) (Object) this).isEmpty(), "clearContent")) ci.cancel();
        }

        /** 持ち物から、その束を取り除く */
        @Inject(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardRemoveStack(ItemStack stack, CallbackInfo ci) {
            if (PlayerItemGuard.shouldBlockBulk(this.player, stack.isEmpty(), "removeItem")) ci.cancel();
        }

        /** 枠から数を取り除く */
        @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardRemoveCount(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
            Inventory self = (Inventory) (Object) this;
            if (slot < 0 || slot >= self.getContainerSize()) return;
            if (PlayerItemGuard.shouldBlockBulk(this.player, self.getItem(slot).isEmpty(), "removeItemCount")) cir.setReturnValue(ItemStack.EMPTY);
        }

        @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardSetItem(int index, ItemStack stack, CallbackInfo ci) {
            Inventory self = (Inventory) (Object) this;
            if (index < 0 || index >= self.getContainerSize()) return;
            if (PlayerItemGuard.shouldBlock(this.player, self.getItem(index), stack, "setItem")) ci.cancel();
        }
    }
}
