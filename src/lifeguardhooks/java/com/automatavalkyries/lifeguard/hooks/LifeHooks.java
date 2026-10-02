package com.automatavalkyries.lifeguard.hooks;

/**
 * LifeGuard エージェントが vanilla の生死の getter（getHealth / isDeadOrDying / isAlive）の戻り際に差し込む呼び出し先。
 * <p>
 * ブートストラップのクラスパスに置く（エージェント jar の Boot-Class-Path）。どのクラスローダーのクラスからも見えるように、
 * JDK 以外に依存しない。中身の判断（保護対象なら実際の値へ直す）は MOD 側が {@link #filter} に入れる。入っていなければ素通し。
 * </p>
 */
public final class LifeHooks {

    /** MOD 側の判断。値を返すだけで、副作用で HP を書き換えない */
    public interface LifeFilter {
        float health(Object entity, float value);

        boolean deadOrDying(Object entity, boolean value);

        boolean alive(Object entity, boolean value);
    }

    /** MOD が入れる */
    public static volatile LifeFilter filter;
    /** エージェントが入れる: 自分の変換を最後（いちばん外側）へ並べ直し、対象のクラスを再変換する */
    public static volatile Runnable ensureLast;
    /** エージェントが入れる: 状態の表示 */
    public static volatile java.util.function.Supplier<String> status;

    private LifeHooks() {}

    public static float health(float value, Object entity) {
        LifeFilter f = filter;
        if (f == null) return value;
        try {
            return f.health(entity, value);
        } catch (Throwable t) {
            return value;
        }
    }

    public static boolean deadOrDying(boolean value, Object entity) {
        LifeFilter f = filter;
        if (f == null) return value;
        try {
            return f.deadOrDying(entity, value);
        } catch (Throwable t) {
            return value;
        }
    }

    public static boolean alive(boolean value, Object entity) {
        LifeFilter f = filter;
        if (f == null) return value;
        try {
            return f.alive(entity, value);
        } catch (Throwable t) {
            return value;
        }
    }
}
