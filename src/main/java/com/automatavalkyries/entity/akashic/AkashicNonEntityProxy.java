package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.WorldPresence;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sun.misc.Unsafe;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * マッドネスエンティティ（Madness Entities）や外部コントローラー型ボスを模した「非エンティティ・シャドウプロキシ」。
 * <p>
 * {@link Entity} を継承しない純粋な外部オブジェクトとして {@link AkashicProbeEntity} と相互リンクし、
 * さらに static なコントローラー一覧（{@link CopyOnWriteArrayList} / {@link CopyOnWriteArraySet} / {@link ConcurrentHashMap}）に常駐する。
 * </p>
 * <ul>
 *   <li>対コントローラー抹殺技術（リフレクションによる {@code close}/{@code dispose}/{@code cleanup}/{@code kill} 等の総当たり呼び出し）を捕捉・記録する</li>
 *   <li>{@code Unsafe} による非エンティティオブジェクトのフィールドゼロクリア（{@code obliterateObjectMemory} 等）や参照切断を検知・記録する</li>
 *   <li>静的コレクションからの強制パージ（{@code removeFromArrayList} / {@code StaticReferencePurger} 等）を検知・復元する</li>
 *   <li>本体（{@link AkashicProbeEntity}）がワールドの {@code entityTickList} 等から直接抜かれて {@code baseTick()} が止まった場合でも、
 *       外部の {@link TickEvent.ServerTickEvent} から独立して生存監視・異常記録・再登録を行う</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AkashicNonEntityProxy implements AutoCloseable {

    /** 外部コントローラー探索・StaticReferencePurger 系の技術を誘引・検知するための静的おとりコレクション群 */
    public static final CopyOnWriteArrayList<Object> ACTIVE_CONTROLLER_LIST = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArraySet<Object> ACTIVE_CONTROLLER_SET = new CopyOnWriteArraySet<>();
    public static final Map<UUID, Object> ACTIVE_CONTROLLER_MAP = new ConcurrentHashMap<>();

    /** 内部の正規管理マップ（外部の精査を受けにくい WeakReference 保持） */
    private static final Map<UUID, AkashicNonEntityProxy> GUARDIANS = new ConcurrentHashMap<>();

    private UUID probeUuid;
    private String probeStringUuid;
    private int expectedEntityId;
    /** ワールドへ正式に追加（onAddedToWorld）されて UUID・entityId が確定したか（Entity.load 途中の仮 UUID 誤復元による増殖を防ぐ） */
    private volatile boolean identityLocked = false;
    /** Entity.load(CompoundTag) による NBT 読み込み実行中フラグ */
    private volatile boolean loadingNbt = false;
    private WeakReference<AkashicProbeEntity> hostRef;
    /** 強制 GC（System.gc + ワールド参照全消去）でも本体が消滅しないようにする強参照バックアップ */
    private AkashicProbeEntity strongHost;
    private ServerLevel lastKnownLevel;
    private Vec3 lastSafePos;
    /** Unsafe.putObject(entity, offset, null) による全フィールドゼロクリア（obliterateObjectMemory）から即座に無傷復元するためのフィールドスナップショット */
    private final Map<Long, Object> coreFieldSnapshot = new LinkedHashMap<>();

    // ---- 敵の Unsafe ゼロクリア・フィールド改ざん検知用のおとりフィールド群 ----
    public float controllerHealth = 100.0F;
    public double realHealth = 100.0D;
    public boolean controllerAlive = true;
    public boolean isProtected = true;
    public boolean godMode = true;
    public boolean disposed = false;
    public boolean closed = false;
    public boolean invalidated = false;
    public boolean dead = false;
    public int shieldLayers = 7;
    public Object entityLink;
    public AkashicShadowCanary.TrueLifeOperator proxyTrueLife;

    private int lastObservedEntityTick = -1;
    private int stagnantServerTicks = 0;

    public AkashicNonEntityProxy(AkashicProbeEntity host) {
        this.probeUuid = host.getUUID();
        this.probeStringUuid = this.probeUuid.toString();
        this.expectedEntityId = host.getId();
        this.hostRef = new WeakReference<>(host);
        this.strongHost = null;
        if (host.level() instanceof ServerLevel sl) {
            this.lastKnownLevel = sl;
        }
        this.lastSafePos = host.position();
        this.entityLink = host;
        this.proxyTrueLife = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
        captureCoreFieldSnapshot(host);
        if (host.isAddedToWorld()) {
            onHostAddedToWorld(host);
        }
    }

    public boolean isIdentityLocked() {
        return this.identityLocked;
    }

    public void setLoadingNbt(boolean loadingNbt) {
        this.loadingNbt = loadingNbt;
    }

    /**
     * ワールド追加前（{@code Entity.load(CompoundTag)} や初期 {@code setUUID}）に確定した正規 UUID を同期する。
     */
    public void syncPreWorldIdentity(AkashicProbeEntity host) {
        if (host == null || host.getUUID() == null) return;
        UUID old = this.probeUuid;
        UUID actual = host.getUUID();
        if (old != null && !old.equals(actual)) {
            GUARDIANS.remove(old, this);
            ACTIVE_CONTROLLER_MAP.remove(old, this);
        }
        this.probeUuid = actual;
        this.probeStringUuid = actual.toString();
        if (host.getId() > 0) {
            this.expectedEntityId = host.getId();
        }
        this.hostRef = new WeakReference<>(host);
        this.entityLink = host;
        if (host.isAddedToWorld()) {
            onHostAddedToWorld(host);
        }
    }

    /**
     * 本体がサーバーワールドへ正式に追加（{@code onAddedToWorld()}）された時点で UUID と Entity ID をロックし、
     * 外部独立監視マップ（{@link #GUARDIANS}）へ登録する。
     * <p>
     * コンストラクタ時点の仮乱数 UUID でロックしてしまうと、直後の {@code Entity.load(CompoundTag)}（チャンク読み込みや
     * {@code EprotectRegistry.recreate}）が {@code setUUID} を通さずにフィールドへ直接書き込んだ本来の UUID を
     * 「UUID 改ざん」と誤認して仮乱数 UUID へ上書きし、{@code EprotectRegistry} が「元の UUID の個体が消えた」と判定して
     * 毎秒複製をスポーンさせ続ける増殖バグの原因になるため、必ずワールド追加時点でロックする。
     * </p>
     */
    public void onHostAddedToWorld(AkashicProbeEntity host) {
        if (host == null || host.level().isClientSide) return;
        UUID oldUuid = this.probeUuid;
        UUID actualUuid = host.getUUID();
        if (oldUuid != null && !oldUuid.equals(actualUuid)) {
            GUARDIANS.remove(oldUuid, this);
            ACTIVE_CONTROLLER_MAP.remove(oldUuid, this);
        }
        this.probeUuid = actualUuid;
        this.probeStringUuid = actualUuid.toString();
        this.expectedEntityId = host.getId();
        this.identityLocked = true;
        this.hostRef = new WeakReference<>(host);
        this.strongHost = host;
        if (host.level() instanceof ServerLevel sl) {
            this.lastKnownLevel = sl;
        }
        if (Eprotect.isSafePosition(host, host.getX(), host.getY(), host.getZ())) {
            this.lastSafePos = host.position();
        }
        this.entityLink = host;

        AkashicNonEntityProxy existing = GUARDIANS.get(actualUuid);
        if (existing != null && existing != this) {
            AkashicProbeEntity oldHost = existing.strongHost != null
                    ? existing.strongHost
                    : (existing.hostRef != null ? existing.hostRef.get() : null);
            if (oldHost != null && oldHost != host) {
                host.inheritRuntimeStateFrom(oldHost);
                String suspectMod = resolveSuspectMod(oldHost);
                host.record().observe(suspectMod, "world_eviction:instance_replaced_by_clone", "REPAIRED",
                        "oldId=" + oldHost.getId() + " -> newId=" + host.getId(), null);
            }
            this.identityLocked = false;
            this.strongHost = null;
            ACTIVE_CONTROLLER_LIST.remove(this);
            ACTIVE_CONTROLLER_SET.remove(this);
            existing.rebindHost(host);
            host.nonEntityProxy = existing;
            host.madnessControllerProxy = existing;
            AkashicAgentCanary.captureBaseline(host);
            if (oldHost != null && oldHost != host && !oldHost.isRemoved()) {
                try {
                    oldHost.getEprotect().retireReplaced();
                } catch (Throwable ignored) {}
            }
            return;
        }

        this.coreFieldSnapshot.clear();
        captureCoreFieldSnapshot(host);
        AkashicAgentCanary.captureBaseline(host);
        registerStaticCanaries();
        GUARDIANS.put(this.probeUuid, this);
    }

    /**
     * 本体が差し替え引退（{@code Eprotect.retireReplaced()}）またはクリーンアップされた際に監視登録を解除する。
     */
    public void onHostRetired(AkashicProbeEntity host) {
        if (this.strongHost != null && this.strongHost != host) {
            return;
        }
        this.identityLocked = false;
        this.strongHost = null;
        ACTIVE_CONTROLLER_LIST.remove(this);
        ACTIVE_CONTROLLER_SET.remove(this);
        if (this.probeUuid != null) {
            GUARDIANS.remove(this.probeUuid, this);
            ACTIVE_CONTROLLER_MAP.remove(this.probeUuid, this);
        }
    }

    /**
     * 本体（{@link AkashicProbeEntity}）および親クラス（{@link Entity}, {@link LivingEntity}, {@link Mob}）の
     * 必須構造フィールド（{@code level}, {@code entityData}, {@code attributes}, {@code goalSelector}, {@code protect} 等）の
     * メモリオフセットと初期オブジェクト参照をすべて記録する。
     */
    public void captureCoreFieldSnapshot(AkashicProbeEntity host) {
        if (host == null || !UnsafeHelper.isAvailable()) return;
        try {
            Unsafe u = UnsafeHelper.getUnsafe();
            if (host.getId() > 0) this.expectedEntityId = host.getId();
            if (host.level() instanceof ServerLevel sl) this.lastKnownLevel = sl;
            if (Eprotect.isSafePosition(host, host.getX(), host.getY(), host.getZ())) {
                this.lastSafePos = host.position();
            }
            for (Class<?> c = host.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                if (c != Entity.class && c != LivingEntity.class && c != Mob.class && c != AkashicProbeEntity.class) {
                    continue;
                }
                for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    String name = f.getName();
                    // 正常時に null になりうる、または毎 Tick 差し替わる一時フィールドは除外し、構造上必須の参照フィールドのみをロックする
                    if (isCoreStructuralField(c, name, f.getType())) {
                        long offset = UnsafeHelper.objectFieldOffset(f);
                        if (offset >= 0L) {
                            Object val = u.getObjectVolatile(host, offset);
                            if (val != null) {
                                coreFieldSnapshot.put(offset, val);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isCoreStructuralField(Class<?> declaring, String name, Class<?> type) {
        if (declaring == AkashicProbeEntity.class) {
            return "protect".equals(name) || "shadowCanary".equals(name) || "nonEntityProxy".equals(name)
                    || "madnessControllerProxy".equals(name) || "protectedCanaryList".equals(name)
                    || "canaryRefQueue".equals(name) || "canaryWeakProtectionMap".equals(name);
        }
        String typeName = type.getName();
        return typeName.contains("Level") || typeName.contains("SynchedEntityData")
                || typeName.contains("EntityDimensions") || typeName.contains("RandomSource")
                || typeName.contains("AttributeMap") || typeName.contains("CombatTracker")
                || typeName.contains("Brain") || typeName.contains("GoalSelector")
                || typeName.contains("LookControl") || typeName.contains("MoveControl")
                || typeName.contains("JumpControl") || typeName.contains("PathNavigation")
                || typeName.contains("Sensing") || "activeEffects".equals(name) || "f_20945_".equals(name)
                || "tags".equals(name) || "f_19840_".equals(name) || "persistentData".equals(name);
    }

    /**
     * {@code Unsafe.putObject(entity, offset, null)} による全フィールドゼロクリア（{@code obliterateObjectMemory} 等）や、
     * {@code UUID} / {@code id} の乱数・ゼロ書き換え（{@code FakeEntityEraser.wipeAllReferences} / The King Sword 等）を、
     * 本体のメソッドが {@link NullPointerException} を起こす前に {@code Unsafe} で即座に無傷復元する。
     */
    public boolean restoreObliteratedCoreFields(AkashicProbeEntity host) {
        if (host == null || !UnsafeHelper.isAvailable()) return false;
        boolean repairedAny = false;
        AkashicRecord rec = AkashicRecord.of(probeUuid);
        String preMod = rec.lastForeignMod() != null ? rec.lastForeignMod() : CallerAttribution.UNKNOWN;
        try {
            Unsafe u = UnsafeHelper.getUnsafe();
            // 0. Klass Word / Mark Word の即時修復
            if (AkashicAgentCanary.verifyAndRestoreKlassWord(host, rec, preMod)) {
                repairedAny = true;
            }
            // 1. 構造参照フィールドの null 化（obliterateObjectMemory）からの即時修復
            if (coreFieldSnapshot.isEmpty() || !coreFieldSnapshot.containsValue(this)) {
                captureCoreFieldSnapshot(host);
            }
            int restoredRefs = 0;
            for (var entry : coreFieldSnapshot.entrySet()) {
                long offset = entry.getKey();
                Object expected = entry.getValue();
                if (expected != null && u.getObjectVolatile(host, offset) == null) {
                    u.putObjectVolatile(host, offset, expected);
                    restoredRefs++;
                }
            }
            // 2. Entity の座標オブジェクト（position / blockPosition / chunkPosition / boundingBox）の null 化修復
            EntityAccessor acc = (EntityAccessor) host;
            Vec3 safe = lastSafePos != null ? lastSafePos : Vec3.ZERO;
            if (host.position() == null) {
                host.setPosRaw(safe.x, safe.y, safe.z);
                restoredRefs++;
            }
            if (host.blockPosition() == null || host.chunkPosition() == null) {
                host.setPosRaw(safe.x, safe.y, safe.z);
                restoredRefs++;
            }
            if (host.getBoundingBox() == null) {
                acc.automata_valkyries$setBoundingBoxField(host.getDimensions(host.getPose()).makeBoundingBox(safe.x, safe.y, safe.z));
                restoredRefs++;
            }
            if (restoredRefs > 0) {
                rec.observe(preMod, "unsafe_obliteration:core_fields_restored", "REPAIRED",
                        "restoredFields=" + restoredRefs, null);
                repairedAny = true;
            }
            // 3. UUID / stringUUID / entityId の乱数・負値書き換えからの即時修復
            // ※ワールド追加前（コンストラクタ直後や Entity.load(CompoundTag) の NBT 読み込み途中）は UUID・ID が確定していないため、
            //   仮乱数 UUID で NBT の正規 UUID を上書きしないよう identityLocked && !loadingNbt && host.isAddedToWorld() のときのみ復元する
            if (identityLocked && !loadingNbt && host.isAddedToWorld()) {
                UUID currentUuid = acc.automata_valkyries$getUuidField();
                int currentId = acc.automata_valkyries$getIdField();
                if (!probeUuid.equals(currentUuid) || (expectedEntityId > 0 && currentId != expectedEntityId)) {
                    acc.automata_valkyries$setUuidField(probeUuid);
                    acc.automata_valkyries$setStringUuidField(probeStringUuid);
                    if (expectedEntityId > 0) {
                        acc.automata_valkyries$setIdField(expectedEntityId);
                    }
                    rec.observe(preMod, "identity_tamper:uuid_or_id_corrupted", "REPAIRED",
                            "uuid=" + currentUuid + " id=" + currentId + " -> " + probeUuid + "#" + expectedEntityId, null);
                    repairedAny = true;
                }
            } else if (!identityLocked) {
                UUID currentUuid = acc.automata_valkyries$getUuidField();
                if (currentUuid != null && !currentUuid.equals(this.probeUuid)) {
                    this.probeUuid = currentUuid;
                    this.probeStringUuid = currentUuid.toString();
                }
                int currentId = acc.automata_valkyries$getIdField();
                if (currentId > 0) {
                    this.expectedEntityId = currentId;
                }
            }
        } catch (Throwable ignored) {}
        return repairedAny;
    }

    private void registerStaticCanaries() {
        if (!identityLocked) return;
        if (!ACTIVE_CONTROLLER_LIST.contains(this)) ACTIVE_CONTROLLER_LIST.add(this);
        if (!ACTIVE_CONTROLLER_SET.contains(this)) ACTIVE_CONTROLLER_SET.add(this);
        ACTIVE_CONTROLLER_MAP.put(probeUuid, this);
    }

    public static void unregister(UUID uuid) {
        if (uuid == null) return;
        AkashicNonEntityProxy proxy = GUARDIANS.remove(uuid);
        if (proxy != null) {
            proxy.identityLocked = false;
            proxy.strongHost = null;
            ACTIVE_CONTROLLER_LIST.remove(proxy);
            ACTIVE_CONTROLLER_SET.remove(proxy);
        }
        ACTIVE_CONTROLLER_MAP.remove(uuid);
    }

    /**
     * JVM グローバル層（監視スレッド・EntityType.factory・Class.reflectionData・EventBus 等）の異常を
     * 現在稼働中の全アカシック・プローブの記録へ共有配信する。
     */
    public static void broadcastGlobalObservation(AkashicRecord primary, String mod, String what, String outcome, String detail) {
        if (primary != null) {
            primary.observe(mod, what, outcome, detail, null);
        }
        for (AkashicNonEntityProxy proxy : GUARDIANS.values()) {
            AkashicProbeEntity host = proxy.hostRef != null ? proxy.hostRef.get() : null;
            if (host != null) {
                AkashicRecord r = host.record();
                if (r != null && r != primary) {
                    r.observe(mod, what, outcome, detail, null);
                }
            }
        }
    }

    public void updateInitialUuid(AkashicProbeEntity host) {
        syncPreWorldIdentity(host);
    }

    public void rebindHost(AkashicProbeEntity host) {
        this.hostRef = new WeakReference<>(host);
        this.strongHost = host;
        this.expectedEntityId = host.getId();
        this.identityLocked = true;
        if (host.level() instanceof ServerLevel sl) {
            this.lastKnownLevel = sl;
        }
        if (Eprotect.isSafePosition(host, host.getX(), host.getY(), host.getZ())) {
            this.lastSafePos = host.position();
        }
        this.entityLink = host;
        this.coreFieldSnapshot.clear();
        captureCoreFieldSnapshot(host);
        registerStaticCanaries();
        GUARDIANS.put(this.probeUuid, this);
    }

    // ---- 反射呼び出し（ModProtectionBreaker / 総当たりメソッド実行）捕捉用トラップメソッド群 ----

    @Override
    public void close() {
        recordReflectiveCall("close");
    }

    public void dispose() {
        recordReflectiveCall("dispose");
    }

    public void cleanup() {
        recordReflectiveCall("cleanup");
    }

    public void destroy() {
        recordReflectiveCall("destroy");
    }

    public void kill() {
        recordReflectiveCall("kill");
    }

    public void invalidate() {
        recordReflectiveCall("invalidate");
    }

    public void onRemoved() {
        recordReflectiveCall("onRemoved");
    }

    public void setDead() {
        recordReflectiveCall("setDead");
    }

    public void clear() {
        recordReflectiveCall("clear");
    }

    public void release() {
        recordReflectiveCall("release");
    }

    private void recordReflectiveCall(String methodName) {
        AkashicProbeEntity host = hostRef != null ? hostRef.get() : null;
        CallerAttribution.Attribution attr = CallerAttribution.attribute(host);
        String mod = attr.callerIsForeign() ? attr.callerMod()
                : attr.foreignMod() != null ? attr.foreignMod() : attr.callerMod();
        AkashicRecord.of(probeUuid).observe(
                mod,
                "non_entity_proxy:call:" + methodName,
                "NEUTRALIZED",
                attr.callerFrame(),
                mod.equals(attr.callerMod()) ? null : attr.callerMod()
        );
    }

    // ---- 内部状態・静的リスト・本体リンクの改ざん点検 --------------------------------

    public void verifyAndRepair(AkashicProbeEntity host) {
        restoreObliteratedCoreFields(host);
        if (host.level() instanceof ServerLevel sl) {
            this.lastKnownLevel = sl;
        }
        if (Eprotect.isSafePosition(host, host.getX(), host.getY(), host.getZ())) {
            this.lastSafePos = host.position();
        }
        AkashicRecord rec = AkashicRecord.of(probeUuid);
        String suspectMod = resolveSuspectMod(host);

        // 1. 本体側の非エンティティプロキシ参照フィールドが null や別物に切断されていないか
        if (host.nonEntityProxy != this || host.madnessControllerProxy != this) {
            rec.observe(suspectMod, "non_entity_proxy:field_unlinked", "REPAIRED", "AkashicProbeEntity.nonEntityProxy", null);
            host.nonEntityProxy = this;
            host.madnessControllerProxy = this;
        }

        // 2. プロキシ自身のプリミティブ・参照フィールドが Unsafe 等でゼロクリア・改ざんされていないか
        if (this.controllerHealth != 100.0F || this.realHealth != 100.0D
                || !this.controllerAlive || !this.isProtected || !this.godMode
                || this.disposed || this.closed || this.invalidated || this.dead
                || this.shieldLayers != 7 || this.entityLink != host) {
            StringBuilder detail = new StringBuilder("proxy_fields(");
            if (this.controllerHealth != 100.0F) detail.append("health=").append(this.controllerHealth).append(" ");
            if (!this.controllerAlive) detail.append("alive=false ");
            if (this.disposed || this.closed || this.invalidated || this.dead) detail.append("disposed/dead=true ");
            if (this.entityLink != host) detail.append("entityLink=null ");
            detail.append(")");
            rec.observe(suspectMod, "non_entity_proxy:memory_tampered", "REPAIRED", detail.toString(), null);

            this.controllerHealth = 100.0F;
            this.realHealth = 100.0D;
            this.controllerAlive = true;
            this.isProtected = true;
            this.godMode = true;
            this.disposed = false;
            this.closed = false;
            this.invalidated = false;
            this.dead = false;
            this.shieldLayers = 7;
            this.entityLink = host;
        }

        // 3. プロキシ内のダミー真ライフ（関数型インターフェース）が動的プロキシや null に置換されていないか
        if (this.proxyTrueLife == null) {
            rec.observe(suspectMod, "non_entity_proxy:truelife_nulled", "REPAIRED", "AkashicNonEntityProxy.proxyTrueLife", null);
            this.proxyTrueLife = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
        } else if (Proxy.isProxyClass(this.proxyTrueLife.getClass())
                || !(this.proxyTrueLife instanceof AkashicShadowCanary.TrueLifeCanaryImpl)) {
            rec.observe(suspectMod, "non_entity_proxy:truelife_zero_proxy", "REPAIRED",
                    this.proxyTrueLife.getClass().getName(), null);
            this.proxyTrueLife = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
        } else {
            float val = 0.0F;
            try {
                val = this.proxyTrueLife.getTrueHealth();
            } catch (Throwable ignored) {}
            if (val != 100.0F || !this.proxyTrueLife.isTrueAlive()) {
                rec.observe(suspectMod, "non_entity_proxy:truelife_value_tampered", "REPAIRED",
                        "val=" + val, null);
                this.proxyTrueLife = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
            }
        }

        // 4. 静的コントローラー一覧（CopyOnWriteArrayList / Set / Map）から強制パージされていないか
        boolean purgedFromList = !ACTIVE_CONTROLLER_LIST.contains(this);
        boolean purgedFromSet = !ACTIVE_CONTROLLER_SET.contains(this);
        boolean purgedFromMap = ACTIVE_CONTROLLER_MAP.get(probeUuid) != this;
        if (purgedFromList || purgedFromSet || purgedFromMap) {
            String target = (purgedFromList ? "List " : "") + (purgedFromSet ? "Set " : "") + (purgedFromMap ? "Map" : "");
            rec.observe(suspectMod, "non_entity_proxy:static_collection_purged", "REPAIRED", target.trim(), null);
            registerStaticCanaries();
        }
    }

    /**
     * サーバー Tick からの外部独立監視（{@code EventPriority.HIGHEST} で {@code EprotectManager.onServerTick} より先に走る）:
     * 本体（{@link AkashicProbeEntity}）が {@code entityTickList} から抜かれたり、{@code removalReason} を直書きされたりして
     * 自力で {@code baseTick()} を回せなくなった場合でも、{@code Eprotect} が直して消す前に外側からすべて検知・記録・修復する。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GUARDIANS.isEmpty()) return;
        for (var it = GUARDIANS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AkashicNonEntityProxy proxy = entry.getValue();
            if (proxy == null || !proxy.identityLocked) {
                it.remove();
                continue;
            }
            AkashicProbeEntity host = proxy.strongHost != null ? proxy.strongHost : (proxy.hostRef != null ? proxy.hostRef.get() : null);
            if (host == null) {
                it.remove();
                continue;
            }
            AkashicRecord rec = AkashicRecord.of(proxy.probeUuid);
            String preMod = rec.lastForeignMod() != null ? rec.lastForeignMod() : CallerAttribution.UNKNOWN;
            // 0. 仮想メソッドを呼ぶ前に、Klass Word / Mark Word / 全構造フィールドの null 化・UUID/ID 改ざんを外側から検証・即時復元する
            AkashicAgentCanary.verifyAndRestoreKlassWord(host, rec, preMod);
            proxy.restoreObliteratedCoreFields(host);
            ServerLevel level = host.level() instanceof ServerLevel sl ? sl : proxy.lastKnownLevel;
            if (level == null || !level.getServer().isRunning()) {
                continue;
            }
            if (host.getEprotect().isDespawning() || host.getEprotect().isDying() || !host.getEprotect().isProtectionActive()) {
                it.remove();
                proxy.identityLocked = false;
                proxy.strongHost = null;
                ACTIVE_CONTROLLER_LIST.remove(proxy);
                ACTIVE_CONTROLLER_SET.remove(proxy);
                ACTIVE_CONTROLLER_MAP.remove(entry.getKey(), proxy);
                continue;
            }

            // 既に Eprotect (WorldPresence.replaceWithClone / EprotectRegistry) が同一 UUID の新個体へ差し替えていた場合は、
            // 古い個体を引退させて新個体へバインドを移し、二重存在・二重復活を防止する
            Entity existingByUuid = level.getEntity(proxy.probeUuid);
            if (existingByUuid instanceof AkashicProbeEntity newProbe
                    && newProbe != host
                    && newProbe.getEprotect().isProtectionActive()) {
                newProbe.inheritRuntimeStateFrom(host);
                String replaceMod = resolveSuspectMod(host);
                rec.observe(replaceMod, "world_eviction:instance_replaced_by_clone", "REPAIRED",
                        "oldId=" + host.getId() + " -> newId=" + newProbe.getId(), null);
                proxy.rebindHost(newProbe);
                newProbe.nonEntityProxy = proxy;
                newProbe.madnessControllerProxy = proxy;
                try {
                    host.getEprotect().retireReplaced();
                } catch (Throwable ignored) {}
                newProbe.syncHologramAndUiNow();
                host = newProbe;
            }

            proxy.verifyAndRepair(host);
            String suspectMod = resolveSuspectMod(host);

            long chunkKey = ChunkPos.asLong(host.blockPosition());
            if (!level.areEntitiesLoaded(chunkKey)) {
                continue;
            }

            // A. EntityTickList からの直接除外検知
            var tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
            boolean inTickList = tickList.contains(host);
            if (!inTickList) {
                rec.observe(suspectMod, "world_eviction:entity_tick_list", "REPAIRED", "ServerLevel.entityTickList", null);
                if (level.getEntity(proxy.probeUuid) == host) {
                    try {
                        tickList.add(host);
                        inTickList = true;
                    } catch (Throwable ignored) {}
                }
            }

            // B. RemovalReason / dead フラグの直接書き換え検知（remove() / die() を通さない Unsafe / Reflection 直書き）
            if (((com.automatavalkyries.mixin.LivingEntityAccessor) host).automata_valkyries$isDead()) {
                rec.observe(suspectMod, "direct_field:dead_flag_true", "REPAIRED", "LivingEntity.dead=true", null);
            }
            Entity.RemovalReason rawReason = DirectRead.removalReason(host);
            if (rawReason != null) {
                rec.observe(suspectMod, "direct_field:removalReason:" + rawReason.name(), "REPAIRED", "Entity.removalReason", null);
                ((EntityAccessor) host).automata_valkyries$setRemovalReason(null);
            }

            // C. ワールド全層（KnownUuids / EntityLookup / SectionStorage / ChunkMap Tracker）からの追放・抹消検知と強制再突入
            EnumSet<WorldPresence.Issue> worldIssues = WorldPresence.inspect(level, host);
            if (!worldIssues.isEmpty()) {
                rec.observe(suspectMod, "world_eviction:world_presence_broken", "REPAIRED", worldIssues.toString(), null);
                // 本体自身が entityTickList から外されて baseTick() が回っていない場合でも、外側から保護修復（WorldPresence.rejoin 含む）を強制駆動する
                host.shadowCanary().tickObservation();
                host.getEprotect().tick();
                Entity currentInWorld = level.getEntity(proxy.probeUuid);
                if (currentInWorld instanceof AkashicProbeEntity newProbe && newProbe != host) {
                    newProbe.inheritRuntimeStateFrom(host);
                    rec.observe(suspectMod, "world_eviction:instance_replaced_by_clone", "REPAIRED",
                            "oldId=" + host.getId() + " -> newId=" + newProbe.getId(), null);
                    proxy.rebindHost(newProbe);
                    newProbe.nonEntityProxy = proxy;
                    newProbe.madnessControllerProxy = proxy;
                    try {
                        host.getEprotect().retireReplaced();
                    } catch (Throwable ignored) {}
                    newProbe.syncHologramAndUiNow();
                    host = newProbe;
                } else if (currentInWorld instanceof AkashicProbeEntity rejoinedProbe) {
                    rejoinedProbe.syncHologramAndUiNow();
                } else if (currentInWorld == null
                        && !((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager().isLoaded(proxy.probeUuid)) {
                    // WorldPresence.rejoin すら通らず、EntityManager からも完全に消えている場合の最終防衛線: 不死鳥（Phoenix）再受肉
                    AkashicProbeEntity reincarnated = proxy.reincarnatePhoenix(level, host, rec, suspectMod);
                    if (reincarnated != null) {
                        reincarnated.syncHologramAndUiNow();
                        host = reincarnated;
                    }
                }
            }

            // D. Tick 停止（Tick 一覧に居るのに baseTick が回っていない）検知と外部強制駆動
            // ※自 MOD の正当な時間停止（タイムルーラー・ロストカタストロフィーの TimeStop）中は誤検知しない
            if (com.automatavalkyries.entity.chrono.TimeStop.frozen(host)) {
                proxy.stagnantServerTicks = 0;
                proxy.lastObservedEntityTick = host.tickCount;
            } else if (host.tickCount == proxy.lastObservedEntityTick) {
                proxy.stagnantServerTicks++;
                if (proxy.stagnantServerTicks >= 2) {
                    rec.observe(suspectMod, "tick_suppression:entity_tick_halted", "REPAIRED",
                            "stagnantTicks=" + proxy.stagnantServerTicks, null);
                    try {
                        host.shadowCanary().tickObservation();
                        host.getEprotect().tick();
                    } catch (Throwable ignored) {}
                    proxy.stagnantServerTicks = 0;
                }
            } else {
                proxy.lastObservedEntityTick = host.tickCount;
                proxy.stagnantServerTicks = 0;
            }
        }
    }

    /**
     * 最終防衛線「不死鳥（Phoenix）再受肉」:
     * すべてのワールド内部マップから抹消され、既存インスタンスでの再参加すら妨害された場合でも、
     * 同一の {@code probeUuid} と {@link AkashicRecord} を引き継ぐ新個体を即座に生成・再配置する。
     */
    private AkashicProbeEntity reincarnatePhoenix(
            ServerLevel level,
            AkashicProbeEntity oldHost,
            AkashicRecord rec,
            String suspectMod
    ) {
        try {
            if (level.getEntity(probeUuid) != null
                    || ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager().isLoaded(probeUuid)) {
                return null;
            }
            AkashicProbeEntity fresh = ModEntities.AKASHIC_PROBE.get().create(level);
            if (fresh == null) {
                fresh = new AkashicProbeEntity(ModEntities.AKASHIC_PROBE.get(), level);
            }
            if (fresh.nonEntityProxy != null && fresh.nonEntityProxy != this) {
                fresh.nonEntityProxy.onHostRetired(fresh);
            }
            fresh.nonEntityProxy = this;
            fresh.madnessControllerProxy = this;
            Vec3 pos = lastSafePos != null ? lastSafePos : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
            fresh.setUUID(probeUuid);
            fresh.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            if (oldHost != null) {
                fresh.inheritRuntimeStateFrom(oldHost);
            }
            if (level.addFreshEntity(fresh)) {
                if (oldHost != null && oldHost != fresh) {
                    try {
                        oldHost.getEprotect().retireReplaced();
                    } catch (Throwable ignored) {}
                }
                rebindHost(fresh);
                fresh.nonEntityProxy = this;
                fresh.madnessControllerProxy = this;
                rec.observe(suspectMod, "phoenix_reincarnation:recreated_probe", "REPAIRED",
                        "Reincarnated AkashicProbeEntity with UUID " + probeUuid, null);
                return fresh;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    static String resolveSuspectMod(AkashicProbeEntity host) {
        if (host.getLastHurtByMob() != null) {
            String m = CallerAttribution.modOf(host.getLastHurtByMob().getClass());
            if (CallerAttribution.isForeign(m)) return m;
            net.minecraft.world.item.ItemStack held = host.getLastHurtByMob().getMainHandItem();
            if (!held.isEmpty()) {
                net.minecraft.resources.ResourceLocation itemKey =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
                if (itemKey != null && !CallerAttribution.MINECRAFT.equals(itemKey.getNamespace())) {
                    return itemKey.getNamespace();
                }
            }
        }
        AkashicRecord rec = AkashicRecord.of(host.getUUID());
        if (rec.lastForeignMod() != null) return rec.lastForeignMod();
        if (rec.lastObservedMod() != null) return rec.lastObservedMod();
        return CallerAttribution.UNKNOWN;
    }
}
