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
import net.minecraft.world.entity.player.Player;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;

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

    /** Omni-Mobs: healthDelta 負数強制注入、RemoveDeadEntities 登録、10億ブロック飛ばし */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void omniMobsAttackIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "omnimobs")) return;
        ElenorEntity elenor = spawnElenor(helper);
        invoke(helper, "flashfur.omnimobs.util.GetHealthModifyUtil", "setHealthDelta",
                new Class<?>[]{net.minecraft.world.entity.LivingEntity.class, float.class}, elenor, -999999.0F);
        invoke(helper, "flashfur.omnimobs.entities.base.RemoveDeadEntities", "add",
                new Class<?>[]{net.minecraft.world.entity.LivingEntity.class}, elenor);
        invoke(helper, "flashfur.omnimobs.util.EntityUtil", "forceSetPos",
                new Class<?>[]{net.minecraft.world.entity.Entity.class, net.minecraft.world.phys.Vec3.class},
                elenor, new net.minecraft.world.phys.Vec3(1.0E9D, 0.0D, 0.0D));
        assertSurvivesAfterRepair(helper, elenor);
    }

    /**
     * Epic Core API: The Last Sword You Never Forgot の撃破処理（theLastEndSetDead）が kill の直前に掛ける種類単位の出現禁止。
     * 禁止中の種類は検索表から隠され、あらゆる追加経路を拒否されるので、入り直せずに退避へ追い込まれていた。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ecaSpawnBanIsCountered(GameTestHelper helper) {
        if (!loaded(helper, "eca")) return;
        ElenorEntity elenor = spawnElenor(helper);
        // 禁止は時間切れ（600 Tick）まで残したまま検証する（検索表から隠されていないこと・入り直せることを含む）
        invoke(helper, "net.eca.api.EcaAPI", "banSpawn",
                new Class<?>[]{ServerLevel.class, EntityType.class, int.class}, helper.getLevel(), elenor.getType(), 600);
        assertSurvivesAfterRepair(helper, elenor);
    }

    /**
     * 他 MOD に頼らず「深い追い出し」を再現する: ワールドの各一覧（UUID 登録・検索表・区画・Tick 一覧・追跡）から直接外し、
     * 接続を何もしない独自実装へ差し替え、消去フラグを直書きする（MoreAvaritia / annihilationblade / ECA 型）。
     * 入り直しで点検項目がすべて正常に戻ることを確認する。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void deepEvictionIsRejoined(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel level = helper.getLevel();
        @SuppressWarnings("unchecked")
        com.automatavalkyries.mixin.EntityManagerAccessor<net.minecraft.world.entity.Entity> manager =
                (com.automatavalkyries.mixin.EntityManagerAccessor<net.minecraft.world.entity.Entity>) (Object)
                        ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
        helper.runAfterDelay(2, () -> {
            level.getChunkSource().removeEntity(elenor);
            ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(elenor);
            manager.automata_valkyries$getVisibleEntityStorage().remove(elenor);
            manager.automata_valkyries$getKnownUuids().remove(elenor.getUUID());
            var section = manager.automata_valkyries$getSectionStorage()
                    .getSection(net.minecraft.core.SectionPos.asLong(elenor.blockPosition()));
            if (section != null) section.remove(elenor);
            ((EntityAccessor) elenor).automata_valkyries$setLevelCallback(new net.minecraft.world.level.entity.EntityInLevelCallback() {
                @Override public void onMove() {}
                @Override public void onRemove(net.minecraft.world.entity.Entity.RemovalReason reason) {}
            });
            ((EntityAccessor) elenor).automata_valkyries$setRemovalReason(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            helper.assertFalse(com.automatavalkyries.protect.WorldPresence.inspect(level, elenor).isEmpty(),
                    "Eviction simulation did not break anything");
            helper.runAfterDelay(REPAIR_TICKS, () -> {
                var issues = com.automatavalkyries.protect.WorldPresence.inspect(level, elenor);
                helper.assertTrue(issues.isEmpty(), "Still broken after rejoin: " + issues);
                assertSurvivesAfterRepair(helper, elenor);
            });
        });
    }

    /**
     * 他 MOD が EntityJoinLevelEvent を最優先でキャンセルして入り直しを拒否しても、同じ個体のまま入り直せること
     * （差し替えに頼らない）。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void cancelledJoinEventIsOverridden(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel level = helper.getLevel();
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> refuser = event -> {
            if (event.getEntity() == elenor) event.setCanceled(true);
        };
        helper.runAfterDelay(2, () -> {
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, refuser);
            com.automatavalkyries.item.SuperKillStickItem.evict(level, elenor);
            helper.runAfterDelay(REPAIR_TICKS, () -> {
                MinecraftForge.EVENT_BUS.unregister(refuser);
                helper.assertTrue(level.getEntity(elenor.getUUID()) == elenor, "Elenor did not rejoin as the same instance");
                assertSurvivesAfterRepair(helper, elenor);
            });
        });
    }

    /**
     * 超抹殺の棒: 深い追い出しに加えて同じ個体の再登録を拒否する。入り直しは失敗し、同じ UUID の新しい個体への差し替えで
     * 生き残ること（古い個体は保護を解かれ、新しい個体が保護を引き継ぐ）を確認する。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void refusedRejoinIsReplacedWithCopy(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel level = helper.getLevel();
        java.util.UUID uuid = elenor.getUUID();
        helper.runAfterDelay(2, () -> {
            com.automatavalkyries.item.DebugKillStickItem.annihilate(level, null, elenor);
            com.automatavalkyries.item.SuperKillStickItem.evict(level, elenor);
            com.automatavalkyries.item.SuperKillStickItem.refuseRejoin(elenor);
            helper.runAfterDelay(REPAIR_TICKS, () -> {
                net.minecraft.world.entity.Entity current = level.getEntity(uuid);
                helper.assertTrue(current instanceof ElenorEntity, "No entity with Elenor's UUID after replacement: " + current);
                helper.assertTrue(current != elenor, "The refused instance came back instead of a fresh copy");
                ElenorEntity copy = (ElenorEntity) current;
                helper.assertTrue(copy.getEprotect().isProtectionActive(), "The copy is not protected");
                helper.assertFalse(elenor.getEprotect().isGuardingRemoval(), "The retired instance is still guarding");
                var issues = com.automatavalkyries.protect.WorldPresence.inspect(level, copy);
                helper.assertTrue(issues.isEmpty(), "The copy is not correctly in the world: " + issues);
                copy.getEprotect().triggerDespawn();
                helper.succeed();
            });
        });
    }

    /**
     * 最後の砦: 見張っている Eprotect ごと失われ、ワールドのどこにも居ない個体が、ワールドデータの登録簿の写しから作り直されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void lostEntityIsRestoredFromRegistry(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel level = helper.getLevel();
        java.util.UUID uuid = elenor.getUUID();
        // 登録簿へ写しが取られるまで待つ（記録は 20 Tick ごと）
        helper.runAfterDelay(30, () -> {
            var entry = com.automatavalkyries.protect.EprotectRegistry.get(level).find(uuid);
            helper.assertTrue(entry != null, "Elenor was never recorded in the world registry");
            // 丸ごと喪失: 一覧から追い出し、同じ個体の再登録を拒否し、見張り（Eprotect）も外す
            com.automatavalkyries.item.SuperKillStickItem.evict(level, elenor);
            com.automatavalkyries.item.SuperKillStickItem.refuseRejoin(elenor);
            elenor.getEprotect().retireReplaced();
            helper.assertTrue(level.getEntity(uuid) == null, "Elenor is still in the level after the total loss");
            helper.runAfterDelay(60, () -> {
                net.minecraft.world.entity.Entity restored = level.getEntity(uuid);
                helper.assertTrue(restored instanceof ElenorEntity && restored != elenor,
                        "Elenor was not restored from the registry: " + restored);
                ElenorEntity copy = (ElenorEntity) restored;
                helper.assertTrue(copy.getEprotect().isProtectionActive(), "The restored copy is not protected");
                copy.getEprotect().triggerDespawn();
                helper.succeed();
            });
        });
    }

    /** 登録簿の記録を書き換えるとチェックサムで弾かれ、書き換えていない記録は読めること */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void tamperedRegistryEntryIsRejected(GameTestHelper helper) {
        ElenorEntity elenor = spawnElenor(helper);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(30, () -> {
            var entry = com.automatavalkyries.protect.EprotectRegistry.get(level).find(elenor.getUUID());
            helper.assertTrue(entry != null, "Elenor was never recorded in the world registry");
            net.minecraft.nbt.CompoundTag intact = com.automatavalkyries.protect.EprotectRegistry.encode(entry);
            helper.assertTrue(com.automatavalkyries.protect.EprotectRegistry.decode(intact.copy()) != null, "An intact entry was rejected");
            net.minecraft.nbt.CompoundTag tampered = intact.copy();
            tampered.putFloat("Health", 0.0F); // 他 MOD・外部ツールによる HP の書き換えを想定
            helper.assertTrue(com.automatavalkyries.protect.EprotectRegistry.decode(tampered) == null, "A tampered entry was accepted");
            elenor.getEprotect().triggerDespawn();
            helper.succeed();
        });
    }

    /** 超絶抹殺の棒: 対象の即死、追跡・区画・一覧からの抹消、及び再参加の完全拒否を検証 */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ultraKillStickEradicatesTargetAndRefusesRejoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        UUID targetUuid = target.getUUID();
        int targetId = target.getId();

        AnnihilationExecutionAgent.annihilateEntity(level, helper.makeMockPlayer(), target);

        // 1. ブラックリストと消去フラグの検証
        helper.assertTrue(AnnihilationExecutionAgent.isAnnihilated(target), "Target was not marked in annihilation agent");
        helper.assertTrue(target.isRemoved(), "Target removalReason is not set");

        // 2. 追跡偽装・一覧からの切断検証
        ServerLevelTickListAccessor tickLists = (ServerLevelTickListAccessor) level;
        helper.assertTrue(level.getEntity(targetUuid) == null, "Target is still present in level entity lookup");
        helper.assertTrue(!tickLists.automata_valkyries$getEntityTickList().contains(target), "Target is still in tick list");
        Int2ObjectMap<?> entityMap = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap();
        helper.assertTrue(!entityMap.containsKey(targetId), "Target is still present in chunkMap entityMap");

        // 3. イベント操作による再スポーン・再参加の拒否検証
        EntityJoinLevelEvent joinEvent = new EntityJoinLevelEvent(target, level);
        MinecraftForge.EVENT_BUS.post(joinEvent);
        helper.assertTrue(joinEvent.isCanceled(), "EntityJoinLevelEvent was not canceled for annihilated entity");

        helper.succeed();
    }

    /** 超絶抹殺の棒: 範囲抹殺（空中右クリック）で全対象が消滅することの検証 */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ultraKillStickAoEEradication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie z1 = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        Zombie z2 = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));

        Player player = helper.makeMockPlayer();
        net.minecraft.world.phys.Vec3 center = helper.absolutePos(new BlockPos(3, 2, 3)).getCenter();
        player.setPos(center.x, center.y, center.z);

        int eradicated = AnnihilationExecutionAgent.annihilateAllInArea(level, player, 16.0);
        helper.assertTrue(eradicated >= 2, "Expected at least 2 entities eradicated, got " + eradicated);

        helper.assertTrue(z1.isRemoved() && z2.isRemoved(), "Zombies were not eradicated by AoE annihilation");
        helper.assertTrue(level.getEntity(z1.getUUID()) == null, "Zombie 1 still in level");
        helper.assertTrue(level.getEntity(z2.getUUID()) == null, "Zombie 2 still in level");

        helper.succeed();
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
        net.minecraft.world.phys.Vec3 spawnPos = elenor.position();
        helper.runAfterDelay(REPAIR_TICKS, () -> {
            // (-999, -999, -999) などへの飛ばしから戻っているか
            helper.assertTrue(elenor.position().distanceTo(spawnPos) < 16.0D, "Elenor was displaced to " + elenor.position());
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
