package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import net.minecraft.server.MinecraftServer;

/**
 * メインスレッドの停止（無限ループ攻撃・ハング）を別スレッドから見張り、原因調査用にスタックトレースを出すウォッチドッグ。
 * <p>
 * 見張るだけで、エンティティ・ワールドには一切触れない。ワールドはメインスレッドからしか安全に書き換えられないため
 * （例: {@code setPos} は区画をまたぐと {@code PersistentEntitySectionManager} の区画を書き換える）、
 * 保護対象の修復はすべてメインスレッドの {@link Eprotect#repair()} が担う。
 * </p>
 * <p>
 * 進み具合はメインループが毎周進める {@link MinecraftServer#getNextTickTime()} で判定する。
 * ServerTickEvent はシングルプレイの一時停止中に発火しないので、それを基準にすると一時停止を停止と誤検出する。
 * </p>
 */
public final class EprotectWatchdogAgent implements Runnable {

    private static final EprotectWatchdogAgent INSTANCE = new EprotectWatchdogAgent();
    /** 同じ停止について警告を繰り返す間隔 */
    private static final long WARNING_INTERVAL_MS = 5000L;
    private static final int STACK_DEPTH = 25;

    private volatile boolean running = false;
    private volatile MinecraftServer server;
    private Thread thread;

    /** 最後にメインループの進行を確認した時刻（ウォッチドッグのスレッドだけが書く） */
    private volatile long lastProgressMillis = 0L;
    private long lastNextTickTime = Long.MIN_VALUE;
    private long lastWarningMillis = 0L;

    private EprotectWatchdogAgent() {}

    public static EprotectWatchdogAgent getInstance() {
        return INSTANCE;
    }

    /** メインループが回り始めてから呼ぶ（ワールド準備中は数秒止まるのが正常なので、ServerStarting では早すぎる） */
    public synchronized void start(MinecraftServer server) {
        this.server = server;
        this.lastProgressMillis = System.currentTimeMillis();
        this.lastNextTickTime = Long.MIN_VALUE;
        this.lastWarningMillis = 0L;
        if (thread != null && thread.isAlive()) return;

        running = true;
        thread = new Thread(this, "AutomataValkyries-EprotectWatchdog");
        thread.setDaemon(true);
        thread.start();
        AutomataValkyries.LOGGER.info("[eprotect-watchdog] Started");
    }

    /** 終了処理（保存）は数秒かかるのが正常なので、停止処理に入った時点で見張りをやめる */
    public synchronized void stop() {
        running = false;
        server = null;
        if (thread != null) {
            thread.interrupt();
            thread = null;
            AutomataValkyries.LOGGER.info("[eprotect-watchdog] Stopped");
        }
    }

    public boolean isRunning() {
        Thread t = thread;
        return running && t != null && t.isAlive();
    }

    public long getLastProgressMillis() {
        return lastProgressMillis;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(EprotectConfig.get(EprotectConfig.WATCHDOG_INTERVAL_MS));
                if (!running) break;
                if (EprotectConfig.get(EprotectConfig.WATCHDOG_ENABLED)) {
                    checkMainThread();
                }
            } catch (InterruptedException e) {
                if (!running) break;
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.error("[eprotect-watchdog] Unexpected error in watchdog loop", t);
            }
        }
    }

    private void checkMainThread() {
        MinecraftServer current = server;
        if (current == null) return;

        // 止まった（または別のワールドに替わって古くなった）サーバーを見続けない。停止の通知が届かなくても誤警告を出し続けない
        Thread running = current.getRunningThread();
        if (current.isStopped() || (running != null && !running.isAlive())) {
            if (server == current) server = null;
            AutomataValkyries.LOGGER.info("[eprotect-watchdog] The watched server has stopped; idle until the next world starts");
            return;
        }

        long now = System.currentTimeMillis();
        long nextTickTime = current.getNextTickTime();
        if (nextTickTime != lastNextTickTime) {
            lastNextTickTime = nextTickTime;
            lastProgressMillis = now;
            return;
        }

        long elapsed = now - lastProgressMillis;
        int threshold = EprotectConfig.get(EprotectConfig.WATCHDOG_HANG_THRESHOLD_MS);
        if (elapsed < threshold || now - lastWarningMillis < WARNING_INTERVAL_MS) return;
        lastWarningMillis = now;

        Thread mainThread = current.getRunningThread();
        StringBuilder sb = new StringBuilder();
        sb.append("Main server thread has not advanced for ").append(elapsed)
                .append(" ms (threshold ").append(threshold).append(" ms)");
        if (mainThread != null) {
            sb.append(", state ").append(mainThread.getState());
            StackTraceElement[] traces = mainThread.getStackTrace();
            for (int i = 0; i < Math.min(traces.length, STACK_DEPTH); i++) {
                sb.append("\n    at ").append(traces[i]);
            }
        }
        AutomataValkyries.LOGGER.warn("[eprotect-watchdog] {}", sb);
    }
}
