package com.automatavalkyries.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部工業 MOD 連携の金属（追加拡張仕様書 §11）。Mekanism・Thermal・Create・Immersive Engineering 等と共通の金属を、
 * 同じ forge: タグで持つ（どの MOD のインゴットとも相互に代わりに使える）。
 * <ul>
 *   <li>基本金属（鉱石あり）: 粗鉱・粗鉱ブロック・鉱石（石・深層岩）＋共通の形</li>
 *   <li>合金（鉱石なし）: 共通の形だけ。暫定のクラフト（粉を混ぜて精錬）で作る</li>
 *   <li>共通の形: インゴット・塊（ナゲット）・ブロック・粉・板・歯車・棒</li>
 * </ul>
 * 鉱石の生成は設定（WorldgenConfig）で金属ごとに切れる（その金属を生成する工業 MOD と重ねないため）。
 */
public final class IndustrialMetals {

    /** 金属の定義。neeedsIron: 鉄のツルハシ以上でないと掘れない */
    public record Metal(String name, boolean hasOre, boolean needsIron) {}

    public static final List<Metal> BASE = List.of(
            new Metal("tin", true, false),
            new Metal("lead", true, false),
            new Metal("silver", true, true),
            new Metal("nickel", true, false),
            new Metal("zinc", true, false),
            new Metal("aluminum", true, false),
            new Metal("osmium", true, false),
            new Metal("uranium", true, true));

    public static final List<Metal> ALLOYS = List.of(
            new Metal("steel", false, false),
            new Metal("bronze", false, false),
            new Metal("brass", false, false),
            new Metal("electrum", false, false),
            new Metal("invar", false, false),
            new Metal("constantan", false, false));

    /** 合金を混ぜるのに使うバニラの金属の粉 */
    public static final List<String> VANILLA_DUSTS = List.of("iron", "copper", "gold");

    /** 形 → 金属 → 登録したもの */
    public static final Map<String, Map<String, RegistryObject<Item>>> ITEMS = new LinkedHashMap<>();
    public static final Map<String, Map<String, RegistryObject<Block>>> BLOCKS = new LinkedHashMap<>();

    private IndustrialMetals() {}

    static {
        for (Metal metal : all()) {
            String m = metal.name();
            item("ingot", m);
            item("nugget", m);
            item("dust", m);
            item("plate", m);
            item("gear", m);
            item("rod", m);
            block("block", m, ModBlocks.storageBlock("block_" + m, metal.needsIron()));
            if (metal.hasOre()) {
                item("raw", m);
                block("block_raw", m, ModBlocks.storageBlock("block_raw_" + m, metal.needsIron()));
                block("ore", m, ModBlocks.oreBlock("ore_" + m, false));
                block("deepslate_ore", m, ModBlocks.oreBlock("deepslate_ore_" + m, true));
            }
        }
        for (String m : VANILLA_DUSTS) item("dust", m);
    }

    public static List<Metal> all() {
        return java.util.stream.Stream.concat(BASE.stream(), ALLOYS.stream()).toList();
    }

    /** クラスを読み込んで登録を済ませる（メインクラスから ITEMS / BLOCKS の登録前に呼ぶ） */
    public static void init() {}

    private static void item(String form, String metal) {
        RegistryObject<Item> item = ModItems.ITEMS.register(form + "_" + metal, () -> new Item(new Item.Properties()));
        ITEMS.computeIfAbsent(form, k -> new LinkedHashMap<>()).put(metal, item);
    }

    private static void block(String form, String metal, RegistryObject<Block> block) {
        BLOCKS.computeIfAbsent(form, k -> new LinkedHashMap<>()).put(metal, block);
    }
}
