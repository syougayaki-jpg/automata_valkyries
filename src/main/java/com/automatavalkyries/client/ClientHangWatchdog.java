package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 手元（クライアント）の描画スレッドが止まったとき、どこで止まっているかをログに残す（2026-10-22。Madness との戦闘中に「応答なし」になり、
 * スレッドダンプが取れなかった）。ワールドの中で、クライアントの Tick が {@value #STALL_MS} ミリ秒以上進まなくなったら、描画スレッドのスタックを
 * {@value #INTERVAL_MS} ミリ秒ごとに最大 {@value #MAX_DUMPS} 回出す。見るだけで何も書き換えない
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientHangWatchdog {

    private static final long STALL_MS = 5_000L;
    private static final long INTERVAL_MS = 5_000L;
    private static final int MAX_DUMPS = 20;
    private static final int DEPTH = 40;

    private static volatile long lastTickMillis = System.currentTimeMillis();
    private static volatile boolean inGame;
    private static volatile Thread renderThread;
    private static volatile boolean started;

    private ClientHangWatchdog() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        renderThread = Thread.currentThread();
        lastTickMillis = System.currentTimeMillis();
        inGame = Minecraft.getInstance().level != null;
        if (!started) start();
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        Thread thread = new Thread(ClientHangWatchdog::run, "AutomataValkyries-ClientHangWatchdog");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        int dumps = 0;
        long lastDump = 0L;
        while (true) {
            try {
                Thread.sleep(1_000L);
                long now = System.currentTimeMillis();
                long stalled = now - lastTickMillis;
                if (stalled < STALL_MS) {
                    dumps = 0;
                    continue;
                }
                Thread target = renderThread;
                if (!inGame || target == null || dumps >= MAX_DUMPS || now - lastDump < INTERVAL_MS) continue;
                lastDump = now;
                dumps++;
                StringBuilder sb = new StringBuilder("The client tick has not advanced for ").append(stalled).append(" ms; render thread state ")
                        .append(target.getState());
                StackTraceElement[] trace = target.getStackTrace();
                for (int i = 0; i < Math.min(trace.length, DEPTH); i++) sb.append("\n    at ").append(trace[i]);
                AutomataValkyries.LOGGER.warn("[client-watchdog] {}", sb);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable ignored) {
                // 見張りが落ちて本体を巻き込まないように
            }
        }
    }
}
