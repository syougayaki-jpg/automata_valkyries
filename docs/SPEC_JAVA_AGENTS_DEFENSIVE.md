# Java エージェントの地図（守る側）

このMOD の Java エージェントのうち、**守る側**（このMOD 自身と戦術兵姫を守るもの）の全体像。2026-10-08 に整理した。

- Eprotect+ とフラグ辞書のエージェント（`src/eprotectplus/`）、真抹殺の棒などの**倒す側**は CODEX（旧 Antigravity）の担当。この資料では名前と境界だけ書く（中身は [`SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md`](SPEC_JAVA_AGENT_TERMINUS_AND_EPROTECT_PLUS.md)、引継書は [`HANDOFF_ANTIGRAVITY_TO_CODEX.md`](HANDOFF_ANTIGRAVITY_TO_CODEX.md)）
- ゲーム内の保護（Eprotect・Mixin・毎 Tick の見張り）は [`SPEC_EPROTECT_ARCHITECTURE.md`](SPEC_EPROTECT_ARCHITECTURE.md)。エージェントはその外側の守りで、**無くてもゲーム内の層は動く**

## 1. 一覧

| エージェント | 起動の切り替え | jar（`build/libs/`） | 書き換えるクラス | 役目 |
|---|---|---|---|---|
| 観察者（Observer） | `-PwithObserver` | `automata_valkyries-observer.jar` | このMOD のクラスだけ（改ざん防止で元に戻すとき） | 他のエージェントの再変換・再定義と呼び出し元の記録、他 MOD のクラスの「できる作り」の地図、このMOD のクラスの改ざん防止（`IntegrityGuard`。引数 `noguard` で切れる） |
| LifeGuard（真ライフ） | `-PwithLifeGuard` | `automata_valkyries-lifeguard.jar` ＋ `-lifeguard-hooks.jar` | vanilla の `LivingEntity` | getHealth / isDeadOrDying / isAlive の戻り際を包み、保護対象だけ実際の値に直す（`protect/TrueLife`） |
| オートマタヴァルキリー protect | `-PwithValkyrieProtect` | `automata_valkyries-valkyrie-protect.jar` ＋ `-valkyrie-protect-hooks.jar` | vanilla の `Entity` / `LivingEntity` | remove / setRemoved / kill / die / setHealth の先頭で問い合わせ、戦術兵姫だけ消去を断り即死を機能停止に置き換える（`protect/valkyrie/ValkyrieAgentFilter`） |
| （CODEX / 旧Antigravity）Eprotect+ | `-PwithEprotectPlus` 等 | `automata_valkyries-eprotect-plus.jar` | — | 担当外（CODEX担当） |
| （CODEX / 旧Antigravity）フラグ辞書のエージェント | `-PwithFlagAgent` 等 | `automata_valkyries-flag-agent.jar` | — | 担当外（CODEX担当） |

エージェントではないが関係するもの:
- `protect/AgentHijackGuard`: 他のエージェントの書き換えを捨てる MOD への備え。SuperSteve の安全モード（`supersteve.safemode=true`）を、どの MOD より先（`mixin/AutomataMixinPlugin#onLoad`）に立てる。`-Dautomata_valkyries.superSteveSafemode=false` で立てない（§5）

## 2. つながり方

```
エージェント jar（システムのクラスローダー）          hooks jar（ブートストラップ。JDK だけ）        MOD 本体（ゲームのクラスローダー）
  LifeGuardAgent ── LivingEntity に呼び出しを差し込む ──▶ LifeHooks.health / deadOrDying / alive ◀── filter を入れる: LifeGuardLink → LifeGuardInstaller（TrueLife）
                  └ ensureLast / status を入れる ─────▶ LifeHooks.ensureLast / status        ◀── /eprotect の表示・並べ直しの依頼
  ValkyrieProtectAgent ── Entity/LivingEntity に差し込む ─▶ ValkyrieHooks.refuse / refuseHealth ◀── filter を入れる: ValkyrieAgentFilter
  ObserverAgent ── ObserverBridge（記録・状態） ◀──────────────────────────────────────────── ObserverLink（/eprotect observer、GameTest）
```

- hooks をブートストラップに置くのは、vanilla のクラス（どのクラスローダーからも見える必要がある）から呼ぶため。hooks は JDK 以外に依存しない
- MOD 側はエージェントが有ると確かめてから hooks に触る（無い環境で参照すると読み込みに失敗する）

## 3. 共通部品（`src/agentcommon`）

守る側の 3 つの jar に同梱する（JDK だけ）。

| クラス | 役目 |
|---|---|
| `InHouse` | 今の再変換・再定義を呼んだのがこのMOD 自身か（呼び出し履歴で `Instrumentation` のすぐ外側を見る）。このMOD 同士の再変換を他のエージェントのものと数えない |
| `OutermostKeeper` | 自分の変換器を同じ `Instrumentation` の中でいちばん最後（いちばん外側で包む位置）に保つ。他のエージェントの再変換を見たら 0.5 秒まとめてから並べ直して再変換する。上限 64 回 |
| `AgentLog` | 標準出力と `logs/<名前>.log` の両方に 1 行ずつ書く |

## 4. 起動の順番と、このMOD 同士の決まり

- 開発の起動（`build.gradle` の runs）は **観察者 → LifeGuard → オートマタヴァルキリー protect** の順に `-javaagent` を付ける。観察者がいちばん先なのは、このMOD のクラスの「読み込んだときの形」を、ほかのエージェントが手を入れる前に覚えるため
- **このMOD 同士の再変換は改ざんでも他のエージェントでもない**（`InHouse`）。数えると LifeGuard とオートマタヴァルキリー protect が互いに並べ直し合い、上限まで回っていた（2026-10-05）
- 例外が 2 つある（2026-10-08 に直した。GameTest の `integrityGuardRestoresOwnClasses` がずっと落ちていた）
  - 改ざん防止が**戻すための自分の再変換**（`IntegrityGuard.maintain`）は通す
  - 改ざん防止の**自己点検**（他のエージェントの書き換えの真似）は改ざんとして数える
- LifeGuard とオートマタヴァルキリー protect はこのMOD のクラスを書き換えない（改ざん防止が戻して、並べ直しと戻しが延々と続くため。2026-10-03）

## 5. 限界（できないこと）

- `OutermostKeeper` が外側を取れるのは**同じ `Instrumentation` の中だけ**。後から attach した他のエージェント（別の `Instrumentation`）の変換器はさらに後に回るので、同じ再変換の中では向こうが最後に決める
- Unsafe でのメモリの直接書き込み、JVM の終了（`Runtime.halt` / `System.exit`）は原理的に止められない
- 他のエージェントの仕組みそのものを乗っ取る MOD がある:
  - **SuperSteve**（PLZLiZi）: JDK の `InstrumentationImpl` を書き換え、自分以外のエージェントの変換結果を捨てる。安全モードでは乗っ取らないので、`AgentHijackGuard` が先に立てる。安全モードでは武器の登録名が `supersteve:endofplz_lite` から `supersteve:endoftwdr` に変わる（以前のワールドの武器は読めなくなる）。2026-10-08 に `-PwithSuperSteve` で確認: 立てる前は LifeGuard の GameTest が落ち、立てた後は通った
  - **Madness Entities**: 本体を暗号化して隠し、Unsafe での HP の直接書き込みとワールドの一覧からの除去で倒す。自分の変換器を外されたり既知のチート MOD（`pig2` 等）を見つけたりすると `Runtime.halt` でゲームを終わらせる作り。**Madness の書き換えを外す対策は入れない**。開発環境では起動できない（隠したクラスを毎回ランダムな名前のパッケージに置き、それが見つからない）
  - **PIG2**: 開発環境では起動できない（SRG 名の Mixin と改ざん検知）

## 6. 確かめ方

| 目的 | コマンド |
|---|---|
| 守る側を全部付けて GameTest | `./gradlew runGameTestServer -PwithObserver -PwithLifeGuard -PwithValkyrieProtect` |
| Eprotect+ との境界（並べ直し合いが無いか） | 上に `-PwithEprotectPlus` を足す。`logs/automata_valkyries-lifeguard.log` / `-valkyrie-protect.log` の「moving to the outermost」が増え続けなければよい |
| 乗っ取る MOD との組み合わせ | `-PwithSuperSteve`（`run/mods/dev-*.jar` に置いて読む） |

GameTest: `ObserverGameTests`（改ざん防止の自己点検・SuperSteve の安全モード）、`lifeguardReportsTrueLife…`、`ValkyrieGameTests`（エージェントの層）。エージェントが付いていなければ素通りで成功する。

2026-10-08 の結果（守る側 3 つ＋Eprotect+）: 並べ直しは 0 回、改ざん防止が戻したのは自己点検のクラスだけ。落ちたのは前からの羊のテストと、Eprotect+ の `retransformEnforcerAndStackCloaking`（Eprotect+ だけで起動しても落ちる。Antigravity 側）。
