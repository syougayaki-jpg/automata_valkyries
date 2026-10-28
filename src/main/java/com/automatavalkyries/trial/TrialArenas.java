package com.automatavalkyries.trial;

import com.automatavalkyries.registry.ModTechBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 回想の闘技場の見た目（2026-10-27 ユーザー指定「闘技場をボスごとに」）。広さ・見えない壁はどれも同じ（半径 {@link TrialManager#ARENA_RADIUS}）で、
 * 床の材と模様・光・壁の外の飾りを、回想するボスの場所に寄せて変える。飾りは壁の外（戦いの邪魔にならない所）にだけ置く。
 * <ul>
 *   <li>{@link Theme#QUARTZ}: 白い石英の円盤（これまでの回想の闘技場）</li>
 *   <li>{@link Theme#BASE}: 侵食基地（白と暗い金属パネル・網目の床・警告の帯・照明パネル、外に光る線の柱）</li>
 *   <li>{@link Theme#LUXION}: 光の回廊（方解石と金の輪、外に金の冠の白い柱）。{@link Theme#LUXION_EX} はその夜（黒い石と金と紅い光）</li>
 *   <li>{@link Theme#GALLIA}: 城壁（深層岩のタイルと鉄の縁、外に鉄格子の狭間の胸壁）</li>
 *   <li>{@link Theme#MEMENTO}: 死蝶の庭（ブラックストーンと泣く黒曜石の輪、紫の結晶、外にウィザーローズの花壇）</li>
 *   <li>{@link Theme#RULER}: 時の文字盤（石英の盤に金の 12 の目盛りと長針・短針、外に金の時計の柱）</li>
 *   <li>{@link Theme#ZERO}: 零の墓標（ひび割れた深層岩と紅い煉瓦の輪、外に崩れた柱と浮かぶ瓦礫）</li>
 *   <li>{@link Theme#AEC}: 三英雄（石英の盤を金・鉄・紫の 3 つの扇に分ける）</li>
 * </ul>
 */
public final class TrialArenas {

    public enum Theme { QUARTZ, BASE, LUXION, LUXION_EX, GALLIA, MEMENTO, RULER, ZERO, AEC }

    /** 壁の外の飾りを置く帯（壁から何ブロック先まで）と、作り直すときに空にする高さ */
    static final int DECOR = 7, CLEAR_BELOW = 14, CLEAR_ABOVE = 26;

    private TrialArenas() {}

    /** ボス 1 体の回想の場 */
    public static Theme themeFor(String boss) {
        return switch (boss) {
            case "crane_loader", "outpost_watcher", "foundry_master", "bastion_keeper" -> Theme.BASE;
            case "luxion" -> Theme.LUXION;
            case "luxion_ex" -> Theme.LUXION_EX;
            case "galliafort", "galliafort_ex" -> Theme.GALLIA;
            case "memento_mori", "memento_mori_ex" -> Theme.MEMENTO;
            case "time_ruler" -> Theme.RULER;
            case "lost_catastrophe" -> Theme.ZERO;
            default -> Theme.QUARTZ;
        };
    }

    /** トライアルの最初の場（同時は三英雄、連戦は最初のボスの場） */
    public static Theme themeFor(Trial trial) {
        if (trial.mode() == Trial.Mode.TOGETHER) return Theme.AEC;
        return themeFor(trial.bosses().get(0));
    }

    /** 床の材（中心の光・輪・縁・床・光の筋） */
    private record Palette(BlockState light, BlockState ring, BlockState edge, BlockState floor, BlockState accent) {}

    private static Palette palette(Theme theme) {
        return switch (theme) {
            case BASE -> new Palette(s(ModTechBlocks.TECH_LIGHT_PANEL.get()), s(ModTechBlocks.TECH_GRATING.get()), s(ModTechBlocks.TECH_HAZARD.get()),
                    s(ModTechBlocks.TECH_PANEL_WHITE.get()), s(ModTechBlocks.TECH_LIGHT_LINE.get()));
            case LUXION -> new Palette(s(Blocks.SEA_LANTERN), s(Blocks.GOLD_BLOCK), s(Blocks.QUARTZ_PILLAR), s(Blocks.CALCITE), s(Blocks.GLOWSTONE));
            case LUXION_EX -> new Palette(s(Blocks.SHROOMLIGHT), s(Blocks.GOLD_BLOCK), s(Blocks.RED_NETHER_BRICKS), s(Blocks.POLISHED_BLACKSTONE),
                    s(Blocks.SHROOMLIGHT));
            case GALLIA -> new Palette(s(Blocks.SEA_LANTERN), s(Blocks.DEEPSLATE_TILES), s(Blocks.IRON_BLOCK), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.LANTERN));
            case MEMENTO -> new Palette(s(Blocks.CRYING_OBSIDIAN), s(Blocks.AMETHYST_BLOCK), s(Blocks.OBSIDIAN), s(Blocks.POLISHED_BLACKSTONE_BRICKS),
                    s(Blocks.CRYING_OBSIDIAN));
            case RULER -> new Palette(s(Blocks.SEA_LANTERN), s(Blocks.CHISELED_QUARTZ_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.SMOOTH_QUARTZ), s(Blocks.GOLD_BLOCK));
            case ZERO -> new Palette(s(Blocks.SHROOMLIGHT), s(Blocks.RED_NETHER_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_TILES),
                    s(Blocks.MAGMA_BLOCK));
            default -> new Palette(s(Blocks.SEA_LANTERN), s(Blocks.CHISELED_QUARTZ_BLOCK), s(Blocks.QUARTZ_PILLAR), s(Blocks.SMOOTH_QUARTZ), s(Blocks.SEA_LANTERN));
        };
    }

    private static BlockState s(Block block) {
        return block.defaultBlockState();
    }

    /** 闘技場を建てる（床・見えない壁・壁の外の飾り）。前の場の飾り・床の上のブロックは消す。エンティティは消さない（{@link TrialManager#buildArena} が消す） */
    public static void build(ServerLevel level, BlockPos c, Theme theme) {
        int r = TrialManager.ARENA_RADIUS;
        Palette p = palette(theme);
        BlockState air = Blocks.AIR.defaultBlockState(), wall = Blocks.BARRIER.defaultBlockState();
        RandomSource random = RandomSource.create(c.asLong() ^ theme.ordinal());
        int outer = r + 2 + DECOR;
        for (int x = -outer; x <= outer; x++) for (int z = -outer; z <= outer; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > outer) continue;
            BlockPos at = c.offset(x, -1, z);
            if (d <= r) {
                level.setBlock(at, floor(theme, p, x, z, d, r), 2);
                for (int y = 1; y <= CLEAR_ABOVE; y++) clear(level, at.above(y), air);
            } else if (d <= r + 1.5) {
                for (int y = -1; y <= CLEAR_ABOVE - 2; y++) level.setBlock(at.above(1 + y), wall, 2);
            } else { // 壁の外: 前の場の飾りを消す
                for (int y = -CLEAR_BELOW; y <= CLEAR_ABOVE; y++) clear(level, at.above(y), air);
            }
        }
        decorate(level, c, theme, p, random);
    }

    private static void clear(ServerLevel level, BlockPos pos, BlockState air) {
        if (!level.getBlockState(pos).isAir()) level.setBlock(pos, air, 2);
    }

    /** 床の 1 ブロック */
    private static BlockState floor(Theme theme, Palette p, int x, int z, double d, int r) {
        int ri = (int) Math.round(d);
        if (d < 1.5) return p.light;
        if (ri >= r - 1) return p.edge;
        double angle = Math.toDegrees(Math.atan2(z, x));
        switch (theme) {
            case BASE -> { // 4 ブロックごとの継ぎ目の格子、十字の光る線
                if (Math.abs(x) < 1 || Math.abs(z) < 1) return ri % 4 == 0 ? p.light : p.accent;
                if (ri % 6 == 0) return p.ring;
                return (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? s(ModTechBlocks.TECH_PANEL_DARK.get()) : p.floor;
            }
            case RULER -> { // 12 の目盛り（r-3 に金）・長針（真北）・短針（4 時）
                if (ri == r - 3 && nearAngle(angle, 30.0, 4.0)) return p.accent;
                if (ri == r - 4 && nearAngle(angle, 90.0, 5.0)) return p.accent; // XII の所は 2 重
                if (ri <= r - 5 && Math.abs(x) < 1 && z < 0) return p.accent; // 長針（-Z = 北）
                if (ri <= (r - 5) * 2 / 3 && onRay(x, z, 30.0)) return p.accent; // 短針
                if (ri % 6 == 0) return p.ring;
                return p.floor;
            }
            case AEC -> { // 金（ルクシオン）・鉄（ガリアフォート）・紫（メメントモリ）の 3 つの扇
                if (ri % 6 == 0) return p.ring;
                double a = Math.floorMod((long) Math.floor(angle + 90.0), 360);
                if (ri == 12 || ri == 13) return a < 120 ? s(Blocks.GOLD_BLOCK) : a < 240 ? s(Blocks.IRON_BLOCK) : s(Blocks.AMETHYST_BLOCK);
                if (nearAngle(angle + 90.0, 120.0, 2.0)) return p.light;
                return p.floor;
            }
            case ZERO -> { // 紅い亀裂（乱れた線）が中心から走る
                if (ri % 7 == 0) return p.ring;
                if (nearAngle(angle + Math.sin(d * 0.6) * 9.0, 72.0, 2.5)) return p.accent;
                return (x * 31 + z * 17) % 5 == 0 ? s(Blocks.DEEPSLATE_TILES) : p.floor;
            }
            default -> {
                if (ri % 6 == 0) return p.ring;
                if (ri == 12 && (Math.abs(x) < 1 || Math.abs(z) < 1 || Math.abs(Math.abs(x) - Math.abs(z)) < 1)) return p.light;
                if (theme == Theme.MEMENTO && ri % 6 == 3 && nearAngle(angle, 45.0, 6.0)) return p.accent;
                if (theme == Theme.LUXION_EX && ri % 6 == 3 && nearAngle(angle, 45.0, 3.0)) return p.accent;
                return p.floor;
            }
        }
    }

    /** angle（度）が step の倍数から tolerance 度以内か */
    private static boolean nearAngle(double angle, double step, double tolerance) {
        double m = Math.floorMod((long) Math.round(angle * 10.0), (long) Math.round(step * 10.0)) / 10.0;
        return m <= tolerance || step - m <= tolerance;
    }

    /** 中心から angle 度（北から右回り）の向きの線の上か */
    private static boolean onRay(int x, int z, double clockDeg) {
        double rad = Math.toRadians(clockDeg);
        double dx = Math.sin(rad), dz = -Math.cos(rad);
        double along = x * dx + z * dz;
        double across = Math.abs(x * dz - z * dx);
        return along > 0 && across < 0.7;
    }

    /** 壁の外の飾り（光の柱・胸壁・花壇・崩れた柱など） */
    private static void decorate(ServerLevel level, BlockPos c, Theme theme, Palette p, RandomSource random) {
        int r = TrialManager.ARENA_RADIUS;
        int ring = r + 4;
        switch (theme) {
            case QUARTZ -> {
                for (int i = 0; i < 8; i++) pillar(level, at(c, ring, i * 45.0), s(Blocks.QUARTZ_PILLAR), 6, s(Blocks.SEA_LANTERN));
            }
            case BASE -> {
                for (int i = 0; i < 8; i++) {
                    BlockPos b = at(c, ring, i * 45.0 + 22.5);
                    pillar(level, b, s(ModTechBlocks.TECH_PANEL_DARK.get()), 7, s(ModTechBlocks.TECH_LIGHT_PANEL.get()));
                    for (int y = 1; y <= 6; y += 2) level.setBlock(b.above(y), s(ModTechBlocks.TECH_LIGHT_LINE.get()), 2);
                }
                for (int i = 0; i < 32; i++) level.setBlock(at(c, r + 2, i * 11.25).below(), s(ModTechBlocks.TECH_HAZARD.get()), 2); // 壁の外の縁
            }
            case LUXION, LUXION_EX -> {
                boolean ex = theme == Theme.LUXION_EX;
                for (int i = 0; i < 12; i++) {
                    BlockPos b = at(c, ring, i * 30.0);
                    int h = 8 + (i % 2) * 4;
                    pillar(level, b, ex ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : s(Blocks.QUARTZ_PILLAR), h, s(Blocks.GOLD_BLOCK));
                    level.setBlock(b.above(h + 1), ex ? s(Blocks.SHROOMLIGHT) : s(Blocks.END_ROD), 2);
                }
            }
            case GALLIA -> { // 狭間のある胸壁
                for (int i = 0; i < 72; i++) {
                    BlockPos b = at(c, r + 3, i * 5.0);
                    for (int y = -1; y <= 3; y++) level.setBlock(b.above(y), s(Blocks.DEEPSLATE_BRICKS), 2);
                    level.setBlock(b.above(4), i % 2 == 0 ? s(Blocks.DEEPSLATE_BRICK_WALL) : s(Blocks.IRON_BARS), 2);
                }
                for (int i = 0; i < 4; i++) {
                    BlockPos b = at(c, r + 6, i * 90.0 + 45.0);
                    pillar(level, b, s(Blocks.POLISHED_DEEPSLATE), 10, s(Blocks.IRON_BLOCK));
                    level.setBlock(b.above(11), s(Blocks.LANTERN), 2);
                }
            }
            case MEMENTO -> { // 死蝶の庭: ウィザーローズの花壇と紫の結晶
                for (int i = 0; i < 48; i++) {
                    BlockPos b = at(c, r + 3 + random.nextInt(3), i * 7.5);
                    level.setBlock(b.below(), s(Blocks.SOUL_SOIL), 2);
                    level.setBlock(b, random.nextInt(3) == 0 ? s(Blocks.AMETHYST_CLUSTER) : s(Blocks.WITHER_ROSE), 2);
                }
                for (int i = 0; i < 6; i++) pillar(level, at(c, ring + 2, i * 60.0), s(Blocks.OBSIDIAN), 5 + random.nextInt(4), s(Blocks.CRYING_OBSIDIAN));
            }
            case RULER -> { // 金の時計の柱 12 本（XII だけ高い）
                for (int i = 0; i < 12; i++) {
                    BlockPos b = at(c, ring, i * 30.0 - 90.0);
                    pillar(level, b, s(Blocks.QUARTZ_PILLAR), i == 0 ? 12 : 7, s(Blocks.GOLD_BLOCK));
                    level.setBlock(b.above(i == 0 ? 13 : 8), s(Blocks.SEA_LANTERN), 2);
                }
            }
            case ZERO -> { // 崩れた柱と、まわりに浮かぶ瓦礫
                for (int i = 0; i < 10; i++) {
                    BlockPos b = at(c, ring + random.nextInt(3), i * 36.0 + random.nextInt(12));
                    int h = 3 + random.nextInt(9);
                    for (int y = -1; y < h; y++) level.setBlock(b.above(y), random.nextInt(4) == 0 ? s(Blocks.CRACKED_DEEPSLATE_BRICKS) : s(Blocks.DEEPSLATE_BRICKS), 2);
                    if (random.nextBoolean()) level.setBlock(b.above(h), s(Blocks.SHROOMLIGHT), 2);
                }
                for (int i = 0; i < 24; i++) { // 浮かぶ瓦礫
                    BlockPos b = at(c, r + 3 + random.nextInt(DECOR - 1), random.nextInt(360)).above(4 + random.nextInt(16));
                    level.setBlock(b, random.nextInt(3) == 0 ? s(Blocks.RED_NETHER_BRICKS) : s(Blocks.COBBLED_DEEPSLATE), 2);
                }
            }
            case AEC -> {
                BlockState[] tops = {s(Blocks.GOLD_BLOCK), s(Blocks.IRON_BLOCK), s(Blocks.AMETHYST_BLOCK)};
                for (int i = 0; i < 9; i++) pillar(level, at(c, ring, i * 40.0 - 90.0 + 20.0), s(Blocks.QUARTZ_PILLAR), 8, tops[i / 3]);
            }
        }
    }

    /** 中心から dist、北から右回りに deg 度の所（床の高さ） */
    private static BlockPos at(BlockPos c, int dist, double deg) {
        double rad = Math.toRadians(deg);
        return c.offset((int) Math.round(Math.sin(rad) * dist), 0, (int) Math.round(-Math.cos(rad) * dist));
    }

    /** 床の高さから下 1・上 h の柱と、頂の飾り */
    private static void pillar(ServerLevel level, BlockPos base, BlockState body, int h, BlockState top) {
        for (int y = -1; y < h; y++) level.setBlock(base.above(y), body, 2);
        level.setBlock(base.above(h), top, 2);
    }
}
