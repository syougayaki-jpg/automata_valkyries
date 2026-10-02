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
            if (EprotectCompat.shieldAttack(entity, "witherzilla:forceRemove")) ci.cancel();
        }

        @Inject(method = "safeEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSafeEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (com.automatavalkyries.protect.SpecificCountermeasures.annihilatedForCompat(entity)) {
                cir.setReturnValue(false);
            }
        }

        // 注意: ModUtil.safeEntity（Witherzilla 自身のボスの保護判定）を兵姫にも true にしてはいけない。
        // Witherzilla の Mixin がチャンクのアンロード時の区画・一覧からの削除まで拒否するため、終了時の保存で
        // 同じ個体が何度も読み直される（UUID 重複の警告が大量に出る）。
    }

    /** 処刑竜の生命維持フラグを抹消時に強制無効化 */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.entity.ExecutionDragonEntity", remap = false)
    public abstract static class ExecutionDragon {
        @Inject(method = "getWitherzillaLife()Z", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$overrideDragonLife(CallbackInfoReturnable<Boolean> cir) {
            Entity self = (Entity) (Object) this;
            if (com.automatavalkyries.protect.SpecificCountermeasures.annihilatedForCompat(self)) {
                cir.setReturnValue(false);
            }
        }
    }

    /** Witherzilla の生命維持フラグを抹消時に強制無効化 */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.entity.WitherzillaEntity", remap = false)
    public abstract static class Witherzilla {
        @Inject(method = "getWitherzillaLife()Z", at = @At("HEAD"), cancellable = true, require = 0)
        private void automata_valkyries$overrideWitherzillaLife(CallbackInfoReturnable<Boolean> cir) {
            Entity self = (Entity) (Object) this;
            if (com.automatavalkyries.protect.SpecificCountermeasures.annihilatedForCompat(self)) {
                cir.setReturnValue(false);
            }
        }
    }

    /** 範囲攻撃の後に予約される時間差の強制消去 */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.util.EntityTimerCache", remap = false)
    public abstract static class TimerCache {
        @Inject(method = {"addDeathEntity(Lnet/minecraft/world/entity/Entity;)V", "scheduleRemoval(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSchedule(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shieldAttack(entity, "witherzilla:schedule")) ci.cancel();
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

    /**
     * Witherzilla 自身の不具合の回避（クライアント）: {@code EventHandle.onRenderGUI} は {@code CopyOnWriteArrayList} の
     * {@code livingEntities} を回しながら {@code Iterator.remove()} を呼ぶが、CopyOnWriteArrayList の反復子は remove できず
     * {@code UnsupportedOperationException} で落ちる（死んだ・消えた個体が一覧に入ると、ワールドから出るときなどに落ちた。2026-10-02）。
     * 反復子だけを「remove で一覧そのものから消す」物に差し替える。回る順と中身・Witherzilla の判定はそのまま
     */
    @Pseudo
    @Mixin(targets = "com.wzz.witherzilla.event.EventHandle", remap = false)
    public abstract static class RenderGui {
        @org.spongepowered.asm.mixin.injection.Redirect(method = "onRenderGUI",
                at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CopyOnWriteArrayList;iterator()Ljava/util/Iterator;"), require = 0)
        private static <E> java.util.Iterator<E> automata_valkyries$removableIterator(java.util.concurrent.CopyOnWriteArrayList<E> list) {
            java.util.Iterator<E> snapshot = list.iterator();
            return new java.util.Iterator<>() {
                private E last;
                private boolean canRemove;

                @Override public boolean hasNext() { return snapshot.hasNext(); }

                @Override
                public E next() {
                    last = snapshot.next();
                    canRemove = true;
                    return last;
                }

                @Override
                public void remove() {
                    if (!canRemove) throw new IllegalStateException();
                    canRemove = false;
                    list.remove(last);
                }
            };
        }
    }
}
