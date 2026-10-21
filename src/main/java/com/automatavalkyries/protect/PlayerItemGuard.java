package com.automatavalkyries.protect;

import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 保護中のプレイヤーの持ち物を、ブラックリストの MOD が直接別のアイテムに差し替えようとしたら止める（2026-10-22 ユーザー指定「アイテム変換鬱陶しい」）。
 * Madness Entities の Notch は、主手・副手のアイテムを本に置き換える（言語ファイルの {@code commands.notch.replaceitem}）。
 * <p>
 * 範囲を絞る: サーバー側・保護中のプレイヤー（護符・特権守護）だけ、元が空でなく別の種類のアイテムへの差し替えだけ（個数や NBT の変化は通す）、
 * 直接の呼び出し元がブラックリストの MOD のときだけ（プレイヤー自身の操作・他の MOD のふつうの付与は通す）。
 * 呼び出し経路を辿るのは最後（種類が変わり、ブラックリストが空でないときだけ）
 * </p>
 */
public final class PlayerItemGuard {

    private PlayerItemGuard() {}

    /** 止めるなら true（記録も残す） */
    public static boolean shouldBlock(Player player, ItemStack current, ItemStack incoming, String op) {
        if (!(player instanceof ServerPlayer) || player.level().isClientSide) return false;
        if (current.isEmpty() || ItemStack.isSameItem(current, incoming)) return false;
        if (!ModBlacklist.isActive() || !EprotectCompat.shouldShield(player)) return false;
        String mod = CallerAttribution.immediateCallerMod(player);
        if (!CallerAttribution.isForeign(mod) || !ModBlacklist.contains(mod)) return false;
        AttackLog.shielded(player, "item_replace:" + op);
        return true;
    }
}
