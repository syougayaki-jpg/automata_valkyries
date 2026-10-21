package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 時間停止の免除を、動いている個体から真似る（準汎用。2026-10-21 ユーザー指定「タイムストップカウントを準汎用処理できるか」）。
 * <p>
 * 時間停止の MOD は、止めない個体を個体ごとの状態で決めることが多い（EndingLibrary: 同期データ {@code timeStopCount} が正なら動ける）。
 * 保護対象が止められたとき（{@link TickStallGuard#isDriving}）、同じレベルで<b>動いている個体</b>（Tick が進んでいる）の状態と
 * 自分の状態を比べ、辞書が time / stop / freeze 系の CONTROL と見分ける名前のうち、動いている側だけ正（真）になっているものを見つける。
 * 2 回続けて同じ名前が見つかったら、その値を毎 Tick 真似る（数値は動いている側 + 1）。動いている側の値が無くなる（時間停止が終わる）か、
 * 真似ても止まったままなら、自分の値を 0（偽）に戻してやめる。
 * </p>
 * <ul>
 *   <li>+1 にするのは、カウンターが 0 になった個体が時間停止そのものを解除する作り（EndingLibrary の {@code TimeStopUtils.use(false, …)}）で、
 *       保護対象が先に 0 になって解除を引き起こさないため。終わるときは減らさず直接 0 に戻す</li>
 *   <li>読むのも書くのも {@link StateScanner}（同期データと足されたフィールドだけ。永続データ・Capability は書けないので対象外）。
 *       MOD 名もクラス名も知らない。見つからなければ従来どおり {@link TickStallGuard} が Tick を直接回す</li>
 *   <li>真似ている名前は、掃除（{@link ForeignStateCleaner}）から一時的に外す（{@link #mirroredNames}。辞書の分類は変えない）。
 *       外さないと毎 Tick 0 に戻されて免除が効かず、修復の件数が積もって退避し、その MOD が自動でブラックリストに載る</li>
 * </ul>
 * 個体のサーバー Tick の中とマネージャーの周期処理の両方から呼ぶ（{@link Eprotect#repair}）。サーバースレッドだけ。
 */
final class TimeStopExemption {

    /** 免除を探す間隔（Tick） */
    private static final int DISCOVER_INTERVAL = 5;
    /** 同じ候補が続けて見つかった回数がこれに届いたら真似始める */
    private static final int CONFIRMATIONS = 2;
    /** 状態を比べる動いている個体の上限（近い順） */
    private static final int MAX_MOVERS = 8;
    private static final double RADIUS = 192.0D;
    /** 真似ているのにこの Tick 数より長く止まったままなら、効いていないとして諦める */
    private static final long GIVE_UP_TICKS = 60;
    /** 効かなかった候補を使わない Tick 数 */
    private static final long BLOCK_TICKS = 1200;
    /** 時間停止系の名前の語幹（単語の前方一致） */
    private static final String[] TIME_STEMS = {"time", "stop", "freez", "frozen", "stasis", "chrono", "pause", "halt"};
    private static final EnumSet<StateScanner.Source> WRITABLE =
            EnumSet.of(StateScanner.Source.SYNCHED_DATA, StateScanner.Source.INJECTED_FIELD);

    private final Eprotect protect;
    private final LivingEntity owner;
    private final TickStallGuard stall;

    // 探している間
    private Map<Integer, Integer> sample = new HashMap<>();
    private long nextAttempt = Long.MIN_VALUE;
    @Nullable
    private String pendingKey;
    @Nullable
    private UUID pendingMover;
    private int pendingSeen;
    private final Map<String, Long> blockedUntil = new HashMap<>();

    // 真似ている間
    private boolean active;
    @Nullable
    private UUID sourceId;
    @Nullable
    private String sourceKey;
    @Nullable
    private String stateName;
    private long stalledSince = Long.MIN_VALUE;

    TimeStopExemption(Eprotect protect, TickStallGuard stall) {
        this.protect = protect;
        this.owner = protect.getOwner();
        this.stall = stall;
    }

    /** 掃除から一時的に外す名前（真似ている間だけ） */
    Set<String> mirroredNames() {
        return active && stateName != null ? Set.of(stateName) : Set.of();
    }

    boolean isActive() {
        return active;
    }

    /** 掃除の後（{@link Eprotect#repair} の終わり近く）で毎回 */
    void update() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (!EprotectConfig.get(EprotectConfig.MIRROR_TIME_STOP_EXEMPTION) || !protect.isProtectionActive()) {
            if (active) end(level, "disabled or protection ended");
            return;
        }
        long now = protect.serverClock();
        if (active) {
            mirror(level, now);
            return;
        }
        if (!stall.isDriving()) {
            sample = new HashMap<>();
            pendingKey = null;
            return;
        }
        discover(level, now);
    }

    // ---- 探す ------------------------------------------------------------------

    private void discover(ServerLevel level, long now) {
        if (now < nextAttempt) return;
        nextAttempt = now + DISCOVER_INTERVAL;
        List<Entity> near = level.getEntities(owner, owner.getBoundingBox().inflate(RADIUS),
                e -> e instanceof LivingEntity && !e.isRemoved());
        Map<Integer, Integer> next = new HashMap<>();
        List<LivingEntity> movers = new ArrayList<>();
        for (Entity e : near) {
            next.put(e.getId(), e.tickCount);
            Integer before = sample.get(e.getId());
            if (before != null && e.tickCount > before) movers.add((LivingEntity) e);
        }
        sample = next;
        if (movers.isEmpty()) {
            pendingKey = null;
            return;
        }
        movers.sort(Comparator.comparingDouble(owner::distanceToSqr));

        Map<String, Object> mine = new HashMap<>();
        StateScanner.observe(owner, o -> mine.put(o.key(), o.value()));
        for (LivingEntity mover : movers.subList(0, Math.min(MAX_MOVERS, movers.size()))) {
            List<StateScanner.Observed> theirs = new ArrayList<>();
            StateScanner.observe(mover, theirs::add);
            for (StateScanner.Observed o : theirs) {
                if (!isCandidateName(o) || !positive(o.value())) continue;
                Object own = mine.get(o.key());
                if (own == null || positive(own)) continue;
                if (blockedUntil.getOrDefault(o.key(), 0L) > now) continue;
                confirm(o, mover, now);
                return;
            }
        }
        pendingKey = null;
    }

    /** 辞書が time / stop / freeze 系の CONTROL と見分ける名前で、書き込める場所にあるか */
    private static boolean isCandidateName(StateScanner.Observed o) {
        if (!WRITABLE.contains(o.source())) return false;
        if (!FlagDictionary.is(o.name(), FlagDictionary.Category.CONTROL)) return false;
        for (String word : FlagDictionary.words(o.name())) {
            for (String stem : TIME_STEMS) if (word.startsWith(stem)) return true;
        }
        return false;
    }

    private void confirm(StateScanner.Observed found, LivingEntity mover, long now) {
        if (found.key().equals(pendingKey) && mover.getUUID().equals(pendingMover)) {
            pendingSeen++;
        } else {
            pendingKey = found.key();
            pendingMover = mover.getUUID();
            pendingSeen = 1;
        }
        if (pendingSeen < CONFIRMATIONS) return;
        active = true;
        sourceId = mover.getUUID();
        sourceKey = found.key();
        stateName = found.name();
        stalledSince = Long.MIN_VALUE;
        pendingKey = null;
        AutomataValkyries.LOGGER.warn("[eprotect] {} is stopped while {} keeps moving; mirroring its time-stop exemption '{}' ({})",
                owner, mover, found.name(), found.key());
        AttackLog.repaired(owner, "time_stop_exemption:" + found.name());
        write(found.value(), false);
    }

    // ---- 真似る ------------------------------------------------------------------

    private void mirror(ServerLevel level, long now) {
        Entity source = sourceId != null ? level.getEntity(sourceId) : null;
        Object value = source instanceof LivingEntity living && !living.isRemoved() ? read(living, sourceKey) : null;
        if (value == null || !positive(value)) {
            end(level, "the exempt entity no longer holds it (the stop ended?)");
            return;
        }
        if (stall.isDriving()) {
            if (stalledSince == Long.MIN_VALUE) {
                stalledSince = now;
            } else if (now - stalledSince > GIVE_UP_TICKS) {
                blockedUntil.put(sourceKey, now + BLOCK_TICKS);
                end(level, "mirroring did not release the stop");
                return;
            }
        } else {
            stalledSince = Long.MIN_VALUE;
        }
        write(value, false);
    }

    private void end(ServerLevel level, String reason) {
        write(null, true);
        AutomataValkyries.LOGGER.info("[eprotect] Stopped mirroring the time-stop exemption '{}' on {}: {}", stateName, owner, reason);
        active = false;
        sourceId = null;
        sourceKey = null;
        stateName = null;
        stalledSince = Long.MIN_VALUE;
        sample = new HashMap<>();
        pendingKey = null;
        nextAttempt = protect.serverClock() + DISCOVER_INTERVAL * 4L;
    }

    @Nullable
    private static Object read(LivingEntity entity, @Nullable String key) {
        if (key == null) return null;
        Object[] found = {null};
        StateScanner.observe(entity, o -> {
            if (o.key().equals(key)) found[0] = o.value();
        });
        return found[0];
    }

    private void write(@Nullable Object sourceValue, boolean restore) {
        if (stateName == null) return;
        StateScanner.apply(owner, new MirrorPolicy(stateName, sourceValue, restore), WRITABLE);
    }

    private static boolean positive(Object value) {
        if (value instanceof Boolean b) return b;
        return value instanceof Number n && n.doubleValue() > 0.0D;
    }

    /** 名前が合う 1 つの状態だけを、動いている個体の値（数値は +1、真偽は真）か 0（偽）にする */
    private static final class MirrorPolicy implements StateScanner.Policy {
        private final String name;
        private final Object sourceValue;
        private final boolean restore;

        MirrorPolicy(String name, @Nullable Object sourceValue, boolean restore) {
            this.name = name;
            this.sourceValue = sourceValue;
            this.restore = restore;
        }

        @Override
        public boolean relevant(String candidate) {
            return name.equals(candidate);
        }

        @Override
        @Nullable
        public Object decide(String candidate, Class<?> type, @Nullable Object current) {
            if (!name.equals(candidate)) return null;
            Object target;
            if (type == boolean.class || type == Boolean.class) {
                target = !restore;
            } else {
                double v = restore ? 0.0D : (sourceValue instanceof Number n ? n.doubleValue() + 1.0D : 1.0D);
                target = coerce(type, v);
            }
            if (target == null || target.equals(current)) return null;
            return target;
        }

        @Override
        public boolean removeKey(String key) {
            return false;
        }

        @Nullable
        private static Object coerce(Class<?> type, double v) {
            if (type == int.class || type == Integer.class) return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(v)));
            if (type == long.class || type == Long.class) return Math.round(v);
            if (type == float.class || type == Float.class) return (float) v;
            if (type == double.class || type == Double.class) return v;
            if (type == short.class || type == Short.class) return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
            if (type == byte.class || type == Byte.class) return (byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(v)));
            return null;
        }
    }
}
