package com.automatavalkyries.item;

import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.entity.ElenorPressure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * 試練の砂時計（2026-10-23 ユーザー指定）: エレノアに使うと、その攻撃ティアを一段上げる。
 * <ul>
 *   <li>そのまま使う: 使った人（対プレイヤー）のティアを上げる</li>
 *   <li>しゃがんで使う: プレイヤー以外のすべての相手（対エンティティ）のティアを上げる</li>
 * </ul>
 * 最果てのエレノア（ティア 7 固定）には効かない。クリエイティブでは減らない
 */
public class TrialHourglassItem extends Item {

    public TrialHourglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ElenorEntity elenor) || elenor.arts() == null) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        ElenorPressure pressure = elenor.arts().pressure();
        boolean raised = player.isShiftKeyDown() ? pressure.boostEntities() : pressure.boost(player);
        String key = raised ? "item.automata_valkyries.trial_hourglass.raised" : "item.automata_valkyries.trial_hourglass.max";
        player.displayClientMessage(Component.translatable(key).withStyle(raised ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
        if (raised && !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.trial_hourglass.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.automata_valkyries.trial_hourglass.usage").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.trial_hourglass.usage_sneak").withStyle(ChatFormatting.AQUA));
    }
}
