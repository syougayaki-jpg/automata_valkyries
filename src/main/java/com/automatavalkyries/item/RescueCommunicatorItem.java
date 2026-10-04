package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.RescueListPacket;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 救済通信機（2026-10-04 ユーザー指定）: オートマタヴァルキリー protect に保存された兵姫のデータを一覧で見て、選んだ兵姫を呼び出す。
 * 一覧は自分が持ち主の兵姫（稼働中・失われた・読み込まれていない）と、持ち主のいない失われた兵姫（呼び出すと自分の登録になる）。
 * 呼び出せるのは失われた兵姫（オメガコア無しで消去された）だけ。登録は 1 人 {@link ValkyrieProtect#MAX_REGISTERED} 体まで。作り方は【仮】
 */
public class RescueCommunicatorItem extends Item {

    public RescueCommunicatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            level.playSound(null, player.blockPosition(), ValkyrieEntity.sound("ui_beep"), SoundSource.PLAYERS, 0.8F, 1.0F);
            sendList(sp, "");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 一覧を送る（届くと画面が開く・更新される） */
    public static void sendList(ServerPlayer player, String message) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RescueListPacket(ValkyrieProtect.summaries(player),
                ValkyrieProtect.ownedCount(player.getServer(), player.getUUID()), ValkyrieProtect.MAX_REGISTERED, message));
    }

    /** 通信機を手に持っているか（呼び出しの要求はそのときだけ受け付ける） */
    public static boolean holds(Player player) {
        return player.getMainHandItem().getItem() instanceof RescueCommunicatorItem || player.getOffhandItem().getItem() instanceof RescueCommunicatorItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.rescue_communicator.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
