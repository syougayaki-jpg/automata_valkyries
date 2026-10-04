package com.automatavalkyries.network;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.menu.ValkyrieMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 戦術兵姫のメニューの操作（クライアント → サーバー。段階 2、2026-10-05）。その兵姫のメニューを開いている持ち主のときだけ受け付ける。
 * <ul>
 *   <li>{@link #MODE}: 行動モード（value = {@link ValkyrieEntity.Mode} の番号）</li>
 *   <li>{@link #SET_HOME}: 今いる場所（兵姫の位置）を拠点に</li>
 *   <li>{@link #ORDER}: 作戦指示の 1 項目（arg = {@link ValkyrieOrders.Field} の番号、value = 値）</li>
 *   <li>{@link #EXPRESSION}: 表情（value = 番号）</li>
 *   <li>{@link #RENAME}: 名前（text。空なら名前を外す）</li>
 * </ul>
 */
public record ValkyrieCommandPacket(int entityId, int action, int arg, int value, String text) {

    public static final int MODE = 0, SET_HOME = 1, ORDER = 2, EXPRESSION = 3, RENAME = 4;
    public static final int MAX_NAME = 32;

    public static ValkyrieCommandPacket of(int entityId, int action, int arg, int value) {
        return new ValkyrieCommandPacket(entityId, action, arg, value, "");
    }

    public static void encode(ValkyrieCommandPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.arg);
        buf.writeVarInt(p.value);
        buf.writeUtf(p.text, MAX_NAME);
    }

    public static ValkyrieCommandPacket decode(FriendlyByteBuf buf) {
        return new ValkyrieCommandPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(MAX_NAME));
    }

    public static void handle(ValkyrieCommandPacket p, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !(player.containerMenu instanceof ValkyrieMenu menu) || menu.valkyrie.getId() != p.entityId
                || !menu.stillValid(player)) return;
        ValkyrieEntity v = menu.valkyrie;
        switch (p.action) {
            case MODE -> {
                ValkyrieEntity.Mode[] modes = ValkyrieEntity.Mode.values();
                if (p.value < 0 || p.value >= modes.length) return;
                if (!v.setMode(modes[p.value])) {
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.too_many", ValkyrieEntity.MAX_FOLLOWERS)
                            .withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            case SET_HOME -> v.setHomeHere();
            case ORDER -> {
                ValkyrieOrders.Field[] fields = ValkyrieOrders.Field.values();
                if (p.arg < 0 || p.arg >= fields.length) return;
                v.orders.set(fields[p.arg], p.value);
                v.applyOrders();
            }
            case EXPRESSION -> v.setExpression(p.value);
            case RENAME -> {
                String name = p.text.strip();
                v.setCustomName(name.isEmpty() ? null : Component.literal(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name));
            }
            default -> {
                return;
            }
        }
        v.playSound(ValkyrieEntity.sound("ui_confirm"), 0.5F, 1.0F);
    }
}
