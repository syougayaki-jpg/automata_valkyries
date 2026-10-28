package com.automatavalkyries.trial;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.entity.chrono.ChronoMidBoss;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.TrialListPacket;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 回想のトライアルの進行（2026-10-04 ユーザー指定。裏ボス撃破後のおまけ）。
 * <ul>
 *   <li>ルミナルステラの追憶メモリ（裏ボスの素材）を右クリック: 回想を選ぶ画面を開く（{@code client/TrialSelectScreen}。2026-10-27 ユーザー指定「選択画面を作る」）。
 *       挑戦中にスニーク＋右クリック: あきらめる</li>
 *   <li>挑むと回想の空間（{@link ModTrials#RECOLLECTION}）の闘技場（ボスごとの場。{@link TrialArenas}）へ移り、3 秒後にボスが現れる。
 *       条件（{@link Trial}）を守って制限時間内に倒すとクリア。同時（{@link Trial.Mode#TOGETHER}）は全員、連戦（{@link Trial.Mode#RUSH}）は順に全員</li>
 *   <li>連戦の合間（{@value #INTERMISSION} Tick）は時間を数えず、最大 HP の {@value #RUSH_HEAL_PERCENT}% を回復し、次のボスの場へ作り替える</li>
 *   <li>失敗（時間切れ・被弾の超過・倒れる・ログアウト・あきらめる）するとボスは退き、元の場所へ戻る。クリアしても数秒後に元の場所へ戻る</li>
 *   <li>報酬（2026-10-27 ユーザー指定「初回報酬＋ランク」）: 初めてのクリアで、倒したボスの落とし物をもう 1 回分手渡す。ランク（{@link Trial#rank}）が
 *       初めて S ならオーバーロードメモリ。全部クリアするとオーバーロードメモリが 1 つ、その後はどれかをクリアするたびにもう 1 つ（これまでどおり）。
 *       中ボスは宝箱を置かない代わりに、宝箱の中身を毎回手渡す</li>
 * </ul>
 * クリアの記録・ランク・最速・戻り先はプレイヤーの死んでも消えない保存データ（{@link Player#PERSISTED_NBT_TAG}）に持つ。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TrialManager {

    public static final int COUNTDOWN = 60, AFTER_CLEAR = 100, AFTER_FAIL = 60, ARENA_RADIUS = 20, ARENA_Y = 100, INTERMISSION = 100, RUSH_HEAL_PERCENT = 30;
    /** 壁の外に出たボスを戻す先（壁から内側へ何ブロック）と、ボスのいてよい高さ（床から。壁の高さ） */
    static final int KEEP_MARGIN = 3, KEEP_TOP = TrialArenas.CLEAR_ABOVE - 2;
    private static final String ROOT = "automata_valkyries_trials";
    private static final UUID FRAIL_ID = UUID.fromString("6b1f3c2e-9a7d-4e51-b8c0-2d4f6a8e1c37");
    /** 同時のボスの最大 HP を減らす修飾子（このMOD の名前で始めるので Eprotect は戻さない） */
    private static final UUID BOSS_HEALTH_ID = UUID.fromString("2f8d61a4-5c3b-47e9-a0d2-91b7e64c3f58");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    enum State { COUNTDOWN, FIGHT, INTERMISSION, CLEARED, FAILED }

    static final class Session {
        final ServerPlayer player;
        final Trial trial;
        final Vec3 center;
        int tick, fightTick, hits, endTick, stage, gap;
        State state = State.COUNTDOWN;
        /** 今の波のボス（出した直後の新しい区画では、世界の一覧から数 Tick 見つからないので、UUID で探さずに持つ） */
        final List<LivingEntity> bosses = new ArrayList<>();
        /** 倒したボス（初回の報酬・中ボスの宝箱の中身を引く） */
        final List<LivingEntity> defeated = new ArrayList<>();

        Session(ServerPlayer player, Trial trial, Vec3 center) {
            this.player = player;
            this.trial = trial;
            this.center = center;
        }

        boolean isBoss(Entity e) {
            for (LivingEntity b : bosses) if (b == e || b.getUUID().equals(e.getUUID())) return true;
            return false;
        }

        /** 今の波で出すボスの ID */
        List<String> wave() {
            return trial.mode() == Trial.Mode.RUSH ? List.of(trial.bosses().get(stage)) : trial.bosses();
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

    /** いちばん良かったランク */
    public static Trial.Rank bestRank(Player player, Trial trial) {
        int r = data(player).getCompound("BestRank").getInt(trial.id());
        return Trial.Rank.values()[Math.max(0, Math.min(r, Trial.Rank.values().length - 1))];
    }

    /** いちばん速かったクリアの Tick（0 = まだ） */
    public static int bestTime(Player player, Trial trial) {
        return data(player).getCompound("BestTime").getInt(trial.id());
    }

    public static int selected(Player player) {
        return Math.floorMod(data(player).getInt("Selected"), Trial.ALL.size());
    }

    public static boolean inTrial(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** クリアを記録して報酬を渡す（テスト用: ランク B・倒したボスなし）。オーバーロードメモリを渡したか */
    public static boolean recordClear(ServerPlayer player, Trial trial) {
        return recordClear(player, trial, Trial.Rank.B, 0, List.of());
    }

    /**
     * クリアを記録して報酬を渡す: 初回はボスの落とし物をもう 1 回分、初めての S ランクでオーバーロードメモリ、全部クリアでオーバーロードメモリ 1 つ
     * （全部クリアした後は、クリアのたびにもう 1 つ）。中ボスの宝箱の中身は毎回。オーバーロードメモリを渡したか
     */
    public static boolean recordClear(ServerPlayer player, Trial trial, Trial.Rank rank, int ticks, List<LivingEntity> defeated) {
        CompoundTag root = data(player);
        CompoundTag cleared = root.getCompound("Cleared");
        boolean first = !cleared.getBoolean(trial.id());
        cleared.putBoolean(trial.id(), true);
        root.put("Cleared", cleared);
        CompoundTag bestRank = root.getCompound("BestRank");
        if (rank.ordinal() > bestRank.getInt(trial.id())) bestRank.putInt(trial.id(), rank.ordinal());
        root.put("BestRank", bestRank);
        CompoundTag bestTime = root.getCompound("BestTime");
        if (ticks > 0 && (bestTime.getInt(trial.id()) <= 0 || ticks < bestTime.getInt(trial.id()))) bestTime.putInt(trial.id(), ticks);
        root.put("BestTime", bestTime);

        for (LivingEntity boss : defeated) {
            if (boss instanceof ChronoMidBoss mid) give(player, rollChest(player, mid.rewardLoot())); // 宝箱の代わり（毎回）
            if (first) give(player, rollDrops(player, boss)); // 初回の報酬
        }
        if (first && !defeated.isEmpty()) {
            player.sendSystemMessage(Component.translatable("trial.automata_valkyries.first_reward", trial.title()).withStyle(s -> s.withColor(0xE8D8B0)));
        }

        boolean granted = false;
        CompoundTag sRank = root.getCompound("SRank");
        if (rank == Trial.Rank.S && !sRank.getBoolean(trial.id())) {
            sRank.putBoolean(trial.id(), true);
            root.put("SRank", sRank);
            give(player, List.of(new ItemStack(ModTrials.OVERLOAD_MEMORY.get())));
            player.sendSystemMessage(Component.translatable("trial.automata_valkyries.reward_s", trial.title()).withStyle(s -> s.withColor(0xFFD860)));
            granted = true;
        }
        if (clearedCount(player) < Trial.ALL.size()) return granted;
        // 全クリアの報酬は「今の一覧を全部越えた」ごとに 1 回（2026-10-27 ユーザー指定「全クリした時にももらえるように」）。回想が増えたら、増えた一覧を全部越えた時にまた出す。
        // 全クリアの時にどれだけの数の回想があったかを覚える（前の保存データの OverloadGranted は、はじめの 5 つの全クリア）
        int allClearedSize = root.contains("AllClearedSize") ? root.getInt("AllClearedSize") : root.getBoolean("OverloadGranted") ? 5 : 0;
        boolean firstAll = allClearedSize < Trial.ALL.size();
        root.putInt("AllClearedSize", Trial.ALL.size());
        root.putBoolean("OverloadGranted", true);
        give(player, List.of(new ItemStack(ModTrials.OVERLOAD_MEMORY.get())));
        player.sendSystemMessage(Component.translatable(firstAll ? "trial.automata_valkyries.all_cleared" : "trial.automata_valkyries.reward")
                .withStyle(s -> s.withColor(0xFFC040)));
        return true;
    }

    private static void give(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    /** ボスの落とし物の表をもう 1 回引く（挑戦者が倒した扱い） */
    static List<ItemStack> rollDrops(ServerPlayer player, LivingEntity boss) {
        ServerLevel level = player.serverLevel();
        LootTable table = level.getServer().getLootData().getLootTable(boss.getLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, boss)
                .withParameter(LootContextParams.ORIGIN, boss.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, player.damageSources().playerAttack(player))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        return table.getRandomItems(params);
    }

    /** 宝箱の表を引く */
    static List<ItemStack> rollChest(ServerPlayer player, ResourceLocation id) {
        ServerLevel level = player.serverLevel();
        LootTable table = level.getServer().getLootData().getLootTable(id);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params);
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
        sendList(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** 回想を選ぶ画面を開く（クリア・ランク・最速を送る） */
    public static void sendList(ServerPlayer player) {
        int n = Trial.ALL.size();
        boolean[] cleared = new boolean[n];
        int[] ranks = new int[n], times = new int[n];
        for (int i = 0; i < n; i++) {
            Trial t = Trial.ALL.get(i);
            cleared[i] = cleared(player, t);
            ranks[i] = bestRank(player, t).ordinal();
            times[i] = bestTime(player, t);
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TrialListPacket(selected(player), cleared, ranks, times));
    }

    /** 選んだ回想に挑む（画面から。追憶メモリを持っているときだけ） */
    public static void startFromScreen(ServerPlayer player, int index) {
        if (index < 0 || index >= Trial.ALL.size() || inTrial(player)) return;
        if (!player.getMainHandItem().is(ModBossDrops.MEMORY_STELLA.get()) && !player.getOffhandItem().is(ModBossDrops.MEMORY_STELLA.get())) return;
        start(player, Trial.ALL.get(index));
    }

    /** あきらめる（挑戦中でなければ何もしない） */
    public static void abandon(ServerPlayer player) {
        fail(player, SESSIONS.get(player.getUUID()), "trial.automata_valkyries.failed.abandon");
    }

    /** 挑戦中のボス（今の波の、まだ倒れていない最初の 1 体。いなければ null。テスト用） */
    public static @Nullable LivingEntity boss(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return null;
        for (LivingEntity b : s.bosses) if (!down(b)) return b;
        return s.bosses.isEmpty() ? null : s.bosses.get(0);
    }

    /** 挑戦中の今の波のボス全員（テスト用） */
    public static List<LivingEntity> bosses(Player player) {
        Session s = SESSIONS.get(player.getUUID());
        return s == null ? List.of() : List.copyOf(s.bosses);
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
        data(player).putInt("Selected", Math.max(0, Trial.indexOf(trial.id())));
        Vec3 center = arenaCenter(player.getUUID());
        buildArena(arena, BlockPos.containing(center), TrialArenas.themeFor(trial));
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

    /** これまでの白い石英の闘技場 */
    public static void buildArena(ServerLevel level, BlockPos c) {
        buildArena(level, c, TrialArenas.Theme.QUARTZ);
    }

    /** 回想の闘技場を建てる（前の挑戦の残りのエンティティは消す） */
    public static void buildArena(ServerLevel level, BlockPos c, TrialArenas.Theme theme) {
        int r = ARENA_RADIUS;
        for (Entity e : level.getEntities((Entity) null, new AABB(c).inflate(r + 8, 32, r + 8), e -> !(e instanceof Player))) {
            // 保護されたボスは保護を外してから（前の挑戦の残り。セリフは出さない）
            if (e instanceof com.automatavalkyries.protect.IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
            e.discard();
        }
        TrialArenas.build(level, c, theme);
    }

    /**
     * ボスを 1 体出す（本物。どれも自分では退かず保存もしない「回想のトライアルのボス」にして、攻撃力に倍率を掛ける）。
     * 同時に出すときは最大 HP を {@link Trial#bossHealth} 倍に。工場長機は位相アンカーのコンデンサーを 3 本連れてくる
     */
    static @Nullable LivingEntity spawnBoss(ServerLevel level, ServerPlayer player, Trial trial, String id, Vec3 at, float yaw) {
        float attack = trial.bossAttack();
        Mob boss = switch (id) {
            case "luxion" -> {
                var luxion = ModEntities.LUXION.get().create(level);
                if (luxion != null) luxion.setTrialBoss(attack); // 技のダメージは属性ではなく技ごとの値なので倍率で
                yield luxion;
            }
            case "luxion_ex" -> {
                var ex = ModEntities.LUXION_EX.get().create(level);
                if (ex != null) ex.setTrialBoss(attack);
                yield ex;
            }
            case "galliafort" -> {
                var fort = ModEntities.GALLIAFORT.get().create(level);
                if (fort != null) fort.setTrialBoss(attack);
                yield fort;
            }
            case "galliafort_ex" -> {
                var fort = ModEntities.GALLIAFORT_EX.get().create(level);
                if (fort != null) fort.setTrialBoss(attack);
                yield fort;
            }
            case "memento_mori_ex" -> {
                var mori = ModEntities.MEMENTO_MORI_EX.get().create(level);
                if (mori != null) mori.setTrialBoss(attack);
                yield mori;
            }
            case "time_ruler" -> { // 凍てつく刻 → タイムルーラー（2026-10-19）
                var ruler = ModEntities.TIME_RULER.get().create(level);
                if (ruler != null) ruler.setTrialBoss(attack);
                yield ruler;
            }
            case "lost_catastrophe" -> { // 零の追憶 → 零号機（2026-10-20）
                var zero = ModEntities.LOST_CATASTROPHE.get().create(level);
                if (zero != null) zero.setTrialBoss(attack);
                yield zero;
            }
            case "memento_mori" -> { // 死蝶の舞踏 → メメントモリ（2026-10-10）
                var mori = ModEntities.MEMENTO_MORI.get().create(level);
                if (mori != null) mori.setTrialBoss(attack);
                yield mori;
            }
            case "crane_loader" -> midBoss(ModEntities.CRANE_LOADER.get().create(level), attack);
            case "outpost_watcher" -> midBoss(ModEntities.OUTPOST_WATCHER.get().create(level), attack);
            case "foundry_master" -> midBoss(ModEntities.FOUNDRY_MASTER.get().create(level), attack);
            case "bastion_keeper" -> midBoss(ModEntities.BASTION_KEEPER.get().create(level), attack);
            default -> {
                ElenorEntity elenor = ModEntities.ELENOR.get().create(level);
                if (elenor != null) {
                    AttributeInstance damage = elenor.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (damage != null) damage.setBaseValue(damage.getBaseValue() * attack);
                    elenor.setTrialBoss(true);
                }
                yield elenor;
            }
        };
        if (boss == null) return null;
        if (trial.bossHealth() < 1.0F) {
            AttributeInstance max = boss.getAttribute(Attributes.MAX_HEALTH);
            if (max != null) {
                max.removeModifier(BOSS_HEALTH_ID);
                max.addPermanentModifier(new AttributeModifier(BOSS_HEALTH_ID, AutomataValkyries.MODID + ":trial_boss_health",
                        trial.bossHealth() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
                boss.setHealth(boss.getMaxHealth());
            }
        }
        boss.moveTo(at.x, at.y, at.z, yaw, 0F);
        boss.setCustomName(Component.translatable("trial.automata_valkyries.boss_name", Component.translatable("boss.automata_valkyries." + id)));
        boss.setPersistenceRequired();
        boss.setTarget(player);
        level.addFreshEntity(boss);
        if ("foundry_master".equals(id)) { // 位相アンカーのコンデンサー（全部壊すまで攻撃が届かない）
            for (int i = 0; i < 3; i++) {
                var condenser = ModEntities.PHASE_CONDENSER.get().create(level);
                if (condenser == null) continue;
                double a = Math.PI * 2 * i / 3 + Math.PI / 6;
                condenser.moveTo(at.x + Math.cos(a) * 9.0, at.y, at.z + Math.sin(a) * 9.0, 0F, 0F);
                level.addFreshEntity(condenser);
            }
        }
        return boss;
    }

    private static @Nullable Mob midBoss(@Nullable ChronoMidBoss boss, float attack) {
        if (boss != null) boss.setTrialBoss(attack);
        return boss;
    }

    /** 今の波を出す（1 体は奥に、同時は 3 方向に） */
    static boolean spawnWave(ServerLevel level, ServerPlayer player, Session s) {
        s.bosses.clear();
        List<String> ids = s.wave();
        for (int i = 0; i < ids.size(); i++) {
            Vec3 at;
            float yaw;
            if (ids.size() == 1) {
                at = s.center.add(0, 0, 10);
                yaw = 180F;
            } else {
                double a = Math.PI / 2 + (i - (ids.size() - 1) / 2.0) * Math.toRadians(55);
                at = s.center.add(Math.cos(a) * 11, 0, Math.sin(a) * 11);
                yaw = (float) Math.toDegrees(Math.atan2(-(s.center.x - at.x), s.center.z - at.z));
            }
            LivingEntity boss = spawnBoss(level, player, s.trial, ids.get(i), at, yaw);
            if (boss != null) s.bosses.add(boss);
        }
        return !s.bosses.isEmpty();
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
                cleanup(s, player);
                returnHome(player);
            }
        }
        // 挑戦していないのに回想の空間にいるプレイヤー（サーバーの再起動など）は元の場所へ戻す
        if (server.getTickCount() % 20 == 0) {
            ServerLevel arena = server.getLevel(ModTrials.RECOLLECTION);
            if (arena != null) for (ServerPlayer p : java.util.List.copyOf(arena.players())) if (!inTrial(p)) returnHome(p);
        }
    }

    /** 倒れた（撃破の演出の途中も含む） */
    static boolean down(LivingEntity boss) {
        return boss.isDeadOrDying() || boss.getRemovalReason() == Entity.RemovalReason.KILLED;
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
                    title(player, Component.literal(String.valueOf(left / 20)).withStyle(ChatFormatting.GOLD), waveName(s), 0, 20, 0);
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1F, 1.2F);
                }
                if (left <= 0) {
                    if (!spawnWave(level, player, s)) return true;
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
                if (s.bosses.isEmpty()) {
                    fail(player, s, "trial.automata_valkyries.failed.lost");
                    return false;
                }
                boolean allDown = true;
                for (LivingEntity boss : s.bosses) {
                    if (down(boss)) continue;
                    if (boss.isRemoved()) { // 倒れずに消えた（退いた・消された）
                        fail(player, s, "trial.automata_valkyries.failed.lost");
                        return false;
                    }
                    keepInside(boss, player, s);
                    allDown = false;
                }
                if (allDown) {
                    s.defeated.addAll(s.bosses);
                    if (s.trial.mode() == Trial.Mode.RUSH && s.stage < s.trial.bosses().size() - 1) intermission(player, s);
                    else clear(player, s);
                    return false;
                }
                if (s.fightTick > s.trial.timeLimit()) {
                    fail(player, s, "trial.automata_valkyries.failed.time");
                    return false;
                }
                if (s.fightTick % 20 == 0) {
                    Component status = Component.translatable("trial.automata_valkyries.status", Trial.clock(s.trial.timeLimit() - s.fightTick),
                            String.valueOf(s.hits));
                    if (s.trial.mode() == Trial.Mode.RUSH) {
                        status = Component.translatable("trial.automata_valkyries.status_rush", s.stage + 1, s.trial.bosses().size()).append(" ").append(status);
                    }
                    player.displayClientMessage(status.copy().withStyle(s.trial.timeLimit() - s.fightTick < 600 ? ChatFormatting.RED : ChatFormatting.GRAY), true);
                }
            }
            case INTERMISSION -> { // 連戦の合間（時間は数えない）
                if (level.dimension() != ModTrials.RECOLLECTION) {
                    fail(player, s, "trial.automata_valkyries.failed.left");
                    return false;
                }
                if (--s.gap <= 0) {
                    s.stage++;
                    if (!spawnWave(level, player, s)) {
                        fail(player, s, "trial.automata_valkyries.failed.lost");
                        return false;
                    }
                    s.state = State.FIGHT;
                    title(player, Component.translatable("trial.automata_valkyries.fight").withStyle(ChatFormatting.RED), waveName(s), 0, 30, 10);
                    level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.2F);
                } else if (s.gap % 20 == 0) {
                    level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1F, 1.2F);
                }
            }
            case CLEARED, FAILED -> {
                return s.tick >= s.endTick;
            }
        }
        return false;
    }

    /**
     * 見えない壁の外に出たボスを場の中へ戻す（2026-10-27 ユーザー指定。飛ぶ・瞬間移動するボスが壁を越えると、外から相手を見失って戻れなかった）。
     * 壁の内側 {@link #KEEP_MARGIN} ブロックへ寄せ、高さも壁の高さに収め、勢いを消して相手をプレイヤーに向け直す
     */
    static void keepInside(LivingEntity boss, ServerPlayer player, Session s) {
        double dx = boss.getX() - s.center.x, dz = boss.getZ() - s.center.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double minY = s.center.y - 1, maxY = s.center.y + KEEP_TOP;
        boolean out = d > ARENA_RADIUS - 0.5 || boss.getY() < minY - 1 || boss.getY() > maxY + 2;
        if (!out) return;
        double limit = ARENA_RADIUS - KEEP_MARGIN;
        double k = d > limit ? limit / d : 1.0;
        double y = Math.max(s.center.y, Math.min(maxY - boss.getBbHeight(), boss.getY()));
        boss.teleportTo(s.center.x + dx * k, y, s.center.z + dz * k);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.fallDistance = 0;
        if (boss instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) mob.setTarget(player);
        }
    }

    /** 今の波のボスの名前（連戦は「第 n 戦: ○○」） */
    static Component waveName(Session s) {
        if (s.trial.mode() != Trial.Mode.RUSH) return s.trial.bossName();
        return Component.translatable("trial.automata_valkyries.rush_stage", s.stage + 1, s.trial.bosses().size(),
                Component.translatable("boss.automata_valkyries." + s.trial.bosses().get(s.stage)));
    }

    /** 連戦の合間: 少し回復し、次のボスの場へ作り替える */
    static void intermission(ServerPlayer player, Session s) {
        s.state = State.INTERMISSION;
        s.gap = INTERMISSION;
        player.heal(player.getMaxHealth() * RUSH_HEAL_PERCENT / 100.0F);
        String next = s.trial.bosses().get(s.stage + 1);
        ServerLevel level = player.serverLevel();
        for (var condenser : level.getEntitiesOfClass(com.automatavalkyries.entity.chrono.PhaseCondenserEntity.class, new AABB(BlockPos.containing(s.center)).inflate(ARENA_RADIUS + 4, 16, ARENA_RADIUS + 4))) {
            condenser.discard(); // 工場長機の残したコンデンサー
        }
        TrialArenas.build(level, BlockPos.containing(s.center), TrialArenas.themeFor(next));
        Component nextName = Component.translatable("trial.automata_valkyries.rush_stage", s.stage + 2, s.trial.bosses().size(),
                Component.translatable("boss.automata_valkyries." + next));
        title(player, Component.translatable("trial.automata_valkyries.rush_next").withStyle(s2 -> s2.withColor(0xE8D8B0)), nextName, 10, 70, 20);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.8F);
    }

    static void clear(ServerPlayer player, Session s) {
        Trial.Rank rank = s.trial.rank(s.fightTick, s.hits);
        AutomataValkyries.LOGGER.info("Trial {} cleared by {} in {} ticks with {} hits (rank {})", s.trial.id(), player.getGameProfile().getName(), s.fightTick, s.hits, rank);
        s.state = State.CLEARED;
        s.endTick = s.tick + AFTER_CLEAR;
        title(player, Component.translatable("trial.automata_valkyries.cleared").withStyle(s2 -> s2.withColor(0xFFE8A0)),
                Component.translatable("trial.automata_valkyries.cleared.rank", s.trial.title(), Trial.clock(s.fightTick), s.hits,
                        Component.literal(rank.letter()).withStyle(s2 -> s2.withColor(rank.color()).withBold(true))), 10, 70, 20);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
        recordClear(player, s.trial, rank, s.fightTick, s.defeated);
    }

    static void fail(ServerPlayer player, @Nullable Session s, String reason) {
        if (s == null || s.state == State.CLEARED || s.state == State.FAILED) return;
        AutomataValkyries.LOGGER.info("Trial {} failed for {}: {} (fight tick {}, hits {}, stage {})", s.trial.id(), player.getGameProfile().getName(), reason, s.fightTick, s.hits, s.stage);
        s.state = State.FAILED;
        s.endTick = s.tick + AFTER_FAIL;
        retreatBosses(s);
        title(player, Component.translatable("trial.automata_valkyries.failed").withStyle(ChatFormatting.DARK_RED), Component.translatable(reason), 10, 50, 20);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.7F);
    }

    static void retreatBosses(Session s) {
        for (LivingEntity boss : s.bosses) {
            if (boss.isRemoved() || down(boss)) continue;
            if (boss instanceof ElenorEntity elenor) elenor.retreat();
            else if (!(boss instanceof com.automatavalkyries.entity.RetreatableBoss retreating) || !retreating.beginRetreat()) {
                // ルクシオンは剣を納めて光に溶ける、ガリアフォートは障壁で身を包んで歪みに消える。中ボスは保護を外して消す
                if (boss instanceof com.automatavalkyries.protect.IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
                boss.discard();
            }
        }
    }

    /**
     * 回想が終わったら闘技場を後始末する（2026-10-27 ユーザー指定「雑魚敵やアイテムと経験値を後始末して」）。
     * 落ちているアイテムと経験値は {@code collector} がいれば渡し（ボスの落とし物を取りこぼさないように）、いなければ消す。
     * ボス（退く途中も）・呼ばれた雑魚・弾などは消す（保護されたものは保護を外してから）。プレイヤー・兵姫・飼い主のいる動物は残す
     */
    static void cleanup(Session s, @Nullable ServerPlayer collector) {
        MinecraftServer server = s.player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(ModTrials.RECOLLECTION);
        if (level == null) return;
        int r = ARENA_RADIUS;
        for (Entity e : level.getEntities((Entity) null, new AABB(BlockPos.containing(s.center)).inflate(r + 8, 32, r + 8),
                e -> !(e instanceof Player) && !(e instanceof com.automatavalkyries.entity.ValkyrieEntity)
                        && !(e instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerUUID() != null))) { // 兵姫・飼っている動物は残す
            if (collector != null && e instanceof net.minecraft.world.entity.item.ItemEntity item && !item.getItem().isEmpty()) {
                give(collector, List.of(item.getItem().copy()));
            } else if (collector != null && e instanceof net.minecraft.world.entity.ExperienceOrb orb) {
                collector.giveExperiencePoints(orb.getValue());
            }
            if (e instanceof com.automatavalkyries.protect.IEprotected p && p.getEprotect() != null) p.getEprotect().releaseForCleanup();
            e.discard();
        }
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
            if (s.state != State.FIGHT || !s.trial.meleeOnly() || !s.isBoss(target)) continue;
            Entity direct = event.getSource().getDirectEntity();
            if (direct == null || !direct.getUUID().equals(e.getKey())) {
                event.setCanceled(true);
                if (event.getSource().getEntity() instanceof ServerPlayer p) {
                    p.displayClientMessage(Component.translatable("trial.automata_valkyries.melee_only_block").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    /** 中ボスの攻撃力の倍率（中ボスの技は値を直に持つので、ここでまとめて掛ける。弾・投げた荷は持ち主で見る） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMidBossHit(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ChronoMidBoss mid && mid.trialAttack() != 1.0F) {
            event.setAmount(event.getAmount() * mid.trialAttack());
        }
    }

    /** 被弾の回数を数える（ランクに使う。被弾の上限があるトライアルは超えたら失敗） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        Session s = fighting(event.getEntity());
        if (s == null || event.getAmount() <= 0) return;
        s.hits++; // 被弾数はランクだけ（失敗にはしない）
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
            if (s != null) cleanup(s, null);
            setFrail(player, false);
            data(player).remove("Return"); // 倒れたら、いつもの復活地点へ
        }
    }

    /**
     * 回想の空間で倒れても持ち物と経験値を落とさない（2026-10-27 ユーザー指定「回想でのデスはキープインベントリで」）。
     * 死ぬ直前に持ち物と経験値を引き継がれる保存（{@link #data}）へ移して空にし、生き返ったときに戻す。ゲームルールの keepInventory が立っていれば何もしない
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void keepOnDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != ModTrials.RECOLLECTION || player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) return;
        CompoundTag kept = new CompoundTag();
        kept.put("Items", player.getInventory().save(new net.minecraft.nbt.ListTag()));
        kept.putInt("XpLevel", player.experienceLevel);
        kept.putFloat("XpProgress", player.experienceProgress);
        kept.putInt("XpTotal", player.totalExperience);
        CompoundTag root = data(player);
        if (root.contains("Kept")) return; // まだ戻していない分を上書きしない
        root.put("Kept", kept);
        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.experienceProgress = 0;
        player.totalExperience = 0;
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = data(player);
        if (!root.contains("Kept")) return;
        CompoundTag kept = root.getCompound("Kept");
        root.remove("Kept");
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        net.minecraft.world.entity.player.Inventory saved = new net.minecraft.world.entity.player.Inventory(player);
        saved.load(kept.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND));
        for (int i = 0; i < saved.getContainerSize(); i++) {
            ItemStack stack = saved.getItem(i);
            if (stack.isEmpty()) continue;
            if (inv.getItem(i).isEmpty()) inv.setItem(i, stack);
            else if (!inv.add(stack)) player.drop(stack, false); // 生き返るまでに何か持たされていたら空きへ、無ければ足もとへ
        }
        player.experienceProgress = kept.getFloat("XpProgress");
        player.totalExperience = kept.getInt("XpTotal");
        player.setExperienceLevels(kept.getInt("XpLevel")); // 送り直しも兼ねる
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) cleanup(s, null); // 次に入ったとき、回想の空間にいれば元の場所へ戻す（毎秒の見回り）
    }
}
