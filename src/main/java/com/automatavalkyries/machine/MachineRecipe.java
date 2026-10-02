package com.automatavalkyries.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 基礎文明マシンのレシピ（粉砕・合金・プレス）。材料は「材料 × 個数」の並び（合金炉は 2 つ、他は 1 つ）、結果は 1 つ。
 * <pre>
 * { "type": "automata_valkyries:alloying",
 *   "ingredients": [ { "ingredient": { "tag": "forge:ingots/copper" }, "count": 3 }, { "ingredient": { "tag": "forge:ingots/tin" } } ],
 *   "result": { "item": "automata_valkyries:ingot_bronze", "count": 4 }, "energy": 4000, "time": 200 }
 * </pre>
 * 材料の並びは順不同（合金炉の 2 つのスロットのどちらに入れてもよい）。
 * クロノリンク・マシンのルミナルエナジーアダプターは、さらに "tier"（必要なティア 1〜4）と "stages"（加工の中の段の数、画面に出す）を持つ。
 */
public class MachineRecipe implements Recipe<Container> {

    public record CountedIngredient(Ingredient ingredient, int count) {}

    private final ResourceLocation id;
    private final Supplier<RecipeType<MachineRecipe>> type;
    private final Supplier<RecipeSerializer<MachineRecipe>> serializer;
    private final List<CountedIngredient> inputs;
    private final ItemStack result;
    /** 1 回の加工に使う電力（FE）。時間で割った分を毎 Tick 使う */
    private final int energy;
    /** 加工にかかる Tick 数 */
    private final int time;
    /** 必要なティア（クロノリンク・マシン。0 は問わない）と、加工の中の段の数 */
    private final int tier, stages;

    public MachineRecipe(ResourceLocation id, Supplier<RecipeType<MachineRecipe>> type, Supplier<RecipeSerializer<MachineRecipe>> serializer,
                         List<CountedIngredient> inputs, ItemStack result, int energy, int time) {
        this(id, type, serializer, inputs, result, energy, time, 0, 1);
    }

    public MachineRecipe(ResourceLocation id, Supplier<RecipeType<MachineRecipe>> type, Supplier<RecipeSerializer<MachineRecipe>> serializer,
                         List<CountedIngredient> inputs, ItemStack result, int energy, int time, int tier, int stages) {
        this.id = id;
        this.type = type;
        this.serializer = serializer;
        this.inputs = inputs;
        this.result = result;
        this.energy = energy;
        this.time = time;
        this.tier = tier;
        this.stages = Math.max(1, stages);
    }

    public int tier() { return tier; }
    public int stages() { return stages; }

    public List<CountedIngredient> inputs() { return inputs; }
    public ItemStack result() { return result; }
    public int energy() { return energy; }
    public int time() { return time; }

    /**
     * 材料スロット（container の先頭から inputs と同じ数）に、どの順でも材料が揃っているか。
     * 揃っていれば、材料ごとに使うスロットの番号を返す（揃っていなければ null）
     */
    public int[] match(Container container, int inputSlots) {
        int[] slots = new int[inputs.size()];
        boolean[] used = new boolean[inputSlots];
        for (int i = 0; i < inputs.size(); i++) {
            CountedIngredient need = inputs.get(i);
            slots[i] = -1;
            for (int s = 0; s < inputSlots; s++) {
                ItemStack stack = container.getItem(s);
                if (!used[s] && need.ingredient().test(stack) && stack.getCount() >= need.count()) {
                    slots[i] = s;
                    used[s] = true;
                    break;
                }
            }
            if (slots[i] < 0) return null;
        }
        // 余計な物が入っていないこと（合金炉の空いていないスロットが材料でない）
        for (int s = 0; s < inputSlots; s++) if (!used[s] && !container.getItem(s).isEmpty()) return null;
        return slots;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return match(container, Math.max(1, Math.min(container.getContainerSize(), 2))) != null;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) { return result.copy(); }

    @Override
    public boolean canCraftInDimensions(int width, int height) { return true; }

    @Override
    public ItemStack getResultItem(RegistryAccess access) { return result; }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (CountedIngredient input : inputs) list.add(input.ingredient());
        return list;
    }

    @Override
    public ResourceLocation getId() { return id; }

    @Override
    public RecipeSerializer<?> getSerializer() { return serializer.get(); }

    @Override
    public RecipeType<?> getType() { return type.get(); }

    @Override
    public boolean isSpecial() { return true; } // レシピ本に出さない

    /** 3 種のマシンで共通の読み書き（種類だけ違う） */
    public static class Serializer implements RecipeSerializer<MachineRecipe> {
        private final Supplier<RecipeType<MachineRecipe>> type;
        private final Supplier<RecipeSerializer<MachineRecipe>> self;
        private final int defaultEnergy, defaultTime;

        public Serializer(Supplier<RecipeType<MachineRecipe>> type, Supplier<RecipeSerializer<MachineRecipe>> self,
                          int defaultEnergy, int defaultTime) {
            this.type = type;
            this.self = self;
            this.defaultEnergy = defaultEnergy;
            this.defaultTime = defaultTime;
        }

        @Override
        public MachineRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<CountedIngredient> inputs = new ArrayList<>();
            JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                inputs.add(new CountedIngredient(Ingredient.fromJson(entry.get("ingredient")), GsonHelper.getAsInt(entry, "count", 1)));
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            return new MachineRecipe(id, type, self, inputs, result,
                    GsonHelper.getAsInt(json, "energy", defaultEnergy), GsonHelper.getAsInt(json, "time", defaultTime),
                    GsonHelper.getAsInt(json, "tier", 0), GsonHelper.getAsInt(json, "stages", 1));
        }

        @Override
        public MachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<CountedIngredient> inputs = new ArrayList<>();
            for (int i = 0; i < size; i++) inputs.add(new CountedIngredient(Ingredient.fromNetwork(buf), buf.readVarInt()));
            return new MachineRecipe(id, type, self, inputs, buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MachineRecipe recipe) {
            buf.writeVarInt(recipe.inputs.size());
            for (CountedIngredient input : recipe.inputs) {
                input.ingredient().toNetwork(buf);
                buf.writeVarInt(input.count());
            }
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.energy);
            buf.writeVarInt(recipe.time);
            buf.writeVarInt(recipe.tier);
            buf.writeVarInt(recipe.stages);
        }
    }
}
