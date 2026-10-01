package com.automatavalkyries.observer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 能力の地図: 読み込まれたクラスが「何をできる作りか」を、定数プール（参照しているメソッド・フィールドと文字列定数）だけから割り出す。
 * 実際にしたことではなく、できること（即死 MOD の手口の下地）。互換 Mixin・フラグ辞書・ブラックリストの判断の手がかり用。
 * <p>
 * Minecraft の名前は開発環境の名前（公式名）で見る。配布環境（SRG 名）では Minecraft 側の規則は当たらない
 * （JDK 側の規則は当たる）。
 * </p>
 */
final class Capabilities {

    /** 読む価値があるかを、クラスファイルのバイト列だけで先に見る（変換の呼び出しの中なので軽く） */
    private static final byte[][] NEEDLES = ascii(
            "misc/Unsafe", "java/lang/instrument/Instrumentation", "com/sun/tools/attach", "halt", "exit", "loadLibrary",
            "removalReason", "levelCallback", "EntityTickList", "PersistentEntitySectionManager", "EntityLookup",
            "EntitySectionStorage", "TransientEntitySectionManager", "reflect/Field", "VarHandle", "objectFieldOffset", "findSetter", "getDeclaredField", "findGetter");

    /** 読まないクラス（JDK・Minecraft・Forge・よく同梱されるライブラリ・このMOD 自身） */
    private static final String[] SKIPPED = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "net/minecraft/", "com/mojang/", "net/minecraftforge/", "cpw/mods/",
            "org/spongepowered/", "org/objectweb/", "it/unimi/", "com/google/", "org/apache/", "io/netty/", "org/lwjgl/",
            "org/slf4j/", "com/electronwill/", "net/jodah/", "oshi/", "com/ibm/", "org/joml/", "kotlin/", "com/llamalad7/",
            "com/automatavalkyries/"};

    private static final Set<String> UNSAFE_OWNERS = Set.of("sun/misc/Unsafe", "jdk/internal/misc/Unsafe");
    private static final Set<String> REFLECTIVE_WRITES = Set.of(
            "java/lang/reflect/Field.set", "java/lang/reflect/Field.setInt", "java/lang/reflect/Field.setFloat",
            "java/lang/reflect/Field.setBoolean", "java/lang/reflect/Field.setLong", "java/lang/reflect/Field.setDouble",
            "java/lang/invoke/MethodHandles$Lookup.findSetter", "java/lang/invoke/MethodHandles$Lookup.unreflectSetter",
            "java/lang/invoke/MethodHandles$Lookup.findVarHandle", "java/lang/invoke/MethodHandles$Lookup.findStaticSetter",
            "java/lang/invoke/VarHandle.set", "java/lang/invoke/VarHandle.setVolatile",
            "sun/misc/Unsafe.objectFieldOffset", "jdk/internal/misc/Unsafe.objectFieldOffset");
    /** リフレクションで名前を指して書き換えると即死・消去につながるエンティティ・ワールドの状態 */
    private static final Set<String> ENTITY_STATE_NAMES = Set.of(
            "removalReason", "levelCallback", "health", "dead", "deathTime", "DATA_HEALTH_ID", "entityTickList", "entityManager",
            "active", "iterated", "byId", "byUuid", "sectionStorage", "entityMap", "callbacks", "visibleEntityStorage",
            "knownUuids", "sections", "storage", "tickingEntities");
    /** リフレクションで名前を指して取り出す・呼ぶ（取り出した一覧から直接外す型。annihilationblade 等） */
    private static final Set<String> REFLECTIVE_ACCESS = Set.of(
            "java/lang/Class.getDeclaredField", "java/lang/Class.getField", "java/lang/reflect/Field.get",
            "java/lang/Class.getDeclaredMethod", "java/lang/reflect/Method.invoke",
            "java/lang/invoke/MethodHandles$Lookup.findGetter", "java/lang/invoke/MethodHandles$Lookup.findVirtual",
            "net/minecraftforge/fml/util/ObfuscationReflectionHelper.findField",
            "net/minecraftforge/fml/util/ObfuscationReflectionHelper.getPrivateValue",
            "net/minecraftforge/fml/util/ObfuscationReflectionHelper.findMethod");
    /** ワールドがエンティティを管理する内部（一覧・追跡・区画）の名前 */
    private static final Set<String> WORLD_INTERNAL_NAMES = Set.of(
            "entityManager", "entityTickList", "entityMap", "chunkMap", "sectionStorage", "visibleEntityStorage", "knownUuids",
            "callbacks", "tickingEntities", "byId", "byUuid", "currentSection", "levelCallback", "removalReason");

    private static final Set<String> WORLD_LIST_OWNERS = Set.of(
            "net/minecraft/world/level/entity/EntityTickList", "net/minecraft/world/level/entity/PersistentEntitySectionManager",
            "net/minecraft/world/level/entity/TransientEntitySectionManager", "net/minecraft/world/level/entity/EntityLookup",
            "net/minecraft/world/level/entity/EntitySectionStorage", "net/minecraft/world/level/entity/EntitySection",
            "net/minecraft/server/level/ChunkMap$TrackedEntity");
    private static final Set<String> DIRECT_STATE_FIELDS = Set.of(
            "net/minecraft/world/entity/Entity.removalReason", "net/minecraft/world/entity/Entity.levelCallback",
            "net/minecraft/world/entity/Entity.setRemoved", "net/minecraft/world/entity/LivingEntity.dead");

    private Capabilities() {}

    static boolean skipped(String className) {
        for (String prefix : SKIPPED) if (className.startsWith(prefix)) return true;
        return false;
    }

    /** バイト列に手がかりの文字列が 1 つでもあるか */
    static boolean worthParsing(byte[] bytes) {
        for (byte[] needle : NEEDLES) if (contains(bytes, needle)) return true;
        return false;
    }

    /** そのクラスにある能力（無ければ空） */
    static List<String> of(ClassSummary summary) {
        List<String> found = new ArrayList<>();
        boolean unsafeWrite = false, rewrite = false, attach = false, exit = false, threadStop = false, nativeLib = false;
        boolean reflectiveWrite = false, worldLists = false, directState = false;
        boolean reflectiveAccess = false;
        for (String ref : summary.refs) {
            int dot = ref.lastIndexOf('.');
            if (dot < 0) continue;
            String owner = ref.substring(0, dot);
            String name = ref.substring(dot + 1);
            if (UNSAFE_OWNERS.contains(owner) && (name.startsWith("put") || name.startsWith("compareAnd")
                    || name.equals("copyMemory") || name.equals("setMemory"))) unsafeWrite = true;
            if (owner.equals("java/lang/instrument/Instrumentation")
                    && (name.equals("retransformClasses") || name.equals("redefineClasses") || name.equals("addTransformer"))) rewrite = true;
            if (owner.startsWith("com/sun/tools/attach/") && (name.equals("attach") || name.equals("loadAgent"))) attach = true;
            if ((owner.equals("java/lang/Runtime") && (name.equals("halt") || name.equals("exit")))
                    || (owner.equals("java/lang/System") && name.equals("exit"))) exit = true;
            if (owner.equals("java/lang/Thread") && (name.equals("stop") || name.equals("suspend"))) threadStop = true;
            if ((owner.equals("java/lang/System") || owner.equals("java/lang/Runtime"))
                    && (name.equals("load") || name.equals("loadLibrary"))) nativeLib = true;
            if (REFLECTIVE_WRITES.contains(ref)) reflectiveWrite = true;
            if (REFLECTIVE_ACCESS.contains(ref)) reflectiveAccess = true;
            if (WORLD_LIST_OWNERS.contains(owner)) worldLists = true;
            if (DIRECT_STATE_FIELDS.contains(ref)) directState = true;
        }
        if (summary.strings.contains("jdk.attach.allowAttachSelf")) attach = true;
        boolean namesWorldInternals = false;
        for (String s : summary.strings) {
            if (WORLD_INTERNAL_NAMES.contains(s) || isSrgField(s)) {
                namesWorldInternals = true;
                break;
            }
        }
        boolean namesEntityState = false;
        for (String s : summary.strings) {
            // 公式名か、配布環境の SRG のフィールド名（f_数字_）。後者はどのクラスのフィールドかまでは分からない
            if (ENTITY_STATE_NAMES.contains(s) || isSrgField(s)) {
                namesEntityState = true;
                break;
            }
        }
        if (unsafeWrite) found.add("unsafe_write");
        if (rewrite) found.add("class_rewrite");
        if (attach) found.add("self_attach");
        if (exit) found.add("jvm_exit");
        if (threadStop) found.add("thread_stop");
        if (nativeLib) found.add("native_library");
        if (reflectiveWrite && namesEntityState) found.add("reflect_entity_state");
        else if (reflectiveAccess && namesWorldInternals) found.add("reflect_world_internals");
        if (worldLists) found.add("world_lists");
        if (directState) found.add("direct_entity_state");
        return found;
    }

    /** "f_19826_" のような SRG のフィールド名 */
    private static boolean isSrgField(String s) {
        if (s.length() < 4 || !s.startsWith("f_") || s.charAt(s.length() - 1) != '_') return false;
        for (int i = 2; i < s.length() - 1; i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static byte[][] ascii(String... values) {
        byte[][] out = new byte[values.length][];
        for (int i = 0; i < values.length; i++) out[i] = values[i].getBytes(StandardCharsets.US_ASCII);
        return out;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        int last = haystack.length - needle.length;
        byte first = needle[0];
        outer:
        for (int i = 0; i <= last; i++) {
            if (haystack[i] != first) continue;
            for (int j = 1; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
}
