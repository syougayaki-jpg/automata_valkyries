package com.automatavalkyries.trial;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.registry.ModBossDrops;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModTrials;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 回想のトライアルの進行（2026-10-04 ユーザー指定。裏ボス撃破後のおまけ）。
 * <ul>
 *   <li>ルミナルステラの追憶メモリ（裏ボスの素材）を右クリック: 選んでいるトライアルに挑む（メモリは減らない）。スニーク＋右クリック: トライアルを選び直す。
 *       挑戦中にスニーク＋右クリック: あきらめる</li>
 *   <li>挑むと回想の空間（{@link ModTrials#RECOLLECTION}）の闘技場へ移り、3 秒後にボスが現れる。条件（{@link Trial}）を守って制限時間内に倒すとクリア</li>
 *   <li>失敗（時間切れ・被弾の超過・倒れる・ログアウト・あきらめる）するとボスは退き、元の場所へ戻る。クリアしても数秒後に元の場所へ戻る</li>
 *   <li>全部クリアするとオーバーロードメモリが 1 つ手に入る。全部クリアした後は、どれかをクリアするたびにもう 1 つ【提案】</li>
 * </ul>
 * クリアの記録・選択・戻り先はプレイヤーの死んでも消えない保存データ（{@link Player#PERSISTED_NBT_TAG}）に持つ。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TrialManager {

    public static final int COUNTDOWN = 60, AFTER_CLEAR = 100, AFTER_FAIL = 60, ARENA_RADIUS = 20, ARENA_Y = 100;
    private static final String ROOT = "automata_valkyries_trials";
    private static final UUID FRAIL_ID = UUID.fromString("6b1f3c2e-9a7d-4e51-b8c0-2d4f6a8e1c37");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    enum State { COUNTDOWN, FIGHT, CLEARED, FAILED }

    static final class Session {
        final ServerPlayer player;
        final Trial trial;
        final Vec3 center;
        int tick, fightTick, hits, endTick;
        State state = State.COUNTDOWN;
        @Nullable UUID boss;
        /** 出したボスそのもの（出した直後の新しい区画では、世界の一覧から数 Tick 見つからないので、UUID で探さずに持つ） */
        @Nullable LivingEntity bossEntity;

        Session(ServerPlayer player, Trial trial, Vec3 center) {
            this.player = player;
            this.trial = trial;
            this.center = center;
        }
    }

    private TrialManager() {}

    // ---- 保存データ ----

    static CompoundTag data(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persisted.getCompound(ROOT);
        persisted.put(ROOT, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return root;
    }

    public static boolean cleared(Player player, Trial trial) {
        return data(player).getCompound("Cleared").getBoolean(trial.id());
    }

    public static int clearedCount(Player player) {
        int n = 0;
        for (Trial t : Trial.ALL) if (cleared(player, t)) n++;
        return n;
    }

    public static int selected(Player player) {
        return Math.floorMod(data(player).getInt("Selected"), Trial.ALL.size());
    }

    public static boolean inTrial(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** クリアを記録して報酬を渡す（全部クリアしたらオーバーロードメモリ 1 つ）。オーバーロードメモリを渡したか */
    public static boolean recordClear(ServerPlayer player, Trial trial) {
        CompoundTag root = data(player);
        CompoundTag cleared = root.getCompound("Cleared");
        cleared.putBoolean(trial.id(), true);
        root.put("Cleared", cleared);
        if (clearedCount(player) < Trial.ALL.size()) return false;
        boolean first = !root.getBoolean("OverloadGranted");
        root.putBoolean("OverloadGranted", true);
        ItemStack memory = new ItemStack(ModTrials.OVERLOAD_MEMORY.get());
        if (!player.getInventory().add(memory)) player.drop(memory, false);
        player.sendSystemMessage(Component.translatable(first ? "trial.automata_valkyries.all_cleared" : "trial.automata_valkyries.reward")
                .withStyle(s -> s.withColor(0xFFC040)));
        return true;
    }

    // ---- 追憶メモリで選ぶ・挑む ----

    @SubscribeEvent
    public static void onUseMemory(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(ModBossDrops.MEMORY_STELLA.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (inTrial(player)) {
            if (player.isShiftKeyDown()) fail(player, SESSIONS.get(player.getUUID()), "trial.automata_valkyries.failed.abandon");
            return;
        }
        if (player.isShiftKeyDown()) {
            int next = Math.floorMod(selected(player) + 1, Trial.ALL.size());
            data(player).putInt("Selected", next);
            showSelection(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F + next * 0.1F);
            return;
        }
        start(player, Trial.get(selected(player)));
    }

    /** あきらめる（挑戦中でなければ何もしない） */
    public static void abandon(ServerPlayer player) {
        fail(player, SESSIONS.get(player.getUUID()), "trial.automata_valkyries.failed.abandon");
    }

    /** 挑戦中のボス（いなければ null。テスト用） */
    public static @Nullable LivingEntity boss(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? null : s.bossEntity;
    }

    static void showSelection(ServerPlayer player) {
        int i = selected(player);
        Trial t = Trial.get(i);
        Component mark = cleared(player, t) ? Component.literal(" ✔").withStyle(ChatFormatting.GREEN) : Component.empty();
        player.displayClientMessage(Component.translatable("trial.automata_valkyries.select", i + 1, Trial.ALL.size(), t.title(), t.bossName(), t.conditions())
                .withStyle(s -> s.withColor(0xE8D8B0)).append(mark), true);
    }

    /** トライアルを始める（回想の空間の闘技場へ移る） */
    public static boolean start(ServerPlayer player, Trial trial) {
        MinecraftServer server = player.getServer();
        ServerLevel arena = server == null ? null : server.getLevel(ModTrials.RECOLLECTION);
        if (arena == null || inTrial(player) || player.level().dimension() == ModTrials.RECOLLECTION) return false;
        // 戻り先
        CompoundTag back = new CompoundTag();
        back.putString("Dim", player.level().dimension().location().toString());
        back.putDouble("X", player.getX());
        back.putDouble("Y", player.getY());
        back.putDouble("Z", player.getZ());
        back.putFloat("Yaw", player.getYRot());
        back.putFloat("Pitch", player.getXRot());
        data(player).put("Return", back);
        Vec3 center = arenaCenter(player.getUUID());
        buildArena(arena, BlockPos.containing(center));
        player.stopRiding();
        player.teleportTo(arena, center.x, center.y, center.z - 12, 0F, 0F);
        Session session = new Session(player, trial, center);
        SESSIONS.put(player.getUUID(), session);
        if (trial.frail()) setFrail(player, true);
        title(player, trial.title(), trial.conditions(), 10, 60, 20);
        player.sendSystemMessage(Component.translatable("trial.automata_valkyries.start", trial.title(), trial.bossName(), trial.conditions())
                .withStyle(s -> s.withColor(0xE8D8B0)));
        arena.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        return true;
    }

    /** 闘技場の中心（プレイヤーごとに離れた場所。回想の空間は空っぽ） */
    static Vec3 arenaCenter(UUID id) {
        int slot = Math.floorMod(id.hashCode(), 4096);
        return new Vec3((slot % 64) * 1024 + 512.5, ARENA_Y, (slot / 64) * 1024 + 512.5);
    }

    /** 回想の闘技場: 白い石英の円盤に、刻まれた輪と光、まわりに見えない壁。前の挑戦の残りは消す */
    public static void buildArena(ServerLevel level, BlockPos c) {
        int r = ARENA_RADIUS;
        for (Entity e : level.getEntities((Entity) null, new AABB(c).inflate(r + 8, 32, r + 8), e -> !(e instanceof Player))) {
            // 保護されたボスは保護を外してから（前の挑戦の残り。セリフは出さない）
            if (e instanceof com.automatavalkyries.protect.IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
            e.discard();
        }
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState(), ring = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(),
                light = Blocks.SEA_LANTERN.defaultBlockState(), edge = Blocks.QUARTZ_PILLAR.defaultBlockState(), wall = Blocks.BARRIER.defaultBlockState();
        for (int x = -r - 1; x <= r + 1; x++) for (int z = -r - 1; z <= r + 1; z++) {
            double d = Math.sqrt(x * x + z * z);
            BlockPos p = c.offset(x, -1, z);
            if (d <= r) {
                int ri = (int) Math.round(d);
                BlockState s = d < 1.5 ? light : ri % 6 == 0 ? ring : ri >= r - 1 ? edge : floor;
                if (ri == 12 && (Math.abs(x) < 1 || Math.abs(z) < 1 || Math.abs(Math.abs(x) - Math.abs(z)) < 1)) s = light;
                level.setBlock(p, s, 2);
                for (int y = 0; y <= 24; y++) if (!level.getBlockState(p.above(1 + y)).isAir()) level.setBlock(p.above(1 + y), Blocks.AIR.defaultBlockState(), 2);
            } else if (d <= r + 1.5) {
                for (int y = -1; y <= 24; y++) level.setBlock(p.above(1 + y), wall, 2);
            }
        }
    }

    /**
     * ボスを出す。できているボスは本物（光速の残影 → ルクシオン。2026-10-09）、まだのボスは検証用ボスのエレノアが代わり。
     * どれも自分では退かず保存もしない「回想のトライアルのボス」にして、攻撃力に倍率を掛ける
     */
    static @Nullable LivingEntity spawnBoss(ServerLevel level, ServerPlayer player, Session session) {
        net.minecraft.world.entity.Mob boss = switch (session.trial.id()) {
            case "luxion" -> {
                com.automatavalkyries.entity.chrono.LuxionEntity luxion = ModEntities.LUXION.get().create(level);
                if (luxion != null) luxion.setTrialBoss(session.trial.bossAttack()); // 技のダメージは属性ではなく技ごとの値なので倍率で
                yield luxion;
            }
            default -> {
                ElenorEntity elenor = ModEntities.ELENOR.get().create(level);
                if (elenor != null) {
                    AttributeInstance attack = elenor.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (attack != null) attack.setBaseValue(attack.getBaseValue() * session.trial.bossAttack());
                    elenor.setTrialBoss(true);
                }
                yield elenor;
            }
        };
        if (boss == null) return null;
        Vec3 at = session.center.add(0, 0, 10);
        boss.moveTo(at.x, at.y, at.z, 180F, 0F);
        boss.setCustomName(Component.translatable("trial.automata_valkyries.boss_name", session.trial.bossName()));
        boss.setPersistenceRequired();
        boss.setTarget(player);
        level.addFreshEntity(boss);
        return boss;
    }

    // ---- 毎 Tick ----

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            Session s = entry.getValue();
            ServerPlayer player = s.player;
            if (player.hasDisconnected()) { // ログアウトは onLogout で片付ける。念のため
                it.remove();
                continue;
            }
            if (tick(player, s)) {
                it.remove();
                returnHome(player);
            }
        }
        // 挑戦していないのに回想の空間にいるプレイヤー（サーバーの再起動など）は元の場所へ戻す
        if (server.getTickCount() % 20 == 0) {
            ServerLevel arena = server.getLevel(ModTrials.RECOLLECTION);
            if (arena != null) for (ServerPlayer p : java.util.List.copyOf(arena.players())) if (!inTrial(p)) returnHome(p);
        }
    }

    /** 1 Tick 進める。終わって戻すときは true */
    static boolean tick(ServerPlayer player, Session s) {
        s.tick++;
        ServerLevel level = player.serverLevel();
        switch (s.state) {
            case COUNTDOWN -> {
                if (level.dimension() != ModTrials.RECOLLECTION) return true;
                int left = COUNTDOWN - s.tick;
                if (left > 0 && left % 20 == 0) {
                    title(player, Component.literal(String.valueOf(left / 20)).withStyle(ChatFormatting.GOLD), s.trial.bossName(), 0, 20, 0);
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1F, 1.2F);
                }
                if (left <= 0) {
                    LivingEntity boss = spawnBoss(level, player, s);
                    if (boss == null) return true;
                    s.boss = boss.getUUID();
                    s.bossEntity = boss;
                    s.state = State.FIGHT;
                    s.fightTick = 0;
                    title(player, Component.translatable("trial.automata_valkyries.fight").withStyle(ChatFormatting.RED), s.trial.conditions(), 0, 30, 10);
                    level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.2F);
                }
            }
            case FIGHT -> {
                s.fightTick++;
                if (level.dimension() != ModTrials.RECOLLECTION) {
                    fail(player, s, "trial.automata_valkyries.failed.left");
                    return false;
                }
                LivingEntity boss = s.bossEntity;
                if (boss == null) {
                    fail(player, s, "trial.automata_valkyries.failed.lost");
                    return false;
                }
                if (boss.isDeadOrDying() || boss.getRemovalReason() == Entity.RemovalReason.KILLED) {
                    clear(player, s);
                    return false;
                }
                if (boss.isRemoved()) { // 倒れずに消えた（退いた・消された）
                    fail(player, s, "trial.automata_valkyries.failed.lost");
                    return false;
                }
                if (s.fightTick > s.trial.timeLimit()) {
                    fail(player, s, "trial.automata_valkyries.failed.time");
                    return false;
                }
                if (s.fightTick % 20 == 0) {
                    Component status = Component.translatable("trial.automata_valkyries.status", Trial.clock(s.trial.timeLimit() - s.fightTick),
                            s.trial.hitLimit() > 0 ? s.hits + "/" + s.trial.hitLimit() : "-");
                    player.displayClientMessage(status.copy().withStyle(s.trial.timeLimit() - s.fightTick < 600 ? ChatFormatting.RED : ChatFormatting.GRAY), true);
                }
            }
            case CLEARED, FAILED -> {
                return s.tick >= s.endTick;
            }
        }
        return false;
    }

    static void clear(ServerPlayer player, Session s) {
        AutomataValkyries.LOGGER.info("Trial {} cleared by {} in {} ticks", s.trial.id(), player.getGameProfile().getName(), s.fightTick);
        s.state = State.CLEARED;
        s.endTick = s.tick + AFTER_CLEAR;
        title(player, Component.translatable("trial.automata_valkyries.cleared").withStyle(s2 -> s2.withColor(0xFFE8A0)),
                Component.translatable("trial.automata_valkyries.cleared.sub", s.trial.title(), Trial.clock(s.fightTick)), 10, 70, 20);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
        recordClear(player, s.trial);
    }

    static void fail(ServerPlayer player, @Nullable Session s, String reason) {
        if (s == null || s.state == State.CLEARED || s.state == State.FAILED) return;
        AutomataValkyries.LOGGER.info("Trial {} failed for {}: {} (fight tick {}, hits {})", s.trial.id(), player.getGameProfile().getName(), reason, s.fightTick, s.hits);
        s.state = State.FAILED;
        s.endTick = s.tick + AFTER_FAIL;
        retreatBoss(player.getServer(), s);
        title(player, Component.translatable("trial.automata_valkyries.failed").withStyle(ChatFormatting.DARK_RED), Component.translatable(reason), 10, 50, 20);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.7F);
    }

    static void retreatBoss(@Nullable MinecraftServer server, Session s) {
        LivingEntity boss = s.bossEntity;
        if (boss == null || boss.isRemoved()) return;
        if (boss instanceof ElenorEntity elenor) elenor.retreat();
        else if (!(boss instanceof com.automatavalkyries.entity.RetreatableBoss retreating) || !retreating.beginRetreat()) boss.discard(); // ルクシオンは剣を納めて光に溶ける
    }

    /** 元の場所へ戻す（戻り先が無ければ、元の世界の初期スポーン） */
    static void returnHome(ServerPlayer player) {
        setFrail(player, false);
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CompoundTag back = data(player).getCompound("Return");
        ServerLevel level = null;
        if (back.contains("Dim")) {
            ResourceLocation id = ResourceLocation.tryParse(back.getString("Dim"));
            if (id != null) level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        }
        if (level == null || level.dimension() == ModTrials.RECOLLECTION) {
            level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(level, back.getDouble("X"), back.getDouble("Y"), back.getDouble("Z"), back.getFloat("Yaw"), back.getFloat("Pitch"));
        }
        data(player).remove("Return");
    }

    static void setFrail(ServerPlayer player, boolean on) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) return;
        max.removeModifier(FRAIL_ID);
        if (on) {
            max.addTransientModifier(new AttributeModifier(FRAIL_ID, "trial_frail", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        }
    }

    static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ---- 条件 ----

    private static @Nullable Session fighting(Entity entity) {
        Session s = entity instanceof Player ? SESSIONS.get(entity.getUUID()) : null;
        return s != null && s.state == State.FIGHT ? s : null;
    }

    /** 近接のみ: ボスへの攻撃は、挑戦者が手で殴ったときだけ通す */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            Session s = e.getValue();
            if (s.state != State.FIGHT || !s.trial.meleeOnly() || !target.getUUID().equals(s.boss)) continue;
            Entity direct = event.getSource().getDirectEntity();
            if (direct == null || !direct.getUUID().equals(e.getKey())) {
                event.setCanceled(true);
                if (event.getSource().getEntity() instanceof ServerPlayer p) {
                    p.displayClientMessage(Component.translatable("trial.automata_valkyries.melee_only_block").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    /** 被弾の回数を数える */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        Session s = fighting(event.getEntity());
        if (s == null || event.getAmount() <= 0 || s.trial.hitLimit() <= 0) return;
        s.hits++;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (s.hits > s.trial.hitLimit()) fail(player, s, "trial.automata_valkyries.failed.hits");
        else player.displayClientMessage(Component.translatable("trial.automata_valkyries.hit", s.hits, s.trial.hitLimit()).withStyle(ChatFormatting.RED), true);
    }

    /** 回復なし */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        Session s = fighting(event.getEntity());
        if (s != null && s.trial.noHeal()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            Session s = SESSIONS.remove(player.getUUID());
            fail(player, s, "trial.automata_valkyries.failed.death");
            setFrail(player, false);
            data(player).remove("Return"); // 倒れたら、いつもの復活地点へ
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) retreatBoss(player.getServer(), s); // 次に入ったとき、回想の空間にいれば元の場所へ戻す（毎秒の見回り）
    }
}
