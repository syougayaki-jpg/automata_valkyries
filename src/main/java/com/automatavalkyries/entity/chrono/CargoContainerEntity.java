package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 荷役機クレーン・ローダーが投げる・落とす物資のコンテナ（2026-10-15 侵食基地の追加）。山なりに飛んで落ちた所で砕けて炸裂する（地形は壊さない）。
 * 見た目は回りながら飛ぶ木の樽（client/CargoContainerRenderer）
 */
public class CargoContainerEntity extends MortarShellEntity {

    public CargoContainerEntity(EntityType<? extends CargoContainerEntity> type, Level level) {
        super(type, level);
    }

    /** from から to へ flight Tick で落ちるコンテナを投げる */
    public static CargoContainerEntity hurl(LivingEntity thrower, Vec3 from, Vec3 to, int flight, float damage, float blast) {
        return lob(new CargoContainerEntity(ModEntities.CARGO_CONTAINER.get(), thrower.level()), thrower, from, to, flight, damage, blast);
    }

    @Override
    protected void trail() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
    }
}
