package com.automatavalkyries.entity;

/**
 * 終焉の権能（Terminus Power）を搭載・行使できるエンティティのインターフェース。
 * <p>
 * このインターフェースを実装した MOB は、攻撃時に対象（プレイヤー含む）へ
 * Java エージェント連携の絶対即死・真抹殺（Terminus）を付与・執行できる。
 * </p>
 */
public interface ITerminusBearer {

    /** 終焉権能が有効化されているか */
    boolean isTerminusEnabled();

    /** 終焉権能の有効／無効を設定 */
    void setTerminusEnabled(boolean enabled);
}
