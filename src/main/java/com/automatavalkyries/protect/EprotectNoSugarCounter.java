package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

/**
 * No-Sugar（シュガーソード / 消しゴム系）の抹消（Erase）に対する特化カウンターハンドラー。
 * <p>
 * No-Sugar は hurt / die / remove を一切通らず、抹消フラグ（{@code erased} と静的な {@code erasedUuids}）を立てた上で
 * フィールドやワールドの一覧を直接書き換えて消す。さらに同梱の Java エージェントが全クラスの
 * {@code getHealth} / {@code isAlive} / {@code isDeadOrDying} / {@code isRemoved} の呼び出し箇所すべてに
 * 戻り値の上書きを差し込み、抹消済みなら「死亡・消去済み」を返させる（eprotect 自身の判定も騙される）。
 * 上書きも Tick 除外・追跡拒否もすべて抹消フラグを見ているので、フラグを戻すのが根本対策になる。
 * </p>
 */
public final class EprotectNoSugarCounter {

    private static boolean initialized = false;
    private static Class<?> iLivingEntityClass = null;
    private static Method isErasedMethod = null;
    private static Method setErasedMethod = null;
    private static Method unmarkErasedMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            iLivingEntityClass = Class.forName("com.test.nosugar.utils.interfaces.ILivingEntity");
            isErasedMethod = iLivingEntityClass.getMethod("isErased");
            setErasedMethod = iLivingEntityClass.getMethod("setErased", boolean.class);
            unmarkErasedMethod = iLivingEntityClass.getMethod("unmarkErased", java.util.UUID.class);
        } catch (Throwable ignored) {
            iLivingEntityClass = null;
        }
    }

    /** No-Sugar の抹消フラグを解除する。抹消されていた場合は true */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();
        if (iLivingEntityClass == null || !iLivingEntityClass.isInstance(entity)) return false;
        try {
            boolean erased = (Boolean) isErasedMethod.invoke(entity);
            if (erased) {
                setErasedMethod.invoke(entity, false);
                unmarkErasedMethod.invoke(entity, entity.getUUID());
            }
            return erased;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
