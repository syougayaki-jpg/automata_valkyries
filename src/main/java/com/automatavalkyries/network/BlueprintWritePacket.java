package com.automatavalkyries.network;

import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoMenu;
import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.menu.BlueprintCardMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * JEI のレシピの「+」で設計図カードに書き込む（クライアント → サーバー。2026-10-29）。開いている画面で書き込む先が決まる:
 * 設計図カードの画面ならそのカード、クロノ・ファブリケータの画面なら差してある空のカード（無ければ書き込まない）
 */
public record BlueprintWritePacket(ResourceLocation recipe) {

    public static void encode(BlueprintWritePacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.recipe);
    }

    public static BlueprintWritePacket decode(FriendlyByteBuf buf) {
        return new BlueprintWritePacket(buf.readResourceLocation());
    }

    public static void handle(BlueprintWritePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) return;
        var recipe = player.level().getRecipeManager().byKey(packet.recipe).orElse(null);
        if (recipe == null || !BlueprintCardItem.writable(recipe)) return;
        boolean written = false;
        if (player.containerMenu instanceof BlueprintCardMenu menu && menu.stillValid(player)) {
            menu.card(player).getOrCreateTag().putString("Recipe", packet.recipe.toString());
            written = true;
        } else if (player.containerMenu instanceof ChronoMenu menu && menu.type() == ChronoMachineType.CHRONO_FABRICATOR && menu.machine() != null && menu.stillValid(player)) {
            var items = menu.machine().items();
            for (int i = 0; i < ChronoMachineType.cardSlots(menu.tier()); i++) {
                var card = items.getStackInSlot(i);
                if (!(card.getItem() instanceof BlueprintCardItem) || BlueprintCardItem.recipeId(card) != null) continue;
                var copy = card.copy();
                copy.getOrCreateTag().putString("Recipe", packet.recipe.toString());
                items.setStackInSlot(i, copy);
                written = true;
                break;
            }
            if (!written) player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.no_blank"), true);
        }
        if (written) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.blueprint.written",
                    recipe.getResultItem(player.level().registryAccess()).getHoverName()).append(" ").append(BlueprintCardItem.materials(recipe, player.level())), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }
}
