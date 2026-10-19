package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 総司令機タイムルーラーの演出の画面（2026-10-19 ユーザー指定「撃破演出出現演出退場演出まで自作して作りこんで」）:
 * 出現の題字（「総司令機」「タイムルーラー」「― 時を統べる者 ―」。金の線に挟まれ、ゆっくり現れて消える）、画面の閃光、撃破の大きな地の文（「終末の時は止まった」）。
 * 近く（{@value #RANGE} m）にいる者の画面にだけ出す
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientRulerShow {

    public static final double RANGE = 96.0;
    private static final int TITLE_IN = 15, TITLE_HOLD = 70, TITLE_OUT = 20, DEFEAT_IN = 20, DEFEAT_HOLD = 90, DEFEAT_OUT = 30;

    private static int titleDelay = -1, titleT = -1, defeatT = -1, flashT = -1, flashLen = 1, flashColor = 0xFFFFFF;

    private ClientRulerShow() {
    }

    static boolean near(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.level() == e.level() && mc.player.distanceToSqr(e) < RANGE * RANGE;
    }

    /** 出現の題字（delay Tick 後に出す） */
    public static void intro(Entity ruler, int delay) {
        if (!near(ruler)) return;
        titleDelay = delay;
        titleT = -1;
    }

    /** 撃破の大きな地の文 */
    public static void defeat(Entity ruler) {
        if (!near(ruler)) return;
        defeatT = 0;
    }

    /** 画面の閃光（ticks の間に薄れていく） */
    public static void flash(Entity ruler, int ticks, int color) {
        if (!near(ruler)) return;
        flashT = 0;
        flashLen = Math.max(1, ticks);
        flashColor = color & 0xFFFFFF;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (titleDelay >= 0 && --titleDelay < 0) titleT = 0;
        if (titleT >= 0 && ++titleT > TITLE_IN + TITLE_HOLD + TITLE_OUT) titleT = -1;
        if (defeatT >= 0 && ++defeatT > DEFEAT_IN + DEFEAT_HOLD + DEFEAT_OUT) defeatT = -1;
        if (flashT >= 0 && ++flashT > flashLen) flashT = -1;
    }

    static float fade(float t, int in, int hold, int out) {
        if (t < in) return t / in;
        if (t < in + hold) return 1.0F;
        return Math.max(0.0F, 1.0F - (t - in - hold) / out);
    }

    static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        Font font = mc.font;
        if (flashT >= 0) {                                                                // 閃光
            float a = 0.85F * (1.0F - (flashT + pt) / flashLen);
            if (a > 0.01F) g.fill(0, 0, w, h, argb(a, flashColor));
        }
        if (titleT >= 0) {                                                                // 出現の題字
            float a = fade(titleT + pt, TITLE_IN, TITLE_HOLD, TITLE_OUT);
            if (a > 0.02F) {
                int cy = h / 3;
                float spread = Mth.clamp((titleT + pt) / (TITLE_IN + 10.0F), 0.0F, 1.0F);
                int lw = (int) (w * 0.32F * spread);
                g.fill(w / 2 - lw, cy - 22, w / 2 + lw, cy - 21, argb(a * 0.9F, 0xFFD166));  // 金の線
                g.fill(w / 2 - lw, cy + 32, w / 2 + lw, cy + 33, argb(a * 0.9F, 0xFFD166));
                text(g, font, Component.translatable("entity.automata_valkyries.time_ruler.title_rank"), w / 2, cy - 16, 1.2F, argb(a, 0xE0D8FF));
                text(g, font, Component.translatable("entity.automata_valkyries.time_ruler.title_name"), w / 2, cy - 4, 3.0F, argb(a, 0xFFD166));
                text(g, font, Component.translatable("entity.automata_valkyries.time_ruler.title_sub"), w / 2, cy + 36, 1.1F, argb(a * 0.9F, 0xD8C8FF));
            }
        }
        if (defeatT >= 0) {                                                               // 撃破の大きな地の文
            float a = fade(defeatT + pt, DEFEAT_IN, DEFEAT_HOLD, DEFEAT_OUT);
            if (a > 0.02F) {
                int cy = h / 3 + 10;
                g.fill(0, cy - 18, w, cy + 30, argb(a * 0.35F, 0x000000));                 // 帯
                int lw = (int) (w * 0.4F * Mth.clamp((defeatT + pt) / 30.0F, 0.0F, 1.0F));
                g.fill(w / 2 - lw, cy - 18, w / 2 + lw, cy - 17, argb(a, 0xFFD166));
                g.fill(w / 2 - lw, cy + 29, w / 2 + lw, cy + 30, argb(a, 0xFFD166));
                text(g, font, Component.translatable("entity.automata_valkyries.time_ruler.defeat"), w / 2, cy - 4, 2.2F, argb(a, 0xFFF0B0));
            }
        }
    }

    static void text(GuiGraphics g, Font font, Component c, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawCenteredString(font, c, 0, 0, color);
        g.pose().popPose();
    }
}
