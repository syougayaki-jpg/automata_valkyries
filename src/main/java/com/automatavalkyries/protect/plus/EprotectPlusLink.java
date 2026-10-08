package com.automatavalkyries.protect.plus;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.protect.attack.CallerAttribution;
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
    private static final AtomicLong LOCAL_REINFORCED_UNTIL_MS = new AtomicLong(0);
    private static volatile String LOCAL_REINFORCED_REASON = "";

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

    /**
     * スタックトレース偽装・検閲欺瞞コンテキストで処理を実行（案3）。
     */
    public static void runWithCloakedStack(Runnable action) {
        boolean prevLocal = LOCAL_PRIVILEGED.get();
        LOCAL_PRIVILEGED.set(true);

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("runCloaked", Runnable.class);
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

    /**
     * JVM エージェントによる能動的バイトコード再監査・強制復元（案2）。
     * @return 再監査・復元したクラス数
     */
    public static int enforceBytecodeIntegrity() {
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("retransformCriticalClasses");
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
        long targetUntil = System.currentTimeMillis() + durationMs;
        LOCAL_REINFORCED_UNTIL_MS.updateAndGet(prev -> Math.max(prev, targetUntil));
        LOCAL_REINFORCED_REASON = reason != null ? reason : "UNKNOWN";

        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("triggerReinforced", long.class, String.class);
                m.invoke(null, durationMs, reason);
            } catch (Throwable ignored) {}
        }

        if (!alreadyActive) {
            // 強化処理開始時に能動的バイトコード点検を執行
            enforceBytecodeIntegrity();

            recordProtection("EprotectPlusLink", reason, "reinforced_mode",
                    "Eprotect+ 強化処理発動（期間: " + durationMs + "ms, 理由: " + LOCAL_REINFORCED_REASON + "）");
            AutomataValkyries.LOGGER.info("[eprotect-plus] >>> REINFORCED PROTECTION ACTIVATED <<< Duration: {}ms, Reason: {}",
                    durationMs, LOCAL_REINFORCED_REASON);
        }
    }

    /**
     * 強化処理（指定 Tick 数、例: 200 ticks = 10秒）を発動する。
     */
    public static void triggerReinforcedProtectionTicks(long durationTicks, String reason) {
        triggerReinforcedProtection(durationTicks * 50L, reason);
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
        if (System.currentTimeMillis() < LOCAL_REINFORCED_UNTIL_MS.get()) return true;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("isReinforced");
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
        long localRemaining = Math.max(0, LOCAL_REINFORCED_UNTIL_MS.get() - System.currentTimeMillis());
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("getReinforcedRemainingMs");
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
        LOCAL_REINFORCED_UNTIL_MS.set(0);
        LOCAL_REINFORCED_REASON = "";
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("resetReinforced");
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
                Method m = type.getMethod("recordInterference", String.class, String.class, String.class, String.class);
                m.invoke(null, target, caller, op, details);
                return;
            } catch (Throwable ignored) {}
        }

        LOCAL_INTERFERENCES.incrementAndGet();
        String key = (target != null ? target : "") + "#" + (op != null ? op : "");
        long now = System.currentTimeMillis();
        Long prev = LAST_LOCAL_INTERFERENCE_MS.get(key);
        if (prev != null && (now - prev) < LOCAL_INTERFERENCE_THROTTLE_MS) {
            return;
        }
        LAST_LOCAL_INTERFERENCE_MS.put(key, now);
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

            // MetapotentFlashfur 本体（Proxy 元）の解体と MetapotentFlashfurLevel / Respawning リストからの抹消
            Object core = null;
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("metapotentFlashfur");
                    f.setAccessible(true);
                    core = f.get(target);
                    if (core != null) break;
                } catch (Throwable ignored) {}
            }
            if (core != null) {
                dismantleFlashfurCore(core);
            }

            // MetapotentFlashfurLevel.metapotentFlashfurList & Respawning.RESPAWNING_ENTITIES から強制除去
            forciblyRemoveFromList("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel", "metapotentFlashfurList", core);
            forciblyRemoveFromList("flashfur.omnimobs.entities.flashfur.powers.Respawning", "RESPAWNING_ENTITIES", target);
            forciblyRemoveFromList("flashfur.omnimobs.entities.base.RemoveDeadEntities", "deadEntities", target);

            // SuperSteve 解体
            if (clazz.getName().startsWith("plz.lizi.supersteve.")) {
                try {
                    if (clazz.getName().contains("SafeServerPlayer") || clazz.getName().contains("DeathServerPlayer")) {
                        try {
                            Method dc = clazz.getMethod("disconnect");
                            dc.invoke(target);
                        } catch (Throwable ignored) {}
                    }
                    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                        try {
                            Field f = c.getDeclaredField("health");
                            f.setAccessible(true);
                            Object op = f.get(target);
                            if (op != null) {
                                Method m = op.getClass().getMethod("operate", Object[].class);
                                Field vrfField = c.getDeclaredField("VRF");
                                vrfField.setAccessible(true);
                                String[] vrf = (String[]) vrfField.get(null);
                                if (vrf != null && vrf.length > 5) {
                                    m.invoke(op, new Object[]{ new Object[]{ vrf[5], 0.0F } });
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Field be = c.getDeclaredField("bossEvent");
                            be.setAccessible(true);
                            Object bossEvent = be.get(target);
                            if (bossEvent != null) {
                                Method clear = bossEvent.getClass().getMethod("removeAllPlayers");
                                clear.invoke(bossEvent);
                            }
                        } catch (Throwable ignored) {}
                    }
                    try {
                        Class<?> ssu = Class.forName("plz.lizi.supersteve.api.SSUtil");
                        for (Method m : ssu.getMethods()) {
                            if (m.getName().equals("killEntity") && m.getParameterCount() == 2) {
                                m.invoke(null, target, true);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }

            // Pig2Mod 解体
            if (clazz.getName().startsWith("kakiku.pig2mod.")) {
                try {
                    try {
                        Method stop = clazz.getDeclaredMethod("stopFightingRemovedEnemy");
                        stop.setAccessible(true);
                        stop.invoke(target);
                    } catch (Throwable ignored) {}
                    for (String fieldName : new String[]{"mAttackingEnemy", "mAttackLevel"}) {
                        try {
                            Field f = clazz.getDeclaredField(fieldName);
                            f.setAccessible(true);
                            f.set(target, null);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            // Nekohashizuku Blade 解体
            try {
                Class<?> helper = Class.forName("com.buwan.shizuku.core.AgentHealthMethodHelper");
                Field f = helper.getDeclaredField("HEALTH_MAP");
                f.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) f.get(null);
                if (map != null) {
                    map.remove(target);
                }
            } catch (Throwable ignored) {}

            // 不死フラグ探索初期化（フラグ辞書と連携）
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String name = f.getName();
                    if (com.automatavalkyries.protect.flag.FlagDictionary.isDefensiveField(name)) {
                        try {
                            f.setAccessible(true);
                            if (f.getType() == boolean.class) {
                                f.setBoolean(target, false);
                            } else if (f.getType() == int.class) {
                                f.setInt(target, 0);
                            } else if (f.getType() == float.class) {
                                f.setFloat(target, 0.0F);
                            } else if (f.getType() == double.class) {
                                f.setDouble(target, 0.0D);
                            }
                        } catch (Throwable ignored) {}
                    } else if (com.automatavalkyries.protect.flag.FlagDictionary.isDeathStateField(name)) {
                        try {
                            f.setAccessible(true);
                            if (f.getType() == boolean.class) {
                                f.setBoolean(target, true);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // プレイヤー無敵・飛行アビリティの剥奪
            if (target instanceof Player player) {
                try {
                    player.getAbilities().invulnerable = false;
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                } catch (Throwable ignored) {}
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

    private static void dismantleFlashfurCore(Object core) {
        if (core == null) return;
        try {
            Class<?> coreClass = core.getClass();
            try {
                Field idf = coreClass.getDeclaredField("id");
                idf.setAccessible(true);
                UUID id = (UUID) idf.get(core);
                if (id != null) {
                    AnnihilationExecutionAgent.markAnnihilated(id);
                }
            } catch (Throwable ignored) {}
            try {
                Field rf = coreClass.getDeclaredField("removed");
                rf.setAccessible(true);
                rf.setBoolean(core, true);
            } catch (Throwable ignored) {}
            try {
                Field pf = coreClass.getDeclaredField("metapotentFlashfurProxy");
                pf.setAccessible(true);
                pf.set(core, null);
            } catch (Throwable ignored) {}
            for (String timerName : new String[]{"beamTimer", "eraseTimer", "swipeTimer", "totalityBoomTimer", "throwInAirTimer"}) {
                try {
                    Field f = coreClass.getDeclaredField(timerName);
                    f.setAccessible(true);
                    f.setInt(core, -9999);
                } catch (Throwable ignored) {}
            }
            try {
                Field bf = coreClass.getDeclaredField("bossEvent");
                bf.setAccessible(true);
                Object be = bf.get(core);
                if (be != null) {
                    try { be.getClass().getMethod("removeAllPlayers").invoke(be); } catch (Throwable ignored) {}
                    try { be.getClass().getMethod("setVisible", boolean.class).invoke(be, false); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void forciblyRemoveFromList(String className, String fieldName, Object target) {
        try {
            Class<?> clazz = Class.forName(className);
            Field listField = clazz.getDeclaredField(fieldName);
            listField.setAccessible(true);
            Object list = listField.get(null);
            if (list == null) return;

            Field sizeField = java.util.ArrayList.class.getDeclaredField("size");
            sizeField.setAccessible(true);
            Field dataField = java.util.ArrayList.class.getDeclaredField("elementData");
            dataField.setAccessible(true);

            int size = sizeField.getInt(list);
            Object[] data = (Object[]) dataField.get(list);
            if (data == null || size == 0) return;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                Object elem = data[i];
                if (elem == null) continue;
                if (target == null || elem == target || elem.equals(target)) {
                    dismantleFlashfurCore(elem);
                    data[i] = null;
                } else {
                    data[newSize++] = elem;
                }
            }
            for (int i = newSize; i < size; i++) {
                data[i] = null;
            }
            sizeField.setInt(list, newSize);
        } catch (Throwable ignored) {}
    }

    /**
     * 外部 MOD のキル管理リスト・消滅リスト（No-Sugar の erasedUuids 等）から
     * プレイヤーの UUID を安全に削除し、リスポーンや再参加を正常化する。
     */
    public static void clearPlayerFromKillModLists(UUID uuid) {
        if (uuid == null) return;
        Class<?> type = bridge();
        if (type != null) {
            try {
                Method m = type.getMethod("clearPlayerFromKillModLists", UUID.class);
                m.invoke(null, uuid);
            } catch (Throwable ignored) {}
        }

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
