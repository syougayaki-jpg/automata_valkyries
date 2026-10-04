package com.automatavalkyries.gametest.fake.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameTest 用: 架空の MOD が生死の getter の戻り値を包んで偽装する（Fantasy Ending のエージェントの包みの再現）。
 * タグ {@link com.automatavalkyries.gametest.fake.FakeKillMod#LIFE_SPOOF_TAG} の付いた個体だけ、HP を 1、isDeadOrDying を真と答えさせる。LifeGuard がいちばん外側で実際の値に直すことを確かめる
 */
@Mixin(LivingEntity.class)
public abstract class FakeLifeSpoofMixin {

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void fakemod$spoofHealth(CallbackInfoReturnable<Float> cir) {
        if (((LivingEntity) (Object) this).getTags().contains(com.automatavalkyries.gametest.fake.FakeKillMod.LIFE_SPOOF_TAG)) cir.setReturnValue(1.0F);
        else if (((LivingEntity) (Object) this).getTags().contains(com.automatavalkyries.gametest.fake.FakeKillMod.FULL_HEALTH_SPOOF_TAG))
            cir.setReturnValue(((LivingEntity) (Object) this).getMaxHealth());
    }

    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void fakemod$spoofDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if (((LivingEntity) (Object) this).getTags().contains(com.automatavalkyries.gametest.fake.FakeKillMod.LIFE_SPOOF_TAG)) cir.setReturnValue(true);
        else if (((LivingEntity) (Object) this).getTags().contains(com.automatavalkyries.gametest.fake.FakeKillMod.FULL_HEALTH_SPOOF_TAG)) cir.setReturnValue(false);
    }
}
