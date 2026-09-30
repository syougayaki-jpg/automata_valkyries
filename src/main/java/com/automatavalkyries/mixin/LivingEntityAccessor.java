package com.automatavalkyries.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code LivingEntity.dead}（protected）への getter / setter */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("dead")
    boolean automata_valkyries$isDead();

    @Accessor("dead")
    void automata_valkyries$setDead(boolean dead);
}
