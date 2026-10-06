package com.automatavalkyries.item;

import com.automatavalkyries.registry.ModRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 設計図カード（docs/SPEC_CHRONOLINK_MACHINES.md §4.4）: クロノ・ファブリケータに差すレシピ 1 つ。
 * 作りたい物をオフハンドに持ち、カードを持って右クリックすると、それを作るレシピ（作業台・組み立て機・兵装ワークベンチ）を書き込む。
 * オフハンドが空なら消す。
 */
public class BlueprintCardItem extends Item {

    public BlueprintCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack card = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(card);
        if (level.isClientSide) return InteractionResultHolder.success(card);
        ItemStack wanted = player.getOffhandItem();
        if (wanted.isEmpty()) {
            card.removeTagKey("Recipe");
            player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.cleared"), true);
            return InteractionResultHolder.success(card);
        }
        // 同じ物を作るレシピがいくつもあれば、持ち物の材料でいちばん作れそうな物を選ぶ。もう一度同じ物で使うと次のレシピへ
        // （最初に見つかったレシピを選び、思っていた材料と違って「材料として認識されない」ことがあった。2026-10-07）
        List<Recipe<?>> options = allFor(level.getRecipeManager(), level, wanted);
        ResourceLocation current = recipeId(card);
        Optional<? extends Recipe<?>> recipe;
        int at = -1;
        for (int i = 0; i < options.size(); i++) if (options.get(i).getId().equals(current)) at = i;
        if (at >= 0 && options.size() > 1) recipe = Optional.of(options.get((at + 1) % options.size()));
        else recipe = options.stream().max(java.util.Comparator.comparingInt(r -> score(r, player)));
        if (recipe.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.none", wanted.getHoverName()), true);
            return InteractionResultHolder.fail(card);
        }
        card.getOrCreateTag().putString("Recipe", recipe.get().getId().toString());
        player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.written", wanted.getHoverName())
                .append(" ").append(materials(recipe.get(), level)).append(options.size() > 1
                        ? Component.translatable("message.automata_valkyries.blueprint.alternatives", options.size()) : Component.empty()), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        return InteractionResultHolder.success(card);
    }

    /** その物を作るレシピ（作業台 → 組み立て機 → 兵装ワークベンチの順に探す） */
    public static Optional<? extends Recipe<?>> findFor(RecipeManager manager, Level level, ItemStack wanted) {
        return allFor(manager, level, wanted).stream().findFirst();
    }

    /** その物を作るレシピみな（作業台 → 組み立て機 → 兵装ワークベンチの順。同じ種類の中は ID 順） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Recipe<?>> allFor(RecipeManager manager, Level level, ItemStack wanted) {
        Stream<Recipe<?>> all = Stream.of(RecipeType.CRAFTING, ModRecipes.ASSEMBLING.get(), ModRecipes.WEAPON_ASSEMBLING.get())
                .flatMap(type -> manager.getAllRecipesFor((RecipeType) type).stream()
                        .sorted(java.util.Comparator.comparing(r -> ((Recipe<?>) r).getId().toString())));
        return all.filter(r -> !r.isSpecial() || r.getType() != RecipeType.CRAFTING)
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), wanted))
                .toList();
    }

    /** レシピの材料（材料 × 個数。同じ材料はまとめる） */
    public static List<java.util.Map.Entry<net.minecraft.world.item.crafting.Ingredient, Integer>> needs(Recipe<?> recipe) {
        List<java.util.Map.Entry<net.minecraft.world.item.crafting.Ingredient, Integer>> out = new java.util.ArrayList<>();
        if (recipe instanceof com.automatavalkyries.machine.MachineRecipe machine) {
            for (var in : machine.inputs()) out.add(java.util.Map.entry(in.ingredient(), in.count()));
            return out;
        }
        outer:
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            for (int i = 0; i < out.size(); i++) {
                if (java.util.Arrays.equals(out.get(i).getKey().getItems(), ingredient.getItems(), (a, b) -> ItemStack.isSameItemSameTags(a, b) ? 0 : 1)) {
                    out.set(i, java.util.Map.entry(out.get(i).getKey(), out.get(i).getValue() + 1));
                    continue outer;
                }
            }
            out.add(java.util.Map.entry(ingredient, 1));
        }
        return out;
    }

    /** 持ち物でそろう材料の種類の数（レシピ選びに使う） */
    private static int score(Recipe<?> recipe, Player player) {
        int ok = 0;
        for (var need : needs(recipe)) {
            int have = 0;
            for (ItemStack s : player.getInventory().items) if (need.getKey().test(s)) have += s.getCount();
            if (have >= need.getValue()) ok++;
        }
        return ok;
    }

    /** 材料の一覧の文（「鉄インゴット ×3、棒 ×2」） */
    public static Component materials(Recipe<?> recipe, Level level) {
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        var needs = needs(recipe);
        for (int i = 0; i < needs.size(); i++) {
            if (i > 0) text.append("、");
            ItemStack[] items = needs.get(i).getKey().getItems();
            text.append(items.length > 0 ? items[0].getHoverName() : Component.literal("?")).append(" ×" + needs.get(i).getValue());
        }
        return text;
    }

    @Nullable
    public static ResourceLocation recipeId(ItemStack card) {
        if (card.getTag() == null || !card.getTag().contains("Recipe")) return null;
        return ResourceLocation.tryParse(card.getTag().getString("Recipe"));
    }

    @Override
    public boolean isFoil(ItemStack stack) { return recipeId(stack) != null; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = recipeId(stack);
        if (id != null && level != null) {
            level.getRecipeManager().byKey(id).ifPresentOrElse(
                    r -> {
                        tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.recipe",
                                r.getResultItem(level.registryAccess()).getHoverName()).withStyle(ChatFormatting.AQUA));
                        // 1 回分の材料（ファブリケータは搬入リンク先にこれがそろうと作る）
                        tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.materials", materials(r, level)).withStyle(ChatFormatting.GRAY));
                    },
                    () -> tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.RED)));
        } else {
            tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.desc").withStyle(ChatFormatting.GRAY));
    }
}
