package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 汎用チートMOB解体エンジン（Generic Cheat Mob Neutralizer）。
 * <p>
 * Witherzilla、Metapotent Flashfur、および今後出現しうる未知のチートMODボスに共通する
 * 不死構造（プロキシと本体の分離、ProtectedArrayListによる外部検閲、タイマーによる無限復活、
 * 独自生命維持フラグ、ディスクへの永続化ファイル保存）を動的に探索・特定し、
 * JVM / リフレクション / Unsafe レベルで包括的に解体・無力化する。
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
        if (target == null) return List.of();

        List<Object> controllers = new ArrayList<>();
        try {
            // 0. StateScanner と FlagDictionary による NBT / Interface / Capability の汎用防御解除
            try {
                StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
            } catch (Throwable ignored) {}

            // PersistentData 内の防御フラグの辞書クリーンアップ
            try {
                net.minecraft.nbt.CompoundTag tag = target.getPersistentData();
                for (String key : new ArrayList<>(tag.getAllKeys())) {
                    if (FlagDictionary.isDefensiveField(key)) {
                        tag.remove(key);
                    }
                }
            } catch (Throwable ignored) {}

            // 1. プロキシから本体（Controller / Core / Parent / Boss）の動的探索と解体
            controllers.addAll(dismantleControllers(target));

            // 2. 独自生命フラグ・無敵フラグの汎用リセット
            resetDefensiveFlags(target);
            for (Object core : controllers) {
                resetDefensiveFlags(core);
            }

            // 3. 既知および動的特定された常駐リスト・キューの Unsafe パージ
            deepPurgeStaticLists(target, controllers);

            // 4. ディスク上の永続保存ファイル（.omnimob, .cheat, 独自フォルダ等）の走査と完全削除
            purgeModSaveFiles(level, target);

            // 5. 乗っ取られた Forge EventBus の正常化
            AnnihilationExecutionAgent.sanitizeEventBus();

            AutomataValkyries.LOGGER.info("[GenericNeutralizer] Neutralized meta-cheat structures for {} (found {} core controllers)",
                    target.getName().getString(), controllers.size());
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[GenericNeutralizer] Error during generic neutralization for {}", target, t);
        }

        return controllers;
    }

    /**
     * エンティティのフィールドから、他 MOD のオブジェクト（本体・コントローラー・管理体）を再帰探索して解体する。
     */
    private static List<Object> dismantleControllers(Entity target) {
        List<Object> controllers = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(target);

        for (Class<?> type = target.getClass(); type != null && isForeign(type); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    Object controller = field.get(target);
                    if (controller == null || visited.contains(controller) || controller instanceof Entity
                            || !isForeign(controller.getClass())) continue;

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
        if (core == null) return;
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
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();

                // 死亡・削除フラグなら true に、防御フラグなら false に
                if (f.getType() == boolean.class) {
                    try {
                        f.setAccessible(true);
                        if (FlagDictionary.isDeathStateField(name)) {
                            f.setBoolean(core, true);
                        } else if (FlagDictionary.isDefensiveField(name)) {
                            f.setBoolean(core, false);
                        }
                    } catch (Throwable ignored) {}
                }

                // proxy 参照の破棄
                if (Entity.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (proxy == null || f.get(core) == proxy) {
                            f.set(core, null);
                        }
                    } catch (Throwable ignored) {}
                }

                // タイマー系フィールドの凍結（負数化）
                if (FlagDictionary.is(name, FlagDictionary.Category.TIMER)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.setInt(core, -9999);
                        } catch (Throwable ignored) {}
                    } else if (f.getType() == long.class || f.getType() == Long.class) {
                        try {
                            f.setAccessible(true);
                            f.setLong(core, -9999L);
                        } catch (Throwable ignored) {}
                    }
                }

                // 残機カウンターのゼロ化
                if (FlagDictionary.livesCounter(name)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.setInt(core, 0);
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
                            f.setFloat(core, 0.0F);
                        } else {
                            f.setDouble(core, 0.0D);
                        }
                    } catch (Throwable ignored) {}
                }

                // Collection / Map のクリア
                if (FlagDictionary.isDefensiveField(name)) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(core);
                        if (val instanceof Collection<?> col) col.clear();
                        else if (val instanceof Map<?, ?> map) map.clear();
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

        // 終了メソッドの呼び出し（FlagDictionary.isTerminationMethod による動的判定）
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == 0 && FlagDictionary.isTerminationMethod(m.getName())) {
                try {
                    m.setAccessible(true);
                    m.invoke(core);
                    AutomataValkyries.LOGGER.info("[GenericNeutralizer] Invoked termination method {}.{}()",
                            clazz.getSimpleName(), m.getName());
                    break;
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * 独自生命維持フラグ・無敵フラグを探索してリセットする。
     */
    private static void resetDefensiveFlags(Object target) {
        if (target == null) return;
        Class<?> clazz = target.getClass();

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();

                // boolean フラグの無効化（死亡フラグは true、防御フラグは false）
                if (f.getType() == boolean.class) {
                    try {
                        f.setAccessible(true);
                        if (FlagDictionary.isDeathStateField(name)) {
                            f.setBoolean(target, true);
                        } else if (FlagDictionary.isDefensiveField(name)) {
                            f.setBoolean(target, false);
                        }
                    } catch (Throwable ignored) {}
                }

                // タイマー系フィールドの凍結
                if (FlagDictionary.is(name, FlagDictionary.Category.TIMER)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.setInt(target, -9999);
                        } catch (Throwable ignored) {}
                    }
                }

                // 残機カウンターのゼロ化
                if (FlagDictionary.livesCounter(name)) {
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        try {
                            f.setAccessible(true);
                            f.setInt(target, 0);
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
                            f.setFloat(target, 0.0F);
                        } else {
                            f.setDouble(target, 0.0D);
                        }
                    } catch (Throwable ignored) {}
                }

                // Collection / Map のクリア
                if (FlagDictionary.isDefensiveField(name)) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(target);
                        if (val instanceof Collection<?> col) col.clear();
                        else if (val instanceof Map<?, ?> map) map.clear();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    /**
     * static な Collection / Map（ProtectedArrayList 等）からターゲットおよび本体参照を強制除去する。
     */
    private static void deepPurgeStaticLists(Entity target, List<Object> controllers) {
        Set<Class<?>> targetClasses = new LinkedHashSet<>();
        addClassHierarchy(target.getClass(), targetClasses);
        for (Object ctrl : controllers) {
            addClassHierarchy(ctrl.getClass(), targetClasses);
        }
        try {
            Class<?> mfl = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            targetClasses.add(mfl);
        } catch (Throwable ignored) {}

        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        victims.addAll(controllers);
        UUID uuid = target.getUUID();

        for (Class<?> clazz : targetClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object list = field.get(null);
                    if (list == null) continue;

                    // ArrayList（および ProtectedArrayList）の Unsafe 強制除去
                    if (list instanceof ArrayList<?>) {
                        purgeArrayList(list, victims, uuid);
                    } else if (list instanceof Collection<?> col) {
                        col.removeIf(elem -> matchesVictim(elem, victims, uuid));
                    } else if (list instanceof Map<?, ?> map) {
                        map.entrySet().removeIf(e -> matchesVictim(e.getKey(), victims, uuid) || matchesVictim(e.getValue(), victims, uuid));
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void purgeArrayList(Object list, Set<Object> victims, UUID uuid) {
        try {
            Field sizeField = ArrayList.class.getDeclaredField("size");
            long sizeOffset = UnsafeHelper.objectFieldOffset(sizeField);
            Field dataField = ArrayList.class.getDeclaredField("elementData");
            long dataOffset = UnsafeHelper.objectFieldOffset(dataField);

            if (sizeOffset < 0 || dataOffset < 0) return;

            Object[] data = (Object[]) UnsafeHelper.getObject(list, dataOffset);
            int size = UnsafeHelper.getInt(list, sizeOffset);
            if (data == null || size == 0) return;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                Object elem = data[i];
                if (elem == null) continue;
                if (matchesVictim(elem, victims, uuid)) {
                    dismantleCoreObject(elem, null);
                    data[i] = null;
                } else {
                    data[newSize++] = elem;
                }
            }
            for (int i = newSize; i < size; i++) {
                data[i] = null;
            }
            UnsafeHelper.putInt(list, sizeOffset, newSize);
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
     * ディスク上に保存された MOD 独自のボス永続化セーブファイルを安全に走査して完全削除する。
     */
    private static void purgeModSaveFiles(ServerLevel level, Entity target) {
        try {
            Path root = level.getServer().getWorldPath(LevelResource.ROOT);
            String targetMod = CallerAttribution.modOf(target.getClass());

            // 1. 既知のチートボス永続フォルダ
            String[] knownDirs = {"metapotent_flashfurs", "omnimobs", "witherzilla"};
            for (String dirName : knownDirs) {
                Path dir = root.resolve(dirName);
                if (Files.isDirectory(dir)) {
                    deleteDirectoryRecursively(dir);
                }
            }

            // 2. MOD 名に一致するサブディレクトリの走査
            if (CallerAttribution.isForeign(targetMod) && !targetMod.isBlank()) {
                Path modDir = root.resolve(targetMod);
                if (Files.isDirectory(modDir)) {
                    deleteDirectoryRecursively(modDir);
                }
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[GenericNeutralizer] Error while deleting save files for {}", target, t);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Throwable ignored) {}
                    });
        } catch (Throwable ignored) {}
    }

    private static void addClassHierarchy(Class<?> type, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && isForeign(c); c = c.getSuperclass()) {
            out.add(c);
        }
    }

    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }
}
