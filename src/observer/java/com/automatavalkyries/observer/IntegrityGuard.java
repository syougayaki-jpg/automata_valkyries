package com.automatavalkyries.observer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * このMOD 自身のクラスの改ざん防止（観察者の中で唯一クラスを書き換える部分。エージェントの引数 {@code noguard} で切れる）。
 * <p>
 * 他の Java エージェントがこのMOD のクラス（{@code com/automatavalkyries/}）を再変換・再定義したら、読み込んだときの形に戻す。
 * 他 MOD・Minecraft のクラスには触れない。
 * </p>
 * <ul>
 *   <li>元の形: 観察者の変換器（最初に登録される）が、読み込みのときに受け取ったバイト列。他のエージェントが手を入れる前の形</li>
 *   <li>戻す役: 別の変換器（{@link #GUARD}）。変換器は登録順に呼ばれ、後から登録したものが後で書き換えるので、
 *       このMOD のクラスの書き換えを見たら登録し直して最後に回り、そのクラスを再変換して戻す（再変換は他の変換器も通り直すので、
 *       最後の自分が元の形を返せば戻る）</li>
 *   <li>元の形を返すのは常に安全（再定義はクラスの構造（フィールド・メソッドの一覧）を変えられないので、元の形と構造は同じ）</li>
 * </ul>
 */
final class IntegrityGuard {

    static final String OWN_PREFIX = "com/automatavalkyries/";

    private static volatile boolean enabled = false;
    private static Instrumentation instrumentation;
    /** 読み込んだときの形 */
    private static final Map<String, byte[]> ORIGINAL = new ConcurrentHashMap<>();
    /** 他のエージェントが再変換・再定義した（戻す必要があるかもしれない）クラス */
    private static final Set<String> DIRTY = ConcurrentHashMap.newKeySet();
    /** 記録待ち（クラス, 呼び出し元, 変わったメソッド, 潰されたメソッド） */
    private static final ConcurrentLinkedQueue<String[]> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicLong RESTORED = new AtomicLong();
    /** 自分が再変換している最中のスレッド（その再変換は他のエージェントの書き換えとして数えない） */
    private static volatile Thread restoringThread;

    private static final ClassFileTransformer GUARD = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            try {
                return restore(className, classBeingRedefined, classfileBuffer);
            } catch (Throwable ignored) {
                return null; // 守りの失敗でクラスの読み込みを止めない
            }
        }
    };

    private IntegrityGuard() {}

    static void install(Instrumentation inst) {
        instrumentation = inst;
        inst.addTransformer(GUARD, true);
        enabled = true;
    }

    static boolean enabled() {
        return enabled;
    }

    /** 自分が戻すために再変換している最中か（観察者はその再変換を記録しない） */
    static boolean restoringNow() {
        return Thread.currentThread() == restoringThread;
    }

    // ---- 観察者の変換器から（読み込みの最中なので軽く） ---------------------------------

    /** 読み込んだときの形を覚える */
    static void remember(String className, byte[] bytes) {
        if (enabled && className.startsWith(OWN_PREFIX)) ORIGINAL.putIfAbsent(className, bytes.clone());
    }

    /** このMOD のクラスが再変換・再定義された */
    static void noteRewrite(String className) {
        if (enabled && className.startsWith(OWN_PREFIX) && Thread.currentThread() != restoringThread && !inHouseRetransform()
                && ORIGINAL.containsKey(className)) {
            DIRTY.add(className);
        }
    }

    // ---- 戻す変換器 ------------------------------------------------------------

    private static byte[] restore(String className, Class<?> redefined, byte[] bytes) throws Exception {
        if (!enabled || className == null || redefined == null || !className.startsWith(OWN_PREFIX)) return null;
        if (inHouseRetransform()) return null; // このMOD 同士の再変換（LifeGuard・Eprotect+・オートマタヴァルキリー protect）は改ざんではない
        byte[] original = ORIGINAL.get(className);
        if (original == null || Arrays.equals(original, bytes)) return null;
        // 並べ替えただけ（他のエージェントがクラスを読み直して書き出しただけ）なら記録はしないが、戻しはする
        ClassSummary before = ClassSummary.parse(original);
        ClassSummary now = ClassSummary.parse(bytes);
        List<String> changed = new ArrayList<>();
        List<String> neutered = new ArrayList<>();
        for (Map.Entry<String, ClassSummary.MethodInfo> entry : now.methods.entrySet()) {
            ClassSummary.MethodInfo old = before.methods.get(entry.getKey());
            if (old == null || old.codeCrc() == entry.getValue().codeCrc()) continue;
            changed.add(entry.getKey());
            if (entry.getValue().trivialReturn() && !old.trivialReturn()) neutered.add(entry.getKey());
        }
        if (!changed.isEmpty()) {
            RESTORED.incrementAndGet();
            PENDING.offer(new String[]{className, caller(), String.join(", ", changed), String.join(", ", neutered)});
        }
        return original.clone();
    }

    private static String caller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int start = 0;
        for (int i = 0; i < stack.length; i++) if (stack[i].getClassName().startsWith("sun.instrument.")) start = i + 1;
        List<String> frames = new ArrayList<>();
        for (int i = start; i < stack.length && frames.size() < 4; i++) {
            String owner = stack[i].getClassName();
            if (owner.startsWith("java.") || owner.startsWith("jdk.") || owner.startsWith("sun.")) continue;
            frames.add(owner + "." + stack[i].getMethodName());
        }
        return frames.isEmpty() ? (Thread.currentThread() == restoringThread ? "(re-check by the guard)" : "-") : String.join(" < ", frames);
    }

    // ---- 観察のスレッドから ------------------------------------------------------

    /**
     * 書き換えられたクラスがあれば、最後に回るよう登録し直してから再変換して戻す。記録待ちを書き出す。
     * 観察のスレッドから定期的に呼ぶ。
     */
    static void maintain() {
        if (enabled && !DIRTY.isEmpty()) {
            List<String> names = new ArrayList<>(DIRTY);
            DIRTY.removeAll(names);
            List<Class<?>> targets = new ArrayList<>();
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                String name = type.getName().replace('.', '/');
                if (names.contains(name) && instrumentation.isModifiableClass(type)) targets.add(type);
            }
            instrumentation.removeTransformer(GUARD);
            instrumentation.addTransformer(GUARD, true);
            restoringThread = Thread.currentThread();
            try {
                for (Class<?> type : targets) {
                    try {
                        instrumentation.retransformClasses(type);
                    } catch (Throwable t) {
                        ObserverBridge.event("guard", type.getName(), "-", "", "", "", "戻せなかった: " + t);
                    }
                }
            } finally {
                restoringThread = null;
            }
        }
        for (String[] entry; (entry = PENDING.poll()) != null; ) {
            ObserverBridge.event("guard", entry[0], entry[1], entry[2], entry[3], "",
                    "このMOD のクラスへの書き換えを、読み込んだときの形に戻した");
        }
    }

    // ---- 自己点検（GameTest 用。この点検用のクラスだけを相手にする） ---------------------------------

    static final String PROBE = OWN_PREFIX + "gametest/GuardProbe";
    /** 点検用のクラスの value() のコード（bipush 42; ireturn）と、潰した形（iconst_0; nop; ireturn） */
    private static final byte[] PROBE_CODE = {0x10, 0x2A, (byte) 0xAC};
    private static final byte[] PROBE_NEUTERED = {0x03, 0x00, (byte) 0xAC};
    private static volatile ClassFileTransformer hostile;

    /**
     * 他のエージェントの書き換えを真似る: 改ざん防止より後に、点検用のクラスの value() を潰す変換器を登録して再変換する。
     * 改ざん防止は最後に回り直してこれを戻すはず。{@link #endSelfTest()} で外す。
     */
    static String startSelfTest(Class<?> probe) throws Exception {
        if (!enabled) return "guard disabled";
        if (!probe.getName().replace('.', '/').equals(PROBE)) return "not the probe";
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (!PROBE.equals(className)) return null;
                byte[] out = classfileBuffer.clone();
                int at = indexOf(out, PROBE_CODE);
                if (at < 0) return null;
                System.arraycopy(PROBE_NEUTERED, 0, out, at, PROBE_NEUTERED.length);
                return out;
            }
        };
        hostile = transformer;
        instrumentation.addTransformer(transformer, true);
        instrumentation.retransformClasses(probe);
        return "started";
    }

    static void endSelfTest() {
        ClassFileTransformer transformer = hostile;
        hostile = null;
        if (transformer != null) instrumentation.removeTransformer(transformer);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    static Map<String, String> status() {
        return Map.of("guard", String.valueOf(enabled), "guardRestored", String.valueOf(RESTORED.get()),
                "guardOriginals", String.valueOf(ORIGINAL.size()));
    }

    /**
     * この再変換・再定義を呼んだのがこのMOD（このMOD のほかのエージェント・Eprotect+・MOD 本体）か（2026-10-05）。
     * 呼び出し履歴で、Instrumentation の再変換・再定義のすぐ外側の呼び出し元を見る。このMOD 同士の再変換を「他のエージェントの書き換え」と
     * 数えると、オートマタヴァルキリー protect と LifeGuard が互いに「いちばん外側へ並べ直す → 再変換」を上限まで繰り返していた
     */
    static boolean inHouseRetransform() {
        return StackWalker.getInstance().walk(frames -> {
            boolean afterInstrumentation = false;
            for (java.util.Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                String c = f.getClassName(), m = f.getMethodName();
                if (c.equals("sun.instrument.InstrumentationImpl") && (m.startsWith("retransformClasses") || m.startsWith("redefineClasses"))) {
                    afterInstrumentation = true;
                    continue;
                }
                if (afterInstrumentation) return c.startsWith("com.automatavalkyries.");
            }
            return false;
        });
    }
}
