package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 瞬間転移の端末（2026-10-19 ユーザー指定「目線の先 10 m 先にテレポートできるアイテム」）。右クリックで、見ている向きへ {@value #RANGE} m 先へ跳ぶ。
 * 途中に壁があれば壁の手前で止まり、着いた先で体が埋まるなら少しずつ手前へ戻す（埋まらない所が無ければ跳ばない）。落下の高さは消える。
 * 使うたびに {@value #COOLDOWN} Tick 待つ。作り方は無し（クリエイティブの特殊のタブ）
 */
public class BlinkItem extends Item {

    public static final double RANGE = 10.0;
    public static final int COOLDOWN = 10;

    public BlinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        Vec3 dest = destination(level, player);
        if (dest == null) {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.blink_device.blocked").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        Vec3 from = player.position();
        ServerLevel server = (ServerLevel) level;
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();
        server.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1.0, dest.z, 40, 0.3, 0.6, 0.3, 0.3);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    /** 跳ぶ先（足の位置）。目の高さから見ている向きへ光線を飛ばし、当たった壁の手前か 10 m 先。体が入らなければ 0.5 m ずつ戻す */
    @Nullable
    public static Vec3 destination(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = hit.getType() == HitResult.Type.MISS ? RANGE : Math.max(0.0, hit.getLocation().distanceTo(eye) - 0.6);
        double eyeHeight = player.getEyeHeight();
        for (double d = reach; d >= 1.0; d -= 0.5) {
            Vec3 feet = eye.add(look.scale(d)).subtract(0, eyeHeight, 0);
            if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) return feet;
            Vec3 up = feet.add(0, 1.0, 0); // 段差なら 1 段上へ
            if (level.noCollision(player, player.getBoundingBox().move(up.subtract(player.position())))) return up;
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.blink_device.desc").withStyle(ChatFormatting.GRAY));
    }
}
