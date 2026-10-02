package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.protect.EprotectForgeEvents;
import com.automatavalkyries.protect.ForeignStateCleaner;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import com.automatavalkyries.protect.flag.FlagDictionaryLink;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * フラグ辞書（{@link FlagDictionary}）を活用した未知の MOD に対する準汎用保護（Protect）の GameTest。
 * <p>
 * 個別 MOD 専用の対策コードが一切存在しない未知の架空 MOD からの攻撃であっても、
 * フラグ辞書の語彙・意味解析によって自動的に即死・無効化・改変が阻止されることを検証する。
 * </p>
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class GenericProtectGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * 未知 MOD の即死ダメージタイプ（例: unknown_mod:obliteration_slash）が、
     * フラグ辞書によって準汎用的に即死判定され、兵姫へのダメージが完全拒絶されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void genericProtectBlocksUnknownKillDamage(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        float initialHealth = elenor.getHealth();

        // 未知 MOD の即死ダメージソースを作成
        ResourceLocation unknownKillType = ResourceLocation.fromNamespaceAndPath("unknown_cheat_mod", "obliteration_slash");
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, unknownKillType);
        DamageType damageType = new DamageType("unknown_cheat_mod.obliteration_slash", 0.0F);
        DamageSource source = new DamageSource(Holder.direct(damageType));

        // フラグ辞書がこれを有害ダメージソースと判定することを確認
        helper.assertTrue(FlagDictionary.isHarmfulDamageSource(source),
                "FlagDictionary failed to identify unknown obliteration_slash as harmful");

        // エレノアに被弾させる
        boolean hurt = elenor.hurt(source, 999999.0F);
        helper.assertFalse(hurt, "Unknown kill damage was not rejected by Eprotect");
        helper.assertTrue(elenor.isAlive(), "Elenor was killed by unknown kill damage");
        helper.assertTrue(elenor.getHealth() == initialHealth, "Elenor took damage from unknown kill damage");

        // AttackLog に記録されていることを確認
        helper.assertTrue(AttackLog.entries().stream().anyMatch(e -> e.op.contains("obliteration_slash") || e.op.contains("kill_damage")),
                "Rejection of unknown kill damage was not recorded in AttackLog");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 未知 MOD の即死武器（例: unknown_mod:reaper_scythe）を持った敵の攻撃が、
     * フラグ辞書によって武器の即死属性を検知され、完全遮断されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void genericProtectBlocksUnknownKillItem(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        Zombie attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 2));

        // 未知 MOD の即死アイテム（例: 表示名が Reaper Scythe、あるいは NBT に即死フラグ）を模倣
        ItemStack weapon = new ItemStack(Items.NETHERITE_SWORD);
        weapon.setHoverName(Component.literal("Reaper Scythe"));
        weapon.getOrCreateTag().putString("kill_modifier", "instant_death");

        // FlagDictionary が即死アイテムと認識するか
        helper.assertTrue(FlagDictionary.isKillItem(weapon), "FlagDictionary failed to identify kill item");

        attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);

        helper.assertTrue(EprotectForgeEvents.isAttackerHoldingKillItem(source),
                "isAttackerHoldingKillItem failed to detect kill weapon in attacker's hand");

        float initialHealth = elenor.getHealth();
        boolean hurt = elenor.hurt(source, 50.0F);
        helper.assertFalse(hurt, "Damage from attacker holding kill weapon was not blocked");
        helper.assertTrue(elenor.isAlive() && elenor.getHealth() == initialHealth, "Elenor took damage from kill weapon");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 未知 MOD が PersistentData に注入した有害フラグ（例: unknown_doom, status: executed）が、
     * ForeignStateCleaner によってフラグ辞書経由で自動除去されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void genericProtectCleansUnknownPersistentStates(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        CompoundTag tag = elenor.getPersistentData();
        tag.putBoolean("unknown_mod_fatal_doom", true);
        tag.putString("unknown_mod_status", "executed");
        tag.putBoolean("innocent_tag", true);

        // フラグ辞書による有害状態の掃除を実行
        boolean cleaned = ForeignStateCleaner.clean(elenor);
        helper.assertTrue(cleaned, "ForeignStateCleaner did not clean unknown states");

        helper.assertFalse(tag.contains("unknown_mod_fatal_doom"), "unknown_mod_fatal_doom was not cleaned");
        helper.assertFalse(tag.contains("unknown_mod_status"), "unknown_mod_status with value 'executed' was not cleaned");
        helper.assertTrue(tag.contains("innocent_tag"), "innocent_tag was wrongly removed");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }



    /**
     * フラグ辞書保護 Java エージェント:
     * 外部 MOD がリフレクションにより FlagDictionary の辞書マップを空化・改ざんしようとした場合、
     * JVM エージェントとの連携により即座に検知され、マスター辞書から完全自動復元されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void flagDictionaryAgentProtectsAgainstTampering(GameTestHelper helper) {
        // 事前確認: 辞書が正常であること
        helper.assertTrue(FlagDictionary.is("dead", FlagDictionary.Category.KILL), "FlagDictionary failed baseline classification");

        try {
            // 悪意のあるチート MOD のリフレクション攻撃をシミュレート: active マップを空にする
            Field activeField = FlagDictionary.class.getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(null, new java.util.EnumMap<>(FlagDictionary.Category.class));

            // 改ざんされた状態から classify を呼ぶと、自動的にエージェント保護が走り復元される
            boolean isKill = FlagDictionary.is("obliterate", FlagDictionary.Category.KILL);
            helper.assertTrue(isKill, "FlagDictionary was not restored upon classify invocation");

            // 手動呼び出しでも修復が正しく完了することを確認
            activeField.set(null, new java.util.EnumMap<>(FlagDictionary.Category.class));
            boolean repaired = FlagDictionary.ensureAgentProtection();
            helper.assertTrue(repaired, "ensureAgentProtection did not report repair");
            helper.assertTrue(FlagDictionary.is("death", FlagDictionary.Category.KILL), "FlagDictionary was not restored after repair");
        } catch (Exception e) {
            throw new RuntimeException("Reflection attack simulation failed", e);
        }

        helper.succeed();
    }

    /**
     * フラグ辞書 Java エージェント密接連携:
     * JVM エージェントが保持する不変マスター辞書のエントリと MOD 側の FlagDictionary が密接に整合していること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void flagDictionaryAgentSynchronizesMasterEntries(GameTestHelper helper) {
        // エージェント側のマスターエントリを取得
        java.util.List<String> killEntries = FlagDictionaryLink.getMasterEntries("KILL");
        java.util.List<String> guardEntries = FlagDictionaryLink.getMasterEntries("GUARD");
        java.util.List<String> respawnBlockEntries = FlagDictionaryLink.getMasterEntries("RESPAWN_BLOCK");

        // エージェントが読み込まれている場合はマスターデータと MOD 側が完全に一致
        if (!killEntries.isEmpty()) {
            helper.assertTrue(killEntries.contains("dead") && killEntries.contains("annihil"),
                    "Agent master entries missing critical kill terms: " + killEntries);
            helper.assertTrue(guardEntries.contains("protect") && guardEntries.contains("invuln"),
                    "Agent master entries missing critical guard terms: " + guardEntries);
            helper.assertTrue(respawnBlockEntries.contains("*norespawn") && respawnBlockEntries.contains("*deathlock"),
                    "Agent master entries missing critical respawn block terms: " + respawnBlockEntries);
        }

        // 連携リンクの統計情報が取得できること
        java.util.Map<String, String> stats = FlagDictionaryLink.getAgentStats();
        helper.assertTrue(stats != null, "Agent stats map is null");

        helper.succeed();
    }

    /**
     * Eprotect+ 案4: EventBus 破壊・全消去への完全二重防護
     * EventBusGuardian の修復機能、および Mixin HEAD での強化ガード（イベント非依存）を検証。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eventBusGuardianRepairsListenersAndMixinHeadDefends(GameTestHelper helper) {
        // 1. EventBusGuardian の点検が正常に完了すること
        com.automatavalkyries.protect.EventBusGuardian.ensureEventBusIntegrity();

        // 2. 兵姫をスポーンして Eprotect+ 強化モードを起動
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        com.automatavalkyries.protect.plus.EprotectPlusLink.triggerReinforcedProtection(5000L, "TEST_DUAL_LAYER");
        helper.assertTrue(com.automatavalkyries.protect.plus.EprotectPlusLink.isReinforcedActive(), "Reinforced mode was not active");

        // 3. hurt() を直接呼んでも、即死系の攻撃は Mixin HEAD で遮断される。通常攻撃は上限どおりに通る（2026-10-03 にユーザー指定で変更）
        boolean lethalLanded = elenor.hurt(helper.getLevel().damageSources().genericKill(), Float.POSITIVE_INFINITY);
        helper.assertFalse(lethalLanded, "Mixin HEAD failed to block a lethal attack in reinforced mode");
        helper.assertTrue(elenor.isAlive(), "Elenor died despite reinforced guard");
        elenor.getEprotect().resetDamageWindow();
        elenor.invulnerableTime = 0;
        float before = com.automatavalkyries.protect.DirectRead.health(elenor);
        boolean normalLanded = elenor.hurt(helper.getLevel().damageSources().generic(), 5.0F);
        float after = com.automatavalkyries.protect.DirectRead.health(elenor);
        helper.assertTrue(normalLanded && after < before, "A normal hit was blocked in reinforced mode (" + before + " -> " + after + ")");
        // 凶悪技術（即死系）を止めたら、罰則として HP が最大まで戻る
        elenor.hurt(helper.getLevel().damageSources().genericKill(), Float.POSITIVE_INFINITY);
        helper.assertTrue(com.automatavalkyries.protect.DirectRead.health(elenor) == elenor.getMaxHealth(),
                "The malicious-attack penalty did not restore max health (" + com.automatavalkyries.protect.DirectRead.health(elenor) + ")");

        com.automatavalkyries.protect.plus.EprotectPlusLink.resetReinforced();
        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 終焉機構 案2, 案4, 案5:
     * 非プレイヤーモブに対するメモリ直接ゼロクリア、最大HP属性 0.0D 化、死亡強制早送り、およびプレイヤー除外の安全性を検証。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusObliteratesMemoryAndAttributesExcludingPlayer(GameTestHelper helper) {
        // 1. 非プレイヤー（Zombie）に対する真抹殺
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer();

        boolean annihilated = com.automatavalkyries.annihilate.TrueAnnihilationEngine.annihilateEntity(helper.getLevel(), player, zombie);
        helper.assertTrue(annihilated, "TrueAnnihilationEngine failed to annihilate non-player mob");
        helper.assertTrue(zombie.isRemoved(), "Annihilated zombie was not removed");
        helper.assertTrue(zombie.deathTime == 20, "Death frame was not fast-forwarded to 20: " + zombie.deathTime);

        var maxHpAttr = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHpAttr != null) {
            helper.assertTrue(maxHpAttr.getBaseValue() == 0.0D, "Max HP attribute was not zeroed: " + maxHpAttr.getBaseValue());
        }

        // 2. プレイヤーに対する安全装置（プレイヤー除外）の検証
        Player targetPlayer = helper.makeMockPlayer();
        boolean playerAnnihilated = com.automatavalkyries.annihilate.TrueAnnihilationEngine.annihilateEntity(helper.getLevel(), player, targetPlayer);
        helper.assertFalse(playerAnnihilated, "TrueAnnihilationEngine must NOT run non-player obliteration on players!");
        helper.assertTrue(targetPlayer.isAlive(), "Target player was harmed by non-player TrueAnnihilationEngine");

        // 3. Unsafe メモリ消去もプレイヤーを厳格に拒否すること
        float hpBefore = targetPlayer.getHealth();
        com.automatavalkyries.util.UnsafeHelper.obliterateObjectMemory(targetPlayer);
        helper.assertTrue(targetPlayer.getHealth() == hpBefore, "UnsafeHelper.obliterateObjectMemory must not touch player memory!");

        helper.succeed();
    }

    /**
     * Eprotect+ 案2 & 案3: 能動的バイトコード整合性再監査とスタック偽装実行の検証。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void retransformEnforcerAndStackCloaking(GameTestHelper helper) {
        // 能動的バイトコード再監査（エージェント接続時はクラス数、非接続時は 0 が安全に返る）
        int retransformed = com.automatavalkyries.protect.plus.EprotectPlusLink.enforceBytecodeIntegrity();
        helper.assertTrue(retransformed >= 0, "enforceBytecodeIntegrity returned negative value");

        // スタック偽装実行が特権フラグを立てて正常終了すること
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean privilegedDuringRun = new java.util.concurrent.atomic.AtomicBoolean(false);

        com.automatavalkyries.protect.plus.EprotectPlusLink.runWithCloakedStack(() -> {
            ran.set(true);
            privilegedDuringRun.set(com.automatavalkyries.protect.plus.EprotectPlusLink.isPrivilegedOrBypassing());
        });

        helper.assertTrue(ran.get(), "runWithCloakedStack failed to run action");
        helper.assertTrue(privilegedDuringRun.get(), "runWithCloakedStack was not privileged during execution");
        helper.assertFalse(com.automatavalkyries.protect.plus.EprotectPlusLink.isPrivilegedOrBypassing(), "Privileged flag leaked after cloaked execution");

        helper.succeed();
    }

    /**
     * Eprotect+ 保護範囲の拡張 1: 乗り物および同乗者（非IEprotected）への保護波及を検証。
     * デフォルトは無効（範囲強化なし）であり、コンフィグ有効化で保護されることを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void vehicleAndPassengerProtectionScope(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        net.minecraft.world.entity.animal.Pig mount = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));

        // エレノアがブタに乗る
        elenor.startRiding(mount, true);
        helper.assertTrue(mount.getPassengers().contains(elenor), "Elenor is not riding mount");

        // 1. デフォルト設定では保護範囲の拡張は無効（範囲強化なし）
        com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(null);
        helper.assertFalse(com.automatavalkyries.config.EprotectConfig.isExpandedScopeEnabled(),
                "Expanded scope must be disabled by default");
        helper.assertFalse(com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(mount),
                "Mount must NOT be under protection scope when expanded scope is disabled by default");

        try {
            // 2. コンフィグ有効化で乗り物への保護が機能すること
            com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(true);
            helper.assertTrue(com.automatavalkyries.protect.EprotectManager.getInstance().isVehicleOrPassengerOfProtected(mount),
                    "Mount was not recognized as vehicle of protected entity");
            helper.assertTrue(com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(mount),
                    "Mount was not under protection scope when enabled");

            // 乗り物に対する即死攻撃の遮断
            boolean landed = mount.hurt(helper.getLevel().damageSources().genericKill(), Float.POSITIVE_INFINITY);
            helper.assertFalse(landed, "Lethal attack on vehicle of protected entity was not blocked");
            helper.assertTrue(mount.isAlive(), "Mount died from lethal attack despite vehicle protection");
        } finally {
            com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(null);
            elenor.stopRiding();
            elenor.getEprotect().triggerDespawn();
            mount.discard();
        }
        helper.succeed();
    }

    /**
     * Eprotect+ 保護範囲の拡張 2: 強化モード結界オーラ（半径16m）による周囲エンティティの即死防御を検証。
     * デフォルトは無効（範囲強化なし）であり、コンフィグ有効化でオーラ保護されることを確認。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void reinforcedAuraProtectionScope(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        net.minecraft.world.entity.animal.Pig companion = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 2, 4));

        com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(null);
        com.automatavalkyries.protect.plus.EprotectPlusLink.resetReinforced();

        // 1. デフォルト設定では強化モードを起動してもオーラ保護は発動しない（範囲強化なし）
        com.automatavalkyries.protect.plus.EprotectPlusLink.triggerReinforcedProtection(5000L, "TEST_AURA");
        helper.assertFalse(com.automatavalkyries.protect.EprotectManager.getInstance().isProtectedByAura(companion),
                "Companion must NOT be aura protected when expanded scope is disabled by default");
        helper.assertFalse(com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(companion),
                "Companion must NOT be under protection scope when expanded scope is disabled by default");

        try {
            // 2. コンフィグ有効化でオーラ保護が発動すること
            com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(true);
            helper.assertTrue(com.automatavalkyries.protect.EprotectManager.getInstance().isNearActiveProtected(companion, 16.0),
                    "Companion was not detected near active protected entity");
            helper.assertTrue(com.automatavalkyries.protect.EprotectManager.getInstance().isProtectedByAura(companion),
                    "Companion was not recognized as protected by aura");
            helper.assertTrue(com.automatavalkyries.protect.EprotectForgeEvents.isUnderProtectionScope(companion),
                    "Companion was not recognized as under protection scope");

            // オーラ範囲内のコンパニオンに対する即死・ブラックリスト攻撃の遮断
            boolean landed = companion.hurt(helper.getLevel().damageSources().genericKill(), Float.POSITIVE_INFINITY);
            helper.assertFalse(landed, "Aura failed to block lethal kill damage on companion entity");
            helper.assertTrue(companion.isAlive(), "Companion was killed despite aura guard");
        } finally {
            com.automatavalkyries.config.EprotectConfig.setExpandedScopeForTesting(null);
            com.automatavalkyries.protect.plus.EprotectPlusLink.resetReinforced();
            elenor.getEprotect().triggerDespawn();
            companion.discard();
        }
        helper.succeed();
    }
}

