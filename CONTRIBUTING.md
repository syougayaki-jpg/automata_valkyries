# 貢献のしかた / Contributing

## 日本語

不具合の報告・要望は Issues へどうぞ。報告のときは、MOD と Forge のバージョン、一緒に入れている MOD、ログを書いてください。

### 開発環境

- Java 17、Minecraft 1.20.1、Forge 47.4.x（マッピングは Mojang 公式名）
- ビルド: `./gradlew build`（Windows は `.\gradlew.bat build`）。成果物は `build/libs/`
- ロード確認: `./gradlew runGameTestServer`（サーバーが起動して GameTest を回し、自動で終わる）
- 手動確認: `./gradlew runClient`

### プルリクエスト

- `./gradlew build` が通ること。登録やリソースを触ったら `runGameTestServer` も通すこと
- 翻訳は `en_us.json` と `ja_jp.json` の両方に足すこと
- レジストリ名は小文字スネークケース（`valkyrie_core` など）

### ライセンス

コードは [MPL-2.0](LICENSE.txt) です。プルリクエストのコードは MPL-2.0 で提供したものとして扱います。
モデル・テクスチャ・音・キャラクターなどのアセットは All Rights Reserved です（[`LICENSE-ASSETS.txt`](LICENSE-ASSETS.txt)）。アセットの変更を含むプルリクエストは、事前に Issue で相談してください。

## English

Please use Issues for bug reports and requests. Include the mod and Forge versions, your other mods, and logs.

### Development

- Java 17, Minecraft 1.20.1, Forge 47.4.x (official Mojang mappings)
- Build: `./gradlew build` (Windows: `.\gradlew.bat build`). Jars go to `build/libs/`
- Load check: `./gradlew runGameTestServer` (starts a server, runs the GameTests and exits)
- Manual check: `./gradlew runClient`

### Pull requests

- `./gradlew build` must pass. If you touch registries or resources, run `runGameTestServer` too
- Add translations to both `en_us.json` and `ja_jp.json`
- Registry names are lower snake case (e.g. `valkyrie_core`)

### License

Code is licensed under [MPL-2.0](LICENSE.txt). Code in pull requests is accepted under MPL-2.0.
Assets such as models, textures, sounds and characters are All Rights Reserved ([`LICENSE-ASSETS.txt`](LICENSE-ASSETS.txt)). Please open an issue before sending a pull request that changes assets.
