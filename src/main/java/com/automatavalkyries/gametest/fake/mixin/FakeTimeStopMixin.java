package com.automatavalkyries.gametest.fake.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * GameTest 用: 架空の MOD の時間停止（EndingLibrary の LevelMixin.guardEntityTick の再現）。
 * タグ {@link com.automatavalkyries.gametest.fake.FakeKillMod#TIME_STOP_TAG} の付いた個体の Tick を包みの入口で打ち切る
 */
@Mixin(Level.class)
public abstract class FakeTimeStopMixin {
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void fakemod$stopTime(Consumer<T> ticker, T entity, CallbackInfo ci) {
        if (entity.getTags().contains(com.automatavalkyries.gametest.fake.FakeKillMod.TIME_STOP_TAG)) ci.cancel();
    }
}
