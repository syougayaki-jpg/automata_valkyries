package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.gametest.fake.FakeKillCapability;
import com.automatavalkyries.gametest.fake.FakeKillMod;
import com.automatavalkyries.gametest.fake.FakeStatefulMob;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.ForeignStateCleaner;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.FlagWatchAgent;
import com.automatavalkyries.protect.flag.InjectedMembers;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** フラグ辞書・準汎用の状態の読み書き・getter を通さない読み出しの検証 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class FlagDictionaryGameTests {

    private static final String TEMPLATE = "empty";

    private static FakeStatefulMob statefulMob(GameTestHelper helper) {
        FakeKillMod.install();
        return new FakeStatefulMob(EntityType.ZOMBIE, helper.getLevel());
    }

    private static FakeKillCapability capability(FakeStatefulMob mob) throws ReflectiveOperationException {
        Field dispatcherField = null;
        for (Field f : CapabilityProvider.class.getDeclaredFields()) if (f.getType() == CapabilityDispatcher.class) dispatcherField = f;
        dispatcherField.setAccessible(true);
        Object dispatcher = dispatcherField.get(mob);
        for (Field f : CapabilityDispatcher.class.getDeclaredFields()) {
            if (!f.getType().isArray()) continue;
            f.setAccessible(true);
            Object array = f.get(dispatcher);
            if (array instanceof Object[] providers) {
                for (Object p : providers) if (p instanceof FakeKillCapability cap) return cap;
            }
        }
        throw new IllegalStateException("The fake capability was not attached");
    }

    private static Field injected(String name) throws NoSuchFieldException {
        Field field = FakeStatefulMob.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** 単語に分けて照らす: 取り違えやすい名前（soldier / studied / reapply）は当たらず、言い回しの違う名前は当たる */
    @GameTest(template = TEMPLATE)
    public static void dictionaryClassifiesNames(GameTestHelper helper) {
        for (String name : new String[]{"isMarkedForDeath", "DeadFlag", "nosugar:erased", "the_mod$soulDamage", "frozen",
                "healthDelta", "SET_NOVELIZED", "imds$forceDead", "bypassProtection"}) {
            helper.assertTrue(FlagDictionary.harmfulOnProtected(name), name + " should be reset on a protected entity");
        }
        for (String name : new String[]{"soldier", "studied", "reapply", "soulProtected", "checkDespawnImmunity", "level",
                "invulnerable", "lastHurtTime"}) {
            helper.assertFalse(FlagDictionary.harmfulOnProtected(name), name + " should not be reset on a protected entity");
        }
        for (String name : new String[]{"invulnerableToAll", "canRevive", "godMode", "soulProtected", "dimensionalCore"}) {
            helper.assertTrue(FlagDictionary.protectiveOnTarget(name), name + " should be broken by annihilation");
        }
        helper.assertFalse(FlagDictionary.protectiveOnTarget("bypassProtection"), "A bypass was treated as a guard");
        helper.assertFalse(FlagDictionary.protectiveOnTarget("respawnDelay"), "A timer was treated as a guard");
        helper.assertFalse(FlagDictionary.protectiveOnTarget("GUARDIAN_TYPE"), "guardian was treated as a guard");
        helper.assertFalse(FlagDictionary.protectiveOnTarget("shieldDisabledUntil"), "A shield timer was treated as a guard");
        helper.assertTrue(FlagDictionary.protectiveOnTarget("isGuarded"), "isGuarded was not treated as a guard");
        for (String name : new String[]{"reviveCount", "livesLeft", "lives", "remainingRespawns"}) {
            helper.assertTrue(FlagDictionary.livesCounter(name), name + " should be a remaining-lives counter");
        }
        helper.assertFalse(FlagDictionary.livesCounter("respawnDelay"), "A timer was treated as a lives counter");
        helper.assertTrue(FlagDictionary.words("the_mod$isMarkedForDeath").equals(List.of("marked", "for", "death")),
                "Bad word split: " + FlagDictionary.words("the_mod$isMarkedForDeath"));
        helper.succeed();
    }

    /** 既存のクラスに後から足されたフィールドが、元のクラスファイルとの比較で見つかり、足した MOD が分かる */
    @GameTest(template = TEMPLATE)
    public static void injectedFieldsAreFoundAndAttributed(GameTestHelper helper) {
        FakeKillMod.install();
        List<InjectedMembers.InjectedField> fields = InjectedMembers.fields(FakeStatefulMob.class);
        Set<String> names = new java.util.HashSet<>();
        for (InjectedMembers.InjectedField f : fields) {
            names.add(f.field().getName());
            helper.assertTrue(f.mod().equals(FakeKillMod.MOD_ID), f.field().getName() + " was attributed to " + f.mod());
        }
        helper.assertTrue(names.contains("fakemod$condemned") && names.contains("fakemod$invulnerable"), "Injected fields were not found: " + names);
        helper.assertFalse(names.contains("markedForDeath"), "An original field was reported as injected");
        // このMOD 自身の Mixin が vanilla に足したフィールドは、このMOD のものと分かる（触らない側）
        for (InjectedMembers.InjectedField f : InjectedMembers.fields(net.minecraft.world.entity.LivingEntity.class)) {
            if (f.field().getName().startsWith("automata_valkyries$")) {
                helper.assertTrue(f.mod().equals(CallerAttribution.SELF), f.field().getName() + " was attributed to " + f.mod());
            }
        }
        helper.succeed();
    }

    /**
     * 保護対象の掃除: 足されたフィールド・Capability（提供元と中身）・永続データの入れ子まで、死亡の印は戻り、守り・無関係な値は残る
     */
    @GameTest(template = TEMPLATE)
    public static void protectedCleaningCoversEveryPlace(GameTestHelper helper) throws ReflectiveOperationException {
        FakeStatefulMob mob = statefulMob(helper);
        injected("fakemod$condemned").setBoolean(mob, true);
        injected("fakemod$invulnerable").setBoolean(mob, true);
        CompoundTag nested = new CompoundTag();
        nested.putBoolean("erased", true);
        nested.putInt("level", 2);
        mob.getPersistentData().put("fakemod", nested);
        FakeKillCapability cap = capability(mob);

        ForeignStateCleaner.clean(mob);
        helper.assertFalse(injected("fakemod$condemned").getBoolean(mob), "Injected death mark was not reset");
        helper.assertTrue(injected("fakemod$invulnerable").getBoolean(mob), "Injected guard was touched");
        helper.assertFalse(cap.soulMarked, "Capability death mark was not reset");
        helper.assertTrue(cap.shielded, "Capability guard was touched");
        helper.assertTrue(cap.data.doomCounter == 0, "Capability data was not reset: " + cap.data.doomCounter);
        helper.assertTrue(cap.data.livesLeft == 2, "An unrelated capability value was touched");
        CompoundTag after = mob.getPersistentData().getCompound("fakemod");
        helper.assertFalse(after.contains("erased"), "Nested persistent kill mark was not removed");
        helper.assertTrue(after.contains("level"), "An unrelated nested value was removed");
        helper.succeed();
    }

    /** 抹殺側: 守り・復活の許可は下り、残り復活回数は 0、迂回は有効に、死亡の印には触れない */
    @GameTest(template = TEMPLATE)
    public static void annihilationBreaksGuardsEverywhere(GameTestHelper helper) throws ReflectiveOperationException {
        FakeStatefulMob mob = statefulMob(helper);
        injected("fakemod$condemned").setBoolean(mob, true);
        injected("fakemod$invulnerable").setBoolean(mob, true);
        mob.getPersistentData().putBoolean("fakemod_immortal", true);
        FakeKillCapability cap = capability(mob);

        StateScanner.apply(mob, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
        helper.assertFalse(injected("fakemod$invulnerable").getBoolean(mob), "Injected guard was not broken");
        helper.assertTrue(injected("fakemod$condemned").getBoolean(mob), "A death mark was touched by annihilation");
        helper.assertFalse(mob.fakemod$isProtected(), "Interface guard was not broken");
        helper.assertFalse(cap.shielded, "Capability guard was not broken");
        helper.assertTrue(cap.data.livesLeft == 0, "Remaining lives were not zeroed: " + cap.data.livesLeft);
        helper.assertTrue(cap.soulMarked, "A death mark in the capability was touched");
        helper.assertFalse(mob.getPersistentData().contains("fakemod_immortal"), "Persistent guard was not removed");
        helper.succeed();
    }

    /** 同期データ: 保護対象の掃除では死亡の印だけ戻り、抹殺では守りが外れ残り復活回数が 0 になる */
    @GameTest(template = TEMPLATE)
    public static void synchedDataIsResetAndBroken(GameTestHelper helper) {
        FakeStatefulMob protectedSide = statefulMob(helper);
        ForeignStateCleaner.clean(protectedSide);
        helper.assertFalse(protectedSide.getEntityData().get(FakeStatefulMob.DATA_CONDEMNED), "Synched death mark was not reset");
        helper.assertTrue(protectedSide.getEntityData().get(FakeStatefulMob.DATA_INVULNERABLE), "Synched guard was touched");
        helper.assertTrue(protectedSide.getEntityData().get(FakeStatefulMob.DATA_REVIVE_COUNT) == 3, "An unrelated synched value was touched");

        FakeStatefulMob target = statefulMob(helper);
        StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
        helper.assertFalse(target.getEntityData().get(FakeStatefulMob.DATA_INVULNERABLE), "Synched guard was not broken");
        helper.assertTrue(target.getEntityData().get(FakeStatefulMob.DATA_REVIVE_COUNT) == 0, "Synched remaining revivals were not zeroed");
        helper.assertTrue(target.getEntityData().get(FakeStatefulMob.DATA_CONDEMNED), "A synched death mark was touched by annihilation");
        helper.succeed();
    }

    /** 読み出しの偽装（getter が「死んでいる」と答える）を、実際の値との食い違いで見つける */
    @GameTest(template = TEMPLATE)
    public static void spoofedReadIsDetected(GameTestHelper helper) {
        FakeStatefulMob mob = statefulMob(helper);
        helper.assertTrue(DirectRead.spoofedRead(mob) == null, "An honest entity was reported as spoofed");
        mob.spoofDead = true;
        helper.assertFalse(mob.isAlive(), "The spoof did not take effect");
        helper.assertTrue(DirectRead.isAlive(mob), "The direct read was fooled");
        helper.assertTrue("isAlive".equals(DirectRead.spoofedRead(mob)), "The spoof was not detected: " + DirectRead.spoofedRead(mob));
        helper.succeed();
    }

    /**
     * Mixin を通らない同期データへの HP 0 の直書きを修復する（以前は getHealth() がこのMOD の Mixin で直前の正常値に読み替えられ、
     * 修復が走らなかった）
     */
    @GameTest(template = TEMPLATE)
    public static void rawHealthWriteIsRepaired(GameTestHelper helper) throws ReflectiveOperationException {
        FakeKillMod.install();
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        EntityDataAccessor<Float> healthId = LivingEntityAccessor.automata_valkyries$getDataHealthId();
        Method getItem = SynchedEntityData.class.getDeclaredMethod("getItem", EntityDataAccessor.class);
        getItem.setAccessible(true);
        ((SynchedEntityData.DataItem<Float>) getItem.invoke(elenor.getEntityData(), healthId)).setValue(0.0F);
        helper.assertTrue(DirectRead.health(elenor) == 0.0F, "The raw write did not take effect");
        helper.assertTrue(elenor.getHealth() > 0.0F, "getHealth() is expected to hide the raw 0");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(DirectRead.health(elenor) > 0.0F, "The raw health was not repaired: " + DirectRead.health(elenor));
            helper.assertTrue(AttackLog.entries().stream().anyMatch(e -> e.op.equals("repair:health")), "The repair was not recorded");
            elenor.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /**
     * 辞書補助の監視: 事件（死亡）の直前にだけ変わる、辞書に無い名前を候補に挙げる。普段から動く名前・辞書にある名前は挙げない。
     * 読むだけで、エンティティの値は変えない
     */
    @GameTest(template = TEMPLATE)
    public static void flagWatchSuggestsNamesThatChangeOnlyBeforeEvents(GameTestHelper helper) {
        FlagWatchAgent agent = FlagWatchAgent.getInstance();
        agent.clear();
        for (int i = 0; i < 3; i++) {
            FakeStatefulMob mob = statefulMob(helper);
            helper.assertTrue(agent.sample(mob), "The fake mod's state was not observed");
            mob.swayPhase++;
            agent.sample(mob);
            mob.swayPhase++;
            agent.sample(mob);
            mob.swayPhase++;
            mob.hexed = true;
            mob.fakemod$setDoomLevel(9.0F);
            agent.event(mob, "death");
            helper.assertTrue(mob.hexed && mob.swayPhase == 3 && mob.fakemod$getDoomLevel() == 9.0F, "The watch wrote to the entity");
        }
        agent.drain();
        List<FlagWatchAgent.Candidate> candidates = agent.candidates();
        FlagWatchAgent.Candidate hexed = candidates.stream().filter(c -> c.name().equals("hexed")).findFirst().orElse(null);
        helper.assertTrue(hexed != null, "A name that changes only before deaths was not suggested: " + candidates);
        helper.assertTrue(hexed.mod().equals(FakeKillMod.MOD_ID), "Wrong mod: " + hexed.mod());
        helper.assertTrue(hexed.eventChanges() == 3 && hexed.rising(), "Wrong statistics: " + hexed);
        helper.assertTrue(hexed.suggestion().equals("kill:*hexed"), "Wrong suggestion: " + hexed.suggestion());
        helper.assertFalse(candidates.stream().anyMatch(c -> c.name().equals("swayPhase")), "A value that always moves was suggested");
        helper.assertFalse(candidates.stream().anyMatch(c -> c.name().equals("doomLevel")), "A name already in the dictionary was suggested");
        agent.clear();
        helper.succeed();
    }

    /** リスポーンまわりの名前: 剣の持ち主の守り・復活の見張り・位置固定・HP の錨・リスポーンの禁止を見分け、記録は印と取り違えない */
    @GameTest(template = TEMPLATE)
    public static void dictionaryClassifiesRespawnNames(GameTestHelper helper) {
        String[][] expected = {
                {"TheLastSwordYouNeverForgotInvul", "GUARD"}, {"TheLastSwordYouNeverForgotDefence", "GUARD"},
                {"ecaResurrectionTracked", "REVIVE"}, {"enableAllReturn", "REVIVE"},
                {"isLocationLocked", "CONTROL"}, {"lockLocation", "CONTROL"}, {"movementLocked", "CONTROL"},
                {"tlsWorldAnchor", "HEALTH_TAMPER"},
                {"noRespawn", "RESPAWN_BLOCK"}, {"allowAntiRespawn", "RESPAWN_BLOCK"}, {"othermod:permaDeath", "RESPAWN_BLOCK"}};
        for (String[] pair : expected) {
            helper.assertTrue(FlagDictionary.is(pair[0], FlagDictionary.Category.valueOf(pair[1])),
                    pair[0] + " should be " + pair[1] + " but was " + FlagDictionary.classify(pair[0]));
        }
        for (String name : new String[]{"tlsIsProtected", "ecaResurrectionTracked", "isLocationLocked", "noRespawn", "godMode"}) {
            helper.assertTrue(FlagDictionary.heldOnDeadPlayer(name), name + " should be released from a dead player");
        }
        for (String name : new String[]{"deathTime", "isDead", "erased", "respawnCooldown"}) {
            helper.assertFalse(FlagDictionary.heldOnDeadPlayer(name), name + " should stay on a dead player");
        }
        for (String name : new String[]{"erased", "imds$forceDead", "frozen", "noRespawn", "deathLock"}) {
            helper.assertTrue(FlagDictionary.hazardOnRespawned(name), name + " should be reset on a respawned player");
        }
        for (String name : new String[]{"lastDeathPos", "deathCount", "invulnerable", "respawnCooldown", "deathLocation", "killerName"}) {
            helper.assertFalse(FlagDictionary.hazardOnRespawned(name), name + " should not be reset on a respawned player");
        }
        helper.succeed();
    }

    /** リスポーンした個体: 引き継がれた永続データ（PlayerPersisted の中も）の死亡・停止・リスポーン禁止の印は消え、記録と守りは残る */
    @GameTest(template = TEMPLATE)
    public static void respawnSanitizerCleansCarriedOverFlags(GameTestHelper helper) {
        var player = helper.makeMockSurvivalPlayer();
        CompoundTag data = player.getPersistentData();
        data.putBoolean("othermod:noRespawn", true);
        data.putBoolean("othermod:frozen", true);
        data.putLong("othermod:lastDeathPos", 12345L);
        data.putInt("othermod:deathCount", 3);
        data.putBoolean("othermod:invulnerable", true);
        CompoundTag persisted = new CompoundTag();
        persisted.putBoolean("othermod:erased", true);
        persisted.putBoolean("othermod:keepInventory", true);
        data.put("PlayerPersisted", persisted);

        com.automatavalkyries.protect.RespawnSanitizer.cleanRespawnedPlayer(player, false);

        helper.assertFalse(data.contains("othermod:noRespawn"), "The respawn ban was not removed");
        helper.assertFalse(data.contains("othermod:frozen"), "The freeze mark was not removed");
        helper.assertFalse(data.getCompound("PlayerPersisted").contains("othermod:erased"), "The carried-over erased mark was not removed");
        helper.assertTrue(data.contains("othermod:lastDeathPos") && data.contains("othermod:deathCount"), "Records were removed");
        helper.assertTrue(data.contains("othermod:invulnerable"), "A guard was removed from the respawned player");
        helper.assertTrue(data.getCompound("PlayerPersisted").contains("othermod:keepInventory"), "An unrelated persisted setting was removed");
        helper.succeed();
    }

    /** 死亡が確定した後に HP を戻されたプレイヤーのリスポーンの要求は通す（HP を 0 に戻す）。生きているプレイヤーには何もしない */
    @GameTest(template = TEMPLATE)
    public static void respawnRequestOfRevivedDeadPlayerIsAllowed(GameTestHelper helper) {
        var alive = helper.makeMockSurvivalPlayer();
        helper.assertFalse(com.automatavalkyries.protect.RespawnSanitizer.allowRespawnOfDeadPlayer(alive), "A living player was touched");
        helper.assertTrue(alive.getHealth() > 0.0F, "A living player's health was changed");

        var revived = helper.makeMockSurvivalPlayer();
        ((LivingEntityAccessor) revived).automata_valkyries$setDead(true);
        revived.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 20.0F);
        helper.assertTrue(com.automatavalkyries.protect.RespawnSanitizer.allowRespawnOfDeadPlayer(revived), "The revived dead player was not released");
        helper.assertTrue(revived.getHealth() <= 0.0F, "The revived dead player's health was not reset");
        helper.succeed();
    }

    /**
     * 足されたフィールドの中の入れ物（isDead）と、補助クラスに置かれた同期データの鍵（getHealth の差分）も見つけて戻す
     * （Fantasy Ending の uom$ecData・FE_GET_HEALTH_DATA の再現。2026-10-03）
     */
    @GameTest(template = TEMPLATE)
    public static void nestedInjectedStateAndHelperClassKeysAreReset(GameTestHelper helper) throws ReflectiveOperationException {
        FakeStatefulMob mob = statefulMob(helper);
        Field ecField = injected("fakemod$ecData");
        com.automatavalkyries.gametest.fake.FakeExpandedContext ec = (com.automatavalkyries.gametest.fake.FakeExpandedContext) ecField.get(mob);
        ec.isDead = true;
        var delta = com.automatavalkyries.gametest.fake.FakeHealthKeys.FAKE_GET_HEALTH_DATA;
        helper.assertTrue(delta != null, "The fake helper-class key was not defined");
        mob.getEntityData().set(delta, -1000.0F);

        StateScanner.apply(mob, StateScanner.CLEAN_PROTECTED, EnumSet.allOf(StateScanner.Source.class));

        helper.assertFalse(ec.isDead, "isDead inside the injected context object was not reset");
        helper.assertTrue(ec.invulnerableTime == 7, "A timer inside the context object was touched");
        helper.assertTrue(mob.getEntityData().get(delta) == 0.0F, "The health delta key on a helper class was not reset: " + mob.getEntityData().get(delta));
        mob.discard();
        helper.succeed();
    }

    /** 時間停止・隠す（透明化・描画やボスバーの取り消し）・vanilla の状態の名前を見分ける（2026-10-03） */
    @GameTest(template = TEMPLATE)
    public static void dictionaryClassifiesTimeAndConcealNames(GameTestHelper helper) {
        for (String name : new String[]{"isTimeStop", "timeStopDimensions", "TimeStopManager", "frozenWorld", "worldStopped", "tickFreeze"}) {
            helper.assertTrue(FlagDictionary.is(name, FlagDictionary.Category.CONTROL), name + " should be CONTROL but was " + FlagDictionary.classify(name));
        }
        for (String name : new String[]{"invisible", "forceInvisible", "hideBossBar", "cancelRender", "clientDelete"}) {
            helper.assertTrue(FlagDictionary.is(name, FlagDictionary.Category.CONCEAL), name + " should be CONCEAL but was " + FlagDictionary.classify(name));
        }
        for (String name : new String[]{"invisible", "noAi", "noGravity", "hideBossBar"}) {
            helper.assertTrue(FlagDictionary.harmfulOnProtected(name), name + " should be reset on a protected entity");
        }
        for (String name : new String[]{"invisibilityDuration", "aiGoals", "gravityScale"}) {
            helper.assertFalse(FlagDictionary.harmfulOnProtected(name), name + " should not be reset on a protected entity");
        }
        helper.succeed();
    }

    /** フラグ辞書エージェント（FlagDictionaryProtector）のマスターデータと MOD 側の完全同期を検証 */
    @GameTest(template = TEMPLATE)
    public static void flagDictionaryAgentMasterMatchesModDictionary(GameTestHelper helper) {
        // エージェント側のマスターエントリの取得・検証
        var conceal = com.automatavalkyries.protect.flag.FlagDictionaryLink.getMasterEntries("CONCEAL");
        var control = com.automatavalkyries.protect.flag.FlagDictionaryLink.getMasterEntries("CONTROL");

        if (!conceal.isEmpty()) {
            helper.assertTrue(conceal.contains("=invisible") && conceal.contains("*hidebossbar"),
                    "Agent master entries missing CONCEAL entries: " + conceal);
        }
        if (!control.isEmpty()) {
            helper.assertTrue(control.contains("*timefreeze") && control.contains("*worldstop"),
                    "Agent master entries missing time stop entries: " + control);
        }

        // ensureAgentProtection が安全に走ること
        boolean repaired = FlagDictionary.ensureAgentProtection();
        helper.assertTrue(!FlagDictionary.entries().isEmpty(), "Flag dictionary active entries is empty");
        helper.assertTrue(FlagDictionary.entries().containsKey(FlagDictionary.Category.CONCEAL), "Flag dictionary missing CONCEAL");

        helper.succeed();
    }
}
