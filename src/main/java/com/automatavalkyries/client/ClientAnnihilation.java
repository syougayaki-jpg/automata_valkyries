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
        Minecraft mc = Minecraft.getInstance();
        UUID localPlayerUuid = mc.player != null ? mc.player.getUUID() : null;
        int localPlayerId = mc.player != null ? mc.player.getId() : -1;

        // モブのみ抹消済みマークをつける（プレイヤーはリスポーンできなくなるためマークしない）
        for (UUID u : uuids) {
            if (localPlayerUuid != null && localPlayerUuid.equals(u)) continue;
            AnnihilationExecutionAgent.markAnnihilated(u);
        }

        // プレイヤー自身およびプレイヤーエンティティは絶対にクライアントレベルから除外（removeEntity）しない
        for (int id : ids) {
            if (id == localPlayerId) continue;
            Entity entity = level.getEntity(id);
            if (entity instanceof net.minecraft.world.entity.player.Player) continue;
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }

        Set<UUID> uuidSet = new HashSet<>(uuids);
        Set<Integer> idSet = new HashSet<>(ids);
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.player.Player) continue; // プレイヤーは決して forceRemove しない
            if (uuidSet.contains(entity.getUUID()) || idSet.contains(entity.getId())) leftovers.add(entity);
        }
        for (Entity entity : leftovers) {
            forceRemove(level, entity);
        }

        // 自プレイヤーが終焉対象に含まれている場合、消去ではなく死亡・消滅フラグを確定して DeathScreen を安全に表示
        if (mc.player != null && (uuidSet.contains(mc.player.getUUID()) || idSet.contains(mc.player.getId()))) {
            handlePlayerDeath(mc.player.getUUID());
        }
    }

    public static void handlePlayerDeath(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            mc.execute(() -> {
                try {
                    com.automatavalkyries.protect.flag.StateScanner.apply(mc.player,
                            com.automatavalkyries.protect.flag.StateScanner.BREAK_TARGET,
                            java.util.EnumSet.allOf(com.automatavalkyries.protect.flag.StateScanner.Source.class));
                    mc.player.setHealth(0.0F);
                    mc.player.deathTime = 19;
                    if (!(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                        mc.setScreen(new net.minecraft.client.gui.screens.DeathScreen(
                                net.minecraft.network.chat.Component.translatable("death.attack.automata_valkyries.terminus", mc.player.getScoreboardName()),
                                false));
                    }
                } catch (Throwable ignored) {}
            });
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
