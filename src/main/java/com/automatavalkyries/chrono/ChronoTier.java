package com.automatavalkyries.chrono;

/**
 * クロノリンク・マシンのティア（docs/SPEC_CHRONOLINK_MACHINES.md §2）。
 * ティアの数字（level）はブロックの状態 tier=1..4 と同じ。
 */
public enum ChronoTier {
    /** T1 ハイエンド: 半径 16 m・同じディメンション */
    HIGH_END(1, "high_end", 1, 2, 100, 2, 16, false, false, 8, 1_000_000, 4, 5_000),
    /** T2 フューチャー: 半径 128 m・同じディメンション */
    FUTURE(2, "future", 4, 4, 80, 4, 128, false, false, 32, 10_000_000, 4, 20_000),
    /** T3 ビヨンド: 無制限・ディメンションをまたぐ */
    BEYOND(3, "beyond", 16, 8, 60, 8, -1, true, false, 128, 100_000_000, 16, 200_000),
    /** T4 ネバーラスト: 無制限・異次元・読み込まれていないチャンクにも届く */
    NEVERLAST(4, "neverlast", 64, 16, 40, 16, -1, true, true, 256, 2_000_000_000, 0, 500_000);

    public final int level;
    public final String id;
    /** 加工の速さ（時間をこの数で割る） */
    public final int speed;
    /** 同時に加工する数（並列スロット） */
    public final int parallel;
    /** 1 回の加工の電力（基準の何 %） */
    public final int energyPercent;
    /** 空間リンクの枠と距離（-1 は無制限） */
    public final int linkSlots, range;
    public final boolean crossDimension, reachUnloaded;
    /** 1 Tick に 1 リンクで運ぶ個数 */
    public final int transferPerTick;
    /** 電力の蓄え（FE） */
    public final int capacity;
    /** 空間リンクを 1 本登録するのに使うクロノスクラップの数 */
    public final int linkCost;
    /** 無線給電・充電の速さ（FE/Tick。コアの放電レートにそろえる） */
    public final int powerRate;

    ChronoTier(int level, String id, int speed, int parallel, int energyPercent, int linkSlots, int range,
               boolean crossDimension, boolean reachUnloaded, int transferPerTick, int capacity, int linkCost, int powerRate) {
        this.level = level;
        this.id = id;
        this.speed = speed;
        this.parallel = parallel;
        this.energyPercent = energyPercent;
        this.linkSlots = linkSlots;
        this.range = range;
        this.crossDimension = crossDimension;
        this.reachUnloaded = reachUnloaded;
        this.transferPerTick = transferPerTick;
        this.capacity = capacity;
        this.linkCost = linkCost;
        this.powerRate = powerRate;
    }

    public static ChronoTier of(int level) {
        return values()[Math.max(1, Math.min(4, level)) - 1];
    }

    public ChronoTier next() {
        return this == NEVERLAST ? null : values()[ordinal() + 1];
    }

    public int scaleTime(int time) { return Math.max(1, time / speed); }

    public int scaleEnergy(int energy) { return (int) ((long) energy * energyPercent / 100); }

    /** 距離・ディメンションが届くか（同じディメンションなら距離の 2 乗で比べる） */
    public boolean reaches(boolean sameDimension, double distanceSqr) {
        if (!sameDimension) return crossDimension;
        return range < 0 || distanceSqr <= (double) range * range;
    }
}
