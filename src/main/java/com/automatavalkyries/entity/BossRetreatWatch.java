package com.automatavalkyries.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ボスの「挑む相手がいなくなったら退く」見張り。退く時と答えるのは次のどちらか（退き方・セリフはボスの側で決める）:
 * <ul>
 *   <li>交戦したプレイヤーが全員倒れた（2026-10-09 ユーザー指定「プレイヤーが死んでも撤退しない」。近くで生き返ると「相手がいない」にならず、
 *       何度倒しても居座っていた）。倒れている間（死亡画面の間）に見るので、毎 Tick 見る</li>
 *   <li>交戦した後、範囲に戦えるプレイヤー（クリエイティブ・観戦・倒れた人は数えない）がいない時間が続いた</li>
 * </ul>
 */
public final class BossRetreatWatch {

    /** 範囲を見る間隔（Tick） */
    private static final int CHECK_INTERVAL = 20;

    private final double range;
    private final int delayTicks;
    /** 交戦したプレイヤー（交戦する前は退かない） */
    private final Set<UUID> engaged = new HashSet<>();
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
        if (boss.getTarget() instanceof Player player && !player.isCreative() && !player.isSpectator()) engaged.add(player.getUUID());
        if (engaged.isEmpty()) return false;
        if (allEngagedDown(boss)) return true;
        if (boss.tickCount % CHECK_INTERVAL != 0) return false;
        boolean challenger = boss.level().getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), range,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE)) != null;
        if (challenger) {
            noChallengerTicks = 0;
            return false;
        }
        noChallengerTicks += CHECK_INTERVAL;
        return noChallengerTicks >= delayTicks;
    }

    /** 交戦したプレイヤーが 1 人以上倒れていて、まだ戦える人（同じ世界の範囲内で生きている人）が残っていないか */
    private boolean allEngagedDown(Mob boss) {
        MinecraftServer server = boss.getServer();
        if (server == null) return false;
        boolean anyDown = false;
        for (UUID id : engaged) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue; // 抜けた人は数えない
            if (player.isDeadOrDying()) {
                anyDown = true;
            } else if (player.level() == boss.level() && !player.isCreative() && !player.isSpectator()
                    && player.distanceToSqr(boss) <= range * range) {
                return false;
            }
        }
        return anyDown;
    }
}
