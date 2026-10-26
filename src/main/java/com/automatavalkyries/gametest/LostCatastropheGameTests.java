package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 裏ボス 零号機（2026-10-20）: 詠唱の間は受けない・同調率 100% の兵姫がそばにいると同期過電圧ショックで崩れる・
 * 止められないと兵姫は機能停止して HP が戻る・FE を吸って障壁にする
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class LostCatastropheGameTests {

    private static final String TEMPLATE = "empty";

    static LostCatastropheEntity boss(GameTestHelper helper) {
        LostCatastropheEntity boss = LostCatastropheEntity.summonAt(helper.getLevel(), Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))));
        helper.assertTrue(boss != null, "the boss was not summoned");
        boss.skipIntro();
        boss.setNoAi(true);
        return boss;
    }

    /** 詠唱の間は一切受けず、同調率 100% の零号機の兵姫がそばに 3 秒いると詠唱が崩れて動けなくなり、受けるダメージが 2 倍になる */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe", timeoutTicks = 200)
    public static void chantIsBrokenBySyncOvervoltage(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        boss.startChant();
        helper.assertTrue(boss.chantLeft() > 0, "the chant did not start");
        helper.assertFalse(boss.hurt(helper.getLevel().damageSources().generic(), 100.0F), "the boss took damage while chanting");
        boss.kill();
        helper.assertTrue(boss.isAlive(), "/kill went through during the chant");
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 2, 2, 2);
        v.setBody(ValkyrieBody.Z);
        v.setNoAi(true);
        v.setEnergy(v.maxEnergy());
        helper.runAfterDelay(LostCatastropheEntity.SHOCK_TICKS + 20, () -> {
            helper.assertTrue(boss.chantLeft() == 0, "the chant was not broken (left " + boss.chantLeft() + ", shock " + boss.shockCharge() + ", valkyrie shutdown " + v.isShutdown()
                    + " sync " + v.getSyncRate() + " body " + v.getBody() + " alive " + v.isAlive() + " dist " + Math.sqrt(v.distanceToSqr(boss)) + " hacked " + LostCatastropheEntity.isHacked(v) + ")");
            helper.assertTrue(boss.stunLeft() > 0, "the boss was not stunned");
            float before = boss.getHealth() + boss.shield();                      // そばの兵姫から吸った障壁も先に削れる
            boss.invulnerableTime = 0;
            boss.hurt(helper.getLevel().damageSources().generic(), 50.0F);         // generic は防具を通す
            float taken = before - boss.getHealth() - boss.shield();
            helper.assertTrue(taken > 90.0F, "stunned damage was not doubled: " + taken);
            v.releaseAndDiscard();
            boss.discard();
            helper.succeed();
        });
    }

    /** 止められなかった詠唱: そばの兵姫は機能停止し、零号機の HP は満タンに戻る */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe_erase")   // 96 m のまわりの兵姫を止めるので、ほかの試験と別の組で
    public static void unbrokenChantErasesValkyries(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        boss.setHealth(1000.0F);
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 3);
        v.setNoAi(true);
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 2, 2, 3);           // ほかの生き物はバニラの kill（2026-10-26）
        var stand = helper.spawn(net.minecraft.world.entity.EntityType.ARMOR_STAND, 3, 2, 2);  // 防具立ては残す
        boss.eraseWorld(helper.getLevel());
        helper.assertTrue(v.isShutdown(), "the valkyrie was not shut down by the erasure");
        helper.assertTrue(v.isAlive() || v.isShutdown(), "the valkyrie was removed instead of being shut down");
        helper.assertFalse(pig.isAlive(), "the erasure did not kill the pig");
        helper.assertTrue(pig.getLastDamageSource() != null && pig.getLastDamageSource().is(LostCatastropheEntity.ZERO_ERASURE),
                "the erasure kill was not recorded as zero_erasure (custom death message): " + pig.getLastDamageSource());
        helper.assertTrue(stand.isAlive(), "the erasure killed the armor stand");
        helper.assertTrue(boss.getHealth() >= boss.getMaxHealth(), "the boss did not return to full HP: " + boss.getHealth());
        stand.discard();
        v.releaseAndDiscard();
        boss.discard();
        helper.succeed();
    }

    /** HP が 2/3 を切ると第 2 形態になり、空の裂け目と残骸の中心が決まる（描く側はそこに裂け目と残骸を出し続ける） */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe", timeoutTicks = 100)
    public static void phaseTwoOpensRift(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        helper.assertTrue(boss.riftAnchor() == null, "the rift was open before phase 2");
        boss.hurt(helper.getLevel().damageSources().generic(), LostCatastropheEntity.MAX_HP * 0.4F);   // setHealth での減りは保護が戻すのでダメージで
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(boss.phase() == 2, "the boss did not enter phase 2: " + boss.phase());
            helper.assertTrue(boss.riftAnchor() != null, "phase 2 did not open the rift");
            boss.discard();
            helper.succeed();
        });
    }

    /**
     * 終幕カタストロフィレクイエム（2026-10-26）: 間は受けず倒れない。足場の外（闘技場の真ん中）の者は鎮魂の光に打たれ、足場の上の者は打たれない。
     * 耐え切ると零号機は力尽きて動けない
     */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe_finale", timeoutTicks = LostCatastropheEntity.FINALE_TICKS + 80)
    public static void finaleRequiemSparesPlatformsAndEndsInStun(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        boss.startFinale();
        helper.assertTrue(boss.finaleTicks() > 0, "the finale did not start");
        helper.assertTrue(boss.fieldCenter() != null, "the finale has no center");
        helper.assertFalse(boss.hurt(helper.getLevel().damageSources().generic(), 100.0F), "the boss took damage during the finale");
        boss.kill();
        helper.assertTrue(boss.isAlive(), "/kill went through during the finale");
        net.minecraft.core.BlockPos c = boss.fieldCenter();
        net.minecraft.world.entity.animal.Pig off = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
        net.minecraft.world.entity.animal.Pig on = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(off != null && on != null, "the pigs were not made");
        Vec3 safe = LostCatastropheEntity.platform(c, 0, 0);
        off.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        on.moveTo(safe.x, safe.y, safe.z);
        for (var pig : new net.minecraft.world.entity.animal.Pig[]{off, on}) {
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
            pig.setHealth(1000.0F);
            helper.getLevel().addFreshEntity(pig);
        }
        int firstPulse = LostCatastropheEntity.FINALE_LEAD + 60 + 5;
        helper.runAfterDelay(firstPulse, () -> {
            on.moveTo(safe.x, safe.y, safe.z);                                  // 楽章ごとに足場が変わるので、最初の楽章の間だけ確かめる
            helper.assertTrue(off.getHealth() < 1000.0F, "the requiem did not hit outside the platforms: " + off.getHealth());
            helper.assertTrue(on.getHealth() >= 1000.0F, "the requiem hit on a platform: " + on.getHealth());
        });
        helper.runAfterDelay(LostCatastropheEntity.FINALE_TICKS + 10, () -> {
            helper.assertTrue(boss.finaleTicks() == 0, "the finale did not end: " + boss.finaleTicks());
            helper.assertTrue(boss.stunLeft() > 0, "the boss was not exhausted after the finale");
            off.discard();
            on.discard();
            boss.discard();
            helper.succeed();
        });
    }

    /**
     * 赤黒い天災の技（2026-10-26）: 赫雷・煉獄焔・焔の大海嘯・赫焉天墜・禍つ嵐は動かない豚に当たる。
     * 黒雷の十字架・終の大波・黙示の大嵐（避け方のある技）は最後まで回って落ちないことだけ確かめる
     */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe_calamity", timeoutTicks = 900)   // 終の大波・大嵐は広いので別の組で
    public static void calamityMovesHit(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 7);
        pig.setNoAi(true);
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
        pig.setHealth(1000.0F);
        java.util.List<java.util.Map.Entry<String, Runnable>> asserted = java.util.List.of(
                java.util.Map.entry("crimson thunder", () -> boss.crimsonThunder(pig)),
                java.util.Map.entry("purgatory flame", () -> boss.purgatoryFlame(pig)),
                java.util.Map.entry("crimson deluge", () -> boss.crimsonDeluge(pig)),
                java.util.Map.entry("heavenfall", () -> boss.heavenfall(pig)),
                java.util.Map.entry("calamity storm", () -> boss.calamityStorm(pig)));
        float[] before = new float[1];
        int at = 0;
        for (var move : asserted) {
            helper.runAfterDelay(at, () -> {
                pig.clearFire();
                pig.setHealth(1000.0F);
                pig.invulnerableTime = 0;
                before[0] = pig.getHealth();
                move.getValue().run();
            });
            helper.runAfterDelay(at + 90, () -> helper.assertTrue(pig.getHealth() < before[0], move.getKey() + " did not hit the pig"));
            at += 100;
        }
        int rest = at;
        helper.runAfterDelay(rest, () -> {
            pig.setHealth(1000.0F);
            boss.thunderCross(pig);
            boss.doomTide(pig);
            boss.apocalypseTempest(pig);
        });
        helper.runAfterDelay(rest + LostCatastropheEntity.TEMPEST_LEN + 60, () -> {
            pig.discard();
            boss.discard();
            helper.succeed();
        });
    }

    /** FE 強制ドレイン: まわりの兵姫の電力を吸い、障壁にする */
    @GameTest(template = TEMPLATE, batch = "lost_catastrophe")
    public static void drainTurnsEnergyIntoShield(GameTestHelper helper) {
        LostCatastropheEntity boss = boss(helper);
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 3);
        v.setNoAi(true);
        v.setEnergy(v.maxEnergy());
        int before = v.getEnergy();
        boss.drainFe(helper.getLevel());
        helper.assertTrue(v.getEnergy() < before, "the valkyrie energy was not drained");
        helper.assertTrue(boss.shield() > 0.0F, "the boss got no shield");
        float sh = boss.shield();
        boss.invulnerableTime = 0;
        float hp = boss.getHealth();
        boss.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        helper.assertTrue(boss.getHealth() == hp && boss.shield() < sh, "the shield did not absorb the hit first");
        v.releaseAndDiscard();
        boss.discard();
        helper.succeed();
    }
}
