package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

/**
 * 即死系 MOD ごとの後始末（その MOD 独自の死亡・消去フラグの解除）。
 * 該当 MOD が入っていなければ何もしないこと（リフレクションで遅延解決する）。
 * 新しい MOD に対応するときは実装を 1 つ足して {@link KillModCounters} に登録する。
 */
@FunctionalInterface
public interface KillModCounter {
    /** 対象に付いた MOD 独自のフラグを解除する。解除すべきフラグが見つかった場合は true */
    boolean counter(LivingEntity entity);
}
