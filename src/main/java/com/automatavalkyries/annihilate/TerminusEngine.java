package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 終焉執行統合エンジン（Terminus Engine）。
 * <p>
 * MOB（兵姫等）およびアイテム（終焉の兵装等）から呼び出され、
 * チートMOB（Witherzilla, Metapotent Flashfur等）の汎用解体および
 * プレイヤー（クリエイティブ・無敵・チート防具着用含む）の絶対即死・終焉を執行する。
 * <p>
 * 終焉執行に対象が10秒（200 ticks / 10,000ms）以上耐えた場合、
 * Eprotect+ の強化処理を10秒間発動する。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TerminusEngine {

    /** 終焉耐性判定の閾値（ミリ秒: 10,000ms = 10秒） */
    public static final long RESISTANCE_THRESHOLD_MS = 10_000L;
    /** 終焉耐性判定の閾値（Ticks: 200 ticks = 10秒） */
    public static final long RESISTANCE_THRESHOLD_TICKS = 200L;
    private static final int[] RETRY_DELAYS_TICKS = {2, 10, 40};
    private static final int MAX_RETRIES_PER_TICK = 2;

    public static final class TargetRecord {
        public final UUID uuid;
        public final WeakReference<Entity> entityRef;
        public final long startTick;
        public final long startTimeMs;
        public final long startNanos = System.nanoTime();
        public volatile boolean reinforcementTriggered = false;
        private WeakReference<Entity> attackerRef;
        private int retries;
        private long nextRetryTick = Long.MAX_VALUE;

        public TargetRecord(Entity entity, long startTick, long startTimeMs) {
            this.uuid = entity.getUUID();
            this.entityRef = new WeakReference<>(entity);
            this.startTick = startTick;
            this.startTimeMs = startTimeMs;
        }

        public Entity getEntity() {
            return entityRef.get();
        }
    }

    private static final Map<UUID, TargetRecord> TRACKED_TARGETS = new ConcurrentHashMap<>();
    public static final int AREA_TARGETS_PER_TICK = 4;
    private static final long AREA_BUDGET_NANOS = 4_000_000L;
    private static final java.util.ArrayDeque<AreaJob> AREA_JOBS = new java.util.ArrayDeque<>();

    private static final class AreaJob {
        final ServerLevel level;
        final WeakReference<Entity> attacker;
        final double radius;
        final java.util.ArrayDeque<WeakReference<Entity>> targets = new java.util.ArrayDeque<>();
        final java.util.function.IntConsumer completed;
        int count;

        AreaJob(ServerLevel level, Entity attacker, double radius, List<Entity> targets,
                java.util.function.IntConsumer completed) {
            this.level = level;
            this.attacker = new WeakReference<>(attacker);
            this.radius = radius;
            targets.forEach(target -> this.targets.add(new WeakReference<>(target)));
            this.completed = completed;
        }
    }

    /** Loaded entities are selected once; repeated requests from the same attacker are coalesced. */
    public static boolean queueAreaTerminus(ServerLevel level, Entity attacker, double radius,
            boolean includePlayers, java.util.function.IntConsumer completed) {
        if (level == null || attacker == null || attacker.level() != level
                || !level.getServer().isSameThread() || !Double.isFinite(radius) || radius < 0) return false;
        for (AreaJob job : AREA_JOBS) {
            if (job.attacker.get() == attacker) return false;
        }
        List<Entity> targets = collectAreaTargets(level, attacker, radius, includePlayers);
        AreaJob job = new AreaJob(level, attacker, radius, targets, completed);
        if (com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
            job.count += MadnessAnnihilation.sweep(level);
        }
        job.count += GenericCheatMobNeutralizer.sweepStandaloneControllers(level, attacker, radius, false);
        AREA_JOBS.add(job);
        return true;
    }

    /**
     * 範囲内の全ターゲットエンティティ（AABB外の座標偽装プロキシ・攻撃エフェクトの所有者を含む）を収集し、
     * ボス本体や LivingEntity を VFX エンティティより優先して並べる。
     */
    private static List<Entity> collectAreaTargets(ServerLevel level, Entity attacker, double radius, boolean includePlayers) {
        UUID attackerUuid = attacker.getUUID();
        AABB box = attacker.getBoundingBox().inflate(radius);
        java.util.Set<Entity> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Entity> targets = new java.util.ArrayList<>();

        java.util.function.Predicate<Entity> eligible = e -> {
            if (e == null || e == attacker) return false;
            if (e.getUUID().equals(attackerUuid)) return false;
            if (e instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) return false;
            if (e instanceof Player && !includePlayers) return false;
            if (isProtectedByTinyCourage(e)) {
                AutomataValkyries.LOGGER.info("[Terminus] refused: {} holds Tiny Courage", e.getName().getString());
                return false;
            }
            return e.isAlive() || e instanceof LivingEntity || MadnessAnnihilation.isMadness(e);
        };

        // 1. 通常の AABB 空間検索
        for (Entity e : level.getEntities(attacker, box, eligible::test)) {
            if (seen.add(e)) {
                targets.add(e);
            }
        }

        // 2. 広域（radius >= 16）では、EntitySection から隠蔽されているプロキシや、
        //    position() オーバーライドにより基底 AABB と実座標が乖離しているプロキシ（MetapotentFlashfurEntity 等）も走査
        if (radius >= 16.0) {
            try {
                for (Entity e : level.getAllEntities()) {
                    if (e == null || seen.contains(e) || !eligible.test(e)) continue;
                    boolean inArea = box.intersects(e.getBoundingBox());
                    if (!inArea) {
                        try {
                            net.minecraft.world.phys.Vec3 pos = e.position();
                            if (pos != null && box.contains(pos)) {
                                inArea = true;
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (!inArea && isForeignBossProxyInArea(e, level, box)) {
                        inArea = true;
                    }
                    if (inArea && seen.add(e)) {
                        targets.add(e);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. 範囲内の攻撃エフェクト（MetaLightning 等）が保持する owner / caster フィールドから親ボスを逆引きして追加
        for (Entity e : new java.util.ArrayList<>(targets)) {
            if (e instanceof LivingEntity) continue;
            Entity owner = findForeignEffectOwner(e, level);
            if (owner != null && eligible.test(owner) && seen.add(owner)) {
                targets.add(owner);
            }
        }

        // 4. ボス本体・プロキシ・LivingEntity を VFX エンティティ（shockwave_render / explosion_render 等）より先に処理
        targets.sort(java.util.Comparator.comparingInt(TerminusEngine::targetPriority));
        return targets;
    }

    private static int targetPriority(Entity e) {
        if (e instanceof LivingEntity || MadnessAnnihilation.isMadness(e)) return 0;
        String name = e.getClass().getSimpleName();
        if (name.endsWith("FlashfurEntity") || name.contains("Boss") || name.contains("Proxy")) return 0;
        return 1;
    }

    private static boolean isForeignBossProxyInArea(Entity entity, ServerLevel level, AABB box) {
        Class<?> type = entity.getClass();
        if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                com.automatavalkyries.protect.attack.CallerAttribution.modOf(type))) {
            return false;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object core = f.get(entity);
                    if (core == null || core instanceof Entity) continue;
                    if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                            com.automatavalkyries.protect.attack.CallerAttribution.modOf(core.getClass()))) {
                        continue;
                    }
                    for (Class<?> cc = core.getClass(); cc != null && cc != Object.class; cc = cc.getSuperclass()) {
                        for (java.lang.reflect.Field cf : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(cc)) {
                            if (java.lang.reflect.Modifier.isStatic(cf.getModifiers())) continue;
                            cf.setAccessible(true);
                            Object val = cf.get(core);
                            if (val instanceof net.minecraft.world.phys.Vec3 vec && box.contains(vec)) {
                                return true;
                            }
                            if (val instanceof Entity combatTarget
                                    && GenericCheatMobNeutralizer.isCombatTargetField(cf.getName())
                                    && combatTarget.level() == level
                                    && box.intersects(combatTarget.getBoundingBox())) {
                                return true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static Entity findForeignEffectOwner(Entity effectEntity, ServerLevel level) {
        Class<?> type = effectEntity.getClass();
        if (!com.automatavalkyries.protect.attack.CallerAttribution.isForeign(
                com.automatavalkyries.protect.attack.CallerAttribution.modOf(type))) {
            return null;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        || !Entity.class.isAssignableFrom(f.getType())) continue;
                String lower = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("owner") || lower.equals("caster") || lower.equals("parent")
                        || lower.equals("summoner") || lower.equals("boss")) {
                    try {
                        f.setAccessible(true);
                        if (f.get(effectEntity) instanceof Entity owner && owner.level() == level) {
                            return owner;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private static void processAreaJobs() {
        long started = System.nanoTime();
        int processed = 0;
        while (!AREA_JOBS.isEmpty() && processed < AREA_TARGETS_PER_TICK
                && System.nanoTime() - started < AREA_BUDGET_NANOS) {
            AreaJob job = AREA_JOBS.removeFirst();
            Entity attacker = job.attacker.get();
            if (attacker == null || attacker.level() != job.level
                    || com.automatavalkyries.protect.DirectRead.isRemoved(attacker)) continue;
            WeakReference<Entity> reference = job.targets.pollFirst();
            if (reference != null) {
                processed++;
                Entity target = reference.get();
                try {
                    if (target != null && target.level() == job.level
                            && !com.automatavalkyries.protect.DirectRead.isRemoved(target)
                            && executeTerminus(job.level, attacker, target)) job.count++;
                } catch (RuntimeException failure) {
                    AutomataValkyries.LOGGER.warn("[TerminusEngine] Area target execution failed", failure);
                }
            }
            if (job.targets.isEmpty()) {
                try {
                    job.count += GenericCheatMobNeutralizer.sweepStandaloneControllers(job.level, attacker, job.radius, true);
                    AnnihilationExecutionAgent.sanitizeEventBus();
                } catch (Throwable ignored) {}
                try {
                    if (job.completed != null) job.completed.accept(job.count);
                } catch (RuntimeException failure) {
                    AutomataValkyries.LOGGER.warn("[TerminusEngine] Area completion notification failed", failure);
                }
            } else {
                AREA_JOBS.addLast(job);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        TRACKED_TARGETS.clear();
        AREA_JOBS.clear();
    }

    private TerminusEngine() {}

    private static final String TINY_COURAGE_CLASS_NAME = "com.automatavalkyries.item.TinyCourageItem";
    private static final String TINY_COURAGE_REGISTRY_ID = "automata_valkyries:tiny_courage";
    private static volatile boolean tinyCourageMethodProbed = false;
    private static volatile java.lang.reflect.Method tinyCourageProtectsMethod = null;

    /**
     * 『ちっぽけな勇気』（{@code automata_valkyries:tiny_courage}）による終焉拒絶判定。
     * <p>
     * プレイヤーが存命かつホットバー（{@code 0..8}）に「ちっぽけな勇気」を保持している場合、
     * 終焉の魔杖・エレノア・最果てのエレノアを含む当 MOD のあらゆる終焉執行から保護される。
     * </p>
     */
    public static boolean isProtectedByTinyCourage(@org.jetbrains.annotations.Nullable Entity target) {
        if (target == null) return false;
        // 1. TinyCourageItem.protects(Entity) が実装されていれば直接委譲
        try {
            if (!tinyCourageMethodProbed) {
                tinyCourageMethodProbed = true;
                Class<?> cls = Class.forName(TINY_COURAGE_CLASS_NAME, false, TerminusEngine.class.getClassLoader());
                tinyCourageProtectsMethod = cls.getMethod("protects", Entity.class);
            }
            java.lang.reflect.Method m = tinyCourageProtectsMethod;
            if (m != null && Boolean.TRUE.equals(m.invoke(null, target))) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            tinyCourageMethodProbed = false;
        } catch (Throwable ignored) {
        }

        // 2. フォールバック兼直接検査：存命の Player がホットバー（0..8）に automata_valkyries:tiny_courage を持つか
        if (target instanceof Player player
                && com.automatavalkyries.protect.DirectRead.hasHealth(player)
                && !com.automatavalkyries.protect.DirectRead.isRemoved(player)) {
            try {
                var items = player.getInventory().items;
                int limit = Math.min(9, items.size());
                for (int i = 0; i < limit; i++) {
                    var stack = items.get(i);
                    if (stack == null || stack.isEmpty()) continue;
                    var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null && TINY_COURAGE_REGISTRY_ID.equals(key.toString())) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * 単一の対象（プレイヤーまたはMOB）に対して終焉を執行する。
     *
     * @param level    サーバーワールド
     * @param attacker 攻撃者（プレイヤーまたはMOB）
     * @param target   対象エンティティ
     * @return 終焉執行に成功したか
     */
    public static boolean executeTerminus(ServerLevel level, Entity attacker, Entity target) {
        if (target == null || level == null || target.level() != level
                || !level.getServer().isSameThread()) return false;

        // 味方の保護対象（兵姫など）は誤爆防止（※検証用ダミーは終焉機構の保護対象外として執行を許可）
        if (target instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget()) {
            return false;
        }

        // 特権守護コア（Javaエージェント）で保護された味方プレイヤーは誤爆・耐性追跡防止
        if (com.automatavalkyries.protect.AgentPlayerProtection.isProtected(target)) {
            AutomataValkyries.LOGGER.info("[Terminus] Target player {} is protected by Java Agent Privilege Aegis; Terminus execution refused", target.getName().getString());
            return false;
        }

        // 『ちっぽけな勇気』（TinyCourageItem）をホットバーに持つ存命プレイヤーには終焉を適用しない
        if (isProtectedByTinyCourage(target)) {
            AutomataValkyries.LOGGER.info("[Terminus] refused: {} holds Tiny Courage", target.getName().getString());
            return false;
        }

        // ボスバー・HUD 追跡の強制事前破棄（侵略者のモノリス等のボスバー消え残りを防止するため、
        // 汎用解体やメモリ消去が走る前に真っ先に全プレイヤーから切り離す）
        AnnihilationExecutionAgent.clearBossEvents(target);

        // 終焉耐性トラッキングに対象を登録
        trackTarget(target, level);

        Player playerAttacker = attacker instanceof Player p ? p : null;

        // 1. プレイヤー対象の場合: TerminusPlayerExecutor による絶対死執行
        boolean confirmed = target instanceof ServerPlayer serverPlayer
                ? TerminusPlayerExecutor.executeOnPlayer(level, playerAttacker, serverPlayer)
                : TrueAnnihilationEngine.annihilateEntity(level, playerAttacker, target);
        if (!confirmed) scheduleRetry(target, attacker, level);
        return confirmed;
    }

    /**
     * 範囲内の全エンティティに対して同期で終焉を執行する。
     * 広域のアイテム操作は queueAreaTerminus を使い、Tick の処理予算を守る。
     *
     * @param level          サーバーワールド
     * @param attacker       発動者
     * @param radius         半径（ブロック）
     * @param includePlayers プレイヤーも終焉対象に含めるか
     * @return 終焉執行したエンティティ数
     */
    public static int executeAreaTerminus(ServerLevel level, Entity attacker, double radius, boolean includePlayers) {
        if (level == null || attacker == null || attacker.level() != level
                || !level.getServer().isSameThread() || !Double.isFinite(radius) || radius < 0) return 0;
        UUID attackerUuid = attacker.getUUID();
        List<Entity> targets = collectAreaTargets(level, attacker, radius, includePlayers);

        int count = com.automatavalkyries.protect.SpecificCountermeasures.enabled()
                ? MadnessAnnihilation.sweep(level) : 0;
        for (Entity target : targets) {
            if (target.getUUID().equals(attackerUuid)) continue;
            if (executeTerminus(level, attacker, target)) {
                count++;
            }
        }
        count += GenericCheatMobNeutralizer.sweepStandaloneControllers(level, attacker, radius, true);
        AnnihilationExecutionAgent.sanitizeEventBus();

        AutomataValkyries.LOGGER.info("[TerminusEngine] Area terminus executed by {} in radius {}: {} eliminated",
                attacker.getName().getString(), radius, count);
        return count;
    }

    // ---- 終焉耐性追跡・Eprotect+ 強化処理発動 ----------------------------------

    public static void trackTarget(Entity target, ServerLevel level) {
        if (target == null || isProtectedByTinyCourage(target)) return;
        long currentTick = level != null && level.getServer() != null ? level.getServer().getTickCount() : 0;
        TRACKED_TARGETS.compute(target.getUUID(), (uuid, previous) ->
                previous != null && previous.getEntity() == target ? previous
                        : new TargetRecord(target, currentTick, System.currentTimeMillis()));
    }

    private static void scheduleRetry(Entity target, Entity attacker, ServerLevel level) {
        if (target == null || isProtectedByTinyCourage(target)) return;
        TargetRecord record = TRACKED_TARGETS.get(target.getUUID());
        if (record == null || record.getEntity() != target || record.nextRetryTick != Long.MAX_VALUE) return;
        record.attackerRef = new WeakReference<>(attacker);
        record.nextRetryTick = level.getServer().getTickCount() + RETRY_DELAYS_TICKS[0];
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkResistanceAll();
        processAreaJobs();
    }

    /**
     * 終焉執行対象の生存状況を点検し、10秒以上耐えている対象がいれば Eprotect+ 強化処理を10秒間実行する。
     */
    public static void checkResistanceAll() {
        if (TRACKED_TARGETS.isEmpty()) return;
        long nowNanos = System.nanoTime();
        int retriesThisTick = 0;

        Iterator<Map.Entry<UUID, TargetRecord>> it = TRACKED_TARGETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetRecord> entry = it.next();
            TargetRecord record = entry.getValue();
            Entity entity = record.getEntity();

            // 対象が消滅（GC 済み・ワールドから削除・非生存）している場合、または『ちっぽけな勇気』で保護された場合は追跡解除
            if (entity == null || isProtectedByTinyCourage(entity) || (entity instanceof ServerPlayer player
                    ? TerminusPlayerExecutor.isPlayerProperlyKilled(player)
                    : EprotectPlusLink.isAnnihilationConfirmed(entity))) {
                it.remove();
                continue;
            }

            if (record.nextRetryTick != Long.MAX_VALUE && retriesThisTick < MAX_RETRIES_PER_TICK
                    && entity.level() instanceof ServerLevel level
                    && level.getServer().getTickCount() >= record.nextRetryTick) {
                // Retry the same individual only. A replacement with another UUID belongs to the spawn guard.
                if ((entity instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget())
                        || com.automatavalkyries.protect.AgentPlayerProtection.isProtected(entity)
                        || isProtectedByTinyCourage(entity)
                        || (!(entity instanceof ServerPlayer) && level.getEntity(entity.getUUID()) != entity)) {
                    it.remove();
                    continue;
                }
                retriesThisTick++;
                record.nextRetryTick = Long.MAX_VALUE;
                try {
                    Entity attacker = record.attackerRef == null ? null : record.attackerRef.get();
                    if (entity instanceof ServerPlayer player) {
                        TerminusPlayerExecutor.verifyAndRemediatePlayerDeath(level, player);
                    } else {
                        TrueAnnihilationEngine.annihilateEntity(level, attacker instanceof Player p ? p : null, entity);
                    }
                } catch (RuntimeException failure) {
                    AutomataValkyries.LOGGER.warn("[TerminusEngine] Retry failed for {}", entity.getName().getString(), failure);
                }
                if (entity instanceof ServerPlayer player
                        ? TerminusPlayerExecutor.isPlayerProperlyKilled(player)
                        : EprotectPlusLink.isAnnihilationConfirmed(entity)) {
                    it.remove();
                    continue;
                }
                record.retries++;
                if (record.retries < RETRY_DELAYS_TICKS.length) {
                    record.nextRetryTick = level.getServer().getTickCount() + RETRY_DELAYS_TICKS[record.retries];
                }
            }

            long elapsedMs = (nowNanos - record.startNanos) / 1_000_000L;
            long currentTick = entity.level().getServer() != null ? entity.level().getServer().getTickCount() : 0;
            long elapsedTicks = currentTick > 0 ? (currentTick - record.startTick) : (elapsedMs / 50L);

            // 10秒（200 ticks または 10,000ms）以上耐えている場合
            if ((elapsedTicks >= RESISTANCE_THRESHOLD_TICKS || elapsedMs >= RESISTANCE_THRESHOLD_MS)
                    && !record.reinforcementTriggered) {
                if (!EprotectPlusLink.isReinforcedActive()) {
                    // Eprotect+ 強化処理を 10 秒間実行！
                    EprotectPlusLink.triggerTerminusResistedReinforcement(entity);
                    record.reinforcementTriggered = true;
                    AutomataValkyries.LOGGER.warn("[TerminusEngine] Entity {} resisted Terminus for >= 10s (elapsed: {} ticks, {} ms)! Triggered Eprotect+ 10-second reinforced protection.",
                            entity.getName().getString(), elapsedTicks, elapsedMs);
                }
            }
        }
    }

    /**
     * 手動またはシミュレーションで特定対象の耐性強化処理を発動させる（テスト用）。
     */
    public static boolean triggerResistedManually(Entity target) {
        if (target == null) return false;
        TargetRecord record = TRACKED_TARGETS.get(target.getUUID());
        if (record != null) {
            record.reinforcementTriggered = true;
        }
        EprotectPlusLink.triggerTerminusResistedReinforcement(target);
        return true;
    }

    public static boolean hasTrackedTarget(UUID uuid) {
        return TRACKED_TARGETS.containsKey(uuid);
    }

    public static int getTrackedTargetCount() {
        return TRACKED_TARGETS.size();
    }

    public static void untrackTarget(UUID uuid) {
        if (uuid != null) {
            TRACKED_TARGETS.remove(uuid);
        }
    }

    public static void clearTrackedTargets() {
        TRACKED_TARGETS.clear();
    }
}
