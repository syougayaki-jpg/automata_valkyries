package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code EntityTickList} の active / iterated マップへの直接アクセス用 Accessor。
 * 他 MOD（Witherzilla の MixinEntityTickList 等）による remove(Entity) の拒否をバイパスし、
 * Tick 対象一覧から強制的に抹消する。
 */
@Mixin(EntityTickList.class)
public interface EntityTickListAccessor {
    @Accessor("active")
    Int2ObjectMap<Entity> automata_valkyries$getActive();

    @Accessor("active")
    void automata_valkyries$setActive(Int2ObjectMap<Entity> active);

    @Accessor("iterated")
    Int2ObjectMap<Entity> automata_valkyries$getIterated();
}
