package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 戦術兵姫の移動の AI（段階 1。docs/SPEC_VALKYRIE_GUI_AI.md §3「持ち主から離れたとき」・§2.4 行動モード）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieGoals {

    /** これ以上離れたら持ち主のそばへテレポート */
    public static final double TELEPORT_DIST = 24.0;
    private static final double START_DIST = 5.0, STOP_DIST = 2.5;
    /** 道をたどる速さの上限（ブロック/Tick 相当の移動速度。オメガコアの速度 3 倍で道の点を通り過ぎ、ジグザグに動いていた。2026-10-06） */
    public static final double MAX_PATH_SPEED = 0.5;

    /** 道をたどるときの速さの倍率（移動速度×倍率が {@link #MAX_PATH_SPEED} を超えないように） */
    public static double pathSpeed(ValkyrieEntity v, double modifier) {
        double attr = v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return attr <= 0 ? modifier : Math.min(modifier, MAX_PATH_SPEED / attr);
    }

    /**
     * 持ち主の足元の地面（飛んでいる・空中にいる持ち主の真下の立てる所）。32 ブロック下まで無ければ null。
     * 飛んでいる持ち主の空中の位置へ道を探して失敗し続け、めちゃくちゃに動いていた（2026-10-06）。兵姫は常時は飛ばない（§3）
     */
    @Nullable
    public static BlockPos groundBelow(Entity owner) {
        BlockPos p = owner.blockPosition();
        for (int i = 0; i < 32; i++, p = p.below()) {
            BlockPos below = p.below();
            if (!owner.level().getBlockState(below).getCollisionShape(owner.level(), below).isEmpty()
                    && owner.level().getBlockState(p).getCollisionShape(owner.level(), p).isEmpty()) return p;
        }
        return null;
    }

    private ValkyrieGoals() {}

    /** 追従: 持ち主へ歩く（離れすぎたらテレポート）。持ち主が乗り物に乗ったら、空いていれば同乗する（空いていなければ並んで走る） */
    public static class FollowOwner extends Goal {
        private final ValkyrieEntity v;
        @Nullable private Player owner;
        private int repath;

        public FollowOwner(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable private BlockPos ground;
        /** 空中で付いて行っている（Mk-III 以上のスラスターで、持ち主が飛んでいるとき） */
        private boolean flying;

        /**
         * 追う先: 持ち主の足元の地面（地上にいれば持ち主の位置そのもの）。Mk-III 以上のスラスターなら、飛んでいる持ち主の斜め後ろの空中
         * （2026-10-06 ユーザー指定「ダッシュ・浮遊・空中ジャンプでヴァルキリー要素を出す」）
         */
        private Vec3 goal(Player o) {
            flying = false;
            if (o.onGround() || o.isPassenger()) return o.position();
            ground = groundBelow(o);
            var m = ValkyrieUnitEffects.maneuver(v);
            boolean high = o.getAbilities().flying || o.isFallFlying() || ground == null || o.getY() - ground.getY() > 3.0;
            if (m != null && m.airFollow() && high && !o.isInWater()) {
                flying = true;
                Vec3 look = o.getLookAngle();
                Vec3 back = new Vec3(-look.x, 0, -look.z);
                back = back.lengthSqr() < 1.0e-4 ? Vec3.ZERO : back.normalize();
                Vec3 side = new Vec3(-back.z, 0, back.x);
                return o.position().add(back.scale(2.0)).add(side.scale(1.2)).add(0, 0.3, 0);
            }
            return ground != null ? Vec3.atBottomCenterOf(ground) : null;
        }

        @Override
        public boolean canUse() {
            if (v.getMode() != Mode.FOLLOW || v.isShutdown() || v.isPassenger()) return false;
            Player o = v.getOwner();
            if (o == null || o.isSpectator()) return false;
            owner = o;
            Vec3 g = goal(o);
            if (g == null) return false;
            return v.position().distanceToSqr(g) > START_DIST * START_DIST || (o.isPassenger() && !v.isPassenger());
        }

        @Override
        public boolean canContinueToUse() {
            if (owner == null || !owner.isAlive() || v.getMode() != Mode.FOLLOW || v.isShutdown() || v.isPassenger()) return false;
            Vec3 g = goal(owner);
            return g != null && (flying || v.position().distanceToSqr(g) > STOP_DIST * STOP_DIST || owner.isPassenger());
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            owner = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            v.getLookControl().setLookAt(owner, 10.0F, v.getMaxHeadXRot());
            Vec3 g = goal(owner);
            if (g == null) return;
            double dist = Math.sqrt(v.position().distanceToSqr(g));
            Entity vehicle = owner.getVehicle();
            if (vehicle != null && v.distanceTo(owner) < 4.0 && v.startRiding(vehicle)) return; // 同乗（空いていなければ失敗して並走）
            if (flying) { // 空中で付いて行く（離れすぎたら持ち主のそばの空中へ）。電力が足りなければ地上の追従に戻る
                if (dist > TELEPORT_DIST * 2) {
                    v.teleportTo(g.x, g.y, g.z);
                    return;
                }
                if (v.combat().fly(g, dist > 12 ? 1.6 : dist > 4 ? 0.9 : 0.4)) return;
                g = ground != null ? Vec3.atBottomCenterOf(ground) : null;
                if (g == null) return;
                dist = Math.sqrt(v.position().distanceToSqr(g));
            }
            if (dist > TELEPORT_DIST) {
                v.teleportNear(BlockPos.containing(g));
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                v.getNavigation().moveTo(g.x, g.y, g.z, pathSpeed(v, dist > 10 ? 1.35 : 1.1));
            }
        }
    }

    /** 拠点防衛・巡回: 拠点から決めた半径より離れたら戻る */
    public static class KeepHome extends Goal {
        private final ValkyrieEntity v;

        public KeepHome(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            BlockPos home = v.getHome();
            return (v.getMode() == Mode.DEFEND || v.getMode() == Mode.PATROL) && home != null && !v.isShutdown()
                    && !home.closerToCenterThan(v.position(), v.homeRadius);
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos home = v.getHome();
            return home != null && !v.isShutdown() && !home.closerToCenterThan(v.position(), Math.max(2, v.homeRadius / 3.0)) && !v.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos home = v.getHome();
            if (home != null) v.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, pathSpeed(v, 1.1));
        }
    }

    /** 歩き回る: 巡回は拠点の半径の中を広く、拠点防衛は拠点のそばを少しだけ。追従・待機では歩き回らない */
    public static class Wander extends RandomStrollGoal {
        private final ValkyrieEntity v;

        public Wander(ValkyrieEntity v) {
            super(v, 0.8, 200); // 平均 10 秒に 1 回（4 秒ごとでは落ち着きがなかった。2026-10-06）
            this.v = v;
        }

        @Override
        public boolean canUse() {
            return (v.getMode() == Mode.PATROL || v.getMode() == Mode.DEFEND) && !v.isShutdown() && super.canUse();
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            BlockPos home = v.getHome();
            if (home == null) return null;
            int radius = v.getMode() == Mode.PATROL ? v.homeRadius : 4;
            for (int i = 0; i < 10; i++) {
                Vec3 p = DefaultRandomPos.getPos(v, radius, 4);
                if (p != null && home.closerToCenterThan(p, radius)) return p;
            }
            return null;
        }
    }

    /** 持ち主が次元を移ったら、元の次元で追従していた兵姫も一緒に移る */
    @SubscribeEvent
    public static void onOwnerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel from = player.getServer().getLevel(event.getFrom());
        if (from == null) return;
        List<ValkyrieEntity> followers = new ArrayList<>();
        for (Entity e : from.getAllEntities()) {
            if (e instanceof ValkyrieEntity v && player.getUUID().equals(v.getOwnerUUID()) && v.getMode() == Mode.FOLLOW && !v.isShutdown()) followers.add(v);
        }
        for (ValkyrieEntity v : followers) v.followTo(player);
    }
}
