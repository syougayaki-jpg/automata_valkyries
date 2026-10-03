package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.TacticalArmoryBlockEntity;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModCompression;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.LuminalCoreItem;
import com.automatavalkyries.weapon.Necrosis;
import com.automatavalkyries.weapon.PolarDeflectorItem;
import com.automatavalkyries.weapon.RailRifleItem;
import com.automatavalkyries.weapon.RepairInjectorItem;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.automatavalkyries.weapon.WeaponDamage;
import com.automatavalkyries.weapon.WeaponPower;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 兵器ユニット（T1 の兵装・ルミナルコア・タクティカル・アーモリー）の GameTest */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class WeaponGameTests {

    private static final String TEMPLATE = "empty";

    private static FakePlayer player(GameTestHelper helper, Vec3 relative) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "weapon_test"));
        Vec3 pos = helper.absoluteVec(relative);
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F); // +X を向く
        player.setYHeadRot(-90.0F); // 視線（getViewVector）は頭の向き
        return player;
    }

    private static Zombie dummy(GameTestHelper helper, Vec3 relative) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, relative);
        zombie.setNoAi(true);
        return zombie;
    }

    /** 持ち物のルミナルコアが、空の兵装へ放電レート（基礎コアは 5,000 FE/t）まで流し込む */
    @GameTest(template = TEMPLATE)
    public static void coreTopsUpWeapon(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        LuminalCoreItem coreItem = (LuminalCoreItem) ModWeapons.CORE_BASIC.get();
        ItemStack core = coreItem.charged();
        ItemStack edge = new ItemStack(ModWeapons.PHOTON_EDGE.get());
        player.getInventory().setItem(0, edge);
        player.getInventory().setItem(1, core);
        WeaponPower.topUp(player, edge, ((TacticalWeaponItem) edge.getItem()).capacity());
        helper.assertTrue(ItemEnergy.get(edge) == LuminalCoreItem.Grade.BASIC.rate, "weapon got " + ItemEnergy.get(edge));
        helper.assertTrue(ItemEnergy.get(core) == LuminalCoreItem.Grade.BASIC.capacity - LuminalCoreItem.Grade.BASIC.rate, "core has " + ItemEnergy.get(core));
        // 無限動力は減らない
        ItemStack omega = ((LuminalCoreItem) ModWeapons.CORE_STELLA_GENESIS_OMEGA.get()).charged();
        helper.assertTrue(((LuminalCoreItem) omega.getItem()).extract(omega, 1_000_000) == 1_000_000 && ItemEnergy.get(omega) == 0,
                "omega core is not infinite");
        helper.succeed();
    }

    /** レールライフル: 線上の 2 体を貫通して当て、電力・弾は {@code use} の側で減る（ここでは発射処理だけ） */
    @GameTest(template = TEMPLATE)
    public static void railRiflePiercesTargets(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(0.5, 2, 2.5));
        Zombie near = dummy(helper, new Vec3(2.5, 2, 2.5));
        Zombie far = dummy(helper, new Vec3(4.5, 2, 2.5));
        player.moveTo(player.getX(), near.getEyeY() - player.getEyeHeight(), player.getZ(), -90.0F, 0.0F);
        RailRifleItem.fire(helper.getLevel(), player);
        helper.assertTrue(near.isDeadOrDying() || near.getHealth() < near.getMaxHealth(), "near target not hit");
        helper.assertTrue(far.isDeadOrDying() || far.getHealth() < far.getMaxHealth(), "far target not pierced");
        helper.succeed();
    }

    /** 徹甲: ダイヤの防具の半分を無視するので、普通の防具計算よりダメージが大きい */
    @GameTest(template = TEMPLATE)
    public static void armorPiercingIgnoresHalf(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        // ダイヤの防具一式と同じ値（防具の属性は mob の Tick で付くので、ここでは基礎値で置く）
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
        float pierced = WeaponDamage.armorPiercing(zombie, 35.0F, 0.5F);
        float normal = CombatRules.getDamageAfterAbsorb(35.0F, zombie.getArmorValue(), 8.0F);
        helper.assertTrue(zombie.getArmorValue() >= 20, "armor not applied: " + zombie.getArmorValue());
        helper.assertTrue(pierced > normal && pierced < 35.0F, "pierced " + pierced + " vs normal " + normal);
        helper.succeed();
    }

    /** 偏光ディフレクター: 展開中は前からの矢を止め、後ろからの矢は通す */
    @GameTest(template = TEMPLATE)
    public static void deflectorBlocksFrontProjectiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(helper, new Vec3(2.5, 2, 2.5));
        player.setItemInHand(InteractionHand.OFF_HAND, ((TacticalWeaponItem) ModWeapons.POLAR_DEFLECTOR.get()).charged());
        player.startUsingItem(InteractionHand.OFF_HAND);
        helper.assertTrue(PolarDeflectorItem.deployed(player), "deflector not deployed");

        Arrow front = new Arrow(level, player.getX() + 3, player.getEyeY(), player.getZ());
        front.setDeltaMovement(-1, 0, 0);
        level.addFreshEntity(front);
        // FakePlayer は何でも無効なので、hurt ではなく攻撃のイベント（ディフレクターが止める所）を直接見る
        helper.assertFalse(ForgeHooks.onPlayerAttack(player, player.damageSources().arrow(front, null), 6.0F), "front arrow was not blocked");

        Arrow back = new Arrow(level, player.getX() - 3, player.getEyeY(), player.getZ());
        level.addFreshEntity(back);
        helper.assertTrue(ForgeHooks.onPlayerAttack(player, player.damageSources().arrow(back, null), 6.0F), "rear arrow should hit");
        helper.assertTrue(ForgeHooks.onPlayerAttack(player, player.damageSources().playerAttack(player), 6.0F), "melee should not be blocked");
        // 跳ね返した矢は Tick の終わりに向きが変わる
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(front.isRemoved() || front.getDeltaMovement().x > 0 || front.getOwner() == player, "front arrow was not reflected");
            helper.succeed();
        });
    }

    /** リペアインジェクター: HP 40% 以下で 20 回復し、溶液を 1 本使い、5 秒は続けて噴霧しない */
    @GameTest(template = TEMPLATE)
    public static void injectorHealsWhenLow(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        ItemStack injector = ((TacticalWeaponItem) ModWeapons.REPAIR_INJECTOR.get()).charged();
        zombie.setHealth(9.0F);
        helper.assertFalse(RepairInjectorItem.tryInject(helper.getLevel(), zombie, injector), "injected above 40%");
        zombie.setHealth(5.0F);
        helper.assertTrue(RepairInjectorItem.tryInject(helper.getLevel(), zombie, injector), "did not inject at 25%");
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "health " + zombie.getHealth());
        helper.assertTrue(RepairInjectorItem.solution(injector) == RepairInjectorItem.TANK - 1, "solution not used");
        zombie.setHealth(5.0F);
        helper.assertFalse(RepairInjectorItem.tryInject(helper.getLevel(), zombie, injector), "injected again during cooldown");
        helper.succeed();
    }

    /** 黒死侵食: mob の最大 HP を 1% ずつ削り（30% まで）、プレイヤーには既定で掛けない */
    @GameTest(template = TEMPLATE)
    public static void necrosisErodesMaxHealth(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        float max = zombie.getMaxHealth();
        for (int i = 0; i < 3; i++) Necrosis.apply(zombie);
        helper.assertTrue(Necrosis.stacks(zombie) == 3, "stacks " + Necrosis.stacks(zombie));
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - max * 0.97F) < 0.01F, "max health " + zombie.getMaxHealth());
        for (int i = 0; i < 40; i++) Necrosis.apply(zombie);
        helper.assertTrue(Necrosis.stacks(zombie) == Necrosis.MAX_STACKS, "stacks over the cap: " + Necrosis.stacks(zombie));
        FakePlayer player = player(helper, new Vec3(3, 2, 3));
        Necrosis.apply(player);
        helper.assertTrue(Necrosis.stacks(player) == 0, "necrosis applied to a player by default");
        helper.succeed();
    }

    /** クリエイティブのタブにアーモリー（T1〜T4）・兵装・コア・弾薬が並ぶ */
    @GameTest(template = TEMPLATE)
    public static void creativeTabListsArmoryAndWeapons(GameTestHelper helper) {
        net.minecraft.world.item.CreativeModeTab tab = com.automatavalkyries.registry.ModCreativeTabs.MAIN_TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), true, helper.getLevel().registryAccess()));
        java.util.Collection<ItemStack> items = tab.getDisplayItems();
        net.minecraft.world.item.Item armory = ModChrono.MACHINES.get(ChronoMachineType.TACTICAL_ARMORY).get().asItem();
        long armories = items.stream().filter(s -> s.is(armory)).count();
        helper.assertTrue(armories == 4, "armory stacks in tab: " + armories + " of " + items.size());
        for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{ModWeapons.PHOTON_EDGE.get(), ModWeapons.CORE_BASIC.get(), ModWeapons.AMMO_AP_ROUND.get()}) {
            helper.assertTrue(items.stream().anyMatch(s -> s.is(item)), item + " missing from tab");
        }
        helper.succeed();
    }

    /** タクティカル・アーモリー（T1）: フォトンエッジを組み立てる */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void armoryAssemblesPhotonEdge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModChrono.MACHINES.get(ChronoMachineType.TACTICAL_ARMORY).get());
        if (!(helper.getBlockEntity(pos) instanceof TacticalArmoryBlockEntity armory)) throw new IllegalStateException("no armory");
        armory.items().setStackInSlot(0, new ItemStack(ModItems.CRYSTAL_LUMINAL.get(), 4));
        armory.items().setStackInSlot(1, new ItemStack(ModCompression.LENS_LUMINAL.get(), 2));
        armory.items().setStackInSlot(2, new ItemStack(ModItems.INGOT_ASTRITE.get(), 4));
        armory.items().setStackInSlot(3, new ItemStack(ModItems.CIRCUIT_ADVANCED.get()));
        helper.onEachTick(() -> armory.energy().set(armory.energy().getMaxEnergyStored()));
        helper.succeedWhen(() -> {
            helper.assertTrue(armory.items().getStackInSlot(TacticalArmoryBlockEntity.OUTPUT).is(ModWeapons.PHOTON_EDGE.get()), "no photon edge yet");
            for (int i = 0; i < 4; i++) helper.assertTrue(armory.items().getStackInSlot(i).isEmpty(), "ingredient " + i + " left");
        });
    }
}
