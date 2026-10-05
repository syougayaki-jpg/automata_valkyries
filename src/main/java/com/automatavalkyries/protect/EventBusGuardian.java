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

            // 1. Omni-Mobs 等による EventBus 乗っ取りの正常化
            if (bus.getClass().getName().endsWith("MetapotentFlashfurEventBus")) {
                AnnihilationExecutionAgent.sanitizeEventBus();
                bus = MinecraftForge.EVENT_BUS;
                repaired = true;
            }

            // 2. リスナーリストがクリアされていないか点検
            boolean needsReRegister = false;
            try {
                Field listenersField = bus.getClass().getDeclaredField("listeners");
                listenersField.setAccessible(true);
                Object listenersObj = listenersField.get(bus);
                if (listenersObj instanceof Map<?, ?> map) {
                    if (map.isEmpty() || !map.containsKey(EprotectForgeEvents.class)) {
                        needsReRegister = true;
                    }
                }
            } catch (Throwable ignored) {
                // フィールドが取得できない場合（難読化等）は安全のためスキップ
            }

            if (needsReRegister) {
                MinecraftForge.EVENT_BUS.register(EprotectForgeEvents.class);
                MinecraftForge.EVENT_BUS.register(EprotectManager.getInstance());
                AttackLog.repaired(null, "event_bus_listeners_restored");
                AutomataValkyries.LOGGER.warn("[EventBusGuardian] Detected EventBus listener clearing! Re-registered Eprotect event listeners.");
                repaired = true;
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[EventBusGuardian] EventBus check failed", t);
        }
        return repaired;
    }
}
