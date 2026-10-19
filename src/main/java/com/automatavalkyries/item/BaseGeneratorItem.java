package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.world.CorruptedBases;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 侵食基地の生成機（クリエイティブ用。2026-10-19 ユーザー指定「基地を生成できるクリエイティブのアイテム」）。
 * スニークしながら右クリックで種類（物資倉庫・クロノ・アウトリポスト・ディメンション・ファウンドリ・クロノ・バスティオン）を切り替え、
 * ブロックへ右クリックでそこに置く（{@code /place structure} と同じ。中心はその塊の真ん中。地上の基地は水の上・急な斜面では断られる。バスティオンは空の上に浮かぶ）。
 * クリエイティブか、コマンドを使える者だけ。敵・中ボスも普通の生成と同じに置かれる
 */
public class BaseGeneratorItem extends Item {

    public BaseGeneratorItem(Properties properties) {
        super(properties);
    }

    static CorruptedBases.Kind kind(ItemStack stack) {
        CorruptedBases.Kind[] kinds = CorruptedBases.Kind.values();
        return kinds[Math.floorMod(stack.getOrCreateTag().getInt("Kind"), kinds.length)];
    }

    static Component kindName(CorruptedBases.Kind kind) {
        return Component.translatable("structure.automata_valkyries." + kind.id);
    }

    static boolean allowed(Player player) {
        return player.getAbilities().instabuild || player.hasPermissions(2);
    }

    /** スニークで種類を切り替える */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            CorruptedBases.Kind next = CorruptedBases.Kind.values()[(kind(stack).ordinal() + 1) % CorruptedBases.Kind.values().length];
            stack.getOrCreateTag().putInt("Kind", next.ordinal());
            player.displayClientMessage(Component.translatable("item.automata_valkyries.base_generator.selected", kindName(next)).withStyle(ChatFormatting.AQUA), true);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return use(ctx.getLevel(), player, ctx.getHand()).getResult();
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !allowed(player)) {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.base_generator.denied").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        CorruptedBases.Kind kind = kind(ctx.getItemInHand());
        BlockPos at = ctx.getClickedPos().above();
        var server = sp.getServer();
        var source = sp.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        int done = server.getCommands().performPrefixedCommand(source,
                "place structure " + AutomataValkyries.MODID + ":" + kind.id + " " + at.getX() + " " + at.getY() + " " + at.getZ());
        if (done > 0) {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.base_generator.placed", kindName(kind)).withStyle(ChatFormatting.GREEN), true);
            ctx.getLevel().playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        } else {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.base_generator.failed", kindName(kind)).withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.base_generator.current", kindName(kind(stack))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.base_generator.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
