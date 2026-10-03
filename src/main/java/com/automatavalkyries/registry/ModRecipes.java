package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.machine.MachineRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 基礎文明マシンのレシピの種類（粉砕・合金・プレス） */
public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AutomataValkyries.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AutomataValkyries.MODID);

    public static final RegistryObject<RecipeType<MachineRecipe>> CRUSHING = type("crushing");
    public static final RegistryObject<RecipeType<MachineRecipe>> ALLOYING = type("alloying");
    public static final RegistryObject<RecipeType<MachineRecipe>> PRESSING = type("pressing");
    public static final RegistryObject<RecipeType<MachineRecipe>> ASSEMBLING = type("assembling");
    public static final RegistryObject<RecipeType<MachineRecipe>> WEAPON_ASSEMBLING = type("weapon_assembling");
    /** ルミナルエナジーアダプターの原子相転移（クロノリンク・マシン設計書 §4.3）。"tier" で必要なティア */
    public static final RegistryObject<RecipeType<MachineRecipe>> PHASE_TRANSITION = type("phase_transition");
    /** タクティカル・アーモリーの兵装の組み立て（兵器ユニット設計書）。"tier" で必要なティア */
    public static final RegistryObject<RecipeType<MachineRecipe>> ARMORY_ASSEMBLING = type("armory_assembling");

    public static final RegistryObject<RecipeSerializer<MachineRecipe>> CRUSHING_SERIALIZER = serializer("crushing", CRUSHING, 2000, 100);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> ALLOYING_SERIALIZER = serializer("alloying", ALLOYING, 4000, 200);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> PRESSING_SERIALIZER = serializer("pressing", PRESSING, 2000, 100);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> ASSEMBLING_SERIALIZER = serializer("assembling", ASSEMBLING, 50000, 400);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> WEAPON_ASSEMBLING_SERIALIZER = serializer("weapon_assembling", WEAPON_ASSEMBLING, 30000, 300);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> PHASE_TRANSITION_SERIALIZER = serializer("phase_transition", PHASE_TRANSITION, 20000, 200);
    public static final RegistryObject<RecipeSerializer<MachineRecipe>> ARMORY_ASSEMBLING_SERIALIZER = serializer("armory_assembling", ARMORY_ASSEMBLING, 100000, 400);

    private ModRecipes() {}

    private static RegistryObject<RecipeType<MachineRecipe>> type(String name) {
        return TYPES.register(name, () -> new RecipeType<>() {
            @Override
            public String toString() {
                return AutomataValkyries.MODID + ":" + name;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static RegistryObject<RecipeSerializer<MachineRecipe>> serializer(String name, RegistryObject<RecipeType<MachineRecipe>> type,
                                                                              int energy, int time) {
        RegistryObject<RecipeSerializer<MachineRecipe>>[] self = new RegistryObject[1];
        self[0] = SERIALIZERS.register(name, () -> new MachineRecipe.Serializer(type, () -> self[0].get(), energy, time));
        return self[0];
    }
}
