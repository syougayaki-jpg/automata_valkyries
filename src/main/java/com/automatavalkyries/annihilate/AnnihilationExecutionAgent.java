package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.AgentPlayerProtection;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.util.UnsafeHelper;
import com.automatavalkyries.weapon.DeathLootFallback;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「イベント操作」及び「追跡偽装」に特化した攻撃的抹消執行エージェント。
 * <p>
 * Witherzilla（生命維持フラグ、EventHandle、ChunkMap 追跡、Mixin 介入）や
 * Metapotent Flashfur（プロキシ構造、StackChecker 防壁、ProtectedArrayList、ディスク永続化、EventBus 乗っ取り）
 * などのチートボスを完全・不可逆的に抹消する。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class AnnihilationExecutionAgent {

    private static final AnnihilationExecutionAgent INSTANCE = new AnnihilationExecutionAgent();

    /** 抹消されたエンティティの UUID ブラックリスト */
    private static final Set<UUID> ANNIHILATED_UUIDS = ConcurrentHashMap.newKeySet();
    /** 抹消されたエンティティのインスタンス（同一インスタンスの再登録拒否） */
    private static final Set<Entity> ANNIHILATED_INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    /** イベント操作中フラグ（ThreadLocal で再帰・イベント通知を制御） */
    private static final ThreadLocal<Boolean> THREAD_ANNIHILATING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AnnihilationExecutionAgent() {}

    public static AnnihilationExecutionAgent getInstance() {
        return INSTANCE;
    }

    /** エンティティが抹消対象として登録されているか */
    public static boolean isAnnihilated(Entity entity) {
        if (entity == null) return false;
        return ANNIHILATED_INSTANCES.contains(entity) || ANNIHILATED_UUIDS.contains(entity.getUUID());
    }

    /** UUID が抹消対象として登録されているか */
    public static boolean isAnnihilated(UUID uuid) {
        if (uuid == null) return false;
        return ANNIHILATED_UUIDS.contains(uuid);
    }

    public static boolean isThreadAnnihilating() {
        return Boolean.TRUE.equals(THREAD_ANNIHILATING.get());
    }

    // =========================================================================
    // 1. イベント操作 (Event Manipulation)
    // =========================================================================

    /**
     * 抹消処理の実行ブロック。実行中はイベント操作を行い、ターゲット MOD の防御フックを遮断する。
     */
    public static void runWithEventSuppression(Runnable action) {
        boolean prev = THREAD_ANNIHILATING.get();
        THREAD_ANNIHILATING.set(true);
        try {
            action.run();
        } finally {
            THREAD_ANNIHILATING.set(prev);
        }
    }

    /**
     * 抹消済みエンティティが再スポーンまたは再参加しようとした場合、最優先（HIGHEST）で拒否・破棄する。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // サーバー側だけで判定する。シングルプレイでは static の一覧をクライアントの描画スレッドと共有してしまい、
        // WeakHashMap（スレッド非安全）を 2 スレッドから触ることになる。消去はサーバーからの通知でクライアントにも届く
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player player && isAnnihilated(entity)) {
            // プレイヤーはリスポーンで同じ UUID の新しい個体として入り直すので、拒否すると入れなくなる（ワールドに居ないまま
            // 操作だけ残り、モブも出せなくなる）。プレイヤーの入り直しは常に通し、一覧から外す
            ANNIHILATED_UUIDS.remove(player.getUUID());
            ANNIHILATED_INSTANCES.remove(player);
            return;
        }
        if (entity != null && isAnnihilated(entity)) {
            if (entity instanceof IEprotected p && p.getEprotect() != null && p.getEprotect().isProtectionActive()) {
                ANNIHILATED_INSTANCES.remove(entity);
                ANNIHILATED_UUIDS.remove(entity.getUUID());
                return;
            }
            event.setCanceled(true);
            entity.discard();
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Blocked and canceled re-join of annihilated entity: {} ({})",
                    entity.getName().getString(), entity.getUUID());
        } else if (entity != null && !(entity instanceof Player) && !(entity instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) && SpawnBanZone.shouldRefuse(entity)) {
            // 消した直後に、同じ種類の個体が別の UUID で作り直された（復活）
            event.setCanceled(true);
            entity.discard();
        }
    }

    /**
     * 抹消処理中の被ダメージキャンセルを無効化する。
     * 優先度の高い購読者から先に呼ばれるので、他 MOD のキャンセルを覆すには最後（LOWEST）に回る必要がある。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || (entity instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) || AgentPlayerProtection.isProtected(entity)) {
            return;
        }
        if (isThreadAnnihilating() || isAnnihilated(entity)) {
            event.setCanceled(false);
        }
    }

    /**
     * 抹消処理中の死亡キャンセルを無効化する（LOWEST の理由は {@link #onLivingHurt} と同じ）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || (entity instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) || AgentPlayerProtection.isProtected(entity)) {
            return;
        }
        if (isThreadAnnihilating() || isAnnihilated(entity)) {
            event.setCanceled(false);
        }
    }

    /**
     * Omni-Mobs に乗っ取られた Forge EventBus を元に戻す。
     * <p>
     * {@code MetapotentFlashfurEventBus} は元のバスへの参照を持たず、生成時に EventBus のフィールド（購読者一覧）を丸写しするだけ。
     * 戻すのは Omni-Mobs 自身の {@code MetapotentFlashfurLevel.updateEventBus()} で、Metapotent Flashfur の一覧が空なら
     * 購読者を写し戻した通常の EventBus を {@code MinecraftForge.EVENT_BUS} に据え直す。
     * そのため {@link #wipeFlashfurLevelMemory()} で一覧を空にした後に呼ぶこと。
     * </p>
     */
    public static void sanitizeEventBus() {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        IEventBus bus = MinecraftForge.EVENT_BUS;
        if (bus == null || !bus.getClass().getName().endsWith("MetapotentFlashfurEventBus")) return;
        try {
            Class<?> levelClass = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            levelClass.getMethod("updateEventBus").invoke(null);
            if (MinecraftForge.EVENT_BUS != bus) {
                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Restored MinecraftForge.EVENT_BUS from the Omni-Mobs bus");
            } else {
                AutomataValkyries.LOGGER.warn("[AnnihilationAgent] Omni-Mobs kept its EventBus (Metapotent Flashfur list is not empty)");
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[AnnihilationAgent] Could not restore the EventBus replaced by Omni-Mobs", t);
        }
    }

    // =========================================================================
    // 2. 追跡偽装 (Tracking Spoofing)
    // =========================================================================

    /**
     * エンティティをワールドの全追跡構造・区画・検索表・Tick一覧から完全切断し、追跡偽装を行う。
     */
    public static void spoofAndDisconnectTracking(ServerLevel level, Entity entity) {
        if (entity == null) return;
        if (entity instanceof IEprotected p && p.getEprotect() != null && p.getEprotect().isProtectionActive()) {
            return;
        }

        try {
            // (1) ChunkMap の追跡解除 ＆ クライアントへの追跡切断パケット送信
            // 正規の追跡解除（見ているプレイヤーの一覧の後始末を含む）を先に行い、残っていれば直接外す
            Int2ObjectMap<?> entityMap = ((ChunkMapAccessor) level.getChunkSource().chunkMap).automata_valkyries$getEntityMap();
            level.getChunkSource().removeEntity(entity);
            entityMap.remove(entity.getId());

            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(entity.getId());
            for (ServerPlayer player : level.players()) {
                player.connection.send(removePacket);
            }

            // マルチパート（処刑竜等の部位エンティティ）の追跡切断
            // 部位は ChunkMap で追跡されない（broadcastAndSend では届かない）ので、全プレイヤーへ直接送る
            if (entity.isMultipartEntity()) {
                Int2ObjectMap<PartEntity<?>> dragonParts = ((ServerLevelTickListAccessor) level).automata_valkyries$getDragonParts();
                for (PartEntity<?> part : entity.getParts()) {
                    if (part != null) {
                        entityMap.remove(part.getId());
                        dragonParts.remove(part.getId());
                        ClientboundRemoveEntitiesPacket partPacket = new ClientboundRemoveEntitiesPacket(part.getId());
                        for (ServerPlayer player : level.players()) {
                            player.connection.send(partPacket);
                        }
                    }
                }
            }

            // (2) PersistentEntitySectionManager の内部マップから直消去（MixinEntityLookup のキャンセルをバイパス）
            @SuppressWarnings("unchecked")
            EntityManagerAccessor<Entity> manager = (EntityManagerAccessor<Entity>) (Object)
                    ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();

            EntityLookup<Entity> lookup = manager.automata_valkyries$getVisibleEntityStorage();
            @SuppressWarnings("unchecked")
            EntityLookupAccessor<Entity> lookupAccessor = (EntityLookupAccessor<Entity>) lookup;
            // 正規の remove も通す: Hyperlink（Fumetsu）は EntityLookup に影の引き表（byId2 / byUuid2）を持ち、
            // 表だけ直接消しても getEntity が影から引き直してしまう。正規の remove なら影の側も消える
            lookup.remove(entity);
            lookupAccessor.automata_valkyries$getById().remove(entity.getId());
            lookupAccessor.automata_valkyries$getByUuid().remove(entity.getUUID());

            // (3) Tick 対象一覧から直消去（MixinEntityTickList の拒否をバイパス）
            EntityTickList tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
            TickListEviction.remove(tickList, entity); // 回されている最中でも反復を壊さない
            tickList.remove(entity);

            // (4) 区画ストレージからの直接削除
            EntitySectionStorage<Entity> sectionStorage = manager.automata_valkyries$getSectionStorage();
            long sectionKey = SectionPos.asLong(entity.blockPosition());
            EntitySection<Entity> section = sectionStorage.getSection(sectionKey);
            if (section != null) {
                section.remove(entity);
            }

            // (5) UUID 一覧・スコアボード・コールバックの無効化
            manager.automata_valkyries$getKnownUuids().remove(entity.getUUID());
            level.getScoreboard().entityRemoved(entity);

            EntityAccessor entityAccessor = (EntityAccessor) entity;
            entityAccessor.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
            entityAccessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            entityAccessor.automata_valkyries$setAddedToWorld(false);

            // (6) 空間座標偽装: 位置を NaN / 無限遠に隔離し、AABB 検索・距離判定から消失させる
            entity.setPos(Double.NaN, Double.NaN, Double.NaN);
            entity.setDeltaMovement(Vec3.ZERO);

            // (7) ボスバー・HUD 追跡の強制破棄
            clearBossEvents(entity);

        } catch (Throwable t) {
            AutomataValkyries.LOGGER.error("[AnnihilationAgent] Error during tracking spoofing for {}", entity, t);
        }
    }

    /**
     * リフレクションでエンティティ（およびコントローラー・本体）の ServerBossEvent を検索し、全プレイヤーから非表示にする。
     * また、エンティティの stopSeenByPlayer(ServerPlayer) を全プレイヤーに対して明示的に呼び出す。
     */
    public static void clearBossEvents(Object target) {
        if (target == null) return;

        // 1. もし Entity であれば、stopSeenByPlayer を呼んでバニラ／MOD 固有の視界・ボスバー後始末を走らせる
        if (target instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            try {
                for (ServerPlayer player : level.players()) {
                    try {
                        entity.stopSeenByPlayer(player);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        // 2. クラス階層を走査し、ServerBossEvent / BossEvent フィールドおよびゲッターを検索して全プレイヤーから削除・非表示化
        Class<?> clz = target.getClass();
        while (clz != null && clz != Object.class) {
            for (Field f : clz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(target);
                    if (val instanceof ServerBossEvent bossEvent) {
                        bossEvent.removeAllPlayers();
                        bossEvent.setVisible(false);
                    }
                } catch (Throwable ignored) {}
            }

            // メソッド（getBossEvent, getBossBar 等引数なし）も検査
            for (Method m : clz.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() == 0 && (ServerBossEvent.class.isAssignableFrom(m.getReturnType())
                        || m.getName().toLowerCase().contains("bossbar")
                        || m.getName().toLowerCase().contains("bossevent"))) {
                    try {
                        m.setAccessible(true);
                        Object val = m.invoke(target);
                        if (val instanceof ServerBossEvent bossEvent) {
                            bossEvent.removeAllPlayers();
                            bossEvent.setVisible(false);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            clz = clz.getSuperclass();
        }
    }

    // =========================================================================
    // 3. ボス固有無力化ルーチン (Target-Specific Neutralization)
    // =========================================================================

    /**
     * Witherzilla MOD のボス（WitherzillaEntity, ExecutionDragonEntity, ChaosWitherEntity）の無力化。
     */
    public static void neutralizeWitherzilla(ServerLevel level, Entity entity) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        String className = entity.getClass().getName();
        if (!className.startsWith("com.wzz.witherzilla")) return;

        try {
            // isDead フィールドの直書き（getWitherzillaLife() を false に落とす）
            UnsafeHelper.setFieldValue(entity, "isDead", true);

            // HP・SynchedEntityData のゼロ化
            if (entity instanceof LivingEntity living) {
                living.setHealth(0.0F);
                ((LivingEntityAccessor) living).automata_valkyries$setDead(true);
                // setHealth を上書きして HP を守る実装に備え、同期データを直接 0 にする
                // （名前でのリフレクションは本番環境の難読化名で外れるので、Mixin の Accessor で取る）
                living.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
                living.die(level.damageSources().genericKill());
            }

            // Witherzilla の EventHandle.livingEntities から削除（画面 HUD バーの消去）
            try {
                Class<?> eventHandleClass = Class.forName("com.wzz.witherzilla.event.EventHandle");
                Field livingListField = eventHandleClass.getDeclaredField("livingEntities");
                livingListField.setAccessible(true);
                List<?> list = (List<?>) livingListField.get(null);
                if (list != null) {
                    list.remove(entity);
                }
            } catch (Throwable ignored) {}

            // 天候の復元（豪雨・雷雨の解除）
            level.getLevelData().setRaining(false);
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);

            // 処刑竜（ExecutionDragonEntity）の部位削除
            if (entity.isMultipartEntity()) {
                for (PartEntity<?> part : entity.getParts()) {
                    if (part != null) {
                        part.discard();
                    }
                }
            }

            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Neutralized Witherzilla boss: {}", entity.getName().getString());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[AnnihilationAgent] Failed during Witherzilla boss neutralization", t);
        }
    }

    private static final ThreadLocal<Boolean> CLEANING_FLASHFUR = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isCleaningFlashfur() {
        return Boolean.TRUE.equals(CLEANING_FLASHFUR.get());
    }

    public static void runWithFlashfurPrivilege(Runnable action) {
        CLEANING_FLASHFUR.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            CLEANING_FLASHFUR.set(Boolean.FALSE);
        }
    }

    /**
     * Metapotent Flashfur（Omni-Mobs）の無力化。
     * プロキシエンティティの向こうにある MetapotentFlashfurLevel を Unsafe で直接クリアし、ディスク保存も消去する。
     */
    public static void neutralizeFlashfur(ServerLevel level, Entity entity) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        String className = entity.getClass().getName();
        if (!className.contains("flashfur.omnimobs")) return;

        try {
            // (1) プロキシエンティティから MetapotentFlashfur インスタンスを取得して本体解体
            try {
                Field mfField = UnsafeHelper.findField(entity.getClass(), "metapotentFlashfur");
                if (mfField != null) {
                    Object flashfurInstance = mfField.get(entity);
                    if (flashfurInstance != null) {
                        try {
                            Field idField = flashfurInstance.getClass().getDeclaredField("id");
                            idField.setAccessible(true);
                            UUID flashfurUuid = (UUID) idField.get(flashfurInstance);
                            if (flashfurUuid != null) {
                                markAnnihilated(flashfurUuid);
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Field rf = flashfurInstance.getClass().getDeclaredField("removed");
                            rf.setAccessible(true);
                            rf.setBoolean(flashfurInstance, true);
                        } catch (Throwable ignored) {}

                        try {
                            Field pf = flashfurInstance.getClass().getDeclaredField("metapotentFlashfurProxy");
                            pf.setAccessible(true);
                            pf.set(flashfurInstance, null);
                        } catch (Throwable ignored) {}

                        for (String timerName : new String[]{"beamTimer", "eraseTimer", "swipeTimer", "totalityBoomTimer", "throwInAirTimer"}) {
                            try {
                                Field f = flashfurInstance.getClass().getDeclaredField(timerName);
                                f.setAccessible(true);
                                f.setInt(flashfurInstance, -9999);
                            } catch (Throwable ignored) {}
                        }

                        Field bossEventField = UnsafeHelper.findField(flashfurInstance.getClass(), "bossEvent");
                        if (bossEventField != null) {
                            ServerBossEvent bossEvent = (ServerBossEvent) bossEventField.get(flashfurInstance);
                            if (bossEvent != null) {
                                bossEvent.removeAllPlayers();
                                bossEvent.setVisible(false);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // (2) MetapotentFlashfurLevel.metapotentFlashfurList から死亡・抹消済み個体を安全に除去
            purgeDeadFlashfurs();

            // (3) ディスク上の .omnimob ファイルの消去（ワールド再ロード時の復活阻止）
            wipeFlashfurDiskSaves(level);

            // (4) Respawning 及び RemoveDeadEntities のリスト消去
            wipeOmniMobsInternalLists();

            // (5) 乗っ取られた EventBus の正常化
            sanitizeEventBus();

            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Neutralized Metapotent Flashfur and cleared Omni-Mobs persistence");
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[AnnihilationAgent] Failed during Metapotent Flashfur neutralization", t);
        }
    }

    /**
     * MetapotentFlashfurLevel.metapotentFlashfurList 内の死亡・抹消済み個体を安全に除去・縮約する。
     * クライアント・サーバー双方の描画ループ前やパケット受信時に呼ばれ、残骸レンダラーの居残りを防ぐ。
     */
    public static void purgeDeadFlashfurs() {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        runWithFlashfurPrivilege(() -> {
            try {
                Class<?> levelClass = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
                Field listField = levelClass.getDeclaredField("metapotentFlashfurList");
                listField.setAccessible(true);
                Object protectedList = listField.get(null);
                if (protectedList instanceof ArrayList<?> list) {
                    Field sizeField = ArrayList.class.getDeclaredField("size");
                    long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
                    Field dataField = ArrayList.class.getDeclaredField("elementData");
                    long dataOffset = UnsafeHelper.objectFieldOffset(dataField);

                    if (sizeOffset >= 0 && dataOffset >= 0) {
                        Object[] elementData = (Object[]) UnsafeHelper.getObject(list, dataOffset);
                        int size = UnsafeHelper.getInt(list, sizeOffset);
                        if (elementData != null && size > 0) {
                            int newSize = 0;
                            for (int i = 0; i < size; i++) {
                                Object mf = elementData[i];
                                boolean shouldRemove = false;
                                if (mf == null) {
                                    shouldRemove = true;
                                } else {
                                    try {
                                        Field rf = mf.getClass().getDeclaredField("removed");
                                        rf.setAccessible(true);
                                        if (rf.getBoolean(mf)) shouldRemove = true;
                                        Field idf = mf.getClass().getDeclaredField("id");
                                        idf.setAccessible(true);
                                        UUID id = (UUID) idf.get(mf);
                                        if (id != null && isAnnihilated(id)) shouldRemove = true;
                                    } catch (Throwable ignored) {}
                                }
                                if (shouldRemove) {
                                    if (mf != null) {
                                        try {
                                            Field pf = mf.getClass().getDeclaredField("metapotentFlashfurProxy");
                                            pf.setAccessible(true);
                                            pf.set(mf, null);
                                        } catch (Throwable ignored) {}
                                    }
                                    elementData[i] = null;
                                } else {
                                    elementData[newSize++] = mf;
                                }
                            }
                            for (int i = newSize; i < size; i++) {
                                elementData[i] = null;
                            }
                            UnsafeHelper.putInt(list, sizeOffset, newSize);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    /**
     * MetapotentFlashfurLevel.metapotentFlashfurList を Unsafe を使って StackChecker なしで直接空にする。
     */
    public static void wipeFlashfurLevelMemory() {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        runWithFlashfurPrivilege(() -> {
            try {
                Class<?> levelClass = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
                Field listField = levelClass.getDeclaredField("metapotentFlashfurList");
                listField.setAccessible(true);
                Object protectedList = listField.get(null);
                // ArrayList のオフセットで書くので、ArrayList 以外（将来の Omni-Mobs の変更）には絶対に書かない（メモリ破壊になる）
                if (protectedList instanceof ArrayList<?> list) {
                    // ArrayList の elementData 配列と size フィールドを Unsafe で直接クリア
                    Field sizeField = ArrayList.class.getDeclaredField("size");
                    long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
                    Field dataField = ArrayList.class.getDeclaredField("elementData");
                    long dataOffset = UnsafeHelper.objectFieldOffset(dataField);

                    if (sizeOffset >= 0 && dataOffset >= 0) {
                        Object[] elementData = (Object[]) UnsafeHelper.getObject(list, dataOffset);
                        int size = UnsafeHelper.getInt(list, sizeOffset);
                        if (elementData != null) {
                            for (int i = 0; i < size; i++) {
                                Object mf = elementData[i];
                                if (mf != null) {
                                    try {
                                        Field idField = mf.getClass().getDeclaredField("id");
                                        idField.setAccessible(true);
                                        UUID flashfurUuid = (UUID) idField.get(mf);
                                        if (flashfurUuid != null) {
                                            markAnnihilated(flashfurUuid);
                                        }
                                    } catch (Throwable ignored) {}
                                    try {
                                        Field rf = mf.getClass().getDeclaredField("removed");
                                        rf.setAccessible(true);
                                        rf.setBoolean(mf, true);
                                    } catch (Throwable ignored) {}
                                    try {
                                        Field pf = mf.getClass().getDeclaredField("metapotentFlashfurProxy");
                                        pf.setAccessible(true);
                                        pf.set(mf, null);
                                    } catch (Throwable ignored) {}
                                    for (String timerName : new String[]{"beamTimer", "eraseTimer", "swipeTimer", "totalityBoomTimer", "throwInAirTimer"}) {
                                        try {
                                            Field f = mf.getClass().getDeclaredField(timerName);
                                            f.setAccessible(true);
                                            f.setInt(mf, -9999);
                                        } catch (Throwable ignored) {}
                                    }
                                    try {
                                        Field beField = UnsafeHelper.findField(mf.getClass(), "bossEvent");
                                        if (beField != null) {
                                            ServerBossEvent be = (ServerBossEvent) beField.get(mf);
                                            if (be != null) {
                                                be.removeAllPlayers();
                                                be.setVisible(false);
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                                elementData[i] = null;
                            }
                        }
                        UnsafeHelper.putInt(list, sizeOffset, 0);
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    /**
     * ディスク上に保存された Metapotent Flashfur の .omnimob ファイルをすべて消去する。
     */
    public static void wipeFlashfurDiskSaves(ServerLevel level) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        if (!EprotectConfig.get(EprotectConfig.ANNIHILATION_DELETE_OMNIMOB_SAVES)) return;
        try {
            Path worldPath = level.getServer().getWorldPath(LevelResource.ROOT);
            Path directory = worldPath.resolve("metapotent_flashfurs");
            if (Files.exists(directory)) {
                File dirFile = directory.toFile();
                File[] files = dirFile.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().endsWith(".omnimob")) {
                            if (!f.delete()) {
                                f.deleteOnExit();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Omni-Mobs の Respawning リスト及び RemoveDeadEntities リストを Unsafe でクリアする。
     */
    public static void wipeOmniMobsInternalLists() {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        wipeStaticArrayList("flashfur.omnimobs.entities.flashfur.powers.Respawning", "RESPAWNING_ENTITIES");
        wipeStaticArrayList("flashfur.omnimobs.entities.base.RemoveDeadEntities", "ENTITIES_TO_REMOVE");
    }

    /**
     * static な ArrayList（Omni-Mobs の ProtectedArrayList は clear/remove を拒否する）を、中身ごと Unsafe で空にする。
     * ArrayList のオフセットで書くので、ArrayList 以外には書かない（メモリ破壊になる）。
     */
    private static void wipeStaticArrayList(String className, String fieldName) {
        try {
            Field listField = Class.forName(className).getDeclaredField(fieldName);
            listField.setAccessible(true);
            if (!(listField.get(null) instanceof ArrayList<?> list)) return;
            long sizeOffset = UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("size"));
            long dataOffset = UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("elementData"));
            if (sizeOffset < 0 || dataOffset < 0) return;
            if (UnsafeHelper.getObject(list, dataOffset) instanceof Object[] elementData) {
                java.util.Arrays.fill(elementData, null); // 参照を残すと GC されない
            }
            UnsafeHelper.putInt(list, sizeOffset, 0);
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 4. 超絶抹殺総合パイプライン (Full Annihilation Pipeline)
    // =========================================================================

    /**
     * 単一のエンティティに対して超絶抹殺を実行する。
     *
     * @param level 実行対象のワールド
     * @param attacker 攻撃者（プレイヤーまたは他エンティティ）
     * @param target 抹消対象
     * @return 抹消に成功した場合 true
     */
    public static boolean annihilateEntity(ServerLevel level, @Nullable Entity attacker, LivingEntity target) {
        return annihilateEntity(level, attacker, target, true);
    }

    /**
     * 単一のエンティティに対して超絶抹殺を実行する（効果音の有無を指定可能）。
     *
     * @param level 実行対象のワールド
     * @param attacker 攻撃者（プレイヤーまたは他エンティティ）
     * @param target 抹消対象
     * @param playSound 効果音を鳴らすか
     * @return 抹消に成功した場合 true
     */
    public static boolean annihilateEntity(ServerLevel level, @Nullable Entity attacker, LivingEntity target, boolean playSound) {
        List<UUID> uuids = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        boolean done = annihilateEntity(level, attacker, target, playSound, true, uuids, ids);
        notifyClients(level, uuids, ids);
        return done;
    }

    /**
     * @param playSound 効果音を鳴らすか（範囲版は 1 体ごとに鳴らさず、最後に 1 回だけ鳴らす）
     * @param expand    つながった同じ MOD の個体（乗客・乗り物）もまとめて消すか（つながった個体からはさらに広げない）
     * @param uuids / ids 抹消した個体をクライアントへ知らせるために集める（部位エンティティの ID も含む）
     */
    private static boolean annihilateEntity(ServerLevel level, @Nullable Entity attacker, LivingEntity target, boolean playSound, boolean expand,
                                            List<UUID> uuids, List<Integer> ids) {
        // 兵姫などの保護対象は範囲版と同じく対象外（抹消のブラックリストと eprotect の入り直しが競合する）（※検証ダミーは対象）
        if (target == null || target instanceof Player || (target instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget())) return false;
        if (ANNIHILATED_INSTANCES.contains(target) && target.isRemoved()) return false; // つながりで既に消した個体
        List<Entity> connected = expand ? ModProtectionBreaker.connectedEntities(target) : List.of();
        // 追跡偽装で座標を NaN にするので、演出の位置は先に取っておく
        Vec3 origin = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
        final boolean[] defended = new boolean[1];

        runWithEventSuppression(() -> {
            // (1) ブラックリスト登録
            ANNIHILATED_UUIDS.add(target.getUUID());
            ANNIHILATED_INSTANCES.add(target);

            // (2) Witherzilla / Omni-Mobs 等のボス固有無力化
            neutralizeWitherzilla(level, target);
            neutralizeFlashfur(level, target);

            // (2') 他 MOD の独自の生命維持（保護の切り替え・フラグ・代理の本体・静的な一覧）を外し、対応していればその MOD 自身の撃破経路で倒す
            ModProtectionBreaker.Context context = ModProtectionBreaker.breakProtection(level, target);
            ModProtectionBreaker.finishWithModOwnDeath(target);
            target.ejectPassengers();
            target.stopRiding();

            // (3) バニラ即死・デスフラグ・無敵リセット
            DamageSource deathSource = attacker instanceof Player p
                    ? level.damageSources().playerAttack(p)
                    : (attacker instanceof LivingEntity living ? level.damageSources().mobAttack(living) : level.damageSources().generic());
            DeathLootFallback.before(target);
            target.invulnerableTime = 0;
            target.setHealth(0.0F);
            target.die(deathSource);
            ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
            target.kill();
            DeathLootFallback.after(level, target, deathSource, attacker instanceof LivingEntity killer ? killer : null);

            // Eprotect+ 防御層による遮断: 不正な消去理由直書き・区画排除を拒絶（Eprotect+ の守護が勝つ）
            if (target instanceof IEprotected p && p.getEprotect() != null && p.getEprotect().isProtectionActive()) {
                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Target {} is defended by Eprotect+; eviction blocked by Eprotect+", target.getName().getString());
                ANNIHILATED_UUIDS.remove(target.getUUID());
                ANNIHILATED_INSTANCES.remove(target);
                defended[0] = true;
                return;
            }

            // (4) 追跡偽装・区画・一覧からの完全切り離し
            spoofAndDisconnectTracking(level, target);

            // (5) 破棄通知・Capability 無効化
            target.discard();
            target.invalidateCaps();
            target.onRemovedFromWorld();

            // (6) 死亡処理の途中で静的な一覧へ戻されていれば、もう一度取り除く
            ModProtectionBreaker.afterErase(target, context);
        });

        if (defended[0]) {
            return false;
        }

        uuids.add(target.getUUID());
        ids.add(target.getId());
        if (target.isMultipartEntity() && target.getParts() != null) {
            for (PartEntity<?> part : target.getParts()) {
                if (part != null) ids.add(part.getId());
            }
        }

        // (6') 同じ種類の作り直し（別の UUID での復活）を、しばらくの間この近くで拒否する（他 MOD の種類だけ）
        SpawnBanZone.banNear(level, target, origin);

        // (7) つながった同じ MOD の個体（乗客・乗り物）もまとめて消す
        for (Entity other : connected) {
            if (other instanceof LivingEntity living) {
                annihilateEntity(level, attacker, living, false, false, uuids, ids);
            } else {
                uuids.add(other.getUUID());
                ids.add(other.getId());
                ANNIHILATED_UUIDS.add(other.getUUID());
                ANNIHILATED_INSTANCES.add(other);
                runWithEventSuppression(() -> {
                    spoofAndDisconnectTracking(level, other);
                    other.discard();
                });
            }
        }

        // (6) 演出（控えめ: 煙のパーティクルと効果音 1 つだけ）
        spawnAnnihilationParticles(level, origin);
        if (playSound) playAnnihilationSound(level, origin);

        return true;
    }

    /**
     * 範囲内の全エンティティ（プレイヤー・兵姫などの保護対象を除く生物）を一掃する（超絶抹殺の棒の空中右クリック）。
     *
     * @param level 実行ワールド
     * @param attacker 実行プレイヤー
     * @param radius 殲滅半径（ブロック）
     * @return 抹殺されたエンティティの数
     */
    public static int annihilateAllInArea(ServerLevel level, Player attacker, double radius) {
        AABB box = attacker.getBoundingBox().inflate(radius);
        List<Entity> entities = level.getEntities(attacker, box, e -> {
            if (e instanceof Player) return false;
            if (e instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) return false; // 兵姫などの味方は保護
            return e instanceof LivingEntity;
        });

        int eradicated = 0;
        List<UUID> uuids = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (Entity e : entities) {
            if (e instanceof LivingEntity living) {
                if (annihilateEntity(level, attacker, living, false, true, uuids, ids)) {
                    eradicated++;
                }
            }
        }
        notifyClients(level, uuids, ids);

        // Metapotent Flashfur のレベル常駐体及びディスク保存の一括抹消
        wipeFlashfurLevelMemory();
        wipeFlashfurDiskSaves(level);
        wipeOmniMobsInternalLists();
        sanitizeEventBus();

        // 天候の正常化
        level.getLevelData().setRaining(false);
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);

        playAnnihilationSound(level, attacker.position());

        return eradicated;
    }

    /**
     * 抹消した個体をこのディメンションのクライアントへ知らせる。
     * バニラの消去通知だけでは、自分で消去を拒否する実装（Witherzilla の処刑竜など）がクライアントに幽霊として残る。
     */
    private static void notifyClients(ServerLevel level, List<UUID> uuids, List<Integer> ids) {
        if (uuids.isEmpty() && ids.isEmpty()) return;
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new AnnihilatedEntitiesPacket(uuids, ids));
    }

    /** クライアントで抹消済みとして記録する（生命維持フラグを false にする Mixin がクライアントでも効くように） */
    public static void markAnnihilated(UUID uuid) {
        ANNIHILATED_UUIDS.add(uuid);
    }

    public static void clearMarks() {
        ANNIHILATED_UUIDS.clear();
        ANNIHILATED_INSTANCES.clear();
        SpawnBanZone.clear();
    }

    /** 消えた位置に煙を少しだけ出す（バニラの死亡時の煙と同じ程度） */
    private static void spawnAnnihilationParticles(ServerLevel level, Vec3 pos) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.02);
    }

    private static void playAnnihilationSound(ServerLevel level, Vec3 pos) {
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearMarks();
    }
}
