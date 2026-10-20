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
 * 近く（{@value #RANGE} m）にいる者の画面にだけ出す。
 * 因果停止された者の画面（2026-10-20 ユーザー指定「因果断絶の演出を自作して美麗に」。{@link #causal}）: 止まっている間は赤く沈み、鼓動に合わせて縁が脈打ち、
 * 画面の縁から 12 本の因果の糸が真ん中へ伸びて縛り、上に「因果停止」と残りの秒。明けた瞬間に白い閃光、画面を斜めに断つ一閃と、真ん中から走るひび
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientRulerShow {

    public static final double RANGE = 96.0;
    private static final int TITLE_IN = 15, TITLE_HOLD = 70, TITLE_OUT = 20, DEFEAT_IN = 20, DEFEAT_HOLD = 90, DEFEAT_OUT = 30;

    private static int titleDelay = -1, titleT = -1, defeatT = -1, flashT = -1, flashLen = 1, flashColor = 0xFFFFFF;
    /** 自分を止めている因果停止のエフェクト（時の流れはそのエフェクトの Tick で測る。時間停止の間は一緒に止まる） */
    private static java.lang.ref.WeakReference<com.automatavalkyries.entity.chrono.RulerFxEntity> causalFx = new java.lang.ref.WeakReference<>(null);

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

    /** 因果停止のエフェクトが自分の足元に現れたら、画面の演出を始める（描く側が毎フレーム呼ぶ） */
    public static void causal(com.automatavalkyries.entity.chrono.RulerFxEntity fx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || causalFx.get() == fx || fx.tickCount > 6) return;
        if (mc.player.level() != fx.level() || mc.player.distanceToSqr(fx) > 1.0) return;
        causalFx = new java.lang.ref.WeakReference<>(fx);
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
        com.automatavalkyries.entity.chrono.RulerFxEntity cfx = causalFx.get();
        if (cfx != null && !cfx.isRemoved()) {
            int lock = Math.max(1, cfx.mode());
            float t = cfx.tickCount + pt;
            if (t < lock + com.automatavalkyries.entity.chrono.RulerFxEntity.SEVER_TICKS) g.drawManaged(() -> causalHud(g, font, w, h, t, lock, cfx.getId()));
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

    /** 因果停止された者の画面（t = 止まってからの Tick） */
    static void causalHud(GuiGraphics g, Font font, int w, int h, float t, int lock, int seed) {
        java.util.Random rnd = new java.util.Random(seed * 31L);
        int cx = w / 2, cy = h / 2;
        if (t < lock) {
            float in = Mth.clamp(t / 6.0F, 0.0F, 1.0F), k = t / lock;
            float p = t % 20.0F, beat = Math.max(Math.max(0.0F, 1.0F - Math.abs(p - 1.5F) / 1.5F), Math.max(0.0F, 1.0F - Math.abs(p - 5.0F) / 1.5F) * 0.6F);
            g.fill(0, 0, w, h, argb(in * (0.16F + 0.06F * beat), 0x3A0010));                         // 赤く沈む
            int layers = 16, step = Math.max(2, Math.min(w, h) / 40);
            for (int i = 0; i < layers; i++) {                                                      // 鼓動に合わせて脈打つ縁
                float a = in * (0.45F + 0.3F * beat + 0.2F * k) * Mth.square(1.0F - i / (float) layers);
                int c = argb(a, 0xC01030), o = i * step;
                g.fill(o, o, w - o, o + step, c);
                g.fill(o, h - o - step, w - o, h - o, c);
                g.fill(o, o + step, o + step, h - o - step, c);
                g.fill(w - o - step, o + step, w - o, h - o - step, c);
            }
            for (int i = 0; i < 12; i++) {                                                          // 縁から真ん中へ伸びて縛る因果の糸
                double a = Math.PI * 2 * i / 12 + rnd.nextDouble() * 0.3;
                float shoot = Mth.clamp((t - i * 0.8F) / 5.0F, 0.0F, 1.0F);
                if (shoot <= 0) continue;
                double ex = cx + Math.cos(a) * w, ey = cy + Math.sin(a) * h;
                double sx = Mth.clamp(ex, 0, w), sy = Mth.clamp(ey, 0, h);
                double tx = sx + (cx - sx) * shoot * 0.92, ty = sy + (cy - sy) * shoot * 0.92;
                line(g, sx, sy, tx, ty, 2, argb(in * 0.55F, 0xFF3050), t * 0.35F + i, (1.0F - k) * 6.0F);
                line(g, sx, sy, tx, ty, 1, argb(in * 0.9F, 0xFFD0D8), t * 0.35F + i, (1.0F - k) * 6.0F);
            }
            int secs = Mth.clamp((int) Math.ceil((lock - t) / 20.0F), 1, 9);
            text(g, font, Component.translatable("entity.automata_valkyries.time_ruler.causal_title"), cx, h / 4 - 14, 1.3F, argb(in, 0xFFB0C0));
            text(g, font, Component.literal(RulerFxRenderer.roman(secs)), cx, h / 4, 3.5F + 0.6F * beat, argb(in, 0xFF4060));
            int lw = (int) (w * 0.2F * in);
            g.fill(cx - lw, h / 4 - 18, cx + lw, h / 4 - 17, argb(in * 0.9F, 0xFF4060));
            g.fill(cx - lw, h / 4 + 36, cx + lw, h / 4 + 37, argb(in * 0.9F, 0xFF4060));
            if (lock - t < 10) {                                                                    // 明ける間際: 真ん中の白い点が膨らむ
                float gr = 1.0F - (lock - t) / 10.0F;
                int r = (int) (4 + gr * 40);
                g.fill(cx - r, cy - r / 4, cx + r, cy + r / 4, argb(gr * 0.8F, 0xFFFFFF));
                g.fill(cx - r / 4, cy - r, cx + r / 4, cy + r, argb(gr * 0.8F, 0xFFFFFF));
            }
            return;
        }
        float s = t - lock, fade = 1.0F - s / com.automatavalkyries.entity.chrono.RulerFxEntity.SEVER_TICKS;
        if (s < 12) g.fill(0, 0, w, h, argb(0.9F * (1.0F - s / 12.0F), 0xFFFFFF));                  // 断った瞬間の白い閃光
        g.fill(0, 0, w, h, argb(0.25F * fade, 0xFF2040));
        float run = Mth.clamp(s / 3.0F, 0.0F, 1.0F);                                               // 画面を斜めに断つ一閃
        double x0 = w * 0.9, y0 = h * 0.05, x1 = x0 + (w * 0.1 - x0) * run, y1 = y0 + (h * 0.95 - y0) * run;
        line(g, x0, y0, x1, y1, 9, argb(0.5F * fade, 0xFF3050), 0, 0);
        line(g, x0, y0, x1, y1, 3, argb(Math.min(1.0F, fade * 1.4F), 0xFFFFFF), 0, 0);
        for (int i = 0; i < 10; i++) {                                                             // 真ん中から走るひび
            double a = Math.PI * 2 * i / 10 + rnd.nextDouble() * 0.5, px = cx, py = cy;
            double reach = Math.min(w, h) * (0.25 + rnd.nextDouble() * 0.35) * Mth.clamp(s / 6.0F, 0.0F, 1.0F);
            for (int seg = 0; seg < 4; seg++) {
                a += (rnd.nextDouble() - 0.5) * 0.8;
                double qx = px + Math.cos(a) * reach / 4, qy = py + Math.sin(a) * reach / 4;
                line(g, px, py, qx, qy, 1, argb(0.9F * fade, 0xFFF0F0), 0, 0);
                px = qx;
                py = qy;
            }
        }
    }

    /** 太さ th の線（点を並べて描く。wave があれば糸のように揺らす） */
    static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int th, int color, float phase, float wave) {
        double dx = x1 - x0, dy = y1 - y0, len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        double nx = -dy / len, ny = dx / len;
        int steps = (int) Math.ceil(len / Math.max(1, th));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps, off = wave * Math.sin(phase + f * 8.0) * Math.sin(f * Math.PI);
            int x = (int) (x0 + dx * f + nx * off) - th / 2, y = (int) (y0 + dy * f + ny * off) - th / 2;
            g.fill(x, y, x + th, y + th, color);
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
