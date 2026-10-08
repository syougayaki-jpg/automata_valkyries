# 終焉機構の修正（2026-10-09）

対象は `REVIEW_TERMINUS_2026_10_08.md` の指摘を受けてユーザーが修正を指示した6項目。Claude の共有ファイル・守る側エージェント・検証用依存は変更していない。コミットには終焉修正と必要な退去確認・前処理の差分を含め、前回のEprotect+改良の残りは分離した。

## 変更

1. **保存の保持**: GenericCheatMobNeutralizer の MOD フォルダ再帰削除を撤去。Terminus の範囲処理から全体の保存・常駐一覧の清掃を除去。単体 Flashfur 対策からも全保存・全復活キューの清掃を外した。未確認の保存形式の所有をファイル名から推測せず、現在は保存を保持する。
2. **共有物の保護**: Generic と ModProtectionBreaker で同じコントローラー選別を使用。対象への直接逆参照があり、別個体への参照がなく、フィールドを読めるものだけを終了対象にする。Collection/Map は対象・対象UUIDのエントリーだけを除去する。明示的に渡された standalone core (`dismantleCoreObject(core, null)`) の従来の操作は維持する。
3. **型別書き込み**: Unsafe の byte/short/char を、それぞれ putByte/putShort/putChar に変更。防御値の Integer/Long/Float/Double は Field.set で型に合った値を入れる。
4. **耐性追跡**: 同じ実体の再攻撃は最初の試行を保持し、同UUIDの新実体は別記録にする。200Tickまたは単調時計の10秒で判定。他の強化が有効なら要求を保留する。MOBの退去確認は DirectRead とワールド検索、プレイヤーは死亡検証を使う。サーバー停止時に記録と範囲キューを清掃する。
5. **プレイヤーの成功判定**: 同期データと getter の両方で有限HP0以下を確認し、修復失敗は false。die の例外終了も false。接続中のプレイヤーでは vanilla の死亡統計更新を確認して、キャンセルされた死亡フックとHP0を区別する。pending_respawn 中の再要求は死亡イベントや通知を繰り返さない。修復は書き込み前の dead を読む。待機中の異常修復を20Tick間隔にする。新しい死亡完了印はリスポーン/Cloneで外す。
6. **範囲処理**: 魔杖の半径500・AABB・プレイヤー対象切替は維持し、対象をキューで処理する。全要求を合わせて最大4件/Tick、約4msで次Tickへ持ち越す。1対象の処理を途中で中断する時間上限ではない。同じ発動者の要求は集約し、アイテムに20Tickのクールダウンを付ける。別次元・消去済み・UUIDの別実体は実行しない。終了時に従来の件数通知を送る。

MOBの本経路は Terminus → TrueAnnihilationEngine に集約した。Generic のゼロクリアを先に行わず、ModProtectionBreaker がコントローラーを保持し、外部MOD自身の終了処理→残った対象所有状態の解除→本体消去→退去確認の順に進む。終了メソッドは重複して呼ばない。

## 回帰テスト

`TerminusRegressionGameTests` に6件を追加した。

- 共有・対象外コントローラーを閉じず、所有コントローラーのboxed値を解除する。統合した実行経路でも確認する。
- 既知フォルダと外部MODフォルダに置いた一意な試験ファイルを残す。試験後は作成したファイルだけを消す。
- 30Tickごとの再登録でも200Tickを超えて耐性を検知する。他の強化中に到達した要求も後で実行する。
- 再要求でdieを繰り返さず、getter偽装、同期HPのNaN/Infinity、die例外を成功扱いしない。直接修復でもdieを飛ばさない。
- 12体の範囲処理を複数Tickに分散し、4件/Tickを超えず、再要求を集約する。
- 狭いprimitiveフィールドの消去時にも、vanillaの基底状態とUUIDを保持する。

## 残る範囲

所有の分からないMOD保存を保持するため、それらの保存による復活をディスク削除で抑止する保証はない。対応形式を確認した対象限定アダプターは今後の課題。

同期の `executeAreaTerminus` は即時の件数を必要とする既存呼び出し・小範囲テスト用に残した。実際の魔杖操作は `queueAreaTerminus` を使う。対象の最初のロード済み一覧取得は同期で行う。

接続クライアントでの死亡画面→リスポーン→操作復帰は今回のGUIなしGameTestでは未確認。既知MOD処理のgenericOnly切替漏れと、遅延したクライアント通知の実体ID照合は今回の6項目と別の残課題。旧全体清掃APIの他アイテムからの呼び出しも、この終焉経路の修正とは別に残る。

## 最終検証

| 構成 | 結果 | ログ |
|---|---|---|
| `./gradlew build --no-daemon` | BUILD SUCCESSFUL（15秒） | `build/codex-terminus-build-final.log` |
| `./gradlew runGameTestServer --no-daemon` | 337件中334件成功。追加6件すべて成功 | `build/codex-terminus-gametest-final.log` |
| `./gradlew runGameTestServer -PgenericOnly -PwithEprotectPlus -PwithObserver -PwithLifeGuard -PwithValkyrieProtect --no-daemon` | 337件中334件成功。追加6件すべて成功 | `build/codex-terminus-agents-generic-final.log` |

両構成の失敗は `overloadT4AnnihilationDropsLootAndExp`（以前からの羊のドロップ不具合）、`everyTierHasMachineRecipesAndKits` / `upgradeRecipesFollowConfig`（現在の upgrade_kit_future 設定に依存）。今回の修正で新しい失敗は出ていない。全GameTestが成功したとは扱わない。担当境界と変更範囲を守り、この3件は変更していない。

検証は前回のEprotect+改良を含む作業ツリーで実施した。切り分けた今回のコミットだけをクリーンな作業ツリーへ取り出しての検証は行っていない。

対象ファイルの `git diff --check` は成功。
