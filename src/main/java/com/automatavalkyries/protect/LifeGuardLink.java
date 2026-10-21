package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;

/**
 * LifeGuard エージェント（{@code -PwithLifeGuard}、{@code automata_valkyries-lifeguard.jar}）との接続。エージェントが無ければ何もしない。
 * <p>
 * エージェントの呼び出し先（{@code LifeHooks}）はブートストラップのクラスパスにある。このクラスはそれを直接参照しない
 * （参照すると、エージェントが無い環境でこのクラスの読み込みが失敗する）。触るのは有ると確かめた後の {@link LifeGuardInstaller} だけ。
 * </p>
 */
public final class LifeGuardLink {

    private static final String HOOKS = "com.automatavalkyries.lifeguard.hooks.LifeHooks";
    private static volatile boolean installed;

    private LifeGuardLink() {}

    public static boolean isAgentPresent() {
        try {
            Class.forName(HOOKS, false, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 判断（{@link TrueLife}）を入れる。MOD の初期化のとき */
    public static synchronized void install() {
        if (installed || !isAgentPresent()) return;
        try {
            LifeGuardInstaller.install();
            installed = true;
            AutomataValkyries.LOGGER.info("[eprotect] LifeGuard agent found; protected entities' life getters now report their true values");
            ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[eprotect] Could not connect to the LifeGuard agent: {}", t.toString());
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** 自分の変換をいちばん外側へ並べ直させる（他のエージェントが後から変換器を足した後。ワールドの開始時など） */
    public static void ensureLast() {
        if (!installed) return;
        try {
            LifeGuardInstaller.ensureLast();
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] LifeGuard reorder failed: {}", t.toString());
        }
    }

    private static long lastErrors;
    private static long lastCanaryAction;
    /** 外側に通っていないと分かってから、また並べ直しを頼むまでの間隔 */
    private static final long CANARY_RETRY_MS = 30_000L;
    private static final java.util.Map<Class<?>, Boolean> CANARY_OK = new java.util.concurrent.ConcurrentHashMap<>();

    public static void verifyHook() {
        verifyHook(null);
    }

    /**
     * 点検（サーバーの周期処理から 1 秒ごと。2026-10-21）:
     * <ol>
     *   <li>入っている判断が自分のものか。違えば入れ直して攻撃の記録に残す（他の MOD が呼び出し先の判断を外す・差し替えると黙って守りが止まるため）</li>
     *   <li>判断が例外を出し続けていないか（例外は握りつぶして元の値を返すので、気づけない）</li>
     *   <li>差し込みが実際に通っているか: 生死の getter を 1 回呼んで、フックの呼び出し回数が増えるか見る。増えなければ他のエージェントに
     *       変換を捨てられた・上書きされたということなので、記録して並べ直しを頼む（30 秒に 1 回まで）</li>
     * </ol>
     *
     * @param canary 呼んで確かめる個体（getHealth を LivingEntity のまま使っているもの。無ければ 3 は飛ばす）
     */
    public static void verifyHook(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity canary) {
        if (!installed) return;
        try {
            if (!LifeGuardInstaller.intact()) {
                LifeGuardInstaller.install();
                com.automatavalkyries.protect.attack.AttackLog.detected(null, "agent_hook_tampered:lifeguard");
                AutomataValkyries.LOGGER.warn("[eprotect] The LifeGuard hook was replaced or removed by something else; put it back");
            }
            long errors = LifeGuardInstaller.errors();
            if (errors > lastErrors) {
                lastErrors = errors;
                com.automatavalkyries.protect.attack.AttackLog.detected(null, "agent_hook_errors:lifeguard");
                AutomataValkyries.LOGGER.warn("[eprotect] The LifeGuard hook threw {} exception(s) so far (swallowed; the original value was used)", errors);
            }
            if (canary != null && !canary.level().isClientSide && usesVanillaGetHealth(canary.getClass())) {
                long before = LifeGuardInstaller.calls();
                canary.getHealth();
                if (LifeGuardInstaller.calls() == before) {
                    long now = System.currentTimeMillis();
                    if (now - lastCanaryAction >= CANARY_RETRY_MS) {
                        lastCanaryAction = now;
                        com.automatavalkyries.protect.attack.AttackLog.detected(null, "agent_not_in_path:lifeguard");
                        AutomataValkyries.LOGGER.warn("[eprotect] LifeGuard's hook is not reached by getHealth (another agent dropped or overwrote the transform?); asking it to move to the outermost position");
                        ensureLast();
                    }
                }
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] LifeGuard hook check failed: {}", t.toString());
        }
    }

    /** getHealth を LivingEntity の実装のまま使っているか（上書きしていれば、そこで呼んでもフックは通らないので確かめに使えない） */
    private static boolean usesVanillaGetHealth(Class<?> type) {
        return CANARY_OK.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null && c != net.minecraft.world.entity.LivingEntity.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && (m.getName().equals("getHealth") || m.getName().equals("m_21223_"))) return false;
                }
            }
            return true;
        });
    }

    public static String status() {
        if (!installed) return "LifeGuard: not loaded";
        try {
            return LifeGuardInstaller.status();
        } catch (Throwable t) {
            return "LifeGuard: " + t;
        }
    }
}
