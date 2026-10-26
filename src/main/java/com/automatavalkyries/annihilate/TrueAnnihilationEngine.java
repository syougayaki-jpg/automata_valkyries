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
        EprotectPlusLink.runWithCloakedStack(() -> AnnihilationExecutionAgent.runWithEventSuppression(() ->
                SpawnBanZone.runWithCloneSpawnBlock(target, () -> {
            // (0) ボスバー・HUD 追跡の事前破棄 ＆ 同期再召喚防止用 SpawnBanZone 先行登録
            AnnihilationExecutionAgent.clearBossEvents(target);
            SpawnBanZone.banNear(level, target, pos);

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

            // (2.5) 他 MOD の独自の生命維持（保護の切り替え・フラグ・代理の本体）を解除（※メモリ直接消去の前に呼び、controller 等への伝播を保証）
            ModProtectionBreaker.finishWithModOwnDeath(target);
            target.ejectPassengers();
            target.stopRiding();

            // (3) 【案4: メモリ直接ゼロクリア】Unsafe による未知チートオブジェクト・ボスの完全解体（※プレイヤー・保護対象除外）
            if (!(target instanceof Player) && !target.getClass().getName().contains("Player") && !(target instanceof IEprotected)) {
                AnnihilationExecutionAgent.clearBossEvents(target);
                UnsafeHelper.obliterateObjectMemory(target);
            }

            // (4) 【案2: 死亡進行・Tick停止強制突破 ＆ 案5: HPアンカー・属性完全破壊】（※プレイヤー除外）
            target.invulnerableTime = 0;
            if (target instanceof LivingEntity living && !(living instanceof Player) && !living.getClass().getName().contains("Player")) {
                // 案5: 最大HP属性の完全破壊（0.0D 化 ＋ cachedValue キャッシュ汚染）
                try {
                    var maxHealthAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        maxHealthAttr.setBaseValue(0.0D);
                        new ArrayList<>(maxHealthAttr.getModifiers()).forEach(maxHealthAttr::removeModifier);
                    }
                    IsolationDamageEngine.poisonMaxHealthAttributeCache(living, 0.0F);
                } catch (Throwable ignored) {}

                // 案5: DataWatcher (SynchedEntityData) の DATA_HEALTH_ID 凍結 ＋ LockedZeroDataItem 定数ロック
                try {
                    var healthId = LivingEntityAccessor.automata_valkyries$getDataHealthId();
                    if (healthId != null) {
                        living.getEntityData().set(healthId, 0.0F);
                        if (living.getEntityData() instanceof com.automatavalkyries.mixin.SynchedEntityDataAccessor synchedAcc) {
                            var items = synchedAcc.automata_valkyries$getItemsById();
                            if (items != null) {
                                items.put(healthId.getId(), new IsolationDamageEngine.LockedZeroDataItem(healthId));
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                // 案2: AI / Brain の完全凍結（Tick停止チート・NoAIバイパス）＋ トロイの木馬 Goal（自滅 AI）注入
                if (living instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(true);
                    try {
                        mob.goalSelector.getAvailableGoals().clear();
                        mob.targetSelector.getAvailableGoals().clear();
                        injectTrojanSelfDestructGoal(mob);
                    } catch (Throwable ignored) {}
                }
                try {
                    net.minecraft.world.entity.ai.Brain<?> brain = living.getBrain();
                    ((net.minecraft.world.entity.ai.Brain) brain).stopAll(level, living);
                    wipeBrainMemories(brain);
                } catch (Throwable ignored) {}

                // 案2: 死亡進行強制早送り（deathTime=20）
                living.deathTime = 20;

                living.setHealth(0.0F);
                ((LivingEntityAccessor) living).automata_valkyries$setDead(true);
                try {
                    EprotectPlusLink.runWithIsolatedTargetHooks(context.registryClasses(), () ->
                            living.die(player != null ? level.damageSources().playerAttack(player) : level.damageSources().genericKill()));
                } catch (Throwable ignored) {}
            }
            // ドロップ抽選（living.die）完了後に Entity.random をゼロ固定 RandomSource へ置換し、確率回避・乱数ワープ・分裂を封殺
            replaceEntityRandomWithZeroLock(target);
            EprotectPlusLink.runWithIsolatedTargetHooks(context.registryClasses(), target::kill);

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

            // (5) 対エージェント仕掛け: ターゲット MOD の監視スレッド無血冷凍 ＆ バニラ注入ヘルパー Map のブラックホール化
            try {
                String targetMod = com.automatavalkyries.protect.attack.CallerAttribution.modOf(target.getClass());
                if (com.automatavalkyries.protect.attack.CallerAttribution.isForeign(targetMod)) {
                    String pkg = target.getClass().getPackageName();
                    if (!pkg.isEmpty()) {
                        com.automatavalkyries.entity.akashic.AkashicAgentCanary.freezeForeignWatchdogThreads(pkg);
                        com.automatavalkyries.entity.akashic.AkashicAgentCanary.hollowOutVanillaInjectedHelpers(pkg);
                    }
                }
            } catch (Throwable ignored) {}

            // (5.5) 追跡偽装・区画・一覧からの完全切り離し
            AnnihilationExecutionAgent.spoofAndDisconnectTracking(level, target);

            // LevelCallback の無効化（削除イベント通知を阻止し、防衛トラップを発動させない）＋ AABB 奈落ゼロ幅孤立化
            EntityAccessor accessor = (EntityAccessor) target;
            accessor.automata_valkyries$setBoundingBoxField(new AABB(0.0D, -1.0E9D, 0.0D, 0.0D, -1.0E9D, 0.0D));
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
            EprotectPlusLink.runWithIsolatedTargetHooks(context.registryClasses(), () -> notifyRemoval(target));

            ModProtectionBreaker.afterErase(target, context);

            // (7.5) バニラ区画・一覧からの退去完了後、外部 MOD エンティティのオブジェクトヘッダ Mark Word (0L) の
            //       identityHashCode ビットをスクランブルし、未知の IdentityHashMap / HashSet 保護表から永久に孤立させる
            scrambleForeignMarkWordHash(target);

            // (8) 周囲の再出現禁止（復活対策: 同種MODボスの再出現をブロック）
            SpawnBanZone.banNear(level, target, pos);

            // (9) クライアント側への消滅パケット送信
            try {
                List<UUID> packetUuids = new ArrayList<>();
                packetUuids.add(targetUuid);
                for (UUID id : context.identities()) {
                    if (!packetUuids.contains(id)) {
                        packetUuids.add(id);
                    }
                }
                try {
                    Field mfField = UnsafeHelper.findField(target.getClass(), "metapotentFlashfur");
                    if (mfField != null) {
                        Object flashfurInstance = mfField.get(target);
                        if (flashfurInstance != null) {
                            Field idField = UnsafeHelper.findField(flashfurInstance.getClass(), "id");
                            if (idField != null) {
                                UUID flashfurUuid = (UUID) idField.get(flashfurInstance);
                                if (flashfurUuid != null) {
                                    packetUuids.add(flashfurUuid);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new AnnihilatedEntitiesPacket(packetUuids, List.of(targetId)));
            } catch (Throwable ignored) {}

            // Unknown save formats cannot establish target ownership. Never erase the whole directory.
        })));

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

    static void evictFromLevel(ServerLevel level, Entity target, int targetId, UUID targetUuid) {
        AnnihilationExecutionAgent.runWithHyperlinkSpecialRemove(() -> {
            AnnihilationExecutionAgent.evictHyperlinkShadowStructures(level, target, targetId, targetUuid);
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
                        AnnihilationExecutionAgent.evictFromEntitySections(storage, target);
                    }
                }
            } catch (Throwable ignored) {}

            try {
                Int2ObjectMap<?> entityMap = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap();
                entityMap.remove(targetId);
            } catch (Throwable ignored) {}
        });
    }

    private static void spawnVisualEffects(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.0D, pos.z, 2, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D, pos.z, 50, 0.8, 1.2, 0.8, 0.1);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0D, pos.z, 30, 0.5, 0.5, 0.5, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0D, pos.z, 60, 0.5, 1.0, 0.5, 0.15);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5F, 0.5F);
    }

    // ---- 深層無力化ヘルパー（トロイ AI Goal / Brain 記憶消去 / ゼロ固定乱数 / Mark Word ハッシュ孤立化） ----

    private static Field entityRandomField;
    private static Field brainMemoriesField;
    private static Field brainActiveActivitiesField;

    private static final net.minecraft.util.RandomSource ZERO_LOCKED_RANDOM = new net.minecraft.util.RandomSource() {
        @Override public net.minecraft.util.RandomSource fork() { return this; }
        @Override public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return net.minecraft.util.RandomSource.create(0L).forkPositional();
        }
        @Override public void setSeed(long seed) {}
        @Override public int nextInt() { return 0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public long nextLong() { return 0L; }
        @Override public boolean nextBoolean() { return false; }
        @Override public float nextFloat() { return 0.0F; }
        @Override public double nextDouble() { return 0.0D; }
        @Override public double nextGaussian() { return 0.0D; }
    };

    private static void injectTrojanSelfDestructGoal(net.minecraft.world.entity.Mob mob) {
        if (mob == null || mob instanceof IEprotected) return;
        mob.goalSelector.addGoal(Integer.MIN_VALUE, new net.minecraft.world.entity.ai.goal.Goal() {
            @Override
            public boolean canUse() {
                forceSelfDestruct();
                return true;
            }

            @Override
            public void start() {
                forceSelfDestruct();
            }

            @Override
            public void tick() {
                forceSelfDestruct();
            }

            private void forceSelfDestruct() {
                try {
                    mob.setHealth(0.0F);
                    ((LivingEntityAccessor) mob).automata_valkyries$setDead(true);
                    ((EntityAccessor) mob).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
                    mob.setRemoved(Entity.RemovalReason.KILLED);
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void wipeBrainMemories(net.minecraft.world.entity.ai.Brain<?> brain) {
        if (brain == null) return;
        try {
            if (brainMemoriesField == null) {
                brainMemoriesField = UnsafeHelper.findField(net.minecraft.world.entity.ai.Brain.class, "memories", "f_21843_");
            }
            if (brainMemoriesField != null && brainMemoriesField.get(brain) instanceof java.util.Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        var rawEntry = (java.util.Map.Entry) entry;
                        rawEntry.setValue(java.util.Optional.empty());
                    } catch (Throwable ignored) {}
                }
            }
            if (brainActiveActivitiesField == null) {
                brainActiveActivitiesField = UnsafeHelper.findField(net.minecraft.world.entity.ai.Brain.class, "activeActivities", "f_21847_");
            }
            if (brainActiveActivitiesField != null && brainActiveActivitiesField.get(brain) instanceof java.util.Set<?> set) {
                set.clear();
            }
        } catch (Throwable ignored) {}
    }

    private static void replaceEntityRandomWithZeroLock(Entity target) {
        if (target == null || target instanceof Player || target instanceof IEprotected) return;
        try {
            if (entityRandomField == null) {
                entityRandomField = UnsafeHelper.findField(Entity.class, "random", "f_19796_");
            }
            if (entityRandomField != null) {
                UnsafeHelper.setFieldValue(target, entityRandomField.getName(), ZERO_LOCKED_RANDOM);
            }
        } catch (Throwable ignored) {}
    }

    private static void scrambleForeignMarkWordHash(Entity target) {
        if (target == null || target instanceof Player || target instanceof IEprotected || !UnsafeHelper.isAvailable()) {
            return;
        }
        try {
            String mod = com.automatavalkyries.protect.attack.CallerAttribution.modOf(target.getClass());
            if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(mod)) return;
            sun.misc.Unsafe u = UnsafeHelper.getUnsafe();
            // 確定前の場合は先に identityHashCode を確定させてから Mark Word (0L) の hash フィールドを反転
            System.identityHashCode(target);
            long mark = u.getLongVolatile(target, 0L);
            // HotSpot 64-bit unlocked Mark Word (low bits == 001) のときだけ安全に hash ビット (bits 8..38) をスクランブル
            if ((mark & 0x07L) == 0x01L) {
                long scrambled = mark ^ (0x5A5A5A5AL << 8);
                u.putLongVolatile(target, 0L, scrambled);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 破棄通知（{@code discard} / {@code invalidateCaps} / {@code onRemovedFromWorld}）を 1 つずつ呼ぶ。
     * 他 MOD の破棄の処理が、先に解体して空にした自分のフィールドを読んで例外を投げても、サーバーごと落とさずに次へ進む
     * （2026-10-27、EEEAB's Mobs の {@code EEEABMobLibrary.remove} が null にされた cooldownManager を読んで落ちた）。
     * 削除理由はこの前に書いてあるので、例外で途中までしか通らなくても消えたことになる
     */
    public static void notifyRemoval(Entity target) {
        try {
            target.discard();
        } catch (Throwable failure) {
            ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            AutomataValkyries.LOGGER.warn("[AnnihilationAgent] {}.remove threw {} after its defenses were dismantled; marked removed directly",
                    target.getClass().getSimpleName(), failure.toString());
        }
        try {
            target.invalidateCaps();
        } catch (Throwable ignored) {}
        try {
            target.onRemovedFromWorld();
        } catch (Throwable ignored) {}
    }
}
