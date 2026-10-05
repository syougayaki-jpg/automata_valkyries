package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.AgentPlayerProtection;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 死亡画面の確かめ（2026-10-04 ユーザー指定「サーバーでは生きているのに死亡画面が出る」の調べ）: 死亡画面が開いた瞬間に、
 * 自分の画面（クライアント）での HP・生死・護符を持っているか、同じ PC の中のサーバーでのその人の HP・生死をログ（{@code [charm-check] DEATH SCREEN}）に出す。
 * 護符を持っていて、サーバーでは実際の HP が残っているなら、画面だけの死なので死亡画面を開かない（同じ PC のサーバーのときだけ判断できる）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class DeathScreenDiagnostics {

    private DeathScreenDiagnostics() {}

    /** 死亡画面を出さなかった最後の時刻（クライアントは毎 Tick 開き直そうとするので、続けて出さなかった分はログに出さない） */
    private static long lastSuppressed = -100;

    private static boolean has(Player player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof DeathScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        Player local = mc.player;
        if (local == null) return;
        String server = "not single player";
        var integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            ServerPlayer sp = integrated.getPlayerList().getPlayer(local.getUUID());
            server = sp == null ? "player not found"
                    : String.format("health=%.1f directHealth=%.1f alive=%s removed=%s", sp.getHealth(), DirectRead.health(sp), sp.isAlive(), sp.isRemoved());
        }
        boolean charm = has(local, ModItems.WARD_CHARM.get())
                || has(local, ModItems.INVINCIBLE_CHARM.get())
                || has(local, ModItems.PRIVILEGE_AEGIS.get())
                || AgentPlayerProtection.isProtected(local);
        ServerPlayer onServer = integrated == null ? null : integrated.getPlayerList().getPlayer(local.getUUID());
        boolean serverAlive = onServer == null || DirectRead.health(onServer) > 0.0F || AgentPlayerProtection.isProtected(onServer);
        long now = local.level().getGameTime();
        boolean repeat = charm && serverAlive && now - lastSuppressed <= 2;
        if (charm && serverAlive) lastSuppressed = now;
        if (!repeat) AutomataValkyries.LOGGER.info("[charm-check] DEATH SCREEN on client: client health={} alive={} | ward={} invincible={} | server: {}{}",
                local.getHealth(), local.isAlive(), has(local, ModItems.WARD_CHARM.get()),
                has(local, ModItems.INVINCIBLE_CHARM.get()) || has(local, ModItems.PRIVILEGE_AEGIS.get()) || AgentPlayerProtection.isProtected(local),
                server, charm && serverAlive ? " -> suppressed (alive on the server)" : "");
        // 護符を持っていて、サーバーでは実際の HP が残っている（生きている）なら、画面だけの死なので死亡画面を出さない（2026-10-04 ユーザー指定）
        if (charm && serverAlive) {
            event.setCanceled(true);
            if (local.getHealth() <= 0.0F || !local.isAlive()) {
                local.setHealth(Math.max(local.getMaxHealth(), 20.0F));
                local.deathTime = 0;
            }
        }
    }
}
