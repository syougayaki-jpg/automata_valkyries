package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * The Trial Monolith の魂ダメージ（Soul Damage）に対する後始末。
 * <p>
 * 試練のモノリスのビーム・範囲攻撃・ダメージキューブは hurt を通らずに魂ダメージを加算する。
 * 1 以上で die() と AI 全消去、10 以上でフィールド・ワールドの一覧からの直接削除が走り、
 * さらに同 MOD の ASM が getHealth / isAlive / isRemoved / getRemovalReason を「死亡・消去済み」に書き換え続ける。
 * 書き換えは魂ダメージの値だけを見ているので、値を 0 に戻すのが根本対策になる。
 * </p>
 */
public final class EprotectTrialMonolithCounter {

    private static boolean initialized = false;
    private static Class<?> iSoulDamageClass = null;
    private static Method getSoulDamageMethod = null;
    private static Method setSoulDamageMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            iSoulDamageClass = Class.forName("io.github.kosianodangoo.trialmonolith.api.mixin.ISoulDamage");
            getSoulDamageMethod = iSoulDamageClass.getMethod("the_trial_monolith$getSoulDamage");
            setSoulDamageMethod = iSoulDamageClass.getMethod("the_trial_monolith$setSoulDamage", float.class);
        } catch (Throwable ignored) {
            iSoulDamageClass = null;
        }
    }

    /** 魂ダメージを 0 に戻す。溜まっていた場合は true */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();
        if (iSoulDamageClass == null || !iSoulDamageClass.isInstance(entity)) return false;
        try {
            float soulDamage = (Float) getSoulDamageMethod.invoke(entity);
            if (soulDamage == 0.0F) return false;
            setSoulDamageMethod.invoke(entity, 0.0F);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
