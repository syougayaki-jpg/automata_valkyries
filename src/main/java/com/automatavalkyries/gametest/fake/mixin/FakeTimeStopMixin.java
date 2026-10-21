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

    /**
     * 免除のある時間停止（EndingLibrary の LevelMixin.guardEntityTick の再現）: 停止の最中は、免除の状態が正でない個体の Tick を打ち切る。
     * 止めるのは範囲の印の付いた個体だけ
     */
    @Inject(method = "guardEntityTick", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void fakemod$stopUnlessExempt(Consumer<T> ticker, T entity, CallbackInfo ci) {
        if (!com.automatavalkyries.gametest.fake.FakeStopSubject.STOP_ACTIVE
                || !entity.getTags().contains(com.automatavalkyries.gametest.fake.FakeStopSubject.SCOPE_TAG)) return;
        if (entity instanceof com.automatavalkyries.gametest.fake.FakeStopSubject subject
                && subject.getEntityData().get(com.automatavalkyries.gametest.fake.FakeStopSubject.FAKE_TIME_STOP_COUNT) > 0) return;
        ci.cancel();
    }
}
