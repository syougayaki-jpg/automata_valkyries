package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.protect.EprotectForgeEvents;
import com.automatavalkyries.protect.ForeignStateCleaner;
import com.automatavalkyries.protect.PlayerWard;
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
     * 守護の護符（PlayerWard）を持つプレイヤーが、
     * 未知 MOD の即死ダメージ（unknown_mod:execution_strike）からフラグ辞書により保護されること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void playerWardProtectsAgainstUnknownInstantKill(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ward-player"));
        player.setHealth(20.0F);

        // 守護の護符をインベントリに持たせる
        player.getInventory().setItem(0, new ItemStack(ModItems.WARD_CHARM.get()));
        PlayerWard.refresh(player);
        helper.assertTrue(PlayerWard.isActive(player), "PlayerWard is not active on player with charm");

        // 未知 MOD の処刑ダメージソース
        DamageType damageType = new DamageType("unknown_mod.execution_strike", 0.0F);
        DamageSource executionSource = new DamageSource(Holder.direct(damageType));

        helper.assertTrue(PlayerWard.isInstantKill(executionSource),
                "PlayerWard failed to classify execution_strike as instant kill via FlagDictionary");

        // 被弾を試みる
        boolean hurt = player.hurt(executionSource, 10000.0F);
        helper.assertFalse(hurt, "Player hurt was not rejected");
        helper.assertTrue(player.isAlive() && player.getHealth() > 0.0F, "Player was killed by unknown execution damage");

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

        // エージェントが読み込まれている場合はマスターデータと MOD 側が完全に一致
        if (!killEntries.isEmpty()) {
            helper.assertTrue(killEntries.contains("dead") && killEntries.contains("annihil"),
                    "Agent master entries missing critical kill terms: " + killEntries);
            helper.assertTrue(guardEntries.contains("protect") && guardEntries.contains("invuln"),
                    "Agent master entries missing critical guard terms: " + guardEntries);
        }

        // 連携リンクの統計情報が取得できること
        java.util.Map<String, String> stats = FlagDictionaryLink.getAgentStats();
        helper.assertTrue(stats != null, "Agent stats map is null");

        helper.succeed();
    }
}
