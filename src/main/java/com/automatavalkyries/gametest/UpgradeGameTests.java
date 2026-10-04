package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.upgrade.UpgradeStationMenu;
import com.automatavalkyries.weapon.ChargedWeaponItem;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.automatavalkyries.weapon.WeaponUpgrades;
import com.automatavalkyries.weapon.WeaponUpgrades.Stat;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 兵装のカスタムアップグレード（2026-10-04 ユーザー指定） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "upgrade_test"));
        Vec3 pos = helper.absoluteVec(new Vec3(1, 2, 1));
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        return player;
    }

    /** 金属の数は 10 から 1.1 倍ずつ（切り上げ）。倍率は 1 回 +10%、上限 +1000% */
    @GameTest(template = TEMPLATE, batch = "upgrades")
    public static void upgradeCostAndCap(GameTestHelper helper) {
        helper.assertTrue(WeaponUpgrades.metalCost(0) == 10 && WeaponUpgrades.metalCost(1) == 11 && WeaponUpgrades.metalCost(2) == 13,
                "metal cost: " + WeaponUpgrades.metalCost(0) + ", " + WeaponUpgrades.metalCost(1) + ", " + WeaponUpgrades.metalCost(2));
        ItemStack edge = new ItemStack(ModWeapons.PHOTON_EDGE.get());
        WeaponUpgrades.setLevel(edge, Stat.DAMAGE, 500);
        helper.assertTrue(WeaponUpgrades.level(edge, Stat.DAMAGE) == WeaponUpgrades.MAX_LEVEL, "level over the cap");
        helper.assertTrue(Math.abs(WeaponUpgrades.mult(edge, Stat.DAMAGE) - 11.0) < 1.0E-6, "cap multiplier is not +1000%");
        helper.succeed();
    }

    /** 強化台で 1 回強化: T1 の兵装はオーブ 1 つとアストライト 10 個、次は 11 個。スターでは上げられない。材料が足りなければ上げない */
    @GameTest(template = TEMPLATE, batch = "upgrades")
    public static void upgradeStationConsumesMaterials(GameTestHelper helper) {
        FakePlayer player = player(helper);
        UpgradeStationMenu menu = new UpgradeStationMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(UpgradeStationMenu.WEAPON).set(((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged());
        menu.getSlot(UpgradeStationMenu.CATALYST).set(new ItemStack(ModItems.STAR_LUMINAL.get(), 2));
        player.getInventory().add(new ItemStack(ModItems.INGOT_ASTRITE.get(), 25));
        helper.assertFalse(menu.clickMenuButton(player, Stat.DAMAGE.ordinal()), "a star upgraded a T1 weapon");
        menu.getSlot(UpgradeStationMenu.CATALYST).set(new ItemStack(ModItems.ORB_LUMINAL.get(), 2));
        helper.assertTrue(menu.clickMenuButton(player, Stat.DAMAGE.ordinal()), "first upgrade failed");
        helper.assertTrue(WeaponUpgrades.level(menu.weapon(), Stat.DAMAGE) == 1, "level is not 1");
        helper.assertTrue(player.getInventory().countItem(ModItems.INGOT_ASTRITE.get()) == 15, "metal left: " + player.getInventory().countItem(ModItems.INGOT_ASTRITE.get()));
        helper.assertTrue(menu.catalyst().getCount() == 1, "catalyst left: " + menu.catalyst().getCount());
        helper.assertTrue(menu.clickMenuButton(player, Stat.DAMAGE.ordinal()), "second upgrade failed");
        helper.assertTrue(player.getInventory().countItem(ModItems.INGOT_ASTRITE.get()) == 4, "second upgrade did not take 11 metal");
        helper.assertFalse(menu.clickMenuButton(player, Stat.DAMAGE.ordinal()), "upgraded without materials");
        helper.assertTrue(WeaponUpgrades.level(menu.weapon(), Stat.DAMAGE) == 2, "level is not 2");
        helper.succeed();
    }

    /** 効き目: 攻撃力 +100% でダメージ 2 倍、チャージ +100% でチャージ時間半分、エネルギー容量 +100% で容量 2 倍 */
    @GameTest(template = TEMPLATE, batch = "upgrades")
    public static void upgradeEffects(GameTestHelper helper) {
        ChargedWeaponItem railgun = (ChargedWeaponItem) ModWeapons.PUNISHMENT_RAILGUN.get();
        ItemStack stack = railgun.charged();
        WeaponUpgrades.setLevel(stack, Stat.CHARGE, 10);
        WeaponUpgrades.setLevel(stack, Stat.ENERGY, 10);
        helper.assertTrue(railgun.chargeTicks(stack) == Math.round(railgun.chargeTicks() / 2.0), "charge: " + railgun.chargeTicks(stack));
        helper.assertTrue(railgun.capacity(stack) == railgun.capacity() * 2, "capacity: " + railgun.capacity(stack));

        FakePlayer player = player(helper);
        ItemStack edge = ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged();
        WeaponUpgrades.setLevel(edge, Stat.DAMAGE, 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, edge);
        Zombie z = helper.spawn(EntityType.HUSK, new Vec3(2.5, 2, 2.5));
        z.setNoAi(true);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        z.setHealth(200);
        z.hurt(helper.getLevel().damageSources().playerAttack(player), 5F);
        helper.assertTrue(Math.abs((200 - z.getHealth()) - 10F) < 0.01F, "damage upgrade: took " + (200 - z.getHealth()));
        z.discard();
        helper.succeed();
    }

    /** カスタム・マキシマイザー: もう片方の手の兵装のカスタムを全部上限に、スニークで 0 に */
    @GameTest(template = TEMPLATE, batch = "upgrades")
    public static void customMaximizerMaxesAll(GameTestHelper helper) {
        FakePlayer player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.automatavalkyries.registry.ModUpgrades.CUSTOM_MAXIMIZER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged());
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        for (Stat stat : Stat.values()) helper.assertTrue(WeaponUpgrades.level(player.getOffhandItem(), stat) == WeaponUpgrades.MAX_LEVEL, stat + " not maxed");
        player.setShiftKeyDown(true);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        for (Stat stat : Stat.values()) helper.assertTrue(WeaponUpgrades.level(player.getOffhandItem(), stat) == 0, stat + " not reset");
        helper.succeed();
    }

    /** 説明の並び: ① ティア → ② 性能 → ③ カスタム → ④ オーバーロード → ⑤ Shift の案内（サーバーでは Shift は押されていない）。クリエイティブのタブの振り分け */
    @GameTest(template = TEMPLATE, batch = "upgrades")
    public static void tooltipOrderAndTabs(GameTestHelper helper) {
        ItemStack edge = ((TacticalWeaponItem) ModWeapons.PHOTON_EDGE.get()).charged();
        WeaponUpgrades.setLevel(edge, Stat.DAMAGE, 3);
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        edge.getItem().appendHoverText(edge, helper.getLevel(), lines, net.minecraft.world.item.TooltipFlag.NORMAL);
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (var line : lines) keys.add(key(line));
        int tier = keys.indexOf("gui.automata_valkyries.tip.tier"), custom = keys.indexOf("upgrade.automata_valkyries.summary"),
                overload = keys.indexOf("gui.automata_valkyries.tip.overload_off"), shift = keys.indexOf("gui.automata_valkyries.tip.shift");
        helper.assertTrue(tier == 0 && custom > tier + 1 && overload == custom + 1 && shift == overload + 1, "tooltip order is off: " + keys);
        helper.assertTrue(lines.get(1).getString().contains("12") || lines.get(1).getString().contains("15"), "attack line missing: " + lines.get(1).getString());
        helper.assertTrue(com.automatavalkyries.registry.ModCreativeTabs.group(ModWeapons.PHOTON_EDGE.get()) == com.automatavalkyries.registry.ModCreativeTabs.Group.WEAPONS
                && com.automatavalkyries.registry.ModCreativeTabs.group(ModItems.INGOT_ASTRITE.get()) == com.automatavalkyries.registry.ModCreativeTabs.Group.MATERIALS
                && com.automatavalkyries.registry.ModCreativeTabs.group(com.automatavalkyries.registry.ModBossDrops.MEMORY_STELLA.get()) == com.automatavalkyries.registry.ModCreativeTabs.Group.SPECIAL
                && com.automatavalkyries.registry.ModCreativeTabs.group(ModWeapons.REQUIEM_BACKDROP.get()) == com.automatavalkyries.registry.ModCreativeTabs.Group.HIDDEN, "tab groups are off");
        helper.succeed();
    }

    /** 行の先頭の翻訳キー（「 ▸ 」の頭が付いた行は、その中身のキー） */
    private static String key(net.minecraft.network.chat.Component line) {
        if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) return t.getKey();
        for (var sib : line.getSiblings()) {
            if (sib.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) return t.getKey();
        }
        return line.getString();
    }
}
