package com.automatavalkyries.entity.ai;

import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.entity.ITerminusBearer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * 終焉攻撃 AI Goal（Terminus Attack Goal）。
 * <p>
 * 終焉の権能を持つ MOB がターゲットに近接攻撃をヒットさせた際、
 * 通常のダメージ計算を完全に超越して {@link TerminusEngine#executeTerminus} を執行する。
 * </p>
 */
public class TerminusAttackGoal extends MeleeAttackGoal {

    private final PathfinderMob mob;

    public TerminusAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
        this.mob = mob;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity enemy, double distToEnemySqr) {
        double attackReach = this.getAttackReachSqr(enemy);
        if (distToEnemySqr <= attackReach && this.isTimeToAttack()) {
            this.resetAttackCooldown();
            this.mob.swing(this.mob.getUsedItemHand());

            if (this.mob.level() instanceof ServerLevel level) {
                boolean executeTerminus = true;
                if (this.mob instanceof ITerminusBearer bearer) {
                    executeTerminus = bearer.isTerminusEnabled();
                }

                if (executeTerminus) {
                    TerminusEngine.executeTerminus(level, this.mob, enemy);
                } else {
                    this.mob.doHurtTarget(enemy);
                }
            }
        }
    }
}
