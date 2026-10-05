package com.automatavalkyries.mixin;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TickListEviction;
import com.automatavalkyries.protect.AgentPlayerProtection;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.EcaLastSwordHelper;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * リスポーンの最初（古い個体を消す前）に、死んでいる古い個体から The Last Sword の保護を外す。
 * <p>
 * The Last Sword の EntityMixin は「保護中」の個体の remove / setRemoved / discard を止める。vanilla の {@code PlayerList.respawn} は
 * Clone イベント（このMODの後始末）より前に古い個体を消すので、保護が残っていると古い個体が消えずに残り、新しい個体が同じ UUID で
 * 足されて二重になる（終焉執行のように死亡イベントが出ない死に方で起きた。二重の個体を ECA が 102400 へ飛ばし続け、区画の記録が壊れて
 * サーバーが重くなった。2026-10-02）。
 * </p>
 * <p>
 * また防護策（セーフガード）として、vanilla の {@code removePlayerImmediately} 呼出し後も古い個体がワールド一覧や区画に残っていた場合、
 * {@link TickListEviction} と同様に安全に Tick 一覧・エンティティ検索表・UUID 登録・区画から除去する。
 * </p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerRespawnReleaseMixin {

    @Shadow @Final private List<ServerPlayer> players;
    @Shadow @Final private Map<UUID, ServerPlayer> playersByUUID;

    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$releaseDeadPlayer(ServerPlayer player, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        if (AgentPlayerProtection.isProtected(player) && DirectRead.hasHealth(player) && !player.isDeadOrDying() && player.deathTime == 0) {
            String foreign = CallerAttribution.foreignClassMod(player);
            if (foreign != null) {
                AttackLog.refused(player, "respawn:forced", foreign);
                cir.setReturnValue(player);
                return;
            }
        }
        if (!DirectRead.hasHealth(player)) {
            // 準汎用: フラグ辞書で守り・復活・停止・リスポーンの妨害を下ろす（MOD ごとの個別対策より先に）
            com.automatavalkyries.protect.RespawnSanitizer.releaseDeadPlayer(player);
            EcaLastSwordHelper.releaseDeadPlayerForRespawn(player);
            EcaLastSwordHelper.cleanupPlayerRestraints(player);
            try {
                EntityAccessor accessor = (EntityAccessor) player;
                if (accessor.automata_valkyries$getRemovalReason() != null) {
                    accessor.automata_valkyries$setRemovalReason(null);
                }
            } catch (Throwable ignored) {}
        }
    }

    /** vanilla が古い個体を消した直後の点検および残留個体の安全な救護・消去 */
    @Inject(method = "respawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            shift = At.Shift.AFTER))
    private void automata_valkyries$afterRemove(ServerPlayer player, boolean keepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        automata_valkyries$safeguardEvictIfStillPresent(player);
    }

    @Unique
    private void automata_valkyries$safeguardEvictIfStillPresent(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        ServerLevel level = player.serverLevel();

        boolean stillInWorld = false;
        try {
            if (!player.isRemoved()) {
                stillInWorld = true;
            } else if (level != null) {
                @SuppressWarnings("unchecked")
                var manager = (EntityManagerAccessor<Entity>) (Object)
                        ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
                if (manager != null && manager.automata_valkyries$getKnownUuids().contains(uuid)) {
                    stillInWorld = true;
                } else if (level.getEntities().get(uuid) != null) {
                    stillInWorld = true;
                } else if (((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().contains(player)) {
                    stillInWorld = true;
                } else if (level.players().contains(player)) {
                    stillInWorld = true;
                }
            }
        } catch (Throwable ignored) {
            stillInWorld = true;
        }

        if (stillInWorld) {
            AutomataValkyries.LOGGER.warn("[respawn] Old player instance {} ({}) remained in world after removePlayerImmediately; executing safe eviction safeguard",
                    player.getScoreboardName(), uuid);
            automata_valkyries$safelyEvictOldPlayer(player);
        }
    }

    @Unique
    private void automata_valkyries$safelyEvictOldPlayer(ServerPlayer oldPlayer) {
        if (oldPlayer == null) return;
        UUID uuid = oldPlayer.getUUID();

        // 1. 古い個体の保護・拘束を再解除
        EcaLastSwordHelper.releaseDeadPlayerForRespawn(oldPlayer);
        EcaLastSwordHelper.cleanupPlayerRestraints(oldPlayer);

        try {
            oldPlayer.unRide();
            oldPlayer.stopRiding();
        } catch (Throwable ignored) {}

        // 2. Accessor 経由で RemovalReason と levelCallback を直書き
        try {
            EntityAccessor entityAccessor = (EntityAccessor) oldPlayer;
            entityAccessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
            entityAccessor.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
        } catch (Throwable ignored) {}

        // 3. 全ディメンションから古い UUID / 個体を安全に外す
        if (oldPlayer.getServer() != null) {
            for (ServerLevel level : oldPlayer.getServer().getAllLevels()) {
                automata_valkyries$evictFromLevel(level, uuid, oldPlayer);
            }
        } else if (oldPlayer.serverLevel() != null) {
            automata_valkyries$evictFromLevel(oldPlayer.serverLevel(), uuid, oldPlayer);
        }

        // 4. PlayerList の管理マップから古い個体を確実に除外
        try {
            if (this.players != null) {
                this.players.remove(oldPlayer);
                this.players.removeIf(p -> p == oldPlayer || (p != null && uuid.equals(p.getUUID())));
            }
            if (this.playersByUUID != null) {
                this.playersByUUID.remove(uuid);
            }
        } catch (Throwable ignored) {}
    }

    @Unique
    private static void automata_valkyries$evictFromLevel(ServerLevel level, UUID uuid, ServerPlayer oldPlayer) {
        if (level == null) return;
        try {
            // A. EntityTickList から TickListEviction で安全に除去
            var tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
            if (tickList != null) {
                TickListEviction.remove(tickList, oldPlayer);
                if (oldPlayer != null) {
                    TickListEviction.remove(tickList, oldPlayer.getId());
                }
            }

            // B. PersistentEntitySectionManager (knownUuids, visibleEntityStorage, sectionStorage) からの除去
            @SuppressWarnings("unchecked")
            var manager = (EntityManagerAccessor<Entity>) (Object)
                    ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
            if (manager != null) {
                Set<UUID> knownUuids = manager.automata_valkyries$getKnownUuids();
                if (knownUuids != null) {
                    knownUuids.remove(uuid);
                }

                var lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getVisibleEntityStorage();
                if (lookup != null) {
                    if (lookup.automata_valkyries$getByUuid() != null) {
                        lookup.automata_valkyries$getByUuid().remove(uuid);
                    }
                    if (lookup.automata_valkyries$getById() != null && oldPlayer != null) {
                        lookup.automata_valkyries$getById().remove(oldPlayer.getId());
                    }
                }

                var sectionStorage = manager.automata_valkyries$getSectionStorage();
                if (sectionStorage != null && oldPlayer != null) {
                    long secKey = SectionPos.asLong(oldPlayer.blockPosition());
                    var section = sectionStorage.getSection(secKey);
                    if (section != null) {
                        section.remove(oldPlayer);
                    }
                }
            }

            // B'. 追跡（ChunkMap の送信先）と ServerLevel のプレイヤー一覧から外す（vanilla の onTrackingEnd の該当部分）。
            // 古い個体の levelCallback が既に外れているとこれが走らず、古い個体が同じ ID のまま位置を送り続け、
            // クライアントの新しい個体が動けなくなった（2026-10-02 の [respawndiag]: 消去後も knownUuid/visible が残っていた）
            if (oldPlayer != null) {
                level.getChunkSource().removeEntity(oldPlayer);
                // 同じ個体が何度も足されていることがある（2026-10-02 は 38 個）ので、1 個ずつではなくすべて外す
                ((ServerLevelTickListAccessor) level).automata_valkyries$getPlayers().removeIf(p -> p == oldPlayer);
                level.updateSleepingPlayerList();
                if (manager != null) {
                    EntityLookup<Entity> storage = manager.automata_valkyries$getVisibleEntityStorage();
                    if (storage != null && storage.getEntity(uuid) == oldPlayer) storage.remove(oldPlayer);
                }
            }

            // C. 同じ UUID を持つ別インスタンスが残存している場合の消去
            var existing = level.getEntities().get(uuid);
            if (existing != null && existing != oldPlayer) {
                try {
                    existing.unRide();
                    existing.stopRiding();
                    ((EntityAccessor) existing).automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
                    ((EntityAccessor) existing).automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
                } catch (Throwable ignored) {}
                if (tickList != null) {
                    TickListEviction.remove(tickList, existing);
                }
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[respawn-evict] Failed to evict old player from level {}: {}",
                    level.dimension().location(), t.toString());
        }
    }
}
