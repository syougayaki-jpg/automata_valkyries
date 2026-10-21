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

    /** MOD が入れた判断（直接は書き換えられない。{@link #install} と {@link #current} 経由。MOD が毎秒、入っているのが自分のものか確かめる。2026-10-21） */
    private static volatile LifeFilter filter;
    /** エージェントが入れる: 自分の変換を最後（いちばん外側）へ並べ直し、対象のクラスを再変換する */
    public static volatile Runnable ensureLast;
    /** エージェントが入れる: 状態の表示 */
    public static volatile java.util.function.Supplier<String> status;

    private LifeHooks() {}

    /** MOD が判断を入れる（入れ直しにも使う） */
    public static void install(LifeFilter f) {
        filter = f;
    }

    /** 今入っている判断（MOD が自分のものか確かめる用） */
    public static LifeFilter current() {
        return filter;
    }

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
