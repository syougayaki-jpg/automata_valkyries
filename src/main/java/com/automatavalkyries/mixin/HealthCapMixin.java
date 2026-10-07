package com.automatavalkyries.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 最大 HP（generic.max_health）の上限をバニラの 1,024 から {@link #MAX_HEALTH_CAP} へ上げる
 * （2026-10-07 ユーザー指定「HP の属性の上限を突破する、1 で」。AEC 級ボス ルクシオンの HP 5,000 などのため）。
 * 他の属性には触らない。1,024 を超える最大 HP を設定しない限り、何も変わらない（AttributeFix と同じ考え方。一緒に入っていても上限を上げ合うだけ）
 */
@Mixin(RangedAttribute.class)
public abstract class HealthCapMixin {

    private static final double MAX_HEALTH_CAP = 1_000_000.0;
    private static final String MAX_HEALTH_ID = "attribute.name.generic.max_health";

    @Shadow @Final private double minValue;

    private boolean automata_valkyries$isMaxHealth() {
        return MAX_HEALTH_ID.equals(((Attribute) (Object) this).getDescriptionId());
    }

    @Inject(method = "getMaxValue", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$raiseMax(CallbackInfoReturnable<Double> cir) {
        if (automata_valkyries$isMaxHealth()) cir.setReturnValue(MAX_HEALTH_CAP);
    }

    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$sanitize(double value, CallbackInfoReturnable<Double> cir) {
        if (automata_valkyries$isMaxHealth()) cir.setReturnValue(Double.isNaN(value) ? minValue : Mth.clamp(value, minValue, MAX_HEALTH_CAP));
    }
}
