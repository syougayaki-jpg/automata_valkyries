package com.automatavalkyries.protect.plus;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * バイトコード監視・再監査・診断を利用できる。
 * エージェントが付いていない環境（通常の GameTest や非エージェント起動時）でも、
 * ゲーム内の対象限定の前処理を使う。バイトコード操作はエージェント接続時だけ利用できる。
 * </p>
 */
public final class EprotectPlusLink {

    private static final String BRIDGE_CLASS = "com.automatavalkyries.eprotectplus.EprotectPlusBridge";

    @Nullable
    private static volatile Class<?> bridge;
    private static volatile long nextBridgeLookupNs = System.nanoTime();
    private static final Map<String, Method> BRIDGE_METHODS = new java.util.concurrent.ConcurrentHashMap<>();

    // ---- フォールバック用ローカル記憶域（エージェント未接続時） -----------------
    private static final int MAX_LOCAL_EVENTS = 512;
    private static final Deque<Map<String, String>> LOCAL_EVENTS = new ArrayDeque<>();
    private static final AtomicLong LOCAL_INTERFERENCES = new AtomicLong();
    private static final AtomicLong LOCAL_BYPASSES = new AtomicLong();
    private static final AtomicLong LOCAL_PROTECTIONS = new AtomicLong();
    private static final AtomicLong LOCAL_ANNIHILATIONS = new AtomicLong();
    private static final ThreadLocal<Boolean> LOCAL_PRIVILEGED = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final AtomicLong LOCAL_REINFORCED_UNTIL_NS = new AtomicLong(System.nanoTime());
    private static volatile String LOCAL_REINFORCED_REASON = "";

    private EprotectPlusLink() {}

    @Nullable
    private static synchronized Class<?> bridge() {
        if (bridge != null) return bridge;
        long now = System.nanoTime();
        if (now - nextBridgeLookupNs < 0) return null;
        nextBridgeLookupNs = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        try {
            bridge = Class.forName(BRIDGE_CLASS, true, ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) { }
        return bridge;
    }

    private static Method bridgeMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        String key = name + java.util.Arrays.toString(parameters);
        Method method = BRIDGE_METHODS.get(key);
        if (method == null) {
            method = type.getMethod(name, parameters);
            BRIDGE_METHODS.putIfAbsent(key, method);
        }
        return method;
    }

    /** Java エージェントが有効かつ稼働中か */
    public static boolean isAgentActive() {
        Class<?> type = bridge();
        if (type == null) return false;
        try {
            Method m = bridgeMethod(type, "isActive");
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 協調用の特権フラグの実行範囲内か（JVM権限・スタックを変更するものではない） */
    public static boolean isPrivilegedOrBypassing() {
        if (Boolean.TRUE.equals(LOCAL_PRIVILEGED.get())) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "isPrivileged");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** 同じスレッド上の協調用フラグ。action は正常時・例外時とも一度だけ実行する。 */
    public static void runPrivileged(Runnable action) {
        runScoped("runPrivileged", action);
    }

    /** 互換API。呼び出し履歴を隠す保証はなく、特権フラグの範囲だけを管理する。 */
    public static void runWithCloakedStack(Runnable action) {
        runScoped("runCloaked", action);
    }

    private static void runScoped(String methodName, Runnable action) {
        java.util.Objects.requireNonNull(action, "action");
        boolean previous = LOCAL_PRIVILEGED.get();
        LOCAL_PRIVILEGED.set(true);
        try {
            Class<?> type = bridge();
            Method method = null;
            if (type != null && isAgentActive()) {
                try {
                    method = bridgeMethod(type, methodName, Runnable.class);
                } catch (NoSuchMethodException ignored) { }
            }
            if (method == null) {
                action.run();
                return;
            }
            try {
                method.invoke(null, action);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Eprotect+ action failed", cause);
            } catch (IllegalAccessException exception) {
                // Invocation has not started; only this case may use the local action.
                action.run();
            }
        } finally {
            if (previous) LOCAL_PRIVILEGED.set(true);
            else LOCAL_PRIVILEGED.remove();
        }
    }

    /**
     * JVM エージェントによる能動的バイトコード再監査・強制復元（案2）。
     * @return 正常に再変換したクラス数（復元を確認した件数ではない）
     */
    public static int enforceBytecodeIntegrity() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "retransformCriticalClasses");
                Object res = m.invoke(null);
                if (res instanceof Integer count) {
                    return count;
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    // ---- 強化処理（Reinforced Protection）制御 ---------------------------------

    /**
     * 強化処理（指定ミリ秒）を発動する。
     */
    public static void triggerReinforcedProtection(long durationMs, String reason) {
        boolean alreadyActive = isReinforcedActive();
        long now = System.nanoTime();
        long durationNs = Math.min(Long.MAX_VALUE / 2,
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, durationMs)));
        LOCAL_REINFORCED_UNTIL_NS.updateAndGet(previous -> previous - now > durationNs ? previous : now + durationNs);
        LOCAL_REINFORCED_REASON = reason != null ? reason : "UNKNOWN";

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "triggerReinforced", long.class, String.class);
                m.invoke(null, durationMs, reason);
            } catch (Throwable ignored) {}
        }

        if (!alreadyActive) {
            // 強化処理開始時に能動的バイトコード点検を執行
            enforceBytecodeIntegrity();

            recordLocal("強化", "EprotectPlusLink", reason, "reinforced_mode", "発動",
                    "期間: " + durationMs + "ms, 理由: " + LOCAL_REINFORCED_REASON);
            AutomataValkyries.LOGGER.info("[eprotect-plus] >>> REINFORCED PROTECTION ACTIVATED <<< Duration: {}ms, Reason: {}",
                    durationMs, LOCAL_REINFORCED_REASON);
        }
    }

    /**
     * 強化処理（Tick 数を50msに換算、例: 200 ticks = 実時間10秒）を発動する。
     */
    public static void triggerReinforcedProtectionTicks(long durationTicks, String reason) {
        long ticks = Math.max(0, durationTicks);
        triggerReinforcedProtection(ticks > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticks * 50L, reason);
    }

    /**
     * ブラックリストに入った MOD 相手の強化処理を発動する（10秒間 = 200 ticks）。
     */
    public static void triggerBlacklistReinforcement(String modId) {
        triggerReinforcedProtection(10000L, "BLACKLIST_MOD:" + modId);
    }

    /**
     * 終焉機能に10秒以上耐えられた場合の強化処理を10秒間実行する。
     */
    public static void triggerTerminusResistedReinforcement(Entity target) {
        String name = target != null ? target.getName().getString() : "UnknownTarget";
        triggerReinforcedProtection(10000L, "TERMINUS_RESISTED_10S:" + name);
    }

    /**
     * 現在 Eprotect+ 強化処理が稼働中か。
     */
    public static boolean isReinforcedActive() {
        if (LOCAL_REINFORCED_UNTIL_NS.get() - System.nanoTime() > 0) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "isReinforced");
                return Boolean.TRUE.equals(m.invoke(null));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static String getReinforcedReason() {
        if (isReinforcedActive()) {
            return LOCAL_REINFORCED_REASON;
        }
        return "";
    }

    public static long getReinforcedRemainingMs() {
        long localRemaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0, LOCAL_REINFORCED_UNTIL_NS.get() - System.nanoTime()));
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getReinforcedRemainingMs");
                Object val = m.invoke(null);
                if (val instanceof Long l) return Math.max(localRemaining, l);
            } catch (Throwable ignored) {}
        }
        return localRemaining;
    }

    /**
     * 強化処理状態を即時リセットする（テスト環境・戦闘終了用）。
     */
    public static void resetReinforced() {
        LOCAL_REINFORCED_UNTIL_NS.set(System.nanoTime());
        LOCAL_REINFORCED_REASON = "";
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "resetReinforced");
                m.invoke(null);
            } catch (Throwable ignored) {}
        }
    }

    // ---- 調査 (Investigation) 記録 -------------------------------------------

    private static final Map<String, Long> LAST_LOCAL_INTERFERENCE_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOCAL_INTERFERENCE_THROTTLE_MS = 5000L;

    public static void recordInterference(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordInterference", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_INTERFERENCES.incrementAndGet();
        String key = (target != null ? target : "") + "#" + (op != null ? op : "");
        long now = System.nanoTime();
        Long prev = LAST_LOCAL_INTERFERENCE_MS.get(key);
        if (prev != null && (now - prev) < java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(LOCAL_INTERFERENCE_THROTTLE_MS)) {
            return;
        }
        if (LAST_LOCAL_INTERFERENCE_MS.size() > 4096) LAST_LOCAL_INTERFERENCE_MS.clear();
        LAST_LOCAL_INTERFERENCE_MS.put(key, now);
        recordLocal("調査・干渉検知", target, caller, op, "検知・解析完了", details);
    }

    public static void recordBypass(String target, String caller, String op, String details) {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "recordBypass", String.class, String.class, String.class, String.class);
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
                Method m = bridgeMethod(type, "recordProtection", String.class, String.class, String.class, String.class);
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
                Method m = bridgeMethod(type, "recordAnnihilation", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_ANNIHILATIONS.incrementAndGet();
        recordLocal("真抹殺執行", target, caller, op, "完全消滅", details);
    }

    private static void recordLocal(String kind, String target, String caller, String op, String action, String note) {
        Map<String, String> ev = new LinkedHashMap<>();
        ev.put("time", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME));
        ev.put("kind", kind);
        ev.put("target", target != null ? target : "-");
        ev.put("caller", caller != null ? caller : "-");
        ev.put("op", op != null ? op : "-");
        ev.put("action", action != null ? action : "-");
        ev.put("note", note != null ? note : "");

        synchronized (LOCAL_EVENTS) {
            LOCAL_EVENTS.addLast(Collections.unmodifiableMap(ev));
            if (LOCAL_EVENTS.size() > MAX_LOCAL_EVENTS) LOCAL_EVENTS.removeFirst();
        }
    }

    // ---- 迂回 & 保護 (Bypass & Protection) -----------------------------------

    /**
     * 保護対象エンティティへの外部干渉を迂回し、正規の生命状態を維持する。
     */
    public static void inspectAndBypassInterference(LivingEntity entity) {
        if (entity == null || !com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
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

        // 3. SuperSteve 改ざん・当たり判定崩壊・コールバック偽装の検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectSuperSteveCounter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "superSteveCounter", "SuperSteveの改ざん・判定崩壊を修復");
            }
        } catch (Throwable ignored) {}

        // 4. Pig2Mod 抹殺追跡リストの検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectPig2Counter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "pig2Counter", "Pig2Modの抹殺追跡リストから除外");
            }
        } catch (Throwable ignored) {}

        // 5. Nekohashizuku Blade HEALTH_MAP 改ざんの検出と迂回
        try {
            if (com.automatavalkyries.protect.EprotectNekohashizukuCounter.counter(entity)) {
                recordBypass(entity.getClass().getName(), "Eprotect+", "nekohashizukuCounter", "Nekohashizuku HEALTH_MAPから除外");
            }
        } catch (Throwable ignored) {}
    }

    // ---- 真抹殺 (True Annihilation) ------------------------------------------

    /** 後続の抹殺パイプラインに渡す対象限定の前処理。エージェント有無で同じ処理を使う。 */
    public static Map<String, String> prepareAnnihilation(Entity target) {
        if (target == null || target instanceof Player
                || (target instanceof com.automatavalkyries.protect.IEprotected protectedEntity
                && !protectedEntity.isTerminusTestTarget())) {
            return Map.of("status", "unsupported", "reason", "excluded_target");
        }
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread()) {
            return Map.of("status", "unsupported", "reason", "server_thread_required");
        }
        try {
            runPrivileged(() -> {
                com.automatavalkyries.annihilate.ModProtectionBreaker.breakProtection(level, target);
                if (target instanceof LivingEntity living) living.setHealth(0.0F);
            });
            // Preparation is not proof of death/removal. The caller verifies the final pipeline.
            return Map.of("status", "attempted", "reason", "target_prepared");
        } catch (RuntimeException exception) {
            recordLocal("実行失敗", target.getClass().getName(), "Eprotect+", "prepare",
                    "failed", exception.toString());
            return Map.of("status", "failed", "reason", exception.toString());
        }
    }

    /** 互換API。true はサーバーで個体の消去が確認できた場合だけ返す。 */
    public static boolean annihilateTargetWithAgent(Entity target) {
        if (!"attempted".equals(prepareAnnihilation(target).get("status"))) return false;
        return isAnnihilationConfirmed(target);
    }

    public static boolean isAnnihilationConfirmed(Entity target) {
        if (target == null || !(target.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        return com.automatavalkyries.protect.DirectRead.isRemoved(target)
                && level.getEntity(target.getUUID()) != target && level.getEntity(target.getId()) != target;
    }

    // ---- 状態・イベント取得 ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> getEvents() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = bridgeMethod(type, "getEvents");
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
                Method m = bridgeMethod(type, "clearEvents");
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
                Method m = bridgeMethod(type, "getStats");
                Object res = m.invoke(null);
                if (res instanceof Map<?, ?> map) return (Map<String, String>) map;
            } catch (Throwable ignored) {}
        }

        Map<String, String> stats = new LinkedHashMap<>();
        stats.put("active", "false");
        stats.put("agentState", "INACTIVE");
        stats.put("gamePreparation", "available");
        stats.put("lateAttached", "false");
        stats.put("instrumentation", "false");
        stats.put("canRetransform", "false");
        stats.put("interferences", String.valueOf(LOCAL_INTERFERENCES.get()));
        stats.put("bypasses", String.valueOf(LOCAL_BYPASSES.get()));
        stats.put("protections", String.valueOf(LOCAL_PROTECTIONS.get()));
        stats.put("annihilations", String.valueOf(LOCAL_ANNIHILATIONS.get()));
        synchronized (LOCAL_EVENTS) { stats.put("events", String.valueOf(LOCAL_EVENTS.size())); }
        return stats;
    }

    /**
     * 外部 MOD のキル管理リスト・消滅リスト（No-Sugar の erasedUuids 等）から
     * プレイヤーの UUID を安全に削除し、リスポーンや再参加を正常化する。
     */
    public static void clearPlayerFromKillModLists(UUID uuid) {
        if (uuid == null) return;
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;

        // フォールバック（エージェント未接続時でもリフレクションで直接除去）
        for (String className : new String[]{
                "com.test.nosugar.mixin.sugar_sword.LivingEntityMixin",
                "com.test.nosugar.utils.EraseUtils",
                "com.test.nosugar.utils.entity.LivingEntityUtils",
                "net.eca.util.health.DelayedHealthVerifier",
                "net.eca.util.EntityRemovalQuarantine"
        }) {
            try {
                Class<?> cls = Class.forName(className);
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        if (Set.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            Set<Object> set = (Set<Object>) f.get(null);
                            if (set != null) set.remove(uuid);
                        } else if (Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
                            if (map != null) {
                                map.remove(uuid);
                                map.keySet().removeIf(k -> k instanceof Entity e && e.getUUID().equals(uuid));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // IMDS クリーンアップ
        try {
            Class<?> km = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            for (String fieldName : new String[]{"FORCE_DEAD_UUIDS", "REMOVED_UUIDS"}) {
                try {
                    Field f = km.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Set<Object> set = (Set<Object>) f.get(null);
                    if (set != null) set.remove(uuid);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // Pig2Mod gKilledEnemyUUIDs クリーンアップ
        try {
            Class<?> pig2 = Class.forName("kakiku.pig2mod.entity.Pig2");
            Field f = pig2.getDeclaredField("gKilledEnemyUUIDs");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Object> set = (Set<Object>) f.get(null);
            if (set != null) set.remove(uuid);
        } catch (Throwable ignored) {}

        // Nekohashizuku HEALTH_MAP クリーンアップ
        try {
            Class<?> shizuku = Class.forName("com.buwan.shizuku.core.AgentHealthMethodHelper");
            Field f = shizuku.getDeclaredField("HEALTH_MAP");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
            if (map != null) {
                map.keySet().removeIf(k -> {
                    try {
                        Method getUuid = k.getClass().getMethod("getUUID");
                        return uuid.equals(getUuid.invoke(k));
                    } catch (Throwable ignored) {
                        return false;
                    }
                });
            }
        } catch (Throwable ignored) {}
    }
}
