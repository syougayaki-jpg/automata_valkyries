package com.automatavalkyries.mixin;

import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.PlayerWard;
import com.automatavalkyries.protect.attack.AttackOp;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 守護の護符（{@link PlayerWard}）: 他 MOD による、生きているプレイヤーの消去と、遠くへの強制移動・ワールド外への飛ばしを拒否する。
 * vanilla の消去（リスポーン時の古い個体の破棄・ログアウト・次元移動）や移動は他 MOD の経路を通らないので通る。保護の中心の Mixin とは別に見る
 */
@Mixin(Entity.class)
public abstract class PlayerWardEntityMixin {

    @Unique
    private boolean automata_valkyries$wardRefusesRemoval(Entity.RemovalReason reason) {
        Entity self = (Entity) (Object) this;
        return self instanceof Player && reason.shouldDestroy() && PlayerWard.isActive(self)
                && PlayerWard.refuseFromForeignMod(self, AttackOp.REMOVE + ":" + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(reason)) ci.cancel();
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(reason)) ci.cancel();
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardDiscard(CallbackInfo ci) {
        if (automata_valkyries$wardRefusesRemoval(Entity.RemovalReason.DISCARDED)) ci.cancel();
    }

    /** 遠くへの強制移動・ワールド外への飛ばし（ふつうの移動は短いので、距離で先に弾いてから呼び出し経路を見る） */
    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardSetPosRaw(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player) || !PlayerWard.isActive(self)) return;
        boolean unsafe = !Eprotect.isSafePosition(self, x, y, z);
        double limit = EprotectConfig.get(EprotectConfig.BLACKLIST_FORCED_RELOCATION_DISTANCE);
        if ((unsafe || self.distanceToSqr(x, y, z) > limit * limit)
                && PlayerWard.refuseFromForeignMod(self, unsafe ? AttackOp.UNSAFE_POSITION : AttackOp.FORCED_RELOCATION)) {
            ci.cancel();
        }
    }
}
