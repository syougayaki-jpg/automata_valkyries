package com.automatavalkyries.gametest.fake;

/**
 * GameTest 用: 架空の MOD がエンティティに足す「状態」のインターフェース（実際の MOD は Mixin で vanilla のクラスに足す）。
 * 死亡予定・破滅度は戻される対象、保護は名前が守りを表すので戻されない対象。
 */
public interface FakeKillState {
    boolean fakemod$isMarkedForDeath();

    void fakemod$setMarkedForDeath(boolean marked);

    float fakemod$getDoomLevel();

    void fakemod$setDoomLevel(float level);

    boolean fakemod$isProtected();

    void fakemod$setProtected(boolean value);
}
