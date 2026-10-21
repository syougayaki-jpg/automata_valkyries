package com.automatavalkyries.agentcommon;

import java.security.CodeSource;
import java.util.Iterator;
import java.util.Locale;

/**
 * このMOD の Java エージェントどうしの共通部品（守る側のエージェント: 観察者・LifeGuard・オートマタヴァルキリー protect が同梱する。JDK だけ）。
 * <p>
 * 今の再変換・再定義を呼んだのがこのMOD 自身（このMOD のほかのエージェント・Eprotect+・MOD 本体）か。呼び出し履歴で、
 * {@code Instrumentation} の再変換・再定義のすぐ外側の呼び出し元を見る（2026-10-05）。このMOD 同士の再変換を「他のエージェントの書き換え」と
 * 数えると、オートマタヴァルキリー protect と LifeGuard が互いに「いちばん外側へ並べ直す → 再変換」を上限まで繰り返していた。
 * </p>
 * 身内かどうかはクラス名の接頭辞だけで決めない（2026-10-21）。同じパッケージ名のクラスを自分の jar に入れた他のエージェントが身内扱いになり、
 * 外側取りも改ざん防止も働かなくなるため。読み込み元（jar・クラスのフォルダ）の名前も {@value #OWN_LOCATION} を含むことを確かめる。
 * 読み込み元が取れないクラス（Forge のモジュール読み込みで起きうる）は従来どおり名前で通す。
 */
public final class InHouse {

    private static final String OWN = "com.automatavalkyries.";
    /** このMOD の jar（automata_valkyries-*.jar）・取り出したエージェントのフォルダ・開発のプロジェクトのフォルダの名前に必ず入る */
    private static final String OWN_LOCATION = "automata_valkyries";

    private InHouse() {
    }

    public static boolean retransformedByThisMod() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> {
            boolean afterInstrumentation = false;
            for (Iterator<StackWalker.StackFrame> it = frames.iterator(); it.hasNext(); ) {
                StackWalker.StackFrame f = it.next();
                String c = f.getClassName(), m = f.getMethodName();
                if (c.equals("sun.instrument.InstrumentationImpl") && (m.startsWith("retransformClasses") || m.startsWith("redefineClasses"))) {
                    afterInstrumentation = true;
                    continue;
                }
                if (afterInstrumentation) return c.startsWith(OWN) && fromThisMod(f.getDeclaringClass());
            }
            return false;
        });
    }

    /** クラスの読み込み元が、このMOD のものか（取れないときは名前だけで通す） */
    static boolean fromThisMod(Class<?> type) {
        try {
            java.security.ProtectionDomain domain = type.getProtectionDomain();
            CodeSource source = domain != null ? domain.getCodeSource() : null;
            if (source == null || source.getLocation() == null) return true;
            return source.getLocation().toString().toLowerCase(Locale.ROOT).contains(OWN_LOCATION);
        } catch (Throwable t) {
            return true; // 調べられないときは、従来どおり名前の判定に任せる（並べ直し合いを起こさない側に倒す）
        }
    }
}
