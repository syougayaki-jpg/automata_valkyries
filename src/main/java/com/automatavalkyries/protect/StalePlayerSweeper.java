package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * ワールドのプレイヤー一覧に残った「古い個体」を外す。
 * <p>
 * 終焉執行で死んだ The Last Sword 所持プレイヤーは、リスポーン後も古い個体がワールドのプレイヤー一覧に残ることがある（2026-10-02）。
 * 古い個体は新しい個体と同じ接続・同じ ID を持つので、追跡が新しい個体を「別のプレイヤー」として古い個体の接続（＝本人の画面）へ
 * 送り、本人の画面では自分のエンティティが他人として上書き・削除され、透明で動けなくなる。
 * </p>
 * サーバーの Tick の終わりに、消去済みで、プレイヤー一覧（PlayerList）の同じ UUID の個体ではないものを、追跡とワールドの一覧から外す。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class StalePlayerSweeper {

    private StalePlayerSweeper() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = ((ServerLevelTickListAccessor) level).automata_valkyries$getPlayers();
            if (players.isEmpty()) continue;
            List<ServerPlayer> stale = null;
            for (ServerPlayer player : players) {
                if (player.isRemoved() && server.getPlayerList().getPlayer(player.getUUID()) != player) {
                    if (stale == null) stale = new ArrayList<>();
                    if (!stale.contains(player)) stale.add(player);
                }
            }
            if (stale == null) continue;
            for (ServerPlayer player : stale) {
                long copies = players.stream().filter(p -> p == player).count();
                AutomataValkyries.LOGGER.warn("[respawn] Removing stale old player instance {} ({}) left in {} ({} entries)",
                        player.getScoreboardName(), player.getUUID(), level.dimension().location(), copies);
                // 追跡の表は ID で引くので、新しい個体が同じ ID で追跡されていたら、外すと新しい個体の追跡まで消える。そのときは一覧から外すだけにする
                ServerPlayer current = server.getPlayerList().getPlayer(player.getUUID());
                try {
                    if (current == null || current.getId() != player.getId()) level.getChunkSource().removeEntity(player);
                } catch (Throwable t) {
                    AutomataValkyries.LOGGER.warn("[respawn] Failed to stop tracking stale player: {}", t.toString());
                }
                players.removeIf(p -> p == player);
            }
            level.updateSleepingPlayerList();
        }
    }
}
