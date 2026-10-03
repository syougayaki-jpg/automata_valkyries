package com.automatavalkyries.weapon;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 兵装・道具・モジュールのティア（タクティカル・アーモリーで組み立てるティア。{@code recipes/armory/*.json} の "tier" と同じ）。
 * 想定の火力（2026-10-04 ユーザー指定）: T1 は 10〜50、T2 は 50〜100、T3 は 100〜500、T4 は 1,000 以上。
 */
public final class WeaponTiers {

    private static final Map<String, Integer> TIERS = new HashMap<>();

    static {
        put(1, "module_polar_deflector", "module_repair_injector", "tool_chainsaw_t1", "tool_drill_t1", "weapon_arc_lancer", "weapon_flash_launcher",
                "weapon_frost_knuckle", "weapon_gale_fan", "weapon_gravity_hammer", "weapon_heat_pile", "weapon_linear_rail_rifle", "weapon_necrosis_needle",
                "weapon_photon_edge", "weapon_scatter_blaster", "weapon_shock_whip", "weapon_twin_blaster", "weapon_venom_stinger");
        put(2, "tool_chainsaw_t2", "tool_drill_t2", "weapon_berserk_axe", "weapon_crimson_sickle", "weapon_heavy_particle_cannon", "weapon_levitation_rod",
                "weapon_luxion_saber", "weapon_mirage_dagger", "weapon_plasma_chakram", "weapon_plasma_flamer", "weapon_prism_rifle", "weapon_ricochet_rifle",
                "weapon_sonic_cannon", "weapon_spectral_chain", "weapon_static_trap", "weapon_thunder_javelin", "weapon_vortex_cannon");
        put(3, "module_aegis_barrier", "tool_chainsaw_t3", "tool_drill_t3", "weapon_avalanche", "weapon_black_lotus", "weapon_chrono_repeater",
                "weapon_eclipse_blade", "weapon_hex_grimoire", "weapon_nova_lance", "weapon_phoenix_wing", "weapon_quantum_blade", "weapon_reflector_blade",
                "weapon_seraph_staff", "weapon_starfall_bow", "weapon_storm_caller", "weapon_time_eliminator", "weapon_void_scythe");
        put(4, "tool_chainsaw_t4", "tool_drill_t4", "weapon_catastrophe_buster", "weapon_genesis_halo", "weapon_luminous_requiem",
                "weapon_punishment_railgun", "weapon_ragnarok_edge");
    }

    private WeaponTiers() {}

    private static void put(int tier, String... ids) {
        for (String id : List.of(ids)) TIERS.put(id, tier);
    }

    /** ティア（1〜4。表に無ければ 1） */
    public static int of(Item item) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? 1 : TIERS.getOrDefault(key.getPath(), 1);
    }

    /** 表に載っている全部（テスト用） */
    public static Map<String, Integer> all() {
        return Map.copyOf(TIERS);
    }
}
