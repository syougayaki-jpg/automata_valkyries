package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraftforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code ServerLevel.entityTickList}（Tick 対象の一覧）と {@code entityManager}（UUID 登録の管理）、
 * {@code dragonParts}（部位エンティティの ID 引き表）への getter
 */
@Mixin(ServerLevel.class)
public interface ServerLevelTickListAccessor {
    @Accessor("entityTickList")
    EntityTickList automata_valkyries$getEntityTickList();

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> automata_valkyries$getEntityManager();

    @Accessor("dragonParts")
    Int2ObjectMap<PartEntity<?>> automata_valkyries$getDragonParts();
}
