package com.automatavalkyries.eprotectplus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared limit for bytecode replacements made by the Eprotect+ and flag agents. */
public final class AgentCollisionPolicy {
    private static final String LIMIT_PROPERTY = "automata_valkyries.agentCollisionLimit";
    private static final int DEFAULT_LIMIT = 16;
    private static final long QUIET_NS = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_THROTTLE_CYCLES = 2;
    private static final int LIMIT = readLimit();
    private static final AtomicInteger replacements = new AtomicInteger();
    private static final AtomicInteger windowReplacements = new AtomicInteger();
    private static final AtomicInteger throttleCycles = new AtomicInteger();
    private static volatile long lastCollisionAttemptNs = System.nanoTime();
    private static final AtomicBoolean configuredPassive = new AtomicBoolean();
    private static final AtomicBoolean permanentPassive = new AtomicBoolean();
    private static final AtomicBoolean windowThrottled = new AtomicBoolean();

    private AgentCollisionPolicy() {}

    public static void configure(String arguments) {
        if (arguments == null) return;
        for (String option : arguments.split(",")) {
            if ("passive".equalsIgnoreCase(option.trim())) {
                if (configuredPassive.compareAndSet(false, true)) {
                    EprotectPlusBridge.recordEvent("互換", "Java agents", "startup", "passive",
                            "有効", "Transformers will not be installed");
                }
                return;
            }
        }
    }

    public static boolean isConfiguredPassive() {
        return configuredPassive.get();
    }

    public static boolean isPassive() {
        if (configuredPassive.get() || permanentPassive.get()) return true;
        if (windowThrottled.get()) {
            long now = System.nanoTime();
            // 相手の再変換が QUIET_NS（60 秒）以上完全に止んでいるときだけ窓を明ける（書き換え合いが続く間は明けず、2 回目の衝突超過で恒久停止）
            if (now - lastCollisionAttemptNs >= QUIET_NS) {
                windowReplacements.set(0);
                windowThrottled.set(false);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns false once the current replacement budget is exhausted.
     * Reopens only after at least QUIET_NS of silence with no incoming retransformation collisions;
     * if collisions recur and hit the limit a second time, transitions to permanent passive mode.
     */
    public static boolean allowReplacement(String className) {
        if (configuredPassive.get() || permanentPassive.get()) return false;
        long now = System.nanoTime();
        long prevAttempt = lastCollisionAttemptNs;
        lastCollisionAttemptNs = now;
        if (now - prevAttempt >= QUIET_NS) {
            windowReplacements.set(0);
            windowThrottled.set(false);
        }
        if (windowThrottled.get()) {
            return false;
        }
        replacements.incrementAndGet();
        int count = windowReplacements.incrementAndGet();
        if (count <= LIMIT) return true;
        if (windowThrottled.compareAndSet(false, true)) {
            int cycles = throttleCycles.incrementAndGet();
            if (cycles >= MAX_THROTTLE_CYCLES) {
                permanentPassive.set(true);
                EprotectPlusBridge.recordEvent("互換", className, "collision limit", "permanent_passive",
                        "恒久停止", "Stopped bytecode replacement permanently after repeated agent collision cycles (" + cycles + ")");
            } else {
                EprotectPlusBridge.recordEvent("互換", className, "collision limit", "quiet_wait",
                        "一時待機", "Paused bytecode replacement after " + LIMIT + " replacements; will reopen only after 60s of quiet");
            }
        }
        return false;
    }

    public static int replacementCount() {
        return replacements.get();
    }

    public static int limit() {
        return LIMIT;
    }

    private static int readLimit() {
        try {
            return Math.max(0, Math.min(64, Integer.parseInt(System.getProperty(LIMIT_PROPERTY,
                    Integer.toString(DEFAULT_LIMIT)))));
        } catch (NumberFormatException ignored) {
            return DEFAULT_LIMIT;
        }
    }
}
