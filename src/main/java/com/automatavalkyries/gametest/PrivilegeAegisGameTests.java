package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.FarthestElenorEntity;
import com.automatavalkyries.lifeguard.hooks.LifeHooks;
import com.automatavalkyries.protect.AgentPlayerProtection;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 特権守護の核（Privilege Aegis / AgentPlayerProtection）の自動検証テスト。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class PrivilegeAegisGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        return player;
    }

    /** 特権刻印を受けたプレイヤーは、即死（die() 直接呼び出しや致命的ダメージ）を拒否して生存すること */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisBindsAndPreventsDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer target = player(helper, "aegis_target");

        helper.assertFalse(AgentPlayerProtection.isProtected(target), "Unbound player should not be protected initially");

        // 魂に特権を刻印
        AgentPlayerProtection.bind(target);
        helper.assertTrue(AgentPlayerProtection.isProtected(target), "Target player was not marked as protected after bind");

        // die() を直接呼び出しても死亡しない
        target.die(level.damageSources().genericKill());
        helper.assertFalse(target.isDeadOrDying(), "Protected player should not die from die()");
        helper.assertTrue(target.getHealth() > 0.0F, "Protected player health dropped to 0 or below: " + target.getHealth());

        // 解除
        AgentPlayerProtection.unbind(target);
        helper.assertFalse(AgentPlayerProtection.isProtected(target), "Target player should not be protected after unbind");

        helper.succeed();
    }

    /** 特権刻印を受けたプレイヤーに対しては、終焉執行（TerminusEngine）も拒否され生存すること */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisBlocksTerminusExecution(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer target = player(helper, "terminus_aegis_target");
        Player attacker = helper.makeMockPlayer();

        AgentPlayerProtection.bind(target);

        // 終焉執行を試みる
        boolean executed = TerminusEngine.executeTerminus(level, attacker, target);
        helper.assertFalse(executed, "Terminus execution should be refused against Agent-protected player");

        helper.assertFalse(target.isDeadOrDying(), "Target player died despite Privilege Aegis protection");
        helper.assertTrue(target.getHealth() > 0.0F, "Target player health was drained: " + target.getHealth());

        AgentPlayerProtection.unbind(target);
        TerminusEngine.clearTrackedTargets();
        EprotectPlusLink.resetReinforced();
        helper.succeed();
    }

    /** インベントリを全消去（strip）されても、UUID刻印により保護が持続すること */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisPersistsWhenItemsStripped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer target = player(helper, "stripped_aegis_target");

        // アイテムを持たせて刻印
        target.getInventory().setItem(0, new ItemStack(ModItems.PRIVILEGE_AEGIS.get()));
        AgentPlayerProtection.bind(target);

        // インベントリを強制全消去（即死MODの剥奪攻撃をシミュレート）
        target.getInventory().clearContent();
        helper.assertTrue(target.getInventory().isEmpty(), "Inventory was not emptied");

        // アイテムが無くても魂に刻印されているため保護が持続する
        helper.assertTrue(AgentPlayerProtection.isProtected(target), "Protection was lost when inventory was cleared");

        target.die(level.damageSources().fellOutOfWorld());
        helper.assertFalse(target.isDeadOrDying(), "Player died after inventory strip");

        AgentPlayerProtection.unbind(target);
        helper.succeed();
    }

    /** LifeHooks（Javaエージェント最外郭）が保護対象プレイヤーの生死ゲッターを強制上書きすること */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisLifeHooksOverride(GameTestHelper helper) {
        if (!com.automatavalkyries.protect.LifeGuardLink.isAgentPresent()) {
            helper.succeed();
            return;
        }
        LifeHooksVerifier.verify(helper);
    }

    private static final class LifeHooksVerifier {
        static void verify(GameTestHelper helper) {
            FakePlayer target = player(helper, "lifehooks_target");
            AgentPlayerProtection.bind(target);

            // 他 MOD のエージェントが 0.0F や死亡を返そうとしても、LifeHooks が最外郭で上書きする
            float interceptedHealth = LifeHooks.health(0.0F, target);
            boolean interceptedDead = LifeHooks.deadOrDying(true, target);
            boolean interceptedAlive = LifeHooks.alive(false, target);

            helper.assertTrue(interceptedHealth >= 20.0F, "LifeHooks did not enforce max health: " + interceptedHealth);
            helper.assertFalse(interceptedDead, "LifeHooks did not enforce deadOrDying = false");
            helper.assertTrue(interceptedAlive, "LifeHooks did not enforce alive = true");

            AgentPlayerProtection.unbind(target);
            helper.succeed();
        }
    }

    /**
     * 特権守護の核を所持していれば未刻印でも保護され、スニーク右クリック等で解除（unbind）後は終焉執行が通り即死すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisUnboundHoldingItemCanBeTerminated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer target = player(helper, "unbound_item_target");
        Player attacker = helper.makeMockPlayer();

        // アイテムを持たせる
        target.getInventory().setItem(0, new ItemStack(ModItems.PRIVILEGE_AEGIS.get()));

        // 所持中（未刻印）: パッシブで保護されていること
        helper.assertTrue(AgentPlayerProtection.isProtected(target), "Holding item should passively protect player");

        // 一度刻印してから解除
        AgentPlayerProtection.bind(target);
        helper.assertTrue(AgentPlayerProtection.isProtected(target), "Player should be protected when bound");
        AgentPlayerProtection.unbind(target);
        helper.assertFalse(AgentPlayerProtection.isProtected(target), "Player holding item should NOT be protected after unbind");

        // 終焉執行を試みる -> 成功して即死すること
        boolean executed = TerminusEngine.executeTerminus(level, attacker, target);
        helper.assertTrue(executed, "Terminus execution should succeed against unbound player holding Aegis item");
        helper.assertTrue(target.isDeadOrDying() || target.deathTime > 0, "Unbound player should die from Terminus execution");

        TerminusEngine.clearTrackedTargets();
        EprotectPlusLink.resetReinforced();
        helper.succeed();
    }

    /**
     * 特権守護の核所持または刻印プレイヤーの最大体力改変や奈落落下からの保護検証。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisVoidAndAttributeTamperProtection(GameTestHelper helper) {
        FakePlayer target = player(helper, "tamper_target");
        target.getInventory().setItem(0, new ItemStack(ModItems.PRIVILEGE_AEGIS.get()));
        helper.assertTrue(AgentPlayerProtection.isProtected(target), "Player holding item should be protected");

        // 1. 最大体力改変（他MODによるMAX_HEALTH 0化などの攻撃）の復旧検証
        var maxHealthAttr = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(0.0D);
        }
        target.setHealth(0.0F);

        // onPlayerTick の安全弁を呼び出す
        AgentPlayerProtection.onPlayerTick(new TickEvent.PlayerTickEvent(
                TickEvent.Phase.END, target));

        helper.assertTrue(target.getMaxHealth() >= 20.0F, "Max health should be restored to at least 20.0: " + target.getMaxHealth());
        helper.assertTrue(target.getHealth() >= 20.0F, "Health should be restored to at least 20.0: " + target.getHealth());
        helper.assertFalse(target.isDeadOrDying(), "Player should not be dead or dying after attribute tampering");

        // 2. 奈落落下ダメージの無効化検証
        boolean hurt = target.hurt(target.level().damageSources().fellOutOfWorld(), 1000.0F);
        helper.assertFalse(hurt, "Protected player should not take void damage");
        helper.assertFalse(target.isDeadOrDying(), "Player should survive fellOutOfWorld damage");

        AgentPlayerProtection.unbind(target);
        helper.succeed();
    }

    /**
     * 最果てのエレノアへの攻撃反撃: 特権刻印中は反撃終焉を完全に無効化して生存すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void farthestElenorRetaliationVsProtectedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FarthestElenorEntity farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(2, 2, 2));
        FakePlayer target = player(helper, "farthest_protected_target");

        // 刻印
        AgentPlayerProtection.bind(target);

        // 最果てのエレノアを攻撃 -> 反撃の終焉を受けるが生存
        farthest.hurt(level.damageSources().playerAttack(target), 5.0F);

        helper.assertFalse(target.isDeadOrDying(), "Protected player should not die from Far Elenor's retaliatory Terminus");
        helper.assertTrue(target.getHealth() > 0.0F, "Protected player health was drained: " + target.getHealth());

        AgentPlayerProtection.unbind(target);
        farthest.getEprotect().triggerDespawn();
        TerminusEngine.clearTrackedTargets();
        EprotectPlusLink.resetReinforced();
        helper.succeed();
    }

    /**
     * 最果てのエレノアへの攻撃反撃: 刻印解除後（アイテムを所持していても）は反撃終焉で正常に即死すること。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void farthestElenorRetaliationVsUnboundPlayerHoldingItem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FarthestElenorEntity farthest = helper.spawnWithNoFreeWill(ModEntities.FARTHEST_ELENOR.get(), new BlockPos(2, 2, 2));
        FakePlayer target = player(helper, "farthest_unbound_target");

        // アイテムを持たせて一度刻印後、解除する
        target.getInventory().setItem(0, new ItemStack(ModItems.PRIVILEGE_AEGIS.get()));
        AgentPlayerProtection.bind(target);
        AgentPlayerProtection.unbind(target);

        helper.assertFalse(AgentPlayerProtection.isProtected(target), "Target should not be protected after unbind");

        // 最果てのエレノアを攻撃 -> 反撃の終焉で正常に死亡すること
        farthest.hurt(level.damageSources().playerAttack(target), 5.0F);

        helper.assertTrue(target.isDeadOrDying() || target.deathTime > 0, "Unbound player should be killed by Far Elenor's retaliatory Terminus");

        farthest.getEprotect().triggerDespawn();
        TerminusEngine.clearTrackedTargets();
        EprotectPlusLink.resetReinforced();
        helper.succeed();
    }

    /**
     * The Trial Monolith などの外部MODによる魂ダメージ・強制消去・getter偽装・kill()に対する完全耐性の検証。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void privilegeAegisSoulDamageAndKillImmunity(GameTestHelper helper) {
        FakePlayer target = player(helper, "soul_immunity_target");
        AgentPlayerProtection.bind(target);

        // 1. EprotectCompat がプレイヤーを保護対象として認識すること
        helper.assertTrue(com.automatavalkyries.protect.EprotectCompat.shouldShield(target),
                "EprotectCompat should shield protected player");

        // 2. 他MODのカウンター（Trial Monolith 等）の適用が正常に完了すること
        com.automatavalkyries.protect.KillModCounters.counterAll(target);

        // 3. kill() 呼び出しが拒否され生存すること
        target.kill();
        helper.assertFalse(target.isDeadOrDying(), "Protected player should not die from kill()");
        helper.assertTrue(target.isAlive(), "Protected player should report isAlive = true");
        helper.assertTrue(target.getHealth() >= 20.0F, "Protected player should maintain >= 20.0 health: " + target.getHealth());

        // 4. discard() 呼び出しが拒否され生存すること
        target.discard();
        helper.assertFalse(target.isRemoved(), "Protected player should not be removed from discard()");

        AgentPlayerProtection.unbind(target);
        helper.succeed();
    }
}
