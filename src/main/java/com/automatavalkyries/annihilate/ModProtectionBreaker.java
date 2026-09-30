package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 他 MOD のボスが持つ「独自の生命維持」を外して、その MOD 自身の死亡・消去経路で倒す（超絶抹殺の棒の前処理）。
 * <ol>
 *   <li>既知の MOD の本来の撃破経路（個別対応）: The Trial Monolith の魂ダメージ（侵略者のモノリス・テッセラクトビースト）</li>
 *   <li>準汎用（未知の MOD 向け）:
 *     <ul>
 *       <li>対象のクラスが実装する他 MOD のインターフェースから、保護の切り替え（set…Protect / Barrier / Invulnerable / Immune / God / Muteki 等、
 *           boolean 1 引数）を探して無効にし、set…Bypass… は有効にする</li>
 *       <li>本体が別オブジェクト（コントローラー）にあり、エンティティは代理（プロキシ）にすぎない構造: 対象のフィールドが指すオブジェクトが
 *           対象を指し返していれば、そのオブジェクトの remove() / kill() / discard() を呼ぶ（代理だけ消すと本体が代理を作り直す）</li>
 *     </ul>
 *   </li>
 * </ol>
 * どれも失敗しても抹殺の本処理（バニラの即死・追跡偽装）は続けるので、例外はすべて握りつぶす。
 */
public final class ModProtectionBreaker {

    private static final Pattern DISABLE_SETTER = Pattern.compile(
            "(?i)set\\w*(protect|barrier|invuln|immun|invinc|godmode|god|muteki|shield|undying|immortal|dimensionalcore)\\w*");
    private static final Pattern ENABLE_SETTER = Pattern.compile("(?i)set\\w*bypass\\w*");
    private static final String[] CONTROLLER_KILL_METHODS = {"remove", "kill", "discard"};

    private static final String TRIAL_MONOLITH = "io.github.kosianodangoo.trialmonolith.";

    private ModProtectionBreaker() {}

    /** @return 何らかの生命維持を外した場合 true */
    public static boolean breakProtection(ServerLevel level, Entity target) {
        boolean acted = breakTrialMonolith(target);
        acted |= disableProtectionSwitches(target);
        acted |= removeControllers(target);
        return acted;
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

    // ---- 個別対応 ------------------------------------------------------------

    /**
     * The Trial Monolith: HP は常に無限大で、死ぬのは魂ダメージ（EntityHelper.setSoulDamageForce → onSoulDeath / onSoulRemove）だけ。
     * 魂の保護・高次元障壁・次元コアを外し、保護の迂回を有効にする。テッセラクトビーストは代理エンティティで、本体の
     * コントローラーを remove() しないと代理を作り直す。
     */
    private static boolean breakTrialMonolith(Entity target) {
        if (!target.getClass().getName().startsWith(TRIAL_MONOLITH)) return false;
        try {
            Class<?> helper = Class.forName(TRIAL_MONOLITH + "common.helper.EntityHelper");
            helper.getMethod("setSoulProtected", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setHighDimensionalBarrier", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setDimensionalCore", Entity.class, boolean.class).invoke(null, target, false);
            helper.getMethod("setBypassProtection", Entity.class, boolean.class).invoke(null, target, true);
            AutomataValkyries.LOGGER.info("[AnnihilationAgent] Disabled Trial Monolith soul protection on {}", target.getName().getString());
            return true;
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[AnnihilationAgent] Trial Monolith protection breaking failed for {}", target, t);
            return false;
        }
    }

    // ---- 準汎用 --------------------------------------------------------------

    /** 他 MOD のインターフェースにある保護の切り替えを無効にする（迂回の切り替えは有効にする） */
    private static boolean disableProtectionSwitches(Entity target) {
        boolean acted = false;
        for (Class<?> api : foreignInterfaces(target.getClass())) {
            for (Method method : api.getMethods()) {
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != boolean.class) continue;
                String name = stripPrefix(method.getName());
                Boolean value = ENABLE_SETTER.matcher(name).matches() ? Boolean.TRUE
                        : DISABLE_SETTER.matcher(name).matches() ? Boolean.FALSE : null;
                if (value == null) continue;
                try {
                    method.invoke(target, value);
                    acted = true;
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] {}.{}({}) on {}", api.getSimpleName(), method.getName(), value,
                            target.getName().getString());
                } catch (Throwable ignored) {}
            }
        }
        return acted;
    }

    /** 代理エンティティの本体（対象を指し返すオブジェクト）を remove() / kill() / discard() で消す */
    private static boolean removeControllers(Entity target) {
        boolean acted = false;
        for (Class<?> type = target.getClass(); type != null && !isPlatform(type.getName()); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller == null || controller == target || controller instanceof Entity
                            || isPlatform(controller.getClass().getName()) || !pointsBackTo(controller, target)) continue;
                    for (String name : CONTROLLER_KILL_METHODS) {
                        Method kill = findNoArg(controller.getClass(), name);
                        if (kill == null) continue;
                        kill.setAccessible(true);
                        kill.invoke(controller);
                        acted = true;
                        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Called {}.{}() (controller of {})",
                                controller.getClass().getSimpleName(), name, target.getName().getString());
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return acted;
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
        for (Class<?> c = type; c != null && !isPlatform(c.getName()); c = c.getSuperclass()) {
            collectInterfaces(c, result);
        }
        return result;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> out) {
        for (Class<?> api : type.getInterfaces()) {
            if (!isPlatform(api.getName()) && out.add(api)) collectInterfaces(api, out);
        }
    }

    /** vanilla / Forge / Java / このMOD のクラス（生命維持の探索対象から外す） */
    private static boolean isPlatform(String className) {
        return className.startsWith("net.minecraft.") || className.startsWith("net.minecraftforge.")
                || className.startsWith("com.mojang.") || className.startsWith("java.") || className.startsWith("jdk.")
                || className.startsWith("com.automatavalkyries.");
    }

    /** Mixin の接頭辞付きメソッド名（the_trial_monolith$setSoulProtected 等）から本来の名前を取り出す */
    private static String stripPrefix(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }
}
