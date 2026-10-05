package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.ValkyrieArts.Action;
import com.automatavalkyries.weapon.ValkyrieArts.Art;
import com.automatavalkyries.weapon.ValkyrieArts.When;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import static com.automatavalkyries.weapon.ValkyrieArts.all;
import static com.automatavalkyries.weapon.ValkyrieArts.any;
import static com.automatavalkyries.weapon.ValkyrieArts.enemiesWithin;
import static com.automatavalkyries.weapon.ValkyrieArts.friendBelow;
import static com.automatavalkyries.weapon.ValkyrieArts.healthAbove;
import static com.automatavalkyries.weapon.ValkyrieArts.projectilesNear;
import static com.automatavalkyries.weapon.ValkyrieArts.put;
import static com.automatavalkyries.weapon.ValkyrieArts.strike;
import static com.automatavalkyries.weapon.ValkyrieArts.targetBetween;
import static com.automatavalkyries.weapon.ValkyrieArts.worthy;

/**
 * 兵装ごとの、戦術兵姫の使い方の表（{@link ValkyrieArts}、段階 3、2026-10-05）。間合い・間隔は【仮】
 * <ul>
 *   <li>近接の通常攻撃は {@link ValkyrieArts#strike}（振り＋近接攻撃＋当てたときの処理）。T4 の通常攻撃は振りで出る</li>
 *   <li>右クリックが唯一の攻撃の銃・砲は通常攻撃（{@link ValkyrieArts#use}）、まわりへの範囲技・守り・回復は技（使ってよいときを付ける）</li>
 *   <li>T4 の溜め撃ちは大技に見合う相手（最大 HP 100 以上か、まわりに敵 3 体以上）にだけ</li>
 * </ul>
 */
final class ValkyrieArtTable {

    private ValkyrieArtTable() {}

    private static final Action USE = ValkyrieArts::use, SKILL = ValkyrieArts::skillOf, SWING = ValkyrieArts::swing;
    /** 大技に見合う相手 */
    private static final When WORTHY = worthy(100.0F, 6.0, 3);
    /** 持ち主・味方が危ない（HP 50% 未満）か、敵・飛び道具が迫っている */
    private static final When THREAT = any(friendBelow(8.0, 0.5F), projectilesNear(8.0), enemiesWithin(4.0, 2));

    static void fill() {
        // ---- T1 ----
        put(ModWeapons.PHOTON_EDGE, Art.melee(3.0).basic(10, strike(3.0)).build());
        put(ModWeapons.LINEAR_RAIL_RIFLE, Art.ranged(48.0).basic(20, USE).build());
        put(ModWeapons.NECROSIS_NEEDLE_GUN, Art.ranged(24.0).basic(8, USE).build());
        put(ModWeapons.ARC_LANCER, Art.melee(4.0).basic(14, strike(4.0)).build());
        put(ModWeapons.SCATTER_BLASTER, Art.ranged(12.0).basic(16, USE).build());
        put(ModWeapons.GRAVITY_HAMMER, Art.melee(3.0).basic(25, strike(3.0))
                .skill(6.0, 60, USE).when(enemiesWithin(GravityHammerItem.RADIUS, 1)).build());
        put(ModWeapons.HEAT_PILE, Art.melee(3.0).basic(16, strike(3.0))
                .skill(HeatPileBunkerItem.REACH, 40, USE).when(targetBetween(0, HeatPileBunkerItem.REACH)).build());
        put(ModWeapons.TWIN_BLASTER, Art.ranged(24.0).basic(36, USE).build());
        put(ModWeapons.SHOCK_WHIP, Art.melee(4.0).basic(12, strike(4.0))
                .skill(ShockWhipItem.LASH_RANGE, 20, USE).when(targetBetween(0, ShockWhipItem.LASH_RANGE)).build());
        put(ModWeapons.VENOM_STINGER, Art.melee(2.5).basic(10, strike(2.5)).skill(6.0, 30, SKILL).build());
        put(ModWeapons.FLASH_LAUNCHER, Art.ranged(20.0).basic(40, SKILL).build());
        put(ModWeapons.GALE_FAN, Art.melee(3.0).basic(12, strike(3.0))
                .skill(10.0, 20, SKILL).when(any(projectilesNear(10.0), targetBetween(0, 6.0))).build());
        put(ModWeapons.FROST_KNUCKLE, Art.melee(2.5).basic(11, strike(2.5)).skill(4.0, 40, SKILL).when(enemiesWithin(4.0, 1)).build());

        // ---- 左手のモジュール ----
        put(ModWeapons.REPAIR_INJECTOR, Art.support(RepairInjectorItem.VALKYRIE_RANGE).swing(20)
                .skill(64.0, RepairInjectorItem.COOLDOWN, USE)
                .when(friendBelow(RepairInjectorItem.VALKYRIE_RANGE, RepairInjectorItem.VALKYRIE_THRESHOLD)).offhand().build());
        put(ModWeapons.POLAR_DEFLECTOR, Art.melee(2.5).basic(15, strike(2.5))
                .skill(64.0, PolarDeflectorItem.VALKYRIE_HOLD + 20, USE).when(THREAT).offhand().build());
        put(ModWeapons.AEGIS_BARRIER, Art.melee(2.5).basic(15, strike(2.5))
                .skill(64.0, AegisBarrierItem.VALKYRIE_HOLD + 40, USE)
                .when(any(friendBelow(AegisBarrierItem.RADIUS + 4.0, 0.5F), projectilesNear(10.0), enemiesWithin(6.0, 3))).offhand().build());

        // ---- T2 ----
        put(ModWeapons.LUXION_SABER, Art.melee(3.0).basic(6, strike(3.0))
                .skill(LuxionSaberItem.BLINK_RANGE, 30, USE).when(targetBetween(4.0, LuxionSaberItem.BLINK_RANGE)).build());
        put(ModWeapons.PLASMA_CHAKRAM, Art.ranged(20.0).basic(40, USE).build());
        put(ModWeapons.PRISM_BEAM_RIFLE, Art.ranged(32.0).basic(90, USE).build());
        put(ModWeapons.HEAVY_PARTICLE_CANNON, Art.ranged(48.0).charged(64.0, 100).build());
        put(ModWeapons.THUNDER_JAVELIN, Art.ranged(32.0).charged(40.0, 30).build());
        put(ModWeapons.PLASMA_FLAMER, Art.ranged(PlasmaFlamerItem.RANGE).basic(32, USE).build());
        put(ModWeapons.MIRAGE_DAGGER, Art.melee(2.5).basic(8, strike(2.5))
                .skill(MirageDaggerItem.RANGE, MirageDaggerItem.COOLDOWN, USE).when(targetBetween(3.0, MirageDaggerItem.RANGE)).build());
        put(ModWeapons.VORTEX_CANNON, Art.ranged(24.0).basic(VortexCannonItem.COOLDOWN, USE).build());
        put(ModWeapons.CRIMSON_SICKLE, Art.melee(3.0).basic(14, strike(3.0)).skill(6.0, 80, SKILL).when(enemiesWithin(6.0, 1)).build());
        put(ModWeapons.LEVITATION_ROD, Art.ranged(6.0).skill(8.0, 120, SKILL).when(enemiesWithin(8.0, 1)).build());
        put(ModWeapons.RICOCHET_RIFLE, Art.ranged(32.0).basic(20, SKILL).build());
        put(ModWeapons.SPECTRAL_CHAIN, Art.melee(2.5).basic(12, strike(2.5)).skill(20.0, 40, SKILL).when(targetBetween(4.0, 20.0)).build());
        put(ModWeapons.STATIC_TRAP, Art.ranged(14.0).basic(30, SKILL).build());
        put(ModWeapons.BERSERK_AXE, Art.melee(3.0).basic(22, strike(3.0))
                .skill(4.0, 400, SKILL).when(all(healthAbove(0.5F), targetBetween(0, 4.0))).build());
        put(ModWeapons.SONIC_CANNON, Art.ranged(20.0).basic(40, SKILL).build());

        // ---- T3 ----
        put(ModWeapons.TIME_ELIMINATOR, Art.melee(3.0).basic(20, strike(3.0)).build());
        put(ModWeapons.QUANTUM_BLADE, Art.melee(3.0).basic(10, strike(3.0))
                .skill(20.0, 10, SWING).when(targetBetween(3.5, 20.0)).build()); // 離れていても振れば斬撃が飛ぶ
        put(ModWeapons.AVALANCHE, Art.ranged(48.0).basic(AvalancheLauncherItem.MISSILES / AvalancheLauncherItem.PER_TICK + AvalancheLauncherItem.RELOAD, USE).build());
        put(ModWeapons.BLACK_LOTUS, Art.melee(3.0).basic(12, strike(3.0))
                .skill(BlackLotusItem.REACH, BlackLotusItem.COOLDOWN, USE).when(enemiesWithin(BlackLotusItem.REACH, 1)).build());
        put(ModWeapons.CHRONO_REPEATER, Art.ranged(40.0).basic(20, USE).build());
        put(ModWeapons.VOID_SCYTHE, Art.melee(3.5).basic(22, strike(3.5))
                .skill(VoidScytheItem.REAP_RADIUS, VoidScytheItem.COOLDOWN, USE).when(enemiesWithin(VoidScytheItem.REAP_RADIUS, 1)).build());
        put(ModWeapons.STARFALL_BOW, Art.ranged(48.0).basic(30, USE).build());
        put(ModWeapons.ECLIPSE_BLADE, Art.melee(3.0).basic(12, strike(3.0)).build());
        put(ModWeapons.SERAPH_STAFF, Art.support(SeraphStaffItem.RADIUS).swing(20)
                .skill(SeraphStaffItem.RADIUS, SeraphStaffItem.COOLDOWN, USE)
                .when(any(friendBelow(SeraphStaffItem.RADIUS, 0.7F), enemiesWithin(SeraphStaffItem.RADIUS, 2))).build());
        put(ModWeapons.HEX_GRIMOIRE, Art.ranged(12.0).skill(12.0, 200, SKILL).when(enemiesWithin(12.0, 1)).build());
        put(ModWeapons.PHOENIX_WING, Art.ranged(16.0).skill(20.0, 120, SKILL).when(targetBetween(5.0, 20.0)).build());
        put(ModWeapons.STORM_CALLER, Art.ranged(16.0).skill(16.0, 160, SKILL).build());
        put(ModWeapons.REFLECTOR_BLADE, Art.melee(3.0).basic(12, strike(3.0))
                .skill(64.0, Batch3T3.ReflectorBlade.VALKYRIE_GUARD + 40, USE).when(any(projectilesNear(8.0), enemiesWithin(4.0, 2))).build());
        put(ModWeapons.NOVA_LANCE, Art.melee(5.0).basic(20, strike(5.0)).skill(8.0, 60, SKILL).when(targetBetween(0, 8.0)).build());

        // ---- T4（通常攻撃は振りで出る。溜め撃ちは大技に見合う相手にだけ） ----
        put(ModWeapons.PUNISHMENT_RAILGUN, Art.ranged(48.0).basic(15, SWING).charged(96.0, 1200).when(WORTHY).build());
        put(ModWeapons.CATASTROPHE_BUSTER, Art.ranged(32.0).basic(15, SWING).charged(64.0, 600).when(WORTHY).build());
        put(ModWeapons.GENESIS_HALO, Art.ranged(48.0).basic(8, SWING).charged(96.0, 1200).when(WORTHY).build());
        put(ModWeapons.RAGNAROK_EDGE, Art.melee(5.0).basic(12, strike(3.5)).charged(64.0, 1200).when(WORTHY).build()); // 黄昏の横薙ぎは半径 7
        // 星葬冠の通常攻撃は虹の斬撃が 40 ブロック飛ぶので、間を取って振る（近ければ剣でも当たる）
        put(ModWeapons.LUMINOUS_REQUIEM, Art.ranged(24.0).basic(6, strike(3.5)).charged(48.0, 1200).when(WORTHY).build());

        // ---- 採掘用（近接の振りだけ） ----
        for (RegistryObject<Item> tool : java.util.List.of(ModWeapons.DRILL_T1, ModWeapons.CHAINSAW_T1, ModWeapons.DRILL_T2, ModWeapons.CHAINSAW_T2,
                ModWeapons.DRILL_T3, ModWeapons.CHAINSAW_T3, ModWeapons.DRILL_T4, ModWeapons.CHAINSAW_T4)) {
            put(tool, Art.melee(3.0).basic(14, strike(3.0)).build());
        }
    }
}
