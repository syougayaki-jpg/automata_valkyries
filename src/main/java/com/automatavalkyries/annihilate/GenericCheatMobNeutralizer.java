package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 汎用チートMOB解体エンジン（Generic Cheat Mob Neutralizer）。
 * <p>
 * Witherzilla、Metapotent Flashfur、および今後出現しうる未知のチートMODボスに共通する
 * 不死構造（プロキシと本体の分離、ProtectedArrayListによる外部検閲、タイマーによる無限復活、
 * 独自生命維持フラグ）を動的に探索し、対象に属する状態だけを解体する。
 * 所有を確認できない永続保存は変更しない。
 * </p>
 */
public final class GenericCheatMobNeutralizer {

    private GenericCheatMobNeutralizer() {}

    /**
     * 任意のターゲット（およびその背後にある本体オブジェクト）に対して汎用解体を執行する。
     *
     * @param level 実行サーバーワールド
     * @param target 対象エンティティ
     * @return 解体されたコントローラー／本体のリスト
     */
    public static List<Object> neutralize(ServerLevel level, Entity target) {
        if (target == null || level == null || target.level() != level
                || !level.getServer().isSameThread()
                || (target instanceof net.minecraft.world.entity.player.Player && !isForeign(target.getClass()))
                || (target instanceof com.automatavalkyries.protect.IEprotected protectedEntity
                    && !protectedEntity.isTerminusTestTarget())) return List.of();

        List<Object> controllers = new ArrayList<>();
        try {
            // 0. 防御構造の探索・調査・把握・解析（本体・隠蔽参照・逆引き static コア・関連クラスを一括把握）
            DefenseStructureAnalyzer.AnalysisReport report = DefenseStructureAnalyzer.analyze(target);

            com.automatavalkyries.protect.plus.EprotectPlusLink.runWithIsolatedTargetHooks(report.registryClasses(), () -> {
                // 0.2. ボスバー・HUD 追跡の事前破棄（メモリ直接ゼロクリア UnsafeHelper.obliterateObjectMemory で
                //      bossEvent 参照が null 化される前に全プレイヤーから切り離して非表示にする）
                AnnihilationExecutionAgent.clearBossEvents(target);

                // 0.5. StateScanner と FlagDictionary による NBT / Interface / Capability の汎用防御解除
                try {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                } catch (Throwable ignored) {}

                // PersistentData 内の防御フラグの辞書クリーンアップ
                try {
                    net.minecraft.nbt.CompoundTag tag = target.getPersistentData();
                    for (String key : new ArrayList<>(tag.getAllKeys())) {
                        if (FlagKillPredicates.isDefensiveField(key)) {
                            tag.remove(key);
                        }
                    }
                } catch (Throwable ignored) {}

                // 1. 解析レポートで把握した本体（直接・ラッパー・内部コンポーネント・static 逆引き）＋動的探索の解体
                Set<Object> seenControllers = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Object analyzedCore : report.controllers()) {
                    if (analyzedCore != null && seenControllers.add(analyzedCore)
                            && ownsController(analyzedCore, target, report.identities(), false)) {
                        controllers.add(analyzedCore);
                        dismantleCoreObject(analyzedCore, target);
                    }
                }
                for (Object fallbackCore : dismantleControllers(target)) {
                    if (fallbackCore != null && seenControllers.add(fallbackCore)) {
                        controllers.add(fallbackCore);
                    }
                }
                for (Object core : controllers) {
                    AnnihilationExecutionAgent.clearBossEvents(core);
                }

                // 2. 独自生命フラグ・無敵フラグの汎用リセット
                resetDefensiveFlags(target);
                for (Object core : controllers) {
                    resetDefensiveFlags(core);
                }

                // 3. 既知および解析レポートで特定された常駐リスト・キュー・単一参照の Unsafe パージ
                deepPurgeStaticLists(target, controllers, report);
            });

            // Unknown save formats do not establish ownership of any disk data.
            // Target-only field/list cleanup deliberately leaves those saves intact.

            AutomataValkyries.LOGGER.info("[GenericNeutralizer] Neutralized meta-cheat structures for {} (found {} core controllers)",
                    target.getName().getString(), controllers.size());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[GenericNeutralizer] Error during generic neutralization for {}", target, t);
        }

        return controllers;
    }

    /**
     * 直接フィールドから、対象への逆参照を持ち別個体を参照しない本体を探索して解体する。
     */
    private static List<Object> dismantleControllers(Entity target) {
        List<Object> controllers = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(target);

        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller == null || visited.contains(controller) || controller instanceof Entity
                            || !isForeign(controller.getClass())
                            || !ownsController(controller, target)) continue;

                    visited.add(controller);
                    controllers.add(controller);

                    // 本体オブジェクトの解体処理
                    dismantleCoreObject(controller, target);
                } catch (Throwable ignored) {}
            }
        }

        return controllers;
    }

    /**
     * 非エンティティ本体オブジェクト（MetapotentFlashfur等）を解体する。
     */
    public static void dismantleCoreObject(Object core, Entity proxy) {
        dismantleCoreObject(core, proxy, true);
    }

    static void resetOwnedCore(Object core, Entity proxy) {
        dismantleCoreObject(core, proxy, false);
    }

    /** Only for cores whose ownership was checked and retained before native removal. */
    static void resetCapturedCore(Object core, Entity proxy) {
        if (core == null || proxy == null || !ownsController(core, proxy, true)) return;
        dismantleCoreObject(core, proxy, false, true);
    }

    private static void dismantleCoreObject(Object core, Entity proxy, boolean terminate) {
        dismantleCoreObject(core, proxy, terminate, false);
    }

    private static void dismantleCoreObject(Object core, Entity proxy, boolean terminate, boolean captured) {
        if (core == null || (proxy != null && !ownsController(core, proxy, captured))) return;
        AnnihilationExecutionAgent.clearBossEvents(core);
        Class<?> clazz = core.getClass();

        try {
            Field idField = UnsafeHelper.findField(clazz, "id");
            if (idField != null && idField.getType() == UUID.class) {
                idField.setAccessible(true);
                UUID id = (UUID) idField.get(core);
                if (id != null) {
                    AnnihilationExecutionAgent.markAnnihilated(id);
                }
            }
        } catch (Throwable ignored) {}

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();

                // 死亡・削除フラグなら true に、防御フラグなら false に
                if (f.getType() == boolean.class) {
                    try {
                        f.setAccessible(true);
                        if (FlagKillPredicates.isDeathStateField(name)) {
                            f.setBoolean(core, true);
                        } else if (FlagKillPredicates.isDefensiveField(name)) {
                            f.setBoolean(core, false);
                        }
                    } catch (Throwable ignored) {}
                }

                // proxy 参照または攻撃対象参照の破棄（ラッパー経由の逆参照・同一 UUID のクライアント対・子エフェクトEntityも含む）
                if (Entity.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object held = f.get(core);
                        if (proxy == null || held == proxy || isCombatTargetField(name)
                                || (held instanceof Entity other && isSameEntityIdentity(other, proxy))
                                || (held instanceof Entity child && isChildEffectEntity(child, core, name))) {
                            if (held instanceof Entity twin && twin != proxy && !isCombatTargetField(name)) {
                                resetDefensiveFlags(twin);
                                if (isChildEffectEntity(twin, core, name)) {
                                    try {
                                        if (twin.level() instanceof ServerLevel sl) {
                                            AnnihilationExecutionAgent.spoofAndDisconnectTracking(sl, twin);
                                        }
                                        twin.discard();
                                    } catch (Throwable ignored) {}
                                }
                            }
                            f.set(core, null);
                        }
                    } catch (Throwable ignored) {}
                } else if (isCombatTargetCollectionField(name) && Collection.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof Collection<?> col) {
                            col.clear();
                        }
                    } catch (Throwable ignored) {}
                } else if (java.lang.ref.Reference.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof java.lang.ref.Reference<?> ref
                                && (proxy == null || ref.get() == proxy || isCombatTargetField(name)
                                || (ref.get() instanceof Entity other && isSameEntityIdentity(other, proxy)))) {
                            if (ref.get() instanceof Entity twin && twin != proxy && !isCombatTargetField(name)) {
                                resetDefensiveFlags(twin);
                            }
                            ref.clear();
                        }
                    } catch (Throwable ignored) {}
                } else if (f.getType() == java.util.concurrent.atomic.AtomicReference.class) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof java.util.concurrent.atomic.AtomicReference<?> atomicRef
                                && (proxy == null || atomicRef.get() == proxy || isCombatTargetField(name)
                                || (atomicRef.get() instanceof Entity other && isSameEntityIdentity(other, proxy)))) {
                            if (atomicRef.get() instanceof Entity twin && twin != proxy && !isCombatTargetField(name)) {
                                resetDefensiveFlags(twin);
                            }
                            atomicRef.set(null);
                        }
                    } catch (Throwable ignored) {}
                } else if (f.getType() == java.util.concurrent.atomic.AtomicBoolean.class) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof java.util.concurrent.atomic.AtomicBoolean ab) {
                            if (FlagKillPredicates.isDeathStateField(name)) ab.set(true);
                            else if (FlagKillPredicates.isDefensiveField(name)) ab.set(false);
                        }
                    } catch (Throwable ignored) {}
                } else if (f.getType() == java.util.concurrent.atomic.AtomicInteger.class) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof java.util.concurrent.atomic.AtomicInteger ai) {
                            if (FlagKillPredicates.livesCounter(name)) ai.set(0);
                            else if (FlagDictionary.is(name, FlagDictionary.Category.TIMER)) ai.set(-9999);
                        }
                    } catch (Throwable ignored) {}
                }

                // タイマー系フィールドの凍結（負数化）
                if (FlagDictionary.is(name, FlagDictionary.Category.TIMER)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.set(core, -9999);
                        } catch (Throwable ignored) {}
                    } else if (f.getType() == long.class || f.getType() == Long.class) {
                        try {
                            f.setAccessible(true);
                            f.set(core, -9999L);
                        } catch (Throwable ignored) {}
                    }
                }

                // 残機カウンターのゼロ化
                if (FlagKillPredicates.livesCounter(name)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.set(core, 0);
                        } catch (Throwable ignored) {}
                    }
                }

                // HP デルタやシールド値（GUARD または HEALTH_TAMPER）のゼロ化
                if ((f.getType() == float.class || f.getType() == Float.class || f.getType() == double.class || f.getType() == Double.class)
                        && (FlagDictionary.is(name, FlagDictionary.Category.HEALTH_TAMPER) || FlagDictionary.is(name, FlagDictionary.Category.GUARD)
                        || name.toLowerCase(Locale.ROOT).contains("delta") || name.toLowerCase(Locale.ROOT).contains("shield"))) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == float.class || f.getType() == Float.class) {
                            f.set(core, 0.0F);
                        } else {
                            f.set(core, 0.0D);
                        }
                    } catch (Throwable ignored) {}
                }

                // 弱参照・ソフト参照（WeakReference / SoftReference）の強制 clear & enqueue（疑似 GC 追放）
                if (java.lang.ref.Reference.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(core) instanceof java.lang.ref.Reference<?> ref) {
                            ref.clear();
                            ref.enqueue();
                        }
                    } catch (Throwable ignored) {}
                }

                // Collection / Map のクリア
                if (FlagKillPredicates.isDefensiveField(name)) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(core);
                        removeTargetReferences(val, proxy);
                    } catch (Throwable ignored) {}
                }

                // ServerBossEvent の非表示
                if (ServerBossEvent.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        ServerBossEvent be = (ServerBossEvent) f.get(core);
                        if (be != null) {
                            be.removeAllPlayers();
                            be.setVisible(false);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 終了メソッドの呼び出し（FlagKillPredicates.isTerminationMethod による動的判定。自爆トラップ付きクラスはスキップ）
        if (!terminate || com.automatavalkyries.protect.plus.EprotectPlusLink.hasHaltOrExitTrap(clazz)) return;
        boolean done = false;
        for (Method m : clazz.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                    && !ModProtectionBreaker.isTrapTerminationName(m.getName())
                    && FlagKillPredicates.isTerminationMethod(m.getName())) {
                try {
                    m.setAccessible(true);
                    m.invoke(core);
                    AutomataValkyries.LOGGER.info("[GenericNeutralizer] Invoked termination method {}.{}()",
                            clazz.getSimpleName(), m.getName());
                    done = true;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (done) return;
        // 引数 1 個の終了メソッド（forceRemove(boolean) 等）: true を渡して呼ぶ
        for (Method m : clazz.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != boolean.class
                    || ModProtectionBreaker.isTrapTerminationName(m.getName())
                    || !FlagKillPredicates.isTerminationMethod(m.getName())) continue;
            try {
                m.setAccessible(true);
                m.invoke(core, true);
                AutomataValkyries.LOGGER.info("[GenericNeutralizer] Invoked termination method {}.{}(true)",
                        clazz.getSimpleName(), m.getName());
                break;
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 独自生命維持フラグ・無敵フラグを探索してリセットする。
     */
    private static void resetDefensiveFlags(Object target) {
        if (target == null) return;
        Class<?> clazz = target.getClass();

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();
                if (f.getType().isInterface() && isForeign(f.getType())) {
                    ModProtectionBreaker.neutralizeForeignFunctionalField(target, c, f);
                }

                // boolean フラグの無効化（死亡フラグは true、防御フラグは false）
                if (f.getType() == boolean.class) {
                    try {
                        f.setAccessible(true);
                        if (FlagKillPredicates.isDeathStateField(name)) {
                            f.setBoolean(target, true);
                        } else if (FlagKillPredicates.isDefensiveField(name)) {
                            f.setBoolean(target, false);
                        }
                    } catch (Throwable ignored) {}
                }

                // タイマー系フィールドの凍結
                if (FlagDictionary.is(name, FlagDictionary.Category.TIMER)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.set(target, -9999);
                        } catch (Throwable ignored) {}
                    }
                }

                // 残機カウンターのゼロ化
                if (FlagKillPredicates.livesCounter(name)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.set(target, 0);
                        } catch (Throwable ignored) {}
                    }
                }

                // float / double HP デルタやシールド値のゼロ化
                if ((f.getType() == float.class || f.getType() == Float.class || f.getType() == double.class || f.getType() == Double.class)
                        && (FlagDictionary.is(name, FlagDictionary.Category.HEALTH_TAMPER) || FlagDictionary.is(name, FlagDictionary.Category.GUARD)
                        || name.toLowerCase(Locale.ROOT).contains("delta") || name.toLowerCase(Locale.ROOT).contains("shield"))) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == float.class || f.getType() == Float.class) {
                            f.set(target, 0.0F);
                        } else {
                            f.set(target, 0.0D);
                        }
                    } catch (Throwable ignored) {}
                }

                // リスポーン座標フィールド（IRespawningEntity.oldPos 等）の null 化
                if (target instanceof Entity && isForeign(target.getClass())
                        && f.getType() == net.minecraft.world.phys.Vec3.class) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (lower.equals("oldpos") || lower.equals("respawnpos") || lower.equals("spawnpos")
                            || lower.equals("savedpos") || lower.equals("lastpos")) {
                        try {
                            f.setAccessible(true);
                            f.set(target, null);
                        } catch (Throwable ignored) {}
                    }
                }

                // 弱参照・ソフト参照（WeakReference / SoftReference）の強制 clear & enqueue（疑似 GC 追放）
                if (java.lang.ref.Reference.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.get(target) instanceof java.lang.ref.Reference<?> ref) {
                            ref.clear();
                            ref.enqueue();
                        }
                    } catch (Throwable ignored) {}
                }

                // Collection / Map のクリア
                if (FlagKillPredicates.isDefensiveField(name)) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(target);
                        if (target instanceof Entity entity) removeTargetReferences(val, entity);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    /**
     * static な Collection / Map（ProtectedArrayList 等）からターゲットおよび本体参照を強制除去する。
     */
    private static void deepPurgeStaticLists(Entity target, List<Object> controllers, DefenseStructureAnalyzer.AnalysisReport report) {
        Set<Class<?>> targetClasses = new LinkedHashSet<>();
        if (report != null) {
            targetClasses.addAll(report.registryClasses());
        }
        addClassHierarchy(target.getClass(), targetClasses);
        addRelatedHierarchy(target.getClass(), targetClasses);
        for (Object ctrl : controllers) {
            addClassHierarchy(ctrl.getClass(), targetClasses);
            addRelatedHierarchy(ctrl.getClass(), targetClasses);
        }
        try {
            if (com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
                for (String cn : new String[]{
                        "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel",
                        "flashfur.omnimobs.entities.flashfur.powers.Respawning",
                        "flashfur.omnimobs.entities.base.RemoveDeadEntities",
                        "flashfur.omnimobs.entities.anticheat.HealthManager",
                        "flashfur.omnimobs.entities.anticheat.HealthManager$LastGoodHealth",
                        "flashfur.omnimobs.util.EntityUtil"
                }) {
                    try {
                        targetClasses.add(Class.forName(cn, false, target.getClass().getClassLoader()));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        victims.addAll(controllers);
        UUID uuid = MadnessAnnihilation.uuid(target);
        Set<UUID> identities = new LinkedHashSet<>();
        if (uuid != null) identities.add(uuid);
        if (report != null) identities.addAll(report.identities());
        int rawId = report != null ? report.rawId() : MadnessAnnihilation.id(target);
        for (UUID identity : identities) {
            com.automatavalkyries.util.StaticReferencePurger.purge(
                    targetClasses, victims, identity, rawId, target.getName().getString());
        }

        for (Class<?> clazz : targetClasses) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(clazz)) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object list = field.get(null);
                    if (list == null) continue;

                    // ArrayList（ProtectedArrayList）および CopyOnWriteArrayList（C848734 等）の Unsafe 強制除去
                    if (list instanceof ArrayList<?>) {
                        purgeArrayList(list, victims, uuid);
                    } else if (list instanceof java.util.concurrent.CopyOnWriteArrayList<?>
                            || list instanceof java.util.concurrent.CopyOnWriteArraySet<?>) {
                        UnsafeHelper.removeFromArrayList(list, elem -> matchesVictim(elem, victims, uuid));
                    } else if (list instanceof Collection<?> col) {
                        col.removeIf(elem -> matchesVictim(elem, victims, uuid));
                    } else if (list instanceof Map<?, ?> map) {
                        String mapName = (clazz.getSimpleName() + "." + field.getName()).toLowerCase(Locale.ROOT);
                        if (mapName.contains("health")) {
                            // HealthManager.healthValues 等は getOrDefault(boss, getMaxHealth()) で未登録時に最大HPへ戻るため、
                            // エントリ自体は残して Entry.setValue(0.0F) で直接 0 に上書きする（ProtectedWeakHashMap.put 拒否も回避）
                            zeroHealthMapEntries(map, victims, uuid);
                        } else {
                            map.entrySet().removeIf(e -> matchesVictim(e.getKey(), victims, uuid) || matchesVictim(e.getValue(), victims, uuid));
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void zeroHealthMapEntries(Map<?, ?> map, Set<Object> victims, UUID uuid) {
        if (map == null) return;
        try {
            for (Map.Entry entry : map.entrySet()) {
                if (entry == null) continue;
                if (matchesVictim(entry.getKey(), victims, uuid)) {
                    Object val = entry.getValue();
                    if (val instanceof Float) {
                        entry.setValue(0.0F);
                    } else if (val instanceof Double) {
                        entry.setValue(0.0D);
                    } else if (val instanceof Integer) {
                        entry.setValue(0);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void purgeArrayList(Object list, Set<Object> victims, UUID uuid) {
        try {
            Field sizeField = UnsafeHelper.findField(ArrayList.class, "size");
            long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
            Field dataField = UnsafeHelper.findField(ArrayList.class, "elementData");
            long dataOffset = UnsafeHelper.objectFieldOffset(dataField);

            if (sizeOffset < 0 || dataOffset < 0) return;

            Object[] data = (Object[]) UnsafeHelper.getObject(list, dataOffset);
            int size = UnsafeHelper.getInt(list, sizeOffset);
            if (data == null || size <= 0 || size > data.length) return;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                Object elem = data[i];
                if (elem == null) continue;
                if (matchesVictim(elem, victims, uuid)) {
                    data[i] = null;
                } else {
                    data[newSize++] = elem;
                }
            }
            for (int i = newSize; i < size; i++) {
                data[i] = null;
            }
            if (newSize != size) {
                UnsafeHelper.putInt(list, sizeOffset, newSize);
                UnsafeHelper.incrementArrayListModCount(list);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 範囲内の非エンティティ独立コントローラー（プロキシが消失・未紐付けの MetapotentFlashfur 等）を探索して解体する。
     */
    public static int sweepStandaloneControllers(ServerLevel level, Entity attacker, double radius) {
        return sweepStandaloneControllers(level, attacker, radius, true);
    }

    public static int sweepStandaloneControllers(ServerLevel level, Entity attacker, double radius, boolean includeLiveProxies) {
        if (level == null || attacker == null || !Double.isFinite(radius) || radius < 0) return 0;
        net.minecraft.world.phys.AABB box = attacker.getBoundingBox().inflate(radius);
        Set<Class<?>> holderClasses = new LinkedHashSet<>();
        try {
            if (com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
                Class<?> mfl = Class.forName(
                        "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel",
                        false, GenericCheatMobNeutralizer.class.getClassLoader());
                if (UnsafeHelper.isInitialized(mfl)) {
                    holderClasses.add(mfl);
                }
            }
        } catch (Throwable ignored) {}

        int dismantledCount = 0;
        Set<Object> dismantledCores = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Entity> proxiesToAnnihilate = new ArrayList<>();

        for (Class<?> holderClass : holderClasses) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(holderClass)) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object container = field.get(null);
                    if (!(container instanceof ArrayList<?> list)) continue;

                    Field sizeField = UnsafeHelper.findField(ArrayList.class, "size");
                    Field dataField = UnsafeHelper.findField(ArrayList.class, "elementData");
                    long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
                    long dataOffset = UnsafeHelper.objectFieldOffset(dataField);
                    if (sizeOffset < 0 || dataOffset < 0) continue;

                    Object[] data = (Object[]) UnsafeHelper.getObject(list, dataOffset);
                    int size = UnsafeHelper.getInt(list, sizeOffset);
                    if (data == null || size <= 0 || size > data.length) continue;

                    for (int i = 0; i < size; i++) {
                        Object core = data[i];
                        if (core == null || core instanceof Entity || !isForeign(core.getClass())) continue;
                        Entity proxy = findOwnedProxyEntity(core);
                        if (!includeLiveProxies && proxy != null
                                && !com.automatavalkyries.protect.DirectRead.isRemoved(proxy)
                                && level.getEntity(proxy.getUUID()) == proxy) {
                            continue;
                        }
                        if (!isControllerInArea(core, proxy, level, box)) continue;
                        if (!ownsController(core, proxy, null, true)) continue;

                        dismantledCores.add(core);
                        dismantleCoreObject(core, proxy, true, true);
                        dismantledCount++;
                        if (proxy != null && includeLiveProxies) {
                            proxiesToAnnihilate.add(proxy);
                        }
                    }

                    if (!dismantledCores.isEmpty()) {
                        purgeArrayList(list, dismantledCores, null);
                    }
                    // 万一プロキシが先に抹消され removed=true 等になったままリストに残っている残骸も同じパスで掃除
                    purgeRemovedOrAnnihilatedCores(list);
                } catch (Throwable ignored) {}
            }
        }

        for (Entity proxy : proxiesToAnnihilate) {
            try {
                if (proxy.level() == level) {
                    TrueAnnihilationEngine.annihilateEntity(
                            level,
                            attacker instanceof net.minecraft.world.entity.player.Player p ? p : null,
                            proxy);
                }
            } catch (Throwable ignored) {}
        }

        if (dismantledCount > 0) {
            AnnihilationExecutionAgent.purgeDeadFlashfurs();
            AnnihilationExecutionAgent.sanitizeEventBus();
        }
        return dismantledCount;
    }

    private static Entity findOwnedProxyEntity(Object core) {
        for (Class<?> c = core.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || !Entity.class.isAssignableFrom(f.getType())) continue;
                String name = f.getName();
                if (isCombatTargetField(name)) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(core);
                    if (val instanceof Entity e && !isChildEffectEntity(e, core, name)) {
                        return e;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static boolean isControllerInArea(Object core, Entity proxy, ServerLevel level, net.minecraft.world.phys.AABB box) {
        if (proxy != null && proxy.level() == level) {
            if (box.intersects(proxy.getBoundingBox())) return true;
            try {
                net.minecraft.world.phys.Vec3 pPos = proxy.position();
                if (pPos != null && box.contains(pPos)) return true;
            } catch (Throwable ignored) {}
        }
        boolean levelMatches = proxy != null && proxy.level() == level;
        boolean posMatches = false;
        for (Class<?> c = core.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(core);
                    if (val instanceof net.minecraft.world.level.Level lvl) {
                        if (lvl != level) return false;
                        levelMatches = true;
                    } else if (val instanceof net.minecraft.world.phys.Vec3 vec) {
                        if (box.contains(vec)) posMatches = true;
                    } else if (val instanceof net.minecraft.world.phys.AABB aabb) {
                        if (box.intersects(aabb)) posMatches = true;
                    } else if (val instanceof Entity targetEntity && isCombatTargetField(f.getName())) {
                        // 本体自身が遠方に退避していても、範囲内の対象を直接攻撃目標にしている場合は範囲内として扱う
                        if (targetEntity.level() == level && box.intersects(targetEntity.getBoundingBox())) {
                            posMatches = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return levelMatches && posMatches;
    }

    private static void purgeRemovedOrAnnihilatedCores(Object list) {
        try {
            Field sizeField = UnsafeHelper.findField(ArrayList.class, "size");
            Field dataField = UnsafeHelper.findField(ArrayList.class, "elementData");
            long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
            long dataOffset = UnsafeHelper.objectFieldOffset(dataField);
            if (sizeOffset < 0 || dataOffset < 0) return;

            Object[] data = (Object[]) UnsafeHelper.getObject(list, dataOffset);
            int size = UnsafeHelper.getInt(list, sizeOffset);
            if (data == null || size <= 0 || size > data.length) return;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                Object core = data[i];
                boolean drop = (core == null);
                if (!drop && !(core instanceof Entity) && isForeign(core.getClass())) {
                    try {
                        Field rf = UnsafeHelper.findField(core.getClass(), "removed");
                        if (rf != null && rf.getType() == boolean.class && rf.getBoolean(core)) drop = true;
                        Field idf = UnsafeHelper.findField(core.getClass(), "id");
                        if (!drop && idf != null && idf.getType() == UUID.class) {
                            UUID id = (UUID) idf.get(core);
                            if (id != null && AnnihilationExecutionAgent.isAnnihilated(id)) drop = true;
                        }
                    } catch (Throwable ignored) {}
                }
                if (drop) {
                    data[i] = null;
                } else {
                    data[newSize++] = core;
                }
            }
            for (int i = newSize; i < size; i++) {
                data[i] = null;
            }
            if (newSize != size) {
                UnsafeHelper.putInt(list, sizeOffset, newSize);
                UnsafeHelper.incrementArrayListModCount(list);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean matchesVictim(Object elem, Set<Object> victims, UUID uuid) {
        if (elem == null) return false;
        if (victims.contains(elem) || (uuid != null && uuid.equals(elem))) return true;
        if (elem instanceof java.lang.ref.Reference<?> ref) {
            return victims.contains(ref.get());
        }
        return false;
    }

    /**
     * コントローラーやエンティティが一時的に保持する「攻撃対象（ターゲット）」フィールド名か。
     * ボスのコントローラーが {@code target = nearestPlayer} のように戦闘対象を保持している場合でも、
     * 別のボスの共有コントローラーと誤認しないために除外する。
     */
    public static boolean isCombatTargetField(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return false;
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.equals("target") || lower.equals("attacktarget") || lower.equals("currenttarget")
                || lower.equals("oldtarget") || lower.equals("prevtarget") || lower.equals("lasttarget")
                || lower.startsWith("mattacking") || lower.equals("lasthurtbymob")
                || lower.equals("lasthurtbyplayer") || lower.equals("hurtbytarget")
                || lower.equals("aggrotarget") || lower.equals("revengetarget")
                || lower.equals("entitiestoattack") || lower.equals("targetstoattack")
                || lower.equals("attacktargets");
    }

    public static boolean isCombatTargetCollectionField(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return false;
        String lower = fieldName.toLowerCase(Locale.ROOT);
        return lower.equals("entitiestoattack") || lower.equals("targetstoattack")
                || lower.equals("attacktargets") || lower.equals("hurtentities")
                || lower.equals("hitentities") || lower.equals("damagedentities");
    }

    private static boolean isChildEffectEntity(Entity entity, Object core, String fieldName) {
        if (entity == null || core == null) return false;
        if (entity instanceof net.minecraft.world.entity.LivingEntity
                || entity instanceof net.minecraftforge.entity.PartEntity<?>) return false;
        if (fieldName != null && fieldName.toLowerCase(Locale.ROOT).contains("other")) return false;
        String coreMod = CallerAttribution.modOf(core.getClass());
        String entityMod = CallerAttribution.modOf(entity.getClass());
        return CallerAttribution.isForeign(coreMod) && Objects.equals(coreMod, entityMod);
    }

    /** Only an exclusive direct owner may be shut down. Unreadable ownership is rejected. */
    static boolean ownsController(Object core, Entity target) {
        return ownsController(core, target, null, false);
    }

    static boolean ownsController(Object core, Entity target, boolean allowDetached) {
        return ownsController(core, target, null, allowDetached);
    }

    static boolean ownsController(Object core, Entity target, Set<UUID> targetIdentities, boolean allowDetached) {
        if (core == null || core == target || core instanceof Entity) return false;
        boolean pointsBack = false;
        UUID rawTargetUuid = target != null ? MadnessAnnihilation.uuid(target) : null;
        UUID reportedTargetUuid = target != null ? target.getUUID() : null;
        for (Class<?> c = core.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(core);
                    if (isCombatTargetCollectionField(field.getName()) && value instanceof Collection<?>) {
                        continue;
                    }
                    boolean combatTargetField = isCombatTargetField(field.getName())
                            && !(value instanceof Collection<?>)
                            && !(value instanceof Map<?, ?>)
                            && !(value instanceof Object[]);
                    if (combatTargetField) {
                        continue;
                    }
                    if (value == target || (value instanceof Entity e && isSameEntityIdentity(e, target))) pointsBack = true;
                    if (containsOtherEntity(value, target, core, field.getName())) return false;
                    Object unwrapped = DefenseStructureAnalyzer.unwrapSingle(value);
                    if (unwrapped == target || (unwrapped instanceof Entity e && isSameEntityIdentity(e, target))) pointsBack = true;
                    if (unwrapped != value && containsOtherEntity(unwrapped, target, core, field.getName())) return false;
                    if (unwrapped instanceof UUID id) {
                        String lower = field.getName().toLowerCase(Locale.ROOT);
                        boolean matchesTarget = (rawTargetUuid != null && id.equals(rawTargetUuid))
                                || (reportedTargetUuid != null && id.equals(reportedTargetUuid))
                                || (targetIdentities != null && targetIdentities.contains(id));
                        if (matchesTarget) {
                            pointsBack = true;
                        } else if (lower.contains("other")) {
                            return false;
                        }
                    }
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }
        return pointsBack || allowDetached;
    }

    static boolean containsOtherEntityObject(Object holder, Entity target) {
        if (holder == null || holder == target) return false;
        if (containsOtherEntity(holder, target, holder, null)) return true;
        for (Class<?> c = holder.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (containsOtherEntity(value, target, holder, field.getName())) return true;
                    Object unwrapped = DefenseStructureAnalyzer.unwrapSingle(value);
                    if (unwrapped != value && containsOtherEntity(unwrapped, target, holder, field.getName())) return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static boolean isSameEntityIdentity(Entity a, Entity b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            UUID ua = MadnessAnnihilation.uuid(a);
            UUID ub = MadnessAnnihilation.uuid(b);
            if (ua != null && ua.equals(ub)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean containsOtherEntity(Object value, Entity target) {
        return containsOtherEntity(value, target, null, null);
    }

    private static boolean containsOtherEntity(Object value, Entity target, Object core, String fieldName) {
        if (value instanceof Entity entity) {
            if (isSameEntityIdentity(entity, target)) return false;
            if (isChildEffectEntity(entity, core, fieldName)) return false;
            return true;
        }
        if (value instanceof java.lang.ref.Reference<?> ref) return containsOtherEntity(ref.get(), target, core, fieldName);
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element instanceof Entity entity && !isSameEntityIdentity(entity, target)
                        && !isChildEffectEntity(entity, core, fieldName)) return true;
                if (element instanceof java.lang.ref.Reference<?> ref && ref.get() instanceof Entity entity
                        && !isSameEntityIdentity(entity, target)
                        && !isChildEffectEntity(entity, core, fieldName)) return true;
            }
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof Entity entity && !isSameEntityIdentity(entity, target)) return true;
                if (entry.getValue() instanceof Entity entity && !isSameEntityIdentity(entity, target)) return true;
            }
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (element instanceof Entity entity && !isSameEntityIdentity(entity, target)
                        && !isChildEffectEntity(entity, core, fieldName)) return true;
            }
        }
        return false;
    }

    private static void removeTargetReferences(Object value, Entity target) {
        // A null proxy is an explicit request to dismantle a supplied standalone core.
        if (target == null) {
            if (value instanceof Collection<?> collection) collection.clear();
            if (value instanceof Map<?, ?> map) map.clear();
            return;
        }
        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        if (value instanceof Collection<?> collection) {
            collection.removeIf(element -> matchesVictim(element, victims, target.getUUID()));
        } else if (value instanceof Map<?, ?> map) {
            map.entrySet().removeIf(entry -> matchesVictim(entry.getKey(), victims, target.getUUID())
                    || matchesVictim(entry.getValue(), victims, target.getUUID()));
        }
    }

    private static void addClassHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            out.add(c);
        }
    }

    private static void addRelatedHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            try {
                for (Class<?> related : com.automatavalkyries.protect.flag.KillLists.relatedClasses(c)) {
                    if (out.size() >= 128) return;
                    if (UnsafeHelper.isInitialized(related)) addClassHierarchy(related, out);
                }
                for (Class<?> subscriber : com.automatavalkyries.protect.EventBusGuardian.subscriberClassesFor(c)) {
                    if (out.size() >= 128) return;
                    addClassHierarchy(subscriber, out);
                }
                for (Class<?> agentLoaded : com.automatavalkyries.protect.plus.EprotectPlusLink.findAgentLoadedClassesFor(c)) {
                    if (out.size() >= 128) return;
                    addClassHierarchy(agentLoaded, out);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }
}
