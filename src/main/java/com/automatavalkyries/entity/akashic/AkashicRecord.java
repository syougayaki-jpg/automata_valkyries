package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事象観測体アカシック・プローブの観測の記録（2026-10-25）。観測体の UUID ごとに 1 つ持ち、入り直し・作り直しで個体が替わっても続く。
 * <ul>
 *   <li>受けた操作（保護の攻撃の記録 {@code AttackLog} のうち、この観測体が受けたもの）と被弾を、MOD・操作・結果ごとに数える</li>
 *   <li>初めての組み合わせは、呼び出し元と経由した MOD まで、ログファイル（{@code logs/automata_valkyries-akashic.log}）へ書く。
 *       続けて起きた数は 10 秒ごとにまとめて書く（毎 Tick 何十回も来る MOD があるので）</li>
 *   <li>初めて見た他 MOD は下調べ（{@link AkashicSurvey}）にかけ、終わったら結果を書く</li>
 *   <li>保護を突破された（消去・撃破が通った）ら、そのときの呼び出しの経路を最後の記録として書く</li>
 *   <li>頭上のホログラムに出す最近の 6 行を作る</li>
 * </ul>
 * サーバーのスレッドの外から呼ばれることがあるので、中身は同期して扱う
 */
public final class AkashicRecord {

    /** ホログラムに出す行数 */
    public static final int HOLOGRAM_LINES = 8;
    /** まとめて書く間隔（Tick） */
    private static final int SUMMARY_TICKS = 200;

    private static final Map<UUID, AkashicRecord> RECORDS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static BufferedWriter writer;

    private final UUID probe;
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private final Deque<String> recentKeys = new ArrayDeque<>();
    private final Map<String, CompletableFuture<AkashicSurvey.Report>> surveys = new LinkedHashMap<>();
    private final Deque<Component> notes = new ArrayDeque<>();
    private String lastForeignMod;
    private String lastObservedMod;
    private long refusedCount;
    private long repairedCount;
    private long detectedCount;
    private long damageReceivedCount;
    private long totalEvents;
    private long lastSummary;
    private boolean dirty = true;

    /** 1 つの組み合わせ（MOD・操作・結果）の数 */
    private static final class Row {
        final String mod;
        final String op;
        final String outcome;
        long count;
        long summarized;
        String frame = "?";
        String via;

        Row(String mod, String op, String outcome) {
            this.mod = mod;
            this.op = op;
            this.outcome = outcome;
        }
    }

    private AkashicRecord(UUID probe) {
        this.probe = probe;
    }

    public static AkashicRecord of(UUID probe) {
        return RECORDS.computeIfAbsent(probe, AkashicRecord::new);
    }

    /** 正規の退場で記録を閉じる（ログには残る） */
    public static void close(UUID probe) {
        AkashicRecord record = RECORDS.remove(probe);
        if (record != null) record.writeSummary(true);
    }

    // ---- 観測 --------------------------------------------------------------

    /** 保護の攻撃の記録から: 受けた操作 */
    public void observe(String mod, String op, String outcome, String frame, String via) {
        synchronized (this) {
            String key = mod + "|" + op + "|" + outcome;
            Row row = rows.get(key);
            boolean first = row == null;
            if (first) {
                row = new Row(mod, op, outcome);
                rows.put(key, row);
            }
            row.count++;
            totalEvents++;
            if (outcome != null) {
                switch (outcome) {
                    case "REFUSED", "BLOCKED_BLACKLIST" -> refusedCount++;
                    case "REPAIRED", "NEUTRALIZED", "SHIELDED" -> repairedCount++;
                    case "DETECTED" -> detectedCount++;
                    case "received" -> damageReceivedCount++;
                    default -> {}
                }
            }
            if (frame != null && !"?".equals(frame)) row.frame = frame;
            if (via != null) row.via = via;
            recentKeys.remove(key);
            recentKeys.addFirst(key);
            while (recentKeys.size() > HOLOGRAM_LINES) recentKeys.removeLast();
            if (CallerAttribution.isForeign(mod)) {
                lastForeignMod = mod;
                lastObservedMod = mod;
            } else if (mod != null
                    && !mod.equals(CallerAttribution.UNKNOWN)
                    && !mod.equals(CallerAttribution.MINECRAFT)
                    && !mod.equals(CallerAttribution.FORGE)
                    && !mod.equals(CallerAttribution.PLATFORM)
                    && (op == null || (!op.startsWith("mode:") && !op.startsWith("lexicon:")))) {
                lastObservedMod = mod;
            }
            dirty = true;
            if (first) {
                write(String.format("observed %s %s [%s] at %s%s", mod, op, outcome, row.frame, via != null ? " via " + via : ""));
                if (CallerAttribution.isForeign(mod) && !surveys.containsKey(mod)) surveys.put(mod, AkashicSurvey.request(mod));
            }
        }
        // 記録のロックを外してから対決エンジンへ知らせる（2026-10-27。対決エンジンは自分のロックを持ったまま lastForeignMod() で記録のロックを取るので、
        // ここで記録のロックを持ったまま呼ぶと取る順番が逆になり、Madness Entities が描画スレッドから殴ったときにサーバーと描画が互いを待って固まった）
        AkashicDuelEngine.onRecordObserved(this.probe, mod, op, outcome, frame);
    }

    /** 直近に観測された他 MOD の ID（フレームから特定できない非同期/Unsafe 直書き時の推定用） */
    public synchronized String lastForeignMod() {
        return lastForeignMod;
    }

    /** 直近に観測された攻撃元 MOD（他 MOD が無ければ自 MOD のテスト用抹殺棒等も含む） */
    public synchronized String lastObservedMod() {
        return lastObservedMod;
    }

    /** 被弾: 種類ごとに数える（ログの最初の 1 行に、相手と量を書く） */
    public void observeDamage(String damageType, float amount, String attackerMod, String attacker) {
        observe(attackerMod, "damage:" + damageType, "received", attacker + " last " + String.format("%.1f", amount), null);
    }

    /** 保護を突破された: そのときの呼び出しの経路を書く */
    public synchronized void observeBreach(String what) {
        StringBuilder trace = new StringBuilder();
        int depth = 0;
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (depth++ < 2) continue;
            trace.append("\n    at ").append(element);
            if (depth > 48) break;
        }
        write("BREACH " + what + " (protection was bypassed; the call path follows)" + trace);
        notes.addFirst(Component.translatable("akashic.automata_valkyries.breach", what).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        while (notes.size() > 2) notes.removeLast();
        dirty = true;
    }

    // ---- 毎 Tick（サーバー） ---------------------------------------------------

    /** 下調べの結果を拾い、まとめを書く。ホログラムが変わったら true */
    public synchronized boolean tick(long gameTime) {
        for (var entry : surveys.entrySet()) {
            CompletableFuture<AkashicSurvey.Report> future = entry.getValue();
            if (future == null || !future.isDone()) continue;
            entry.setValue(null);
            AkashicSurvey.Report report = future.getNow(null);
            if (report != null) noteSurvey(report);
        }
        if (gameTime - lastSummary >= SUMMARY_TICKS) {
            lastSummary = gameTime;
            writeSummary(false);
        }
        boolean changed = dirty;
        dirty = false;
        return changed;
    }

    private void noteSurvey(AkashicSurvey.Report report) {
        if (!report.ok()) {
            write("survey " + report.mod() + ": failed (" + report.error() + ")");
            return;
        }
        StringBuilder line = new StringBuilder("survey ").append(report.mod()).append(": ").append(report.scannedClasses()).append(" classes read");
        List<String> keys = new ArrayList<>();
        for (AkashicSurvey.Finding finding : report.findings()) {
            line.append("\n    ").append(finding.key()).append(": ").append(finding.classes()).append(" classes, e.g. ").append(String.join(", ", finding.samples()));
            if (!finding.key().startsWith("observer:")) keys.add(finding.key());
        }
        for (AkashicSurvey.Finding sub : report.subFindings()) {
            line.append("\n    ").append(sub.key()).append(": ").append(sub.classes()).append(" classes, e.g. ").append(String.join(", ", sub.samples()));
        }
        if (!report.dictionaryHits().isEmpty()) {
            line.append("\n    flag_dictionary_hits: ").append(String.join(", ", report.dictionaryHits()));
        }
        if (report.findings().isEmpty() && report.subFindings().isEmpty()) {
            line.append("\n    (nothing that looks like an instant-kill technique)");
        }
        write(line.toString());
        MutableComponent note = Component.translatable("akashic.automata_valkyries.survey", report.mod()).withStyle(ChatFormatting.AQUA);
        for (int i = 0; i < keys.size(); i++) {
            note.append(Component.literal(i == 0 ? " " : "・"))
                    .append(Component.translatable("akashic.automata_valkyries.capability." + keys.get(i)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (keys.isEmpty()) note.append(" ").append(Component.translatable("akashic.automata_valkyries.capability.none").withStyle(ChatFormatting.GRAY));
        notes.addFirst(note);
        while (notes.size() > 2) notes.removeLast();
        dirty = true;
    }

    private synchronized void writeSummary(boolean closing) {
        StringBuilder line = new StringBuilder();
        for (Row row : rows.values()) {
            if (row.count == row.summarized) continue;
            line.append("\n    ").append(row.mod).append(' ').append(row.op).append(" [").append(row.outcome).append("] +")
                    .append(row.count - row.summarized).append(" (total ").append(row.count).append(')');
            row.summarized = row.count;
        }
        if (line.length() > 0) write((closing ? "final summary" : "summary") + line);
        else if (closing) write("closed");
    }

    // ---- ホログラム -------------------------------------------------------------

    /** 頭上観測 UI（Overhead Observation Console）表示用に防御・検死カウンタを書き出す */
    public synchronized void writeObservationUiCounters(CompoundTag tag) {
        tag.putLong("RefusedCount", refusedCount);
        tag.putLong("RepairedCount", repairedCount);
        tag.putLong("DetectedCount", detectedCount);
        tag.putLong("DamageReceivedCount", damageReceivedCount);
        tag.putLong("TotalEvents", totalEvents);
        String suspect = lastForeignMod != null ? lastForeignMod : (lastObservedMod != null ? lastObservedMod : "none");
        tag.putString("SuspectMod", suspect);
        tag.putInt("UniqueRowCount", rows.size());
    }

    /** 頭上に出す行（JSON の部品。新しい物が上）: 下調べ・突破の知らせ（2 行まで）と、最近の組み合わせ */
    public synchronized List<Component> hologram() {
        List<Component> out = new ArrayList<>(notes);
        for (String key : recentKeys) {
            if (out.size() >= HOLOGRAM_LINES) break;
            Row row = rows.get(key);
            if (row == null) continue;
            ChatFormatting color = switch (row.outcome) {
                case "REFUSED", "BLOCKED_BLACKLIST" -> ChatFormatting.RED;
                case "REPAIRED", "NEUTRALIZED", "SHIELDED" -> ChatFormatting.GOLD;
                case "DETECTED", "ADAPTED" -> ChatFormatting.YELLOW;
                case "PENETRATED", "DEFEATED_OPPONENT" -> ChatFormatting.LIGHT_PURPLE;
                case "DAMAGED", "HIT" -> ChatFormatting.GREEN;
                case "RESISTED", "BLOCKED_ESCALATING" -> ChatFormatting.AQUA;
                default -> ChatFormatting.WHITE;
            };
            String badge = switch (row.outcome) {
                case "REFUSED", "BLOCKED_BLACKLIST" -> "[遮断] ";
                case "REPAIRED", "NEUTRALIZED", "SHIELDED" -> "[修復] ";
                case "DETECTED" -> "[検知] ";
                case "ADAPTED" -> "[適応] ";
                case "PENETRATED", "DEFEATED_OPPONENT" -> "[貫通] ";
                case "DAMAGED", "HIT" -> "[有効] ";
                case "RESISTED", "BLOCKED_ESCALATING" -> "[耐性] ";
                case "received" -> "[被弾] ";
                case "ACTIVE", "CONFIGURED", "TARGET_LOCKED" -> "[状態] ";
                default -> "[" + row.outcome + "] ";
            };
            out.add(Component.literal(badge).withStyle(color)
                    .append(Component.literal(row.mod).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(Component.literal(row.op).withStyle(color))
                    .append(Component.literal(" ×" + row.count).withStyle(ChatFormatting.DARK_GRAY)));
        }
        return out;
    }

    // ---- ログファイル ------------------------------------------------------------

    private void write(String text) {
        String line = LocalDateTime.now().format(TIME) + " [" + probe.toString().substring(0, 8) + "] " + text;
        AutomataValkyries.LOGGER.info("[akashic] {}", line);
        synchronized (AkashicRecord.class) {
            try {
                if (writer == null) {
                    Path path = FMLPaths.GAMEDIR.get().resolve("logs").resolve("automata_valkyries-akashic.log");
                    Files.createDirectories(path.getParent());
                    writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                AutomataValkyries.LOGGER.warn("[akashic] Could not write the observation log: {}", e.toString());
            }
        }
    }

    /** GameTest 用 */
    public static Set<String> keysForTest(UUID probe) {
        AkashicRecord record = RECORDS.get(probe);
        return record == null ? Set.of() : Set.copyOf(record.rows.keySet());
    }
}
