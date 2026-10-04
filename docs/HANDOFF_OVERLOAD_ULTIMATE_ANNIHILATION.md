# 依頼書: オーバーロード T4 兵装のチャージ攻撃を「超絶抹殺」へ格上げ（Antigravity 向け）

作成: 2026-10-05（Claude）。ユーザーの依頼「オーバーロード化ティア４兵装のチャージ攻撃を汎用・準汎用・特化の超絶抹殺に格上げ（プレイヤーだとアイテムをドロップさせる仕様を継続）」を、Antigravity に実装してもらうための仕様書。
Claude は抹殺の仕組み（`annihilate/`）を兵装に組み込む作業は行わない取り決めなので、この依頼書で引き継ぐ。兵装側（Claude の担当）の現状と、変えてほしいこと・変えないでほしいことをまとめる。

## 1. 今の作り（兵装側、Claude が作ったもの）

- オーバーロード化した T4 兵装のチャージ攻撃が当たったあと、各兵装が `weapon/Overload.overkill(victim, attacker)` を呼ぶ
  - 呼んでいる所: `RagnarokEdgeItem`（黄昏の斬撃）・`LuminousRequiemItem`（星の鎮魂歌）・`PunishmentRailgunItem`（極光のレール）・`BlackHoleEntity`（カタストロフ・バスターの空間崩壊）・`GenesisHaloItem`（軌道砲撃の光の柱）
- `overkill` は、撃った者がオーバーロードの T4 兵装を持っているときだけ `Overload.kill(victim, attacker)` を呼ぶ
- `Overload.kill` の今の中身（「超抹殺の棒」相当。2026-10-04 ユーザー指定）:
  1. 抹殺の棒の汎用の手段（`DebugKillStickItem.annihilateGeneric`）
  2. ふつうの死亡処理を通らずに消える相手への落とし物と経験値の補い（`DeathLootFallback.before` / `after`）
  3. 深い追い出しと同じ個体の再参加拒否（`SuperKillStickItem.evict` / `refuseRejoin`）
- 対象外: プレイヤー・保護対象（`IEprotected`。このMOD のボス・兵姫）・攻撃者自身。プレイヤーにはチャージ攻撃のカンストダメージがそのまま入り、ふつうに死んで持ち物を落とす

## 2. 変えてほしいこと

- `Overload.kill` の即死を、**超絶抹殺の棒（`item/UltraKillStickItem`）と同じ水準**（汎用・準汎用・特化）に上げる
  - 超絶抹殺の棒の単体の抹殺（`AnnihilationExecutionAgent` の単体版）を、チャージ攻撃の当たり 1 体ごとに使う想定
  - 攻撃者はプレイヤーとは限らない（戦術兵姫が兵装を持つ予定）。攻撃者がプレイヤーでないときの扱い（攻撃者の引数・ダメージの源）を決めて実装する
- **落とし物と経験値**: 今と同じく、倒した相手の落とし物と経験値が出るようにする（`DeathLootFallback` の考え方を続ける。抹殺の経路で落とし物が出ない相手にも補う）
- **演出**: チャージ攻撃は範囲で多くの敵に当たるので、1 体ごとの効果音・煙は控えめに（超絶抹殺の範囲版のように最後に 1 回だけでもよい）

## 3. 変えないでほしいこと（ユーザー指定）

- **プレイヤーには抹殺を使わない**: 今と同じく、チャージ攻撃のカンストダメージでふつうに死に、**持ち物を落とす**仕様を続ける
- **保護対象（`IEprotected`）は対象外のまま**: このMOD のボス（エレノア等）は被弾上限が優先。戦術兵姫は対象外（兵姫の守りはオートマタヴァルキリー protect。オメガコアの兵姫は超絶抹殺の棒までは耐える、とユーザーが確認済み）
- **オーバーロードしていない兵装・T4 でない兵装には何もしない**（`Overload.overkill` の条件はそのまま）
- 通常攻撃（左クリック）の即死化はしない。通常攻撃は今の「最大 HP の 1% の割合ダメージ・ウィザーの無敵の貫通」のまま

## 4. 確認してほしいこと（GameTest）

今ある GameTest（`TrialGameTests` の `Overload.overkill` を使う試験）が通ったまま、次を足してほしい:

1. オーバーロードの T4 兵装の持ち主のチャージ攻撃（`Overload.overkill`）で、ふつうの mob が消え、同じ UUID で戻ってこない
2. その mob の落とし物と経験値が出る
3. プレイヤー（モックのプレイヤー）は抹殺されず、ダメージで死んで持ち物を落とす
4. エレノア（保護対象）・戦術兵姫は抹殺されない
5. オーバーロードしていない兵装・T4 でない兵装の持ち主のときは何も起きない

## 5. 関係するファイル

- 兵装側（Claude）: `weapon/Overload.java`（`overkill` / `kill`）、`weapon/DeathLootFallback.java`、上の 5 つの兵装
- 抹殺側（Antigravity）: `annihilate/AnnihilationExecutionAgent.java`、`item/UltraKillStickItem.java`
- 仕様書: `docs/SPEC_WEAPON_UNITS.md` §3.5.5（回想のトライアルとオーバーロード化）・§3.5.8

`Overload.kill` の中身の差し替えは Antigravity 側で行ってよい（兵装側の他の所は変えなくてよい）。差し替えたら §3.5.5 の即死の説明と、説明文（`lang` の `gui.automata_valkyries.overload_t4`「チャージ攻撃が即死（超抹殺）」）も合わせて直してほしい。
