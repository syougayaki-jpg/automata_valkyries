package com.automatavalkyries.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code LivingEntity.dead}（protected）への getter / setter と {@code DATA_HEALTH_ID}（private static）への getter */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("dead")
    boolean automata_valkyries$isDead();

    @Accessor("dead")
    void automata_valkyries$setDead(boolean dead);

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> automata_valkyries$getDataHealthId() {
        throw new AssertionError();
    }
}
