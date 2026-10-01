package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * No-Sugar（シュガーソード等）の抹消。hurt / die / remove を一切通らずにフィールドとワールドの一覧を直接書き換えるうえ、
 * 同梱の Java エージェントが抹消済み個体の getHealth / isAlive / isRemoved を全呼び出し箇所で上書きするため、入口で止める。
 */
public final class NoSugarMixins {

    private NoSugarMixins() {}

    /** 剣の左クリック・振り・範囲攻撃・レイキャストのパケットが共通で通る入口 */
    @Pseudo
    @Mixin(targets = "com.test.nosugar.utils.item.Eraser_Utils", remap = false)
    public abstract static class EraserUtils {
        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;D)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard1(Entity self, Entity attacker, double radius, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZ)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard2(Entity self, Entity attacker, double radius, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard3(Entity self, Entity attacker, double radius, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Z)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard4(LivingEntity self, LivingEntity attacker, boolean skip, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard5(LivingEntity self, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }

        @Inject(method = "killIfParentFound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;DZLnet/minecraft/world/damagesource/DamageSource;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guard6(Entity self, Entity attacker, double radius, boolean skip, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shieldAttack(self, "nosugar:killIfParentFound")) cir.setReturnValue(false);
        }
    }

    /**
     * No-Sugar が LivingEntity に合成するメソッド（instantKill / forceErase / setErased / markErased / eraseClientEntity）。
     * No-Sugar の Mixin（priority 0）より後に適用される必要があるので priority を上げる。
     * クリエイティブソードや凝視の呪文など Eraser_Utils を通らない経路もここで止まる。
     */
    @Mixin(value = LivingEntity.class, priority = 100000)
    public abstract static class Living {
        @Inject(method = "instantKill(Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardInstantKill(LivingEntity attacker, boolean skip, DamageSource src, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack((Entity) (Object) this, "nosugar:instantKill")) ci.cancel();
        }

        @Inject(method = {"instantKill()V", "instantKill(Lnet/minecraft/world/damagesource/DamageSource;)V", "forceErase()V", "eraseClientEntity()V"},
                at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardErase(CallbackInfo ci) {
            if (EprotectCompat.shieldAttack((Entity) (Object) this, "nosugar:erase")) ci.cancel();
        }

        @Inject(method = "setErased(Z)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardSetErased(boolean flag, CallbackInfo ci) {
            if (flag && EprotectCompat.shieldAttack((Entity) (Object) this, "nosugar:setErased")) ci.cancel();
        }

        @Inject(method = "markErased(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
        private void automata_valkyries$guardMarkErased(UUID uuid, CallbackInfo ci) {
            Entity self = (Entity) (Object) this;
            if (self.getUUID().equals(uuid) && EprotectCompat.shieldAttack(self, "nosugar:markErased")) ci.cancel();
        }
    }

    /**
     * ストップウォッチ（時間停止）。No-Sugar は止めた本人以外の Tick・AI・描画を CanMove で止める。保護中の兵姫は止まらない側に入れる。
     * ワールドの Tick（ServerLevel.tick）自体も止められるが、そちらは準汎用の層（Eprotect の Tick 停止対策）が本体の Tick を代わりに回す。
     */
    @Pseudo
    @Mixin(targets = "com.test.nosugar.utils.TimeStopManager", remap = false)
    public abstract static class TimeStop {
        @Inject(method = "CanMove(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$protectedCanMove(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(true);
        }
    }

    /**
     * No-Sugar の不具合の修正（保護とは関係なく常に効かせる）。クライアントの毎 Tick（ClientEvents.onClientTick）が手持ちの
     * アイテムが何であれ DestroyMode.getMode を呼び、そこで getOrCreateTag するので、持っただけで空のタグ {} が付く。
     * 読むだけのメソッドではタグを作らず、無ければ空のタグを渡す（戻り値は元と同じ）。
     */
    @Pseudo
    @Mixin(targets = "com.test.nosugar.utils.DestroyMode", remap = false)
    public abstract static class DestroyModeTag {
        @Redirect(method = {"getMode(Lnet/minecraft/world/item/ItemStack;)Lcom/test/nosugar/utils/DestroyMode;",
                            "isSilkTouchEnabled(Lnet/minecraft/world/item/ItemStack;)Z"},
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;", remap = true),
                require = 0)
        private static CompoundTag automata_valkyries$readTagOnly(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag != null ? tag : new CompoundTag();
        }
    }

    /**
     * ストップウォッチ（時間停止）の所持判定。No-Sugar は Inventory.contains(new ItemStack(STOP_WATCH)) でタグまで比べるので、
     * 上の不具合で {} が付いたストップウォッチ（ワールドに保存済みのものも）を持っていないことにされる。アイテムの種類だけで見る。
     */
    @Pseudo
    @Mixin(targets = "com.test.nosugar.network.packets.TimeStopPacket", remap = false)
    public abstract static class StopWatchCheck {
        @Redirect(method = "lambda$handle$0(Lnet/minecraftforge/network/NetworkEvent$Context;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;contains(Lnet/minecraft/world/item/ItemStack;)Z", remap = true),
                require = 0)
        private static boolean automata_valkyries$containsIgnoringTag(Inventory inventory, ItemStack wanted) {
            Item item = wanted.getItem();
            return inventory.hasAnyMatching(stack -> stack.is(item));
        }
    }
}
