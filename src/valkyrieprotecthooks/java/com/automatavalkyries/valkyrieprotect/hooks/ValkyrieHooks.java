package com.automatavalkyries.valkyrieprotect.hooks;

/**
 * オートマタヴァルキリー protect のエージェント（{@code automata_valkyries-valkyrie-protect.jar}）が、vanilla の
 * {@code Entity} / {@code LivingEntity} の消去・即死の入口（remove / setRemoved / kill / die / setHealth）の先頭に差し込む呼び出し先。
 * <p>
 * ブートストラップのクラスパスに置く（エージェント jar の Boot-Class-Path）。どのクラスローダーのクラスからも見えるように JDK だけに依存する。
 * 判断（戦術兵姫・保護対象のボスなら断る・機能停止に置き換える）は MOD が {@link #install} で入れる。入っていなければ素通し。
 * </p>
 */
public final class ValkyrieHooks {

    /** MOD 側の判断。true を返すとその呼び出しをそこで打ち切る（元の処理をしない） */
    public interface Filter {
        /** @param op "remove" / "setRemoved" / "kill" / "die" / "setHealth"、arg はその引数（消去の理由・ダメージの源・HP） */
        boolean refuse(Object entity, String op, Object arg);
    }

    /** MOD が入れた判断（直接は書き換えられない。{@link #install} と {@link #current} 経由。MOD が毎秒、入っているのが自分のものか確かめる。2026-10-21） */
    private static volatile Filter filter;
    /** エージェントが入れる: 自分の変換を最後（いちばん外側）へ並べ直し、対象のクラスを再変換する */
    public static volatile Runnable ensureLast;
    /** エージェントが入れる: 状態の表示 */
    public static volatile java.util.function.Supplier<String> status;

    /** 判断が例外を出した回数（例外は握りつぶして素通しにする。起こり続けていないか MOD が見る。原子的には増やさない） */
    private static volatile long errors;

    private ValkyrieHooks() {}

    /** MOD が判断を入れる（入れ直しにも使う） */
    public static void install(Filter f) {
        filter = f;
    }

    /** 今入っている判断（MOD が自分のものか確かめる用） */
    public static Filter current() {
        return filter;
    }

    public static long errors() {
        return errors;
    }

    public static boolean refuse(Object entity, String op, Object arg) {
        Filter f = filter;
        if (f == null) return false;
        try {
            return f.refuse(entity, op, arg);
        } catch (Throwable t) {
            errors++;
            return false;
        }
    }

    /** setHealth 用（float を箱に入れる） */
    public static boolean refuseHealth(Object entity, float value) {
        Filter f = filter;
        if (f == null || value > 0.0F) return false; // 正の HP は見ない（毎回の箱詰めを避ける）
        return refuse(entity, "setHealth", value);
    }
}
