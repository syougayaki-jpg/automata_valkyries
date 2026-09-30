package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code ChunkMap.entityMap}（エンティティ ID → 追跡情報）への getter。追跡から外されていないかの点検に使う */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<?> automata_valkyries$getEntityMap();
}
