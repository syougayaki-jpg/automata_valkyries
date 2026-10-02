package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayDeque;

/**
 * Omni-Mobs の「全体のスポーン禁止」の詰まりを外す。
 * <p>
 * Omni-Mobs は Flashfur の攻撃・Metapotent Flashfur の消去と即死の間だけ、{@code EntitySpawnController.pushGlobalSpawningAllowed(false)}
 * でワールド全体のエンティティの追加を止め、終わりに {@code popGlobalSpawningAllowed()} で戻す。途中で例外・打ち切り（抹殺で攻撃が
 * 途切れる等）が起きると pop されずに残り、以後ずっと mob がわかず、スポーンエッグも使えなくなる（2026-10-02）。
 * </p>
 * push と pop は 1 回の呼び出しの中で対になるので、サーバーの Tick の終わり（どの攻撃も途中ではない）に残っていれば取り残しと分かる。
 * そのときだけ空にして記録する。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class OmniMobsSpawnUnstick {

    private static Field stackField;
    private static Object controller;
    private static boolean unavailable;

    private OmniMobsSpawnUnstick() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || unavailable) return;
        ArrayDeque<?> stack = stack();
        if (stack == null || stack.isEmpty()) return;
        AutomataValkyries.LOGGER.warn("[omnimobs] Global entity spawning was left blocked ({} unmatched push(es)); restoring spawning", stack.size());
        stack.clear();
    }

    /** Omni-Mobs の禁止の積み重ね（Omni-Mobs が無ければ null） */
    static ArrayDeque<?> stack() {
        try {
            if (stackField == null) {
                if (!ModList.get().isLoaded("omnimobs")) {
                    unavailable = true;
                    return null;
                }
                Class<?> type = Class.forName("flashfur.omnimobs.entities.base.EntitySpawnController");
                controller = type.getMethod("getInstance").invoke(null);
                stackField = type.getDeclaredField("globalEntitySpawningAllowed");
                stackField.setAccessible(true);
            }
            return (ArrayDeque<?>) stackField.get(controller);
        } catch (Throwable t) {
            unavailable = true;
            AutomataValkyries.LOGGER.debug("[omnimobs] Spawn controller not available: {}", t.toString());
            return null;
        }
    }
}
