package com.automatavalkyries.mixin.client;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** クライアント側のエンティティ管理（{@code TransientEntitySectionManager}）の検索表と区画への getter */
@Mixin(TransientEntitySectionManager.class)
public interface TransientEntitySectionManagerAccessor<T extends EntityAccess> {
    @Accessor("entityStorage")
    EntityLookup<T> automata_valkyries$getEntityStorage();

    @Accessor("sectionStorage")
    EntitySectionStorage<T> automata_valkyries$getSectionStorage();
}
