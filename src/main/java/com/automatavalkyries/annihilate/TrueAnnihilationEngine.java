package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.EntityTickListAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 真抹殺執行エンジン（True Annihilation Engine）。
 * <p>
 * Eprotect+ Java エージェントと連携し、Witherzilla の生命維持フラグ（witherzillaLife）や
 * Metapotent Flashfur の StackChecker（スタック検閲防壁）を含む、あらゆるチートモブの
 * JVM レベルの防御・偽装・不死機構を突破・解体して不可逆的に抹殺する。
 * </p>
 */
public final class TrueAnnihilationEngine {

    private TrueAnnihilationEngine() {}

    /**
     * 単体真抹殺の執行。
     *
     * @param level  サーバーワールド
     * @param player 発動者（プレイヤー）
     * @param target 抹殺対象エンティティ
     * @return 抹殺が完了したか
     */
    public static boolean annihilateEntity(ServerLevel level, Player player, Entity target) {
        if (target == null || target instanceof Player || target instanceof IEprotected) {
            return false;
        }

        UUID targetUuid = target.getUUID();
        String targetName = target.getDisplayName().getString();
        int targetId = target.getId();
        Vec3 pos = target.position();

        // 1. Eprotect+ Java エージェント（または内蔵特権空間）による JVM レベルの不死剥奪
        EprotectPlusLink.annihilateTargetWithAgent(target);

        // 2. AnnihilationExecutionAgent のブラックリスト登録 & スポーン拒否
        AnnihilationExecutionAgent.runWithEventSuppression(() -> {
            // LevelCallback の無効化（削除イベント通知を阻止し、防衛トラップを発動させない）
            EntityAccessor accessor = (EntityAccessor) target;
            accessor.automata_valkyries$setLevelCallback(new EntityInLevelCallback() {
                @Override public void onMove() {}
                @Override public void onRemove(Entity.RemovalReason reason) {}
            });

            // 削除理由を強制設定
            accessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            target.setRemoved(Entity.RemovalReason.KILLED);

            // 3. ワールド・チャンク・検索表・Tick リストからの物理的強制排除
            evictFromLevel(level, target, targetId, targetUuid);

            // 4. 周囲の再出現禁止（復活対策: 他MODボスの再出現をブロック）
            SpawnBanZone.banNear(level, target, pos);

            // 5. 抹殺リストへの登録（AnnihilationExecutionAgent）
            try {
                java.lang.reflect.Field fieldUuids = AnnihilationExecutionAgent.class.getDeclaredField("ANNIHILATED_UUIDS");
                fieldUuids.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Set<UUID> uuids = (java.util.Set<UUID>) fieldUuids.get(null);
                if (uuids != null) uuids.add(targetUuid);

                java.lang.reflect.Field fieldInst = AnnihilationExecutionAgent.class.getDeclaredField("ANNIHILATED_INSTANCES");
                fieldInst.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Set<Entity> instances = (java.util.Set<Entity>) fieldInst.get(null);
                if (instances != null) instances.add(target);
            } catch (Throwable ignored) {}

            // 6. クライアント側への消滅パケット送信
            try {
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new AnnihilatedEntitiesPacket(List.of(targetUuid), List.of(targetId)));
            } catch (Throwable ignored) {}
        });

        // 7. 演出エフェクト
        spawnVisualEffects(level, pos);

        AutomataValkyries.LOGGER.info("[TrueAnnihilation] Eradicated {} ({}) with Eprotect+ Java Agent", targetName, targetUuid);
        return true;
    }

    /**
     * 範囲真抹殺の執行。
     *
     * @param level  サーバーワールド
     * @param player 発動者
     * @param radius 半径（ブロック）
     * @return 抹殺したエンティティ数
     */
    public static int annihilateAllInArea(ServerLevel level, Player player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> targets = level.getEntities(player, box, e ->
                !(e instanceof Player) && !(e instanceof IEprotected) && e.isAlive());

        int count = 0;
        for (Entity target : targets) {
            if (annihilateEntity(level, player, target)) {
                count++;
            }
        }

        if (count > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5F, 0.7F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 0.5F);
        }

        return count;
    }

    private static void evictFromLevel(ServerLevel level, Entity target, int targetId, UUID targetUuid) {
        try {
            level.getChunkSource().removeEntity(target);
        } catch (Throwable ignored) {}

        try {
            ServerLevelTickListAccessor tickAccessor = (ServerLevelTickListAccessor) level;
            EntityTickList tickList = tickAccessor.automata_valkyries$getEntityTickList();
            if (tickList != null) {
                tickList.remove(target);
                ((EntityTickListAccessor) tickList).automata_valkyries$getIterated().remove(target);
            }
        } catch (Throwable ignored) {}

        try {
            ServerLevelTickListAccessor tickAccessor = (ServerLevelTickListAccessor) level;
            @SuppressWarnings("unchecked")
            EntityManagerAccessor<Entity> manager =
                    (EntityManagerAccessor<Entity>) (Object) tickAccessor.automata_valkyries$getEntityManager();
            if (manager != null) {
                manager.automata_valkyries$getKnownUuids().remove(targetUuid);

                EntityLookup<Entity> lookup = manager.automata_valkyries$getVisibleEntityStorage();
                if (lookup != null) {
                    lookup.remove(target);
                    EntityLookupAccessor<Entity> lookupAcc = (EntityLookupAccessor<Entity>) (Object) lookup;
                    lookupAcc.automata_valkyries$getById().remove(targetId);
                    lookupAcc.automata_valkyries$getByUuid().remove(targetUuid);
                }

                EntitySectionStorage<Entity> storage = manager.automata_valkyries$getSectionStorage();
                if (storage != null) {
                    EntitySection<Entity> section = storage.getSection(SectionPos.asLong(target.blockPosition()));
                    if (section != null) section.remove(target);
                }
            }
        } catch (Throwable ignored) {}

        try {
            Int2ObjectMap<?> entityMap = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap();
            entityMap.remove(targetId);
        } catch (Throwable ignored) {}
    }

    private static void spawnVisualEffects(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.0D, pos.z, 2, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D, pos.z, 50, 0.8, 1.2, 0.8, 0.1);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0D, pos.z, 30, 0.5, 0.5, 0.5, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0D, pos.z, 60, 0.5, 1.0, 0.5, 0.15);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.5F);
    }
}
