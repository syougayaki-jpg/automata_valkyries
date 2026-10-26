package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.EventBusGuardian;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 相手の防御構造の【探索 → 調査 → 把握 → 解析】を一貫して行い、
 * 解体パイプライン（{@link ModProtectionBreaker} / {@link GenericCheatMobNeutralizer}）へ
 * 構造化プロファイル（{@link AnalysisReport}）を引き渡す解析エンジン。
 *
 * <ol>
 *   <li><b>探索 (Reconnaissance)</b>: 生の UUID / Entity ID と偽装の有無、対象クラス階層、バイトコード参照先、
 *       EventBus 購読者、Java エージェント定義クラスを網羅収集する。</li>
 *   <li><b>調査 (Investigation)</b>: 対象の直接フィールドだけでなく、{@link Reference} / {@link AtomicReference} /
 *       {@link Optional} / 1要素配列や内部コンポーネントに隠された本体（Controller / Core）を走査する。</li>
 *   <li><b>把握 (Grasping / Reverse Static Lookup)</b>: 対象エンティティ側から一切フィールド参照を持たない
 *       完全分離型の外部管理コア（static フィールド・static リスト・static マップ内にのみ存在し、
 *       対象エンティティまたは対象 UUID を逆参照している本体）を逆引きで捕捉する。</li>
 *   <li><b>解析 (Structural Analysis)</b>: 捕捉した全本体の UUID・関連クラス・防御種別（{@link Mechanism}）を
 *       統合した {@link AnalysisReport} を生成し、後続の解体手段が漏れなく一気通貫で機能するようにする。</li>
 * </ol>
 */
public final class DefenseStructureAnalyzer {

    private static final int MAX_REGISTRY_CLASSES = 160;
    private static final int MAX_STATIC_INSPECT_ELEMENTS = 128;

    public enum Mechanism {
        IDENTITY_SPOOF,
        DIRECT_CONTROLLER,
        WRAPPED_OR_NESTED_CONTROLLER,
        REVERSE_STATIC_CONTROLLER,
        SAVED_DATA_CONTROLLER,
        OWNED_COMPONENT,
        BYTECODE_RELATED_CLASS,
        EVENT_BUS_SUBSCRIBER,
        AGENT_LOADED_CLASS,
        PROTECTIVE_MOB_EFFECT
    }

    public record AnalysisReport(
            Entity target,
            String modId,
            UUID rawUuid,
            int rawId,
            List<Object> controllers,
            Set<Class<?>> registryClasses,
            Set<UUID> identities,
            EnumSet<Mechanism> mechanisms
    ) {
        public static final AnalysisReport EMPTY = new AnalysisReport(
                null,
                CallerAttribution.UNKNOWN,
                new UUID(0L, 0L),
                -1,
                List.of(),
                Set.of(),
                Set.of(),
                EnumSet.noneOf(Mechanism.class)
        );

        public boolean foreign() {
            return CallerAttribution.isForeign(modId);
        }

        public ModProtectionBreaker.Context toContext() {
            return new ModProtectionBreaker.Context(
                    List.copyOf(controllers),
                    Set.copyOf(registryClasses),
                    Set.copyOf(identities),
                    this
            );
        }
    }

    private DefenseStructureAnalyzer() {}

    /**
     * 対象エンティティの防御構造を探索・調査・把握・解析し、解体用の構造化レポートを返す。
     * （※本体の remove() 等が逆参照 proxy を必要とするため、この段階では対象や本体のフィールドを書き換えない）
     */
    public static AnalysisReport analyze(Entity target) {
        EnumSet<Mechanism> mechanisms = EnumSet.noneOf(Mechanism.class);
        if (target == null) {
            return new AnalysisReport(null, CallerAttribution.UNKNOWN, new UUID(0L, 0L), -1,
                    List.of(), Set.of(), Set.of(), mechanisms);
        }

        String modId = CallerAttribution.modOf(target.getClass());
        UUID rawUuid = MadnessAnnihilation.uuid(target);
        int rawId = MadnessAnnihilation.id(target);

        Set<UUID> identities = new LinkedHashSet<>();
        if (rawUuid != null) identities.add(rawUuid);
        try {
            UUID reportedUuid = target.getUUID();
            if (reportedUuid != null && !reportedUuid.equals(rawUuid)) {
                identities.add(reportedUuid);
                mechanisms.add(Mechanism.IDENTITY_SPOOF);
            }
            if (target.getId() != rawId || (target instanceof LivingEntity living && DirectRead.spoofedRead(living) != null)) {
                mechanisms.add(Mechanism.IDENTITY_SPOOF);
            }
        } catch (Throwable ignored) {
            mechanisms.add(Mechanism.IDENTITY_SPOOF);
        }

        Set<Class<?>> registryClasses = new LinkedHashSet<>();
        List<Object> controllers = new ArrayList<>();
        Set<Object> seenControllers = Collections.newSetFromMap(new IdentityHashMap<>());
        seenControllers.add(target);

        // Phase 1: 対象自身のクラス階層・フィールド・関連クラスの探索
        captureObjectGraphAndControllers(target, target, identities, registryClasses,
                controllers, seenControllers, mechanisms);
        expandRelatedClasses(target.getClass(), registryClasses, mechanisms);

        // Phase 2: 既に発見された本体の関連クラスを先に展開
        for (Object controller : new ArrayList<>(controllers)) {
            captureControllerMetadata(controller, identities, registryClasses, mechanisms);
        }

        // Phase 3: 把握（Reverse Static Lookup）— 関連クラスの static 領域から、
        // 対象から直接参照されていない「逆参照のみの独立本体（Detached Static Core）」を逆引き発見する
        discoverReverseStaticControllers(target, identities, registryClasses,
                controllers, seenControllers, mechanisms);

        // Phase 3.5: 把握（DimensionDataStorage SavedData Lookup）— ワールドの SavedData ストレージ内に格納された
        // 他 MOD 製の復活管理・永続状態コントローラー（BossPersistentData 等）を逆引き発見する
        discoverSavedDataControllers(target, modId, identities, registryClasses,
                controllers, seenControllers, mechanisms);

        // Phase 4: 保護エフェクトの把握と診断記録
        if (target instanceof LivingEntity living) {
            try {
                for (MobEffectInstance instance : living.getActiveEffects()) {
                    ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect());
                    if (key != null && CallerAttribution.isForeign(key.getNamespace())
                            && FlagKillPredicates.protectiveOnTarget(key.getPath())) {
                        mechanisms.add(Mechanism.PROTECTIVE_MOB_EFFECT);
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (!mechanisms.isEmpty() || !controllers.isEmpty()) {
            EprotectPlusLink.recordInterference(
                    target.getClass().getName(),
                    modId,
                    "defense_structure_analyzed",
                    "mechanisms=" + mechanisms + ", controllers=" + controllers.size()
                            + ", registries=" + registryClasses.size() + ", identities=" + identities.size()
            );
        }

        return new AnalysisReport(
                target,
                modId,
                rawUuid,
                rawId,
                List.copyOf(controllers),
                Set.copyOf(registryClasses),
                Set.copyOf(identities),
                mechanisms
        );
    }

    private static void captureObjectGraphAndControllers(
            Object holder,
            Entity target,
            Set<UUID> targetIdentities,
            Set<Class<?>> registryClasses,
            List<Object> controllers,
            Set<Object> seenControllers,
            EnumSet<Mechanism> mechanisms
    ) {
        addForeignHierarchy(holder.getClass(), registryClasses);
        List<Object> oneHopContainers = new ArrayList<>();

        for (Class<?> type = holder.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            Field[] fields = UnsafeHelper.getRealDeclaredFields(type);
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                Object rawValue = readFieldSafe(holder, field);
                if (rawValue == null || rawValue == target || rawValue instanceof Entity) continue;

                if (isForeign(rawValue.getClass())) {
                    addForeignHierarchy(rawValue.getClass(), registryClasses);
                }

                // (A) 直接フィールドの本体判定
                if (isForeign(rawValue.getClass())
                        && GenericCheatMobNeutralizer.ownsController(rawValue, target, targetIdentities, false)) {
                    if (seenControllers.add(rawValue)) {
                        controllers.add(rawValue);
                        mechanisms.add(holder == target
                                ? Mechanism.DIRECT_CONTROLLER
                                : Mechanism.WRAPPED_OR_NESTED_CONTROLLER);
                    }
                    continue;
                }

                // (B) Reference / AtomicReference / Optional / 1要素配列のアンラップ判定
                Object unwrapped = unwrapSingle(rawValue);
                if (unwrapped != null && unwrapped != rawValue && unwrapped != target && !(unwrapped instanceof Entity)) {
                    if (isForeign(unwrapped.getClass())) {
                        addForeignHierarchy(unwrapped.getClass(), registryClasses);
                        if (GenericCheatMobNeutralizer.ownsController(unwrapped, target, targetIdentities, false)) {
                            if (seenControllers.add(unwrapped)) {
                                controllers.add(unwrapped);
                                mechanisms.add(Mechanism.WRAPPED_OR_NESTED_CONTROLLER);
                            }
                            continue;
                        }
                    }
                }

                // (C) 1段目の内部コンポーネント候補として記録
                Object candidateContainer = unwrapped != null ? unwrapped : rawValue;
                if (holder == target && isForeign(candidateContainer.getClass())
                        && !isValueOrCollectionType(candidateContainer)
                        && oneHopContainers.size() < 16) {
                    oneHopContainers.add(candidateContainer);
                    mechanisms.add(Mechanism.OWNED_COMPONENT);
                }
            }
        }

        // 1段目の内部コンポーネントの中に隠された本体も探索する
        if (holder == target) {
            for (Object container : oneHopContainers) {
                if (GenericCheatMobNeutralizer.containsOtherEntityObject(container, target)) continue;
                captureObjectGraphAndControllers(container, target, targetIdentities,
                        registryClasses, controllers, seenControllers, mechanisms);
            }
        }
    }

    private static void discoverReverseStaticControllers(
            Entity target,
            Set<UUID> targetIdentities,
            Set<Class<?>> registryClasses,
            List<Object> controllers,
            Set<Object> seenControllers,
            EnumSet<Mechanism> mechanisms
    ) {
        List<Class<?>> snapshotClasses = new ArrayList<>(registryClasses);
        List<Object> newlyFound = new ArrayList<>();

        for (Class<?> clazz : snapshotClasses) {
            if (!isForeign(clazz) || !UnsafeHelper.isInitialized(clazz)) continue;
            Field[] fields = UnsafeHelper.getRealDeclaredFields(clazz);
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                Object value = readStaticFieldSafe(field);
                if (value == null) continue;

                // 1. 単一 static フィールド（または Reference / AtomicReference / Optional ラッパー）
                Object single = unwrapSingle(value);
                if (single != null && isCandidateControllerObject(single, target)
                        && !seenControllers.contains(single)
                        && GenericCheatMobNeutralizer.ownsController(single, target, targetIdentities, false)) {
                    seenControllers.add(single);
                    controllers.add(single);
                    newlyFound.add(single);
                    mechanisms.add(Mechanism.REVERSE_STATIC_CONTROLLER);
                    continue;
                }

                // 2. static な Collection / ArrayList / CopyOnWriteArrayList / Map / Object[] 内の本体探索
                for (Object elem : extractStaticElementsSafe(value)) {
                    Object unwrapped = unwrapSingle(elem);
                    if (unwrapped != null && isCandidateControllerObject(unwrapped, target)
                            && !seenControllers.contains(unwrapped)
                            && GenericCheatMobNeutralizer.ownsController(unwrapped, target, targetIdentities, false)) {
                        seenControllers.add(unwrapped);
                        controllers.add(unwrapped);
                        newlyFound.add(unwrapped);
                        mechanisms.add(Mechanism.REVERSE_STATIC_CONTROLLER);
                    }
                }
            }
        }

        for (Object controller : newlyFound) {
            captureControllerMetadata(controller, targetIdentities, registryClasses, mechanisms);
        }
    }

    /**
     * サーバーワールドの {@code DimensionDataStorage}（{@code ServerLevel.getDataStorage()}）の内部マップから、
     * 抹殺対象（または同一 MOD のボス）を管理・監視している他 MOD 製の {@code SavedData} を逆引きで捕捉する。
     */
    private static void discoverSavedDataControllers(
            Entity target,
            String targetMod,
            Set<UUID> targetIdentities,
            Set<Class<?>> registryClasses,
            List<Object> controllers,
            Set<Object> seenControllers,
            EnumSet<Mechanism> mechanisms
    ) {
        if (target == null || !(target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        List<Object> newlyFound = new ArrayList<>();
        try {
            Object dataStorage = serverLevel.getDataStorage();
            if (dataStorage == null) return;
            for (Class<?> sc = dataStorage.getClass(); sc != null && sc != Object.class; sc = sc.getSuperclass()) {
                for (Field f : UnsafeHelper.getRealDeclaredFields(sc)) {
                    if (Modifier.isStatic(f.getModifiers()) || !Map.class.isAssignableFrom(f.getType())) continue;
                    Object rawMap = readFieldSafe(dataStorage, f);
                    if (!(rawMap instanceof Map<?, ?> storageMap) || storageMap.isEmpty()) continue;
                    int inspected = 0;
                    for (Object val : new ArrayList<>(storageMap.values())) {
                        if (++inspected > MAX_STATIC_INSPECT_ELEMENTS) break;
                        Object unwrapped = unwrapSingle(val);
                        if (unwrapped == null || !isCandidateControllerObject(unwrapped, target)
                                || seenControllers.contains(unwrapped)) {
                            continue;
                        }
                        boolean owned = GenericCheatMobNeutralizer.ownsController(unwrapped, target, targetIdentities, false);
                        if (!owned && CallerAttribution.isForeign(targetMod)
                                && targetMod.equals(CallerAttribution.modOf(unwrapped.getClass()))) {
                            owned = savedDataOwnedBySameModTarget(unwrapped, target, targetIdentities, serverLevel);
                        }
                        if (owned) {
                            seenControllers.add(unwrapped);
                            controllers.add(unwrapped);
                            newlyFound.add(unwrapped);
                            mechanisms.add(Mechanism.SAVED_DATA_CONTROLLER);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        for (Object controller : newlyFound) {
            captureControllerMetadata(controller, targetIdentities, registryClasses, mechanisms);
        }
    }

    private static boolean savedDataOwnedBySameModTarget(
            Object savedData,
            Entity target,
            Set<UUID> targetIdentities,
            net.minecraft.server.level.ServerLevel level
    ) {
        if (GenericCheatMobNeutralizer.containsOtherEntityObject(savedData, target)) return false;
        boolean hasBossStateStructure = false;
        for (Class<?> c = savedData.getClass(); c != null && isForeign(c); c = c.getSuperclass()) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName();
                Object val = unwrapSingle(readFieldSafe(savedData, field));
                if (val instanceof UUID id && isIdentityFieldName(name)) {
                    hasBossStateStructure = true;
                    if (!targetIdentities.contains(id) && !id.equals(target.getUUID())) {
                        return false;
                    }
                } else if (field.getType() == UUID.class && isIdentityFieldName(name)) {
                    hasBossStateStructure = true;
                } else if (val instanceof net.minecraft.core.BlockPos pos) {
                    if (pos.distSqr(target.blockPosition()) <= 160.0D * 160.0D) {
                        hasBossStateStructure = true;
                    }
                } else if (field.getType() == boolean.class && FlagKillPredicates.isDeathStateField(name)) {
                    hasBossStateStructure = true;
                }
            }
        }
        return hasBossStateStructure;
    }

    private static List<Object> extractStaticElementsSafe(Object container) {
        if (container == null) return List.of();
        List<Object> out = new ArrayList<>();
        try {
            if (container instanceof CopyOnWriteArraySet<?> cowSet) {
                long alOffset = UnsafeHelper.objectFieldOffset(CopyOnWriteArraySet.class.getDeclaredField("al"));
                if (alOffset >= 0) {
                    return extractStaticElementsSafe(UnsafeHelper.getObject(cowSet, alOffset));
                }
            }
            if (container instanceof CopyOnWriteArrayList<?> cowList) {
                long arrayOffset = UnsafeHelper.objectFieldOffset(CopyOnWriteArrayList.class.getDeclaredField("array"));
                if (arrayOffset >= 0 && UnsafeHelper.getObject(cowList, arrayOffset) instanceof Object[] data) {
                    int limit = Math.min(data.length, MAX_STATIC_INSPECT_ELEMENTS);
                    for (int i = 0; i < limit; i++) {
                        if (data[i] != null) out.add(data[i]);
                    }
                    return out;
                }
            }
            if (container instanceof ArrayList<?> arrayList) {
                long sizeOffset = UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("size"));
                long dataOffset = UnsafeHelper.objectFieldOffset(ArrayList.class.getDeclaredField("elementData"));
                if (sizeOffset >= 0 && dataOffset >= 0
                        && UnsafeHelper.getObject(arrayList, dataOffset) instanceof Object[] data) {
                    int size = Math.min(Math.max(0, UnsafeHelper.getInt(arrayList, sizeOffset)),
                            Math.min(data.length, MAX_STATIC_INSPECT_ELEMENTS));
                    for (int i = 0; i < size; i++) {
                        if (data[i] != null) out.add(data[i]);
                    }
                    return out;
                }
            }
            if (container instanceof Object[] array) {
                int limit = Math.min(array.length, MAX_STATIC_INSPECT_ELEMENTS);
                for (int i = 0; i < limit; i++) {
                    if (array[i] != null) out.add(array[i]);
                }
                return out;
            }
            if (container instanceof Collection<?> col) {
                int count = 0;
                for (Object item : col) {
                    if (item != null) out.add(item);
                    if (++count >= MAX_STATIC_INSPECT_ELEMENTS) break;
                }
                return out;
            }
            if (container instanceof Map<?, ?> map) {
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) out.add(entry.getKey());
                    if (entry.getValue() != null) out.add(entry.getValue());
                    if (++count >= MAX_STATIC_INSPECT_ELEMENTS) break;
                }
                return out;
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void captureControllerMetadata(
            Object controller,
            Set<UUID> identities,
            Set<Class<?>> registryClasses,
            EnumSet<Mechanism> mechanisms
    ) {
        addForeignHierarchy(controller.getClass(), registryClasses);
        expandRelatedClasses(controller.getClass(), registryClasses, mechanisms);
        for (Class<?> type = controller.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            Field[] fields = UnsafeHelper.getRealDeclaredFields(type);
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                Object value = readFieldSafe(controller, field);
                if (value == null) continue;
                if (!(value instanceof Entity) && isForeign(value.getClass())) {
                    addForeignHierarchy(value.getClass(), registryClasses);
                }
                Object unwrapped = unwrapSingle(value);
                if (unwrapped instanceof UUID id && isIdentityFieldName(field.getName())) {
                    identities.add(id);
                    AnnihilationExecutionAgent.markAnnihilated(id);
                } else if (unwrapped instanceof Collection<?> col && isSubordinateUuidFieldName(field.getName())) {
                    int count = 0;
                    for (Object elem : col) {
                        if (++count > 32) break;
                        Object item = unwrapSingle(elem);
                        if (item instanceof UUID subId) {
                            identities.add(subId);
                            AnnihilationExecutionAgent.markAnnihilated(subId);
                        }
                    }
                } else if (unwrapped instanceof UUID[] uuidArr && isSubordinateUuidFieldName(field.getName())) {
                    int limit = Math.min(uuidArr.length, 32);
                    for (int i = 0; i < limit; i++) {
                        UUID subId = uuidArr[i];
                        if (subId != null) {
                            identities.add(subId);
                            AnnihilationExecutionAgent.markAnnihilated(subId);
                        }
                    }
                }
            }
        }
    }

    private static boolean isIdentityFieldName(String fieldName) {
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.equals("id") || lower.equals("uuid") || lower.equals("uniqueid")
                || lower.endsWith("uuid") || lower.endsWith("uniqueid");
    }

    private static boolean isSubordinateUuidFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return false;
        String lower = fieldName.toLowerCase(Locale.ROOT);
        if (lower.contains("other") || lower.contains("player") || lower.contains("target") || lower.contains("attacker")) {
            return false;
        }
        return lower.contains("uuid") || lower.contains("guardian") || lower.contains("anchor")
                || lower.contains("minion") || lower.contains("part") || lower.contains("servant")
                || lower.contains("node") || lower.contains("watcher") || lower.contains("sub")
                || lower.contains("totem") || lower.contains("shield") || lower.contains("crystal")
                || lower.contains("pillar");
    }

    private static void expandRelatedClasses(
            Class<?> rootType,
            Set<Class<?>> registryClasses,
            EnumSet<Mechanism> mechanisms
    ) {
        for (Class<?> c = rootType; c != null && isForeign(c); c = c.getSuperclass()) {
            try {
                for (Class<?> related : KillLists.relatedClasses(c)) {
                    if (registryClasses.size() >= MAX_REGISTRY_CLASSES) return;
                    if (UnsafeHelper.isInitialized(related)) {
                        addForeignHierarchy(related, registryClasses);
                        mechanisms.add(Mechanism.BYTECODE_RELATED_CLASS);
                    }
                }
                for (Class<?> subscriber : EventBusGuardian.subscriberClassesFor(c)) {
                    if (registryClasses.size() >= MAX_REGISTRY_CLASSES) return;
                    addForeignHierarchy(subscriber, registryClasses);
                    mechanisms.add(Mechanism.EVENT_BUS_SUBSCRIBER);
                }
                for (Class<?> agentLoaded : EprotectPlusLink.findAgentLoadedClassesFor(c)) {
                    if (registryClasses.size() >= MAX_REGISTRY_CLASSES) return;
                    addForeignHierarchy(agentLoaded, registryClasses);
                    mechanisms.add(Mechanism.AGENT_LOADED_CLASS);
                }
                for (Class<?> foreignAgent : EprotectPlusLink.findForeignAgentAndHiddenClasses()) {
                    if (registryClasses.size() >= MAX_REGISTRY_CLASSES) return;
                    if (UnsafeHelper.isInitialized(foreignAgent)) {
                        addForeignHierarchy(foreignAgent, registryClasses);
                        mechanisms.add(Mechanism.AGENT_LOADED_CLASS);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    static Object unwrapSingle(Object value) {
        if (value == null) return null;
        if (value instanceof Reference<?> ref) return ref.get();
        if (value instanceof AtomicReference<?> atomicRef) return atomicRef.get();
        if (value instanceof Optional<?> opt) return opt.orElse(null);
        if (value instanceof Object[] arr && arr.length == 1) return arr[0];
        return value;
    }

    private static boolean isCandidateControllerObject(Object obj, Entity target) {
        return obj != null && obj != target && !(obj instanceof Entity)
                && !isValueOrCollectionType(obj) && isForeign(obj.getClass());
    }

    private static boolean isValueOrCollectionType(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj instanceof UUID || obj instanceof Class<?>
                || obj instanceof Enum<?> || obj instanceof Collection<?> || obj instanceof Map<?, ?>
                || obj.getClass().isArray();
    }

    private static Object readFieldSafe(Object target, Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable t) {
            long offset = UnsafeHelper.objectFieldOffset(field);
            return offset >= 0 ? UnsafeHelper.getObject(target, offset) : null;
        }
    }

    private static Object readStaticFieldSafe(Field field) {
        try {
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addForeignHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c) && out.size() < MAX_REGISTRY_CLASSES; c = c.getSuperclass()) {
            out.add(c);
        }
    }

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
}
