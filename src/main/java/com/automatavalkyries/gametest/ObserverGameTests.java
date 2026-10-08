package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.ObserverLink;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 観察用 Java エージェント（-PwithObserver）の GameTest。エージェントが付いていなければ何もせず成功する。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ObserverGameTests {

    /**
     * SuperSteve（-PwithSuperSteve）が入っていれば、安全モード（他のエージェントの書き換えを捨てない）で動いている（protect/AgentHijackGuard。2026-10-08）。
     * 入っていなければ何もせず成功する
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void superSteveRunsInSafemode(GameTestHelper helper) {
        Boolean safe = com.automatavalkyries.protect.AgentHijackGuard.superSteveSafemode();
        helper.assertTrue(safe == null || safe, "SuperSteve is not in safemode, so it discards other Java agents' transforms");
        helper.succeed();
    }

    /**
     * 改ざん防止（IntegrityGuard）: 他のエージェントがこのMOD のクラスを潰しても、最後に回り直して元の形に戻す。
     * 点検用のクラス（GuardProbe）の value() を潰す変換器を改ざん防止より後に入れ、潰れたこと → 戻ったことを見る。
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void integrityGuardRestoresOwnClasses(GameTestHelper helper) {
        if (!ObserverLink.active() || !"true".equals(ObserverLink.status().get("guard"))) {
            helper.succeed();
            return;
        }
        helper.assertTrue(GuardProbe.value() == 42, "The probe was already broken (" + GuardProbe.value() + ")");
        String started = ObserverLink.startGuardSelfTest(GuardProbe.class);
        int broken = GuardProbe.value();
        // 改ざん防止は観察のスレッド（1 秒ごと）で最後に回り直して戻す
        helper.runAfterDelay(60, () -> {
            int restored = GuardProbe.value();
            ObserverLink.endGuardSelfTest();
            helper.assertTrue("started".equals(started), "The self-test did not start: " + started);
            helper.assertTrue(broken == 0, "The hostile rewrite did not take effect (" + broken + ")");
            helper.assertTrue(restored == 42, "The guard did not restore the class (" + restored + ")");
            helper.succeed();
        });
    }
}
