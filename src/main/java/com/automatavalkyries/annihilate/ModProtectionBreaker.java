package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.util.StaticReferencePurger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 他 MOD のボスが持つ「独自の生命維持」を外して、その MOD 自身の死亡・消去経路で倒す（超絶抹殺の棒の前処理・後処理）。
 * <ol>
 *   <li>防御構造の探索・調査・把握・解析（{@link DefenseStructureAnalyzer}）: 破壊操作の前に相手の防御機構・正体・本体・関連クラス網を一括マッピング</li>
 *   <li>既知の MOD の本来の撃破経路（個別対応）: The Trial Monolith の魂ダメージ（侵略者のモノリス・テッセラクトビースト）等</li>
 *   <li>準汎用（未知の MOD 向け。どれも「他 MOD のクラス」だけを対象にし、vanilla・Forge・このMOD のクラスには触れない）:
 *     <ul>
 *       <li>守りの状態: フラグ辞書（{@link FlagDictionary}）で守り・復活の許可と分かる状態を、他 MOD のインターフェース・
 *           既存クラスに足されたフィールド・対象自身のフィールド・Capability・永続データから下ろし、残り復活回数を 0 に、
 *           守りの迂回を有効にする。守りを表す他 MOD の状態異常も外す（待ち時間などのタイマーは触らない）</li>
 *       <li>代理と本体: 直接参照・ラッパー越しの参照・static 逆引きで見つけた本体の remove() / kill() / discard() / 終了メソッドを呼び、
 *           さらに本体の保護状態と逆参照を解体する</li>
 *       <li>静的な一覧・シングルトン: 対象・本体のクラスと関連クラス網の static な Collection / Map / 配列 / 単一参照から、
 *           対象・本体・対象の UUID だけを取り除く（復活待ちの一覧・管理表・シングルトン）</li>
 *       <li>つながった個体: 同じ MOD の乗客・乗り物（{@link #connectedEntities}。抹殺側がまとめて消す）</li>
 *     </ul>
 *   </li>
 * </ol>
 * どれも失敗しても抹殺の本処理（バニラの即死・追跡偽装）は続けるので、例外はすべて握りつぶす。
 */
public final class ModProtectionBreaker {

    private static final String[] CONTROLLER_KILL_METHODS = {"remove", "kill", "discard"};

    private static final String TRIAL_MONOLITH = "io.github.kosianodangoo.trialmonolith.";

    private ModProtectionBreaker() {}

    /** 前処理で見つけた本体と構造解析レポート（後処理の静的な一覧の掃除でも取り除く） */
    public record Context(
            List<Object> controllers,
            Set<Class<?>> registryClasses,
            Set<UUID> identities,
            DefenseStructureAnalyzer.AnalysisReport report
    ) {
        static final Context EMPTY = new Context(
                List.of(),
                Set.of(),
                Set.of(),
                DefenseStructureAnalyzer.AnalysisReport.EMPTY
        );

        public Context(List<Object> controllers, Set<Class<?>> registryClasses, Set<UUID> identities) {
            this(controllers, registryClasses, identities, DefenseStructureAnalyzer.AnalysisReport.EMPTY);
        }
    }

    /** 抹殺の前処理: 防御構造の探索・調査・把握・解析を一括で行い、生命維持を外し、本体を消し、静的な一覧から取り除く */
    public static Context breakProtection(ServerLevel level, Entity target) {
        if (MadnessAnnihilation.isMadness(target) && com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
            try { MadnessAnnihilation.prepare(target); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                AutomataValkyries.LOGGER.warn("[Madness compatibility] Preparation failed", failure);
            }
            return Context.EMPTY;
        }
        FlagKillPredicates.ensureAgentProtection();
        // 1. 探索・調査・把握・解析: 破壊操作で参照が失われる前に防御構造の全体像を把握する
        DefenseStructureAnalyzer.AnalysisReport report = DefenseStructureAnalyzer.analyze(target);
        Set<Class<?>> registryClasses = new LinkedHashSet<>(report.registryClasses());
        Set<UUID> identities = new LinkedHashSet<>(report.identities());
        identities.add(MadnessAnnihilation.uuid(target));
        for (UUID id : identities) {
            AnnihilationExecutionAgent.markAnnihilated(id);
        }
        captureRegistryClasses(target, registryClasses);
        captureRelatedClasses(target.getClass(), registryClasses);

        // 2. 個別および準汎用の保護状態解除（対象クラス群のコールバック・フックを一時隔離した状態で執行）
        final List<Object> controllers = new ArrayList<>();
        com.automatavalkyries.protect.plus.EprotectPlusLink.runWithIsolatedTargetHooks(registryClasses, () -> {
            breakProtectiveStates(target);
            neutralizeForeignFunctionalFields(target);
            breakTrialMonolith(target);
            breakSuperSteve(target);
            breakPig2(target);
            breakNekohashizuku(target);

            // 3. 本体（直接・ラッパー内・static 逆引き発見を含む全コントローラー）の解体
            controllers.addAll(removeControllers(target, report.controllers(), registryClasses, identities));
            invokeStaticEntityKillHelpers(target, registryClasses);
            cleanupForeignBoss(target);
        });
        Context context = new Context(
                List.copyOf(controllers),
                Set.copyOf(registryClasses),
                Set.copyOf(identities),
                report
        );

        // 4. 静的レジストリ・シングルトン・スレッド・外部セーブ・EventBus の一掃
        purgeStaticReferences(target, context);
        if (report.foreign() || isForeign(target.getClass()) || !controllers.isEmpty()) {
            severForeignThreadReferences(target, context);
            deleteOwnedForeignSaveFiles(level, context.identities());
            com.automatavalkyries.protect.EventBusGuardian.ensureEventBusIntegrity();
        }
        return context;
    }

    /** 抹殺の後処理: 死亡処理の途中で一覧に戻されていれば、もう一度取り除く */
    public static void afterErase(Entity target, Context context) {
        if (MadnessAnnihilation.isMadness(target) && com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        cleanupForeignBoss(target);
        for (Object controller : context.controllers()) cleanupForeignBoss(controller);
        purgeStaticReferences(target, context);
        if (context.report().foreign() || isForeign(target.getClass()) || !context.controllers().isEmpty()) {
            if (target.level() instanceof ServerLevel serverLevel) {
                severForeignThreadReferences(target, context);
                deleteOwnedForeignSaveFiles(serverLevel, context.identities());
            }
            com.automatavalkyries.protect.EventBusGuardian.ensureEventBusIntegrity();
        }
    }

    /** 抹殺の最後に呼ぶ: 対応する MOD 自身の撃破経路を走らせる（魂ダメージの最大化で死亡処理と完全消去） */
    public static void finishWithModOwnDeath(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        if (MadnessAnnihilation.isMadness(target)) {
            try { MadnessAnnihilation.nativeDelete(target); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                AutomataValkyries.LOGGER.warn("[Madness compatibility] Native removal failed", failure);
            }
            return;
        }
        if (!target.getClass().getName().startsWith(TRIAL_MONOLITH)) return;
        try {
            Class<?> helper = Class.forName(TRIAL_MONOLITH + "common.helper.EntityHelper");
            helper.getMethod("setSoulDamageForce", Entity.class, float.class).invoke(null, target, Float.MAX_VALUE);
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] Trial Monolith soul death failed for {}", target, t);
        }
    }

    /**
     * 対象とつながった同じ MOD の個体（乗客・乗り物。部位は追跡偽装で扱う）。対象が他 MOD の個体のときだけ広げ、
     * プレイヤー・保護対象（兵姫）は含めない。つながった個体からさらに広げはしない。
     */
    public static List<Entity> connectedEntities(Entity target) {
        String mod = CallerAttribution.modOf(target.getClass());
        if (!CallerAttribution.isForeign(mod)) return List.of();
        Set<Entity> found = Collections.newSetFromMap(new IdentityHashMap<>());
        target.getIndirectPassengers().forEach(found::add);
        for (Entity vehicle = target.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) found.add(vehicle);
        List<Entity> result = new ArrayList<>();
        for (Entity entity : found) {
            if (entity == target || entity instanceof Player || (entity instanceof IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget())) continue;
            if (mod.equals(CallerAttribution.modOf(entity.getClass()))) result.add(entity);
        }
        return result;
    }

    // ---- 個別対応 ------------------------------------------------------------

    /**
     * The Trial Monolith: HP は常に無限大で、死ぬのは魂ダメージ（EntityHelper.setSoulDamageForce → onSoulDeath / onSoulRemove）だけ。
     * 魂の保護・高次元障壁・次元コアを外し、保護の迂回を有効にする。テッセラクトビーストは代理エンティティで、本体の
     * コントローラーを remove() しないと代理を作り直す（{@link #removeControllers} が扱う）。
     */
    private static void breakTrialMonolith(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        if (!target.getClass().getName().startsWith(TRIAL_MONOLITH)) return;
        try {
            Class<?> helper = Class.forName(TRIAL_MONOLITH + "common.helper.EntityHelper");
            helper.getMethod("setSoulProtected", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setHighDimensionalBarrier", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setDimensionalCore", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setBypassProtection", Entity.class, boolean.class).invoke(null, target, true);
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Disabled Trial Monolith soul protection on {}", target.getName().getString());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] Trial Monolith protection breaking failed for {}", target, t);
        }
    }

    private static void breakSuperSteve(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        String name = target.getClass().getName();
        if (!name.startsWith("plz.lizi.supersteve.")) return;
        try {
            if (name.contains("SafeServerPlayer") || name.contains("DeathServerPlayer")) {
                try {
                    Method dc = com.automatavalkyries.util.UnsafeHelper.findRealMethod(target.getClass(), "disconnect");
                    if (dc != null) {
                        dc.invoke(target);
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Disconnected SuperSteve player {}", target.getName().getString());
                    }
                } catch (Throwable ignored) {}
            }
            for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = com.automatavalkyries.util.UnsafeHelper.findField(c, "health");
                    if (f != null) {
                        Object op = f.get(target);
                        if (op != null) {
                            Method m = com.automatavalkyries.util.UnsafeHelper.findRealMethod(op.getClass(), "operate", Object[].class);
                            Field vrfField = com.automatavalkyries.util.UnsafeHelper.findField(c, "VRF");
                            String[] vrf = vrfField != null ? (String[]) vrfField.get(null) : null;
                            if (m != null && vrf != null && vrf.length > 5) {
                                m.invoke(op, new Object[]{ new Object[]{ vrf[5], 0.0F } });
                                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Reset SuperSteve health operator on {}", target.getName().getString());
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    Field be = com.automatavalkyries.util.UnsafeHelper.findField(c, "bossEvent");
                    if (be != null) {
                        Object bossEvent = be.get(target);
                        if (bossEvent != null) {
                            Method clear = com.automatavalkyries.util.UnsafeHelper.findRealMethod(bossEvent.getClass(), "removeAllPlayers");
                            if (clear != null) clear.invoke(bossEvent);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            try {
                Class<?> ssu = Class.forName("plz.lizi.supersteve.api.SSUtil");
                Method k = com.automatavalkyries.util.UnsafeHelper.findRealMethod(ssu, "killEntity", Entity.class, boolean.class);
                if (k != null) k.invoke(null, target, true);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] SuperSteve protection breaking failed for {}", target, t);
        }
    }

    /**
     * 準汎用: 対象（または本体）が持つ他 MOD 独自の関数型インターフェース / オペレーター型のフィールド
     * （例: SuperSteve の {@code Operator health} のように暗号トークン配列とペアで状態を隠蔽する構造）を検出し、
     * 同じクラス階層の static {@code String[]} トークン配列を用いてゼロ化を試みたうえで、
     * 常に {@code 0.0F} / {@code false} を返し例外を出さないゼロプロキシに差し替える。
     */
    public static void neutralizeForeignFunctionalFields(Object owner) {
        if (owner == null) return;
        for (Class<?> c = owner.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            for (Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                if (!f.getType().isInterface() || !isForeign(f.getType())) continue;
                neutralizeForeignFunctionalField(owner, c, f);
            }
        }
    }

    public static void neutralizeForeignFunctionalField(Object owner, Class<?> declaringClass, Field field) {
        if (owner == null || field == null || !field.getType().isInterface() || !isForeign(field.getType())) return;
        try {
            long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(field);
            if (offset < 0) return;
            Object current = com.automatavalkyries.util.UnsafeHelper.getObject(owner, offset);
            if (current != null && declaringClass != null) {
                Method varargsMethod = null;
                for (Method m : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(field.getType())) {
                    if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object[].class) {
                        varargsMethod = m;
                        break;
                    }
                }
                if (varargsMethod != null) {
                    varargsMethod.setAccessible(true);
                    String fieldLower = field.getName().toLowerCase(java.util.Locale.ROOT);
                    Object targetValue = (fieldLower.contains("dead") || fieldLower.contains("remove")) ? Boolean.TRUE : Float.valueOf(0.0F);
                    for (Class<?> tc = declaringClass; tc != null && isForeign(tc); tc = tc.getSuperclass()) {
                        for (Field sf : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(tc)) {
                            if (!Modifier.isStatic(sf.getModifiers()) || sf.getType() != String[].class) continue;
                            try {
                                sf.setAccessible(true);
                                String[] tokens = (String[]) sf.get(null);
                                if (tokens == null) continue;
                                for (String token : tokens) {
                                    if (token == null) continue;
                                    try {
                                        varargsMethod.invoke(current, new Object[]{ new Object[]{ token, targetValue } });
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            Object zeroProxy = com.automatavalkyries.util.UnsafeHelper.createZeroProxy(field.getType());
            if (zeroProxy != null) {
                com.automatavalkyries.util.UnsafeHelper.putObject(owner, offset, zeroProxy);
            }
        } catch (Throwable ignored) {}
    }

    private static void breakPig2(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        if (!target.getClass().getName().startsWith("kakiku.pig2mod.")) return;
        try {
            try {
                Method stop = target.getClass().getDeclaredMethod("stopFightingRemovedEnemy");
                stop.setAccessible(true);
                stop.invoke(target);
            } catch (Throwable ignored) {}
            for (String fieldName : new String[]{"mAttackingEnemy", "mAttackLevel"}) {
                try {
                    Field f = target.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(target, null);
                } catch (Throwable ignored) {}
            }
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Neutralized Pig2 combat state on {}", target.getName().getString());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] Pig2 protection breaking failed for {}", target, t);
        }
    }

    private static void breakNekohashizuku(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
        try {
            Class<?> helper = Class.forName("com.buwan.shizuku.core.AgentHealthMethodHelper");
            Field f = helper.getDeclaredField("HEALTH_MAP");
            f.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) f.get(null);
            if (map != null && map.remove(target) != null) {
                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Removed {} from Nekohashizuku HEALTH_MAP", target.getName().getString());
            }
        } catch (Throwable ignored) {}
    }

    // ---- 準汎用: 保護の切り替え・フラグ・永続データ・状態異常 -------------------------------

    private static final EnumSet<StateScanner.Source> SOURCES = EnumSet.allOf(StateScanner.Source.class);

    /**
     * フラグ辞書（{@link FlagDictionary}）で守り・復活の許可と分かる状態を下ろし、残り復活回数を 0 に、守りの迂回を有効にする。
     * 他 MOD のインターフェース・既存クラスに足されたフィールド・対象のクラス自身のフィールド・Capability・永続データが対象
     * （{@link StateScanner}）。復活までの待ち時間などのタイマーは、0 にすると早まりうるので触らない。
     */
    private static void breakProtectiveStates(Entity target) {
        for (StateScanner.Change change : StateScanner.apply(target, StateScanner.BREAK_TARGET, SOURCES)) {
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Broke {} ({}) on {}", change, change.mod(), target.getName().getString());
        }
        if (!(target instanceof LivingEntity living)) return;
        List<MobEffect> protective = new ArrayList<>();
        for (MobEffectInstance instance : living.getActiveEffects()) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect());
            if (key != null && CallerAttribution.isForeign(key.getNamespace()) && FlagKillPredicates.protectiveOnTarget(key.getPath())) {
                protective.add(instance.getEffect());
            }
        }
        for (MobEffect effect : protective) {
            living.removeEffect(effect);
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Removed protective effect {} from {}",
                    BuiltInRegistries.MOB_EFFECT.getKey(effect), target.getName().getString());
        }
    }

    // ---- 準汎用: 代理と本体 ------------------------------------------------------

    /** 代理エンティティの本体（直接参照・ラッパー内・static 逆引きで発見された本体）を remove() / kill() / discard() で消し、見つけた本体を返す */
    private static List<Object> removeControllers(
            Entity target,
            List<Object> analyzedControllers,
            Set<Class<?>> registryClasses,
            Set<UUID> identities
    ) {
        List<Object> controllers = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> candidates = new ArrayList<>(analyzedControllers);
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller != null && controller != target && !(controller instanceof Entity)
                            && isForeign(controller.getClass())
                            && GenericCheatMobNeutralizer.ownsController(controller, target, identities, false)) {
                        candidates.add(controller);
                    }
                } catch (Throwable ignored) {}
            }
        }
        for (Object controller : candidates) {
            if (controller == null || controller == target || controller instanceof Entity
                    || !isForeign(controller.getClass())) continue;
            if (!seen.add(controller)) continue;
            try {
                controllers.add(controller);
                captureRegistryClasses(controller, registryClasses);
                captureRelatedClasses(controller.getClass(), registryClasses);
                captureControllerIdentity(controller, identities);
                cleanupForeignBoss(controller);
                if (com.automatavalkyries.protect.plus.EprotectPlusLink.hasHaltOrExitTrap(controller.getClass())) {
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] Skipped native termination methods on {} due to halt/exit trap; using Unsafe field neutralization",
                            controller.getClass().getSimpleName());
                    continue;
                }
                boolean invoked = false;
                for (String name : CONTROLLER_KILL_METHODS) {
                    Method kill = findNoArg(controller.getClass(), name);
                    if (kill == null) continue;
                    kill.setAccessible(true);
                    kill.invoke(controller);
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called {}.{}() (controller of {})",
                            controller.getClass().getSimpleName(), name, target.getName().getString());
                    invoked = true;
                    break;
                }
                if (!invoked) {
                    for (Class<?> c = controller.getClass(); c != null && c != Object.class && !invoked; c = c.getSuperclass()) {
                        for (Method m : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(c)) {
                            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers()) || isTrapTerminationName(m.getName())) continue;
                            if (m.getParameterCount() == 0 && FlagKillPredicates.isTerminationMethod(m.getName())) {
                                m.setAccessible(true);
                                m.invoke(controller);
                                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called termination method {}.{}() (controller of {})",
                                        controller.getClass().getSimpleName(), m.getName(), target.getName().getString());
                                invoked = true;
                                break;
                            }
                        }
                    }
                }
                if (!invoked) {
                    outer:
                    for (Class<?> c = controller.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                        for (Method m : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(c)) {
                            if (Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers()) || isTrapTerminationName(m.getName())) continue;
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                                    && FlagKillPredicates.isTerminationMethod(m.getName())) {
                                m.setAccessible(true);
                                m.invoke(controller, true);
                                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called termination method {}. {}(true) (controller of {})",
                                        controller.getClass().getSimpleName(), m.getName(), target.getName().getString());
                                break outer;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        // All entries passed ownership checks before native removal. Reject newly shared cores,
        // but allow native removal to have cleared the original reverse reference.
        for (Object controller : controllers) {
            GenericCheatMobNeutralizer.resetCapturedCore(controller, target);
        }
        return controllers;
    }

    /**
     * 関連クラス網（SSUtil 等のヘルパークラスを含む）に定義された public static なエンティティ抹殺・除去メソッド
     * （{@code killEntity(Entity, boolean)} や {@code removeEntity(Entity)} 等）を汎用的に呼び出す。
     * バイトコードに {@code Runtime.halt} / {@code System.exit} トラップを含むクラスはスキップする。
     */
    private static void invokeStaticEntityKillHelpers(Entity target, Set<Class<?>> registryClasses) {
        if (target == null || registryClasses == null || registryClasses.isEmpty()) return;
        for (Class<?> cls : registryClasses) {
            if (cls == null || !isForeign(cls) || Entity.class.isAssignableFrom(cls)) continue;
            if (com.automatavalkyries.protect.plus.EprotectPlusLink.hasHaltOrExitTrap(cls)) continue;
            Method[] methods;
            try {
                methods = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(cls);
            } catch (Throwable t) {
                continue;
            }
            for (Method m : methods) {
                int mod = m.getModifiers();
                if (!Modifier.isStatic(mod) || !Modifier.isPublic(mod) || m.isSynthetic()) continue;
                String name = m.getName();
                if (isTrapTerminationName(name)) continue;
                Class<?>[] params = m.getParameterTypes();
                try {
                    if (params.length == 2
                            && (params[0] == Entity.class || (params[0] == LivingEntity.class && target instanceof LivingEntity))
                            && params[1] == float.class
                            && name.toLowerCase(java.util.Locale.ROOT).startsWith("set")
                            && FlagDictionary.is(name, FlagDictionary.Category.KILL)
                            && !FlagKillPredicates.protectiveOnTarget(name)) {
                        m.setAccessible(true);
                        m.invoke(null, target, Float.MAX_VALUE);
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Invoked static entity kill-damage helper {}.{}(target, MAX_VALUE)",
                                cls.getSimpleName(), name);
                        continue;
                    }
                    if (!FlagKillPredicates.isTerminationMethod(name)) continue;
                    if (params.length == 2
                            && (params[0] == Entity.class || (params[0] == LivingEntity.class && target instanceof LivingEntity))
                            && params[1] == boolean.class) {
                        m.setAccessible(true);
                        m.invoke(null, target, true);
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Invoked static entity kill helper {}.{}(target, true)",
                                cls.getSimpleName(), name);
                    } else if (params.length == 1
                            && (params[0] == Entity.class || (params[0] == LivingEntity.class && target instanceof LivingEntity))) {
                        m.setAccessible(true);
                        m.invoke(null, target);
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Invoked static entity kill helper {}.{}(target)",
                                cls.getSimpleName(), name);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    // ---- 準汎用: 静的な一覧 ------------------------------------------------------

    /** 他 MOD の対象と、対象を指し返すコントローラーだけを後始末する。 */
    private static void cleanupForeignBoss(Object owner) {
        Set<String> invoked = new LinkedHashSet<>();
        for (Class<?> type = owner.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            Method[] methods;
            Field[] fields;
            try {
                methods = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(type);
                fields = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : methods) {
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) continue;
                String name = method.getName();
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                boolean stopCombat = (lower.startsWith("stop") && (lower.contains("fighting") || lower.contains("combat")))
                        || lower.startsWith("cleartarget") || lower.startsWith("resettarget");
                boolean disconnect = name.equals("disconnect") || name.equals("kick");
                Object[] args;
                if ((stopCombat || disconnect) && method.getParameterCount() == 0) {
                    args = new Object[0];
                } else if (disconnect && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
                    args = new Object[]{"Annihilated"};
                } else if (disconnect && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Component.class) {
                    args = new Object[]{Component.literal("Annihilated")};
                } else continue;
                if (!invoked.add(name + java.util.Arrays.toString(method.getParameterTypes()))) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(owner, args);
                } catch (Throwable ignored) {}
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    String lower = field.getName().toLowerCase(java.util.Locale.ROOT);
                    if (value instanceof ServerBossEvent boss) {
                        boss.removeAllPlayers();
                        boss.setVisible(false);
                    } else if (value != null && (lower.equals("bossevent") || lower.equals("bossbar"))) {
                        invokeBossBarMethod(value, "removeAllPlayers", new Class<?>[0], new Object[0]);
                        invokeBossBarMethod(value, "setVisible", new Class<?>[]{boolean.class}, new Object[]{false});
                    }
                    if (lower.startsWith("mattacking") || lower.equals("target")
                            || lower.equals("attacktarget") || lower.equals("currenttarget")) {
                        field.set(owner, null);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void invokeBossBarMethod(Object boss, String name, Class<?>[] parameters, Object[] args) {
        try {
            Method method = com.automatavalkyries.util.UnsafeHelper.findRealMethod(boss.getClass(), name, parameters);
            if (method == null || Modifier.isStatic(method.getModifiers())) return;
            method.setAccessible(true);
            method.invoke(boss, args);
        } catch (Throwable ignored) {}
    }

    /**
     * 対象・本体のクラスと、対象のフィールドが直接指す他 MOD のオブジェクトのクラス（それぞれの親クラス含む）の static な
     * Collection / Map / 配列 / 単一参照から、対象・本体・対象の UUID だけを取り除く。MOD 全体は走査しない。
     */
    private static void purgeStaticReferences(Entity target, Context context) {
        List<Object> controllers = context.controllers();
        Set<Class<?>> classes = new LinkedHashSet<>(context.registryClasses());
        addForeignHierarchy(target.getClass(), classes);
        for (Object controller : controllers) addForeignHierarchy(controller.getClass(), classes);
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value != null && !(value instanceof Entity) && isForeign(value.getClass())) {
                        addForeignHierarchy(value.getClass(), classes);
                    }
                } catch (Throwable ignored) {}
            }
        }

        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        victims.addAll(controllers);
        for (UUID identity : context.identities()) {
            StaticReferencePurger.purge(classes, victims, identity, MadnessAnnihilation.id(target), target.getName().getString());
        }
    }

    /**
     * 対象のクラス階層が関わる他 MOD のクラス（tick から static 呼び出しでしか触らない管理クラス等）も、静的な一覧の掃除先に加える。
     * 初期化済みのクラスだけ（static を読むと初期化が走るため）
     */
    private static void captureRelatedClasses(Class<?> type, Set<Class<?>> classes) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            try {
                for (Class<?> related : com.automatavalkyries.protect.flag.KillLists.relatedClasses(c)) {
                    if (classes.size() >= 128) return;
                    if (com.automatavalkyries.util.UnsafeHelper.isInitialized(related)) addForeignHierarchy(related, classes);
                }
                for (Class<?> subscriber : com.automatavalkyries.protect.EventBusGuardian.subscriberClassesFor(c)) {
                    if (classes.size() >= 128) return;
                    addForeignHierarchy(subscriber, classes);
                }
                for (Class<?> agentLoaded : com.automatavalkyries.protect.plus.EprotectPlusLink.findAgentLoadedClassesFor(c)) {
                    if (classes.size() >= 128) return;
                    addForeignHierarchy(agentLoaded, classes);
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Keep directly reachable registry classes before native removal/Unsafe destroys their references. */
    private static void captureRegistryClasses(Object owner, Set<Class<?>> classes) {
        addForeignHierarchy(owner.getClass(), classes);
        for (Class<?> type = owner.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value != null && !(value instanceof Entity) && isForeign(value.getClass())) {
                        addForeignHierarchy(value.getClass(), classes);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Only conventional identity fields on an ownership-checked core; unrelated UUID fields are retained. */
    private static void captureControllerIdentity(Object controller, Set<UUID> identities) {
        for (Class<?> type = controller.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(type)) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != UUID.class
                        || !(name.equals("id") || name.equals("uuid") || name.equals("uniqueid"))) continue;
                try {
                    field.setAccessible(true);
                    UUID id = (UUID) field.get(controller);
                    if (id != null) {
                        identities.add(id);
                        AnnihilationExecutionAgent.markAnnihilated(id);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
    // ---- 共通 --------------------------------------------------------------

    private static final Set<String> VANILLA_WORLD_ENTRIES = Set.of(
            "region", "DIM-1", "DIM1", "entities", "poi", "data", "playerdata",
            "advancements", "stats", "datapacks", "serverconfig", "dimensions", "generated",
            "icon.png", "level.dat", "level.dat_old", "session.lock"
    );

    /**
     * ワールド保存フォルダ配下の他 MOD 用ディレクトリにあるファイルのうち、
     * 抹殺対象（またはその本体）の UUID をファイル名または内容（平文・16バイトバイナリ・GZIP圧縮NBT）に含むものだけを削除する。
     * 他の個体のセーブデータには一切触れない。
     */
    public static int deleteOwnedForeignSaveFiles(ServerLevel level, Set<UUID> identities) {
        if (level == null || level.getServer() == null || identities == null || identities.isEmpty()) return 0;
        if (!com.automatavalkyries.config.EprotectConfig.get(com.automatavalkyries.config.EprotectConfig.ANNIHILATION_DELETE_OMNIMOB_SAVES)) {
            return 0;
        }
        int deleted = 0;
        try {
            java.nio.file.Path worldRoot = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            if (!java.nio.file.Files.isDirectory(worldRoot)) return 0;
            try (var rootStream = java.nio.file.Files.list(worldRoot)) {
                for (java.nio.file.Path subDir : rootStream.toList()) {
                    String dirName = subDir.getFileName().toString();
                    if (VANILLA_WORLD_ENTRIES.contains(dirName) || !java.nio.file.Files.isDirectory(subDir)) continue;
                    try (var walk = java.nio.file.Files.walk(subDir, 2)) {
                        for (java.nio.file.Path file : walk.toList()) {
                            if (!java.nio.file.Files.isRegularFile(file)) continue;
                            long size = java.nio.file.Files.size(file);
                            if (size <= 0 || size > 1_048_576L) continue;
                            if (fileContainsAnyIdentity(file, identities)) {
                                if (java.nio.file.Files.deleteIfExists(file)) {
                                    deleted++;
                                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] Deleted owned foreign save file {}",
                                            worldRoot.relativize(file));
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return deleted;
    }

    private static boolean fileContainsAnyIdentity(java.nio.file.Path file, Set<UUID> identities) {
        String fileName = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        for (UUID id : identities) {
            String dashed = id.toString().toLowerCase(java.util.Locale.ROOT);
            String compact = dashed.replace("-", "");
            if (fileName.contains(dashed) || fileName.contains(compact)) return true;
        }
        try {
            byte[] raw = java.nio.file.Files.readAllBytes(file);
            if (bytesContainAnyIdentity(raw, identities)) return true;
            if (raw.length >= 2 && raw[0] == (byte) 0x1f && raw[1] == (byte) 0x8b) {
                try (var bais = new java.io.ByteArrayInputStream(raw);
                     var gzip = new java.util.zip.GZIPInputStream(bais);
                     var baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int total = 0;
                    int r;
                    while ((r = gzip.read(buf)) != -1 && total < 1_048_576) {
                        baos.write(buf, 0, r);
                        total += r;
                    }
                    if (bytesContainAnyIdentity(baos.toByteArray(), identities)) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean bytesContainAnyIdentity(byte[] data, Set<UUID> identities) {
        if (data == null || data.length < 16) return false;
        for (UUID id : identities) {
            byte[] ascii = id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (indexOfBytes(data, ascii) >= 0) return true;
            byte[] binary = uuidToBytes(id);
            if (indexOfBytes(data, binary) >= 0) return true;
        }
        return false;
    }

    private static byte[] uuidToBytes(UUID id) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(16);
        bb.putLong(id.getMostSignificantBits());
        bb.putLong(id.getLeastSignificantBits());
        return bb.array();
    }

    private static int indexOfBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return -1;
        byte first = needle[0];
        int limit = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= limit; i++) {
            if (haystack[i] != first) continue;
            for (int j = 1; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static Method findNoArg(Class<?> type, String name) {
        return com.automatavalkyries.util.UnsafeHelper.findRealMethod(type, name);
    }

    private static void addForeignHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) out.add(c);
    }

    /** 他 MOD のクラスか（vanilla・Forge・Java・このMOD・ライブラリは対象外。Bootstrap 注入・Hidden クラスも対象） */
    private static boolean isForeign(Class<?> type) {
        if (type == null) return false;
        if (CallerAttribution.isForeign(CallerAttribution.modOf(type))) return true;
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.")
                || name.startsWith("com.mojang.") || name.startsWith("net.minecraftforge.")
                || name.startsWith("cpw.mods.") || name.startsWith("org.spongepowered.")
                || name.startsWith("org.objectweb.") || name.startsWith("org.slf4j.")) {
            return false;
        }
        if (name.startsWith("net.minecraft.") && !name.startsWith("net.minecraft.madcrazy.")) return false;
        if (name.startsWith("com.automatavalkyries.") && !name.startsWith("com.automatavalkyries.gametest.fake.")) return false;
        return type.getClassLoader() == null || type.isHidden();
    }

    /**
     * 実行中の別スレッド（{@link Thread} サブクラスや {@code Runnable} / ラムダ式）のうち、
     * 他 MOD に属していて抹殺対象・本体・対象の UUID を直接握っているものの参照を {@code null} で断ち切る。
     * 他の個体を指しているスレッドや vanilla / Forge のスレッド本体には触れない。
     */
    public static int severForeignThreadReferences(Entity target, Context context) {
        if (target == null || !com.automatavalkyries.util.UnsafeHelper.isAvailable()) return 0;
        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        victims.addAll(context.controllers());
        Set<UUID> identities = context.identities();
        int severedCount = 0;
        Thread self = Thread.currentThread();
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread == null || thread == self || !thread.isAlive()) continue;
                Set<Object> tasks = Collections.newSetFromMap(new IdentityHashMap<>());
                if (isForeignTaskClass(thread.getClass())) {
                    tasks.add(thread);
                }
                collectThreadTasks(thread, tasks, 0);
                for (Object task : tasks) {
                    if (severTaskReferences(task, victims, identities)) {
                        severedCount++;
                    }
                }
            }
        } catch (Throwable ignored) {}
        severedCount += severStaticExecutorQueueTasks(context.registryClasses(), victims, identities);
        return severedCount;
    }

    /**
     * 外部エージェントや対象 MOD のクラスが static に保持する {@link java.util.concurrent.ExecutorService} /
     * {@link java.util.concurrent.ScheduledThreadPoolExecutor} / タスクキュー内の待機タスク（{@code FutureTask} 等）を走査し、
     * 抹殺対象（または強化保護対象）のみを狙うタスクの参照を切断・キャンセル・除去する。
     */
    private static int severStaticExecutorQueueTasks(
            Set<Class<?>> registryClasses,
            Set<Object> victims,
            Set<UUID> identities
    ) {
        int severed = 0;
        Set<Class<?>> scanClasses = new LinkedHashSet<>();
        if (registryClasses != null) scanClasses.addAll(registryClasses);
        try {
            scanClasses.addAll(com.automatavalkyries.protect.plus.EprotectPlusLink.findForeignAgentAndHiddenClasses());
        } catch (Throwable ignored) {}

        for (Class<?> cls : scanClasses) {
            if (cls == null || !isForeign(cls) || !com.automatavalkyries.util.UnsafeHelper.isInitialized(cls)) continue;
            Field[] fields;
            try {
                fields = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(cls);
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object holder = f.get(null);
                    if (holder == null) continue;
                    java.util.Collection<?> queue = extractExecutorQueue(holder, 0);
                    if (queue == null || queue.isEmpty()) continue;
                    for (Object elem : new ArrayList<>(queue)) {
                        if (elem == null) continue;
                        Set<Object> innerTasks = Collections.newSetFromMap(new IdentityHashMap<>());
                        if (isForeignTaskClass(elem.getClass())) {
                            innerTasks.add(elem);
                        }
                        collectThreadTasks(elem, innerTasks, 0);
                        boolean matched = false;
                        for (Object task : innerTasks) {
                            if (severTaskReferences(task, victims, identities)) {
                                matched = true;
                                severed++;
                            }
                        }
                        if (matched) {
                            if (elem instanceof java.util.concurrent.Future<?> future) {
                                try {
                                    future.cancel(false);
                                } catch (Throwable ignored) {}
                            }
                            try {
                                queue.remove(elem);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return severed;
    }

    private static java.util.Collection<?> extractExecutorQueue(Object holder, int depth) {
        if (holder == null || depth > 2) return null;
        if (holder instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return tpe.getQueue();
        }
        if (holder instanceof java.util.concurrent.BlockingQueue<?> bq) {
            return bq;
        }
        if (holder instanceof java.util.concurrent.Executor) {
            for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(f);
                    if (offset < 0) continue;
                    Object sub = com.automatavalkyries.util.UnsafeHelper.getObject(holder, offset);
                    if (sub != null && sub != holder) {
                        java.util.Collection<?> found = extractExecutorQueue(sub, depth + 1);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private static void collectThreadTasks(Object holder, Set<Object> out, int depth) {
        if (holder == null || depth >= 3 || out.size() >= 16) return;
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields;
            try {
                fields = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c);
            } catch (Throwable t) {
                continue;
            }
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(f);
                if (offset < 0) continue;
                Object val = com.automatavalkyries.util.UnsafeHelper.getObject(holder, offset);
                if (val == null || val == holder || val instanceof Entity || val instanceof Class<?>
                        || val instanceof ThreadGroup || val instanceof ClassLoader) continue;
                boolean foreignTask = isForeignTaskClass(val.getClass());
                boolean wrapperTask = val instanceof Runnable
                        || val instanceof java.util.concurrent.Callable<?>
                        || val instanceof java.util.concurrent.Future<?>;
                if (!foreignTask && !wrapperTask) continue;
                if (foreignTask) out.add(val);
                collectThreadTasks(val, out, depth + 1);
            }
        }
    }

    private static boolean severTaskReferences(Object task, Set<Object> victims, Set<UUID> identities) {
        for (Class<?> c = task.getClass(); c != null && c != Object.class && c != Thread.class; c = c.getSuperclass()) {
            if (!isForeignTaskClass(c)) continue;
            for (Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(f);
                if (offset < 0) continue;
                Object val = com.automatavalkyries.util.UnsafeHelper.getObject(task, offset);
                if (val instanceof Entity e && !victims.contains(e)) return false;
                if (val instanceof java.lang.ref.Reference<?> ref && ref.get() instanceof Entity e && !victims.contains(e)) {
                    return false;
                }
            }
        }
        boolean severed = false;
        for (Class<?> c = task.getClass(); c != null && c != Object.class && c != Thread.class; c = c.getSuperclass()) {
            if (!isForeignTaskClass(c)) continue;
            for (Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(f);
                if (offset < 0) continue;
                Object val = com.automatavalkyries.util.UnsafeHelper.getObject(task, offset);
                if (val == null) continue;
                boolean match = victims.contains(val)
                        || (val instanceof java.lang.ref.Reference<?> ref && victims.contains(ref.get()))
                        || (val instanceof UUID id && identities.contains(id));
                if (match) {
                    com.automatavalkyries.util.UnsafeHelper.putObject(task, offset, null);
                    severed = true;
                }
            }
        }
        if (severed) {
            for (Class<?> c = task.getClass(); c != null && c != Object.class && c != Thread.class; c = c.getSuperclass()) {
                if (!isForeignTaskClass(c)) continue;
                for (Field f : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String lower = f.getName().toLowerCase(java.util.Locale.ROOT);
                    long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(f);
                    if (offset < 0) continue;
                    if (f.getType() == boolean.class) {
                        if (lower.equals("running") || lower.equals("active") || lower.equals("alive") || lower.equals("loop")) {
                            com.automatavalkyries.util.UnsafeHelper.putBoolean(task, offset, false);
                        } else if (lower.equals("stopped") || lower.equals("cancelled") || lower.equals("done")) {
                            com.automatavalkyries.util.UnsafeHelper.putBoolean(task, offset, true);
                        }
                    } else if (f.getType() == java.util.concurrent.atomic.AtomicBoolean.class) {
                        if (com.automatavalkyries.util.UnsafeHelper.getObject(task, offset) instanceof java.util.concurrent.atomic.AtomicBoolean ab) {
                            if (lower.equals("running") || lower.equals("active") || lower.equals("alive") || lower.equals("loop")) {
                                ab.set(false);
                            } else if (lower.equals("stopped") || lower.equals("cancelled") || lower.equals("done")) {
                                ab.set(true);
                            }
                        }
                    }
                }
            }
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Severed background thread/task reference in {}",
                    task.getClass().getName());
        }
        return severed;
    }

    private static boolean isForeignTaskClass(Class<?> type) {
        if (type == null) return false;
        if (isForeign(type)) return true;
        String name = type.getName();
        int lambdaIdx = name.indexOf("$$Lambda$");
        if (lambdaIdx > 0) {
            String hostName = name.substring(0, lambdaIdx);
            return CallerAttribution.isForeign(CallerAttribution.modOfClassName(hostName));
        }
        return false;
    }

    static boolean isTrapTerminationName(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("end") || lower.equals("endgame") || lower.contains("halt")
                || lower.contains("exit") || lower.contains("crash");
    }

}
