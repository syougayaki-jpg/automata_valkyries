package com.automatavalkyries.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * {@code ChunkMap.TrackedEntity}（エンティティ 1 体ぶんの追跡情報）への getter / setter。
 * ワールドの Tick ごと止められている間（時間停止など）、Eprotect が代わりに回した Tick の移動・状態の変化を
 * クライアントへ送るのに使う（送るのは本来 ChunkMap.tick の役目だが、それもワールドの Tick の中で止まる）。
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity automata_valkyries$getServerEntity();

    @Accessor("lastSectionPos")
    SectionPos automata_valkyries$getLastSectionPos();

    @Accessor("lastSectionPos")
    void automata_valkyries$setLastSectionPos(SectionPos pos);

    @Invoker("updatePlayers")
    void automata_valkyries$updatePlayers(List<ServerPlayer> players);
}
