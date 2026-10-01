package com.automatavalkyries.protect.attack;

/** 記録する操作の名前（ログ・コマンドでそのまま表示する。grep しやすいよう英小文字の固定名） */
public final class AttackOp {

    public static final String REMOVE = "remove";                         // + ":" + 消去理由
    public static final String DISCARD = "discard";
    public static final String REMOVED_FROM_WORLD = "removed_from_world";
    public static final String UNSAFE_POSITION = "unsafe_position";
    public static final String KILL_DAMAGE = "kill_damage";               // + ":" + ダメージの種類
    public static final String HEALTH_LOSS = "health_loss";
    public static final String DIE = "die";
    public static final String NOVELIZE = "novelize";
    public static final String FORCED_RELOCATION = "forced_relocation";
    public static final String DIMENSION_CHANGE = "dimension_change";
    public static final String SYNCHED_DATA = "synched_data";             // + ":" + 同期データの鍵の名前
    public static final String SPOOFED_READ = "spoofed_read";             // + ":" + 偽装された getter
    public static final String DAMAGE_PENALTY = "damage_penalty";
    public static final String COMPAT = "compat";                         // + ":" + 互換 Mixin の操作名
    public static final String NEUTRALIZED = "neutralized";               // + ":" + 即死 MOD の対策名
    public static final String REPAIR = "repair";                         // + ":" + 修復の種類
    public static final String RETREAT = "retreat";

    private AttackOp() {}
}
