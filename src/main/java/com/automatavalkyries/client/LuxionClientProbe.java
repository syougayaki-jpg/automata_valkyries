package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.LuxionEntity;
import com.automatavalkyries.protect.DirectRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 調査用（2026-10-22）: サーバーでは生きているルクシオンが、手元では当たり判定ごと消えて見える件。
 * {@code onRemovedFromWorld} を通らずに消えることがあるので、手元の世界にいるルクシオンの状態を毎 Tick 見て、変わった瞬間だけログに出す
 * （除去の印・検索表に載っているか・見えているか・HP・撃破の印・deathTime）。見るだけで何も書き換えない
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class LuxionClientProbe {

    private LuxionClientProbe() {}

    /** 個体の ID → 前回の状態の文字列 */
    private static final Map<Integer, String> LAST = new HashMap<>();
    private static long clock;
    private static int hiddenLogs;
    private static int listedLogs;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LAST.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LuxionEntity lux)) continue;
            seen.add(lux.getId());
            String state = "removed=" + lux.getRemovalReason() + " inLookup=" + (level.getEntity(lux.getId()) == lux)
                    + " invisible=" + lux.isInvisible() + " health=" + DirectRead.health(lux)
                    + " defeated=" + lux.isDefeatedSynced() + " deathTime=" + lux.deathTime;
            if (clock % 40 == 0 && listedLogs++ < 60) {
                Minecraft mc = Minecraft.getInstance();
                AutomataValkyries.LOGGER.info("[luxion-probe] id {} is in the list: pos=({}, {}, {}) box={} distToPlayer={} invisible={} noCulling={} tickCount={} instance={}",
                        lux.getId(), lux.getX(), lux.getY(), lux.getZ(), lux.getBoundingBox(),
                        mc.player != null ? Math.sqrt(mc.player.distanceToSqr(lux)) : -1.0, lux.isInvisible(), lux.noCulling, lux.tickCount, System.identityHashCode(lux));
            }
            String previous = LAST.put(lux.getId(), state);
            if (!state.equals(previous)) {
                AutomataValkyries.LOGGER.info("[luxion-probe] id {} {} at ({}, {}, {}) tick {}", lux.getId(), state,
                        (int) lux.getX(), (int) lux.getY(), (int) lux.getZ(), lux.tickCount);
            }
        }
        clock++;
        for (Integer id : new java.util.ArrayList<>(LAST.keySet())) {
            if (seen.contains(id)) continue;
            net.minecraft.world.entity.Entity hidden = level.getEntity(id);
            if (hidden == null) {
                AutomataValkyries.LOGGER.info("[luxion-probe] id {} is gone from the local world (level.getEntity = null)", id);
                LAST.remove(id);
            } else if (clock % 20 == 0 && hiddenLogs++ < 30) {
                // ID の表では引けるのに描画の一覧に出ない個体の詳細
                boolean inIteration = false;
                for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
                    if (e == hidden) {
                        inIteration = true;
                        break;
                    }
                }
                Minecraft mc = Minecraft.getInstance();
                AutomataValkyries.LOGGER.info("[luxion-probe] id {} is found by id but NOT drawn: inIteration={} class={} removed={} addedToWorld={} invisible={} tickCount={} pos=({}, {}, {}) box={} distToPlayer={} noCulling={} sameLevel={}",
                        id, inIteration, hidden.getClass().getSimpleName(), hidden.getRemovalReason(), hidden.isAddedToWorld(), hidden.isInvisible(), hidden.tickCount,
                        hidden.getX(), hidden.getY(), hidden.getZ(), hidden.getBoundingBox(),
                        mc.player != null ? Math.sqrt(mc.player.distanceToSqr(hidden)) : -1.0, hidden.noCulling, hidden.level() == level);
            }
        }
    }
}
