package com.automatavalkyries.protect.attack;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOD 単位のブラックリスト。載った MOD が「直接の呼び出し元」のとき、保護対象への即死系の操作
 * （強制移動・次元移動・次元移動を装った消去・ダメージ外の HP 減少）も拒否する。通常のダメージ（hurt 経由）は通す。
 * <p>
 * 手動（/eprotect blacklist add）と自動（即死攻撃を一定回数拒否した MOD）の両方で載せる。
 * minecraft / forge / このMOD と、設定の neverBlacklist は載せない。空のうちは何も判定しない（スタックも辿らない）。
 * </p>
 */
public final class ModBlacklist {

    public enum Source { MANUAL, AUTO, CONFIG }

    public enum Result { ADDED, ALREADY, PROTECTED, INVALID, REMOVED, NOT_LISTED }

    private static final Set<String> ALWAYS_PROTECTED = Set.of(CallerAttribution.MINECRAFT, CallerAttribution.FORGE,
            AutomataValkyries.MODID, CallerAttribution.UNKNOWN, CallerAttribution.PLATFORM);

    private static volatile Set<String> active = Set.of();
    private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();
    /** MOD → {窓の開始時刻, 回数, 最後に数えた時刻} */
    private static final Map<String, long[]> AUTO_COUNTS = new ConcurrentHashMap<>();

    /** GameTest 用: 設定ファイルへ保存しない */
    private static volatile boolean persistDisabled = false;

    private ModBlacklist() {}

    public static boolean isActive() {
        return !active.isEmpty();
    }

    public static boolean contains(@Nullable String mod) {
        return mod != null && active.contains(mod);
    }

    public static Set<String> list() {
        return active;
    }

    @Nullable
    public static Source sourceOf(String mod) {
        return SOURCES.getOrDefault(mod, active.contains(mod) ? Source.CONFIG : null);
    }

    public static boolean isProtected(String mod) {
        return ALWAYS_PROTECTED.contains(mod) || EprotectConfig.getList(EprotectConfig.BLACKLIST_NEVER).contains(mod);
    }

    /** 設定の読み込み・再読み込み時に作り直す（保護された ID は落とす） */
    public static synchronized void reloadFromConfig() {
        Set<String> set = new LinkedHashSet<>();
        for (String id : EprotectConfig.getList(EprotectConfig.BLACKLIST_MODS)) {
            if (EprotectConfig.ModIdCheck.isValid(id) && !isProtected(id)) set.add(id);
        }
        active = Set.copyOf(set);
    }

    public static synchronized Result add(String mod, Source source, boolean persist) {
        if (!EprotectConfig.ModIdCheck.isValid(mod)) return Result.INVALID;
        if (isProtected(mod)) return Result.PROTECTED;
        if (active.contains(mod)) return Result.ALREADY;
        Set<String> set = new LinkedHashSet<>(active);
        set.add(mod);
        active = Set.copyOf(set);
        SOURCES.put(mod, source);
        if (persist) save();
        AttackLogFile.write("blacklist add " + mod + " (" + source + ")");
        return Result.ADDED;
    }

    public static synchronized Result remove(String mod, boolean persist) {
        if (!active.contains(mod)) return Result.NOT_LISTED;
        Set<String> set = new LinkedHashSet<>(active);
        set.remove(mod);
        active = Set.copyOf(set);
        SOURCES.remove(mod);
        AUTO_COUNTS.remove(mod); // 数え直さないと直後に再登録されてしまう
        if (persist) save();
        AttackLogFile.write("blacklist remove " + mod);
        return Result.REMOVED;
    }

    /** 即死攻撃を拒否したとき、直接の呼び出し元の MOD を数える（1 Tick に 1 回まで）。しきい値を超えたら自動で載せる */
    public static void noteAttack(Entity target, String mod) {
        noteAttackAt(target.level().getServer(), mod, target.level().getGameTime());
    }

    /** {@link #noteAttack} の本体（GameTest からゲーム時刻を指定して呼べる） */
    public static void noteAttackAt(@Nullable MinecraftServer server, String mod, long now) {
        if (!EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_ENABLED) || active.contains(mod) || isProtected(mod)) return;
        int window = EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_WINDOW_TICKS);
        int threshold = EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_THRESHOLD);
        long[] counter = AUTO_COUNTS.computeIfAbsent(mod, k -> new long[]{now, 0, Long.MIN_VALUE});
        long count;
        synchronized (counter) {
            if (counter[2] == now) return; // 1 回の攻撃が remove → setRemoved → onRemove と連なっても 1 回
            counter[2] = now;
            if (now - counter[0] > window) {
                counter[0] = now;
                counter[1] = 0;
            }
            count = ++counter[1];
        }
        if (count < threshold) return;
        if (add(mod, Source.AUTO, !persistDisabled) != Result.ADDED) return;

        long finalCount = count;
        AutomataValkyries.LOGGER.warn("[eprotect] Blacklisted {} automatically ({} attacks within {} ticks)", mod, finalCount, window);
        if (server != null) {
            server.execute(() -> {
                Component message = Component.translatable("automata_valkyries.eprotect.auto_blacklisted", mod, finalCount, window)
                        .withStyle(ChatFormatting.GOLD);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.hasPermissions(2)) player.sendSystemMessage(message);
                }
            });
        }
    }

    /** GameTest 用 */
    public static void setPersistDisabledForTest(boolean disabled) {
        persistDisabled = disabled;
    }

    public static void resetAutoCounters() {
        AUTO_COUNTS.clear();
    }

    private static void save() {
        if (persistDisabled) return;
        try {
            EprotectConfig.BLACKLIST_MODS.set(new ArrayList<>(active));
            EprotectConfig.BLACKLIST_MODS.save();
        } catch (IllegalStateException e) {
            // 設定の読み込み前（GameTest の一部経路等）。メモリ上の一覧が正なので続行する
            AutomataValkyries.LOGGER.debug("[eprotect] Blacklist could not be saved yet", e);
        }
    }

    static List<String> sortedList() {
        List<String> list = new ArrayList<>(active);
        list.sort(String::compareTo);
        return list;
    }
}
