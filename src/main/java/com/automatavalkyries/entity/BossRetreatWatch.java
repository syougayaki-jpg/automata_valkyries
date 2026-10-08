package com.automatavalkyries.entity;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * ボスの「挑む相手がいなくなったら退く」見張り。プレイヤーと交戦した後、範囲に戦えるプレイヤー（クリエイティブ・観戦・倒れた人は数えない）が
 * いない時間が続いたら退く時と答える。退き方（演出・セリフ）はボスの側で決める
 */
public final class BossRetreatWatch {

    /** 見る間隔（Tick） */
    private static final int CHECK_INTERVAL = 20;

    private final double range;
    private final int delayTicks;
    /** プレイヤーと交戦したか（交戦する前は退かない） */
    private boolean engaged;
    private int noChallengerTicks;

    /**
     * @param range      この範囲に戦えるプレイヤーがいなくなったら「全滅」とみなす
     * @param delayTicks 全滅とみなしてから退くまでの猶予（Tick）
     */
    public BossRetreatWatch(double range, int delayTicks) {
        this.range = range;
        this.delayTicks = delayTicks;
    }

    /** 毎 Tick（サーバー側）。退く時が来ていたら true（退くまで true を返し続ける） */
    public boolean tick(Mob boss) {
        if (boss.getTarget() instanceof Player) engaged = true;
        if (!engaged || boss.tickCount % CHECK_INTERVAL != 0) return false;
        boolean challenger = boss.level().getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), range,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE)) != null;
        if (challenger) {
            noChallengerTicks = 0;
            return false;
        }
        noChallengerTicks += CHECK_INTERVAL;
        return noChallengerTicks >= delayTicks;
    }
}
