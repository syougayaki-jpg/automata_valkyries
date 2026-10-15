package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.MementoBitEntity;
import com.automatavalkyries.entity.chrono.MementoBulletEntity;
import com.automatavalkyries.entity.chrono.MementoCurse;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.trial.Trial;
import com.automatavalkyries.trial.TrialManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** AEC 級英雄機メメントモリ（2026-10-10）: 死蝶の呪い（最大 HP 5% ずつ・下限 40%・倒すと戻る）、蝶ビットの当たりと壊れ方、回想の試練で本物が出る */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class MementoMoriGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "memento_test"));
        Vec3 pos = helper.absoluteVec(relative);
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return player;
    }

    /** 世界と同じように Tick を数えてから 1 Tick 回す（tickCount は世界の側が進める） */
    private static void step(net.minecraft.world.entity.Entity e) {
        e.tickCount++;
        e.tick();
    }

    /** 呪い: 蝶ビット 1 匹分で最大 HP 95%、重ねても 40% より下にならない、解くと元に戻る */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoCurseCutsMaxHealth(GameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoAi(true);
        float max = pig.getMaxHealth();
        MementoCurse.add(pig, MementoCurse.BIT);
        helper.assertTrue(Math.abs(pig.getMaxHealth() - max * 0.95F) < 1.0e-3, "one bit's stacks did not cut max HP to 95%: " + pig.getMaxHealth());
        MementoCurse.add(pig, 200);
        helper.assertTrue(Math.abs(pig.getMaxHealth() - max * 0.40F) < 1.0e-3, "stacks went below the 40% floor: " + pig.getMaxHealth());
        helper.assertTrue(pig.getHealth() <= pig.getMaxHealth(), "health was left above the cut max HP");
        MementoCurse.clear(pig);
        helper.assertTrue(pig.getMaxHealth() == max, "clearing did not restore max HP: " + pig.getMaxHealth());
        helper.succeed();
    }

    /** 蝶ビット: 当たると呪いが 1 つ付いて消える。攻撃されると一撃で壊れる */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoBitCursesAndBreaks(GameTestHelper helper) {
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoAi(true);
        var level = helper.getLevel();
        MementoBitEntity onPig = MementoBitEntity.swarm(level, mori, pig, pig.position().add(0, 0.4, 0), Vec3.ZERO, 1.0F, false);
        MementoBitEntity other = MementoBitEntity.swarm(level, mori, pig, helper.absoluteVec(new Vec3(5, 6, 5)), Vec3.ZERO, 1.0F, false);
        helper.assertTrue(other.hurt(level.damageSources().generic(), 1.0F), "the bit did not take the hit");
        helper.assertTrue(other.isRemoved(), "a hit bit was not destroyed");
        // 試験の区画は並びによってエンティティが動き出すまで時間がかかるので、ビットの 1 Tick を直に回して確かめる
        onPig.tick();
        helper.assertTrue(onPig.isRemoved(), "the bit touching the pig did not hit (bit " + onPig.position() + ", pig " + pig.position() + ")");
        helper.assertTrue(MementoCurse.stacks(pig) == MementoCurse.BIT, "the bit hit did not add 5% of curse: " + MementoCurse.stacks(pig));
        MementoCurse.clear(pig);
        mori.getEprotect().triggerDespawn(); // 守られたボスは discard では消えないので、正規の退場で片付ける
        helper.succeed();
    }

    /** 弾（2026-10-15）: 当たるのは芯だけ（見た目より小さい）。芯が重なれば当たって消え、すぐ横を通るだけなら当たらない */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoBulletHitsOnlyCore(GameTestHelper helper) {
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoAi(true);
        var level = helper.getLevel();
        float hp = pig.getHealth();
        double edge = pig.getBbWidth() * 0.5;
        MementoBulletEntity beside = MementoBulletEntity.shot(pig.position().add(edge + 0.3, 0.4, 0), Vec3.ZERO).fire(level, mori);
        MementoBulletEntity on = MementoBulletEntity.shot(pig.position().add(0, 0.4, 0), Vec3.ZERO).fire(level, mori);
        helper.assertTrue(beside != null && on != null, "the two bullets were not fired");
        // 区画によっては世界の側でエンティティを探せないので、当たりの箱が重なるかを直に確かめる
        helper.assertTrue(on.core().intersects(pig.getBoundingBox()), "the bullet on the pig does not overlap it");
        helper.assertFalse(beside.core().intersects(pig.getBoundingBox()), "the bullet beside the pig overlaps it (core " + beside.core() + ")");
        helper.assertTrue(on.strike(pig) && pig.getHealth() < hp, "the bullet on the pig did no damage");
        var bullets = java.util.List.of(beside, on);
        bullets.forEach(net.minecraft.world.entity.Entity::discard);
        mori.getEprotect().triggerDespawn();
        pig.discard(); // ほかの試験の区画へ迷い込まないように
        helper.succeed();
    }

    /** 弾（2026-10-15 ユーザー指定「防御無視と最大体力削り」）: 防具値 30 と耐性 V でも通り、当たると最大 HP を 1% 削る */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoBulletIgnoresDefenceAndCurses(GameTestHelper helper) {
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        var target = helper.spawn(EntityType.PIG, 2, 2, 2); // 敵対の生き物は難易度によって消されるので豚にする
        target.setNoAi(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(30.0); // 装備の防具は Tick を待たないと効かないので、防具値を直に
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 200, 4));
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0); // 続けて当てても倒れないように
        target.setHealth(200.0F);
        float hp = target.getHealth(), max = target.getMaxHealth();
        MementoBulletEntity b = MementoBulletEntity.shot(target.position().add(0, 0.4, 0), Vec3.ZERO).fire(helper.getLevel(), mori);
        helper.assertTrue(b != null, "the bullet was not fired");
        // 区画によっては世界の側でエンティティを探せないので、当てる所だけ直に（当たりの箱の大きさは mementoBulletHitsOnlyCore が確かめる）
        helper.assertTrue(b.strike(target), "the bullet did not land through armour 30 and Resistance V");
        helper.assertTrue(target.getHealth() < hp, "the bullet did no damage through armour 30 and Resistance V: " + target.getHealth());
        helper.assertTrue(MementoCurse.stacks(target) == MementoCurse.BULLET && Math.abs(target.getMaxHealth() - max * 0.99F) < 1.0e-3,
                "the bullet hit did not cut max HP by 1%: " + target.getMaxHealth());
        // 終葬の弾（2026-10-15 ユーザー指定「死蝶終葬『メメント・モリ』だけは無敵無視」）: ふつうの弾は被弾後の無敵時間に止められ、終葬の弾は続けて当てても入る
        // （試験を増やすと区画の並びがずれて、世界の側でエンティティが動かない区画にほかの試験が入るので、同じ試験の中で確かめる）
        MementoBulletEntity plain = MementoBulletEntity.shot(target.position(), Vec3.ZERO).fire(helper.getLevel(), mori);
        MementoBulletEntity f1 = MementoBulletEntity.shot(target.position(), Vec3.ZERO).finale().fire(helper.getLevel(), mori);
        MementoBulletEntity f2 = MementoBulletEntity.shot(target.position(), Vec3.ZERO).finale().fire(helper.getLevel(), mori);
        helper.assertTrue(plain != null && f1 != null && f2 != null, "the cooldown test bullets were not fired");
        helper.assertFalse(plain.strike(target), "a plain bullet landed during the hurt cooldown");
        float before = target.getHealth();
        helper.assertTrue(f1.strike(target) && f2.strike(target), "a finale bullet was stopped by the hurt cooldown");
        helper.assertTrue(target.getHealth() < before, "the finale bullets did no damage during the cooldown");
        MementoCurse.clear(target);
        for (MementoBulletEntity e : new MementoBulletEntity[]{b, plain, f1, f2}) e.discard();
        mori.getEprotect().triggerDespawn();
        target.discard(); // ほかの試験の区画へ迷い込まないように
        helper.succeed();
    }

    /** 弾（2026-10-15）: 止まって待つ間は動かず、待ち終わりに相手へ向き直して飛ぶ。動く相手は先を読む */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoBulletWaitsThenReaims(GameTestHelper helper) {
        // 動きを読む狙い（2026-10-15「AI 強化」）: 横へ走る相手には弾が届く時にいる所を、止まった相手はそのまま狙う（試験を増やすと区画の並びがずれるので、ここで確かめる）
        Vec3 leadFrom = new Vec3(0, 10, 0), aim = new Vec3(0, 1, 13);
        Vec3 still = com.automatavalkyries.entity.chrono.MementoTactics.leadPoint(aim, Vec3.ZERO, leadFrom, 0.5, 1.0);
        helper.assertTrue(still.distanceTo(aim) < 1.0e-6, "a still target was not aimed at directly: " + still);
        Vec3 led = com.automatavalkyries.entity.chrono.MementoTactics.leadPoint(aim, new Vec3(0.2, 0, 0), leadFrom, 0.5, 1.0);
        double time = led.distanceTo(leadFrom) / 0.5;
        helper.assertTrue(Math.abs(led.x - 0.2 * time) < 0.5 && led.x > 4.0, "a running target was not led by its travel time: " + led + " (flight " + time + " ticks)");
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoAi(true);
        var level = helper.getLevel();
        Vec3 start = pig.position().add(3, 4, 0);
        MementoBulletEntity b = MementoBulletEntity.shot(start, Vec3.ZERO).delay(3).reaim(pig, 0.5).kind(MementoBulletEntity.LARGE).fire(level, mori);
        helper.assertTrue(b != null, "the bullet was not fired");
        for (int i = 0; i < 3; i++) step(b);
        helper.assertTrue(b.position().distanceTo(start) < 1.0e-6, "the bullet moved while waiting: " + b.position());
        step(b);
        Vec3 moved = b.position().subtract(start);
        Vec3 toPig = pig.position().add(0, pig.getBbHeight() * 0.5, 0).subtract(start).normalize();
        helper.assertTrue(moved.length() > 0.4 && moved.normalize().dot(toPig) > 0.99, "the bullet did not turn toward the pig after waiting: " + moved);
        b.discard();
        mori.getEprotect().triggerDespawn();
        pig.discard(); // ほかの試験の区画へ迷い込まないように
        helper.succeed();
    }

    /** 終葬の時計の針（2026-10-15）: 針の弾は中心から同じ距離を保ったまま回る（曲げる向きを間違えると外へ飛んでいく） */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoClockHandOrbits(GameTestHelper helper) {
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        Vec3 center = helper.absoluteVec(new Vec3(4, 5, 4));
        for (double omega : new double[]{0.028, -0.018}) {
            MementoBulletEntity b = com.automatavalkyries.entity.chrono.MementoFinale.orbit(center, 0.0, 2.5, omega).fire(helper.getLevel(), mori); // 試験の区画の中に収まる半径
            helper.assertTrue(b != null, "the hand bullet was not fired");
            for (int i = 0; i < 60; i++) step(b);
            helper.assertFalse(b.isRemoved(), "the hand bullet was removed while orbiting (omega " + omega + ", at " + b.position() + ")");
            double r = b.position().subtract(center).multiply(1, 0, 1).length();
            double ang = Math.atan2(b.getZ() - center.z, b.getX() - center.x);
            helper.assertTrue(Math.abs(r - 2.5) < 0.2, "the hand bullet left its circle (omega " + omega + ", radius " + r + ")");
            helper.assertTrue(Math.abs(ang - omega * 60) < 0.15, "the hand bullet turned the wrong way (omega " + omega + ", angle " + ang + ")");
            b.discard();
        }
        mori.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /** 倒すと、まわりの呪いが解ける */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoDefeatReleasesCurse(GameTestHelper helper) {
        MementoMoriEntity mori = helper.spawn(ModEntities.MEMENTO_MORI.get(), 6, 8, 6);
        mori.setNoAi(true);
        mori.portraitState(false); // 現れる演出（攻撃を受けない）を飛ばす
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2); // 世界にいる生き物（呪いを解くときに世界から探す）
        pig.setNoAi(true);
        float max = pig.getMaxHealth();
        MementoCurse.add(pig, 4);
        helper.assertTrue(pig.getMaxHealth() < max, "the pig was not cursed");
        mori.hurt(helper.getLevel().damageSources().playerAttack(player(helper, new Vec3(1, 2, 1))), 1.0e6F);
        helper.assertTrue(mori.isDefeatedSynced(), "Memento Mori was not defeated by a huge hit");
        helper.assertTrue(pig.getMaxHealth() == max, "defeating Memento Mori did not lift the curse: " + pig.getMaxHealth());
        // 撃破の演出（らせんを描いて落ちる）が終わって消える（次の試験が同じ場所を使うので、残すと邪魔になる）
        // 区画によっては世界の側でエンティティが動き続けないので、撃破の演出の Tick を直に回す
        for (int i = 0; i < MementoMoriEntity.DEFEAT_TICKS + 10 && !mori.isRemoved(); i++) mori.tick();
        helper.assertTrue(mori.isRemoved(), "the defeated Memento Mori was not removed after its defeat motion (deathTime " + mori.deathTime + ")");
        helper.succeed();
    }

    /** 死蝶の舞踏は本物のメメントモリ */
    @GameTest(template = TEMPLATE, batch = "memento_mori_trial", timeoutTicks = 400)
    public static void mementoTrialSpawnsMementoMori(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        int index = Trial.indexOf("memento_mori");
        helper.assertTrue(index >= 0 && TrialManager.start(player, Trial.get(index)), "the Memento Mori trial did not start");
        helper.runAfterDelay(TrialManager.COUNTDOWN + 60, () -> {
            var boss = TrialManager.boss(player);
            helper.assertTrue(boss instanceof MementoMoriEntity, "the Memento Mori trial did not spawn Memento Mori: " + boss);
            helper.assertFalse(((MementoMoriEntity) boss).shouldBeSaved(), "the trial boss would be saved");
            TrialManager.abandon(player);
        });
        helper.runAfterDelay(TrialManager.COUNTDOWN + 60 + TrialManager.AFTER_FAIL + 10, () -> {
            helper.assertFalse(TrialManager.inTrial(player), "trial did not end after abandoning");
            helper.succeed();
        });
    }
}
