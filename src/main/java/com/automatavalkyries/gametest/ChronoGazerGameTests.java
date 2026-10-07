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

    /** 上位の 2 体（2026-10-07）: 兵士は HP 100、仮面の観測機は溜めてから光線を当てる */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer", timeoutTicks = 260)
    public static void maskDroneBeamHitsItsTarget(GameTestHelper helper) {
        com.automatavalkyries.entity.chrono.TrooperEntity trooper = helper.spawn(ModEntities.CHRONO_TROOPER.get(), 1, 2, 1);
        helper.assertTrue(trooper.getMaxHealth() == 100.0F, "the trooper's max health is not 100");
        trooper.discard();
        com.automatavalkyries.entity.chrono.MaskDroneEntity mask = helper.spawn(ModEntities.CHRONO_MASK_DRONE.get(), 2, 4, 2);
        net.minecraft.world.entity.animal.IronGolem golem = helper.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, 6, 2, 6);
        golem.setNoAi(true);
        mask.setTarget(golem);
        helper.succeedWhen(() -> {
            mask.setTarget(golem);
            helper.assertTrue(golem.getHealth() < golem.getMaxHealth(), "the mask drone's beam has not hit yet");
            mask.discard();
            golem.discard();
        });
    }

    /** ルクシオン（AEC 級、2026-10-07）: HP 5,000、/kill では倒れない、被弾の上限は無いので大きな普通の攻撃はそのまま通る */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer", timeoutTicks = 60)
    public static void luxionRefusesKillButTakesBigHits(GameTestHelper helper) {
        com.automatavalkyries.entity.chrono.LuxionEntity lux = helper.spawn(ModEntities.LUXION.get(), 2, 2, 2);
        lux.setNoAi(true);
        helper.assertTrue(lux.getMaxHealth() == 5000.0F && lux.getHealth() == 5000.0F, "Luxion's HP is not 5000 (max health cap): " + lux.getMaxHealth());
        lux.kill();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(lux.isAlive() && !lux.isRemoved() && lux.getHealth() == lux.getMaxHealth(), "/kill hurt or removed Luxion: " + lux.getHealth());
            lux.hurt(helper.getLevel().damageSources().generic(), 3000.0F);
            helper.assertTrue(lux.getHealth() < 2500.0F, "a big normal hit was capped: " + lux.getHealth()); // 3,000（防具で少し減る）
            lux.hurt(helper.getLevel().damageSources().generic(), 6000.0F); // 残りを超える普通の一撃で倒れる（2026-10-08、倒れなかった不具合）
            helper.assertTrue(lux.isDeadOrDying(), "a lethal normal hit did not defeat Luxion: " + lux.getHealth());
            helper.succeed();
        });
    }

    /** ルクシオンをプレイヤーが倒すと、ボス素材を落とす（2026-10-08「アイテム落ちないです」） */
    @GameTest(template = TEMPLATE, batch = "chrono_gazer", timeoutTicks = 120)
    public static void luxionDropsBossMaterials(GameTestHelper helper) {
        com.automatavalkyries.entity.chrono.LuxionEntity lux = helper.spawn(ModEntities.LUXION.get(), 2, 2, 2);
        lux.setNoAi(true);
        var player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModWeapons.LUMINOUS_REQUIEM.get()));
        // ルミナス・レクイエムの星の鎮魂歌と同じダメージ（2026-10-08「クリエイティブでルミナスレクイエムで倒すと落ちない」）
        lux.hurt(com.automatavalkyries.weapon.WeaponDamage.source(player, com.automatavalkyries.weapon.WeaponDamage.ORBITAL, player, player), (float) Integer.MAX_VALUE);
        com.automatavalkyries.weapon.Overload.overkill(lux, player);
        helper.succeedWhen(() -> {
            var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, lux.getBoundingBox().inflate(6.0));
            helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(com.automatavalkyries.registry.ModBossDrops.LUXION_CORE.get())),
                    "no luxion core dropped (dead=" + lux.isDeadOrDying() + ", removed=" + lux.isRemoved() + ", items=" + items.size() + ")");
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
