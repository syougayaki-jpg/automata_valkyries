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
        Optional<? extends Recipe<?>> recipe = findFor(level.getRecipeManager(), level, wanted);
        if (recipe.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.none", wanted.getHoverName()), true);
            return InteractionResultHolder.fail(card);
        }
        card.getOrCreateTag().putString("Recipe", recipe.get().getId().toString());
        player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.written", wanted.getHoverName()), true);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        return InteractionResultHolder.success(card);
    }

    /** その物を作るレシピ（作業台 → 組み立て機 → 兵装ワークベンチの順に探す） */
    public static Optional<? extends Recipe<?>> findFor(RecipeManager manager, Level level, ItemStack wanted) {
        Stream<Recipe<?>> all = Stream.of(RecipeType.CRAFTING, ModRecipes.ASSEMBLING.get(), ModRecipes.WEAPON_ASSEMBLING.get())
                .flatMap(type -> manager.getAllRecipesFor((RecipeType) type).stream());
        return all.filter(r -> !r.isSpecial() || r.getType() != RecipeType.CRAFTING)
                .filter(r -> ItemStack.isSameItem(r.getResultItem(level.registryAccess()), wanted))
                .findFirst();
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
                    r -> tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.recipe",
                            r.getResultItem(level.registryAccess()).getHoverName()).withStyle(ChatFormatting.AQUA)),
                    () -> tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.RED)));
        } else {
            tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.automata_valkyries.blueprint_card.desc").withStyle(ChatFormatting.GRAY));
    }
}
