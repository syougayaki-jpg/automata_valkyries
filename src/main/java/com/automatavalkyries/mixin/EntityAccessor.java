package com.automatavalkyries.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code Entity.removalReason}（private）への getter / setter。リフレクションは使わない */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("removalReason")
    Entity.RemovalReason getRemovalReason();

    @Accessor("removalReason")
    void setRemovalReason(Entity.RemovalReason removalReason);
}
