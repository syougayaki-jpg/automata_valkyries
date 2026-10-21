package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.EventBusGuardian;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** サーバーの Tick の先頭で、Forge のイベントに頼らない購読の点検を呼ぶ（{@link EventBusGuardian#poll}） */
@Mixin(MinecraftServer.class)
public abstract class EventBusPollMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void automata_valkyries$pollEventBus(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        EventBusGuardian.poll((MinecraftServer) (Object) this);
    }
}
