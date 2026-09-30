package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * {@code EntityLookup} の byId / byUuid マップへの直接アクセス用 Accessor。
 * 他 MOD（Witherzilla, Omni-Mobs 等）が remove(EntityAccess) に Mixin 注入してキャンセルしてくるのを
 * 完全にバイパスして内部マップから確実に消去する。
 */
@Mixin(EntityLookup.class)
public interface EntityLookupAccessor<T extends EntityAccess> {
    @Accessor("byId")
    Int2ObjectMap<T> automata_valkyries$getById();

    @Accessor("byUuid")
    Map<UUID, T> automata_valkyries$getByUuid();
}
