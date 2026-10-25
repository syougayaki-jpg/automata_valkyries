package com.automatavalkyries.eprotectplus.flag;

import com.automatavalkyries.eprotectplus.EprotectPlusBridge;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * フラグ辞書防護専用 Java エージェント（FlagDictionaryAgent）。
 * <p>
 * {@code FlagDictionary} のバイトコード完全性を常時監視し、
 * 他 MOD（即死・チート系）によるクラス再定義・メソッド無効化・例外注入を
 * 保存した変換入力を返す。後続の変換器やJVMの適用結果は保証できない。
 * 単独のエージェントとしても、Eprotect+ 統合エージェントの一部としても動作する。
 * </p>
 */
public final class FlagDictionaryAgent {

    private static volatile Instrumentation instrumentation;
    private static final ThreadLocal<Boolean> OBSERVING_LATE_INPUT = ThreadLocal.withInitial(() -> false);
    private static final com.automatavalkyries.eprotectplus.BaselineStore BASELINE_BYTECODE = new com.automatavalkyries.eprotectplus.BaselineStore();

    private static final String FLAG_DICT_INTERNAL = "com/automatavalkyries/protect/flag/FlagDictionary";

    private static final ClassFileTransformer TRANSFORMER = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (com.automatavalkyries.eprotectplus.AgentCollisionPolicy.isConfiguredPassive()) return null;
            if (className == null) return null;
            if (!className.equals(FLAG_DICT_INTERNAL) && !className.startsWith("com/automatavalkyries/protect/flag/")) {
                return null;
            }

            // 初回ロード時の変換入力を保存（先行変換済みの場合もあり、未改ざんとは保証しない）。
            if (classBeingRedefined == null) {
                BASELINE_BYTECODE.capture(loader, className, classfileBuffer);
                return null;
            }

            // 再定義・再変換（他 MOD による改ざんの試み）を検知
            byte[] baseline = BASELINE_BYTECODE.get(loader, className);
            if (baseline == null && OBSERVING_LATE_INPUT.get()) {
                BASELINE_BYTECODE.capture(loader, className, classfileBuffer);
                return null;
            }
            if (baseline != null && !java.util.Arrays.equals(baseline, classfileBuffer)
                    && !com.automatavalkyries.eprotectplus.EprotectPlusAgent.isInternalTransformCall()) {
                if (!com.automatavalkyries.eprotectplus.AgentCollisionPolicy.allowReplacement(className)) return null;
                EprotectPlusBridge.recordProtection(className, "ExternalCaller", "baseline_returned",
                        "保存した変換入力を返却。後続の変換とJVMへの適用結果はここでは確認できない");
                return baseline.clone();
            }

            return null;
        }
    };

    private FlagDictionaryAgent() {}

    public static void premain(String args, Instrumentation inst) {
        start(inst, false, args);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true, args);
    }

    public static synchronized void start(Instrumentation inst, boolean lateAttach, String args) {
        if (instrumentation != null) return;
        EprotectPlusBridge.init(lateAttach);
        try {
            com.automatavalkyries.eprotectplus.AgentCollisionPolicy.configure(args);
            if (!com.automatavalkyries.eprotectplus.AgentCollisionPolicy.isPassive())
                inst.addTransformer(TRANSFORMER, inst.isRetransformClassesSupported());
            instrumentation = inst;
            com.automatavalkyries.eprotectplus.HiddenAgentVault.storeFlagInstrumentation(inst);
            EprotectPlusBridge.recordEvent("初期化", "FlagDictionaryAgent", "JVM-Agent", "start", "起動完了",
                    "CanRetransform=" + inst.isRetransformClassesSupported());
            if (lateAttach && inst.isRetransformClassesSupported()
                    && !com.automatavalkyries.eprotectplus.AgentCollisionPolicy.isPassive()) observeLoadedInputs(inst);
        } catch (Throwable t) {
            EprotectPlusBridge.recordEvent("エラー", "FlagDictionaryAgent", "JVM-Agent", "start", "失敗", t.getMessage());
        }
    }

    public static boolean hasInstrumentation() {
        Instrumentation current = instrumentation;
        if (current != null) return true;
        Instrumentation vaulted = com.automatavalkyries.eprotectplus.HiddenAgentVault.loadFlagInstrumentation();
        if (vaulted != null) {
            instrumentation = vaulted;
            return true;
        }
        return false;
    }

    private static void observeLoadedInputs(Instrumentation inst) {
        OBSERVING_LATE_INPUT.set(true);
        try {
            for (Class<?> type : inst.getAllLoadedClasses()) {
                if (!type.getName().startsWith("com.automatavalkyries.protect.flag.")
                        || !inst.isModifiableClass(type)) continue;
                try { inst.retransformClasses(type); }
                catch (Throwable failure) {
                    EprotectPlusBridge.recordEvent("警告", type.getName(), "FlagDictionaryAgent", "late_attach_input",
                            "観測失敗", failure.toString());
                }
            }
            EprotectPlusBridge.recordEvent("監査", "FlagDictionary", "FlagDictionaryAgent", "late_attach_input",
                    "現在の入力を観測", "Inputs observed after attachment may already have been modified");
        } finally {
            OBSERVING_LATE_INPUT.remove();
        }
    }
}
