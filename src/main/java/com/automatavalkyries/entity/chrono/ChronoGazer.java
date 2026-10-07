package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * 敵「クロノゲイザー」の目印（全体仕様書 §4.1）。同じ陣営どうしでは撃ち合わない。
 * 狙いは戦術兵姫を優先し、次にプレイヤー（2026-10-07 ユーザー指定「プレイヤーと兵姫（兵姫優先）」）
 */
public interface ChronoGazer {

    /** 同じ陣営か（弾・爆風・踏みつけを当てない） */
    static boolean isFriend(Entity e) {
        return e instanceof ChronoGazer;
    }

    /** 当ててよい相手か */
    static boolean canHurt(Entity e) {
        return e instanceof LivingEntity living && living.isAlive() && !isFriend(e)
                && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** 狙いの選び方（反撃は仲間にも知らせる・兵姫優先・次にプレイヤー） */
    static void addTargets(Mob mob) {
        mob.targetSelector.addGoal(1, new HurtByTargetGoal((net.minecraft.world.entity.PathfinderMob) mob).setAlertOthers());
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, ValkyrieEntity.class, 10, true, false,
                v -> v instanceof ValkyrieEntity valkyrie && !valkyrie.isShutdown()));
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }

    /** 近くの仲間に相手を教える（索敵ドローンの警報・反撃） */
    static int alert(Mob caller, LivingEntity target, double radius) {
        int n = 0;
        for (Mob m : caller.level().getEntitiesOfClass(Mob.class, caller.getBoundingBox().inflate(radius),
                m -> m != caller && m instanceof ChronoGazer && m.isAlive() && m.getTarget() == null)) {
            m.setTarget(target);
            n++;
        }
        return n;
    }
}
