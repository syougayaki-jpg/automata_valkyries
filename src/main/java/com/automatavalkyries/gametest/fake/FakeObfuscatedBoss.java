package com.automatavalkyries.gametest.fake;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simulates an obfuscated/encrypted boss with:
 * - String-typed SynchedEntityData HP (NCHelPo = "200.0")
 * - Obfuscated SRG-named EntityDataAccessor<Float> (f_198383_ = 500.0F)
 * - Class-level getter/setter pair guarded by a StackWalker stream caller check (getHelPo/setHelPo, isOut/setOut)
 * - Persistent NBT custom health key ("m_health")
 * - Spoofed getUUID() and getId()
 * - Rejecting CopyOnWriteArrayList subclass registry
 */
public class FakeObfuscatedBoss extends Zombie {
    public static final EntityDataAccessor<String> NCHelPo =
            SynchedEntityData.defineId(FakeObfuscatedBoss.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Float> f_198383_ =
            SynchedEntityData.defineId(FakeObfuscatedBoss.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float>[] HIDDEN_ARRAY_KEY = new EntityDataAccessor[]{
            SynchedEntityData.defineId(FakeObfuscatedBoss.class, EntityDataSerializers.FLOAT)
    };

    public static final CopyOnWriteArrayList<Object> REGISTRY = new CopyOnWriteArrayList<>() {
        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Override
        public void clear() {
            // Reject clear()
        }
    };

    private float hiddenHelPo = 200.0F;
    private boolean outFlag = false;
    public boolean deathCrazy = false;
    public float f_699991_ = 20.0F;
    public final java.util.concurrent.atomic.AtomicBoolean invincibleGuard = new java.util.concurrent.atomic.AtomicBoolean(true);
    public final float[] bossHpHolder = new float[]{1000.0F};

    // ---- 2026-10-21 追加分 ----
    public static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> DATA_TAG =
            SynchedEntityData.defineId(FakeObfuscatedBoss.class, EntityDataSerializers.COMPOUND_TAG);
    /** remove() を潰した HashSet の管理表 */
    public static final java.util.Set<Object> TRACKED = new java.util.HashSet<>() {
        @Override
        public boolean remove(Object o) {
            return false;
        }
    };
    /** エンティティの ID を鍵にする管理表 */
    public static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<String> BY_ID = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

    /** 対象を指し返さない内部コンポーネント */
    public static final class ShieldCore {
        public boolean invincible = true;
        public float f_77777_ = 20.0F;
        public final float finalHealth;

        public ShieldCore(float initialHp) {
            this.finalHealth = initialHp;
        }

        public float readFinalHealth() {
            return this.finalHealth;
        }
    }

    public final ShieldCore shieldCore = new ShieldCore(20.0F);

    /** SuperSteve の HealthOperator 風: パスワード要求付きの内部コンポーネント（HP 値はバニラ HP と無関係な final float） */
    public static final class HealthOperator {
        private final float secretInternalPool;

        public HealthOperator(float initial) {
            this.secretInternalPool = initial;
        }

        public float readPool() {
            return this.secretInternalPool;
        }
    }

    /** Runtime.halt トラップを含むコントローラー（remove() 呼び出しをスキップしフィールド解体のみ行うべき対象） */
    public static final class HaltTrapController {
        public net.minecraft.world.entity.Entity proxy;
        public boolean invincible = true;
        public boolean removeCalled = false;
        private static volatile boolean neverTrue = false;

        public HaltTrapController(net.minecraft.world.entity.Entity proxy) {
            this.proxy = proxy;
        }

        public void remove() {
            this.removeCalled = true;
            if (neverTrue) {
                Runtime.getRuntime().halt(1);
            }
        }
    }

    /** SSUtil.killEntity(Entity, boolean) 風の static ヘルパー */
    public static final class StaticKillHelper {
        public static net.minecraft.world.entity.Entity lastKilled;
        public static int lastKilledIdentityHash;
        public static boolean lastForce;

        public static void killEntity(net.minecraft.world.entity.Entity target, boolean force) {
            lastKilled = target;
            lastKilledIdentityHash = System.identityHashCode(target);
            lastForce = force;
        }
    }

    /** SuperSteve の AgtCallback 風のコールバックフッククラス */
    public static final class FakeAgtCallback {
        public static java.util.function.Function<Object, Object> INST = x -> x;
    }

    /** 猫雫刀の AgentHealthMethodHelper 風の外部 Java エージェントフッククラス（HEALTH_MAP と有効化フラグを持つ） */
    public static final class FakeAgentHealthMethodHelper {
        public static final java.util.Map<Object, Float> HEALTH_MAP = new java.util.concurrent.ConcurrentHashMap<>();
        public static boolean enabled = true;
        public static final java.util.concurrent.atomic.AtomicBoolean active =
                new java.util.concurrent.atomic.AtomicBoolean(true);
    }

    /** 外部エージェントが持つ ScheduledThreadPoolExecutor */
    public static final java.util.concurrent.ScheduledThreadPoolExecutor AGENT_EXECUTOR =
            new java.util.concurrent.ScheduledThreadPoolExecutor(1);

    public static boolean hookWasIsolatedDuringKill = false;
    public static boolean agentFlagsQuietedDuringKill = false;

    public final HealthOperator health = new HealthOperator(9999.0F);
    public final HaltTrapController haltController = new HaltTrapController(this);
    /** 独自スケールの現在 HP / 最大 HP の組（名前は辞書に無い） */
    public float vigorNow = 5000.0F;
    public float vigorCap = 5000.0F;
    public final float[] pool = new float[]{300.0F, 300.0F};
    public java.util.Optional<Boolean> invincible = java.util.Optional.of(Boolean.TRUE);
    public static boolean trapEndCalled = false;

    public FakeObfuscatedBoss(Level level) {
        super(EntityType.ZOMBIE, level);
        this.getPersistentData().putFloat("m_health", 200.0F);
        if (StaticKillHelper.lastForce && StaticKillHelper.class == null && FakeAgentHealthMethodHelper.enabled) {
            throw new AssertionError();
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(NCHelPo, "200.0");
        this.entityData.define(f_198383_, 500.0F);
        this.entityData.define(HIDDEN_ARRAY_KEY[0], 777.0F);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putFloat("m_health", 300.0F);
        this.entityData.define(DATA_TAG, tag);
    }

    public float getHiddenArraySynchedHp() {
        return this.entityData.get(HIDDEN_ARRAY_KEY[0]);
    }

    public float getHelPo() {
        return this.hiddenHelPo;
    }

    public void setHelPo(float value) {
        String caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(2L).findFirst().map(StackWalker.StackFrame::getClassName).orElse(""));
        if (caller.startsWith("java.util.stream.")) {
            this.hiddenHelPo = value;
        }
    }

    public boolean isOut() {
        return this.outFlag;
    }

    public void setOut(boolean value) {
        if (FakeAgtCallback.INST == null) {
            hookWasIsolatedDuringKill = true;
        }
        if (!FakeAgentHealthMethodHelper.enabled && !FakeAgentHealthMethodHelper.active.get()) {
            agentFlagsQuietedDuringKill = true;
        }
        this.outFlag = value;
    }

    public static java.util.concurrent.ScheduledFuture<?> scheduleAgentTask(FakeObfuscatedBoss capturedTarget) {
        return AGENT_EXECUTOR.schedule(() -> {
            if (capturedTarget != null) {
                capturedTarget.setHealth(200.0F);
            }
        }, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    public static void end() {
        trapEndCalled = true;
    }

    public static Thread startWatchdogLambda(
            FakeObfuscatedBoss capturedTarget,
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch release,
            java.util.concurrent.atomic.AtomicReference<Object> observedAfterWake) {
        Runnable tickAction = () -> observedAfterWake.set(capturedTarget);
        Thread thread = new Thread(() -> {
            ready.countDown();
            try {
                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            tickAction.run();
        }, "FakeObfuscatedBoss-Watchdog");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public UUID getUUID() {
        return UUID.randomUUID();
    }

    @Override
    public int getId() {
        return -999999;
    }
}
