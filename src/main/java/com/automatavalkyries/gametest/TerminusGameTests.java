package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.automatavalkyries.annihilate.GenericCheatMobNeutralizer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import com.mojang.authlib.GameProfile;

import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import java.util.UUID;

/**
 * 終焉執行機構（Terminus Engine）および MOB・プレイヤー終焉機能の GameTest。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class TerminusGameTests {

    private static final String TEMPLATE = "empty";

    /**
     * Eprotect+ による外部干渉の検査・迂回・兵姫（エレノア）の保護確認（真抹殺の棒の GameTest から移した）。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eprotectPlusBypassesInterferenceAndProtectsElenor(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        // 外部干渉をシミュレート
        EprotectPlusLink.recordInterference(elenor.getClass().getName(), "TestMockMod", "fakeHook", "Simulated interference hook");
        EprotectPlusLink.inspectAndBypassInterference(elenor);

        var events = EprotectPlusLink.getEvents();
        helper.assertFalse(events.isEmpty(), "EprotectPlusLink did not record any events");

        helper.assertTrue(elenor.isAlive(), "Elenor is dead");
        helper.assertTrue(elenor.getEprotect().isProtectionActive(), "Elenor protection was deactivated");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * TerminusEngine:
     * MOB に対して汎用解体と絶対抹殺が執行されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusEngineEradicatesCheatMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var skeleton = helper.spawnWithNoFreeWill(EntityType.WITHER_SKELETON, new BlockPos(4, 2, 4));
        Player attacker = helper.makeMockPlayer();

        // 終焉執行
        boolean zDone = TerminusEngine.executeTerminus(level, attacker, zombie);
        boolean sDone = TerminusEngine.executeTerminus(level, attacker, skeleton);

        helper.assertTrue(zDone, "Terminus execution failed on a zombie");
        helper.assertTrue(sDone, "Terminus execution failed on a wither skeleton");

        helper.assertTrue(zombie.isRemoved(), "The zombie was not marked as removed");
        helper.assertTrue(skeleton.isRemoved(), "The wither skeleton was not marked as removed");

        helper.succeed();
    }

    /**
     * TerminusEngine:
     * クリエイティブモード・無敵フラグのプレイヤーに対しても絶対即死が執行されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusEngineExecutesAbsolutePlayerDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "test-player"));
        target.setGameMode(GameType.CREATIVE);
        target.getAbilities().invulnerable = true;
        target.setHealth(20.0F);

        Player attacker = helper.makeMockPlayer();

        // クリエイティブ・無敵のプレイヤーに対して終焉執行
        boolean done = TerminusEngine.executeTerminus(level, attacker, target);
        helper.assertTrue(done, "Terminus execution failed on player");

        // サバイバル化、無敵解除、HPゼロ化、死亡状態の検証
        helper.assertTrue(target.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Player game mode was not converted to survival");
        helper.assertFalse(target.getAbilities().invulnerable, "Player invulnerability was not stripped");
        helper.assertTrue(target.getHealth() <= 0.0F, "Player health was not reduced to zero");
        helper.assertTrue(target.isDeadOrDying(), "Player is not dead or dying");

        helper.succeed();
    }

    /**
     * エレノアの終焉モード:
     * 終焉権能を有効化したエレノアが攻撃した時、相手を一撃で終焉執行できること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void elenorTerminusModeKillsCheatBosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        var witherzilla = helper.spawnWithNoFreeWill(EntityType.WITHER_SKELETON, new BlockPos(3, 2, 3));

        // 終焉モード有効化
        elenor.setTerminusEnabled(true);
        helper.assertTrue(elenor.isTerminusEnabled(), "Elenor terminus mode was not enabled");

        // エレノアがチートボスを攻撃
        boolean hit = elenor.doHurtTarget(witherzilla);
        helper.assertTrue(hit, "Elenor attack returned false");

        // チートボスの完全消滅を確認
        helper.assertTrue(witherzilla.isRemoved(), "The target was not removed by Elenor Terminus attack");

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 最果てのエレノア: 攻撃してきた相手に終焉で反撃し、自分は保護されたまま残ること。終焉は切り替えられないこと。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void farthestElenorRetaliatesWithTerminus(GameTestHelper helper) {
        var farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(2, 2, 2));
        Zombie attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));

        farthest.setTerminusEnabled(false);
        helper.assertTrue(farthest.isTerminusEnabled(), "The Farthest Elenor's Terminus could be turned off");

        farthest.hurt(helper.getLevel().damageSources().mobAttack(attacker), 5.0F);
        helper.assertTrue(attacker.isRemoved(), "The attacker was not eliminated by the counterattack");
        helper.assertTrue(farthest.isAlive() && farthest.getEprotect().isProtectionActive(),
                "The Farthest Elenor lost her protection");

        farthest.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * 広域終焉執行:
     * executeAreaTerminus により範囲内の MOB が一括消滅すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void areaTerminusEradicatesCheatEntities(GameTestHelper helper) {
        com.automatavalkyries.annihilate.SpawnBanZone.clear();
        ServerLevel level = helper.getLevel();
        Zombie w = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var f = helper.spawnWithNoFreeWill(EntityType.WITHER_SKELETON, new BlockPos(4, 2, 4));

        Player player = helper.makeMockPlayer();
        net.minecraft.world.phys.Vec3 center = helper.absolutePos(new BlockPos(3, 2, 3)).getCenter();
        player.setPos(center.x, center.y, center.z);

        int count = TerminusEngine.executeAreaTerminus(level, player, 16.0, false);
        helper.assertTrue(count >= 2, "Expected at least 2 entities eradicated, got " + count);

        helper.assertTrue(w.isRemoved() && f.isRemoved(), "Entities were not removed by Area Terminus");
        helper.succeed();
    }

    /**
     * 終焉準汎用処理（解体）:
     * 未知 MOD のチートコアオブジェクトが持つ防御フラグ、タイマー、残機、リスト、終了メソッドが
     * フラグ辞書により動的解体・無力化されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusGenericNeutralizerCleansUnknownCheatCore(GameTestHelper helper) {
        FakeUnknownCheatCore core = new FakeUnknownCheatCore();
        GenericCheatMobNeutralizer.dismantleCoreObject(core, null);

        helper.assertTrue(core.isDead, "isDead was not set to true");
        helper.assertFalse(core.immortalState, "immortalState was not reset to false");
        helper.assertFalse(core.invulnerableBarrier, "invulnerableBarrier was not reset to false");
        helper.assertTrue(core.resurrectLives == 0, "resurrectLives was not reset to 0");
        helper.assertTrue(core.respawnTimer == -9999, "respawnTimer was not frozen to -9999");
        helper.assertTrue(core.shieldDelta == 0.0F, "shieldDelta was not reset to 0");
        helper.assertTrue(core.protectedWhitelist.isEmpty(), "protectedWhitelist was not cleared");
        helper.assertTrue(core.closeCalled, "close() termination method was not invoked");

        helper.succeed();
    }

    /**
     * 終焉準汎用処理（プレイヤー）:
     * 未知 MOD の不死アイテム（Totem等）やチート防具、PersistentData の神モードタグを持つプレイヤーに対して、
     * フラグ辞書による汎用アイテム剥奪とタグ消去が行われ、確実に絶対死が執行されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusExecutesOnPlayerHoldingUnknownCheatItemsAndDefensiveTags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer target = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "cheat-player"));
        target.setHealth(20.0F);

        // 未知 MOD のチートアイテム（不死トーテム互換アイテム、神の盾）をインベントリに持たせる
        ItemStack cheatTotem = new ItemStack(Items.FEATHER);
        cheatTotem.setHoverName(Component.literal("Immortal Soul Totem"));
        cheatTotem.getOrCreateTag().putBoolean("undying_charm", true);

        ItemStack godArmor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        godArmor.setHoverName(Component.literal("Invincible God Armor"));

        target.getInventory().setItem(0, cheatTotem);
        target.setItemSlot(EquipmentSlot.CHEST, godArmor);

        // プレイヤーの PersistentData に他 MOD による無敵タグを付与
        target.getPersistentData().putBoolean("unknown_mod_godmode", true);
        target.getPersistentData().putBoolean("undying_blessing", true);

        Player attacker = helper.makeMockPlayer();

        // 終焉執行
        boolean done = TerminusEngine.executeTerminus(level, attacker, target);
        helper.assertTrue(done, "Terminus execution failed on cheat player");

        // インベントリと装備枠から防御アイテムが剥奪されていること
        helper.assertTrue(target.getInventory().getItem(0).isEmpty(), "Cheat totem was not stripped from inventory");
        helper.assertTrue(target.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "God armor was not stripped from chest slot");

        // PersistentData の防御フラグがパージされていること
        helper.assertFalse(target.getPersistentData().contains("unknown_mod_godmode"), "godmode tag was not purged from persistent data");
        helper.assertFalse(target.getPersistentData().contains("undying_blessing"), "undying tag was not purged from persistent data");

        // プレイヤーが死亡していること
        helper.assertTrue(target.getHealth() <= 0.0F, "Player health was not reduced to zero");
        helper.assertTrue(target.isDeadOrDying(), "Player is not dead or dying");

        helper.succeed();
    }

    /**
     * Eprotect+:
     * ブラックリストに入った MOD の攻撃を受けた際、Eprotect+ 強化処理が発動し、
     * 10秒間強化防御が作動してダメージが完全無効化（0.0F）されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eprotectPlusReinforcesAgainstBlacklistMod(GameTestHelper helper) {
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        String enemyMod = "hostile_cheat_mod";
        ModBlacklist.setPersistDisabledForTest(true);
        ModBlacklist.add(enemyMod, ModBlacklist.Source.MANUAL, false);

        try {
            ServerPlayer enemyPlayer = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "enemy-player"));
            DamageSource hostileSource = helper.getLevel().damageSources().playerAttack(enemyPlayer);

            // ブラックリスト強化トリガーを発動
            EprotectPlusLink.triggerBlacklistReinforcement(enemyMod);

            helper.assertTrue(EprotectPlusLink.isReinforcedActive(), "Eprotect+ reinforced mode is not active");
            helper.assertTrue(elenor.getEprotect().isReinforcedActive(), "Elenor's Eprotect did not report reinforced mode active");
            helper.assertTrue(EprotectPlusLink.getReinforcedReason().contains(enemyMod), "Reinforced reason does not contain blacklist mod ID");

            // 強化モード中のダメージ遮断テスト
            float cappedDamage = elenor.getEprotect().capFinalDamage(50.0F, hostileSource);
            helper.assertTrue(cappedDamage == 0.0F, "Damage was not 0.0F during reinforced mode: " + cappedDamage);

            // 毎 Tick の修復で体力が全快維持されること
            elenor.setHealth(10.0F);
            elenor.getEprotect().tick();
            helper.assertTrue(elenor.getHealth() == elenor.getMaxHealth(), "Health was not restored to max during reinforced mode");
        } finally {
            ModBlacklist.remove(enemyMod, false);
            EprotectPlusLink.resetReinforced();
        }

        elenor.getEprotect().triggerDespawn();
        helper.succeed();
    }

    /**
     * TerminusEngine:
     * 終焉執行に対象が10秒（200 ticks / 10,000ms）以上耐えた場合、
     * Eprotect+ 強化処理が10秒間実行されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void terminusResistedFor10SecondsTriggersReinforcedProtection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));

        try {
            // 終焉の対象となるダミーの耐性 MOB を作成
            Zombie resistantMob = new Zombie(EntityType.ZOMBIE, level);
            resistantMob.setPos(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());

            // 終焉エンジンに対象を登録
            TerminusEngine.trackTarget(resistantMob, level);
            helper.assertTrue(TerminusEngine.hasTrackedTarget(resistantMob.getUUID()), "Target was not tracked by TerminusEngine");

            // 耐性発動（手動シミュレーションまたは時間経過チェック）
            boolean triggered = TerminusEngine.triggerResistedManually(resistantMob);
            helper.assertTrue(triggered, "Terminus resistance reinforcement failed to trigger");

            // Eprotect+ 強化処理が10秒間発動していることを検証
            helper.assertTrue(EprotectPlusLink.isReinforcedActive(), "Reinforced protection is not active after 10s resistance");
            helper.assertTrue(elenor.getEprotect().isReinforcedActive(), "Elenor did not recognize reinforced protection");
            helper.assertTrue(EprotectPlusLink.getReinforcedReason().contains("TERMINUS_RESISTED_10S"),
                    "Reinforced reason does not specify TERMINUS_RESISTED_10S: " + EprotectPlusLink.getReinforcedReason());
            helper.assertTrue(EprotectPlusLink.getReinforcedRemainingMs() > 0, "Reinforced remaining time is not positive");

            // 強化モード中は即死・大ダメージも完全遮断されること
            float cap = elenor.getEprotect().capFinalDamage(999.0F, level.damageSources().generic());
            helper.assertTrue(cap == 0.0F, "Damage was not 0.0F during terminus-resisted reinforced mode: " + cap);
        } finally {
            TerminusEngine.clearTrackedTargets();
            EprotectPlusLink.resetReinforced();
            elenor.getEprotect().triggerDespawn();
        }

        helper.succeed();
    }

    public static class FakeUnknownCheatCore {
        public boolean immortalState = true;
        public boolean invulnerableBarrier = true;
        public boolean isDead = false;
        public int resurrectLives = 5;
        public int respawnTimer = 200;
        public float shieldDelta = 50.0F;
        public java.util.List<String> protectedWhitelist = new java.util.ArrayList<>(java.util.List.of("safe_zone"));
        public boolean closeCalled = false;

        public void close() {
            this.closeCalled = true;
        }
    }
}
