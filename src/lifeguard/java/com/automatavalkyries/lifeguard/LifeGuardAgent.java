package com.automatavalkyries.lifeguard;

import com.automatavalkyries.agentcommon.AgentLog;
import com.automatavalkyries.agentcommon.OutermostKeeper;
import com.automatavalkyries.lifeguard.hooks.LifeHooks;

import java.lang.instrument.Instrumentation;

/**
 * LifeGuard（防御専用の Java エージェント）: 保護対象の生死の getter を、他のエージェント・ASM の偽装より外側で実際の値へ直す。
 * <p>
 * 他 MOD のエージェントは後から変換器を足して {@code LivingEntity} を再変換し、getHealth 等の戻り値を包む（Fantasy Ending）。
 * 他のエージェントの再変換を見たら、自分の変換器をいちばん最後へ並べ直して外側を取り戻す（{@link OutermostKeeper}）。
 * </p>
 * 書き換えるのは vanilla の LivingEntity だけ（他 MOD のクラスも、このMOD のクラスも書き換えない）。起動: {@code -javaagent:automata_valkyries-lifeguard.jar}。
 */
public final class LifeGuardAgent {

    private static LifeGuardAgent instance;

    private final LifeGuardTransformer transformer;
    private final OutermostKeeper keeper;
    private final AgentLog logger = new AgentLog("automata_valkyries-lifeguard.log", "lifeguard");

    private LifeGuardAgent(Instrumentation instrumentation) {
        this.transformer = new LifeGuardTransformer(this);
        this.keeper = new OutermostKeeper(instrumentation, transformer, LifeGuardTransformer::isTarget, logger, "LifeGuard-Reorder");
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
        instance = new LifeGuardAgent(inst);
        LifeHooks.ensureLast = instance.keeper::reorderNow;
        LifeHooks.status = instance::status;
        instance.log("started (retransform supported: " + inst.isRetransformClassesSupported() + ")");
        instance.keeper.install();
    }

    String status() {
        return "LifeGuard: transformed " + transformer.transformed.size() + " class(es), reordered " + keeper.reorders() + " time(s)";
    }

    void log(String message) {
        logger.log(message);
    }
}
