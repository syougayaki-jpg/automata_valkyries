package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * ガリアフォートの副砲の徹甲弾（2026-10-10 ユーザー指定「副砲とミサイルの弾を自作して搭載」）。飛び方・当たり方はクロノゲイザーの弾（{@link ChronoBolt}）と同じで、
 * 見た目だけ専用: 尖った弾頭・紫の弾帯・赤く光る曳光の尾の弾が、飛ぶ向きへ機首を向けて飛ぶ（client/GalliaAmmoRenderer、形は tools/gen_gallia_ammo.py）。
 * 尾に橙の曳光の光と細い煙を引く
 */
public class GalliaShellEntity extends ChronoBolt implements GeoEntity {

    private static final Vector3f TRACER = new Vector3f(1.0F, 0.45F, 0.15F);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public GalliaShellEntity(EntityType<? extends GalliaShellEntity> type, Level level) {
        super(type, level);
    }

    /** 向き dir へまっすぐ撃つ（副砲の扇の弾幕） */
    public static GalliaShellEntity fire(LivingEntity shooter, Vec3 from, Vec3 dir, float damage, float speed, float inaccuracy) {
        return init(new GalliaShellEntity(ModEntities.GALLIA_SHELL.get(), shooter.level()), shooter, from, dir, damage, speed, inaccuracy, true);
    }

    @Override
    protected void trail() {
        Vec3 v = getDeltaMovement();
        level().addParticle(new DustParticleOptions(TRACER, 0.8F), getX() - v.x * 0.3, getY() - v.y * 0.3, getZ() - v.z * 0.3, 0, 0, 0);
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 0.6, getY() - v.y * 0.6, getZ() - v.z * 0.6, 0, 0.01, 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
