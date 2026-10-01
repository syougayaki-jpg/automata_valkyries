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
        assertAttackRecorded(helper, "more_avaritia");
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
        assertAttackRecorded(helper, "eca");
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
     * No-Sugar のストップウォッチ（時間停止）: ワールドの Tick ごと止められても、保護中のエレノアは動き続け、退避もしない。
     * 時間停止の間は同じワールドの他の GameTest も止まるので、別の batch で単独に走らせる。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "nosugar_time_stop")
    public static void noSugarTimeStopDoesNotFreezeElenor(GameTestHelper helper) {
        if (!loaded(helper, "nosugar")) return;
        ElenorEntity elenor = spawnElenor(helper);
        Zombie stopper = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        Zombie bystander = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 4));
        ServerLevel level = helper.getLevel();
        String manager = "com.test.nosugar.utils.TimeStopManager";
        Class<?>[] levelAndEntity = {net.minecraft.world.level.Level.class, net.minecraft.world.entity.Entity.class};
        int[] ticked = new int[2];
        // 時間停止の間はゲーム時刻が止まり、GameTest の遅延（ゲーム時刻で数える）も進まない。解除はサーバー Tick で数える
        TimeStopRelease release = new TimeStopRelease(30, () -> {
            ticked[0] = elenor.tickCount - ticked[0];
            ticked[1] = bystander.tickCount - ticked[1];
            invoke(helper, manager, "Resume", levelAndEntity, level, stopper);
            invoke(helper, manager, "clearLevel", new Class<?>[0]);
        });
        helper.runAfterDelay(2, () -> {
            ticked[0] = elenor.tickCount;
            ticked[1] = bystander.tickCount;
            invoke(helper, manager, "startStop", levelAndEntity, level, stopper);
            MinecraftForge.EVENT_BUS.register(release);
        });
        // 解除の後でしか来ない（停止中はゲーム時刻が 2 から進まない）
        helper.runAfterDelay(4, () -> {
            stopper.discard();
            bystander.discard();
            helper.assertTrue(release.done, "The time stop was not released");
            // 時間停止が本当に効いていたこと（止められた側は進まない）
            helper.assertTrue(ticked[1] <= 1, "The time stop did not freeze the bystander (" + ticked[1] + " ticks)");
            // 30 Tick のうち、検出までの数 Tick を除いてほぼ毎 Tick 動いていること。
            // 準汎用の層だけでは No-Sugar が Entity.tick の入口で止めるのを外せない（互換 Mixin の CanMove が要る）ので、退避しないことだけ見る
            if (com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
                helper.assertTrue(ticked[0] >= 20, "Elenor was frozen by the time stop (" + ticked[0] + " ticks in 30)");
            } else {
                // 代わりに回した Tick も止められるので、入口に差し込んだ No-Sugar の処理が候補として記録されていること
                helper.assertTrue(com.automatavalkyries.protect.attack.AttackLog.entries().stream().anyMatch(e ->
                                e.mod.equals("nosugar") && e.op.startsWith("frozen_tick:")
                                        && e.outcome == com.automatavalkyries.protect.attack.AttackLog.Outcome.DETECTED),
                        "No-Sugar was not named as the one stopping the tick");
            }
            helper.assertTrue(elenor.getEprotect().isProtectionActive(), "Elenor retreated during the time stop");
            helper.succeed();
        });
    }

    /** サーバー Tick を数えて、決まった数の後に 1 度だけ処理を走らせる（時間停止の解除用） */
    public static final class TimeStopRelease {
        private final Runnable action;
        private int remaining;
        volatile boolean done = false;

        TimeStopRelease(int serverTicks, Runnable action) {
            this.remaining = serverTicks;
            this.action = action;
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || done || --remaining > 0) return;
            done = true;
            MinecraftForge.EVENT_BUS.unregister(this);
            action.run();
        }
    }

    /**
     * No-Sugar の不具合の修正（NoSugarMixins.DestroyModeTag）: 手持ちを読むだけの DestroyMode.getMode / isSilkTouchEnabled が
     * 空のタグ {} を付けないこと。付くとストップウォッチが所持判定に落ちて使えなくなっていた。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void noSugarModeReadKeepsStackTagless(GameTestHelper helper) {
        if (!loaded(helper, "nosugar")) return;
        net.minecraft.world.item.Item watch = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nosugar", "stop_watch"));
        if (watch == null) {
            helper.fail("nosugar:stop_watch is not registered");
            return;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(watch);
        invoke(helper, "com.test.nosugar.utils.DestroyMode", "getMode",
                new Class<?>[]{net.minecraft.world.item.ItemStack.class}, stack);
        invoke(helper, "com.test.nosugar.utils.DestroyMode", "isSilkTouchEnabled",
                new Class<?>[]{net.minecraft.world.item.ItemStack.class}, stack);
        if (stack.getTag() != null) helper.fail("No-Sugar added a tag while only reading the mode: " + stack.getTag());
        else helper.succeed();
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

    /**
     * 超絶抹殺の棒 × The Trial Monolith の侵略者のモノリス（HP は常に無限大、魂ダメージでしか死なない、remove は空）。
     * しばらく待っても同じ種類の個体が残っていない（復活もしない）こと
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ultraKillStickErasesInvaderMonolith(GameTestHelper helper) {
        annihilateTrialMonolithBoss(helper, "invader_monolith");
    }

    /**
     * 超絶抹殺の棒 × テッセラクトビースト（エンティティは代理で、本体のコントローラーが代理を作り直す）。
     * 代理を消すだけでは復活するので、本体ごと消えていること
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ultraKillStickErasesTesseractBeast(GameTestHelper helper) {
        annihilateTrialMonolithBoss(helper, "tesseract_beast");
    }



    /**
     * フラグ辞書による同期データの入口での拒否: The Trial Monolith が vanilla に足した魂ダメージの同期データを、
     * 専用の関数（EntityHelper）を通さず直接書いても保護対象には溜まらない。下げる書き込みは通す
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("unchecked")
    public static void injectedSynchedKillMarkIsRefused(GameTestHelper helper) {
        if (!loaded(helper, "the_trial_monolith")) return;
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.Entity.class.getDeclaredField("the_trial_monolith$DATA_SOUL_DAMAGE_ID");
            field.setAccessible(true);
            var accessor = (net.minecraft.network.syncher.EntityDataAccessor<Float>) field.get(null);
            helper.assertTrue(com.automatavalkyries.protect.flag.StateScanner.injectedDataKey(accessor) != null,
                    "The injected soul damage key was not recognised");
            elenor.getEntityData().set(accessor, 50.0F);
            helper.assertTrue(elenor.getEntityData().get(accessor) == 0.0F, "Soul damage was written: " + elenor.getEntityData().get(accessor));
            helper.assertTrue(com.automatavalkyries.protect.attack.AttackLog.entries().stream()
                    .anyMatch(e -> e.op.startsWith("synched_data:") && e.mod.equals("the_trial_monolith")), "The refusal was not recorded");
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not reach the soul damage key: " + e);
        }
        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    private static void annihilateTrialMonolithBoss(GameTestHelper helper, String path) {
        if (!loaded(helper, "the_trial_monolith")) return;
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("the_trial_monolith", path));
        if (type == null) {
            helper.fail("Entity type the_trial_monolith:" + path + " is not registered");
            return;
        }
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(2, () -> {
            net.minecraft.world.entity.Entity boss = helper.spawn(type, new BlockPos(3, 2, 3));
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(boss instanceof net.minecraft.world.entity.LivingEntity, path + " is not a living entity");
                AnnihilationExecutionAgent.annihilateEntity(level, helper.makeMockPlayer(), (net.minecraft.world.entity.LivingEntity) boss);
                helper.runAfterDelay(60, () -> {
                    net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(64.0D);
                    var remaining = level.getEntities(type, area, e -> true);
                    helper.assertTrue(remaining.isEmpty(), path + " survived or came back: " + remaining);
                    helper.assertTrue(level.getEntity(boss.getUUID()) == null, path + " is still in the level lookup");
                    // 消した直後は、同じ種類の作り直し（別の UUID での復活）も近くでは拒否される
                    net.minecraft.world.entity.Entity recreated = helper.spawn(type, new BlockPos(3, 2, 3));
                    helper.assertTrue(level.getEntity(recreated.getUUID()) == null, path + " was re-created right after the annihilation");
                    helper.succeed();
                });
            });
        });
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

    /** 出現位置（攻撃で飛ばされる前の座標。戻ったかの判定に使う） */
    private static final java.util.Map<ElenorEntity, net.minecraft.world.phys.Vec3> SPAWN_POSITIONS = new java.util.WeakHashMap<>();

    private static ElenorEntity spawnElenor(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        SPAWN_POSITIONS.put(elenor, elenor.position());
        return elenor;
    }

    /** Hyperlink: NovelHandler が null エンティティで呼ばれても NPE で落ちずに安全に false を返すこと */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void hyperlinkNovelHandlerNullSafety(GameTestHelper helper) {
        if (!loaded(helper, "hyperdaimc")) return;
        try {
            Class<?> nhClass = Class.forName("com.sakurafuld.hyperdaimc.content.hyper.novel.NovelHandler");
            Method novelizedMethod = nhClass.getDeclaredMethod("novelized", net.minecraft.world.entity.Entity.class);
            novelizedMethod.setAccessible(true);
            Object res = novelizedMethod.invoke(null, (Object) null);
            helper.assertTrue(Boolean.FALSE.equals(res), "NovelHandler.novelized(null) should return false");

            Method specialMethod = nhClass.getDeclaredMethod("special", net.minecraft.world.entity.Entity.class);
            specialMethod.setAccessible(true);
            Object specialRes = specialMethod.invoke(null, (Object) null);
            helper.assertTrue(Boolean.FALSE.equals(specialRes), "NovelHandler.special(null) should return false");

            Method novelizeMethod = nhClass.getDeclaredMethod("novelize", net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.entity.Entity.class, boolean.class);
            novelizeMethod.setAccessible(true);
            novelizeMethod.invoke(null, null, null, false);

            Player p = helper.makeMockPlayer();
            com.automatavalkyries.protect.EprotectHyperlinkCounter.counter(p);
            helper.assertFalse(com.automatavalkyries.protect.EprotectHyperlinkCounter.isNovelized(p), "Mock player reported novelized after counter");
        } catch (Throwable t) {
            helper.fail("NovelHandler threw exception on null: " + t.getMessage());
            return;
        }
        helper.succeed();
    }

    /** Omni-Mobs: 解体後の Flashfur メモリパージおよび UUID 抹消照合が正常に動作すること */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void omniMobsFlashfurPurgeTest(GameTestHelper helper) {
        UUID testUuid = UUID.randomUUID();
        AnnihilationExecutionAgent.markAnnihilated(testUuid);
        helper.assertTrue(AnnihilationExecutionAgent.isAnnihilated(testUuid), "UUID should be marked as annihilated");

        // purgeDeadFlashfurs() および wipeFlashfurLevelMemory() が安全に例外なく完了すること
        AnnihilationExecutionAgent.purgeDeadFlashfurs();
        AnnihilationExecutionAgent.wipeFlashfurLevelMemory();
        helper.succeed();
    }

    /**
     * The Last Sword / ECA: プレイヤーが死亡または手放した際、EntityLocationManager の位置固定・ヘルスロック・無敵・飛行タグが
     * 即座に解除され、移動不能や死地への強制引き戻しが発生しないこと。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 50)
    public static void ecaPlayerRestraintsAreClearedOnDropOrDeath(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getPersistentData().putBoolean("UltraTestSwordDefence", true);
        player.getPersistentData().putBoolean("TheLastSwordFly", true);
        player.getPersistentData().putBoolean("TheLastSwordDefence", true);
        com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);

        helper.assertFalse(player.getPersistentData().contains("UltraTestSwordDefence"), "UltraTestSwordDefence tag remained");
        helper.assertFalse(player.getPersistentData().contains("TheLastSwordFly"), "TheLastSwordFly tag remained");
        helper.assertFalse(player.getPersistentData().contains("TheLastSwordDefence"), "TheLastSwordDefence tag remained");
        helper.assertFalse(com.automatavalkyries.protect.EcaLastSwordHelper.hasDefenseSword(player), "Mock player reported having defense sword");
        helper.succeed();
    }

    /**
     * 残存フラグの完全パージの検証:
     * リスポーン時や手放し時に EcaLastSwordHelper.cleanupPlayerRestraints が
     * ECA や Hyperlink の残存フラグを正常にパージすること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 50)
    public static void cleanupPlayerRestraintsPurgesLingeringFlags(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.getPersistentData().putString("hyperdaimc:NovelDead", "1");
        player.getPersistentData().putString("ecaHealBanValue", "0.0");
        com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);
        helper.assertFalse(player.getPersistentData().contains("hyperdaimc:NovelDead"), "hyperdaimc:NovelDead was not purged");
        helper.assertFalse(player.getPersistentData().contains("ecaHealBanValue"), "ecaHealBanValue was not purged");
        helper.succeed();
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

    /** 攻撃の記録に、その MOD の名前で止めた記録が残っていること（どの MOD の攻撃かを割り出せているか） */
    private static void assertAttackRecorded(GameTestHelper helper, String modId) {
        // 準汎用の層だけを測る設定では、攻撃の記録（互換 Mixin が付ける）は無いことがある。守れたかだけを見る
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        helper.assertTrue(com.automatavalkyries.protect.attack.AttackLog.entries().stream().anyMatch(e -> e.mod.equals(modId)),
                "No attack from " + modId + " was recorded");
    }

    private static void assertSurvivesAfterRepair(GameTestHelper helper, ElenorEntity elenor) {
        net.minecraft.world.phys.Vec3 spawnPos = SPAWN_POSITIONS.getOrDefault(elenor, elenor.position());
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
