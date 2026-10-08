package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import org.jetbrains.annotations.Nullable;

/** {@link Eprotect} のチャンクロック: 保護対象のいるチャンクを読み込んだままにする */
final class ForcedChunk {

    private final LivingEntity owner;
    @Nullable
    private ServerLevel level;
    @Nullable
    private ChunkPos chunk;

    ForcedChunk(LivingEntity owner) {
        this.owner = owner;
    }

    /** 今いるチャンクへ付け替える（終了処理中・壊れた座標では触らない） */
    void update() {
        if (!(owner.level() instanceof ServerLevel current)) return;
        if (!current.getServer().isRunning()) return; // 終了処理中にチャンクを同期ロードすると固まる
        if (!Eprotect.isSafePosition(owner, owner.getX(), owner.getY(), owner.getZ())) return; // 壊れた座標のチャンクを強制ロードしない
        ChunkPos pos = owner.chunkPosition();
        if (current == level && pos.equals(chunk)) return;

        release();
        ForgeChunkManager.forceChunk(current, AutomataValkyries.MODID, owner, pos.x, pos.z, true, true);
        level = current;
        chunk = pos;
    }

    void release() {
        if (level != null && chunk != null) {
            ForgeChunkManager.forceChunk(level, AutomataValkyries.MODID, owner, chunk.x, chunk.z, false, true);
        }
        level = null;
        chunk = null;
    }
}
