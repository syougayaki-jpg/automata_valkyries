# 即死MODの実装調査（2026-10-08、CODEX）

## 調査対象と方法

取得済みの配布 jar を Java 17 の `javap -p -c` で静的解析した。MOD 本体の起動や attach はしていない。最新版全般の仕様ではなく、次の2つのバイナリについての記録である。

| 対象 | バージョン | SHA-256 |
|---|---|---|
| Nekohashizuku Blade（猫雫） | 3.7.0 | `4B86C8832A971A19BF5EFCD19EE5D9B86DD19EE32890C0DF46708F7BC708739E` |
| SuperSteve | 6.10.5pr | `BE0E7418A02A7F23FC5AAAE90774D4CE35ECEC66B0AA1955ED155039FC12C773` |

保存先は Gradle の `modules-2/files-2.1/maven.modrinth/`。解析出力は `build/codex-research/` の `shizuku.javap.txt`、`shizuku-bootstrap.javap.txt`、`supersteve.javap.txt`、`supersteve-agent.javap.txt`。生の逆アセンブル出力はビルド成果物として扱い、Git には追加しない。SRG 名の対応は `build/createMcpToSrg/output.tsrg` と `.mcsrc/` で確認した。

## 1. 猫雫: 読み取り偽装・継続的なHP制限・同期データ書き換えは別経路

### 1.1 外部のHP表

`com.buwan.shizuku.core.AgentHealthMethodHelper` は、個体をキーとする `HEALTH_MAP` に HP と最大HPを保持する。実体は `Collections.synchronizedMap(new WeakHashMap<>())`。`forceSetHealth` はこの表へ書き込む。`agentGetHealth` / `agentGetMaxHealth` は登録された値を返し、未登録・null の場合は `-1F` を返す。

ここで変更されるのは外部の表であり、このメソッド単体からバニラのHP値を書き換える命令は見つからない。したがって、外部表と同期データのHPを別々に観測する必要がある。

### 1.2 getter に挿入する分岐の注意点

`AgentHealthTransformer$AgentHealthClassVisitor` は、`()F` の `getHealth` / `ssGetHealth` / `m_21223_` と `getMaxHealth` / `m_21233_` に `HealthMethodVisitor` を付ける。

`HealthMethodVisitor.visitCode` が生成する命令は、概念的には次の分岐になる（実際には helper を2回呼ぶ）。

```text
helper(this) と -1F を FCMPL で比較
結果が 0 以上なら元のメソッドへ進む（IFGE）
それ以外なら helper(this) を FRETURN
```

この命令列では、登録値が `0F` や正の値でも元のメソッドへ進む。`-1F` も元のメソッドへ進む。`-1F` 未満や NaN では早期 return 側へ進む。一般的な「未登録の -1 だけ除外し、登録値は何でも返す」という説明と一致しない。

これはバイトコードからの解釈であり、実際の LivingEntity の変換結果を実行した検証ではない。他の変換・Mixin・別の HealthModify 経路が最終結果を変える可能性がある。特に「表へ0を入れれば getter が0になる」と断定してテストを組まないこと。

`AgentBootstrap.premain` は `addTransformer(transformer)` の1引数版で登録し、`agentmain` も `premain` を呼ぶ。調査した登録経路では再変換対応フラグを立てていない。後から attach するだけで読み込み済みクラスが変わるとは限らない。登録方式による変換順と再変換時の再利用規則は [Java 17 ClassFileTransformer](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html) を参照。

### 1.3 UUIDをキーにしたHP制限

`com.buwan.shizuku.core.HealthLockManager` は別の `lockedEntities: Map<UUID, LockedHealthData>` を持つ。データにはHPフィールドへのアクセス情報が入っている。

`checkAndFixFields` は、そのフィールドの現在値が `lockedHealth` より大きければ指定値へ戻す。厳密には上限固定であり、低い値を常に引き上げる処理ではない。`forceSetLockedFields` は指定値を各フィールドへ書く。

従って `HEALTH_MAP` だけを外しても別のHP制限が残る可能性がある。前回追加した名前分類では `HealthLockManager` のクラス名が health + lock に一致するが、実際に発見経路へ入るかは別途検証が必要。`KillLists` は全クラスを無制限に探索する作りではない。

### 1.4 HP以外の同期値も巻き込む経路

`UltimateHealthDestroyer.executeEntityDataDestruction` は `SynchedEntityData` の内部表を反射で読み、数値の DataItem を走査する。Float・Double・Integer の値を指定値へ直接書く。調査したループには、HP用のアクセサーに限定する条件も、同クラスにある `isReasonableHealthValue` を呼ぶ命令もない。

HPの復元だけでは、別の数値の同期状態に残る変更を説明できない可能性がある。副作用の具体的な範囲は対象の DataItem 構成次第であり、実機では未検証。

## 2. SuperSteve: 個体の削除と空間条件による再消去

### 2.1 `killEntity` と `simpleKillEntity` の役割

`SSUtil.killEntity(Entity, boolean)` は null・Player・ItemEntity を早期除外する。boolean はこの除外を解除しない。このメソッドだけから「通常のプレイヤーも同じ経路で消す」とは言えない。

通常の対象に対しては `D_ENTITIES` に個体を登録し、`D_SRT_ENTITIES` にエンティティ種別・座標・半径 `2D`・期限（現在の実時間 + `4000ms`）を持つ `SRTEntry` を追加する。クライアントへの独自消去パケットを送り、`simpleKillEntity` へ進む。

`simpleKillEntity` は `remove()` にまとめて任せず、次の状態と一覧を直接変更する。

- `removalReason = DISCARDED`、`noPhysics = true`、`canUpdate = false`、`invulnerable = false`。
- AABB を現在位置の一点にする。乗客一覧を空にし、`levelCallback` を NULL にする。
- LivingEntity では `dead = true`、`deathTime = 0`。
- ChunkMap の追跡表、knownUuids、EntityLookup の ID/UUID 表、EntitySection の一覧、EntityTickList の active 表などをコピーして対象を除き、コピーをフィールドへ差し戻す。
- マルチパートの部位やナビゲーション集合にも除去処理がある。
- `RmCEntityL` という独自パケットを送る。

これはダメージや死亡イベントの入口だけを拒否しても止められない経路。今回確認した Tick 除去は active 表への処理であり、pending 表まで一律に除去するとは断定しない。クライアント側のパケット受信実装は今回の解析対象に含めていない。

### 2.2 UUIDを変えて復元しても、空間条件に一致する可能性

`SSUtil.dsrtEntities(Iterable<?>)` は期限切れを除いた SRTEntry を種別ごとに分類し、渡された個体の種別と距離を比較する。距離の二乗が半径の二乗以下なら `simpleKillEntity` を呼ぶ。

この比較は UUID を使っていない。復元された個体だけでなく、同種で近くにいる別個体も条件に一致し得る。呼び出し頻度と実際に渡される個体集合は未調査であり、「4秒間必ず毎Tick消す」とは断定しない。

`StaticReferencePurger` の個体・UUID・Reference の照合だけでは SRTEntry のような種別/位置による予約を除去できない。名前だけを広く分類しても解決しない。対象範囲の誤拡大を避けるため、空間予約は専用の観測・再現テストを先に作るべき。

### 2.3 エージェント干渉の比較対象

`AgtCallback.afterTransform` は、引数の Instrumentation と静的 `INST` が同一なら変換結果を返し、別のインスタンスなら null を返す。`shouldTransform` は常に true。

これは変換結果の内容ではなく Instrumentation の同一性で分ける。通常の Transformer が null を返す意味と、JDKの変換ディスパッチへ独自フックを入れて結果を捨てる意味は区別が必要。今回確認したのは callback の分岐であり、JDKへの差し込み箇所全体を再検証したわけではない。既存の安全モード対策は維持する。

## 3. このMODの「特権」「スタック偽装」の説明を再評価

`EprotectPlusBridge.runCloaked` は ThreadLocal を立てて `AccessController.doPrivileged` から action を実行する。別スレッドへの移動や getStackTrace の書き換えは、このメソッドにはない。

Java 17 で `build/codex-research/PrivilegeProbe.java` を実行し、doPrivileged 内の `Thread.currentThread().getStackTrace()` に外側の呼び出し元が残ることを確認した。結果は `callerVisibleInsideDoPrivileged=true`。

`doPrivileged` は権限検査の境界に関するAPIであり、呼び出し履歴を消す機能ではない。[Java 17 セキュリティアーキテクチャ](https://docs.oracle.com/en/java/javase/17/security/java-se-platform-security-architecture.html) と [Thread.getStackTrace](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#getStackTrace()) を参照。特定MODの StackChecker を実際に通過できるかは、その検査実装で測る必要がある。

また、別クラスローダーや ThreadLocal の true は、このMODが独自に使う境界・協調用フラグであり、それ自体を Unsafe に対する物理的隔離の証明にはできない。既存の仕様書の「完全迂回」「物理的隔離」は確認済みの保証として扱わない。共有仕様書は今回変更していない。

### 既知の特権フラグ漏れの原因候補

`EprotectPlusLink.runPrivileged` と `runWithCloakedStack` は LOCAL_PRIVILEGED を true にした後、エージェント経由の呼び出しが成功すると return する。その return はローカルフラグを戻す finally の外にある。これは既知の GameTest 失敗と整合する原因候補。

さらに `Method.invoke` の例外を一括で握りつぶしてローカル実行へ進むため、action が一度実行されて例外を出した場合に再実行され得る。修正時には正常・例外・入れ子・エージェントなしを分け、実行回数とフラグ復元を検証する必要がある。今回は研究として原因を記録し、製品コードは変更していない。

## 次に調べる項目

1. 猫雫の別 HealthModify 経路と、UUID HP制限を呼ぶ側・頻度・クラスローダー。
2. 猫雫の実際の変換済み getter の分岐を小さな隔離された再現で確認。
3. SuperSteve の SRTEntry 呼び出し元とクライアント消去パケット受信処理。
4. 上記の個体除去と空間予約を別々に再現し、`genericOnly` の復元が再消去される条件を測る。
5. Eprotect+ の特権フラグ復元と action の二重実行を修正・検証する。

Madness の変換器を外す対策は追加しない。今回の研究で新しい外部MODの起動、既存依存の削除、共有ファイルの編集、コミットは行っていない。
