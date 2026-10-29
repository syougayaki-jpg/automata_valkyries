package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoMachineItem;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.item.BossDropItem;
import com.automatavalkyries.weapon.LuminalCoreItem;
import com.automatavalkyries.weapon.RequiemBackdropItem;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import com.automatavalkyries.weapon.WeaponTiers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * クリエイティブのタブ（2026-10-04 に 4 つに分けた）。2026-10-29 ユーザー指定「クリエイティブのアイテムタブも整理して」「分け方は四つで十分です」で、
 * タブは 4 つのまま、中を区切り（{@link Section}）ごとにまとめて並べ直した:
 * <ol>
 *   <li>素材・部品: 素材（金属ごとに 鉱石 → 原石 → インゴット → … → ブロック → オーブ・スター・コア）→ 部品（線・回路は段の順・部品・マシンフレーム・中間素材）→ 建材</li>
 *   <li>マシン: マシン・クロノリンク・マシン（T1〜T4）→ ティアフレーム → アップグレードキット → 調整の道具</li>
 *   <li>兵装: 兵装強化台 → ティア順の兵装・モジュール・道具 → ルミナルコア → 弾 → 防具・ポケット・観測の立方体 → 戦術兵姫（素体・外装フレーム・背部ユニット・ロジックチップ・兵姫に使う道具）</li>
 *   <li>ボス素材・特別: 呼び出しの道具と素材 → ボス素材 → 試練 → スポーンエッグ（ボス → 雑魚）→ クリエイティブ用（上限にする道具・お守り・検証用の道具）</li>
 * </ol>
 * 描くためだけのアイテムは並べない。並びは区切りの順 → 区切りの中の順位 → 登録順
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AutomataValkyries.MODID);

    public enum Group { MATERIALS, MACHINES, WEAPONS, SPECIAL, HIDDEN }

    /** タブの中の区切り（この順に並ぶ） */
    public enum Section {
        MATERIALS(Group.MATERIALS), COMPONENTS(Group.MATERIALS), BUILDING(Group.MATERIALS), MACHINES(Group.MACHINES),
        WEAPONS(Group.WEAPONS), VALKYRIES(Group.WEAPONS), BOSSES(Group.SPECIAL), CREATIVE(Group.SPECIAL), HIDDEN(Group.HIDDEN);

        public final Group group;

        Section(Group group) {
            this.group = group;
        }
    }

    static String path(Item item) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.getPath();
    }

    /** クリエイティブ用（上限にする・お守り・検証用） */
    private static final Set<String> CREATIVE = Set.of("custom_maximizer", "cube_maximizer", "overload_igniter", "sync_maximizer", "tac_override",
            "ward_charm", "invincible_charm", "privilege_aegis", "eviction_notice", "retreat_order", "valkyrie_dummy_spawn_egg", "protect_dummy_zombie_spawn_egg", "akashic_probe_spawn_egg");
    /** 兵姫に使う道具 */
    private static final Set<String> VALKYRIE_TOOLS = Set.of("tactical_remote", "motion_remote", "rescue_communicator", "valkyrie_stretcher", "emergency_recall",
            "valkyrie_radar", "tool_tac_pointer", "luminal_candy", "cooling_ice", "valkyrie_spawn_egg", "core_proto");
    /** ボスのスポーンエッグ（雑魚より前に並べる） */
    private static final List<String> BOSS_EGGS = List.of("elenor", "farthest_elenor", "luxion", "luxion_ex", "galliafort", "galliafort_ex", "memento_mori",
            "memento_mori_ex", "akashic_seer", "sky_boss_skeleton", "foundry_master", "outpost_watcher", "bastion_keeper", "time_ruler", "lost_catastrophe");
    /** 部品のうち、置く順を決めておく物（回路は段の順） */
    private static final List<String> COMPONENT_ORDER = List.of("wire_copper", "wire_gold", "circuit_basic", "circuit_advanced", "circuit_quantum_tac",
            "circuit_photonic", "circuit_chrono_lattice", "circuit_causal", "motor", "heating_coil", "composite_plate", "uranium_cell",
            "machine_frame_basic", "machine_frame_improved", "machine_frame_advanced", "machine_frame_heavy");

    public static Group group(Item item) {
        return section(item).group;
    }

    public static Section section(Item item) {
        String id = path(item);
        if (item instanceof RequiemBackdropItem) return Section.HIDDEN;
        if (id.equals("guide_book")) return Section.MATERIALS;                                // 手引きは素材・部品のタブの先頭
        if (CREATIVE.contains(id) || id.contains("kill_stick") || id.contains("terminus") || id.startsWith("debug_")
                || item instanceof com.automatavalkyries.item.BaseGeneratorItem || item instanceof com.automatavalkyries.item.BlinkItem) {
            return Section.CREATIVE;
        }
        if (VALKYRIE_TOOLS.contains(id) || id.startsWith("valkyrie_body_") || id.equals("backpack_steam_thruster")
                || item instanceof com.automatavalkyries.item.ZeroFrameItem || item instanceof com.automatavalkyries.item.ZeroRebootPatchItem
                || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem
                || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem) {
            return Section.VALKYRIES;
        }
        if (item instanceof BossDropItem || item instanceof SpawnEggItem || id.startsWith("signal_") || id.equals("overload_memory")
                || id.equals("photon_blade_shard") || id.equals("distortion_core_shard") || id.equals("black_death_cocoon")
                || id.equals("fate_thread") || id.equals("chrono_key") || id.equals("base_radar")
                || item instanceof com.automatavalkyries.item.TinyCourageItem || item instanceof com.automatavalkyries.item.TrialHourglassItem) {
            return Section.BOSSES;
        }
        if (item instanceof TacticalWeaponItem || (id.startsWith("weapon_") && !(item instanceof BlockItem)) || item instanceof com.automatavalkyries.item.FlightWingItem || item instanceof LuminalCoreItem
                || id.startsWith("ammo_") || id.equals("nano_solution") || id.equals("upgrade_station") || item instanceof com.automatavalkyries.item.armor.MetalArmorItem
                || item instanceof com.automatavalkyries.item.ItemPocketItem || item instanceof com.automatavalkyries.item.AkashicCubesItem) {
            return Section.WEAPONS;
        }
        if (item instanceof com.automatavalkyries.item.ChronoTunerItem || item instanceof ChronoMachineItem
                || id.startsWith("frame_high_end") || id.equals("frame_future") || id.equals("frame_beyond") || id.equals("frame_neverlast")
                || id.startsWith("upgrade_kit_") || id.equals("blueprint_card")
                || (item instanceof BlockItem block && block.getBlock() instanceof EntityBlock)) {
            return Section.MACHINES;
        }
        if (item instanceof BlockItem && materialFamily(id) < 0) return Section.BUILDING;
        if (materialFamily(id) >= 0) return Section.MATERIALS;
        return Section.COMPONENTS;
    }

    // ---- 素材の並び（金属ごと → 形ごと） ----

    /** 金属の並び（このMOD独自 → 3 系統 → 上位合金 → 連携金属 → 合金 → バニラの粉） */
    private static final List<String> FAMILIES = List.of("astrite", "phononium", "ceruleas", "luminal", "chronos", "nano",
            "luminal_sterling", "chrono_fortress", "tachyon_accel", "eclipse_biometal", "stellachronodyme",
            "tin", "lead", "silver", "nickel", "zinc", "aluminum", "osmium", "uranium",
            "steel", "bronze", "brass", "electrum", "invar", "constantan", "iron", "copper", "gold");
    /** 形の並び（頭に付く名前） */
    private static final List<String> FORMS = List.of("ore_", "deepslate_ore_", "raw_", "block_raw_", "drop_", "crystal_", "ingot_", "alloy_", "nugget_",
            "dust_", "plate_", "gear_", "rod_", "block_alloy_", "block_", "orb_", "star_", "core_");
    /** 3 系統などの名前のゆれ */
    private static final Map<String, String> ALIASES = Map.of("luminal_gem", "luminal", "chrono_metal", "chronos", "chrono_scrap", "chronos",
            "distorted_chrono_shard", "chronos", "nano_mesh", "nano", "nanomachine", "nano", "singularity_chronos", "chronos",
            "genesis_luminal", "luminal", "catastrophe_nano", "nano");

    private static String form(String id) {
        String best = "";
        for (String f : FORMS) if (id.startsWith(f) && f.length() > best.length()) best = f;
        return best;
    }

    /** 素材の金属の順位（素材でなければ -1） */
    static int materialFamily(String id) {
        String f = form(id);
        if (f.isEmpty()) return -1;
        String rest = id.substring(f.length());
        rest = ALIASES.getOrDefault(rest, rest);
        return FAMILIES.indexOf(rest);
    }

    static int materialOrder(Item item) {
        String id = path(item);
        return materialFamily(id) * 100 + FORMS.indexOf(form(id));
    }

    // ---- ほかのタブの並び ----

    /** 兵装の並び: 兵装強化台 → ティア順に（兵装 → モジュール → 道具）→ ルミナルコア → 弾・溶液 → 防具 → ポケット → 観測の立方体 */
    static int weaponOrder(Item item) {
        String id = path(item);
        if (id.equals("upgrade_station")) return 0;
        if (item instanceof TacticalWeaponItem) {
            int kind = id.startsWith("module_") ? 1 : id.startsWith("tool_") ? 2 : 0;
            return 100 + WeaponTiers.of(item) * 10 + kind;
        }
        if (id.startsWith("weapon_")) return 90; // 墜落ポッドの初期装備（スクラップ・ショットガン・モーターブレード）は T1 の前
        if (item instanceof com.automatavalkyries.item.FlightWingItem) return 132; // T3 の道具の並び
        if (item instanceof LuminalCoreItem) return 200;
        if (id.startsWith("ammo_") || id.equals("nano_solution")) return 300;
        if (item instanceof com.automatavalkyries.item.armor.MetalArmorItem) return 400;
        if (item instanceof com.automatavalkyries.item.ItemPocketItem) return 500;
        return 600;
    }

    static int componentOrder(Item item) {
        int i = COMPONENT_ORDER.indexOf(path(item));
        return i >= 0 ? i : 1000;
    }

    static int machineOrder(Item item) {
        String id = path(item);
        if (id.startsWith("frame_")) return 100;
        if (id.startsWith("upgrade_kit_")) return 200;
        if (item instanceof com.automatavalkyries.item.ChronoTunerItem || id.equals("blueprint_card")) return 300;
        return 0;
    }

    static int valkyrieOrder(Item item) {
        String id = path(item);
        if (id.equals("valkyrie_spawn_egg") || id.startsWith("valkyrie_body_")) return 0;
        if (id.equals("core_proto")) return 10;
        if (item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem) return 100;
        if (item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem || id.equals("backpack_steam_thruster")) return 200;
        if (item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem) return 300;
        if (item instanceof com.automatavalkyries.item.ZeroFrameItem || item instanceof com.automatavalkyries.item.ZeroRebootPatchItem) return 500;
        return 400;
    }

    static int bossOrder(Item item) {
        String id = path(item);
        if (id.startsWith("signal_")) return 0;
        if (id.equals("photon_blade_shard") || id.equals("distortion_core_shard") || id.equals("black_death_cocoon")) return 10;
        if (id.equals("fate_thread") || id.equals("chrono_key") || id.equals("base_radar")) return 20;
        if (item instanceof BossDropItem && !(item instanceof com.automatavalkyries.item.TinyCourageItem || item instanceof com.automatavalkyries.item.TrialHourglassItem)) return 100;
        if (item instanceof com.automatavalkyries.item.TinyCourageItem || item instanceof com.automatavalkyries.item.TrialHourglassItem || id.equals("overload_memory")) return 200;
        if (item instanceof SpawnEggItem) {
            int boss = BOSS_EGGS.indexOf(id.replace("_spawn_egg", ""));
            return boss >= 0 ? 300 + boss : 400;
        }
        return 500;
    }

    /** クリエイティブ用の並び: 上限にする道具 → 戦術コードの上書き → お守り → 基地の生成機・瞬間転移 → テスト用のモブの卵 → 退去願い・撤退の号令 → 検証用の棒 */
    static int creativeOrder(Item item) {
        String id = path(item);
        if (id.endsWith("_maximizer") || id.equals("overload_igniter")) return 0;
        if (id.equals("tac_override")) return 10;
        if (id.endsWith("_charm") || id.equals("privilege_aegis")) return 20;
        if (item instanceof com.automatavalkyries.item.BaseGeneratorItem || item instanceof com.automatavalkyries.item.BlinkItem) return 30;
        if (item instanceof SpawnEggItem) return 40;
        if (path(item).equals("eviction_notice") || path(item).equals("retreat_order")) return 45; // 退去願い・撤退の号令（2026-10-29 ユーザー指定でボス素材・特別へ）
        return 50;
    }

    static int order(Section section, Item item) {
        return switch (section) {
            case MATERIALS -> materialOrder(item);
            case COMPONENTS -> componentOrder(item);
            case MACHINES -> machineOrder(item);
            case WEAPONS -> weaponOrder(item);
            case VALKYRIES -> valkyrieOrder(item);
            case BOSSES -> bossOrder(item);
            case CREATIVE -> creativeOrder(item);
            default -> 0;
        };
    }

    static List<Item> items(Group group) {
        List<Item> list = new ArrayList<>();
        Map<Item, Integer> registered = new HashMap<>();
        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
            registered.put(entry.get(), registered.size());
            if (group(entry.get()) == group) list.add(entry.get());
        }
        list.sort(Comparator.<Item, Section>comparing(ModCreativeTabs::section).thenComparingInt(i -> order(section(i), i)).thenComparingInt(registered::get));
        return list;
    }

    /** 置く物（兵装・ルミナルコアは満充電、クロノリンク・マシンは T2〜T4 も） */
    static void accept(CreativeModeTab.Output output, Item item) {
        if (item instanceof TacticalWeaponItem weapon) output.accept(weapon.charged());
        else if (item instanceof com.automatavalkyries.item.FlightWingItem wing) output.accept(wing.charged());
        else if (item instanceof LuminalCoreItem core) output.accept(core.charged());
        else output.accept(item);
        if (item instanceof ChronoMachineItem) {
            for (ChronoTier tier : ChronoTier.values()) {
                if (tier.level >= 2) output.accept(ChronoMachineItem.withTier(new ItemStack(item), tier));
            }
        }
    }

    private static RegistryObject<CreativeModeTab> tab(String name, java.util.function.Supplier<ItemStack> icon, Group group, String after) {
        return CREATIVE_MODE_TABS.register(name, () -> {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.automata_valkyries." + name))
                    .icon(icon)
                    .displayItems((parameters, output) -> items(group).forEach(item -> accept(output, item)));
            if (after == null) builder.withTabsBefore(CreativeModeTabs.SPAWN_EGGS);
            else builder.withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, after));
            return builder.build();
        });
    }

    private static ItemStack icon(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, id));
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = tab("main", () -> ModItems.INGOT_ASTRITE.get().getDefaultInstance(), Group.MATERIALS, null);
    public static final RegistryObject<CreativeModeTab> MACHINES_TAB = tab("machines", () -> icon("weapon_workbench"), Group.MACHINES, "main");
    public static final RegistryObject<CreativeModeTab> WEAPONS_TAB = tab("weapons", () -> ModWeapons.LUMINOUS_REQUIEM.get().getDefaultInstance(), Group.WEAPONS, "machines");
    public static final RegistryObject<CreativeModeTab> SPECIAL_TAB = tab("special", () -> ModBossDrops.MEMORY_STELLA.get().getDefaultInstance(), Group.SPECIAL, "weapons");
}
