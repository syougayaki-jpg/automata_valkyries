package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectManager;
import com.automatavalkyries.protect.EprotectWatchdogAgent;
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

    /**
     * 同じ Tick に無敵時間を消して hurt() を連打しても、{@link Eprotect#DAMAGE_WINDOW_TICKS} の間に減る HP は
     * 1 撃の上限（最大 HP の {@link Eprotect#MAX_HIT_RATIO}）まで。上限ごとの連打で 1 Tick のうちに正規撃破されないこと
     */
    @GameTest(template = TEMPLATE)
    public static void rapidHitsShareOneDamageBudget(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        for (int i = 0; i < 30; i++) {
            elenor.invulnerableTime = 0;
            elenor.hurt(generic(helper), 1000.0F);
        }
        float hp = elenor.getHealth();
        helper.assertTrue(hp >= max * (1 - Eprotect.MAX_HIT_RATIO) - 0.01F, "Rapid hits exceeded the damage budget: " + hp);
        helper.assertFalse(elenor.getEprotect().isDefeated(), "Rapid hits defeated Elenor");
        helper.runAfterDelay(1, () -> {
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    /**
     * チャンクのアンロードを装った消去（UNLOADED_TO_CHUNK / UNLOADED_WITH_PLAYER）。
     * 正規のアンロードは区画が HIDDEN になってからしか起きないので、見えているチャンクでは拒否・修復される
     */
    @GameTest(template = TEMPLATE)
    public static void fakeUnloadIsRejected(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        elenor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        elenor.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        helper.runAfterDelay(3, () -> {
            assertProtectedAndAlive(helper, elenor);
            helper.assertTrue(EprotectManager.getInstance().isRegistered(elenor.getEprotect()), "Fake unload stopped the watch");
            // Mixin を経ないフィールド直書きも修復される
            ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            helper.runAfterDelay(4, () -> {
                helper.assertTrue(((EntityAccessor) elenor).automata_valkyries$getRemovalReason() == null,
                        "Fake unload flag was not cleared: " + elenor.getRemovalReason());
                assertProtectedAndAlive(helper, elenor);
                despawnAndSucceed(helper, elenor);
            });
        });
    }

    /**
     * 最大 HP（MAX_HEALTH 属性）の改ざん: 基礎値の書き換えと他 MOD の補正が外れ、既定値（500）に戻ること。
     * 基礎値を 20 億へ上げてから、他 MOD 名義の -100% 補正で 1 まで下げる（1 撃で倒せる残量になる）
     */
    @GameTest(template = TEMPLATE)
    public static void tamperedMaxHealthIsRestored(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();
        var attribute = elenor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        attribute.setBaseValue(2.0E9D);
        elenor.setHealth(2.0E9F); // 回復方向の書き込みは通る
        attribute.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                java.util.UUID.randomUUID(), "foreign_max_health_cut", -1.0D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(elenor.getMaxHealth() == max, "Max health was not restored: " + elenor.getMaxHealth());
            helper.assertTrue(attribute.getModifiers().isEmpty(), "Foreign modifier remained: " + attribute.getModifiers());
            float hp = elenor.getHealth();
            helper.assertTrue(hp > 0.0F && hp <= max, "Health is outside the restored max: " + hp);
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
        ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(((EntityAccessor) elenor).automata_valkyries$getRemovalReason() == null, "Removal flag was not cleared");
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void legitimateDefeatIsAllowed(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        for (int i = 0; i < 100 && !elenor.isDeadOrDying(); i++) {
            elenor.invulnerableTime = 0;
            elenor.getEprotect().resetDamageWindow(); // 10 秒おきの被弾を 1 Tick に縮めて再現
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
        // 実際の使い方どおり棒を手に持たせる（持っていないと「即死武器を持った攻撃」の判定を通らず、見落とす）
        var player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModItems.DEBUG_KILL_STICK.get()));
        DebugKillStickItem.annihilate(helper.getLevel(), player, elenor);

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
            elenor.getEprotect().resetDamageWindow(); // 10 秒おきの被弾を 1 Tick に縮めて再現
            elenor.hurt(generic(helper), 1000.0F);
        }
        // 最後の一撃ごと、抹殺の棒（最大ダメージ → kill → 強制デスフラグ → Tick 停止）
        elenor.getEprotect().resetDamageWindow();
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
                ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(elenor.getEprotect().isDespawning(), "Elenor has not retreated yet");
            helper.assertTrue(elenor.isRemoved(), "Retreated Elenor is still in the world");
        });
    }

    /** IMDS-Reborn の即死手順（無敵剥奪、die()、forceDead、リスト登録、AI全消去）を無力化できるか検証 */
    @GameTest(template = TEMPLATE)
    public static void imdsKillProcedureIsCountered(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();

        // 1. IMDS の手順をシミュレート
        elenor.getEprotect().beginHurt(); // hurt 中を装う
        elenor.setHealth(0.0F);
        elenor.die(generic(helper));
        elenor.getEprotect().endHurt();

        // AI 消去
        elenor.goalSelector.getAvailableGoals().clear();
        helper.assertTrue(elenor.goalSelector.getAvailableGoals().isEmpty(), "Goals were not cleared");

        // 2. 数 Tick 後に修復され、AI も復元されて生きているか
        helper.runAfterDelay(4, () -> {
            assertProtectedAndAlive(helper, elenor);
            helper.assertTrue(elenor.getHealth() >= max * (1 - Eprotect.MAX_HIT_RATIO) - 1.0F,
                    "Health dropped below single-hit cap: " + elenor.getHealth());
            helper.assertFalse(elenor.goalSelector.getAvailableGoals().isEmpty(), "AI was not restored after IMDS cleanAI");
            despawnAndSucceed(helper, elenor);
        });
    }

    /** Hyperlink のノベル化（即死・消去・Tick停止）を無力化できるか検証 */
    @GameTest(template = TEMPLATE)
    public static void hyperlinkNovelizeIsCountered(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        float max = elenor.getMaxHealth();

        // ノベル化・直接消去のシミュレート
        ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
        elenor.deathTime = 15;

        helper.runAfterDelay(4, () -> {
            assertProtectedAndAlive(helper, elenor);
            helper.assertTrue(elenor.getHealth() == max, "Health not restored after Hyperlink simulation");
            helper.assertTrue(elenor.deathTime == 0, "Death animation was not reset");
            despawnAndSucceed(helper, elenor);
        });
    }

    /** ウォッチドッグが起動していて、メインループの進行を追えているか */
    @GameTest(template = TEMPLATE)
    public static void watchdogIsRunningAndSeesProgress(GameTestHelper helper) {
        EprotectWatchdogAgent watchdog = EprotectWatchdogAgent.getInstance();
        helper.assertTrue(watchdog.isRunning(), "Watchdog thread is not running");
        long elapsed = System.currentTimeMillis() - watchdog.getLastProgressMillis();
        helper.assertTrue(elapsed < 2000L, "Watchdog has not seen the main loop advance for " + elapsed + " ms");
        helper.succeed();
    }

    /** 別スレッドから消去フラグを直書きされても、メインスレッドの修復で戻る */
    @GameTest(template = TEMPLATE)
    public static void removalFlagWrittenFromAnotherThreadIsRepaired(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        Thread attacker = new Thread(() ->
                ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED),
                "MaliciousExternalThread");
        attacker.start();

        helper.runAfterDelay(6, () -> {
            helper.assertTrue(((EntityAccessor) elenor).automata_valkyries$getRemovalReason() == null,
                    "Removal flag written from another thread was not cleared");
            assertProtectedAndAlive(helper, elenor);
            despawnAndSucceed(helper, elenor);
        });
    }

    /** 同期データの HP を、set() も setHealth() も通さずに直接書き換える（Fantasy Ending の catchSetTrueHealth の再現） */
    private static void writeHealthBehindTheScenes(ElenorEntity elenor, float health) {
        try {
            java.lang.reflect.Field itemsField = null;
            for (java.lang.reflect.Field f : net.minecraft.network.syncher.SynchedEntityData.class.getDeclaredFields()) {
                if (it.unimi.dsi.fastutil.ints.Int2ObjectMap.class.isAssignableFrom(f.getType())) itemsField = f;
            }
            itemsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var items = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.network.syncher.SynchedEntityData.DataItem<Object>>) itemsField.get(elenor.getEntityData());
            items.get(com.automatavalkyries.mixin.LivingEntityAccessor.automata_valkyries$getDataHealthId().getId()).setValue(health);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * ダメージ処理を通らない HP の削り（直書き）も、普通の被弾と同じ上限に掛かる: 1 回目は上限（最大 HP の 10%）まで通り、
     * 同じ時間窓の 2 回目は全部戻る（2026-10-03。これまでは HP 0 にならない削りは素通りだった）
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void untrackedHealthLossIsCappedLikeDamage(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        elenor.getEprotect().resetDamageWindow();
        float max = elenor.getMaxHealth();
        helper.runAfterDelay(2, () -> {
            writeHealthBehindTheScenes(elenor, max * 0.5F);
            helper.runAfterDelay(3, () -> {
                float afterFirst = com.automatavalkyries.protect.DirectRead.health(elenor);
                float expected = max - max * 0.1F;
                helper.assertTrue(Math.abs(afterFirst - expected) < 0.5F,
                        "First untracked loss was not capped to 10% (health " + afterFirst + ", expected " + expected + ")");
                writeHealthBehindTheScenes(elenor, max * 0.4F);
                helper.runAfterDelay(3, () -> {
                    float afterSecond = com.automatavalkyries.protect.DirectRead.health(elenor);
                    helper.assertTrue(Math.abs(afterSecond - expected) < 0.5F,
                            "Second untracked loss in the same window was not fully reverted (health " + afterSecond + ")");
                    elenor.getEprotect().triggerDespawn();
                    helper.succeed();
                });
            });
        });
    }

    /**
     * LifeGuard（-PwithLifeGuard）: 生死の getter を他 MOD が包んで偽装しても、保護対象だけはいちばん外側で実際の値に直る。
     * 保護していない個体の偽装には触らない。エージェントが無ければ何もしない
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void lifeGuardReportsTrueLifeOfProtectedEntities(GameTestHelper helper) {
        if (!com.automatavalkyries.protect.LifeGuardLink.isInstalled()) {
            helper.succeed();
            return;
        }
        String tag = com.automatavalkyries.gametest.fake.FakeKillMod.LIFE_SPOOF_TAG;
        ElenorEntity elenor = spawnElenor(helper);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        elenor.addTag(tag);
        zombie.addTag(tag);
        float real = com.automatavalkyries.protect.DirectRead.health(elenor);
        helper.assertTrue(elenor.getHealth() == real, "Protected entity's getHealth was not converted to the true value (" + elenor.getHealth() + " vs " + real + ")");
        helper.assertFalse(elenor.isDeadOrDying(), "Protected entity's isDeadOrDying was not converted to the true value");
        helper.assertTrue(zombie.getHealth() == 1.0F, "An unprotected entity's spoof was touched (" + zombie.getHealth() + ")");
        helper.assertTrue(zombie.isDeadOrDying(), "An unprotected entity's isDeadOrDying spoof was touched");
        zombie.removeTag(tag);
        zombie.discard();
        elenor.removeTag(tag);
        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /** 時間停止で包み（guardEntityTick）の入口から Tick を打ち切られても、保護対象は入口を飛ばして動き続ける（EndingLibrary の再現） */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void timeStopAtTickWrapperIsBypassed(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        helper.runAfterDelay(3, () -> {
            elenor.addTag(com.automatavalkyries.gametest.fake.FakeKillMod.TIME_STOP_TAG);
            int before = elenor.tickCount;
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(elenor.tickCount > before + 5, "Elenor stopped ticking under a guardEntityTick time stop (" + before + " -> " + elenor.tickCount + ")");
                elenor.removeTag(com.automatavalkyries.gametest.fake.FakeKillMod.TIME_STOP_TAG);
                elenor.getEprotect().triggerDespawn();
                helper.succeed();
            });
        });
    }

    /** 消去を拒否したら、見ているプレイヤーへ見せ直す（追跡を作り直す。クライアントだけ消す MOD・外されたボスバーの対策） */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void refusedRemovalResyncsViewers(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        helper.runAfterDelay(25, () -> {
            elenor.discard();
            helper.runAfterDelay(3, () -> {
                helper.assertFalse(elenor.isRemoved(), "Elenor was removed");
                boolean resynced = com.automatavalkyries.protect.attack.AttackLog.entries().stream()
                        .anyMatch(e -> e.op.equals(com.automatavalkyries.protect.attack.AttackOp.REPAIR + ":viewers_resync"));
                helper.assertTrue(resynced, "Viewers were not resynced after a refused removal");
                elenor.getEprotect().triggerDespawn();
                helper.succeed();
            });
        });
    }

    /** vanilla の見た目・AI の状態（透明・AI 停止・重力無し）を他 MOD に書き換えられても、保護を始めたときの状態に戻る（Fantasy Ending の消去・AI 停止） */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void vanillaAppearanceAndAiFlagsAreRestored(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        helper.runAfterDelay(3, () -> {
            boolean baselineNoAi = elenor.isNoAi();
            elenor.setInvisible(true);
            elenor.setNoAi(!baselineNoAi);
            elenor.setNoGravity(true);
            helper.runAfterDelay(3, () -> {
                helper.assertFalse(elenor.isInvisible(), "Invisibility forced by another mod was not reverted");
                helper.assertTrue(elenor.isNoAi() == baselineNoAi, "The AI flag changed by another mod was not restored");
                helper.assertFalse(elenor.isNoGravity(), "No-gravity forced by another mod was not reverted");
                elenor.getEprotect().triggerDespawn();
                helper.succeed();
            });
        });
    }
}
