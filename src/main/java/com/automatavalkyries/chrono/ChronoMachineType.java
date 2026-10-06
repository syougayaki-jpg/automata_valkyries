package com.automatavalkyries.chrono;

import java.util.ArrayList;
import java.util.List;

/**
 * クロノリンク・マシンの種類（docs/SPEC_CHRONOLINK_MACHINES.md §4）。ブロックは種類ごとに 1 つで、ティアはブロックの状態に持つ。
 * スロットの並びと画面上の位置（{@link #layout}）もここで決める（ブロックエンティティと画面で共通）。
 */
public enum ChronoMachineType {
    /** ルミナル反応炉: ルミナル系の圧縮素材を燃やして発電 */
    LUMINAL_REACTOR("luminal_reactor", 2),
    /** クロノ・スメルター: 鉱石の倍加・製錬（並列スロット = 材料 16 + 結果 16） */
    CHRONO_SMELTER("chrono_smelter", 32),
    /** ルミナルエナジーアダプター: 原子相転移（材料 6 + 結果 1） */
    LUMINAL_ADAPTER("luminal_adapter", 7),
    /** クロノ・ファブリケータ: 設計図カードで自動組み立て（カード 27 + 結果の置き場 9） */
    CHRONO_FABRICATOR("chrono_fabricator", 36),
    /** テザー・パイロン: 無線給電（充電スロット 1） */
    TETHER_PYLON("tether_pylon", 1),
    /** ヴォイド・リポジトリ: 空間圧縮ストレージ（中身は別に持つ。スロットは預け入れ口 1） */
    VOID_REPOSITORY("void_repository", 1),
    /** タクティカル・アーモリー: 兵装・ルミナルコアの組み立て（材料 9 + 結果 1。docs/SPEC_WEAPON_UNITS.md） */
    TACTICAL_ARMORY("tactical_armory", 10),
    /** 給電ドック: 兵姫の給電・修復（オーブ 1 + ナノマシン溶液 1 + コア 1。§4.7） */
    CHARGING_DOCK("charging_dock", 3);

    public final String id;
    /** 内部の入れ物の大きさ（いちばん上のティアの分） */
    public final int slotCount;

    ChronoMachineType(String id, int slotCount) {
        this.id = id;
        this.slotCount = slotCount;
    }

    /** スロットの役割 */
    public enum Role { INPUT, OUTPUT, FUEL, CARD, CHARGE, DEPOSIT, CORE }

    public record SlotSpec(int index, int x, int y, Role role) {}

    /** 画面の大きさ（全種類で共通。宇宙の背景を広めに取る） */
    public static final int WIDTH = 206, HEIGHT = 210, PLAYER_X = 22, PLAYER_Y = 128, LINK_Y = 102;

    /** そのティアで使うスロット（画面の位置つき） */
    public List<SlotSpec> layout(ChronoTier tier) {
        List<SlotSpec> out = new ArrayList<>();
        switch (this) {
            case LUMINAL_REACTOR -> {
                out.add(new SlotSpec(0, 56, 50, Role.FUEL));
                // T4: 創世ルミナルコアを置く枠（燃やさず、毎秒 1,000,000 FE）
                if (tier == ChronoTier.NEVERLAST) out.add(new SlotSpec(1, 30, 50, Role.CORE));
            }
            case CHRONO_SMELTER -> {
                int n = tier.parallel, cols = Math.min(4, n), rows = (n + cols - 1) / cols;
                int y0 = 22 + (72 - rows * 18) / 2;
                for (int i = 0; i < n; i++) {
                    out.add(new SlotSpec(i, 30 + (i % cols) * 18 + (4 - cols) * 9, y0 + (i / cols) * 18, Role.INPUT));
                    out.add(new SlotSpec(16 + i, 124 + (i % cols) * 18, y0 + (i / cols) * 18, Role.OUTPUT));
                }
            }
            case LUMINAL_ADAPTER -> {
                for (int i = 0; i < 6; i++) out.add(new SlotSpec(i, 34 + (i % 3) * 18, 40 + (i / 3) * 18, Role.INPUT));
                out.add(new SlotSpec(6, 150, 49, Role.OUTPUT));
            }
            case CHRONO_FABRICATOR -> {
                int cards = cardSlots(tier);
                for (int i = 0; i < cards; i++) out.add(new SlotSpec(i, 22 + (i % 9) * 18, 22 + (i / 9) * 18, Role.CARD));
                for (int i = 0; i < 9; i++) out.add(new SlotSpec(27 + i, 22 + i * 18, 80, Role.OUTPUT));
            }
            case TETHER_PYLON -> out.add(new SlotSpec(0, 56, 50, Role.CHARGE));
            case VOID_REPOSITORY -> out.add(new SlotSpec(0, 26, 22, Role.DEPOSIT));
            case TACTICAL_ARMORY -> {
                for (int i = 0; i < 9; i++) out.add(new SlotSpec(i, 34 + (i % 3) * 18, 31 + (i / 3) * 18, Role.INPUT));
                out.add(new SlotSpec(9, 150, 49, Role.OUTPUT));
            }
            case CHARGING_DOCK -> {
                out.add(new SlotSpec(0, 34, 31, Role.CHARGE));                     // 光子励起のオーブ
                if (tier.level >= 2) out.add(new SlotSpec(1, 34, 61, Role.INPUT)); // ナノマシン溶液（T2 から）
                out.add(new SlotSpec(2, 150, 49, Role.CHARGE));                    // ルミナルコアの充電
            }
        }
        return out;
    }

    /** ファブリケータの設計図カードの枠（T1 3、T2 6、T3 12、T4 27） */
    public static int cardSlots(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 3;
            case FUTURE -> 6;
            case BEYOND -> 12;
            case NEVERLAST -> 27;
        };
    }

    /** ファブリケータが 1 回に作る数（T1 1、T2 4、T3 16、T4 64） */
    public static int craftsPerOp(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 1;
            case FUTURE -> 4;
            case BEYOND -> 16;
            case NEVERLAST -> 64;
        };
    }
}
