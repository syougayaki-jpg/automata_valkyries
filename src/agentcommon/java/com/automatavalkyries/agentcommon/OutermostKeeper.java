package com.automatavalkyries.agentcommon;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * 自分の変換器を、同じ {@code Instrumentation} の中でいちばん最後（いちばん外側で包む位置）に保つ（LifeGuard・オートマタヴァルキリー protect で共通）。
 * <p>
 * 変換器は足した順に回り、後から足された方が外側で包む。他のエージェントが対象のクラスを再変換したのを見たら（{@link #foreignRetransformSeen}）、
 * 少し待って（同じ時期の再変換をまとめる）自分の変換器を外して足し直し、対象のクラスを再変換する。このMOD 同士の再変換は数えない（{@link InHouse}）。
 * 回数には上限がある（他のエージェントと並べ直し合っても止まる）。
 * </p>
 * 別の {@code Instrumentation}（後から attach した他のエージェント）の変換器はさらに後に回るので、そちらに対しては外側を取れない。
 */
public final class OutermostKeeper {

    /**
     * 並べ直しの上限は起動からの通算ではなく時間窓ごと（2026-10-21）。通算 64 回だと、他のエージェントが 64 回再変換するだけで
     * 以後ずっと並べ直さなくなった（使い切らせる攻撃）。窓が明ければまた並べ直す
     */
    private static final int MAX_REORDERS_PER_WINDOW = 32;
    private static final long WINDOW_MS = 600_000L;
    private static final long DEBOUNCE_MS = 500;

    private final Instrumentation instrumentation;
    private final ClassFileTransformer transformer;
    private final Predicate<String> target;
    private final AgentLog log;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicInteger reorders = new AtomicInteger();
    /** 窓の中で並べ直した時刻 */
    private final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();
    private long limitNotedAt;
    /** 自分が再変換している最中か（その再変換は他のエージェントのものとして数えない） */
    private final ThreadLocal<Boolean> self = ThreadLocal.withInitial(() -> false);

    /**
     * @param target 再変換し直すクラス（内部名 a/b/C）
     */
    public OutermostKeeper(Instrumentation instrumentation, ClassFileTransformer transformer, Predicate<String> target, AgentLog log, String threadName) {
        this.instrumentation = instrumentation;
        this.transformer = transformer;
        this.target = target;
        this.log = log;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    /** 変換器を登録し、もう読み込まれている対象を変換し直す（後から attach されたとき用） */
    public void install() {
        instrumentation.addTransformer(transformer, true);
        retransformTargets();
    }

    /** 変換器から: このクラスの再変換が、他のエージェントによるものか（自分・このMOD のものでない） */
    public boolean isForeignRetransform(Class<?> classBeingRedefined) {
        return classBeingRedefined != null && !self.get() && !InHouse.retransformedByThisMod();
    }

    /** 変換器から: 他のエージェントの再変換を見た。少し待ってから並べ直す */
    public void foreignRetransformSeen(String className) {
        if (limited()) return;
        if (!pending.compareAndSet(false, true)) return;
        log.log("foreign retransform of " + className.replace('/', '.') + " seen; moving to the outermost position");
        executor.schedule(() -> {
            pending.set(false);
            reorderNow();
        }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /** 自分の変換器をいちばん最後へ並べ直し、対象のクラスを再変換する */
    public synchronized void reorderNow() {
        if (!takeSlot()) return;
        reorders.incrementAndGet();
        instrumentation.removeTransformer(transformer);
        instrumentation.addTransformer(transformer, true);
        retransformTargets();
    }

    /** 窓の中の回数が上限か（数えない） */
    private synchronized boolean limited() {
        expire(System.currentTimeMillis());
        return recent.size() >= MAX_REORDERS_PER_WINDOW;
    }

    /** 並べ直す枠を 1 つ取る。上限なら取れない（窓が明けるまで。最初の 1 回だけ記録する） */
    private synchronized boolean takeSlot() {
        long now = System.currentTimeMillis();
        expire(now);
        if (recent.size() >= MAX_REORDERS_PER_WINDOW) {
            if (now - limitNotedAt > WINDOW_MS) {
                limitNotedAt = now;
                log.log("reorder limit reached (" + MAX_REORDERS_PER_WINDOW + " in " + WINDOW_MS / 60_000 + " min); waiting for the window to pass");
            }
            return false;
        }
        recent.addLast(now);
        return true;
    }

    private void expire(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst() > WINDOW_MS) recent.pollFirst();
    }

    public int reorders() {
        return reorders.get();
    }

    private void retransformTargets() {
        List<Class<?>> targets = new ArrayList<>();
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            if (target.test(c.getName().replace('.', '/')) && instrumentation.isModifiableClass(c)) targets.add(c);
        }
        if (targets.isEmpty()) return;
        self.set(true);
        try {
            instrumentation.retransformClasses(targets.toArray(new Class<?>[0]));
            log.log("retransformed " + targets.size() + " class(es)");
        } catch (UnmodifiableClassException | RuntimeException | LinkageError e) {
            log.log("retransform failed: " + e);
        } finally {
            self.set(false);
        }
    }
}
