package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.EprotectCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Witherzilla の強制消去。{@code ModUtil.forceRemoveEntity} は removalReason の直書き・Tick 一覧／エンティティ一覧／区画からの直接削除・
 * クライアントへの消去パケットをまとめて行う（ChunkMap の追跡だけは残すので、復元で addFreshEntity すると「already tracked」で落ちる）。
 * 範囲攻撃（処刑竜・Witherzilla）は HP を直書きしたうえで EntityTimerCache に登録し、約 20 Tick 後から毎 Tick forceRemove する。
 */
public final class WitherzillaMixins {

    private WitherzillaMixins() {}

    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.util.ModUtil", remap = false)
    public abstract static class Util {
        /** 剣（ultima_blade_god）・範囲攻撃・クライアント側の消去パケットが共通で通る */
        @Inject(method = "forceRemoveEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardForceRemove(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        // 注意: ModUtil.safeEntity（Witherzilla 自身のボスの保護判定）を兵姫にも true にしてはいけない。
        // Witherzilla の Mixin がチャンクのアンロード時の区画・一覧からの削除まで拒否するため、終了時の保存で
        // 同じ個体が何度も読み直される（UUID 重複の警告が大量に出る）。
    }

    /** 範囲攻撃の後に予約される時間差の強制消去 */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.util.EntityTimerCache", remap = false)
    public abstract static class TimerCache {
        @Inject(method = {"addDeathEntity(Lnet/minecraft/world/entity/Entity;)V", "scheduleRemoval(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSchedule(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"isDeathEntity(Lnet/minecraft/world/entity/Entity;)Z", "tickAndCheck(Lnet/minecraft/world/entity/Entity;)Z"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCheck(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /**
     * 処刑竜の出現時に、全 EntityType のクラスを Unsafe で差し替える（保存されない・生成できない型にする）。
     * このMODの EntityType だけは差し替えさせない。
     */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.util.UnsafeUtil", remap = false)
    public abstract static class Unsafe {
        @Inject(method = "redefineObjectClass(Ljava/lang/Object;Ljava/lang/Class;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRedefine(Object object, Class<?> clazz, CallbackInfo ci) {
            if (object instanceof EntityType<?> type) {
                ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (key != null && AutomataValkyries.MODID.equals(key.getNamespace())) ci.cancel();
            }
        }
    }
}
