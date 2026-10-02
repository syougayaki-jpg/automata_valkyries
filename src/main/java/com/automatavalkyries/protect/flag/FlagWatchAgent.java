package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 辞書補助の非干渉監視エージェント: 他 MOD がエンティティに付けた状態を読むだけで見張り、
 * 死亡・消去・保護対象への攻撃の直前に変わったのに {@link FlagDictionary} に当たらない名前を、辞書に足す候補として挙げる。
 * <p>
 * 非干渉: エンティティ・ワールド・他 MOD の状態には一切書き込まず、他 MOD のコード（getter）も呼ばない
 * （{@link StateScanner#observe}）。辞書・設定も自分では変えない（足すかどうかはユーザーが決める）。
 * </p>
 * <p>
 * 値の読み取りは、他スレッドから読むと永続データの走査などが壊れうるのでメインスレッドで行い（読むだけなので軽い）、
 * 集計と候補の書き出しはこのエージェントのスレッドで行う。
 * </p>
 * 判定:
 * <ul>
 *   <li>普段の読み取り（{@code sampleIntervalTicks} ごと）で変わった回数 … 普段から動く値（アニメーション・内部の計数）を見分ける</li>
 *   <li>事件（死亡・死亡を経ない消去・保護対象への攻撃）の時点の値を、その個体の前回の読み取りと比べて変わった回数</li>
 * </ul>
 * 事件の直前によく変わり、普段はほとんど変わらない名前ほど点数が高い。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlagWatchAgent implements Runnable {

    private static final FlagWatchAgent INSTANCE = new FlagWatchAgent();

    /** 候補にする、事件の直前に変わった割合の下限 */
    private static final double MIN_EVENT_RATE = 0.3D;
    /** 候補から外す、普段変わる割合の上限 */
    private static final double MAX_NORMAL_RATE = 0.25D;
    /** 集計する名前の上限（溜め込み過ぎない） */
    private static final int MAX_STATS = 20000;
    /** メインスレッドからエージェントへ渡す読み取りの上限（超えた分は捨てて数える） */
    private static final int QUEUE_CAPACITY = 16384;
    /** エージェントが溜まった読み取りを集計する間隔 */
    private static final long PROCESS_INTERVAL_MS = 250L;
    /** 他スレッドからの攻撃の知らせを溜める上限 */
    private static final int MAX_PENDING = 256;
    /** 候補を書き出す間隔 */
    private static final long WRITE_INTERVAL_MS = 30_000L;
    /** 死亡を見た個体を、消去の知らせと重ねて数えないよう覚えておく Tick 数 */
    private static final int DEATH_MEMORY_TICKS = 200;

    /** メインスレッドで読んだ 1 回分（{@code from} は前回から変わった鍵と前回の値） */
    private record Observation(boolean event, String kind, String entityType, List<StateScanner.Observed> current,
                               Map<String, Object> from) {}

    private record PendingEvent(WeakReference<Entity> entity, String kind) {}

    /** 1 つの名前の集計（エージェントのスレッドと、テストではメインスレッドから {@link #stats} の同期下で触る） */
    private static final class Stat {
        final StateScanner.Observed first;
        int samples;
        int normalChanges;
        int eventsSeen;
        int eventChanges;
        int rises;
        int falls;
        final Map<String, Integer> kinds = new LinkedHashMap<>();
        String example = "";

        Stat(StateScanner.Observed first) {
            this.first = first;
        }
    }

    /** 辞書に足す候補 */
    public record Candidate(double score, String key, String name, String mod, int eventChanges, int eventsSeen,
                            int normalChanges, int samples, boolean rising, String kinds, String example) {
        /** 設定 flagDictionary.extra に足すときの 1 行（向きからの推測。足す前に確かめる） */
        public String suggestion() {
            return (rising ? "kill" : "guard") + ":*" + String.join("", FlagDictionary.words(name));
        }
    }

    // ---- メインスレッドだけが触る ------------------------------------------------
    private final Map<UUID, Map<String, StateScanner.Observed>> lastSnapshot = new HashMap<>();
    private final Map<UUID, Long> recentDeaths = new HashMap<>();
    private int tickCounter = 0;

    // ---- スレッドをまたぐ ------------------------------------------------------
    private final ConcurrentLinkedQueue<PendingEvent> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSize = new AtomicInteger();
    private final LinkedBlockingQueue<Observation> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Map<String, Stat> stats = new HashMap<>();
    private volatile List<Candidate> candidates = List.of();
    private volatile long sampleCount = 0;
    private volatile long eventCount = 0;
    private volatile long droppedCount = 0;
    private volatile boolean dirty = false;

    private volatile boolean running = false;
    private Thread thread;

    private FlagWatchAgent() {}

    public static FlagWatchAgent getInstance() {
        return INSTANCE;
    }

    public static Path path() {
        return Paths.get("logs", AutomataValkyries.MODID + "-flag-candidates.txt");
    }

    // ---- 起動・停止 ------------------------------------------------------------

    public synchronized void start(MinecraftServer server) {
        if (thread != null && thread.isAlive()) return;
        running = true;
        thread = new Thread(this, "AutomataValkyries-FlagWatch");
        thread.setDaemon(true);
        thread.start();
        AutomataValkyries.LOGGER.info("[flag-watch] Started");
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        processQueued();
        writeIfDirty();
        lastSnapshot.clear();
        recentDeaths.clear();
        pending.clear();
        pendingSize.set(0);
        tickCounter = 0;
        AutomataValkyries.LOGGER.info("[flag-watch] Stopped");
    }

    public boolean isRunning() {
        Thread t = thread;
        return running && t != null && t.isAlive();
    }

    // ---- 知らせ（どのスレッドからでも） ----------------------------------------------

    /**
     * 保護対象への攻撃を拒否・修復した（{@link com.automatavalkyries.protect.attack.AttackLog} から呼ぶ）。
     * 攻撃の最中に読むと入れ子になるので、その Tick の終わりに読む
     */
    public static void noteAttack(Entity target, String op) {
        if (!enabled() || target.level().isClientSide) return;
        if (INSTANCE.pendingSize.get() >= MAX_PENDING) return;
        int colon = op.indexOf(':');
        INSTANCE.pending.add(new PendingEvent(new WeakReference<>(target), "attack:" + (colon > 0 ? op.substring(0, colon) : op)));
        INSTANCE.pendingSize.incrementAndGet();
    }

    // ---- メインスレッド --------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || !INSTANCE.isRunning()) return;
        INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !enabled() || !INSTANCE.isRunning()) return;
        INSTANCE.recentDeaths.put(entity.getUUID(), entity.level().getGameTime());
        INSTANCE.event(entity, "death");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity instanceof LivingEntity) || !enabled() || !INSTANCE.isRunning()) return;
        Entity.RemovalReason reason = DirectRead.removalReason(entity);
        // 死亡を経た退場（死亡で数えた）・チャンクの読み込み解除・次元移動は事件ではない
        if ((reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
                && INSTANCE.recentDeaths.remove(entity.getUUID()) == null) {
            INSTANCE.event(entity, "erase:" + reason.name().toLowerCase(java.util.Locale.ROOT));
        }
        INSTANCE.lastSnapshot.remove(entity.getUUID());
    }

    private void tick(MinecraftServer server) {
        ++tickCounter;
        Set<UUID> handled = new HashSet<>();
        PendingEvent next;
        while ((next = pending.poll()) != null) {
            pendingSize.decrementAndGet();
            Entity entity = next.entity().get();
            if (entity != null && handled.add(entity.getUUID())) event(entity, next.kind());
        }
        if (tickCounter % EprotectConfig.get(EprotectConfig.FLAG_WATCH_SAMPLE_INTERVAL_TICKS) != 0) return;

        int max = EprotectConfig.get(EprotectConfig.FLAG_WATCH_MAX_ENTITIES);
        Set<UUID> seen = new HashSet<>();
        int tracked = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity) || DirectRead.isRemoved(entity)) continue;
                boolean always = entity instanceof IEprotected;
                if (!always && tracked >= max) continue;
                if (sample(entity)) {
                    seen.add(entity.getUUID());
                    if (!always) tracked++;
                }
            }
        }
        lastSnapshot.keySet().retainAll(seen);
        long now = server.overworld().getGameTime();
        recentDeaths.values().removeIf(tick -> now - tick > DEATH_MEMORY_TICKS);
    }

    /** 普段の読み取り。他 MOD の状態を持っていれば true */
    public boolean sample(Entity entity) {
        Map<String, StateScanner.Observed> current = read(entity);
        if (current.isEmpty()) {
            lastSnapshot.remove(entity.getUUID());
            return false;
        }
        Map<String, StateScanner.Observed> previous = lastSnapshot.put(entity.getUUID(), current);
        if (previous != null) offer(new Observation(false, "sample", typeOf(entity), List.copyOf(current.values()), diff(previous, current)));
        return true;
    }

    /** 事件の時点の読み取り（前回の読み取りが無ければ比べられないので数えない） */
    public void event(Entity entity, String kind) {
        Map<String, StateScanner.Observed> current = read(entity);
        Map<String, StateScanner.Observed> previous = lastSnapshot.put(entity.getUUID(), current);
        if (previous == null || current.isEmpty()) return;
        offer(new Observation(true, kind, typeOf(entity), List.copyOf(current.values()), diff(previous, current)));
    }

    private static Map<String, StateScanner.Observed> read(Entity entity) {
        Map<String, StateScanner.Observed> values = new LinkedHashMap<>();
        StateScanner.observe(entity, observed -> values.put(observed.key(), observed));
        return values;
    }

    private static Map<String, Object> diff(Map<String, StateScanner.Observed> previous, Map<String, StateScanner.Observed> current) {
        Map<String, Object> from = new HashMap<>();
        current.forEach((key, now) -> {
            StateScanner.Observed before = previous.get(key);
            if (before != null && !java.util.Objects.equals(before.value(), now.value())) from.put(key, before.value());
        });
        return from;
    }

    private void offer(Observation observation) {
        if (!queue.offer(observation)) droppedCount++;
    }

    private static String typeOf(Entity entity) {
        return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    // ---- エージェントのスレッド ---------------------------------------------------

    @Override
    public void run() {
        long lastWrite = System.currentTimeMillis();
        while (running) {
            try {
                Thread.sleep(PROCESS_INTERVAL_MS);
                processQueued();
                if (System.currentTimeMillis() - lastWrite >= WRITE_INTERVAL_MS) {
                    lastWrite = System.currentTimeMillis();
                    writeIfDirty();
                }
            } catch (InterruptedException e) {
                if (!running) break;
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.error("[flag-watch] Unexpected error in the watch loop", t);
            }
        }
    }

    /** 取り出しと集計を同じ同期の中で行う（取り出したのに未集計の読み取りを、drain から見落とさないように） */
    private void processQueued() {
        synchronized (stats) {
            Observation observation;
            while ((observation = queue.poll()) != null) process(observation);
        }
    }

    /** 溜まっている読み取りをすべて集計し、候補を作り直す（コマンド・テストから） */
    public void drain() {
        processQueued();
        rebuildCandidates();
    }

    private void process(Observation observation) {
        synchronized (stats) {
            if (observation.event()) eventCount++;
            else sampleCount++;
            for (StateScanner.Observed observed : observation.current()) {
                Stat stat = stats.get(observed.key());
                if (stat == null) {
                    if (stats.size() >= MAX_STATS) continue;
                    stat = new Stat(observed);
                    stats.put(observed.key(), stat);
                }
                Object from = observation.from().get(observed.key());
                if (observation.event()) {
                    stat.eventsSeen++;
                    if (from == null) continue;
                    stat.eventChanges++;
                    stat.kinds.merge(observation.kind(), 1, Integer::sum);
                    if (rose(from, observed.value())) stat.rises++;
                    else stat.falls++;
                    stat.example = from + " → " + observed.value() + " (" + observation.entityType() + " " + observation.kind() + ")";
                } else {
                    stat.samples++;
                    if (from != null) stat.normalChanges++;
                }
            }
            dirty = true;
        }
    }

    private static boolean rose(Object from, Object to) {
        if (from instanceof Boolean a && to instanceof Boolean b) return !a && b;
        if (from instanceof Number a && to instanceof Number b) return b.doubleValue() > a.doubleValue();
        return false;
    }

    private void rebuildCandidates() {
        int minEvents = EprotectConfig.get(EprotectConfig.FLAG_WATCH_MIN_EVENTS);
        List<Candidate> result = new ArrayList<>();
        synchronized (stats) {
            stats.forEach((key, stat) -> {
                String name = stat.first.name();
                // 辞書に当たる名前（扱い済み・時間や回数として触らないと決めたもの）と除外した名前は挙げない
                if (stat.eventChanges < minEvents || !FlagDictionary.classify(name).isEmpty() || FlagDictionary.ignored(name)) return;
                double eventRate = stat.eventChanges / (double) Math.max(1, stat.eventsSeen);
                double normalRate = stat.normalChanges / (double) Math.max(1, stat.samples);
                if (eventRate < MIN_EVENT_RATE || normalRate > MAX_NORMAL_RATE || eventRate <= normalRate * 2.0D) return;
                String kinds = stat.kinds.entrySet().stream().map(e -> e.getKey() + "×" + e.getValue())
                        .collect(java.util.stream.Collectors.joining(", "));
                result.add(new Candidate(stat.eventChanges * (eventRate - normalRate), key, name, stat.first.mod(),
                        stat.eventChanges, stat.eventsSeen, stat.normalChanges, stat.samples, stat.rises >= stat.falls, kinds, stat.example));
            });
        }
        result.sort(Comparator.comparingDouble(Candidate::score).reversed());
        candidates = List.copyOf(result);
    }

    // ---- 表示・書き出し ----------------------------------------------------------

    public List<Candidate> candidates() {
        return candidates;
    }

    public long sampleCount() {
        return sampleCount;
    }

    public long eventCount() {
        return eventCount;
    }

    public long droppedCount() {
        return droppedCount;
    }

    /** 集計を捨てる（各個体の前回の読み取りは残す） */
    public void clear() {
        synchronized (stats) {
            queue.clear();
            stats.clear();
            sampleCount = 0;
            eventCount = 0;
            droppedCount = 0;
            dirty = true;
        }
        candidates = List.of();
    }

    private void writeIfDirty() {
        if (!dirty) return;
        dirty = false;
        rebuildCandidates();
        try {
            write();
        } catch (IOException e) {
            AutomataValkyries.LOGGER.warn("[flag-watch] Could not write {}", path(), e);
        }
    }

    public void write() throws IOException {
        List<Candidate> list = candidates;
        Path path = path();
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("# 辞書補助の監視（読むだけ）: 死亡・消去・保護対象への攻撃の直前に変わった、フラグ辞書に当たらない名前");
            writer.newLine();
            writer.write(String.format("# %s / 普段の読み取り %d 回・事件 %d 件・捨てた読み取り %d 回 / 候補 %d 件",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), sampleCount, eventCount, droppedCount, list.size()));
            writer.newLine();
            writer.write("# 書式: 点数 | MOD 場所:持ち主.名前 | 事件の直前に変わった/見た事件 | 普段変わった/普段の読み取り | 事件の内訳 | 例 | 足すなら（向きからの推測。確かめてから）");
            writer.newLine();
            writer.write("# 足す: 設定 flagDictionary.extra に最後の欄を追加。取り違えなら flagDictionary.ignore に名前を追加");
            writer.newLine();
            for (Candidate c : list) {
                writer.write(String.format("%.2f | %s | %d/%d | %d/%d | %s | %s | %s", c.score(), c.key(), c.eventChanges(), c.eventsSeen(),
                        c.normalChanges(), c.samples(), c.kinds(), c.example(), c.suggestion()));
                writer.newLine();
            }
        }
    }

    private static boolean enabled() {
        return EprotectConfig.get(EprotectConfig.FLAG_WATCH_ENABLED);
    }
}
