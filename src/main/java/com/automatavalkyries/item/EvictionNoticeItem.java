package com.automatavalkyries.item;

import com.automatavalkyries.entity.ProtectDummyZombieEntity;
import com.automatavalkyries.entity.akashic.AkashicNonEntityProxy;
import com.automatavalkyries.entity.akashic.AkashicProbeEntity;
import com.automatavalkyries.entity.akashic.AkashicRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * 退去願い（2026-10-05 ユーザー指定）: 検証用のダミーゾンビ（{@link ProtectDummyZombieEntity}、Eprotect の保護対象）に、正規の退場（{@code Eprotect#triggerDespawn}）を
 * してもらう。ダミーゾンビに右クリックでその 1 体、空中で右クリックで半径 {@link #RADIUS} のダミーゾンビ全部（クライアントから見えなくなった個体も）。
 * エレノア等のボスには効かない。減らない
 */
public class EvictionNoticeItem extends Item {

    public static final double RADIUS = 64.0;

    public EvictionNoticeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof ProtectDummyZombieEntity dummy) {
            if (!player.level().isClientSide) report(player, evict(dummy) ? 1 : 0);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        if (target instanceof AkashicProbeEntity probe) {
            if (!player.level().isClientSide) report(player, evictProbe(probe) ? 1 : 0);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int n = 0;
            for (ProtectDummyZombieEntity dummy : level.getEntitiesOfClass(ProtectDummyZombieEntity.class, player.getBoundingBox().inflate(RADIUS))) {
                if (evict(dummy)) n++;
            }
            for (AkashicProbeEntity probe : level.getEntitiesOfClass(AkashicProbeEntity.class, player.getBoundingBox().inflate(RADIUS))) {
                if (evictProbe(probe)) n++;
            }
            report(player, n);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 正規の退場をしてもらう（保護の登録も外れて、復元されない） */
    public static boolean evict(ProtectDummyZombieEntity dummy) {
        if (dummy.isRemoved() || dummy.getEprotect() == null || dummy.getEprotect().isDespawning()) return false;
        if (dummy.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, dummy.getX(), dummy.getY(0.5), dummy.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        dummy.getEprotect().triggerDespawn();
        return true;
    }

    /** アカシック・プローブにも正規の退場をしてもらう（重複クローンや孤児レコードも合わせて掃除する） */
    public static boolean evictProbe(AkashicProbeEntity probe) {
        if (probe.isRemoved() || probe.getEprotect() == null || probe.getEprotect().isDespawning()) return false;
        if (probe.level() instanceof ServerLevel server) {
            probe.deduplicateStackedClonesAndOrphans(server);
            server.sendParticles(ParticleTypes.POOF, probe.getX(), probe.getY(0.5), probe.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        AkashicNonEntityProxy.unregister(probe.getUUID());
        AkashicRecord.close(probe.getUUID());
        probe.getEprotect().triggerDespawn();
        return true;
    }

    private static void report(Player player, int n) {
        player.displayClientMessage(n > 0
                ? Component.translatable("item.automata_valkyries.eviction_notice.done", n).withStyle(ChatFormatting.GREEN)
                : Component.translatable("item.automata_valkyries.eviction_notice.none").withStyle(ChatFormatting.GRAY), true);
        if (n > 0) player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.eviction_notice.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.eviction_notice.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
