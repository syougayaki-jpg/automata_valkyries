package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.MementoBitEntity;
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

    /** 呪い: 1 つで最大 HP 95%、重ねても 40% より下にならない、解くと元に戻る */
    @GameTest(template = TEMPLATE, batch = "memento_mori", timeoutTicks = 20)
    public static void mementoCurseCutsMaxHealth(GameTestHelper helper) {
        var pig = helper.spawn(EntityType.PIG, 2, 2, 2);
        pig.setNoAi(true);
        float max = pig.getMaxHealth();
        MementoCurse.add(pig, 1);
        helper.assertTrue(Math.abs(pig.getMaxHealth() - max * 0.95F) < 1.0e-3, "one stack did not cut max HP to 95%: " + pig.getMaxHealth());
        MementoCurse.add(pig, 30);
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
        helper.assertTrue(MementoCurse.stacks(pig) == 1, "the bit hit did not add one curse stack: " + MementoCurse.stacks(pig));
        MementoCurse.clear(pig);
        mori.getEprotect().triggerDespawn(); // 守られたボスは discard では消えないので、正規の退場で片付ける
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
