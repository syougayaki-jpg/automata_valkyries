package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 浮遊要塞（T3）の雑魚「時間干渉機 ディレイ・ノード」（2026-10-19 ユーザー指定「既存＋新しい 2 種」。数値は【仮】）。
 * 宙に浮いて動かない砂時計の形の機械。自分では撃たないが、半径 {@value #FIELD} m の中の時を遅らせる:
 * 中の相手（プレイヤー・兵姫）は鈍化 II・採掘速度低下、中を飛ぶ相手の飛び道具は毎 Tick {@value #PROJECTILE_DRAG} 倍に遅くなって落ちる（仲間の弾は遅らせない）。
 * 輪の縁に紫の時計の目盛り。先に壊すべき相手
 */
public class DelayNodeEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double FIELD = 10.0, PROJECTILE_DRAG = 0.8;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public DelayNodeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, FIELD);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, (float) FIELD));
    }

    @Override
    public void aiStep() {
        setDeltaMovement(Vec3.ZERO); // その場に浮かぶ
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        AABB area = getBoundingBox().inflate(FIELD);
        for (Projectile p : server.getEntitiesOfClass(Projectile.class, area, p -> !(p.getOwner() instanceof ChronoGazer) && p.distanceToSqr(this) < FIELD * FIELD)) {
            p.setDeltaMovement(p.getDeltaMovement().scale(PROJECTILE_DRAG));
            p.hurtMarked = true;
        }
        if (tickCount % 10 == 0) {
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, area, e -> ChronoGazer.canHurt(e) && e.distanceToSqr(this) < FIELD * FIELD)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 0), this);
            }
            double spin = tickCount * 0.02;
            for (int i = 0; i < 24; i++) { // 輪の縁の時計の目盛り（3 つに 1 つは長い目盛り）
                double a = spin + Math.PI * 2 * i / 24;
                Vec3 at = position().add(Math.cos(a) * FIELD, getBbHeight() * 0.5, Math.sin(a) * FIELD);
                Fx.one(server, MementoMoriEntity.glow(0xB060FF, i % 3 == 0 ? 0.35F : 0.2F, 12), at, Vec3.ZERO);
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
