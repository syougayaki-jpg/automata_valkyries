package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.PlayerWard;
import com.automatavalkyries.protect.attack.AttackOp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.common.util.ITeleporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 守護の護符: 他 MOD によるプレイヤーの別の次元への移動を入口で拒否する（ServerPlayer は Entity の次元移動・teleportTo を上書きしている）。
 * 同じ次元の中の移動は {@link PlayerWardEntityMixin} の setPosRaw で見る。ポータル・/tp・リスポーン等の vanilla の経路は通す
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerWardMixin {

    /**
     * 死亡処理: ServerPlayer は LivingEntity.die を上書きし、最初に死亡のイベントを出すので、LivingEntity 側の入口では止められない
     * （2026-10-04 修正: 護符を持っていても止まっていなかった）。ここで止める
     */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardPlayerDie(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!PlayerWard.refusesDeath(self)) return;
        com.automatavalkyries.protect.attack.AttackLog.refused(self, AttackOp.DIE);
        ci.cancel();
    }

    @Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void automata_valkyries$wardChangeDimension(ServerLevel destination, ITeleporter teleporter, CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (PlayerWard.isActive(self) && PlayerWard.refuseFromForeignMod(self, AttackOp.DIMENSION_CHANGE)) cir.setReturnValue(null);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardTeleportTo(ServerLevel level, double x, double y, double z, float yRot, float xRot, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (level != self.level() && PlayerWard.isActive(self) && PlayerWard.refuseFromForeignMod(self, AttackOp.DIMENSION_CHANGE)) ci.cancel();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$wardTeleportToRelative(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative,
                                                           float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (level != self.level() && PlayerWard.isActive(self) && PlayerWard.refuseFromForeignMod(self, AttackOp.DIMENSION_CHANGE)) cir.setReturnValue(false);
    }
}
