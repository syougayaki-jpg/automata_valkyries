package com.automatavalkyries.valkyrieprotect;

import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;

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
 * オートマタヴァルキリー protect の Java エージェント（2026-10-04 新設。戦術兵姫の保護だけ。docs/SPEC_VALKYRIE_GUI_AI.md §5）。
 * <p>
 * vanilla の {@code Entity} / {@code LivingEntity} の消去・即死の入口の先頭に {@link ValkyrieHooks} の問い合わせを差し込み、
 * 戦術兵姫なら（MOD の判断で）そこで打ち切る。Mixin や他のエージェントの差し込みより外側で効くよう、他のエージェントが同じクラスを
 * 再変換したのを見たら、自分の変換器をいちばん最後へ並べ直して再変換する（LifeGuard と同じやり方）。
 * </p>
 * ゲーム内の層（兵姫のクラス自身・Mixin・毎秒の見張りと復元）はエージェントが無くても動く。これはその外側の守り。
 * 起動: {@code -javaagent:automata_valkyries-valkyrie-protect.jar}（開発では {@code -PwithValkyrieProtect}）。
 */
public final class ValkyrieProtectAgent {

    private static final int MAX_REORDERS = 64;

    private static ValkyrieProtectAgent instance;

    private final Instrumentation instrumentation;
    private final ValkyrieProtectTransformer transformer;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ValkyrieProtect-Reorder");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reorderPending = new AtomicBoolean();
    private final AtomicInteger reorders = new AtomicInteger();
    private final Path logFile = Path.of("logs", "automata_valkyries-valkyrie-protect.log");

    private ValkyrieProtectAgent(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        this.transformer = new ValkyrieProtectTransformer(this);
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
        inst.addTransformer(instance.transformer, true);
        ValkyrieHooks.ensureLast = instance::reorderNow;
        ValkyrieHooks.status = instance::status;
        instance.log("started (retransform supported: " + inst.isRetransformClassesSupported() + ")");
        instance.retransformTargets(); // 後から読み込まれた（agentmain）ときは、もう読み込まれている対象を変換し直す
    }

    void scheduleReorder(String className) {
        if (reorders.get() >= MAX_REORDERS) return;
        if (!reorderPending.compareAndSet(false, true)) return;
        log("foreign retransform of " + className.replace('/', '.') + " seen; moving to the outermost position");
        executor.schedule(() -> {
            reorderPending.set(false);
            reorderNow();
        }, 500, TimeUnit.MILLISECONDS);
    }

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
            if (ValkyrieProtectTransformer.isTarget(name) && instrumentation.isModifiableClass(c)) targets.add(c);
        }
        if (targets.isEmpty()) return;
        ValkyrieProtectTransformer.SELF.set(true);
        try {
            instrumentation.retransformClasses(targets.toArray(new Class<?>[0]));
            log("retransformed " + targets.size() + " class(es)");
        } catch (UnmodifiableClassException | RuntimeException | LinkageError e) {
            log("retransform failed: " + e);
        } finally {
            ValkyrieProtectTransformer.SELF.set(false);
        }
    }

    String status() {
        return "Valkyrie protect agent: guarded " + transformer.transformed.size() + " class(es), reordered " + reorders.get() + " time(s)";
    }

    void log(String message) {
        String line = "[" + LocalTime.now().withNano(0) + "] [valkyrie-protect-agent] " + message;
        System.out.println(line);
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}
