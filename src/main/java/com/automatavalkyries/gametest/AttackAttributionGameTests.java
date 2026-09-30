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
