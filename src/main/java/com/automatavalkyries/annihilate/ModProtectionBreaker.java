package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.util.StaticReferencePurger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 他 MOD のボスが持つ「独自の生命維持」を外して、その MOD 自身の死亡・消去経路で倒す（超絶抹殺の棒の前処理・後処理）。
 * <ol>
 *   <li>既知の MOD の本来の撃破経路（個別対応）: The Trial Monolith の魂ダメージ（侵略者のモノリス・テッセラクトビースト）</li>
 *   <li>準汎用（未知の MOD 向け。どれも「他 MOD のクラス」だけを対象にし、vanilla・Forge・このMOD のクラスには触れない）:
 *     <ul>
 *       <li>保護の切り替え: 対象が実装する他 MOD のインターフェースの set…Protect / Barrier / Invulnerable / Immune / God / Muteki 等を無効、
 *           set…Bypass… を有効にする</li>
 *       <li>保護・復活のフラグ: 対象自身のフィールド（final 以外）のうち、保護・復活の boolean を false、残り復活回数を 0 にする
 *           （復活までの待ち時間などのタイマーは、0 にすると早まりうるので触らない）</li>
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

    private static final Pattern DISABLE_SETTER = Pattern.compile(
            "(?i)set\\w*(protect|barrier|invuln|immun|invinc|godmode|god|muteki|shield|undying|immortal|dimensionalcore)\\w*");
    private static final Pattern ENABLE_SETTER = Pattern.compile("(?i)set\\w*bypass\\w*");
    /** false にする boolean フィールド（保護・復活の許可） */
    private static final Pattern DISABLE_FLAG = Pattern.compile(
            "(?i).*(invuln|immortal|invinc|protect|undying|godmode|muteki|immune|barrier|canrevive|canrespawn|revivable|respawnable|resurrect).*");
    /** 0 にする数値フィールド（残り復活回数。待ち時間・間隔は含めない） */
    private static final Pattern ZERO_COUNT = Pattern.compile(
            "(?i)(lives|lifecount|livesleft|remaininglives|extralives)|.*(revive|respawn|resurrect|rebirth)\\w*(count|left|remaining|times|charges)");
    private static final String[] CONTROLLER_KILL_METHODS = {"remove", "kill", "discard"};

    private static final String TRIAL_MONOLITH = "io.github.kosianodangoo.trialmonolith.";

    private ModProtectionBreaker() {}

    /** 前処理で見つけた本体（後処理の静的な一覧の掃除でも取り除く） */
    public record Context(List<Object> controllers) {
        static final Context EMPTY = new Context(List.of());
    }

    /** 抹殺の前処理: 生命維持を外し、本体を消し、静的な一覧から取り除く */
    public static Context breakProtection(ServerLevel level, Entity target) {
        breakTrialMonolith(target);
        disableProtectionSwitches(target);
        clearFlagFields(target);
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
            if (entity == target || entity instanceof Player || entity instanceof IEprotected) continue;
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

    // ---- 準汎用: 保護の切り替え・フラグ ---------------------------------------------

    /** 他 MOD のインターフェースにある保護の切り替えを無効にする（迂回の切り替えは有効にする） */
    private static void disableProtectionSwitches(Entity target) {
        for (Class<?> api : foreignInterfaces(target.getClass())) {
            for (Method method : api.getMethods()) {
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != boolean.class) continue;
                String name = stripPrefix(method.getName());
                Boolean value = ENABLE_SETTER.matcher(name).matches() ? Boolean.TRUE
                        : DISABLE_SETTER.matcher(name).matches() ? Boolean.FALSE : null;
                if (value == null) continue;
                try {
                    method.invoke(target, value);
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] {}.{}({}) on {}", api.getSimpleName(), method.getName(), value,
                            target.getName().getString());
                } catch (Throwable ignored) {}
            }
        }
    }

    /** 対象自身（他 MOD のクラスで宣言された final 以外のフィールド）の保護・復活のフラグを下ろし、残り復活回数を 0 にする */
    private static void clearFlagFields(Entity target) {
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;
                String name = stripPrefix(field.getName());
                try {
                    Class<?> fieldType = field.getType();
                    if (fieldType == boolean.class && DISABLE_FLAG.matcher(name).matches()) {
                        field.setAccessible(true);
                        if (field.getBoolean(target)) {
                            field.setBoolean(target, false);
                            AutomataValkyries.LOGGER.info("[AnnihilationAgent] {}.{} = false on {}", type.getSimpleName(), field.getName(),
                                    target.getName().getString());
                        }
                    } else if ((fieldType == int.class || fieldType == long.class || fieldType == short.class || fieldType == byte.class)
                            && ZERO_COUNT.matcher(name).matches()) {
                        field.setAccessible(true);
                        if (((Number) field.get(target)).longValue() != 0) {
                            if (fieldType == int.class) field.setInt(target, 0);
                            else if (fieldType == long.class) field.setLong(target, 0L);
                            else if (fieldType == short.class) field.setShort(target, (short) 0);
                            else field.setByte(target, (byte) 0);
                            AutomataValkyries.LOGGER.info("[AnnihilationAgent] {}.{} = 0 on {}", type.getSimpleName(), field.getName(),
                                    target.getName().getString());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    // ---- 準汎用: 代理と本体 ------------------------------------------------------

    /** 代理エンティティの本体（対象を指し返すオブジェクト）を remove() / kill() / discard() で消し、見つけた本体を返す */
    private static List<Object> removeControllers(Entity target) {
        List<Object> controllers = new ArrayList<>();
        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller == null || controller == target || controller instanceof Entity
                            || !isForeign(controller.getClass()) || !pointsBackTo(controller, target)) continue;
                    controllers.add(controller);
                    for (String name : CONTROLLER_KILL_METHODS) {
                        Method kill = findNoArg(controller.getClass(), name);
                        if (kill == null) continue;
                        kill.setAccessible(true);
                        kill.invoke(controller);
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called {}.{}() (controller of {})",
                                controller.getClass().getSimpleName(), name, target.getName().getString());
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return controllers;
    }

    /** そのオブジェクトのフィールドのどれかが対象そのものを指しているか（本体と代理の関係か） */
    private static boolean pointsBackTo(Object controller, Entity target) {
        for (Class<?> type = controller.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    if (field.get(controller) == target) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
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

    private static Set<Class<?>> foreignInterfaces(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) collectInterfaces(c, result);
        return result;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> out) {
        for (Class<?> api : type.getInterfaces()) {
            if (isForeign(api) && out.add(api)) collectInterfaces(api, out);
        }
    }

    private static void addForeignHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) out.add(c);
    }

    /** 他 MOD のクラスか（vanilla・Forge・Java・このMOD・ライブラリは対象外） */
    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }

    /** Mixin の接頭辞付きの名前（the_trial_monolith$setSoulProtected 等）から本来の名前を取り出す */
    private static String stripPrefix(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }
}
