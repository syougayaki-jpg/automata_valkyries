package com.automatavalkyries.protect;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * プレイヤーの持ち物の一覧（Inventory の items・armor・offhand）に付ける持ち主の印（{@code mixin/PlayerItemGuardMixin}）。
 * 一覧への直接の書き込みを見張るのに使う（{@link PlayerItemGuard}）
 */
public interface OwnedItemList {
    @Nullable
    Player automata_valkyries$getOwner();

    void automata_valkyries$setOwner(Player owner);
}
