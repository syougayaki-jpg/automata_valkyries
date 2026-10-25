package com.automatavalkyries.client;

/** 手元のボスバーの一覧を、サーバーが送ってきた状態へ戻す（{@code mixin/client/BossHealthOverlayGuardMixin} が実装する） */
public interface BossBarRestorer {
    void automata_valkyries$restoreRemoved();
}
