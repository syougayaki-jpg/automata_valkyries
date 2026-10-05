package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 戦術兵姫の戦闘 AI（段階 3）: 交戦の方針・兵装での攻撃・味方への攻撃の無効 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieCombatGameTests {

    private static final String TEMPLATE = "empty";

    private static ValkyrieEntity armed(GameTestHelper helper, int x, int z, ValkyrieOrders.Policy policy) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), x, 2, z);
        v.setItemSlot(EquipmentSlot.MAINHAND, ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged());
        v.orders.set(ValkyrieOrders.Field.POLICY, policy.ordinal());
        v.setMode(ValkyrieEntity.Mode.DEFEND);
        return v;
    }

    /** 積極的: 近くの敵対モブを自分で見つけて、兵装で倒す */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 300)
    public static void valkyrieAggressiveKillsHostile(GameTestHelper helper) {
        ValkyrieEntity v = armed(helper, 1, 1, ValkyrieOrders.Policy.AGGRESSIVE);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)); // 日光で燃えないように
        WeaponGameTests.forceChunks(helper, v, zombie);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(v.getTarget() == zombie, "did not target the hostile"))
                .thenWaitUntil(() -> helper.assertTrue(!zombie.isAlive(), "did not defeat the hostile: hp=" + zombie.getHealth()))
                .thenExecute(v::releaseAndDiscard)
                .thenSucceed();
    }

    /** 反撃: 攻撃してこない敵対モブは狙わず、殴られたら狙う。非戦闘は狙わない */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 200)
    public static void valkyrieRetaliatesOnlyWhenAttacked(GameTestHelper helper) {
        ValkyrieEntity v = armed(helper, 1, 1, ValkyrieOrders.Policy.RETALIATE);
        ValkyrieEntity passive = armed(helper, 1, 4, ValkyrieOrders.Policy.PASSIVE);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 2);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)); // 日光で燃えないように
        WeaponGameTests.forceChunks(helper, v, passive, zombie);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(v.getTarget() == null && passive.getTarget() == null, "targeted a zombie that did not attack"))
                .thenExecute(() -> {
                    v.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
                    passive.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(v.getTarget() == zombie, "did not retaliate"))
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(passive.getTarget() == null, "a passive valkyrie engaged"))
                .thenExecute(() -> {
                    v.releaseAndDiscard();
                    passive.releaseAndDiscard();
                    zombie.discard();
                })
                .thenSucceed();
    }

    /** 味方への攻撃は当たらない: 持ち主 → 兵姫、同じ持ち主の兵姫同士。別の持ち主の兵姫の攻撃は当たる */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat")
    public static void valkyrieNoFriendlyFire(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer();
        ValkyrieEntity a = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity b = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        ValkyrieEntity stranger = helper.spawn(ModEntities.VALKYRIE.get(), 5, 2, 1);
        a.setOwnerUUID(owner.getUUID());
        b.setOwnerUUID(owner.getUUID());
        stranger.setOwnerUUID(UUID.randomUUID());
        float shieldA = a.getShield(), shieldB = b.getShield();
        helper.assertFalse(a.hurt(helper.getLevel().damageSources().playerAttack(owner), 5.0F), "the owner's attack hit the valkyrie");
        helper.assertFalse(b.hurt(helper.getLevel().damageSources().mobAttack(a), 5.0F), "an allied valkyrie's attack hit");
        helper.assertTrue(a.getShield() == shieldA && b.getShield() == shieldB, "friendly attacks took shield/HP");
        float before = b.getShield() + DirectRead.health(b);
        helper.assertTrue(b.hurt(helper.getLevel().damageSources().mobAttack(stranger), 5.0F), "another owner's valkyrie could not hit");
        helper.assertTrue(b.getShield() + DirectRead.health(b) < before, "the stranger's attack did no damage");
        a.releaseAndDiscard();
        b.releaseAndDiscard();
        stranger.releaseAndDiscard();
        helper.succeed();
    }

    /** オメガコアで全ステータス 3 倍: 最大 HP・シールド・防御・速度、与えるダメージも 3 倍 */
    @GameTest(template = TEMPLATE, batch = "valkyrie_combat", timeoutTicks = 40)
    public static void valkyrieOmegaCoreTriplesStats(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        ValkyrieEntity plain = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        Zombie a = helper.spawn(EntityType.ZOMBIE, 1, 2, 4), b = helper.spawn(EntityType.ZOMBIE, 3, 2, 4);
        a.setNoAi(true);
        b.setNoAi(true);
        float baseHp = v.getMaxHealth(), baseShield = v.getMaxShield();
        double baseArmor = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        v.setCore(new net.minecraft.world.item.ItemStack(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()));
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(Math.abs(v.getMaxHealth() - baseHp * 3) < 0.01F, "max HP not tripled: " + v.getMaxHealth());
            helper.assertTrue(Math.abs(v.getMaxShield() - baseShield * 3) < 0.01F, "max shield not tripled: " + v.getMaxShield());
            helper.assertTrue(Math.abs(v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - baseArmor * 3) < 0.01, "armor not tripled");
            float hpA = a.getHealth(), hpB = b.getHealth();
            a.hurt(helper.getLevel().damageSources().mobAttack(v), 2.0F);
            b.hurt(helper.getLevel().damageSources().mobAttack(plain), 2.0F);
            float dealtOmega = hpA - a.getHealth(), dealtPlain = hpB - b.getHealth();
            helper.assertTrue(dealtPlain > 0 && Math.abs(dealtOmega - dealtPlain * 3) < 0.6F, "damage not tripled: omega=" + dealtOmega + " plain=" + dealtPlain);
            v.releaseAndDiscard();
            plain.releaseAndDiscard();
            a.discard();
            b.discard();
        }).thenSucceed();
    }
}
