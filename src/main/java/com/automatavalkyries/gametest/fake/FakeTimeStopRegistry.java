package com.automatavalkyries.gametest.fake;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GameTest 用: 架空の MOD の「時間停止中の個体の一覧」（EndingLibrary の TimeStopUtils 等の再現）。
 * 一覧の名前（FROZEN_IDS）ではなくクラス名（TimeStop）で、止める一覧と分かる。攻撃するクラス（{@link FakeKillMod}）とは別のクラスにある
 */
public final class FakeTimeStopRegistry {

    public static final Set<UUID> FROZEN_IDS = new HashSet<>();

    private FakeTimeStopRegistry() {}

    static void freeze(UUID uuid) {
        FROZEN_IDS.add(uuid);
    }
}
