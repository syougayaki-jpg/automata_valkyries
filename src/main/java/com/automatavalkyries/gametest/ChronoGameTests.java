package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoLink;
import com.automatavalkyries.chrono.ChronoMachineBlock;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.chrono.ChronoMachineItem;
import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.chrono.LuminalAdapterBlockEntity;
import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * クロノリンク・マシンの GameTest（docs/SPEC_CHRONOLINK_MACHINES.md）: ティアの昇格、空間リンクの搬出入、
 * 鉱石の倍加、原子相転移（必要ティア・多段）、無線給電、自動組み立て、ストレージ、レシピ。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ChronoGameTests {

    private static final String TEMPLATE = "empty";

    private static <T extends ChronoMachineBlockEntity> T place(GameTestHelper helper, BlockPos pos, ChronoMachineType type) {
        helper.setBlock(pos, ModChrono.MACHINES.get(type).get());
        @SuppressWarnings("unchecked") T machine = (T) helper.getBlockEntity(pos);
        return machine;
    }

    private static void powered(ChronoMachineBlockEntity machine) {
        machine.energy().set(machine.energy().getMaxEnergyStored());
    }

    private static ChronoLink link(GameTestHelper helper, BlockPos relative, ChronoLink.Mode mode) {
        return new ChronoLink(helper.absolutePos(relative), helper.getLevel().dimension(), Direction.UP, mode);
    }

    private static IItemHandler chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity be = helper.getBlockEntity(pos);
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static int count(IItemHandler handler, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        return n;
    }

    private static void setTier(GameTestHelper helper, BlockPos pos, int tier) {
        BlockState state = helper.getBlockState(pos);
        helper.setBlock(pos, state.setValue(ChronoMachineBlock.TIER, tier));
    }

    /** ティアの昇格: 次のティアのアップデートキットで右クリックすると、中身・リンク・電力を保ったまま上がる。飛び級はできない */
    @GameTest(template = TEMPLATE)
    public static void frameUpgradesTierInPlace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ChronoMachineBlockEntity smelter = place(helper, pos, ChronoMachineType.CHRONO_SMELTER);
        smelter.items().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 5));
        smelter.energy().set(12345);
        smelter.addLink(link(helper, new BlockPos(3, 2, 1), ChronoLink.Mode.IN));
        Player player = helper.makeMockPlayer();
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModChrono.FRAME_FUTURE.get()));
        helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(pos).getValue(ChronoMachineBlock.TIER) == 1, "a frame should no longer upgrade a machine");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModChrono.UPGRADE_KIT_BEYOND.get()));
        helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(pos).getValue(ChronoMachineBlock.TIER) == 1, "skipped a tier");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModChrono.UPGRADE_KIT_FUTURE.get()));
        helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(pos).getValue(ChronoMachineBlock.TIER) == 2, "kit did not upgrade the machine");
        helper.runAfterDelay(2, () -> {
            ChronoMachineBlockEntity after = (ChronoMachineBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(after == smelter, "block entity was replaced");
            helper.assertTrue(after.tier() == ChronoTier.FUTURE, "tier is " + after.tier());
            helper.assertTrue(after.energy().getMaxEnergyStored() == ChronoTier.FUTURE.capacity, "capacity not raised");
            helper.assertTrue(after.items().getStackInSlot(0).getCount() >= 4 && after.links().size() == 1, "contents or links were lost");
            helper.succeed();
        });
    }

    /** クロノ・スメルター: 搬入リンクのチェストから粗鉄を引き、×2（T1）で焼いて、搬出リンクのチェストへ送る */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void smelterDoublesOreThroughLinks(GameTestHelper helper) {
        IItemHandler in = chest(helper, new BlockPos(0, 2, 0));
        IItemHandler out = chest(helper, new BlockPos(4, 2, 0));
        in.insertItem(0, new ItemStack(Items.RAW_IRON, 1), false);
        ChronoMachineBlockEntity smelter = place(helper, new BlockPos(2, 2, 2), ChronoMachineType.CHRONO_SMELTER);
        powered(smelter);
        smelter.addLink(link(helper, new BlockPos(0, 2, 0), ChronoLink.Mode.IN));
        smelter.addLink(link(helper, new BlockPos(4, 2, 0), ChronoLink.Mode.OUT));
        helper.succeedWhen(() -> helper.assertTrue(count(out, Items.IRON_INGOT) == 2, "output chest has " + count(out, Items.IRON_INGOT) + " iron"));
    }

    /** 空間リンクの距離: T1 は 16 m・同じディメンション、T3 から異次元・無制限 */
    @GameTest(template = TEMPLATE)
    public static void linkRangeFollowsTier(GameTestHelper helper) {
        helper.assertTrue(ChronoTier.HIGH_END.reaches(true, 15 * 15) && !ChronoTier.HIGH_END.reaches(true, 17 * 17), "T1 range");
        helper.assertTrue(ChronoTier.FUTURE.reaches(true, 120 * 120) && !ChronoTier.FUTURE.reaches(false, 0), "T2 range");
        helper.assertTrue(ChronoTier.BEYOND.reaches(false, 0) && ChronoTier.BEYOND.reaches(true, 1e9), "T3 range");
        helper.assertTrue(ChronoTier.NEVERLAST.reachUnloaded && !ChronoTier.BEYOND.reachUnloaded, "T4 reaches unloaded chunks");
        helper.succeed();
    }

    /** 原子相転移: 鉄 2 → アストライト（T1）。ステラ・クロノダイムは T2 から（4 段） */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void adapterTransitionsRespectTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        LuminalAdapterBlockEntity adapter = place(helper, pos, ChronoMachineType.LUMINAL_ADAPTER);
        powered(adapter);
        Object[][] stella = {{ModItems.ALLOY_LUMINAL_STERLING.get(), 4}, {ModItems.ALLOY_CHRONO_FORTRESS.get(), 1}, {ModItems.ALLOY_TACHYON_ACCEL.get(), 1},
                {ModItems.ALLOY_ECLIPSE_BIOMETAL.get(), 1}, {ModItems.CIRCUIT_QUANTUM_TAC.get(), 1}, {ModItems.ORB_LUMINAL.get(), 1}};
        for (int i = 0; i < stella.length; i++) adapter.items().setStackInSlot(i, new ItemStack((net.minecraft.world.item.Item) stella[i][0], (int) stella[i][1]));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(adapter.items().getStackInSlot(6).isEmpty() && adapter.stage() == 0, "T1 adapter made Stellachronodyme");
            setTier(helper, pos, 2);
            ChronoMachineBlockEntity t2 = (ChronoMachineBlockEntity) helper.getBlockEntity(pos);
            helper.runAfterDelay(2, () -> powered(t2));
            helper.succeedWhen(() -> helper.assertTrue(t2.items().getStackInSlot(6).is(ModItems.INGOT_STELLACHRONODYME.get()),
                    "no Stellachronodyme (stage " + ((LuminalAdapterBlockEntity) t2).stage() + ")"));
        });
    }

    /** 原子相転移 T1: 鉄 2 → アストライト 1。並列（T1 2）までまとめて */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void adapterMakesAstrite(GameTestHelper helper) {
        LuminalAdapterBlockEntity adapter = place(helper, new BlockPos(1, 2, 1), ChronoMachineType.LUMINAL_ADAPTER);
        powered(adapter);
        adapter.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        helper.succeedWhen(() -> {
            ItemStack out = adapter.items().getStackInSlot(6);
            helper.assertTrue(out.is(ModItems.INGOT_ASTRITE.get()) && out.getCount() == 2, "adapter output " + out);
        });
    }

    /** ルミナル反応炉 → 隣のテザー・パイロン → 離れたクロノ・スメルターへ無線給電 */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void pylonPowersMachinesWirelessly(GameTestHelper helper) {
        ChronoMachineBlockEntity reactor = place(helper, new BlockPos(0, 2, 0), ChronoMachineType.LUMINAL_REACTOR);
        place(helper, new BlockPos(1, 2, 0), ChronoMachineType.TETHER_PYLON);
        ChronoMachineBlockEntity smelter = place(helper, new BlockPos(6, 2, 6), ChronoMachineType.CHRONO_SMELTER);
        reactor.items().setStackInSlot(0, new ItemStack(ModItems.CRYSTAL_LUMINAL.get(), 4));
        helper.succeedWhen(() -> helper.assertTrue(smelter.energy().getEnergyStored() > 1000,
                "smelter energy " + smelter.energy().getEnergyStored() + ", reactor " + reactor.energy().getEnergyStored()));
    }

    /** 給電ドック（T2）: 上に立った兵姫へ給電し、ナノマシン溶液で機能停止を修復する */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void chargingDockChargesAndRepairs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        com.automatavalkyries.chrono.ChargingDockBlockEntity dock = place(helper, pos, ChronoMachineType.CHARGING_DOCK);
        setTier(helper, pos, 2);
        dock = (com.automatavalkyries.chrono.ChargingDockBlockEntity) helper.getBlockEntity(pos);
        powered(dock);
        dock.items().setStackInSlot(com.automatavalkyries.chrono.ChargingDockBlockEntity.NANO_SLOT, new ItemStack(com.automatavalkyries.registry.ModWeapons.NANO_SOLUTION.get(), 2));
        com.automatavalkyries.entity.ValkyrieEntity v = helper.spawn(com.automatavalkyries.registry.ModEntities.VALKYRIE.get(), 2, 2, 2);
        v.setNoGravity(true);
        v.setEnergy(5_000);
        v.shutdown();
        helper.succeedWhen(() -> {
            helper.assertTrue(v.getEnergy() > 20_000, "the dock did not charge the valkyrie: " + v.getEnergy());
            helper.assertFalse(v.isShutdown(), "the dock did not repair the shut-down valkyrie");
            v.releaseAndDiscard();
        });
    }

    /** 互いにリンクした 2 台: 片方でリンクを外すと、相手からのリンクも外れる */
    @GameTest(template = TEMPLATE)
    public static void removingLinkRemovesReverseLink(GameTestHelper helper) {
        ChronoMachineBlockEntity a = place(helper, new BlockPos(0, 2, 0), ChronoMachineType.CHRONO_FABRICATOR);
        ChronoMachineBlockEntity b = place(helper, new BlockPos(3, 2, 0), ChronoMachineType.VOID_REPOSITORY);
        a.addLink(link(helper, new BlockPos(3, 2, 0), ChronoLink.Mode.OUT));
        b.addLink(link(helper, new BlockPos(0, 2, 0), ChronoLink.Mode.IN));
        a.removeLink(0);
        helper.assertTrue(a.links().isEmpty(), "the link was not removed");
        helper.assertTrue(b.links().isEmpty(), "the reverse link was left: " + b.links());
        helper.succeed();
    }

    /** クロノ・ファブリケータ: 設計図カード（棒）で、搬入リンクのチェストの板材から組み立てる */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fabricatorCraftsFromBlueprint(GameTestHelper helper) {
        IItemHandler in = chest(helper, new BlockPos(0, 2, 0));
        in.insertItem(0, new ItemStack(Items.OAK_PLANKS, 8), false);
        ChronoMachineBlockEntity fabricator = place(helper, new BlockPos(2, 2, 2), ChronoMachineType.CHRONO_FABRICATOR);
        powered(fabricator);
        fabricator.addLink(link(helper, new BlockPos(0, 2, 0), ChronoLink.Mode.IN));
        ItemStack card = new ItemStack(ModChrono.BLUEPRINT_CARD.get());
        // 棒のレシピは複数ある（竹からの物・他 MOD の物）。板材から作る vanilla の物を名前で指す
        var recipe = helper.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("minecraft:stick"));
        helper.assertTrue(recipe.isPresent(), "no minecraft:stick recipe");
        card.getOrCreateTag().putString("Recipe", recipe.get().getId().toString());
        fabricator.items().setStackInSlot(0, card);
        helper.succeedWhen(() -> {
            int sticks = 0;
            for (int s = 27; s < 36; s++) if (fabricator.items().getStackInSlot(s).is(Items.STICK)) sticks += fabricator.items().getStackInSlot(s).getCount();
            helper.assertTrue(sticks >= 4, "fabricator made " + sticks + " sticks");
        });
    }

    /** ヴォイド・リポジトリ: 1 種類に大量に入り（T2 65,536）、壊すと中身ごとアイテムになり、置き直すと戻る。T4 は爆発で壊れない */
    @GameTest(template = TEMPLATE)
    public static void repositoryStoresAndKeepsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        VoidRepositoryBlockEntity repo = place(helper, pos, ChronoMachineType.VOID_REPOSITORY);
        setTier(helper, pos, 2);
        for (int i = 0; i < 200; i++) repo.insert(new ItemStack(Items.COBBLESTONE, 64), false);
        helper.assertTrue(repo.entries().size() == 1 && repo.entries().get(0).count == 12800, "stored " + repo.entries().get(0).count);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), level, abs, repo);
        helper.assertTrue(drops.size() == 1 && ChronoMachineItem.tierOf(drops.get(0)) == ChronoTier.FUTURE, "drop " + drops);
        var tag = drops.get(0).getTagElement("BlockEntityTag");
        helper.assertTrue(tag != null && tag.getList("Contents", 10).size() == 1, "contents not kept in the item");
        setTier(helper, pos, 4);
        helper.assertTrue(helper.getBlockState(pos).getBlock().getExplosionResistance(helper.getBlockState(pos), level, abs, null) > 1_000_000F,
                "T4 repository is not blast-proof");
        helper.succeed();
    }

    /** レシピ: ティアフレーム（T1〜T3）・マシン 6 種・道具があり、上位合金の暫定クラフトは相転移に置き換わった */
    @GameTest(template = TEMPLATE)
    public static void chronoRecipesLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var crafting = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        for (net.minecraft.world.item.Item item : List.of(ModChrono.FRAME_HIGH_END.get(), ModChrono.FRAME_FUTURE.get(), ModChrono.FRAME_BEYOND.get(), ModChrono.FRAME_NEVERLAST.get(),
                ModChrono.TOOL_CHRONO_TUNER.get(), ModChrono.BLUEPRINT_CARD.get(), ModChrono.LUMINAL_REACTOR.get().asItem(), ModChrono.CHRONO_SMELTER.get().asItem(),
                ModChrono.LUMINAL_ADAPTER.get().asItem(), ModChrono.CHRONO_FABRICATOR.get().asItem(), ModChrono.TETHER_PYLON.get().asItem(),
                ModChrono.VOID_REPOSITORY.get().asItem())) {
            helper.assertTrue(crafting.stream().anyMatch(r -> r.getResultItem(level.registryAccess()).is(item)), "no crafting recipe for " + item);
        }
        for (net.minecraft.world.item.Item item : List.of(ModItems.ALLOY_LUMINAL_STERLING.get(), ModItems.CIRCUIT_QUANTUM_TAC.get(), ModItems.INGOT_STELLACHRONODYME.get())) {
            helper.assertFalse(crafting.stream().anyMatch(r -> r.getResultItem(level.registryAccess()).is(item) && r.getIngredients().size() > 1),
                    "provisional crafting still exists for " + item);
        }
        helper.assertTrue(level.getRecipeManager().getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.PHASE_TRANSITION.get()).size() >= 20, "phase transitions missing");
        helper.succeed();
    }
    /** ティアごとの中間素材: T1 のアダプターでルミナルレンズ、タキオン駆動核は T2 から */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void intermediatesFollowTier(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        LuminalAdapterBlockEntity adapter = place(helper, pos, ChronoMachineType.LUMINAL_ADAPTER);
        powered(adapter);
        adapter.items().setStackInSlot(0, new ItemStack(ModItems.ALLOY_TACHYON_ACCEL.get(), 2));
        adapter.items().setStackInSlot(1, new ItemStack(com.automatavalkyries.registry.ModCompression.COIL_CHRONO.get(), 2));
        adapter.items().setStackInSlot(2, new ItemStack(ModItems.CIRCUIT_QUANTUM_TAC.get(), 1));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(adapter.items().getStackInSlot(6).isEmpty(), "T1 adapter made a T2 intermediate");
            for (int i = 0; i < 3; i++) adapter.items().setStackInSlot(i, ItemStack.EMPTY);
            adapter.items().setStackInSlot(0, new ItemStack(ModItems.CRYSTAL_LUMINAL.get(), 4));
            adapter.items().setStackInSlot(1, new ItemStack(Items.GLASS, 1));
            adapter.items().setStackInSlot(2, new ItemStack(ModItems.INGOT_CERULEAS.get(), 1));
            helper.succeedWhen(() -> helper.assertTrue(adapter.items().getStackInSlot(6).is(com.automatavalkyries.registry.ModCompression.LENS_LUMINAL.get()),
                    "no luminal lens: " + adapter.items().getStackInSlot(6)));
        });
    }

    /** T4 の反応炉に創世ルミナルコアを置くと、燃料なしで毎 Tick 50,000 FE（毎秒 1,000,000 FE） */
    @GameTest(template = TEMPLATE)
    public static void genesisCorePowersNeverlastReactor(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ChronoMachineBlockEntity reactor = place(helper, pos, ChronoMachineType.LUMINAL_REACTOR);
        setTier(helper, pos, 4);
        helper.runAfterDelay(1, () -> {
            ChronoMachineBlockEntity t4 = (ChronoMachineBlockEntity) helper.getBlockEntity(pos);
            t4.items().setStackInSlot(1, new ItemStack(com.automatavalkyries.registry.ModCompression.CORE_GENESIS_LUMINAL.get()));
            int before = t4.energy().getEnergyStored();
            helper.runAfterDelay(10, () -> {
                int gained = t4.energy().getEnergyStored() - before;
                helper.assertTrue(gained >= 50_000 * 9, "genesis core made only " + gained + " FE in 10 ticks");
                helper.assertTrue(t4.items().getStackInSlot(1).getCount() == 1, "genesis core was consumed");
                helper.succeed();
            });
        });
    }

    /** アップグレードパーツの設定: 切るとレシピの条件が偽になる（既定はすべて作れる） */
    @GameTest(template = TEMPLATE)
    public static void upgradeRecipesFollowConfig(GameTestHelper helper) {
        var condition = new com.automatavalkyries.config.MachineConfig.Condition("upgrades.upgrade_kit_future");
        helper.assertTrue(condition.test(null), "upgrade_kit_future should be craftable by default");
        // 保存された設定は書き換えない（書き換えると自動保存と競り合って false のまま残ることがあった）
        com.automatavalkyries.config.MachineConfig.TEST_OVERRIDES.put("upgrades.upgrade_kit_future", false);
        try {
            helper.assertFalse(condition.test(null), "the config switch does not turn the recipe off");
        } finally {
            com.automatavalkyries.config.MachineConfig.TEST_OVERRIDES.remove("upgrades.upgrade_kit_future");
        }
        helper.assertTrue(com.automatavalkyries.config.MachineConfig.FLAGS.size() == 3, "one switch per upgrade kit");
        helper.succeed();
    }
    /** 未来技術の上位回路・パーツ: 必要なティアがそろって上がっていく（T1 フォトニック → T3 時空格子 → T4 因果演算）、ティアフレームの材料になる */
    @GameTest(template = TEMPLATE)
    public static void futureCircuitsClimbTiers(GameTestHelper helper) {
        var level = helper.getLevel();
        var transitions = level.getRecipeManager().getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.PHASE_TRANSITION.get());
        Object[][] expected = {{com.automatavalkyries.registry.ModFutureTech.CIRCUIT_PHOTONIC.get(), 1}, {com.automatavalkyries.registry.ModFutureTech.EMITTER_SPATIAL.get(), 1},
                {com.automatavalkyries.registry.ModFutureTech.PROCESSOR_SYNAPSE.get(), 2}, {com.automatavalkyries.registry.ModFutureTech.CIRCUIT_CHRONO_LATTICE.get(), 3},
                {com.automatavalkyries.registry.ModFutureTech.GATE_WORMHOLE.get(), 3}, {com.automatavalkyries.registry.ModFutureTech.CIRCUIT_CAUSAL.get(), 4},
                {com.automatavalkyries.registry.ModFutureTech.ENGINE_PERPETUAL.get(), 4}};
        for (Object[] e : expected) {
            var item = (net.minecraft.world.item.Item) e[0];
            var recipe = transitions.stream().filter(r -> r.getResultItem(level.registryAccess()).is(item)).findFirst();
            helper.assertTrue(recipe.isPresent(), "no phase transition for " + item);
            helper.assertTrue(recipe.get().tier() == (int) e[1], item + " needs tier " + recipe.get().tier() + ", expected " + e[1]);
        }
        var crafting = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        Object[][] frames = {{ModChrono.FRAME_FUTURE.get(), com.automatavalkyries.registry.ModFutureTech.CIRCUIT_PHOTONIC.get()},
                {ModChrono.FRAME_BEYOND.get(), ModItems.CIRCUIT_QUANTUM_TAC.get()},
                {ModChrono.FRAME_NEVERLAST.get(), com.automatavalkyries.registry.ModFutureTech.CIRCUIT_CHRONO_LATTICE.get()}};
        for (Object[] f : frames) {
            var frame = (net.minecraft.world.item.Item) f[0];
            var circuit = new ItemStack((net.minecraft.world.item.Item) f[1]);
            boolean uses = crafting.stream().filter(r -> r.getResultItem(level.registryAccess()).is(frame))
                    .anyMatch(r -> r.getIngredients().stream().anyMatch(i -> i.test(circuit)));
            helper.assertTrue(uses, frame + " does not use " + circuit);
        }
        helper.succeed();
    }
    /** 各ティアのマシンは作業台で直接作れる（ティアつきのアイテム）。アップデートキットはそのティアのアダプターで作る */
    @GameTest(template = TEMPLATE)
    public static void everyTierHasMachineRecipesAndKits(GameTestHelper helper) {
        var level = helper.getLevel();
        var crafting = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        for (ChronoMachineType type : ChronoMachineType.values()) {
            var item = ModChrono.MACHINES.get(type).get().asItem();
            for (int tier = 1; tier <= 4; tier++) {
                ChronoTier want = ChronoTier.of(tier);
                boolean found = crafting.stream().map(r -> r.getResultItem(level.registryAccess()))
                        .anyMatch(out -> out.is(item) && ChronoMachineItem.tierOf(out) == want);
                helper.assertTrue(found, "no crafting recipe for " + type.id + " at " + want);
            }
        }
        var transitions = level.getRecipeManager().getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.PHASE_TRANSITION.get());
        Object[][] kits = {{ModChrono.UPGRADE_KIT_FUTURE.get(), 2}, {ModChrono.UPGRADE_KIT_BEYOND.get(), 3}, {ModChrono.UPGRADE_KIT_NEVERLAST.get(), 4}};
        for (Object[] k : kits) {
            var kit = (net.minecraft.world.item.Item) k[0];
            var recipe = transitions.stream().filter(r -> r.getResultItem(level.registryAccess()).is(kit)).findFirst();
            helper.assertTrue(recipe.isPresent() && recipe.get().tier() == (int) k[1], "kit " + kit + " is not made at tier " + k[1]);
        }
        helper.succeed();
    }

    /** 6 種のマシンすべてに T2〜T4 を作業台で直接作るレシピがあり、できたアイテムにそのティアが付き、材料がすべて登録済み */
    @GameTest(template = TEMPLATE)
    public static void everyMachineHasDirectRecipesForTiers2To4(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (ChronoMachineType type : ChronoMachineType.values()) {
            for (int level = 2; level <= 4; level++) {
                var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, type.id + "_t" + level);
                var recipe = recipes.byKey(id).orElse(null);
                if (recipe == null) {
                    problems.add(id + " missing");
                    continue;
                }
                ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
                if (!(result.getItem() instanceof ChronoMachineItem) || ChronoMachineItem.tierOf(result).level != level) {
                    problems.add(id + " makes " + result + " (tier " + ChronoMachineItem.tierOf(result).level + ")");
                }
                for (var ingredient : recipe.getIngredients()) {
                    if (!ingredient.isEmpty() && ingredient.getItems().length == 0) problems.add(id + " has an ingredient with no items");
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "Tier recipe problems: " + problems);
        helper.succeed();
    }

    /** 兵姫のオペレーター（2026-10-29）: 同調率 100 の素直な兵姫で、T1 は速さ +25%・電力 -10%、T4 は速さ +200%・電力 -40%。同調率 0 なら何も変わらない */
    @GameTest(template = TEMPLATE)
    public static void operatorBoostFollowsSyncAndTier(GameTestHelper helper) {
        var v = helper.spawn(com.automatavalkyries.registry.ModEntities.VALKYRIE.get(), 1, 2, 1);
        var zero = com.automatavalkyries.chrono.MachineOperator.boost(v, ChronoTier.HIGH_END);
        helper.assertTrue(v.getSyncRate() > 0.0F || (zero.speed() == 1.0F && zero.energy() == 1.0F), "sync 0 still boosted: " + zero);
        v.addSync(100.0F, "test");
        var t1 = com.automatavalkyries.chrono.MachineOperator.boost(v, ChronoTier.HIGH_END);
        var t4 = com.automatavalkyries.chrono.MachineOperator.boost(v, ChronoTier.NEVERLAST);
        helper.assertTrue(Math.abs(t1.speed() - 1.25F) < 0.01F && Math.abs(t1.energy() - 0.90F) < 0.01F, "T1 boost is off: " + t1 + " body=" + v.getBody());
        helper.assertTrue(Math.abs(t4.speed() - 3.0F) < 0.01F && Math.abs(t4.energy() - 0.60F) < 0.01F, "T4 boost is off: " + t4 + " body=" + v.getBody());
        v.releaseAndDiscard();
        helper.succeed();
    }

    /** 時間加速器の設定（2026-10-29）: 既定のブラックリストのスポナー・加速器そのものは速めず、かまど・作物は速める */
    @GameTest(template = TEMPLATE)
    public static void acceleratorRulesSkipBlacklisted(GameTestHelper helper) {
        var allows = (java.util.function.Predicate<net.minecraft.world.level.block.Block>) b ->
                com.automatavalkyries.chrono.ChronoAcceleratorBlockEntity.allows(b.defaultBlockState());
        helper.assertTrue(!allows.test(net.minecraft.world.level.block.Blocks.SPAWNER), "the spawner is accelerated");
        helper.assertTrue(!allows.test(ModChrono.CHRONO_ACCELERATOR.get()), "an accelerator accelerates another accelerator");
        helper.assertTrue(allows.test(net.minecraft.world.level.block.Blocks.FURNACE), "the furnace is not accelerated");
        helper.assertTrue(allows.test(net.minecraft.world.level.block.Blocks.WHEAT), "wheat is not accelerated");
        helper.succeed();
    }

    /** 時間巻き戻し機（2026-10-29）: 傷んだ鉄のツルハシの耐久を戻して結果の枠へ出す。焼き石は丸石へ戻せ、鉄のインゴットは原石へ戻さない */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void rewinderRepairsAndReverses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        com.automatavalkyries.chrono.ChronoRewinderBlockEntity rewinder = place(helper, pos, ChronoMachineType.CHRONO_REWINDER);
        powered(rewinder);
        helper.assertTrue(rewinder.reverseOf(new ItemStack(net.minecraft.world.item.Items.STONE)).is(net.minecraft.world.item.Items.COBBLESTONE), "stone does not rewind to cobblestone");
        var oreTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "ores"));
        var rawTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "raw_materials"));
        ItemStack back = rewinder.reverseOf(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)); // 粉からも焼けるので、粉に戻るのはよい
        helper.assertTrue(back.isEmpty() || (!back.is(oreTag) && !back.is(rawTag)), "iron ingot rewinds into an ore or raw ore: " + back);
        com.automatavalkyries.config.MachineConfig.TEST_OVERRIDES.put(com.automatavalkyries.config.MachineConfig.REWIND_ORES_KEY, true);
        try {
            ItemStack ore = rewinder.reverseOf(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
            helper.assertTrue(!ore.isEmpty(), "with the setting on, iron ingot does not rewind at all");
        } finally {
            com.automatavalkyries.config.MachineConfig.TEST_OVERRIDES.remove(com.automatavalkyries.config.MachineConfig.REWIND_ORES_KEY);
        }
        ItemStack pick = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        pick.setDamageValue(120);
        rewinder.items().setStackInSlot(0, pick);
        helper.succeedWhen(() -> {
            ItemStack out = rewinder.items().getStackInSlot(1);
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_PICKAXE) && !out.isDamaged(), "the pickaxe was not repaired: in=" + rewinder.items().getStackInSlot(0) + " out=" + out);
        });
    }
}
