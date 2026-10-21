package com.automatavalkyries.observer;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 観察用の Java エージェント（{@code -javaagent:automata_valkyries-observer.jar}、または実行中に attach）。
 * <p>
 * Java エージェント型の即死 MOD が実行中に行うクラスの書き換え（再変換・再定義）を見張って記録する。
 * 観察の変換器はクラスを一切書き換えない（transform は常に null を返す）。他の変換器・エンティティ・ワールドにも触れない。
 * 例外は改ざん防止（{@link IntegrityGuard}）で、このMOD 自身のクラスを他のエージェントが書き換えたときだけ元の形に戻す
 * （エージェントの引数 {@code noguard}（{@code -javaagent:...observer.jar=noguard}）で切れる）。
 * </p>
 * 記録するもの:
 * <ul>
 *   <li>再変換・再定義が起きたクラスと、それを呼んだ側（スタックから）</li>
 *   <li>そのとき中身が変わったメソッド、「すぐ return するだけ」に潰されたメソッド、新しく呼ぶようになったメソッド・フィールド</li>
 *   <li>新しく読み込まれた変換器（ClassFileTransformer）のクラスとその jar、起動引数の -javaagent</li>
 *   <li>能力の地図: 初めて読み込まれる他 MOD のクラスが何をできる作りか（Unsafe での書き込み・クラスの書き換え・JVM の終了・
 *       ワールドの一覧への直接の手出し等。{@link Capabilities}）。定数プールを読むだけで、実際にしたかは別</li>
 * </ul>
 * 見える範囲: JVMTI は Java エージェントを作られた順に呼ぶので、再変換（retransform）でこの観察者に渡るのは、
 * 先に読み込まれたエージェントが書き換えた後の形。後から読み込まれたエージェント（実行中に自分を差し込む即死 MOD の多く）の
 * 書き換えは、起きたこと・呼んだ側は分かるが中身は見えない。中身まで見るには、そのエージェントの後でこの観察者を attach する
 * （{@code jcmd <pid> JVMTI.agent_load <この jar>}）。再定義（redefine）は新しい形がどのエージェントにも渡るので常に見える。
 */
public final class ObserverAgent {

    /** 書き換え前の形を覚えておくクラス（他は jar の元の版と比べる） */
    private static final String[] WATCHED_PREFIXES = {
            "net/minecraft/world/entity/", "net/minecraft/server/level/", "net/minecraft/world/level/entity/",
            "com/automatavalkyries/"};
    private static final long SCAN_INTERVAL_MS = 5000L;
    private static final int MAX_QUEUE = 4096;

    private record Task(String className, byte[] bytes, WeakReference<ClassLoader> loader, boolean redefined,
                        String operation, List<String> caller) {}

    private static Instrumentation instrumentation;
    /** 実行中に付いたか（読み込み済みのクラスの元の形を覚えていない） */
    private static volatile boolean lateAttached;
    private static final Map<String, ClassSummary> BASELINES = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<Task> QUEUE = new LinkedBlockingQueue<>(MAX_QUEUE);
    private static final Set<String> KNOWN_TRANSFORMERS = ConcurrentHashMap.newKeySet();
    /** 能力の地図: 置き場所（jar） → 能力 → そのクラス */
    private static final Map<String, Map<String, Set<String>>> CAPABILITIES = new ConcurrentHashMap<>();
    /** まだ記録していない（置き場所, 能力, 最初のクラス） */
    private static final java.util.concurrent.ConcurrentLinkedQueue<String[]> NEW_CAPABILITIES = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** 能力のあったクラスが使う名前（フィールド・メソッド名と、識別子らしい文字列定数）。置き場所ごとに {@link #MAX_NAMES} 件まで。
     *  MOD 側がフラグ辞書で分類する（エージェントは Minecraft・辞書に依存できないので名前を渡すだけ） */
    private static final Map<String, Set<String>> NAMES = new ConcurrentHashMap<>();
    private static final int MAX_NAMES = 512;

    private static final ClassFileTransformer TRANSFORMER = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            try {
                observe(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
            } catch (Throwable ignored) {
                // 観察の失敗でクラスの読み込みを止めない
            }
            return null; // 書き換えない
        }
    };

    private ObserverAgent() {}

    public static void premain(String args, Instrumentation inst) {
        start(inst, false, args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true, args);
    }

    private static synchronized void start(Instrumentation inst, boolean lateAttach, String args) {
        if (instrumentation != null) return;
        instrumentation = inst;
        lateAttached = lateAttach;
        // 変換の最中に初めて読み込むと、読み込みの入れ子になるので先に読んでおく
        ClassSummary.class.getName();
        ClassSummary.MethodInfo.class.getName();
        Capabilities.class.getName();
        IntegrityGuard.class.getName();
        Task.class.getName();
        ObserverBridge.init(lateAttach);
        inst.addTransformer(TRANSFORMER, true);
        // このMOD のクラスの改ざん防止（観察者より後に登録する。引数 noguard で切れる）
        boolean guard = args == null || !args.contains("noguard");
        if (guard) IntegrityGuard.install(inst);
        ObserverBridge.event("note", "-", "-", "", "", "", guard ? "このMOD のクラスの改ざん防止: 有効" : "このMOD のクラスの改ざん防止: 無効（noguard）");

        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-javaagent:") || arg.startsWith("-agentlib:") || arg.startsWith("-agentpath:")) {
                ObserverBridge.event("javaagent", "-", arg, "", "", "", "起動引数");
            }
        }
        if (lateAttach) {
            ObserverBridge.event("note", "-", "-", "", "", "",
                    "実行中に attach した。読み込み済みのクラスの書き換え前の形は覚えていないので、jar の元の版と比べる");
        }

        Thread worker = new Thread(ObserverAgent::work, "AutomataValkyries-Observer");
        worker.setDaemon(true);
        worker.start();
    }

    // ---- 変換の呼び出しの中（軽く。読み込み中なので重い処理・クラスの読み込みはしない） --------------------

    private static void observe(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
        if (className == null) return;
        if (redefined == null) {
            IntegrityGuard.remember(className, bytes);
            scanCapabilities(className, domain, bytes);
            if (watched(className)) enqueue(new Task(className, bytes.clone(), new WeakReference<>(loader), false, "load", List.of()));
            return;
        }
        if (IntegrityGuard.restoringNow()) return; // 改ざん防止が自分で戻している再変換
        IntegrityGuard.noteRewrite(className);
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String operation = "retransform";
        int start = 0;
        for (int i = 0; i < stack.length; i++) {
            String owner = stack[i].getClassName();
            if (owner.startsWith("sun.instrument.")) {
                start = i + 1;
                if (stack[i].getMethodName().startsWith("redefine")) operation = "redefine";
            }
        }
        List<String> caller = new ArrayList<>();
        for (int i = start; i < stack.length && caller.size() < 4; i++) {
            String owner = stack[i].getClassName();
            if (owner.startsWith("java.") || owner.startsWith("jdk.") || owner.startsWith("sun.")) continue;
            caller.add(owner + "." + stack[i].getMethodName());
        }
        enqueue(new Task(className, bytes.clone(), new WeakReference<>(loader), true, operation, caller));
    }

    /**
     * 能力の地図（{@link Capabilities}）: 初めて読み込まれる他 MOD のクラスを、手がかりの文字列があるものだけ定数プールまで読む。
     * 読み込みの最中なので、ここでは集計だけして、記録は観察のスレッドに任せる。
     */
    private static void scanCapabilities(String className, ProtectionDomain domain, byte[] bytes) {
        if (Capabilities.skipped(className) || !Capabilities.worthParsing(bytes)) return;
        List<String> found;
        ClassSummary summary;
        try {
            summary = ClassSummary.parse(bytes);
            found = Capabilities.of(summary);
        } catch (Throwable t) {
            return;
        }
        if (found.isEmpty()) return;
        String location = location(domain);
        rememberNames(location, summary);
        // 読み込みの最中にラムダ（隠しクラスの生成）を使わない
        Map<String, Set<String>> byCapability = CAPABILITIES.get(location);
        if (byCapability == null) {
            CAPABILITIES.putIfAbsent(location, new ConcurrentHashMap<>());
            byCapability = CAPABILITIES.get(location);
        }
        for (String capability : found) {
            Set<String> classes = byCapability.get(capability);
            if (classes == null) {
                Set<String> fresh = ConcurrentHashMap.newKeySet();
                classes = byCapability.putIfAbsent(capability, fresh);
                if (classes == null) {
                    classes = fresh;
                    NEW_CAPABILITIES.offer(new String[]{location, capability, className});
                }
            }
            classes.add(className);
        }
    }

    /** 能力のあったクラスの名前を控える（JDK の名前は除く。ラムダは使わない） */
    private static void rememberNames(String location, ClassSummary summary) {
        Set<String> names = NAMES.get(location);
        if (names == null) {
            NAMES.putIfAbsent(location, ConcurrentHashMap.newKeySet());
            names = NAMES.get(location);
        }
        for (String ref : summary.refs) {
            if (names.size() >= MAX_NAMES) return;
            if (ref.startsWith("java/") || ref.startsWith("jdk/") || ref.startsWith("sun/")) continue;
            int dot = ref.lastIndexOf('.');
            if (dot > 0 && identifier(ref.substring(dot + 1))) names.add(ref.substring(dot + 1));
        }
        for (String s : summary.strings) {
            if (names.size() >= MAX_NAMES) return;
            if (identifier(s)) names.add(s);
        }
    }

    private static boolean identifier(String s) {
        if (s.length() < 3 || s.length() > 40 || s.startsWith("<") || s.startsWith("lambda$")) return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        return true;
    }

    private static void enqueue(Task task) {
        if (!QUEUE.offer(task)) ObserverBridge.dropped();
    }

    private static boolean watched(String className) {
        for (String prefix : WATCHED_PREFIXES) if (className.startsWith(prefix)) return true;
        return false;
    }

    // ---- 観察のスレッド --------------------------------------------------------

    private static void work() {
        long lastScan = 0L;
        if (lateAttached && IntegrityGuard.enabled()) {
            try {
                int captured = IntegrityGuard.captureLoaded();
                ObserverBridge.event("note", "-", "-", "", "", "", "後から付いたので、読み込み済みのこのMOD のクラス " + captured + " 個の元の形を読み込み元から取った（改ざん防止）");
            } catch (Throwable t) {
                ObserverBridge.event("error", "-", "-", "", "", "", String.valueOf(t));
            }
        }
        while (true) {
            try {
                Task task = QUEUE.poll(1, TimeUnit.SECONDS);
                if (task != null) handle(task);
                IntegrityGuard.maintain();
                for (String[] found; (found = NEW_CAPABILITIES.poll()) != null; ) {
                    ObserverBridge.event("capability", found[2], found[0], found[1], "", "", "この jar のクラスが " + found[1] + " をできる作り（実際にしたかは別）");
                }
                if (System.currentTimeMillis() - lastScan >= SCAN_INTERVAL_MS) {
                    lastScan = System.currentTimeMillis();
                    scanTransformers();
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                ObserverBridge.event("error", "-", "-", "", "", "", String.valueOf(t));
            }
        }
    }

    private static void handle(Task task) throws Exception {
        ClassSummary now = ClassSummary.parse(task.bytes());
        if (!task.redefined()) {
            BASELINES.putIfAbsent(task.className(), now);
            return;
        }
        ClassSummary before = BASELINES.get(task.className());
        String compared = "読み込み時の形と比較";
        if (before == null) {
            before = original(task);
            compared = before != null ? "jar の元の版と比較（Mixin 等の変更も差分に出る）" : "比べる元が無い";
        }
        List<String> changed = new ArrayList<>();
        List<String> neutered = new ArrayList<>();
        List<String> added = new ArrayList<>();
        if (before != null) {
            for (Map.Entry<String, ClassSummary.MethodInfo> entry : now.methods.entrySet()) {
                ClassSummary.MethodInfo old = before.methods.get(entry.getKey());
                ClassSummary.MethodInfo cur = entry.getValue();
                if (old == null) {
                    changed.add("+" + entry.getKey());
                } else if (old.codeCrc() != cur.codeCrc()) {
                    changed.add(entry.getKey());
                    if (cur.trivialReturn() && !old.trivialReturn()) neutered.add(entry.getKey());
                }
            }
            for (String ref : now.refs) if (!before.refs.contains(ref)) added.add(ref);
            // 次の書き換えはこの形と比べる（書き換えが積み重なっても差分が分かるように）
            if (watched(task.className())) BASELINES.put(task.className(), now);
        }
        if (changed.isEmpty() && added.isEmpty() && "retransform".equals(task.operation())) {
            scanTransformers(); // 呼んだ側の変換器をすぐ記録する
            compared += "。差分なし（この観察者より後に読み込まれた Java エージェントの書き換えは見えない。そのエージェントの後で attach すれば見える）";
        }
        ObserverBridge.event(task.operation(), task.className(), String.join(" < ", task.caller()),
                String.join(", ", changed), String.join(", ", neutered), String.join(", ", added), compared);
    }

    /** jar にある元のクラスファイル */
    private static ClassSummary original(Task task) {
        ClassLoader loader = task.loader().get();
        String resource = task.className() + ".class";
        try (InputStream in = loader != null ? loader.getResourceAsStream(resource) : ClassLoader.getSystemResourceAsStream(resource)) {
            return in != null ? ClassSummary.parse(in.readAllBytes()) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 新しく読み込まれた変換器のクラスを記録する */
    private static void scanTransformers() {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (type == TRANSFORMER.getClass() || type.isInterface() || !ClassFileTransformer.class.isAssignableFrom(type)) continue;
            if (!KNOWN_TRANSFORMERS.add(type.getName())) continue;
            ObserverBridge.event("transformer", type.getName(), "-", "", "", "", "jar: " + location(type));
        }
    }

    private static String location(Class<?> type) {
        try {
            return location(type.getProtectionDomain());
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String location(ProtectionDomain domain) {
        try {
            CodeSource source = domain != null ? domain.getCodeSource() : null;
            return source != null && source.getLocation() != null ? source.getLocation().toString() : "?";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 能力の地図（橋渡し用）: 置き場所, 能力, クラスの数, 例（3 件まで） */
    static List<Map<String, String>> capabilities() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> jar : CAPABILITIES.entrySet()) {
            for (Map.Entry<String, Set<String>> capability : jar.getValue().entrySet()) {
                List<String> samples = new ArrayList<>();
                for (String name : capability.getValue()) {
                    if (samples.size() >= 3) break;
                    samples.add(name.replace('/', '.'));
                }
                Map<String, String> row = new LinkedHashMap<>();
                row.put("location", jar.getKey());
                row.put("capability", capability.getKey());
                row.put("classes", String.valueOf(capability.getValue().size()));
                row.put("samples", String.join(", ", samples));
                Set<String> names = NAMES.get(jar.getKey());
                row.put("names", names != null ? String.join(",", names) : "");
                rows.add(row);
            }
        }
        return rows;
    }

    /** 状態（橋渡し用） */
    static Map<String, String> status() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("baselines", String.valueOf(BASELINES.size()));
        status.put("queued", String.valueOf(QUEUE.size()));
        status.put("transformers", String.valueOf(KNOWN_TRANSFORMERS.size()));
        status.put("capabilityJars", String.valueOf(CAPABILITIES.size()));
        status.putAll(IntegrityGuard.status());
        return status;
    }
}
