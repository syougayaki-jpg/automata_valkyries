package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.EntityTickListAccessor;
import com.automatavalkyries.mixin.client.ClientLevelAccessor;
import com.automatavalkyries.mixin.client.TransientEntitySectionManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * クライアント側で、超絶抹殺の棒で抹消されたエンティティを確実に取り除く。
 * <ol>
 *   <li>抹消済みとして記録する（Witherzilla の生命維持フラグを false にする既存の Mixin がクライアントでも効くように）</li>
 *   <li>バニラの消去を通す</li>
 *   <li>それでも残っていれば、検索表・区画・Tick 一覧・部位表から直接外す（何が掴んでいたかをログに出す）</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientAnnihilation {

    private ClientAnnihilation() {}

    public static void purge(List<UUID> uuids, List<Integer> ids) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        uuids.forEach(AnnihilationExecutionAgent::markAnnihilated);

        for (int id : ids) {
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }

        Set<UUID> uuidSet = new HashSet<>(uuids);
        Set<Integer> idSet = new HashSet<>(ids);
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (uuidSet.contains(entity.getUUID()) || idSet.contains(entity.getId())) leftovers.add(entity);
        }
        for (Entity entity : leftovers) {
            forceRemove(level, entity);
        }
    }

    private static void forceRemove(ClientLevel level, Entity entity) {
        ClientLevelAccessor clientLevel = (ClientLevelAccessor) level;
        @SuppressWarnings("unchecked")
        TransientEntitySectionManagerAccessor<Entity> manager =
                (TransientEntitySectionManagerAccessor<Entity>) (Object) clientLevel.automata_valkyries$getEntityStorage();
        @SuppressWarnings("unchecked")
        EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();

        EntityAccessor accessor = (EntityAccessor) entity;
        accessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);

        boolean inLookup = lookup.automata_valkyries$getById().remove(entity.getId()) != null;
        lookup.automata_valkyries$getByUuid().remove(entity.getUUID());

        boolean inSection = false;
        EntitySectionStorage<Entity> sections = manager.automata_valkyries$getSectionStorage();
        for (long chunk : sections.getAllChunksWithExistingSections().toLongArray()) {
            for (EntitySection<Entity> section : sections.getExistingSectionsInChunk(chunk).toList()) {
                if (section.remove(entity)) inSection = true;
            }
        }

        boolean ticking = ((EntityTickListAccessor) clientLevel.automata_valkyries$getTickingEntities())
                .automata_valkyries$getActive().remove(entity.getId()) != null;

        if (entity.isMultipartEntity() && entity.getParts() != null) {
            for (PartEntity<?> part : entity.getParts()) {
                if (part != null) clientLevel.automata_valkyries$getPartEntities().remove(part.getId());
            }
        }
        accessor.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);

        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Force-removed leftover client entity {} (lookup={}, section={}, ticking={})",
                entity, inLookup, inSection, ticking);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AnnihilationExecutionAgent.clearMarks();
    }
}
