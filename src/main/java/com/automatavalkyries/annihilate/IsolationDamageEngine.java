package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.SuperKillStickItem;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.GuardLists;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.util.UnsafeHelper;
import com.automatavalkyries.weapon.DeathLootFallback;
import com.automatavalkyries.weapon.Overload;
import com.automatavalkyries.weapon.WeaponDamage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 複合・特殊仕様ダメージ「隔絶ダメージ（Isolation Damage）」エンジン。
 * <p>
 * オーバーロード化した兵装（{@link Overload#holding(LivingEntity)}）によるあらゆる攻撃に付与され、
 * 以下の 3 つの特殊ダメージ機構の長所を Java エージェント不要で複合統合する：
 * </p>
 * <ol>
 *   <li><b>Fantasy Ending 方式（カスタム HP 動的特定 ＋ {@code hurt()} 迂回被弾パイプライン）</b>:
 *       対象クラスの {@code HEALTH}/{@code HP} 系 {@link EntityDataAccessor} スキャンおよび動的値照合により、
 *       バニラ {@code DATA_HEALTH_ID} 以外の独自 HP データ項目も特定して内部値を直接書き換える。
 *       また {@code hurt()} がキャンセル・無効化された場合でも、被弾演出・ノックバック・ヘイト・統計・HP 減算を強制遂行する。</li>
 *   <li><b>SuperSteve 方式（{@link SynchedEntityData} 層の読み書きクランプ）</b>:
 *       {@link SynchedEntityData#get} / {@link SynchedEntityData#set} および {@link LivingEntity#getHealth} / {@link LivingEntity#setHealth}
 *       の最優先（{@code priority = 1}）フックにより、隔絶侵食された実効最大 HP（{@code effectiveMaxHp}）を超える自己回復・値固定を封殺する。</li>
 *   <li><b>The Trial Monolith 方式（実数値＋割合の二軸侵食と 3 段階フェーズ処刑）</b>:
 *       <ul>
 *         <li><b>Phase 1（0% &lt; 侵食率 &lt; 100%：隔絶侵食）</b>: 与ダメージ分だけ最大 HP の天井自体を永続的に削り取る。</li>
 *         <li><b>Phase 2（侵食率 &ge; 100%：隔絶致死）</b>: {@code getHealth() -> 0}、{@code isDeadOrDying() -> true}、{@code isAlive() -> false} に固定し、
 *             不死保護リストからのパージ・{@code die()}・{@link DeathLootFallback} によるドロップ保証・AI 完全消去を実行する。</li>
 *         <li><b>Phase 3（侵食率 &ge; 200% または致死後の死体居座り追撃：隔絶消滅）</b>:
 *             {@code tickDeath} キャンセル等でワールドに居座る不死ボスを {@link AnnihilationExecutionAgent} でチャンク・TickList から直接抹消する。</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class IsolationDamageEngine {

    /** 1 ヒットあたりの最低保証割合侵食（1% = 0.01F） */
    public static final float MIN_RATIO_STEP = 0.01F;
    private static final float FINITE_HP_THRESHOLD = 1_000_000.0F;

    /** 隔絶ダメージ専用のダメージタイプ（魂ダメージの死亡メッセージに上書きされないよう専用キーを使用） */
    public static final ResourceKey<DamageType> ISOLATION_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "isolation"));

    /**
     * オブジェクト参照同一性（{@code ==} / {@link System#identityHashCode}）で比較する弱参照キー。
     * <p>
     * バニラの {@link Entity#equals} / {@link Entity#hashCode} は {@code entityId} で比較するため、
     * リスポーン時に {@code PlayerList.respawn} が {@code newPlayer.setId(oldPlayer.getId())} を呼ぶと、
     * 通常の {@link java.util.WeakHashMap} では新しい {@link ServerPlayer} が古い死亡個体の {@link IsolationState} にヒットしてしまう。
     * 参照同一性キーを使うことで、リスポーン後の新個体や別個体に古い隔絶状態が絶対に引き継がれないようにする。
     * </p>
     */
    private static final class IdentityWeakKey extends WeakReference<LivingEntity> {
        private final int hash;

        IdentityWeakKey(LivingEntity referent, @Nullable ReferenceQueue<LivingEntity> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof IdentityWeakKey other)) return false;
            LivingEntity a = this.get();
            LivingEntity b = other.get();
            return a != null && a == b;
        }
    }

    private static final ReferenceQueue<LivingEntity> STATE_REF_QUEUE = new ReferenceQueue<>();
    /** 個体インスタンス（参照同一性）ごとの隔絶侵食状態 */
    private static final Map<IdentityWeakKey, IsolationState> STATES = new ConcurrentHashMap<>();
    /** 段階 6（防御解体・抹殺）が有効化されたプレイヤー UUID（統合サーバーのクライアントスレッド LocalPlayer からも即時参照可能にする） */
    private static final Set<UUID> TIER6_SUPPRESSED_PLAYER_UUIDS = ConcurrentHashMap.newKeySet();
    /** 隔絶ダメージ（Tier 5 または Tier 6）によって死亡確定したプレイヤー UUID（リスポーン完了まで LocalPlayer 含め HP 0.0F / 死亡状態を維持する） */
    private static final Set<UUID> CONFIRMED_DEAD_PLAYER_UUIDS = ConcurrentHashMap.newKeySet();
    /**
     * 安全弁（Safety Valve）が発動したプレイヤー UUID と、その時点で干渉を停止した最大ティア番号（5 または 6）。
     * 万一死亡確定不能・リスポーン不能に陥った場合、該当ティア以下の当 MOD 側の干渉を完全に停止して次のティアへ移行させる。
     */
    private static final Map<UUID, Integer> SAFETY_VALVE_BYPASSED_TIER = new ConcurrentHashMap<>();
    /** ElenorPressure.strike 実行中に安全弁が発動した場合の目標ティア予約（strike 末尾の二重加算を防ぎ確実に +1 ティア進める） */
    private static final Map<UUID, Integer> PENDING_SAFETY_VALVE_TARGET_TIER = new ConcurrentHashMap<>();
    /** 外部 MOD のインベントリ検査を無力化するための空ダミーインベントリ（プレイヤー個体単位） */
    private static final Map<IdentityWeakKey, Inventory> EMPTY_MASK_INVENTORIES = new ConcurrentHashMap<>();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Boolean> IN_INVENTORY_MASK = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 安全弁による直接 PlayerList.respawn 実行中フラグ（Clone / Respawn イベントで SAFETY_VALVE_BYPASSED_TIER が消えないようにする） */
    private static final ThreadLocal<Boolean> IN_SAFETY_VALVE_RESPAWN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** クラスごとのカスタム HP EntityDataAccessor ID キャッシュ */
    private static final Map<Class<?>, int[]> CUSTOM_HEALTH_IDS_CACHE = new ConcurrentHashMap<>();

    /** SynchedEntityData.DataItem.value フィールド（リフレクション直書き用） */
    private static final Field DATA_ITEM_VALUE_FIELD = resolveDataItemValueField();

    /** hurt() 再帰やネスト呼び出しを追跡するスレッドローカルフレーム */
    private static final ThreadLocal<Deque<HitContext>> HIT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /** クランプ処理中の再入防止フラグ */
    private static final ThreadLocal<Boolean> IN_INTERNAL_WRITE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private IsolationDamageEngine() {}

    /** 個体ごとの隔絶侵食状態 */
    public static final class IsolationState {
        /** 初期基準上限 HP（最初の隔絶被弾時点の有効上限） */
        public float baseCeiling = Float.NaN;
        /** 累積実数侵食ダメージ */
        public float flatIsolation = 0.0F;
        /** 累積割合侵食ダメージ（1.0F = 100%、2.0F = 200%） */
        public float ratioIsolation = 0.0F;
        /** 現在の実効上限 HP */
        public float effectiveMaxHp = Float.POSITIVE_INFINITY;
        /** 隔絶フェーズ（0: 未侵食, 1: 隔絶侵食, 2: 隔絶致死, 3: 隔絶消滅） */
        public volatile int phase = 0;
        /** 致死（Phase 2）到達後の経過 Tick 数 */
        public int deadTicks = 0;
        /** 致死（Phase 2）到達後に他 MOD が dead=false 等へ巻き戻しリスポーン不能になっている連続 Tick 数 */
        public int unrespawnableTicks = 0;
        /**
         * 対象（プレイヤーまたはエンティティ）に対する段階 6（抹殺・防御解体）が解禁されたか。
         * <p>
         * {@code false}（エレノアの段階 5 隔絶ダメージ時点）では、対象の実効上限 HP は最低 {@code 1.0F} に留まり、
         * 致死ライン到達時はバニラ {@code die()} のみを試行する（不死 MOD 保持者が段階 5 で HP=0 かつ生存のまま固まるのを防ぎ、
         * {@code die()} が拒否された場合は HP 1.0F を維持して段階 6 の抹殺へ進行させる）。
         * オーバーロード兵装による攻撃や、エレノアの段階 6 到達時には {@code true} となり Phase 2/3 の防御解体・抹殺が解禁される。
         * </p>
         */
        public volatile boolean tier6DefenseBroken = false;
        /** 対象が隔絶ダメージ（Tier 5 または Tier 6）によって実際に死亡完了したか（HP 0.0F / dead=true を保持） */
        public volatile boolean playerConfirmedDead = false;
        /** 最後に隔絶ダメージを与えた攻撃者（20 Tick 死亡演出完了後の自動消去用） */
        @Nullable
        public LivingEntity lastAttacker;
        /** 最後に隔絶ダメージを与えた DamageSource（20 Tick 死亡演出完了後の自動消去用） */
        @Nullable
        public DamageSource lastSource;

        public synchronized float computeEffectiveMaxHp(LivingEntity entity) {
            boolean allowZeroFloor = tier6DefenseBroken || playerConfirmedDead;
            if ((phase >= 2 || playerConfirmedDead) && allowZeroFloor) {
                effectiveMaxHp = 0.0F;
                return 0.0F;
            }
            float rawMax = getSafeMaxHealth(entity);
            float startCap = Float.isNaN(baseCeiling) ? rawMax : Math.min(rawMax, baseCeiling);
            float byFlat = startCap - flatIsolation;
            float byRatio = rawMax * (1.0F - Math.min(1.0F, ratioIsolation));
            float minFloor = allowZeroFloor ? 0.0F : 1.0F;
            float eff = Math.max(minFloor, Math.min(byFlat, byRatio));
            effectiveMaxHp = eff;
            return eff;
        }

        /** 実数侵食または割合侵食が 100%（本来なら HP 0 以下）の致死ラインに到達しているか */
        public synchronized boolean isLethalErosionReached(LivingEntity entity) {
            float rawMax = getSafeMaxHealth(entity);
            float startCap = Float.isNaN(baseCeiling) ? rawMax : Math.min(rawMax, baseCeiling);
            float byFlat = startCap - flatIsolation;
            float byRatio = rawMax * (1.0F - Math.min(1.0F, ratioIsolation));
            return Math.min(byFlat, byRatio) <= 0.0F || ratioIsolation >= 1.0F;
        }
    }

    /** 1 回の hurt() 呼び出しの追跡コンテキスト */
    public static final class HitContext {
        public final LivingEntity victim;
        public final LivingEntity attacker;
        public final DamageSource source;
        public final float rawAmount;
        public final float preHealth;
        public final int preDeathStatCount;
        public float expectedDamage;
        public boolean completed;

        HitContext(LivingEntity victim, LivingEntity attacker, DamageSource source, float rawAmount, float expectedDamage, float preHealth, int preDeathStatCount) {
            this.victim = victim;
            this.attacker = attacker;
            this.source = source;
            this.rawAmount = rawAmount;
            this.expectedDamage = expectedDamage;
            this.preHealth = preHealth;
            this.preDeathStatCount = preDeathStatCount;
        }
    }

    private static int getPlayerDeathStatCount(@Nullable Player player) {
        if (player instanceof ServerPlayer sp) {
            try {
                return sp.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /**
     * 保護対象（当 MOD のエレノア・クロノゲイザー等の IEprotected、および戦術兵姫 ValkyrieEntity）を除外し、
     * オーバーロード兵装保持者による有効な攻撃かどうかを判定する。
     */
    public static boolean isEligibleOverloadHit(@Nullable DamageSource source, @Nullable LivingEntity victim, float amount) {
        if (source == null || victim == null || amount <= 0.0F || !Float.isFinite(amount)) {
            return false;
        }
        if (isProtectedTarget(victim)) {
            return false;
        }
        Entity causing = source.getEntity();
        if (!(causing instanceof LivingEntity attacker) || attacker == victim) {
            return false;
        }
        return Overload.holding(attacker);
    }

    /** 当 MOD の保護対象（IEprotected / ValkyrieEntity）かどうか（保護対象には Eprotect の被弾上限を優先する） */
    public static boolean isProtectedTarget(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof ValkyrieEntity) return true;
        if (entity instanceof IEprotected p) {
            return !p.isTerminusTestTarget() || (p.getEprotect() != null && p.getEprotect().isProtectionActive());
        }
        return false;
    }

    /** 隔絶ダメージの基本期待値（オーバーロード倍率込み・防具減衰なし）を算出する */
    public static float computeExpectedIsolationDamage(DamageSource source, LivingEntity attacker, float rawAmount) {
        if (rawAmount <= 0.0F || !Float.isFinite(rawAmount)) return 0.0F;
        if (source.is(WeaponDamage.OVERLOAD_REND)) {
            return rawAmount;
        }
        return rawAmount * Overload.damageMult(attacker.getMainHandItem().getItem());
    }

    /**
     * {@code LivingEntity.hurt} の先頭（{@code priority = 1}）で呼ばれるフック。
     */
    @Nullable
    public static HitContext onHurtHead(LivingEntity victim, DamageSource source, float amount) {
        if (victim.level().isClientSide() || Boolean.TRUE.equals(IN_INTERNAL_WRITE.get())) {
            return null;
        }
        if (!isEligibleOverloadHit(source, victim, amount)) {
            return null;
        }
        LivingEntity attacker = (LivingEntity) source.getEntity();
        // 無敵時間を即座に解除（イベントがキャンセルされても貫通できるよう最優先で 0 にする）
        victim.invulnerableTime = 0;

        float expected = computeExpectedIsolationDamage(source, attacker, amount);
        float preHp = readRawOrEffectiveHealth(victim);
        int preDeathStats = (victim instanceof Player p) ? getPlayerDeathStatCount(p) : -1;
        HitContext ctx = new HitContext(victim, attacker, source, amount, expected, preHp, preDeathStats);
        HIT_STACK.get().push(ctx);
        return ctx;
    }

    /**
     * {@link Overload#onHurt} で倍率が乗った直後のダメージ量（防具逆算で水増しされる前の正確な値）を記録する。
     */
    public static void recordMultipliedDamage(LivingEntity victim, float multipliedAmount) {
        if (multipliedAmount <= 0.0F || !Float.isFinite(multipliedAmount)) return;
        Deque<HitContext> stack = HIT_STACK.get();
        for (HitContext ctx : stack) {
            if (ctx.victim == victim && !ctx.completed) {
                ctx.expectedDamage = multipliedAmount;
                break;
            }
        }
    }

    /**
     * {@code LivingEntity.hurt} の末尾（{@code priority = 1}）で呼ばれるフック。
     * バニラの {@code hurt()} が成功したかキャンセルされたかを問わず、隔絶侵食を確定させる。
     *
     * @return 隔絶ダメージが適用された場合は {@code true}
     */
    public static boolean onHurtReturn(LivingEntity victim, boolean vanillaResult) {
        Deque<HitContext> stack = HIT_STACK.get();
        if (stack.isEmpty()) return vanillaResult;
        HitContext top = stack.peek();
        if (top == null || top.victim != victim) return vanillaResult;
        stack.pop();
        if (top.completed) return vanillaResult;
        top.completed = true;
        return applyIsolationHit(top, vanillaResult);
    }

    /**
     * 攻撃者がエレノアであり、かつ対象に対するペナルティティアが段階 6（抹殺・防御解体）以上に達しているか判定する。
     */
    private static boolean isElenorAtTier6OrHigher(@Nullable LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof com.automatavalkyries.entity.ElenorEntity elenor) {
            com.automatavalkyries.entity.ElenorArts arts = elenor.arts();
            if (arts != null) {
                return arts.pressure().tier(victim) >= 6;
            }
        }
        return false;
    }

    /**
     * 攻撃者がエレノアであり、かつ対象に対するペナルティティアが段階 6 未満（段階 5 隔絶ダメージ）か判定する。
     */
    private static boolean isElenorBelowTier6(@Nullable LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof com.automatavalkyries.entity.ElenorEntity elenor) {
            com.automatavalkyries.entity.ElenorArts arts = elenor.arts();
            return arts == null || arts.pressure().tier(victim) < 6;
        }
        return false;
    }

    /**
     * {@code hurt()} を独自オーバーライドして {@code super.hurt()} を呼ばない特殊ボス（{@code InvaderMonolithEntity} 等）や、
     * エレノアの段階 5（隔絶ダメージ）・段階 6（抹殺・防御解体）ペナルティ等の直接呼び出しから隔絶ダメージを強制適用する。
     * <p>
     * 攻撃者がオーバーロード兵装を保持している場合はオーバーロード倍率を適用し、
     * エレノア等の非オーバーロード攻撃者が直接呼び出した場合は {@code rawAmount} をそのまま隔絶ダメージとして適用する。
     * </p>
     */
    public static boolean ensureIsolatedAttack(LivingEntity attacker, LivingEntity victim, DamageSource source, float rawAmount) {
        if (victim == null || victim.level().isClientSide() || source == null
                || rawAmount <= 0.0F || !Float.isFinite(rawAmount)
                || isProtectedTarget(victim) || attacker == null || attacker == victim) {
            return false;
        }
        if (victim instanceof Player player) {
            IsolationState existing = getState(player);
            int currentTier = (existing != null && existing.tier6DefenseBroken) ? 6 : 5;
            if (isSafetyValveBypassed(player, currentTier)) {
                return false;
            }
        } else if (isElenorAtTier6OrHigher(attacker, victim)) {
            ServerLevel serverLevel = victim.level() instanceof ServerLevel sl ? sl : null;
            breakEntityDefenses(serverLevel, victim);
        }
        victim.invulnerableTime = 0;
        float expected = Overload.holding(attacker)
                ? computeExpectedIsolationDamage(source, attacker, rawAmount)
                : rawAmount;
        float preHp = readRawOrEffectiveHealth(victim);
        int preDeathStats = (victim instanceof Player p) ? getPlayerDeathStatCount(p) : -1;
        boolean vanillaHurtSucceeded = false;
        if (victim instanceof Player || !Overload.holding(attacker)) {
            // 隔絶 DamageSource でまず通常の hurt() を通す
            // （通常のプレイヤーやエンティティであればバニラ hurt() -> die() で正常死亡する）
            DamageSource isoSource = createIsolationDamageSource(victim, attacker, source);
            IN_INTERNAL_WRITE.set(Boolean.TRUE);
            try {
                vanillaHurtSucceeded = victim.hurt(isoSource, expected);
            } catch (Throwable ignored) {
            } finally {
                IN_INTERNAL_WRITE.set(Boolean.FALSE);
            }
        }
        HitContext ctx = new HitContext(victim, attacker, source, rawAmount, expected, preHp, preDeathStats);
        ctx.completed = true;
        return applyIsolationHit(ctx, vanillaHurtSucceeded);
    }

    /**
     * 隔絶ダメージの中核適用処理（Phase 1: 侵食 -> Phase 2: 致死 -> Phase 3: 消滅）。
     */
    private static boolean applyIsolationHit(HitContext ctx, boolean vanillaHurtSucceeded) {
        LivingEntity victim = ctx.victim;
        if (victim.level().isClientSide() || isProtectedTarget(victim)) {
            return vanillaHurtSucceeded;
        }
        float damage = ctx.expectedDamage;
        if (damage <= 0.0F || !Float.isFinite(damage)) {
            return vanillaHurtSucceeded;
        }

        // StateScanner 等が dead フラグを書き換える前のバニラ死亡状態を記録
        // ※バニラの ServerPlayer.die() は super.die() を呼ばず this.dead を true に設定しないため、
        //   Stats.DEATHS の増加でもバニラ hurt() 内での正常死亡完了を検出する
        boolean diedDuringVanillaHurt = (victim instanceof Player p)
                && ctx.preDeathStatCount >= 0
                && getPlayerDeathStatCount(p) > ctx.preDeathStatCount;
        if (diedDuringVanillaHurt && victim instanceof Player p) {
            markPlayerIsolationDead(p);
        }
        boolean wasVanillaDeadBeforeHit = ((LivingEntityAccessor) victim).automata_valkyries$isDead()
                || victim.isRemoved()
                || diedDuringVanillaHurt;

        // カスタム HP アクセサを初回にプローブしてキャッシュ
        probeAndCacheCustomHealthIds(victim);

        IsolationState state = getOrCreateState(victim);
        if (!(victim instanceof Player)) {
            if (isElenorAtTier6OrHigher(ctx.attacker, victim)) {
                ServerLevel serverLevel = victim.level() instanceof ServerLevel sl ? sl : null;
                breakEntityDefenses(serverLevel, victim);
            } else if (!isElenorBelowTier6(ctx.attacker, victim)) {
                // オーバーロード兵装等の攻撃では Phase 2/3（隔絶致死・隔絶消滅）を解禁
                state.tier6DefenseBroken = true;
            }
            if (wasVanillaDeadBeforeHit) {
                state.playerConfirmedDead = true;
            }
        }
        boolean allowFatalPhase = state.tier6DefenseBroken || state.playerConfirmedDead;
        boolean wasAlreadyDeadPhase = (allowFatalPhase && state.phase >= 2) || victim.isDeadOrDying();
        float maxHp = getSafeMaxHealth(victim);
        boolean finiteNormalHp = Float.isFinite(maxHp) && maxHp > 0.0F && maxHp <= FINITE_HP_THRESHOLD;
        boolean lifeDepletedByHit = Float.isFinite(ctx.preHealth) && ctx.preHealth > 0.0F && (ctx.preHealth - damage) <= 0.0F;

        float targetHp;
        int currentPhase;
        synchronized (state) {
            if (Float.isNaN(state.baseCeiling)) {
                if (finiteNormalHp && Float.isFinite(ctx.preHealth) && ctx.preHealth > 0.0F && ctx.preHealth <= maxHp) {
                    state.baseCeiling = ctx.preHealth;
                } else {
                    state.baseCeiling = maxHp;
                }
            }

            if (finiteNormalHp) {
                float minFlat = maxHp * MIN_RATIO_STEP;
                state.flatIsolation += Math.max(damage, minFlat);
                state.ratioIsolation += Math.max(damage / maxHp, MIN_RATIO_STEP);
            } else {
                state.flatIsolation += damage;
                state.ratioIsolation += MIN_RATIO_STEP;
            }

            targetHp = state.computeEffectiveMaxHp(victim);
            if (allowFatalPhase && (wasAlreadyDeadPhase || state.ratioIsolation >= 2.0F)) {
                if (targetHp <= 0.0F || state.ratioIsolation >= 1.0F || lifeDepletedByHit) {
                    state.phase = 3;
                }
            } else if (allowFatalPhase && (targetHp <= 0.0F || state.ratioIsolation >= 1.0F || lifeDepletedByHit)) {
                state.phase = 2;
                state.effectiveMaxHp = 0.0F;
                targetHp = 0.0F;
            } else if (state.phase < 1) {
                state.phase = 1;
            }
            state.lastAttacker = ctx.attacker;
            state.lastSource = ctx.source;
            currentPhase = state.phase;
        }

        // 他 MOD（Trial Monolith の SoulDamage / Fantasy Ending の feGetHealth / SuperSteve の hurtOffset）への互換ブリッジ同期
        bridgeExternalSpecialDamage(victim, state, maxHp, targetHp);

        if (currentPhase >= 3 && allowFatalPhase) {
            executePhase3IsolationRemoval(victim, ctx.attacker, ctx.source, wasVanillaDeadBeforeHit);
            return true;
        } else if (currentPhase == 2 && allowFatalPhase) {
            executePhase2IsolationDeath(victim, ctx.attacker, ctx.source, vanillaHurtSucceeded, wasVanillaDeadBeforeHit);
            return true;
        } else {
            executePhase1IsolationErosion(victim, ctx.attacker, ctx.source, damage, ctx.preHealth, targetHp, vanillaHurtSucceeded, wasVanillaDeadBeforeHit);
            return true;
        }
    }

    /**
     * 【Phase 1：隔絶侵食】
     * 実効上限 HP（{@code targetHp}）まで現在 HP を強制引き下げ、{@code hurt()} がブロックされていた場合は被弾反応を直接実行する。
     * プレイヤーおよびエンティティの双方で、ライフが致死ライン（HP 0 以下または侵食 100%）に達した場合は {@code victim.die()} を呼び出す。
     */
    private static void executePhase1IsolationErosion(LivingEntity victim, LivingEntity attacker, DamageSource source,
                                                      float damage, float preHealth, float targetHp,
                                                      boolean vanillaHurtSucceeded, boolean wasVanillaDeadBeforeHit) {
        // バニラの hurt() で既に死亡している場合は上書きして蘇生させない
        if (wasVanillaDeadBeforeHit || ((LivingEntityAccessor) victim).automata_valkyries$isDead() || victim.isRemoved()) {
            return;
        }
        IsolationState state = getState(victim);
        boolean isTier5Target = state == null || !state.tier6DefenseBroken;
        float minFloor = isTier5Target ? 1.0F : 0.0F;
        float desiredHp = targetHp;
        if (!vanillaHurtSucceeded && Float.isFinite(preHealth) && preHealth > 0.0F) {
            desiredHp = Math.max(minFloor, Math.min(targetHp, preHealth - damage));
        }

        float currentHp = readRawSynchedHealth(victim);
        boolean neededForceWrite = !vanillaHurtSucceeded
                || !Float.isFinite(currentHp)
                || currentHp > desiredHp + 0.001F;

        if (neededForceWrite) {
            forceWriteHealth(victim, desiredHp);
        }

        if (!vanillaHurtSucceeded) {
            forceApplyHurtEffects(victim, attacker, source, damage);
        }

        // プレイヤー・エンティティ双方に対し、ライフまたは隔絶侵食が致死ライン（HP 削り切り）に到達していたら die() を呼び出す
        boolean lethalReached = desiredHp <= 0.0F
                || targetHp <= 0.0F
                || readRawSynchedHealth(victim) <= 0.0F
                || victim.getHealth() <= 0.0F
                || (state != null && state.isLethalErosionReached(victim));
        if (!lethalReached) {
            return;
        }

        DamageSource deathSource = createIsolationDamageSource(victim, attacker, source);
        victim.getCombatTracker().recordDamage(deathSource, getSafeMaxHealth(victim));

        if (victim instanceof Player player) {
            if (state != null && !state.tier6DefenseBroken) {
                int prevDeaths = getPlayerDeathStatCount(player);
                // die() 発火時（LivingDeathEvent / ClientboundPlayerCombatKillPacket 送信時）に
                // サーバー・クライアント双方で HP 0 を確実にするため、die() の前に DATA_HEALTH_ID を 0.0F に書き戻す
                forceWriteHealth(player, 0.0F);
                IN_INTERNAL_WRITE.set(Boolean.TRUE);
                try {
                    player.die(deathSource);
                } catch (Throwable ignored) {
                } finally {
                    IN_INTERNAL_WRITE.set(Boolean.FALSE);
                }
                boolean diedCleanly = (prevDeaths >= 0 && getPlayerDeathStatCount(player) > prevDeaths)
                        || ((LivingEntityAccessor) player).automata_valkyries$isDead();
                if (diedCleanly) {
                    markPlayerIsolationDead(player);
                    if (player instanceof ServerPlayer sp && sp.getUUID() != null) {
                        try {
                            com.automatavalkyries.network.ModNetwork.CHANNEL.send(
                                    net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                                    new com.automatavalkyries.network.TerminusPlayerDeathPacket(sp.getUUID(), sp.getId()));
                        } catch (Throwable ignored) {}
                    }
                } else {
                    // die() が他 MOD（IMDS 等）にキャンセルされた場合は半死（HP=0 かつ dead=false）にせず 1.0F に留め、ティア 6 へ進行させる
                    state.playerConfirmedDead = false;
                    if (player.getUUID() != null) {
                        CONFIRMED_DEAD_PLAYER_UUIDS.remove(player.getUUID());
                    }
                    ((LivingEntityAccessor) player).automata_valkyries$setDead(false);
                    forceWriteHealth(player, Math.max(1.0F, targetHp));
                }
            }
        } else {
            forceWriteHealth(victim, 0.0F);
            // LivingEntity.die() 内の `if (!this.isRemoved() && !this.dead)` を確実に通過させるため dead=false にしてから呼ぶ
            ((LivingEntityAccessor) victim).automata_valkyries$setDead(false);
            IN_INTERNAL_WRITE.set(Boolean.TRUE);
            try {
                victim.die(deathSource);
            } catch (Throwable ignored) {
            } finally {
                IN_INTERNAL_WRITE.set(Boolean.FALSE);
            }
            boolean diedCleanly = ((LivingEntityAccessor) victim).automata_valkyries$isDead() || victim.isRemoved();
            if (diedCleanly) {
                if (state != null) {
                    state.playerConfirmedDead = true;
                    state.phase = Math.max(state.phase, 2);
                    state.effectiveMaxHp = 0.0F;
                }
                forceWriteHealth(victim, 0.0F);
            } else if (state != null && !state.tier6DefenseBroken) {
                // 段階 5 で他 MOD の不死機構により die() が拒否・キャンセルされた場合は、
                // HP 0（isAlive() == false）のまま生存してエレノアの攻撃対象から外れるのを防ぐため HP 1.0F に留め、段階 6（抹殺）へ進行させる
                state.playerConfirmedDead = false;
                ((LivingEntityAccessor) victim).automata_valkyries$setDead(false);
                forceWriteHealth(victim, Math.max(1.0F, targetHp));
            }
        }
    }

    /**
     * 【Phase 2：隔絶致死】
     * 侵食率 100%（{@code effectiveMaxHp <= 0}）到達時（※エレノアからは段階 6 の抹殺解禁後のみ到達）。
     * プレイヤー用と非プレイヤー（エンティティ）用の致死・抹殺処理へそれぞれ振り分ける。
     */
    private static void executePhase2IsolationDeath(LivingEntity victim, LivingEntity attacker, DamageSource source,
                                                    boolean vanillaHurtSucceeded, boolean wasVanillaDeadBeforeHit) {
        if (victim instanceof Player player) {
            executePlayerPhase2IsolationDeath(player, attacker, source, wasVanillaDeadBeforeHit);
        } else {
            executeEntityPhase2IsolationDeath(victim, attacker, source, vanillaHurtSucceeded, wasVanillaDeadBeforeHit);
        }
    }

    /**
     * プレイヤーに対する Phase 2（段階 6 抹殺解禁後の隔絶致死）処理。
     */
    private static void executePlayerPhase2IsolationDeath(Player player, LivingEntity attacker, DamageSource source,
                                                          boolean wasVanillaDeadBeforeHit) {
        GuardLists.purge(player);
        com.automatavalkyries.protect.EprotectImdsCounter.counter(player);
        com.automatavalkyries.protect.EcaLastSwordHelper.forceClearEcaAndTlsProtection(player);
        com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);
        StateScanner.apply(player, StateScanner.BREAK_PLAYER_DEFENSES, EnumSet.allOf(StateScanner.Source.class));
        TerminusPlayerExecutor.markPlayerTerminusDead(player);

        forceWriteHealth(player, 0.0F);
        enablePlayerTier6Kill(player);

        DamageSource deathSource = createIsolationDamageSource(player, attacker, source);
        player.getCombatTracker().recordDamage(deathSource, getSafeMaxHealth(player));
        boolean alreadyDead = wasVanillaDeadBeforeHit || ((LivingEntityAccessor) player).automata_valkyries$isDead();
        int prevDeaths = getPlayerDeathStatCount(player);
        // 既にバニラの hurt() 内で正常死亡していればアイテムドロップ済みのため、追加の強制死亡のみ補完
        if (!alreadyDead) {
            IN_INTERNAL_WRITE.set(Boolean.TRUE);
            try {
                com.automatavalkyries.protect.EprotectImdsCounter.counter(player);
                player.die(deathSource);
            } catch (Throwable ignored) {
            } finally {
                IN_INTERNAL_WRITE.set(Boolean.FALSE);
            }
        }
        // ※バニラの ServerPlayer.die() は super.die() を呼ばず this.dead を true に設定しないため、
        //   Stats.DEATHS の増加または alreadyDead によって正常死亡完了を判定する
        boolean deathCompleted = alreadyDead
                || (prevDeaths >= 0 && getPlayerDeathStatCount(player) > prevDeaths)
                || ((LivingEntityAccessor) player).automata_valkyries$isDead();
        if (!deathCompleted) {
            // 万一他 MOD によってバニラ die() が拒否・キャンセルされた場合、
            // 強制的に dead=true / HP=0 にするとリスポーン不能状態に陥るため、
            // 安全弁を発動してこちら側の干渉を辞め、エレノアの次のティア（Tier 7）へ移行する
            triggerSafetyValveAndAdvanceTier(player, "player.die() rejected at Isolation Phase 2");
            return;
        }
        try {
            markPlayerIsolationDead(player);
            com.automatavalkyries.protect.EprotectImdsCounter.counter(player);
            StateScanner.apply(player, StateScanner.RELEASE_DEAD_PLAYER, EnumSet.allOf(StateScanner.Source.class));
            com.automatavalkyries.protect.EcaLastSwordHelper.releaseDeadPlayerForRespawn(player);
            com.automatavalkyries.mixin.EntityAccessor accessor = (com.automatavalkyries.mixin.EntityAccessor) player;
            if (accessor.automata_valkyries$getRemovalReason() != null) {
                accessor.automata_valkyries$setRemovalReason(null);
            }
            if (player instanceof ServerPlayer sp) {
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                        new com.automatavalkyries.network.TerminusPlayerDeathPacket(sp.getUUID(), sp.getId()));
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 非プレイヤー（エンティティ）に対する Phase 2（隔絶致死・段階 6 抹殺）処理。
     * <ul>
     *   <li>エレノアの段階 6 攻撃による致死到達時は、防御解体＋超絶抹殺（{@link #executeEntityTier6Annihilation}）を即時執行する。</li>
     *   <li>オーバーロード兵装の Phase 2 到達時は、防御解体・HP 0 固定・{@code die()}・{@link DeathLootFallback}・AI 停止を行い、
     *       万一それでも死亡状態が拒否された場合は {@link #executeEntityTier6Annihilation} で強制抹殺する。</li>
     * </ul>
     */
    private static void executeEntityPhase2IsolationDeath(LivingEntity victim, LivingEntity attacker, DamageSource source,
                                                          boolean vanillaHurtSucceeded, boolean wasVanillaDeadBeforeHit) {
        ServerLevel serverLevel = victim.level() instanceof ServerLevel sl ? sl : null;
        breakEntityDefenses(serverLevel, victim);
        forceWriteHealth(victim, 0.0F);

        DamageSource deathSource = createIsolationDamageSource(victim, attacker, source);
        victim.getCombatTracker().recordDamage(deathSource, getSafeMaxHealth(victim));
        if (!vanillaHurtSucceeded) {
            forceApplyHurtEffects(victim, attacker, deathSource, getSafeMaxHealth(victim));
        }

        if (!wasVanillaDeadBeforeHit) {
            // bridgeExternalSpecialDamage / StateScanner.BREAK_TARGET が先に dead=true を書き込んでいると
            // バニラの LivingEntity.die() 冒頭の `if (!this.isRemoved() && !this.dead)` で弾かれてしまうため、
            // 一旦 dead=false に戻してから正規の victim.die(deathSource) を呼び出す
            ((LivingEntityAccessor) victim).automata_valkyries$setDead(false);
            IN_INTERNAL_WRITE.set(Boolean.TRUE);
            try {
                victim.die(deathSource);
            } catch (Throwable ignored) {
            } finally {
                IN_INTERNAL_WRITE.set(Boolean.FALSE);
            }
        }

        if (!((LivingEntityAccessor) victim).automata_valkyries$isDead()) {
            ((LivingEntityAccessor) victim).automata_valkyries$setDead(true);
            if (serverLevel != null) {
                DeathLootFallback.drop(serverLevel, victim, deathSource, attacker);
                serverLevel.broadcastEntityEvent(victim, (byte) 3);
            }
            victim.setPose(Pose.DYING);
        }

        // Trial Monolith の onSoulDeath と同様に Brain / Goal / Target / AI を完全停止
        clearEntityAiAndBrain(victim);

        // エレノアの段階 6 ペナルティ、または Phase 2 致死を拒否して生存し続ける個体には段階 6 抹殺を即時執行する
        boolean resistedDeath = !victim.isRemoved()
                && (!((LivingEntityAccessor) victim).automata_valkyries$isDead() || victim.getHealth() > 0.0F);
        if (serverLevel != null && !victim.isRemoved()
                && (attacker instanceof com.automatavalkyries.entity.ElenorEntity || resistedDeath)) {
            executeEntityTier6Annihilation(serverLevel, attacker, victim, false);
        }
    }

    /**
     * 【Phase 3：隔絶消滅】
     * Phase 2 で致死判定になった後も {@code tickDeath} キャンセル等でワールドに居座る不死個体を、
     * 段階 6 抹殺（{@link #executeEntityTier6Annihilation}）によってチャンク・{@code EntityTickList}・{@code EntityLookup} から直接抹消する。
     */
    private static void executePhase3IsolationRemoval(LivingEntity victim, LivingEntity attacker, DamageSource source) {
        executePhase3IsolationRemoval(victim, attacker, source, ((LivingEntityAccessor) victim).automata_valkyries$isDead());
    }

    private static void executePhase3IsolationRemoval(LivingEntity victim, LivingEntity attacker, DamageSource source,
                                                      boolean wasVanillaDeadBeforeHit) {
        executePhase2IsolationDeath(victim, attacker, source, false, wasVanillaDeadBeforeHit);
        if (victim instanceof Player) {
            // プレイヤーはブラックリスト抹殺せず（overloadT4PlayerTakesDamageNotAnnihilated 準拠）、死亡状態を維持
            return;
        }
        if (victim.level() instanceof ServerLevel serverLevel && !victim.isRemoved()) {
            executeEntityTier6Annihilation(serverLevel, attacker, victim, false);
        }
    }

    /**
     * 非プレイヤー（エンティティ）に対する段階 6 の防御解体処理。
     * <p>
     * プレイヤー向けの {@link TerminusPlayerExecutor#breakPlayerDefensesOnly} に対応するエンティティ用の防御解体であり、
     * 段階 6 解禁フラグ（{@code tier6DefenseBroken = true}）、バニラ無敵解除、静的保護リスト（{@link GuardLists}）パージ、
     * 個別 MOD カウンター（{@link com.automatavalkyries.protect.KillModCounters#counterAll}）、
     * 準汎用防御剥奪（{@link StateScanner#BREAK_TARGET}）、および保護バフ・吸収量の除去を一括で行う。
     * </p>
     */
    public static void breakEntityDefenses(@Nullable ServerLevel level, @Nullable LivingEntity target) {
        if (target == null || target instanceof Player || isProtectedTarget(target)) {
            return;
        }
        IsolationState state = getOrCreateState(target);
        state.tier6DefenseBroken = true;
        try {
            target.invulnerableTime = 0;
            target.setInvulnerable(false);
        } catch (Throwable ignored) {}
        try {
            GuardLists.purge(target);
        } catch (Throwable ignored) {}
        try {
            com.automatavalkyries.protect.KillModCounters.counterAll(target);
        } catch (Throwable ignored) {}
        try {
            StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
        } catch (Throwable ignored) {}
        try {
            for (net.minecraft.world.effect.MobEffectInstance effect : new java.util.ArrayList<>(target.getActiveEffects())) {
                var effectKey = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                if (effectKey != null && FlagKillPredicates.protectiveOnTarget(effectKey.getPath())) {
                    target.removeEffect(effect.getEffect());
                }
            }
            target.setAbsorptionAmount(0.0F);
        } catch (Throwable ignored) {}
    }

    /**
     * 非プレイヤー（エンティティ）に対する段階 6（抹殺・防御解体）の統合執行入口。
     * <p>
     * エレノアの段階 6 ペナルティ、隔絶ダメージ Phase 3（消滅）、およびオーバーロード T4 兵装の抹殺から共通で呼び出され、
     * 以下の順序でエンティティを確実に抹殺する：
     * </p>
     * <ol>
     *   <li>{@link #breakEntityDefenses(ServerLevel, LivingEntity)} による無敵・保護リスト・特殊防御フラグの先行解体</li>
     *   <li>{@link AnnihilationExecutionAgent#annihilateEntity}（非生物の場合は {@link TrueAnnihilationEngine#annihilateEntity}）による
     *       防御構造解析（{@link ModProtectionBreaker}）・ドロップ補完（{@link DeathLootFallback}）・追跡切断・区画排除</li>
     *   <li>{@link SuperKillStickItem#refuseRejoin(Entity)} による同一固体のワールド再参加拒否</li>
     * </ol>
     */
    public static boolean executeEntityTier6Annihilation(ServerLevel level, @Nullable Entity attacker, Entity target, boolean playSound) {
        if (target == null || level == null || target.level() != level
                || target == attacker || target instanceof Player || isProtectedTarget(target)) {
            return false;
        }
        if (target instanceof LivingEntity livingTarget) {
            breakEntityDefenses(level, livingTarget);
            IsolationState state = getOrCreateState(livingTarget);
            state.phase = 3;
            state.effectiveMaxHp = 0.0F;
            state.playerConfirmedDead = true;
            AnnihilationExecutionAgent.annihilateEntity(level, attacker, livingTarget, playSound);
        } else {
            TrueAnnihilationEngine.annihilateEntity(level, attacker instanceof Player p ? p : null, target);
        }
        if (!(target instanceof IEprotected p && p.getEprotect() != null && p.getEprotect().isProtectionActive())) {
            SuperKillStickItem.refuseRejoin(target);
        }
        return true;
    }

    /**
     * Fantasy Ending（{@code EntityActuallyHurt}）＋ SuperSteve（{@code SSUtil.forceHurt}）の複合：
     * {@code hurt()} がキャンセルされた場合でも、被弾アニメーション・ノックバック・統計・ヘイト・閃光エフェクトを強制実行する。
     */
    private static void forceApplyHurtEffects(LivingEntity victim, LivingEntity attacker, DamageSource source, float damage) {
        try {
            if (victim.isSleeping() && !victim.level().isClientSide()) {
                victim.stopSleeping();
            }
            victim.invulnerableTime = 0;
            victim.hurtDuration = 10;
            victim.hurtTime = 10;
            victim.hurtMarked = true;
            victim.walkAnimation.setSpeed(1.5F);

            victim.getCombatTracker().recordDamage(source, damage);

            if (!source.is(DamageTypeTags.NO_ANGER)) {
                victim.setLastHurtByMob(attacker);
            }
            if (attacker instanceof Player player) {
                victim.setLastHurtByPlayer(player);
            } else if (attacker instanceof TamableAnimal tamable && tamable.isTame() && tamable.getOwner() instanceof Player owner) {
                victim.setLastHurtByPlayer(owner);
            }

            if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                double dx = attacker.getX() - victim.getX();
                double dz = attacker.getZ() - victim.getZ();
                while (dx * dx + dz * dz < 1.0E-4D) {
                    dx = (Math.random() - Math.random()) * 0.01D;
                    dz = (Math.random() - Math.random()) * 0.01D;
                }
                victim.knockback(0.4F, dx, dz);
                victim.indicateDamage(dx, dz);
            }

            victim.level().broadcastDamageEvent(victim, source);
            victim.gameEvent(GameEvent.ENTITY_DAMAGE);

            if (victim.level() instanceof ServerLevel serverLevel) {
                Overload.triggerHitFlash(serverLevel, victim);
            }

            if (victim instanceof ServerPlayer serverVictim) {
                CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(serverVictim, source, damage, damage, false);
            }
            if (attacker instanceof ServerPlayer serverAttacker) {
                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(serverAttacker, victim, source, damage, damage, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Trial Monolith の {@code onSoulDeath} 相当の AI・Brain 完全無力化 */
    private static void clearEntityAiAndBrain(LivingEntity victim) {
        try {
            victim.getBrain().clearMemories();
        } catch (Throwable ignored) {
        }
        if (victim instanceof Mob mob) {
            try {
                mob.goalSelector.removeAllGoals(goal -> {
                    try {
                        goal.stop();
                    } catch (Throwable ignored) {
                    }
                    return true;
                });
                mob.targetSelector.removeAllGoals(goal -> {
                    try {
                        goal.stop();
                    } catch (Throwable ignored) {
                    }
                    return true;
                });
                mob.setTarget(null);
                mob.setNoAi(true);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * バニラ {@code DATA_HEALTH_ID} および動的特定したカスタム HP {@code DataItem} に直接値を書き込む。
     */
    public static void forceWriteHealth(LivingEntity entity, float targetHp) {
        if (Boolean.TRUE.equals(IN_INTERNAL_WRITE.get())) return;
        IN_INTERNAL_WRITE.set(Boolean.TRUE);
        try {
            float clamped = Math.max(0.0F, targetHp);
            EntityDataAccessor<Float> vanillaKey = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            SynchedEntityData synchedData = entity.getEntityData();
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Object> rawVanillaKey = (EntityDataAccessor<Object>) (EntityDataAccessor<?>) vanillaKey;
            StateScanner.writeSynchedValueDirectAndStandard(synchedData, rawVanillaKey, clamped);
            if (synchedData instanceof SynchedEntityDataAccessor accessor) {
                Int2ObjectMap<SynchedEntityData.DataItem<?>> items = accessor.automata_valkyries$getItemsById();
                if (items != null) {
                    // バニラ HP DataItem 直書き
                    SynchedEntityData.DataItem<?> vanillaItem = items.get(vanillaKey.getId());
                    if (vanillaItem != null) {
                        writeDataItemValueRaw(vanillaItem, clamped);
                        vanillaItem.setDirty(true);
                    }
                    // カスタム HP DataItem 直書き（Float / Double 両対応）
                    int[] customIds = CUSTOM_HEALTH_IDS_CACHE.get(entity.getClass());
                    if (customIds != null) {
                        for (int id : customIds) {
                            SynchedEntityData.DataItem<?> item = items.get(id);
                            if (item == null) continue;
                            Object current = item.getValue();
                            if (current instanceof Float) {
                                writeDataItemValueRaw(item, clamped);
                                item.setDirty(true);
                            } else if (current instanceof Double) {
                                writeDataItemValueRaw(item, (double) clamped);
                                item.setDirty(true);
                            }
                        }
                    }
                    accessor.automata_valkyries$setDirty(true);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            IN_INTERNAL_WRITE.set(Boolean.FALSE);
        }
    }

    /**
     * Fantasy Ending の {@code checkAndSave0_class} を改良したカスタム HP {@link EntityDataAccessor} 自動特定。
     */
    private static void probeAndCacheCustomHealthIds(LivingEntity entity) {
        Class<?> clazz = entity.getClass();
        if (clazz == LivingEntity.class || clazz == Player.class || clazz == ServerPlayer.class
                || CUSTOM_HEALTH_IDS_CACHE.containsKey(clazz)) {
            return;
        }
        IntList foundIds = new IntArrayList();
        int vanillaId = LivingEntityAccessor.automata_valkyries$getDataHealthId().getId();

        // 1. クラス階層から HEALTH / HP という名前を持つ EntityDataAccessor フィールドを抽出
        for (Class<?> c = clazz; c != null && LivingEntity.class.isAssignableFrom(c) && c != LivingEntity.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || !EntityDataAccessor.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                String norm = f.getName().toUpperCase(Locale.ROOT).replace("_", "");
                if ((norm.contains("HEALTH") || norm.endsWith("HP")) && !norm.contains("MAX")) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(null);
                        if (val instanceof EntityDataAccessor<?> acc && acc.getId() != vanillaId && !foundIds.contains(acc.getId())) {
                            foundIds.add(acc.getId());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        // 2. SynchedEntityData.itemsById 内で現在 HP または最大 HP と一致している Float/Double 項目を検出
        try {
            SynchedEntityData data = entity.getEntityData();
            if (data instanceof SynchedEntityDataAccessor accessor) {
                Int2ObjectMap<SynchedEntityData.DataItem<?>> items = accessor.automata_valkyries$getItemsById();
                float currentHp = entity.getHealth();
                float maxHp = entity.getMaxHealth();
                if (items != null && Float.isFinite(currentHp) && currentHp > 0.0F) {
                    for (Int2ObjectMap.Entry<SynchedEntityData.DataItem<?>> entry : items.int2ObjectEntrySet()) {
                        int id = entry.getIntKey();
                        if (id == vanillaId || foundIds.contains(id)) continue;
                        SynchedEntityData.DataItem<?> item = entry.getValue();
                        if (item == null) continue;
                        Object v = item.getValue();
                        if (v instanceof Float fv && (Math.abs(fv - currentHp) < 0.001F || Math.abs(fv - maxHp) < 0.001F)) {
                            foundIds.add(id);
                        } else if (v instanceof Double dv && (Math.abs(dv - currentHp) < 0.001D || Math.abs(dv - maxHp) < 0.001D)) {
                            foundIds.add(id);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        CUSTOM_HEALTH_IDS_CACHE.put(clazz, foundIds.toIntArray());
    }

    /**
     * 特定ダメージ専用仕様（魂ダメージ・侵食ゲージ・カスタム HP・専用バリア・負の最大 HP デルタ・直前 HP スナップショット等）に対する
     * 準汎用侵食・突破処理（{@link StateScanner} 6 ソース＋{@link FlagDictionary} 連動）。
     * <p>
     * 個別 MOD のクラス名に依存せず、以下の 4 パターンを全 6 ソース（{@code INTERFACE}, {@code INJECTED_FIELD},
     * {@code OWN_FIELD}, {@code CAPABILITY}, {@code PERSISTENT}, {@code SYNCHED_DATA}）にわたり自動侵食する：
     * </p>
     * <ol>
     *   <li><b>専用バリア・保護フラグ</b>（{@link FlagKillPredicates#protectiveOnTarget} / {@code GUARD} / {@code REVIVE}）：
     *       Phase 1（侵食開始）から {@code false} に解除し、残り復活回数（{@link FlagKillPredicates#livesCounter}）も削る。</li>
     *   <li><b>負の最大 HP デルタ</b>（{@code healthDelta}, {@code maxHealthMod} 等）：
     *       {@code -(maxHp - targetHp)} を書き込み、外部 MOD の実効最大 HP 計算式も連動して引き下げる。</li>
     *   <li><b>蓄積侵食・魂ダメージ・オフセットゲージ</b>（{@code soulDamage}, {@code hurtOffset}, {@code erosion}, {@code corruption} 等）：
     *       侵食率 {@code ratioIsolation}（{@code 0.0 -> 0.9999F}）または実数侵食量 {@code flatIsolation} を書き込み、
     *       特定ダメージでしか削れないゲージを準汎用で進行させる（※他 MOD の即死フックが死亡メッセージを横取りしないよう比率は {@code 0.9999F} 上限）。</li>
     *   <li><b>独自 HP・直前 HP スナップショット</b>（{@code customHealth}, {@code realHp}, {@code shieldHp}, {@code lastHealth} 等）：
     *       現在の実効上限 {@code targetHp} を超えていれば {@code targetHp} にクランプする。</li>
     * </ol>
     */
    private static void applySemiGenericSpecialDamageErosion(LivingEntity victim, IsolationState state, float maxHp, float targetHp) {
        final boolean isPlayer = victim instanceof Player;
        final boolean allowBreakDefenses = !isPlayer || state.tier6DefenseBroken;
        final boolean lethal = allowBreakDefenses && (state.phase >= 2 || targetHp <= 0.0F);
        final float clampedHp = Math.max(0.0F, targetHp);
        final float safeMax = Float.isFinite(maxHp) && maxHp > 0.0F ? maxHp : 20.0F;
        final float clampedRatio = Math.min(0.9999F, Math.max(0.0F, state.ratioIsolation));
        final float flatErosion = Math.max(0.0F, state.flatIsolation);
        final float negativeDelta = lethal ? -Math.max(safeMax, 1_000_000.0F) : -Math.max(0.0F, safeMax - clampedHp);

        StateScanner.Policy erosionPolicy = new StateScanner.Policy() {
            @Override
            public boolean relevant(String name) {
                if (name == null || name.isEmpty() || FlagDictionary.is(name, FlagDictionary.Category.TIMER)) {
                    return false;
                }
                if (allowBreakDefenses && (FlagKillPredicates.protectiveOnTarget(name) || FlagKillPredicates.livesCounter(name))) {
                    return true;
                }
                if (isPlayer) {
                    return isLastHealthSnapshotName(name);
                }
                if (lethal && (FlagDictionary.is(name, FlagDictionary.Category.BYPASS) || FlagKillPredicates.isDeathStateField(name))) {
                    return true;
                }
                return isNegativeHealthDeltaName(name)
                        || isAccumulatedErosionGaugeName(name)
                        || isLastHealthSnapshotName(name)
                        || StateScanner.isCustomHealthOrValueName(name);
            }

            @Override
            public @Nullable Object decide(String name, Class<?> type, @Nullable Object current) {
                if (type == boolean.class) {
                    if (allowBreakDefenses && FlagKillPredicates.protectiveOnTarget(name)) {
                        return Boolean.FALSE.equals(current) ? null : Boolean.FALSE;
                    }
                    if (!isPlayer && lethal && (FlagDictionary.is(name, FlagDictionary.Category.BYPASS) || FlagKillPredicates.isDeathStateField(name))) {
                        return Boolean.TRUE.equals(current) ? null : Boolean.TRUE;
                    }
                    return null;
                }

                // 残り復活回数カウンタ：致死時は 0、侵食中は割合に応じて削減（プレイヤーは段階 6 解禁後のみ）
                if (allowBreakDefenses && FlagKillPredicates.livesCounter(name)) {
                    if (lethal) {
                        return numberZero(type, current);
                    }
                    if (current instanceof Number n && n.doubleValue() > 0.0D) {
                        double next = Math.floor(n.doubleValue() * (1.0D - clampedRatio));
                        return castNumber(type, next, current);
                    }
                    return null;
                }

                if (isPlayer) {
                    if (isLastHealthSnapshotName(name) && current instanceof Number n && n.doubleValue() > clampedHp) {
                        return castNumber(type, clampedHp, current);
                    }
                    return null;
                }

                // 負の最大 HP デルタ（healthDelta 等）
                if (isNegativeHealthDeltaName(name)) {
                    return castNumber(type, negativeDelta, current);
                }

                // 蓄積侵食ゲージ（soulDamage / hurtOffset / erosion / trauma 等）
                if (isAccumulatedErosionGaugeName(name)) {
                    boolean useFlat = isFlatOffsetGaugeName(name)
                            || (current instanceof Number n && n.doubleValue() > 1.0001D);
                    double targetGauge = useFlat ? flatErosion : clampedRatio;
                    if (current instanceof Number n && n.doubleValue() >= targetGauge) {
                        return null;
                    }
                    return castNumber(type, targetGauge, current);
                }

                // 直前 HP スナップショット（lastHealth / prevHealth 等）またはカスタム HP（realHp / shieldHp / customHealth 等）
                if (isLastHealthSnapshotName(name) || StateScanner.isCustomHealthOrValueName(name)) {
                    if (type == String.class) {
                        if (lethal && StateScanner.isPositiveNumericString(current)) {
                            return "0.0";
                        }
                        if (StateScanner.isPositiveNumericString(current)) {
                            try {
                                float cur = Float.parseFloat(((String) current).trim());
                                if (cur > clampedHp) return Float.toString(clampedHp);
                            } catch (NumberFormatException ignored) {}
                        }
                        return null;
                    }
                    if (current instanceof Number n) {
                        double cur = n.doubleValue();
                        if (!Double.isFinite(cur) || cur > clampedHp) {
                            return castNumber(type, clampedHp, current);
                        }
                    } else if (current == null && lethal) {
                        return castNumber(type, 0.0D, null);
                    }
                }
                return null;
            }

            @Override
            public boolean removeKey(String key) {
                return allowBreakDefenses && FlagKillPredicates.protectiveOnTarget(key);
            }

            @Override
            public boolean callBlindSetters() {
                return !isPlayer;
            }
        };

        try {
            StateScanner.apply(victim, erosionPolicy, EnumSet.allOf(StateScanner.Source.class));
        } catch (Throwable ignored) {
        }

        // SynchedEntityData 自体がサブクラス差し替え（SuperSteve の HurtSynchedEntityData 等）されている場合の
        // 独自オフセット／侵食フィールドも準汎用走査
        if (!isPlayer) {
            try {
                SynchedEntityData synched = victim.getEntityData();
                if (synched != null && synched.getClass() != SynchedEntityData.class) {
                    for (Class<?> c = synched.getClass(); c != null && c != SynchedEntityData.class && c != Object.class; c = c.getSuperclass()) {
                        for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                            if (Modifier.isStatic(f.getModifiers())) continue;
                            String fn = f.getName();
                            if (isAccumulatedErosionGaugeName(fn) || isNegativeHealthDeltaName(fn) || StateScanner.isCustomHealthOrValueName(fn)) {
                                f.setAccessible(true);
                                Object cur = f.get(synched);
                                Object decided = erosionPolicy.decide(fn, f.getType(), cur);
                                if (decided != null && !decided.equals(cur)) {
                                    f.set(synched, decided);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isNegativeHealthDeltaName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return (lower.contains("health") || lower.contains("hp"))
                && (lower.contains("delta") || lower.contains("deficit") || lower.contains("penalty") || lower.contains("reduction"));
    }

    private static boolean isFlatOffsetGaugeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("offset") || lower.contains("taken") || lower.contains("lost");
    }

    private static boolean isAccumulatedErosionGaugeName(String name) {
        if (isNegativeHealthDeltaName(name)) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("souldamage") || lower.contains("soul_damage")
                || lower.contains("hurtoffset") || lower.contains("hurt_offset")
                || lower.contains("erosion") || lower.contains("corruption")
                || lower.contains("trauma") || lower.contains("witheredhp")
                || lower.contains("cursedamage") || lower.contains("abyssdamage")
                || lower.contains("voiddamage") || lower.contains("entropy")) {
            return true;
        }
        java.util.List<String> words = FlagDictionary.words(name);
        boolean hasDamageOrOffset = words.contains("damage") || words.contains("dmg")
                || words.contains("offset") || words.contains("erosion") || words.contains("decay")
                || words.contains("trauma") || words.contains("corruption");
        boolean hasSpecialQualifier = words.contains("soul") || words.contains("hurt")
                || words.contains("spirit") || words.contains("true") || words.contains("void")
                || words.contains("abyss") || words.contains("curse") || words.contains("fatal")
                || words.contains("max") || words.contains("perm") || words.contains("permanent");
        return hasDamageOrOffset && hasSpecialQualifier;
    }

    private static boolean isLastHealthSnapshotName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("lasthealth") || lower.equals("last_health")
                || lower.equals("prevhealth") || lower.equals("prev_health")
                || lower.equals("previoushealth") || lower.equals("oldhealth")
                || lower.equals("lasthp") || lower.equals("prevhp")
                || lower.equals("recordedhealth") || lower.equals("cachedhealth");
    }

    @Nullable
    private static Object numberZero(Class<?> type, @Nullable Object current) {
        if (current instanceof Number n && n.doubleValue() == 0.0D) return null;
        return castNumber(type, 0.0D, current);
    }

    @Nullable
    private static Object castNumber(Class<?> type, double value, @Nullable Object current) {
        if (type == float.class || type == Float.class) {
            float fv = (float) value;
            return (current instanceof Float cf && Float.compare(cf, fv) == 0) ? null : fv;
        }
        if (type == double.class || type == Double.class) {
            return (current instanceof Double cd && Double.compare(cd, value) == 0) ? null : value;
        }
        if (type == int.class || type == Integer.class) {
            int iv = (int) Math.round(value);
            return (current instanceof Integer ci && ci == iv) ? null : iv;
        }
        if (type == long.class || type == Long.class) {
            long lv = Math.round(value);
            return (current instanceof Long cl && cl == lv) ? null : lv;
        }
        if (type == short.class || type == Short.class) {
            short sv = (short) Math.round(value);
            return (current instanceof Short cs && cs == sv) ? null : sv;
        }
        if (type == byte.class || type == Byte.class) {
            byte bv = (byte) Math.round(value);
            return (current instanceof Byte cb && cb == bv) ? null : bv;
        }
        return null;
    }

    /**
     * 特定ダメージ専用仕様に対する準汎用侵食（{@link #applySemiGenericSpecialDamageErosion}）を実行した上で、
     * static ヘルパーメソッド経由でのみ状態を持つ外部 MOD（Trial Monolith / Fantasy Ending 等）の互換ブリッジも同期する。
     */
    private static void bridgeExternalSpecialDamage(LivingEntity victim, IsolationState state, float maxHp, float targetHp) {
        // 0. 準汎用：StateScanner（全 6 ソース）＋ SynchedEntityData サブクラス走査による特殊ダメージゲージ・独自 HP・専用バリアの侵食
        applySemiGenericSpecialDamageErosion(victim, state, maxHp, targetHp);

        // 1. The Trial Monolith: static EntityHelper 経由の同期（非プレイヤーのみ・最大 0.9999F）
        if (!(victim instanceof Player)) {
            try {
                Class<?> helperClass = Class.forName("io.github.kosianodangoo.trialmonolith.common.helper.EntityHelper", false, victim.getClass().getClassLoader());
                Method setSoulProt = helperClass.getMethod("setSoulProtected", Entity.class, boolean.class);
                setSoulProt.invoke(null, victim, false);
                Method setBarrier = helperClass.getMethod("setHighDimensionalBarrier", Entity.class, boolean.class);
                setBarrier.invoke(null, victim, false);
                Method setSoulDmgForce = helperClass.getMethod("setSoulDamageForce", Entity.class, float.class);
                float soulRatio = Math.min(0.9999F, state.ratioIsolation);
                setSoulDmgForce.invoke(null, victim, soulRatio);
            } catch (Throwable ignored) {
            }
        }

        // 2. Fantasy Ending: static EntityASMUtil.setHealthDelta (非プレイヤーのみ)
        if (!(victim instanceof Player)) {
            try {
                Class<?> asmUtilClass = Class.forName("com.mega.uom.util.entity.EntityASMUtil", false, victim.getClass().getClassLoader());
                Method setHealthDelta = asmUtilClass.getMethod("setHealthDelta", LivingEntity.class, float.class);
                float feDelta = state.phase >= 2 ? -Math.max(maxHp, 1_000_000.0F) : -Math.max(0.0F, maxHp - targetHp);
                setHealthDelta.invoke(null, victim, feDelta);
            } catch (Throwable ignored) {
            }
        }

        // 3. Hyperlink (hyperdaimc): 致死時の引数なし setNovelized() 呼び出し・Fumetsu ターゲット解除
        if (state.phase >= 2 && !(victim instanceof Player)) {
            try {
                Method setNovelized = victim.getClass().getMethod("setNovelized");
                setNovelized.invoke(victim);
            } catch (Throwable ignored) {
            }
            if ("com.sakurafuld.hyperdaimc.content.hyper.fumetsu.FumetsuEntity".equals(victim.getClass().getName())) {
                try {
                    Field targetField = victim.getClass().getDeclaredField("DATA_TARGET");
                    targetField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    EntityDataAccessor<Integer> targetAcc = (EntityDataAccessor<Integer>) targetField.get(null);
                    victim.getEntityData().set(targetAcc, 0);
                } catch (Throwable ignored) {
                }
                try {
                    Field stormField = victim.getClass().getDeclaredField("DATA_STORM");
                    stormField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    EntityDataAccessor<Boolean> stormAcc = (EntityDataAccessor<Boolean>) stormField.get(null);
                    victim.getEntityData().set(stormAcc, false);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * {@code LivingEntity.baseTick} の末尾で呼ばれるフック。
     * <p>
     * {@code FumetsuEntity} 等の {@code tickDeath()} を無効化・条件制限している特殊ボスが Phase 2（隔絶致死）になった際、
     * サーバー・クライアント双方で死亡転倒アニメーション（{@code deathTime} 1 &rarr; 20）を確実に進行させ、
     * 20 Tick 到達時にワールドおよび影の追跡表から完全消去する。
     * </p>
     */
    public static void onLivingBaseTickTail(LivingEntity entity, int preTickDeathTime) {
        if (entity == null || entity instanceof Player || isProtectedTarget(entity) || entity.isRemoved()) {
            return;
        }
        if (entity.level().isClientSide()) {
            if (!DirectRead.hasHealth(entity)
                    && (entity.getPose() == Pose.DYING || entity.deathTime > 0
                    || ((LivingEntityAccessor) entity).automata_valkyries$isDead())) {
                if (entity.deathTime <= preTickDeathTime && entity.deathTime < 20) {
                    entity.deathTime = preTickDeathTime + 1;
                }
            }
            return;
        }

        IsolationState state = getState(entity);
        if (state == null || state.phase < 2) {
            return;
        }

        ((LivingEntityAccessor) entity).automata_valkyries$setDead(true);
        if (entity.getPose() != Pose.DYING) {
            entity.setPose(Pose.DYING);
        }

        int currentDeadTicks;
        LivingEntity attacker;
        DamageSource source;
        synchronized (state) {
            state.deadTicks++;
            currentDeadTicks = state.deadTicks;
            attacker = state.lastAttacker;
            source = state.lastSource;
        }

        if (entity.deathTime <= preTickDeathTime) {
            entity.deathTime = Math.max(preTickDeathTime + 1, currentDeadTicks);
        }

        if (entity.deathTime >= 20 || currentDeadTicks >= 20) {
            if (!entity.isRemoved()) {
                entity.level().broadcastEntityEvent(entity, (byte) 60);
                state.phase = 3;
                DamageSource finalSource = source != null ? source : entity.damageSources().genericKill();
                executePhase3IsolationRemoval(entity, attacker, finalSource);
            }
        }
    }

    // ---- Mixin（LivingEntity / SynchedEntityData）からの常時クランプ用クエリ API ----

    private static void expungeStaleStates() {
        ReferenceQueue<LivingEntity> q = STATE_REF_QUEUE;
        Object ref;
        while ((ref = q.poll()) != null) {
            STATES.remove(ref);
            EMPTY_MASK_INVENTORIES.remove(ref);
        }
    }

    /** 隔絶ダメージ専用 DamageSource を生成する（死亡メッセージを「隔絶ダメージ」にする） */
    public static DamageSource createIsolationDamageSource(LivingEntity victim, @Nullable LivingEntity attacker, @Nullable DamageSource fallback) {
        try {
            Entity direct = fallback != null && fallback.getDirectEntity() != null ? fallback.getDirectEntity() : attacker;
            Entity causing = attacker != null ? attacker : (fallback != null ? fallback.getEntity() : null);
            return new DamageSource(
                    victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ISOLATION_DAMAGE_TYPE),
                    direct,
                    causing);
        } catch (Throwable ignored) {
            return fallback != null ? fallback : victim.damageSources().genericKill();
        }
    }

    /** 指定エンティティの隔絶侵食状態を完全クリアする（リスポーン・クローン時など） */
    public static void clearState(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        expungeStaleStates();
        IdentityWeakKey lookup = new IdentityWeakKey(living, null);
        STATES.remove(lookup);
        EMPTY_MASK_INVENTORIES.remove(lookup);
        // 念のため同一 entityId や同一 UUID の古い Player エントリも掃除
        if (living instanceof Player player) {
            int id = player.getId();
            var uuid = player.getUUID();
            if (uuid != null) {
                TIER6_SUPPRESSED_PLAYER_UUIDS.remove(uuid);
                CONFIRMED_DEAD_PLAYER_UUIDS.remove(uuid);
            }
            STATES.keySet().removeIf(k -> {
                LivingEntity e = k.get();
                return e == null || e == living || (e instanceof Player p && (p.getId() == id || p.getUUID().equals(uuid)));
            });
            EMPTY_MASK_INVENTORIES.keySet().removeIf(k -> {
                LivingEntity e = k.get();
                return e == null || e == living || (e instanceof Player p && (p.getId() == id || p.getUUID().equals(uuid)));
            });
            com.automatavalkyries.protect.EprotectTrialMonolithCounter.counter(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!Boolean.TRUE.equals(IN_SAFETY_VALVE_RESPAWN.get())
                && event.getEntity() != null && event.getEntity().getUUID() != null) {
            SAFETY_VALVE_BYPASSED_TIER.remove(event.getEntity().getUUID());
            PENDING_SAFETY_VALVE_TARGET_TIER.remove(event.getEntity().getUUID());
        }
        clearState(event.getOriginal());
        clearState(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!Boolean.TRUE.equals(IN_SAFETY_VALVE_RESPAWN.get())
                && event.getEntity() != null && event.getEntity().getUUID() != null) {
            SAFETY_VALVE_BYPASSED_TIER.remove(event.getEntity().getUUID());
            PENDING_SAFETY_VALVE_TARGET_TIER.remove(event.getEntity().getUUID());
        }
        clearState(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() != null && event.getEntity().isAlive()) {
            if (event.getEntity().getUUID() != null) {
                SAFETY_VALVE_BYPASSED_TIER.remove(event.getEntity().getUUID());
                PENDING_SAFETY_VALVE_TARGET_TIER.remove(event.getEntity().getUUID());
            }
            clearState(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null && event.getEntity().getUUID() != null) {
            SAFETY_VALVE_BYPASSED_TIER.remove(event.getEntity().getUUID());
            PENDING_SAFETY_VALVE_TARGET_TIER.remove(event.getEntity().getUUID());
        }
        clearState(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        Integer pendingTarget = uuid != null ? PENDING_SAFETY_VALVE_TARGET_TIER.remove(uuid) : null;
        if (pendingTarget != null) {
            advanceElenorTierToTarget(player, pendingTarget);
        }

        IsolationState state = getState(player);
        if (state != null && state.phase >= 2 && (state.tier6DefenseBroken || state.playerConfirmedDead)) {
            suppressPlayerActiveDefensesOnTick(player);
            // 死亡確定（Phase 2）後に他 MOD がサーバー側の死亡状態（HP <= 0 / isDeadOrDying / dead フラグ）を
            // 強制解除して巻き戻している場合、クライアントの死亡画面からリスポーン要求（PERFORM_RESPAWN）を
            // 送っても拒否される不整合状態になるため、安全弁を発動する
            boolean serverRespawnableDead = ((LivingEntityAccessor) player).automata_valkyries$isDead()
                    && player.getHealth() <= 0.0F
                    && player.isDeadOrDying();
            if (!serverRespawnableDead) {
                if (++state.unrespawnableTicks >= 2) {
                    triggerSafetyValveAndAdvanceTier(player, "server dead state was reverted after Isolation death");
                }
            } else {
                state.unrespawnableTicks = 0;
            }
        }
    }

    @Nullable
    public static IsolationState getState(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        return STATES.get(new IdentityWeakKey(living, null));
    }

    private static IsolationState getOrCreateState(LivingEntity entity) {
        expungeStaleStates();
        IdentityWeakKey lookupKey = new IdentityWeakKey(entity, null);
        IsolationState existing = STATES.get(lookupKey);
        if (existing != null) return existing;
        IsolationState created = new IsolationState();
        IsolationState prev = STATES.putIfAbsent(new IdentityWeakKey(entity, STATE_REF_QUEUE), created);
        return prev != null ? prev : created;
    }

    /** 指定プレイヤーに対して指定ティア以下の隔絶・抹殺干渉が安全弁により停止されているか */
    public static boolean isSafetyValveBypassed(@Nullable Player player, int tier) {
        if (player == null || player.getUUID() == null) return false;
        Integer bypassed = SAFETY_VALVE_BYPASSED_TIER.get(player.getUUID());
        return bypassed != null && bypassed >= tier;
    }

    /**
     * 隔絶ダメージ（Tier 5 または Tier 6）によってプレイヤーの死亡（{@code die()}）が成立した際に呼び出し、
     * サーバー・クライアント双方で HP 0.0F と死亡状態を確定・即時同期する。
     */
    public static void markPlayerIsolationDead(@Nullable Player player) {
        if (player == null || isProtectedTarget(player)) return;
        UUID uuid = player.getUUID();
        IsolationState existing = getState(player);
        int currentTier = (existing != null && existing.tier6DefenseBroken)
                || (uuid != null && TIER6_SUPPRESSED_PLAYER_UUIDS.contains(uuid)) ? 6 : 5;
        if (isSafetyValveBypassed(player, currentTier)) return;

        IsolationState state = getOrCreateState(player);
        state.playerConfirmedDead = true;
        state.phase = Math.max(state.phase, 2);
        state.effectiveMaxHp = 0.0F;
        if (uuid != null) {
            CONFIRMED_DEAD_PLAYER_UUIDS.add(uuid);
        }
        try {
            ((LivingEntityAccessor) player).automata_valkyries$setDead(true);
            forceWriteHealth(player, 0.0F);
            if (player instanceof ServerPlayer serverPlayer
                    && serverPlayer.connection != null
                    && serverPlayer.connection.connection != null
                    && serverPlayer.connection.connection.isConnected()) {
                if (serverPlayer.getEntityData().isDirty()) {
                    serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                            serverPlayer.getId(), serverPlayer.getEntityData().packDirty()));
                }
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                        0.0F, serverPlayer.getFoodData().getFoodLevel(), serverPlayer.getFoodData().getSaturationLevel()));
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 安全弁（Safety Valve）：万が一プレイヤーが死亡確定不能・リスポーン不能な状態に陥った場合、
     * 当 MOD 側の隔絶・抹殺干渉をすべて停止してプレイヤーを正常な生存状態に戻し（死亡後・リスポーン失敗時は
     * サーバー側から {@code PlayerList.respawn(player, false)} で直接リスポーンさせ）、
     * 交戦中のエレノアのペナルティティアを次の段階（Tier + 1）へ移行させる。
     */
    public static void triggerSafetyValveAndAdvanceTier(@Nullable Player player, String reason) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        IsolationState prevState = getState(player);
        int trippedTier = (prevState != null && prevState.tier6DefenseBroken)
                || (uuid != null && TIER6_SUPPRESSED_PLAYER_UUIDS.contains(uuid)) ? 6 : 5;
        boolean inStrike = STACK_WALKER.walk(s -> s.limit(16).anyMatch(
                f -> f.getClassName().equals("com.automatavalkyries.entity.ElenorPressure")
                        && f.getMethodName().equals("strike")));

        // リスポーンで別座標へ移動する前に、死亡地点周辺の交戦中エレノアを取得しておく
        java.util.List<com.automatavalkyries.entity.ElenorEntity> nearbyElenors = new java.util.ArrayList<>();
        if (player.level() instanceof ServerLevel preLevel) {
            try {
                nearbyElenors.addAll(preLevel.getEntitiesOfClass(
                        com.automatavalkyries.entity.ElenorEntity.class,
                        player.getBoundingBox().inflate(64.0D),
                        e -> e != null && e.isAlive() && e.arts() != null));
            } catch (Throwable ignored) {}
        }

        boolean wasDeadOrPostDeath = !inStrike
                || (prevState != null && prevState.playerConfirmedDead)
                || (uuid != null && CONFIRMED_DEAD_PLAYER_UUIDS.contains(uuid))
                || ((LivingEntityAccessor) player).automata_valkyries$isDead()
                || player.deathTime > 0
                || player.isRemoved()
                || !com.automatavalkyries.protect.DirectRead.hasHealth(player);

        if (uuid != null) {
            SAFETY_VALVE_BYPASSED_TIER.merge(uuid, trippedTier, Math::max);
            TIER6_SUPPRESSED_PLAYER_UUIDS.remove(uuid);
            CONFIRMED_DEAD_PLAYER_UUIDS.remove(uuid);
        }

        // 1. 当 MOD 側の隔絶・抹殺・終焉干渉状態を完全クリア
        clearState(player);
        TerminusPlayerExecutor.clearPlayerTerminusDead(player);
        try {
            player.getPersistentData().remove("automata_valkyries:terminus_pending_respawn");
            player.getPersistentData().remove("automata_valkyries:terminus_death_completed");
        } catch (Throwable ignored) {}

        Player activePlayer = player;
        if (player instanceof ServerPlayer serverPlayer) {
            // 2a. 既に死亡処理後またはリスポーン失敗（PERFORM_RESPAWN 等）で呼ばれた場合は、
            //     古い死亡 ServerPlayer の HP だけを戻すとワールド登録解除済みで移動・インベントリ不能になるため、
            //     サーバー側から PlayerList.respawn(player, false) を直接呼んで新しい ServerPlayer に切り替える
            if (wasDeadOrPostDeath && serverPlayer.server != null && serverPlayer.connection != null) {
                IN_SAFETY_VALVE_RESPAWN.set(Boolean.TRUE);
                try {
                    com.automatavalkyries.protect.RespawnSanitizer.releaseDeadPlayer(serverPlayer);
                    com.automatavalkyries.protect.EcaLastSwordHelper.releaseDeadPlayerForRespawn(serverPlayer);
                    com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(serverPlayer);
                    forceWriteHealth(serverPlayer, 0.0F);
                    ServerPlayer respawned = serverPlayer.server.getPlayerList().respawn(serverPlayer, false);
                    if (respawned != null) {
                        if (respawned.connection != null) {
                            respawned.connection.player = respawned;
                        }
                        serverPlayer.connection.player = respawned;
                        activePlayer = respawned;
                    }
                } catch (Throwable t) {
                    AutomataValkyries.LOGGER.warn("[IsolationDamage] Direct PlayerList.respawn in safety valve failed for {}: {}",
                            serverPlayer.getScoreboardName(), t.toString());
                } finally {
                    IN_SAFETY_VALVE_RESPAWN.set(Boolean.FALSE);
                }
            }
        }

        if (uuid != null) {
            SAFETY_VALVE_BYPASSED_TIER.merge(uuid, trippedTier, Math::max);
            TIER6_SUPPRESSED_PLAYER_UUIDS.remove(uuid);
            CONFIRMED_DEAD_PLAYER_UUIDS.remove(uuid);
        }
        clearState(activePlayer);
        TerminusPlayerExecutor.clearPlayerTerminusDead(activePlayer);
        try {
            activePlayer.getPersistentData().remove("automata_valkyries:terminus_pending_respawn");
            activePlayer.getPersistentData().remove("automata_valkyries:terminus_death_completed");
            com.automatavalkyries.protect.RespawnSanitizer.cleanRespawnedPlayer(activePlayer, false);
        } catch (Throwable ignored) {}

        // 2b. 新しい（または生存継続の）プレイヤーの生死・HP・ポーズ・クライアント画面を正常な生存状態に確定
        float restoreHp = getSafeMaxHealth(activePlayer);
        try {
            ((LivingEntityAccessor) activePlayer).automata_valkyries$setDead(false);
            activePlayer.deathTime = 0;
            activePlayer.setPose(Pose.STANDING);
            com.automatavalkyries.mixin.EntityAccessor entityAccessor = (com.automatavalkyries.mixin.EntityAccessor) activePlayer;
            if (entityAccessor.automata_valkyries$getRemovalReason() != null) {
                entityAccessor.automata_valkyries$setRemovalReason(null);
            }
            forceWriteHealth(activePlayer, restoreHp);
            activePlayer.setHealth(restoreHp);
        } catch (Throwable ignored) {}

        if (activePlayer instanceof ServerPlayer activeServerPlayer) {
            try {
                if (activeServerPlayer.connection != null && activeServerPlayer.connection.connection != null
                        && activeServerPlayer.connection.connection.isConnected()) {
                    if (activeServerPlayer.getEntityData().isDirty()) {
                        activeServerPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                                activeServerPlayer.getId(), activeServerPlayer.getEntityData().packDirty()));
                    }
                    activeServerPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                            restoreHp, activeServerPlayer.getFoodData().getFoodLevel(), activeServerPlayer.getFoodData().getSaturationLevel()));
                }
                if (uuid != null) {
                    com.automatavalkyries.network.ModNetwork.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                            new com.automatavalkyries.network.TerminusPlayerRespawnPacket(uuid));
                }
            } catch (Throwable ignored) {}

            // 3. 交戦中のエレノアのペナルティティアを次の段階へ進める
            scheduleOrAdvanceElenorTier(activeServerPlayer, nearbyElenors, trippedTier, inStrike);
        }

        AutomataValkyries.LOGGER.warn("[IsolationDamage] Safety valve triggered for player {} (tier={}, reason={}): stopped interference and advancing to next tier",
                activePlayer.getScoreboardName(), trippedTier, reason);
    }

    private static void scheduleOrAdvanceElenorTier(ServerPlayer player,
                                                    java.util.List<com.automatavalkyries.entity.ElenorEntity> preCollectedElenors,
                                                    int trippedTier,
                                                    boolean inStrike) {
        UUID uuid = player.getUUID();
        java.util.Set<com.automatavalkyries.entity.ElenorEntity> targetElenors = new java.util.LinkedHashSet<>();
        if (preCollectedElenors != null) {
            targetElenors.addAll(preCollectedElenors);
        }
        if (player.level() instanceof ServerLevel level) {
            try {
                targetElenors.addAll(level.getEntitiesOfClass(
                        com.automatavalkyries.entity.ElenorEntity.class,
                        player.getBoundingBox().inflate(64.0D),
                        e -> e != null && e.isAlive() && e.arts() != null));
            } catch (Throwable ignored) {}
        }
        int maxCurrentTier = trippedTier;
        for (com.automatavalkyries.entity.ElenorEntity elenor : targetElenors) {
            if (elenor == null || !elenor.isAlive()) continue;
            com.automatavalkyries.entity.ElenorArts arts = elenor.arts();
            if (arts != null) {
                maxCurrentTier = Math.max(maxCurrentTier, arts.pressure().tier(player));
            }
        }
        int targetTier = Math.min(com.automatavalkyries.entity.ElenorPressure.MAX_TIER, maxCurrentTier + 1);
        if (inStrike && uuid != null) {
            PENDING_SAFETY_VALVE_TARGET_TIER.put(uuid, targetTier);
        } else {
            advanceElenorTierToTarget(player, targetElenors, targetTier);
        }
    }

    private static void advanceElenorTierToTarget(ServerPlayer player, int targetTier) {
        java.util.Set<com.automatavalkyries.entity.ElenorEntity> targetElenors = new java.util.LinkedHashSet<>();
        if (player.level() instanceof ServerLevel level) {
            try {
                targetElenors.addAll(level.getEntitiesOfClass(
                        com.automatavalkyries.entity.ElenorEntity.class,
                        player.getBoundingBox().inflate(64.0D),
                        e -> e != null && e.isAlive() && e.arts() != null));
            } catch (Throwable ignored) {}
        }
        advanceElenorTierToTarget(player, targetElenors, targetTier);
    }

    private static void advanceElenorTierToTarget(ServerPlayer player,
                                                  java.util.Collection<com.automatavalkyries.entity.ElenorEntity> targetElenors,
                                                  int targetTier) {
        UUID uuid = player.getUUID();
        for (com.automatavalkyries.entity.ElenorEntity elenor : targetElenors) {
            if (elenor == null || !elenor.isAlive()) continue;
            try {
                com.automatavalkyries.entity.ElenorArts arts = elenor.arts();
                if (arts == null) continue;
                com.automatavalkyries.entity.ElenorPressure pressure = arts.pressure();
                int beforeTier = pressure.tier(player);
                int clampedTarget = Math.min(com.automatavalkyries.entity.ElenorPressure.MAX_TIER, Math.max(targetTier, beforeTier + 1));
                if (beforeTier < clampedTarget) {
                    int maxCalls = (clampedTarget - beforeTier) * (Math.max(1, pressure.failThreshold(uuid)) + 2);
                    for (int i = 0; i < maxCalls && pressure.tier(player) < clampedTarget; i++) {
                        pressure.noteStrikeResult(player, 0.0F, 100.0F);
                    }
                    AutomataValkyries.LOGGER.info("[IsolationDamage] Safety valve advanced Elenor penalty tier for {} from {} to {}",
                            player.getScoreboardName(), beforeTier, pressure.tier(player));
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * プレイヤーに対して段階 6（抹殺・防御解体）が執行されたことを記録し、
     * 隔絶ダメージによる確殺（Phase 2 致死・上限 0.0F クランプ）およびインベントリ防御アイテムの準汎用無力化を解禁する。
     */
    public static void enablePlayerTier6Kill(@Nullable Player player) {
        if (player == null || isSafetyValveBypassed(player, 6)) return;
        IsolationState state = getOrCreateState(player);
        state.tier6DefenseBroken = true;
        if (player.getUUID() != null) {
            TIER6_SUPPRESSED_PLAYER_UUIDS.add(player.getUUID());
        }
        suppressPlayerActiveDefensesOnTick(player);
    }

    /**
     * 段階 6 解禁中または終焉死亡中のプレイヤーに対し、毎 Tick 先頭（IMDS の {@code LivingEntityMixin.imds_tick} 等より前）で
     * 無敵フラグ・保護リストを解除し、死亡確定済みの場合は HP 0.0F と {@code dead = true} を維持する。
     */
    public static void suppressPlayerActiveDefensesOnTick(@Nullable Player player) {
        if (player == null || !shouldSuppressPlayerInventoryDefense(player)) return;
        try {
            com.automatavalkyries.protect.flag.GuardLists.purge(player);
            com.automatavalkyries.protect.EprotectImdsCounter.counter(player);
            com.automatavalkyries.protect.EcaLastSwordHelper.forceClearEcaAndTlsProtection(player);
            if (isIsolatedDead(player) || TerminusPlayerExecutor.isTerminusDeadOrDying(player)) {
                ((LivingEntityAccessor) player).automata_valkyries$setDead(true);
                forceWriteHealth(player, 0.0F);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 指定プレイヤーに対して、インベントリ内の防御・不死アイテム（IMDS・The Last Sword 等）の
     * {@code inventoryTick} および外部 MOD からの所持判定を抑制すべき状態か判定する。
     * <p>
     * アイテム自体はインベントリから消去しない（エレノア仕様書 §5.5 準拠）まま、
     * 段階 6 解禁中または死亡中（リスポーン完了まで）の間だけ防御アイテムの無敵・蘇生・リスポーン妨害を無力化する。
     * ただし安全弁（{@link #triggerSafetyValveAndAdvanceTier}）が発動済みの場合は干渉を行わない。
     * </p>
     */
    public static boolean shouldSuppressPlayerInventoryDefense(@Nullable Player player) {
        if (player == null || Boolean.TRUE.equals(IN_INVENTORY_MASK.get())) return false;
        UUID uuid = player.getUUID();
        IsolationState state = getState(player);
        boolean tier6Active = (state != null && state.tier6DefenseBroken)
                || (uuid != null && TIER6_SUPPRESSED_PLAYER_UUIDS.contains(uuid));
        boolean confirmedDead = (state != null && state.playerConfirmedDead)
                || (uuid != null && CONFIRMED_DEAD_PLAYER_UUIDS.contains(uuid))
                || TerminusPlayerExecutor.isTerminusDeadOrDying(player);
        if (!tier6Active && !confirmedDead) {
            return false;
        }
        int checkTier = tier6Active ? 6 : 5;
        if (isSafetyValveBypassed(player, checkTier)) {
            return false;
        }
        IN_INVENTORY_MASK.set(Boolean.TRUE);
        try {
            if (com.automatavalkyries.protect.AgentPlayerProtection.isProtected(player)) {
                return false;
            }
        } catch (Throwable ignored) {
        } finally {
            IN_INVENTORY_MASK.set(Boolean.FALSE);
        }
        return true;
    }

    /**
     * 段階 6 解禁中または死亡中のプレイヤーに対し、外部 MOD（{@code InventoryCheckProcedure.hasImds} 等）が
     * {@link Player#getInventory()} / {@link Player#getItemBySlot} を呼んだ場合に空インベントリへマスクすべきか判定する。
     * <p>
     * バニラ処理、当 MOD 自身の処理、および死亡時アイテム保持（{@code dropAll} / {@code clearContent} / {@code dropEquipment}）・
     * リスポーン時のインベントリ引き継ぎ（{@code restoreFrom}）では常に {@code false}（本物のインベントリ）を返す。
     * </p>
     */
    public static boolean shouldMaskInventoryForForeignCaller(@Nullable Player player) {
        if (player == null || Boolean.TRUE.equals(IN_INVENTORY_MASK.get())) {
            return false;
        }
        if (!shouldSuppressPlayerInventoryDefense(player)) {
            return false;
        }
        IN_INVENTORY_MASK.set(Boolean.TRUE);
        try {
            return STACK_WALKER.walk(stream -> {
                int depth = 0;
                boolean immediateCallerIsForeign = false;
                var it = stream.iterator();
                while (it.hasNext() && depth < 14) {
                    StackWalker.StackFrame frame = it.next();
                    String className = frame.getClassName();
                    String methodName = frame.getMethodName();
                    if (className.equals(IsolationDamageEngine.class.getName())
                            || className.startsWith("com.automatavalkyries.mixin.IsolationDamageMixin")) {
                        continue;
                    }
                    if (depth == 0 && (methodName.equals("getInventory") || methodName.equals("m_150109_")
                            || methodName.equals("getItemBySlot") || methodName.equals("m_6844_")
                            || methodName.equals("getMainHandItem") || methodName.equals("m_21205_")
                            || methodName.equals("getOffhandItem") || methodName.equals("m_21206_")
                            || methodName.contains("automata_valkyries$"))) {
                        continue;
                    }
                    depth++;
                    if (depth == 1) {
                        if (!isForeignFrame(className, methodName)) {
                            return false;
                        }
                        immediateCallerIsForeign = true;
                    }
                    // 死亡時のアイテム保持（dropAll / clearContent / dropEquipment）や
                    // リスポーン時のアイテム引き継ぎ（restoreFrom）では本物のインベントリを見せる
                    if (methodName.contains("restoreFrom") || methodName.equals("m_9015_")
                            || methodName.contains("DropAll") || methodName.contains("dropAll") || methodName.equals("m_36071_")
                            || methodName.contains("ClearContent") || methodName.contains("clearContent") || methodName.equals("m_6211_")
                            || methodName.contains("dropEquipment") || methodName.equals("m_5907_")) {
                        return false;
                    }
                }
                return immediateCallerIsForeign;
            });
        } catch (Throwable ignored) {
            return false;
        } finally {
            IN_INVENTORY_MASK.set(Boolean.FALSE);
        }
    }

    private static boolean isForeignFrame(@Nullable String className, @Nullable String methodName) {
        if (className == null) return false;
        if (className.startsWith("com.automatavalkyries.")
                || className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("org.spongepowered.")
                || className.startsWith("cpw.mods.")) {
            return false;
        }
        if (className.startsWith("net.minecraft.")
                || className.startsWith("net.minecraftforge.")
                || className.startsWith("com.mojang.")) {
            if (methodName != null && methodName.indexOf('$') >= 0
                    && !methodName.startsWith("lambda$")
                    && !methodName.contains("automata_valkyries$")) {
                return true;
            }
            return false;
        }
        return true;
    }

    /** 外部 MOD の所持検査を無力化するための空 {@link Inventory} を取得する */
    public static Inventory getEmptyMaskInventory(Player player) {
        expungeStaleStates();
        IdentityWeakKey lookup = new IdentityWeakKey(player, null);
        Inventory existing = EMPTY_MASK_INVENTORIES.get(lookup);
        if (existing != null) {
            existing.clearContent();
            return existing;
        }
        Inventory created = new Inventory(player);
        EMPTY_MASK_INVENTORIES.put(new IdentityWeakKey(player, STATE_REF_QUEUE), created);
        return created;
    }

    /** 隔絶致死（Phase 2 以上または死亡確定済み）により死亡が確定しているか */
    public static boolean isIsolatedDead(@Nullable Entity entity) {
        if (entity instanceof Player player) {
            UUID uuid = player.getUUID();
            if (uuid != null && CONFIRMED_DEAD_PLAYER_UUIDS.contains(uuid)) {
                return true;
            }
            IsolationState state = getState(entity);
            if (state == null || state.phase < 2) return false;
            return state.tier6DefenseBroken || state.playerConfirmedDead;
        }
        IsolationState state = getState(entity);
        return state != null && state.phase >= 2;
    }

    /** 隔絶侵食（Phase 1 以上）により上限クランプが有効な場合、その上限 HP を返す（未侵食なら {@code Float.NaN}） */
    public static float getClampedMaxHealth(@Nullable LivingEntity entity) {
        if (entity == null) return Float.NaN;
        if (entity instanceof Player player && player.getUUID() != null
                && CONFIRMED_DEAD_PLAYER_UUIDS.contains(player.getUUID())) {
            return 0.0F;
        }
        IsolationState state = STATES.get(new IdentityWeakKey(entity, null));
        if (state == null || state.phase <= 0) return Float.NaN;
        if (state.phase >= 2 && (!(entity instanceof Player) || state.tier6DefenseBroken || state.playerConfirmedDead)) {
            return 0.0F;
        }
        return state.computeEffectiveMaxHp(entity);
    }

    /** 指定された {@link EntityDataAccessor} が対象エンティティのバニラ HP またはカスタム HP アクセサかどうか */
    public static boolean isHealthAccessor(LivingEntity entity, EntityDataAccessor<?> key) {
        if (key == null) return false;
        int id = key.getId();
        if (id == LivingEntityAccessor.automata_valkyries$getDataHealthId().getId()) {
            return true;
        }
        int[] customIds = CUSTOM_HEALTH_IDS_CACHE.get(entity.getClass());
        if (customIds != null) {
            for (int cid : customIds) {
                if (cid == id) return true;
            }
        }
        return false;
    }

    private static float getSafeMaxHealth(LivingEntity entity) {
        try {
            float max = entity.getMaxHealth();
            if (Float.isFinite(max) && max > 0.0F) {
                return max;
            }
        } catch (Throwable ignored) {
        }
        return 20.0F;
    }

    private static float readRawOrEffectiveHealth(LivingEntity entity) {
        try {
            float hp = entity.getHealth();
            if (Float.isFinite(hp)) return hp;
        } catch (Throwable ignored) {
        }
        return readRawSynchedHealth(entity);
    }

    private static float readRawSynchedHealth(LivingEntity entity) {
        try {
            SynchedEntityData data = entity.getEntityData();
            if (data instanceof SynchedEntityDataAccessor accessor) {
                Int2ObjectMap<SynchedEntityData.DataItem<?>> items = accessor.automata_valkyries$getItemsById();
                if (items != null) {
                    SynchedEntityData.DataItem<?> item = items.get(LivingEntityAccessor.automata_valkyries$getDataHealthId().getId());
                    if (item != null && item.getValue() instanceof Number num) {
                        return num.floatValue();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0.0F;
    }

    private static void writeDataItemValueRaw(SynchedEntityData.DataItem<?> item, Object value) {
        if (DATA_ITEM_VALUE_FIELD != null) {
            try {
                DATA_ITEM_VALUE_FIELD.set(item, value);
                return;
            } catch (Throwable ignored) {
            }
        }
        // フォールバック
        @SuppressWarnings("unchecked")
        SynchedEntityData.DataItem<Object> raw = (SynchedEntityData.DataItem<Object>) item;
        raw.setValue(value);
    }

    @Nullable
    private static Field resolveDataItemValueField() {
        for (Field f : SynchedEntityData.DataItem.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Object.class) {
                try {
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
