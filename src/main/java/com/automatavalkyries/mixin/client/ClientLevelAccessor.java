package com.automatavalkyries.mixin.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import net.minecraftforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code ClientLevel} の Tick 一覧・エンティティ管理・部位エンティティ表への getter（抹消済みの幽霊を取り除く用） */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Accessor("tickingEntities")
    EntityTickList automata_valkyries$getTickingEntities();

    @Accessor("entityStorage")
    TransientEntitySectionManager<Entity> automata_valkyries$getEntityStorage();

    /** Forge が追加したフィールドなので難読化名はない */
    @Accessor(value = "partEntities", remap = false)
    Int2ObjectMap<PartEntity<?>> automata_valkyries$getPartEntities();
}
