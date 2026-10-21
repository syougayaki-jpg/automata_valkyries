package com.automatavalkyries.protect;

/**
 * 他の Java エージェントの書き換えを丸ごと捨てる MOD への備え（2026-10-08 ユーザー指定「エージェント保護」）。
 * <p>
 * SuperSteve（PLZLiZi）は自分で attach したうえで JDK の {@code sun.instrument.InstrumentationImpl} を書き換え、
 * 自分以外のエージェントの変換結果を null にして捨てる。これでこのMOD のエージェント（LifeGuard・オートマタヴァルキリー protect・
 * 観察者の改ざん防止）の書き換えが効かなくなった（GameTest の lifeguardReportsTrueLife と integrityGuardRestoresOwnClasses が落ちた）。
 * SuperSteve 自身に、この乗っ取りをしない「安全モード」（システムプロパティ {@code supersteve.safemode=true}、MOD の作成時に読む）があるので、
 * どの MOD も作られる前（Mixin の読み込み、{@code mixin/AutomataMixinPlugin#onLoad}）に立てておく。
 * <p>
 * 安全モードの違いは、乗っ取りをしないことと、武器の登録名が {@code supersteve:endofplz_lite} から {@code supersteve:endoftwdr} に変わること
 * （以前のワールドにあった武器は読み込めなくなる）だけ。立てたくないときは {@code -Dautomata_valkyries.superSteveSafemode=false}
 */
public final class AgentHijackGuard {

    public static final String SUPERSTEVE_SAFEMODE = "supersteve.safemode";
    public static final String OPT_OUT = "automata_valkyries.superSteveSafemode";

    private AgentHijackGuard() {
    }

    /** どの MOD も作られる前に呼ぶ */
    public static void beforeModsLoad() {
        if ("false".equalsIgnoreCase(System.getProperty(OPT_OUT))) {
            System.out.println("[automata_valkyries] agent guard: SuperSteve safemode left off (" + OPT_OUT + "=false)");
            return;
        }
        if (System.getProperty(SUPERSTEVE_SAFEMODE) == null) {
            System.setProperty(SUPERSTEVE_SAFEMODE, "true");
            System.out.println("[automata_valkyries] agent guard: set " + SUPERSTEVE_SAFEMODE + "=true so SuperSteve does not discard other Java agents' transforms");
        }
    }

    /** SuperSteve が入っていて、安全モードで動いているか（入っていなければ null） */
    public static Boolean superSteveSafemode() {
        try {
            Class<?> mod = Class.forName("plz.lizi.supersteve.SuperSteveMod");
            return mod.getField("SAFEMODE").getBoolean(null);
        } catch (Throwable notLoaded) {
            return null;
        }
    }

    /**
     * SuperSteve の AgtCallback.INST を検査する。<b>検知だけで、解除はしない</b>（名前は昔のまま。呼び出し元は Antigravity の
     * EprotectSuperSteveCounter なので変えていない）。乗っ取りそのものは {@link #beforeModsLoad()} の安全モードで止めている
     *
     * @return AgtCallback.INST が入っている（＝安全モードでなければ、他のエージェントの変換結果を捨てる状態）か
     */
    public static boolean ensureAgtCallbackNeutralized() {
        try {
            Class<?> agtCallback = Class.forName("plz.lizi.supersteve.power.AgtCallback");
            java.lang.reflect.Field instField = agtCallback.getDeclaredField("INST");
            instField.setAccessible(true);
            Object inst = instField.get(null);
            if (inst != null) {
                // INST が設定されている場合、AgtCallback.afterTransform は SuperSteve 以外の
                // 全ての Java エージェントのバイトコード変換を強制破棄（null 返却）する。ここでは見つけたと返すだけ。
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}

