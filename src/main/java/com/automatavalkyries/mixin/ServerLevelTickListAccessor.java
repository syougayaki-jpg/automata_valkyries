package com.automatavalkyries.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code ServerLevel.entityTickList}（Tick 対象の一覧）と {@code entityManager}（UUID 登録の管理）への getter */
@Mixin(ServerLevel.class)
public interface ServerLevelTickListAccessor {
    @Accessor("entityTickList")
    EntityTickList automata_valkyries$getEntityTickList();

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> automata_valkyries$getEntityManager();
}
