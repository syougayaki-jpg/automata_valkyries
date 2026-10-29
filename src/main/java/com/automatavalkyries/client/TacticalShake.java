package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 戦術級コードの着弾の画面の揺れ（{@link com.automatavalkyries.network.TacticalShakePacket}）。だんだん収まる */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class TacticalShake {

    private static float shake;

    private TacticalShake() {}

    public static void add(float power) {
        shake = Math.min(1.5F, Math.max(shake, power));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shake <= 0.0F || Minecraft.getInstance().isPaused()) return;
        shake *= 0.9F;
        if (shake < 0.01F) shake = 0.0F;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.0F || Minecraft.getInstance().isPaused() || Minecraft.getInstance().level == null) return;
        float t = (float) (Minecraft.getInstance().level.getGameTime() + event.getPartialTick());
        float a = shake * 2.6F;
        event.setYaw(event.getYaw() + Mth.sin(t * 2.9F) * a);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.7F + 1.0F) * a * 0.8F);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.3F + 2.0F) * a * 0.7F);
    }
}
