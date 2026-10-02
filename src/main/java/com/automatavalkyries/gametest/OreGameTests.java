package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModBlocks;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 鉱石・ストレージブロックの GameTest: 掘ったときの落とし物、採掘に要る道具、タグ、精錬・圧縮のレシピ、鉱石の生成の登録。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class OreGameTests {

    private static final String TEMPLATE = "empty";

    private record Ore(Block stone, Block deepslate, Item drop, Item smelted, boolean needsIron, String name) {}

    private static List<Ore> ores() {
        return List.of(
                new Ore(ModBlocks.ORE_ASTRITE.get(), ModBlocks.DEEPSLATE_ORE_ASTRITE.get(), ModItems.RAW_ASTRITE.get(), ModItems.INGOT_ASTRITE.get(), false, "astrite"),
                new Ore(ModBlocks.ORE_PHONONIUM.get(), ModBlocks.DEEPSLATE_ORE_PHONONIUM.get(), ModItems.RAW_PHONONIUM.get(), ModItems.INGOT_PHONONIUM.get(), false, "phononium"),
                new Ore(ModBlocks.ORE_CERULEAS.get(), ModBlocks.DEEPSLATE_ORE_CERULEAS.get(), ModItems.RAW_CERULEAS.get(), ModItems.INGOT_CERULEAS.get(), true, "ceruleas"),
                new Ore(ModBlocks.ORE_LUMINAL.get(), ModBlocks.DEEPSLATE_ORE_LUMINAL.get(), ModItems.CRYSTAL_LUMINAL.get(), ModItems.CRYSTAL_LUMINAL.get(), true, "luminal"));
    }

    /** ツルハシで掘ると粗鉱（ルミナルは結晶）、シルクタッチなら鉱石そのもの。必要な道具の段階もタグどおり */
    @GameTest(template = TEMPLATE)
    public static void oresDropTheirMaterialAndNeedTheRightTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        for (Ore ore : ores()) {
            for (Block block : List.of(ore.stone(), ore.deepslate())) {
                var state = block.defaultBlockState();
                String id = ForgeRegistries.BLOCKS.getKey(block).toString();
                List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, pickaxe);
                helper.assertTrue(!drops.isEmpty() && drops.stream().allMatch(s -> s.is(ore.drop())), id + " dropped " + drops);
                List<ItemStack> silkDrops = Block.getDrops(state, level, pos, null, null, silk);
                helper.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(block.asItem()), id + " with silk touch dropped " + silkDrops);
                helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), id + " is not mineable with a pickaxe");
                helper.assertTrue(state.is(ore.needsIron() ? BlockTags.NEEDS_IRON_TOOL : BlockTags.NEEDS_STONE_TOOL), id + " has the wrong tool tier");
                helper.assertTrue(state.requiresCorrectToolForDrops(), id + " drops without the right tool");
                helper.assertTrue(state.is(blockTag("forge", "ores/" + ore.name())), id + " is missing from forge:ores/" + ore.name());
            }
        }
        helper.succeed();
    }

    /** 鉱石・粗鉱を精錬するとインゴット（ルミナルは結晶）、9 個 ↔ 1 ブロックの圧縮が往復できる */
    @GameTest(template = TEMPLATE)
    public static void oresSmeltAndMaterialsCompress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var recipes = level.getRecipeManager();
        for (Ore ore : ores()) {
            ItemStack smelted = recipes.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(ore.stone())), level)
                    .map(r -> r.getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(smelted.is(ore.smelted()), ore.name() + " ore smelted into " + smelted);
        }
        compressRoundTrip(helper, ModItems.INGOT_ASTRITE.get(), ModBlocks.BLOCK_ASTRITE.get());
        compressRoundTrip(helper, ModItems.INGOT_PHONONIUM.get(), ModBlocks.BLOCK_PHONONIUM.get());
        compressRoundTrip(helper, ModItems.INGOT_CERULEAS.get(), ModBlocks.BLOCK_CERULEAS.get());
        compressRoundTrip(helper, ModItems.RAW_ASTRITE.get(), ModBlocks.BLOCK_RAW_ASTRITE.get());
        compressRoundTrip(helper, ModItems.RAW_PHONONIUM.get(), ModBlocks.BLOCK_RAW_PHONONIUM.get());
        compressRoundTrip(helper, ModItems.RAW_CERULEAS.get(), ModBlocks.BLOCK_RAW_CERULEAS.get());
        // 圧縮階層（追加拡張仕様書 §9）: クリスタル → ルミナルブロック（Tier 1）→ 光子励起のオーブ（Tier 2）
        compressRoundTrip(helper, ModItems.CRYSTAL_LUMINAL.get(), ModBlocks.BLOCK_LUMINAL_GEM.get());
        compressRoundTrip(helper, ModBlocks.BLOCK_LUMINAL_GEM.get().asItem(), ModItems.ORB_LUMINAL.get());
        helper.assertTrue(ModBlocks.BLOCK_LUMINAL_GEM.get().defaultBlockState().getLightEmission() == 15,
                "The luminal block does not glow at light level 15");
        helper.succeed();
    }

    /** ストレージブロックは自分を落とし、forge:storage_blocks に入っている */
    @GameTest(template = TEMPLATE)
    public static void storageBlocksDropThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (Block block : List.of(ModBlocks.BLOCK_ASTRITE.get(), ModBlocks.BLOCK_PHONONIUM.get(), ModBlocks.BLOCK_CERULEAS.get(),
                ModBlocks.BLOCK_RAW_ASTRITE.get(), ModBlocks.BLOCK_RAW_PHONONIUM.get(), ModBlocks.BLOCK_RAW_CERULEAS.get())) {
            var state = block.defaultBlockState();
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), block + " dropped " + drops);
            helper.assertTrue(state.is(blockTag("forge", "storage_blocks")), block + " is missing from forge:storage_blocks");
        }
        helper.assertTrue(new ItemStack(ModItems.CRYSTAL_LUMINAL.get()).is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "gems/luminal"))),
                "crystal_luminal is missing from forge:gems/luminal");
        helper.succeed();
    }

    /** 4 種の鉱石の生成（配置）が読み込まれている */
    @GameTest(template = TEMPLATE)
    public static void oreFeaturesAreRegistered(GameTestHelper helper) {
        var placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String name : List.of("ore_astrite", "ore_phononium", "ore_ceruleas", "ore_luminal")) {
            var key = ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name));
            PlacedFeature feature = placed.get(key);
            helper.assertTrue(feature != null, "Placed feature " + name + " is not registered");
        }
        helper.succeed();
    }

    private static void compressRoundTrip(GameTestHelper helper, Item unit, net.minecraft.world.level.ItemLike storage) {
        ServerLevel level = helper.getLevel();
        var recipes = level.getRecipeManager();
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(unit));
        ItemStack packed = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(packed.is(storage.asItem()) && packed.getCount() == 1, "9 x " + unit + " crafted into " + packed);

        for (int i = 0; i < 9; i++) grid.setItem(i, ItemStack.EMPTY);
        grid.setItem(0, new ItemStack(storage));
        ItemStack unpacked = recipes.getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(unpacked.is(unit) && unpacked.getCount() == 9, storage + " crafted into " + unpacked);
    }

    private static net.minecraft.tags.TagKey<Block> blockTag(String namespace, String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
