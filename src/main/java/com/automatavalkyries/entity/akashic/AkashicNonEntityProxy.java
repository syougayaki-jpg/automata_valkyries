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
        this.strongHost = host;
        if (host.level() instanceof ServerLevel sl) {
            this.lastKnownLevel = sl;
        }
        this.lastSafePos = host.position();
        this.entityLink = host;
        this.proxyTrueLife = new AkashicShadowCanary.TrueLifeCanaryImpl(100.0F);
        captureCoreFieldSnapshot(host);
        AkashicAgentCanary.captureBaseline(host);
        registerStaticCanaries();
        GUARDIANS.put(this.probeUuid, this);
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
        } catch (Throwable ignored) {}
        return repairedAny;
    }

    private void registerStaticCanaries() {
        if (!ACTIVE_CONTROLLER_LIST.contains(this)) ACTIVE_CONTROLLER_LIST.add(this);
        if (!ACTIVE_CONTROLLER_SET.contains(this)) ACTIVE_CONTROLLER_SET.add(this);
        ACTIVE_CONTROLLER_MAP.put(probeUuid, this);
    }

    public static void unregister(UUID uuid) {
        AkashicNonEntityProxy proxy = GUARDIANS.remove(uuid);
        if (proxy != null) {
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
        if (host == null || host.getUUID() == null) return;
        UUID old = this.probeUuid;
        if (old != null) {
            GUARDIANS.remove(old);
            ACTIVE_CONTROLLER_MAP.remove(old);
        }
        this.probeUuid = host.getUUID();
        this.probeStringUuid = this.probeUuid.toString();
        GUARDIANS.put(this.probeUuid, this);
        rebindHost(host);
    }

    public void rebindHost(AkashicProbeEntity host) {
        this.hostRef = new WeakReference<>(host);
        this.strongHost = host;
        this.expectedEntityId = host.getId();
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
     * サーバー Tick からの外部独立監視:
     * 本体（{@link AkashicProbeEntity}）が {@code entityTickList} から抜かれたり、{@code removalReason} を直書きされたりして
     * 自力で {@code baseTick()} を回せなくなった場合でも、外側から検知・記録・修復する。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GUARDIANS.isEmpty()) return;
        for (var it = GUARDIANS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AkashicNonEntityProxy proxy = entry.getValue();
            AkashicProbeEntity host = proxy.strongHost != null ? proxy.strongHost : (proxy.hostRef != null ? proxy.hostRef.get() : null);
            if (host == null) {
                continue;
            }
            AkashicRecord rec = AkashicRecord.of(proxy.probeUuid);
            String preMod = rec.lastForeignMod() != null ? rec.lastForeignMod() : CallerAttribution.UNKNOWN;
            // 0. 仮想メソッドを呼ぶ前に、Klass Word / Mark Word / 全構造フィールドの null 化・UUID/ID 改ざんを外側から検証・即時復元する
            AkashicAgentCanary.verifyAndRestoreKlassWord(host, rec, preMod);
            proxy.restoreObliteratedCoreFields(host);
            ServerLevel level = host.level() instanceof ServerLevel sl ? sl : proxy.lastKnownLevel;
            if (level == null) {
                continue;
            }
            if (host.getEprotect().isDespawning() || host.getEprotect().isDying()) {
                it.remove();
                proxy.strongHost = null;
                ACTIVE_CONTROLLER_LIST.remove(proxy);
                ACTIVE_CONTROLLER_SET.remove(proxy);
                ACTIVE_CONTROLLER_MAP.remove(entry.getKey());
                continue;
            }

            proxy.verifyAndRepair(host);
            String suspectMod = resolveSuspectMod(host);

            // A. EntityTickList からの直接除外検知
            var tickList = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList();
            boolean inTickList = tickList.contains(host);
            if (!inTickList && level.getEntity(proxy.probeUuid) == host) {
                rec.observe(suspectMod, "world_eviction:entity_tick_list", "REPAIRED", "ServerLevel.entityTickList", null);
                try {
                    tickList.add(host);
                    inTickList = true;
                } catch (Throwable ignored) {}
            }

            // B. RemovalReason の直接書き換え検知（remove() を通さない Unsafe / Reflection 直書き）
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
                    proxy.rebindHost(newProbe);
                    host = newProbe;
                } else if (currentInWorld == null) {
                    // WorldPresence.rejoin すら通らなかった場合の最終防衛線: 同一 UUID・同一記録を引き継ぐ不死鳥（Phoenix）再受肉
                    AkashicProbeEntity reincarnated = proxy.reincarnatePhoenix(level, rec, suspectMod);
                    if (reincarnated != null) {
                        host = reincarnated;
                    }
                }
            }

            // D. Tick 停止（Tick 一覧に居るのに baseTick が回っていない）検知と外部強制駆動
            if (host.tickCount == proxy.lastObservedEntityTick) {
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
    private AkashicProbeEntity reincarnatePhoenix(ServerLevel level, AkashicRecord rec, String suspectMod) {
        try {
            AkashicProbeEntity fresh = ModEntities.AKASHIC_PROBE.get().create(level);
            if (fresh == null) {
                fresh = new AkashicProbeEntity(ModEntities.AKASHIC_PROBE.get(), level);
            }
            Vec3 pos = lastSafePos != null ? lastSafePos : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
            fresh.setUUID(probeUuid);
            fresh.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            if (level.addFreshEntity(fresh)) {
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
        }
        String lastAttackerMod = AkashicRecord.of(host.getUUID()).lastForeignMod();
        return lastAttackerMod != null ? lastAttackerMod : CallerAttribution.UNKNOWN;
    }
}
