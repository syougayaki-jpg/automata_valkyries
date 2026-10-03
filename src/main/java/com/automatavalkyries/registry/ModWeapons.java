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

    private ModWeapons() {}

    public static void init() {}

    private static RegistryObject<Item> core(LuminalCoreItem.Grade grade, Rarity rarity) {
        return ModItems.ITEMS.register(grade.id, () -> new LuminalCoreItem(new Item.Properties().rarity(rarity), grade));
    }
}
