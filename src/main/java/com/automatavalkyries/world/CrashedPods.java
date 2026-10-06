package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModBlocks;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 墜落ポッド（追加拡張仕様書 §8.2。2026-10-07 ユーザー指定: 初期スポーンの近く（200 m 以内）に 1 つ確定、ほかは平均 800 m 四方に 1 つ、
 * オーバーワールドの陸地全部、損傷したインターフェースは目覚めの端末）。
 * 地面に半分埋まった黒焦げの外殻（5×4×5）と、まわりの焦げ跡。中に深い休眠状態の兵姫（HP 5、電力 0。{@link ValkyrieEntity#isPodSleeper}）、
 * 端末（{@link com.automatavalkyries.block.DamagedInterfaceBlock}）、チェスト（loot_tables/chests/crashed_pod）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class CrashedPods {

    private CrashedPods() {}

    public static final ResourceLocation LOOT = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "chests/crashed_pod");
    /** 初期スポーンから確定の 1 つを置く距離 */
    public static final int SPAWN_MIN = 60, SPAWN_MAX = 200;

    /** 自然生成（placed_feature/crashed_pod。稀に） */
    public static class PodFeature extends Feature<NoneFeatureConfiguration> {
        public PodFeature() {
            super(NoneFeatureConfiguration.CODEC);
        }

        @Override
        public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
            WorldGenLevel level = ctx.level();
            BlockPos origin = ctx.origin();
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, origin);
            return build(level, surface, ctx.random());
        }
    }

    /** 置ける所か（海・川でなく、地面が水でなく固い） */
    static boolean suitable(WorldGenLevel level, BlockPos surface) {
        var biome = level.getBiome(surface);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) return false;
        BlockState below = level.getBlockState(surface.below());
        return below.getFluidState().isEmpty() && below.isSolid() && level.getFluidState(surface).isEmpty();
    }

    /** ポッドを組み立てる（surface = 地面の上の空気）。置けたら true */
    public static boolean build(WorldGenLevel level, BlockPos surface, RandomSource random) {
        if (!suitable(level, surface)) return false;
        place(level, surface, random);
        return true;
    }

    /** 場所を確かめずに組み立てる（テスト用。テストの世界は海のバイオーム） */
    public static void place(WorldGenLevel level, BlockPos surface, RandomSource random) {
        Direction front = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos base = surface.below(); // 1 段埋める
        // 焦げ跡（まわり半径 5 の地面の一部）
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 25 || random.nextFloat() < 0.35F) continue;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, base.offset(dx, 0, dz)).below();
                BlockState st = level.getBlockState(top);
                if (st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(top, random.nextFloat() < 0.25F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
                // 草や花は焼けて消える
                BlockPos above = top.above();
                if (!level.getBlockState(above).isAir() && level.getBlockState(above).canBeReplaced()) level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockState hull = ModBlocks.CRASHED_POD_HULL.get().defaultBlockState();
        // 外殻 5×4×5（中は 3×3×3 の空き）
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos p = base.offset(x, y, z);
                    boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0 || y == 3;
                    boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2 && (y == 0 || y == 3);
                    if (corner) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2); // 角を落として丸く
                        continue;
                    }
                    if (shell && y == 3 && random.nextFloat() < 0.15F) { // 天井の破れ
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        continue;
                    }
                    level.setBlock(p, shell ? hull : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // 入口（前面の真ん中を 1×2）
        BlockPos door = base.relative(front, 2);
        level.setBlock(door.above(1), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(door.above(2), Blocks.AIR.defaultBlockState(), 2);
        // 端末（奥の壁、内側を向く）
        Direction back = front.getOpposite();
        BlockPos terminal = base.relative(back, 2).above(2);
        level.setBlock(terminal, ModBlocks.DAMAGED_INTERFACE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, front), 2);
        // チェスト（奥の隅）
        BlockPos chestPos = base.relative(back, 1).relative(back.getClockWise(), 1).above(1);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, front), 2);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) chest.setLootTable(LOOT, random.nextLong());
        // 眠っている兵姫（真ん中）
        ServerLevel server = level.getLevel();
        ValkyrieEntity v = ModEntities.VALKYRIE.get().create(server);
        if (v != null) {
            BlockPos at = base.above(1);
            v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, back.toYRot(), 0.0F);
            v.makePodSleeper();
            level.addFreshEntity(v);
        }
        record(level, base.above(1));
    }

    // ------------------------------------------------------------------ 置いたポッドの記録（兵姫探知レーダー）

    /** 生成の途中（ワーカースレッド）で置いたポッドの場所とディメンション。サーバーの Tick で記録へ移す */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** まだ目覚めていない兵姫がいるポッドの場所（ディメンションごと） */
    public static class Registry extends SavedData {
        public final java.util.Set<Long> pods = new java.util.HashSet<>();

        static Registry load(CompoundTag tag) {
            Registry r = new Registry();
            for (long l : tag.getLongArray("Pods")) r.pods.add(l);
            return r;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("Pods", pods.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }
    }

    public static Registry registry(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Registry::load, Registry::new, AutomataValkyries.MODID + "_pods");
    }

    /** ポッドを置いた（どのスレッドからでも） */
    static void record(WorldGenLevel level, BlockPos pos) {
        PENDING.add(new Object[]{pos.asLong(), level.getLevel().dimension()});
    }

    /** 兵姫が目覚めた: 近くのポッドを記録から外す */
    public static void forget(ServerLevel level, BlockPos near) {
        Registry r = registry(level);
        if (r.pods.removeIf(l -> BlockPos.of(l).distSqr(near) <= 10 * 10)) r.setDirty();
    }

    private static void flushPending(net.minecraft.server.MinecraftServer server) {
        Object[] e;
        while ((e = PENDING.poll()) != null) {
            @SuppressWarnings("unchecked") ServerLevel l = server.getLevel((net.minecraft.resources.ResourceKey<Level>) e[1]);
            if (l == null) continue;
            Registry r = registry(l);
            if (r.pods.add((Long) e[0])) r.setDirty();
        }
    }

    // ------------------------------------------------------------------ 初期スポーンの近くに 1 つ

    /** 置いたかどうか（ワールドごと） */
    public static class Placed extends SavedData {
        boolean done;

        static Placed load(CompoundTag tag) {
            Placed p = new Placed();
            p.done = tag.getBoolean("Done");
            return p;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Done", done);
            return tag;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) flushPending(event.getServer());
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null || event.getServer().getTickCount() % 20 != 5) return;
        Placed placed = level.getDataStorage().computeIfAbsent(Placed::load, Placed::new, AutomataValkyries.MODID + "_crashed_pod");
        if (placed.done) return;
        placed.done = true; // 失敗しても何度も試さない
        placed.setDirty();
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x5EEDB0DL);
        BlockPos spawn = level.getSharedSpawnPos();
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = random.nextDouble() * Math.PI * 2;
            int d = SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN);
            int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
            level.getChunk(x >> 4, z >> 4); // 作らせる
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            if (build(level, surface, random)) {
                AutomataValkyries.LOGGER.info("[automata_valkyries] crashed pod placed near spawn at {}", surface);
                return;
            }
        }
        AutomataValkyries.LOGGER.warn("[automata_valkyries] could not find land for the starting crashed pod near {}", spawn);
    }
}
