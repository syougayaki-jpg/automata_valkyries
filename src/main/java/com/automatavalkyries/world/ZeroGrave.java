package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.block.ZeroPortalBlock;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * 裏ボスの専用の次元「零の墓標」（2026-10-20 ユーザー指定「裏ボスをつくりますか」。時の狭間の奥）。
 * 終わりの空の下の闇に浮かぶ、ルミナル・ステラの残骸。紅・黒・紫に灰色の崩れた石の、欠けた円い闘技場（半径 {@value #R}、床 Y={@value #Y}）と、
 * 折れた柱、まわりに浮かぶ瓦礫、北の空に折れた艦の輪。南に着く所の甲板と帰りの門。
 * <ul>
 *   <li>入口: 時の狭間でタイムルーラーを倒すと、闘技場の北に紅の門（{@link ZeroPortalBlock}）が目覚める（{@link #openEntryGate}。枠が無ければ描く）。
 *       次のタイムルーラーと戦う間は眠る</li>
 *   <li>紅の門から入ると、闘技場（初めて入る時に描く）の南の甲板に着き、墓標の主（{@link LostCatastropheEntity}）がいなければ北に現れ、帰りの門は眠る</li>
 *   <li>主を倒すと（{@link #onBossDefeated}）帰りの門が目覚め、入った所（紅の門の手前の足場のある所）へ帰す。門を出入りした後 {@value #GATE_COOLDOWN} Tick は門に入らない</li>
 *   <li>時の狭間と同じく、着く所の床か真ん中が欠けていれば描き直し、着く所のまわり 5×5 は必ず埋め、闘技場の下へ落ちたら（Y {@value #Y} より 40 下）着いた所へ戻す。
 *       門以外（/tp・入り直し・生き返り）で来て足元が奈落なら着く所へ。寝床・リスポーンアンカーは使えない（dimension_type）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ZeroGrave {

    public static final ResourceKey<Level> GRAVE = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "zero_grave"));
    public static final int Y = 100, R = 36, GATE_Z = 46, LAND_Z = GATE_Z - 8;
    /** 主の現れる所（闘技場の北、床の 4 上） */
    public static final Vec3 BOSS_SPAWN = new Vec3(0.5, Y + 4, -17.5);
    /** 時の狭間の紅の門の z（時の狭間の闘技場の北、床の上に立つ。膜は 3×5） */
    public static final int ENTRY_Z = -26;
    static final String RETURN = AutomataValkyries.MODID + ".grave_return";
    static final String COOLDOWN = AutomataValkyries.MODID + ".grave_gate_cooldown";
    public static final int GATE_COOLDOWN = 100;

    private ZeroGrave() {
    }

    public static boolean isGrave(Level level) {
        return level.dimension() == GRAVE;
    }

    // ------------------------------------------------------------------ 出入り

    /** 紅の門をくぐって零の墓標へ（戻り先 = いまの所の手前） */
    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel grave = server == null ? null : server.getLevel(GRAVE);
        if (grave == null) return;
        CompoundTag back = new CompoundTag();
        back.putString("Dim", player.level().dimension().location().toString());
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4) look = new Vec3(0, 0, -1);
        Vec3 out = safeReturn((ServerLevel) player.level(), player.position(), look.normalize());   // 門の手前の、足場のある所へ帰す
        back.putDouble("X", out.x);
        back.putDouble("Y", out.y);
        back.putDouble("Z", out.z);
        player.getPersistentData().put(RETURN, back);
        ensureArena(grave);
        if (arenaBroken(grave)) rebuild(grave);                                     // 戦いで床が壊れていたら描き直す
        ensureLanding(grave);
        startCooldown(player);
        player.setPortalCooldown();
        player.teleportTo(grave, 0.5, Y + 1, LAND_Z + 0.5, 180.0F, 0.0F);
        player.fallDistance = 0.0F;
        grave.playSound(null, BlockPos.containing(0, Y + 1, LAND_Z), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 0.7F);
        State state = state(grave);
        if (state.boss == null) {
            LostCatastropheEntity boss = livingBoss(grave);                        // コマンドなどで先にいれば、その者を主にする
            if (boss == null) boss = LostCatastropheEntity.summonAt(grave, BOSS_SPAWN);
            if (boss != null) {
                state.boss = boss.getUUID();
                state.setDirty();
                setGate(grave, false); // 戦いの間、帰りの門は眠る
                player.displayClientMessage(Component.translatable("message.automata_valkyries.zero_grave.arrive").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            }
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
        startCooldown(player);
        player.setPortalCooldown();
        if (dest == null || dest.dimension() == GRAVE) {
            ServerLevel over = server.overworld();
            BlockPos spawn = over.getSharedSpawnPos();
            player.teleportTo(over, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        } else {
            player.teleportTo(dest, back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"), player.getYRot(), 0.0F);
        }
        player.fallDistance = 0.0F;
        player.getPersistentData().remove(RETURN);
    }

    /** 門を出入りしたばかりか（その間は門に入らない） */
    public static boolean onGateCooldown(ServerPlayer player) {
        return player.getServer() != null && player.getServer().getTickCount() < player.getPersistentData().getLong(COOLDOWN);
    }

    static void startCooldown(ServerPlayer player) {
        if (player.getServer() != null) player.getPersistentData().putLong(COOLDOWN, player.getServer().getTickCount() + GATE_COOLDOWN);
    }

    /** 墓標の主が倒れた: 帰りの門が目覚める（零の墓標の外で倒れた主なら何もしない） */
    public static void onBossDefeated(ServerLevel level) {
        if (!isGrave(level)) return;
        State state = state(level);
        state.boss = null;
        state.setDirty();
        setGate(level, true);
        level.playSound(null, BlockPos.containing(0, Y + 3, GATE_Z), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 0.7F);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.zero_grave.gate_open").withStyle(ChatFormatting.RED), false);
        }
    }

    // ------------------------------------------------------------------ 時の狭間の紅の門

    /** タイムルーラーが倒れた: 時の狭間の北の紅の門を目覚めさせる（枠が欠けていれば描く）。{@link TimeRift#onRulerDefeated} から呼ぶ */
    public static void openEntryGate(ServerLevel rift) {
        gate(rift, ENTRY_Z, true);
        rift.playSound(null, BlockPos.containing(0, TimeRift.Y + 3, ENTRY_Z), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 0.5F);
        for (ServerPlayer p : rift.players()) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.zero_grave.entry_open").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
        }
    }

    /** 時の狭間の紅の門の膜を目覚めさせる・眠らせる（門がまだ無ければ何もしない。次のタイムルーラーと戦う間は眠らせる） */
    public static void setEntryGate(ServerLevel rift, boolean awake) {
        setMembrane(rift, TimeRift.Y, ENTRY_Z, awake);
    }

    // ------------------------------------------------------------------ 着く所の守り

    /** 着く所の床・真ん中・帰りの門の甲板のどれかが欠けているか、盤の 24 か所（もともと床のある所だけ数える）のうち 4 か所以上が欠けているか */
    static boolean arenaBroken(ServerLevel grave) {
        if (grave.getBlockState(new BlockPos(0, Y, LAND_Z)).isAir() || grave.getBlockState(new BlockPos(0, Y, 0)).isAir()
                || grave.getBlockState(new BlockPos(0, Y, GATE_Z)).isAir()) return true;
        int missing = 0;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24, r = i % 2 == 0 ? 10 : 25;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (solid(x, z) && grave.getBlockState(new BlockPos(x, Y, z)).isAir()) missing++;
        }
        return missing >= 4;
    }

    /** 着く所のまわり 5×5 の床が欠けていれば埋める（描き直すほどでない小さな穴でも落ちないように） */
    public static void ensureLanding(ServerLevel grave) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos pos = new BlockPos(dx, Y, LAND_Z + dz);
            if (grave.getBlockState(pos).isAir()) grave.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
        }
    }

    /** 描き直す（門は眠って描かれるので、主と戦っていなければ目覚めさせる） */
    static void rebuild(ServerLevel grave) {
        build(grave);
        setGate(grave, state(grave).boss == null);
    }

    /** 足元 6 ブロック以内に足場があるか */
    static boolean grounded(ServerLevel level, Vec3 at) {
        BlockPos feet = BlockPos.containing(at);
        for (int k = 0; k <= 6; k++) {
            if (!level.getBlockState(feet.below(k)).getCollisionShape(level, feet.below(k)).isEmpty()) return true;
        }
        return false;
    }

    static boolean portal(BlockState s) {
        return s.is(ModTechBlocks.ZERO_PORTAL.get()) || s.is(ModTechBlocks.RULER_PORTAL.get());
    }

    /** 帰り先: 入った所から入ってきた側へ 2.5〜4.5 m のうち、足場があって体が入る所（門の中・宙ではない所）。見つからなければ 2.5 m 手前（{@link TimeRift} と同じ） */
    static Vec3 safeReturn(ServerLevel level, Vec3 at, Vec3 dir) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (double back = 2.5; back <= 4.5; back += 1.0) {
            for (double sideways : new double[]{0, 1.5, -1.5}) {
                Vec3 c = at.subtract(dir.scale(back)).add(side.scale(sideways));
                BlockPos feet = BlockPos.containing(c);
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos f = feet.above(dy);
                    boolean floor = !level.getBlockState(f.below()).getCollisionShape(level, f.below()).isEmpty();
                    boolean room = level.getBlockState(f).getCollisionShape(level, f).isEmpty() && level.getBlockState(f.above()).getCollisionShape(level, f.above()).isEmpty()
                            && !portal(level.getBlockState(f)) && !portal(level.getBlockState(f.above()));
                    if (floor && room) return new Vec3(c.x, f.getY(), c.z);
                }
            }
        }
        return at.subtract(dir.scale(2.5));
    }

    /** 門以外（/tp・入り直し・生き返り）で零の墓標に来て、足元に足場が無ければ着く所へ */
    static void landSafely(ServerPlayer player) {
        if (!isGrave(player.level()) || player.isSpectator()) return;
        ServerLevel grave = (ServerLevel) player.level();
        ensureArena(grave);
        if (arenaBroken(grave)) rebuild(grave);
        ensureLanding(grave);
        if (grounded(grave, player.position())) return;
        player.teleportTo(grave, 0.5, Y + 1, LAND_Z + 0.5, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTo() == GRAVE) landSafely(p);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) landSafely(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) landSafely(p);
    }

    /** 闘技場の下へ落ちたら着いた所へ戻す。主が倒れずに消えていたら（/kill など）帰りの門を目覚めさせる（閉じ込められないように） */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !isGrave(player.level()) || player.isSpectator()) return;
        ServerLevel grave = (ServerLevel) player.level();
        if (player.tickCount % 40 == 0) watchBoss(grave, player);
        if (player.getY() > Y - 40 || player.tickCount % 5 != 0) return;
        if (arenaBroken(grave)) rebuild(grave);
        ensureLanding(grave);
        player.teleportTo(grave, 0.5, Y + 1, LAND_Z + 0.5, player.getYRot(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.displayClientMessage(Component.translatable("message.automata_valkyries.zero_grave.rescued").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    /** 戦いの最中のはずの主が、主のいた区画も自分の区画も読み込まれているのに見つからない: 倒れずに消えた */
    static void watchBoss(ServerLevel grave, ServerPlayer player) {
        State state = state(grave);
        if (state.boss == null) return;
        Entity boss = grave.getEntity(state.boss);
        if (boss != null && !boss.isRemoved()) return;   // 倒れて消えていく途中も、まだいることにする（倒れた知らせは主の側から来る）
        ChunkPos spawn = new ChunkPos(BlockPos.containing(BOSS_SPAWN));
        if (!grave.areEntitiesLoaded(spawn.toLong()) || !grave.areEntitiesLoaded(player.chunkPosition().toLong())) return;
        LostCatastropheEntity other = livingBoss(grave);
        if (other != null) {                              // 別の主が闘技場にいれば、その者を主にする
            state.boss = other.getUUID();
            state.setDirty();
            return;
        }
        state.boss = null;
        state.setDirty();
        setGate(grave, true);
        for (ServerPlayer p : grave.players()) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.zero_grave.lost").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        }
    }

    @Nullable
    static LostCatastropheEntity livingBoss(ServerLevel grave) {
        return grave.getEntitiesOfClass(LostCatastropheEntity.class, arenaBox(), Entity::isAlive).stream().findFirst().orElse(null);
    }

    static AABB arenaBox() {
        return new AABB(-R - 30, Y - 40, -R - 30, R + 30, Y + 60, GATE_Z + 10);
    }

    /** 帰りの門の膜を目覚めさせる・眠らせる */
    static void setGate(ServerLevel grave, boolean awake) {
        setMembrane(grave, Y, GATE_Z, awake);
    }

    static void setMembrane(ServerLevel level, int floorY, int z, boolean awake) {
        for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 5; h++) {
            BlockPos pos = new BlockPos(dx, floorY + h, z);
            BlockState s = level.getBlockState(pos);
            if (s.is(ModTechBlocks.ZERO_PORTAL.get())) level.setBlock(pos, s.setValue(ZeroPortalBlock.AWAKE, awake), 3);
        }
    }

    /**
     * 紅の門（幅 5・高さ 6 の枠に 3×5 の膜。膜は北・南を向く）: 磨かれたブラックストーンの煉瓦の枠、足に模様入りのブラックストーン、上に泣く黒曜石と紅のガラス。
     * 床は {@code level} の {@code floorY}
     */
    static void gate(ServerLevel l, int z, boolean awake) {
        int floorY = l.dimension() == GRAVE ? Y : TimeRift.Y;
        for (int dx = -2; dx <= 2; dx++) {
            for (int h = 1; h <= 6; h++) {
                BlockState s;
                if (Math.abs(dx) == 2 || h == 6) {
                    s = h == 6 && dx % 2 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                            : h == 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                } else {
                    s = ModTechBlocks.ZERO_PORTAL.get().defaultBlockState().setValue(ZeroPortalBlock.AXIS, Direction.Axis.X).setValue(ZeroPortalBlock.AWAKE, awake);
                }
                set(l, dx, floorY + h, z, s);
            }
        }
        set(l, 0, floorY + 7, z, Blocks.RED_STAINED_GLASS.defaultBlockState());
        set(l, -1, floorY + 7, z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
        set(l, 1, floorY + 7, z, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
    }

    // ------------------------------------------------------------------ 闘技場

    /** 闘技場がまだ無ければ描く（次元ごとに 1 度） */
    public static void ensureArena(ServerLevel grave) {
        State state = state(grave);
        if (state.done) return;
        build(grave);
        state.done = true;
        state.setDirty();
    }

    static State state(ServerLevel grave) {
        return grave.getDataStorage().computeIfAbsent(State::load, State::new, AutomataValkyries.MODID + "_zero_grave");
    }

    static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, 2);
    }

    static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    /** 決まった並びの乱れ（描き直しても同じ形になる） */
    static int hash(int x, int z, int salt) {
        int h = x * 374761393 + z * 668265263 + salt * 982451653;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    static int roll(int x, int z, int salt, int n) {
        return Math.floorMod(hash(x, z, salt), n);
    }

    /** 南の甲板（着く所と帰りの門） */
    static boolean deck(int x, int z) {
        return Math.abs(x) <= 3 && z >= R - 3 && z <= GATE_Z + 1;
    }

    /** 欠けた縁: 向きによって内側へ食い込む（南の甲板の口のまわりは欠けない） */
    static double edge(double ang) {
        if (Math.abs(ang - Math.PI / 2) < 0.45) return R + 0.5;
        double bite = Math.pow(Math.max(0, Math.sin(ang * 7 + 0.4)), 3) * 7 + Math.pow(Math.max(0, Math.sin(ang * 3 - 1.1)), 4) * 5;
        return R + 0.5 - bite;
    }

    /** 床に抜けた穴（x, z, 半径） */
    static final int[][] HOLES = {{-22, -12, 3}, {19, -21, 2}, {26, 7, 2}, {-17, 19, 2}, {9, -29, 2}};

    /** もともと床のある所か */
    static boolean solid(int x, int z) {
        if (deck(x, z)) return true;
        if (Math.sqrt(x * x + z * z) > edge(Math.atan2(z, x))) return false;
        for (int[] h : HOLES) if ((x - h[0]) * (x - h[0]) + (z - h[1]) * (z - h[1]) <= h[2] * h[2]) return false;
        return true;
    }

    static boolean nearHole(int x, int z) {
        for (int[] h : HOLES) if ((x - h[0]) * (x - h[0]) + (z - h[1]) * (z - h[1]) <= (h[2] + 2) * (h[2] + 2)) return true;
        return false;
    }

    /**
     * ルミナル・ステラの残骸の闘技場: 灰と黒の崩れた石の円い盤（縁は欠け、ところどころ穴が抜ける）に、真ん中の泣く黒曜石と 8 本の紅のガラスの筋（下で泣く黒曜石が紫に光る）、
     * 紫に光る内の輪と外の紫のガラスの輪。下は崩れかけた逆さの円錐。縁に崩れた低い壁、まわりに折れた柱、宙に浮かぶ瓦礫、北の空に折れた艦の輪。南に着く所の甲板と帰りの門
     */
    static void build(ServerLevel l) {
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= GATE_Z + 2; z++) {
                if (!solid(x, z)) continue;
                boolean pad = deck(x, z);
                double r = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(z, x);
                double spokeAng = Math.round(ang / (Math.PI / 4)) * (Math.PI / 4);
                boolean spoke = !pad && r > 4 && r < 26 && Math.abs(ang - spokeAng) * r < 0.5;
                boolean rim = !solid(x + 1, z) || !solid(x - 1, z) || !solid(x, z + 1) || !solid(x, z - 1);
                BlockState floor;
                if (pad) {
                    floor = Math.abs(x) == 3 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : t(ModTechBlocks.TECH_PANEL_DARK);
                } else if (r < 1.5) {
                    floor = b(Blocks.CRYING_OBSIDIAN);
                } else if (Math.abs(r - 3) < 0.6) {
                    floor = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
                } else if (spoke) {
                    floor = b(Blocks.RED_STAINED_GLASS);                                  // 8 本の紅の筋
                } else if (Math.abs(r - 14) < 0.5) {
                    floor = t(ModTechBlocks.TECH_PHASE_PANEL);                            // 紫に光る内の輪
                } else if (Math.abs(r - 28) < 0.5 && !nearHole(x, z)) {
                    floor = b(Blocks.PURPLE_STAINED_GLASS);                               // 外の紫のガラスの輪
                } else if (rim || nearHole(x, z)) {
                    floor = roll(x, z, 1, 3) == 0 ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : roll(x, z, 2, 2) == 0 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : b(Blocks.CRACKED_DEEPSLATE_TILES);
                } else {
                    int k = roll(x, z, 3, 100);
                    floor = k < 10 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : k < 17 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : k < 22 ? b(Blocks.POLISHED_DEEPSLATE)
                            : k < 24 ? b(Blocks.CRYING_OBSIDIAN) : k < 27 ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.DEEPSLATE_TILES);
                }
                set(l, x, Y, z, floor);
                int depth = pad ? 2 : (int) Math.max(2, (edge(ang) - r) * 0.6) + 1 + roll(x, z, 4, 3);   // 下: 崩れかけた逆さの円錐
                for (int k = 1; k <= depth; k++) {
                    BlockState rock;
                    if (pad) rock = t(ModTechBlocks.TECH_PANEL_DARK);
                    else if (k == 1 && (spoke || Math.abs(r - 28) < 0.5)) rock = b(Blocks.CRYING_OBSIDIAN);   // ガラスの下で紫に光る
                    else if (roll(x, z, 5 + k, 17) == 0) rock = b(Blocks.CRYING_OBSIDIAN);
                    else rock = roll(x, z, 20 + k, 3) == 0 ? b(Blocks.BLACKSTONE) : roll(x, z, 30 + k, 2) == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES);
                    set(l, x, Y - k, z, rock);
                }
                if (pad && Math.abs(x) == 3 && z > R - 1 && z != GATE_Z) set(l, x, Y + 1, z, t(ModTechBlocks.TECH_GLASS));   // 甲板の手すり
                if (!pad && rim && !nearHole(x, z) && !(z > 0 && Math.abs(x) <= 6)) {                                       // 縁の崩れた低い壁
                    int w = roll(x, z, 6, 6);
                    if (w <= 1) set(l, x, Y + 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                    else if (w == 2) {
                        set(l, x, Y + 1, z, b(Blocks.CRACKED_DEEPSLATE_BRICKS));
                        if (roll(x, z, 7, 3) == 0) set(l, x, Y + 2, z, b(Blocks.DEEPSLATE_BRICK_WALL));
                    }
                }
            }
        }
        pillars(l);
        debris(l);
        halo(l);
        gate(l, GATE_Z, false);    // 帰りの門（甲板の先、北＝闘技場を向く。はじめは眠っている）
        AutomataValkyries.LOGGER.info("[zero-grave] built the ruins of Luminal Stella");
    }

    static BlockState t(java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> block) {
        return block.get().defaultBlockState();
    }

    /** 盤の外寄り（半径 31）に 10 本の折れた柱（2×2）。3 本に 1 本は折れずに泣く黒曜石の冠が残り、折れたものは横に倒れたかけらが転がる */
    static void pillars(ServerLevel l) {
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + 0.31;
            int px = (int) Math.round(Math.cos(a) * 31), pz = (int) Math.round(Math.sin(a) * 31);
            if (pz > 20 && Math.abs(px) <= 8) continue;                       // 南の甲板の口は空ける
            boolean whole = roll(px, pz, 8, 3) == 0;
            int height = whole ? 11 : 4 + roll(px, pz, 9, 6);
            for (int h = -2; h <= height; h++) {
                for (int ox = 0; ox <= 1; ox++) for (int oz = 0; oz <= 1; oz++) {
                    if (!whole && h >= height - 1 && roll(px + ox, pz + oz, 10 + h, 3) == 0) continue;   // 折れた先はぎざぎざ
                    BlockState s = h == 2 ? t(ModTechBlocks.TECH_PHASE_PANEL)
                            : h % 4 == 0 ? b(Blocks.CHISELED_POLISHED_BLACKSTONE)
                            : roll(px + ox, pz + oz, 11 + h, 4) == 0 ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : b(Blocks.POLISHED_BLACKSTONE_BRICKS);
                    set(l, px + ox, Y + h, pz + oz, s);
                }
            }
            if (whole) {
                for (int ox = 0; ox <= 1; ox++) for (int oz = 0; oz <= 1; oz++) set(l, px + ox, Y + height + 1, pz + oz, b(Blocks.CRYING_OBSIDIAN));
            } else {
                double fall = a + (roll(px, pz, 12, 2) == 0 ? 1.3 : -1.3);    // 縁に沿って横に倒れたかけら
                for (int s = 2; s <= 4; s++) {
                    int fx = px + (int) Math.round(Math.cos(fall) * s), fz = pz + (int) Math.round(Math.sin(fall) * s);
                    if (solid(fx, fz) && !deck(fx, fz)) set(l, fx, Y + 1, fz, s == 4 ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                }
            }
        }
    }

    /** 闘技場のまわり（半径 44〜70）に浮かぶ 16 個の瓦礫の塊（上は割れたタイル、下は黒い岩、ところどころ紅と紫のガラスのかけら） */
    static void debris(ServerLevel l) {
        Random rnd = new Random(20261020L);
        int placed = 0;
        for (int tries = 0; placed < 16 && tries < 64; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, dist = 44 + rnd.nextDouble() * 26;
            int cx = (int) Math.round(Math.cos(a) * dist), cz = (int) Math.round(Math.sin(a) * dist);
            int cy = Y - 12 + rnd.nextInt(34);
            double size = 1.5 + rnd.nextDouble() * 2.5;
            if (Math.abs(cx) < 10 && cz > 30) continue;                       // 甲板と帰りの門の前は空ける
            placed++;
            int s = (int) Math.ceil(size);
            for (int dx = -s; dx <= s; dx++) for (int dz = -s; dz <= s; dz++) for (int dy = -s; dy <= 1; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + (dy * 1.6) * (dy * 1.6));
                if (d > size || (d > size - 1 && roll(cx + dx, cz + dz, 40 + dy, 3) == 0)) continue;
                BlockState block;
                if (dy == 1 || d < size && dy == 0 && roll(cx + dx, cz + dz, 50, 4) == 0) {
                    block = roll(cx + dx, cz + dz, 51, 2) == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
                } else {
                    int k = roll(cx + dx, cz + dz + dy * 31, 52, 14);
                    block = k == 0 ? b(Blocks.RED_STAINED_GLASS) : k == 1 ? b(Blocks.PURPLE_STAINED_GLASS) : k == 2 ? b(Blocks.CRYING_OBSIDIAN)
                            : k < 7 ? b(Blocks.BLACKSTONE) : b(Blocks.COBBLED_DEEPSLATE);
                }
                set(l, cx + dx, cy + dy, cz + dz, block);
            }
        }
    }

    /** 北の空に、折れたルミナル・ステラの艦の輪（半径 40 の上半分の弧、ところどころ途切れる。内側に紫の光と紅のガラス） */
    static void halo(ServerLevel l) {
        int cy = Y + 26, z0 = -60;
        for (int x = -42; x <= 42; x++) {
            for (int y = cy; y <= cy + 42; y++) {
                double d = Math.sqrt(x * x + (y - cy) * (y - cy));
                if (d < 38.5 || d > 41.5) continue;
                double a = Math.atan2(y - cy, x);
                if (a < 0.25 || a > Math.PI - 0.25 || Math.sin(a * 9 + 0.7) > 0.62) continue;   // 途切れた所
                boolean inner = d < 39.5;
                for (int z = z0 - 1; z <= z0; z++) {
                    BlockState s = inner ? (roll(x, y, 60, 5) == 0 ? b(Blocks.RED_STAINED_GLASS) : t(ModTechBlocks.TECH_PHASE_PANEL))
                            : roll(x, y + z, 61, 4) == 0 ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : b(Blocks.POLISHED_BLACKSTONE_BRICKS);
                    set(l, x, y, z, s);
                }
            }
        }
    }

    /** 闘技場を描いたかの印と、いま戦っている主 */
    static final class State extends SavedData {
        boolean done;
        @Nullable
        UUID boss;

        static State load(CompoundTag tag) {
            State s = new State();
            s.done = tag.getBoolean("Done");
            if (tag.hasUUID("Boss")) s.boss = tag.getUUID("Boss");
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("Done", done);
            if (boss != null) tag.putUUID("Boss", boss);
            return tag;
        }
    }
}
