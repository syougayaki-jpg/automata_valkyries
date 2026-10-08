package com.automatavalkyries.agentcommon;

import com.sun.tools.attach.VirtualMachine;

/**
 * 守る側のエージェントを、動いているゲームへ付ける小さなプログラム（2026-10-09）。MOD が設定でエージェントを読み込むとき、
 * 別のプロセスとしてこれを起こす（Java は同じプロセスから自分へエージェントを付けるのを既定で禁じている）。
 *
 * <pre>java -cp &lt;エージェントの jar&gt; com.automatavalkyries.agentcommon.Attacher &lt;pid&gt; &lt;エージェントの jar&gt; [引数]</pre>
 *
 * 成功したら「attached」を出して 0 で終わる。失敗は標準エラーに理由を出して 1 で終わる。JDK（jdk.attach）だけに依存する
 */
public final class Attacher {

    private Attacher() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: Attacher <pid> <agent jar> [options]");
            System.exit(2);
        }
        try {
            VirtualMachine vm = VirtualMachine.attach(args[0]);
            try {
                vm.loadAgent(args[1], args.length > 2 ? args[2] : null);
            } finally {
                vm.detach();
            }
            System.out.println("attached");
        } catch (Throwable t) {
            System.err.println(t);
            System.exit(1);
        }
    }
}
