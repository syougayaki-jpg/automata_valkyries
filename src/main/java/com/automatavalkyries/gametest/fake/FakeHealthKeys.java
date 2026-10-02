package com.automatavalkyries.gametest.fake;

import net.minecraft.network.syncher.EntityDataAccessor;

/** GameTest 用: 架空の MOD が同期データの鍵を置く補助クラス（Fantasy Ending の EntityASMUtil.FE_GET_HEALTH_DATA の再現） */
public final class FakeHealthKeys {
    /** getHealth の結果を下げる差分（Mixin の差し込みから作られ、ここに入る） */
    public static EntityDataAccessor<Float> FAKE_GET_HEALTH_DATA;

    private FakeHealthKeys() {}
}
