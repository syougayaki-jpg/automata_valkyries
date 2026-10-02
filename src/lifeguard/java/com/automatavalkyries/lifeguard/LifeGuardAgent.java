package com.automatavalkyries.lifeguard;

import com.automatavalkyries.lifeguard.hooks.LifeHooks;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LifeGuard（防御専用の Java エージェント）: 保護対象の生死の getter を、他のエージェント・ASM の偽装より外側で実際の値へ直す。
 * <p>
 * 他 MOD のエージェントは後から変換器を足して {@code LivingEntity} を再変換し、getHealth 等の戻り値を包む（Fantasy Ending）。
 * 変換器は足した順に回るので、後から足された方が外側になる。他のエージェントの再変換を見たら、自分の変換器を外して足し直し
 * （いちばん最後へ）、対象のクラスを再変換して外側を取り戻す。
 * </p>
 * 書き換えるのは vanilla の LivingEntity だけ（他 MOD のクラスも、このMOD のクラスも書き換えない）。起動: {@code -javaagent:automata_valkyries-lifeguard.jar}。
 */
public final class LifeGuardAgent {

    private static final int MAX_REORDERS = 64;

    private static LifeGuardAgent instance;

    private final Instrumentation instrumentation;
    private final LifeGuardTransformer transformer;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LifeGuard-Reorder");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reorderPending = new AtomicBoolean();
    private final AtomicInteger reorders = new AtomicInteger();
    private final Path logFile = Path.of("logs", "automata_valkyries-lifeguard.log");

    private LifeGuardAgent(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        this.transformer = new LifeGuardTransformer(this);
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
        inst.addTransformer(instance.transformer, true);
        LifeHooks.ensureLast = instance::reorderNow;
        LifeHooks.status = instance::status;
        instance.log("started (retransform supported: " + inst.isRetransformClassesSupported() + ")");
        // 後から読み込まれた（agentmain）ときは、もう読み込まれている対象を変換し直す
        instance.retransformTargets();
    }

    /** 他のエージェントの再変換を見たとき: 少し待ってから並べ直す（同じ時期の再変換をまとめる） */
    void scheduleReorder(String className) {
        if (reorders.get() >= MAX_REORDERS) return;
        if (!reorderPending.compareAndSet(false, true)) return;
        log("foreign retransform of " + className.replace('/', '.') + " seen; moving to the outermost position");
        executor.schedule(() -> {
            reorderPending.set(false);
            reorderNow();
        }, 500, TimeUnit.MILLISECONDS);
    }

    /** 自分の変換器をいちばん最後へ並べ直し、対象のクラスを再変換する */
    synchronized void reorderNow() {
        if (reorders.incrementAndGet() > MAX_REORDERS) return;
        instrumentation.removeTransformer(transformer);
        instrumentation.addTransformer(transformer, true);
        retransformTargets();
    }

    private void retransformTargets() {
        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            String name = c.getName().replace('.', '/');
            if ((LifeGuardTransformer.isTarget(name) && (transformer.transformed.contains(name) || name.equals("net/minecraft/world/entity/LivingEntity")))
                    && instrumentation.isModifiableClass(c)) {
                targets.add(c);
            }
        }
        if (targets.isEmpty()) return;
        LifeGuardTransformer.SELF.set(true);
        try {
            instrumentation.retransformClasses(targets.toArray(new Class<?>[0]));
            log("retransformed " + targets.size() + " class(es)");
        } catch (UnmodifiableClassException | RuntimeException | LinkageError e) {
            log("retransform failed: " + e);
        } finally {
            LifeGuardTransformer.SELF.set(false);
        }
    }

    String status() {
        return "LifeGuard: transformed " + transformer.transformed.size() + " class(es), reordered " + reorders.get() + " time(s)";
    }

    void log(String message) {
        String line = "[" + LocalTime.now().withNano(0) + "] [lifeguard] " + message;
        System.out.println(line);
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}
