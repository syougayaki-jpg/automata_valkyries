package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.EprotectConfig;
import com.automatavalkyries.mixin.ChunkMapAccessor;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.EntityTickListAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.util.UnsafeHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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

import java.io.File;
import java.lang.reflect.Field;
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
        Entity entity = event.getEntity();
        if (entity != null && isAnnihilated(entity)) {
            event.setCanceled(true);
            entity.discard();
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Blocked and canceled re-join of annihilated entity: {} ({})",
                    entity.getName().getString(), entity.getUUID());
        }
    }

    /**
     * 抹消処理中の被ダメージキャンセルを無効化する。
     * 優先度の高い購読者から先に呼ばれるので、他 MOD のキャンセルを覆すには最後（LOWEST）に回る必要がある。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (isThreadAnnihilating() || isAnnihilated(event.getEntity())) {
            event.setCanceled(false);
        }
    }

    /**
     * 抹消処理中の死亡キャンセルを無効化する（LOWEST の理由は {@link #onLivingHurt} と同じ）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (isThreadAnnihilating() || isAnnihilated(event.getEntity())) {
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
            EntityTickListAccessor tickListAccessor = (EntityTickListAccessor) tickList;
            tickListAccessor.automata_valkyries$getActive().remove(entity.getId());
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
     * リフレクションでエンティティの ServerBossEvent を検索し、全プレイヤーから非表示にする。
     */
    private static void clearBossEvents(Entity entity) {
        Class<?> clz = entity.getClass();
        while (clz != null && clz != Object.class) {
            for (Field f : clz.getDeclaredFields()) {
                if (ServerBossEvent.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        ServerBossEvent bossEvent = (ServerBossEvent) f.get(entity);
                        if (bossEvent != null) {
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

    /**
     * Metapotent Flashfur（Omni-Mobs）の無力化。
     * プロキシエンティティの向こうにある MetapotentFlashfurLevel を Unsafe で直接クリアし、ディスク保存も消去する。
     */
    public static void neutralizeFlashfur(ServerLevel level, Entity entity) {
        String className = entity.getClass().getName();
        if (!className.contains("flashfur.omnimobs")) return;

        try {
            // (1) プロキシエンティティから MetapotentFlashfur インスタンスを取得してボスバー非表示
            try {
                Field mfField = UnsafeHelper.findField(entity.getClass(), "metapotentFlashfur");
                if (mfField != null) {
                    Object flashfurInstance = mfField.get(entity);
                    if (flashfurInstance != null) {
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

            // (2) MetapotentFlashfurLevel.metapotentFlashfurList の Unsafe 直接メモリ抹消
            wipeFlashfurLevelMemory();

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
     * MetapotentFlashfurLevel.metapotentFlashfurList を Unsafe を使って StackChecker なしで直接空にする。
     */
    public static void wipeFlashfurLevelMemory() {
        try {
            Class<?> levelClass = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            Field listField = levelClass.getDeclaredField("metapotentFlashfurList");
            listField.setAccessible(true);
            Object protectedList = listField.get(null);
            // ArrayList のオフセットで書くので、ArrayList 以外（将来の Omni-Mobs の変更）には絶対に書かない（メモリ破壊になる）
            if (protectedList instanceof ArrayList<?>) {
                // ArrayList の elementData 配列と size フィールドを Unsafe で直接クリア
                Field sizeField = ArrayList.class.getDeclaredField("size");
                long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
                Field dataField = ArrayList.class.getDeclaredField("elementData");
                long dataOffset = UnsafeHelper.objectFieldOffset(dataField);

                if (sizeOffset >= 0 && dataOffset >= 0) {
                    Object[] elementData = (Object[]) UnsafeHelper.getObject(protectedList, dataOffset);
                    int size = UnsafeHelper.getInt(protectedList, sizeOffset);
                    if (elementData != null) {
                        for (int i = 0; i < size; i++) {
                            Object mf = elementData[i];
                            if (mf != null) {
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
                    UnsafeHelper.putInt(protectedList, sizeOffset, 0);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * ディスク上に保存された Metapotent Flashfur の .omnimob ファイルをすべて消去する。
     */
    public static void wipeFlashfurDiskSaves(ServerLevel level) {
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
     * @param attacker 攻撃者（プレイヤー）
     * @param target 抹消対象
     * @return 抹消に成功した場合 true
     */
    public static boolean annihilateEntity(ServerLevel level, Player attacker, LivingEntity target) {
        // 兵姫などの保護対象は範囲版と同じく対象外（抹消のブラックリストと eprotect の入り直しが競合する）
        if (target == null || target instanceof Player || target instanceof IEprotected) return false;

        runWithEventSuppression(() -> {
            // (1) ブラックリスト登録
            ANNIHILATED_UUIDS.add(target.getUUID());
            ANNIHILATED_INSTANCES.add(target);

            // (2) Witherzilla / Omni-Mobs 等のボス固有無力化
            neutralizeWitherzilla(level, target);
            neutralizeFlashfur(level, target);

            // (3) バニラ即死・デスフラグ・無敵リセット
            target.invulnerableTime = 0;
            target.setHealth(0.0F);
            ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
            target.die(level.damageSources().playerAttack(attacker));
            target.kill();

            // (4) 追跡偽装・区画・一覧からの完全切り離し
            spoofAndDisconnectTracking(level, target);

            // (5) 破棄通知・Capability 無効化
            target.discard();
            target.invalidateCaps();
            target.onRemovedFromWorld();

            // (6) 抹消ビジュアル＆サウンド演出
            spawnAnnihilationEffects(level, target.position());
        });

        return true;
    }

    /**
     * 範囲内の全チートエンティティ・ボスを一掃する（超絶抹殺の棒の右クリック範囲殲滅）。
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
            if (e instanceof IEprotected) return false; // 兵姫などの味方は保護
            return e instanceof LivingEntity;
        });

        int eradicated = 0;
        for (Entity e : entities) {
            if (e instanceof LivingEntity living) {
                if (annihilateEntity(level, attacker, living)) {
                    eradicated++;
                }
            }
        }

        // Metapotent Flashfur のレベル常駐体及びディスク保存の一括抹消
        wipeFlashfurLevelMemory();
        wipeFlashfurDiskSaves(level);
        wipeOmniMobsInternalLists();
        sanitizeEventBus();

        // 天候の正常化
        level.getLevelData().setRaining(false);
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);

        // プレイヤー周囲の演出
        spawnAnnihilationEffects(level, attacker.position());

        return eradicated;
    }

    /**
     * 超絶抹殺発動時のエフェクト・パーティクル・サウンドの生成
     */
    private static void spawnAnnihilationEffects(ServerLevel level, Vec3 pos) {
        if (!Float.isFinite((float) pos.x) || !Float.isFinite((float) pos.y) || !Float.isFinite((float) pos.z)) {
            return;
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 2, 0.5, 0.5, 0.5, 0.0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0, pos.z, 50, 1.5, 1.5, 1.5, 0.1);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 5, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 40, 1.0, 1.0, 1.0, 1.0);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 2.0F, 0.8F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ANNIHILATED_UUIDS.clear();
        ANNIHILATED_INSTANCES.clear();
    }
}
