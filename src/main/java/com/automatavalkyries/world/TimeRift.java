package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.block.RulerPortalBlock;
import com.automatavalkyries.entity.chrono.TimeRulerEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModTechBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * 専用の次元「時の狭間」（2026-10-19 ユーザー指定「クロノルーラーを制作」。質問で: 専用の次元）。星空の闇に浮かぶ文字盤の闘技場（半径 {@value #R}、床 Y={@value #Y}）。
 * 目覚めた眠れる時の門から入ると、闘技場（初めて入る時に描く）の南に着き、タイムルーラーがいなければ北に現れる。
 * 南の甲板の帰りの門は、タイムルーラーを倒すと目覚め、入った所（門の前）へ帰す。闘技場の外は奈落
 */
public final class TimeRift {

    public static final ResourceKey<Level> RIFT = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "time_rift"));
    public static final int Y = 100, R = 34, GATE_Z = 40;
    static final String RETURN = AutomataValkyries.MODID + ".rift_return";

    private TimeRift() {
    }

    public static boolean isRift(Level level) {
        return level.dimension() == RIFT;
    }

    /** 門をくぐって時の狭間へ（戻り先 = いまの所） */
    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel rift = server == null ? null : server.getLevel(RIFT);
        if (rift == null) return;
        CompoundTag back = new CompoundTag();
        back.putString("Dim", player.level().dimension().location().toString());
        back.putDouble("X", player.getX());
        back.putDouble("Y", player.getY());
        back.putDouble("Z", player.getZ());
        player.getPersistentData().put(RETURN, back);
        ensureArena(rift);
        player.setPortalCooldown();
        player.teleportTo(rift, 0.5, Y + 1, GATE_Z - 10 + 0.5, 180.0F, 0.0F);
        rift.playSound(null, BlockPos.containing(0, Y + 1, GATE_Z - 10), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 1.4F);
        if (rift.getEntitiesOfClass(TimeRulerEntity.class, arenaBox(), e -> e.isAlive() && !e.isDefeated()).isEmpty()) {
            TimeRulerEntity ruler = ModEntities.TIME_RULER.get().create(rift);
            if (ruler != null) {
                ruler.moveTo(0.5, Y + 3, -10.5, 0.0F, 0.0F);
                ruler.finalizeSpawn(rift, rift.getCurrentDifficultyAt(new BlockPos(0, Y, -10)), MobSpawnType.EVENT, null, null);
                rift.addFreshEntity(ruler);
            }
            setGate(rift, false); // 戦いの間、帰りの門は眠る
        }
    }

    /** 帰りの門をくぐって、来た所へ */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CompoundTag back = player.getPersistentData().getCompound(RETURN);
        ServerLevel dest = null;
        if (back.contains("Dim")) {
            ResourceLocation id = ResourceLocation.tryParse(back.getString("Dim"));
            if (id != null) dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
        player.setPortalCooldown();
        if (dest == null) {
            ServerLevel over = server.overworld();
            BlockPos spawn = over.getSharedSpawnPos();
            player.teleportTo(over, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        } else {
            player.teleportTo(dest, back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"), player.getYRot(), 0.0F);
        }
        player.getPersistentData().remove(RETURN);
    }

    /** タイムルーラーが倒れた: 帰りの門が目覚める */
    public static void onRulerDefeated(ServerLevel rift) {
        setGate(rift, true);
        for (ServerPlayer p : rift.players()) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.time_rift.gate_open").withStyle(ChatFormatting.GOLD), false);
        }
    }

    static AABB arenaBox() {
        return new AABB(-R - 8, Y - 20, -R - 8, R + 8, Y + 40, R + 8);
    }

    /** 帰りの門の膜を目覚めさせる・眠らせる */
    static void setGate(ServerLevel rift, boolean awake) {
        for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 5; h++) {
            BlockPos pos = new BlockPos(dx, Y + h, GATE_Z);
            BlockState s = rift.getBlockState(pos);
            if (s.is(ModTechBlocks.RULER_PORTAL.get())) rift.setBlock(pos, s.setValue(RulerPortalBlock.AWAKE, awake), 3);
        }
    }

    // ------------------------------------------------------------------ 闘技場

    /** 闘技場がまだ無ければ描く（次元ごとに 1 度） */
    public static void ensureArena(ServerLevel rift) {
        Built built = rift.getDataStorage().computeIfAbsent(Built::load, Built::new, AutomataValkyries.MODID + "_time_rift");
        if (built.done) return;
        build(rift);
        built.done = true;
        built.setDirty();
    }

    static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    static BlockState t(java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> b) {
        return b.get().defaultBlockState();
    }

    /** 文字盤の闘技場: 白い盤に紫の 12 本の線、金の縁と 12 の要石の目盛り、分の目盛りの照明、真ん中の要石。下は逆さの円錐の黒い岩。縁に強化ガラスの手すり、まわりに浮かぶ 11 本の時の柱、南に帰りの門の甲板 */
    static void build(ServerLevel l) {
        for (int x = -R - 6; x <= R + 6; x++) {
            for (int z = -R - 6; z <= GATE_Z + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                boolean pad = Math.abs(x) <= 3 && z > 0 && z <= GATE_Z + 1 && r > R - 1;
                if (r > R + 0.5 && !pad) continue;
                double ang = Math.atan2(z, x);
                double hourAng = Math.round(ang / (Math.PI / 6)) * (Math.PI / 6);
                double offHour = Math.abs(ang - hourAng) * r;
                BlockState floor;
                if (pad) {
                    floor = Math.abs(x) == 3 ? t(ModTechBlocks.TECH_HAZARD) : t(ModTechBlocks.RULER_GATE_FRAME);
                } else if (r < 1.5) {
                    floor = t(ModTechBlocks.RULER_GATE_KEYSTONE);
                } else if (r >= R - 2) {
                    floor = t(ModTechBlocks.RULER_GATE_FRAME);
                } else if (Math.abs(r - 30) < 1.6 && offHour < 1.6) {
                    floor = t(ModTechBlocks.RULER_GATE_KEYSTONE);                        // 12 の時の目盛り
                } else if (Math.abs(r - 26.5) < 0.6 && Math.abs(ang * 30 / Math.PI - Math.round(ang * 30 / Math.PI)) * Math.PI / 30 * r < 0.5) {
                    floor = t(ModTechBlocks.TECH_LIGHT_PANEL);                            // 分の目盛り
                } else if (r > 3 && r < 25 && offHour < 0.5) {
                    floor = t(ModTechBlocks.TECH_PHASE_PANEL);                            // 12 本の紫の線
                } else if (Math.abs(r - 12) < 0.5) {
                    floor = t(ModTechBlocks.RULER_GATE_FRAME);                            // 内側の金の輪
                } else {
                    floor = Blocks.CALCITE.defaultBlockState();
                }
                set(l, x, Y, z, floor);
                int depth = pad ? 2 : (int) Math.max(2, (R + 0.5 - r) * 0.55) + 1;      // 下: 逆さの円錐
                for (int k = 1; k <= depth; k++) {
                    BlockState rock = k == depth && !pad && (Math.abs(r - 20) < 0.8 || Math.abs(r - 8) < 0.8) ? t(ModTechBlocks.TECH_PHASE_PANEL)
                            : (x * 7 + z * 13 + k * 3) % 17 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                    set(l, x, Y - k, z, pad ? t(ModTechBlocks.TECH_PANEL_DARK) : rock);
                }
                if (!pad && r >= R - 0.5 && !(z > 0 && Math.abs(x) <= 3)) set(l, x, Y + 1, z, t(ModTechBlocks.TECH_GLASS)); // 手すり（南の甲板の口は空ける）
                if (pad && Math.abs(x) == 3) set(l, x, Y + 1, z, t(ModTechBlocks.TECH_GLASS));
            }
        }
        // まわりに浮かぶ時の柱（南の 6 時は甲板なので除く）
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int x = (int) Math.round(Math.cos(a) * (R + 5)), z = (int) Math.round(Math.sin(a) * (R + 5));
            if (z > R) continue;
            for (int h = -3; h <= 4; h++) set(l, x, Y + h, z, h == 4 ? t(ModTechBlocks.RULER_GATE_KEYSTONE) : h % 3 == 0 ? t(ModTechBlocks.TECH_PHASE_PANEL) : t(ModTechBlocks.RULER_GATE_FRAME));
            set(l, x, Y + 5, z, Blocks.END_ROD.defaultBlockState());
        }
        // 帰りの門（甲板の先、北＝闘技場を向く。膜は 3×5、はじめは眠っている）
        for (int dx = -2; dx <= 2; dx++) {
            for (int h = 1; h <= 6; h++) {
                BlockState s = Math.abs(dx) == 2 || h == 6 ? (h == 6 && dx % 2 == 0 ? t(ModTechBlocks.RULER_GATE_KEYSTONE) : t(ModTechBlocks.RULER_GATE_FRAME))
                        : ModTechBlocks.RULER_PORTAL.get().defaultBlockState().setValue(RulerPortalBlock.AXIS, Direction.Axis.X);
                set(l, dx, Y + h, GATE_Z, s);
            }
        }
        set(l, 0, Y + 7, GATE_Z, Blocks.END_ROD.defaultBlockState());
        AutomataValkyries.LOGGER.info("[time-rift] built the clock-face arena");
    }

    /** 闘技場を描いたかの印 */
    static final class Built extends SavedData {
        boolean done;

        static Built load(CompoundTag tag) {
            Built b = new Built();
            b.done = tag.getBoolean("Done");
            return b;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Done", done);
            return tag;
        }
    }
}
