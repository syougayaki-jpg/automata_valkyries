package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.gametest.fake.FakeKillMod;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 攻撃の記録（どの MOD が攻撃したか）とブラックリストの検証。
 * {@link FakeKillMod} のパッケージを架空の MOD として扱い、そこからの呼び出しを「他 MOD の攻撃」として再現する。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class AttackAttributionGameTests {

    private static final String TEMPLATE = "empty";

    private static ElenorEntity spawnElenor(GameTestHelper helper) {
        FakeKillMod.install();
        ModBlacklist.setPersistDisabledForTest(true);
        com.automatavalkyries.protect.plus.EprotectPlusLink.resetReinforced();
        return helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
    }

    private static void finish(GameTestHelper helper, ElenorEntity elenor) {
        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    private static boolean logged(String mod, String op, AttackLog.Outcome outcome) {
        return AttackLog.entries().stream().anyMatch(e -> e.mod.equals(mod) && e.op.equals(op) && e.outcome == outcome);
    }

    /** クラス → MOD の対応（vanilla / このMOD / 架空の MOD） */
    @GameTest(template = TEMPLATE)
    public static void modMappingIsCorrect(GameTestHelper helper) {
        FakeKillMod.install();
        String vanilla = CallerAttribution.modOf(Entity.class);
        helper.assertTrue(vanilla.equals(CallerAttribution.MINECRAFT) || vanilla.equals(CallerAttribution.FORGE),
                "Entity was attributed to " + vanilla);
        helper.assertTrue(CallerAttribution.modOf(AttackAttributionGameTests.class).equals(AutomataValkyries.MODID),
                "This mod's class was attributed to " + CallerAttribution.modOf(AttackAttributionGameTests.class));
        helper.assertTrue(CallerAttribution.modOf(FakeKillMod.class).equals(FakeKillMod.MOD_ID),
                "The fake mod's class was attributed to " + CallerAttribution.modOf(FakeKillMod.class));
        helper.succeed();
    }

    /** 架空の MOD から呼ぶと、直接の呼び出し元がその MOD になる。このMOD から直接呼べば自分 */
    @GameTest(template = TEMPLATE)
    public static void immediateCallerIsTheCallingMod(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        CallerAttribution.Attribution fromFake = FakeKillMod.probe(elenor);
        helper.assertTrue(fromFake.callerMod().equals(FakeKillMod.MOD_ID), "Fake mod call was attributed to " + fromFake.callerMod());
        CallerAttribution.Attribution fromSelf = CallerAttribution.attribute(elenor);
        helper.assertTrue(fromSelf.callerMod().equals(AutomataValkyries.MODID), "Own call was attributed to " + fromSelf.callerMod());
        finish(helper, elenor);
    }

    /** 全 MOD 共通で拒否した攻撃は、攻撃した MOD の名前で記録される */
    @GameTest(template = TEMPLATE)
    public static void refusedAttackIsRecordedWithTheAttacker(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        FakeKillMod.setHealth(elenor, 0.0F);
        helper.assertTrue(logged(FakeKillMod.MOD_ID, "health_loss:zero", AttackLog.Outcome.REFUSED),
                "The refused health wipe was not recorded under the fake mod");
        finish(helper, elenor);
    }

    /** ブラックリストの MOD による遠くへの強制移動は止まり、近くへの移動は通る */
    @GameTest(template = TEMPLATE)
    public static void blacklistBlocksForcedRelocationOnly(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
        try {
            double x = elenor.getX();
            FakeKillMod.relocate(elenor, 100.0D);
            helper.assertTrue(elenor.getX() == x, "Forced relocation by a blacklisted mod was not blocked");
            helper.assertTrue(logged(FakeKillMod.MOD_ID, "forced_relocation", AttackLog.Outcome.BLOCKED_BLACKLIST),
                    "The blocked relocation was not recorded");
            FakeKillMod.relocate(elenor, 1.0D);
            helper.assertTrue(elenor.getX() == x + 1.0D, "A short move by a blacklisted mod was blocked");
        } finally {
            ModBlacklist.remove(FakeKillMod.MOD_ID, false);
        }
        finish(helper, elenor);
    }

    /** ブラックリストの MOD でも通常のダメージ（hurt 経由）は通る */
    @GameTest(template = TEMPLATE)
    public static void blacklistDoesNotBlockOrdinaryDamage(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
        try {
            elenor.getEprotect().resetDamageWindow();
            float before = elenor.getHealth();
            FakeKillMod.hit(elenor, helper.getLevel().damageSources().generic(), 5.0F);
            helper.assertTrue(elenor.getHealth() == before - 5.0F, "Ordinary damage from a blacklisted mod was blocked: " + elenor.getHealth());
        } finally {
            ModBlacklist.remove(FakeKillMod.MOD_ID, false);
        }
        finish(helper, elenor);
    }

    /** ブラックリストの MOD による次元移動と、次元移動を装った消去は止まる（移動先に複製も残らない） */
    @GameTest(template = TEMPLATE)
    public static void blacklistBlocksDimensionChange(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
        try {
            if (nether != null) {
                Entity moved = FakeKillMod.changeDimension(elenor, nether);
                helper.assertTrue(moved == null, "Dimension change by a blacklisted mod was not blocked");
                helper.assertTrue(nether.getEntity(elenor.getUUID()) == null, "A copy was left in the Nether");
            }
            FakeKillMod.fakeDimensionRemoval(elenor);
            helper.assertTrue(elenor.getRemovalReason() == null, "Fake dimension removal was not blocked: " + elenor.getRemovalReason());
            helper.assertTrue(helper.getLevel().getEntity(elenor.getUUID()) == elenor, "Elenor left the level");
        } finally {
            ModBlacklist.remove(FakeKillMod.MOD_ID, false);
        }
        finish(helper, elenor);
    }

    /** minecraft / forge / このMOD はブラックリストに載せられない */
    @GameTest(template = TEMPLATE)
    public static void protectedModsCannotBeBlacklisted(GameTestHelper helper) {
        for (String id : new String[]{"minecraft", "forge", AutomataValkyries.MODID}) {
            helper.assertTrue(ModBlacklist.add(id, ModBlacklist.Source.MANUAL, false) == ModBlacklist.Result.PROTECTED,
                    id + " was not refused");
        }
        helper.assertTrue(ModBlacklist.add("Not A Mod!", ModBlacklist.Source.MANUAL, false) == ModBlacklist.Result.INVALID,
                "An invalid id was accepted");
        helper.succeed();
    }

    /** 自動登録: しきい値に達したら載り、同じ Tick の重複は数えず、外すと数え直す */
    @GameTest(template = TEMPLATE)
    public static void autoBlacklistAfterThreshold(GameTestHelper helper) {
        ModBlacklist.setPersistDisabledForTest(true);
        String mod = "test_fake_auto";
        ModBlacklist.remove(mod, false);
        long base = helper.getLevel().getGameTime() + 1_000_000L; // 他のテストの時刻と重ならない
        for (int i = 0; i < 19; i++) {
            ModBlacklist.noteAttackAt(null, mod, base + i);
            ModBlacklist.noteAttackAt(null, mod, base + i); // 同じ Tick は 1 回
        }
        helper.assertFalse(ModBlacklist.contains(mod), "Blacklisted before reaching the threshold");
        ModBlacklist.noteAttackAt(null, mod, base + 19);
        helper.assertTrue(ModBlacklist.contains(mod), "Not blacklisted at the threshold");
        ModBlacklist.remove(mod, false);
        ModBlacklist.noteAttackAt(null, mod, base + 20);
        helper.assertFalse(ModBlacklist.contains(mod), "Counter was not reset on removal");
        helper.succeed();
    }

    /** 一括で外す: すべて外れ、自動登録の回数も数え直す */
    @GameTest(template = TEMPLATE)
    public static void blacklistClearRemovesEverything(GameTestHelper helper) {
        ModBlacklist.setPersistDisabledForTest(true);
        java.util.Set<String> saved = ModBlacklist.list();
        try {
            ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
            String mod = "test_fake_clear";
            long base = helper.getLevel().getGameTime() + 2_000_000L;
            for (int i = 0; i < 19; i++) ModBlacklist.noteAttackAt(null, mod, base + i);
            int cleared = ModBlacklist.clear(false);
            helper.assertTrue(cleared == saved.size() + 1, "Cleared " + cleared + " instead of " + (saved.size() + 1));
            helper.assertFalse(ModBlacklist.isActive(), "The blacklist is not empty: " + ModBlacklist.list());
            ModBlacklist.noteAttackAt(null, mod, base + 19);
            helper.assertFalse(ModBlacklist.contains(mod), "Auto counters were not reset");
        } finally {
            ModBlacklist.clear(false);
            for (String mod : saved) ModBlacklist.add(mod, ModBlacklist.Source.MANUAL, false);
        }
        helper.succeed();
    }

    /** ブラックリストがあっても、通常の移動（短い距離）ではスタックを辿らない */
    @GameTest(template = TEMPLATE)
    public static void ordinaryMovementDoesNotWalkTheStack(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
        try {
            long before = CallerAttribution.walkCount();
            for (int i = 0; i < 100; i++) {
                elenor.setPos(elenor.getX() + 0.01D, elenor.getY(), elenor.getZ());
            }
            helper.assertTrue(CallerAttribution.walkCount() == before, "Ordinary movement walked the stack");
        } finally {
            ModBlacklist.remove(FakeKillMod.MOD_ID, false);
        }
        finish(helper, elenor);
    }

    /**
     * 超絶抹殺の棒の準汎用処理: 架空の MOD のボスの保護・復活のフラグが下り（待ち時間には触れない）、本体が消され、
     * 静的な一覧・管理表から対象・本体・UUID が取り除かれ、同じ MOD の乗客もまとめて消える
     */
    @GameTest(template = TEMPLATE)
    public static void ultraKillStickBreaksSemiGenericLifeSupport(GameTestHelper helper) {
        FakeKillMod.install();
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        com.automatavalkyries.gametest.fake.FakeBoss boss = new com.automatavalkyries.gametest.fake.FakeBoss(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        boss.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        boss.registerInMod();
        var minion = new com.automatavalkyries.gametest.fake.FakeBoss.Minion(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        minion.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(minion);
        minion.startRiding(boss, true);

        com.automatavalkyries.annihilate.AnnihilationExecutionAgent.annihilateEntity(level, helper.makeMockPlayer(), boss);

        helper.assertFalse(boss.invulnerableToAll, "Protection flag was not lowered");
        helper.assertFalse(boss.canRevive, "Revival flag was not lowered");
        helper.assertTrue(boss.reviveCount == 0, "Remaining revivals were not zeroed: " + boss.reviveCount);
        helper.assertTrue(boss.respawnDelay == 100, "A timer was touched: " + boss.respawnDelay);
        helper.assertTrue(boss.controller.removed, "The controller was not removed");
        helper.assertTrue(com.automatavalkyries.gametest.fake.FakeBoss.REVIVE_QUEUE.isEmpty(),
                "Revival queue still holds: " + com.automatavalkyries.gametest.fake.FakeBoss.REVIVE_QUEUE);
        helper.assertTrue(com.automatavalkyries.gametest.fake.FakeBoss.REGISTRY.isEmpty(), "Registry still holds the boss");
        helper.assertTrue(minion.isRemoved(), "The same-mod passenger was not erased");
        helper.assertTrue(level.getEntity(minion.getUUID()) == null, "The passenger is still in the level");
        helper.succeed();
    }

    /** 種類単位の一時的な出現拒否: 期限内は近くでの同じ種類の参加を拒否し、期限が切れたら通す */
    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void spawnBanZoneRefusesRecreationUntilExpiry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.phys.Vec3 center = helper.absolutePos(new BlockPos(2, 2, 2)).getCenter();
        com.automatavalkyries.annihilate.SpawnBanZone.ban(level, net.minecraft.world.entity.EntityType.ALLAY, center, 40);
        Entity refused = helper.spawn(net.minecraft.world.entity.EntityType.ALLAY, new BlockPos(2, 2, 2));
        helper.assertTrue(level.getEntity(refused.getUUID()) == null, "A banned type joined inside the zone");
        helper.runAfterDelay(50, () -> {
            Entity allowed = helper.spawn(net.minecraft.world.entity.EntityType.ALLAY, new BlockPos(2, 2, 2));
            helper.assertTrue(level.getEntity(allowed.getUUID()) == allowed, "The ban did not expire");
            allowed.discard();
            helper.succeed();
        });
    }



    /** ブラックリストの MOD からのダメージは、上限が最大 HP の 1% に下がる（罰則） */
    @GameTest(template = TEMPLATE)
    public static void blacklistedDamageIsCappedHarder(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ModBlacklist.add(FakeKillMod.MOD_ID, ModBlacklist.Source.MANUAL, false);
        try {
            elenor.getEprotect().resetDamageWindow();
            float before = elenor.getHealth();
            float penaltyCap = Math.max(1.0F, elenor.getMaxHealth() * com.automatavalkyries.protect.Eprotect.BLACKLIST_HIT_RATIO);
            FakeKillMod.hit(elenor, helper.getLevel().damageSources().generic(), elenor.getMaxHealth());
            float taken = before - elenor.getHealth();
            helper.assertTrue(Math.abs(taken - penaltyCap) < 0.01F, "Blacklisted damage was not capped at 1%: took " + taken + ", cap " + penaltyCap);
            helper.assertTrue(logged(FakeKillMod.MOD_ID, com.automatavalkyries.protect.attack.AttackOp.DAMAGE_PENALTY, AttackLog.Outcome.BLOCKED_BLACKLIST),
                    "The damage penalty was not recorded");
        } finally {
            ModBlacklist.remove(FakeKillMod.MOD_ID, false);
        }
        finish(helper, elenor);
    }

    /**
     * 他 MOD の状態の準汎用リセット: 死亡・破滅を表す値は戻り、守りを表す値は残る。
     * 永続データの死亡の印は消え、他の値と vanilla の状態異常は残る
     */
    @GameTest(template = TEMPLATE)
    public static void foreignKillStateIsReset(GameTestHelper helper) {
        FakeKillMod.install();
        var mob = new com.automatavalkyries.gametest.fake.FakeStatefulMob(net.minecraft.world.entity.EntityType.ZOMBIE, helper.getLevel());
        mob.getPersistentData().putBoolean("fakemod_doomed", true);
        mob.getPersistentData().putBoolean("fakemod_protected", true);
        mob.getPersistentData().putInt("fakemod_level", 3);
        mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200));

        helper.assertTrue(com.automatavalkyries.protect.ForeignStateCleaner.clean(mob), "Nothing was cleaned");
        helper.assertFalse(mob.fakemod$isMarkedForDeath(), "Death mark was not reset");
        helper.assertTrue(mob.fakemod$getDoomLevel() == 0.0F, "Doom level was not reset: " + mob.fakemod$getDoomLevel());
        helper.assertTrue(mob.fakemod$isProtected(), "A guard-like value was touched");
        helper.assertFalse(mob.getPersistentData().contains("fakemod_doomed"), "Persistent kill mark was not removed");
        helper.assertTrue(mob.getPersistentData().contains("fakemod_protected"), "A guard-like persistent value was removed");
        helper.assertTrue(mob.getPersistentData().contains("fakemod_level"), "An unrelated persistent value was removed");
        helper.assertTrue(mob.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "A vanilla effect was removed");
        helper.assertFalse(com.automatavalkyries.protect.ForeignStateCleaner.clean(mob), "A clean entity was cleaned again");
        helper.succeed();
    }

    /** 他 MOD が保護対象の Tick を取り消しても戻す。保護対象でなければ取り消しのまま */
    @GameTest(template = TEMPLATE)
    public static void cancelledTickIsRestoredForProtectedOnly(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        var protectedTick = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(elenor);
        protectedTick.setCanceled(true);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(protectedTick);
        helper.assertFalse(protectedTick.isCanceled(), "The protected entity's tick stayed cancelled");

        Entity zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var otherTick = new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent((net.minecraft.world.entity.LivingEntity) zombie);
        otherTick.setCanceled(true);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(otherTick);
        helper.assertTrue(otherTick.isCanceled(), "An unprotected entity's tick was un-cancelled");
        zombie.discard();
        finish(helper, elenor);
    }

    /** 拒否した攻撃の後、攻撃した MOD のクラスの静的な一覧から保護対象が取り除かれる（同じ個体を狙い直させない） */
    @GameTest(template = TEMPLATE)
    public static void attackerKillListIsCleanedAfterRefusal(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        FakeKillMod.DOOMED_UUIDS.clear();
        FakeKillMod.DOOMED_ENTITIES.clear();
        FakeKillMod.doomAndDiscard(elenor);
        helper.assertFalse(elenor.isRemoved(), "The discard was not refused");
        helper.assertFalse(FakeKillMod.DOOMED_UUIDS.contains(elenor.getUUID()), "The UUID is still in the attacker's list");
        helper.assertFalse(FakeKillMod.DOOMED_ENTITIES.contains(elenor), "The entity is still in the attacker's list");
        finish(helper, elenor);
    }

    /**
     * 抹殺予定の一覧の準汎用の発見: 攻撃したクラスが参照する別のクラスの一覧を、クラス名（Death）から見つけて覚え、
     * 拒否の後に載せ直されても毎 Tick 外す
     */
    @GameTest(template = TEMPLATE)
    public static void killListInReferencedClassIsFoundAndPurged(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        com.automatavalkyries.gametest.fake.FakeDeathRegistry.ACTIVE.clear();
        FakeKillMod.quarantineAndDiscard(elenor);
        helper.assertTrue(com.automatavalkyries.protect.flag.KillLists.describe().contains("FakeDeathRegistry.ACTIVE"),
                "The kill list was not discovered: " + com.automatavalkyries.protect.flag.KillLists.describe());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(com.automatavalkyries.gametest.fake.FakeDeathRegistry.ACTIVE.containsKey(elenor),
                    "Elenor is still in the kill list");
            finish(helper, elenor);
        });
    }

    /** 止める一覧（時間停止中の個体の一覧）も、クラス名（TimeStop）から見つけて覚え、保護対象を毎 Tick 外す（2026-10-03） */
    @GameTest(template = TEMPLATE)
    public static void timeStopListInReferencedClassIsFoundAndPurged(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        com.automatavalkyries.gametest.fake.FakeTimeStopRegistry.FROZEN_IDS.clear();
        FakeKillMod.freezeAndDiscard(elenor);
        helper.assertTrue(com.automatavalkyries.protect.flag.KillLists.describe().contains("FakeTimeStopRegistry.FROZEN_IDS"),
                "The time-stop list was not discovered: " + com.automatavalkyries.protect.flag.KillLists.describe());
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(com.automatavalkyries.gametest.fake.FakeTimeStopRegistry.FROZEN_IDS.contains(elenor.getUUID()),
                    "Elenor is still in the time-stop list");
            finish(helper, elenor);
        });
    }

    /**
     * 作った時点で「ワールドに追加済み」になる個体（Witherzilla の処刑竜）を別スレッドで読み込んでも、移動先のチャンクの読み込み
     * （サーバースレッド待ち）をしない。サーバースレッドが読み込みの完了を待っていると互いに待ち合って固まる（終了時の保存で実際に固まった）
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void offThreadEntityLoadDoesNotWaitForChunks(GameTestHelper helper) throws InterruptedException {
        net.minecraft.world.entity.monster.Zombie zombie = new net.minecraft.world.entity.monster.Zombie(
                net.minecraft.world.entity.EntityType.ZOMBIE, helper.getLevel());
        ((com.automatavalkyries.mixin.EntityAccessor) zombie).automata_valkyries$setAddedToWorld(true);
        Thread loader = new Thread(() -> zombie.setPos(1_000_000.5D, 64.0D, 1_000_000.5D), "test-entity-deserializer");
        loader.setDaemon(true);
        loader.start();
        loader.join(3000); // サーバースレッドはここで待っているので、チャンクの読み込みを待つと 3 秒で終わらない
        helper.assertFalse(loader.isAlive(), "Off-thread setPos waited for a chunk load on the server thread");
        helper.assertTrue(zombie.isAddedToWorld(), "isAddedToWorld was not restored");
        helper.assertTrue(zombie.getX() == 1_000_000.5D, "The position was not set: " + zombie.getX());
        helper.succeed();
    }

    /** Mixin を経ない直書きの修復は、呼び出し元不明（unknown）として記録される */
    @GameTest(template = TEMPLATE)
    public static void directWriteRepairIsRecordedAsUnknown(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ((LivingEntityAccessor) elenor).automata_valkyries$setDead(true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(logged(CallerAttribution.UNKNOWN, "repair:dead_flag", AttackLog.Outcome.REPAIRED),
                    "The dead-flag repair was not recorded");
            finish(helper, elenor);
        });
    }
    /**
     * 抹殺で Tick 対象一覧から外すとき、一覧が回されている最中でも反復を壊さない（壊すと次の個体の代わりに null が渡り、
     * No-Sugar の時間停止の Mixin が落ちた。2026-10-02）。回している途中で別の個体を外しても、null は来ず、残りは最後まで回る
     */
    @GameTest(template = TEMPLATE)
    public static void tickListEvictionDuringIterationIsSafe(GameTestHelper helper) {
        var level = helper.getLevel();
        java.util.List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) zombies.add(helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1 + i % 3, 2, 1 + i / 3)));
        var tickList = ((com.automatavalkyries.mixin.ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
        int[] nulls = {0}, seen = {0};
        boolean[] evicted = {false};
        tickList.forEach(entity -> {
            if (entity == null) { nulls[0]++; return; }
            seen[0]++;
            if (!evicted[0] && zombies.contains(entity)) {
                evicted[0] = true;
                // 回している最中に、まだ回っていない別の個体を外す
                for (var z : zombies) if (z != entity) com.automatavalkyries.annihilate.TickListEviction.remove(tickList, z);
            }
        });
        helper.assertTrue(nulls[0] == 0, nulls[0] + " null entities were passed during iteration");
        helper.assertTrue(evicted[0], "no zombie was iterated");
        long left = zombies.stream().filter(tickList::contains).count();
        helper.assertTrue(left == 1, "expected only the first iterated zombie to remain in the tick list, left " + left);
        zombies.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }
}
