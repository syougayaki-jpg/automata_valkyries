package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/**
 * ワールドのエンティティ管理の内部一覧（UUID 登録・ID/UUID の検索表・区画）への getter。
 * 他 MOD がこれらを直接書き換えて消すので、保護対象がまだ正しく載っているかの点検と、入り直し前の後片付けに使う。
 */
@Mixin(PersistentEntitySectionManager.class)
public interface EntityManagerAccessor<T extends EntityAccess> {
    @Accessor("knownUuids")
    Set<UUID> automata_valkyries$getKnownUuids();

    @Accessor("visibleEntityStorage")
    EntityLookup<T> automata_valkyries$getVisibleEntityStorage();

    @Accessor("sectionStorage")
    EntitySectionStorage<T> automata_valkyries$getSectionStorage();

    /** チャンクごとの可視性。正規のアンロード（UNLOADED_TO_CHUNK）は HIDDEN になったチャンクでしか起きない */
    @Accessor("chunkVisibility")
    Long2ObjectMap<Visibility> automata_valkyries$getChunkVisibility();
}
