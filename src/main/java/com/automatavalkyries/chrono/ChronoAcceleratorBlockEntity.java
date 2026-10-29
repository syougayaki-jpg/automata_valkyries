package com.automatavalkyries.chrono;

import com.automatavalkyries.config.MachineConfig;
import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 時間加速器（2026-10-29 ユーザー指定「時間を扱う機械を直接追加して」。質問で: 時間加速器・時間巻き戻し。数値は【仮】）:
 * まわり（T1 半径 1〜T4 半径 4 の立方体）のブロックの時間を速める。電力を毎 Tick 使う。
 * <ul>
 *   <li>ブロックエンティティ（かまど・醸造台・このMOD の機械・他 MOD の機械など）を 1 Tick に {@link #extraTicks} 回余分に動かす</li>
 *   <li>ランダムに育つブロック（作物・苗・草・サンゴなど）に、ふつうの {@link #extraTicks} 倍のランダムな Tick を足す（ゲームルールの randomTickSpeed に合わせる）</li>
 *   <li>兵姫のオペレーターが付くと、余分に動かす回数が速さの倍率だけ増え、電力が節約の分だけ減る</li>
 * </ul>
 * 重くなりすぎないように（2026-10-29 ユーザー指定「取り扱いミスるとえぐい重いので、多重加速を防ぎ、加速させるブロックまで詳しく設定出来るように」）:
 * <ul>
 *   <li>多重加速を防ぐ: 同じブロックは、範囲が重なった何台の加速器からも 1 Tick に 1 台分しか速めない（{@link #claim}）。
 *       加速器どうしは速めず、加速器そのものも他（他 MOD の加速器など）から速められても 1 Tick に 1 回しか働かない</li>
 *   <li>設定（automata_valkyries-machines.toml の [accelerator]）: 全体の入り切り・ブロックエンティティ／ランダムな Tick の入り切り・
 *       余分に動かす回数の上限・1 台が速める数の上限・速めないブロック（ブラックリスト）・速めるブロックだけ（ホワイトリスト）。
 *       ブロック・タグ（#）・MOD（modid:*）で書ける（{@link Rules}）</li>
 * </ul>
 */
public class ChronoAcceleratorBlockEntity extends ChronoMachineBlockEntity {

    /** ティアごとの半径・余分に動かす回数（添字 = ティア）・毎 Tick の電力（FE） */
    public static final int[] RADIUS = {0, 1, 2, 3, 4};
    public static final int[] EXTRA = {0, 1, 3, 7, 15};
    public static final int[] ENERGY = {0, 500, 2_000, 8_000, 32_000};

    /** ディメンションごとの、この Tick に速めたブロック（位置 → ゲームの時刻）。範囲が重なった加速器で二重に速めない */
    private static final Map<Level, Map<Long, Long>> CLAIMED = new WeakHashMap<>();

    /** 20 Tick ごとに見直す、範囲の動かす相手 */
    private final List<BlockPos> tickers = new ArrayList<>(), randoms = new ArrayList<>();
    private float randomDebt;
    private int lastTicked;
    private long lastWorkTime = Long.MIN_VALUE;
    private boolean lastActive;

    public ChronoAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.ACCELERATOR_ENTITY.get(), ChronoMachineType.CHRONO_ACCELERATOR, pos, state);
    }

    /** 1 Tick に余分に動かす回数（オペレーター込み、設定の上限まで） */
    public int extraTicks(ChronoTier tier) {
        int max = MachineConfig.get(MachineConfig.ACCEL_MAX_EXTRA, 15);
        return Math.max(1, Math.min(max, Math.round(EXTRA[tier.level] * operator().speed())));
    }

    /** 設定の決まりで、このブロックを速めてよいか */
    public static boolean allows(BlockState state) {
        return Rules.current().allows(state);
    }

    /** この Tick にこの位置を速めてよいか（まだどの加速器も速めていなければ、自分の物にして true） */
    private static boolean claim(ServerLevel level, BlockPos p) {
        long now = level.getGameTime();
        Map<Long, Long> map = CLAIMED.computeIfAbsent(level, l -> new HashMap<>());
        if (map.size() > 65536) map.values().removeIf(t -> t < now); // 古い印を捨てる
        Long prev = map.put(p.asLong(), now);
        return prev == null || prev != now;
    }

    @Override
    protected boolean work(ChronoTier tier) {
        if (!(level instanceof ServerLevel server)) return false;
        long now = server.getGameTime();
        if (now == lastWorkTime) return lastActive; // 他から速められても 1 Tick に 1 回だけ
        lastWorkTime = now;
        lastActive = run(server, tier);
        return lastActive;
    }

    private boolean run(ServerLevel server, ChronoTier tier) {
        if (!MachineConfig.get(MachineConfig.ACCEL_ENABLED, true)) {
            lastTicked = 0;
            return false;
        }
        if (ticks % 20 == 0) scan(server, tier);
        if (tickers.isEmpty() && randoms.isEmpty()) {
            lastTicked = 0;
            return false;
        }
        if (!energy.consume(scaledEnergy(tier, ENERGY[tier.level] * 100 / tier.energyPercent))) return false;
        int extra = extraTicks(tier);
        int ticked = 0;
        for (BlockPos p : tickers) {
            BlockEntity be = server.getBlockEntity(p);
            if (be == null || be.isRemoved() || be instanceof ChronoAcceleratorBlockEntity) continue;
            BlockEntityTicker<BlockEntity> ticker = ticker(server, be);
            if (ticker == null || !claim(server, p)) continue;
            BlockState state = be.getBlockState();
            for (int i = 0; i < extra && !be.isRemoved(); i++) ticker.tick(server, p, state, be);
            ticked++;
        }
        // ランダムな Tick: 1 ブロックあたり randomTickSpeed / 4096 回／Tick（バニラ）の extra 倍を、範囲のランダムに育つブロックに配る
        if (!randoms.isEmpty()) {
            int speed = server.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
            randomDebt = Math.min(64.0F, randomDebt + randoms.size() * speed * extra / 4096.0F);
            while (randomDebt >= 1.0F) {
                randomDebt -= 1.0F;
                BlockPos p = randoms.get(server.random.nextInt(randoms.size()));
                BlockState s = server.getBlockState(p);
                if (s.isRandomlyTicking() && claim(server, p)) s.randomTick(server, p, server.random);
            }
            ticked += randoms.size();
        }
        lastTicked = ticked;
        if (ticks % 10 == 0) {
            int r = RADIUS[tier.level];
            BlockPos c = worldPosition;
            server.sendParticles(ParticleTypes.ENCHANT, c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5, 6, r * 0.6, 0.4, r * 0.6, 0.6);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityTicker<BlockEntity> ticker(ServerLevel level, BlockEntity be) {
        return (BlockEntityTicker<BlockEntity>) be.getBlockState().getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
    }

    private void scan(ServerLevel server, ChronoTier tier) {
        tickers.clear();
        randoms.clear();
        boolean doBe = MachineConfig.get(MachineConfig.ACCEL_BLOCK_ENTITIES, true), doRandom = MachineConfig.get(MachineConfig.ACCEL_RANDOM_TICKS, true);
        Rules rules = Rules.current();
        int r = RADIUS[tier.level];
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -r, -r), worldPosition.offset(r, r, r))) {
            if (p.equals(worldPosition) || !server.isLoaded(p)) continue;
            BlockState state = server.getBlockState(p);
            if (state.isAir() || !rules.allows(state)) continue;
            if (doBe) {
                BlockEntity be = server.getBlockEntity(p);
                if (be != null && !(be instanceof ChronoAcceleratorBlockEntity) && ticker(server, be) != null) tickers.add(p.immutable());
            }
            if (doRandom && state.isRandomlyTicking()) randoms.add(p.immutable());
        }
        int max = MachineConfig.get(MachineConfig.ACCEL_MAX_TARGETS, 64);
        if (tickers.size() > max) {
            tickers.sort(Comparator.comparingDouble(p -> p.distSqr(worldPosition)));
            tickers.subList(max, tickers.size()).clear();
        }
    }

    @Override
    protected int extraData(int index) {
        ChronoTier tier = tier();
        return switch (index) {
            case 0 -> extraTicks(tier) + 1;   // 速さ（何倍）
            case 1 -> RADIUS[tier.level] * 2 + 1;
            case 2 -> Math.min(0xFFFF, lastTicked);
            default -> 0;
        };
    }

    /**
     * 速めてよいブロックの決まり（設定のブラックリスト・ホワイトリスト）。書き方: "minecraft:furnace"（ブロック）、"#minecraft:crops"（ブロックのタグ）、
     * "create:*"（その MOD のすべて）。設定が変わったら作り直す
     */
    static final class Rules {
        private static Rules cached;
        private static List<?> cachedBlack, cachedWhite;
        private static boolean cachedMode;

        private final Matcher black, white;
        private final boolean whitelistMode;

        private Rules(List<? extends String> black, List<? extends String> white, boolean whitelistMode) {
            this.black = new Matcher(black);
            this.white = new Matcher(white);
            this.whitelistMode = whitelistMode;
        }

        static Rules current() {
            List<? extends String> black = MachineConfig.get(MachineConfig.ACCEL_BLACKLIST, List.of());
            List<? extends String> white = MachineConfig.get(MachineConfig.ACCEL_WHITELIST, List.of());
            boolean mode = MachineConfig.get(MachineConfig.ACCEL_WHITELIST_MODE, false);
            if (cached == null || !black.equals(cachedBlack) || !white.equals(cachedWhite) || mode != cachedMode) {
                cached = new Rules(black, white, mode);
                cachedBlack = List.copyOf(black);
                cachedWhite = List.copyOf(white);
                cachedMode = mode;
            }
            return cached;
        }

        boolean allows(BlockState state) {
            if (state.getBlock() == ModChrono.CHRONO_ACCELERATOR.get()) return false;
            if (black.matches(state)) return false;
            return !whitelistMode || white.matches(state);
        }
    }

    /** ブロック・タグ・MOD の名前の並びに当てはまるか */
    static final class Matcher {
        private final Set<Block> blocks = new HashSet<>();
        private final List<TagKey<Block>> tags = new ArrayList<>();
        private final Set<String> mods = new HashSet<>();

        Matcher(List<? extends String> entries) {
            for (String raw : entries) {
                String e = raw.trim();
                if (e.isEmpty()) continue;
                if (e.startsWith("#")) {
                    ResourceLocation id = ResourceLocation.tryParse(e.substring(1));
                    if (id != null) tags.add(TagKey.create(Registries.BLOCK, id));
                } else if (e.endsWith(":*")) {
                    mods.add(e.substring(0, e.length() - 2));
                } else {
                    ResourceLocation id = ResourceLocation.tryParse(e);
                    Block b = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
                    if (b != null && ForgeRegistries.BLOCKS.containsKey(id)) blocks.add(b);
                }
            }
        }

        boolean matches(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) if (state.is(tag)) return true;
            if (!mods.isEmpty()) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                return id != null && mods.contains(id.getNamespace());
            }
            return false;
        }
    }
}
