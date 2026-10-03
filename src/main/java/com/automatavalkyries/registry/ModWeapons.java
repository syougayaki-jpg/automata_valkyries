package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.LuminalCoreItem;
import com.automatavalkyries.weapon.Necrosis;
import com.automatavalkyries.weapon.NecrosisNeedleEntity;
import com.automatavalkyries.weapon.NecrosisNeedleItem;
import com.automatavalkyries.weapon.PhotonEdgeItem;
import com.automatavalkyries.weapon.PolarDeflectorItem;
import com.automatavalkyries.weapon.RailRifleItem;
import com.automatavalkyries.weapon.RepairInjectorItem;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 兵器ユニット（兵器ユニット設計書 docs/SPEC_WEAPON_UNITS.md）: ルミナルコア（§7.1）・兵装（§7.2）・弾薬。
 * 兵装とコアはタクティカル・アーモリー（armory_assembling）で組み立て、弾薬は作業台で作る。
 */
public final class ModWeapons {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, AutomataValkyries.MODID);

    // ---- ルミナルコア（§7.1） ----
    public static final RegistryObject<Item> CORE_BASIC = core(LuminalCoreItem.Grade.BASIC, Rarity.UNCOMMON);
    public static final RegistryObject<Item> CORE_HIGH_DENSITY = core(LuminalCoreItem.Grade.HIGH_DENSITY, Rarity.RARE);
    public static final RegistryObject<Item> CORE_SINGULARITY = core(LuminalCoreItem.Grade.SINGULARITY, Rarity.RARE);
    public static final RegistryObject<Item> CORE_CHRONO_GENESIS = core(LuminalCoreItem.Grade.CHRONO_GENESIS, Rarity.EPIC);
    public static final RegistryObject<Item> CORE_STELLA_GENESIS_OMEGA = core(LuminalCoreItem.Grade.STELLA_OMEGA, Rarity.EPIC);

    // ---- T1 の兵装（§7.2） ----
    public static final RegistryObject<Item> PHOTON_EDGE = ModItems.ITEMS.register("weapon_photon_edge", () ->
            new PhotonEdgeItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> LINEAR_RAIL_RIFLE = ModItems.ITEMS.register("weapon_linear_rail_rifle", () ->
            new RailRifleItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> NECROSIS_NEEDLE_GUN = ModItems.ITEMS.register("weapon_necrosis_needle", () ->
            new NecrosisNeedleItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> POLAR_DEFLECTOR = ModItems.ITEMS.register("module_polar_deflector", () ->
            new PolarDeflectorItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> REPAIR_INJECTOR = ModItems.ITEMS.register("module_repair_injector", () ->
            new RepairInjectorItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---- T2〜T4 の兵装（§7.2） ----
    public static final RegistryObject<Item> LUXION_SABER = ModItems.ITEMS.register("weapon_luxion_saber", () ->
            new com.automatavalkyries.weapon.LuxionSaberItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HEAVY_PARTICLE_CANNON = ModItems.ITEMS.register("weapon_heavy_particle_cannon", () ->
            new com.automatavalkyries.weapon.HeavyParticleCannonItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TIME_ELIMINATOR = ModItems.ITEMS.register("weapon_time_eliminator", () ->
            new com.automatavalkyries.weapon.TimeEliminatorItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> AEGIS_BARRIER = ModItems.ITEMS.register("module_aegis_barrier", () ->
            new com.automatavalkyries.weapon.AegisBarrierItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> QUANTUM_BLADE = ModItems.ITEMS.register("weapon_quantum_blade", () ->
            new com.automatavalkyries.weapon.QuantumBladeItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> PUNISHMENT_RAILGUN = ModItems.ITEMS.register("weapon_punishment_railgun", () ->
            new com.automatavalkyries.weapon.PunishmentRailgunItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> AVALANCHE = ModItems.ITEMS.register("weapon_avalanche", () ->
            new com.automatavalkyries.weapon.AvalancheLauncherItem(new Item.Properties().rarity(Rarity.EPIC)));
    // ---- 追加の兵装 10 種（2026-10-03、ユーザーの依頼でこちらが考えた。T1〜T3 が 3 つずつ、T4 が 1 つ） ----
    public static final RegistryObject<Item> ARC_LANCER = ModItems.ITEMS.register("weapon_arc_lancer", () ->
            new com.automatavalkyries.weapon.ArcLancerItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SCATTER_BLASTER = ModItems.ITEMS.register("weapon_scatter_blaster", () ->
            new com.automatavalkyries.weapon.ScatterBlasterItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GRAVITY_HAMMER = ModItems.ITEMS.register("weapon_gravity_hammer", () ->
            new com.automatavalkyries.weapon.GravityHammerItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PLASMA_CHAKRAM = ModItems.ITEMS.register("weapon_plasma_chakram", () ->
            new com.automatavalkyries.weapon.PlasmaChakramItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> PRISM_BEAM_RIFLE = ModItems.ITEMS.register("weapon_prism_rifle", () ->
            new com.automatavalkyries.weapon.PrismBeamRifleItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> THUNDER_JAVELIN = ModItems.ITEMS.register("weapon_thunder_javelin", () ->
            new com.automatavalkyries.weapon.ThunderJavelinItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BLACK_LOTUS = ModItems.ITEMS.register("weapon_black_lotus", () ->
            new com.automatavalkyries.weapon.BlackLotusItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> CHRONO_REPEATER = ModItems.ITEMS.register("weapon_chrono_repeater", () ->
            new com.automatavalkyries.weapon.ChronoRepeaterItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> VOID_SCYTHE = ModItems.ITEMS.register("weapon_void_scythe", () ->
            new com.automatavalkyries.weapon.VoidScytheItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> GENESIS_HALO = ModItems.ITEMS.register("weapon_genesis_halo", () ->
            new com.automatavalkyries.weapon.GenesisHaloItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> CATASTROPHE_BUSTER = ModItems.ITEMS.register("weapon_catastrophe_buster", () ->
            new com.automatavalkyries.weapon.CatastropheBusterItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // ---- 追加の兵装 第 2 弾 10 種（2026-10-03、ユーザーの依頼でこちらが考えた。T1〜T3 が 3 つずつ、T4 が 1 つ） ----
    public static final RegistryObject<Item> HEAT_PILE = ModItems.ITEMS.register("weapon_heat_pile", () ->
            new com.automatavalkyries.weapon.HeatPileBunkerItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TWIN_BLASTER = ModItems.ITEMS.register("weapon_twin_blaster", () ->
            new com.automatavalkyries.weapon.TwinBlasterItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SHOCK_WHIP = ModItems.ITEMS.register("weapon_shock_whip", () ->
            new com.automatavalkyries.weapon.ShockWhipItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PLASMA_FLAMER = ModItems.ITEMS.register("weapon_plasma_flamer", () ->
            new com.automatavalkyries.weapon.PlasmaFlamerItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> MIRAGE_DAGGER = ModItems.ITEMS.register("weapon_mirage_dagger", () ->
            new com.automatavalkyries.weapon.MirageDaggerItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> VORTEX_CANNON = ModItems.ITEMS.register("weapon_vortex_cannon", () ->
            new com.automatavalkyries.weapon.VortexCannonItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> STARFALL_BOW = ModItems.ITEMS.register("weapon_starfall_bow", () ->
            new com.automatavalkyries.weapon.StarfallBowItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ECLIPSE_BLADE = ModItems.ITEMS.register("weapon_eclipse_blade", () ->
            new com.automatavalkyries.weapon.EclipseBladeItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SERAPH_STAFF = ModItems.ITEMS.register("weapon_seraph_staff", () ->
            new com.automatavalkyries.weapon.SeraphStaffItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> RAGNAROK_EDGE = ModItems.ITEMS.register("weapon_ragnarok_edge", () ->
            new com.automatavalkyries.weapon.RagnarokEdgeItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // ---- 追加の兵装 第 3 弾 17 種（2026-10-03、ユーザーの依頼「50 種類に。T4 を 1 つ、残りは特殊効果多めに各ティアへ」でこちらが考えた） ----
    public static final RegistryObject<Item> VENOM_STINGER = ModItems.ITEMS.register("weapon_venom_stinger", () ->
            new com.automatavalkyries.weapon.Batch3T1.VenomStinger(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FLASH_LAUNCHER = ModItems.ITEMS.register("weapon_flash_launcher", () ->
            new com.automatavalkyries.weapon.Batch3T1.FlashLauncher(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GALE_FAN = ModItems.ITEMS.register("weapon_gale_fan", () ->
            new com.automatavalkyries.weapon.Batch3T1.GaleFan(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FROST_KNUCKLE = ModItems.ITEMS.register("weapon_frost_knuckle", () ->
            new com.automatavalkyries.weapon.Batch3T1.FrostKnuckle(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CRIMSON_SICKLE = ModItems.ITEMS.register("weapon_crimson_sickle", () ->
            new com.automatavalkyries.weapon.Batch3T2.CrimsonSickle(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LEVITATION_ROD = ModItems.ITEMS.register("weapon_levitation_rod", () ->
            new com.automatavalkyries.weapon.Batch3T2.LevitationRod(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> RICOCHET_RIFLE = ModItems.ITEMS.register("weapon_ricochet_rifle", () ->
            new com.automatavalkyries.weapon.Batch3T2.RicochetRifle(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPECTRAL_CHAIN = ModItems.ITEMS.register("weapon_spectral_chain", () ->
            new com.automatavalkyries.weapon.Batch3T2.SpectralChain(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> STATIC_TRAP = ModItems.ITEMS.register("weapon_static_trap", () ->
            new com.automatavalkyries.weapon.Batch3T2.StaticTrap(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BERSERK_AXE = ModItems.ITEMS.register("weapon_berserk_axe", () ->
            new com.automatavalkyries.weapon.Batch3T2.BerserkAxe(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SONIC_CANNON = ModItems.ITEMS.register("weapon_sonic_cannon", () ->
            new com.automatavalkyries.weapon.Batch3T2.SonicCannon(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> HEX_GRIMOIRE = ModItems.ITEMS.register("weapon_hex_grimoire", () ->
            new com.automatavalkyries.weapon.Batch3T3.HexGrimoire(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> PHOENIX_WING = ModItems.ITEMS.register("weapon_phoenix_wing", () ->
            new com.automatavalkyries.weapon.Batch3T3.PhoenixWing(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> STORM_CALLER = ModItems.ITEMS.register("weapon_storm_caller", () ->
            new com.automatavalkyries.weapon.Batch3T3.StormCaller(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> REFLECTOR_BLADE = ModItems.ITEMS.register("weapon_reflector_blade", () ->
            new com.automatavalkyries.weapon.Batch3T3.ReflectorBlade(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> NOVA_LANCE = ModItems.ITEMS.register("weapon_nova_lance", () ->
            new com.automatavalkyries.weapon.Batch3T3.NovaLance(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> LUMINOUS_REQUIEM = ModItems.ITEMS.register("weapon_luminous_requiem", () ->
            new com.automatavalkyries.weapon.LuminousRequiemItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    /** カタストロフ・バスターの背の渦・ラグナロクの背の夕焼けの立体を描くためだけのアイテム（手に入らない） */
    public static final RegistryObject<Item> BUSTER_BACKDROP = ModItems.ITEMS.register("buster_backdrop", () ->
            new com.automatavalkyries.weapon.T4BackdropItem(new Item.Properties()));
    public static final RegistryObject<Item> RAGNAROK_BACKDROP = ModItems.ITEMS.register("ragnarok_backdrop", () ->
            new com.automatavalkyries.weapon.T4BackdropItem(new Item.Properties()));
    /** 極光轟雷の背の歯車を描くためだけのアイテム（手に入らない） */
    public static final RegistryObject<Item> RAILGUN_BACKDROP = ModItems.ITEMS.register("railgun_backdrop", () ->
            new com.automatavalkyries.weapon.RailgunBackdropItem(new Item.Properties()));
    /** ジェネシス・ヘイローの光輪モードのリングを描くためだけのアイテム（手に入らない） */
    public static final RegistryObject<Item> GENESIS_BACKDROP = ModItems.ITEMS.register("genesis_backdrop", () ->
            new com.automatavalkyries.weapon.GenesisBackdropItem(new Item.Properties()));
    /** 星葬冠の背の光輪と 6 振りの剣を描くためだけのアイテム（手に入らない） */
    public static final RegistryObject<Item> REQUIEM_BACKDROP = ModItems.ITEMS.register("requiem_backdrop", () ->
            new com.automatavalkyries.weapon.RequiemBackdropItem(new Item.Properties()));

    // ---- 採掘用の兵装（2026-10-03 ユーザー指定）: 各ティアにドリル（つるはし＋シャベルの上位）とチェーンソー（斧の上位） ----
    public static final RegistryObject<Item> DRILL_T1 = ModItems.ITEMS.register("tool_drill_t1", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.UNCOMMON), com.automatavalkyries.weapon.MiningToolItem.Kind.DRILL, 1));
    public static final RegistryObject<Item> CHAINSAW_T1 = ModItems.ITEMS.register("tool_chainsaw_t1", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.UNCOMMON), com.automatavalkyries.weapon.MiningToolItem.Kind.CHAINSAW, 1));
    public static final RegistryObject<Item> DRILL_T2 = ModItems.ITEMS.register("tool_drill_t2", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.RARE), com.automatavalkyries.weapon.MiningToolItem.Kind.DRILL, 2));
    public static final RegistryObject<Item> CHAINSAW_T2 = ModItems.ITEMS.register("tool_chainsaw_t2", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.RARE), com.automatavalkyries.weapon.MiningToolItem.Kind.CHAINSAW, 2));
    public static final RegistryObject<Item> DRILL_T3 = ModItems.ITEMS.register("tool_drill_t3", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), com.automatavalkyries.weapon.MiningToolItem.Kind.DRILL, 3));
    public static final RegistryObject<Item> CHAINSAW_T3 = ModItems.ITEMS.register("tool_chainsaw_t3", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), com.automatavalkyries.weapon.MiningToolItem.Kind.CHAINSAW, 3));
    public static final RegistryObject<Item> DRILL_T4 = ModItems.ITEMS.register("tool_drill_t4", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), com.automatavalkyries.weapon.MiningToolItem.Kind.DRILL, 4));
    public static final RegistryObject<Item> CHAINSAW_T4 = ModItems.ITEMS.register("tool_chainsaw_t4", () ->
            new com.automatavalkyries.weapon.MiningToolItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(), com.automatavalkyries.weapon.MiningToolItem.Kind.CHAINSAW, 4));

    // ---- 弾薬 ----
    public static final RegistryObject<Item> AMMO_AP_ROUND = ModItems.ITEMS.register("ammo_ap_round", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NANO_SOLUTION = ModItems.ITEMS.register("nano_solution", () -> new Item(new Item.Properties().stacksTo(16)));

    // ---- 弾・状態異常 ----
    public static final RegistryObject<EntityType<NecrosisNeedleEntity>> NECROSIS_NEEDLE = ModEntities.ENTITY_TYPES.register("necrosis_needle", () ->
            EntityType.Builder.<NecrosisNeedleEntity>of(NecrosisNeedleEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(2)
                    .build("necrosis_needle"));
    public static final RegistryObject<MobEffect> NECROSIS = MOB_EFFECTS.register("necrosis", Necrosis.Effect::new);
    public static final RegistryObject<MobEffect> CAUSAL_STOP = MOB_EFFECTS.register("causal_stop", com.automatavalkyries.weapon.TimeEliminatorItem.CausalStop::new);
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.QuantumSlashEntity>> QUANTUM_SLASH = ModEntities.ENTITY_TYPES.register("quantum_slash", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.QuantumSlashEntity>of(com.automatavalkyries.weapon.QuantumSlashEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("quantum_slash"));
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.PunishmentBeamEntity>> PUNISHMENT_BEAM = ModEntities.ENTITY_TYPES.register("punishment_beam", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.PunishmentBeamEntity>of(com.automatavalkyries.weapon.PunishmentBeamEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .clientTrackingRange(32) // 遠くの光の柱（ジェネシス・ヘイローの 256 ブロック先）も見えるように
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build("punishment_beam"));
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.FrostMissileEntity>> FROST_MISSILE = ModEntities.ENTITY_TYPES.register("frost_missile", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.FrostMissileEntity>of(com.automatavalkyries.weapon.FrostMissileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("frost_missile"));
    public static final RegistryObject<MobEffect> FROSTBITE = MOB_EFFECTS.register("frostbite", com.automatavalkyries.weapon.Frostbite.Effect::new);
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.ChakramEntity>> CHAKRAM = ModEntities.ENTITY_TYPES.register("plasma_chakram", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.ChakramEntity>of(com.automatavalkyries.weapon.ChakramEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.25F).clientTrackingRange(6).updateInterval(1).build("plasma_chakram"));
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.JavelinEntity>> JAVELIN = ModEntities.ENTITY_TYPES.register("thunder_javelin", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.JavelinEntity>of(com.automatavalkyries.weapon.JavelinEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).build("thunder_javelin"));
    public static final RegistryObject<EntityType<com.automatavalkyries.weapon.BlackHoleEntity>> BLACK_HOLE = ModEntities.ENTITY_TYPES.register("black_hole", () ->
            EntityType.Builder.<com.automatavalkyries.weapon.BlackHoleEntity>of(com.automatavalkyries.weapon.BlackHoleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("black_hole"));

    private ModWeapons() {}

    public static void init() {}

    private static RegistryObject<Item> core(LuminalCoreItem.Grade grade, Rarity rarity) {
        return ModItems.ITEMS.register(grade.id, () -> new LuminalCoreItem(new Item.Properties().rarity(rarity), grade));
    }
}
