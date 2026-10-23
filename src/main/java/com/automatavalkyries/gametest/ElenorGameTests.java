package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorArts;
import com.automatavalkyries.entity.ElenorPressure;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** エレノアの 3 フェーズの戦い方（2026-10-22） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ElenorGameTests {

    private static final String TEMPLATE = "empty";

    /** 凶悪度の分け方: 即死 MOD 専用の対策は操作の名前で分け、ブラックリストの MOD は一段だけ上げる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void viciousSeverityByOperation(GameTestHelper helper) {
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:erase", false) == ElenorPressure.Vicious.ERASURE, "erase should be IV");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:instantKill", false) == ElenorPressure.Vicious.DEATH, "instantKill should be III");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:setHealth", false) == ElenorPressure.Vicious.LETHAL, "setHealth should be II");
        helper.assertTrue(ElenorPressure.classify("compat:nosugar:timeStop", false) == ElenorPressure.Vicious.TAMPER, "time stop should be V");
        helper.assertTrue(ElenorPressure.classify("damage_penalty", true) == ElenorPressure.Vicious.LETHAL, "a blacklisted ordinary hit should be one step up (II)");
        helper.assertTrue(ElenorPressure.classify("compat:omnimobs:erase", true) == ElenorPressure.Vicious.TAMPER, "a blacklisted erase should be one step up (V)");
        helper.assertTrue(ElenorPressure.classify("retreat", false) == null, "retreat is not counted");
        helper.succeed();
    }

    /** 未知のダメージ属性: データで作った 4 種が読み込まれ、最果てはすべての貫通のタグに入っている */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void unknownDamageTypesBypass(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        for (String name : new String[]{"elenor_hollow", "elenor_ribbon", "elenor_afterimage", "elenor_farthest"}) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name));
            helper.assertTrue(registry.getHolder(key).isPresent(), name + " must be registered");
        }
        var farthest = new net.minecraft.world.damagesource.DamageSource(registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "elenor_farthest"))));
        helper.assertTrue(farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS)
                && farthest.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD),
                "elenor_farthest must be in every bypass tag");
        helper.succeed();
    }

    /** 第 2 は HP 60% 以下、第 3 は 30% 以下。最果てのエレノアは技を使わない */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void phasesFollowHealth(GameTestHelper helper) {
        helper.assertTrue(ElenorArts.phaseFor(1.0F) == 1 && ElenorArts.phaseFor(0.61F) == 1, "phase 1 range");
        helper.assertTrue(ElenorArts.phaseFor(0.6F) == 2 && ElenorArts.phaseFor(0.31F) == 2, "phase 2 range");
        helper.assertTrue(ElenorArts.phaseFor(0.3F) == 3 && ElenorArts.phaseFor(0.0F) == 3, "phase 3 range");
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(5, 2, 5));
        helper.assertTrue(elenor.arts() != null, "Elenor has no arts");
        helper.assertTrue(farthest.arts() != null && farthest.arts().fullPower(), "Farthest Elenor must fight at full power from the start");
        helper.assertTrue(farthest.arts().pressure().tier(helper.makeMockPlayer()) == ElenorPressure.MAX_TIER, "Farthest Elenor must start at tier 7");
        elenor.retreat();
        farthest.retreat();
        helper.succeed();
    }

    /** 標的（ダメージを受けないモックのプレイヤー）に向けて技を回し続けても落ちず、エレノアは生きている */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 400)
    public static void artsRunWithoutErrors(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        elenor.setTarget(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (elenor.arts() != null && ticks[0]++ < 300) {
                elenor.arts().tick(player);
                elenor.arts().tickProjectiles();
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(elenor.isAlive(), "Elenor died while running arts");
            elenor.retreat();
            helper.succeed();
        });
    }

    /** HP 10% 以下（第 3 フェーズ・カーテンコール・残像・リボンの縛めが選ばれうる）で技を回しても落ちない */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 400)
    public static void lowHealthArtsRunWithoutErrors(GameTestHelper helper) {
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        helper.assertTrue(elenor.arts() != null, "Elenor has no arts");
        elenor.arts().setTestHealthRatio(0.05F); // HP の直接の書き換えは Eprotect が止める
        elenor.setTarget(player);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (elenor.arts() != null && ticks[0]++ < 300) {
                elenor.arts().tick(player);
                elenor.arts().tickProjectiles();
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(elenor.arts() != null && elenor.arts().phase() == 3, "Elenor is not in phase 3");
            helper.assertTrue(elenor.isAlive(), "Elenor died while running arts");
            elenor.retreat();
            helper.succeed();
        });
    }

    /** 髪留めを兵姫に渡すと結ばれ（1 つ減る）、ハサミで外すと戻ってくる */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void hairRibbonTiesAndUnties(GameTestHelper helper) {
        var valkyrie = helper.spawn(ModEntities.VALKYRIE.get(), 2, 2, 2);
        valkyrie.setNoAi(true);
        var player = helper.makeMockPlayer();
        valkyrie.setOwnerUUID(player.getUUID());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get(), 2));
        valkyrie.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(valkyrie.hasHairRibbon(), "the ribbon was not tied");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "the ribbon was not consumed");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS));
        valkyrie.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!valkyrie.hasHairRibbon(), "the ribbon was not untied");
        helper.assertTrue(player.getInventory().countItem(com.automatavalkyries.registry.ModBossDrops.ELENOR_RIBBON.get()) >= 1, "the ribbon was not returned");
        valkyrie.releaseAndDiscard();
        helper.succeed();
    }

    /** ちっぽけな勇気: ホットバー（0〜8 番）にあれば守られ、リュックの奥（9 番以降）なら守られない。ボスのタグにエレノアが入っている */
    @GameTest(template = TEMPLATE, batch = "elenor")
    public static void tinyCourageProtectsOnlyFromHotbar(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var courage = new net.minecraft.world.item.ItemStack(com.automatavalkyries.registry.ModBossDrops.TINY_COURAGE.get());
        helper.assertTrue(!com.automatavalkyries.item.TinyCourageItem.protects(player), "protected without the item");
        player.getInventory().setItem(20, courage.copy());
        helper.assertTrue(!com.automatavalkyries.item.TinyCourageItem.protects(player), "an item outside the hotbar must not protect");
        player.getInventory().setItem(20, net.minecraft.world.item.ItemStack.EMPTY);
        player.getInventory().setItem(8, courage.copy());
        helper.assertTrue(com.automatavalkyries.item.TinyCourageItem.protects(player), "the hotbar item did not protect");
        helper.assertTrue(ModEntities.ELENOR.get().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES), "Elenor is not tagged as a boss");
        helper.assertTrue(ModEntities.FARTHEST_ELENOR.get().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES), "Farthest Elenor is not tagged as a boss");
        helper.succeed();
    }

    /** ペナルティのティア: 技が通らない（無敵の相手）と数えて上がり、過剰な攻撃・即死を感知すると上がりやすくなる。レベル 1000 超は強く掛かる */
    @GameTest(template = TEMPLATE, batch = "elenor", timeoutTicks = 200)
    public static void penaltyTierEscalates(GameTestHelper helper) {
        helper.assertTrue(ElenorPressure.levelFactor(1500) > ElenorPressure.levelFactor(1000) + 0.9F, "levels above 1000 are not weighted strongly");
        helper.assertTrue(ElenorPressure.levelFactor(100) < 1.3F, "level 100 should be an ordinary level");
        var elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(3, 2, 3));
        var player = helper.makeMockPlayer();
        var pressure = elenor.arts().pressure();
        helper.assertTrue(pressure.tier(player) == 0, "tier must start at 0");
        int normal = pressure.failThreshold(player.getUUID());
        // 過剰な攻撃・即死の感知（攻撃者が分からないものは全員の上がりやすさに効く）
        elenor.hurt(helper.getLevel().damageSources().generic(), Float.POSITIVE_INFINITY);
        helper.assertTrue(pressure.failThreshold(player.getUUID()) < normal, "sensing an excessive attack did not make tiers easier to reach");
        for (int i = 0; i < 40; i++) { // 技が通らなかった（模擬プレイヤーはクリエイティブなので、結果を直接数える）
            pressure.noteStrikeResult(player, 0.0F, 8.0F);
        }
        for (int tier = 0; tier <= ElenorPressure.MAX_TIER; tier++) { // どのティアの攻め方でも落ちない（生きた標的に）
            var target = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(5, 2, 5));
            pressure.strike(target, 4.0F);
            target.discard();
        }
        helper.assertTrue(pressure.tier(player) >= 1, "failed strikes did not raise the tier");
        helper.assertTrue(pressure.tier(player) <= ElenorPressure.MAX_TIER, "tier above the maximum");
        elenor.retreat();
        helper.succeed();
    }
}
