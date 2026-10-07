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
        addTargets(mob, 4.0);
    }

    /**
     * 狙いの選び方。vertical は上下に探す幅（バニラの既定は 4 ブロックで、それより上・下の相手には気づかない。
     * ルクシオンは広く探す。2026-10-08「上下差があると反応しない」）
     */
    static void addTargets(Mob mob, double vertical) {
        mob.targetSelector.addGoal(1, new HurtByTargetGoal((net.minecraft.world.entity.PathfinderMob) mob).setAlertOthers());
        mob.targetSelector.addGoal(2, new Nearest<>(mob, ValkyrieEntity.class, 10, v -> v instanceof ValkyrieEntity valkyrie && !valkyrie.isShutdown(), vertical));
        // 戦術兵姫のダミーちゃん（練習台）も兵姫と同じに狙う（2026-10-08）
        mob.targetSelector.addGoal(2, new Nearest<>(mob, com.automatavalkyries.entity.ValkyrieDummyEntity.class, 10, null, vertical));
        mob.targetSelector.addGoal(3, new Nearest<>(mob, Player.class, 10, null, vertical));
    }

    /** 上下に探す幅を変えられる、いちばん近い相手を狙う目標 */
    class Nearest<T extends net.minecraft.world.entity.LivingEntity> extends NearestAttackableTargetGoal<T> {
        private final double vertical;

        Nearest(Mob mob, Class<T> type, int interval, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> filter, double vertical) {
            super(mob, type, interval, true, false, filter);
            this.vertical = vertical;
        }

        @Override
        protected net.minecraft.world.phys.AABB getTargetSearchArea(double range) {
            return mob.getBoundingBox().inflate(range, vertical, range);
        }
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
