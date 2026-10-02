package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.TickListEviction;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.client.ClientLevelAccessor;
import com.automatavalkyries.mixin.client.TransientEntitySectionManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自分（LocalPlayer）がクライアントのワールドに載っていなければ載せ直す。
 * <p>
 * 終焉執行の最初のリスポーンで、新しい LocalPlayer がクライアントのワールドに入らないことがある（2026-10-03。
 * {@code level.getEntity(自分の ID)} が null）。ワールドに載っていない自分は Tick されないので、
 * 視点・クリック・インベントリは動くのに WASD だけ効かない。2 回目以降のリスポーンでは起きない。
 * </p>
 * 同じ ID・UUID の席を古い個体が塞いでいたら外し、参加のイベント（他 MOD が止め得る）を通さずに直接載せる。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class LocalPlayerRejoin {

    /** 同じ個体を続けて載せ直したときは 1 回目だけ記録する（リスポーン直後は 1 秒ほど毎 Tick 外れる） */
    private static LocalPlayer lastLogged;

    private LocalPlayerRejoin() {}

    /**
     * 新しい LocalPlayer ができたら、死亡・停止の印とリスポーンの妨害を戻す（準汎用。サーバー側の {@code RespawnSanitizer} と同じ方針）。
     * UUID で持つ印がクライアントにもあり、新しい LocalPlayer まで死亡扱い・停止のままになるため
     */
    @SubscribeEvent
    public static void onClone(net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        LocalPlayer player = event.getNewPlayer();
        if (player == null) return;
        try {
            var changes = com.automatavalkyries.protect.flag.StateScanner.apply(player,
                    com.automatavalkyries.protect.flag.StateScanner.CLEAN_RESPAWNED_PLAYER,
                    java.util.EnumSet.allOf(com.automatavalkyries.protect.flag.StateScanner.Source.class));
            if (!changes.isEmpty()) {
                AutomataValkyries.LOGGER.info("[respawn] Cleaned respawned local player: {}", changes);
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isRemoved() || player.level() != level) return;
        if (level.getEntity(player.getId()) == player) return;
        try {
            rejoin(level, player);
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[rejoin] Failed to put the local player back into the client level: {}", t.toString());
        }
    }

    private static void rejoin(ClientLevel level, LocalPlayer player) {
        ClientLevelAccessor clientLevel = (ClientLevelAccessor) level;
        @SuppressWarnings("unchecked")
        TransientEntitySectionManagerAccessor<Entity> manager =
                (TransientEntitySectionManagerAccessor<Entity>) (Object) clientLevel.automata_valkyries$getEntityStorage();
        @SuppressWarnings("unchecked")
        EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();

        Entity byId = lookup.automata_valkyries$getById().get(player.getId());
        Entity byUuid = lookup.automata_valkyries$getByUuid().get(player.getUUID());
        if (lastLogged != player) {
            lastLogged = player;
            AutomataValkyries.LOGGER.warn("[rejoin] Local player {} was missing from the client level (id slot: {}, uuid slot: {}); adding it back",
                    player.getScoreboardName(), describe(byId), describe(byUuid));
        }

        for (Entity stale : new Entity[] {byId, byUuid}) {
            if (stale == null || stale == player) continue;
            lookup.automata_valkyries$getById().remove(stale.getId());
            lookup.automata_valkyries$getByUuid().remove(stale.getUUID());
            for (long chunk : manager.automata_valkyries$getSectionStorage().getAllChunksWithExistingSections().toLongArray()) {
                manager.automata_valkyries$getSectionStorage().getExistingSectionsInChunk(chunk).forEach(section -> section.remove(stale));
            }
            TickListEviction.remove(clientLevel.automata_valkyries$getTickingEntities(), stale);
            ((EntityAccessor) stale).automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
            ((EntityAccessor) stale).automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
        }

        clientLevel.automata_valkyries$getEntityStorage().addEntity(player);
        player.onAddedToWorld();
        if (level.getEntity(player.getId()) != player) {
            AutomataValkyries.LOGGER.warn("[rejoin] Local player is still missing from the client level after re-adding");
        }
    }

    private static String describe(Entity entity) {
        if (entity == null) return "empty";
        return entity.getClass().getSimpleName() + "@" + System.identityHashCode(entity) + (entity.isRemoved() ? " removed=" + entity.getRemovalReason() : "");
    }
}
