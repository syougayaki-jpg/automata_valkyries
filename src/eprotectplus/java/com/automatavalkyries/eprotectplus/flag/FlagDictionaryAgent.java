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
    private static final com.automatavalkyries.eprotectplus.BaselineStore BASELINE_BYTECODE = new com.automatavalkyries.eprotectplus.BaselineStore();

    private static final String FLAG_DICT_INTERNAL = "com/automatavalkyries/protect/flag/FlagDictionary";

    private static final ClassFileTransformer TRANSFORMER = new ClassFileTransformer() {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className == null) return null;
            if (!className.equals(FLAG_DICT_INTERNAL) && !className.startsWith("com/automatavalkyries/protect/flag/")) {
                return null;
            }

            // 初回ロード時: 未改ざんのオリジナルバイトコードを保存
            if (classBeingRedefined == null) {
                BASELINE_BYTECODE.capture(loader, className, classfileBuffer);
                return null;
            }

            // 再定義・再変換（他 MOD による改ざんの試み）を検知
            byte[] baseline = BASELINE_BYTECODE.get(loader, className);
            if (baseline != null && !java.util.Arrays.equals(baseline, classfileBuffer)
                    && !com.automatavalkyries.eprotectplus.EprotectPlusAgent.isInternalTransformCall()) {
                EprotectPlusBridge.recordProtection(className, "ExternalCaller", "baseline_returned",
                        "保存した変換入力を返却。後続の変換とJVMへの適用結果はここでは確認できない");
                return baseline.clone();
            }

            return null;
        }
    };

    private FlagDictionaryAgent() {}

    public static void premain(String args, Instrumentation inst) {
        start(inst, false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        start(inst, true);
    }

    public static synchronized void start(Instrumentation inst, boolean lateAttach) {
        if (instrumentation != null) return;
        EprotectPlusBridge.init(lateAttach);
        try {
            inst.addTransformer(TRANSFORMER, inst.isRetransformClassesSupported());
            instrumentation = inst;
            EprotectPlusBridge.recordEvent("初期化", "FlagDictionaryAgent", "JVM-Agent", "start", "起動完了",
                    "CanRetransform=" + inst.isRetransformClassesSupported());
        } catch (Throwable t) {
            EprotectPlusBridge.recordEvent("エラー", "FlagDictionaryAgent", "JVM-Agent", "start", "失敗", t.getMessage());
        }
    }

    public static boolean hasInstrumentation() {
        return instrumentation != null;
    }
}
