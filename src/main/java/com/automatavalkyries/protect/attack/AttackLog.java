package com.automatavalkyries.protect.attack;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 保護対象への攻撃の記録。（MOD, 操作, 結果）ごとに集計し、初回はすぐ、以後は {@link #flush} でまとめてログ・ファイルへ出す。
 * 実際に拒否・修復した箇所からだけ呼ぶ（読み取りの偽装対策は常に走るので記録しない）。どのスレッドから呼ばれてもよい。
 */
public final class AttackLog {

    public enum Outcome {
        /** 全 MOD 共通の防御で拒否した */
        REFUSED(true),
        /** ブラックリストの MOD なので拒否した */
        BLOCKED_BLACKLIST(false),
        /** 即死 MOD 専用の対策（互換 Mixin）で止めた */
        SHIELDED(true),
        /** 即死 MOD が付けたフラグ・登録を解除した */
        NEUTRALIZED(true),
        /** Mixin を経ない直書きを後から修復した（呼び出し元は分からない） */
        REPAIRED(false),
        /** 見つけたが直せない（読み出しの偽装など。呼び出し元は分からない） */
        DETECTED(false);

        final boolean countsTowardAuto;

        Outcome(boolean countsTowardAuto) {
            this.countsTowardAuto = countsTowardAuto;
        }
    }

    public static final class Entry {
        public final String mod;
        public final String op;
        public final Outcome outcome;
        final LongAdder count = new LongAdder();
        final LongAdder pending = new LongAdder();
        volatile long firstTime;
        volatile long lastTime;
        volatile String target = "?";
        volatile String frame = "?";
        @Nullable
        volatile String via;

        Entry(String mod, String op, Outcome outcome) {
            this.mod = mod;
            this.op = op;
            this.outcome = outcome;
        }

        public long count() { return count.sum(); }
        public long lastTime() { return lastTime; }
        public String target() { return target; }
        public String frame() { return frame; }
        @Nullable public String via() { return via; }
    }

    /** 1 個体・1 操作につき、1 Tick にスタックを辿る上限（連打されても重くならないように） */
    private static final int WALKS_PER_TICK = 32;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, long[]> WALK_BUDGET = new ConcurrentHashMap<>();
    private static final Map<String, CallerAttribution.Attribution> LAST_ATTRIBUTION = new ConcurrentHashMap<>();

    private AttackLog() {}

    // ---- 記録 --------------------------------------------------------------

    /** 全 MOD 共通の防御で拒否した */
    public static void refused(Entity target, String op) {
        refused(target, op, null);
    }

    /**
     * 全 MOD 共通の防御で拒否した。
     *
     * @param knownMod 攻撃した MOD が別の手がかりで分かっていればその ID（例: 即死ダメージを与えたアイテムの名前空間）
     */
    public static void refused(Entity target, String op, @Nullable String knownMod) {
        if (!enabled(target)) return;
        CallerAttribution.Attribution a = attribution(target, op);
        String mod = CallerAttribution.isForeign(knownMod) ? knownMod
                : a.callerIsForeign() ? a.callerMod() : a.foreignMod() != null ? a.foreignMod() : a.callerMod();
        String charge = CallerAttribution.isForeign(knownMod) ? knownMod : chargeable(a);
        record(target, mod, op, Outcome.REFUSED, a.callerFrame(), mod.equals(a.callerMod()) ? null : a.callerMod(), charge);
        AttackerListCleanup.afterAttack(target, a);
    }

    /** ブラックリストの MOD なので拒否した（判定で求めた呼び出し元をそのまま使う） */
    public static void blocked(Entity target, String op, String mod) {
        if (!enabled(target)) return;
        record(target, mod, op, Outcome.BLOCKED_BLACKLIST, "?", null, null);
    }

    /**
     * 即死 MOD 専用の対策（互換 Mixin）で止めた。直接の呼び出し元は止めた MOD（経由した MOD）、
     * それを呼んだ別の MOD がいればそちらを攻撃した MOD とする（例: The Last Sword → ECA）
     */
    public static void shielded(Entity target, String op) {
        if (!enabled(target)) return;
        CallerAttribution.Attribution a = attribution(target, op);
        String via = a.callerMod();
        String mod = a.otherForeign() != null ? a.otherForeign() : via;
        record(target, mod, AttackOp.COMPAT + ":" + op, Outcome.SHIELDED, a.callerFrame(), mod.equals(via) ? null : via, chargeable(a));
        AttackerListCleanup.afterAttack(target, a);
    }

    /** 即死 MOD が付けたフラグ・登録を解除した（その MOD の名前は対策側が知っている） */
    public static void neutralized(Entity target, String ownerMod, String counter) {
        if (!enabled(target)) return;
        record(target, ownerMod, AttackOp.NEUTRALIZED + ":" + counter, Outcome.NEUTRALIZED, "?", null,
                CallerAttribution.isForeign(ownerMod) ? ownerMod : null);
    }

    /** Mixin を経ない直書きを修復した（誰が書いたかは分からない） */
    public static void repaired(Entity target, String kind) {
        if (!enabled(target)) return;
        record(target, CallerAttribution.UNKNOWN, AttackOp.REPAIR + ":" + kind, Outcome.REPAIRED, "(direct write)", null, null);
    }

    /** 見つけたが直せない異常（読み出しの偽装など） */
    public static void detected(Entity target, String op) {
        if (!enabled(target)) return;
        record(target, CallerAttribution.UNKNOWN, op, Outcome.DETECTED, "(spoofed read)", null, null);
    }

    public static void retreated(Entity target) {
        if (!enabled(target)) return;
        record(target, CallerAttribution.UNKNOWN, AttackOp.RETREAT, Outcome.REPAIRED, "(too many repairs)", null, null);
    }

    // ---- 表示・後片付け ------------------------------------------------------

    /** 新しい順 */
    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>(ENTRIES.values());
        list.sort(Comparator.comparingLong(Entry::lastTime).reversed());
        return list;
    }

    /** 記録に出てきた MOD（コマンドの候補用） */
    public static List<String> recordedMods() {
        return entries().stream().map(e -> e.mod).filter(CallerAttribution::isForeign).distinct().toList();
    }

    public static void clear() {
        ENTRIES.clear();
        LAST_ATTRIBUTION.clear();
        WALK_BUDGET.clear();
    }

    /** 前回から増えた分をまとめてログ・ファイルへ出す（サーバー Tick から定期的に呼ぶ） */
    public static void flush() {
        for (Entry entry : ENTRIES.values()) {
            long added = entry.pending.sumThenReset();
            if (added <= 0) continue;
            String line = String.format("%s %s [%s] +%d (total %d), last target %s", entry.mod, entry.op, entry.outcome,
                    added, entry.count(), entry.target);
            AutomataValkyries.LOGGER.info("[eprotect-attack] {}", line);
            if (EprotectConfig.get(EprotectConfig.ATTACK_LOG_WRITE_FILE)) AttackLogFile.write(line);
        }
    }

    // ---- 内部 --------------------------------------------------------------

    private static boolean enabled(Entity target) {
        return !target.level().isClientSide && EprotectConfig.get(EprotectConfig.ATTACK_LOG_ENABLED);
    }

    /** 自動登録で数える MOD（直接の呼び出し元が他 MOD ならそれ、無ければ最寄りの他 MOD は数えない） */
    @Nullable
    private static String chargeable(CallerAttribution.Attribution a) {
        return a.callerIsForeign() ? a.callerMod() : null;
    }

    private static CallerAttribution.Attribution attribution(Entity target, String op) {
        String key = target.getId() + "|" + op;
        long tick = target.level().getGameTime();
        if (WALK_BUDGET.size() > 4096) { // 個体 ID が増え続けても溜め込まない
            WALK_BUDGET.clear();
            LAST_ATTRIBUTION.clear();
        }
        long[] budget = WALK_BUDGET.computeIfAbsent(key, k -> new long[]{Long.MIN_VALUE, 0});
        synchronized (budget) {
            if (budget[0] != tick) {
                budget[0] = tick;
                budget[1] = 0;
            }
            if (budget[1] >= WALKS_PER_TICK) {
                CallerAttribution.Attribution last = LAST_ATTRIBUTION.get(key);
                if (last != null) return last;
            }
            budget[1]++;
        }
        CallerAttribution.Attribution a = CallerAttribution.attribute(target);
        LAST_ATTRIBUTION.put(key, a);
        return a;
    }

    private static void record(Entity target, String mod, String op, Outcome outcome, String frame,
                               @Nullable String via, @Nullable String chargeMod) {
        String key = mod + "|" + op + "|" + outcome;
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            if (ENTRIES.size() >= EprotectConfig.get(EprotectConfig.ATTACK_LOG_MAX_ENTRIES)) {
                mod = "other";
                key = mod + "|" + op + "|" + outcome;
            }
            Entry created = new Entry(mod, op, outcome);
            Entry existing = ENTRIES.putIfAbsent(key, created);
            entry = existing != null ? existing : created;
            if (existing == null) {
                created.firstTime = target.level().getGameTime();
                String line = String.format("%s %s [%s] first seen on %s at %s%s", mod, op, outcome, describe(target), frame,
                        via != null ? " via " + via : "");
                AutomataValkyries.LOGGER.warn("[eprotect-attack] {}", line);
                if (EprotectConfig.get(EprotectConfig.ATTACK_LOG_WRITE_FILE)) AttackLogFile.write(line);
            } else {
                entry.pending.increment();
            }
        } else {
            entry.pending.increment();
        }
        entry.count.increment();
        entry.lastTime = target.level().getGameTime();
        entry.target = describe(target);
        entry.frame = frame;
        entry.via = via;

        if (outcome.countsTowardAuto && chargeMod != null) {
            ModBlacklist.noteAttack(target, chargeMod);
        }
    }

    private static String describe(Entity target) {
        String uuid = target.getUUID().toString();
        return BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + "#" + uuid.substring(0, 8);
    }
}
