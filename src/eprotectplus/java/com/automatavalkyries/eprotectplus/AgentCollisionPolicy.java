package com.automatavalkyries.eprotectplus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared limit for bytecode replacements made by the Eprotect+ and flag agents. */
public final class AgentCollisionPolicy {
    private static final String LIMIT_PROPERTY = "automata_valkyries.agentCollisionLimit";
    private static final int DEFAULT_LIMIT = 16;
    private static final int LIMIT = readLimit();
    private static final AtomicInteger replacements = new AtomicInteger();
    private static final AtomicBoolean passive = new AtomicBoolean();

    private AgentCollisionPolicy() {}

    public static void configure(String arguments) {
        if (arguments == null) return;
        for (String option : arguments.split(",")) {
            if ("passive".equalsIgnoreCase(option.trim())) {
                if (passive.compareAndSet(false, true)) {
                    EprotectPlusBridge.recordEvent("互換", "Java agents", "startup", "passive",
                            "有効", "Transformers will not be installed");
                }
                return;
            }
        }
    }

    public static boolean isPassive() {
        return passive.get();
    }

    /** Returns false once the common replacement budget is exhausted. */
    public static boolean allowReplacement(String className) {
        if (passive.get()) return false;
        int count = replacements.incrementAndGet();
        if (count <= LIMIT) return true;
        if (passive.compareAndSet(false, true)) {
            EprotectPlusBridge.recordEvent("互換", className, "collision limit", "passive",
                    "有効", "Stopped bytecode replacement and active audits after " + LIMIT + " replacements");
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
