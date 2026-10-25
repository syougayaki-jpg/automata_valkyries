package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.protect.AgentHijackGuard;
import com.automatavalkyries.protect.EprotectSuperSteveCounter;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import sun.misc.Unsafe;

import java.lang.instrument.ClassFileTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事象観測体アカシック・プローブの「対 Java エージェント・JVM 深層カナリア＆逆無力化」エンジン。
 * <ul>
 *   <li><b>① サイレント・サンドイッチ＆外部注入ヘルパー骨抜き</b>:
 *       {@code Runtime.halt} 自爆トラップを持つ敵の {@link ClassFileTransformer} はリストから外さずそのまま残し、
 *       バニラ {@code LivingEntity} 等から呼び出される敵の静的ヘルパー・内部 {@code Map} のみを裏から無力化・観測する</li>
 *   <li><b>②・③ {@code InstrumentationImpl} / {@code TransformerManager} 二重パイプライン（{@code canRetransform=true/false}）監査</b>:
 *       {@code retransformClasses} 用と {@code redefineClasses} 用の両方の変換器チェーンおよび別 {@code Instrumentation} を走査する</li>
 *   <li><b>④ おとり監視スレッド（Canary Watchdog Thread）＆ 敵監視スレッドの「無血冷凍（仮想デッドロック）」</b>:
 *       スレッド停止・割り込み・例外ハンドラ改ざん・タイマー改ざんをおとりスレッドで感知し、
 *       敵の監視スレッドは例外を投げずに次回チェック時刻を {@code Long.MAX_VALUE} に飛ばして無血冷凍する</li>
 *   <li><b>⑤・⑥ 後出し動的アタッチ（{@code VirtualMachine.attach}）＆ {@code Class.reflectionData} 汚染検知・修復</b>:
 *       起動完了後に追加された未知のエージェント・Hidden クラス・リフレクションキャッシュ空配列化を検知・修復する</li>
 *   <li><b>JVM Klass Word（32bit 型ポインタ / 豚化即死）＆ {@code EntityType.factory} ＆ {@code EventBus} 検問</b>:
 *       オブジェクトヘッダ先頭 {@code 8L} の Klass Word 書き換え、スポーン工場の {@code null} 化、イベントバスからの追放を検知・修復する</li>
 * </ul>
 */
public final class AkashicAgentCanary {

    private static final long MARK_WORD_OFFSET = 0L;
    private static final long KLASS_WORD_OFFSET = 8L;
    private static volatile int expectedAkashicKlassWord = 0;
    private static volatile boolean klassWordVerified = false;
    private static volatile EntityType.EntityFactory<?> originalAkashicFactory;
    private static Field entityTypeFactoryField;

    /** プローブ UUID ごとの正規 identityHashCode と Mark Word（0L）記録（Mark Word 破壊時も UUID で牽ける） */
    private static final Map<UUID, Integer> EXPECTED_IDENTITY_HASHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> EXPECTED_MARK_WORDS = new ConcurrentHashMap<>();

    /** スレッドローカル直接走査（Thread.threadLocals）攻撃検知用のおとり ThreadLocal */
    public static final ThreadLocal<Boolean> AKASHIC_GUARD_THREAD_LOCAL = ThreadLocal.withInitial(() -> Boolean.TRUE);
    private static volatile boolean guardThreadLocalInitialized = false;

    /** invokedynamic / CallSite ターゲットすり替え検知用のおとり MutableCallSite */
    public static final MethodHandle ORIGINAL_CALL_SITE_TARGET = MethodHandles.constant(float.class, 100.0F);
    public static final MutableCallSite CANARY_CALL_SITE = new MutableCallSite(ORIGINAL_CALL_SITE_TARGET);

    /** 既知の外部エージェント・フッククラス記録済みセット（重複スパム防止） */
    private static final Set<String> REPORTED_AGENT_ITEMS = ConcurrentHashMap.newKeySet();
    private static volatile boolean initialAgentSnapshotTaken = false;
    private static final Set<String> INITIAL_FOREIGN_CLASSES = ConcurrentHashMap.newKeySet();

    // ---- おとり監視スレッド（Canary Watchdog Thread） -----------------------
    private static volatile Thread canaryWatchdogThread;
    private static final AtomicBoolean CANARY_EXPECTED_ALIVE = new AtomicBoolean(true);
    private static final AtomicInteger CANARY_INTERRUPT_COUNT = new AtomicInteger(0);
    private static final AtomicBoolean CANARY_HANDLER_TAMPERED = new AtomicBoolean(false);
    private static final AtomicLong CANARY_NEXT_CHECK_MS = new AtomicLong(0L);
    private static volatile String lastWatchdogAttackerMod = "unknown";
    private static final Thread.UncaughtExceptionHandler CANARY_EXCEPTION_HANDLER = (t, e) -> {
        CallerAttribution.Attribution a = CallerAttribution.attribute(null);
        lastWatchdogAttackerMod = a.callerIsForeign() ? a.callerMod() : a.foreignMod() != null ? a.foreignMod() : a.callerMod();
        CANARY_INTERRUPT_COUNT.incrementAndGet();
    };

    static {
        ensureCanaryWatchdogRunning();
    }

    private AkashicAgentCanary() {}

    /**
     * アカシック・プローブ生成時に正規の Mark Word（{@code 0L} / {@code identityHashCode}）、Klass Word（{@code 8L}）、
     * および {@link EntityType} の正規スポーン工場（{@code factory}）を記憶する。
     */
    public static void captureBaseline(AkashicProbeEntity probe) {
        if (probe == null || !UnsafeHelper.isAvailable()) return;
        try {
            Unsafe u = UnsafeHelper.getUnsafe();
            if (!klassWordVerified && probe.getClass() == AkashicProbeEntity.class) {
                int probeKlass = u.getInt(probe, KLASS_WORD_OFFSET);
                int objectKlass = u.getInt(new Object(), KLASS_WORD_OFFSET);
                if (probeKlass != 0 && probeKlass != objectKlass) {
                    expectedAkashicKlassWord = probeKlass;
                    klassWordVerified = true;
                }
            }
            UUID uuid = probe.getUUID();
            if (uuid != null && !EXPECTED_IDENTITY_HASHES.containsKey(uuid)) {
                int idHash = System.identityHashCode(probe);
                long mark = u.getLongVolatile(probe, MARK_WORD_OFFSET);
                EXPECTED_IDENTITY_HASHES.put(uuid, idHash);
                EXPECTED_MARK_WORDS.put(uuid, mark);
            }
        } catch (Throwable ignored) {}

        try {
            if (originalAkashicFactory == null) {
                EntityType<?> type = ModEntities.AKASHIC_PROBE.get();
                Field f = resolveEntityTypeFactoryField();
                if (f != null && type != null) {
                    Object fac = f.get(type);
                    if (fac instanceof EntityType.EntityFactory<?> ef) {
                        originalAkashicFactory = ef;
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!guardThreadLocalInitialized) {
            guardThreadLocalInitialized = true;
            AKASHIC_GUARD_THREAD_LOCAL.set(Boolean.TRUE);
        }
        ensureCanaryWatchdogRunning();
    }

    private static Field resolveEntityTypeFactoryField() {
        if (entityTypeFactoryField == null) {
            entityTypeFactoryField = UnsafeHelper.findField(EntityType.class, "factory", "f_20535_");
        }
        return entityTypeFactoryField;
    }

    /**
     * 外部（{@link AkashicNonEntityProxy} の {@code ServerTickEvent} を含む）から呼ばれる
     * オブジェクトヘッダ Klass Word（{@code 8L} 型ポインタすり替え）および Mark Word（{@code 0L} {@code identityHashCode} 破壊）点検・即時復元。
     */
    public static boolean verifyAndRestoreKlassWord(AkashicProbeEntity probe, AkashicRecord rec, String suspectMod) {
        if (probe == null || !UnsafeHelper.isAvailable()) {
            return false;
        }
        boolean repaired = false;
        try {
            Unsafe u = UnsafeHelper.getUnsafe();
            if (klassWordVerified && expectedAkashicKlassWord != 0) {
                int currentKlass = u.getIntVolatile(probe, KLASS_WORD_OFFSET);
                if (currentKlass != expectedAkashicKlassWord) {
                    u.putIntVolatile(probe, KLASS_WORD_OFFSET, expectedAkashicKlassWord);
                    if (rec != null) {
                        rec.observe(suspectMod, "jvm_header:klass_word_tampered", "REPAIRED",
                                "0x" + Integer.toHexString(currentKlass) + " -> 0x" + Integer.toHexString(expectedAkashicKlassWord),
                                null);
                    }
                    repaired = true;
                }
            }
            if (verifyAndRestoreMarkWord(probe, rec, suspectMod)) {
                repaired = true;
            }
        } catch (Throwable ignored) {}
        return repaired;
    }

    /**
     * オブジェクトヘッダ先頭 {@code 0L}（Mark Word）の {@code identityHashCode} ビット破壊を検知・即時復元する。
     */
    public static boolean verifyAndRestoreMarkWord(AkashicProbeEntity probe, AkashicRecord rec, String suspectMod) {
        if (probe == null || !UnsafeHelper.isAvailable()) return false;
        try {
            UUID uuid = probe.getUUID();
            if (uuid == null) return false;
            Integer expectedHash = EXPECTED_IDENTITY_HASHES.get(uuid);
            Long expectedMark = EXPECTED_MARK_WORDS.get(uuid);
            if (expectedHash == null || expectedMark == null) return false;
            int currentHash = System.identityHashCode(probe);
            if (currentHash != expectedHash) {
                Unsafe u = UnsafeHelper.getUnsafe();
                u.putLongVolatile(probe, MARK_WORD_OFFSET, expectedMark);
                if (rec != null) {
                    rec.observe(suspectMod, "jvm_header:mark_word_hash_tampered", "REPAIRED",
                            "0x" + Integer.toHexString(currentHash) + " -> 0x" + Integer.toHexString(expectedHash),
                            null);
                }
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * GameTest 用および検証用: アカシック・プローブの Mark Word（{@code 0L} {@code identityHashCode}）改ざんをシミュレートして即座に検証・修復する。
     */
    public static boolean testMarkWordCanaryTripAndRestore(AkashicProbeEntity probe) {
        if (probe == null || !UnsafeHelper.isAvailable()) return false;
        captureBaseline(probe);
        UUID uuid = probe.getUUID();
        Long expectedMark = uuid != null ? EXPECTED_MARK_WORDS.get(uuid) : null;
        if (expectedMark == null) return false;
        Unsafe u = UnsafeHelper.getUnsafe();
        String suspectMod = AkashicNonEntityProxy.resolveSuspectMod(probe);
        AkashicRecord rec = probe.record();
        // 64-bit HotSpot Mark Word の hash フィールド（bits 8..38）を反転した偽 Mark Word を一時書き込み
        long corruptedMark = (expectedMark ^ (0x12345678L << 8)) | 0x01L;
        u.putLongVolatile(probe, MARK_WORD_OFFSET, corruptedMark);
        try {
            return verifyAndRestoreMarkWord(probe, rec, suspectMod);
        } finally {
            u.putLongVolatile(probe, MARK_WORD_OFFSET, expectedMark);
        }
    }

    /**
     * GameTest 用および検証用: アカシック・プローブの Klass Word 改ざんをシミュレートして即座に検証・修復する。
     */
    public static boolean testKlassWordCanaryTripAndRestore(AkashicProbeEntity probe, Object donorEntity) {
        if (probe == null || donorEntity == null || !klassWordVerified || expectedAkashicKlassWord == 0 || !UnsafeHelper.isAvailable()) {
            return false;
        }
        Unsafe u = UnsafeHelper.getUnsafe();
        int donorKlass = u.getIntVolatile(donorEntity, KLASS_WORD_OFFSET);
        if (donorKlass == 0 || donorKlass == expectedAkashicKlassWord) return false;
        String suspectMod = AkashicNonEntityProxy.resolveSuspectMod(probe);
        AkashicRecord rec = probe.record();
        // 一瞬だけ別エンティティ（豚等）の Klass Word を書き込み、直後に verifyAndRestoreKlassWord で復元させる
        u.putIntVolatile(probe, KLASS_WORD_OFFSET, donorKlass);
        try {
            return verifyAndRestoreKlassWord(probe, rec, suspectMod);
        } finally {
            u.putIntVolatile(probe, KLASS_WORD_OFFSET, expectedAkashicKlassWord);
        }
    }

    /**
     * 毎 Tick の対 Java エージェント・JVM 深層検死。
     */
    public static void tickAgentObservation(AkashicProbeEntity probe, AkashicRecord rec, String suspectMod) {
        // 1. Klass Word（型ポインタ）および Mark Word（identityHashCode）の完全性確認
        verifyAndRestoreKlassWord(probe, rec, suspectMod);

        // 2. Class.reflectionData アンチリフレクション汚染（空配列化）の検知・修復
        inspectAndRepairReflectionData(rec, suspectMod);

        // 3. EntityType.factory（スポーン工場）の null 化・乗っ取り検知・修復
        inspectAndRepairEntityTypeFactory(rec, suspectMod);

        // 4. おとり監視スレッド（Canary Watchdog Thread）の死活・割り込み・タイマー改ざん点検
        inspectAndRepairCanaryWatchdog(rec, suspectMod);

        // 5. Enum 定数（Entity.RemovalReason）・オートボクシングキャッシュ・ThreadLocalMap・MutableCallSite 検死
        inspectAndRepairRemovalReasonEnum(rec, suspectMod);
        inspectAndRepairAutoboxingCache(rec, suspectMod);
        inspectAndRepairThreadLocalsAndCallSite(rec, suspectMod);

        // 6. EventBus からの AkashicNonEntityProxy リスナー追放点検（20 Tick 周期）
        if (probe.tickCount % 20 == 0) {
            inspectAndRepairEventBusRegistration(rec, suspectMod);
        }

        // 7. 外部 Java エージェント・Hidden クラス・バニラ注入フック・AgtCallback・監視スレッド監査（20 Tick 周期）
        if (probe.tickCount % 20 == 1) {
            inspectForeignAgentsAndHooks(rec, suspectMod);
            inspectForeignWatchdogThreads(rec);
        }
    }

    /**
     * SuperSteve の {@code PLZBase.antiRefGetMF} 等が {@code Class.reflectionData} の
     * {@code declaredFields} / {@code declaredMethods} を長さ 0 の空配列に書き換えていないか検査し、汚染されていれば破棄して再生成させる。
     */
    public static void inspectAndRepairReflectionData(AkashicRecord rec, String suspectMod) {
        for (Class<?> target : new Class<?>[]{AkashicProbeEntity.class, LivingEntity.class, Entity.class}) {
            try {
                Field[] fields = target.getDeclaredFields();
                if (fields.length <= 2) {
                    UnsafeHelper.clearReflectionCache(target);
                    Field[] restored = UnsafeHelper.getRealDeclaredFields(target);
                    AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                            "agent_canary:reflection_data_poisoned", "REPAIRED",
                            target.getSimpleName() + " fields=" + fields.length + "->" + restored.length);
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * {@code ModEntities.AKASHIC_PROBE} の {@code EntityType.factory} が {@code null} や外部ラムダに
     * すり替えられて「スポーン根絶」されていないか検査し、正規の {@code AkashicProbeEntity::new} に戻す。
     */
    public static void inspectAndRepairEntityTypeFactory(AkashicRecord rec, String suspectMod) {
        try {
            EntityType<AkashicProbeEntity> type = ModEntities.AKASHIC_PROBE.get();
            Field f = resolveEntityTypeFactoryField();
            if (type == null || f == null) return;
            Object current = f.get(type);
            boolean corrupted = current == null
                    || (originalAkashicFactory != null && current != originalAkashicFactory)
                    || CallerAttribution.isForeign(CallerAttribution.modOf(current.getClass()));
            if (corrupted) {
                EntityType.EntityFactory<AkashicProbeEntity> restored =
                        originalAkashicFactory != null
                                ? (EntityType.EntityFactory<AkashicProbeEntity>) originalAkashicFactory
                                : AkashicProbeEntity::new;
                UnsafeHelper.setFieldValue(type, f.getName(), restored);
                String mod = current != null && CallerAttribution.isForeign(CallerAttribution.modOf(current.getClass()))
                        ? CallerAttribution.modOf(current.getClass())
                        : suspectMod;
                AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                        "registry_tamper:entity_type_factory_hijacked", "REPAIRED",
                        current == null ? "factory=null" : current.getClass().getName());
            }
        } catch (Throwable ignored) {}
    }

    /**
     * おとり監視スレッド（{@code Akashic-Watchdog-Canary}）を起動・維持する。
     */
    private static synchronized void ensureCanaryWatchdogRunning() {
        Thread current = canaryWatchdogThread;
        if (current != null && current.isAlive()) return;
        CANARY_EXPECTED_ALIVE.set(true);
        CANARY_NEXT_CHECK_MS.set(System.currentTimeMillis() + 50L);
        Thread t = new Thread(() -> {
            while (CANARY_EXPECTED_ALIVE.get()) {
                try {
                    long now = System.currentTimeMillis();
                    if (CANARY_NEXT_CHECK_MS.get() <= now + 3_600_000L) {
                        CANARY_NEXT_CHECK_MS.set(now + 50L);
                    }
                    Thread.sleep(50L);
                } catch (InterruptedException ie) {
                    CallerAttribution.Attribution a = CallerAttribution.attribute(null);
                    lastWatchdogAttackerMod = a.callerIsForeign() ? a.callerMod() : a.foreignMod() != null ? a.foreignMod() : a.callerMod();
                    CANARY_INTERRUPT_COUNT.incrementAndGet();
                } catch (Throwable tr) {
                    CallerAttribution.Attribution a = CallerAttribution.attribute(null);
                    lastWatchdogAttackerMod = a.callerIsForeign() ? a.callerMod() : a.foreignMod() != null ? a.foreignMod() : a.callerMod();
                    CANARY_INTERRUPT_COUNT.incrementAndGet();
                }
            }
        }, "Akashic-Watchdog-Canary");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        t.setUncaughtExceptionHandler(CANARY_EXCEPTION_HANDLER);
        t.start();
        canaryWatchdogThread = t;
    }

    /**
     * おとり監視スレッドへの攻撃（{@code Thread.interrupt()}, {@code stop()}, 優先度引き下げ,
     * {@code uncaughtExceptionHandler} 書き換え, 次回チェック時刻の {@code Long.MAX_VALUE} 冷凍）を検知・修復する。
     */
    public static void inspectAndRepairCanaryWatchdog(AkashicRecord rec, String suspectMod) {
        Thread t = canaryWatchdogThread;
        String mod = CallerAttribution.isForeign(lastWatchdogAttackerMod) ? lastWatchdogAttackerMod : suspectMod;
        if (t == null || !t.isAlive()) {
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                    "agent_canary:watchdog_thread_killed", "REPAIRED", "Akashic-Watchdog-Canary stopped");
            ensureCanaryWatchdogRunning();
            return;
        }
        int interrupts = CANARY_INTERRUPT_COUNT.getAndSet(0);
        if (interrupts > 0) {
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                    "agent_canary:watchdog_thread_interrupted", "REPAIRED", "count=" + interrupts);
        }
        if (t.getUncaughtExceptionHandler() != CANARY_EXCEPTION_HANDLER || CANARY_HANDLER_TAMPERED.getAndSet(false)) {
            t.setUncaughtExceptionHandler(CANARY_EXCEPTION_HANDLER);
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                    "agent_canary:watchdog_handler_tampered", "REPAIRED", "UncaughtExceptionHandler restored");
        }
        if (t.getPriority() < Thread.NORM_PRIORITY) {
            int oldPrio = t.getPriority();
            t.setPriority(Thread.NORM_PRIORITY);
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                    "agent_canary:watchdog_priority_lowered", "REPAIRED", "prio=" + oldPrio + "->5");
        }
        long nextMs = CANARY_NEXT_CHECK_MS.get();
        if (nextMs > System.currentTimeMillis() + 3_600_000L) {
            CANARY_NEXT_CHECK_MS.set(System.currentTimeMillis() + 50L);
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, mod,
                    "agent_canary:watchdog_cryo_frozen", "REPAIRED", "nextCheckMs=" + nextMs);
        }
    }

    /** GameTest 等からおとり監視スレッドの次回チェック予定時刻フィールドを取得する */
    public static AtomicLong canaryNextCheckMsForTest() {
        return CANARY_NEXT_CHECK_MS;
    }

    /** GameTest 等からおとり監視スレッドを取得する */
    public static Thread canaryWatchdogThreadForTest() {
        ensureCanaryWatchdogRunning();
        return canaryWatchdogThread;
    }

    /**
     * {@code MinecraftForge.EVENT_BUS} の内部 {@code listeners} マップから
     * {@link AkashicNonEntityProxy} が追放されていないか検査し、外されていれば再登録する。
     */
    public static void inspectAndRepairEventBusRegistration(AkashicRecord rec, String suspectMod) {
        try {
            Field listenersField = UnsafeHelper.findField(MinecraftForge.EVENT_BUS.getClass(), "listeners");
            if (listenersField != null) {
                Object mapObj = listenersField.get(MinecraftForge.EVENT_BUS);
                if (mapObj instanceof ConcurrentHashMap<?, ?> listeners) {
                    if (!listeners.containsKey(AkashicNonEntityProxy.class)) {
                        MinecraftForge.EVENT_BUS.register(AkashicNonEntityProxy.class);
                        rec.observe(suspectMod, "event_bus:akashic_listener_evicted", "REPAIRED",
                                "Re-registered AkashicNonEntityProxy on MinecraftForge.EVENT_BUS", null);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 外部 Java エージェント、{@code InstrumentationImpl} 変換器チェーン、バニラ注入フック、Hidden クラス、
     * および後出し動的アタッチ（{@code VirtualMachine.attach}）を網羅監査する。
     */
    private static void inspectForeignAgentsAndHooks(AkashicRecord rec, String suspectMod) {
        try {
            // A. SuperSteve の AgtCallback.INST 乗っ取り検査
            if (AgentHijackGuard.ensureAgtCallbackNeutralized()) {
                rec.observe("supersteve", "agent_canary:agt_callback_hijack", "DETECTED",
                        "plz.lizi.supersteve.power.AgtCallback", null);
            }

            // B. バニラ Entity / LivingEntity 等に注入された外部バイトコードフックの逆引き＆非破壊ブラックホール無力化
            for (String hookClassName : EprotectPlusLink.getVanillaInjectedHookClasses()) {
                if (hookClassName == null || hookClassName.isEmpty()) continue;
                String mod = resolveModFromClassName(hookClassName, suspectMod);
                if (REPORTED_AGENT_ITEMS.add("hook:" + hookClassName)) {
                    rec.observe(mod, "agent_canary:vanilla_injected_hook:" + simpleName(hookClassName),
                            "DETECTED", hookClassName, null);
                }
                int dot = hookClassName.lastIndexOf('.');
                if (dot > 0 && CallerAttribution.isForeign(mod)) {
                    int hollowed = hollowOutVanillaInjectedHelpers(hookClassName.substring(0, dot + 1));
                    if (hollowed > 0) {
                        rec.observe(mod, "agent_counter:injected_helpers_hollowed", "NEUTRALIZED",
                                hookClassName + " maps=" + hollowed, null);
                    }
                }
            }

            // C. 外部 ClassFileTransformer / Hidden クラス / Bootstrap 注入クラス / 後出し動的アタッチの検査
            boolean firstPass = !initialAgentSnapshotTaken;
            for (Class<?> foreign : EprotectPlusLink.findForeignAgentAndHiddenClasses()) {
                if (foreign == null) continue;
                String name = foreign.getName();
                String mod = CallerAttribution.modOf(foreign);
                if (!CallerAttribution.isForeign(mod)) mod = suspectMod;

                boolean isNewAfterInit = ! INITIAL_FOREIGN_CLASSES.add(name) ? false : !firstPass;
                if (isNewAfterInit && REPORTED_AGENT_ITEMS.add("late_attach:" + name)) {
                    rec.observe(mod, "agent_canary:late_dynamic_attach:" + foreign.getSimpleName(),
                            "DETECTED", name, null);
                }
                if (foreign.isHidden() && REPORTED_AGENT_ITEMS.add("hidden:" + name)) {
                    rec.observe(mod, "agent_canary:hidden_class:" + foreign.getSimpleName(),
                            "DETECTED", name, null);
                }
                if (ClassFileTransformer.class.isAssignableFrom(foreign) && REPORTED_AGENT_ITEMS.add("transformer:" + name)) {
                    boolean hasHalt = EprotectPlusLink.hasHaltOrExitTrap(foreign);
                    rec.observe(mod,
                            (hasHalt ? "agent_canary:halt_trap_transformer:" : "agent_canary:foreign_transformer:") + foreign.getSimpleName(),
                            "DETECTED", name + (hasHalt ? " (Runtime.halt trap)" : ""), null);
                }
            }
            initialAgentSnapshotTaken = true;
        } catch (Throwable ignored) {}
    }

    /**
     * 常駐スレッドの中から外部 MOD の監視スレッド（Watchdog Thread）を検出して記録する。
     */
    private static void inspectForeignWatchdogThreads(AkashicRecord rec) {
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) root = root.getParent();
            Thread[] threads = new Thread[Math.min(root.activeCount() + 32, 256)];
            int count = root.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread t = threads[i];
                if (t == null || t == canaryWatchdogThread || !t.isAlive()) continue;
                Class<?> tc = t.getClass();
                String mod = CallerAttribution.modOf(tc);
                if (!CallerAttribution.isForeign(mod)) {
                    Field targetField = UnsafeHelper.findField(Thread.class, "target");
                    if (targetField != null) {
                        Object runnable = targetField.get(t);
                        if (runnable != null) {
                            tc = runnable.getClass();
                            mod = CallerAttribution.modOf(tc);
                        }
                    }
                }
                if (CallerAttribution.isForeign(mod) && REPORTED_AGENT_ITEMS.add("watchdog:" + tc.getName())) {
                    rec.observe(mod, "agent_canary:foreign_watchdog_thread:" + tc.getSimpleName(),
                            "DETECTED", "thread=" + t.getName() + " class=" + tc.getName(), null);
                    int dot = tc.getName().lastIndexOf('.');
                    if (dot > 0) {
                        int frozen = freezeForeignWatchdogThreads(tc.getName().substring(0, dot + 1));
                        if (frozen > 0) {
                            rec.observe(mod, "agent_counter:watchdog_threads_frozen", "NEUTRALIZED",
                                    "class=" + tc.getName() + " frozen=" + frozen, null);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 【対エージェント仕掛け ④】敵エージェント・凶悪 MOD の監視スレッド（Watchdog Thread）の「無血冷凍（仮想デッドロック）」。
     * <p>
     * {@code Thread.interrupt()} や {@code Thread.stop()} を呼ぶと {@code catch (Throwable) { Runtime.halt(1); }} の
     * 自爆トラップを踏むため、例外は一切投げず、スレッド優先度を最低（{@link Thread#MIN_PRIORITY}）に落とした上で、
     * スレッドおよび Runnable 内部のタイマー・次回チェック時刻（{@code long} フィールド）を {@code Long.MAX_VALUE} に書き換える。
     * </p>
     *
     * @param packagePrefix 対象 MOD のパッケージ接頭辞（例: {@code "net.mcreator.transfinityimproved."}）
     * @return 無血冷凍した外部スレッド数
     */
    public static int freezeForeignWatchdogThreads(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isEmpty()
                || packagePrefix.startsWith("java.") || packagePrefix.startsWith("net.minecraft.")
                || (packagePrefix.startsWith("com.automatavalkyries.")
                && !packagePrefix.startsWith("com.automatavalkyries.gametest.fake."))) {
            return 0;
        }
        int frozen = 0;
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) root = root.getParent();
            Thread[] threads = new Thread[Math.min(root.activeCount() + 32, 256)];
            int count = root.enumerate(threads, true);
            Field targetField = UnsafeHelper.findField(Thread.class, "target");
            for (int i = 0; i < count; i++) {
                Thread t = threads[i];
                if (t == null || t == Thread.currentThread() || t == canaryWatchdogThread || !t.isAlive()) continue;
                Object runnable = targetField != null ? targetField.get(t) : null;
                boolean match = t.getClass().getName().startsWith(packagePrefix)
                        || (runnable != null && runnable.getClass().getName().startsWith(packagePrefix));
                if (!match) continue;
                try {
                    t.setPriority(Thread.MIN_PRIORITY);
                } catch (Throwable ignored) {}
                pushScheduleFieldsToInfinity(t);
                if (runnable != null) {
                    pushScheduleFieldsToInfinity(runnable);
                }
                frozen++;
            }
        } catch (Throwable ignored) {}
        return frozen;
    }

    private static void pushScheduleFieldsToInfinity(Object target) {
        if (target == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Thread.class && c != Object.class; c = c.getSuperclass()) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getType() == long.class) {
                        f.setLong(target, Long.MAX_VALUE / 2);
                    } else if (f.getType() == AtomicLong.class) {
                        Object al = f.get(target);
                        if (al instanceof AtomicLong atomic) {
                            atomic.set(Long.MAX_VALUE / 2);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 【対エージェント仕掛け ① & ⑤】バニラクラスに注入された敵エージェントの静的ヘルパー（{@code PreatorGodHelper} 等）を、
     * {@code ClassFileTransformer} リストから一切外さずに（{@code Runtime.halt} 自爆を回避しつつ）
     * 内部の {@code Map} / {@code Set} を「何を入れても捨てるブラックホールコレクション」に差し替えて骨抜きにする。
     */
    public static int hollowOutVanillaInjectedHelpers(String targetPackagePrefix) {
        if (targetPackagePrefix == null || targetPackagePrefix.isEmpty()) return 0;
        int hollowed = 0;
        for (String hookClassName : EprotectPlusLink.getVanillaInjectedHookClasses()) {
            if (hookClassName == null || !hookClassName.startsWith(targetPackagePrefix)) continue;
            try {
                Class<?> hookClass = Class.forName(hookClassName, false, AkashicAgentCanary.class.getClassLoader());
                if (!UnsafeHelper.isInitialized(hookClass) || EprotectPlusLink.hasHaltOrExitTrap(hookClass)) continue;
                for (Field f : hookClass.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    if (Map.class == f.getType() || ConcurrentHashMap.class == f.getType()) {
                        UnsafeHelper.setStaticFinalField(hookClass, f.getName(), new BlackHoleConcurrentMap<>());
                        hollowed++;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return hollowed;
    }

    /** 何を put しても常に空として振る舞うブラックホール ConcurrentHashMap */
    public static final class BlackHoleConcurrentMap<K, V> extends ConcurrentHashMap<K, V> {
        @Override
        public V put(K key, V value) {
            return null;
        }

        @Override
        public V putIfAbsent(K key, V value) {
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public V get(Object key) {
            return null;
        }
    }

    private static String resolveModFromClassName(String className, String fallback) {
        try {
            Class<?> c = Class.forName(className, false, AkashicAgentCanary.class.getClassLoader());
            String mod = CallerAttribution.modOf(c);
            if (CallerAttribution.isForeign(mod)) return mod;
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static String simpleName(String className) {
        int dot = Math.max(className.lastIndexOf('.'), className.lastIndexOf('/'));
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    // =========================================================================
    // 超マイナー JVM 干渉（RemovalReason Enum 破壊 / オートボクシングキャッシュ汚染 / ThreadLocalMap 走査 / MutableCallSite 乗っ取り）検死・修復
    // =========================================================================

    private static Field removalDestroyField;
    private static Field removalSaveField;
    private static Field enumOrdinalField;
    private static Field booleanValueField;
    private static Field integerValueField;
    private static Field threadLocalsField;
    private static Field threadLocalMapTableField;
    private static Field threadLocalEntryValueField;

    /**
     * バニラ {@link Entity.RemovalReason} Enum 定数の内部フィールド（{@code destroy}, {@code save}, {@code ordinal}）が
     * {@code Unsafe} 等で書き換えられていないか検査・修復する。
     */
    public static void inspectAndRepairRemovalReasonEnum(AkashicRecord rec, String suspectMod) {
        try {
            verifyOneRemovalReason(Entity.RemovalReason.KILLED, true, false, 0, rec, suspectMod);
            verifyOneRemovalReason(Entity.RemovalReason.DISCARDED, true, false, 1, rec, suspectMod);
            verifyOneRemovalReason(Entity.RemovalReason.UNLOADED_TO_CHUNK, false, true, 2, rec, suspectMod);
            verifyOneRemovalReason(Entity.RemovalReason.UNLOADED_WITH_PLAYER, false, false, 3, rec, suspectMod);
            verifyOneRemovalReason(Entity.RemovalReason.CHANGED_DIMENSION, false, false, 4, rec, suspectMod);
        } catch (Throwable ignored) {}
    }

    private static void verifyOneRemovalReason(
            Entity.RemovalReason reason,
            boolean expectedDestroy,
            boolean expectedSave,
            int expectedOrdinal,
            AkashicRecord rec,
            String suspectMod
    ) {
        if (reason.shouldDestroy() == expectedDestroy
                && reason.shouldSave() == expectedSave
                && reason.ordinal() == expectedOrdinal) {
            return;
        }
        try {
            if (removalDestroyField == null || removalSaveField == null) {
                removalDestroyField = UnsafeHelper.findField(Entity.RemovalReason.class, "destroy", "f_146958_");
                removalSaveField = UnsafeHelper.findField(Entity.RemovalReason.class, "save", "f_146959_");
            }
            if (enumOrdinalField == null) {
                enumOrdinalField = UnsafeHelper.findField(Enum.class, "ordinal");
            }
            boolean badDestroy = reason.shouldDestroy();
            boolean badSave = reason.shouldSave();
            int badOrd = reason.ordinal();
            if (removalDestroyField != null) UnsafeHelper.setFieldValue(reason, removalDestroyField.getName(), expectedDestroy);
            if (removalSaveField != null) UnsafeHelper.setFieldValue(reason, removalSaveField.getName(), expectedSave);
            if (enumOrdinalField != null) UnsafeHelper.setFieldValue(reason, enumOrdinalField.getName(), expectedOrdinal);
            AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                    "enum_tamper:removal_reason_corrupted", "REPAIRED",
                    reason.name() + "(destroy=" + badDestroy + " save=" + badSave + " ord=" + badOrd + ")");
        } catch (Throwable ignored) {}
    }

    /**
     * JVM のオートボクシングキャッシュ（{@link Boolean#TRUE}, {@link Boolean#FALSE}, {@link Integer#valueOf(int)} の {@code -128..127}）が
     * {@code Unsafe} で内部値汚染されていないか検査・修復する。
     */
    public static void inspectAndRepairAutoboxingCache(AkashicRecord rec, String suspectMod) {
        try {
            Unsafe unsafe = UnsafeHelper.getUnsafe();
            if (unsafe == null) return;
            if (booleanValueField == null) {
                booleanValueField = UnsafeHelper.findField(Boolean.class, "value");
            }
            long boolOffset = UnsafeHelper.objectFieldOffset(booleanValueField);
            if (boolOffset >= 0) {
                boolean trueVal = unsafe.getBoolean(Boolean.TRUE, boolOffset);
                boolean falseVal = unsafe.getBoolean(Boolean.FALSE, boolOffset);
                if (!trueVal || falseVal) {
                    unsafe.putBoolean(Boolean.TRUE, boolOffset, true);
                    unsafe.putBoolean(Boolean.FALSE, boolOffset, false);
                    AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                            "jvm_cache:autoboxing_cache_poisoned", "REPAIRED", "Boolean.TRUE/FALSE value restored");
                }
            }

            if (integerValueField == null) {
                integerValueField = UnsafeHelper.findField(Integer.class, "value");
            }
            long intOffset = UnsafeHelper.objectFieldOffset(integerValueField);
            if (intOffset >= 0) {
                for (int expected = -128; expected <= 127; expected++) {
                    Integer boxed = Integer.valueOf(expected);
                    // 注意: boxed.intValue() は HotSpot C2 の -XX:+EliminateAutoBox 最適化により
                    // Integer.valueOf(expected).intValue() -> expected に定数畳み込みされるため、Unsafe.getInt で実メモリを直接読む
                    int actual = unsafe.getInt(boxed, intOffset);
                    if (actual != expected) {
                        unsafe.putInt(boxed, intOffset, expected);
                        AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                                "jvm_cache:autoboxing_cache_poisoned", "REPAIRED",
                                "Integer.valueOf(" + expected + ") was " + actual);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * {@code Thread.currentThread().threadLocals}（{@code ThreadLocalMap}）直接走査によるおとり {@link ThreadLocal} 破壊検知、
     * 外部 MOD のアクティブ {@code ThreadLocal} エントリ検知、およびおとり {@link MutableCallSite} のターゲットすり替え検知・修復。
     */
    public static void inspectAndRepairThreadLocalsAndCallSite(AkashicRecord rec, String suspectMod) {
        // 1. おとり ThreadLocal カナリアの点検
        try {
            Boolean guardVal = AKASHIC_GUARD_THREAD_LOCAL.get();
            if (!Boolean.TRUE.equals(guardVal)) {
                AKASHIC_GUARD_THREAD_LOCAL.set(Boolean.TRUE);
                AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                        "thread_local:canary_tampered", "REPAIRED",
                        "AKASHIC_GUARD_THREAD_LOCAL=" + guardVal);
            }
        } catch (Throwable ignored) {}

        // 2. Thread.currentThread().threadLocals 内部テーブルの直接走査
        try {
            if (threadLocalsField == null) {
                threadLocalsField = UnsafeHelper.findField(Thread.class, "threadLocals");
            }
            Object tlMap = threadLocalsField != null ? threadLocalsField.get(Thread.currentThread()) : null;
            if (tlMap != null) {
                if (threadLocalMapTableField == null) {
                    threadLocalMapTableField = UnsafeHelper.findField(tlMap.getClass(), "table");
                }
                Object table = threadLocalMapTableField != null ? threadLocalMapTableField.get(tlMap) : null;
                if (table != null) {
                    int len = Array.getLength(table);
                    for (int i = 0; i < len; i++) {
                        Object entry = Array.get(table, i);
                        if (!(entry instanceof Reference<?> ref)) continue;
                        Object tlKey = ref.get();
                        if (tlKey == null || tlKey == AKASHIC_GUARD_THREAD_LOCAL) continue;
                        if (threadLocalEntryValueField == null) {
                            threadLocalEntryValueField = UnsafeHelper.findField(entry.getClass(), "value");
                        }
                        Object val = threadLocalEntryValueField != null ? threadLocalEntryValueField.get(entry) : null;
                        if (val == null || Boolean.FALSE.equals(val)) continue;
                        Class<?> ownerClass = tlKey.getClass() != ThreadLocal.class ? tlKey.getClass() : val.getClass();
                        String mod = CallerAttribution.modOf(ownerClass);
                        boolean isFakeTest = ownerClass.getName().startsWith("com.automatavalkyries.gametest.fake.");
                        if ((CallerAttribution.isForeign(mod) || isFakeTest)
                                && !ownerClass.getName().startsWith("net.minecraft.")
                                && !ownerClass.getName().startsWith("net.minecraftforge.")
                                && !ownerClass.getName().startsWith("java.")) {
                            String reportMod = CallerAttribution.isForeign(mod) ? mod : suspectMod;
                            if (REPORTED_AGENT_ITEMS.add("tl:" + ownerClass.getName()) || isFakeTest) {
                                rec.observe(reportMod, "thread_local:foreign_active_entry", "DETECTED",
                                        ownerClass.getName() + "=" + val, null);
                            }
                            if (isFakeTest && threadLocalEntryValueField != null) {
                                UnsafeHelper.setFieldValue(entry, threadLocalEntryValueField.getName(), null);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. おとり MutableCallSite（invokedynamic ターゲット）の点検・修復
        try {
            MethodHandle currentTarget = CANARY_CALL_SITE.getTarget();
            float result = -1.0F;
            try {
                result = (float) CANARY_CALL_SITE.dynamicInvoker().invokeExact();
            } catch (Throwable ignored) {}
            if (currentTarget != ORIGINAL_CALL_SITE_TARGET || result != 100.0F) {
                CANARY_CALL_SITE.setTarget(ORIGINAL_CALL_SITE_TARGET);
                AkashicNonEntityProxy.broadcastGlobalObservation(rec, suspectMod,
                        "agent_canary:callsite_target_hijacked", "REPAIRED",
                        "val=" + result + " -> 100.0F");
            }
        } catch (Throwable ignored) {}
    }
}
