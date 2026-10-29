package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ゲーム内の説明書「戦術兵姫の手引き」（2026-10-30 ユーザー指定「最後にこのMODの説明を詳細にかいて作る」→「ゲーム内での説明書をつくる」）。
 * 右クリックで章ごとの読み物の画面（{@code client/GuideScreen}）を開く。中身は {@code assets/automata_valkyries/guide/<言語>.json}（{@code tools/gen_guide.py} で作る）。
 * 初めて入ったプレイヤーに 1 冊渡す（死んでも消えない永続データに印）。なくしたら作業台で本＋クロノスクラップから作れる
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class GuideBookItem extends Item {

    private static final String GIVEN = "automata_valkyries_guide_given";

    public GuideBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.automatavalkyries.client.GuideScreen.open());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.guide_book.desc").withStyle(ChatFormatting.GRAY));
    }

    /** 初めて入ったら 1 冊渡す */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) return;
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(com.automatavalkyries.registry.ModItems.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
