package com.automatavalkyries.entity.chrono;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * 侵食基地の防衛兵器「パルス・ファランクス」（拡張仕様書 §10.2。2026-10-15 実装、数値は【仮】）: 動かない対空タレット。射程 {@value #RANGE} m。
 * 空を飛ぶ相手（エリトラ・飛行・宙に浮く兵姫）を優先して狙い、{@value #BURST} 発の迎撃弾をばらまく（1 発 {@value #DAMAGE}・無敵時間を貫く）。
 * 地上の相手も撃つ。土台は動かず、砲塔だけが向きを変える
 */
public class PulsePhalanxEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double RANGE = 40.0;
    public static final float DAMAGE = 3.0F;
    public static final int BURST = 8, INTERVAL = 50;
    private static final byte EVENT_FIRE = 60;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** クライアント: 最後に撃った Tick（砲身の回り・反動の描画） */
    public int fireTick = -100;

    public PulsePhalanxEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new PhalanxGoal(this));
        ChronoGazer.addTargets(this, RANGE);
    }

    /** 宙にいる相手か（足元から 3 ブロックの間に地面が無い・滑空している） */
    static boolean airborne(LivingEntity e) {
        if (e.isFallFlying() || (e instanceof Player p && p.getAbilities().flying)) return true;
        if (e.onGround()) return false;
        return e.level().noCollision(e, e.getBoundingBox().move(0, -3.0, 0).expandTowards(0, 3.0, 0));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void travel(Vec3 input) {
        super.travel(Vec3.ZERO); // 土台は動かない（落ちるだけ）
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_FIRE) fireTick = tickCount;
        else super.handleEntityEvent(id);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 宙の相手がいればそちらへ向き直し、見えていれば一定の間隔で迎撃弾を連射する */
    static class PhalanxGoal extends Goal {
        private final PulsePhalanxEntity turret;
        private int cooldown = 20, shots, scan;

        PhalanxGoal(PulsePhalanxEntity turret) {
            this.turret = turret;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return turret.getTarget() != null && turret.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (--scan <= 0) { // 宙の相手を優先する
                scan = 20;
                if (!airborne(turret.getTarget())) {
                    for (LivingEntity e : turret.level().getEntitiesOfClass(LivingEntity.class, turret.getBoundingBox().inflate(RANGE), ChronoGazer::canHurt)) {
                        if (airborne(e) && turret.hasLineOfSight(e)) {
                            turret.setTarget(e);
                            break;
                        }
                    }
                }
            }
            LivingEntity t = turret.getTarget();
            if (t == null) return;
            turret.getLookControl().setLookAt(t, 40.0F, 40.0F);
            if (shots > 0) {
                if (turret.tickCount % 2 == 0) {
                    shots--;
                    Vec3 aim = t.position().add(t.getDeltaMovement().scale(6.0)).add(0, t.getBbHeight() * 0.5, 0);
                    Vec3 from = turret.getEyePosition();
                    ChronoBolt.fireDir(turret, from, aim.subtract(from), DAMAGE, 2.4F, 2.0F, true);
                    turret.playSound(SoundEvents.CROSSBOW_SHOOT, 0.6F, 1.8F);
                    turret.level().broadcastEntityEvent(turret, EVENT_FIRE);
                }
                return;
            }
            if (--cooldown <= 0 && turret.distanceToSqr(t) < RANGE * RANGE && turret.hasLineOfSight(t)) {
                cooldown = INTERVAL;
                shots = BURST;
            }
        }
    }
}
