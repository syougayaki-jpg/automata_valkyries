package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.machine.MachineBlockEntity;
import com.automatavalkyries.machine.MachineKind;
import com.automatavalkyries.registry.IndustrialMetals;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 基礎文明マシンの GameTest: 加工（粉砕・合金・プレス）、燃料発電機 → ケーブル → 電気炉の送電、部品とマシンのレシピ。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class MachineGameTests {

    private static final String TEMPLATE = "empty";

    private static Item metal(String form, String metal) {
        return IndustrialMetals.ITEMS.get(form).get(metal).get();
    }

    private static MachineBlockEntity place(GameTestHelper helper, BlockPos pos, MachineKind kind) {
        helper.setBlock(pos, ModMachines.MACHINES.get(kind).get());
        if (!(helper.getBlockEntity(pos) instanceof MachineBlockEntity machine)) throw new IllegalStateException(kind.id + " has no block entity");
        return machine;
    }

    private static void powered(MachineBlockEntity machine) {
        machine.energy().set(machine.energy().getMaxEnergyStored());
    }

    /** 粉砕機: 錫鉱石 1 個 → 錫の粉 2 個（電力を使う） */
    @GameTest(template = TEMPLATE)
    public static void crusherDoublesOre(GameTestHelper helper) {
        MachineBlockEntity crusher = place(helper, new BlockPos(1, 2, 1), MachineKind.CRUSHER);
        powered(crusher);
        int before = crusher.energy().getEnergyStored();
        crusher.items().setStackInSlot(0, new ItemStack(IndustrialMetals.BLOCKS.get("ore").get("tin").get()));
        helper.succeedWhen(() -> {
            ItemStack out = crusher.items().getStackInSlot(crusher.kind().outputSlot());
            helper.assertTrue(out.is(metal("dust", "tin")) && out.getCount() == 2, "expected 2 tin dust, got " + out);
            helper.assertTrue(crusher.energy().getEnergyStored() < before, "crushing used no energy");
            helper.assertTrue(crusher.items().getStackInSlot(0).isEmpty(), "ore was not consumed");
        });
    }

    /** 合金炉: 銅 3 + 錫 1 → 青銅 4（スロットの順番は問わない） */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void alloySmelterMakesBronze(GameTestHelper helper) {
        MachineBlockEntity smelter = place(helper, new BlockPos(1, 2, 1), MachineKind.ALLOY_SMELTER);
        powered(smelter);
        smelter.items().setStackInSlot(0, new ItemStack(metal("ingot", "tin")));
        smelter.items().setStackInSlot(1, new ItemStack(Items.COPPER_INGOT, 3));
        helper.succeedWhen(() -> {
            ItemStack out = smelter.items().getStackInSlot(smelter.kind().outputSlot());
            helper.assertTrue(out.is(metal("ingot", "bronze")) && out.getCount() == 4, "expected 4 bronze, got " + out);
        });
    }

    /** 金属プレス: 鋼のインゴット → 鋼の板。電力が無ければ動かない */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void pressNeedsPower(GameTestHelper helper) {
        MachineBlockEntity press = place(helper, new BlockPos(1, 2, 1), MachineKind.PRESS);
        press.items().setStackInSlot(0, new ItemStack(metal("ingot", "steel")));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(press.items().getStackInSlot(press.kind().outputSlot()).isEmpty(), "press worked without power");
            powered(press);
            helper.succeedWhen(() -> helper.assertTrue(
                    press.items().getStackInSlot(press.kind().outputSlot()).is(metal("plate", "steel")), "no steel plate"));
        });
    }

    /** 燃料発電機（石炭）→ 電力ケーブル 3 本 → 電気炉で粗鉄を精錬 */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void generatorPowersFurnaceThroughCable(GameTestHelper helper) {
        MachineBlockEntity generator = place(helper, new BlockPos(0, 2, 1), MachineKind.GENERATOR);
        for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 2, 1), ModMachines.CABLE.get());
        MachineBlockEntity furnace = place(helper, new BlockPos(4, 2, 1), MachineKind.ELECTRIC_FURNACE);
        generator.items().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        furnace.items().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> {
            ItemStack out = furnace.items().getStackInSlot(furnace.kind().outputSlot());
            helper.assertTrue(out.is(Items.IRON_INGOT), "furnace output " + out + ", furnace energy " + furnace.energy().getEnergyStored());
        });
    }

    /** 部品とマシンのレシピがすべて読み込まれ、重め（フレームは回路を、マシンはフレームを要する） */
    @GameTest(template = TEMPLATE)
    public static void machineRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        Item[] needFrame = ModMachines.MACHINES.values().stream().map(b -> b.get().asItem()).toArray(Item[]::new);
        for (Item machine : needFrame) {
            CraftingRecipe recipe = recipes.stream().filter(r -> r.getResultItem(helper.getLevel().registryAccess()).is(machine)).findFirst()
                    .orElseThrow(() -> new AssertionError("no recipe for " + ForgeRegistries.ITEMS.getKey(machine)));
            boolean usesFrame = recipe.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(ModItems.MACHINE_FRAME_BASIC.get()))
                    || i.test(new ItemStack(ModItems.MACHINE_FRAME_ADVANCED.get())) || i.test(new ItemStack(ModItems.MACHINE_FRAME_IMPROVED.get()))
                    || i.test(new ItemStack(ModItems.MACHINE_FRAME_HEAVY.get())));
            helper.assertTrue(usesFrame, ForgeRegistries.ITEMS.getKey(machine) + " does not need a machine frame");
        }
        for (Item component : new Item[]{ModItems.WIRE_COPPER.get(), ModItems.WIRE_GOLD.get(), ModItems.CIRCUIT_BASIC.get(), ModItems.CIRCUIT_ADVANCED.get(),
                ModItems.MOTOR.get(), ModItems.HEATING_COIL.get(), ModItems.MACHINE_FRAME_BASIC.get(), ModItems.MACHINE_FRAME_ADVANCED.get(),
                ModMachines.CABLE.get().asItem()}) {
            helper.assertTrue(recipes.stream().anyMatch(r -> r.getResultItem(helper.getLevel().registryAccess()).is(component)),
                    "no recipe for " + ForgeRegistries.ITEMS.getKey(component));
        }
        // 加工レシピ: 粉砕・合金・プレスが読み込まれている
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(!manager.getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.CRUSHING.get()).isEmpty(), "no crushing recipes");
        helper.assertTrue(manager.getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.ALLOYING.get()).size() >= 6, "missing alloying recipes");
        helper.assertTrue(manager.getAllRecipesFor(com.automatavalkyries.registry.ModRecipes.PRESSING.get()).size() >= 14, "missing pressing recipes");
        helper.succeed();
    }
    /** 改良型粉砕機: 4 倍の速さ（100 Tick → 25 Tick）で、使う電力は 75% */
    @GameTest(template = TEMPLATE)
    public static void improvedCrusherIsFasterAndCheaper(GameTestHelper helper) {
        MachineBlockEntity crusher = place(helper, new BlockPos(1, 2, 1), MachineKind.IMPROVED_CRUSHER);
        powered(crusher);
        int before = crusher.energy().getEnergyStored();
        crusher.items().setStackInSlot(0, new ItemStack(IndustrialMetals.BLOCKS.get("ore").get("tin").get()));
        helper.runAfterDelay(35, () -> {
            ItemStack out = crusher.items().getStackInSlot(crusher.kind().outputSlot());
            helper.assertTrue(out.is(metal("dust", "tin")) && out.getCount() == 2, "improved crusher too slow: " + out);
            int used = before - crusher.energy().getEnergyStored();
            helper.assertTrue(used > 0 && used <= 1500, "expected at most 1500 FE (75% of 2000), used " + used);
            helper.succeed();
        });
    }

    /** 組み立て機: 6 種の材料からプロトコア */
    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void assemblerBuildsProtoCore(GameTestHelper helper) {
        MachineBlockEntity assembler = place(helper, new BlockPos(1, 2, 1), MachineKind.ASSEMBLER);
        powered(assembler);
        Item[] items = {ModItems.COMPOSITE_PLATE.get(), ModItems.WIRE_GOLD.get(), ModItems.ALLOY_LUMINAL_STERLING.get(),
                ModItems.CRYSTAL_LUMINAL.get(), ModItems.CIRCUIT_QUANTUM_TAC.get(), ModItems.URANIUM_CELL.get()};
        int[] counts = {2, 4, 2, 4, 1, 1};
        for (int i = 0; i < items.length; i++) assembler.items().setStackInSlot(i, new ItemStack(items[i], counts[i]));
        helper.succeedWhen(() -> {
            ItemStack out = assembler.items().getStackInSlot(assembler.kind().outputSlot());
            helper.assertTrue(out.is(ModItems.CORE_PROTO.get()), "no proto core yet: " + out);
            for (int i = 0; i < 6; i++) helper.assertTrue(assembler.items().getStackInSlot(i).isEmpty(), "slot " + i + " not consumed");
        });
    }

    /** 兵装ワークベンチ: 散弾銃 */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void workbenchBuildsShotgun(GameTestHelper helper) {
        MachineBlockEntity bench = place(helper, new BlockPos(1, 2, 1), MachineKind.WEAPON_WORKBENCH);
        powered(bench);
        bench.items().setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 2));
        bench.items().setStackInSlot(1, new ItemStack(Items.DISPENSER));
        bench.items().setStackInSlot(2, new ItemStack(metal("gear", "bronze")));
        bench.items().setStackInSlot(3, new ItemStack(metal("plate", "steel"), 3));
        bench.items().setStackInSlot(4, new ItemStack(metal("rod", "steel"), 2));
        helper.succeedWhen(() -> helper.assertTrue(bench.items().getStackInSlot(bench.kind().outputSlot()).is(ModItems.WEAPON_SCRAP_SHOTGUN.get()), "no shotgun"));
    }

    /** 蓄電器の充電スロット: モーターブレードを充電し、充電されると攻撃力が 9 になる */
    @GameTest(template = TEMPLATE)
    public static void batteryChargesMotorBlade(GameTestHelper helper) {
        MachineBlockEntity battery = place(helper, new BlockPos(1, 2, 1), MachineKind.BATTERY);
        powered(battery);
        ItemStack blade = new ItemStack(ModItems.WEAPON_MOTOR_BLADE.get());
        helper.assertTrue(attackDamage(blade) == com.automatavalkyries.item.MotorBladeItem.DULL_DAMAGE, "uncharged blade should be dull");
        battery.items().setStackInSlot(0, blade);
        helper.succeedWhen(() -> {
            ItemStack charged = battery.items().getStackInSlot(0);
            helper.assertTrue(com.automatavalkyries.item.ItemEnergy.get(charged) >= com.automatavalkyries.item.MotorBladeItem.CAPACITY, "blade not fully charged");
            helper.assertTrue(attackDamage(charged) == com.automatavalkyries.item.MotorBladeItem.CHARGED_DAMAGE, "charged blade damage " + attackDamage(charged));
        });
    }

    /** 攻撃力の合計（プレイヤーの素の 1 を含む） */
    private static double attackDamage(ItemStack stack) {
        return 1.0 + stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).stream().mapToDouble(m -> m.getAmount()).sum();
    }

    /** 改良型・重工業マシンのレシピ: 改良型フレームを要し、改良型フレームは連携素材 14 種をすべて（部品を通して）使う */
    @GameTest(template = TEMPLATE)
    public static void improvedFrameUsesAllIntegrationMetals(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        java.util.Set<String> metals = new java.util.HashSet<>();
        java.util.ArrayDeque<Item> todo = new java.util.ArrayDeque<>(java.util.List.of(ModItems.MACHINE_FRAME_IMPROVED.get()));
        java.util.Set<Item> seen = new java.util.HashSet<>();
        java.util.List<String> all = java.util.List.of("tin", "lead", "silver", "nickel", "zinc", "aluminum", "osmium", "uranium",
                "steel", "bronze", "brass", "electrum", "invar", "constantan");
        while (!todo.isEmpty()) {
            Item item = todo.poll();
            if (!seen.add(item)) continue;
            recipes.stream().filter(r -> r.getResultItem(level.registryAccess()).is(item)).findFirst().ifPresent(r -> {
                for (var ingredient : r.getIngredients()) for (ItemStack option : ingredient.getItems()) {
                    for (String m : all) for (String form : new String[]{"ingot", "plate", "gear", "rod", "block"}) {
                        var forms = IndustrialMetals.ITEMS.get(form);
                        if (forms != null && forms.containsKey(m) && option.is(forms.get(m).get())) metals.add(m);
                    }
                    if (ForgeRegistries.ITEMS.getKey(option.getItem()).getNamespace().equals(AutomataValkyries.MODID)) todo.add(option.getItem());
                }
            });
        }
        java.util.List<String> missing = all.stream().filter(m -> !metals.contains(m)).toList();
        helper.assertTrue(missing.isEmpty(), "improved frame does not use " + missing);
        for (MachineKind kind : MachineKind.values()) {
            if (!kind.id.startsWith("improved_") && kind != MachineKind.ASSEMBLER && kind != MachineKind.WEAPON_WORKBENCH) continue;
            Item machine = ModMachines.MACHINES.get(kind).get().asItem();
            helper.assertTrue(recipes.stream().anyMatch(r -> r.getResultItem(level.registryAccess()).is(machine)), "no recipe for " + kind.id);
        }
        helper.succeed();
    }
    /** 古い保存（蓄電器のスロットが 0 枠だった頃）を読み込んでも、今のスロットの数のまま動く（読み込んで Tick で落ちた） */
    @GameTest(template = TEMPLATE)
    public static void oldSaveKeepsCurrentSlotCount(GameTestHelper helper) {
        MachineBlockEntity battery = place(helper, new BlockPos(1, 2, 1), MachineKind.BATTERY);
        net.minecraft.nbt.CompoundTag tag = battery.saveWithoutMetadata();
        tag.put("items", new net.minecraftforge.items.ItemStackHandler(0).serializeNBT());
        tag.putInt("energy", 5000);
        battery.load(tag);
        helper.assertTrue(battery.items().getSlots() == MachineKind.BATTERY.slots(), "slot count changed to " + battery.items().getSlots());
        battery.items().setStackInSlot(0, new ItemStack(ModItems.CORE_PROTO.get()));
        helper.succeedWhen(() -> helper.assertTrue(
                com.automatavalkyries.item.ItemEnergy.get(battery.items().getStackInSlot(0)) > 0, "proto core not charged"));
    }
}
