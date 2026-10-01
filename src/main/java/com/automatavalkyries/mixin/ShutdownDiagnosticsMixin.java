package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.ShutdownDiagnostics;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;

/**
 * 終了時に固まる原因調べ（記録のみ。処理は変えない）。{@link ShutdownDiagnostics} を参照。
 */
public final class ShutdownDiagnosticsMixin {

    private ShutdownDiagnosticsMixin() {}

    /** 終了の待ち（ChunkMap.hasWork が true のあいだ回り続ける）の中身 */
    @Mixin(ChunkMap.class)
    public abstract static class Chunks {
        @Shadow @Final ServerLevel level;
        @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;
        @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
        @Shadow @Final LongSet toDrop;
        @Shadow @Final private Queue<Runnable> unloadQueue;

        @Inject(method = "hasWork", at = @At("RETURN"))
        private void automata_valkyries$reportWork(CallbackInfoReturnable<Boolean> cir) {
            if (!cir.getReturnValueZ() || !ShutdownDiagnostics.isStopping()) return;
            ShutdownDiagnostics.reportPendingWork(level, updatingChunkMap.size(), pendingUnloads.size(), toDrop.size(),
                    unloadQueue.size(), ((DistanceManagerAccessor) ((ChunkCacheAccessor) level.getChunkSource()).automata_valkyries$getDistanceManager()).automata_valkyries$getTickets());
        }
    }

    /** 終了中に足された札（チャンクを読み込ませておく印）と、足した呼び出し元 */
    @Mixin(DistanceManager.class)
    public abstract static class Tickets {
        @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"))
        private void automata_valkyries$reportTicket(long chunk, Ticket<?> ticket, CallbackInfo ci) {
            if (ShutdownDiagnostics.isStopping()) ShutdownDiagnostics.reportTicket(chunk, ticket);
        }
    }

    /** {@code ServerChunkCache.distanceManager}（private）への getter */
    @Mixin(net.minecraft.server.level.ServerChunkCache.class)
    public interface ChunkCacheAccessor {
        @org.spongepowered.asm.mixin.gen.Accessor("distanceManager")
        DistanceManager automata_valkyries$getDistanceManager();
    }

    /** {@code DistanceManager.tickets}（package-private）への getter */
    @Mixin(DistanceManager.class)
    public interface DistanceManagerAccessor {
        @org.spongepowered.asm.mixin.gen.Accessor("tickets")
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> automata_valkyries$getTickets();
    }
}
