package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.ChronoMidBoss;
import com.automatavalkyries.entity.chrono.CraneLoaderEntity;
import com.automatavalkyries.entity.chrono.GalliaFortExEntity;
import com.automatavalkyries.entity.chrono.LuxionExEntity;
import com.automatavalkyries.entity.chrono.MementoMoriExEntity;
import com.automatavalkyries.weapon.WeaponDamage;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModTrials;
import com.automatavalkyries.trial.Trial;
import com.automatavalkyries.trial.TrialArenas;
import com.automatavalkyries.trial.TrialManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** 回想のトライアルの調整（2026-10-27 ユーザー指定「追憶メモリを調整する」）: ボスごとの闘技場・ランク・報酬・同時と連戦・中ボス・ルクシオン EX */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class TrialRecollectionGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "recollection_test"));
        Vec3 pos = helper.absoluteVec(relative);
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        return player;
    }

    /** どの場でも闘技場が建ち（床・見えない壁・床の上は空き）、場を替えても前の飾りが残らない */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void everyArenaThemeBuilds(GameTestHelper helper) {
        ServerLevel arena = helper.getLevel().getServer().getLevel(ModTrials.RECOLLECTION);
        helper.assertTrue(arena != null, "recollection dimension is missing");
        BlockPos c = new BlockPos(-8192, TrialManager.ARENA_Y, -4096);
        int r = TrialManager.ARENA_RADIUS;
        for (TrialArenas.Theme theme : TrialArenas.Theme.values()) {
            TrialArenas.build(arena, c, theme);
            helper.assertTrue(arena.getBlockState(c.offset(r / 2, -1, 0)).isSolid(), "no floor for " + theme);
            helper.assertTrue(arena.getBlockState(c.offset(r + 1, 3, 0)).is(Blocks.BARRIER), "no wall for " + theme);
            helper.assertTrue(arena.getBlockState(c.offset(3, 2, 3)).isAir(), "arena floor is not clear for " + theme);
        }
        TrialArenas.build(arena, c, TrialArenas.Theme.QUARTZ); // 零の墓標の浮かぶ瓦礫など、前の場の飾りは消える
        for (int dy = 0; dy <= 20; dy++) for (int dx = r + 3; dx <= r + 8; dx++) {
            var state = arena.getBlockState(c.offset(dx, dy + 4, 3));
            helper.assertTrue(state.isAir(), "leftover decoration at " + dx + "," + (dy + 4) + ": " + state);
        }
        helper.succeed();
    }

    /** ランク: S は制限時間の半分以内・被弾 3 回以下、A は 6 割以内・被弾 10 回以下、それ以外は B */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void rankFollowsTimeAndHits(GameTestHelper helper) {
        Trial t = Trial.get(Trial.indexOf("luxion"));
        int limit = t.timeLimit();
        helper.assertTrue(t.rank(limit / 2, 3) == Trial.Rank.S, "half time and 3 hits is not S");
        helper.assertTrue(t.rank(limit / 2, 4) == Trial.Rank.A, "4 hits should drop to A");
        helper.assertTrue(t.rank((int) (limit * 0.6F), 10) == Trial.Rank.A, "60% and 10 hits is not A");
        helper.assertTrue(t.rank((int) (limit * 0.7F), 0) == Trial.Rank.B, "70% is not B");
        helper.assertTrue(t.rank(limit / 4, 11) == Trial.Rank.B, "11 hits is not B");
        helper.succeed();
    }

    /** 初めての S ランクでオーバーロードメモリ（同じ回想の 2 回目の S では出ない）。いちばん良いランクと最速を覚える */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void sRankGivesOverloadMemoryOnce(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        Trial t = Trial.get(Trial.indexOf("luxion"));
        helper.assertTrue(TrialManager.recordClear(player, t, Trial.Rank.S, 1200, List.of()), "first rank S gave no memory");
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 1, "first rank S did not give one memory");
        helper.assertFalse(TrialManager.recordClear(player, t, Trial.Rank.S, 1500, List.of()), "second rank S gave a memory again");
        TrialManager.recordClear(player, t, Trial.Rank.B, 900, List.of());
        helper.assertTrue(TrialManager.bestRank(player, t) == Trial.Rank.S, "best rank was lowered");
        helper.assertTrue(TrialManager.bestTime(player, t) == 900, "best time is " + TrialManager.bestTime(player, t));
        helper.succeed();
    }

    /**
     * 全クリアの報酬（2026-10-27 ユーザー指定「全クリした時にももらえるように」）: はじめの 5 つを全クリアしてオーバーロードメモリを受け取った前の保存データでも、
     * 増えた一覧を全部越えた時にもう 1 つ出る
     */
    @GameTest(template = TEMPLATE, batch = "trials")
    public static void fullClearOfGrownListGivesMemory(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        var persisted = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        var root = persisted.getCompound("automata_valkyries_trials");
        root.putBoolean("OverloadGranted", true); // はじめの 5 つの全クリアで受け取り済み
        persisted.put("automata_valkyries_trials", root);
        player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        for (int i = 0; i < Trial.ALL.size() - 1; i++) TrialManager.recordClear(player, Trial.ALL.get(i));
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 0, "memory given before the full clear");
        helper.assertTrue(TrialManager.recordClear(player, Trial.ALL.get(Trial.ALL.size() - 1)), "no memory on the full clear of the grown list");
        helper.assertTrue(player.getInventory().countItem(ModTrials.OVERLOAD_MEMORY.get()) == 1, "full clear did not give one memory");
        helper.succeed();
    }

    /** 中ボスの回想:本物の中ボスが出て保存されず、倒すとクリア（宝箱は置かず、中身と初回の報酬を手渡す）。数秒後に元の場所へ戻る */
    @GameTest(template = TEMPLATE, batch = "trial_run", timeoutTicks = 600)
    public static void midBossTrialClearsAndRewards(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        ServerLevel home = helper.getLevel();
        Trial trial = Trial.get(Trial.indexOf("crane_loader"));
        helper.assertTrue(TrialManager.start(player, trial), "trial did not start");
        long spawned = TrialManager.COUNTDOWN + 10;
        helper.runAfterDelay(spawned, () -> {
            LivingEntity boss = TrialManager.boss(player);
            helper.assertTrue(boss instanceof CraneLoaderEntity, "the crane loader trial did not spawn the crane loader: " + boss);
            helper.assertFalse(boss.shouldBeSaved(), "the trial mid-boss would be saved");
            helper.assertTrue(((ChronoMidBoss) boss).trialAttack() == trial.bossAttack(), "attack multiplier was not set");
            boss.hurt(boss.damageSources().playerAttack(player), 100000.0F);
        });
        helper.runAfterDelay(spawned + 20, () -> {
            helper.assertTrue(TrialManager.cleared(player, trial), "the trial was not cleared");
            helper.assertFalse(player.getInventory().isEmpty(), "no reward was handed over");
        });
        helper.runAfterDelay(spawned + 20 + TrialManager.AFTER_CLEAR + 10, () -> {
            helper.assertFalse(TrialManager.inTrial(player), "trial did not end after clearing");
            helper.assertTrue(player.level() == home, "player was not returned home");
            helper.succeed();
        });
    }

    /** 三英雄の共鳴: ルクシオン・ガリアフォート・メメントモリが同時に出て、どれも最大 HP が 6 割 */
    @GameTest(template = TEMPLATE, batch = "trial_run", timeoutTicks = 400)
    public static void aecTrioSpawnsTogetherWithReducedHealth(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        Trial trial = Trial.get(Trial.indexOf("aec_trio"));
        helper.assertTrue(TrialManager.start(player, trial), "trial did not start");
        helper.runAfterDelay(TrialManager.COUNTDOWN + 20, () -> {
            List<LivingEntity> bosses = TrialManager.bosses(player);
            helper.assertTrue(bosses.size() == 3, "expected 3 bosses, got " + bosses.size());
            helper.assertTrue(bosses.get(0).getType() == ModEntities.LUXION.get() && bosses.get(1).getType() == ModEntities.GALLIAFORT.get()
                    && bosses.get(2).getType() == ModEntities.MEMENTO_MORI.get(), "wrong bosses: " + bosses);
            for (LivingEntity b : bosses) {
                double base = b.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
                helper.assertTrue(Math.abs(b.getMaxHealth() - base * trial.bossHealth()) < 1.0, "max health of " + b + " is " + b.getMaxHealth());
                helper.assertTrue(b.isAlive() && !b.isRemoved(), b + " is not alive");
            }
            TrialManager.abandon(player);
        });
        helper.runAfterDelay(TrialManager.COUNTDOWN + 20 + TrialManager.AFTER_FAIL + 10, () -> {
            helper.assertFalse(TrialManager.inTrial(player), "trial did not end after abandoning");
            helper.succeed();
        });
    }

    /** ルクシオン EX: HP 30,000、66% で第 2 形態、33% で第 3 形態（変身の後）。第 3 形態の 30% 以下で刻の加速 */
    @GameTest(template = TEMPLATE, batch = "trials", timeoutTicks = 300)
    public static void luxionExPhases(GameTestHelper helper) {
        LuxionExEntity ex = helper.spawn(ModEntities.LUXION_EX.get(), new Vec3(2.5, 2, 2.5));
        helper.assertTrue(Math.abs(ex.getMaxHealth() - LuxionExEntity.MAX_HP_EX) < 1.0F, "max health is " + ex.getMaxHealth());
        helper.runAfterDelay(2, () -> ex.hurt(ex.damageSources().magic(), 11000.0F)); // 19,000（63%）
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ex.isPhase2(), "phase 2 did not start below 66% (" + ex.healthRatio() + ")");
            helper.assertFalse(ex.isPhase3(), "phase 3 started too early");
        });
        helper.runAfterDelay(50, () -> ex.hurt(ex.damageSources().magic(), 10100.0F)); // 8,900（約 30%）
        helper.runAfterDelay(55, () -> helper.assertTrue(ex.isPhase3(), "phase 3 did not start below 33% (" + ex.healthRatio() + ")"));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(ex.isHasted(), "acceleration of time did not start at 30%");
            ex.discard();
            helper.succeed();
        });
    }

    /** ガリアフォート EX: HP 30,000、66% で第 2 形態（人型への変形）、33% で第 3 形態。障壁を素通しする重粒子収束砲で削る */
    @GameTest(template = TEMPLATE, batch = "trials", timeoutTicks = 300)
    public static void galliaFortExPhases(GameTestHelper helper) {
        GalliaFortExEntity ex = helper.spawn(ModEntities.GALLIAFORT_EX.get(), new Vec3(3.5, 2, 3.5));
        FakePlayer player = player(helper, new Vec3(0.5, 2, 0.5));
        helper.assertTrue(Math.abs(ex.getMaxHealth() - GalliaFortExEntity.MAX_HP_EX) < 1.0F, "max health is " + ex.getMaxHealth());
        helper.runAfterDelay(2, () -> ex.hurt(WeaponDamage.source(player, WeaponDamage.PARTICLE_BEAM, player, player), 13000.0F));
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ex.isPhase2(), "phase 2 did not start below 66% (" + ex.getHealth() + ")");
            helper.assertFalse(ex.isPhase3(), "phase 3 started too early");
        });
        helper.runAfterDelay(100, () -> ex.hurt(WeaponDamage.source(player, WeaponDamage.PARTICLE_BEAM, player, player), 14000.0F));
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(ex.isPhase3(), "phase 3 did not start below 33% (" + ex.getHealth() + ")");
            ex.discard();
            helper.succeed();
        });
    }

    /** メメントモリ EX: HP 90,000、66% で第 2 形態、33% で第 3 形態（呪いが深まる: 最大 HP は 25% まで・移動速度も削る）。終葬は第四楽章のぶん長い */
    @GameTest(template = TEMPLATE, batch = "trials", timeoutTicks = 400)
    public static void mementoMoriExPhasesAndDeepCurse(GameTestHelper helper) {
        MementoMoriExEntity ex = helper.spawn(ModEntities.MEMENTO_MORI_EX.get(), new Vec3(3.5, 2, 3.5));
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        pig.setNoAi(true);
        helper.assertTrue(Math.abs(ex.getMaxHealth() - MementoMoriExEntity.MAX_HP_EX) < 1.0F, "max health is " + ex.getMaxHealth());
        helper.assertTrue(ex.spellTicks() > com.automatavalkyries.entity.chrono.MementoMoriEntity.SPELL_TICKS, "the true finale is not longer");
        helper.runAfterDelay(95, () -> ex.hurt(ex.damageSources().magic(), 35000.0F)); // 55,000（61%）
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(ex.isPhase2(), "phase 2 did not start below 66% (" + ex.getHealth() + ")");
            helper.assertFalse(ex.isPhase3(), "phase 3 started too early");
        });
        helper.runAfterDelay(210, () -> ex.hurt(ex.damageSources().magic(), 30000.0F)); // 25,000（28%）
        helper.runAfterDelay(215, () -> {
            helper.assertTrue(ex.isPhase3() && ex.deepCurse(), "phase 3 / deep curse did not start below 33% (" + ex.getHealth() + ")");
            float max = pig.getMaxHealth();
            double speed = pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            com.automatavalkyries.entity.chrono.MementoCurse.add(pig, 200, ex);
            helper.assertTrue(Math.abs(pig.getMaxHealth() - max * 0.25F) < 1.0e-3, "deep curse floor is not 25%: " + pig.getMaxHealth() / max);
            helper.assertTrue(pig.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < speed * 0.75, "deep curse did not slow");
            com.automatavalkyries.entity.chrono.MementoCurse.clear(pig);
            helper.assertTrue(pig.getMaxHealth() == max, "clearing did not restore max HP");
            ex.discard();
            pig.discard();
            helper.succeed();
        });
    }
}
