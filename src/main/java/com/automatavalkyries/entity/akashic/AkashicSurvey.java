package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.flag.FlagDictionary;
import net.minecraftforge.fml.ModList;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 相手の MOD の下調べ（事象観測体アカシック・プローブ。2026-10-25 ユーザー指定「相手の MOD を下調べする」）。
 * その MOD の jar のクラスを、定数プール（参照しているクラス・メソッド・フィールドの名前と文字列）だけ読み、即死・凶悪技術の下地になる
 * 「できる作り」を割り出す。実際にしたことではなく、できること。クラスは読み込まない（実行しない）。
 * <p>
 * 重いので別のスレッドで回し、MOD ごとに 1 回だけ（結果は使い回す）。観察用エージェント（{@code -PwithObserver}）が付いていれば、
 * その能力の地図（{@code ObserverLink.capabilities}）も合わせる。Minecraft の名前は配布環境では SRG 名なので、主な物は両方で見る
 * </p>
 */
public final class AkashicSurvey {

    private AkashicSurvey() {}

    /** 1 つの能力: 名前（翻訳キーの末尾）・当てはまったクラスの数・例（クラス名を 3 つまで） */
    public record Finding(String key, int classes, List<String> samples) {}

    /** 下調べの結果（{@code subFindings} は派生・細部技術、{@code dictionaryHits} はフラグ辞書に当たった語彙の例） */
    public record Report(String mod, int scannedClasses, List<Finding> findings, String error,
                         List<Finding> subFindings, List<String> dictionaryHits) {
        public Report(String mod, int scannedClasses, List<Finding> findings, String error) {
            this(mod, scannedClasses, findings, error, List.of(), List.of());
        }

        public boolean ok() {
            return error == null;
        }
    }

    /** 能力の見分け方: クラスの定数プールの文字列の集まりに当てはまるか */
    private record Rule(String key, Predicate<Set<String>> test) {}

    private static boolean any(Set<String> strings, String... needles) {
        for (String s : strings) {
            for (String needle : needles) {
                if (s.contains(needle)) return true;
            }
        }
        return false;
    }

    /** 名前で指して書き換えると即死・消去につながる Minecraft のフィールド（公式名と SRG 名） */
    private static final String[] ENTITY_STATE_FIELDS = {
            "removalReason", "f_146795_", "levelCallback", "f_146801_", "deathTime", "f_20919_", "DATA_HEALTH_ID", "f_20961_",
            "f_20890_", "uuid", "f_19820_", "f_19848_", "lastHurt", "f_20898_", "invulnerableTime", "f_19802_"};
    private static final String[] WORLD_INTERNALS = {
            "net/minecraft/world/level/entity/EntityTickList", "net/minecraft/world/level/entity/PersistentEntitySectionManager",
            "net/minecraft/world/level/entity/TransientEntitySectionManager", "net/minecraft/world/level/entity/EntityLookup",
            "net/minecraft/world/level/entity/EntitySectionStorage", "net/minecraft/server/level/ChunkMap$TrackedEntity",
            "ClassInstanceMultiMap", "EntityAccess"};

    private static final List<Rule> RULES = List.of(
            new Rule("unsafe", s -> any(s, "sun/misc/Unsafe", "jdk/internal/misc/Unsafe")),
            new Rule("instrumentation", s -> any(s, "java/lang/instrument/Instrumentation", "java/lang/instrument/ClassFileTransformer")),
            new Rule("attach", s -> any(s, "com/sun/tools/attach", "jdk/attach", "VirtualMachine")),
            new Rule("coremod", s -> any(s, "cpw/mods/modlauncher/api/ITransformer", "cpw/mods/modlauncher/serviceapi/ILaunchPluginService")),
            new Rule("overwrite", s -> any(s, "org/spongepowered/asm/mixin/Overwrite")),
            new Rule("jvm_exit", s -> (s.contains("java/lang/System") || s.contains("java/lang/Runtime")) && (s.contains("exit") || s.contains("halt"))),
            new Rule("field_write", s -> any(s, "java/lang/reflect/Field", "java/lang/invoke/VarHandle", "MethodHandles$Lookup")
                    && any(s, ENTITY_STATE_FIELDS)),
            new Rule("reflective_calls", s -> s.contains("java/lang/reflect/Method") && s.contains("invoke") && any(s, "getDeclaredMethods", "getMethods")),
            new Rule("world_lists", s -> any(s, WORLD_INTERNALS)),
            new Rule("client_remove", s -> any(s, "ClientboundRemoveEntitiesPacket")),
            new Rule("boss_bar", s -> s.contains("net/minecraft/server/level/ServerBossEvent") && any(s, "removeAllPlayers", "m_7706_")
                    || s.contains("net/minecraft/client/gui/components/BossHealthOverlay")),
            new Rule("threads", s -> s.contains("java/lang/Thread") && any(s, "getAllStackTraces", "interrupt", "stop"))
                    // 他のスレッドを列挙して止められる作り（The King Sword は他 MOD の見張りのスレッドを「侵入者」として止める）
    );

    /**
     * 基幹技術のさらに奥にある派生・マニアック技術の見分け方（ログファイルに詳細出力し、ホログラムの翻訳キーは壊さない）
     */
    private static final List<Rule> SUB_RULES = List.of(
            // StackWalker / getStackTrace による呼び出し元偽装・検閲（Omni-Mobs StackChecker 等）
            new Rule("sub:stack_inspect", s -> any(s, "java/lang/StackWalker", "getStackTrace", "SecurityManager", "getClassContext")),
            // Class.reflectionData 汚染や IMPL_LOOKUP 奪取によるリフレクション対策・突破（SuperSteve PLZBase 等）
            new Rule("sub:reflection_bypass", s -> any(s, "reflectionData", "IMPL_LOOKUP", "getDeclaredFields0", "getDeclaredMethods0", "theInternalUnsafe")),
            // 実行時クラス定義・動的プロキシ生成
            new Rule("sub:dynamic_class", s -> any(s, "defineClass", "defineHiddenClass", "java/lang/reflect/Proxy", "InvocationHandler")),
            // Forge イベントバス内部リスナー配列の直接操作・強制登録解除
            new Rule("sub:event_bus_hack", s -> any(s, "net/minecraftforge/eventbus/EventBus", "net/minecraftforge/eventbus/ListenerList")
                    && any(s, "unregister", "listeners", "busID")),
            // 別個体すげ替え・エンティティクローン復活（Tesseract Beast / Immortal Wither 系）
            new Rule("sub:clone_respawn", s -> any(s, "restoreFrom", "m_20361_", "addFreshEntity", "m_7967_")
                    && any(s, "saveWithoutId", "m_20240_", "load", "m_20258_")),
            // 独自シャドウHP・同期NBT・保護リストの保持（Omni-Mobs Flashfur 等）
            new Rule("sub:shadow_health", s -> any(s, "ProtectedArrayList", "omnimobs_health_data", "trueHealth", "realHealth", "shadowHealth", "shieldHealth")),
            // AttributeInstance のキャッシュ・ベース値への直接介入
            new Rule("sub:attr_poison", s -> any(s, "net/minecraft/world/entity/ai/attributes/AttributeInstance")
                    && any(s, "cachedValue", "f_22093_", "baseValue", "f_22091_", "removeModifier")),
            // 非エンティティ外部コントローラー構造（CopyOnWriteArrayList / CopyOnWriteArraySet 等での常駐管理）
            new Rule("sub:non_entity_controller", s -> any(s, "java/util/concurrent/CopyOnWriteArrayList", "java/util/concurrent/CopyOnWriteArraySet")
                    && any(s, "TickEvent$ServerTickEvent", "TickEvent$LevelTickEvent"))
    );

    private static final Map<String, CompletableFuture<Report>> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AutomataValkyries-AkashicSurvey");
        thread.setDaemon(true);
        return thread;
    });
    /** 1 つの MOD で読むクラスの上限（巨大な MOD でも止まらないように） */
    private static final int MAX_CLASSES = 40_000;

    /** その MOD の下調べ（まだなら始める）。同じ MOD は 1 回だけ */
    public static CompletableFuture<Report> request(String mod) {
        return CACHE.computeIfAbsent(mod, m -> CompletableFuture.supplyAsync(() -> survey(m), EXECUTOR));
    }

    private static Report survey(String mod) {
        try {
            var info = ModList.get().getModFileById(mod);
            if (info == null) return new Report(mod, 0, List.of(), "no mod file");
            Path path = info.getFile().getFilePath();
            Map<String, int[]> counts = new LinkedHashMap<>();
            Map<String, List<String>> samples = new LinkedHashMap<>();
            Map<String, int[]> subCounts = new LinkedHashMap<>();
            Map<String, List<String>> subSamples = new LinkedHashMap<>();
            Set<String> dictHits = new java.util.LinkedHashSet<>();
            int scanned = scan(path, (className, strings) -> {
                for (Rule rule : RULES) {
                    if (!rule.test().test(strings)) continue;
                    counts.computeIfAbsent(rule.key(), k -> new int[1])[0]++;
                    List<String> list = samples.computeIfAbsent(rule.key(), k -> new ArrayList<>());
                    if (list.size() < 3) list.add(className.replace('/', '.'));
                }
                for (Rule subRule : SUB_RULES) {
                    if (!subRule.test().test(strings)) continue;
                    subCounts.computeIfAbsent(subRule.key(), k -> new int[1])[0]++;
                    List<String> list = subSamples.computeIfAbsent(subRule.key(), k -> new ArrayList<>());
                    if (list.size() < 3) list.add(className.replace('/', '.'));
                }
                if (dictHits.size() < 24) {
                    collectDictionaryHits(strings, dictHits);
                }
            });
            List<Finding> findings = new ArrayList<>();
            for (Rule rule : RULES) {
                int[] n = counts.get(rule.key());
                if (n != null) findings.add(new Finding(rule.key(), n[0], List.copyOf(samples.get(rule.key()))));
            }
            for (var row : com.automatavalkyries.protect.ObserverLink.capabilities()) { // 観察用エージェントが付いていれば
                if (mod.equals(row.mod())) findings.add(new Finding("observer:" + row.capability(), row.classes(), List.of(row.samples())));
            }
            List<Finding> subFindings = new ArrayList<>();
            for (Rule subRule : SUB_RULES) {
                int[] n = subCounts.get(subRule.key());
                if (n != null) subFindings.add(new Finding(subRule.key(), n[0], List.copyOf(subSamples.get(subRule.key()))));
            }
            return new Report(mod, scanned, List.copyOf(findings), null, List.copyOf(subFindings), List.copyOf(dictHits));
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[akashic] Could not survey {}: {}", mod, t.toString());
            return new Report(mod, 0, List.of(), t.toString());
        }
    }

    private static void collectDictionaryHits(Set<String> strings, Set<String> out) {
        for (String s : strings) {
            if (out.size() >= 24) break;
            if (s.length() < 4 || s.length() > 42) continue;
            if (s.indexOf('/') >= 0 || s.indexOf('(') >= 0 || s.indexOf(';') >= 0 || s.indexOf(' ') >= 0) continue;
            if (s.startsWith("f_") || s.startsWith("m_") || s.startsWith("java") || s.startsWith("net.")) continue;
            Set<FlagDictionary.Category> cats = FlagDictionary.classify(s);
            if (cats.contains(FlagDictionary.Category.KILL)
                    || cats.contains(FlagDictionary.Category.BYPASS)
                    || cats.contains(FlagDictionary.Category.HEALTH_TAMPER)
                    || cats.contains(FlagDictionary.Category.GUARD)
                    || cats.contains(FlagDictionary.Category.BANISH)) {
                out.add(s + cats);
            }
        }
    }

    private interface ClassVisitor {
        void visit(String className, Set<String> strings);
    }

    /** jar（またはクラスの入ったフォルダ）のクラスを順に読む。入れ子の jar（jarJar）の中も 1 段だけ読む */
    private static int scan(Path path, ClassVisitor visitor) throws IOException {
        int[] scanned = {0};
        if (Files.isDirectory(path)) {
            try (Stream<Path> files = Files.walk(path)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    if (scanned[0]++ >= MAX_CLASSES) break;
                    try (InputStream in = Files.newInputStream(file)) {
                        visitClass(path.relativize(file).toString().replace('\\', '/'), in.readAllBytes(), visitor);
                    }
                }
            }
            return scanned[0];
        }
        try (InputStream in = Files.newInputStream(path)) {
            scanZip(in, visitor, scanned, 0);
        }
        return scanned[0];
    }

    private static void scanZip(InputStream raw, ClassVisitor visitor, int[] scanned, int depth) throws IOException {
        ZipInputStream zip = new ZipInputStream(raw);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null && scanned[0] < MAX_CLASSES) {
            String name = entry.getName();
            if (name.endsWith(".class")) {
                scanned[0]++;
                visitClass(name, zip.readAllBytes(), visitor);
            } else if (depth == 0 && name.endsWith(".jar")) {
                scanZip(new java.io.ByteArrayInputStream(zip.readAllBytes()), visitor, scanned, depth + 1);
            }
        }
    }

    private static void visitClass(String entryName, byte[] bytes, ClassVisitor visitor) {
        Set<String> strings = constantStrings(bytes);
        if (strings == null || strings.isEmpty()) return;
        String className = entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - 6) : entryName;
        visitor.visit(className, strings);
    }

    /** 定数プールの UTF-8 の項目を全部取り出す（クラス・メソッド・フィールドの名前と記述子、文字列定数）。クラスファイルでなければ null */
    static Set<String> constantStrings(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (in.readInt() != 0xCAFEBABE) return null;
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            Set<String> out = new HashSet<>();
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> out.add(in.readUTF());
                    case 3, 4 -> in.skipBytes(4);
                    case 5, 6 -> {
                        in.skipBytes(8);
                        i++; // long・double は 2 つ分
                    }
                    case 7, 8, 16, 19, 20 -> in.skipBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                    case 15 -> in.skipBytes(3);
                    default -> {
                        return out; // 知らない種類: そこまでで止める
                    }
                }
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }
}
