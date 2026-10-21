# CODEX 準汎用化の実装記録

2026-10-08。`HANDOFF_CODEX_GENERALIZATION.md` の5項目を実装。

- `ForeignStateCleaner`: AABB のいずれかの軸が 0.001 未満なら寸法を再計算し、乗客以外の `noPhysics` を解除。修復を攻撃ログに記録。
- `KillLists`: HP 改ざん分類と health/hp 一覧名による探索を追加。守りを表す名前を除外し、既存のパージで対象の個体・UUID をキーと値から除去。
- `ModProtectionBreaker`: 他 MOD の対象と、対象を指し返す非 Entity のコントローラーに対し、切断、ボスバー解放、戦闘停止、ターゲット参照解除を実行。コントローラーの削除前と抹殺後にも後始末。静的メンバーは操作しない。
- `GeneralizationGameTests` / `FakeCleanupBoss`: 単軸の当たり判定崩壊、物理復元と乗客除外、HP Map のキー・値・UUID 除去と無関係な要素・守り一覧の保持、ボスとコントローラーの後始末を検証する3件を追加。

## 検証

| 実行 | 結果 |
|---|---|
| `build --no-daemon` | 成功 |
| `runGameTestServer --no-daemon` | 323件中322件成功 |
| `runGameTestServer -PgenericOnly --no-daemon` | 323件中322件成功 |
| `runGameTestServer -PwithObserver -PwithLifeGuard -PwithValkyrieProtect -PwithEprotectPlus --no-daemon` | 323件中321件成功 |

追加した3件はすべてのモードで成功。通常・準汎用モードの失敗は既知の `overloadT4AnnihilationDropsLootAndExp` のみ。併用モードでは既知の `retransformEnforcerAndStackCloaking`（特権フラグ漏れ）も失敗。併用ログに `moving to the outermost` はなく、並べ直しループは確認されなかった。

実行ログは `build/codex-generalization-default.log`、`build/codex-generalization-generic.log`、`build/codex-generalization-agents.log`（ビルド成果物であり Git 管理外）。

## 継続課題

特権フラグ漏れ、クライアント側の誤消去、バイトコード再監査の負荷は今回の5項目とは別に残る。Claude の担当ファイルと作業開始時からある未コミット変更には手を入れていない。
