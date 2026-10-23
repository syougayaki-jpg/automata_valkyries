package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 零号機再起動パッチ（全体仕様書 §4 の 4・「零号機再起動パッチ」。2026-10-20）: 給電ドックに安置した零号機の素体（眠った零号機）に右クリックで使う。
 * 怨嗟プログラムを完全に消し、同調率を 100% に固定して、使った人の味方として完全に目覚めさせる（{@link ValkyrieEntity#rebootZero}）。
 * 眠った零号機にしか効かない
 */
public class ZeroRebootPatchItem extends Item {

    /** 演出の色（零号機の発光の赤と、消える怨嗟の紫） */
    public static final int RED = 0xFF2858, VIOLET = 0xB04BFF;

    public ZeroRebootPatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ValkyrieEntity v)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!v.isDormantZero()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.zero.patch_only_dormant").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!v.rebootZero(player)) return InteractionResult.FAIL; // 登録の上限など（兵姫の側でメッセージ）
        if (!player.getAbilities().instabuild) stack.shrink(1);
        effect((ServerLevel) player.level(), v);
        player.sendSystemMessage(Component.translatable("message.automata_valkyries.zero.rebooted", v.getDisplayName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("message.automata_valkyries.zero.rebooted.line").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        return InteractionResult.CONSUME;
    }

    /** 再起動の演出: 紫の怨嗟が抜けて散り、赤い魔法陣と衝撃波、光の柱 */
    private static void effect(ServerLevel level, ValkyrieEntity v) {
        Vec3 c = v.position().add(0, v.getBbHeight() * 0.55, 0);
        Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), VIOLET, 0.5F, 30), c, 60, 0.45);
        Fx.mandala(level, v.position().add(0, 0.05, 0), RED, 2.5F, 50, false, false);
        Fx.shockwave(level, v.position().add(0, 0.1, 0), RED, 6.0F, 24);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), RED, 0.45F, 30), v.position(), v.position().add(0, 6.0, 0), 0.25);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.25);
        level.playSound(null, v.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.5F, 0.7F);
        level.playSound(null, v.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.8F, 0.6F);
        v.playSound(ValkyrieEntity.sound("core_startup"), 1.2F, 0.8F);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.item_zero_reboot_patch.desc").withStyle(ChatFormatting.GRAY));
    }
}
