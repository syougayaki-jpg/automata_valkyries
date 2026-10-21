package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
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
import java.util.UUID;

/**
 * エンティティに他 MOD が付けた「状態」を、{@link FlagDictionary} の分類に従って読み書きする（準汎用）。
 * 読む場所:
 * <ul>
 *   <li>{@link Source#INTERFACE}: 他 MOD のインターフェース（Mixin で vanilla のクラスに足されたものを含む）の getter / setter の組</li>
 *   <li>{@link Source#INJECTED_FIELD}: 他 MOD が既存のクラス（vanilla・このMOD を含む）に後から足したフィールド（{@link InjectedMembers}）</li>
 *   <li>{@link Source#OWN_FIELD}: 他 MOD のエンティティのクラスが自分で宣言したフィールド</li>
 *   <li>{@link Source#CAPABILITY}: 他 MOD の Capability（Forge の追加データ）の提供元と、それが直接持つ他 MOD のオブジェクトのフィールド</li>
 *   <li>{@link Source#PERSISTENT}: Forge の永続データ（persistentData）のキー（1 段下の入れ子まで）</li>
 *   <li>{@link Source#SYNCHED_DATA}: 他 MOD の同期データ（EntityDataAccessor。既存クラスに足されたものと、他 MOD のエンティティの
 *       クラスが自分で宣言したもの）の真偽・数値（IMDS の神無敵・Trial Monolith の魂ダメージ・Hyperlink のノベル化はここにある）</li>
 * </ul>
 * 扱う値は boolean と数値だけ。参照・一覧は触らない（静的な一覧は {@link com.automatavalkyries.util.StaticReferencePurger}）。
 * このMOD が足したもの・vanilla 本来のフィールドには触れない。
 */
public final class StateScanner {

    public enum Source { INTERFACE, INJECTED_FIELD, OWN_FIELD, CAPABILITY, PERSISTENT, SYNCHED_DATA }

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

    /** 抹殺の対象: 守り・復活の許可を下ろし、残り復活回数を 0 に、守りの迂回および死亡・消滅状態を有効にする（待ち時間には触れない） */
    public static final Policy BREAK_TARGET = new Policy() {
        @Override
        public boolean relevant(String name) {
            return FlagDictionary.is(name, FlagDictionary.Category.BYPASS) || isExtendedProtectiveOnTarget(name)
                    || com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name) || isExtendedDeathStateField(name)
                    || isCustomHealthOrValueName(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            if (type == boolean.class) {
                if (FlagDictionary.is(name, FlagDictionary.Category.BYPASS)) return Boolean.TRUE.equals(current) ? null : Boolean.TRUE;
                if (isExtendedDeathStateField(name)) return Boolean.TRUE.equals(current) ? null : Boolean.TRUE;
                if (isExtendedProtectiveOnTarget(name)) return cleared(type, current);
                return null;
            }
            if (type == String.class) {
                if ((com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name) || isCustomHealthOrValueName(name))
                        && (current == null || isPositiveNumericString(current))) {
                    return "-1.0";
                }
                return null;
            }
            return (com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name) || isCustomHealthOrValueName(name)) ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return isExtendedProtectiveOnTarget(key);
        }

        @Override
        public boolean callBlindSetters() {
            return true;
        }
    };

    /**
     * 抹殺対象が持つ内部コンポーネント用（{@link #BREAK_TARGET} より狭い）: 守り・復活の許可を下ろし、残り復活回数と独自 HP を 0 にするだけ。
     * 死亡・消滅状態の有効化、見えない setter、一覧の消去はしない（共有されている入れ物を壊さない）
     */
    public static final Policy BREAK_COMPONENT = new Policy() {
        @Override
        public boolean relevant(String name) {
            return isExtendedProtectiveOnTarget(name) || com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name)
                    || isCustomHealthOrValueName(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            if (type == boolean.class) return isExtendedProtectiveOnTarget(name) ? cleared(type, current) : null;
            boolean value = com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name) || isCustomHealthOrValueName(name);
            if (type == String.class) return value && isPositiveNumericString(current) ? "-1.0" : null;
            return value ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return isExtendedProtectiveOnTarget(key);
        }
    };

    /** プレイヤー用: 守り・無敵・復活の許可を下ろし、残り復活回数を 0 にする（死亡・消滅状態や removalReason、見えない setter には触れない） */
    public static final Policy BREAK_PLAYER_DEFENSES = new Policy() {
        @Override
        public boolean relevant(String name) {
            return com.automatavalkyries.annihilate.FlagKillPredicates.protectiveOnTarget(name)
                    || com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            if (type == boolean.class) {
                if (com.automatavalkyries.annihilate.FlagKillPredicates.protectiveOnTarget(name)) return cleared(type, current);
                return null;
            }
            return com.automatavalkyries.annihilate.FlagKillPredicates.livesCounter(name) ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return com.automatavalkyries.annihilate.FlagKillPredicates.protectiveOnTarget(key);
        }

        @Override
        public boolean callBlindSetters() {
            return false;
        }
    };

    /**
     * 死んだプレイヤー（リスポーンで消える古い個体）: 守り・復活の許可・停止・HP の固定・リスポーンの妨害を下ろす（真偽だけ）。
     * 他 MOD がこれを見て古い個体を守り続け・復活させ続けると、古い個体が消えずに残り、リスポーンの要求も無視される
     */
    public static final Policy RELEASE_DEAD_PLAYER = new Policy() {
        @Override
        public boolean relevant(String name) {
            return FlagDictionary.heldOnDeadPlayer(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            return type == boolean.class && FlagDictionary.heldOnDeadPlayer(name) ? cleared(type, current) : null;
        }

        /** 永続データは消さない（古い個体の永続データのうち PlayerPersisted は新しい個体へ引き継がれ、正規の設定まで消してしまう）。
         *  リスポーンの妨害・停止の印だけは消す */
        @Override
        public boolean removeKey(String key) {
            return FlagDictionary.is(key, FlagDictionary.Category.RESPAWN_BLOCK)
                    || (FlagDictionary.is(key, FlagDictionary.Category.CONTROL) && !FlagDictionary.recordsData(key));
        }
    };

    /**
     * リスポーンした新しいプレイヤー: 死亡・停止・HP 改ざん・追放・迂回の印とリスポーンの妨害を戻す（真偽だけ。守りは触らない）。
     * UUID で持つ印や引き継がれた永続データ（PlayerPersisted）で、新しい個体まで死亡扱い・停止のままになるのを防ぐ
     */
    public static final Policy CLEAN_RESPAWNED_PLAYER = new Policy() {
        @Override
        public boolean relevant(String name) {
            return FlagDictionary.hazardOnRespawned(name);
        }

        @Override
        public Object decide(String name, Class<?> type, @Nullable Object current) {
            return type == boolean.class && FlagDictionary.hazardOnRespawned(name) ? cleared(type, current) : null;
        }

        @Override
        public boolean removeKey(String key) {
            return FlagDictionary.hazardOnRespawned(key);
        }
    };

    private static final Set<Class<?>> VALUE_TYPES = Set.of(boolean.class, int.class, long.class, float.class, double.class,
            short.class, byte.class, String.class, java.util.UUID.class);

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
                    applyInjectedObject(entity, injected, policy, changes);
                }
            }
        }
        if (sources.contains(Source.OWN_FIELD)) {
            for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
                String mod = CallerAttribution.modOf(c);
                for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) applyField(entity, field, mod, Source.OWN_FIELD, policy, changes);
            }
        }
        if (sources.contains(Source.OWN_FIELD) && policy == BREAK_TARGET && isForeign(type)) {
            applyOwnedComponents(entity, changes);
            applyPairedHealthFields(entity, changes);
        }
        if (sources.contains(Source.CAPABILITY)) applyCapabilities(entity, policy, changes);
        if (sources.contains(Source.PERSISTENT)) applyPersistent(entity, policy, changes);
        if (sources.contains(Source.SYNCHED_DATA)) applySynchedData(entity, policy, changes);
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
                if (value == null && accessor.type() == java.util.UUID.class && holder instanceof Entity e) {
                    String setterName = accessor.setter().getName().toLowerCase(java.util.Locale.ROOT);
                    if (policy == BREAK_TARGET && com.automatavalkyries.annihilate.FlagKillPredicates.isDeathStateField(accessor.name())) {
                        if (setterName.startsWith("mark")
                                && (!(e instanceof net.minecraft.world.entity.player.Player) || isForeign(e.getClass()))) {
                            value = e.getUUID();
                        }
                    } else if (policy == CLEAN_PROTECTED && com.automatavalkyries.annihilate.FlagKillPredicates.isDeathStateField(accessor.name())) {
                        if (setterName.startsWith("unmark")) {
                            value = e.getUUID();
                        }
                    }
                }
                if (value == null) continue;
                final Object toWrite = value;
                java.util.stream.Stream.of(holder).forEach(target -> {
                    try {
                        accessor.setter().invoke(target, toWrite);
                    } catch (Throwable ignored) {}
                });
                changes.add(new Change(Source.INTERFACE, accessor.setter().getDeclaringClass().getSimpleName(), accessor.name(), accessor.mod()));
            } catch (Throwable ignored) {}
        }
    }

    private static List<Accessor> findAccessors(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) collectForeignInterfaces(c, interfaces);
        List<Accessor> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Class<?> api : interfaces) {
            String mod = CallerAttribution.modOf(api);
            collectAccessorsFromType(api, api.getMethods(), mod, false, seen, result);
        }
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            String mod = CallerAttribution.modOf(c);
            collectAccessorsFromType(c, com.automatavalkyries.util.UnsafeHelper.getRealDeclaredMethods(c), mod, true, seen, result);
        }
        return List.copyOf(result);
    }

    private static void collectAccessorsFromType(Class<?> owner, Method[] methods, String mod, boolean skipVanillaOverrides,
                                                 Set<String> seen, List<Accessor> result) {
        for (Method setter : methods) {
            if (Modifier.isStatic(setter.getModifiers()) || setter.isSynthetic()
                    || setter.getParameterCount() != 1 || !VALUE_TYPES.contains(setter.getParameterTypes()[0])) continue;
            if (skipVanillaOverrides && isVanillaEntityMethod(setter.getName(), setter.getParameterTypes())) continue;
            String bare = bareName(setter.getName());
            String lower = bare.toLowerCase(java.util.Locale.ROOT);
            String property = null;
            if (lower.startsWith("set") && bare.length() > 3) {
                property = bare.substring(3);
            } else if (lower.startsWith("mark") && bare.length() > 4) {
                property = bare.substring(4);
            } else if (lower.startsWith("unmark") && bare.length() > 6) {
                property = bare.substring(6);
            }
            if (property == null || property.isEmpty()) continue;
            Class<?> valueType = setter.getParameterTypes()[0];
            String sig = bare + "(" + valueType.getName() + ")";
            if (!seen.add(sig)) continue;
            try {
                setter.setAccessible(true);
            } catch (Throwable ignored) {}
            Method getter = findGetter(owner, methods, property, valueType);
            if (getter != null) {
                try {
                    getter.setAccessible(true);
                } catch (Throwable ignored) {}
            }
            result.add(new Accessor(getter, setter, valueType, property, mod));
        }
    }

    private static boolean isVanillaEntityMethod(String name, Class<?>[] params) {
        for (Class<?> vanilla : new Class<?>[]{Entity.class, net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.entity.Mob.class}) {
            try {
                vanilla.getDeclaredMethod(name, params);
                return true;
            } catch (NoSuchMethodException ignored) {}
        }
        return false;
    }

    @Nullable
    private static Method findGetter(Class<?> api, Method[] methods, String property, Class<?> type) {
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() != type) continue;
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
        if (Modifier.isStatic(modifiers)) {
            if (Collection.class.isAssignableFrom(field.getType()) && holder instanceof Entity entity) {
                if (policy.relevant(field.getName())) {
                    try {
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Collection<Object> col = (Collection<Object>) field.get(null);
                        if (col != null) {
                            UUID uuid = entity.getUUID();
                            if (policy == BREAK_TARGET) {
                                if (com.automatavalkyries.annihilate.FlagKillPredicates.isDeathStateField(field.getName())) {
                                    if (!(entity instanceof net.minecraft.world.entity.player.Player) || isForeign(entity.getClass())) {
                                        col.add(uuid);
                                        changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
                                    }
                                } else if (com.automatavalkyries.annihilate.FlagKillPredicates.protectiveOnTarget(field.getName())) {
                                    if (col.remove(uuid) || col.remove(entity)) {
                                        changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
                                    }
                                }
                            } else if (policy == CLEAN_PROTECTED) {
                                if (com.automatavalkyries.annihilate.FlagKillPredicates.isDeathStateField(field.getName())) {
                                    if (col.remove(uuid) || col.remove(entity)) {
                                        changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            return;
        }

        // コレクション・マップのクリア対応（finalであっても中身をクリア可能。共有されうるコンポーネントでは消さない）
        if (policy == BREAK_COMPONENT && (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType()))) return;
        if (Collection.class.isAssignableFrom(field.getType())) {
            if (!policy.relevant(field.getName())) return;
            try {
                field.setAccessible(true);
                Collection<?> col = (Collection<?>) field.get(holder);
                if (col != null && !col.isEmpty()) {
                    col.clear();
                    changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
                }
            } catch (Throwable ignored) {}
            return;
        }
        if (Map.class.isAssignableFrom(field.getType())) {
            if (!policy.relevant(field.getName())) return;
            try {
                field.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) field.get(holder);
                if (map != null && !map.isEmpty()) {
                    map.clear();
                    changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
                }
            } catch (Throwable ignored) {}
            return;
        }

        if (CompoundTag.class.isAssignableFrom(field.getType())) {
            if (policy != BREAK_TARGET) return;
            try {
                field.setAccessible(true);
                if (field.get(holder) instanceof CompoundTag tag && !tag.isEmpty()) {
                    applyCompound(tag, field.getDeclaringClass().getSimpleName() + "." + field.getName(), policy, changes, 0);
                }
            } catch (Throwable ignored) {}
            return;
        }

        if (field.getType() == java.util.Optional.class) {
            applyOptionalField(holder, field, mod, source, policy, changes);
            return;
        }

        if (applyWrapperOrArrayField(holder, field, mod, source, policy, changes)) {
            return;
        }

        boolean isFinal = Modifier.isFinal(modifiers);
        boolean canWriteFinal = isFinal && (policy == BREAK_TARGET || policy == BREAK_COMPONENT)
                && (field.getType().isPrimitive() || field.getType() == String.class);
        if ((isFinal && !canWriteFinal) || (!VALUE_TYPES.contains(field.getType()) && !Entity.class.isAssignableFrom(field.getType()))) return;
        boolean relevant = policy.relevant(field.getName());
        boolean checkHpValue = !relevant && (policy == BREAK_TARGET || policy == BREAK_COMPONENT) && source == Source.OWN_FIELD
                && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                && (!(living instanceof net.minecraft.world.entity.player.Player) || isForeign(living.getClass()))
                && isNumericOrStringType(field.getType())
                && !isIgnoredSpatialOrTimerName(field.getName());
        if (!relevant && !checkHpValue) return;
        try {
            field.setAccessible(true);
            Object current = field.get(holder);
            Object value = relevant ? policy.decide(field.getName(), field.getType(), current) : null;
            if (value == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living) {
                if (matchesHealthOrObfuscatedPositive(living, field.getName(), current)) {
                    value = field.getType() == String.class ? "-1.0" : cleared(field.getType(), current);
                }
            }
            if (value == null && current == null) return;
            if (value != null || (relevant && current != null && field.getType() != String.class && !field.getType().isPrimitive())) {
                if (!writeFieldValue(holder, field, value)) return;
                changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
            }
        } catch (Throwable ignored) {}
    }

    private static boolean applyWrapperOrArrayField(Object holder, Field field, String mod, Source source, Policy policy, List<Change> changes) {
        Class<?> fType = field.getType();
        boolean isAtomic = fType == java.util.concurrent.atomic.AtomicBoolean.class
                || fType == java.util.concurrent.atomic.AtomicInteger.class
                || fType == java.util.concurrent.atomic.AtomicLong.class
                || fType == java.util.concurrent.atomic.AtomicReference.class;
        boolean isPrimArray = fType == float[].class || fType == double[].class || fType == int[].class || fType == boolean[].class;
        if (!isAtomic && !isPrimArray) return false;

        boolean relevant = policy.relevant(field.getName());
        boolean checkHpValue = !relevant && (policy == BREAK_TARGET || policy == BREAK_COMPONENT) && source == Source.OWN_FIELD
                && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                && (!(living instanceof net.minecraft.world.entity.player.Player) || isForeign(living.getClass()))
                && !isIgnoredSpatialOrTimerName(field.getName());
        if (!relevant && !checkHpValue) return true;

        try {
            field.setAccessible(true);
            Object wrapper = field.get(holder);
            if (wrapper == null) return true;
            String ownerName = field.getDeclaringClass().getSimpleName();
            if (wrapper instanceof java.util.concurrent.atomic.AtomicBoolean ab) {
                Boolean current = ab.get();
                Object decided = relevant ? policy.decide(field.getName(), boolean.class, current) : null;
                if (decided instanceof Boolean next && !next.equals(current)) {
                    ab.set(next);
                    changes.add(new Change(source, ownerName, field.getName(), mod));
                }
            } else if (wrapper instanceof java.util.concurrent.atomic.AtomicInteger ai) {
                Integer current = ai.get();
                Object decided = relevant ? policy.decide(field.getName(), int.class, current) : null;
                if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                        && matchesHealthOrObfuscatedPositive(living, field.getName(), current)) {
                    decided = 0;
                }
                if (decided instanceof Number next && next.intValue() != current) {
                    ai.set(next.intValue());
                    changes.add(new Change(source, ownerName, field.getName(), mod));
                }
            } else if (wrapper instanceof java.util.concurrent.atomic.AtomicLong al) {
                Long current = al.get();
                Object decided = relevant ? policy.decide(field.getName(), long.class, current) : null;
                if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                        && matchesHealthOrObfuscatedPositive(living, field.getName(), current)) {
                    decided = 0L;
                }
                if (decided instanceof Number next && next.longValue() != current) {
                    al.set(next.longValue());
                    changes.add(new Change(source, ownerName, field.getName(), mod));
                }
            } else if (wrapper instanceof java.util.concurrent.atomic.AtomicReference<?> ar) {
                @SuppressWarnings("unchecked")
                java.util.concurrent.atomic.AtomicReference<Object> ref = (java.util.concurrent.atomic.AtomicReference<Object>) ar;
                Object current = ref.get();
                Class<?> valType = current instanceof Boolean ? boolean.class
                        : current instanceof Integer ? int.class
                        : current instanceof Long ? long.class
                        : current instanceof Float ? float.class
                        : current instanceof Double ? double.class
                        : current instanceof String ? String.class : null;
                if (valType != null) {
                    Object decided = relevant ? policy.decide(field.getName(), valType, current) : null;
                    if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                            && matchesHealthOrObfuscatedPositive(living, field.getName(), current)) {
                        decided = valType == String.class ? "-1.0" : cleared(valType, current);
                    }
                    if (decided != null && !decided.equals(current)) {
                        ref.set(decided);
                        changes.add(new Change(source, ownerName, field.getName(), mod));
                    }
                }
            } else if (checkHpValue && wrapper instanceof float[] arr && arr.length == 2
                    && arr[0] > 0.0F && arr[0] <= arr[1] && arr[1] >= 20.0F) {
                arr[0] = 0.0F;
                changes.add(new Change(source, ownerName, field.getName(), mod));
            } else if (checkHpValue && wrapper instanceof double[] arr && arr.length == 2
                    && arr[0] > 0.0D && arr[0] <= arr[1] && arr[1] >= 20.0D) {
                arr[0] = 0.0D;
                changes.add(new Change(source, ownerName, field.getName(), mod));
            } else if (wrapper instanceof float[] arr && arr.length >= 1 && arr.length <= 4) {
                boolean modified = false;
                for (int i = 0; i < arr.length; i++) {
                    Object decided = relevant ? policy.decide(field.getName(), float.class, arr[i]) : null;
                    if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                            && matchesHealthOrObfuscatedPositive(living, field.getName(), arr[i])) {
                        decided = 0.0F;
                    }
                    if (decided instanceof Number n && Float.compare(arr[i], n.floatValue()) != 0) {
                        arr[i] = n.floatValue();
                        modified = true;
                    }
                }
                if (modified) changes.add(new Change(source, ownerName, field.getName(), mod));
            } else if (wrapper instanceof double[] arr && arr.length >= 1 && arr.length <= 4) {
                boolean modified = false;
                for (int i = 0; i < arr.length; i++) {
                    Object decided = relevant ? policy.decide(field.getName(), double.class, arr[i]) : null;
                    if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                            && matchesHealthOrObfuscatedPositive(living, field.getName(), arr[i])) {
                        decided = 0.0D;
                    }
                    if (decided instanceof Number n && Double.compare(arr[i], n.doubleValue()) != 0) {
                        arr[i] = n.doubleValue();
                        modified = true;
                    }
                }
                if (modified) changes.add(new Change(source, ownerName, field.getName(), mod));
            } else if (wrapper instanceof int[] arr && arr.length >= 1 && arr.length <= 4) {
                boolean modified = false;
                for (int i = 0; i < arr.length; i++) {
                    Object decided = relevant ? policy.decide(field.getName(), int.class, arr[i]) : null;
                    if (decided == null && checkHpValue && hpOwner(holder) instanceof net.minecraft.world.entity.LivingEntity living
                            && matchesHealthOrObfuscatedPositive(living, field.getName(), arr[i])) {
                        decided = 0;
                    }
                    if (decided instanceof Number n && arr[i] != n.intValue()) {
                        arr[i] = n.intValue();
                        modified = true;
                    }
                }
                if (modified) changes.add(new Change(source, ownerName, field.getName(), mod));
            } else if (wrapper instanceof boolean[] arr && arr.length >= 1 && arr.length <= 4 && relevant) {
                boolean modified = false;
                for (int i = 0; i < arr.length; i++) {
                    Object decided = policy.decide(field.getName(), boolean.class, arr[i]);
                    if (decided instanceof Boolean b && arr[i] != b) {
                        arr[i] = b;
                        modified = true;
                    }
                }
                if (modified) changes.add(new Change(source, ownerName, field.getName(), mod));
            }
        } catch (Throwable ignored) {}
        return true;
    }

    /** 他 MOD のオブジェクト（エンティティ以外）の、自分のクラス階層で宣言されたフィールド */
    /**
     * 足されたフィールドが他 MOD のオブジェクト（状態の入れ物）を持っていれば、その中の値も 1 段だけ見る。
     * Fantasy Ending は LivingEntity に {@code uom$ecData}（{@code isDead} 等を持つ入れ物）を足し、getHealth の書き換えがそれを見る（2026-10-03）
     */
    private static void applyInjectedObject(Entity entity, InjectedMembers.InjectedField injected, Policy policy, List<Change> changes) {
        Field field = injected.field();
        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || VALUE_TYPES.contains(field.getType())) return;
        try {
            field.setAccessible(true);
            Object data = field.get(entity);
            if (data == null || data instanceof Entity || data instanceof Collection<?> || data instanceof Map<?, ?>
                    || data.getClass().isArray() || !isForeign(data.getClass())) return;
            applyObjectFields(data, Source.INJECTED_FIELD, policy, changes);
        } catch (Throwable ignored) {}
    }

    // ---- 抹殺対象の内部コンポーネント・ペア HP・Optional（BREAK_TARGET 専用） ------------------------

    private static final int COMPONENT_DEPTH = 2;
    private static final int COMPONENT_LIMIT = 32;
    /** コンポーネント（共有されている可能性のあるオブジェクト）を見ている最中か。名前だけの難読化判定は使わない */
    private static final ThreadLocal<Boolean> IN_COMPONENT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** HP・防御専用のコンテナ（HealthOperator / BossShieldData 等）の内部を見ている最中か */
    private static final ThreadLocal<Boolean> IN_HEALTH_OR_GUARD_CONTAINER = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** コンポーネントを見ているときの持ち主（HP 値の照合用） */
    private static final ThreadLocal<net.minecraft.world.entity.LivingEntity> COMPONENT_OWNER = new ThreadLocal<>();

    /**
     * 抹殺対象自身のフィールドが指す、他 MOD のデータ入れ物（BossShieldData・HealthContainer・HealthOperator 等）の中身を 2 段まで見る。
     * 入れ物が対象を指し返さなくても見る（指し返すものは本体として {@code ModProtectionBreaker} が別に扱う）
     */
    private static void applyOwnedComponents(Entity entity, List<Change> changes) {
        Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<Object> healthContainers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        visited.add(entity);
        List<Object> frontier = new ArrayList<>();
        frontier.add(entity);
        IN_COMPONENT.set(Boolean.TRUE);
        COMPONENT_OWNER.set(entity instanceof net.minecraft.world.entity.LivingEntity living
                && (!(living instanceof net.minecraft.world.entity.player.Player) || isForeign(living.getClass()))
                ? living : null);
        try {
            for (int depth = 0; depth < COMPONENT_DEPTH && !frontier.isEmpty(); depth++) {
                List<Object> next = new ArrayList<>();
                for (Object owner : frontier) {
                    boolean ownerIsHealthContainer = healthContainers.contains(owner);
                    for (Class<?> c = owner.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
                        Field[] fields = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c);
                        for (Field field : fields) {
                            if (visited.size() >= COMPONENT_LIMIT) break;
                            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()
                                    || VALUE_TYPES.contains(field.getType())) continue;
                            try {
                                field.setAccessible(true);
                                Object value = field.get(owner);
                                if (value == null || value instanceof Entity || value instanceof Collection<?>
                                        || value instanceof Map<?, ?> || value instanceof CompoundTag || value instanceof Class<?>
                                        || value.getClass().isArray() || value.getClass().isEnum()
                                        || !isForeign(value.getClass()) || !visited.add(value)
                                        || referencesOtherEntity(value, entity)) continue;
                                boolean isHealthContainer = ownerIsHealthContainer
                                        || isHealthOrGuardContainerField(field.getName(), value.getClass().getSimpleName());
                                if (isHealthContainer) {
                                    healthContainers.add(value);
                                }
                                boolean prevHealthContainer = IN_HEALTH_OR_GUARD_CONTAINER.get();
                                IN_HEALTH_OR_GUARD_CONTAINER.set(isHealthContainer);
                                try {
                                    applyObjectFields(value, Source.OWN_FIELD, BREAK_COMPONENT, changes);
                                } finally {
                                    IN_HEALTH_OR_GUARD_CONTAINER.set(prevHealthContainer);
                                }
                                next.add(value);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
                frontier = next;
            }
        } finally {
            IN_COMPONENT.set(Boolean.FALSE);
            IN_HEALTH_OR_GUARD_CONTAINER.set(Boolean.FALSE);
            COMPONENT_OWNER.remove();
        }
    }

    private static boolean isHealthOrGuardContainerField(String fieldName, String typeSimpleName) {
        String lowerField = bareName(fieldName).toLowerCase(java.util.Locale.ROOT);
        String lowerType = bareName(typeSimpleName).toLowerCase(java.util.Locale.ROOT);
        return lowerField.equals("health") || lowerField.equals("hp")
                || isCustomHealthOrValueName(fieldName)
                || isExtendedProtectiveOnTarget(fieldName)
                || lowerType.contains("health") || lowerType.contains("hp")
                || lowerType.contains("shield")
                || isCustomHealthOrValueName(typeSimpleName);
    }

    /** 入れ物が対象以外のエンティティを指していれば共有物とみなす（別の個体の管理物を巻き込まない。同一 UUID のクライアント／サーバー対は同一視） */
    private static boolean referencesOtherEntity(Object holder, Entity owner) {
        UUID ownerUuid = owner != null ? owner.getUUID() : null;
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c);
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof Entity other && other != owner
                            && (ownerUuid == null || !ownerUuid.equals(other.getUUID()))) return true;
                    if (value instanceof java.lang.ref.Reference<?> ref && ref.get() instanceof Entity other && other != owner
                            && (ownerUuid == null || !ownerUuid.equals(other.getUUID()))) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    /**
     * 名前が難読化されていても、同じ正の値（20 以上）を持つ数値フィールドがちょうど 2 つあれば「現在 HP と最大 HP の組」とみなして 0 にする
     * （独自のスケールで HP を持ち、vanilla の HP と一致しない MOD 向け）
     */
    private static void applyPairedHealthFields(Entity entity, List<Change> changes) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity)
                || (entity instanceof net.minecraft.world.entity.player.Player && !isForeign(entity.getClass()))) return;
        Map<Double, List<Field>> groups = new java.util.HashMap<>();
        for (Class<?> c = entity.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) {
                int m = field.getModifiers();
                if (Modifier.isStatic(m)
                        || (field.getType() != float.class && field.getType() != double.class)
                        || isIgnoredSpatialOrTimerName(field.getName())) continue;
                try {
                    field.setAccessible(true);
                    double value = ((Number) field.get(entity)).doubleValue();
                    if (!Double.isFinite(value) || value < 20.0D) continue;
                    groups.computeIfAbsent(value, k -> new ArrayList<>()).add(field);
                } catch (Throwable ignored) {}
            }
        }
        for (List<Field> group : groups.values()) {
            if (group.size() != 2) continue;
            for (Field field : group) {
                if (writeFieldValue(entity, field, field.getType() == float.class ? 0.0F : 0.0D)) {
                    changes.add(new Change(Source.OWN_FIELD, field.getDeclaringClass().getSimpleName(), field.getName(),
                            CallerAttribution.modOf(field.getDeclaringClass())));
                }
            }
        }
    }

    /** final ではないフィールドはリフレクションで、final なプリミティブ・String は Unsafe で書き込む */
    private static boolean writeFieldValue(Object holder, Field field, @Nullable Object value) {
        if (!Modifier.isFinal(field.getModifiers())) {
            try {
                field.set(holder, value);
                return true;
            } catch (Throwable ignored) {}
        }
        long offset = com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(field);
        if (offset < 0 || !com.automatavalkyries.util.UnsafeHelper.isAvailable()) return false;
        Class<?> type = field.getType();
        try {
            if (type == boolean.class && value instanceof Boolean b) {
                com.automatavalkyries.util.UnsafeHelper.putBoolean(holder, offset, b);
            } else if (type == int.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.putInt(holder, offset, n.intValue());
            } else if (type == long.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.putLong(holder, offset, n.longValue());
            } else if (type == float.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.putFloat(holder, offset, n.floatValue());
            } else if (type == double.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.putDouble(holder, offset, n.doubleValue());
            } else if (type == byte.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.getUnsafe().putByte(holder, offset, n.byteValue());
            } else if (type == short.class && value instanceof Number n) {
                com.automatavalkyries.util.UnsafeHelper.getUnsafe().putShort(holder, offset, n.shortValue());
            } else if (!type.isPrimitive()) {
                com.automatavalkyries.util.UnsafeHelper.putObject(holder, offset, value);
            } else {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code Optional<Boolean/Number/String>} に入った状態を書き換える（final でも Unsafe で差し替える） */
    private static void applyOptionalField(Object holder, Field field, String mod, Source source, Policy policy, List<Change> changes) {
        if (!policy.relevant(field.getName())) return;
        try {
            field.setAccessible(true);
            if (!(field.get(holder) instanceof java.util.Optional<?> optional) || optional.isEmpty()) return;
            Object current = optional.get();
            Class<?> valType = current instanceof Boolean ? boolean.class
                    : current instanceof Integer ? int.class
                    : current instanceof Long ? long.class
                    : current instanceof Float ? float.class
                    : current instanceof Double ? double.class
                    : current instanceof String ? String.class : null;
            if (valType == null) return;
            Object decided = policy.decide(field.getName(), valType, current);
            if (decided == null || decided.equals(current)) return;
            java.util.Optional<Object> replacement = java.util.Optional.of(decided);
            try {
                field.set(holder, replacement);
            } catch (Throwable denied) {
                com.automatavalkyries.util.UnsafeHelper.putObject(holder,
                        com.automatavalkyries.util.UnsafeHelper.objectFieldOffset(field), replacement);
            }
            changes.add(new Change(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod));
        } catch (Throwable ignored) {}
    }

    /** HP の照合に使う持ち主（エンティティ自身か、コンポーネントを見ているときの持ち主）。通常のバニラプレイヤーは除くが他 MOD の Player サブクラスは対象 */
    @Nullable
    private static Object hpOwner(Object holder) {
        if (holder instanceof net.minecraft.world.entity.LivingEntity le) {
            return (le instanceof net.minecraft.world.entity.player.Player && !isForeign(le.getClass())) ? null : le;
        }
        return IN_COMPONENT.get() ? COMPONENT_OWNER.get() : null;
    }

    private static void applyObjectFields(Object holder, Source source, Policy policy, List<Change> changes) {
        for (Class<?> c = holder.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            String mod = CallerAttribution.modOf(c);
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) applyField(holder, field, mod, source, policy, changes);
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

    // ---- 同期データ --------------------------------------------------------------

    /** 他 MOD の同期データの鍵（static な EntityDataAccessor）と、その名前・MOD */
    public record DataKey(EntityDataAccessor<?> accessor, String name, String mod) {}

    private static final ClassValue<List<DataKey>> DATA_KEYS = new ClassValue<>() {
        @Override
        protected List<DataKey> computeValue(Class<?> type) {
            return findDataKeys(type);
        }
    };

    /** vanilla のエンティティのクラスに足された同期データの鍵（入口で書き込みを拒否する判定用。全エンティティ共通） */
    @Nullable
    private static volatile Map<EntityDataAccessor<?>, DataKey> injectedKeys;

    @Nullable
    private static final Field ENTITY_DATA_FIELD = findFirstField(Entity.class, "entityData", "f_19804_");
    @Nullable
    private static final Field SYNCHED_ITEMS_FIELD = findFieldByType(SynchedEntityData.class, it.unimi.dsi.fastutil.ints.Int2ObjectMap.class);

    private static final ClassValue<Set<EntityDataAccessor<?>>> VANILLA_DATA_ACCESSORS = new ClassValue<>() {
        @Override
        protected Set<EntityDataAccessor<?>> computeValue(Class<?> type) {
            Set<EntityDataAccessor<?>> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                if (isForeign(c)) continue;
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) continue;
                    if (InjectedMembers.fields(c).stream().anyMatch(inf -> inf.field().equals(field))) continue;
                    try {
                        field.setAccessible(true);
                        if (field.get(null) instanceof EntityDataAccessor<?> acc) set.add(acc);
                    } catch (Throwable ignored) {}
                }
            }
            return set;
        }
    };

    private static void applySynchedData(Entity entity, Policy policy, List<Change> changes) {
        SynchedEntityData data = readEntityData(entity);
        boolean foreignEntity = isForeign(entity.getClass());
        Set<EntityDataAccessor<?>> handled = policy == BREAK_TARGET && foreignEntity
                ? java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())
                : null;
        for (DataKey key : DATA_KEYS.get(entity.getClass())) {
            if (handled != null) handled.add(key.accessor());
            boolean relevant = policy.relevant(key.name());
            if (!relevant && !(policy == BREAK_TARGET && foreignEntity && !FlagDictionary.is(key.name(), FlagDictionary.Category.TIMER))) continue;
            try {
                @SuppressWarnings("unchecked")
                EntityDataAccessor<Object> accessor = (EntityDataAccessor<Object>) key.accessor();
                Object current = data.get(accessor); // この個体に無い鍵なら例外（無視する）
                Class<?> type = current instanceof Boolean ? boolean.class : current instanceof Float ? float.class
                        : current instanceof Integer ? int.class : current instanceof String ? String.class : null;
                if (type == null && current instanceof CompoundTag tag && policy == BREAK_TARGET) {
                    CompoundTag copy = tag.copy();
                    int before = changes.size();
                    applyCompound(copy, "data." + key.name(), policy, changes, 0);
                    if (changes.size() > before) data.set(accessor, copy);
                    continue;
                }
                if (type == null) continue;
                Object value = relevant ? policy.decide(key.name(), type, current) : null;
                if (value == null && policy == BREAK_TARGET && foreignEntity && !FlagDictionary.is(key.name(), FlagDictionary.Category.TIMER)) {
                    if (current instanceof Float f && f > 0.0F) {
                        value = 0.0F;
                    } else if (current instanceof String s && isPositiveNumericString(s)) {
                        value = "-1.0";
                    }
                }
                if (value == null) continue;
                data.set(accessor, value);
                changes.add(new Change(Source.SYNCHED_DATA, "data", key.name(), key.mod()));
            } catch (Throwable ignored) {}
        }

        // 非公開・非 static な EntityDataAccessor を itemsById から直接掃討（BREAK_TARGET かつ他 MOD エンティティ）
        if (policy == BREAK_TARGET && foreignEntity && SYNCHED_ITEMS_FIELD != null && handled != null) {
            try {
                if (SYNCHED_ITEMS_FIELD.get(data) instanceof it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> itemsMap) {
                    Set<EntityDataAccessor<?>> vanillaAccessors = VANILLA_DATA_ACCESSORS.get(entity.getClass());
                    String mod = CallerAttribution.modOf(entity.getClass());
                    for (Object rawItem : itemsMap.values()) {
                        if (!(rawItem instanceof SynchedEntityData.DataItem<?> item)) continue;
                        EntityDataAccessor<?> rawAcc = item.getAccessor();
                        if (handled.contains(rawAcc) || vanillaAccessors.contains(rawAcc)) continue;
                        Object current = item.getValue();
                        Object nextValue = null;
                        if (current instanceof Float f && f > 0.0F) {
                            nextValue = 0.0F;
                        } else if (current instanceof String s && isPositiveNumericString(s)) {
                            nextValue = "-1.0";
                        } else if (current instanceof CompoundTag tag) {
                            CompoundTag copy = tag.copy();
                            int before = changes.size();
                            applyCompound(copy, "itemsById.id_" + rawAcc.getId(), policy, changes, 0);
                            if (changes.size() > before) nextValue = copy;
                        } else if (current instanceof Integer iv && iv > 0 && entity instanceof net.minecraft.world.entity.LivingEntity living
                                && matchesHealthOrObfuscatedPositive(living, "id_" + rawAcc.getId(), iv)) {
                            nextValue = 0;
                        }
                        if (nextValue != null) {
                            @SuppressWarnings("unchecked")
                            EntityDataAccessor<Object> acc = (EntityDataAccessor<Object>) rawAcc;
                            data.set(acc, nextValue);
                            changes.add(new Change(Source.SYNCHED_DATA, "itemsById", "id_" + rawAcc.getId(), mod));
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static SynchedEntityData readEntityData(Entity entity) {
        if (ENTITY_DATA_FIELD != null) {
            try {
                if (ENTITY_DATA_FIELD.get(entity) instanceof SynchedEntityData data) return data;
            } catch (Throwable ignored) {}
        }
        return entity.getEntityData();
    }

    /** そのクラス階層の、他 MOD の同期データの鍵（既存クラスに足されたもの＋他 MOD のクラスが宣言したもの） */
    private static List<DataKey> findDataKeys(Class<?> type) {
        List<DataKey> keys = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (isForeign(c)) {
                String mod = CallerAttribution.modOf(c);
                for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) addDataKey(field, mod, keys);
            } else {
                for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                    if (!CallerAttribution.SELF.equals(injected.mod())) addDataKey(injected.field(), injected.mod(), keys);
                }
            }
        }
        // 他 MOD が補助クラスに置いた鍵（作られた場所から名前を引いたもの）
        for (DataKey extra : SynchedKeyOrigins.keysFor(type)) {
            if (keys.stream().noneMatch(k -> k.accessor() == extra.accessor())) keys.add(extra);
        }
        return List.copyOf(keys);
    }

    private static void addDataKey(Field field, String mod, List<DataKey> out) {
        if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) return;
        try {
            field.setAccessible(true);
            if (field.get(null) instanceof EntityDataAccessor<?> key) out.add(new DataKey(key, field.getName(), mod));
        } catch (Throwable ignored) {}
    }

    /** 他 MOD が vanilla のエンティティのクラスに足した同期データの鍵なら、その情報（入口での拒否の判定用） */
    @Nullable
    public static DataKey injectedDataKey(EntityDataAccessor<?> accessor) {
        Map<EntityDataAccessor<?>, DataKey> keys = injectedKeys;
        if (keys == null) {
            Map<EntityDataAccessor<?>, DataKey> built = new java.util.IdentityHashMap<>();
            for (Class<?> c : new Class<?>[]{Entity.class, net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.entity.Mob.class}) {
                List<DataKey> found = new ArrayList<>();
                for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                    if (!CallerAttribution.SELF.equals(injected.mod())) addDataKey(injected.field(), injected.mod(), found);
                }
                for (DataKey key : found) built.put(key.accessor(), key);
            }
            injectedKeys = keys = built;
        }
        return keys.get(accessor);
    }

    // ---- 永続データ --------------------------------------------------------------

    private static void applyPersistent(Entity entity, Policy policy, List<Change> changes) {
        CompoundTag tag = entity.getPersistentData();
        java.util.stream.Stream.of(tag).forEach(t -> applyCompound(t, "persistentData", policy, changes, 0));
    }

    private static void applyCompound(CompoundTag tag, String path, Policy policy, List<Change> changes, int depth) {
        List<String> remove = new ArrayList<>();
        List<String> zeroHealthKeys = new ArrayList<>();
        for (String key : tag.getAllKeys()) {
            if (key.startsWith(AutomataValkyries.MODID)) continue;
            if (policy == BREAK_TARGET && isCustomHealthOrValueName(key) && tag.get(key) instanceof net.minecraft.nbt.NumericTag) {
                zeroHealthKeys.add(key);
            } else if (policy.removeKey(key)) {
                remove.add(key);
            } else if (tag.getTagType(key) == Tag.TAG_STRING && policy.relevant(tag.getString(key))) {
                remove.add(key);
            } else if (depth <= 2 && tag.getTagType(key) == Tag.TAG_COMPOUND) {
                applyCompound(tag.getCompound(key), path + "." + key, policy, changes, depth + 1);
            }
        }
        for (String key : zeroHealthKeys) {
            tag.putFloat(key, -1.0F);
            changes.add(new Change(Source.PERSISTENT, path, key, modOfKey(key)));
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

    // ---- 読むだけ（辞書の監視・ForeignStateBaseline 用） ----------------------------------------------------

    /** 読んだ 1 件。値は Boolean か Number */
    public record Observed(Source source, String holder, String name, String mod, Object value) {
        /** 同じ状態を指す鍵（個体をまたいで集計する） */
        public String key() {
            return mod + " " + source.name().toLowerCase(java.util.Locale.ROOT) + ":" + holder + "." + name;
        }
    }

    @Nullable
    private static final Field PERSISTENT_DATA = findField(Entity.class, "persistentData");

    /**
     * 他 MOD の状態を読むだけ（{@link FlagWatchAgent} / {@code ForeignStateBaseline} 用）。書き込まず、他 MOD のコード（getter）も呼ばない。
     * 読む場所は {@link #apply} と同じだが、インターフェースは getter を呼ぶことになるので読まない。
     * 永続データは {@code getPersistentData()} が空の入れ物を作ってしまうので、フィールドを直接読む。
     */
    public static void observe(Entity entity, java.util.function.Consumer<Observed> out) {
        Class<?> type = entity.getClass();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                if (CallerAttribution.SELF.equals(injected.mod())) continue;
                observeField(entity, injected.field(), injected.mod(), Source.INJECTED_FIELD, out);
                observeInjectedObject(entity, injected, out);
            }
        }
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            String mod = CallerAttribution.modOf(c);
            for (Field field : com.automatavalkyries.util.UnsafeHelper.getRealDeclaredFields(c)) observeField(entity, field, mod, Source.OWN_FIELD, out);
        }
        observeCapabilities(entity, out);
        try {
            if (PERSISTENT_DATA != null && PERSISTENT_DATA.get(entity) instanceof CompoundTag tag) {
                observeCompound(tag, "persistentData", out, 0);
            }
        } catch (Throwable ignored) {}
        SynchedEntityData data = entity.getEntityData();
        for (DataKey key : DATA_KEYS.get(type)) {
            try {
                Object value = data.get(key.accessor());
                if (value instanceof Boolean || value instanceof Number) {
                    out.accept(new Observed(Source.SYNCHED_DATA, "data", key.name(), key.mod(), value));
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void observeInjectedObject(Entity entity, InjectedMembers.InjectedField injected, java.util.function.Consumer<Observed> out) {
        Field field = injected.field();
        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || VALUE_TYPES.contains(field.getType())) return;
        try {
            field.setAccessible(true);
            Object data = field.get(entity);
            if (data == null || data instanceof Entity || data instanceof Collection<?> || data instanceof Map<?, ?>
                    || data.getClass().isArray() || !isForeign(data.getClass())) return;
            for (Class<?> c = data.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
                String mod = CallerAttribution.modOf(c);
                for (Field f : c.getDeclaredFields()) observeField(data, f, mod, Source.INJECTED_FIELD, out);
            }
        } catch (Throwable ignored) {}
    }

    private static void observeField(Object holder, Field field, String mod, Source source, java.util.function.Consumer<Observed> out) {
        if (Modifier.isStatic(field.getModifiers())) return;
        Class<?> fType = field.getType();
        if (VALUE_TYPES.contains(fType)) {
            try {
                field.setAccessible(true);
                out.accept(new Observed(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod, field.get(holder)));
            } catch (Throwable ignored) {}
            return;
        }
        // AtomicBoolean / AtomicInteger / AtomicLong / AtomicReference / Optional / 1要素配列の読み取り
        boolean isSupportedContainer = fType == java.util.concurrent.atomic.AtomicBoolean.class
                || fType == java.util.concurrent.atomic.AtomicInteger.class
                || fType == java.util.concurrent.atomic.AtomicLong.class
                || fType == java.util.concurrent.atomic.AtomicReference.class
                || fType == java.util.Optional.class
                || fType == float[].class || fType == double[].class || fType == int[].class || fType == boolean[].class;
        if (!isSupportedContainer) return;
        try {
            field.setAccessible(true);
            Object raw = field.get(holder);
            if (raw == null) return;
            Object unwrapped = null;
            if (raw instanceof java.util.concurrent.atomic.AtomicBoolean ab) {
                unwrapped = ab.get();
            } else if (raw instanceof java.util.concurrent.atomic.AtomicInteger ai) {
                unwrapped = ai.get();
            } else if (raw instanceof java.util.concurrent.atomic.AtomicLong al) {
                unwrapped = al.get();
            } else if (raw instanceof java.util.concurrent.atomic.AtomicReference<?> ar) {
                Object inner = ar.get();
                if (inner instanceof Boolean || inner instanceof Number) unwrapped = inner;
            } else if (raw instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object inner = opt.get();
                if (inner instanceof Boolean || inner instanceof Number) unwrapped = inner;
            } else if (raw instanceof float[] arr && arr.length == 1) {
                unwrapped = arr[0];
            } else if (raw instanceof double[] arr && arr.length == 1) {
                unwrapped = arr[0];
            } else if (raw instanceof int[] arr && arr.length == 1) {
                unwrapped = arr[0];
            } else if (raw instanceof boolean[] arr && arr.length == 1) {
                unwrapped = arr[0];
            }
            if (unwrapped instanceof Boolean || unwrapped instanceof Number) {
                out.accept(new Observed(source, field.getDeclaringClass().getSimpleName(), field.getName(), mod, unwrapped));
            }
        } catch (Throwable ignored) {}
    }

    private static void observeObjectFields(Object holder, java.util.function.Consumer<Observed> out) {
        for (Class<?> c = holder.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            String mod = CallerAttribution.modOf(c);
            for (Field field : c.getDeclaredFields()) observeField(holder, field, mod, Source.CAPABILITY, out);
        }
    }

    private static void observeCapabilities(Entity entity, java.util.function.Consumer<Observed> out) {
        if (CAPABILITIES == null || PROVIDERS == null) return;
        try {
            Object dispatcher = CAPABILITIES.get(entity);
            if (dispatcher == null) return;
            Object[] providers = (Object[]) PROVIDERS.get(dispatcher);
            if (providers == null) return;
            for (Object provider : providers) {
                if (provider == null || !isForeign(provider.getClass())) continue;
                observeObjectFields(provider, out);
                for (Class<?> c = provider.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        field.setAccessible(true);
                        Object data = field.get(provider);
                        if (data == null || data == provider || data instanceof Entity || data instanceof Collection<?>
                                || data instanceof Map<?, ?> || !isForeign(data.getClass())) continue;
                        observeObjectFields(data, out);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void observeCompound(CompoundTag tag, String path, java.util.function.Consumer<Observed> out, int depth) {
        for (String key : tag.getAllKeys()) {
            if (key.startsWith(AutomataValkyries.MODID)) continue;
            Tag value = tag.get(key);
            if (value instanceof net.minecraft.nbt.NumericTag number) {
                out.accept(new Observed(Source.PERSISTENT, path, key, modOfKey(key), number.getAsNumber()));
            } else if (depth == 0 && value instanceof CompoundTag nested) {
                observeCompound(nested, path + "." + key, out, depth + 1);
            }
        }
    }

    @Nullable
    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Field findFirstField(Class<?> owner, String... names) {
        for (String name : names) {
            Field f = findField(owner, name);
            if (f != null) return f;
        }
        return null;
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
        if (type == String.class) return (current == null || "".equals(current)) ? null : "";
        if (type == java.util.UUID.class) return null;
        if (Entity.class.isAssignableFrom(type)) return null;
        return null;
    }

    public static boolean isCustomHealthOrValueName(String name) {
        if (name == null || name.isEmpty() || FlagDictionary.is(name, FlagDictionary.Category.TIMER)) return false;
        if (FlagDictionary.is(name, FlagDictionary.Category.HEALTH_TAMPER)) return true;
        String lower = bareName(name).toLowerCase(java.util.Locale.ROOT);
        return lower.contains("helpo") || lower.equals("m_health") || lower.equals("mhealth")
                || lower.equals("data_value_id") || lower.equals("datavalueid") || lower.equals("data_health_id")
                || lower.equals("datahealthid") || lower.contains("customhealth") || lower.contains("realhealth")
                || lower.contains("truehealth") || lower.contains("bosshp") || lower.contains("shieldhp")
                || lower.contains("realhp") || lower.contains("truehp") || lower.contains("customhp");
    }

    public static boolean isExtendedDeathStateField(String name) {
        return com.automatavalkyries.annihilate.FlagKillPredicates.isDeathStateField(name);
    }

    public static boolean isExtendedProtectiveOnTarget(String name) {
        return com.automatavalkyries.annihilate.FlagKillPredicates.protectiveOnTarget(name);
    }

    public static boolean isPositiveNumericString(@Nullable Object value) {
        if (!(value instanceof String text) || text.isEmpty() || text.length() > 32) return false;
        try {
            float parsed = Float.parseFloat(text.trim());
            return Float.isFinite(parsed) && parsed > 0.0F;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isNumericOrStringType(Class<?> type) {
        return type == float.class || type == double.class || type == int.class || type == long.class || type == String.class;
    }

    private static boolean isIgnoredSpatialOrTimerName(String name) {
        if (name == null || name.isEmpty() || FlagDictionary.is(name, FlagDictionary.Category.TIMER)) return true;
        String lower = bareName(name).toLowerCase(java.util.Locale.ROOT);
        return lower.equals("x") || lower.equals("y") || lower.equals("z")
                || lower.endsWith("pos") || lower.endsWith("coord") || lower.contains("yaw") || lower.contains("pitch")
                || lower.contains("rot") || lower.contains("scale") || lower.contains("width") || lower.contains("height")
                || lower.contains("size") || lower.contains("speed") || lower.contains("range") || lower.contains("radius")
                || lower.contains("color") || lower.contains("alpha") || lower.contains("anim") || lower.contains("frame")
                || lower.contains("age") || lower.contains("tick") || lower.contains("time") || lower.contains("delay")
                || lower.contains("cooldown") || lower.equals("id") || lower.endsWith("id");
    }

    private static boolean isObfuscatedName(String name) {
        String bare = bareName(name);
        if (bare.length() <= 2) return true;
        if (bare.startsWith("f_") && bare.endsWith("_")) return true;
        if (bare.startsWith("field_")) return true;
        return false;
    }

    private static boolean matchesHealthOrObfuscatedPositive(net.minecraft.world.entity.LivingEntity living, String fieldName, @Nullable Object current) {
        double val;
        if (current instanceof Number n) {
            val = n.doubleValue();
        } else if (current instanceof String s && isPositiveNumericString(s)) {
            val = Double.parseDouble(s.trim());
        } else {
            return false;
        }
        if (!Double.isFinite(val) || val <= 0.0D) return false;
        try {
            float maxHp = living.getMaxHealth();
            if (maxHp >= 10.0F && Math.abs(val - maxHp) <= 0.05D) return true;
            float curHp = living.getHealth();
            if (curHp >= 10.0F && Math.abs(val - curHp) <= 0.05D) return true;
        } catch (Throwable ignored) {}
        if (IN_HEALTH_OR_GUARD_CONTAINER.get()) {
            return current instanceof Float || current instanceof Double || current instanceof Integer
                    || current instanceof Long || current instanceof String;
        }
        if (IN_COMPONENT.get()) return false; // 共有されているかもしれない入れ物では、名前だけでは決めない
        return isObfuscatedName(fieldName) && (current instanceof Float || current instanceof Double || current instanceof String);
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
