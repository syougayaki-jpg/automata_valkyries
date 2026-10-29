package com.automatavalkyries.compat.jei;

import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoMenu;
import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.menu.BlueprintCardMenu;
import com.automatavalkyries.network.BlueprintWritePacket;
import com.automatavalkyries.network.ModNetwork;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

/**
 * JEI のレシピの「+」で設計図カードに書き込む（2026-10-29 ユーザー指定「設計図カードもJEIの窓からもレシピを持ってこれるようにして」）。
 * 設計図カードの画面と、クロノ・ファブリケータの画面で効く。カードに書けるレシピ（作業台・組み立て機・兵装ワークベンチ）のときだけ「+」が出る
 */
final class BlueprintTransferHandler<C extends AbstractContainerMenu> implements IUniversalRecipeTransferHandler<C> {

    private final Class<C> menuClass;
    private final MenuType<C> menuType;
    private final IRecipeTransferHandlerHelper helper;

    BlueprintTransferHandler(Class<C> menuClass, MenuType<C> menuType, IRecipeTransferHandlerHelper helper) {
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.helper = helper;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return menuClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public IRecipeTransferError transferRecipe(C menu, Object recipe, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!(recipe instanceof Recipe<?> r) || !BlueprintCardItem.writable(r)) return helper.createInternalError(); // 書けないレシピでは「+」を出さない
        if (menu instanceof ChronoMenu chrono) {
            if (chrono.type() != ChronoMachineType.CHRONO_FABRICATOR) return helper.createInternalError();
            boolean blank = false;
            for (int i = 0; i < ChronoMachineType.cardSlots(chrono.tier()); i++) {
                var card = chrono.getSlot(i).getItem();
                if (card.getItem() instanceof BlueprintCardItem && BlueprintCardItem.recipeId(card) == null) blank = true;
            }
            if (!blank) return helper.createUserErrorWithTooltip(Component.translatable("message.automata_valkyries.blueprint.no_blank"));
        } else if (!(menu instanceof BlueprintCardMenu)) {
            return helper.createInternalError();
        }
        if (doTransfer) ModNetwork.CHANNEL.sendToServer(new BlueprintWritePacket(r.getId()));
        return null;
    }
}
