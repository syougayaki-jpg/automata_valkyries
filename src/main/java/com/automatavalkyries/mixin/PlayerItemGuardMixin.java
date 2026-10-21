package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.PlayerItemGuard;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Mixin(Inventory.class)
    public abstract static class Inv {
        @Shadow
        public Player player;

        @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$guardSetItem(int index, ItemStack stack, CallbackInfo ci) {
            Inventory self = (Inventory) (Object) this;
            if (index < 0 || index >= self.getContainerSize()) return;
            if (PlayerItemGuard.shouldBlock(this.player, self.getItem(index), stack, "setItem")) ci.cancel();
        }
    }
}
