package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.TrueAnnihilationEngine;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.entity.test.ExperimentalFlashfurEntity;
import com.automatavalkyries.entity.test.ExperimentalWitherzillaEntity;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.registry.ModEntities;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 真抹殺の棒（TrueKillStick）および Eprotect+ Java エージェント機構の GameTest。
 * <p>
 * 試験的ウィザージラ（生命維持フラグ・不死）および試験的フラッシュファー（StackChecker・healthDelta）に対する
 * Eprotect+ による特権迂回・不死剥奪・完全抹殺を検証する。
 * </p>
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class TrueKillStickGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * 試験的ウィザージラ:
     * 通常攻撃・/kill・discard() すべてを witherzillaLife により拒絶・全快するが、
     * 真抹殺の棒（TrueKillStick）の Eprotect+ Java エージェント機構により witherzillaLife を剥奪され完全消滅すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void witherzillaResistsNormalAttacksAndDiesToTrueKillStick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExperimentalWitherzillaEntity witherzilla = helper.spawnWithNoFreeWill(ModEntities.EXPERIMENTAL_WITHERZILLA.get(), new BlockPos(2, 2, 2));
        UUID uuid = witherzilla.getUUID();
        int id = witherzilla.getId();

        // 1. 通常攻撃・/kill・discard の無力化を確認
        helper.assertTrue(witherzilla.getWitherzillaLife(), "witherzillaLife was not true initially");
        witherzilla.kill();
        helper.assertTrue(witherzilla.isAlive() && witherzilla.getHealth() > 0.0F, "Witherzilla was killed by normal kill()");

        witherzilla.hurt(level.damageSources().generic(), Float.MAX_VALUE);
        helper.assertTrue(witherzilla.isAlive() && witherzilla.getHealth() >= witherzilla.getMaxHealth(), "Witherzilla took damage");

        witherzilla.discard();
        helper.assertFalse(witherzilla.isRemoved(), "Witherzilla was removed by discard()");

        // 2. 真抹殺の棒（TrueKillStick）による真抹殺の執行
        Player player = helper.makeMockPlayer();
        boolean success = TrueAnnihilationEngine.annihilateEntity(level, player, witherzilla);
        helper.assertTrue(success, "TrueAnnihilationEngine returned false");

        // 3. 検証: witherzillaLife が剥奪され、ワールドから完全に消滅していること
        helper.assertFalse(witherzilla.getWitherzillaLife(), "witherzillaLife was not stripped by TrueAnnihilationEngine");
        helper.assertTrue(witherzilla.isRemoved(), "Witherzilla was not marked as removed");
        helper.assertTrue(level.getEntity(uuid) == null, "Witherzilla still present in level entity lookup");

        ServerLevelTickListAccessor tickLists = (ServerLevelTickListAccessor) level;
        helper.assertFalse(tickLists.automata_valkyries$getEntityTickList().contains(witherzilla), "Witherzilla still in tick list");

        Int2ObjectMap<?> entityMap = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap();
        helper.assertFalse(entityMap.containsKey(id), "Witherzilla still in chunkMap entityMap");

        // 4. 再参加が最優先で拒絶されること
        EntityJoinLevelEvent joinEvent = new EntityJoinLevelEvent(witherzilla, level);
        MinecraftForge.EVENT_BUS.post(joinEvent);
        helper.assertTrue(joinEvent.isCanceled(), "EntityJoinLevelEvent was not canceled for annihilated Witherzilla");

        helper.succeed();
    }

    /**
     * 試験的フラッシュファー:
     * StackChecker により外部からの攻撃・削除を拒絶するが、
     * 真抹殺の棒の特権空間（Eprotect+ Java エージェント）により StackChecker を迂回・抹殺されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void flashfurResistsStackCheckerAndDiesToTrueKillStick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExperimentalFlashfurEntity flashfur = helper.spawnWithNoFreeWill(ModEntities.EXPERIMENTAL_FLASHFUR.get(), new BlockPos(3, 2, 3));
        UUID uuid = flashfur.getUUID();
        int id = flashfur.getId();

        // 1. StackChecker による通常攻撃のブロックを確認
        helper.assertFalse(flashfur.checkStackAuthorized(), "StackChecker should not authorize normal caller");
        flashfur.hurt(level.damageSources().generic(), 500.0F);
        helper.assertTrue(flashfur.getHealth() >= flashfur.getMaxHealth(), "Flashfur took damage without stack authorization");

        flashfur.discard();
        helper.assertFalse(flashfur.isRemoved(), "Flashfur was discarded without stack authorization");

        // 2. 真抹殺の棒の執行
        Player player = helper.makeMockPlayer();
        boolean success = TrueAnnihilationEngine.annihilateEntity(level, player, flashfur);
        helper.assertTrue(success, "TrueAnnihilationEngine returned false for Flashfur");

        // 3. 検証: 完全消滅
        helper.assertTrue(flashfur.isRemoved(), "Flashfur is not removed");
        helper.assertTrue(level.getEntity(uuid) == null, "Flashfur still in level");

        ServerLevelTickListAccessor tickLists = (ServerLevelTickListAccessor) level;
        helper.assertFalse(tickLists.automata_valkyries$getEntityTickList().contains(flashfur), "Flashfur still in tick list");

        // 4. 再参加拒否
        EntityJoinLevelEvent joinEvent = new EntityJoinLevelEvent(flashfur, level);
        MinecraftForge.EVENT_BUS.post(joinEvent);
        helper.assertTrue(joinEvent.isCanceled(), "EntityJoinLevelEvent was not canceled for Flashfur");

        helper.succeed();
    }

    /**
     * Eprotect+ による外部干渉の検査・迂回・兵姫（エレノア）の保護確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eprotectPlusBypassesInterferenceAndProtectsElenor(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        // 外部干渉をシミュレート
        EprotectPlusLink.recordInterference(elenor.getClass().getName(), "TestMockMod", "fakeHook", "Simulated interference hook");
        EprotectPlusLink.inspectAndBypassInterference(elenor);

        var events = EprotectPlusLink.getEvents();
        helper.assertFalse(events.isEmpty(), "EprotectPlusLink did not record any events");

        helper.assertTrue(elenor.isAlive(), "Elenor is dead");
        helper.assertTrue(elenor.getEprotect().isProtectionActive(), "Elenor protection was deactivated");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 真抹殺の棒: 範囲抹殺（空中右クリック）で試験的ウィザージラと試験的フラッシュファーが一括消滅すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void trueKillStickAoEEradicatesExperimentalBosses(GameTestHelper helper) {
        com.automatavalkyries.annihilate.SpawnBanZone.clear();
        ServerLevel level = helper.getLevel();
        ExperimentalWitherzillaEntity w = helper.spawnWithNoFreeWill(ModEntities.EXPERIMENTAL_WITHERZILLA.get(), new BlockPos(2, 2, 2));
        ExperimentalFlashfurEntity f = helper.spawnWithNoFreeWill(ModEntities.EXPERIMENTAL_FLASHFUR.get(), new BlockPos(4, 2, 4));

        Player player = helper.makeMockPlayer();
        net.minecraft.world.phys.Vec3 center = helper.absolutePos(new BlockPos(3, 2, 3)).getCenter();
        player.setPos(center.x, center.y, center.z);

        int count = TrueAnnihilationEngine.annihilateAllInArea(level, player, 16.0);
        helper.assertTrue(count >= 2, "Expected at least 2 entities eradicated, got " + count);

        helper.assertTrue(w.isRemoved() && f.isRemoved(), "Bosses were not removed by AoE");
        helper.assertTrue(level.getEntity(w.getUUID()) == null, "Witherzilla still in level");
        helper.assertTrue(level.getEntity(f.getUUID()) == null, "Flashfur still in level");

        helper.succeed();
    }
}
