package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.SteamThrusterItem;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.SteamThrustPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 蒸気スラスターの二段ジャンプ: 地面を離れてから空中でジャンプキーを押し直すと、1 回だけ蒸気ステップする（着地で戻る）。
 * 動きはクライアントで付け（プレイヤーの移動はクライアントが決めるため）、サーバーへ知らせて蒸気を減らす。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class SteamThrusterClient {

    private static boolean jumpWasDown;
    private static boolean used;

    private SteamThrusterClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) { jumpWasDown = false; return; }
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        if (player.onGround() || player.isInWater() || player.getAbilities().flying) { used = false; return; }
        if (!pressed || used) return;
        ItemStack thruster = SteamThrusterItem.worn(player);
        if (thruster.isEmpty() || (SteamThrusterItem.steam(thruster) <= 0 && !player.getAbilities().instabuild)) return;
        used = true;
        player.setDeltaMovement(SteamThrusterItem.thrust(player));
        player.fallDistance = 0;
        ModNetwork.CHANNEL.sendToServer(new SteamThrustPacket());
    }
}
