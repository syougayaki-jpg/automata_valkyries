package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.ChronoMidBoss;
import com.automatavalkyries.entity.chrono.MimicContainerEntity;
import com.automatavalkyries.entity.chrono.PhaseCondenserEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModStructures;
import com.automatavalkyries.registry.ModTechBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 侵食基地のダンジョン（拡張仕様書 §10.1。2026-10-15 ユーザー指定「物資倉庫と基地と工場の三種の生成ダンジョン」。質問で: §10 に合わせる・部屋をつなぐランダム生成・地上にまばら）。
 * <ul>
 *   <li>{@link Kind#SUPPLY_DEPOT 物資倉庫}（入門、3×3 の区画）: 樽の積まれた倉庫。ミミック・コンテナが紛れる。奥に荷役機クレーン・ローダー</li>
 *   <li>{@link Kind#CHRONO_OUTPOST クロノ・アウトリポスト}（T1 前哨監視基地、4×4）: 黒い石の要塞。屋上の対空タレット、中庭の曲射砲、巡回するガーディアン、警報ドローン。奥に前哨司令機ウォッチャー</li>
 *   <li>{@link Kind#DIMENSION_FOUNDRY ディメンション・ファウンドリ}（T2 時空兵站工場、4×4）: 地上の入口の竪穴を {@value #FOUNDRY_DEPTH} ブロック降りた地下の工場。
 *       組立ラインと溶鉱炉の部屋。奥に工場長機ファウンドリ・マスターと、位相アンカーのコンデンサー 4 本</li>
 * </ul>
 * 区画（1 辺 {@value #CELL} ブロックの部屋）を格子に並べ、入口から乱数で枝を伸ばして木につなぎ（少しだけ輪を足す）、入口からいちばん遠い部屋をボスの部屋にする。
 * つながった部屋の間には長さ {@value #CORRIDOR} のガラス屋根の通路（地下の工場は空を映す照明パネルの天井）を挟み、壁に 3 ブロック幅の戸口をあける。
 * 2026-10-15 ユーザー指定で近未来的な建材（{@link com.automatavalkyries.registry.ModTechBlocks}）で建て、天井に照明パネルを 4 ブロックごとに並べて明るくし、
 * 敵は部屋のクロノ転送ゲート（専用のスポナー）から出る。部屋の中身（家具・宝箱・敵・ゲート）は部屋ごとの種から決めるので、どの塊から描いても同じになる
 */
public final class CorruptedBases {

    /**
     * 区画の 1 辺（壁を含む）と、部屋の間の通路の長さ（2026-10-15 ユーザー指定「空が見えるように通路」。ガラス屋根の通路を挟む）。並べる間隔は CELL + CORRIDOR
     */
    public static final int CELL = 13, CORRIDOR = 5, STRIDE = CELL + CORRIDOR, CORRIDOR_H = 4;
    public static final int ROOM_H = 6, BOSS_H = 9, FOUNDRY_DEPTH = 16;
    /** 空中庭園要塞の外周の庭の高さ（地表がそれに近い山の上では、地表の 40 上まで上げる） */
    public static final int SKY_Y = 200, SKY_CLEARANCE = 40, SKY_MAX = 250;
    /** 敵・中ボスを置くか（試験で建物だけを確かめる時に切る。試験の世界に敵を残さないため） */
    public static boolean populate = true;
    static final UUID HEALTH_SCALE_ID = UUID.fromString("5c1e0a7d-3b2f-4e8a-9d41-7a6b2c90f3e1");

    private CorruptedBases() {
    }

    public enum Kind implements StringRepresentable {
        SUPPLY_DEPOT("supply_depot", 3, 6, 8, false, 1.0),
        CHRONO_OUTPOST("chrono_outpost", 4, 10, 13, false, 1.5),
        DIMENSION_FOUNDRY("dimension_foundry", 4, 11, 15, true, 2.0),
        /** T3 の空中庭園要塞（2026-10-19）。部屋の格子ではなく、設計した 1 つの形（{@link SkyGardenFortress}）。空の上に浮かび、行くには飛ぶしかない */
        CHRONO_BASTION("chrono_bastion", 0, 0, 0, false, 3.0);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public final String id;
        final int grid, minRooms, maxRooms;
        final boolean underground;
        /** 中で生まれる雑魚の最大 HP の倍率（倉庫 ×1・基地 ×1.5・工場 ×2・要塞 ×3。中ボスとコンデンサーは除く） */
        public final double healthScale;

        Kind(String id, int grid, int minRooms, int maxRooms, boolean underground, double healthScale) {
            this.id = id;
            this.grid = grid;
            this.minRooms = minRooms;
            this.maxRooms = maxRooms;
            this.underground = underground;
            this.healthScale = healthScale;
        }

        /** 空に浮かぶ設計した要塞か（部屋の格子を使わない） */
        public boolean floating() {
            return this == CHRONO_BASTION;
        }

        @Nullable
        public static Kind byId(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }

        /** ダンジョンのティアに合わせて雑魚の最大 HP を上げ、満タンにする（属性の修飾子なので保存される） */
        public void scaleHealth(Mob mob) {
            if (healthScale == 1.0 || mob instanceof ChronoMidBoss || mob instanceof PhaseCondenserEntity) return;
            AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
            if (hp == null || hp.getModifier(HEALTH_SCALE_ID) != null) return;
            hp.addPermanentModifier(new AttributeModifier(HEALTH_SCALE_ID, "corrupted base tier", healthScale - 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            mob.setHealth(mob.getMaxHealth());
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        ResourceLocation chestLoot() {
            return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "chests/" + id);
        }
    }

    /** 部屋の役 */
    enum Role { ENTRANCE, ROOM, BOSS }

    /** 戸口の向きの印（北 = -Z、南 = +Z、西 = -X、東 = +X） */
    static final int N = 1, S = 2, W = 4, E = 8;

    // ------------------------------------------------------------------ 構造物

    public static class BaseStructure extends Structure {

        public static final Codec<BaseStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
                settingsCodec(i),
                Kind.CODEC.fieldOf("kind").forGetter(s -> s.kind)
        ).apply(i, BaseStructure::new));

        final Kind kind;

        public BaseStructure(StructureSettings settings, Kind kind) {
            super(settings);
            this.kind = kind;
        }

        @Override
        public StructureType<?> type() {
            return ModStructures.CORRUPTED_BASE.get();
        }

        @Override
        protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
            ChunkPos cp = ctx.chunkPos();
            int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
            ChunkGenerator gen = ctx.chunkGenerator();
            int y = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
            if (kind.floating()) { // 空の上: 水・斜面は気にしない。真下の地表が高ければ、そのぶん上げる
                BlockPos sky = new BlockPos(x, Math.min(SKY_MAX, Math.max(SKY_Y, y + SKY_CLEARANCE)), z);
                long seed = ctx.random().nextLong();
                return Optional.of(new GenerationStub(sky, b -> b.addPiece(new SkyGardenFortress.Piece(sky, seed))));
            }
            int floor = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
            if (y - floor > 1) return Optional.empty(); // 水の上には建てない
            int half = kind.grid * STRIDE / 2;
            int lo = y, hi = y;
            for (int[] c : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) { // 斜面がきつい所は避ける
                int h = gen.getFirstOccupiedHeight(x + c[0], z + c[1], Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > 12) return Optional.empty();
            BlockPos center = new BlockPos(x, y, z);
            return Optional.of(new GenerationStub(center, b -> layout(b, ctx.random(), kind, center)));
        }
    }

    /** 区画の並びを決めて部屋を置く */
    static void layout(StructurePiecesBuilder builder, RandomSource rand, Kind kind, BlockPos center) {
        int n = kind.grid;
        int target = kind.minRooms + rand.nextInt(kind.maxRooms - kind.minRooms + 1);
        int[][] doors = new int[n][n];
        boolean[][] used = new boolean[n][n];
        // 入口: 南の辺の真ん中（外へ向く戸口）
        int ex = n / 2, ez = n - 1;
        used[ex][ez] = true;
        List<int[]> open = new ArrayList<>();
        open.add(new int[]{ex, ez});
        int count = 1;
        int[][] dirs = {{0, -1, N, S}, {0, 1, S, N}, {-1, 0, W, E}, {1, 0, E, W}};
        while (count < target && !open.isEmpty()) { // 乱数の木（伸ばせる部屋から 1 つ選び、空いた隣へ）
            int[] c = open.get(rand.nextInt(open.size()));
            List<int[]> next = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !used[nx][nz]) next.add(new int[]{nx, nz, d[2], d[3]});
            }
            if (next.isEmpty()) {
                open.remove(c);
                continue;
            }
            int[] t = next.get(rand.nextInt(next.size()));
            used[t[0]][t[1]] = true;
            doors[c[0]][c[1]] |= t[2];
            doors[t[0]][t[1]] |= t[3];
            open.add(new int[]{t[0], t[1]});
            count++;
        }
        for (int k = 0; k < 2; k++) { // 輪を少し足す（行き止まりばかりにしない）
            int x = rand.nextInt(n), z = rand.nextInt(n);
            int[] d = dirs[rand.nextInt(4)];
            int nx = x + d[0], nz = z + d[1];
            if (nx >= 0 && nz >= 0 && nx < n && nz < n && used[x][z] && used[nx][nz]) {
                doors[x][z] |= d[2];
                doors[nx][nz] |= d[3];
            }
        }
        // ボスの部屋: 入口から戸口をたどっていちばん遠い部屋
        int[][] dist = new int[n][n];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[ex][ez] = 0;
        q.add(new int[]{ex, ez});
        int bx = ex, bz = ez;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (dist[c[0]][c[1]] > dist[bx][bz]) {
                bx = c[0];
                bz = c[1];
            }
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if ((doors[c[0]][c[1]] & d[2]) != 0 && nx >= 0 && nz >= 0 && nx < n && nz < n && dist[nx][nz] < 0) {
                    dist[nx][nz] = dist[c[0]][c[1]] + 1;
                    q.add(new int[]{nx, nz});
                }
            }
        }
        int surface = center.getY();
        int floorY = kind.underground ? surface - FOUNDRY_DEPTH : surface;
        int ox = center.getX() - n * STRIDE / 2, oz = center.getZ() - n * STRIDE / 2;
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                if (!used[x][z]) continue;
                Role role = x == ex && z == ez ? Role.ENTRANCE : x == bx && z == bz ? Role.BOSS : Role.ROOM;
                int d = doors[x][z] | (role == Role.ENTRANCE ? S : 0);
                int x0 = ox + x * STRIDE, z0 = oz + z * STRIDE;
                int top = floorY + (role == Role.BOSS ? BOSS_H : ROOM_H) + 3; // 屋上のタレットの分
                if (role == Role.ENTRANCE && kind.underground) top = surface + ROOM_H + 1;
                int bottom = floorY - (kind.underground ? 1 : 12);   // 地上は土台を下へ伸ばす
                BoundingBox box = new BoundingBox(x0, bottom, z0, x0 + CELL - 1, top, z0 + CELL - 1);
                builder.addPiece(new Room(kind, role, d, box, floorY, surface, rand.nextLong()));
                // 東・南の隣とつながっていれば、その間に通路
                if ((doors[x][z] & E) != 0) {
                    int cx0 = x0 + CELL, cz0 = z0 + CELL / 2 - 2;
                    builder.addPiece(new Corridor(kind, true, new BoundingBox(cx0, bottom, cz0, cx0 + CORRIDOR - 1, floorY + CORRIDOR_H + 6, cz0 + 4), floorY));
                }
                if ((doors[x][z] & S) != 0 && z + 1 < n) {
                    int cx0 = x0 + CELL / 2 - 2, cz0 = z0 + CELL;
                    builder.addPiece(new Corridor(kind, false, new BoundingBox(cx0, bottom, cz0, cx0 + 4, floorY + CORRIDOR_H + 6, cz0 + CORRIDOR - 1), floorY));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 通路

    /** 部屋の間のガラス屋根の通路（幅 3・高さ {@value #CORRIDOR_H}。地下は空を映す照明パネルの天井）。壁の真ん中の高さに光る線 */
    public static class Corridor extends StructurePiece {
        final Kind kind;
        final boolean eastWest;
        final int floorY;

        Corridor(Kind kind, boolean eastWest, BoundingBox box, int floorY) {
            super(ModStructures.BASE_CORRIDOR.get(), 0, box);
            this.kind = kind;
            this.eastWest = eastWest;
            this.floorY = floorY;
        }

        public Corridor(CompoundTag tag) {
            super(ModStructures.BASE_CORRIDOR.get(), tag);
            this.kind = Kind.values()[Math.floorMod(tag.getInt("Kind"), Kind.values().length)];
            this.eastWest = tag.getBoolean("EastWest");
            this.floorY = tag.getInt("FloorY");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("Kind", kind.ordinal());
            tag.putBoolean("EastWest", eastWest);
            tag.putInt("FloorY", floorY);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource ignored, BoundingBox chunk, ChunkPos cp, BlockPos pivot) {
            Painter p = new Painter(level, chunk, kind);
            int x0 = boundingBox.minX(), z0 = boundingBox.minZ(), x1 = boundingBox.maxX(), z1 = boundingBox.maxZ();
            int roof = floorY + CORRIDOR_H + 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = eastWest ? (z == z0 || z == z1) : (x == x0 || x == x1);
                    if (!kind.underground) p.foundation(x, floorY - 1, z, 12);
                    p.set(x, floorY, z, side ? p.wall(x, floorY, z, 0) : Painter.tech(ModTechBlocks.TECH_GRATING));
                    for (int y = floorY + 1; y < roof; y++) {
                        if (side) p.set(x, y, z, y == floorY + 3 ? Painter.tech(ModTechBlocks.TECH_LIGHT_LINE) : p.wall(x, y, z, y - floorY));
                        else p.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                    if (side) p.set(x, roof, z, p.wall(x, roof, z, 0));
                    else p.set(x, roof, z, Painter.tech(kind.underground ? ModTechBlocks.TECH_SKY_PANEL : ModTechBlocks.TECH_GLASS)); // 空が見える
                    if (!kind.underground) for (int y = roof + 1; y <= roof + 5; y++) p.clearNatural(x, y, z); // 屋根の上の土・石をどけて空を見せる
                }
            }
        }
    }

    // ------------------------------------------------------------------ 部屋

    public static class Room extends StructurePiece {
        final Kind kind;
        final Role role;
        final int doors, floorY, surface;
        final long seed;

        Room(Kind kind, Role role, int doors, BoundingBox box, int floorY, int surface, long seed) {
            super(ModStructures.BASE_ROOM.get(), 0, box);
            this.kind = kind;
            this.role = role;
            this.doors = doors;
            this.floorY = floorY;
            this.surface = surface;
            this.seed = seed;
        }

        public Room(CompoundTag tag) {
            super(ModStructures.BASE_ROOM.get(), tag);
            this.kind = Kind.values()[Math.floorMod(tag.getInt("Kind"), Kind.values().length)];
            this.role = Role.values()[Math.floorMod(tag.getInt("Role"), Role.values().length)];
            this.doors = tag.getInt("Doors");
            this.floorY = tag.getInt("FloorY");
            this.surface = tag.getInt("Surface");
            this.seed = tag.getLong("Seed");
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("Kind", kind.ordinal());
            tag.putInt("Role", role.ordinal());
            tag.putInt("Doors", doors);
            tag.putInt("FloorY", floorY);
            tag.putInt("Surface", surface);
            tag.putLong("Seed", seed);
        }

        int height() {
            return role == Role.BOSS ? BOSS_H : ROOM_H;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource ignored, BoundingBox chunk, ChunkPos cp, BlockPos pivot) {
            Painter p = new Painter(level, chunk, kind);
            RandomSource rand = RandomSource.create(seed);          // 部屋ごとの種（どの塊から描いても同じになる）
            int x0 = boundingBox.minX(), z0 = boundingBox.minZ(), x1 = boundingBox.maxX(), z1 = boundingBox.maxZ();
            boolean tallEntrance = role == Role.ENTRANCE && kind.underground;
            int roof = tallEntrance ? surface + ROOM_H + 1 : floorY + height() + 1;
            boolean courtyard = kind == Kind.CHRONO_OUTPOST && role == Role.ROOM && rand.nextInt(4) == 0;
            // 骨組み: 土台・床・壁・天井・中の空気
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (!kind.underground) p.foundation(x, floorY - 1, z, 12);
                    p.set(x, floorY, z, p.floor(x, z));
                    for (int y = floorY + 1; y < roof; y++) {
                        if (edge && !door(x, y, z, x0, z0, x1, z1)) p.set(x, y, z, p.wall(x, y, z, y - floorY));
                        else p.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                    if (courtyard) {
                        if (edge) { // 中庭は天井なし、壁の上に凹凸の狭間
                            if (!door(x, roof, z, x0, z0, x1, z1)) p.set(x, roof, z, p.wall(x, roof, z, 0));
                            if ((x + z) % 2 == 0) p.set(x, roof + 1, z, p.wall(x, roof + 1, z, 0));
                        }
                    } else {
                        p.set(x, roof, z, edge ? p.wall(x, roof, z, 0) : p.ceiling(x, z));
                    }
                }
            }
            if (tallEntrance) stairs(p, x0, z0);
            int cx = x0 + CELL / 2, cz = z0 + CELL / 2;
            switch (role) {
                case ENTRANCE -> {
                    if (!tallEntrance) furnish(p, rand, x0, z0, false, courtyard);
                }
                case ROOM -> {
                    furnish(p, rand, x0, z0, true, courtyard);
                    if (rand.nextInt(10) < 6) { // クロノ転送ゲート（6 割の部屋に 1 つ、内側の四隅のどれか）
                        int[] c = corners(x0, z0)[rand.nextInt(4)];
                        p.gate(c[0], floorY + 1, c[1]);
                    }
                }
                case BOSS -> boss(p, rand, x0, z0);
            }
            if (kind == Kind.CHRONO_OUTPOST && role == Role.ROOM && !courtyard && rand.nextInt(3) == 0) { // 屋上の対空タレット
                p.spawn(ModEntities.PULSE_PHALANX, cx + 0.5, roof + 1, cz + 0.5, rand);
            }
        }

        /** 戸口（3 ブロック幅・4 ブロックの高さ。地下工場の入口は地上の高さに外への戸口） */
        boolean door(int x, int y, int z, int x0, int z0, int x1, int z1) {
            int base = role == Role.ENTRANCE && kind.underground && z == z1 ? surface : floorY;
            if (y < base + 1 || y > base + 4) return false;
            int mx = x0 + CELL / 2, mz = z0 + CELL / 2;
            if (z == z0 && (doors & N) != 0 && Math.abs(x - mx) <= 1) return true;
            if (z == z1 && (doors & S) != 0 && Math.abs(x - mx) <= 1) return true;
            if (x == x0 && (doors & W) != 0 && Math.abs(z - mz) <= 1) return true;
            return x == x1 && (doors & E) != 0 && Math.abs(z - mz) <= 1;
        }

        /** 地下工場の入口の竪穴: 外の戸口の踊り場から、壁沿いに 1 段ずつ回って地下の床まで降りる段 */
        void stairs(Painter p, int x0, int z0) {
            List<int[]> ring = new ArrayList<>(); // 壁の内側 1 列目を、南の真ん中から時計回りに
            int a = x0 + 1, b = z0 + 1, c = x0 + CELL - 2, d = z0 + CELL - 2, mx = x0 + CELL / 2;
            for (int x = mx; x <= c; x++) ring.add(new int[]{x, d});
            for (int z = d - 1; z >= b; z--) ring.add(new int[]{c, z});
            for (int x = c - 1; x >= a; x--) ring.add(new int[]{x, b});
            for (int z = b + 1; z <= d; z++) ring.add(new int[]{a, z});
            for (int x = a + 1; x < mx; x++) ring.add(new int[]{x, d});
            for (int i = -1; i <= 1; i++) p.set(mx + i, surface, d, p.floor(mx + i, d)); // 戸口の前の踊り場
            int drop = surface - floorY;
            for (int i = 1; i <= drop; i++) {
                int[] s = ring.get((i + 1) % ring.size());
                p.set(s[0], surface - i, s[1], p.wall(s[0], 0, s[1], 0));
                int[] t = ring.get((i + 2) % ring.size());          // 2 枚目（幅のある段）
                p.set(t[0], surface - i, t[1], p.wall(t[0], 0, t[1], 0));
            }
        }

        /** ふつうの部屋の家具・宝箱・敵 */
        void furnish(Painter p, RandomSource rand, int x0, int z0, boolean enemies, boolean courtyard) {
            int y = floorY + 1, cx = x0 + CELL / 2, cz = z0 + CELL / 2;
            switch (kind) {
                case SUPPLY_DEPOT -> {
                    List<int[]> crates = new ArrayList<>();
                    for (int i = 0; i < 6 + rand.nextInt(5); i++) { // 壁沿いの樽の山
                        int[] s = wallSpot(rand, x0, z0);
                        int h = 1 + rand.nextInt(3);
                        for (int k = 0; k < h; k++) {
                            boolean loot = k == 0 && rand.nextInt(4) == 0;
                            p.container(s[0], y + k, s[1], Blocks.BARREL.defaultBlockState(), loot ? kind.chestLoot() : null, rand);
                        }
                        crates.add(s);
                    }
                    p.set(cx, y + ROOM_H - 2, cz, Blocks.CHAIN.defaultBlockState());
                    if (enemies) {
                        for (int i = 0; i < rand.nextInt(3); i++) { // 樽の間に紛れたミミック
                            int[] s = wallSpot(rand, x0, z0);
                            int[] in = inward(s, x0, z0);
                            p.mimic(in[0] + 0.5, y, in[1] + 0.5, rand);
                        }
                        if (rand.nextInt(3) == 0) p.spawn(ModEntities.CHRONO_TROOPER, cx + 0.5, y, cz + 0.5, rand);
                        if (rand.nextInt(4) == 0) p.spawn(ModEntities.CHRONO_SCOUT_DRONE, cx + 0.5, y + 2, cz + 0.5, rand);
                        if (rand.nextInt(6) == 0) p.spawn(ModEntities.SIREN_EYE, cx + 0.5, y + 3, cz + 0.5, rand);
                    }
                }
                case CHRONO_OUTPOST -> {
                    if (courtyard) {
                        p.set(cx, floorY, cz, Painter.tech(ModTechBlocks.TECH_LIGHT_PANEL)); // 天井の無い中庭は床の照明
                        if (enemies) p.spawn(ModEntities.SIEGE_CANNON, cx + 0.5, y, cz + 0.5, rand);
                    } else {
                        for (int i = 0; i < 3; i++) { // 武器棚と端末
                            int[] s = wallSpot(rand, x0, z0);
                            p.set(s[0], y, s[1], rand.nextBoolean() ? Blocks.LECTERN.defaultBlockState() : Blocks.SMITHING_TABLE.defaultBlockState());
                        }
                    }
                    if (rand.nextInt(3) == 0) p.chest(wallSpot(rand, x0, z0), y, kind.chestLoot(), rand);
                    if (enemies) {
                        if (rand.nextInt(2) == 0) p.spawn(ModEntities.CHRONO_GUARDIAN, cx + 0.5, y, cz + 2.5, rand);
                        if (rand.nextInt(2) == 0) p.spawn(ModEntities.CHRONO_TROOPER, cx - 2.5, y, cz + 0.5, rand);
                        if (rand.nextInt(5) == 0) p.spawn(ModEntities.SIREN_EYE, cx + 0.5, y + 3, cz + 0.5, rand);
                    }
                }
                case DIMENSION_FOUNDRY -> {
                    boolean line = rand.nextBoolean();
                    if (line) { // 組立ライン: 観察器とピストンの列と金床
                        for (int x = x0 + 3; x <= x0 + CELL - 4; x++) {
                            p.set(x, y, cz - 2, Blocks.OBSERVER.defaultBlockState());
                            p.set(x, y, cz + 2, (x % 2 == 0 ? Blocks.PISTON : Blocks.STICKY_PISTON).defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.UP));
                        }
                        p.set(cx, y, cz, Blocks.ANVIL.defaultBlockState());
                    } else { // 溶鉱炉の部屋: 炉と床の熱い筋
                        for (int i = 0; i < 4; i++) {
                            int[] s = wallSpot(rand, x0, z0);
                            p.set(s[0], y, s[1], Blocks.BLAST_FURNACE.defaultBlockState());
                        }
                        for (int x = x0 + 2; x <= x0 + CELL - 3; x++) p.set(x, floorY, cz, Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                    if (rand.nextInt(3) == 0) p.chest(wallSpot(rand, x0, z0), y, kind.chestLoot(), rand);
                    if (enemies) {
                        if (rand.nextInt(2) == 0) p.spawn(ModEntities.CHRONO_GUARDIAN, cx + 2.5, y, cz + 3.5, rand);
                        if (rand.nextInt(3) == 0) p.spawn(ModEntities.CHRONO_HEAVY_WALKER, cx - 2.5, y, cz - 3.5, rand);
                        if (rand.nextInt(2) == 0) p.spawn(ModEntities.CHRONO_TROOPER, cx - 3.5, y, cz + 3.5, rand);
                        if (rand.nextInt(4) == 0) p.spawn(ModEntities.PULSE_PHALANX, cx + 3.5, y, cz - 3.5, rand);
                        if (rand.nextInt(5) == 0) p.spawn(ModEntities.SIREN_EYE, cx + 0.5, y + 3, cz + 0.5, rand);
                    }
                }
            }
        }

        /** ボスの部屋: 中ボスと、撃破で宝箱が出る所（入口から遠い側の壁の前） */
        void boss(Painter p, RandomSource rand, int x0, int z0) {
            int y = floorY + 1, cx = x0 + CELL / 2, cz = z0 + CELL / 2;
            // 宝箱の所: 戸口の無い壁の前（北・東・西・南の順で探す）
            int[] chest = (doors & N) == 0 ? new int[]{cx, z0 + 1} : (doors & E) == 0 ? new int[]{x0 + CELL - 2, cz}
                    : (doors & W) == 0 ? new int[]{x0 + 1, cz} : new int[]{cx, z0 + CELL - 2};
            BlockPos reward = new BlockPos(chest[0], y, chest[1]);
            switch (kind) {
                case SUPPLY_DEPOT -> {
                    for (int[] s : corners(x0, z0)) for (int k = 0; k < 2; k++) p.set(s[0], y + k, s[1], Blocks.BARREL.defaultBlockState());
                    p.boss(ModEntities.CRANE_LOADER, cx + 0.5, y, cz + 0.5, reward, rand);
                }
                case CHRONO_OUTPOST -> {
                    p.set(cx, floorY, cz, Painter.tech(ModTechBlocks.TECH_PHASE_PANEL));
                    p.boss(ModEntities.OUTPOST_WATCHER, cx + 0.5, y, cz + 0.5, reward, rand);
                }
                case DIMENSION_FOUNDRY -> {
                    for (int[] s : corners(x0, z0)) { // 位相アンカーのコンデンサー 4 本（部屋の四隅）
                        p.set(s[0], floorY, s[1], Blocks.CRYING_OBSIDIAN.defaultBlockState());
                        p.spawn(ModEntities.PHASE_CONDENSER, s[0] + 0.5, y, s[1] + 0.5, rand);
                    }
                    p.boss(ModEntities.FOUNDRY_MASTER, cx + 0.5, y, cz + 0.5, reward, rand);
                }
            }
        }

        /** 部屋の内側の四隅（壁から 2 ブロック内） */
        static int[][] corners(int x0, int z0) {
            int a = x0 + 2, b = z0 + 2, c = x0 + CELL - 3, d = z0 + CELL - 3;
            return new int[][]{{a, b}, {c, b}, {a, d}, {c, d}};
        }

        /** 壁沿いの床の 1 か所（戸口の前は避ける） */
        static int[] wallSpot(RandomSource rand, int x0, int z0) {
            int side = rand.nextInt(4), t = 1 + rand.nextInt(CELL - 2);
            if (Math.abs(t - CELL / 2) <= 2) t = t < CELL / 2 ? CELL / 2 - 3 : CELL / 2 + 3; // 戸口の前を空ける
            return switch (side) {
                case 0 -> new int[]{x0 + t, z0 + 1};
                case 1 -> new int[]{x0 + t, z0 + CELL - 2};
                case 2 -> new int[]{x0 + 1, z0 + t};
                default -> new int[]{x0 + CELL - 2, z0 + t};
            };
        }

        /** 壁沿いの所から 1 ブロック内側 */
        static int[] inward(int[] s, int x0, int z0) {
            int x = s[0], z = s[1];
            if (z == z0 + 1) z++;
            else if (z == z0 + CELL - 2) z--;
            else if (x == x0 + 1) x++;
            else x--;
            return new int[]{x, z};
        }
    }

    // ------------------------------------------------------------------ 描く道具

    /** 塊の範囲（chunk）の中にだけ置く。ダンジョンの種類ごとの材質 */
    static final class Painter {
        final WorldGenLevel level;
        final BoundingBox chunk;
        final Kind kind;

        Painter(WorldGenLevel level, BoundingBox chunk, Kind kind) {
            this.level = level;
            this.chunk = chunk;
            this.kind = kind;
        }

        boolean inside(int x, int y, int z) {
            return chunk.isInside(x, y, z);
        }

        void set(int x, int y, int z, BlockState state) {
            if (inside(x, y, z)) level.setBlock(new BlockPos(x, y, z), state, 2);
        }

        /** 床の下の空き（空気・水・草など）を土台で埋める（最大 depth ブロック下まで） */
        void foundation(int x, int y, int z, int depth) {
            if (!inside(x, y, z)) return;
            for (int k = 0; k < depth; k++) {
                BlockPos pos = new BlockPos(x, y - k, z);
                BlockState here = level.getBlockState(pos);
                if (!here.canBeReplaced() && here.getFluidState().isEmpty()) break;
                level.setBlock(pos, foundationBlock(), 2);
            }
        }

        BlockState foundationBlock() {
            return switch (kind) {
                case SUPPLY_DEPOT -> Blocks.COBBLESTONE.defaultBlockState();
                case CHRONO_OUTPOST -> Blocks.BLACKSTONE.defaultBlockState();
                case DIMENSION_FOUNDRY -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                case CHRONO_BASTION -> tech(ModTechBlocks.TECH_PANEL_DARK);
            };
        }

        static BlockState tech(Supplier<? extends net.minecraft.world.level.block.Block> block) {
            return block.get().defaultBlockState();
        }

        /** 床（2026-10-15 近未来的な建材: 4 ブロックごとの継ぎ目の格子） */
        BlockState floor(int x, int z) {
            boolean seam = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
            return switch (kind) {
                case SUPPLY_DEPOT -> tech(seam ? ModTechBlocks.TECH_PANEL_DARK : ModTechBlocks.TECH_PANEL_WHITE);
                case CHRONO_OUTPOST -> tech(seam ? ModTechBlocks.TECH_GRATING : ModTechBlocks.TECH_PANEL_DARK);
                case DIMENSION_FOUNDRY -> seam ? tech(ModTechBlocks.TECH_GRATING) : Blocks.SMOOTH_BASALT.defaultBlockState();
                case CHRONO_BASTION -> tech(seam ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_WHITE);
            };
        }

        /** 壁（h = 床からの高さ）: 足元に警告の帯、光る線の帯（基地は窓の強化ガラス）、工場は紫に光る帯 */
        BlockState wall(int x, int y, int z, int h) {
            if (h == 1) return tech(ModTechBlocks.TECH_HAZARD);
            return switch (kind) {
                case SUPPLY_DEPOT -> tech(h % 5 == 3 ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_WHITE);
                case CHRONO_OUTPOST -> tech(h == 3 && Math.floorMod(x + z, 4) == 0 ? ModTechBlocks.TECH_GLASS
                        : h % 5 == 3 ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_DARK);
                case DIMENSION_FOUNDRY -> tech(h % 5 == 4 ? ModTechBlocks.TECH_PHASE_PANEL : h % 5 == 2 ? ModTechBlocks.TECH_LIGHT_LINE : ModTechBlocks.TECH_PANEL_DARK);
                case CHRONO_BASTION -> tech(h == 5 ? ModTechBlocks.TECH_PHASE_PANEL : ModTechBlocks.TECH_PANEL_WHITE);
            };
        }

        /** 天井: 4 ブロックごとに照明パネル（2026-10-15 ユーザー指定「光源を設置」。松明の要らない明るさ） */
        BlockState ceiling(int x, int z) {
            if (Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2) return tech(ModTechBlocks.TECH_LIGHT_PANEL);
            return tech(kind == Kind.SUPPLY_DEPOT ? ModTechBlocks.TECH_PANEL_WHITE : ModTechBlocks.TECH_PANEL_DARK);
        }

        /** 地表の土・石・草など（建物・木でない物）を空気にする */
        void clearNatural(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            BlockPos pos = new BlockPos(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.hasBlockEntity() && !here.is(net.minecraft.tags.BlockTags.LOGS)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }

        /** クロノ転送ゲート（どのダンジョンのものかを覚えさせる） */
        void gate(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            BlockPos pos = new BlockPos(x, y, z);
            level.setBlock(pos, tech(ModTechBlocks.CHRONO_GATE), 2);
            if (level.getBlockEntity(pos) instanceof com.automatavalkyries.block.ChronoGateBlock.Gate g) g.setKind(kind.id);
        }

        /** 入れ物（樽・宝箱）を置き、loot があれば中身の表を付ける */
        void container(int x, int y, int z, BlockState state, ResourceLocation loot, RandomSource rand) {
            if (!inside(x, y, z)) return;
            BlockPos pos = new BlockPos(x, y, z);
            level.setBlock(pos, state, 2);
            if (loot != null) RandomizableContainerBlockEntity.setLootTable(level, rand, pos, loot);
        }

        void chest(int[] s, int y, ResourceLocation loot, RandomSource rand) {
            container(s[0], y, s[1], Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(rand.nextInt(4))), loot, rand);
        }

        /** 敵を置く（足元がこの塊の中なら。ほかの塊から描く時に二重に置かない） */
        <T extends Mob> T spawn(Supplier<? extends EntityType<T>> type, double x, double y, double z, RandomSource rand) {
            if (!populate || !inside((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) return null;
            T mob = type.get().create(level.getLevel());
            if (mob == null) return null;
            mob.moveTo(x, y, z, rand.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            kind.scaleHealth(mob);
            mob.setPersistenceRequired();
            level.addFreshEntityWithPassengers(mob);
            return mob;
        }

        void mimic(double x, double y, double z, RandomSource rand) {
            MimicContainerEntity m = spawn(ModEntities.MIMIC_CONTAINER, x, y, z, rand);
            if (m != null) m.settle(rand.nextInt(4) * 90.0F);
        }

        void boss(Supplier<? extends EntityType<? extends ChronoMidBoss>> type, double x, double y, double z, BlockPos reward, RandomSource rand) {
            if (!populate || !inside((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) return;
            ChronoMidBoss boss = type.get().create(level.getLevel());
            if (boss == null) return;
            boss.moveTo(x, y, z, rand.nextFloat() * 360.0F, 0.0F);
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.STRUCTURE, null, null);
            boss.setRewardChest(reward);
            level.addFreshEntityWithPassengers(boss);
        }
    }
}
