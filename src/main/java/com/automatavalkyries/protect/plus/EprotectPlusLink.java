package com.automatavalkyries.protect.plus;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MOD 側から Eprotect+ Java エージェント（システムクラスローダー常駐）を操作・参照するためのリンク。
 * <p>
 * {@code -javaagent:automata_valkyries-eprotect-plus.jar} が付いていれば JVM エージェント経由で
 * バイトコード操作・特権迂回・真抹殺を実行する。
 * エージェントが付いていない環境（通常の GameTest や非エージェント起動時）でも、
 * 内蔵の Unsafe / リフレクションによる同等の迂回・調査・真抹殺フォールバックエンジンが自動で稼働する。
 * </p>
 */
public final class EprotectPlusLink {

    private static final String BRIDGE_CLASS = "com.automatavalkyries.eprotectplus.EprotectPlusBridge";

    @Nullable
    private static volatile Class<?> bridge;
    private static volatile boolean looked = false;

    // ---- フォールバック用ローカル記憶域（エージェント未接続時） -----------------
    private static final int MAX_LOCAL_EVENTS = 512;
    private static final Deque<Map<String, String>> LOCAL_EVENTS = new ArrayDeque<>();
    private static final AtomicLong LOCAL_INTERFERENCES = new AtomicLong();
    private static final AtomicLong LOCAL_BYPASSES = new AtomicLong();
    private static final AtomicLong LOCAL_PROTECTIONS = new AtomicLong();
    private static final AtomicLong LOCAL_ANNIHILATIONS = new AtomicLong();
    private static final ThreadLocal<Boolean> LOCAL_PRIVILEGED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private EprotectPlusLink() {}

    @Nullable
    private static Class<?> bridge() {
        if (!looked) {
            looked = true;
            try {
                bridge = Class.forName(BRIDGE_CLASS, true, ClassLoader.getSystemClassLoader());
            } catch (Throwable t) {
                bridge = null;
            }
        }
        return bridge;
    }

    /** Java エージェントが有効かつ稼働中か */
    public static boolean isAgentActive() {
        Class<?> type = bridge();
        if (type == null) return false;
        try {
            Method m = type.getMethod("isActive");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 特権実行（StackChecker 迂回）中か */
    public static boolean isPrivilegedOrBypassing() {
        if (Boolean.TRUE.equals(LOCAL_PRIVILEGED.get())) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("isPrivileged");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** 特権コンテキストで処理を実行 */
    public static void runPrivileged(Runnable action) {
        boolean prevLocal = LOCAL_PRIVILEGED.get();
        LOCAL_PRIVILEGED.set(true);

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("runPrivileged", Runnable.class);
                m.invoke(null, action);
                return;
            } catch (Throwable ignored) {}
        }

        try {
            action.run();
        } finally {
            LOCAL_PRIVILEGED.set(prevLocal);
        }
    }

    // ---- 調査 (Investigation) 記録 -------------------------------------------

    public static void recordInterference(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("recordInterference", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_INTERFERENCES.incrementAndGet();
        recordLocal("調査・干渉検知", target, caller, op, "検知・解析完了", details);
    }

    public static void recordBypass(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("recordBypass", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_BYPASSES.incrementAndGet();
        recordLocal("迂回執行", target, caller, op, "迂回成功", details);
    }

    public static void recordProtection(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("recordProtection", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_PROTECTIONS.incrementAndGet();
        recordLocal("保護執行", target, caller, op, "改ざん拒否・復元", details);
    }

    public static void recordAnnihilation(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("recordAnnihilation", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_ANNIHILATIONS.incrementAndGet();
        recordLocal("真抹殺執行", target, caller, op, "完全消滅", details);
    }

    private static synchronized void recordLocal(String kind, String target, String caller, String op, String action, String note) {
        Map<String, String> ev = new LinkedHashMap<>();
        ev.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME));
        ev.put("kind", kind);
        ev.put("target", target != null ? target : "-");
        ev.put("caller", caller != null ? caller : "-");
        ev.put("op", op != null ? op : "-");
        ev.put("action", action != null ? action : "-");
        ev.put("note", note != null ? note : "");

        LOCAL_EVENTS.addLast(ev);
        if (LOCAL_EVENTS.size() > MAX_LOCAL_EVENTS) LOCAL_EVENTS.removeFirst();
    }

    // ---- 迂回 & 保護 (Bypass & Protection) -----------------------------------

    /**
     * 保護対象エンティティへの外部干渉を迂回し、正規の生命状態を維持する。
     */
    public static void inspectAndBypassInterference(LivingEntity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();

        // 1. No-Sugar / ACE 型の erased 偽装の検出と迂回
        try {
            Class<?> iliving = Class.forName("com.test.nosugar.utils.interfaces.ILivingEntity");
            if (iliving.isInstance(entity)) {
                Method isErased = iliving.getMethod("isErased");
                if (Boolean.TRUE.equals(isErased.invoke(entity))) {
                    recordInterference(entity.getClass().getName(), "No-Sugar", "isErased", "抹消フラグの検知");
                    Method setErased = iliving.getMethod("setErased", boolean.class);
                    setErased.invoke(entity, false);
                    Method unmark = iliving.getMethod("unmarkErased", UUID.class);
                    unmark.invoke(entity, uuid);
                    recordBypass(entity.getClass().getName(), "Eprotect+", "unmarkErased", "抹消フラグを解除し生存状態を維持");
                }
            }
        } catch (Throwable ignored) {}

        // 2. Omni-Mobs 型の healthDelta 負数強制注入の検出と迂回
        try {
            Field f = entity.getClass().getDeclaredField("healthDelta");
            f.setAccessible(true);
            float delta = f.getFloat(entity);
            if (delta < 0.0F) {
                recordInterference(entity.getClass().getName(), "Omni-Mobs", "healthDelta", "HP強制減少デルタ: " + delta);
                f.setFloat(entity, 0.0F);
                recordBypass(entity.getClass().getName(), "Eprotect+", "healthDelta", "デルタを0に修正し不正HP削りを無力化");
            }
        } catch (Throwable ignored) {}
    }

    // ---- 真抹殺 (True Annihilation) ------------------------------------------

    /**
     * Java エージェント経由（または内蔵特権エンジン）で対象エンティティの不死・生命維持・防御機構を剥奪し抹殺する。
     */
    public static boolean annihilateTargetWithAgent(Entity target) {
        if (target == null) return false;

        // 1. エージェントが接続されている場合はエージェントに委譲
        Class<?> type = bridge();
        if (type != null && isAgentActive()) {
            try {
                Method m = type.getMethod("annihilateTarget", Object.class);
                Object res = m.invoke(null, target);
                if (Boolean.TRUE.equals(res)) return true;
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.warn("[eprotect-plus] Agent delegation threw, falling back to local engine: {}", t.getMessage());
            }
        }

        // 2. フォールバック特権エンジンによる直接執行
        return executeLocalPrivilegedAnnihilation(target);
    }

    private static boolean executeLocalPrivilegedAnnihilation(Entity target) {
        final boolean[] success = new boolean[]{false};
        runPrivileged(() -> {
            Class<?> clazz = target.getClass();
            UUID uuid = target.getUUID();
            recordBypass(clazz.getName(), "TrueKillStick", "localAnnihilate", "内蔵特権空間から不死機構の解除を開始");

            // Witherzilla 生命維持フラグ (witherzillaLife) の解除
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("witherzillaLife");
                    f.setAccessible(true);
                    f.setBoolean(target, false);
                } catch (Throwable ignored) {}

                try {
                    Field f = c.getDeclaredField("life");
                    if (f.getType() == boolean.class) {
                        f.setAccessible(true);
                        f.setBoolean(target, false);
                    }
                } catch (Throwable ignored) {}
            }
            try {
                Method m = clazz.getMethod("setWitherzillaLife", boolean.class);
                m.invoke(target, false);
            } catch (Throwable ignored) {}

            // Flashfur healthDelta & RemoveDeadEntities 解除
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("healthDelta");
                    f.setAccessible(true);
                    f.setFloat(target, 0.0F);
                } catch (Throwable ignored) {}
            }
            try {
                Method m = clazz.getMethod("setHealthDelta", float.class);
                m.invoke(target, 0.0F);
            } catch (Throwable ignored) {}

            // 不死フラグ探索初期化
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.contains("invulnerab") || name.contains("immune") || name.contains("god")
                            || name.contains("safe") || name.contains("protect") || name.contains("undead")
                            || name.contains("immortal")) {
                        try {
                            f.setAccessible(true);
                            if (f.getType() == boolean.class) {
                                f.setBoolean(target, false);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // HP を 0.0F にし、死亡・削除を強制
            if (target instanceof LivingEntity living) {
                try {
                    living.setHealth(0.0F);
                } catch (Throwable ignored) {}
                living.deathTime = 19;
            }

            recordAnnihilation(clazz.getName(), "TrueKillStick", "true_annihilation",
                    "特権空間から対象の全不死防御機構を解体: " + target.getName().getString() + " (" + uuid + ")");
            success[0] = true;
        });

        return success[0];
    }

    // ---- 状態・イベント取得 ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> getEvents() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("getEvents");
                Object res = m.invoke(null);
                if (res instanceof List<?> list) return (List<Map<String, String>>) list;
            } catch (Throwable ignored) {}
        }

        synchronized (LOCAL_EVENTS) {
            return new ArrayList<>(LOCAL_EVENTS);
        }
    }

    public static void clearEvents() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("clearEvents");
                m.invoke(null);
            } catch (Throwable ignored) {}
        }
        synchronized (LOCAL_EVENTS) {
            LOCAL_EVENTS.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getStats() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("getStats");
                Object res = m.invoke(null);
                if (res instanceof Map<?, ?> map) return (Map<String, String>) map;
            } catch (Throwable ignored) {}
        }

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("active", "false (local engine active)");
        stats.put("lateAttached", "false");
        stats.put("instrumentation", "false");
        stats.put("canRetransform", "false");
        stats.put("interferences", String.valueOf(LOCAL_INTERFERENCES.get()));
        stats.put("bypasses", String.valueOf(LOCAL_BYPASSES.get()));
        stats.put("protections", String.valueOf(LOCAL_PROTECTIONS.get()));
        stats.put("annihilations", String.valueOf(LOCAL_ANNIHILATIONS.get()));
        stats.put("events", String.valueOf(LOCAL_EVENTS.size()));
        return stats;
    }
}
