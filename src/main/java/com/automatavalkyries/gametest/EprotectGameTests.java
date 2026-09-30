package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectManager;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** eprotect の保護動作を検証する GameTest（{@code ./gradlew runGameTestServer} で実行） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class EprotectGameTests {

    private static final String TEMPLATE = "empty";

    private static ElenorEntity spawnElenor(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
    }

    private static DamageSource generic(GameTestHelper helper) {
        return helper.getLevel().damageSources().generic();
    }

    private static void assertProtectedAndAlive(GameTestHelper helper, ElenorEntity elenor) {
        helper.assertFalse(elenor.isRemoved(), "Elenor was removed: " + elenor.getRemovalReason());
        helper.assertTrue(elenor.isAlive(), "Elenor is not alive");
        helper.assertTrue(helper.getLevel().getEntity(elenor.getUUID()) == elenor, "Elenor is missing from the level");
        helper.assertTrue(elenor.getEprotect().isProtectionActive(), "Protection was deactivated");
    }

    /** 保護は正規退場で必ず外れること自体も検証しつつ、テスト後の後片付けをする */
    private static void despawnAndSucceed(GameTestHelper helper, ElenorEntity elenor) {
        elenor.getEprotect().triggerDespawn();
        helper.assertTrue(elenor.isRemoved(), "triggerDespawn() did not remove Elenor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void killCommandIsRejected(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        elenor.kill(); // /kill と同じ経路
        helper.runAfterDelay(3, () -> {
            assertProtectedAndAlive(helper, elenor);
            helper.assertTrue(elenor.getHealth() == max, "/kill damaged Elenor: " + elenor.getHealth());
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void forcedRemovalIsRejected(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        elenor.discard();
        elenor.remove(Entity.RemovalReason.KILLED);
        elenor.setRemoved(Entity.RemovalReason.DISCARDED);
        helper.runAfterDelay(3, () -> {
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void hugeDamageIsCapped(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        float cap = max * Eprotect.MAX_HIT_RATIO;

        elenor.hurt(generic(helper), Float.MAX_VALUE);
        float afterHuge = elenor.getHealth();
        helper.assertTrue(max - afterHuge <= cap + 0.01F, "Huge damage was not capped: " + afterHuge);

        elenor.invulnerableTime = 0;
        elenor.hurt(generic(helper), Float.NaN);
        helper.assertTrue(Float.isFinite(elenor.getHealth()), "NaN damage broke health");

        elenor.invulnerableTime = 0;
        elenor.hurt(generic(helper), Float.POSITIVE_INFINITY);
        helper.assertTrue(elenor.getHealth() >= max - cap * 3 - 0.01F, "Infinite damage was not capped: " + elenor.getHealth());

        helper.runAfterDelay(1, () -> {
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void directDeathIsRepaired(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        elenor.setHealth(0.0F);
        elenor.die(generic(helper)); // hurt() を経ない即死
        helper.runAfterDelay(3, () -> {
            assertProtectedAndAlive(helper, elenor);
            helper.assertTrue(elenor.getHealth() == max, "Health was not restored: " + elenor.getHealth());
            helper.assertFalse(elenor.getEprotect().isDefeated(), "Direct die() counted as a defeat");
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void illegalRemovalFlagIsCleared(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        // Mixin を経由しないフィールド直書き（リフレクション等）を再現
        ((EntityAccessor) elenor).setRemovalReason(Entity.RemovalReason.KILLED);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(((EntityAccessor) elenor).getRemovalReason() == null, "Removal flag was not cleared");
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void legitimateDefeatIsAllowed(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        for (int i = 0; i < 100 && !elenor.isDeadOrDying(); i++) {
            elenor.invulnerableTime = 0;
            elenor.hurt(generic(helper), 1000.0F);
        }
        helper.assertTrue(elenor.getEprotect().isDefeated(), "Elenor was not defeated by legitimate damage");
        // 撃破演出（DEATH_TICKS）後に正規に消去され、監視からも外れる
        helper.runAfterDelay(ElenorEntity.DEATH_TICKS + 10, () -> {
            helper.assertTrue(elenor.isRemoved(), "Defeated Elenor was not removed");
            helper.assertFalse(EprotectManager.getInstance().isRegistered(elenor.getEprotect()), "Defeated Elenor is still registered");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void debugKillStickCannotKillElenor(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        DebugKillStickItem.annihilate(helper.getLevel(), helper.makeMockPlayer(), elenor);

        int[] ticksAt8 = new int[1];
        helper.runAfterDelay(8, () -> {
            assertProtectedAndAlive(helper, elenor);
            float hp = elenor.getHealth();
            // 最大ダメージは 1 撃の上限（最大 HP の 10%）まで、HP 0 化は被弾後の値へ戻る
            helper.assertTrue(hp < max && hp >= max * (1 - Eprotect.MAX_HIT_RATIO) - 0.01F, "Unexpected health: " + hp);
            helper.assertFalse(((LivingEntityAccessor) elenor).automata_valkyries$isDead(), "Forced death flag remained");
            helper.assertTrue(((ServerLevelTickListAccessor) helper.getLevel()).automata_valkyries$getEntityTickList().contains(elenor),
                    "Elenor was not returned to the tick list");
            ticksAt8[0] = elenor.tickCount;
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(elenor.tickCount > ticksAt8[0], "Elenor is not ticking");
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void debugKillStickKillsNormalMob(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        DebugKillStickItem.annihilate(helper.getLevel(), helper.makeMockPlayer(), zombie);
        helper.assertTrue(zombie.isRemoved(), "Zombie survived the debug kill stick");
        helper.succeed();
    }

    /** 正規撃破の直後に強制消去されても、撃破演出を最後まで再生してから消える */
    @GameTest(template = TEMPLATE)
    public static void deathAnimationIsNotSkipped(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        for (int i = 0; i < 100 && elenor.getHealth() > elenor.getMaxHealth() * Eprotect.MAX_HIT_RATIO; i++) {
            elenor.invulnerableTime = 0;
            elenor.hurt(generic(helper), 1000.0F);
        }
        // 最後の一撃ごと、抹殺の棒（最大ダメージ → kill → 強制デスフラグ → Tick 停止）
        DebugKillStickItem.annihilate(helper.getLevel(), helper.makeMockPlayer(), elenor);
        helper.assertTrue(elenor.getEprotect().isDying(), "Elenor is not in the death animation");

        helper.runAfterDelay(ElenorEntity.DEATH_TICKS - 8, () -> {
            helper.assertFalse(elenor.isRemoved(), "Death animation was cut short: " + elenor.getRemovalReason());
            helper.assertTrue(elenor.deathTime > 0, "Death animation is not progressing");
        });
        helper.runAfterDelay(ElenorEntity.DEATH_TICKS + 10, () -> {
            helper.assertTrue(elenor.isRemoved(), "Elenor was not removed after the death animation");
            helper.succeed();
        });
    }

    /** hurt() を通らない HP の削り（setHealth(現在値 - x)）は拒否され、回復は通る */
    @GameTest(template = TEMPLATE)
    public static void healthLossOutsideDamageIsRejected(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        elenor.setHealth(max - 100.0F);
        helper.assertTrue(elenor.getHealth() == max, "Health loss outside hurt() was accepted: " + elenor.getHealth());

        elenor.hurt(generic(helper), 20.0F); // 正規の被弾は通る
        float afterHurt = elenor.getHealth();
        helper.assertTrue(afterHurt < max, "Legitimate damage was rejected");
        elenor.setHealth(max); // 回復は通る
        helper.assertTrue(elenor.getHealth() == max, "Healing was rejected");
        despawnAndSucceed(helper, elenor);
    }

    /** 修復が追いつかない（毎 Tick 壊される）ときは、正規の退場処理で安全に退避する */
    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void unrepairableStateTriggersSafeRetreat(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        helper.onEachTick(() -> {
            if (!elenor.getEprotect().isDespawning()) {
                ((EntityAccessor) elenor).setRemovalReason(Entity.RemovalReason.KILLED);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(elenor.getEprotect().isDespawning(), "Elenor has not retreated yet");
            helper.assertTrue(elenor.isRemoved(), "Retreated Elenor is still in the world");
        });
    }
}
