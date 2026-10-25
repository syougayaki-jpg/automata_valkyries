package com.automatavalkyries.protect.flag;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.util.StaticReferencePurger;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 他 MOD の抹殺・停止・HP 改ざんの一覧（static な Collection / Map）を見つけて覚え、保護対象を毎 Tick そこから外す（準汎用）。
 * <p>
 * 即死 MOD は消した個体を一覧に載せ、後から攻撃し直す・再登録を拒否する（IMDS の強制死亡リスト、ECA の消去中の隔離、
 * Omni-Mobs の消去予約、No-Sugar の消去済み UUID 等）。一覧は攻撃したクラス自身ではなく、そこから呼ぶ別のクラスにあることが多い。
 * </p>
 * 見つけ方（読み込み済み・初期化済みの他 MOD のクラスだけを覗く。他 MOD のクラスを新たに動かさない）:
 * <ol>
 *   <li>攻撃を拒否したとき、攻撃したクラスのバイトコード（jar の元のクラスファイル）を ASM で読み、参照している他 MOD のクラスを集める</li>
 *   <li>そのクラスの static な一覧のうち、フィールド名が死亡・消去を表すもの、またはクラス名が死亡・消去を表すクラス
 *       （EntityRemovalQuarantine・ImdsKillManager 等）の一覧を、抹殺予定の一覧として覚える（名前が守りを表すものは除く）</li>
 *   <li>vanilla のエンティティのクラスに他 MOD が足した static な一覧も、フィールド名で同じく覚える（No-Sugar の erasedUuids 等）</li>
 * </ol>
 * 覚えた一覧からは保護対象（個体と UUID）だけを取り除く。他の要素には触れない。
 */
public final class KillLists {

    private static final int MAX_LISTS = 256;

    /** 一覧 → それを持つ MOD（vanilla に足されたものは足した MOD） */
    private static final Map<Field, String> LISTS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> SCANNED = ConcurrentHashMap.newKeySet();
    /** まだ初期化されていなかった候補（次の攻撃のときに覗き直す） */
    private static final Set<Class<?>> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile boolean injectedScanned = false;

    private KillLists() {}

    /** 覚えている一覧（表示・テスト用） */
    public static Set<Field> lists() {
        return Collections.unmodifiableSet(LISTS.keySet());
    }

    /** 攻撃したクラスと、それが参照している他 MOD のクラスから一覧を探す（同じクラスは 1 回だけ） */
    public static void discoverFrom(@Nullable Class<?> attacker) {
        retryPending();
        if (attacker == null || !isForeign(attacker) || !SCANNED.add(attacker)) return;
        if (SCANNED.size() > 4096) return; // 探しすぎない
        Set<Class<?>> candidates = new LinkedHashSet<>();
        candidates.add(attacker);
        for (String owner : referencedClasses(attacker)) {
            try {
                Class<?> type = Class.forName(owner.replace('/', '.'), false, attacker.getClassLoader());
                if (isForeign(type)) candidates.add(type);
            } catch (Throwable ignored) {}
        }
        if (candidates.size() == 1) candidates.addAll(relatedClasses(attacker)); // クラスファイルが読めない（メモリ上で作られた）クラス
        for (Class<?> type : candidates) collectLists(type);
    }

    /**
     * そのクラスが関わる他 MOD のクラス。jar の元のクラスファイル（またはエージェント捕獲キャッシュ）が読めればバイトコードの参照先
     * （static 呼び出し・static フィールド・new）、読めなければ（{@code Unsafe.defineClass} でメモリ上に作られたクラス等）フィールドの型・
     * メソッドの引数と戻り値の型・内側／外側のクラスから推測する。クラスは初期化しない
     */
    public static Set<Class<?>> relatedClasses(Class<?> type) {
        Set<Class<?>> out = new LinkedHashSet<>();
        Set<String> owners = referencedClasses(type);
        if (!owners.isEmpty()) {
            for (String owner : owners) {
                try {
                    Class<?> found = Class.forName(owner.replace('/', '.'), false, type.getClassLoader());
                    if (isForeign(found) && out.size() < 64) out.add(found);
                } catch (Throwable ignored) {}
            }
            return out;
        }
        try {
            for (Field field : UnsafeHelper.getRealDeclaredFields(type)) addRelated(field.getType(), type, out);
            for (java.lang.reflect.Method method : UnsafeHelper.getRealDeclaredMethods(type)) {
                addRelated(method.getReturnType(), type, out);
                for (Class<?> param : method.getParameterTypes()) addRelated(param, type, out);
            }
            for (Class<?> inner : type.getDeclaredClasses()) addRelated(inner, type, out);
            addRelated(type.getEnclosingClass(), type, out);
        } catch (Throwable ignored) {}
        return out;
    }

    private static void addRelated(@Nullable Class<?> candidate, Class<?> self, Set<Class<?>> out) {
        while (candidate != null && candidate.isArray()) candidate = candidate.getComponentType();
        if (candidate == null || candidate == self || candidate.isPrimitive() || out.size() >= 64) return;
        if (isForeign(candidate)) out.add(candidate);
    }

    /** 保護対象を、覚えている一覧から外す（毎 Tick） */
    public static int purge(Entity target) {
        if (!injectedScanned) scanInjected();
        if (LISTS.isEmpty()) return 0;
        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        int total = 0;
        for (Map.Entry<Field, String> entry : LISTS.entrySet()) {
            Field field = entry.getKey();
            // ID（整数）で持つ一覧からも外す（The King Sword の強制死亡・強制消去の印。2026-10-25、Claude がユーザー指定で足した）
            int removed = StaticReferencePurger.purgeFields(List.of(field), victims, target.getUUID(), target.getId(), target.getName().getString());
            if (removed == 0) continue;
            total += removed;
            AttackLog.neutralized(target, entry.getValue(),
                    "kill_list:" + field.getDeclaringClass().getSimpleName() + "." + field.getName());
        }
        return total;
    }

    /** GameTest 用 */
    public static void clearForTest() {
        LISTS.clear();
        SCANNED.clear();
        PENDING.clear();
    }

    // ---- 探す --------------------------------------------------------------

    private static void retryPending() {
        if (PENDING.isEmpty()) return;
        for (Class<?> type : PENDING.toArray(new Class<?>[0])) {
            if (UnsafeHelper.isInitialized(type)) {
                PENDING.remove(type);
                collectLists(type);
            }
        }
    }

    private static void scanInjected() {
        injectedScanned = true;
        for (Class<?> c : new Class<?>[]{Entity.class, net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.entity.Mob.class}) {
            for (InjectedMembers.InjectedField injected : InjectedMembers.fields(c)) {
                if (CallerAttribution.SELF.equals(injected.mod())) continue;
                Field field = injected.field();
                if (isListField(field) && (isKillName(field.getName()) || isStopName(field.getName()) || isTamperName(field.getName()))) remember(field, injected.mod());
            }
        }
        try {
            for (Class<?> agentClass : com.automatavalkyries.protect.plus.EprotectPlusLink.findForeignAgentAndHiddenClasses()) {
                if (agentClass != null) discoverFrom(agentClass);
            }
        } catch (Throwable ignored) {}
    }

    private static void collectLists(Class<?> type) {
        if (!UnsafeHelper.isInitialized(type)) { // 初期化前の static を読むと初期化が走る。次の攻撃のときに覗き直す
            if (PENDING.size() < 1024) PENDING.add(type);
            return;
        }
        boolean killClass = isKillName(type.getSimpleName()) || isStopName(type.getSimpleName())
                || isTamperName(type.getSimpleName()) || isAgentHookClass(type.getSimpleName());
        Field[] fields = UnsafeHelper.getRealDeclaredFields(type);
        for (Field field : fields) {
            if (!isListField(field)) continue;
            String name = field.getName();
            if (FlagDictionary.is(name, FlagDictionary.Category.GUARD) && !isKillName(name)) continue;
            if (killClass || isKillName(name) || isStopName(name) || isTamperName(name)) remember(field, CallerAttribution.modOf(type));
        }
    }

    private static void remember(Field field, String mod) {
        if (LISTS.size() >= MAX_LISTS || LISTS.containsKey(field)) return;
        try {
            field.setAccessible(true);
        } catch (Throwable t) {
            return;
        }
        if (LISTS.putIfAbsent(field, mod) == null) {
            AutomataValkyries.LOGGER.info("[eprotect] Watching kill list {}.{} ({})", field.getDeclaringClass().getName(), field.getName(), mod);
        }
    }

    private static boolean isListField(Field field) {
        return Modifier.isStatic(field.getModifiers())
                && (Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType()));
    }

    /** 死亡・消去を表す名前か（守りを表すものは除く。クラス名にも使う） */
    private static boolean isKillName(String name) {
        Set<FlagDictionary.Category> c = FlagDictionary.classify(name);
        return c.contains(FlagDictionary.Category.KILL) && !c.contains(FlagDictionary.Category.GUARD);
    }

    /**
     * 止める・隠す一覧を表す名前か（時間停止中の個体・凍結した個体・隠した個体の一覧。守りを表すものは除く）。
     * 載った保護対象をその MOD が止め続ける・隠し続けるので、死亡の一覧と同じく外す（2026-10-03）
     */
    private static boolean isStopName(String name) {
        Set<FlagDictionary.Category> c = FlagDictionary.classify(name);
        return (c.contains(FlagDictionary.Category.CONTROL) || c.contains(FlagDictionary.Category.CONCEAL))
                && !c.contains(FlagDictionary.Category.GUARD);
    }

    /** HP 改ざんの一覧。守りを表す名前は推測による分類でも除外する。 */
    private static boolean isTamperName(String name) {
        Set<FlagDictionary.Category> categories = FlagDictionary.classify(name);
        if (categories.contains(FlagDictionary.Category.GUARD)) return false;
        if (categories.contains(FlagDictionary.Category.HEALTH_TAMPER)) return true;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return (lower.contains("health") || lower.contains("hp"))
                && (lower.contains("map") || lower.contains("list") || lower.contains("set")
                || lower.contains("lock") || lower.contains("record") || lower.contains("helper")
                || lower.contains("hook") || lower.contains("agent"));
    }

    private static boolean isAgentHookClass(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return false;
        String lower = simpleName.toLowerCase(java.util.Locale.ROOT);
        return (lower.contains("agent") || lower.contains("transformer") || lower.contains("bytecode"))
                && (lower.contains("health") || lower.contains("hp") || lower.contains("kill")
                || lower.contains("helper") || lower.contains("hook"));
    }

    /** そのクラスのバイトコードが参照しているクラス（static 呼び出し・static フィールド・new の相手、およびエージェント捕獲キャッシュ） */
    private static Set<String> referencedClasses(Class<?> type) {
        Set<String> owners = new LinkedHashSet<>();
        String rawClassName = type.getName();
        int slashIdx = rawClassName.indexOf('/');
        if (slashIdx > 0) {
            rawClassName = rawClassName.substring(0, slashIdx);
        }
        String internalSelf = rawClassName.replace('.', '/');
        String resource = internalSelf + ".class";
        ClassLoader loader = type.getClassLoader();
        try (InputStream in = loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            if (in != null) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String desc, boolean isInterface) {
                                if (opcode == Opcodes.INVOKESTATIC) owners.add(owner);
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fieldName, String desc) {
                                if (opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC) owners.add(owner);
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String typeName) {
                                if (opcode == Opcodes.NEW) owners.add(typeName);
                            }

                            @Override
                            public void visitLdcInsn(Object value) {
                                if (value instanceof org.objectweb.asm.Type t && t.getSort() == org.objectweb.asm.Type.OBJECT) {
                                    owners.add(t.getInternalName());
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[eprotect] Could not read {}", resource, t);
        }
        if (owners.isEmpty()) {
            for (String dotName : com.automatavalkyries.protect.plus.EprotectPlusLink.getAgentReferencedClasses(type)) {
                if (dotName != null && !dotName.isEmpty()) {
                    owners.add(dotName.replace('.', '/'));
                }
            }
        }
        owners.remove(internalSelf);
        owners.remove(type.getName().replace('.', '/'));
        return owners;
    }

    private static boolean isForeign(Class<?> type) {
        return CallerAttribution.isForeign(CallerAttribution.modOf(type));
    }

    /** 見つけた一覧の名前（コマンド・ログ用） */
    public static List<String> describe() {
        return LISTS.keySet().stream().map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getName()).sorted().toList();
    }
}
