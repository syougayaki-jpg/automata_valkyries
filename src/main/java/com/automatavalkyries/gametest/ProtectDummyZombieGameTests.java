package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.ProtectDummyZombieEntity;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Overload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ProtectDummyZombieGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * ダミーゾンビが Eprotect+ を保持し、かつ終焉機構の保護対象外（isTerminusTestTarget=true）として
     * 正常に初期化されていることを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void dummyZombieAttributesAndEprotectSetup(GameTestHelper helper) {
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        helper.assertTrue(dummy.isTerminusTestTarget(), "dummy zombie must be marked as isTerminusTestTarget");
        helper.assertTrue(dummy.getEprotect() != null, "dummy zombie eprotect is null");
        helper.assertTrue(dummy.getEprotect().isProtectionActive(), "dummy zombie eprotect is not active");
        helper.assertTrue(dummy.getHealth() > 0.0F, "dummy zombie health is not positive");
        helper.assertTrue(dummy.getMaxHealth() >= 100.0F, "dummy zombie max health is less than 100");

        helper.succeed();
    }

    /**
     * 終焉執行（TerminusEngine.executeTerminus）をダミーゾンビに直接叩き込み、
     * 終焉機構のホワイトリストによる早期リターンをすり抜けた上で、
     * Eprotect+ の防御機構が即死・解体・消去を遮断して生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsTerminusEngine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        // 終焉執行（ホワイトリストで弾かれず、パイプラインが実行される）
        TerminusEngine.executeTerminus(level, attacker, dummy);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after Terminus");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after Terminus");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after Terminus");
            helper.succeed();
        });
    }

    /**
     * オーバーロード超絶抹殺（Overload.kill）をダミーゾンビに執行し、
     * ホワイトリスト除外下でも Eprotect+ により生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsOverloadKill(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        Overload.kill(dummy, attacker);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after Overload kill");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after Overload kill");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after Overload kill");
            helper.succeed();
        });
    }

    /**
     * テスト用抹殺の棒（DebugKillStickItem.annihilate）の即死手段を叩き込み、
     * Eprotect の自己修復・復元サイクルによって生存することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void dummyZombieVsDebugKillStick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        ProtectDummyZombieEntity dummy = helper.spawnWithNoFreeWill(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), new BlockPos(2, 2, 2));

        DebugKillStickItem.annihilate(level, attacker, dummy);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(dummy.isAlive(), "Dummy zombie should be alive after DebugKillStick");
            helper.assertFalse(dummy.isRemoved(), "Dummy zombie should not be removed after DebugKillStick");
            helper.assertTrue(dummy.getHealth() > 0.0F, "Dummy zombie health should remain positive after DebugKillStick");
            helper.assertFalse(com.automatavalkyries.annihilate.AnnihilationExecutionAgent.isAnnihilated(dummy),
                    "Dummy zombie must NOT be marked in ANNIHILATED lists after defense");
            helper.succeed();
        });
    }

    /**
     * エレノアに "terminus_test_target" タグを付与して終焉ホワイトリストスキップを解除し、
     * 真抹殺（TrueAnnihilationEngine.annihilateEntity）を執行しても Eprotect+ により防衛され
     * 生存かつブラックリスト漏れがないことを検証する。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void elenorTaggedTerminusTestTargetSurvivesTrueAnnihilation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        com.automatavalkyries.entity.ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        elenor.addTag("terminus_test_target");
        helper.assertTrue(elenor.isTerminusTestTarget(), "Elenor should be terminus test target after tag added");

        boolean result = com.automatavalkyries.annihilate.TrueAnnihilationEngine.annihilateEntity(level, attacker, elenor);
        helper.assertFalse(result, "TrueAnnihilationEngine should return false when defended by Eprotect+");

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(elenor.isAlive(), "Elenor should be alive after TrueAnnihilation");
            helper.assertFalse(elenor.isRemoved(), "Elenor should not be removed after TrueAnnihilation");
            helper.assertFalse(com.automatavalkyries.annihilate.AnnihilationExecutionAgent.isAnnihilated(elenor),
                    "Elenor must NOT be in ANNIHILATED lists after defense");
            elenor.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * オーバーロード兵装の「隔絶ダメージ（IsolationDamageEngine）」3 段階検証:
     * setInvulnerable(true) でバニラ hurt() が完全拒否され、かつ毎ヒット後に setHealth(maxHp) で全回復を試みる個体に対し、
     * Phase 1（上限侵食＆回復封殺）-> Phase 2（100% 到達で隔絶致死）-> Phase 3（追撃で隔絶消滅）が段階的に機能することを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void overloadIsolationDamagePhases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        net.minecraft.world.item.ItemStack overEdge = Overload.apply(
                ((com.automatavalkyries.weapon.TacticalWeaponItem) com.automatavalkyries.registry.ModWeapons.PHOTON_EDGE.get()).charged());
        attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, overEdge);

        net.minecraft.world.entity.monster.Zombie boss = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(2, 2, 2));
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
        boss.setHealth(200.0F);
        boss.setInvulnerable(true); // バニラの hurt() を無敵判定で拒否させる

        // 1撃目（2.0 * 40 = 80 隔絶ダメージ -> 上限 120）
        boolean hit1 = boss.hurt(level.damageSources().playerAttack(attacker), 2.0F);
        helper.assertTrue(hit1, "Isolation damage hit 1 should return true even against invulnerable entity");
        // 被弾後に MAX_HEALTH 属性を 10000.0 に動的膨張させても上限 120 が維持されることを確認
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000.0);
        boss.setHealth(10000.0F); // 全回復を試みる
        boss.heal(10000.0F);
        helper.assertTrue(Math.abs(boss.getHealth() - 120.0F) < 0.05F, "Phase 1 failed to clamp health to 120 after MAX_HEALTH inflation: " + boss.getHealth());

        // 同一 UUID の別インスタンスに自己置換（クローン再生成）しても侵食状態（上限 120）が引き継がれることを確認
        java.util.UUID sharedUuid = boss.getUUID();
        boss.discard();
        net.minecraft.world.entity.monster.Husk cloneBoss = new net.minecraft.world.entity.monster.Husk(net.minecraft.world.entity.EntityType.HUSK, level);
        cloneBoss.setUUID(sharedUuid);
        cloneBoss.moveTo(boss.getX(), boss.getY(), boss.getZ());
        cloneBoss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
        cloneBoss.setHealth(200.0F);
        cloneBoss.setInvulnerable(true);
        level.addFreshEntity(cloneBoss);
        helper.assertTrue(Math.abs(cloneBoss.getHealth() - 120.0F) < 0.05F,
                "UUID replacement clone failed to inherit eroded ceiling 120: " + cloneBoss.getHealth());

        // 2撃目（PartEntity 部位経由での隔絶ダメージ透過 -> 80 隔絶ダメージで親本体の上限 40）
        net.minecraftforge.entity.PartEntity<net.minecraft.world.entity.monster.Husk> dummyPart =
                new net.minecraftforge.entity.PartEntity<>(cloneBoss) {
                    @Override
                    protected void defineSynchedData() {}
                    @Override
                    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
                    @Override
                    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
                };
        com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                attacker, (net.minecraft.world.entity.Entity) dummyPart, level.damageSources().playerAttack(attacker), 2.0F);
        cloneBoss.setHealth(200.0F);
        helper.assertTrue(Math.abs(cloneBoss.getHealth() - 40.0F) < 0.05F, "Phase 1 failed to clamp health to 40 via PartEntity: " + cloneBoss.getHealth());

        // 3撃目（80 隔絶ダメージ -> 上限 0：Phase 2 隔絶致死）
        cloneBoss.hurt(level.damageSources().playerAttack(attacker), 2.0F);
        cloneBoss.setHealth(200.0F);
        helper.assertTrue(cloneBoss.getHealth() == 0.0F && cloneBoss.isDeadOrDying() && !cloneBoss.isAlive(),
                "Phase 2 failed to lock entity in dead state: hp=" + cloneBoss.getHealth());

        // 4撃目（死体居座りへの追撃 -> Phase 3 隔絶消滅）
        cloneBoss.hurt(level.damageSources().playerAttack(attacker), 2.0F);
        helper.assertTrue(cloneBoss.isRemoved() && level.getEntity(cloneBoss.getUUID()) == null,
                "Phase 3 failed to evict lingering entity from world");
        helper.succeed();
    }

    /**
     * DOOM The Titans の WitherZillaEntity 型ボス模擬クラス：
     * 1. hurt() を拒否
     * 2. die() の中で super.die() の直後に別 UUID の新個体を同座標へ addFreshEntity で即時再召喚（WitherZillaEntityDiesProcedure 相当）
     * 3. baseTick() の中で super.baseTick() の後に if (isRemoved()) revive(); deathTime = 0; setInvulnerable(true); を実行（WitherZillaOnEntityTickUpdateProcedure 相当）
     */
    private static class WitherZillaLikeBoss extends net.minecraft.world.entity.monster.Husk {
        static final java.util.List<WitherZillaLikeBoss> SPAWNED_CLONES = new java.util.ArrayList<>();

        WitherZillaLikeBoss(ServerLevel level) {
            super(net.minecraft.world.entity.EntityType.HUSK, level);
            this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(160.0);
            this.setHealth(160.0F);
            this.setInvulnerable(true);
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            return false;
        }

        @Override
        public void die(net.minecraft.world.damagesource.DamageSource source) {
            super.die(source);
            if (this.level() instanceof ServerLevel sl) {
                WitherZillaLikeBoss replacement = new WitherZillaLikeBoss(sl);
                replacement.moveTo(this.getX(), this.getY(), this.getZ());
                SPAWNED_CLONES.add(replacement);
                sl.addFreshEntity(replacement);
            }
        }

        @Override
        public void baseTick() {
            super.baseTick();
            if (this.isRemoved()) {
                this.revive();
            }
            this.deathTime = 0;
            this.setInvulnerable(true);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void doomTitansWitherZillaCloneAndBaseTickCountered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer();
        net.minecraft.world.item.ItemStack overEdge = Overload.apply(
                ((com.automatavalkyries.weapon.TacticalWeaponItem) com.automatavalkyries.registry.ModWeapons.PHOTON_EDGE.get()).charged());
        attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, overEdge);

        BlockPos spawnPos = helper.absolutePos(new BlockPos(2, 2, 2));

        // 1. 終焉 / 超絶抹殺（TerminusEngine.executeTerminus）で die() 内の新 UUID 再召喚が完全ブロックされ消滅することを確認
        WitherZillaLikeBoss.SPAWNED_CLONES.clear();
        WitherZillaLikeBoss boss1 = new WitherZillaLikeBoss(level);
        boss1.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        level.addFreshEntity(boss1);

        boolean terminusResult = TerminusEngine.executeTerminus(level, attacker, boss1);
        helper.assertTrue(terminusResult, "TerminusEngine should succeed against WitherZillaLikeBoss");
        helper.assertTrue(boss1.isRemoved() && level.getEntity(boss1.getUUID()) == null,
                "Original WitherZillaLikeBoss must be removed from world");
        for (WitherZillaLikeBoss clone : WitherZillaLikeBoss.SPAWNED_CLONES) {
            helper.assertTrue(clone.isRemoved() && level.getEntity(clone.getUUID()) == null,
                    "Clone spawned inside die() must be blocked and removed by SpawnBanZone");
        }

        // 2. 隔絶ダメージ（Overload / Tier 5 相当）でも die() 内の再召喚がブロックされ、
        //    baseTick() 末尾の deathTime=0 / revive() を乗り越えて 20 Tick 後に完全消滅することを確認
        com.automatavalkyries.annihilate.SpawnBanZone.clear();
        WitherZillaLikeBoss.SPAWNED_CLONES.clear();
        WitherZillaLikeBoss boss2 = new WitherZillaLikeBoss(level);
        boss2.moveTo(spawnPos.getX() + 2.5, spawnPos.getY(), spawnPos.getZ() + 2.5);
        level.addFreshEntity(boss2);

        // 80 ダメージ x 2 撃で 160 HP 削り切り -> Phase 2 隔絶致死
        com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                attacker, boss2, level.damageSources().playerAttack(attacker), 2.0F);
        com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                attacker, boss2, level.damageSources().playerAttack(attacker), 2.0F);

        for (WitherZillaLikeBoss clone : WitherZillaLikeBoss.SPAWNED_CLONES) {
            helper.assertTrue(clone.isRemoved() && level.getEntity(clone.getUUID()) == null,
                    "Clone spawned during IsolationDamage die() must be blocked by SpawnBanZone");
        }

        // 20 Tick 経過させ、baseTick() の deathTime = 0 / revive() を貫通して自動消滅することを確認
        for (int i = 0; i < 22 && !boss2.isRemoved(); i++) {
            boss2.tick();
        }
        helper.assertTrue(boss2.isRemoved() && level.getEntity(boss2.getUUID()) == null,
                "WitherZillaLikeBoss must be completely removed after 20 death ticks despite baseTick() resetting deathTime=0 and calling revive()");
        helper.succeed();
    }
}
