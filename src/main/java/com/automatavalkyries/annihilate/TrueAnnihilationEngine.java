package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.SpecificCountermeasures;
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

import com.automatavalkyries.util.UnsafeHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 真抹殺執行エンジン（True Annihilation Engine）。
 * <p>
 * MOD 側の対策と消去処理を実行し、対象がワールドの UUID/ID 検索から退去したことを確認する。
 * Eprotect+ 接続時には協調用の実行範囲と診断を利用する。将来の復活や外部保存までの消去は保証しない。
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
        if (target == null || target.level() != level || !level.getServer().isSameThread()
                || target instanceof Player || (target instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget())) {
            return false;
        }

        if (SpecificCountermeasures.enabled() && MadnessAnnihilation.isMadness(target)) return MadnessAnnihilation.annihilate(level, target);

        UUID targetUuid = MadnessAnnihilation.uuid(target);
        String targetName = target.getDisplayName().getString();
        int targetId = MadnessAnnihilation.id(target);
        Vec3 pos = target.position();

        // 0. ボスバー・HUD 追跡の強制事前破棄（侵略者のモノリス等のボスバー消え残りを防止するため、
        //    メモリ消去 UnsafeHelper.obliterateObjectMemory で bossEvent 参照が null 化される前に全プレイヤーから確実に切り離す）
        AnnihilationExecutionAgent.clearBossEvents(target);

        final boolean[] defended = new boolean[1];

        // 2. AnnihilationExecutionAgent のブラックリスト登録 & スポーン拒否 & ボス固有解体
        EprotectPlusLink.runWithCloakedStack(() -> AnnihilationExecutionAgent.runWithEventSuppression(() -> {
            // (0) ボスバー・HUD 追跡の事前破棄
            AnnihilationExecutionAgent.clearBossEvents(target);

            // (1) ブラックリスト登録
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

            // Capture target-owned references before any specific or memory cleanup.
            ModProtectionBreaker.Context context = ModProtectionBreaker.breakProtection(level, target);

            // (2) Witherzilla / Omni-Mobs 等のボス固有無力化（Unsafe 解体 & メモリ直接消去）
            AnnihilationExecutionAgent.neutralizeWitherzilla(level, target);
            AnnihilationExecutionAgent.neutralizeFlashfur(level, target);

            // (2.5) 【案4: メモリ直接ゼロクリア】Unsafe による未知チートオブジェクト・ボスの完全解体（※プレイヤー・保護対象除外）
            if (!(target instanceof Player) && !target.getClass().getName().contains("Player") && !(target instanceof IEprotected)) {
                AnnihilationExecutionAgent.clearBossEvents(target);
                UnsafeHelper.obliterateObjectMemory(target);
            }

            // (3) 他 MOD の独自の生命維持（保護の切り替え・フラグ・代理の本体）を解除
            ModProtectionBreaker.finishWithModOwnDeath(target);
            target.ejectPassengers();
            target.stopRiding();

            // (4) 【案2: 死亡進行・Tick停止強制突破 ＆ 案5: HPアンカー・属性完全破壊】（※プレイヤー除外）
            target.invulnerableTime = 0;
            if (target instanceof LivingEntity living && !(living instanceof Player) && !living.getClass().getName().contains("Player")) {
                // 案5: 最大HP属性の完全破壊（0.0D 化）
                try {
                    var maxHealthAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        maxHealthAttr.setBaseValue(0.0D);
                        new ArrayList<>(maxHealthAttr.getModifiers()).forEach(maxHealthAttr::removeModifier);
                    }
                } catch (Throwable ignored) {}

                // 案5: DataWatcher (SynchedEntityData) の DATA_HEALTH_ID 凍結
                try {
                    var healthId = LivingEntityAccessor.automata_valkyries$getDataHealthId();
                    if (healthId != null) {
                        living.getEntityData().set(healthId, 0.0F);
                    }
                } catch (Throwable ignored) {}

                // 案2: AI / Brain の完全凍結（Tick停止チート・NoAIバイパス）
                if (living instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(true);
                    try {
                        mob.goalSelector.getAvailableGoals().clear();
                        mob.targetSelector.getAvailableGoals().clear();
                    } catch (Throwable ignored) {}
                }
                try {
                    ((net.minecraft.world.entity.ai.Brain) living.getBrain()).stopAll(level, living);
                } catch (Throwable ignored) {}

                // 案2: 死亡進行強制早送り（deathTime=20）
                living.deathTime = 20;

                living.setHealth(0.0F);
                ((LivingEntityAccessor) living).automata_valkyries$setDead(true);
                try {
                    living.die(level.damageSources().playerAttack(player));
                } catch (Throwable ignored) {}
            }
            target.kill();

            // Eprotect+ 防御層による遮断: 不正な消去理由直書き・区画排除を拒絶（Eprotect+ の守護が勝つ）
            if (target instanceof IEprotected p && p.getEprotect() != null && p.getEprotect().isProtectionActive()) {
                AutomataValkyries.LOGGER.info("[TrueAnnihilation] Target {} is defended by Eprotect+; eviction and removal spoofing blocked by Eprotect+", targetName);
                try {
                    java.lang.reflect.Field fieldUuids = AnnihilationExecutionAgent.class.getDeclaredField("ANNIHILATED_UUIDS");
                    fieldUuids.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Set<UUID> uuids = (java.util.Set<UUID>) fieldUuids.get(null);
                    if (uuids != null) uuids.remove(targetUuid);

                    java.lang.reflect.Field fieldInst = AnnihilationExecutionAgent.class.getDeclaredField("ANNIHILATED_INSTANCES");
                    fieldInst.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Set<Entity> instances = (java.util.Set<Entity>) fieldInst.get(null);
                    if (instances != null) instances.remove(target);
                } catch (Throwable ignored) {}
                defended[0] = true;
                return;
            }

            // (5) 追跡偽装・区画・一覧からの完全切り離し
            AnnihilationExecutionAgent.spoofAndDisconnectTracking(level, target);

            // LevelCallback の無効化（削除イベント通知を阻止し、防衛トラップを発動させない）
            EntityAccessor accessor = (EntityAccessor) target;
            accessor.automata_valkyries$setLevelCallback(new EntityInLevelCallback() {
                @Override public void onMove() {}
                @Override public void onRemove(Entity.RemovalReason reason) {}
            });

            // 削除理由を強制設定
            accessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            target.setRemoved(Entity.RemovalReason.KILLED);

            // (6) ワールド・チャンク・検索表・Tick リストからの物理的強制排除
            evictFromLevel(level, target, targetId, targetUuid);

            // (7) 破棄通知・Capability 無効化
            target.discard();
            target.invalidateCaps();
            target.onRemovedFromWorld();

            ModProtectionBreaker.afterErase(target, context);

            // (8) 周囲の再出現禁止（復活対策: 同種MODボスの再出現をブロック）
            SpawnBanZone.banNear(level, target, pos);

            // (9) クライアント側への消滅パケット送信
            try {
                List<UUID> packetUuids = new ArrayList<>();
                packetUuids.add(targetUuid);
                try {
                    Field mfField = UnsafeHelper.findField(target.getClass(), "metapotentFlashfur");
                    if (mfField != null) {
                        Object flashfurInstance = mfField.get(target);
                        if (flashfurInstance != null) {
                            Field idField = flashfurInstance.getClass().getDeclaredField("id");
                            idField.setAccessible(true);
                            UUID flashfurUuid = (UUID) idField.get(flashfurInstance);
                            if (flashfurUuid != null) {
                                packetUuids.add(flashfurUuid);
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new AnnihilatedEntitiesPacket(packetUuids, List.of(targetId)));
            } catch (Throwable ignored) {}

            // Unknown save formats cannot establish target ownership. Never erase the whole directory.
        }));

        if (defended[0]) {
            return false;
        }

        if (!com.automatavalkyries.protect.DirectRead.isRemoved(target)
                || level.getEntity(targetUuid) == target || level.getEntity(targetId) == target) {
            AutomataValkyries.LOGGER.warn("[TrueAnnihilation] Removal unconfirmed: {} ({})", targetName, targetUuid);
            return false;
        }

        // 演出エフェクト
        spawnVisualEffects(level, pos);

        EprotectPlusLink.recordAnnihilation(targetName, "TrueAnnihilationEngine", "confirmed_removal", targetUuid.toString());
        AutomataValkyries.LOGGER.info("[TrueAnnihilation] Removal confirmed for {} ({})", targetName, targetUuid);
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
                !(e instanceof Player) && !(e instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) && e.isAlive());

        int count = SpecificCountermeasures.enabled() ? MadnessAnnihilation.sweep(level) : 0;
        for (Entity target : targets) {
            if (annihilateEntity(level, player, target)) {
                count++;
            }
        }

        // 範囲抹殺時の一括クリーンアップ（Metapotent Flashfur 本体のメモリ・ディスク・EventBus）
        AnnihilationExecutionAgent.wipeFlashfurLevelMemory();
        AnnihilationExecutionAgent.wipeFlashfurDiskSaves(level);
        AnnihilationExecutionAgent.wipeOmniMobsInternalLists();
        AnnihilationExecutionAgent.sanitizeEventBus();

        // 天候の正常化
        level.getLevelData().setRaining(false);
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);

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
                // 他 MOD に remove を拒否されても外す（回されている最中のマップは直接いじらない。TickListEviction）
                TickListEviction.remove(tickList, target);
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
