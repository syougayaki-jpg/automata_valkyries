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

    /**
     * 工業 MOD 連携の金属（追加拡張仕様書 §11）: 鉱石は粗鉱を落として精錬でインゴット、9 個 ↔ 1 個が往復でき、
     * 共通の形（塊・粉・板・歯車・棒）が forge: タグに入っている。鉱石の生成も登録されている
     */
    @GameTest(template = TEMPLATE)
    public static void industrialMetalsWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var recipes = level.getRecipeManager();
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (var metal : com.automatavalkyries.registry.IndustrialMetals.all()) {
            String m = metal.name();
            Item ingot = com.automatavalkyries.registry.IndustrialMetals.ITEMS.get("ingot").get(m).get();
            compressRoundTrip(helper, com.automatavalkyries.registry.IndustrialMetals.ITEMS.get("nugget").get(m).get(), ingot);
            compressRoundTrip(helper, ingot, com.automatavalkyries.registry.IndustrialMetals.BLOCKS.get("block").get(m).get());
            for (String form : List.of("ingots", "nuggets", "dusts", "plates", "gears", "rods")) {
                Item item = com.automatavalkyries.registry.IndustrialMetals.ITEMS.get(form.substring(0, form.length() - 1)).get(m).get();
                helper.assertTrue(new ItemStack(item).is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", form + "/" + m))),
                        item + " is missing from forge:" + form + "/" + m);
            }
            ItemStack fromDust = recipes.getRecipeFor(RecipeType.SMELTING,
                            new SimpleContainer(new ItemStack(com.automatavalkyries.registry.IndustrialMetals.ITEMS.get("dust").get(m).get())), level)
                    .map(r -> r.getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(sameKind(fromDust, ingot), m + " dust smelted into " + fromDust);
            if (!metal.hasOre()) continue;
            Block ore = com.automatavalkyries.registry.IndustrialMetals.BLOCKS.get("ore").get(m).get();
            Item raw = com.automatavalkyries.registry.IndustrialMetals.ITEMS.get("raw").get(m).get();
            List<ItemStack> drops = Block.getDrops(ore.defaultBlockState(), level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(!drops.isEmpty() && drops.stream().allMatch(s -> s.is(raw)), m + " ore dropped " + drops);
            ItemStack fromRaw = recipes.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(raw)), level)
                    .map(r -> r.getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(sameKind(fromRaw, ingot), "raw " + m + " smelted into " + fromRaw);
            var key = ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "ore_" + m));
            helper.assertTrue(placed.get(key) != null, "Placed feature ore_" + m + " is not registered");
            helper.assertTrue(com.automatavalkyries.config.WorldgenConfig.oreEnabled(m), m + " ore generation is off by default");
        }
        helper.succeed();
    }

    /** 上位合金（SPEC §7.7）の貯蔵ブロック: 9 個 ↔ 1 ブロック、光る。ステラ・クロノダイムは付与のきらめき付き */
    @GameTest(template = TEMPLATE)
    public static void alloyBlocksCompressAndGlow(GameTestHelper helper) {
        compressRoundTrip(helper, ModItems.ALLOY_LUMINAL_STERLING.get(), ModBlocks.BLOCK_ALLOY_LUMINAL_STERLING.get());
        compressRoundTrip(helper, ModItems.ALLOY_CHRONO_FORTRESS.get(), ModBlocks.BLOCK_ALLOY_CHRONO_FORTRESS.get());
        compressRoundTrip(helper, ModItems.ALLOY_TACHYON_ACCEL.get(), ModBlocks.BLOCK_ALLOY_TACHYON_ACCEL.get());
        compressRoundTrip(helper, ModItems.ALLOY_ECLIPSE_BIOMETAL.get(), ModBlocks.BLOCK_ALLOY_ECLIPSE_BIOMETAL.get());
        compressRoundTrip(helper, ModItems.INGOT_STELLACHRONODYME.get(), ModBlocks.BLOCK_STELLACHRONODYME.get());
        for (Block block : List.of(ModBlocks.BLOCK_ALLOY_LUMINAL_STERLING.get(), ModBlocks.BLOCK_ALLOY_CHRONO_FORTRESS.get(),
                ModBlocks.BLOCK_ALLOY_TACHYON_ACCEL.get(), ModBlocks.BLOCK_ALLOY_ECLIPSE_BIOMETAL.get(), ModBlocks.BLOCK_STELLACHRONODYME.get())) {
            helper.assertTrue(block.defaultBlockState().getLightEmission() > 0, block + " does not glow");
            helper.assertTrue(block.defaultBlockState().is(BlockTags.NEEDS_DIAMOND_TOOL), block + " does not need a diamond pickaxe");
        }
        helper.assertTrue(new ItemStack(ModItems.INGOT_STELLACHRONODYME.get()).hasFoil(), "Stellachronodyme has no glint");
        helper.succeed();
    }

    /** 合金の粉の調合（暫定）: 青銅 = 銅 3 + スズ 1 → 4、鋼鉄 = 鉄 1 + 石炭 2 → 1 */
    @GameTest(template = TEMPLATE)
    public static void alloyDustsMix(GameTestHelper helper) {
        var dusts = com.automatavalkyries.registry.IndustrialMetals.ITEMS.get("dust");
        assertCrafts(helper, List.of(dusts.get("copper").get(), dusts.get("copper").get(), dusts.get("copper").get(), dusts.get("tin").get()),
                dusts.get("bronze").get(), 4);
        assertCrafts(helper, List.of(dusts.get("iron").get(), Items.COAL, Items.COAL), dusts.get("steel").get(), 1);
        assertCrafts(helper, List.of(dusts.get("gold").get(), dusts.get("silver").get()), dusts.get("electrum").get(), 2);
        helper.succeed();
    }

    private static void assertCrafts(GameTestHelper helper, List<Item> inputs, Item result, int count) {
        ServerLevel level = helper.getLevel();
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        for (int i = 0; i < inputs.size(); i++) grid.setItem(i, new ItemStack(inputs.get(i)));
        ItemStack out = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.assemble(grid, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(out.is(result) && out.getCount() == count, inputs + " crafted into " + out);
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
        helper.assertTrue(sameKind(unpacked, unit) && unpacked.getCount() == 9, storage + " crafted into " + unpacked);
    }

    /**
     * 同じ物か、同じ金属の同じ形（forge:ingots/tin 等の同じタグ）の物か。同じ金属を持つ他 MOD（Mekanism 等）が
     * タグで受けるレシピを持っていると、作られるのがそちらの物になることがある（どちらでも互いに代わりに使える）
     */
    private static boolean sameKind(ItemStack actual, Item expected) {
        return actual.is(expected) || expected.builtInRegistryHolder().tags()
                .filter(t -> t.location().getNamespace().equals("forge") && t.location().getPath().contains("/"))
                .anyMatch(actual::is);
    }

    private static net.minecraft.tags.TagKey<Block> blockTag(String namespace, String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
