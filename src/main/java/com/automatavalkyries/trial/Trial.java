package com.automatavalkyries.trial;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * 回想のトライアル 1 つ（過去の回想という設定で、回想の空間で指定された条件のもとボスを倒す。2026-10-04 ユーザー指定。条件と数値は【提案】）。
 * ボスは本物が出る（{@link TrialManager#spawnBoss} で出し分ける）。
 * 2026-10-27 ユーザー指定「追憶メモリを調整する」: 制限時間をボスの HP に合わせ、中ボス 4 体・AEC 級 3 体の同時・全ボスのボスラッシュ・ルクシオン EX を足した。
 *
 * @param id         名前（{@code trial.automata_valkyries.<id>}）。回想するボスの名前（{@code boss.automata_valkyries.<id>}）も兼ねる
 * @param bosses     出すボス（{@link TrialManager#spawnBoss} の ID）。{@link Mode#RUSH} はこの順に 1 体ずつ
 * @param mode       1 体・同時・連戦
 * @param timeLimit  ボスが出てから倒すまでの制限時間（Tick。連戦の合間は数えない）
 * @param frail      最大 HP が半分になる
 * @param noHeal     体力が回復しない
 * @param meleeOnly  ボスには自分の手の攻撃（近接）しか効かない
 * @param bossAttack ボスの攻撃力の倍率
 * @param bossHealth ボスの最大 HP の倍率（同時に出すときに減らす）
 */
public record Trial(String id, List<String> bosses, Mode mode, int timeLimit, boolean frail, boolean noHeal, boolean meleeOnly,
                    float bossAttack, float bossHealth) {

    /** ボスの出し方: 1 体・同時に全部・1 体ずつ連戦 */
    public enum Mode { SINGLE, TOGETHER, RUSH }

    /** ランク（2026-10-27 ユーザー指定「タイムと被弾数」）。S の初回でオーバーロードメモリ */
    public enum Rank {
        NONE, B, A, S;

        public String letter() {
            return this == NONE ? "-" : name();
        }

        public int color() {
            return switch (this) {
                case S -> 0xFFD860;
                case A -> 0xE0E8F0;
                case B -> 0xC89060;
                default -> 0x807060;
            };
        }
    }

    /** S: 制限時間の半分以内・被弾 3 回以下。A: 制限時間の 6 割以内（残り 4 割以上）・被弾 10 回以下。それ以外は B【仮】 */
    public static final float S_TIME = 0.5F, A_TIME = 0.6F;
    public static final int S_HITS = 3, A_HITS = 10;

    private static final int MIN = 20 * 60;

    /** 回想のトライアルの一覧（ボスの登場順。最後にボスラッシュ） */
    public static final List<Trial> ALL = List.of(
            single("crane_loader", (int) (1.5 * MIN), 3.0F),                                   // 荷役機 HP 800
            single("outpost_watcher", 2 * MIN, 3.0F),                                          // 前哨司令機 HP 1,500
            single("foundry_master", (int) (2.5 * MIN), 3.0F),                                 // 工場長機 HP 3,000
            single("bastion_keeper", 3 * MIN, 2.5F),                                           // 要塞守護機 HP 6,000
            new Trial("luxion", List.of("luxion"), Mode.SINGLE, 3 * MIN, false, false, false, 1.5F, 1.0F),            // 光速の残影: HP 10,000
            new Trial("galliafort", List.of("galliafort"), Mode.SINGLE, 5 * MIN, false, false, true, 1.0F, 1.0F),     // 不落の城壁: HP 15,000・近接のみ
            new Trial("memento_mori", List.of("memento_mori"), Mode.SINGLE, 5 * MIN, false, false, false, 1.5F, 1.0F), // 死蝶の舞踏: HP 45,000
            new Trial("aec_trio", List.of("luxion", "galliafort", "memento_mori"), Mode.TOGETHER, 7 * MIN, false, false, false, 1.2F, 0.6F), // 三英雄の共鳴: 同時・HP 6 割
            new Trial("time_ruler", List.of("time_ruler"), Mode.SINGLE, 8 * MIN, false, true, false, 2.0F, 1.0F),     // 凍てつく刻: HP 80,000・回復なし（4:00 → 8:00）
            new Trial("lost_catastrophe", List.of("lost_catastrophe"), Mode.SINGLE, 10 * MIN, true, false, false, 2.5F, 1.0F), // 零の追憶: HP 120,000（5:00 → 10:00）
            new Trial("luxion_ex", List.of("luxion_ex"), Mode.SINGLE, 5 * MIN, false, false, false, 1.0F, 1.0F),      // 極光の残響: ルクシオン EX HP 30,000
            new Trial("galliafort_ex", List.of("galliafort_ex"), Mode.SINGLE, 7 * MIN, false, false, false, 1.0F, 1.0F), // 鋼鉄の残響: ガリアフォート EX HP 30,000
            new Trial("memento_mori_ex", List.of("memento_mori_ex"), Mode.SINGLE, 9 * MIN, false, false, false, 1.0F, 1.0F), // 死蝶の残響: メメントモリ EX HP 90,000
            new Trial("boss_rush", List.of("crane_loader", "outpost_watcher", "foundry_master", "bastion_keeper",
                    "luxion", "galliafort", "memento_mori", "time_ruler", "lost_catastrophe"), Mode.RUSH, 40 * MIN, false, false, false, 1.5F, 1.0F)); // 追憶の回廊

    private static Trial single(String boss, int time, float attack) {
        return new Trial(boss, List.of(boss), Mode.SINGLE, time, false, false, false, attack, 1.0F);
    }

    public static Trial get(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equals(id)) return i;
        return -1;
    }

    /** かかった Tick と被弾数からランク */
    public Rank rank(int ticks, int hits) {
        float used = ticks / (float) timeLimit;
        if (used <= S_TIME && hits <= S_HITS) return Rank.S;
        if (used <= A_TIME && hits <= A_HITS) return Rank.A;
        return Rank.B;
    }

    public Component title() {
        return Component.translatable("trial.automata_valkyries." + id);
    }

    /** 回想するボス（同時・連戦は「○○ ほか」ではなく、トライアルごとの名前） */
    public Component bossName() {
        return Component.translatable("boss.automata_valkyries." + id);
    }

    /** 条件の説明（「制限時間 3:00・回復なし」など。被弾数はランクだけに使い、失敗の条件にはしない: 2026-10-27 ユーザー指定） */
    public Component conditions() {
        MutableComponent c = Component.translatable("trial.automata_valkyries.cond.time", clock(timeLimit));
        if (frail) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.frail"));
        if (noHeal) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.no_heal"));
        if (meleeOnly) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.melee_only"));
        if (mode == Mode.TOGETHER) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.together", Math.round(bossHealth * 100)));
        if (mode == Mode.RUSH) c.append(" / ").append(Component.translatable("trial.automata_valkyries.cond.rush", bosses.size()));
        return c;
    }

    /** Tick を「分:秒」に */
    public static String clock(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
