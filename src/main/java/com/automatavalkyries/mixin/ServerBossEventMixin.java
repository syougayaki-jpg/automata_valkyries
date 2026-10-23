package com.automatavalkyries.mixin;

import com.automatavalkyries.world.BossBarJanitor;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ボスバーにプレイヤーが足されたら、後片付けの見張りに知らせる（{@link BossBarJanitor}） */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {
    @Inject(method = "addPlayer", at = @At("HEAD"))
    private void automata_valkyries$track(ServerPlayer player, CallbackInfo ci) {
        BossBarJanitor.track((ServerBossEvent) (Object) this);
    }
}
