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

        @Override
        public boolean canUse() {
            if (v.getMode() != Mode.FOLLOW || v.isShutdown() || v.isPassenger()) return false;
            Player o = v.getOwner();
            if (o == null || o.isSpectator()) return false;
            owner = o;
            return v.distanceToSqr(o) > START_DIST * START_DIST || (o.isPassenger() && !v.isPassenger());
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && v.getMode() == Mode.FOLLOW && !v.isShutdown() && !v.isPassenger()
                    && (v.distanceToSqr(owner) > STOP_DIST * STOP_DIST || owner.isPassenger());
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
            double dist = v.distanceTo(owner);
            Entity vehicle = owner.getVehicle();
            if (vehicle != null && dist < 4.0 && v.startRiding(vehicle)) return; // 同乗（空いていなければ失敗して並走）
            if (dist > TELEPORT_DIST) {
                v.teleportNear(owner);
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                v.getNavigation().moveTo(owner, dist > 10 ? 1.35 : 1.1);
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
            if (home != null) v.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.1);
        }
    }

    /** 歩き回る: 巡回は拠点の半径の中を広く、拠点防衛は拠点のそばを少しだけ。追従・待機では歩き回らない */
    public static class Wander extends RandomStrollGoal {
        private final ValkyrieEntity v;

        public Wander(ValkyrieEntity v) {
            super(v, 0.8, 80);
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
