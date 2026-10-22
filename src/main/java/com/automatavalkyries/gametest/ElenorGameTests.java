package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorArts;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** エレノアの 3 フェーズの戦い方（2026-10-22） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ElenorGameTests {

    private static final String TEMPLATE = "empty";

    /** 第 2 は HP 60% 以下、第 3 は 30% 以下。最果てのエレノアは技を使わない */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void phasesFollowHealth(GameTestHelper helper) {
        helper.assertTrue(ElenorArts.phaseFor(1.0F) == 1 && ElenorArts.phaseFor(0.61F) == 1, "phase 1 range");
        helper.assertTrue(ElenorArts.phaseFor(0.6F) == 2 && ElenorArts.phaseFor(0.31F) == 2, "phase 2 range");
        helper.assertTrue(ElenorArts.phaseFor(0.3F) == 3 && ElenorArts.phaseFor(0.0F) == 3, "phase 3 range");
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(5, 2, 5));
        helper.assertTrue(elenor.arts() != null, "Elenor has no arts");
        helper.assertTrue(farthest.arts() == null, "Farthest Elenor must not use the phase arts");
        elenor.retreat();
        farthest.retreat();
        helper.succeed();
    }

    /** 標的（ダメージを受けないモックのプレイヤー）に向けて技を回し続けても落ちず、エレノアは生きている */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 400)
    public static void artsRunWithoutErrors(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        elenor.setTarget(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (elenor.arts() != null && ticks[0]++ < 300) {
                elenor.arts().tick(player);
                elenor.arts().tickProjectiles();
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(elenor.isAlive(), "Elenor died while running arts");
            elenor.retreat();
            helper.succeed();
        });
    }
}
