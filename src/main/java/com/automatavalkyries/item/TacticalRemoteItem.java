package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ai.ValkyrieCombat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 指示リモコン（2026-10-06 ユーザー指定「ステップをさせれる指示リモコン」）: 自分の戦術兵姫にステップを指示する。
 * <ul>
 *   <li>右クリック: 半径 {@link #RANGE} の自分の兵姫みなに、選んでいる向き（自分の向きから見て前・後ろ・左・右）へステップさせる。
 *       兵姫を右クリックするとその 1 体だけ</li>
 *   <li>しゃがみ右クリック: 指示を 前 → 後ろ → 左 → 右（ステップ）→ 手を振る → 喜ぶ → うなずく → 覗き込む → しょんぼり（仕草）→ 話す（セリフ）と切り替える
 *       （仕草・セリフは 2026-10-06 ユーザー指定）</li>
 * </ul>
 * 指示のステップは、近接の封じ・その向きのクールタイムを無視する（ステップの最中は無理）。クールタイムはその向きに付く。減らない
 */
public class TacticalRemoteItem extends Item {

    public static final double RANGE = 32.0;
    private static final String[] DIRS = {"forward", "back", "left", "right", "wave", "joy", "nod", "peek", "dejected", "talk"};
    /** 仕草の指示の種類（DIRS の 4 番から） */
    private static final int[] GESTURES = {ValkyrieEntity.GESTURE_WAVE, ValkyrieEntity.GESTURE_JOY, ValkyrieEntity.GESTURE_NOD,
            ValkyrieEntity.GESTURE_PEEK, ValkyrieEntity.GESTURE_DEJECTED};
    /** 話す指示で言う場面（そのときに合いそうな物から） */
    private static final String[] TALKS = {"idle", "greet", "pat", "sit", "night"};

    public TacticalRemoteItem(Properties properties) {
        super(properties);
    }

    public static int dir(ItemStack stack) {
        return Math.floorMod(stack.getOrCreateTag().getInt("Dir"), DIRS.length);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                stack.getOrCreateTag().putInt("Dir", (dir(stack) + 1) % DIRS.length);
                player.displayClientMessage(Component.translatable("item.automata_valkyries.tactical_remote.mode",
                        Component.translatable("item.automata_valkyries.tactical_remote.dir." + DIRS[dir(stack)])).withStyle(ChatFormatting.AQUA), true);
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
            } else {
                int n = 0;
                for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, player.getBoundingBox().inflate(RANGE),
                        v -> player.getUUID().equals(v.getOwnerUUID()))) {
                    if (order(player, stack, v)) n++;
                }
                report(player, n);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ValkyrieEntity v) || !player.getUUID().equals(v.getOwnerUUID())) return InteractionResult.PASS;
        if (!player.level().isClientSide) report(player, order(player, stack, v) ? 1 : 0);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** 自分の向きから見た、選んでいる向き（水平） */
    private static Vec3 direction(Player player, ItemStack stack) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return switch (dir(stack)) {
            case 1 -> fwd.scale(-1);
            case 2 -> new Vec3(fwd.z, 0, -fwd.x);  // 左
            case 3 -> new Vec3(-fwd.z, 0, fwd.x);  // 右
            default -> fwd;
        };
    }

    private static boolean order(Player player, ItemStack stack, ValkyrieEntity v) {
        int mode = dir(stack);
        if (mode < 4) return ValkyrieCombat.orderStep(v, direction(player, stack));
        if (v.isShutdown() || v.isAsleep()) return false;
        if (mode < 4 + GESTURES.length) {
            int g = GESTURES[mode - 4];
            v.gesture(g);
            v.mood(g == ValkyrieEntity.GESTURE_DEJECTED ? 4 : g == ValkyrieEntity.GESTURE_PEEK ? 5 : 1, 40);
        } else {
            v.talkNow(TALKS[v.getRandom().nextInt(TALKS.length)]);
        }
        return true;
    }

    private static void report(Player player, int n) {
        player.displayClientMessage(n > 0
                ? Component.translatable("item.automata_valkyries.tactical_remote.done", n).withStyle(ChatFormatting.AQUA) // 「n 体がステップした」等
                : Component.translatable("item.automata_valkyries.tactical_remote.none").withStyle(ChatFormatting.GRAY), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, n > 0 ? 1.8F : 0.8F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.tactical_remote.mode",
                Component.translatable("item.automata_valkyries.tactical_remote.dir." + DIRS[dir(stack)])).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.tactical_remote.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.tactical_remote.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
