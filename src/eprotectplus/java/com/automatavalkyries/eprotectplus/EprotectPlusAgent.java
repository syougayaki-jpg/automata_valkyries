package com.automatavalkyries.eprotectplus;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eprotect+（イープロテクト・プラス）高度防御・調査・迂回・真抹殺 Java エージェント。
 * <p>
 * Witherzilla、ACE、Omni-Mobs（Metapotent Flashfur）、No-Sugar 等の強力なチート/即死/干渉 MOD を解析・対抗する。
 * <ul>
 *   <li><b>調査 (Investigation)</b>: 他 MOD の ClassFileTransformer、再定義・再変換、スタック追跡、偽装フックを監視・解析して記録。</li>
 *   <li><b>迂回 (Bypass)</b>: No-Sugar 等による getHealth / isAlive 偽装、Flashfur 等の StackChecker 発信元検閲を特権エージェント空間から迂回。</li>
 *   <li><b>保護 (Protection)</b>: eprotect 対象のバイトコード改ざん（メソッド空化・例外注入）を検知し、未改ざんバイトコードへ即時復元。</li>
 *   <li><b>真抹殺 (True Annihilation)</b>: 試験的な Witherzilla の生命維持フラグ（witherzillaLife）強制剥奪、Flashfur の healthDelta / StackChecker 解除、不可逆的消滅の執行。</li>
 * </ul>
 * </p>
 */
public final class EprotectPlusAgent {

    private static Instrumentation instrumentation;

    /** 破壊耐性のために保持する未改ざんバイトコード */
    private static final Map<String, byte[]> BASELINE_BYTECODE = new ConcurrentHashMap<>();
    private static final Map<String, ClassSummary> BASELINES = new ConcurrentHashMap<>();

    /** 監視対象の重要パッケージ・クラス prefix */
    private static final String[] CRITICAL_PREFIXES = {
            "com/automatavalkyries/protect/",
            "com/automatavalkyries/entity/",
            "net/minecraft/world/entity/Entity",
            "net/minecraft/world/entity/LivingEntity"
    };

    /** 既知の干渉 MOD パッケージ候補 */
    private static final String[] FOREIGN_INTERFERENCE_SIGNATURES = {
            "witherzilla",
            "omnimobs",
            "flashfur",
            "nosugar",
            "the_trial_monolith",
            "imds",
            "more_avaritia",
            "annihilationblade",
            "shizuku",
            "eca"
    };

    private static final ClassFileTransformer TRANSFORMER = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className == null) return null;
            try {
                return handleTransform(loader, className, classBeingRedefined, classfileBuffer);
            } catch (Throwable t) {
                return null;
            }
        }
    };

    private EprotectPlusAgent() {}

    public static void premain(String args, Instrumentation inst) {
        start(inst, false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true);
    }

    private static synchronized void start(Instrumentation inst, boolean lateAttach) {
        if (instrumentation != null) return;
        instrumentation = inst;
        EprotectPlusBridge.init(lateAttach);

        try {
            inst.addTransformer(TRANSFORMER, true);
            com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent.start(inst, lateAttach);
            EprotectPlusBridge.recordEvent("初期化", "EprotectPlusAgent", "JVM-Agent", "start", "起動完了",
                    "CanRetransform=" + inst.isRetransformClassesSupported() + ", CanRedefine=" + inst.isRedefineClassesSupported());
        } catch (Throwable t) {
            EprotectPlusBridge.recordEvent("エラー", "EprotectPlusAgent", "JVM-Agent", "start", "失敗", t.getMessage());
        }
    }

    public static boolean hasInstrumentation() {
        return instrumentation != null;
    }

    public static boolean canRetransform() {
        return instrumentation != null && instrumentation.isRetransformClassesSupported();
    }

    // =========================================================================
    // バイトコード監視・調査・保護
    // =========================================================================

    private static byte[] handleTransform(ClassLoader loader, String className, Class<?> redefined, byte[] bytes) {
        boolean critical = isCriticalClass(className);

        // 初回ロード時: 未改ざんバイトコードのベースラインを保存
        if (redefined == null) {
            if (critical) {
                BASELINE_BYTECODE.putIfAbsent(className, bytes.clone());
                try {
                    BASELINES.putIfAbsent(className, ClassSummary.parse(bytes));
                } catch (Throwable ignored) {}
            }
            return null; // 初回は改変せず通過
        }

        // 再変換 (retransform) または 再定義 (redefine) が発生: 他 MOD による干渉を調査
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String caller = extractCaller(stack);
        boolean foreign = isForeignCaller(caller);

        ClassSummary before = BASELINES.get(className);
        ClassSummary now = null;
        try {
            now = ClassSummary.parse(bytes);
        } catch (Throwable ignored) {}

        List<String> neuteredMethods = new ArrayList<>();
        if (before != null && now != null) {
            for (Map.Entry<String, ClassSummary.MethodInfo> entry : now.methods.entrySet()) {
                ClassSummary.MethodInfo old = before.methods.get(entry.getKey());
                ClassSummary.MethodInfo cur = entry.getValue();
                if (old != null && cur.trivialReturn() && !old.trivialReturn()) {
                    neuteredMethods.add(entry.getKey());
                }
            }
        }

        // 調査記録: 干渉ログを出力
        if (foreign || critical || !neuteredMethods.isEmpty()) {
            EprotectPlusBridge.recordInterference(className, caller, "retransform/redefine",
                    "Neutered=" + neuteredMethods + ", Critical=" + critical + ", Foreign=" + foreign);
        }

        // 保護ロジック: eprotect 自身のクラスが空化・無力化されそうになった場合は元のバイトコードを強制返却
        if (className.startsWith("com/automatavalkyries/protect/")) {
            if (!neuteredMethods.isEmpty() || foreign) {
                byte[] baseline = BASELINE_BYTECODE.get(className);
                if (baseline != null) {
                    EprotectPlusBridge.recordProtection(className, caller, "protect",
                            "外部干渉による改ざんを拒否し元のバイトコードへ復元（対象メソッド: " + neuteredMethods + "）");
                    return baseline.clone();
                }
            }
        }

        return null;
    }

    private static boolean isCriticalClass(String className) {
        for (String p : CRITICAL_PREFIXES) {
            if (className.startsWith(p)) return true;
        }
        return false;
    }

    private static String extractCaller(StackTraceElement[] stack) {
        for (StackTraceElement elem : stack) {
            String cn = elem.getClassName();
            if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("sun.")
                    || cn.contains("EprotectPlus") || cn.contains("ClassFileTransformer")) {
                continue;
            }
            return cn + "." + elem.getMethodName() + ":" + elem.getLineNumber();
        }
        return "UnknownCaller";
    }

    private static boolean isForeignCaller(String caller) {
        String lower = caller.toLowerCase(java.util.Locale.ROOT);
        for (String sig : FOREIGN_INTERFERENCE_SIGNATURES) {
            if (lower.contains(sig)) return true;
        }
        return false;
    }

    // =========================================================================
    // 真抹殺 (True Annihilation) JVM エージェント実行
    // =========================================================================

    /**
     * Java エージェント特権空間から対象エンティティの不死・防御機構を直接剥奪し、不可逆的に抹殺する。
     *
     * @param entity 対象エンティティ（LivingEntity / Entity 等）
     * @return 剥奪・抹殺に成功したか
     */
    public static boolean annihilateEntityJvm(Object entity) {
        if (entity == null) return false;

        return runPrivilegedBypass(() -> {
            Class<?> clazz = entity.getClass();
            String entityName = entity.toString();
            UUID entityUuid = null;
            try {
                Method getUuidMethod = clazz.getMethod("getUUID");
                entityUuid = (UUID) getUuidMethod.invoke(entity);
            } catch (Throwable ignored) {}

            EprotectPlusBridge.recordBypass(clazz.getName(), "TrueKillStick", "annihilateEntityJvm",
                    "特権エージェント空間から不死フラグ・StackCheckerの迂回・剥奪を開始");

            // 1. Witherzilla 対策: 生命維持フラグ (witherzillaLife) を強制無効化
            neutralizeWitherzillaLife(entity, clazz);

            // 2. Omni-Mobs / Metapotent Flashfur 対策: healthDelta をリセットし、StackChecker を迂回
            neutralizeFlashfur(entity, clazz);

            // 3. No-Sugar / IMDS 対策: erasedUuids, FORCE_DEAD_UUIDS 等の消滅登録 & 不死リスト除外
            neutralizeKillModLists(entityUuid, entity);

            // 4. プレイヤー無敵・クリエイティブ・チート防具アビリティの剥奪
            neutralizePlayerDefenses(entity, clazz);

            // 5. 残存不死フィールド・フラグの強制初期化（リフレクション探索）
            neutralizeDefensiveFields(entity, clazz);

            // 6. HP を強制的に 0.0F に固定し、死亡進行
            forceZeroHealthAndKill(entity, clazz);

            EprotectPlusBridge.recordAnnihilation(clazz.getName(), "TrueKillStick", "true_annihilation",
                    "対象の全不死防御機構を解体し不可逆抹殺を完了: " + entityName + " (" + entityUuid + ")");
            return true;
        });
    }

    private static <T> T runPrivilegedBypass(java.util.concurrent.Callable<T> callable) {
        boolean prev = EprotectPlusBridge.isPrivileged();
        EprotectPlusBridge.setPrivileged(true);
        try {
            return callable.call();
        } catch (Throwable t) {
            EprotectPlusBridge.recordEvent("エラー", "EprotectPlusAgent", "annihilate", "exception", "失敗", t.getMessage());
            return null;
        } finally {
            EprotectPlusBridge.setPrivileged(prev);
        }
    }

    private static void neutralizeWitherzillaLife(Object entity, Class<?> clazz) {
        // フィールド witherzillaLife を探索して false に直書き
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("witherzillaLife");
                f.setAccessible(true);
                f.setBoolean(entity, false);
            } catch (Throwable ignored) {}

            try {
                Field f = c.getDeclaredField("life");
                if (f.getType() == boolean.class) {
                    f.setAccessible(true);
                    f.setBoolean(entity, false);
                }
            } catch (Throwable ignored) {}
        }

        // メソッド setWitherzillaLife(false) があれば呼び出す
        try {
            Method m = clazz.getMethod("setWitherzillaLife", boolean.class);
            m.invoke(entity, false);
        } catch (Throwable ignored) {}
    }

    private static void forciblyRemoveFromProtectedList(Object list, Object target) {
        if (list == null) return;
        try {
            Field sizeField = java.util.ArrayList.class.getDeclaredField("size");
            sizeField.setAccessible(true);
            Field dataField = java.util.ArrayList.class.getDeclaredField("elementData");
            dataField.setAccessible(true);

            int size = sizeField.getInt(list);
            Object[] data = (Object[]) dataField.get(list);
            if (data == null || size == 0) return;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                Object elem = data[i];
                if (elem == null) continue;
                if (target == null || elem == target || elem.equals(target)) {
                    dismantleMetapotentFlashfur(elem);
                    data[i] = null; // 抹消・参照解除
                } else {
                    data[newSize++] = elem;
                }
            }
            for (int i = newSize; i < size; i++) {
                data[i] = null;
            }
            sizeField.setInt(list, newSize);
        } catch (Throwable ignored) {}
    }

    private static void dismantleMetapotentFlashfur(Object flashfurCore) {
        if (flashfurCore == null) return;
        try {
            Class<?> coreClass = flashfurCore.getClass();
            try {
                Field idField = coreClass.getDeclaredField("id");
                idField.setAccessible(true);
                UUID id = (UUID) idField.get(flashfurCore);
                if (id != null) {
                    neutralizeKillModLists(id, null);
                }
            } catch (Throwable ignored) {}
            // 1. removed = true
            try {
                Field f = coreClass.getDeclaredField("removed");
                f.setAccessible(true);
                f.setBoolean(flashfurCore, true);
            } catch (Throwable ignored) {}

            // 2. proxy = null
            try {
                Field f = coreClass.getDeclaredField("metapotentFlashfurProxy");
                f.setAccessible(true);
                f.set(flashfurCore, null);
            } catch (Throwable ignored) {}

            // 3. bossEvent.removeAllPlayers() & setVisible(false)
            try {
                Field f = coreClass.getDeclaredField("bossEvent");
                f.setAccessible(true);
                Object bossEvent = f.get(flashfurCore);
                if (bossEvent != null) {
                    try {
                        Method clear = bossEvent.getClass().getMethod("removeAllPlayers");
                        clear.invoke(bossEvent);
                    } catch (Throwable ignored) {}
                    try {
                        Method vis = bossEvent.getClass().getMethod("setVisible", boolean.class);
                        vis.invoke(bossEvent, false);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // 4. 全攻撃・召喚タイマー停止
            for (String timerName : new String[]{"beamTimer", "eraseTimer", "swipeTimer", "totalityBoomTimer", "throwInAirTimer"}) {
                try {
                    Field f = coreClass.getDeclaredField(timerName);
                    f.setAccessible(true);
                    f.setInt(flashfurCore, -9999);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static void neutralizeFlashfur(Object entity, Class<?> clazz) {
        // 1. healthDelta フィールドを探索して 0.0F に直書き
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("healthDelta");
                f.setAccessible(true);
                f.setFloat(entity, 0.0F);
            } catch (Throwable ignored) {}
        }

        try {
            Method m = clazz.getMethod("setHealthDelta", float.class);
            m.invoke(entity, 0.0F);
        } catch (Throwable ignored) {}

        // 2. MetapotentFlashfurEntity の場合、metapotentFlashfur フィールド（本体）を取得して解体
        Object core = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("metapotentFlashfur");
                f.setAccessible(true);
                core = f.get(entity);
                if (core != null) break;
            } catch (Throwable ignored) {}
        }
        if (core != null) {
            dismantleMetapotentFlashfur(core);
        }

        // 3. MetapotentFlashfurLevel.metapotentFlashfurList からの完全抹消
        try {
            Class<?> mfl = Class.forName("flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel");
            Field listField = mfl.getDeclaredField("metapotentFlashfurList");
            listField.setAccessible(true);
            Object list = listField.get(null);
            if (core != null) {
                forciblyRemoveFromProtectedList(list, core);
            } else {
                forciblyRemoveFromProtectedList(list, null);
            }
        } catch (Throwable ignored) {}

        // 4. 通常種 Flashfur の Respawning.RESPAWNING_ENTITIES からの完全抹消
        try {
            Class<?> resp = Class.forName("flashfur.omnimobs.entities.flashfur.powers.Respawning");
            Field listField = resp.getDeclaredField("RESPAWNING_ENTITIES");
            listField.setAccessible(true);
            Object list = listField.get(null);
            forciblyRemoveFromProtectedList(list, entity);
        } catch (Throwable ignored) {}

        // 5. Omni-Mobs の RemoveDeadEntities キューからの除外
        try {
            Class<?> rde = Class.forName("flashfur.omnimobs.entities.base.RemoveDeadEntities");
            Field listField = rde.getDeclaredField("deadEntities");
            listField.setAccessible(true);
            Object list = listField.get(null);
            forciblyRemoveFromProtectedList(list, entity);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void neutralizeKillModLists(UUID uuid, Object entity) {
        if (uuid == null) return;

        boolean isPlayer = entity != null && entity.getClass().getName().contains("Player");

        // 1. 抹殺対象（モブ）を外部 MOD の消滅・処刑リストへ強制追加（消滅状態を確定して相手の防護フックを自壊させる）
        // ※プレイヤーはリスポーンして再参加するため、静的リスト（erasedUuids等）に登録してはならない
        if (!isPlayer) {
            for (String className : new String[]{
                    "com.test.nosugar.mixin.sugar_sword.LivingEntityMixin",
                    "com.test.nosugar.utils.EraseUtils",
                    "com.test.nosugar.utils.entity.LivingEntityUtils"
            }) {
                try {
                    Class<?> cls = Class.forName(className);
                    for (Field f : cls.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) && Set.class.isAssignableFrom(f.getType())) {
                            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                            if (name.contains("eras") || name.contains("kill") || name.contains("dead")) {
                                f.setAccessible(true);
                                Set<Object> set = (Set<Object>) f.get(null);
                                if (set != null) set.add(uuid);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // IMDS の FORCE_DEAD_UUIDS / REMOVED_UUIDS にも抹殺対象を追加して死を確定
            try {
                Class<?> km = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
                for (String fieldName : new String[]{"FORCE_DEAD_UUIDS", "REMOVED_UUIDS"}) {
                    try {
                        Field f = km.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Set<Object> set = (Set<Object>) f.get(null);
                        if (set != null) set.add(uuid);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } else {
            // プレイヤーの場合は逆に静的消滅リストから確実に除外
            clearPlayerFromKillModLists(uuid);
        }

        // 2. 外部 MOD の無敵・保護リストからは対象 UUID を除外
        try {
            Class<?> km = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            for (String fieldName : new String[]{"IMDS_INV_UUIDS", "KEEPINV_UUIDS"}) {
                try {
                    Field f = km.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Set<Object> set = (Set<Object>) f.get(null);
                    if (set != null) set.remove(uuid);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3. 対象エンティティの消滅・抹殺メソッド（setErased 等）をリフレクション直接呼び出し
        if (entity != null) {
            Class<?> eClass = entity.getClass();
            for (Method m : eClass.getMethods()) {
                String mName = m.getName();
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) {
                    if (com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.isDeathState(mName)) {
                        try {
                            m.invoke(entity, true);
                        } catch (Throwable ignored) {}
                    } else if (com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.isDefensive(mName)) {
                        try {
                            m.invoke(entity, false);
                        } catch (Throwable ignored) {}
                    }
                } else if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == UUID.class) {
                    if (com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.isDeathState(mName)) {
                        try {
                            m.invoke(entity, uuid);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    private static void neutralizeDefensiveFields(Object entity, Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String name = f.getName();
                boolean isDef = com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.isDefensive(name);
                boolean isDeath = com.automatavalkyries.eprotectplus.flag.FlagDictionaryProtector.isDeathState(name);

                if (isDef) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == boolean.class) {
                            f.setBoolean(entity, false);
                        } else if (f.getType() == int.class) {
                            f.setInt(entity, 0);
                        } else if (f.getType() == float.class) {
                            f.setFloat(entity, 0.0F);
                        } else if (f.getType() == double.class) {
                            f.setDouble(entity, 0.0D);
                        }
                    } catch (Throwable ignored) {}
                } else if (isDeath) {
                    try {
                        f.setAccessible(true);
                        if (f.getType() == boolean.class) {
                            f.setBoolean(entity, true);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void neutralizePlayerDefenses(Object entity, Class<?> clazz) {
        // Player の abilities フィールドの探索と無力化
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field abilitiesField = c.getDeclaredField("abilities");
                abilitiesField.setAccessible(true);
                Object abilities = abilitiesField.get(entity);
                if (abilities != null) {
                    Class<?> abClass = abilities.getClass();
                    for (String fName : new String[]{"invulnerable", "mayfly", "flying", "instabuild"}) {
                        try {
                            Field f = abClass.getDeclaredField(fName);
                            f.setAccessible(true);
                            f.setBoolean(abilities, false);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void forceZeroHealthAndKill(Object entity, Class<?> clazz) {
        // setHealth(0.0F) の直接呼出し
        try {
            Method setHealth = clazz.getMethod("setHealth", float.class);
            setHealth.invoke(entity, 0.0F);
        } catch (Throwable ignored) {}

        // フィールド直書き
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("health");
                f.setAccessible(true);
                f.setFloat(entity, 0.0F);
            } catch (Throwable ignored) {}

            try {
                Field f = c.getDeclaredField("deathTime");
                f.setAccessible(true);
                f.setInt(entity, 19);
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    public static void clearPlayerFromKillModLists(UUID uuid) {
        if (uuid == null) return;
        for (String className : new String[]{
                "com.test.nosugar.mixin.sugar_sword.LivingEntityMixin",
                "com.test.nosugar.utils.EraseUtils",
                "com.test.nosugar.utils.entity.LivingEntityUtils"
        }) {
            try {
                Class<?> cls = Class.forName(className);
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && Set.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Set<Object> set = (Set<Object>) f.get(null);
                        if (set != null) set.remove(uuid);
                    }
                }
            } catch (Throwable ignored) {}
        }
        // ECA (EntityRemovalQuarantine, DelayedHealthVerifier) のクリーンアップ
        for (String className : new String[]{
                "net.eca.util.health.DelayedHealthVerifier",
                "net.eca.util.EntityRemovalQuarantine"
        }) {
            try {
                Class<?> cls = Class.forName(className);
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        if (Set.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Set<Object> set = (Set<Object>) f.get(null);
                            if (set != null) set.remove(uuid);
                        } else if (Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Map<Object, Object> map = (Map<Object, Object>) f.get(null);
                            if (map != null) {
                                map.remove(uuid);
                                try {
                                    map.keySet().removeIf(k -> {
                                        try {
                                            Method getUuid = k.getClass().getMethod("getUUID");
                                            return uuid.equals(getUuid.invoke(k));
                                        } catch (Throwable ignored) {
                                            return false;
                                        }
                                    });
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            Class<?> km = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            for (String fieldName : new String[]{"FORCE_DEAD_UUIDS", "REMOVED_UUIDS"}) {
                try {
                    Field f = km.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Set<Object> set = (Set<Object>) f.get(null);
                    if (set != null) set.remove(uuid);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
