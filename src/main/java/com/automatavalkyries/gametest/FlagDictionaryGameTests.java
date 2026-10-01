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
}
