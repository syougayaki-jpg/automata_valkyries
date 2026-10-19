package com.automatavalkyries.world;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModStructures;
import com.automatavalkyries.registry.ModTechBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.function.Supplier;

/**
 * T3「クロノ・バスティオン」の空中庭園要塞（2026-10-19 ユーザー指定「ランダム生成型ではなく空中庭園要塞としてきれいにデザインする」。
 * 質問で: 段々の島＋中央の塔、形は同じで飾りだけ揺らす、白い要塞＋緑と水）。形は毎回同じで、花・木の種類と位置・敵の位置だけ種で少し変わる。
 * 中心からの距離 r と向きで、柱（x, z の 1 列）ごとに描く（どの塊から描いても同じになる）。高さは外周の庭の床 Y から:
 * <ul>
 *   <li>外周の庭（r ≤ {@value #R1}、床 Y）: 芝と花、円い水路（r 36〜37.5）、白い環状の道（r 31.5〜33.5）、東西南北の大通り。縁に強化ガラスの手すりと灯りの柱（1 つおきに対空砲）、
 *       木、4 つの東屋（宝箱）、6 つの転送ゲート。東西南北に着陸の甲板（r 44〜53）</li>
 *   <li>中段（r ≤ {@value #R2}、床 Y+7）: 光る窓の白い擁壁、生け垣と花の輪、白い広場。要塞守護機と報酬の宝箱。大通りから階段</li>
 *   <li>上段（r ≤ {@value #R3}、床 Y+14）: 八角の白い塔（高さ 30、斜めの面に縦の窓、7 ごとに紫の帯、頂に光の尖塔）。塔の 1 階が奥の中枢:
 *       真ん中に封印の召喚台、北の面に眠れる時の門、南に戸口、宝箱 2 つ</li>
 *   <li>下: 逆さの円錐の岩（真ん中ほど深く、最大 36）。下面に紫に光る 2 重の輪。水路から 4 本の水の柱が強化ガラスの管で下へ落ちる（水は管から出ない）</li>
 * </ul>
 */
public final class SkyGardenFortress {

    public static final int R1 = 44, R2 = 28, R3 = 14, PAD = 53, TOWER = 30;
    static final int T2 = 7, T3 = 14;
    private static final Block[] FLOWERS = {Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.LILY_OF_THE_VALLEY,
            Blocks.BLUE_ORCHID, Blocks.WHITE_TULIP, Blocks.PINK_TULIP};

    private SkyGardenFortress() {
    }

    public static class Piece extends StructurePiece {
        final BlockPos center;
        final long seed;

        public Piece(BlockPos center, long seed) {
            super(ModStructures.SKY_GARDEN.get(), 0, new BoundingBox(center.getX() - PAD - 1, center.getY() - 42, center.getZ() - PAD - 1,
                    center.getX() + PAD + 1, center.getY() + T3 + TOWER + 12, center.getZ() + PAD + 1));
            this.center = center;
            this.seed = seed;
        }

        public Piece(CompoundTag tag) {
            super(ModStructures.SKY_GARDEN.get(), tag);
            this.center = new BlockPos(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"));
            this.seed = tag.getLong("Seed");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("CX", center.getX());
            tag.putInt("CY", center.getY());
            tag.putInt("CZ", center.getZ());
            tag.putLong("Seed", seed);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource ignored, BoundingBox chunk, ChunkPos cp, BlockPos pivot) {
            Draw d = new Draw(new CorruptedBases.Painter(level, chunk, CorruptedBases.Kind.CHRONO_BASTION), center, seed);
            int xa = Math.max(chunk.minX(), boundingBox.minX()), xb = Math.min(chunk.maxX(), boundingBox.maxX());
            int za = Math.max(chunk.minZ(), boundingBox.minZ()), zb = Math.min(chunk.maxZ(), boundingBox.maxZ());
            for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) d.column(x - center.getX(), z - center.getZ());
            d.features(RandomSource.create(seed)); // 木・柱・東屋・ゲート・敵（どの塊からでも同じ順に乱数を引く。塊の外の分は置かない）
        }
    }

    /** 描く道具（中心からの相対座標 dx, dz と、外周の庭の床からの高さ h で置く） */
    static final class Draw {
        final CorruptedBases.Painter p;
        final int cx, y0, cz;
        final long seed;

        Draw(CorruptedBases.Painter p, BlockPos center, long seed) {
            this.p = p;
            this.cx = center.getX();
            this.y0 = center.getY();
            this.cz = center.getZ();
            this.seed = seed;
        }

        void set(int dx, int h, int dz, BlockState s) {
            p.set(cx + dx, y0 + h, cz + dz, s);
        }

        static BlockState t(Supplier<? extends Block> b) {
            return b.get().defaultBlockState();
        }

        static final BlockState AIR = Blocks.AIR.defaultBlockState();

        /** 0〜1 の、柱ごとに決まった乱数 */
        double hash(int dx, int dz, int salt) {
            long h = seed ^ (dx * 0x9E3779B97F4A7C15L) ^ (dz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
            h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
            return ((h ^ (h >>> 32)) >>> 11) * 0x1.0p-53;
        }

        /** 東西南北の大通りの上か: 返すのは中心からの距離（大通りの向きに測る）、違えば -1。half は大通りの片側の幅 */
        static int along(int dx, int dz, int half) {
            if (Math.abs(dx) <= half && Math.abs(dz) >= Math.abs(dx)) return Math.abs(dz);
            if (Math.abs(dz) <= half && Math.abs(dx) > Math.abs(dz)) return Math.abs(dx);
            return -1;
        }

        static int lateral(int dx, int dz) {
            return Math.abs(dz) >= Math.abs(dx) ? Math.abs(dx) : Math.abs(dz);
        }

        /** 階段の向き（中心の方が高い） */
        static Direction inward(int dx, int dz) {
            if (Math.abs(dz) >= Math.abs(dx)) return dz < 0 ? Direction.SOUTH : Direction.NORTH;
            return dx < 0 ? Direction.EAST : Direction.WEST;
        }

        // ------------------------------------------------------------------ 柱ごと

        void column(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int pad = along(dx, dz, 4);
            if (pad > R1 && pad <= PAD) {
                landingPad(dx, dz, pad);
                return;
            }
            if (r > R1 + 0.5) return;
            underside(dx, dz, r);
            garden(dx, dz, r);
            waterPillar(dx, dz);
            if (r <= R2 + 0.5) tier(dx, dz, r, T2, R2);
            int a = along(dx, dz, 2);
            if (a >= R2 && a < R2 + T2) stairs(dx, dz, a, 0, R2);         // 外周 → 中段
            if (a >= R3 && a < R3 + T2) stairs(dx, dz, a, T2, R3);        // 中段 → 上段
            int b = along(dx, dz, 3);
            if (lateral(dx, dz) == 3 && b >= R2 && b < R2 + T2) balustrade(dx, dz, b, 0, R2);
            if (lateral(dx, dz) == 3 && b >= R3 && b < R3 + T2) balustrade(dx, dz, b, T2, R3);
            if (r <= R3 + 0.5) tier(dx, dz, r, T3, R3);
            tower(dx, dz);
        }

        /** 下: 逆さの円錐の岩。上の 2 段は土、下面に紫に光る輪 */
        void underside(int dx, int dz, double r) {
            int depth = (int) Math.max(3, Math.min(36, (R1 + 0.5 - r) * 0.8)) + (int) (hash(dx, dz, 1) * 3);
            boolean ring = (r >= 19 && r < 20.5) || (r >= 32 && r < 33.5);
            for (int k = 1; k <= depth; k++) {
                BlockState s;
                if (k == depth && ring) s = t(ModTechBlocks.TECH_PHASE_PANEL);
                else if (k <= 2) s = Blocks.DIRT.defaultBlockState();
                else if (k > 22) s = Blocks.DEEPSLATE.defaultBlockState();
                else {
                    double n = hash(dx, dz + k * 7, 2);
                    s = (n < 0.5 ? Blocks.STONE : n < 0.75 ? Blocks.ANDESITE : n < 0.9 ? Blocks.TUFF : Blocks.CALCITE).defaultBlockState();
                }
                set(dx, -k, dz, s);
            }
        }

        /** 外周の庭の床（高さ 0）とその上の草花 */
        void garden(int dx, int dz, double r) {
            int ave = along(dx, dz, 2);
            if (r >= R1 - 1) { // 縁: 白い縁石と強化ガラスの手すり（着陸の甲板の口は空ける）
                set(dx, 0, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
                set(dx, 1, dz, along(dx, dz, 4) > 0 ? AIR : t(ModTechBlocks.TECH_GLASS));
                return;
            }
            if (ave >= R2) { // 大通り（真ん中に光る線）
                set(dx, 0, dz, t(lateral(dx, dz) == 0 ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_WHITE));
                set(dx, -1, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
                return;
            }
            if (r >= 36 && r < 37.6) { // 円い水路（底と岸は白）
                set(dx, 0, dz, Blocks.WATER.defaultBlockState());
                set(dx, -1, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
                return;
            }
            if (r >= 31.5 && r < 33.5) { // 環状の道（真ん中に光る線）
                set(dx, 0, dz, t(Math.abs(r - 32.5) < 0.5 ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_WHITE));
                return;
            }
            if ((r >= 35.4 && r < 36) || (r >= 37.6 && r < 38.2)) { // 水路の岸
                set(dx, 0, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
                return;
            }
            set(dx, 0, dz, Blocks.GRASS_BLOCK.defaultBlockState());
            boolean bed = r >= R2 && r < 31.5; // 擁壁の足元の花壇（花が多い）
            double f = hash(dx, dz, 3);
            if (f < (bed ? 0.55 : 0.07)) set(dx, 1, dz, FLOWERS[(int) (hash(dx, dz, 4) * FLOWERS.length)].defaultBlockState());
            else if (!bed && f < 0.16) set(dx, 1, dz, Blocks.GRASS.defaultBlockState());
        }

        /** 中段・上段の台（top = 床の高さ、radius = 縁）。外側は光る窓の白い擁壁、上は縁の手すり・生け垣の輪・広場 */
        void tier(int dx, int dz, double r, int top, int radius) {
            int base = top == T2 ? 1 : T2 + 1;
            double ang = Math.toDegrees(Math.atan2(dz, dx));
            boolean window = Math.floorMod((int) Math.floor(ang / 6.0), 2) == 0;
            for (int h = base; h <= top; h++) {
                int k = h - base + 1; // 擁壁の下からの段（1〜7）
                BlockState s;
                if (r >= radius - 1) {
                    s = k == 5 ? t(ModTechBlocks.TECH_LIGHT_LINE) : (k == 3 || k == 4) && window ? t(ModTechBlocks.TECH_GLASS) : t(ModTechBlocks.TECH_PANEL_WHITE);
                } else if (r >= radius - 2 && (k == 3 || k == 4) && window) {
                    s = t(ModTechBlocks.TECH_LIGHT_PANEL); // 窓の奥が光る
                } else {
                    s = h >= top - 1 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                }
                set(dx, h, dz, s);
            }
            int ave = along(dx, dz, 2);
            boolean stairsHere = ave >= 0 && ave <= radius + 1;
            if (r >= radius - 1.5) { // 縁と手すり（階段の口は空ける）
                set(dx, top, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
                set(dx, top + 1, dz, stairsHere ? AIR : t(ModTechBlocks.TECH_GLASS));
                return;
            }
            if (top == T2 && r >= 22.5 && !stairsHere) { // 中段の生け垣と花の輪
                set(dx, top, dz, Blocks.GRASS_BLOCK.defaultBlockState());
                if (r >= 25.4) set(dx, top + 1, dz, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                else if (hash(dx, dz, 5) < 0.45) set(dx, top + 1, dz, FLOWERS[(int) (hash(dx, dz, 6) * FLOWERS.length)].defaultBlockState());
                return;
            }
            double ring = top == T2 ? 18.0 : 9.5;
            boolean line = Math.abs(r - ring) < 0.5 || (stairsHere && lateral(dx, dz) == 0);
            set(dx, top, dz, t(line ? ModTechBlocks.TECH_LIGHT_LINE : Math.abs(r - ring) < 1.5 ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_WHITE));
        }

        /** 大通りの階段: a は中心からの距離、from は下の床の高さ、wall は上の台の縁。7 段で 7 上がる */
        void stairs(int dx, int dz, int a, int from, int wall) {
            int k = wall + T2 - a; // 1〜7
            for (int h = from + 1; h < from + k; h++) set(dx, h, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
            set(dx, from + k, dz, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, inward(dx, dz)));
            for (int h = from + k + 1; h <= from + k + 3; h++) set(dx, h, dz, AIR);
        }

        /** 階段の脇の白い欄干と、その上の強化ガラス */
        void balustrade(int dx, int dz, int b, int from, int wall) {
            int k = wall + T2 - b;
            for (int h = from + 1; h <= from + k; h++) set(dx, h, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
            set(dx, from + k + 1, dz, t(ModTechBlocks.TECH_GLASS));
        }

        /** 着陸の甲板（東西南北。白い床、縁の警告の帯、真ん中の光る線、両脇の強化ガラスの手すり。先は開いている） */
        void landingPad(int dx, int dz, int a) {
            int lat = lateral(dx, dz);
            boolean edge = lat == 4 || a == PAD;
            set(dx, 0, dz, t(edge ? ModTechBlocks.TECH_HAZARD : lat == 0 ? ModTechBlocks.TECH_LIGHT_LINE
                    : (a - R1) % 3 == 0 && lat == 2 ? ModTechBlocks.TECH_LIGHT_PANEL : ModTechBlocks.TECH_PANEL_WHITE));
            set(dx, -1, dz, t(ModTechBlocks.TECH_PANEL_DARK));
            set(dx, -2, dz, t(edge ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_DARK));
            set(dx, 1, dz, lat == 4 ? t(ModTechBlocks.TECH_GLASS) : AIR);
        }

        /** 水路から下へ落ちる 4 本の水の柱（斜めの 4 か所。強化ガラスの管の中だけ） */
        void waterPillar(int dx, int dz) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean channel = ax >= 27 && ax <= 29 && az >= 27 && az <= 29 && Math.abs(ax - az) <= 1; // 水路から管への水の溝
            int ox = ax - 29, oz = az - 29;
            boolean tube = Math.abs(ox) <= 1 && Math.abs(oz) <= 1, core = ox == 0 && oz == 0;
            if (channel) {
                set(dx, 0, dz, Blocks.WATER.defaultBlockState());
                set(dx, 1, dz, AIR);
                if (!tube) set(dx, -1, dz, t(ModTechBlocks.TECH_PANEL_WHITE));
            }
            if (!tube) return;
            for (int k = 1; k <= 30; k++) set(dx, -k, dz, core ? Blocks.WATER.defaultBlockState() : t(ModTechBlocks.TECH_GLASS));
            set(dx, -31, dz, t(core ? ModTechBlocks.TECH_LIGHT_PANEL : ModTechBlocks.TECH_PHASE_PANEL));
        }

        /** 八角の塔（上段の真ん中）。1 階が奥の中枢（高さ 11）、北の面に眠れる時の門、南に戸口 */
        void tower(int dx, int dz) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean inside = ax <= 5 && az <= 5 && ax + az <= 7;
            if (!inside) return;
            boolean shell = !(ax <= 4 && az <= 4 && ax + az <= 6);
            for (int h = 1; h <= TOWER; h++) {
                int y = T3 + h;
                BlockState s;
                if (shell) {
                    boolean diagonal = ax + az == 7 && ax >= 2 && az >= 2;
                    if (h % 7 == 0) s = t(ModTechBlocks.TECH_PHASE_PANEL);
                    else if (diagonal && h % 7 >= 2 && h % 7 <= 5) s = t(ModTechBlocks.TECH_GLASS);
                    else s = t(ModTechBlocks.TECH_PANEL_WHITE);
                    if (dz == 5 && ax <= 1 && h <= 4) s = AIR;                    // 南の戸口
                    if (dz == -5 && ax <= 2 && h <= 6) {                          // 北の眠れる時の門（紫の枠の中の 3×4）
                        s = ax == 2 || h == 1 || h == 6 ? t(ModTechBlocks.TECH_PHASE_PANEL) : t(ModTechBlocks.RULER_PORTAL);
                    }
                } else if (h <= 11) {
                    s = AIR;
                } else if (h == 12) {
                    s = t((ax + az) % 3 == 0 ? ModTechBlocks.TECH_LIGHT_PANEL : ModTechBlocks.TECH_PANEL_WHITE);
                } else {
                    s = t(ModTechBlocks.TECH_PANEL_WHITE);
                }
                set(dx, y, dz, s);
            }
            if (!shell) set(dx, T3, dz, t((ax + az) % 4 == 0 ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_WHITE)); // 中枢の床
            set(dx, T3 + TOWER + 1, dz, t(shell ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_WHITE));       // 頂の冠
            if (dx == 0 && dz == 0) {
                set(0, T3, 0, t(ModTechBlocks.TECH_PANEL_WHITE));
                set(0, T3 + 1, 0, ModTechBlocks.BASTION_ALTAR.get().defaultBlockState());
                for (int h = TOWER + 2; h <= TOWER + 8; h++) set(0, T3 + h, 0, t(h % 2 == 0 ? ModTechBlocks.TECH_LIGHT_PANEL : ModTechBlocks.TECH_PHASE_PANEL)); // 光の尖塔
                set(0, T3 + TOWER + 9, 0, Blocks.END_ROD.defaultBlockState());
            }
        }

        // ------------------------------------------------------------------ 置き物と敵

        /** 中心からの向き deg・距離 r の柱 */
        static int[] polar(double deg, double r) {
            double a = Math.toRadians(deg);
            return new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
        }

        static boolean nearAxis(double deg, double tol) {
            double m = Math.floorMod((int) Math.round(deg * 10), 900) / 10.0;
            return m < tol || m > 90 - tol;
        }

        void features(RandomSource rand) {
            // 外周の縁の灯りの柱（15 度ごと、1 つおきに対空砲）
            for (int i = 0; i < 24; i++) {
                double deg = i * 15 + 7.5;
                int[] c = polar(deg, R1 - 1.0);
                set(c[0], 1, c[1], t(ModTechBlocks.TECH_PANEL_WHITE));
                set(c[0], 2, c[1], t(ModTechBlocks.TECH_PANEL_WHITE));
                set(c[0], 3, c[1], t(ModTechBlocks.TECH_LIGHT_PANEL));
                if (i % 2 == 0) spawn(ModEntities.PULSE_PHALANX, c, 4, rand);
            }
            // 環状の道の外側の灯り
            for (int i = 0; i < 24; i++) {
                double deg = i * 15;
                if (nearAxis(deg, 6)) continue;
                int[] c = polar(deg, 34.4);
                set(c[0], 1, c[1], t(ModTechBlocks.TECH_PANEL_WHITE));
                set(c[0], 2, c[1], t(ModTechBlocks.TECH_LIGHT_PANEL));
            }
            // 中段・上段の縁の柱と対空砲
            for (int i = 0; i < 8; i++) {
                int[] c = polar(i * 45 + 22.5, R2 - 1.0);
                set(c[0], T2 + 1, c[1], t(ModTechBlocks.TECH_PANEL_WHITE));
                set(c[0], T2 + 2, c[1], t(ModTechBlocks.TECH_LIGHT_PANEL));
                spawn(ModEntities.PULSE_PHALANX, c, T2 + 3, rand);
            }
            for (int i = 0; i < 4; i++) {
                int[] c = polar(i * 90 + 45, R3 - 1.0);
                set(c[0], T3 + 1, c[1], t(ModTechBlocks.TECH_LIGHT_PANEL));
                spawn(ModEntities.PULSE_PHALANX, c, T3 + 2, rand);
            }
            for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) spawn(ModEntities.PULSE_PHALANX, c, T3 + TOWER + 2, rand); // 塔の頂
            // 東屋（4 つ。宝箱）
            for (int i = 0; i < 4; i++) pavilion(polar(i * 90 + 22.5, 40.0), rand);
            // 木（外周の庭。大通り・水の柱・東屋の向きは避ける）
            for (int i = 0; i < 20; i++) {
                double deg = i * 18 + 9 + (rand.nextDouble() - 0.5) * 6;
                double r = 39.5 + rand.nextDouble() * 2.0;
                int kind = rand.nextInt(10);
                int height = 4 + rand.nextInt(3);
                double m45 = Math.floorMod((int) Math.round(deg), 90);
                if (nearAxis(deg, 8) || Math.abs(m45 - 45) < 9 || Math.abs(m45 - 22.5) < 8) continue;
                tree(polar(deg, r), kind, height);
            }
            // 中段の小さな花の木
            for (int i = 0; i < 4; i++) tree(polar(i * 90 + 45, 24.0), 7, 3);
            // 転送ゲート（外周 6・中段 2）
            for (int i = 0; i < 6; i++) gate(polar(i * 60 + 15, 34.8), 1);
            gate(polar(67.5, 23.5), T2 + 1);
            gate(polar(247.5, 23.5), T2 + 1);
            // 中段の宝箱 2 つ（花の輪の中）
            chest(polar(157.5, 23.5), T2 + 1, rand);
            chest(polar(337.5, 23.5), T2 + 1, rand);
            // 奥の中枢の宝箱 2 つ
            chest(new int[]{3, 2}, T3 + 1, rand);
            chest(new int[]{-3, 2}, T3 + 1, rand);
            // 要塞守護機（中段の南）と報酬の宝箱の所
            if (p.inside(cx + 5, y0 + T2 + 1, cz + 19)) {
                p.boss(ModEntities.BASTION_KEEPER, cx + 5.5, y0 + T2 + 1, cz + 19.5, new BlockPos(cx - 5, y0 + T2 + 1, cz + 19), rand);
            }
            // 雑魚
            for (int i = 0; i < 5; i++) spawn(ModEntities.CHRONO_TROOPER, polar(i * 72 + 30 + rand.nextInt(20), 32.5), 1, rand); // 環状の道の上
            for (int i = 0; i < 3; i++) spawn(ModEntities.CHRONO_GUARDIAN, polar(i * 90 + 90 + 6, 38.0), 1, rand);
            for (int i = 0; i < 3; i++) spawn(ModEntities.DELAY_NODE, polar(i * 120 + 60 + rand.nextInt(30), 40.0), 3, rand);
            for (int i = 0; i < 6; i++) spawn(ModEntities.CHRONO_INTERCEPTOR, polar(i * 60 + rand.nextInt(30), 30.0 + rand.nextInt(10)), 12, rand);
            for (int i = 0; i < 2; i++) spawn(ModEntities.SIREN_EYE, polar(i * 180 + 45, 36.0), 6, rand);
            spawn(ModEntities.CHRONO_GUARDIAN, polar(135, 20.0), T2 + 1, rand);
            spawn(ModEntities.CHRONO_GUARDIAN, polar(315, 20.0), T2 + 1, rand);
            spawn(ModEntities.CHRONO_TROOPER, polar(200, 19.0), T2 + 1, rand);
            spawn(ModEntities.CHRONO_TROOPER, polar(20, 19.0), T2 + 1, rand);
            spawn(ModEntities.DELAY_NODE, polar(270 + rand.nextInt(30), 24.0), T2 + 3, rand);
        }

        <T extends net.minecraft.world.entity.Mob> void spawn(Supplier<? extends net.minecraft.world.entity.EntityType<T>> type, int[] c, int h, RandomSource rand) {
            p.spawn(type, cx + c[0] + 0.5, y0 + h, cz + c[1] + 0.5, rand);
        }

        void gate(int[] c, int h) {
            p.gate(cx + c[0], y0 + h, cz + c[1]);
        }

        void chest(int[] c, int h, RandomSource rand) {
            p.chest(new int[]{cx + c[0], cz + c[1]}, y0 + h, CorruptedBases.Kind.CHRONO_BASTION.chestLoot(), rand);
        }

        /** 東屋: 5×5 の白い床と 4 本の柱、強化ガラスの屋根（真ん中に照明）、真ん中に宝箱 */
        void pavilion(int[] c, RandomSource rand) {
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    int x = c[0] + a, z = c[1] + b;
                    boolean corner = Math.abs(a) == 2 && Math.abs(b) == 2;
                    set(x, 0, z, t(a == 0 && b == 0 ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_WHITE));
                    for (int h = 1; h <= 3; h++) set(x, h, z, corner ? t(ModTechBlocks.TECH_PANEL_WHITE) : AIR);
                    set(x, 4, z, t(a == 0 && b == 0 ? ModTechBlocks.TECH_LIGHT_PANEL : Math.abs(a) == 2 || Math.abs(b) == 2 ? ModTechBlocks.TECH_PANEL_WHITE : ModTechBlocks.TECH_GLASS));
                }
            }
            p.container(cx + c[0], y0 + 1, cz + c[1], Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(rand.nextInt(4))),
                    CorruptedBases.Kind.CHRONO_BASTION.chestLoot(), rand);
        }

        /** 木: kind 0〜3 ナラ、4〜6 シラカバ、7〜8 花咲くツツジ、9 サクラ。葉は散らない */
        void tree(int[] c, int kind, int height) {
            int base = Math.sqrt(c[0] * c[0] + c[1] * c[1]) < R2 + 0.5 ? T2 + 1 : 1; // 中段の木は中段の床の上に
            Block log = kind <= 3 ? Blocks.OAK_LOG : kind <= 6 ? Blocks.BIRCH_LOG : kind <= 8 ? Blocks.OAK_LOG : Blocks.CHERRY_LOG;
            Block leaves = kind <= 3 ? Blocks.OAK_LEAVES : kind <= 6 ? Blocks.BIRCH_LEAVES : kind <= 8 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.CHERRY_LEAVES;
            BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            set(c[0], base - 1, c[1], Blocks.DIRT.defaultBlockState());
            int top = base + height - 1;
            int radius = height <= 3 ? 1 : 2;
            for (int h = top - radius; h <= top + 1; h++) {
                int rr = h == top + 1 ? radius - 1 : radius;
                for (int a = -rr; a <= rr; a++) {
                    for (int b = -rr; b <= rr; b++) {
                        if (Math.abs(a) == rr && Math.abs(b) == rr && rr > 1) continue; // 角を落として丸く
                        set(c[0] + a, h, c[1] + b, leaf);
                    }
                }
            }
            for (int h = base; h <= top; h++) set(c[0], h, c[1], log.defaultBlockState());
        }
    }
}
