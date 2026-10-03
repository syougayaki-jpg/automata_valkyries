package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 染まる画面（クライアントだけ）。0.3 秒で染まり、しばらく残って、ゆっくり褪せる。
 * <ul>
 *   <li>黄昏（終の黄昏『ラグナロク』）: 上から紫・茜・橙・金へ移る薄い幕、霧（地平の空）の色を橙へ</li>
 *   <li>星空（星葬冠『ルミナス・レクイエム』）: 上から深い紺・紫・水色へ移る幕に、瞬く星を 90 個散らし、霧の色を青へ</li>
 *   <li>閃光（パニッシュメント・レールガン）: 一瞬で白に近い水色に染まり、すぐ褪せる（上下の縁は紫）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientTwilight {

    private static int total, left, scheme;

    private ClientTwilight() {}

    public static void start(int ticks, int kind) {
        total = Math.max(1, ticks);
        left = total;
        scheme = kind;
    }

    /** 今の染まり具合（0〜1） */
    static float intensity(float partialTick) {
        if (left <= 0) return 0;
        float age = total - left + partialTick;
        float rise = Math.min(1F, age / 6F);
        float fade = Math.min(1F, (left - partialTick) / (total * 0.6F));
        return Mth.clamp(rise * fade, 0F, 1F);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && left > 0 && !Minecraft.getInstance().isPaused()) left--;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float k = intensity(event.getPartialTick());
        if (k <= 0.001F) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth(), h = event.getWindow().getGuiScaledHeight();
        if (scheme == com.automatavalkyries.network.TwilightPacket.FLASH) {
            // 閃光: 一瞬で真っ白に近い水色に染まり、すぐ褪せる。縁は紫
            float f = Math.max(0F, (left - event.getPartialTick()) / total);
            float k2 = f * f;
            g.fill(0, 0, w, h, argb((int) (200 * k2), 0xE8FAFF));
            g.fillGradient(0, 0, w, h / 5, argb((int) (120 * k2), 0x8A40FF), argb(0, 0x8A40FF));
            g.fillGradient(0, h - h / 5, w, h, argb(0, 0x8A40FF), argb((int) (120 * k2), 0x8A40FF));
            return;
        }
        if (scheme == com.automatavalkyries.network.TwilightPacket.STARRY) {
            starry(g, w, h, k, total - left + event.getPartialTick());
            return;
        }
        // 上から: 深い紫 → 茜 → 橙 → 地平の金（下ほど明るく、真ん中は薄く抜いて見通しを残す）
        int a1 = (int) (150 * k), a2 = (int) (70 * k), a3 = (int) (60 * k), a4 = (int) (120 * k);
        g.fillGradient(0, 0, w, h / 3, argb(a1, 0x3A1060), argb(a2, 0xC03A5A));
        g.fillGradient(0, h / 3, w, h * 2 / 3, argb(a2, 0xC03A5A), argb(a3, 0xFF7A30));
        g.fillGradient(0, h * 2 / 3, w, h, argb(a3, 0xFF7A30), argb(a4, 0xFFC060));
        // 縁を暗く（夕暮れの影）
        int edge = (int) (90 * k);
        g.fillGradient(0, 0, w, h / 8, argb(edge, 0x180818), argb(0, 0x180818));
        g.fillGradient(0, h - h / 8, w, h, argb(0, 0x200A08), argb(edge, 0x200A08));
    }

    /** 星空の幕と瞬く星 */
    private static void starry(GuiGraphics g, int w, int h, float k, float time) {
        int a1 = (int) (160 * k), a2 = (int) (60 * k), a3 = (int) (80 * k);
        g.fillGradient(0, 0, w, h / 2, argb(a1, 0x060C30), argb(a2, 0x4A2A90));
        g.fillGradient(0, h / 2, w, h, argb(a2, 0x4A2A90), argb(a3, 0x60C8FF));
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 90; i++) {
            int x = r.nextInt(Math.max(1, w)), y = r.nextInt(Math.max(1, h * 3 / 4));
            float tw = 0.5F + 0.5F * Mth.sin(time * (0.3F + r.nextFloat() * 0.5F) + r.nextFloat() * 6.28F);
            int alpha = (int) (230 * k * tw);
            int color = i % 5 == 0 ? 0xFFE8A0 : i % 3 == 0 ? 0xA0D8FF : 0xFFFFFF;
            int size = i % 9 == 0 ? 2 : 1;
            g.fill(x, y, x + size, y + size, argb(alpha, color));
            if (size == 2) { // 大きい星は十字に光る
                g.fill(x - 2, y, x + 4, y + 1, argb(alpha / 2, color));
                g.fill(x, y - 2, x + 1, y + 4, argb(alpha / 2, color));
            }
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (scheme == com.automatavalkyries.network.TwilightPacket.FLASH) return;
        float k = intensity((float) event.getPartialTick()) * 0.75F;
        if (k <= 0.001F) return;
        boolean stars = scheme == com.automatavalkyries.network.TwilightPacket.STARRY;
        event.setRed(Mth.lerp(k, event.getRed(), stars ? 0.3F : 1.0F));
        event.setGreen(Mth.lerp(k, event.getGreen(), stars ? 0.4F : 0.45F));
        event.setBlue(Mth.lerp(k, event.getBlue(), stars ? 1.0F : 0.3F));
    }

    private static int argb(int a, int rgb) {
        return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
