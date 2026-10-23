package com.automatavalkyries.mixin;

import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 終焉機構（{@link TerminusPlayerExecutor}）によって死亡確定・リスポーン待機中となったプレイヤー個体に対し、
 * Java エージェント無しでも他 MOD の Mixin（IMDS・ECA・No-Sugar 等の {@code getHealth()=20} 偽装や
 * {@code isDeadOrDying()=false} 偽装）より先に（{@code priority = 1}）死亡状態の戻り値を確定させる。
 * <p>
 * 個体インスタンス単位（および {@code terminus_pending_respawn}）で判定するため、
 * リスポーン時に新しく生成される {@code ServerPlayer} / {@code LocalPlayer} 個体の HP 初期化は一切妨げない。
 * </p>
 */
@Mixin(value = LivingEntity.class, priority = 1)
public abstract class TerminusDeadPlayerMixin {

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$terminusDeadGetHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player player && TerminusPlayerExecutor.isTerminusDeadOrDying(player)) {
            cir.setReturnValue(0.0F);
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$terminusDeadIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && TerminusPlayerExecutor.isTerminusDeadOrDying(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$terminusDeadIsAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && TerminusPlayerExecutor.isTerminusDeadOrDying(player)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float automata_valkyries$terminusDeadSetHealth(float health) {
        if ((Object) this instanceof Player player && TerminusPlayerExecutor.isTerminusDeadOrDying(player)) {
            return 0.0F;
        }
        return health;
    }
}
