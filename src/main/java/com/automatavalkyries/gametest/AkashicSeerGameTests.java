package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.automatavalkyries.entity.seer.AkashicSeerEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 事象観測姫アカシック（一般ボス。2026-10-26）: HP・第 2 形態・論理突破率・技の立方体が当たる・観測の立方体が敵を撃つ */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class AkashicSeerGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 200)
    public static void seerPhasesGaugeAndCubes(GameTestHelper helper) {
        AkashicSeerEntity seer = helper.spawn(ModEntities.AKASHIC_SEER.get(), 1, 2, 1);
        seer.setNoAi(true);
        helper.assertTrue(seer.getMaxHealth() == AkashicSeerEntity.MAX_HP, "max health " + seer.getMaxHealth());
        float g0 = seer.gauge();
        seer.hurt(helper.getLevel().damageSources().magic(), 10.0F);
        helper.assertTrue(seer.gauge() > g0, "the gauge did not rise when hit");
        seer.invulnerableTime = 0;
        seer.hurt(helper.getLevel().damageSources().magic(), AkashicSeerEntity.MAX_HP * 0.55F);
        Pig pig = helper.spawn(EntityType.PIG, 1, 2, 6);
        pig.setNoAi(true);
        float hp = pig.getHealth();
        AkashicCubeEntity.shoot(seer, seer.position().add(0, 1.0, 0), pig.position().add(0, 0.5, 0).subtract(seer.position().add(0, 1.0, 0)), 0.9F, 3.0F,
                AkashicCubeEntity.CYAN, 0.16F);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(seer.isPhase2(), "the seer did not enter phase 2");
            for (int i = 0; i < 30; i++) seer.observed(5.0F);
            helper.assertTrue(seer.gauge() >= 100.0F, "the gauge did not reach 100% in phase 2: " + seer.gauge());   // 第 1 形態は 60% まで
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pig.getHealth() < hp, "the seer's cube did not hit the pig");
            pig.discard();
            seer.discard();
            helper.succeed();
        });
    }

    /** 結晶槍（2026-10-26 に増やした技）: 相手への道に結晶の棘が突き出して当たる */
    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 100)
    public static void seerLanceHits(GameTestHelper helper) {
        AkashicSeerEntity seer = helper.spawn(ModEntities.AKASHIC_SEER.get(), 1, 2, 1);
        seer.setNoAi(true);
        Pig pig = helper.spawn(EntityType.PIG, 1, 2, 7);
        pig.setNoAi(true);
        float hp = pig.getHealth();
        seer.lance(pig);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(pig.getHealth() < hp, "the crystal lance did not hit the pig");
            pig.discard();
            seer.discard();
            helper.succeed();
        });
    }

    /** 観測の印（2026-10-26）: 印の付いた相手へのアカシックのダメージは 1.2 倍、印は時間で消える */
    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 300)
    public static void seerMarkBoostsDamage(GameTestHelper helper) {
        AkashicSeerEntity seer = helper.spawn(ModEntities.AKASHIC_SEER.get(), 1, 2, 1);
        seer.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        zombie.setNoAi(true);
        seer.mark(zombie);
        helper.assertTrue(seer.isMarked(zombie), "the zombie was not marked");
        float hp = zombie.getHealth();
        zombie.hurt(helper.getLevel().damageSources().mobAttack(seer), 5.0F);
        helper.assertTrue(hp - zombie.getHealth() > 5.5F, "the mark did not raise the damage: " + (hp - zombie.getHealth()));
        helper.runAfterDelay(AkashicSeerEntity.MARK_TICKS + 10, () -> {
            helper.assertFalse(seer.isMarked(zombie), "the mark did not wear off");
            zombie.discard();
            seer.discard();
            helper.succeed();
        });
    }

    /** 大技（2026-10-26「三次元の弾幕」）: 溜めの後、宙に留まって結晶の球・螺旋・輪・照準を重ね、終わると地面へ戻る */
    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 260)
    public static void seerRecordBarrage(GameTestHelper helper) {
        AkashicSeerEntity seer = helper.spawn(ModEntities.AKASHIC_SEER.get(), 1, 2, 1);
        seer.setNoAi(true);
        seer.record();
        AABB area = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))).inflate(30);
        helper.runAfterDelay(AkashicSeerEntity.CHARGE_TICKS + 4, () -> {
            helper.assertTrue(seer.bursting(), "the seer did not start the barrage");
            int n = helper.getLevel().getEntitiesOfClass(AkashicCubeEntity.class, area).size();
            helper.assertTrue(n >= 40, "the crystal sphere was not fired: " + n);
        });
        helper.runAfterDelay(AkashicSeerEntity.CHARGE_TICKS + AkashicSeerEntity.BURST_TICKS + 10, () -> {
            helper.assertFalse(seer.bursting(), "the barrage did not end");
            helper.assertFalse(seer.isNoGravity(), "the seer stayed weightless after the barrage");
            for (AkashicCubeEntity c : helper.getLevel().getEntitiesOfClass(AkashicCubeEntity.class, area)) c.discard();
            seer.discard();
            helper.succeed();
        });
    }

    /** 論理突破率による適応（2026-10-26）: 相手の攻撃力に応じて防具が、防具と HP に応じて攻撃の倍率が、論理突破率の分だけ上がる */
    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 100)
    public static void seerAdaptsToOpponent(GameTestHelper helper) {
        AkashicSeerEntity seer = helper.spawn(ModEntities.AKASHIC_SEER.get(), 1, 2, 1);
        seer.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
        zombie.setNoAi(true);
        seer.setTarget(zombie);
        helper.assertTrue(seer.damageOf(AkashicCubeEntity.CYAN, null).is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE), "cyan is not a physical projectile");   // 弾の色ごとの種類
        helper.assertTrue(seer.damageOf(AkashicCubeEntity.PINK, null).is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR), "pink magic does not bypass armor");
        helper.assertTrue(seer.damageOf(AkashicCubeEntity.VIOLET, null).is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD), "violet imaginary does not bypass shields");
        double baseArmor = seer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        seer.observed(100.0F);                                                                 // 第 1 形態は 60% まで
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(seer.attackMultiplier() > 1.0F, "the attack multiplier did not rise: " + seer.attackMultiplier());
            helper.assertTrue(seer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) > baseArmor,
                    "the armor did not rise against the zombie's attack");
            helper.assertTrue(seer.attackMultiplier() <= AkashicSeerEntity.ADAPT_ATTACK_MAX, "the attack multiplier went over the cap");
            zombie.discard();
            seer.discard();
            helper.succeed();
        });
    }

    /** 観測の立方体: ホットバーにあると、近くの敵のモブへ立方体を撃つ（プレイヤーの弾） */
    @GameTest(template = TEMPLATE, batch = "akashic_seer", timeoutTicks = 100)
    public static void cubesItemShootsEnemies(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        player.getInventory().setItem(3, new ItemStack(ModItems.AKASHIC_CUBES.get()));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 6);
        zombie.setNoAi(true);
        player.tickCount = com.automatavalkyries.item.AkashicCubesItem.INTERVAL * 4;
        com.automatavalkyries.item.AkashicCubesItem.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        AABB area = new AABB(helper.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0))).inflate(12);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(AkashicCubeEntity.class, area).isEmpty(), "no cube was fired at the zombie");
        float hp = zombie.getHealth();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(zombie.getHealth() < hp, "the cube did not hit the zombie");
            zombie.discard();
            helper.succeed();
        });
    }
}
