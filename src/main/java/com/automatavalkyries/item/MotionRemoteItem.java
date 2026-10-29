package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * モーションリモコン（2026-10-29 ユーザー指定「モーションリモコンを追加して」）: 自分の戦術兵姫に、戦闘以外の仕草をさせる。
 * <ul>
 *   <li>右クリック: 半径 {@link #RANGE} の自分の兵姫みなに、選んでいる仕草をさせる。兵姫を右クリックするとその 1 体だけ</li>
 *   <li>しゃがみ右クリック: 仕草を 手を振る → 喜ぶ → うなずく → 覗き込む → しょんぼり → 伸び → あくび → きょろきょろ → 髪を直す → 鼻歌 と切り替える</li>
 *   <li>仕草に合う感情アイコンを出し、仕草ごとの一言を添える（伸び・あくび・きょろきょろ・髪・鼻歌は待機中の仕草と同じセリフ）</li>
 * </ul>
 * 戦っている・機能停止・休眠中の兵姫はしない。減らない
 */
public class MotionRemoteItem extends Item {

    public static final double RANGE = 32.0;
    /** 仕草の名前（lang の item.automata_valkyries.motion_remote.motion.<名前>）と種類・感情アイコン・セリフの場面 */
    private static final String[] NAMES = {"wave", "joy", "nod", "peek", "dejected", "stretch", "yawn", "look_around", "fix_hair", "hum"};
    private static final int[] GESTURES = {ValkyrieEntity.GESTURE_WAVE, ValkyrieEntity.GESTURE_JOY, ValkyrieEntity.GESTURE_NOD, ValkyrieEntity.GESTURE_PEEK,
            ValkyrieEntity.GESTURE_DEJECTED, ValkyrieEntity.GESTURE_STRETCH, ValkyrieEntity.GESTURE_YAWN, ValkyrieEntity.GESTURE_LOOK_AROUND,
            ValkyrieEntity.GESTURE_FIX_HAIR, ValkyrieEntity.GESTURE_HUM};
    private static final ValkyrieEntity.Emote[] EMOTES = {ValkyrieEntity.Emote.NONE, ValkyrieEntity.Emote.HEART, ValkyrieEntity.Emote.NONE,
            ValkyrieEntity.Emote.QUESTION, ValkyrieEntity.Emote.SWEAT, ValkyrieEntity.Emote.NONE, ValkyrieEntity.Emote.SLEEP,
            ValkyrieEntity.Emote.QUESTION, ValkyrieEntity.Emote.NONE, ValkyrieEntity.Emote.NOTE};
    private static final String[] TALKS = {"greet", "pat", null, "watch_work", null, "idle_stretch", "idle_yawn", "idle_look", "idle_hair", "idle_hum"};

    public MotionRemoteItem(Properties properties) {
        super(properties);
    }

    public static int motion(ItemStack stack) {
        return Math.floorMod(stack.getOrCreateTag().getInt("Motion"), NAMES.length);
    }

    private static Component name(int i) {
        return Component.translatable("item.automata_valkyries.motion_remote.motion." + NAMES[i]);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                stack.getOrCreateTag().putInt("Motion", (motion(stack) + 1) % NAMES.length);
                player.displayClientMessage(Component.translatable("item.automata_valkyries.motion_remote.mode", name(motion(stack))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
            } else {
                int n = 0;
                for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, player.getBoundingBox().inflate(RANGE),
                        v -> player.getUUID().equals(v.getOwnerUUID()))) {
                    if (play(player, stack, v)) n++;
                }
                report(player, stack, n);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ValkyrieEntity v) || !player.getUUID().equals(v.getOwnerUUID())) return InteractionResult.PASS;
        if (!player.level().isClientSide) report(player, stack, play(player, stack, v) ? 1 : 0);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** 1 体に仕草をさせる（できたら true） */
    private static boolean play(Player player, ItemStack stack, ValkyrieEntity v) {
        if (v.isShutdown() || v.isAsleep() || v.getTarget() != null) return false;
        int i = motion(stack);
        v.getLookControl().setLookAt(player, 30.0F, 30.0F);
        v.gesture(GESTURES[i]);
        if (EMOTES[i] != ValkyrieEntity.Emote.NONE) v.emote(EMOTES[i]);
        if (TALKS[i] != null) v.talkNow(TALKS[i]);
        return true;
    }

    private static void report(Player player, ItemStack stack, int n) {
        player.displayClientMessage((n > 0 ? Component.translatable("item.automata_valkyries.motion_remote.done", name(motion(stack)), n)
                : Component.translatable("item.automata_valkyries.motion_remote.none")).withStyle(n > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.motion_remote.mode", name(motion(stack))).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.automata_valkyries.motion_remote.desc").withStyle(ChatFormatting.GRAY));
    }
}
