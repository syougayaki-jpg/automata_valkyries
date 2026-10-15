package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 位相アンカーのサブ・コンデンサー（拡張仕様書 §10.2 の位相アンカー。2026-10-15、工場長機ファウンドリ・マスターの無敵バリアの供給源として実装）。
 * 動かない柱で、紫に光る核のまわりを輪が回る。半径 {@value #LINK} m の工場長機へ紫の光の線を送り、1 本でも立っている間は工場長機に攻撃が届かない。
 * HP {@value #HP}。壊すと紫の光が弾ける
 */
public class PhaseCondenserEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final double LINK = 24.0;
    public static final float HP = 40.0F;
    public static final int C_PHASE = 0xB060FF;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public PhaseCondenserEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || tickCount % 10 != 0) return;
        for (FoundryMasterEntity boss : server.getEntitiesOfClass(FoundryMasterEntity.class, getBoundingBox().inflate(LINK), b -> b.isAlive() && !b.isDefeated())) {
            Fx.line(server, MementoMoriEntity.glow(C_PHASE, 0.18F, 8), position().add(0, 2.0, 0), boss.position().add(0, boss.getBbHeight() * 0.6, 0), 1.0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            Vec3 c = position().add(0, 1.6, 0);
            Fx.burst(server, MementoMoriEntity.glow(C_PHASE, 0.6F, 20), c, 30, 0.3);
            server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
            playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.2F);
        }
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
        super.travel(Vec3.ZERO);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
