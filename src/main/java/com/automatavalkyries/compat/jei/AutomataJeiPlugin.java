package com.automatavalkyries.compat.jei;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoSmelterBlockEntity;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.chrono.LuminalReactorBlockEntity;
import com.automatavalkyries.machine.MachineKind;
import com.automatavalkyries.machine.MachineRecipe;
import com.automatavalkyries.registry.ModBlocks;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModCompression;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.registry.ModMachines;
import com.automatavalkyries.registry.ModRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JEI のプラグイン（JEI が入っているときだけ JEI から読み込まれる。このMODの他のクラスからは参照しない）。
 * マシンのレシピ型ごとの表示、マシンを触媒として、ルミナル反応炉の燃料、クロノ・スメルターの倍率の説明。
 */
@JeiPlugin
public class AutomataJeiPlugin implements IModPlugin {

    /** 表示するレシピ型: JEI の型・このMODのレシピ型・題の鍵・アイコン・ティアを出すか */
    private record Entry(RecipeType<MachineRecipe> jei, Supplier<net.minecraft.world.item.crafting.RecipeType<MachineRecipe>> type,
                         String key, Supplier<ItemLike> icon, boolean tier, List<Supplier<ItemLike>> catalysts) {}

    private static RecipeType<MachineRecipe> jei(String path) {
        return RecipeType.create(AutomataValkyries.MODID, path, MachineRecipe.class);
    }

    private static Supplier<ItemLike> machine(MachineKind kind) {
        return () -> ModMachines.MACHINES.get(kind).get();
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(jei("crushing"), ModRecipes.CRUSHING, "crushing", machine(MachineKind.CRUSHER), false,
                    List.of(machine(MachineKind.CRUSHER), machine(MachineKind.IMPROVED_CRUSHER))),
            new Entry(jei("alloying"), ModRecipes.ALLOYING, "alloying", machine(MachineKind.ALLOY_SMELTER), false,
                    List.of(machine(MachineKind.ALLOY_SMELTER), machine(MachineKind.IMPROVED_ALLOY_SMELTER))),
            new Entry(jei("pressing"), ModRecipes.PRESSING, "pressing", machine(MachineKind.PRESS), false,
                    List.of(machine(MachineKind.PRESS), machine(MachineKind.IMPROVED_PRESS))),
            new Entry(jei("assembling"), ModRecipes.ASSEMBLING, "assembling", machine(MachineKind.ASSEMBLER), false,
                    List.of(machine(MachineKind.ASSEMBLER), () -> ModChrono.CHRONO_FABRICATOR.get())),
            new Entry(jei("weapon_assembling"), ModRecipes.WEAPON_ASSEMBLING, "weapon_assembling", machine(MachineKind.WEAPON_WORKBENCH), false,
                    List.of(machine(MachineKind.WEAPON_WORKBENCH), () -> ModChrono.CHRONO_FABRICATOR.get())),
            new Entry(jei("phase_transition"), ModRecipes.PHASE_TRANSITION, "phase_transition", () -> ModChrono.LUMINAL_ADAPTER.get(), true,
                    List.of(() -> ModChrono.LUMINAL_ADAPTER.get())),
            new Entry(jei("armory_assembling"), ModRecipes.ARMORY_ASSEMBLING, "armory_assembling", () -> ModChrono.TACTICAL_ARMORY.get(), true,
                    List.of(() -> ModChrono.TACTICAL_ARMORY.get())));

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (Entry e : ENTRIES) registration.addRecipeCategories(new MachineRecipeCategory(gui, e.jei(), e.key(), e.icon().get(), e.tier()));
        registration.addRecipeCategories(new ReactorFuelCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var manager = level.getRecipeManager();
        for (Entry e : ENTRIES) {
            List<MachineRecipe> recipes = new ArrayList<>(manager.getAllRecipesFor(e.type().get()));
            if (e.tier()) recipes.sort(java.util.Comparator.comparingInt(MachineRecipe::tier));
            registration.addRecipes(e.jei(), recipes);
        }
        registration.addRecipes(ReactorFuelCategory.TYPE, List.of(
                new ReactorFuelCategory.Fuel(new ItemStack(ModItems.CRYSTAL_LUMINAL.get()), LuminalReactorBlockEntity.fuelValue(new ItemStack(ModItems.CRYSTAL_LUMINAL.get()), ChronoTier.HIGH_END), ChronoTier.HIGH_END, false),
                new ReactorFuelCategory.Fuel(new ItemStack(ModBlocks.BLOCK_LUMINAL_GEM.get()), LuminalReactorBlockEntity.fuelValue(new ItemStack(ModBlocks.BLOCK_LUMINAL_GEM.get()), ChronoTier.HIGH_END), ChronoTier.HIGH_END, false),
                new ReactorFuelCategory.Fuel(new ItemStack(ModItems.ORB_LUMINAL.get()), LuminalReactorBlockEntity.fuelValue(new ItemStack(ModItems.ORB_LUMINAL.get()), ChronoTier.FUTURE), ChronoTier.FUTURE, false),
                new ReactorFuelCategory.Fuel(new ItemStack(ModItems.STAR_LUMINAL.get()), LuminalReactorBlockEntity.fuelValue(new ItemStack(ModItems.STAR_LUMINAL.get()), ChronoTier.BEYOND), ChronoTier.BEYOND, false),
                new ReactorFuelCategory.Fuel(new ItemStack(ModCompression.CORE_GENESIS_LUMINAL.get()), LuminalReactorBlockEntity.CORE_FUEL, ChronoTier.NEVERLAST, false),
                new ReactorFuelCategory.Fuel(new ItemStack(ModCompression.CORE_GENESIS_LUMINAL.get()), 0, ChronoTier.NEVERLAST, true)));
        registration.addIngredientInfo(ModChrono.CHRONO_SMELTER.get(), Component.translatable("jei.automata_valkyries.info.chrono_smelter",
                ChronoSmelterBlockEntity.multiplier(ChronoTier.HIGH_END), ChronoSmelterBlockEntity.multiplier(ChronoTier.NEVERLAST)));
        registration.addIngredientInfo(ModChrono.TOOL_CHRONO_TUNER.get(), Component.translatable("jei.automata_valkyries.info.chrono_tuner"));
        // 観測の立方体の金床での強化（2026-10-29）: 能力ごとに、オーブ・スター・コアで 1 段上げた見本
        registration.addIngredientInfo(ModItems.AKASHIC_CUBES.get(), Component.translatable("jei.automata_valkyries.info.akashic_cubes"));
        var anvil = new ArrayList<mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe>();
        ItemStack cubes = new ItemStack(ModItems.AKASHIC_CUBES.get());
        for (var stat : com.automatavalkyries.item.AkashicCubeUpgrades.Stat.values()) {
            List<ItemStack> catalysts = new ArrayList<>(), outputs = new ArrayList<>();
            for (String kind : List.of("orb", "star", "core")) {
                ItemStack catalyst = new ItemStack(com.automatavalkyries.item.AkashicCubeUpgrades.catalyst(kind, stat));
                if (catalyst.isEmpty()) continue;
                catalysts.add(catalyst);
                outputs.add(com.automatavalkyries.item.AkashicCubeUpgrades.result(cubes, catalyst));
            }
            if (!catalysts.isEmpty()) anvil.add(registration.getVanillaRecipeFactory().createAnvilRecipe(cubes, catalysts, outputs));
        }
        registration.addRecipes(RecipeTypes.ANVIL, anvil);
        registration.addIngredientInfo(ModChrono.BLUEPRINT_CARD.get(), Component.translatable("jei.automata_valkyries.info.blueprint_card"));
        for (var frame : List.of(ModChrono.FRAME_HIGH_END, ModChrono.FRAME_FUTURE, ModChrono.FRAME_BEYOND, ModChrono.FRAME_NEVERLAST)) {
            registration.addIngredientInfo(frame.get(), Component.translatable("jei.automata_valkyries.info.tier_frame"));
        }
        for (var kit : List.of(ModChrono.UPGRADE_KIT_FUTURE, ModChrono.UPGRADE_KIT_BEYOND, ModChrono.UPGRADE_KIT_NEVERLAST)) {
            registration.addIngredientInfo(kit.get(), Component.translatable("jei.automata_valkyries.info.upgrade_kit"));
        }
        for (ChronoMachineType type : ChronoMachineType.values()) {
            registration.addIngredientInfo(ModChrono.MACHINES.get(type).get(), Component.translatable("block.automata_valkyries." + type.id + ".desc"),
                    Component.translatable("jei.automata_valkyries.info.chrono_machine"));
        }
    }

    /** JEI のレシピの「+」で設計図カードに書き込む（設計図カードの画面とクロノ・ファブリケータの画面。2026-10-29） */
    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addUniversalRecipeTransferHandler(new BlueprintTransferHandler<>(com.automatavalkyries.menu.BlueprintCardMenu.class, ModChrono.BLUEPRINT_MENU.get(), helper));
        registration.addUniversalRecipeTransferHandler(new BlueprintTransferHandler<>(com.automatavalkyries.chrono.ChronoMenu.class, ModChrono.MENU.get(), helper));
    }

    /** クロノリンク・マシンはティアごとに別のアイテムとして扱う（T2〜T4 のレシピが T1 のレシピに混ざらず、それぞれで引ける） */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (var block : ModChrono.MACHINES.values()) {
            registration.registerSubtypeInterpreter(block.get().asItem(),
                    (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>) (stack, context) ->
                            "tier" + com.automatavalkyries.chrono.ChronoMachineItem.tierOf(stack).level);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (Entry e : ENTRIES) for (Supplier<ItemLike> catalyst : e.catalysts()) registration.addRecipeCatalyst(catalyst.get(), e.jei());
        registration.addRecipeCatalyst(ModChrono.LUMINAL_REACTOR.get(), ReactorFuelCategory.TYPE);
        registration.addRecipeCatalyst(ModMachines.MACHINES.get(MachineKind.ELECTRIC_FURNACE).get(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(ModMachines.MACHINES.get(MachineKind.IMPROVED_ELECTRIC_FURNACE).get(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(ModChrono.CHRONO_SMELTER.get(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(ModChrono.CHRONO_FABRICATOR.get(), RecipeTypes.CRAFTING);
        registration.addRecipeCatalyst(ModMachines.MACHINES.get(MachineKind.GENERATOR).get(), RecipeTypes.FUELING);
        registration.addRecipeCatalyst(ModMachines.MACHINES.get(MachineKind.IMPROVED_GENERATOR).get(), RecipeTypes.FUELING);
    }
}
