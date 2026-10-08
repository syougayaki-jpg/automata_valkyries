# Java エージェントを起動の引数なしで（設定で）読み込むしくみ — CODEX 向け

作成: 2026-10-09（Claude）。対象: CODEX の担当のエージェント（Eprotect+ `src/eprotectplus/` の `automata_valkyries-eprotect-plus.jar`、
フラグ辞書のエージェント `automata_valkyries-flag-agent.jar`）。

## 1. 何ができるようになったか

ユーザーの依頼（2026-10-09「Java エージェントを引数付けなくてもコンフィグで設定できるように」）で、Claude の担当の守る側のエージェント 3 つ
（観察者・LifeGuard・オートマタヴァルキリー protect）は、`-javaagent` を付けなくても、設定（`automata_valkyries-common.toml` の `agents.*.enabled`）で
読み込めるようになった。同じしくみに、CODEX の担当のエージェントも **自分の担当のファイルだけを触って** 足せるように、入口を 2 つ用意してある。

しくみの全体は [`SPEC_JAVA_AGENTS_DEFENSIVE.md`](SPEC_JAVA_AGENTS_DEFENSIVE.md) §2.1。要点:

1. ビルドで、エージェントの jar を MOD の jar の `META-INF/automata_valkyries/agents/` に入れる（`build.gradle` の `jar` タスク。入れる物は `ext.bundledAgents` の一覧）
2. 起動の途中（共通の準備 `FMLCommonSetupEvent` の始め）に `protect/AgentSelfAttach.attachConfigured()` が、設定で有効なエージェントを
   `<ゲームのフォルダ>/automata_valkyries/agents/` へ取り出す
3. 同じ Java で別のプロセス（`agentcommon.Attacher`。`VirtualMachine.attach(pid).loadAgent(jar, options)`）を起こして、このゲームへ付ける。
   Java は同じプロセスから自分へ付けるのを既定で禁じている（`jdk.attach.allowAttachSelf`）ため
4. 付けた後、marker のクラスが見えるかで成功を確かめ、`after` を呼ぶ（MOD 側の接続）。付けられなければ警告を出して、起動の引数で付ける方法を案内する
5. 起動の引数で付けてあれば（marker がもう見えれば）何もしない。開発環境（runClient 等）は jar ではなくクラスのフォルダから動くので `build/libs` から読む

## 2. CODEX の担当のエージェントを足す手順

Claude の担当のファイル（`protect/AgentSelfAttach.java`・`build.gradle` の守る側のエージェントの部分）は編集しなくてよい。

### 2.1 `build.gradle`（CODEX の担当の `eprotectPlusJar` / `flagDictionaryAgentJar` のブロックのそば）

```groovy
// 設定で読み込めるよう、MOD の jar に入れる（ext.bundledAgents は Claude の部分で eprotectPlusJarFile の直前に定義してある。それより後に書く）
bundledAgents += ['eprotect-plus', 'flag-agent']
tasks.named('jar') { mustRunAfter 'eprotectPlusJar', 'flagDictionaryAgentJar' }
```

- 一覧に書くのは jar の名前の `automata_valkyries-` の後ろ（`build/libs/automata_valkyries-<名前>.jar` を読む）。
- `jar` から `eprotectPlusJar` へは `dependsOn` で繋がないこと。MixinGradle が jar のタスクごとに足す `addMixinsTo*` の間で輪（Circular dependency）になる。
  順番だけ `mustRunAfter` で決める（`build` / `assemble` は既にエージェントの jar も作る）。

### 2.2 入口のクラス `com.automatavalkyries.protect.plus.BundledAgents`（CODEX の担当のパッケージに新しく作る）

`AgentSelfAttach` は、このクラスがあれば `public static void register()` を（反射で）呼ぶ。中で `AgentSelfAttach.register(...)` を呼ぶ。

```java
package com.automatavalkyries.protect.plus;

import com.automatavalkyries.protect.AgentSelfAttach;

/** CODEX の担当のエージェントを、設定で（起動の引数なしに）読み込む（AgentSelfAttach から呼ばれる） */
public final class BundledAgents {

    private BundledAgents() {}

    public static void register() {
        AgentSelfAttach.register(new AgentSelfAttach.BundledAgent(
                "Eprotect+",                                   // ログに出す名前
                "automata_valkyries-eprotect-plus.jar",        // jar の名前
                null,                                          // ブートストラップに置く呼び出し先の jar（Boot-Class-Path に書いた名前。無ければ null）
                "com.automatavalkyries.eprotectplus.<読み込まれたかを見るクラス>",
                false,                                         // そのクラスがブートストラップにあるなら true、エージェントの jar（システムのクラスローダー）なら false
                () -> /* 自分の設定（例: agents.eprotectPlus.enabled） */ false,
                () -> null,                                    // エージェントへ渡す引数（無ければ null）
                () -> { /* 付けた後の MOD 側の接続（EprotectPlusLink の初期化など。無ければ null を渡す） */ }));
        // フラグ辞書のエージェントも同じように
    }
}
```

- `enabled` で読む設定は、CODEX の担当の設定（自分の設定クラス、または `EprotectConfig` に足してほしければユーザー経由で Claude に頼む）。
  `register()` は共通の準備のときに呼ばれるので、設定はもう読める。
- `marker` は付いたかどうかの確かめに使う。エージェントの jar のクラスなら `bootstrap = false`（付けると jar がシステムのクラスローダーに足される）、
  Boot-Class-Path の呼び出し先のクラスなら `true`。

### 2.3 エージェントの jar の条件

- マニフェストに `Agent-Class`（`agentmain`）があること。後から付くので `premain` ではなく `agentmain` が呼ばれる。
  Eprotect+ とフラグ辞書のエージェントの jar には `Agent-Class` が書いてある（`build.gradle`）。`agentmain` の中身が後からの読み込みで正しく動くかは CODEX が確かめる。
- 後から付くので、もう読み込まれたクラスは再変換（`retransformClasses`）で包む必要がある。起動の引数で付けたときより効き始めが遅れる。
- 呼び出し先の jar を Boot-Class-Path に書くなら、エージェントの jar と同じフォルダに置かれる（取り出し先は同じフォルダ）。

## 3. 確かめ方

1. `./gradlew build`（MOD の jar に入ったかは `unzip -l build/libs/automata_valkyries-<版>.jar | grep agents/`）
2. `run/config/automata_valkyries-common.toml`（または自分の設定）で有効にして `./gradlew runGameTestServer`
3. `run/logs/latest.log` に `[agents] Attached the <名前> agent from the config` が出て、MOD 側の接続のログが続くこと。
   付けられなかったら `[agents] Could not attach the <名前> agent ...` に理由が出る
4. 確かめたら設定を元に戻す（既定は false）

## 4. 決まり

- `protect/AgentSelfAttach.java`・`agentcommon/Attacher.java`・`build.gradle` の守る側のエージェントの部分は Claude の担当。直したいところがあれば、
  直さずにユーザーへ伝える（AGENTS.md の決まり）。
- 入口のクラス `protect/plus/BundledAgents.java` と、`build.gradle` の自分のブロックに足す 2 行は CODEX の担当。
- 普通のランチャーに付いてくる Java に `jdk.attach` があるかは、まだ実機で確かめていない（2026-10-09 時点）。無ければ警告が出て、起動の引数で付けることになる。
