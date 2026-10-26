package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.MadnessAnnihilation;
import com.automatavalkyries.annihilate.ModProtectionBreaker;
import com.automatavalkyries.annihilate.TrueAnnihilationEngine;
import com.automatavalkyries.gametest.fake.FakeCleanupBoss;
import com.automatavalkyries.gametest.fake.FakeKillMod;
import com.automatavalkyries.gametest.fake.FakeObfuscatedBoss;
import com.automatavalkyries.gametest.fake.FakeProxyBoss;
import com.automatavalkyries.protect.ForeignStateCleaner;
import com.automatavalkyries.protect.flag.KillLists;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class GeneralizationGameTests {
    @GameTest(template = "empty")
    public static void detachedCoreAndRejectingRegistryAreDismantled(GameTestHelper helper) {
        FakeKillMod.install();
        var boss = new FakeProxyBoss(helper.getLevel());
        var other = new FakeProxyBoss(helper.getLevel());
        var registry = FakeProxyBoss.Registry.entries;
        java.nio.file.Path worldRoot = helper.getLevel().getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        java.nio.file.Path saveDir = worldRoot.resolve("metapotent_flashfurs");
        java.nio.file.Path ownedSave = saveDir.resolve("owned-test.omnimob");
        java.nio.file.Path otherSave = saveDir.resolve("other-test.omnimob");
        try {
            java.nio.file.Files.createDirectories(saveDir);
            java.nio.ByteBuffer bbOwned = java.nio.ByteBuffer.allocate(24);
            bbOwned.putLong(0x12345678L);
            bbOwned.putLong(boss.core.id.getMostSignificantBits());
            bbOwned.putLong(boss.core.id.getLeastSignificantBits());
            java.nio.file.Files.write(ownedSave, bbOwned.array());
            java.nio.ByteBuffer bbOther = java.nio.ByteBuffer.allocate(24);
            bbOther.putLong(0x12345678L);
            bbOther.putLong(other.core.id.getMostSignificantBits());
            bbOther.putLong(other.core.id.getLeastSignificantBits());
            java.nio.file.Files.write(otherSave, bbOther.array());

            FakeProxyBoss.LevelManager.rawClear();
            var detachedBossCore = new FakeProxyBoss.DetachedStaticCore(boss.getUUID());
            var detachedOtherCore = new FakeProxyBoss.DetachedStaticCore(other.getUUID());
            FakeProxyBoss.LevelManager.addDirect(boss.core);
            FakeProxyBoss.LevelManager.addDirect(detachedBossCore);
            FakeProxyBoss.LevelManager.addDirect(other.core);
            FakeProxyBoss.LevelManager.addDirect(detachedOtherCore);

            FakeProxyBoss.Registry.activeSingletonBoss = boss;
            FakeProxyBoss.Registry.BOSS_SLOTS[0] = boss;
            FakeProxyBoss.Registry.BOSS_SLOTS[1] = other;

            var guardian = new FakeProxyBoss.FakeGuardianAnchor(helper.getLevel());
            var guardianPos = helper.absolutePos(new BlockPos(3, 2, 2));
            guardian.setPos(guardianPos.getX() + 0.5D, guardianPos.getY(), guardianPos.getZ() + 0.5D);
            helper.getLevel().addFreshEntity(guardian);
            var savedData = new FakeProxyBoss.FakeBossSavedData(boss.getUUID());
            savedData.guardianUUIDs.add(guardian.getUUID());
            helper.getLevel().getDataStorage().set("fake_boss_state", savedData);

            FakeProxyBoss.FakeResolutionEngine.ACTIVE_RESOLUTIONS.put(other.getUUID(), new Object());
            FakeProxyBoss.FakeResolutionEngine.PENDING.put(other.getUUID(), new Object());
            KillLists.discoverFrom(FakeProxyBoss.FakeResolutionEngine.class);
            KillLists.purge(other);
            helper.assertFalse(FakeProxyBoss.FakeResolutionEngine.ACTIVE_RESOLUTIONS.containsKey(other.getUUID())
                            || FakeProxyBoss.FakeResolutionEngine.PENDING.containsKey(other.getUUID()),
                    "KillLists did not purge target from FakeResolutionEngine ACTIVE_RESOLUTIONS / PENDING maps");

            registry.add(boss.core);
            registry.add(boss.core.id);
            registry.add(other.core);
            registry.add(other.core.id);
            boss.core.target = other; // 攻撃対象（target）に別個体を保持していても専用コントローラーとして解体されること
            var wrappedRef = boss.wrappedCore.get();
            var context = ModProtectionBreaker.breakProtection(helper.getLevel(), boss);
            ModProtectionBreaker.finishWithModOwnDeath(boss);
            var mechanisms = context.report().mechanisms();
            helper.assertTrue(mechanisms.contains(com.automatavalkyries.annihilate.DefenseStructureAnalyzer.Mechanism.DIRECT_CONTROLLER)
                    && mechanisms.contains(com.automatavalkyries.annihilate.DefenseStructureAnalyzer.Mechanism.WRAPPED_OR_NESTED_CONTROLLER)
                    && mechanisms.contains(com.automatavalkyries.annihilate.DefenseStructureAnalyzer.Mechanism.REVERSE_STATIC_CONTROLLER)
                    && mechanisms.contains(com.automatavalkyries.annihilate.DefenseStructureAnalyzer.Mechanism.SAVED_DATA_CONTROLLER),
                    "DefenseStructureAnalyzer missed direct, wrapped, reverse-static, or SavedData controller");
            helper.assertTrue(java.math.BigInteger.ZERO.equals(boss.trueHealth),
                    "BigInteger trueHealth field was not zeroed by StateScanner / GenericCheatMobNeutralizer");
            helper.assertTrue(boss.defeated && boss.retired && boss.specialRemove,
                    "defeated / retired / specialRemove boolean flags were not enabled on target");
            helper.assertTrue(boss.trueDefeatInvoked,
                    "Foreign self-defeat method trueDefeat() was not invoked by finishWithModOwnDeath");
            helper.assertTrue(savedData.defeated && boss.getUUID().equals(savedData.markedDefeatedUuid) && savedData.isDirty(),
                    "DimensionDataStorage SavedData controller was not marked defeated / dirty");
            helper.assertTrue(boss.core.nativeSawProxy, "Native removal lost its proxy before being called");
            helper.assertTrue(boss.core.removed && boss.core.beamTimer == -9999 && boss.core.lives == 0
                    && boss.core.target == null,
                    "Detached core continued running or retained combat target");
            helper.assertFalse(com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("setSoulProtected")
                    || com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("toggleSoulProtected")
                    || com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("isSoulProtected")
                    || com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("getSoulDamage"),
                    "Guard setter/toggle/getter was misclassified as a termination method");
            helper.assertTrue(com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("setDead")
                    && com.automatavalkyries.annihilate.FlagKillPredicates.isTerminationMethod("killEntity"),
                    "setDead or killEntity was not recognized as a termination method");
            helper.assertTrue(wrappedRef != null && wrappedRef.discarded && !wrappedRef.invincible,
                    "Wrapped AtomicReference controller was not dismantled");
            helper.assertTrue(detachedBossCore.terminated && !detachedOtherCore.terminated,
                    "Reverse-static-only controller was not terminated or other detached core was touched");
            helper.assertTrue(FakeProxyBoss.Registry.activeSingletonBoss == null
                    && FakeProxyBoss.Registry.BOSS_SLOTS[0] == null
                    && FakeProxyBoss.Registry.BOSS_SLOTS[1] == other,
                    "Static singleton or Object[] slot was not purged cleanly");
            helper.assertTrue(registry.size() == 2 && registry.contains(other.core) && registry.contains(other.core.id),
                    "Rejecting registry retained target or erased another core");
            helper.assertTrue(FakeProxyBoss.LevelManager.rawSize() == 2
                    && FakeProxyBoss.LevelManager.rawGet(0) == other.core
                    && FakeProxyBoss.LevelManager.rawGet(1) == detachedOtherCore,
                    "Controller-related LevelManager ProtectedArrayList was not purged or lost another core");
            helper.assertTrue(!java.nio.file.Files.exists(ownedSave) && java.nio.file.Files.exists(otherSave),
                    "Owned save file was not deleted or unowned save file was deleted");
            registry.add(boss.core.id); // Re-registration after all manager references were severed.
            ModProtectionBreaker.afterErase(boss, context);
            helper.assertTrue(registry.size() == 2, "Captured manager/identity was lost during final cleanup");
            helper.assertTrue(guardian.unloadedBypassTriggered && helper.getLevel().getEntity(guardian.getUUID()) == null,
                    "Subordinate guardian entity linked in SavedData was not evicted via UNLOADED_TO_CHUNK bypass");
            other.core.otherEntity = boss;
            ModProtectionBreaker.breakProtection(helper.getLevel(), other);
            helper.assertTrue(!other.core.removed && other.core.beamTimer == 12 && registry.size() == 2,
                    "Shared controller was dismantled");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            registry.clear();
            FakeProxyBoss.Registry.activeSingletonBoss = null;
            FakeProxyBoss.Registry.BOSS_SLOTS[0] = null;
            FakeProxyBoss.Registry.BOSS_SLOTS[1] = null;
            FakeProxyBoss.LevelManager.rawClear();
            try {
                java.nio.file.Files.deleteIfExists(ownedSave);
                java.nio.file.Files.deleteIfExists(otherSave);
            } catch (java.io.IOException ignored) {}
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collapsedHitboxAndPhysicsAreRestored(GameTestHelper helper) {
        var mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var original = mob.getBoundingBox();
        mob.setBoundingBox(new AABB(original.minX, original.minY, original.minZ,
                original.minX, original.maxY, original.maxZ));
        mob.noPhysics = true;
        helper.assertTrue(ForeignStateCleaner.clean(mob), "Collapsed single axis was missed");
        helper.assertTrue(mob.getBoundingBox().getXsize() > 0.001D && !mob.noPhysics, "Geometry/physics were not restored");
        helper.assertFalse(ForeignStateCleaner.clean(mob), "Healthy state was changed");
        var mount = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
        mob.startRiding(mount, true);
        mob.noPhysics = true;
        ForeignStateCleaner.clean(mob);
        helper.assertTrue(mob.noPhysics, "Passenger physics flag was changed");
        mob.stopRiding();
        mob.noPhysics = false;
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void healthMapsPurgeOnlyTargetAndPreserveGuards(GameTestHelper helper) {
        FakeKillMod.install();
        var mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var other = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        try {
            FakeCleanupBoss.HEALTH_MAP.put(mob, 0F);
            FakeCleanupBoss.HEALTH_MAP.put(mob.getUUID(), 0F);
            FakeCleanupBoss.HEALTH_MAP.put("value", mob);
            FakeCleanupBoss.HEALTH_MAP.put(other, 5F);
            FakeCleanupBoss.PROTECTED_HEALTH_MAP.put(mob, 20F);
            FakeCleanupBoss.UNRELATED.put(mob, 1F);
            KillLists.discoverFrom(FakeCleanupBoss.class);
            KillLists.purge(mob);
            helper.assertTrue(FakeCleanupBoss.HEALTH_MAP.size() == 1 && FakeCleanupBoss.HEALTH_MAP.containsKey(other), "Target references survived or another entity was removed");
            helper.assertTrue(FakeCleanupBoss.PROTECTED_HEALTH_MAP.containsKey(mob), "Guard map was changed");
            helper.assertTrue(FakeCleanupBoss.UNRELATED.containsKey(mob), "Unrelated map was changed");
        } finally {
            FakeCleanupBoss.HEALTH_MAP.clear();
            FakeCleanupBoss.PROTECTED_HEALTH_MAP.clear();
            FakeCleanupBoss.UNRELATED.clear();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foreignBossAndControllerAreCleaned(GameTestHelper helper) {
        FakeKillMod.install();
        var boss = new FakeCleanupBoss(helper.getLevel());
        boss.currentTarget = new Object();
        boss.controller.attackTarget = new Object();
        var context = ModProtectionBreaker.breakProtection(helper.getLevel(), boss);
        helper.assertTrue(boss.disconnected && boss.combatStopped && boss.currentTarget == null, "Boss combat/connection survived");
        helper.assertFalse(boss.bossEvent.isVisible(), "Boss bar remained visible");
        helper.assertTrue(boss.controller.kicked && boss.controller.stopped && boss.controller.removed
                && boss.controller.attackTarget == null, "Controller was not cleaned before removal");
        boss.currentTarget = new Object();
        ModProtectionBreaker.afterErase(boss, context);
        helper.assertTrue(boss.currentTarget == null, "Death callback state survived final cleanup");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void obfuscatedBossDataAndSpoofedIdentityAreDismantled(GameTestHelper helper) {
        FakeKillMod.install();
        var boss = new FakeObfuscatedBoss(helper.getLevel());
        var other = new FakeObfuscatedBoss(helper.getLevel());
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        boss.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(boss);
        FakeObfuscatedBoss.trapEndCalled = false;
        FakeObfuscatedBoss.hookWasIsolatedDuringKill = false;
        FakeObfuscatedBoss.agentFlagsQuietedDuringKill = false;
        FakeObfuscatedBoss.FakeAgtCallback.INST = x -> x;
        FakeObfuscatedBoss.FakeAgentHealthMethodHelper.enabled = true;
        FakeObfuscatedBoss.FakeAgentHealthMethodHelper.active.set(true);
        FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.clear();
        FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.put(boss, 9999.0F);
        FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.put(other, 9999.0F);
        var bossScheduledFuture = FakeObfuscatedBoss.scheduleAgentTask(boss);
        var otherScheduledFuture = FakeObfuscatedBoss.scheduleAgentTask(other);
        FakeObfuscatedBoss.StaticKillHelper.lastKilled = null;
        FakeObfuscatedBoss.StaticKillHelper.lastKilledIdentityHash = 0;
        FakeObfuscatedBoss.StaticKillHelper.lastForce = false;
        java.util.List<com.automatavalkyries.protect.flag.StateScanner.Observed> observedBefore = new java.util.ArrayList<>();
        com.automatavalkyries.protect.flag.StateScanner.observe(boss, observedBefore::add);
        helper.assertTrue(observedBefore.stream().anyMatch(o -> o.name().equals("invincibleGuard") && Boolean.TRUE.equals(o.value())),
                "StateScanner.observe did not unwrap AtomicBoolean invincibleGuard");
        helper.assertTrue(observedBefore.stream().anyMatch(o -> o.name().equals("invincible") && Boolean.TRUE.equals(o.value())),
                "StateScanner.observe did not unwrap Optional<Boolean> invincible");
        helper.assertTrue(observedBefore.stream().anyMatch(o -> o.name().equals("bossHpHolder") && o.value() instanceof Number n && n.floatValue() == 1000.0F),
                "StateScanner.observe did not unwrap 1-element float[] bossHpHolder");
        FakeObfuscatedBoss.REGISTRY.add(boss);
        FakeObfuscatedBoss.REGISTRY.add(other);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.class);
        com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.livingEntities.add(boss);
        com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.livingEntities.add(other);
        var watchdogReady = new java.util.concurrent.CountDownLatch(2);
        var watchdogRelease = new java.util.concurrent.CountDownLatch(1);
        var observedBossRef = new java.util.concurrent.atomic.AtomicReference<Object>("UNSET");
        var observedOtherRef = new java.util.concurrent.atomic.AtomicReference<Object>("UNSET");
        Thread bossWatchdog = FakeObfuscatedBoss.startWatchdogLambda(boss, watchdogReady, watchdogRelease, observedBossRef);
        Thread otherWatchdog = FakeObfuscatedBoss.startWatchdogLambda(other, watchdogReady, watchdogRelease, observedOtherRef);
        try {
            watchdogReady.await(2, java.util.concurrent.TimeUnit.SECONDS);
            var rawUuid = MadnessAnnihilation.uuid(boss);
            var rawId = MadnessAnnihilation.id(boss);
            helper.assertTrue(!rawUuid.equals(boss.getUUID()) && rawId != boss.getId(), "Identity spoofing did not activate");
            FakeObfuscatedBoss.TRACKED.add(boss);
            FakeObfuscatedBoss.TRACKED.add(other);
            FakeObfuscatedBoss.BY_ID.put(rawId, "boss");
            FakeObfuscatedBoss.BY_ID.put(rawId + 100000, "other");
            var context = ModProtectionBreaker.breakProtection(helper.getLevel(), boss);
            watchdogRelease.countDown();
            bossWatchdog.join(2000L);
            otherWatchdog.join(2000L);
            helper.assertTrue(Float.parseFloat(boss.getEntityData().get(FakeObfuscatedBoss.NCHelPo)) <= 0.0F,
                    "String-typed SynchedEntityData health survived");
            helper.assertTrue(boss.getEntityData().get(FakeObfuscatedBoss.f_198383_) <= 0.0F,
                    "Obfuscated Float SynchedEntityData slot survived");
            helper.assertTrue(boss.getHiddenArraySynchedHp() <= 0.0F,
                    "Unexposed SynchedEntityData itemsById slot survived");
            helper.assertTrue(boss.getHelPo() <= 0.0F, "Stream-guarded class setter was not updated");
            helper.assertTrue(boss.f_699991_ <= 0.0F, "Obfuscated HP field f_699991_ survived");
            helper.assertFalse(boss.invincibleGuard.get(), "Final AtomicBoolean defense wrapper survived");
            helper.assertTrue(boss.bossHpHolder[0] <= 0.0F, "Final float[] HP wrapper survived");
            helper.assertFalse(boss.shieldCore.invincible, "Owned component guard flag survived");
            helper.assertTrue(boss.shieldCore.f_77777_ <= 0.0F, "Owned component HP mirror survived");
            helper.assertTrue(boss.shieldCore.readFinalHealth() <= 0.0F, "Owned component final float HP survived");
            helper.assertTrue(boss.health.readPool() <= 0.0F,
                    "SuperSteve-style HealthOperator custom-scale final float pool survived");
            helper.assertFalse(boss.haltController.removeCalled,
                    "Controller with Runtime.halt trap in bytecode had remove() invoked");
            helper.assertFalse(boss.haltController.invincible,
                    "Controller with Runtime.halt trap was not neutralized via Unsafe field reset");
            helper.assertTrue(FakeObfuscatedBoss.StaticKillHelper.lastKilledIdentityHash == System.identityHashCode(boss)
                            && FakeObfuscatedBoss.StaticKillHelper.lastForce
                            && FakeObfuscatedBoss.StaticKillHelper.lastKilled == null,
                    "Static entity kill helper killEntity(Entity, boolean) was not invoked or static ref was not purged");
            helper.assertTrue(FakeObfuscatedBoss.hookWasIsolatedDuringKill && FakeObfuscatedBoss.FakeAgtCallback.INST != null,
                    "Target static callback hook INST was not isolated during breakProtection and restored afterward");
            helper.assertTrue(FakeObfuscatedBoss.agentFlagsQuietedDuringKill
                            && FakeObfuscatedBoss.FakeAgentHealthMethodHelper.enabled
                            && FakeObfuscatedBoss.FakeAgentHealthMethodHelper.active.get(),
                    "Foreign agent enablement flags were not quieted during breakProtection and restored afterward");
            helper.assertFalse(FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.containsKey(boss),
                    "Foreign agent HEALTH_MAP retained boss after breakProtection");
            helper.assertTrue(FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.containsKey(other),
                    "Foreign agent HEALTH_MAP lost other entity");
            helper.assertTrue(bossScheduledFuture.isCancelled()
                            && !FakeObfuscatedBoss.AGENT_EXECUTOR.getQueue().contains(bossScheduledFuture),
                    "ScheduledThreadPoolExecutor task targeting boss was not cancelled and removed");
            helper.assertFalse(otherScheduledFuture.isCancelled(),
                    "ScheduledThreadPoolExecutor task targeting other entity was cancelled");

            // 保護対象のバニラ物理・コールバック自己修復（levelCallback / bb / noPhysics / HEALTH_MAP 汎用パージ）の検証
            var otherAccessor = (com.automatavalkyries.mixin.EntityAccessor) other;
            var validLevelCallback = new net.minecraft.world.level.entity.EntityInLevelCallback() {
                @Override
                public void onMove() {
                }

                @Override
                public void onRemove(net.minecraft.world.entity.Entity.RemovalReason reason) {
                }
            };
            otherAccessor.automata_valkyries$setLevelCallback(validLevelCallback);
            com.automatavalkyries.protect.plus.EprotectPlusLink.inspectAndBypassInterference(other);
            otherAccessor.automata_valkyries$setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback.NULL);
            other.setBoundingBox(new net.minecraft.world.phys.AABB(other.getX(), other.getY(), other.getZ(),
                    other.getX(), other.getY(), other.getZ()));
            other.noPhysics = true;
            com.automatavalkyries.protect.flag.KillLists.discoverFrom(FakeObfuscatedBoss.FakeAgentHealthMethodHelper.class);
            com.automatavalkyries.protect.flag.KillLists.purge(other);
            com.automatavalkyries.protect.plus.EprotectPlusLink.inspectAndBypassInterference(other);
            helper.assertTrue(otherAccessor.automata_valkyries$getLevelCallback() == validLevelCallback,
                    "Tampered EntityInLevelCallback.NULL was not restored on protected entity");
            helper.assertTrue(other.getBoundingBox().getXsize() > 0.1D && other.getBoundingBox().getYsize() > 0.1D,
                    "Collapsed 0-volume BoundingBox was not restored on protected entity");
            helper.assertFalse(other.noPhysics,
                    "Tampered noPhysics=true was not restored on protected entity");
            helper.assertFalse(FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.containsKey(other),
                    "KillLists did not generically purge protected entity from AgentHealthMethodHelper.HEALTH_MAP");
            helper.assertFalse(com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.livingEntities.contains(boss),
                    "Unreferenced EventBus subscriber static list retained boss");
            helper.assertTrue(com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.livingEntities.contains(other),
                    "Unreferenced EventBus subscriber static list lost other");
            helper.assertTrue(observedBossRef.get() == null, "Background lambda thread retained reference to boss");
            helper.assertTrue(observedOtherRef.get() == other, "Background lambda thread for other entity was severed");
            helper.assertTrue(boss.vigorNow <= 0.0F && boss.vigorCap <= 0.0F, "Paired custom-scale HP fields survived");
            helper.assertTrue(boss.pool[0] <= 0.0F, "[hp, maxHp] array survived");
            helper.assertTrue(boss.invincible.isPresent() && !boss.invincible.get(), "Optional<Boolean> guard survived");
            helper.assertTrue(boss.getEntityData().get(FakeObfuscatedBoss.DATA_TAG).getFloat("m_health") <= 0.0F,
                    "CompoundTag SynchedEntityData health survived");
            helper.assertFalse(FakeObfuscatedBoss.TRACKED.contains(boss), "Remove-refusing HashSet retained target");
            helper.assertTrue(FakeObfuscatedBoss.TRACKED.contains(other), "Remove-refusing HashSet lost another entry");
            helper.assertFalse(FakeObfuscatedBoss.BY_ID.containsKey(rawId), "Int2ObjectMap retained target id");
            helper.assertTrue(FakeObfuscatedBoss.BY_ID.containsKey(rawId + 100000), "Int2ObjectMap lost another id");
            helper.assertTrue(boss.isOut() && boss.deathCrazy, "Extended death state flags were not enabled");
            helper.assertTrue(boss.getPersistentData().getFloat("m_health") <= 0.0F,
                    "Persistent custom health key survived");
            helper.assertTrue(!FakeObfuscatedBoss.trapEndCalled, "Static trap termination method was invoked");
            helper.assertTrue(FakeObfuscatedBoss.REGISTRY.size() == 1 && FakeObfuscatedBoss.REGISTRY.contains(other),
                    "Rejecting CopyOnWriteArrayList retained target or erased another entry");
            ModProtectionBreaker.afterErase(boss, context);
            helper.assertTrue(TrueAnnihilationEngine.annihilateEntity(helper.getLevel(), null, boss),
                    "Spoofed-identity boss removal was not confirmed");
            helper.assertTrue(helper.getLevel().getEntity(rawUuid) != boss && helper.getLevel().getEntity(rawId) != boss,
                    "Spoofed-identity boss remained in level lookup");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            watchdogRelease.countDown();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(
                    com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.class);
            com.automatavalkyries.gametest.fake.FakeUnreferencedEventSubscriber.livingEntities.clear();
            FakeObfuscatedBoss.REGISTRY.removeIf(e -> true);
            FakeObfuscatedBoss.TRACKED.removeIf(e -> true);
            FakeObfuscatedBoss.BY_ID.clear();
            FakeObfuscatedBoss.FakeAgentHealthMethodHelper.HEALTH_MAP.clear();
            FakeObfuscatedBoss.AGENT_EXECUTOR.getQueue().clear();
            boss.discard();
        }
        helper.succeed();
    }
}
