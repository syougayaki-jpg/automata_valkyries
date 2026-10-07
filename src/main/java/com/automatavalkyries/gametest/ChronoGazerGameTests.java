package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.chrono.ChronoBolt;
import com.automatavalkyries.entity.chrono.HeavyWalkerEntity;
import com.automatavalkyries.entity.chrono.ScoutDroneEntity;
import com.automatavalkyries.entity.chrono.SpiderBotEntity;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** クロノゲイザーの汎用機（2026-10-07）: 出せる・数値・仲間の弾は仲間に当たらない・兵姫を狙う */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ChronoGazerGameTests {

    private static final String TEMPLATE = "empty";

    /** 3 種とも出せて、中盤の数値（HP 40 / 30 / 200）が入っている */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer")
    public static void chronoGazersSpawnWithStats(GameTestHelper helper) {
        ScoutDroneEntity drone = helper.spawn(ModEntities.CHRONO_SCOUT_DRONE.get(), 1, 3, 1);
        SpiderBotEntity spider = helper.spawn(ModEntities.CHRONO_SPIDER_BOT.get(), 3, 2, 1);
        HeavyWalkerEntity walker = helper.spawn(ModEntities.CHRONO_HEAVY_WALKER.get(), 1, 2, 3);
        helper.assertTrue(drone.getMaxHealth() == 40.0F && spider.getMaxHealth() == 30.0F && walker.getMaxHealth() == 200.0F, "unexpected max health");
        helper.assertTrue(walker.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.8, "the walker is not knockback resistant");
        helper.assertTrue(drone.isNoGravity(), "the drone does not fly");
        drone.discard();
        spider.discard();
        walker.discard();
        helper.succeed();
    }

    /** 仲間の弾（砲弾の炸裂も）は仲間に当たらない */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer", timeoutTicks = 60)
    public static void chronoBoltsSpareAllies(GameTestHelper helper) {
        HeavyWalkerEntity shooter = helper.spawn(ModEntities.CHRONO_HEAVY_WALKER.get(), 1, 2, 1);
        HeavyWalkerEntity ally = helper.spawn(ModEntities.CHRONO_HEAVY_WALKER.get(), 4, 2, 1);
        shooter.setNoAi(true);
        ally.setNoAi(true);
        ChronoBolt.fire(shooter, ally, 50.0F, 3.0F, 1.0F, 0.0F);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(ally.getHealth() >= ally.getMaxHealth(), "an ally was hurt by a chrono bolt: " + ally.getHealth());
            shooter.discard();
            ally.discard();
            helper.succeed();
        });
    }

    /** 兵姫を見つけると狙う */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer", timeoutTicks = 120)
    public static void chronoGazersTargetValkyries(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        v.setNoAi(true);
        HeavyWalkerEntity walker = helper.spawn(ModEntities.CHRONO_HEAVY_WALKER.get(), 6, 2, 6);
        helper.succeedWhen(() -> {
            helper.assertTrue(walker.getTarget() == v, "the walker has not targeted the valkyrie");
            walker.discard();
            v.releaseAndDiscard();
        });
    }
}
