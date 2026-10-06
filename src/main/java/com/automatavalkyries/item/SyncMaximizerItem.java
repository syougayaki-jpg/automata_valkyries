package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 同調率マキシマイザー（クリエイティブ用。2026-10-07 ユーザー指定「戦術兵姫の同調率を 100% に上げるクリエイティブのアイテム」）。
 * 自分の兵姫を右クリックすると同調率を 100% にする。レシピは無く、特別タブにだけ並ぶ
 */
public class SyncMaximizerItem extends Item {

    public SyncMaximizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ValkyrieEntity v) || !player.getUUID().equals(v.getOwnerUUID())) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        v.addSync(100.0F - v.getSyncRate(), "creative");
        player.displayClientMessage(Component.translatable("message.automata_valkyries.sync_maximizer.done", v.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, v.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.sync_maximizer.desc").withStyle(ChatFormatting.GRAY));
    }
}
