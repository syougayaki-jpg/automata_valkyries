【libs フォルダの使い方】
ここにテストしたい外部 MOD の .jar ファイルを配置してください。
Gradle が自動的にデオブフ（難読化解除）し、開発環境のゲーム（runClient）へ MOD として読み込みます。

※もし配置した MOD が Java エージェント（MANIFEST に Premain-Class / Agent-Class）を含んでいる場合、
  自動的に検知され、JVM の -javaagent 起動引数にも追加されます。
