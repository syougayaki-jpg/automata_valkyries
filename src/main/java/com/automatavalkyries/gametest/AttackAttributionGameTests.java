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
     * 守護の護符を持つプレイヤー: 他 MOD の強制テレポート・即死（/kill 型・HP 0 化・die 直呼び・消去）は拒否し、
     * 短い移動と通常のダメージ（致死量なら死亡）は通す
     */
    @GameTest(template = TEMPLATE)
    public static void wardCharmBlocksModTeleportAndInstantKill(GameTestHelper helper) {
        FakeKillMod.install();
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.getInventory().add(new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.WARD_CHARM.get()));
        com.automatavalkyries.protect.PlayerWard.refresh(player);
        var generic = helper.getLevel().damageSources().generic();

        double x = player.getX();
        FakeKillMod.relocate(player, 100.0D);
        helper.assertTrue(player.getX() == x, "A mod's forced teleport moved the warded player");
        FakeKillMod.relocate(player, 1.0D);
        helper.assertTrue(player.getX() == x + 1.0D, "A short move of the warded player was blocked");

        player.kill();
        FakeKillMod.setHealth(player, 0.0F);
        FakeKillMod.die(player, generic);
        FakeKillMod.discard(player);
        helper.assertTrue(player.getHealth() > 0.0F && !player.isDeadOrDying(), "An instant kill got through: " + player.getHealth());
        helper.assertFalse(player.isRemoved(), "The warded player was removed: " + player.getRemovalReason());

        float before = player.getHealth();
        FakeKillMod.hit(player, generic, 5.0F);
        helper.assertTrue(player.getHealth() < before, "Ordinary damage to the warded player was blocked");
        FakeKillMod.hit(player, generic, 1000.0F);
        helper.assertTrue(player.isDeadOrDying(), "Lethal ordinary damage did not kill the warded player");
        helper.succeed();
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

    /** 守護の護符は死亡時に落とし物から外れ、リスポーン後の個体の持ち物へ戻る */
    @GameTest(template = TEMPLATE)
    public static void wardCharmIsKeptOnDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.player.Player dying = helper.makeMockPlayer();
        net.minecraft.world.item.ItemStack charm = new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.WARD_CHARM.get());
        java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
        drops.add(new net.minecraft.world.entity.item.ItemEntity(level, dying.getX(), dying.getY(), dying.getZ(), charm));
        drops.add(new net.minecraft.world.entity.item.ItemEntity(level, dying.getX(), dying.getY(), dying.getZ(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT)));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDropsEvent(
                dying, level.damageSources().generic(), drops, 0, false));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(net.minecraft.world.item.Items.DIRT),
                "The charm was dropped (or other drops were touched): " + drops);

        net.minecraft.world.entity.player.Player respawned = helper.makeMockPlayer();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(respawned, dying, true));
        helper.assertTrue(respawned.getInventory().contains(charm), "The charm was not returned after respawning");
        helper.succeed();
    }

    /** 護符を持たないプレイヤーは守られない */
    @GameTest(template = TEMPLATE)
    public static void playerWithoutCharmIsNotWarded(GameTestHelper helper) {
        FakeKillMod.install();
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        com.automatavalkyries.protect.PlayerWard.refresh(player);
        FakeKillMod.setHealth(player, 0.0F);
        helper.assertTrue(player.getHealth() <= 0.0F, "A player without the charm was protected");
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
}
