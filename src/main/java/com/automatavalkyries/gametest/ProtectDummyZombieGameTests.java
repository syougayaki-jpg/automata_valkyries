package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.ProtectDummyZombieEntity;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Overload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ProtectDummyZombieGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * ダミーゾンビが Eprotect+ を保持し、かつ終焉機構の保護対象外（isTerminusTestTarget=true）として
     * 正常に初期化されていることを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void dummyZombieAttributesAndEprotectSetup(GameTestHelper helper) {
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        helper.assertTrue(dummy.isTerminusTestTarget(), "dummy zombie must be marked as isTerminusTestTarget");
        helper.assertTrue(dummy.getEprotect() != null, "dummy zombie eprotect is null");
        helper.assertTrue(dummy.getEprotect().isProtectionActive(), "dummy zombie eprotect is not active");
        helper.assertTrue(dummy.getHealth() > 0.0F, "dummy zombie health is not positive");
        helper.assertTrue(dummy.getMaxHealth() >= 100.0F, "dummy zombie max health is less than 100");

        helper.succeed();
    }

    /**
     * 終焉執行（TerminusEngine.executeTerminus）をダミーゾンビに直接叩き込み、
     * 終焉機構のホワイトリストによる早期リターンをすり抜けた上で、
     * Eprotect+ の防御機構が即死・解体・消去を遮断して生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsTerminusEngine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        // 終焉執行（ホワイトリストで弾かれず、パイプラインが実行される）
        TerminusEngine.executeTerminus(level, attacker, dummy);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after Terminus");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after Terminus");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after Terminus");
            helper.succeed();
        });
    }

    /**
     * オーバーロード超絶抹殺（Overload.kill）をダミーゾンビに執行し、
     * ホワイトリスト除外下でも Eprotect+ により生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsOverloadKill(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        Overload.kill(dummy, attacker);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after Overload kill");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after Overload kill");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after Overload kill");
            helper.succeed();
        });
    }

    /**
     * テスト用抹殺の棒（DebugKillStickItem.annihilate）の即死手段を叩き込み、
     * Eprotect の自己修復・復元サイクルによって生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsDebugKillStick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        DebugKillStickItem.annihilate(level, attacker, dummy);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after DebugKillStick");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after DebugKillStick");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after DebugKillStick");
            helper.assertFalse(com.automatavalkyries.annihilate.AnnihilationExecutionAgent.isAnnihilated(dummy),
                    "Dummy zombie must NOT be marked in ANNIHILATED lists after defense");
            helper.succeed();
        });
    }

    /**
     * エレノアに "terminus_test_target" タグを付与して終焉ホワイトリストスキップを解除し、
     * 真抹殺（TrueAnnihilationEngine.annihilateEntity）を執行しても Eprotect+ により防衛され
     * 生存かつブラックリスト漏れがないことを検証する。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void elenorTaggedTerminusTestTargetSurvivesTrueAnnihilation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        com.automatavalkyries.entity.ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        elenor.addTag("terminus_test_target");
        helper.assertTrue(elenor.isTerminusTestTarget(), "Elenor should be terminus test target after tag added");

        boolean result = com.automatavalkyries.annihilate.TrueAnnihilationEngine.annihilateEntity(level, attacker, elenor);
        helper.assertFalse(result, "TrueAnnihilationEngine should return false when defended by Eprotect+");

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(elenor.isAlive(), "Elenor should be alive after TrueAnnihilation");
            helper.assertFalse(elenor.isRemoved(), "Elenor should not be removed after TrueAnnihilation");
            helper.assertFalse(com.automatavalkyries.annihilate.AnnihilationExecutionAgent.isAnnihilated(elenor),
                    "Elenor must NOT be in ANNIHILATED lists after defense");
            elenor.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }
}
