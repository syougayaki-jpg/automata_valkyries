package com.automatavalkyries.valkyrieprotect;

import com.automatavalkyries.agentcommon.AgentLog;
import com.automatavalkyries.agentcommon.OutermostKeeper;
import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;

import java.lang.instrument.Instrumentation;

/**
 * オートマタヴァルキリー protect の Java エージェント（2026-10-04 新設。戦術兵姫の保護だけ。docs/SPEC_VALKYRIE_GUI_AI.md §5）。
 * <p>
 * vanilla の {@code Entity} / {@code LivingEntity} の消去・即死の入口の先頭に {@link ValkyrieHooks} の問い合わせを差し込み、
 * 戦術兵姫なら（MOD の判断で）そこで打ち切る。Mixin や他のエージェントの差し込みより外側で効くよう、他のエージェントが同じクラスを
 * 再変換したのを見たら、自分の変換器をいちばん最後へ並べ直して再変換する（{@link OutermostKeeper}。LifeGuard と同じ）。
 * </p>
 * ゲーム内の層（兵姫のクラス自身・Mixin・毎秒の見張りと復元）はエージェントが無くても動く。これはその外側の守り。
 * 起動: {@code -javaagent:automata_valkyries-valkyrie-protect.jar}（開発では {@code -PwithValkyrieProtect}）。
 */
public final class ValkyrieProtectAgent {

    private static ValkyrieProtectAgent instance;

    private final ValkyrieProtectTransformer transformer;
    private final OutermostKeeper keeper;
    private final AgentLog logger = new AgentLog("automata_valkyries-valkyrie-protect.log", "valkyrie-protect-agent");

    private ValkyrieProtectAgent(Instrumentation instrumentation) {
        this.transformer = new ValkyrieProtectTransformer(this);
        this.keeper = new OutermostKeeper(instrumentation, transformer, ValkyrieProtectTransformer::isTarget, logger, "ValkyrieProtect-Reorder");
        transformer.keeper = keeper;
    }

    public static void premain(String args, Instrumentation inst) {
        start(inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst);
    }

    private static synchronized void start(Instrumentation inst) {
        if (instance != null) return;
        instance = new ValkyrieProtectAgent(inst);
        ValkyrieHooks.ensureLast = instance.keeper::reorderNow;
        ValkyrieHooks.status = instance::status;
        instance.log("started (retransform supported: " + inst.isRetransformClassesSupported() + ")");
        instance.keeper.install(); // 後から読み込まれた（agentmain）ときは、もう読み込まれている対象を変換し直す
    }

    String status() {
        return "Valkyrie protect agent: guarded " + transformer.transformed.size() + " class(es), reordered " + keeper.reorders() + " time(s)";
    }

    void log(String message) {
        logger.log(message);
    }
}
