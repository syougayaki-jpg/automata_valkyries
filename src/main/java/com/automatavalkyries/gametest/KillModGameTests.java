package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;

/**
 * 他の即死 MOD のキル処理を直接呼び出して、eprotect が耐えるかを検証する GameTest。
 * 対象 MOD が入っていなければ何もせず成功する（検証用の依存は build.gradle の -PwithKillMods2 / -PwithSlashBlade / -PwithEca で入れる）。
 * <p>
 * 判定は他 MOD の ASM に偽装されうる isRemoved() / isAlive() ではなく、フィールドとワールドの一覧を直接見る。
 * </p>
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class KillModGameTests {

    private static final String TEMPLATE = "empty";
    /** キル処理の後、修復に使わせる Tick 数 */
    private static final int REPAIR_TICKS = 10;

    /** MoreAvaritia: エンティティ一覧・区画・Tick 一覧からの直接削除と removalReason / dead の直書き */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void moreAvaritiaKillEntityIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "more_avaritia")) return;
        ElenorEntity elenor = spawnElenor(helper);
        Zombie attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        // killEntity はクライアント専用クラス（ClientLevel）を参照するので専用サーバーでは途中で例外になる。サーバーでも通る forceSetHealth も呼ぶ
        invoke(helper, "net.wzz.more_avaritia.util.InfinityUtils", "forceSetHealth",
                new Class<?>[]{net.minecraft.world.entity.LivingEntity.class, float.class, net.minecraft.world.damagesource.DamageSource.class,
                        net.minecraft.world.entity.LivingEntity.class},
                elenor, 0.0F, helper.getLevel().damageSources().mobAttack(attacker), attacker);
        invoke(helper, "net.wzz.more_avaritia.util.InfinityUtils", "killEntity",
                new Class<?>[]{net.minecraft.world.entity.Entity.class, net.minecraft.world.entity.LivingEntity.class}, elenor, attacker);
        attacker.discard();
        assertSurvivesAfterRepair(helper, elenor);
    }

    /** annihilationblade: リフレクションによる一覧・追跡からの削除、levelCallback の NULL 化、再登録の拒否 */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void annihilationBladeEvictionIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "annihilationblade")) return;
        ElenorEntity elenor = spawnElenor(helper);
        invoke(helper, "org.examplea.annihilationblade.combat.DeepEvictionService", "purge",
                new Class<?>[]{net.minecraft.world.entity.Entity.class}, elenor);
        assertSurvivesAfterRepair(helper, elenor);
    }

    /** Shizuku Blade: 永続・一時エンティティ管理と区画からの直接削除、消去パケット */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void shizukuRemoverIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "shizuku")) return;
        ElenorEntity elenor = spawnElenor(helper);
        invoke(helper, "com.buwan.shizuku.core.UltimateEntityRemover", "remove",
                new Class<?>[]{net.minecraft.world.entity.Entity.class, net.minecraft.world.level.Level.class}, elenor, helper.getLevel());
        assertSurvivesAfterRepair(helper, elenor);
    }

    /** Epic Core API: HP の多経路書き込みと kill */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ecaKillIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "eca")) return;
        ElenorEntity elenor = spawnElenor(helper);
        invoke(helper, "net.eca.api.EcaAPI", "setHealth",
                new Class<?>[]{net.minecraft.world.entity.LivingEntity.class, float.class}, elenor, 0.0F);
        invoke(helper, "net.eca.api.EcaAPI", "kill",
                new Class<?>[]{net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.damagesource.DamageSource.class},
                elenor, helper.getLevel().damageSources().generic());
        assertSurvivesAfterRepair(helper, elenor);
    }

    // ---- 共通 ----------------------------------------------------------------

    private static ElenorEntity spawnElenor(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
    }

    /** 対象 MOD が無ければ成功扱いで終える */
    private static boolean loaded(GameTestHelper helper, String modId) {
        if (ModList.get().isLoaded(modId)) return true;
        helper.succeed();
        return false;
    }

    private static void invoke(GameTestHelper helper, String className, String method, Class<?>[] types, Object... args) {
        try {
            Method m = Class.forName(className).getDeclaredMethod(method, types);
            m.setAccessible(true);
            m.invoke(null, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // キル処理の途中で他 MOD が例外を出しても、エレノアの状態の検証は続ける
            AutomataValkyries.LOGGER.warn("[eprotect-test] {}.{} threw", className, method, e);
        }
    }

    private static void assertSurvivesAfterRepair(GameTestHelper helper, ElenorEntity elenor) {
        helper.runAfterDelay(REPAIR_TICKS, () -> {
            ServerLevel level = helper.getLevel();
            ServerLevelTickListAccessor lists = (ServerLevelTickListAccessor) level;
            helper.assertTrue(((EntityAccessor) elenor).automata_valkyries$getRemovalReason() == null,
                    "removalReason is still set: " + ((EntityAccessor) elenor).automata_valkyries$getRemovalReason());
            helper.assertTrue(level.getEntity(elenor.getUUID()) == elenor, "Elenor is missing from the level lookup");
            helper.assertTrue(lists.automata_valkyries$getEntityTickList().contains(elenor), "Elenor is not in the tick list");
            helper.assertTrue(elenor.getEprotect().isProtectionActive(), "Protection was deactivated (retreated or defeated)");
            helper.assertTrue(elenor.getEprotect().getLastValidHealth() > 0.0F, "Health was not restored");
            elenor.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }
}
