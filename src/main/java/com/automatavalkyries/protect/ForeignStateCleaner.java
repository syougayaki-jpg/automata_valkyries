package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 保護対象に他 MOD が付けた「状態」を毎 Tick 元に戻す（準汎用。MOD ごとの対策が無い即死 MOD にも効かせる）。
 * <ol>
 *   <li>状態のインターフェース: 他 MOD が Mixin で vanilla のクラスに足したインターフェースのうち、名前が死亡・消去等を表す
 *       getter / setter の組を、boolean は false、数値は 0 に戻す（保護・迂回など名前が守りを表すものは触らない）</li>
 *   <li>状態異常: 他 MOD の「有害」な状態異常を外す（vanilla の状態異常は通常の戦闘の一部なので残す）</li>
 *   <li>永続データ: 他 MOD が永続データ（Forge の persistentData）に足した、名前が死亡・消去等を表す印を消す</li>
 * </ol>
 */
public final class ForeignStateCleaner {

    /** 死亡・消去・呪い等を表す名前 */
    private static final Pattern KILL_STATE = Pattern.compile(
            "(?i).*(dead|death|die|erase|remov|novel|kill|soul|doom|condemn|curse|annihil|obliter).*");
    /** 守り・迂回を表す名前（KILL_STATE に当たっても触らない） */
    private static final Pattern GUARD_STATE = Pattern.compile("(?i).*(protect|immun|bypass|invuln|guard|shield|resist).*");

    private record StatePair(Method getter, Method setter, Class<?> type, String name, String mod) {}

    /** クラス → そのクラスに他 MOD が足した状態の getter / setter の組 */
    private static final ClassValue<List<StatePair>> STATE_PAIRS = new ClassValue<>() {
        @Override
        protected List<StatePair> computeValue(Class<?> type) {
            return findStatePairs(type);
        }
    };

    private ForeignStateCleaner() {}

    /** @return 何か戻した場合 true */
    public static boolean clean(LivingEntity entity) {
        boolean cleaned = resetStateInterfaces(entity);
        cleaned |= removeForeignHarmfulEffects(entity);
        cleaned |= removePersistentKillMarks(entity);
        return cleaned;
    }

    // ---- 1. 状態のインターフェース ------------------------------------------------

    private static boolean resetStateInterfaces(LivingEntity entity) {
        boolean reset = false;
        for (StatePair pair : STATE_PAIRS.get(entity.getClass())) {
            try {
                Object value = pair.getter().invoke(entity);
                Object cleanValue = cleanValue(pair.type(), value);
                if (cleanValue == null) continue;
                pair.setter().invoke(entity, cleanValue);
                reset = true;
                AttackLog.neutralized(entity, pair.mod(), "state:" + pair.name());
            } catch (Throwable ignored) {}
        }
        return reset;
    }

    /** 戻すべき値（今の値が既に無害なら null） */
    private static Object cleanValue(Class<?> type, Object value) {
        if (type == boolean.class) return Boolean.TRUE.equals(value) ? Boolean.FALSE : null;
        if (value instanceof Number number) {
            if (number.doubleValue() == 0.0D) return null;
            if (type == float.class) return 0.0F;
            if (type == double.class) return 0.0D;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
        }
        return null;
    }

    private static List<StatePair> findStatePairs(Class<?> entityClass) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = entityClass; c != null; c = c.getSuperclass()) collectForeignInterfaces(c, interfaces);
        List<StatePair> pairs = new ArrayList<>();
        for (Class<?> api : interfaces) {
            String mod = CallerAttribution.modOf(api);
            for (Method setter : api.getMethods()) {
                if (setter.getParameterCount() != 1) continue;
                Class<?> type = setter.getParameterTypes()[0];
                if (type != boolean.class && type != float.class && type != double.class && type != int.class && type != long.class) continue;
                String name = stripPrefix(setter.getName());
                if (!name.toLowerCase(Locale.ROOT).startsWith("set")) continue;
                String property = name.substring(3);
                if (!KILL_STATE.matcher(property).matches() || GUARD_STATE.matcher(property).matches()) continue;
                Method getter = findGetter(api, property, type);
                if (getter != null) pairs.add(new StatePair(getter, setter, type, property, mod));
            }
        }
        if (!pairs.isEmpty()) {
            AutomataValkyries.LOGGER.info("[eprotect] Watching {} foreign state value(s) on {}", pairs.size(), entityClass.getSimpleName());
        }
        return pairs;
    }

    private static Method findGetter(Class<?> api, String property, Class<?> type) {
        for (Method method : api.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() != type) continue;
            String name = stripPrefix(method.getName());
            for (String prefix : new String[]{"get", "is", "has"}) {
                if (name.equalsIgnoreCase(prefix + property)) return method;
            }
        }
        return null;
    }

    private static void collectForeignInterfaces(Class<?> type, Set<Class<?>> out) {
        for (Class<?> api : type.getInterfaces()) {
            if (CallerAttribution.isForeign(CallerAttribution.modOf(api)) && out.add(api)) collectForeignInterfaces(api, out);
        }
    }

    // ---- 2. 状態異常 ------------------------------------------------------------

    private static boolean removeForeignHarmfulEffects(LivingEntity entity) {
        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (effect.getCategory() != MobEffectCategory.HARMFUL) continue;
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || !CallerAttribution.isForeign(key.getNamespace())) continue;
            toRemove.add(effect);
        }
        for (MobEffect effect : toRemove) {
            entity.removeEffect(effect);
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            AttackLog.neutralized(entity, key != null ? key.getNamespace() : CallerAttribution.UNKNOWN, "effect:" + key);
        }
        return !toRemove.isEmpty();
    }

    // ---- 3. 永続データ ----------------------------------------------------------

    private static boolean removePersistentKillMarks(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        List<String> marks = new ArrayList<>();
        for (String key : data.getAllKeys()) {
            if (key.startsWith(AutomataValkyries.MODID)) continue;
            if (KILL_STATE.matcher(key).matches() && !GUARD_STATE.matcher(key).matches()) marks.add(key);
        }
        for (String key : marks) {
            data.remove(key);
            AttackLog.neutralized(entity, CallerAttribution.UNKNOWN, "persistent:" + key);
        }
        return !marks.isEmpty();
    }

    private static String stripPrefix(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }
}
