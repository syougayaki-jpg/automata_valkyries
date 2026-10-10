package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.GalliaFortEntity;
import com.automatavalkyries.registry.ModBossDrops;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.trial.Trial;
import com.automatavalkyries.trial.TrialManager;
import com.automatavalkyries.weapon.WeaponDamage;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** AEC 級英雄機ガリアフォート（2026-10-10）: 歪曲障壁の決まり・/kill を断る・ボス素材・回想の試練で本物が出る */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class GalliaFortGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "gallia_test"));
        Vec3 pos = helper.absoluteVec(relative);
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        return player;
    }

    /** 被弾して、次の一撃のために無敵時間を消す */
    private static void hit(GalliaFortEntity fort, DamageSource source, float amount) {
        fort.hurt(source, amount);
        fort.invulnerableTime = 0;
    }

    /**
     * 歪曲障壁（ユーザー指定）: 100 未満は本体に 1（防具で減らない）、100 以上は障壁を削って本体は無傷、
     * 重粒子収束砲（particle_beam）は障壁を無視して通る、障壁を削り切ると素通し。/kill では倒れない
     */
    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 60)
    public static void galliaBarrierRules(GameTestHelper helper) {
        GalliaFortEntity fort = helper.spawn(ModEntities.GALLIAFORT.get(), 3, 2, 3);
        fort.setNoAi(true);
        float max = GalliaFortEntity.MAX_HP;
        helper.assertTrue(fort.getMaxHealth() == max && fort.getHealth() == max, "Galliafort's HP is not " + max + ": " + fort.getMaxHealth());
        var generic = helper.getLevel().damageSources().generic();
        hit(fort, generic, 50.0F);
        helper.assertTrue(fort.getHealth() == max - 1.0F, "a hit under 100 was not turned into 1: " + fort.getHealth());
        hit(fort, generic, 500.0F);
        helper.assertTrue(fort.getHealth() == max - 1.0F, "a hit of 100 or more reached the body through the barrier: " + fort.getHealth());
        helper.assertTrue(fort.barrierHealth() == GalliaFortEntity.BARRIER_MAX - 500.0F, "the barrier did not lose 500: " + fort.barrierHealth());
        FakePlayer player = player(helper, new Vec3(0.5, 2, 0.5));
        float before = fort.getHealth();
        hit(fort, WeaponDamage.source(player, WeaponDamage.PARTICLE_BEAM, player, player), 20.0F);
        helper.assertTrue(fort.getHealth() < before - 1.0F, "the particle beam did not pass the barrier: " + fort.getHealth());
        helper.assertTrue(fort.barrierHealth() == GalliaFortEntity.BARRIER_MAX - 500.0F, "the particle beam wore down the barrier");
        hit(fort, generic, 5000.0F);
        helper.assertTrue(fort.isBarrierDown(), "the barrier did not break at 0");
        before = fort.getHealth();
        hit(fort, generic, 50.0F);
        helper.assertTrue(fort.getHealth() < before - 1.0F, "damage did not pass while the barrier was down: " + fort.getHealth());
        fort.kill();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(fort.isAlive() && !fort.isRemoved(), "/kill removed Galliafort");
            fort.getEprotect().releaseForCleanup();
            fort.discard();
            helper.succeed();
        });
    }

    /** プレイヤーが倒すとボス素材（ガリア重護装甲板）を落とす */
    /** 戦車の姿のロードキルの見極め: 突進の道に 2 体いれば 2、横にずれた 1 体だけなら 1（2026-10-10「戦車状態でも複数体巻き込めそうならロードキルを狙って」） */
    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 20)
    public static void galliaRoadkillCountsTargetsInLane(GameTestHelper helper) {
        GalliaFortEntity fort = helper.spawn(ModEntities.GALLIAFORT.get(), 3, 2, 3);
        fort.setNoAi(true);
        var near = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 3, 2, 10);
        helper.spawn(net.minecraft.world.entity.EntityType.PIG, 3, 2, 15);
        var aside = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 16, 2, 3);
        near.setNoAi(true);
        aside.setNoAi(true);
        int lane = fort.roadkillTargets(near), side = fort.roadkillTargets(aside);
        helper.assertTrue(lane == 2, "two pigs in the lane were counted as " + lane);
        helper.assertTrue(side == 1, "a lone pig to the side was counted as " + side);
        helper.succeed();
    }

    /** 対空の見極め: 地面の上は空ではなく、足元から 3 ブロック下まで地面が無ければ空（2026-10-10「対空には副砲と主砲と弱めに追尾するミサイルで」） */
    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 20)
    public static void galliaTellsAirborneTargets(GameTestHelper helper) {
        var ground = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 2, 2, 2);
        var air = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 4, 10, 4);
        ground.setNoAi(true);
        air.setNoAi(true);
        helper.assertTrue(!GalliaFortEntity.airborne(ground), "a pig on the ground was taken as airborne");
        helper.assertTrue(GalliaFortEntity.airborne(air), "a pig 8 blocks up was not taken as airborne");
        helper.succeed();
    }

    /**
     * 強化した兵装の一撃は、強化の倍率を入れた大きさで障壁を貫くかを決める。100 未満のままなら本体に 1（倍率で 1 より大きくしない）
     * （2026-10-10「攻撃力 100 以下の武器をカスタムで強化したのにダメージが入らない」）
     */
    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 20)
    public static void galliaBarrierCountsUpgradedDamage(GameTestHelper helper) {
        GalliaFortEntity fort = helper.spawn(ModEntities.GALLIAFORT.get(), 3, 2, 3);
        fort.setNoAi(true);
        FakePlayer player = player(helper, new Vec3(0.5, 2, 0.5));
        net.minecraft.world.item.ItemStack sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        com.automatavalkyries.weapon.WeaponUpgrades.setLevel(sword, com.automatavalkyries.weapon.WeaponUpgrades.Stat.DAMAGE, 10); // 攻撃力 2 倍
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
        DamageSource attack = helper.getLevel().damageSources().playerAttack(player);
        float max = GalliaFortEntity.MAX_HP, barrier = fort.barrierHealth();
        hit(fort, attack, 60.0F); // 60 × 2 = 120: 障壁を 120 削り、本体は無傷
        helper.assertTrue(fort.barrierHealth() == barrier - 120.0F, "an upgraded 60-hit did not drain 120 from the barrier: " + (barrier - fort.barrierHealth()));
        helper.assertTrue(fort.getHealth() == max, "an upgraded hit that pierced the barrier hurt the body: " + fort.getHealth());
        hit(fort, attack, 30.0F); // 30 × 2 = 60: 100 未満なので本体にちょうど 1
        helper.assertTrue(fort.getHealth() == max - 1.0F, "an upgraded hit under 100 did not deal exactly 1: " + (max - fort.getHealth()));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 160)
    public static void galliaDropsBossMaterials(GameTestHelper helper) {
        GalliaFortEntity fort = helper.spawn(ModEntities.GALLIAFORT.get(), 3, 2, 3);
        fort.setNoAi(true);
        var player = helper.makeMockPlayer();
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        hit(fort, source, 1.0e6F); // 障壁を砕く
        hit(fort, source, 1.0e6F); // 素通しの間に倒す
        helper.succeedWhen(() -> {
            var items = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fort.getBoundingBox().inflate(8.0));
            helper.assertTrue(items.stream().anyMatch(i -> i.getItem().is(ModBossDrops.GALLIA_PLATE.get())),
                    "no gallia plate dropped (dead=" + fort.isDeadOrDying() + ", removed=" + fort.isRemoved() + ", items=" + items.size() + ")");
        });
    }

    /** HP 50% で人型へ変形（2026-10-10）: 変形の間は攻撃を受けず、終わると人型になって当たり判定が高く（10 ブロック）なる */
    @GameTest(template = TEMPLATE, batch = "galliafort", timeoutTicks = 200)
    public static void galliaTransformsIntoHumanoid(GameTestHelper helper) {
        GalliaFortEntity fort = helper.spawn(ModEntities.GALLIAFORT.get(), 3, 2, 3);
        fort.setNoAi(true);
        var generic = helper.getLevel().damageSources().generic();
        hit(fort, generic, 1.0e5F); // 障壁を砕く
        hit(fort, generic, GalliaFortEntity.MAX_HP * 0.75F); // 防具で少し減って HP 4 割ほどへ
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(fort.isPhase2() && fort.stage() == GalliaFortEntity.STAGE_TRANSFORM, "Galliafort did not start transforming: " + fort.stage());
            float before = fort.getHealth();
            hit(fort, generic, 500.0F);
            helper.assertTrue(fort.getHealth() == before, "Galliafort took damage while transforming");
        });
        helper.runAfterDelay(GalliaFortEntity.TRANSFORM_TICKS + 6, () -> {
            helper.assertTrue(fort.isHumanoid(), "Galliafort is not humanoid after the transform");
            helper.assertTrue(fort.getBbHeight() == GalliaFortEntity.HUMAN_HEIGHT, "the humanoid hitbox is not tall: " + fort.getBbHeight());
            fort.getEprotect().releaseForCleanup();
            fort.discard();
            helper.succeed();
        });
    }

    /** 不落の城壁は本物のガリアフォート。近接のみなので、離れた所からの 100 以上の一撃は障壁も削れない */
    @GameTest(template = TEMPLATE, batch = "galliafort_trial", timeoutTicks = 400)
    public static void galliaTrialSpawnsGalliafort(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        int index = Trial.indexOf("galliafort");
        helper.assertTrue(index >= 0 && TrialManager.start(player, Trial.get(index)), "the Galliafort trial did not start");
        helper.runAfterDelay(TrialManager.COUNTDOWN + 60, () -> {
            var boss = TrialManager.boss(player);
            helper.assertTrue(boss instanceof GalliaFortEntity, "the Galliafort trial did not spawn Galliafort: " + boss);
            GalliaFortEntity fort = (GalliaFortEntity) boss;
            helper.assertFalse(fort.shouldBeSaved(), "the trial boss would be saved");
            float barrier = fort.barrierHealth();
            var arrow = helper.getLevel().damageSources().indirectMagic(fort, player); // 直接の当たり手がプレイヤーでない
            fort.hurt(arrow, 500.0F);
            helper.assertTrue(fort.barrierHealth() == barrier, "a ranged hit wore down the barrier in the melee-only trial");
            fort.invulnerableTime = 0;
            fort.hurt(helper.getLevel().damageSources().playerAttack(player), 500.0F);
            helper.assertTrue(fort.barrierHealth() == barrier - 500.0F, "a melee hit of 500 did not wear down the barrier: " + fort.barrierHealth());
            TrialManager.abandon(player);
        });
        helper.runAfterDelay(TrialManager.COUNTDOWN + 60 + TrialManager.AFTER_FAIL + 10, () -> {
            helper.assertFalse(TrialManager.inTrial(player), "trial did not end after abandoning");
            helper.succeed();
        });
    }
}
