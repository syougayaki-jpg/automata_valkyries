package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * エンティティに他 MOD が付けた「状態」を、{@link FlagDictionary} の分類に従って読み書きする（準汎用）。
 * 読む場所:
 * <ul>
 *   <li>{@link Source#INTERFACE}: 他 MOD のインターフェース（Mixin で vanilla のクラスに足されたものを含む）の getter / setter の組</li>
 *   <li>{@link Source#INJECTED_FIELD}: 他 MOD が既存のクラス（vanilla・このMOD を含む）に後から足したフィールド（{@link InjectedMembers}）</li>
 *   <li>{@link Source#OWN_FIELD}: 他 MOD のエンティティのクラスが自分で宣言したフィールド</li>
 *   <li>{@link Source#CAPABILITY}: 他 MOD の Capability（Forge の追加データ）の提供元と、それが直接持つ他 MOD のオブジェクトのフィールド</li>
 *   <li>{@link Source#PERSISTENT}: Forge の永続データ（persistentData）のキー（1 段下の入れ子まで）</li>
 * </ul>
 * 扱う値は boolean と数値だけ。参照・一覧は触らない（静的な一覧は {@link com.automatavalkyries.util.StaticReferencePurger}）。
 * このMOD が足したもの・vanilla 本来のフィールドには触れない。
 */
public final class StateScanner {

    public enum Source { INTERFACE, INJECTED_FIELD, OWN_FIELD, CAPABILITY, PERSISTENT }

    /** 何をどう書き換えるか */
    public interface Policy {
        /** 名前だけで見て、扱う可能性があるか（無いものは値を読まない） */
        boolean relevant(String name);

        /**
         * @param current 今の値（getter が無い setter なら null）
         * @return 書き込む値（触らないなら null）
         */
        @Nullable
        Object decide(String name, Class<?> type, @Nullable Object current);

        /** 永続データのキーを消すか */
        boolean removeKey(String key);

        /** getter の無い setter も呼ぶか（今の値が分からないので、毎 Tick 回す側では false にする） */
        default boolean callBlindSetters() {
            return false;
        }
    }

    /** 書き換えた 1 件 */
    public record Change(Source source, String holder, String name, String mod) {
        @Override
        public String toString() {
            return source.name().toLowerCase(java.util.Locale.ROOT) + ":" + holder + "." + name;
        }
    }

    /** 保護対象: 死亡・停止・HP 改ざんの印と守りの迂回を戻す（守りは触らない） */
    public static final Policy CLEAN_PROTECTED = new Policy() {
        @Override
        public boolean relevant(String name) {
            return FlagDictionary.harmfulOnProtected(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            return FlagDictionary.harmfulOnProtected(name) ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return FlagDictionary.harmfulOnProtected(key);
        }
    };

    /** 抹殺の対象: 守り・復活の許可を下ろし、残り復活回数を 0 に、守りの迂回を有効にする（待ち時間には触れない） */
    public static final Policy BREAK_TARGET = new Policy() {
        @Override
        public boolean relevant(String name) {
            return FlagDictionary.is(name, FlagDictionary.Category.BYPASS) || FlagDictionary.protectiveOnTarget(name)
                    || FlagDictionary.livesCounter(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            if (type == boolean.class) {
                if (FlagDictionary.is(name, FlagDictionary.Category.BYPASS)) return Boolean.TRUE.equals(current) ? null : Boolean.TRUE;
                if (FlagDictionary.protectiveOnTarget(name)) return cleared(type, current);
                return null;
            }
            return FlagDictionary.livesCounter(name) ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return FlagDictionary.protectiveOnTarget(key);
        }

        @Override
        public boolean callBlindSetters() {
            return true;
        }
    };

    private static final Set<Class<?>> VALUE_TYPES = Set.of(boolean.class, int.class, long.class, float.class, double.class,
            short.class, byte.class);

    private record Accessor(@Nullable Method getter, Method setter, Class<?> type, String name, String mod) {}

    private StateScanner() {}

    /** 書き換えて、書き換えたものを返す */
    public static List<Change> apply(Entity entity, Policy policy, EnumSet<Source> sources) {
        List<Change> changes = new ArrayList<>();
        Class<?> type = entity.getClass();
        if (sources.contains(Source.INTERFACE)) applyInterfaces(entity, policy, changes);
        if (sources.contains(Source.INJECTED_FIELD)) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                    if (CallerAttribution.SELF.equals(injected.mod())) continue; // このMOD 自身の Mixin のフィールド
                    applyField(entity, injected.field(), injected.mod(), Source.INJECTED_FIELD, policy, changes);
                }
            }
        }
        if (sources.contains(Source.OWN_FIELD)) {
            for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
                String mod = CallerAttribution.modOf(c);
                for (Field field : c.getDeclaredFields()) applyField(entity, field, mod, Source.OWN_FIELD, policy, changes);
            }
        }
        if (sources.contains(Source.CAPABILITY)) applyCapabilities(entity, policy, changes);
        if (sources.contains(Source.PERSISTENT)) applyPersistent(entity, policy, changes);
        return changes;
    }

    // ---- インターフェース --------------------------------------------------------

    private static final ClassValue<List<Accessor>> ACCESSORS = new ClassValue<>() {
        @Override
        protected List<Accessor> computeValue(Class<?> type) {
            return findAccessors(type);
        }
    };

    private static void applyInterfaces(Object holder, Policy policy, List<Change> changes) {
        for (Accessor accessor : ACCESSORS.get(holder.getClass())) {
            if (!policy.relevant(accessor.name())) continue;
            try {
                Object current = null;
                if (accessor.getter() != null) {
                    current = accessor.getter().invoke(holder);
                } else if (!policy.callBlindSetters()) {
                    continue;
                }
                Object value = policy.decide(accessor.name(), accessor.type(), current);
                if (value == null) continue;
                accessor.setter().invoke(holder, value);
                changes.add(new Change(Source.INTERFACE, accessor.setter().getDeclaringClass().getSimpleName(), accessor.name(), accessor.mod()));
            } catch (Throwable ignored) {}
        }
    }

    private static List<Accessor> findAccessors(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) collectForeignInterfaces(c, interfaces);
        List<Accessor> result = new ArrayList<>();
        for (Class<?> api : interfaces) {
            String mod = CallerAttribution.modOf(api);
            for (Method setter : api.getMethods()) {
                if (setter.getParameterCount() != 1 || !VALUE_TYPES.contains(setter.getParameterTypes()[0])) continue;
                String bare = bareName(setter.getName());
                if (bare.length() <= 3 || !bare.toLowerCase(java.util.Locale.ROOT).startsWith("set")) continue;
                String property = bare.substring(3);
                Class<?> valueType = setter.getParameterTypes()[0];
                result.add(new Accessor(findGetter(api, property, valueType), setter, valueType, property, mod));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    private static Method findGetter(Class<?> api, String property, Class<?> type) {
        for (Method method : api.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() != type) continue;
            String name = bareName(method.getName());
            for (String prefix : new String[]{"get", "is", "has", ""}) {
                if (name.equalsIgnoreCase(prefix + property)) return method;
            }
        }
        return null;
    }

    private static void collectForeignInterfaces(Class<?> type, Set<Class<?>> out) {
        for (Class<?> api : type.getInterfaces()) {
            if (isForeign(api) && out.add(api)) collectForeignInterfaces(api, out);
        }
    }

    // ---- フィールド ------------------------------------------------------------

    private static void applyField(Object holder, Field field, String mod, Source source, Policy policy, List<Change> changes) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || !VALUE_TYPES.contains(field.getType())) return;
        if (!policy.relevant(field.getName())) return;
        try {
            field.setAccessible(true);
            Object current = field.get(holder);
            Object value = policy.decide(field.getName(), field.getType(), current);
            if (value == null) return;
            field.set(holder, value);
            changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
        } catch (Throwable ignored) {}
    }

    /** 他 MOD のオブジェクト（エンティティ以外）の、自分のクラス階層で宣言されたフィールド */
    private static void applyObjectFields(Object holder, Source source, Policy policy, List<Change> changes) {
        for (Class<?> c = holder.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            String mod = CallerAttribution.modOf(c);
            for (Field field : c.getDeclaredFields()) applyField(holder, field, mod, source, policy, changes);
        }
    }

    // ---- Capability ------------------------------------------------------------

    @Nullable
    private static final Field CAPABILITIES = findFieldByType(CapabilityProvider.class, CapabilityDispatcher.class);
    @Nullable
    private static final Field PROVIDERS = findFieldByType(CapabilityDispatcher.class, ICapabilityProvider[].class);

    private static void applyCapabilities(Entity entity, Policy policy, List<Change> changes) {
        if (CAPABILITIES == null || PROVIDERS == null) return;
        try {
            Object dispatcher = CAPABILITIES.get(entity);
            if (dispatcher == null) return;
            Object[] providers = (Object[]) PROVIDERS.get(dispatcher);
            if (providers == null) return;
            for (Object provider : providers) {
                if (provider == null || !isForeign(provider.getClass())) continue;
                applyObjectFields(provider, Source.CAPABILITY, policy, changes);
                applyInterfaces(provider, policy, changes);
                // 提供元が直接持つ他 MOD のオブジェクト（中身のデータ）も 1 段だけ見る
                for (Class<?> c = provider.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        field.setAccessible(true);
                        Object data = field.get(provider);
                        if (data == null || data == provider || data instanceof Entity || data instanceof Collection<?>
                                || data instanceof Map<?, ?> || !isForeign(data.getClass())) continue;
                        applyObjectFields(data, Source.CAPABILITY, policy, changes);
                        applyInterfaces(data, policy, changes);
                    }
                }
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Capability scan failed on {}", entity, t);
        }
    }

    @Nullable
    private static Field findFieldByType(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---- 永続データ --------------------------------------------------------------

    private static void applyPersistent(Entity entity, Policy policy, List<Change> changes) {
        applyCompound(entity.getPersistentData(), "persistentData", policy, changes, 0);
    }

    private static void applyCompound(CompoundTag tag, String path, Policy policy, List<Change> changes, int depth) {
        List<String> remove = new ArrayList<>();
        for (String key : tag.getAllKeys()) {
            if (key.startsWith(AutomataValkyries.MODID)) continue;
            if (policy.removeKey(key)) {
                remove.add(key);
            } else if (depth == 0 && tag.getTagType(key) == Tag.TAG_COMPOUND) {
                applyCompound(tag.getCompound(key), path + "." + key, policy, changes, depth + 1);
            }
        }
        for (String key : remove) {
            tag.remove(key);
            changes.add(new Change(Source.PERSISTENT, path, key, modOfKey(key)));
        }
    }

    private static String modOfKey(String key) {
        int colon = key.indexOf(':');
        return colon > 0 ? key.substring(0, colon) : CallerAttribution.UNKNOWN;
    }

    // ---- 共通 --------------------------------------------------------------

    /** 害の無い値（boolean は false、数値は 0）。既にそうなら null */
    @Nullable
    static Object cleared(Class<?> type, @Nullable Object current) {
        if (type == boolean.class) return Boolean.FALSE.equals(current) ? null : Boolean.FALSE;
        if (current instanceof Number number && number.doubleValue() == 0.0D) return null;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return null;
    }

    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }

    /** Mixin の接頭辞付きの名前（modid$setX）から本来の名前を取り出す */
    private static String bareName(String name) {
        int dollar = name.lastIndexOf('$');
        return dollar >= 0 ? name.substring(dollar + 1) : name;
    }
}
