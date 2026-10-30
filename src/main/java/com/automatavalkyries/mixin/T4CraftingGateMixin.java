package com.automatavalkyries.mixin;

import com.automatavalkyries.chrono.T4Unlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * T4 のマシンのレシピを、タイム・ルーラーを倒すまで作業台（と持ち物の 2×2）で作れなくする（{@link T4Unlock}。2026-10-31）。
 * 作業台は結果を出す前に {@code ResultContainer.setRecipeUsed(level, player, recipe)} で「このプレイヤーが使ってよいレシピか」を聞く（バニラの
 * doLimitedCrafting と同じ所）。そこで解禁していなければ false を返し、結果の枠を空にする
 */
@Mixin(CraftingMenu.class)
public abstract class T4CraftingGateMixin {

    @Redirect(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private static boolean automata_valkyries$gateT4(ResultContainer result, Level level, ServerPlayer player, Recipe<?> recipe) {
        if (T4Unlock.gated(recipe) && !T4Unlock.unlocked(player)) return false;
        return result.setRecipeUsed(level, player, recipe);
    }
}
