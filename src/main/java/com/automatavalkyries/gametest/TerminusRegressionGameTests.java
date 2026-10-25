package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.GenericCheatMobNeutralizer;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import com.automatavalkyries.gametest.fake.FakeKillMod;
import com.automatavalkyries.gametest.fake.FakeTerminusBoss;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.util.UnsafeHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class TerminusRegressionGameTests {
    @GameTest(template = "empty")
    public static void terminusPreservesSharedServicesAndBoxedFields(GameTestHelper helper) {
        FakeKillMod.install();
        var other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var boss = new FakeTerminusBoss(helper.getLevel(), other);
        var cores = GenericCheatMobNeutralizer.neutralize(helper.getLevel(), boss);
        helper.assertTrue(cores.size() == 1 && cores.get(0) == boss.owned, "Shared or unrelated service selected");
        helper.assertTrue(!boss.shared.closed && !boss.unrelated.closed, "Another individual lost its service");
        helper.assertTrue(boss.owned.closed && boss.owned.resurrectLives == 0
                && boss.owned.respawnTimer == -9999L && boss.owned.shieldDelta == 0F
                && boss.owned.healthDelta == 0D && boss.owned.oldTarget == null
                && boss.owned.entitiesToAttack.isEmpty(), "Boxed defensive values or combat targets were not reset");
        helper.assertTrue(boss.owned.protectedWhitelist.equals(List.of("unrelated-entry")),
                "Target cleanup cleared unrelated entries");
        var executed = new FakeTerminusBoss(helper.getLevel(), other);
        var owned = executed.owned;
        var shared = executed.shared;
        helper.assertTrue(TerminusEngine.executeTerminus(helper.getLevel(), null, executed), "Integrated execution failed");
        helper.assertTrue(owned.closed && owned.resurrectLives == 0 && !shared.closed,
                "Integrated pipeline missed owned flags or closed a shared service");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void terminusKeepsUnownedSaveFiles(GameTestHelper helper) throws Exception {
        FakeKillMod.install();
        Path root = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
        List<Path> fixtures = new ArrayList<>();
        try {
            for (String directory : List.of("metapotent_flashfurs", "omnimobs", "witherzilla", FakeKillMod.MOD_ID)) {
                Path file = root.resolve(directory).resolve("terminus-test-" + UUID.randomUUID() + ".omnimob");
                Files.createDirectories(file.getParent());
                Files.writeString(file, "another individual's save");
                fixtures.add(file);
            }
            var vanilla = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
            var boss = new FakeTerminusBoss(helper.getLevel(), vanilla);
            GenericCheatMobNeutralizer.neutralize(helper.getLevel(), vanilla);
            GenericCheatMobNeutralizer.neutralize(helper.getLevel(), boss);
            var attacker = helper.makeMockPlayer();
            TerminusEngine.executeAreaTerminus(helper.getLevel(), attacker, 0, false);
            for (Path file : fixtures) helper.assertTrue(Files.exists(file), "Unowned save deleted: " + file);
        } finally {
            for (Path file : fixtures) Files.deleteIfExists(file);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void repeatedTerminusAttemptsStillTriggerAfter200Ticks(GameTestHelper helper) {
        Zombie resistant = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        resistant.setInvulnerable(true);
        TerminusEngine.trackTarget(resistant, helper.getLevel());
        for (int tick = 30; tick <= 180; tick += 30) {
            helper.runAfterDelay(tick, () -> TerminusEngine.trackTarget(resistant, helper.getLevel()));
        }
        // Existing reinforcement must leave this request pending rather than consume it.
        helper.runAfterDelay(190, () -> EprotectPlusLink.triggerBlacklistReinforcement("pending-test"));
        helper.runAfterDelay(205, () -> {
            helper.assertTrue(TerminusEngine.hasTrackedTarget(resistant.getUUID()), "Resistance record disappeared");
            EprotectPlusLink.resetReinforced();
        });
        helper.runAfterDelay(210, () -> {
            try {
                helper.assertTrue(EprotectPlusLink.isReinforcedActive()
                                && EprotectPlusLink.getReinforcedReason().contains("TERMINUS_RESISTED_10S"),
                        "Repeated attacks reset the threshold or an active reinforcement consumed the request");
            } finally {
                TerminusEngine.untrackTarget(resistant.getUUID());
                EprotectPlusLink.resetReinforced();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void terminusPlayerFailuresAreReportedAndDeathIsNotRepeated(GameTestHelper helper) {
        CountingPlayer target = new CountingPlayer(helper.getLevel());
        helper.assertTrue(TerminusPlayerExecutor.executeOnPlayer(helper.getLevel(), null, target), "Normal death failed");
        helper.assertTrue(TerminusPlayerExecutor.executeOnPlayer(helper.getLevel(), null, target)
                && target.deathCalls == 1, "Repeated request repeated die()");
        target.refuse = true;
        helper.assertFalse(TerminusPlayerExecutor.executeOnPlayer(helper.getLevel(), null, target), "Failed verification reported success");
        target.refuse = false;
        target.forceZero = true;
        for (float invalid : new float[]{Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), invalid);
            helper.assertFalse(TerminusPlayerExecutor.isPlayerProperlyKilled(target), "Invalid stored HP accepted");
        }
        CountingPlayer refused = new CountingPlayer(helper.getLevel());
        refused.refuse = true;
        helper.assertFalse(TerminusPlayerExecutor.executeOnPlayer(helper.getLevel(), null, refused), "Initial failure reported success");
        CountingPlayer throwing = new CountingPlayer(helper.getLevel());
        throwing.failDeath = true;
        helper.assertFalse(TerminusPlayerExecutor.executeOnPlayer(helper.getLevel(), null, throwing),
                "HP zero without a completed die() reported success");
        CountingPlayer repair = new CountingPlayer(helper.getLevel());
        helper.assertTrue(TerminusPlayerExecutor.verifyAndRemediatePlayerDeath(helper.getLevel(), repair)
                && repair.deathCalls == 1, "Repair skipped die() after setting HP zero");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void terminusRetriesUnconfirmedPlayerDeath(GameTestHelper helper) {
        CountingPlayer target = new CountingPlayer(helper.getLevel());
        target.failDeath = true;
        helper.assertFalse(TerminusEngine.executeTerminus(helper.getLevel(), null, target),
                "Rejected death was reported as confirmed");
        int failedCalls = target.deathCalls;
        target.failDeath = false;
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(TerminusPlayerExecutor.isPlayerProperlyKilled(target),
                        "Server tick did not retry the unconfirmed death");
                helper.assertTrue(target.deathCalls > failedCalls, "Rejected die() was never retried");
                helper.assertFalse(TerminusEngine.hasTrackedTarget(target.getUUID()),
                        "Confirmed death remained queued for retries");
            } finally {
                TerminusEngine.untrackTarget(target.getUUID());
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void queuedAreaTerminusIsBudgetedAndCoalesced(GameTestHelper helper) {
        var attacker = helper.makeMockPlayer();
        attacker.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        List<Zombie> targets = new ArrayList<>();
        for (int i = 0; i < 12; i++) targets.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3)));
        int[] completed = {-1};
        helper.assertTrue(TerminusEngine.queueAreaTerminus(helper.getLevel(), attacker, 1, false, count -> completed[0] = count),
                "Area job rejected");
        helper.assertFalse(TerminusEngine.queueAreaTerminus(helper.getLevel(), attacker, 1, false, null), "Duplicate job accepted");
        helper.assertTrue(targets.stream().noneMatch(Zombie::isRemoved), "Queued targets executed synchronously");
        int[] previous = {0};
        for (int tick = 1; tick <= 30; tick++) {
            helper.runAfterDelay(tick, () -> {
                int removed = (int) targets.stream().filter(Zombie::isRemoved).count();
                helper.assertTrue(removed - previous[0] <= TerminusEngine.AREA_TARGETS_PER_TICK, "Per-tick budget exceeded");
                previous[0] = removed;
            });
        }
        helper.runAfterDelay(31, () -> {
            java.lang.ref.Reference.reachabilityFence(attacker);
            helper.assertTrue(completed[0] == 12 && previous[0] == 12, "Queue did not complete all targets: " + completed[0]);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unsafeClearsEachPrimitiveUsingItsOwnWidth(GameTestHelper helper) {
        PrimitiveState state = new PrimitiveState(helper.getLevel());
        state.setCanPickUpLoot(true);
        state.setPersistenceRequired();
        state.noPhysics = true;
        state.invulnerableTime = 123;
        UUID identity = state.getUUID();
        UnsafeHelper.obliterateObjectMemory(state);
        helper.assertTrue(state.b == 0 && state.s == 0 && state.c == 0 && state.i == 0
                && state.l == 0 && state.f == 0 && state.d == 0 && !state.flag, "Primitive clearing failed");
        helper.assertTrue(state.canPickUpLoot() && state.isPersistenceRequired() && state.noPhysics
                && state.invulnerableTime == 123 && identity.equals(state.getUUID()),
                "Clearing subclass fields damaged vanilla base state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void isolationDamageErodesNonEntityControllerAndBypassesTimeWindowCaps(GameTestHelper helper) {
        com.automatavalkyries.annihilate.SpawnBanZone.clear();
        var attacker = helper.makeMockPlayer();
        try {
            // 1. 非エンティティ本体（難読化 int HP f_699991_ = 150 + 毎tick getHP 上書き + プロキシ再生成）の侵食・撃破検証
            SimulatedAvatarController controller = new SimulatedAvatarController();
            SimulatedAvatarProxy proxy1 = new SimulatedAvatarProxy(helper.getLevel(), controller);
            proxy1.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
            helper.getLevel().addFreshEntity(proxy1);

            com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                    attacker, proxy1, attacker.damageSources().playerAttack(attacker), 10.0F);
            controller.tickController();
            helper.assertTrue(controller.f_699991_ < 150 && controller.f_699991_ > 0 && controller.getHP < 150.0F,
                    "Value-matched obfuscated int HP f_699991_ was not eroded on Phase 1: " + controller.f_699991_);

            // プロキシが再生成されても本体経由で IsolationState を引き継ぎ、致死到達時に f_699991_ == -1 & isDeathCrazy == true になること
            SimulatedAvatarProxy proxy2 = new SimulatedAvatarProxy(helper.getLevel(), controller);
            proxy2.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
            helper.getLevel().addFreshEntity(proxy2);
            com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                    attacker, proxy2, attacker.damageSources().playerAttack(attacker), 30.0F);
            controller.tickController();
            helper.assertTrue(controller.f_699991_ < 0 && controller.isDeathCrazy,
                    "Lethal erosion did not set controller f_699991_ < 0 or trigger isDeathCrazy: hp=" + controller.f_699991_);

            // 2. 時間窓ダメージ上限（200 tick で 10% 上限）＋ hurtDepth > 0 & lastValidHealth <= 10% 致死制限ボスの1撃突破検証
            WindowCappedBoss cappedBoss = new WindowCappedBoss(helper.getLevel());
            cappedBoss.setPos(helper.absolutePos(new BlockPos(3, 2, 3)).getCenter());
            helper.getLevel().addFreshEntity(cappedBoss);
            com.automatavalkyries.annihilate.IsolationDamageEngine.ensureIsolatedAttack(
                    attacker, cappedBoss, attacker.damageSources().playerAttack(attacker), 200.0F);
            helper.assertTrue(cappedBoss.legitimateDefeatAccepted || cappedBoss.isDeadOrDying() || cappedBoss.isRemoved(),
                    "Window-capped boss was not defeated through rapid window-reset cascade");
        } finally {
            com.automatavalkyries.annihilate.SpawnBanZone.clear();
        }
        helper.succeed();
    }

    public static class SimulatedAvatarController {
        public double getMaxHP = 150.0;
        public float getHP = 150.0F;
        public int f_699991_ = 150;
        public int hurtCooldown = 10;
        public boolean isDeathCrazy = false;

        public void tickController() {
            this.getHP = (float) this.f_699991_;
            if (this.f_699991_ < 0) {
                this.isDeathCrazy = true;
            }
        }
    }

    public static class SimulatedAvatarProxy extends Zombie {
        public SimulatedAvatarController target;

        public SimulatedAvatarProxy(ServerLevel level, SimulatedAvatarController target) {
            super(EntityType.ZOMBIE, level);
            this.target = target;
        }
    }

    public static class WindowDamageLimiterComponent {
        public boolean windowOpen = false;
        public long windowStart = 0L;
        public float inWindow = 0.0F;
        public float lastValidHealth = 20.0F;
    }

    public static class WindowCappedBoss extends Zombie {
        public final WindowDamageLimiterComponent damageLimiter = new WindowDamageLimiterComponent();
        public int hurtDepth = 0;
        public boolean legitimateDefeatAccepted = false;

        public WindowCappedBoss(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            hurtDepth++;
            try {
                long now = this.level().getGameTime();
                if (!damageLimiter.windowOpen || now - damageLimiter.windowStart >= 200L) {
                    damageLimiter.windowOpen = true;
                    damageLimiter.windowStart = now;
                    damageLimiter.inWindow = 0.0F;
                }
                float cap = this.getMaxHealth() * 0.10F;
                float allowed = Math.max(0.0F, Math.min(amount, cap - damageLimiter.inWindow));
                damageLimiter.inWindow += allowed;
                if (allowed <= 0.0F) {
                    return false;
                }
                boolean res = super.hurt(source, allowed);
                float hp = this.getHealth();
                if (Float.isFinite(hp) && hp > 0.0F) {
                    damageLimiter.lastValidHealth = hp;
                }
                return res;
            } finally {
                hurtDepth--;
            }
        }

        @Override
        public void die(net.minecraft.world.damagesource.DamageSource source) {
            float maxSingleHit = this.getMaxHealth() * 0.10F + 1.0F;
            if (hurtDepth <= 0 || damageLimiter.lastValidHealth > maxSingleHit) {
                this.setHealth(damageLimiter.lastValidHealth);
                return;
            }
            legitimateDefeatAccepted = true;
            super.die(source);
        }
    }

    public static class PrimitiveState extends Zombie {
        public PrimitiveState(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        public byte b = 127;
        public short s = 32767;
        public char c = 'x';
        public int i = 123;
        public long l = 456;
        public float f = 7;
        public double d = 8;
        public boolean flag = true;
    }

    public static class CountingPlayer extends FakePlayer {
        public int deathCalls;
        public boolean refuse;
        public boolean failDeath;
        public boolean forceZero;
        public CountingPlayer(ServerLevel level) { super(level, new GameProfile(UUID.randomUUID(), "terminus-regression")); }
        @Override public float getHealth() { return refuse ? 20F : forceZero ? 0F : super.getHealth(); }
        @Override public void die(net.minecraft.world.damagesource.DamageSource source) {
            deathCalls++;
            if (failDeath) throw new IllegalStateException("Death rejected by test");
        }
    }
}
