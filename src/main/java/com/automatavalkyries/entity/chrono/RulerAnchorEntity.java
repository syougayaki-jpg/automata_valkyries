package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * タイムルーラーの「因果の歯車」（2026-10-19）。因果逆転の間だけ闘技場に浮かぶ金の歯車（HP {@value #HP}）。
 * 1 つでも残ったまま詠唱が終わると、タイムルーラーの体力が少し前へ巻き戻る。全部壊せば詠唱は崩れ、ルーラーはしばらく脆くなる。
 * 動かず、撃たない。ルーラーの方へ金の光の糸を張る
 */
public class RulerAnchorEntity extends Monster implements GeoEntity, ChronoGazer {

    public static final float HP = 150.0F;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public RulerAnchorEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
        setDeltaMovement(Vec3.ZERO);
        super.aiStep();
        if (level() instanceof ServerLevel server && tickCount % 4 == 0) {
            TimeRulerEntity ruler = server.getEntitiesOfClass(TimeRulerEntity.class, getBoundingBox().inflate(64.0), TimeRulerEntity::isAlive).stream().findFirst().orElse(null);
            if (ruler != null) Fx.line(server, MementoMoriEntity.glow(0xFFD166, 0.12F, 5), position().add(0, 1.0, 0), ruler.position().add(0, 3.0, 0), 1.2);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
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
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ANVIL_LAND;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ANVIL_DESTROY;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
