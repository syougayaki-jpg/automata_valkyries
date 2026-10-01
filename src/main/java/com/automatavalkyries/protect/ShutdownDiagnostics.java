package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 終了時に固まる原因調べ（記録のみ。処理は変えない）。
 * <p>
 * サーバーの終了処理は「全ディメンションのチャンクの仕事（読み込み済みのチャンク・解放待ち・札）が無くなるまで」回り続ける。
 * 何かが札を足し続ける・チャンクが解放されないと終わらない。終了中に限り、
 * <ul>
 *   <li>足された札の種類と、足した呼び出し元のスタック（最初の数件）</li>
 *   <li>数秒ごとに、残っている仕事の量と札の種類ごとの数</li>
 * </ul>
 * をログに出す。
 * </p>
 */
public final class ShutdownDiagnostics {

    private static final long REPORT_INTERVAL_MS = 3000;
    private static final int MAX_TICKET_TRACES = 8;

    private static volatile boolean stopping = false;
    private static volatile long stoppingSince = 0;
    private static final Map<String, Long> LAST_REPORT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicInteger TICKET_TRACES = new AtomicInteger();

    private ShutdownDiagnostics() {}

    public static boolean isStopping() {
        return stopping;
    }

    public static void onStopping() {
        stopping = true;
        stoppingSince = System.currentTimeMillis();
        LAST_REPORT.clear();
        TICKET_TRACES.set(0);
    }

    public static void onStopped() {
        stopping = false;
    }

    public static void reportTicket(long chunk, Ticket<?> ticket) {
        if (TICKET_TRACES.incrementAndGet() > MAX_TICKET_TRACES) return;
        AutomataValkyries.LOGGER.warn("[eprotect-shutdown] Ticket {} added at chunk {} during shutdown", ticket, new ChunkPos(chunk),
                new Throwable("ticket added during shutdown"));
    }

    public static void reportPendingWork(ServerLevel level, int loaded, int pendingUnloads, int toDrop, int unloadQueue,
                                         Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets) {
        long now = System.currentTimeMillis();
        String key = level.dimension().location().toString();
        Long last = LAST_REPORT.get(key);
        if (now - stoppingSince < REPORT_INTERVAL_MS || (last != null && now - last < REPORT_INTERVAL_MS)) return;
        LAST_REPORT.put(key, now);
        Map<String, Integer> byType = new TreeMap<>();
        tickets.values().forEach(set -> set.forEach(t -> byType.merge(t.getType().toString(), 1, Integer::sum)));
        AutomataValkyries.LOGGER.warn("[eprotect-shutdown] {} still has work after {} ms: loaded={} pendingUnloads={} toDrop={} unloadQueue={} "
                        + "ticketChunks={} tickets={}", key, now - stoppingSince, loaded, pendingUnloads, toDrop, unloadQueue,
                tickets.size(), byType);
    }
}
