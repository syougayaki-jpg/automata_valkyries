package com.automatavalkyries.agentcommon;

import java.util.Iterator;

/**
 * このMOD の Java エージェントどうしの共通部品（守る側のエージェント: 観察者・LifeGuard・オートマタヴァルキリー protect が同梱する。JDK だけ）。
 * <p>
 * 今の再変換・再定義を呼んだのがこのMOD 自身（このMOD のほかのエージェント・Eprotect+・MOD 本体）か。呼び出し履歴で、
 * {@code Instrumentation} の再変換・再定義のすぐ外側の呼び出し元を見る（2026-10-05）。このMOD 同士の再変換を「他のエージェントの書き換え」と
 * 数えると、オートマタヴァルキリー protect と LifeGuard が互いに「いちばん外側へ並べ直す → 再変換」を上限まで繰り返していた。
 */
public final class InHouse {

    private static final String OWN = "com.automatavalkyries.";

    private InHouse() {
    }

    public static boolean retransformedByThisMod() {
        return StackWalker.getInstance().walk(frames -> {
            boolean afterInstrumentation = false;
            for (Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                String c = f.getClassName(), m = f.getMethodName();
                if (c.equals("sun.instrument.InstrumentationImpl") && (m.startsWith("retransformClasses") || m.startsWith("redefineClasses"))) {
                    afterInstrumentation = true;
                    continue;
                }
                if (afterInstrumentation) return c.startsWith(OWN);
            }
            return false;
        });
    }
}
