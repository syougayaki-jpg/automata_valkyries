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
import java.util.List;

/**
 * クリエイティブのタブ（2026-10-04 ユーザー指定で整理）: 素材・部品／マシン／兵装（ティア順。兵装 → モジュール → 道具、続いてルミナルコア・弾・装備・強化）／
 * ボス素材・特別（ボス素材・オーバーロードメモリ・呼び出しの卵・検証用の道具）。描くためだけのアイテムは並べない
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AutomataValkyries.MODID);

    public enum Group { MATERIALS, MACHINES, WEAPONS, SPECIAL, HIDDEN }

    static String path(Item item) {
        var key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.getPath();
    }

    public static Group group(Item item) {
        String id = path(item);
        if (item instanceof RequiemBackdropItem) return Group.HIDDEN;
        if (item instanceof BossDropItem || item instanceof SpawnEggItem || id.equals("overload_memory") || id.equals("custom_maximizer") || id.equals("overload_igniter") || id.equals("ward_charm") || id.equals("invincible_charm") || id.equals("rescue_communicator") || id.equals("tactical_remote") || id.equals("luminal_candy") || id.equals("cooling_ice") || id.equals("valkyrie_charger") || id.equals("eviction_notice") || id.equals("privilege_aegis") || id.contains("kill_stick") || id.contains("terminus")) {
            return Group.SPECIAL;
        }
        if (item instanceof TacticalWeaponItem || item instanceof LuminalCoreItem || id.startsWith("ammo_") || id.equals("nano_solution")
                || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem
                || item instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem
                || id.equals("backpack_steam_thruster") || id.equals("upgrade_station")) {
            return Group.WEAPONS;
        }
        if (item instanceof ChronoMachineItem || (item instanceof BlockItem block && block.getBlock() instanceof EntityBlock)) return Group.MACHINES;
        return Group.MATERIALS;
    }

    static List<Item> items(Group group) {
        List<Item> list = new ArrayList<>();
        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) if (group(entry.get()) == group) list.add(entry.get());
        return list;
    }

    /** 置く物（兵装・ルミナルコアは満充電、クロノリンク・マシンは T2〜T4 も） */
    static void accept(CreativeModeTab.Output output, Item item) {
        if (item instanceof TacticalWeaponItem weapon) output.accept(weapon.charged());
        else if (item instanceof LuminalCoreItem core) output.accept(core.charged());
        else output.accept(item);
        if (item instanceof ChronoMachineItem) {
            for (ChronoTier tier : ChronoTier.values()) {
                if (tier.level >= 2) output.accept(ChronoMachineItem.withTier(new ItemStack(item), tier));
            }
        }
    }

    /** 兵装の並び: 兵装強化台 → ティア順に（兵装 → モジュール → 道具）→ ルミナルコア → 弾・溶液 → 装備 */
    static int weaponOrder(Item item) {
        String id = path(item);
        if (id.equals("upgrade_station")) return 0;
        if (item instanceof TacticalWeaponItem) {
            int kind = id.startsWith("module_") ? 1 : id.startsWith("tool_") ? 2 : 0;
            return 100 + WeaponTiers.of(item) * 10 + kind;
        }
        if (item instanceof LuminalCoreItem) return 200;
        if (id.startsWith("ammo_") || id.equals("nano_solution")) return 300;
        return 400;
    }

    private static RegistryObject<CreativeModeTab> tab(String name, java.util.function.Supplier<ItemStack> icon, Group group, String after) {
        return CREATIVE_MODE_TABS.register(name, () -> {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.automata_valkyries." + name))
                    .icon(icon)
                    .displayItems((parameters, output) -> {
                        List<Item> list = items(group);
                        if (group == Group.WEAPONS) list.sort(Comparator.comparingInt(ModCreativeTabs::weaponOrder)); // 同じ順位の中は登録順のまま
                        list.forEach(item -> accept(output, item));
                    });
            if (after == null) builder.withTabsBefore(CreativeModeTabs.SPAWN_EGGS);
            else builder.withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, after));
            return builder.build();
        });
    }

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = tab("main", () -> ModItems.INGOT_ASTRITE.get().getDefaultInstance(), Group.MATERIALS, null);
    public static final RegistryObject<CreativeModeTab> MACHINES_TAB = tab("machines", () -> ForgeRegistries.ITEMS.getValue(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "weapon_workbench")).getDefaultInstance(), Group.MACHINES, "main");
    public static final RegistryObject<CreativeModeTab> WEAPONS_TAB = tab("weapons", () -> ModWeapons.LUMINOUS_REQUIEM.get().getDefaultInstance(), Group.WEAPONS, "machines");
    public static final RegistryObject<CreativeModeTab> SPECIAL_TAB = tab("special", () -> ModBossDrops.MEMORY_STELLA.get().getDefaultInstance(), Group.SPECIAL, "weapons");
}
