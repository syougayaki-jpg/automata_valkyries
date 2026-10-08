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

/**
 * 他 MOD のボスが持つ「独自の生命維持」を外して、その MOD 自身の死亡・消去経路で倒す（超絶抹殺の棒の前処理・後処理）。
 * <ol>
 *   <li>既知の MOD の本来の撃破経路（個別対応）: The Trial Monolith の魂ダメージ（侵略者のモノリス・テッセラクトビースト）</li>
 *   <li>準汎用（未知の MOD 向け。どれも「他 MOD のクラス」だけを対象にし、vanilla・Forge・このMOD のクラスには触れない）:
 *     <ul>
 *       <li>守りの状態: フラグ辞書（{@link FlagDictionary}）で守り・復活の許可と分かる状態を、他 MOD のインターフェース・
 *           既存クラスに足されたフィールド・対象自身のフィールド・Capability・永続データから下ろし、残り復活回数を 0 に、
 *           守りの迂回を有効にする。守りを表す他 MOD の状態異常も外す（待ち時間などのタイマーは触らない）</li>
 *       <li>代理と本体: 対象のフィールドが指すオブジェクトが対象を指し返していれば、そのオブジェクトの remove() / kill() / discard() を呼ぶ
 *           （代理だけ消すと本体が代理を作り直す）</li>
 *       <li>静的な一覧: 対象・本体のクラスと、対象のフィールドが直接指す他 MOD のオブジェクトのクラスの static な
 *           Collection / Map から、対象・本体・対象の UUID だけを取り除く（復活待ちの一覧・管理表）</li>
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

    /** 前処理で見つけた本体（後処理の静的な一覧の掃除でも取り除く） */
    public record Context(List<Object> controllers) {
        static final Context EMPTY = new Context(List.of());
    }

    /** 抹殺の前処理: 生命維持を外し、本体を消し、静的な一覧から取り除く */
    public static Context breakProtection(ServerLevel level, Entity target) {
        FlagDictionary.ensureAgentProtection();
        breakTrialMonolith(target);
        breakSuperSteve(target);
        breakPig2(target);
        breakNekohashizuku(target);
        breakProtectiveStates(target);
        List<Object> controllers = removeControllers(target);
        purgeStaticReferences(target, controllers);
        return new Context(controllers);
    }

    /** 抹殺の後処理: 死亡処理の途中で一覧に戻されていれば、もう一度取り除く */
    public static void afterErase(Entity target, Context context) {
        purgeStaticReferences(target, context.controllers());
    }

    /** 抹殺の最後に呼ぶ: 対応する MOD 自身の撃破経路を走らせる（魂ダメージの最大化で死亡処理と完全消去） */
    public static void finishWithModOwnDeath(Entity target) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) return;
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
                    Method dc = target.getClass().getMethod("disconnect");
                    dc.invoke(target);
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] Disconnected SuperSteve player {}", target.getName().getString());
                } catch (Throwable ignored) {}
            }
            for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("health");
                    f.setAccessible(true);
                    Object op = f.get(target);
                    if (op != null) {
                        Method m = op.getClass().getMethod("operate", Object[].class);
                        Field vrfField = c.getDeclaredField("VRF");
                        vrfField.setAccessible(true);
                        String[] vrf = (String[]) vrfField.get(null);
                        if (vrf != null && vrf.length > 5) {
                            m.invoke(op, new Object[]{ new Object[]{ vrf[5], 0.0F } });
                            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Reset SuperSteve health operator on {}", target.getName().getString());
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    Field be = c.getDeclaredField("bossEvent");
                    be.setAccessible(true);
                    Object bossEvent = be.get(target);
                    if (bossEvent != null) {
                        Method clear = bossEvent.getClass().getMethod("removeAllPlayers");
                        clear.invoke(bossEvent);
                    }
                } catch (Throwable ignored) {}
            }
            try {
                Class<?> ssu = Class.forName("plz.lizi.supersteve.api.SSUtil");
                Method k = ssu.getMethod("killEntity", Entity.class, boolean.class);
                k.invoke(null, target, true);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] SuperSteve protection breaking failed for {}", target, t);
        }
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
            if (key != null && CallerAttribution.isForeign(key.getNamespace()) && FlagDictionary.protectiveOnTarget(key.getPath())) {
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

    /** 代理エンティティの本体（対象を指し返すオブジェクト）を remove() / kill() / discard() で消し、見つけた本体を返す */
    private static List<Object> removeControllers(Entity target) {
        List<Object> controllers = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller == null || controller == target || controller instanceof Entity
                            || !isForeign(controller.getClass()) || !GenericCheatMobNeutralizer.ownsController(controller, target)) continue;
                    if (!seen.add(controller)) continue;
                    controllers.add(controller);
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
                        for (Method m : controller.getClass().getMethods()) {
                            if (m.getParameterCount() == 0 && FlagDictionary.isTerminationMethod(m.getName())) {
                                m.setAccessible(true);
                                m.invoke(controller);
                                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called termination method {}.{}() (controller of {})",
                                        controller.getClass().getSimpleName(), m.getName(), target.getName().getString());
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        // Native removal runs first. Remaining target-owned flags are then reset without invoking close twice.
        for (Object controller : controllers) GenericCheatMobNeutralizer.resetOwnedCore(controller, target);
        return controllers;
    }

    // ---- 準汎用: 静的な一覧 ------------------------------------------------------

    /**
     * 対象・本体のクラスと、対象のフィールドが直接指す他 MOD のオブジェクトのクラス（それぞれの親クラス含む）の static な
     * Collection / Map から、対象・本体・対象の UUID だけを取り除く。MOD 全体は走査しない。
     */
    private static void purgeStaticReferences(Entity target, List<Object> controllers) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        addForeignHierarchy(target.getClass(), classes);
        for (Object controller : controllers) addForeignHierarchy(controller.getClass(), classes);
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
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
        StaticReferencePurger.purge(classes, victims, target.getUUID(), target.getName().getString());
    }
    // ---- 共通 --------------------------------------------------------------

    private static Method findNoArg(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static void addForeignHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) out.add(c);
    }

    /** 他 MOD のクラスか（vanilla・Forge・Java・このMOD・ライブラリは対象外） */
    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }

}
