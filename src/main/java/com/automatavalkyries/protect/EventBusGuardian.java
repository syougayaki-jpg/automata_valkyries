package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Forge EventBus の保護・自己修復ガーディアン（Eprotect+ 案4）。
 * <p>
 * 他 MOD（ACE, Omni-Mobs 等）による EventBus の乗っ取りや、
 * リフレクションによる listeners.clear()（全リスナー抹消）を常時検知し、
 * 即座にバニラ EventBus への修復および Eprotect リスナーの再登録を断行する。
 * </p>
 */
public final class EventBusGuardian {

    private static volatile IEventBus originalEventBus;

    private EventBusGuardian() {}

    /**
     * EventBus の正常性とリスナー生存を点検し、破壊されていれば復元する。
     *
     * @return リスナーの再登録や EventBus 復元が行われたか
     */
    public static boolean ensureEventBusIntegrity() {
        boolean repaired = false;
        try {
            IEventBus bus = MinecraftForge.EVENT_BUS;
            if (bus == null) return false;

            if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                    com.automatavalkyries.protect.attack.CallerAttribution.modOf(bus.getClass()))) {
                if (originalEventBus == null) {
                    originalEventBus = bus;
                }
            } else {
                // 1. 他 MOD による EventBus 乗っ取りの正常化（個別＋準汎用）
                if (bus.getClass().getName().endsWith("MetapotentFlashfurEventBus")) {
                    AnnihilationExecutionAgent.sanitizeEventBus();
                }
                if (com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                        com.automatavalkyries.protect.attack.CallerAttribution.modOf(MinecraftForge.EVENT_BUS.getClass()))) {
                    restoreHijackedEventBusGenerically(MinecraftForge.EVENT_BUS);
                }
                bus = MinecraftForge.EVENT_BUS;
                repaired = !com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                        com.automatavalkyries.protect.attack.CallerAttribution.modOf(bus.getClass()));
            }

            // 2. リスナーリストがクリアされていないか点検
            // 登録の鍵は、static の購読（クラス）と、インスタンスの購読（EprotectManager）で別。片方だけ外されても見つけて、外れた方だけ登録し直す
            // （両方を登録し直すと、残っていた方の処理が二重に走る。2026-10-22: Madness の回で EprotectManager の停止・起動の通知だけ届かなかった）
            boolean missingForgeEvents = false;
            boolean missingManager = false;
            try {
                Field listenersField = findFieldInHierarchy(bus.getClass(), "listeners");
                if (listenersField != null) {
                    listenersField.setAccessible(true);
                    Object listenersObj = listenersField.get(bus);
                    if (listenersObj instanceof Map<?, ?> map) {
                        missingForgeEvents = !map.containsKey(EprotectForgeEvents.class);
                        missingManager = !map.containsKey(EprotectManager.getInstance());
                    }
                }
            } catch (Throwable ignored) {
                // フィールドが取得できない場合（難読化等）は安全のためスキップ
            }

            if (missingForgeEvents || missingManager) {
                if (missingForgeEvents) MinecraftForge.EVENT_BUS.register(EprotectForgeEvents.class);
                if (missingManager) MinecraftForge.EVENT_BUS.register(EprotectManager.getInstance());
                AttackLog.repaired(null, "event_bus_listeners_restored");
                AutomataValkyries.LOGGER.warn("[EventBusGuardian] Detected EventBus listener removal (forgeEvents missing: {}, manager missing: {}); re-registered them.",
                        missingForgeEvents, missingManager);
                repaired = true;
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[EventBusGuardian] EventBus check failed", t);
        }
        return repaired;
    }

    private static volatile long lastPollMillis;

    /**
     * Forge のイベントを使わない点検の入口（{@code MinecraftServer.tickServer} の Mixin から。{@code EventBusPollMixin}）。
     * 購読が外されると、{@link EprotectManager#onServerTick} も止まって、そこからの点検は二度と走らない。そのために、イベントに頼らない所から
     * 1 秒に 1 回、購読の点検と、見張り（ウォッチドッグ・辞書補助）の起動し直しを行う
     */
    public static void poll(net.minecraft.server.MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastPollMillis < 1000L) return;
        lastPollMillis = now;
        ensureEventBusIntegrity();
        try {
            if (server.isRunning() && !EprotectWatchdogAgent.getInstance().isRunning()) {
                AutomataValkyries.LOGGER.warn("[EventBusGuardian] The watchdog was not running on a running server (the start event did not arrive); starting it");
                EprotectWatchdogAgent.getInstance().start(server);
            }
            if (server.isRunning() && !com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().isRunning()) {
                AutomataValkyries.LOGGER.warn("[EventBusGuardian] The flag watch was not running on a running server; starting it");
                com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().start(server);
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[EventBusGuardian] Poll failed", t);
        }
    }

    /**
     * 乗っ取られた {@link MinecraftForge#EVENT_BUS} を MOD 名に頼らず汎用的に正規の EventBus へ戻す。
     * <ol>
     *   <li>乗っ取ったバスの関連クラスに引数なしの {@code updateEventBus()} / {@code restoreEventBus()} があれば先に呼ぶ</li>
     *   <li>それでも他 MOD のバスのままなら、{@code net.minecraftforge.eventbus.EventBus} の全フィールド（購読者一覧・busID 等）を
     *       引き継いだ正規の {@code EventBus} インスタンス（または保持していた元のバス）へ差し替える</li>
     * </ol>
     */
    private static void restoreHijackedEventBusGenerically(IEventBus hijacked) {
        try {
            java.util.Set<Class<?>> candidates = new java.util.LinkedHashSet<>();
            candidates.add(hijacked.getClass());
            candidates.addAll(com.automatavalkyries.protect.flag.KillLists.relatedClasses(hijacked.getClass()));
            for (Class<?> candidate : candidates) {
                if (!com.automatavalkyries.util.UnsafeHelper.isInitialized(candidate)) continue;
                for (String methodName : new String[]{"updateEventBus", "restoreEventBus"}) {
                    try {
                        java.lang.reflect.Method m = candidate.getDeclaredMethod(methodName);
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0) {
                            m.setAccessible(true);
                            m.invoke(null);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        IEventBus current = MinecraftForge.EVENT_BUS;
        if (current == null || !com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                com.automatavalkyries.protect.attack.CallerAttribution.modOf(current.getClass()))) {
            return;
        }

        try {
            Class<?> forgeBusClass = Class.forName("net.minecraftforge.eventbus.EventBus");
            IEventBus restored = originalEventBus;
            if (forgeBusClass.isInstance(current)) {
                if (restored == null || !forgeBusClass.equals(restored.getClass())) {
                    restored = (IEventBus) com.automatavalkyries.util.UnsafeHelper.getUnsafe().allocateInstance(forgeBusClass);
                }
                for (Field f : forgeBusClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object val = f.get(current);
                    com.automatavalkyries.util.UnsafeHelper.setFieldValue(restored, f.getName(), val);
                }
            }
            if (restored != null) {
                com.automatavalkyries.util.UnsafeHelper.setStaticFinalField(MinecraftForge.class, "EVENT_BUS", restored);
                AutomataValkyries.LOGGER.info("[EventBusGuardian] Generically restored MinecraftForge.EVENT_BUS from {}",
                        current.getClass().getName());
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[EventBusGuardian] Generic EventBus restoration failed", t);
        }
    }

    /**
     * {@link MinecraftForge#EVENT_BUS} に登録されている購読者のうち、指定したクラスと同じ他 MOD に属するクラスを返す。
     * エンティティ本体から直接参照されていないイベント駆動の管理表（Witherzilla の {@code EventHandle.livingEntities} 等）を
     * 静的な一覧の掃除先に加えるために使う。
     */
    public static java.util.Set<Class<?>> subscriberClassesFor(Class<?> referenceType) {
        if (referenceType == null) return java.util.Set.of();
        String targetMod = com.automatavalkyries.protect.attack.CallerAttribution.modOf(referenceType);
        if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(targetMod)) return java.util.Set.of();
        String refPkg = packageRoot(referenceType.getName());
        java.util.Set<Class<?>> result = new java.util.LinkedHashSet<>();
        try {
            IEventBus bus = MinecraftForge.EVENT_BUS;
            if (bus == null) return result;
            Field listenersField = findFieldInHierarchy(bus.getClass(), "listeners");
            if (listenersField == null) return result;
            listenersField.setAccessible(true);
            if (!(listenersField.get(bus) instanceof Map<?, ?> map)) return result;
            for (Object key : map.keySet()) {
                if (key == null) continue;
                Class<?> subClass = key instanceof Class<?> c ? c : key.getClass();
                String subMod = com.automatavalkyries.protect.attack.CallerAttribution.modOf(subClass);
                if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(subMod)) continue;
                boolean sameMod = targetMod.equals(subMod)
                        && (!"foreign_unnamed".equals(targetMod)
                        || (!refPkg.isEmpty() && packageRoot(subClass.getName()).equals(refPkg)));
                if (sameMod && com.automatavalkyries.util.UnsafeHelper.isInitialized(subClass)) {
                    result.add(subClass);
                    if (result.size() >= 32) break;
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    /**
     * 指定した他 MOD ID に属する EventBus 購読者クラス一覧を返す（ブラックリスト強化保護時の即時発見用）。
     */
    public static java.util.Set<Class<?>> subscriberClassesForMod(String modId) {
        if (modId == null || !com.automatavalkyries.protect.attack.CallerAttribution.isForeign(modId)) {
            return java.util.Set.of();
        }
        java.util.Set<Class<?>> result = new java.util.LinkedHashSet<>();
        try {
            IEventBus bus = MinecraftForge.EVENT_BUS;
            if (bus == null) return result;
            Field listenersField = findFieldInHierarchy(bus.getClass(), "listeners");
            if (listenersField == null) return result;
            listenersField.setAccessible(true);
            if (!(listenersField.get(bus) instanceof Map<?, ?> map)) return result;
            for (Object key : map.keySet()) {
                if (key == null) continue;
                Class<?> subClass = key instanceof Class<?> c ? c : key.getClass();
                if (modId.equals(com.automatavalkyries.protect.attack.CallerAttribution.modOf(subClass))
                        && com.automatavalkyries.util.UnsafeHelper.isInitialized(subClass)) {
                    result.add(subClass);
                    if (result.size() >= 32) break;
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static String packageRoot(String className) {
        int first = className.indexOf('.');
        if (first <= 0) return "";
        int second = className.indexOf('.', first + 1);
        return second > first ? className.substring(0, second) : className.substring(0, first);
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
