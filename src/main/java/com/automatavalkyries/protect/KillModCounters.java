package com.automatavalkyries.protect;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** 登録済みの {@link KillModCounter} をまとめて適用する */
public final class KillModCounters {

    /**
     * 適用順。値を偽装する ASM / エージェントを持つ MOD（No-Sugar, The Trial Monolith）を先に置くこと。フラグが立ったままだと以後の
     * getHealth / isRemoved 等の読み出しがすべて偽装される。
     */
    private static final List<KillModCounter> COUNTERS = List.of(
            EprotectNoSugarCounter::counter,
            EprotectOmniMobsCounter::counter,
            EprotectTrialMonolithCounter::counter,
            EprotectImdsCounter::counter,
            EprotectHyperlinkCounter::counter
    );

    private KillModCounters() {}

    /** すべての MOD の後始末を適用する。どれかがフラグを解除した場合は true */
    public static boolean counterAll(LivingEntity entity) {
        boolean found = false;
        for (KillModCounter counter : COUNTERS) {
            found |= counter.counter(entity);
        }
        return found;
    }
}
