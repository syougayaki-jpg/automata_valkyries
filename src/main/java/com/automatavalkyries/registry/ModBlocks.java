package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 鉱石・ストレージブロック（SPEC §7.7 の素材の鉱石と、9 個 ↔ 1 ブロックの貯蔵ブロック）。
 * 採掘に必要な道具の段階はタグ（minecraft:needs_stone_tool / needs_iron_tool）で決める。
 * 鉱石の生成は data/automata_valkyries/worldgen と data/automata_valkyries/forge/biome_modifier（SPEC §0.2-F、暫定）。
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AutomataValkyries.MODID);

    // ---- 鉱石（石・深層岩）。粗鉱を落とす（幸運で増える）。経験値は精錬のときに出る（バニラの鉄・銅・金と同じ） ----
    public static final RegistryObject<Block> ORE_ASTRITE = ore("ore_astrite", false);
    public static final RegistryObject<Block> DEEPSLATE_ORE_ASTRITE = ore("deepslate_ore_astrite", true);
    public static final RegistryObject<Block> ORE_PHONONIUM = ore("ore_phononium", false);
    public static final RegistryObject<Block> DEEPSLATE_ORE_PHONONIUM = ore("deepslate_ore_phononium", true);
    public static final RegistryObject<Block> ORE_CERULEAS = ore("ore_ceruleas", false);
    public static final RegistryObject<Block> DEEPSLATE_ORE_CERULEAS = ore("deepslate_ore_ceruleas", true);

    // ---- ルミナルクリスタルの鉱石。結晶をそのまま落とし、掘ったときに経験値が出る（バニラのダイヤモンドと同じ） ----
    public static final RegistryObject<Block> ORE_LUMINAL = register("ore_luminal", () -> new DropExperienceBlock(
            oreProperties(false).lightLevel(state -> 5), UniformInt.of(3, 7)));
    public static final RegistryObject<Block> DEEPSLATE_ORE_LUMINAL = register("deepslate_ore_luminal", () -> new DropExperienceBlock(
            oreProperties(true).lightLevel(state -> 5), UniformInt.of(3, 7)));

    // ---- ストレージブロック（粗鉱・インゴット 9 個 ↔ 1 ブロック） ----
    public static final RegistryObject<Block> BLOCK_RAW_ASTRITE = storage("block_raw_astrite", MapColor.COLOR_LIGHT_GRAY, SoundType.STONE);
    public static final RegistryObject<Block> BLOCK_RAW_PHONONIUM = storage("block_raw_phononium", MapColor.COLOR_ORANGE, SoundType.STONE);
    public static final RegistryObject<Block> BLOCK_RAW_CERULEAS = storage("block_raw_ceruleas", MapColor.COLOR_LIGHT_BLUE, SoundType.STONE);
    public static final RegistryObject<Block> BLOCK_ASTRITE = storage("block_astrite", MapColor.METAL, SoundType.METAL);
    public static final RegistryObject<Block> BLOCK_PHONONIUM = storage("block_phononium", MapColor.COLOR_ORANGE, SoundType.METAL);
    public static final RegistryObject<Block> BLOCK_CERULEAS = storage("block_ceruleas", MapColor.COLOR_LIGHT_BLUE, SoundType.METAL);

    // ---- 圧縮階層 Tier 1（追加拡張仕様書 §9）: ルミナルクリスタル 9 個 ↔ 1 ブロック。常時発光・高誘電 ----
    public static final RegistryObject<Block> BLOCK_LUMINAL_GEM = register("block_luminal_gem", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIAMOND).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.AMETHYST)
            .lightLevel(state -> 15)));

    // ---- 上位合金の貯蔵ブロック（9 個 ↔ 1 ブロック）。光り、表面から合金の粒子を出す。テクスチャは光の帯が走るアニメーション ----
    public static final RegistryObject<Block> BLOCK_ALLOY_LUMINAL_STERLING = effectBlock("block_alloy_luminal_sterling", MapColor.COLOR_LIGHT_BLUE, 12, ModEffects.LUMINAL_STERLING, 0.35F);
    public static final RegistryObject<Block> BLOCK_ALLOY_CHRONO_FORTRESS = effectBlock("block_alloy_chrono_fortress", MapColor.COLOR_PURPLE, 7, ModEffects.CHRONO_FORTRESS, 0.45F);
    public static final RegistryObject<Block> BLOCK_ALLOY_TACHYON_ACCEL = effectBlock("block_alloy_tachyon_accel", MapColor.COLOR_ORANGE, 10, ModEffects.TACHYON_ACCEL, 0.5F);
    public static final RegistryObject<Block> BLOCK_ALLOY_ECLIPSE_BIOMETAL = effectBlock("block_alloy_eclipse_biometal", MapColor.COLOR_GREEN, 6, ModEffects.ECLIPSE_BIOMETAL, 0.35F);
    public static final RegistryObject<Block> BLOCK_STELLACHRONODYME = effectBlock("block_stellachronodyme", MapColor.COLOR_BLUE, 15, ModEffects.STELLACHRONODYME, 0.8F);

    private static RegistryObject<Block> effectBlock(String name, MapColor color, int light,
                                                     java.util.List<net.minecraft.core.particles.ParticleOptions> particles, float chance) {
        return register(name, () -> new com.automatavalkyries.block.EffectStorageBlock(BlockBehaviour.Properties.of()
                .mapColor(color).requiresCorrectToolForDrops().strength(8.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
                .lightLevel(state -> light), particles, chance));
    }

    /** 工業 MOD 連携の金属（IndustrialMetals）用: 鉱石 */
    static RegistryObject<Block> oreBlock(String name, boolean deepslate) {
        return ore(name, deepslate);
    }

    /** 工業 MOD 連携の金属（IndustrialMetals）用: 貯蔵ブロック（金属ブロック・粗鉱ブロック） */
    static RegistryObject<Block> storageBlock(String name, boolean needsIron) {
        return storage(name, MapColor.METAL, name.startsWith("block_raw_") ? SoundType.STONE : SoundType.METAL);
    }

    private static RegistryObject<Block> ore(String name, boolean deepslate) {
        return register(name, () -> new Block(oreProperties(deepslate)));
    }

    private static BlockBehaviour.Properties oreProperties(boolean deepslate) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .requiresCorrectToolForDrops();
        return deepslate
                ? properties.mapColor(MapColor.DEEPSLATE).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE)
                : properties.mapColor(MapColor.STONE).strength(3.0F, 3.0F);
    }

    private static RegistryObject<Block> storage(String name, MapColor color, SoundType sound) {
        return register(name, () -> new Block(BlockBehaviour.Properties.of()
                .mapColor(color).requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(sound)));
    }

    /** ブロックと、同じ名前のブロックアイテム（クリエイティブタブは ModItems の登録順に並ぶ） */
    private static RegistryObject<Block> register(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
