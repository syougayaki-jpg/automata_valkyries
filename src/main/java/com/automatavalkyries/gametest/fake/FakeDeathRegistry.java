package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * GameTest 用: 架空の即死 MOD の「消去中の隔離」（ECA の EntityRemovalQuarantine の再現）。
 * 一覧の名前（ACTIVE）は辞書に当たらず、クラス名（Death）で抹殺予定の一覧と分かる。攻撃するクラス（{@link FakeKillMod}）とは別のクラスにある。
 */
public final class FakeDeathRegistry {

    public static final Map<Entity, Boolean> ACTIVE = new IdentityHashMap<>();

    private FakeDeathRegistry() {}

    static void begin(Entity entity) {
        ACTIVE.put(entity, Boolean.TRUE);
    }
}
