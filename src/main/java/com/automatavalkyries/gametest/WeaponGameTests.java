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
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void armorPiercingIgnoresHalf(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        whenLoaded(helper, () -> {
            // ダイヤの防具一式と同じ値（防具の属性は mob の Tick で付くので、ここでは基礎値で置く）
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
            zombie.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8.0);
            float pierced = WeaponDamage.armorPiercing(zombie, 35.0F, 0.5F);
            float normal = CombatRules.getDamageAfterAbsorb(35.0F, zombie.getArmorValue(), 8.0F);
            helper.assertTrue(zombie.getArmorValue() >= 20, "armor not applied: " + zombie.getArmorValue());
            helper.assertTrue(pierced > normal && pierced < 35.0F, "pierced " + pierced + " vs normal " + normal);
            helper.succeed();
        }, zombie);
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
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void injectorHealsWhenLow(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        whenLoaded(helper, () -> {
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
        }, zombie);
    }

    /** 黒死侵食: mob の最大 HP を 1% ずつ削り（30% まで）、プレイヤーには既定で掛けない */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void necrosisErodesMaxHealth(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        whenLoaded(helper, () -> {
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
        }, zombie);
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

    /** ルクシオンセイバー: 前の敵の向こう側へ跳び、すれ違いざまに斬る */
    @GameTest(template = TEMPLATE)
    public static void luxionSaberBlinksThroughTarget(GameTestHelper helper) {
        // FakePlayer はワールドに居ないので跳べない。兵姫と同じく mob に持たせて試す
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie zombie = dummy(helper, new Vec3(3.5, 2, 2.5));
        double before = user.getX();
        com.automatavalkyries.weapon.LuxionSaberItem.blink(helper.getLevel(), user, zombie,
                ((TacticalWeaponItem) ModWeapons.LUXION_SABER.get()).charged());
        helper.assertTrue(user.getX() > zombie.getX(), "did not pass the target: " + before + " -> " + user.getX());
        helper.assertTrue(zombie.isDeadOrDying() || zombie.getHealth() < zombie.getMaxHealth(), "target not cut");
        helper.succeed();
    }

    /** 重粒子収束砲: 光線が線上の 2 体を貫く（0.2 秒ごとの当たり） */
    @GameTest(template = TEMPLATE)
    public static void particleBeamPierces(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(0.5, 2, 2.5));
        Zombie near = dummy(helper, new Vec3(2.5, 2, 2.5));
        Zombie far = dummy(helper, new Vec3(4.5, 2, 2.5));
        player.moveTo(player.getX(), near.getEyeY() - player.getEyeHeight(), player.getZ(), -90.0F, 0.0F);
        player.setYHeadRot(-90.0F);
        com.automatavalkyries.weapon.HeavyParticleCannonItem.beamTick(helper.getLevel(), player, 0);
        helper.assertTrue(near.getHealth() < near.getMaxHealth(), "near target not hit");
        helper.assertTrue(far.getHealth() < far.getMaxHealth(), "far target not pierced");
        helper.succeed();
    }

    /** タイムエリミネイター: 有益な状態異常と衝撃吸収を消し、因果停止を掛ける（プレイヤーには既定で掛けない） */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void timeEliminatorStripsBuffs(GameTestHelper helper) {
        Zombie zombie = dummy(helper, new Vec3(1.5, 2, 1.5));
        whenLoaded(helper, () -> {
            zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 600));
            zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 600, 3));
            zombie.setAbsorptionAmount(10);
            com.automatavalkyries.weapon.TimeEliminatorItem.sever(helper.getLevel(), zombie);
            helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "regeneration kept");
            helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "resistance kept");
            helper.assertTrue(zombie.getAbsorptionAmount() == 0, "absorption kept");
            helper.assertTrue(zombie.hasEffect(ModWeapons.CAUSAL_STOP.get()), "no causal stop");
            FakePlayer player = player(helper, new Vec3(3, 2, 3));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 600));
            com.automatavalkyries.weapon.TimeEliminatorItem.sever(helper.getLevel(), player);
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) && !player.hasEffect(ModWeapons.CAUSAL_STOP.get()),
                    "strong effects applied to a player by default");
            helper.succeed();
        }, zombie);
    }

    /** アイギス: ドームの中の者へ、外からの攻撃は通さず、中どうしの攻撃は通す */
    @GameTest(template = TEMPLATE)
    public static void aegisBlocksOutsideAttacks(GameTestHelper helper) {
        // 見回りはワールドの中のエンティティを探すので、ワールドに居る mob に持たせる
        Zombie guardian = dummy(helper, new Vec3(2.5, 2, 2.5));
        guardian.setItemInHand(InteractionHand.OFF_HAND, ((TacticalWeaponItem) ModWeapons.AEGIS_BARRIER.get()).charged());
        guardian.startUsingItem(InteractionHand.OFF_HAND);
        com.automatavalkyries.weapon.AegisBarrierItem.ACTIVE.add(guardian);
        try {
            Zombie inside = dummy(helper, new Vec3(4.5, 2, 2.5));
            Zombie insideAttacker = dummy(helper, new Vec3(2.5, 2, 4.5));
            Zombie outsideAttacker = dummy(helper, new Vec3(11.5, 2, 2.5));
            helper.assertFalse(ForgeHooks.onLivingAttack(inside, inside.damageSources().mobAttack(outsideAttacker), 5.0F), "outside attack got through");
            helper.assertTrue(ForgeHooks.onLivingAttack(inside, inside.damageSources().mobAttack(insideAttacker), 5.0F), "inside attack was blocked");
            helper.assertTrue(ForgeHooks.onLivingAttack(inside, inside.damageSources().fall(), 5.0F), "fall damage was blocked");
        } finally {
            com.automatavalkyries.weapon.AegisBarrierItem.ACTIVE.remove(guardian);
        }
        helper.succeed();
    }

    /** カタストロフ・バスター: ブラックホール弾が崩壊して範囲の敵を倒し、既定ではブロックを壊さない。吸い込みが 20 ブロックと広いので、別の組で動かす */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "weapon_blackhole")
    public static void blackHoleCollapses(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(0.5, 2, 0.5));
        Zombie zombie = dummy(helper, new Vec3(4.5, 2, 4.5));
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        zombie.setHealth(500);
        BlockPos stone = new BlockPos(5, 2, 6);
        helper.setBlock(stone, net.minecraft.world.level.block.Blocks.STONE);
        com.automatavalkyries.weapon.BlackHoleEntity hole = new com.automatavalkyries.weapon.BlackHoleEntity(helper.getLevel(), player);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 3, 4.5));
        hole.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(hole);
        helper.succeedWhen(() -> {
            helper.assertTrue(hole.isRemoved(), "not collapsed yet");
            helper.assertTrue(zombie.isDeadOrDying(), "target survived the collapse: " + zombie.getHealth());
            helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE, stone);
        });
    }

    /** カタストロフ・バスター: ブラックホール弾は着弾するまでの最中も、まわりの者を吸い込む */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "weapon_blackhole_flight")
    public static void blackHolePullsInFlight(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(0.5, 2, 0.5));
        Zombie zombie = dummy(helper, new Vec3(3.5, 2, 4.5));
        com.automatavalkyries.weapon.BlackHoleEntity hole = new com.automatavalkyries.weapon.BlackHoleEntity(helper.getLevel(), player);
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 3, 0.5));
        hole.setPos(at.x, at.y, at.z);
        hole.shoot(1, 0, 0, 0.3F, 0); // 横を通り過ぎる（当たらない）
        helper.getLevel().addFreshEntity(hole);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(zombie.getDeltaMovement().z < -0.05, "target not pulled toward the flying hole: " + zombie.getDeltaMovement());
            hole.discard();
            sweep(helper);
            helper.succeed();
        });
    }

    /** クアンタムブレード: 三日月の斬撃がまっすぐ飛び、線上の 2 体を貫いて斬る */
    @GameTest(template = TEMPLATE, timeoutTicks = 360)
    public static void quantumSlashPierces(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.moveTo(user.getX(), user.getY(), user.getZ(), -90.0F, 0.0F);
            user.setYHeadRot(-90.0F);
            Zombie near = dummy(helper, new Vec3(4.5, 2, 2.5));
            Zombie far = dummy(helper, new Vec3(9.5, 2, 2.5));
            com.automatavalkyries.weapon.QuantumBladeItem.slash(helper.getLevel(), user);
            helper.succeedWhen(() -> {
                helper.assertTrue(near.getHealth() < near.getMaxHealth(), "near target not cut");
                helper.assertTrue(far.getHealth() < far.getMaxHealth(), "far target not pierced");
                helper.assertTrue(user.getHealth() == user.getMaxHealth(), "the slash hit its owner");
            });
        }, user);
    }

    /** パニッシュメント・レールガン: 線上の全員を貫いてカンストダメージで倒し、光のレールを出す */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void punishmentRailgunPierces(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.moveTo(user.getX(), user.getY(), user.getZ(), -90.0F, 0.0F);
            user.setYHeadRot(-90.0F);
            Zombie near = dummy(helper, new Vec3(5.5, 2, 2.5));
            Zombie far = dummy(helper, new Vec3(14.5, 2, 2.5));
            for (Zombie z : new Zombie[]{near, far}) {
                z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                z.setHealth(1000);
                z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
            }
            com.automatavalkyries.weapon.PunishmentRailgunItem.fire(helper.getLevel(), user, 16); // 隣のテストに届かないよう短く
            helper.assertTrue(near.isDeadOrDying(), "near target survived: " + near.getHealth());
            helper.assertTrue(far.isDeadOrDying(), "far target survived: " + far.getHealth());
            helper.assertFalse(helper.getLevel().getEntitiesOfClass(com.automatavalkyries.weapon.PunishmentBeamEntity.class,
                    user.getBoundingBox().inflate(4)).isEmpty(), "no beam entity");
            helper.succeed();
        }, user);
    }

    /** パニッシュメント・レールガン: 壁をすべて貫き、射線のまわりの者にも余波（1 万）が届く。余波の半径はテストでは 4 に縮める */
    @GameTest(template = TEMPLATE, batch = "weapon_railgun_wave", timeoutTicks = 40)
    public static void punishmentRailgunPiercesWallsWithAftershock(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) for (int z = 0; z <= 4; z++) helper.setBlock(new BlockPos(2, y, z), net.minecraft.world.level.block.Blocks.STONE);
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie behindWall = tough(helper, new Vec3(4.5, 2, 2.5));
        Zombie offLine = tough(helper, new Vec3(4.5, 2, 0.5));
        com.automatavalkyries.weapon.PunishmentRailgunItem.fire(helper.getLevel(), user, 16, 4);
        helper.assertTrue(behindWall.isDeadOrDying(), "rail did not pierce the wall: " + behindWall.getHealth());
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(offLine.isDeadOrDying(), "aftershock missed the target beside the line: " + offLine.getHealth());
            sweep(helper);
            helper.succeed();
        });
    }

    /** アバランチ: 冷凍ミサイルは無敵時間中でも当たり、凍傷を重ねる（最大 V）。長射程の兵装のテストと離すため、別の組で動かす */
    @GameTest(template = TEMPLATE, timeoutTicks = 360, batch = "weapon_frost")
    public static void frostMissilePiercesInvulnerability(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        whenLoaded(helper, () -> {
            // 昼の日光で燃える体力の減りと見分けるため、燃えないハスクを的にする
            Zombie target = helper.spawn(EntityType.HUSK, new Vec3(4.5, 2, 2.5));
            target.setNoAi(true);
            target.invulnerableTime = 20; // 無敵時間の最中
            com.automatavalkyries.weapon.FrostMissileEntity missile =
                    new com.automatavalkyries.weapon.FrostMissileEntity(helper.getLevel(), user, target);
            missile.setPos(helper.absoluteVec(new Vec3(2.5, 3, 2.5)));
            missile.setDeltaMovement(new Vec3(0.6, 0, 0));
            helper.getLevel().addFreshEntity(missile);
            helper.succeedWhen(() -> {
                helper.assertTrue(target.getHealth() < target.getMaxHealth(), "missile did not pierce invulnerability");
                helper.assertTrue(target.hasEffect(ModWeapons.FROSTBITE.get()), "no frostbite");
                for (int i = 0; i < 8; i++) com.automatavalkyries.weapon.Frostbite.apply(target);
                helper.assertTrue(target.getEffect(ModWeapons.FROSTBITE.get()).getAmplifier() == com.automatavalkyries.weapon.Frostbite.MAX_LEVEL, "frostbite over the cap");
            });
        }, user);
    }

    // ======== 追加の兵装 10 種（長射程・範囲の攻撃が隣のテストに届かないよう、それぞれ別の組で動かす） ========

    private static Zombie facingX(GameTestHelper helper, Vec3 at) {
        Zombie user = dummy(helper, at);
        user.moveTo(user.getX(), user.getY(), user.getZ(), -90.0F, 0.0F);
        user.setYHeadRot(-90.0F);
        return user;
    }

    private static Zombie tough(GameTestHelper helper, Vec3 at) {
        Zombie z = helper.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);
        return z;
    }

    /** アークランサー: 稲妻が近くの敵へ跳ぶ */
    @GameTest(template = TEMPLATE, batch = "weapon_arc", timeoutTicks = 400)
    public static void arcLancerChains(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 0.5));
        Zombie first = tough(helper, new Vec3(2.5, 2, 2.5));
        Zombie second = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.ArcLancerItem.chain(helper.getLevel(), user, first);
            helper.assertTrue(second.getHealth() < second.getMaxHealth(), "lightning did not jump");
            helper.succeed();
        }, user, first, second);
    }

    /** スキャッターブラスター: 目の前の敵に散弾が当たる */
    @GameTest(template = TEMPLATE, batch = "weapon_scatter", timeoutTicks = 400)
    public static void scatterBlasterHits(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(2.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.ScatterBlasterItem.fire(helper.getLevel(), user);
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "no pellet hit");
            helper.succeed();
        }, user, target);
    }

    /** グラビティハンマー: まわりの敵を打ち上げる */
    @GameTest(template = TEMPLATE, batch = "weapon_hammer", timeoutTicks = 400)
    public static void gravityHammerLaunches(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(2.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.GravityHammerItem.slam(helper.getLevel(), user);
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "slam did no damage");
            helper.assertTrue(target.getDeltaMovement().y > 0.5, "target not launched: " + target.getDeltaMovement());
            helper.succeed();
        }, user, target);
    }

    /** プラズマチャクラム: 投げた円盤が敵を斬って戻る */
    @GameTest(template = TEMPLATE, batch = "weapon_chakram", timeoutTicks = 400)
    public static void plasmaChakramReturns(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(5.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.ChakramEntity disc = com.automatavalkyries.weapon.PlasmaChakramItem.toss(helper.getLevel(), user);
            helper.succeedWhen(() -> {
                helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 2 * com.automatavalkyries.weapon.ChakramEntity.DAMAGE * 0.9F, "disc did not cut both ways: " + target.getHealth()); // ハスクの防具の分を見込む
                helper.assertTrue(disc.isRemoved(), "disc has not returned");
            });
        }, user, target);
    }

    /** プリズムビームライフル: 光線が当たり、照射し続けるほど強くなる */
    @GameTest(template = TEMPLATE, batch = "weapon_prism", timeoutTicks = 400)
    public static void prismBeamRamps(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.PrismBeamRifleItem.beam(helper.getLevel(), user, 0);
            float early = target.getMaxHealth() - target.getHealth();
            float before = target.getHealth();
            com.automatavalkyries.weapon.PrismBeamRifleItem.beam(helper.getLevel(), user, 60);
            float late = before - target.getHealth();
            helper.assertTrue(early > 0 && late > early * 2.5F, "ramp " + early + " -> " + late);
            helper.succeed();
        }, user, target);
    }

    /** サンダージャベリン: 刺さって 1 秒後に雷が落ちる */
    @GameTest(template = TEMPLATE, batch = "weapon_javelin", timeoutTicks = 380)
    public static void thunderJavelinStrikes(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        Zombie bystander = tough(helper, new Vec3(4.5, 2, 4.5));
        whenLoaded(helper, () -> {
            user.setXRot(5);
            com.automatavalkyries.weapon.ThunderJavelinItem.hurl(helper.getLevel(), user);
            helper.succeedWhen(() -> {
                helper.assertTrue(target.getHealth() < target.getMaxHealth() - 30, "javelin and lightning did not both hit: " + target.getHealth());
                helper.assertTrue(bystander.getHealth() < bystander.getMaxHealth(), "lightning did not spread");
            });
        }, user, target, bystander);
    }

    /** ブラックロータス: 嵐の中の敵が斬られる */
    @GameTest(template = TEMPLATE, batch = "weapon_lotus", timeoutTicks = 400)
    public static void blackLotusStormCuts(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(2.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.BlackLotusItem.storm(helper.getLevel(), user, com.automatavalkyries.weapon.BlackLotusItem.TICK_RATE);
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "storm did no damage");
            float before = target.getHealth();
            com.automatavalkyries.weapon.BlackLotusItem.burst(helper.getLevel(), user);
            helper.assertTrue(target.getHealth() < before, "burst did no damage");
            helper.succeed();
        }, user, target);
    }

    /** クロノリピーター: 1 射で 3 回（時の残響が 2 回）当たる */
    @GameTest(template = TEMPLATE, batch = "weapon_chrono", timeoutTicks = 360)
    public static void chronoRepeaterEchoes(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.ChronoRepeaterItem.fire(helper.getLevel(), user);
            helper.succeedWhen(() -> helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 3 * com.automatavalkyries.weapon.ChronoRepeaterItem.DAMAGE * 0.9F, // ハスクの防具の分を見込む
                    "echoes missing: " + target.getHealth()));
        }, user, target);
    }

    /** ヴォイドサイズ: 刈り取りでまわりの敵を斬り、持ち主が回復する */
    @GameTest(template = TEMPLATE, batch = "weapon_scythe", timeoutTicks = 400)
    public static void voidScytheReapsAndHeals(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(1.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.setHealth(5);
            Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
            // 新しく作られた区画に当たると、区画の準備が済むまで mob が世界に入らない（裏で区画を作る速さ次第）。入るまで刈り取りを繰り返す
            helper.succeedWhen(() -> {
                if (target.getHealth() >= target.getMaxHealth()) com.automatavalkyries.weapon.VoidScytheItem.reap(helper.getLevel(), user);
                helper.assertTrue(target.getHealth() < target.getMaxHealth(), "reap did no damage");
                helper.assertTrue(user.getHealth() > 5, "no lifesteal");
            });
        }, user);
    }

    /** ジェネシス・ヘイロー: 光の柱が注いで、柱の中の敵を焼く（撃った者には当たらない） */
    @GameTest(template = TEMPLATE, batch = "weapon_halo", timeoutTicks = 400)
    public static void genesisHaloStrikes(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 0.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 4.5));
        whenLoaded(helper, () -> {
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1024);
            target.setHealth(1024);
            com.automatavalkyries.weapon.GenesisHaloItem.strike(helper.getLevel(), user, helper.absoluteVec(new Vec3(4.5, 2, 4.5)));
            helper.succeedWhen(() -> {
                helper.assertTrue(target.isDeadOrDying(), "pillar did not burn the target: " + target.getHealth());
                helper.assertTrue(user.isAlive(), "the strike hit its owner");
            });
        }, user, target);
    }

    // ======== 採掘用の兵装 ========

    /** ドリルは石も土も速く掘り（電力があれば）、チェーンソーは丸太を速く切る。電力が無いと遅い */
    @GameTest(template = TEMPLATE)
    public static void miningToolSpeeds(GameTestHelper helper) {
        ItemStack drill = ((TacticalWeaponItem) ModWeapons.DRILL_T1.get()).charged();
        ItemStack saw = ((TacticalWeaponItem) ModWeapons.CHAINSAW_T1.get()).charged();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        var log = net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(drill.getDestroySpeed(stone) > 8 && drill.getDestroySpeed(dirt) > 8, "drill is slow");
        helper.assertTrue(drill.isCorrectToolForDrops(net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState()), "drill cannot harvest obsidian");
        helper.assertTrue(saw.getDestroySpeed(log) > 8 && saw.getDestroySpeed(stone) <= 1, "chainsaw speeds wrong");
        ItemStack empty = new ItemStack(ModWeapons.DRILL_T1.get());
        helper.assertTrue(empty.getDestroySpeed(stone) < 2, "unpowered drill is fast");
        helper.succeed();
    }

    private static FakePlayer miner(GameTestHelper helper, ItemStack tool) {
        FakePlayer player = player(helper, new Vec3(0.5, 2, 2.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return player;
    }

    /** ビヨンド・ドリル（T3）は向いた面の 3×3 をまとめて掘る */
    @GameTest(template = TEMPLATE, batch = "tool_area")
    public static void drillMinesThreeByThree(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(3, y, z), net.minecraft.world.level.block.Blocks.STONE);
        FakePlayer player = miner(helper, ((TacticalWeaponItem) ModWeapons.DRILL_T3.get()).charged());
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 3, 2)));
        for (int y = 2; y <= 4; y++) for (int z = 1; z <= 3; z++) helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(3, y, z));
        helper.succeed();
    }

    /** ビヨンド・チェーンソー（T3）はつながった丸太をまとめて切る */
    @GameTest(template = TEMPLATE, batch = "tool_fell")
    public static void chainsawFellsTree(GameTestHelper helper) {
        for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(3, y, 2), net.minecraft.world.level.block.Blocks.OAK_LOG);
        FakePlayer player = miner(helper, ((TacticalWeaponItem) ModWeapons.CHAINSAW_T3.get()).charged());
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 2)));
        for (int y = 2; y <= 6; y++) helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(3, y, 2));
        helper.succeed();
    }

    /** 採掘用の兵装で殴ると、相手の無敵時間を貫く */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void miningToolPiercesInvulnerability(GameTestHelper helper) {
        Zombie target = tough(helper, new Vec3(1.5, 2, 2.5));
        whenLoaded(helper, () -> {
            FakePlayer player = miner(helper, ((TacticalWeaponItem) ModWeapons.CHAINSAW_T2.get()).charged());
            target.hurt(target.damageSources().generic(), 50.0F); // 大きな被弾で無敵時間に入る（次の小さな一撃は普通なら弾かれる）
            float before = target.getHealth();
            helper.assertTrue(target.invulnerableTime > 10, "target is not invulnerable");
            player.attack(target);
            helper.assertTrue(target.getHealth() < before, "chainsaw did not pierce invulnerability");
            helper.succeed();
        }, target);
    }

    /** ハイエンド・ドリル（T1）でも初めから 3×3 で掘れ、右クリックで範囲が 1×1 ⇔ 3×3 と切り替わる。間合いはティアごとに伸びる */
    @GameTest(template = TEMPLATE, batch = "tool_area_t1")
    public static void drillT1AreaAndSwitch(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(3, y, z), net.minecraft.world.level.block.Blocks.STONE);
        ItemStack drill = ((TacticalWeaponItem) ModWeapons.DRILL_T1.get()).charged();
        com.automatavalkyries.weapon.MiningToolItem item = (com.automatavalkyries.weapon.MiningToolItem) drill.getItem();
        helper.assertTrue(item.areaRadius(drill) == 1, "T1 does not start at 3x3");
        FakePlayer player = miner(helper, drill);
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 3, 2)));
        for (int y = 2; y <= 4; y++) for (int z = 1; z <= 3; z++) helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, new BlockPos(3, y, z));
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(item.areaRadius(drill) == 0, "did not switch to 1x1");
        item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(item.areaRadius(drill) == 1, "did not wrap back to 3x3 (T1 max)");
        helper.assertTrue(com.automatavalkyries.weapon.MiningToolItem.blockReach(4) > com.automatavalkyries.weapon.MiningToolItem.blockReach(1), "reach does not grow with tier");
        helper.succeed();
    }

    // ---- 追加の兵装 第 2 弾 10 種 ----

    /**
     * 新しく作られた区画では、作った mob が世界に入るまで数 Tick かかる（その間は範囲の検索で見つからない）。全員が入ってから body を動かす。
     * 組（batch）を足すと後の試験の置き場所がずれて新しい区画に当たるので、一度に当てて確かめる試験はこれを通す（2026-10-04）
     */
    private static void whenLoaded(GameTestHelper helper, Runnable body, net.minecraft.world.entity.Entity... mobs) {
        helper.startSequence().thenWaitUntil(() -> {
            for (net.minecraft.world.entity.Entity e : mobs) helper.assertTrue(helper.getLevel().getEntity(e.getUUID()) == e, "mob not in the world yet");
        }).thenExecute(body);
    }

    /**
     * 試験の後片付け: 試験の場所のまわり 32 ブロックの生き物を消す。吹き飛ばし・引き寄せ・突進で枠の外へ出た mob は
     * 次の試験の枠を置くときに消えずに残り、後の試験の邪魔をする（雷の的を奪う、アイギスのドームで攻撃を止める等）
     */
    private static void sweep(GameTestHelper helper) {
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        for (net.minecraft.world.entity.LivingEntity e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(o).inflate(32), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            e.discard();
        }
    }

    // ======== T4 兵装の通常攻撃（2026-10-04。範囲・射程が長いので、それぞれ別の組で動かす） ========

    /** 通常攻撃は左クリック（振り）で出て、間隔のあいだは出ず、1 回分の電力だけ減る */
    @GameTest(template = TEMPLATE, batch = "t4_normal_ready")
    public static void t4NormalAttackCooldown(GameTestHelper helper) {
        FakePlayer player = player(helper, new Vec3(1, 2, 1));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ((TacticalWeaponItem) ModWeapons.LUMINOUS_REQUIEM.get()).charged());
        ItemStack stack = player.getMainHandItem();
        int before = ItemEnergy.get(stack);
        stack.getItem().onEntitySwing(stack, player);
        stack.getItem().onEntitySwing(stack, player);
        helper.assertTrue(before - ItemEnergy.get(stack) == 50_000, "energy used: " + (before - ItemEnergy.get(stack)));
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.automatavalkyries.weapon.RainbowSlashEntity.class, player.getBoundingBox().inflate(6)).size() == 1, "slash count is not 1");
        sweep(helper);
        helper.succeed();
    }

    /** T4 兵装を手に持っている間の加護（それぞれの兵装の印になる効果が付く） */
    @GameTest(template = TEMPLATE, batch = "t4_held")
    public static void t4HeldBlessings(GameTestHelper helper) {
        Object[][] cases = {
                {(java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) com.automatavalkyries.weapon.RagnarokEdgeItem::blessHolder, net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 3},
                {(java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) com.automatavalkyries.weapon.PunishmentRailgunItem::blessHolder, net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 2},
                {(java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) com.automatavalkyries.weapon.CatastropheBusterItem::blessHolder, net.minecraft.world.effect.MobEffects.SLOW_FALLING, 0},
                {(java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) com.automatavalkyries.weapon.GenesisHaloItem::blessHolder, net.minecraft.world.effect.MobEffects.NIGHT_VISION, 0}}; // ゾンビはアンデッドなので再生が付かない
        int i = 0;
        for (Object[] c : cases) {
            Zombie z = dummy(helper, new Vec3(0.5 + 2 * i++, 2, 0.5));
            @SuppressWarnings("unchecked") var bless = (java.util.function.Consumer<net.minecraft.world.entity.LivingEntity>) c[0];
            bless.accept(z);
            var effect = (net.minecraft.world.effect.MobEffect) c[1];
            helper.assertTrue(z.hasEffect(effect) && z.getEffect(effect).getAmplifier() == (int) c[2], "missing blessing " + effect.getDescriptionId() + " on case " + i);
            helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "no resistance on case " + i);
        }
        sweep(helper);
        helper.succeed();
    }

    /** ルミナス・レクイエム: 虹の斬撃が飛んで敵を斬る */
    @GameTest(template = TEMPLATE, batch = "t4_normal_requiem", timeoutTicks = 400)
    public static void requiemRainbowSlash(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(7.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.T4NormalAttacks.rainbowSlash(helper.getLevel(), user);
            helper.succeedWhen(() -> {
                helper.assertTrue(target.isDeadOrDying(), "rainbow slash did not cut down the target: " + target.getHealth());
                helper.assertTrue(user.getHealth() == user.getMaxHealth(), "the slash hit its owner");
                sweep(helper);
            });
        }, user, target);
    }

    /** ラグナロク: 横薙ぎが前にも後ろにも届く */
    @GameTest(template = TEMPLATE, batch = "t4_normal_ragnarok", timeoutTicks = 400)
    public static void ragnarokTwilightSweep(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(4.5, 2, 4.5));
        Zombie front = tough(helper, new Vec3(8.5, 2, 4.5));
        Zombie behind = tough(helper, new Vec3(0.5, 2, 4.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.T4NormalAttacks.twilightSweep(helper.getLevel(), user) >= 2, "sweep hit fewer than 2");
            helper.assertTrue(front.isDeadOrDying() && behind.isDeadOrDying(), "sweep did not cut both: " + front.getHealth() + " / " + behind.getHealth());
            sweep(helper);
            helper.succeed();
        }, user, front, behind);
    }

    /** パニッシュメント・レールガン: 狙った敵に落雷、近くの敵へ放電 */
    @GameTest(template = TEMPLATE, batch = "t4_normal_railgun", timeoutTicks = 400)
    public static void railgunThunderChains(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie aimed = tough(helper, new Vec3(8.5, 2, 2.5));
        Zombie near = tough(helper, new Vec3(8.5, 2, 6.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.T4NormalAttacks.thunder(helper.getLevel(), user) == aimed, "thunder did not strike the aimed enemy");
            helper.assertTrue(aimed.isDeadOrDying(), "aimed enemy survived: " + aimed.getHealth());
            helper.assertTrue(near.isDeadOrDying(), "discharge did not reach the nearby enemy: " + near.getHealth());
            sweep(helper);
            helper.succeed();
        }, user, aimed, near);
    }

    /** カタストロフ・バスター: 極小のカタストロフが脇の敵を追って当たる */
    @GameTest(template = TEMPLATE, batch = "t4_normal_buster", timeoutTicks = 400)
    public static void busterMiniCatastropheHoms(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(8.5, 2, 5.5)); // まっすぐ前ではない
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.T4NormalAttacks.miniCatastrophe(helper.getLevel(), user);
            helper.succeedWhen(() -> {
                helper.assertTrue(target.isDeadOrDying(), "tiny catastrophe did not reach the target: " + target.getHealth());
                var last = user.getLastDamageSource(); // ゾンビは日光で燃えるので、体力ではなくダメージの種類で見る
                helper.assertFalse(last != null && last.is(com.automatavalkyries.weapon.WeaponDamage.CATASTROPHE), "the catastrophe hit its owner");
                sweep(helper);
            });
        }, user, target);
    }

    /** ジェネシス・ヘイロー: 光線が線上の 2 体を貫く */
    @GameTest(template = TEMPLATE, batch = "t4_normal_genesis", timeoutTicks = 400)
    public static void genesisLaserPierces(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie near = tough(helper, new Vec3(5.5, 2, 2.5));
        Zombie far = tough(helper, new Vec3(10.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.T4NormalAttacks.genesisLaser(helper.getLevel(), user) >= 2, "laser hit fewer than 2");
            helper.assertTrue(near.isDeadOrDying() && far.isDeadOrDying(), "laser did not pierce: " + near.getHealth() + " / " + far.getHealth());
            sweep(helper);
            helper.succeed();
        }, user, near, far);
    }

    /** ヒートパイルバンカー: 正面の敵に杭を打ち込み、燃やす */
    @GameTest(template = TEMPLATE, batch = "weapon2_pile", timeoutTicks = 400)
    public static void heatPileStrikes(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(2.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.HeatPileBunkerItem.strike(helper.getLevel(), user) == target, "pile missed");
            helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 20, "pile too weak: " + target.getHealth());
            helper.assertTrue(target.getRemainingFireTicks() > 0, "target not set on fire");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ツインブラスター: 連射の 1 発ずつが無敵時間に弾かれずに入る */
    @GameTest(template = TEMPLATE, batch = "weapon2_twin", timeoutTicks = 400)
    public static void twinBlasterRapidFire(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.TwinBlasterItem.shot(helper.getLevel(), user, 1, 1);
            float first = target.getHealth();
            com.automatavalkyries.weapon.TwinBlasterItem.shot(helper.getLevel(), user, -1, -1); // オフハンド（左）から
            helper.assertTrue(first < target.getMaxHealth(), "first bolt did nothing");
            helper.assertTrue(target.getHealth() < first, "second bolt was blocked by invulnerability");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ショックウィップ: 扇の敵を打って引き寄せ、鈍らせる */
    @GameTest(template = TEMPLATE, batch = "weapon2_whip", timeoutTicks = 400)
    public static void shockWhipPulls(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.ShockWhipItem.lash(helper.getLevel(), user) >= 1, "whip hit nothing");
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "whip did no damage");
            helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "target not slowed");
            helper.assertTrue(target.getDeltaMovement().x < 0, "target not pulled toward the user: " + target.getDeltaMovement());
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** プラズマフレイマー: 扇の敵を焼く */
    @GameTest(template = TEMPLATE, batch = "weapon2_flamer", timeoutTicks = 400)
    public static void plasmaFlamerBurns(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.PlasmaFlamerItem.pulse(helper.getLevel(), user) >= 1, "flame missed"); // 前の試験で飛ばされた mob が近くに残ることがある
            float first = target.getHealth();
            com.automatavalkyries.weapon.PlasmaFlamerItem.pulse(helper.getLevel(), user);
            helper.assertTrue(first < target.getMaxHealth() && target.getHealth() < first, "flame pulses blocked: " + target.getHealth());
            helper.assertTrue(target.getRemainingFireTicks() > 0, "target not set on fire");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ミラージュダガー: 敵の背後へ跳んで刺す */
    @GameTest(template = TEMPLATE, batch = "weapon2_dagger", timeoutTicks = 400)
    public static void mirageDaggerBlinksBehind(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.MirageDaggerItem.blink(helper.getLevel(), user) == target, "did not blink to the target");
            helper.assertTrue(user.getX() > target.getX(), "user is not behind the target: " + user.getX() + " vs " + target.getX());
            helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 15, "backstab too weak: " + target.getHealth());
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ヴォルテックスキャノン: 渦が 2 秒後に潰れて、まわりの敵にもダメージ */
    @GameTest(template = TEMPLATE, batch = "weapon2_vortex", timeoutTicks = 400)
    public static void vortexCannonCollapses(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie first = tough(helper, new Vec3(4.5, 2, 2.5));
        Zombie side = tough(helper, new Vec3(4.5, 2, 0.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.VortexCannonItem.fire(helper.getLevel(), user);
            helper.runAfterDelay(com.automatavalkyries.weapon.VortexCannonItem.DURATION + 5, () -> {
                helper.assertTrue(first.getHealth() < first.getMaxHealth(), "vortex did not hurt the target");
                helper.assertTrue(side.getHealth() < side.getMaxHealth(), "vortex collapse did not reach the side");
                sweep(helper);
                helper.succeed();
            });
        }, user, first, side);
    }

    /** 星弓: 矢が貫き、星が降った所の敵にも当たる */
    @GameTest(template = TEMPLATE, batch = "weapon2_starfall", timeoutTicks = 400)
    public static void starfallBowHits(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.StarfallBowItem.loose(helper.getLevel(), user, 0.5F); // 途中まで引いた矢（星は降らない）
            float afterArrow = target.getHealth();
            helper.assertTrue(afterArrow < target.getMaxHealth() && afterArrow > target.getMaxHealth() - 20, "half-drawn arrow damage is off: " + afterArrow);
            com.automatavalkyries.weapon.StarfallBowItem.star(helper.getLevel(), user, target.position());
            helper.assertTrue(target.getHealth() < afterArrow, "falling star missed");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** 蝕刃: 印が 5 つ揃うと日蝕で大きな一撃と回復 */
    @GameTest(template = TEMPLATE, batch = "weapon2_eclipse", timeoutTicks = 400)
    public static void eclipseBladeDetonates(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(2.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.setHealth(5);
            for (int i = 1; i < com.automatavalkyries.weapon.EclipseBladeItem.MARKS; i++) {
                helper.assertTrue(com.automatavalkyries.weapon.EclipseBladeItem.mark(helper.getLevel(), user, target) == i, "mark count wrong at " + i);
            }
            helper.assertTrue(target.getHealth() == target.getMaxHealth(), "marks alone should not hurt");
            helper.assertTrue(com.automatavalkyries.weapon.EclipseBladeItem.mark(helper.getLevel(), user, target) == 0, "eclipse did not trigger at 5 marks");
            helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 50, "eclipse too weak: " + target.getHealth());
            helper.assertTrue(user.getHealth() > 5, "attacker not healed");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** 熾天杖: 持ち主を癒やし、アンデッドの敵を 2 倍で打つ */
    @GameTest(template = TEMPLATE, batch = "weapon2_seraph", timeoutTicks = 400)
    public static void seraphStaffBlesses(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.setHealth(5);
            int[] result = com.automatavalkyries.weapon.SeraphStaffItem.bless(helper.getLevel(), user);
            helper.assertTrue(result[0] >= 1 && result[1] >= 1, "bless counts wrong: healed " + result[0] + ", smitten " + result[1]); // 隣の試験の mob も範囲に入ることがある
            helper.assertTrue(user.getHealth() > 5 && user.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "user not blessed"); // ゾンビはアンデッドなので再生は付かない
            helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 20, "undead smite not doubled: " + target.getHealth());
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ラグナロク・エッジ: 正面の敵は大地ごと両断され、背後の敵は無事 */
    @GameTest(template = TEMPLATE, batch = "weapon2_ragnarok", timeoutTicks = 360)
    public static void ragnarokEdgeCleaves(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(2.5, 2, 2.5));
        Zombie front = tough(helper, new Vec3(4.5, 2, 2.5));
        Zombie behind = tough(helper, new Vec3(0.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.RagnarokEdgeItem.cleave(helper.getLevel(), user);
            helper.runAfterDelay(com.automatavalkyries.weapon.RagnarokEdgeItem.STEPS + 2, () -> {
                helper.assertTrue(!front.isAlive(), "front target survived the cleave: " + front.getHealth());
                helper.assertTrue(behind.isAlive() && behind.getHealth() == behind.getMaxHealth(), "cleave hit behind the user");
                sweep(helper);
                helper.succeed();
            });
        }, user, front, behind);
    }

    /** 終の黄昏: 斬撃は上下にも向いた方向へ飛ぶ（斜め上の敵に届く） */
    @GameTest(template = TEMPLATE, batch = "weapon2_ragnarok_up", timeoutTicks = 600)
    public static void ragnarokFollowsPitch(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(1.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.moveTo(user.getX(), user.getY(), user.getZ(), -90.0F, -45.0F); // +X の斜め上を向く
            Zombie above = tough(helper, new Vec3(4.5, 5, 2.5));
            above.setNoGravity(true);
            // 新しく作られた区画に当たると、準備が済むまで mob が世界に入らない。届かなければ 1 秒ごとに振り直す
            int[] tick = {0};
            helper.succeedWhen(() -> {
                if (tick[0]++ % 20 == 0 && above.isAlive()) com.automatavalkyries.weapon.RagnarokEdgeItem.cleave(helper.getLevel(), user);
                helper.assertTrue(!above.isAlive(), "upward cleave missed the target above: " + above.getHealth());
            });
        }, user);
    }

    // ---- 追加の兵装 第 3 弾 17 種 ----


    /** ヴェノムスティンガー: 突進が通り道の敵を刺して弱らせる */
    @GameTest(template = TEMPLATE, batch = "weapon3_venom", timeoutTicks = 400)
    public static void venomStingerLunges(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T1.VenomStinger.lunge(helper.getLevel(), user) >= 1, "lunge hit nothing");
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "lunge did not weaken the target");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** フラッシュランチャー: 閃光弾で目をくらませる */
    @GameTest(template = TEMPLATE, batch = "weapon3_flash", timeoutTicks = 400)
    public static void flashLauncherBlinds(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T1.FlashLauncher.flash(helper.getLevel(), user) >= 1, "flash hit nothing");
            helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && target.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "target not blinded");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** ゲイルファン: 敵を吹き飛ばし、飛んでくる矢を撃ち返す */
    @GameTest(template = TEMPLATE, batch = "weapon3_gale", timeoutTicks = 400)
    public static void galeFanBlowsAndReflects(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(2.5, 3.6, 2.5));
            Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z);
            arrow.setDeltaMovement(-1.0, 0, 0);
            helper.getLevel().addFreshEntity(arrow);
            int[] r = com.automatavalkyries.weapon.Batch3T1.GaleFan.gust(helper.getLevel(), user);
            helper.assertTrue(r[0] >= 1 && target.getDeltaMovement().x > 0.5, "target not blown away: " + target.getDeltaMovement());
            helper.assertTrue(r[1] >= 1 && arrow.getDeltaMovement().x > 0 && arrow.getOwner() == user, "arrow not turned back");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** フロストナックル: 3 発目で氷が砕ける */
    @GameTest(template = TEMPLATE, batch = "weapon3_frost", timeoutTicks = 400)
    public static void frostKnuckleShatters(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(2.5, 2, 2.5));
        whenLoaded(helper, () -> {
            var level = helper.getLevel();
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T1.FrostKnuckle.punch(level, user, target) == 1, "first punch count wrong");
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T1.FrostKnuckle.punch(level, user, target) == 2, "second punch count wrong");
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T1.FrostKnuckle.punch(level, user, target) == 0, "third punch did not shatter");
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && target.hasEffect(ModWeapons.FROSTBITE.get()), "shatter did nothing");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** クリムゾンシックル: 血の波動で吸って回復する */
    @GameTest(template = TEMPLATE, batch = "weapon3_crimson", timeoutTicks = 600)
    public static void crimsonSickleDrains(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            user.setHealth(5);
            // 新しく作られた区画に当たると、準備が済むまで mob が世界に入らない。入るまで 0.5 秒ごとに吸い直す
            int[] tick = {0};
            helper.succeedWhen(() -> {
                if (tick[0]++ % 10 == 0 && !target.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) com.automatavalkyries.weapon.Batch3T2.CrimsonSickle.drain(helper.getLevel(), user);
                helper.assertTrue(user.getHealth() > 5 && target.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "drain did not heal or wither");
            });
        }, user, target);
    }

    /** レビテーションロッド: 浮かせて、3 秒後に叩き落とす */
    @GameTest(template = TEMPLATE, batch = "weapon3_levitate", timeoutTicks = 400)
    public static void levitationRodLiftsAndSlams(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T2.LevitationRod.levitate(helper.getLevel(), user).contains(target), "target not lifted");
            helper.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION), "no levitation");
            float lifted = target.getHealth();
            helper.runAfterDelay(com.automatavalkyries.weapon.Batch3T2.LevitationRod.LIFT + 5, () -> {
                helper.assertTrue(target.getHealth() < lifted, "slam did no damage");
                sweep(helper);
                helper.succeed();
            });
        }, user, target);
    }

    /** リコシェライフル: 弾が次の敵へ跳ねる */
    @GameTest(template = TEMPLATE, batch = "weapon3_ricochet", timeoutTicks = 400)
    public static void ricochetRifleBounces(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie first = tough(helper, new Vec3(4.5, 2, 2.5));
        Zombie second = tough(helper, new Vec3(4.5, 2, 0.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T2.RicochetRifle.ricochet(helper.getLevel(), user) >= 2, "bullet did not bounce");
            helper.assertTrue(first.getHealth() < first.getMaxHealth() && second.getHealth() < second.getMaxHealth(), "bounce missed a target");
            sweep(helper);
                helper.succeed();
        }, user, first, second);
    }

    /** スペクトラルチェイン: 鎖を撃ち込んで飛びつく */
    @GameTest(template = TEMPLATE, batch = "weapon3_chain", timeoutTicks = 400)
    public static void spectralChainHooks(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T2.SpectralChain.hook(helper.getLevel(), user) == target, "chain missed");
            helper.assertTrue(user.getDeltaMovement().x > 0.3 && target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "user not pulled or target not bound");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** スタティックトラップ: 敵が近づくと炸裂する */
    @GameTest(template = TEMPLATE, batch = "weapon3_trap", timeoutTicks = 400)
    public static void staticTrapBursts(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.Batch3T2.StaticTrap.place(helper.getLevel(), user, target.position());
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "trap did not burst");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** バーサークアックス: 体力が減るほど強く、激昂で強化される */
    @GameTest(template = TEMPLATE, batch = "weapon3_berserk", timeoutTicks = 400)
    public static void berserkAxeScales(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T2.BerserkAxe.multiplier(user) == 1F, "full health multiplier should be 1");
            user.setHealth(user.getMaxHealth() * 0.2F);
            helper.assertTrue(Math.abs(com.automatavalkyries.weapon.Batch3T2.BerserkAxe.multiplier(user) - 2F) < 1.0E-3, "20% health multiplier should be 2");
            user.setHealth(user.getMaxHealth());
            com.automatavalkyries.weapon.Batch3T2.BerserkAxe.rage(helper.getLevel(), user);
            helper.assertTrue(user.getHealth() < user.getMaxHealth() && user.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST), "rage did not cost health or buff");
            sweep(helper);
                helper.succeed();
        }, user);
    }

    /** ソニックキャノン: 一直線の敵をまとめて打つ */
    @GameTest(template = TEMPLATE, batch = "weapon3_sonic", timeoutTicks = 400)
    public static void sonicCannonPierces(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie near = tough(helper, new Vec3(2.5, 2, 2.5));
        Zombie far = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T2.SonicCannon.blast(helper.getLevel(), user) >= 2, "shockwave did not pierce");
            helper.assertTrue(near.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION) && far.getHealth() < far.getMaxHealth(), "shockwave effects missing");
            sweep(helper);
                helper.succeed();
        }, user, near, far);
    }

    /** 呪詛の魔導書: 呪われた敵は 1.5 倍のダメージを受ける */
    @GameTest(template = TEMPLATE, batch = "weapon3_hex", timeoutTicks = 400)
    public static void hexGrimoireAmplifies(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.HexGrimoire.hex(helper.getLevel(), user) >= 1, "hex hit nothing");
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.HexGrimoire.cursed(target), "target not cursed");
            target.removeAllEffects();
            target.invulnerableTime = 0;
            float before = target.getHealth();
            target.hurt(helper.getLevel().damageSources().magic(), 10.0F);
            helper.assertTrue(before - target.getHealth() > 14.0F, "curse did not amplify: took " + (before - target.getHealth()));
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** 不死鳥の翼: 飛翔した通り道の敵を焼く */
    @GameTest(template = TEMPLATE, batch = "weapon3_phoenix", timeoutTicks = 400)
    public static void phoenixWingBurns(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.PhoenixWing.soar(helper.getLevel(), user) >= 1, "soar burned nothing");
            helper.assertTrue(target.getRemainingFireTicks() > 0 && user.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE), "fire effects missing");
            helper.assertTrue(user.getDeltaMovement().x > 1.0, "user did not soar: " + user.getDeltaMovement());
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** 嵐呼びの杖: 雷が範囲の敵に落ちる */
    @GameTest(template = TEMPLATE, batch = "weapon3_storm", timeoutTicks = 100)
    public static void stormCallerStrikes(GameTestHelper helper) {
        sweep(helper); // 前の試験で枠の外へ飛ばされた mob が残っていると、雷の的を奪われる
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.StormCaller.call(helper.getLevel(), user) >= 8, "storm did not start");
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "no lightning hit the target");
            sweep(helper);
            helper.succeed();
        });
    }

    /** 鏡刃: 守りの最中、近接は減らして相手に返す */
    @GameTest(template = TEMPLATE, batch = "weapon3_reflector", timeoutTicks = 400)
    public static void reflectorBladeReturnsMelee(GameTestHelper helper) {
        Zombie defender = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie attacker = tough(helper, new Vec3(1.5, 2, 2.5));
        whenLoaded(helper, () -> {
            defender.setItemInHand(InteractionHand.MAIN_HAND, ((TacticalWeaponItem) ModWeapons.REFLECTOR_BLADE.get()).charged());
            defender.startUsingItem(InteractionHand.MAIN_HAND);
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.ReflectorBlade.guarding(defender), "not guarding");
            defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 8.0F);
            helper.assertTrue(defender.getHealth() >= defender.getMaxHealth() - 2.5F, "melee not reduced: " + defender.getHealth());
            helper.assertTrue(attacker.getHealth() < attacker.getMaxHealth(), "nothing returned to the attacker");
            sweep(helper);
                helper.succeed();
        }, defender, attacker);
    }

    /** 新星槍: 新星と突進 */
    @GameTest(template = TEMPLATE, batch = "weapon3_nova", timeoutTicks = 400)
    public static void novaLanceBursts(GameTestHelper helper) {
        Zombie user = facingX(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(3.5, 2, 2.5));
        whenLoaded(helper, () -> {
            com.automatavalkyries.weapon.Batch3T3.NovaLance.nova(helper.getLevel(), user, new Vec3(target.getX(), target.getY(0.5), target.getZ()));
            helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 25 && target.getRemainingFireTicks() > 0, "nova too weak: " + target.getHealth());
            float afterNova = target.getHealth();
            target.invulnerableTime = 0;
            helper.assertTrue(com.automatavalkyries.weapon.Batch3T3.NovaLance.thrust(helper.getLevel(), user) >= 1 && target.getHealth() < afterNova, "thrust missed");
            sweep(helper);
                helper.succeed();
        }, user, target);
    }

    /** 星葬冠: 範囲の敵の時を止め、1 秒後に星の光で消す */
    @GameTest(template = TEMPLATE, batch = "weapon3_requiem", timeoutTicks = 380)
    public static void luminousRequiemErases(GameTestHelper helper) {
        Zombie user = dummy(helper, new Vec3(0.5, 2, 2.5));
        Zombie target = tough(helper, new Vec3(4.5, 2, 2.5));
        whenLoaded(helper, () -> {
            helper.assertTrue(com.automatavalkyries.weapon.LuminousRequiemItem.requiem(helper.getLevel(), user) >= 1, "requiem targeted nothing");
            helper.assertTrue(target.hasEffect(ModWeapons.CAUSAL_STOP.get()), "target not frozen in time");
            helper.runAfterDelay(com.automatavalkyries.weapon.LuminousRequiemItem.DELAY + 3, () -> {
                helper.assertTrue(!target.isAlive(), "requiem did not erase the target: " + target.getHealth());
                sweep(helper);
                helper.succeed();
            });
        }, user, target);
    }
}
