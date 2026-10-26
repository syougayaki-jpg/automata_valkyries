package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 零号機【マリスライズ・ロストカタストロフィー】の演出の画面（2026-10-26 ユーザー指定「マリスライズロストカタストロフィーの演出を改良する」、
 * 質問で: 出現・撃破・形態変化と詠唱・技と撤退の全部を、タイムルーラー並みに。{@link ClientRulerShow} の零号機版で、主題は赤黒のグリッチ）:
 * <ul>
 *   <li>出現の題字（「戦術兵姫零号機」「マリスライズ・ロストカタストロフィー」「― 零に還す者 ―」）: 文字が崩れた記号から組み上がり、赤と水色にずれて重なる</li>
 *   <li>形態変化の帯（「― 第二位相：怨嗟同期 ―」など）、撃破の大きな地の文、世界確定消滅の白い塗りつぶしと題字</li>
 *   <li>グリッチ（画面を横に走るずれた帯）、閃光、同期過電圧ショックの水色のひび、画面の揺れ</li>
 *   <li>詠唱の間: 残りが少ないほど画面の縁が赤く沈み、最後の 10 秒は大きな秒読みとグリッチ</li>
 * </ul>
 * 近く（{@value #RANGE} m）にいる者の画面にだけ出す。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientZeroShow {

    public static final double RANGE = 96.0;
    private static final int TITLE_IN = 24, TITLE_HOLD = 70, TITLE_OUT = 20, BANNER_IN = 10, BANNER_HOLD = 50, BANNER_OUT = 16,
            DEFEAT_IN = 20, DEFEAT_HOLD = 90, DEFEAT_OUT = 30, ERASE_LEN = 80, CRACK_LEN = 36, BLOW_LEN = 80;
    private static final String NOISE = "01#%&$@!?<>/\\|=+*零怨嗟";

    private static int titleDelay = -1, titleT = -1, bannerT = -1, defeatT = -1, eraseT = -1, crackT = -1, blowT = -1;
    private static int flashT = -1, flashLen = 1, flashColor = 0xFFFFFF;
    private static int glitchT = -1, glitchLen = 1;
    private static float glitchPower;
    private static int bannerPhase = 2;
    private static float shake;
    /** 詠唱している零号機（毎 Tick 本体が知らせる。来なくなったら消える） */
    private static java.lang.ref.WeakReference<LostCatastropheEntity> chanting = new java.lang.ref.WeakReference<>(null);
    private static int chantSeen;

    private ClientZeroShow() {
    }

    static boolean near(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.level() == e.level() && mc.player.distanceToSqr(e) < RANGE * RANGE;
    }

    /** 出現の題字（delay Tick 後に出す） */
    public static void intro(Entity zero, int delay) {
        if (!near(zero)) return;
        titleDelay = delay;
        titleT = -1;
    }

    /** 形態変化の帯 */
    public static void phase(Entity zero, int phase) {
        if (!near(zero)) return;
        bannerPhase = phase;
        bannerT = 0;
    }

    /** 撃破の大きな地の文 */
    public static void defeat(Entity zero) {
        if (!near(zero)) return;
        defeatT = 0;
    }

    /** 終幕カタストロフィレクイエムの始まり（2026-10-26 ユーザー指定「声なき葬送としてテキストは無しに」）: 鳴っている音と曲を止めて静まり返らせ、画面を一瞬暗くする */
    public static void finale(Entity zero) {
        if (!near(zero)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().stop();
        mc.getMusicManager().stopPlaying();
        flashT = 0;
        flashLen = 30;
        flashColor = 0x000000;
    }

    /** 終楽章の確定の一撃: 長い白い閃光・白いひび・強い揺れ */
    public static void blow(Entity zero) {
        if (!near(zero)) return;
        flashT = 0;
        flashLen = 60;
        flashColor = 0xFFFFFF;
        blowT = 0;
        shake(zero, 1.0F);
    }

    /** 世界確定消滅（白く塗りつぶして題字） */
    public static void erase(Entity zero) {
        if (!near(zero)) return;
        eraseT = 0;
        shake(zero, 1.0F);
    }

    /** 同期過電圧ショック（水色の閃光と画面のひび） */
    public static void crack(Entity zero) {
        if (!near(zero)) return;
        crackT = 0;
    }

    /** 画面の閃光（ticks の間に薄れていく） */
    public static void flash(Entity zero, int ticks, int color) {
        if (!near(zero)) return;
        flashT = 0;
        flashLen = Math.max(1, ticks);
        flashColor = color & 0xFFFFFF;
    }

    /** 画面を横に走るずれた帯（power 0〜1） */
    public static void glitch(Entity zero, int ticks, float power) {
        if (!near(zero)) return;
        if (glitchT >= 0 && glitchPower > power && glitchLen - glitchT > ticks) return;   // 強い方を残す
        glitchT = 0;
        glitchLen = Math.max(1, ticks);
        glitchPower = power;
    }

    /** 画面の揺れ（power 0〜1。近いほど強い） */
    public static void shake(Entity at, float power) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.level() != at.level()) return;
        double d = Math.sqrt(mc.player.distanceToSqr(at));
        if (d > 48.0) return;
        shake = Math.max(shake, power * (float) (1.0 - d / 48.0));
    }

    /** 詠唱の間、本体が毎 Tick 呼ぶ */
    public static void chanting(LostCatastropheEntity zero) {
        if (!near(zero)) return;
        chanting = new java.lang.ref.WeakReference<>(zero);
        chantSeen = 3;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (titleDelay >= 0 && --titleDelay < 0) {                                         // 題字は赤い閃光と一緒に
            titleT = 0;
            flashT = 0;
            flashLen = 18;
            flashColor = 0xFF2A44;
        }
        if (titleT >= 0 && ++titleT > TITLE_IN + TITLE_HOLD + TITLE_OUT) titleT = -1;
        if (bannerT >= 0 && ++bannerT > BANNER_IN + BANNER_HOLD + BANNER_OUT) bannerT = -1;
        if (blowT >= 0 && ++blowT > BLOW_LEN) blowT = -1;
        if (defeatT >= 0 && ++defeatT > DEFEAT_IN + DEFEAT_HOLD + DEFEAT_OUT) defeatT = -1;
        if (eraseT >= 0 && ++eraseT > ERASE_LEN) eraseT = -1;
        if (crackT >= 0 && ++crackT > CRACK_LEN) crackT = -1;
        if (flashT >= 0 && ++flashT > flashLen) flashT = -1;
        if (glitchT >= 0 && ++glitchT > glitchLen) glitchT = -1;
        shake *= 0.86F;
        if (shake < 0.01F) shake = 0.0F;
        if (chantSeen > 0 && --chantSeen == 0) chanting = new java.lang.ref.WeakReference<>(null);
        LostCatastropheEntity c = chanting.get();
        if (c != null && c.chantLeft() > 0 && c.chantLeft() <= 200 && c.chantLeft() % 20 == 0) glitch(c, 6, 0.35F + 0.5F * (1.0F - c.chantLeft() / 200.0F));
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.0F || Minecraft.getInstance().isPaused()) return;
        float t = (float) (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() + event.getPartialTick() : 0);
        float a = shake * 2.2F;
        event.setYaw(event.getYaw() + Mth.sin(t * 2.7F) * a);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.3F + 1.0F) * a * 0.8F);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.1F + 2.0F) * a * 0.6F);
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        Font font = mc.font;
        long seed = mc.level != null ? mc.level.getGameTime() / 2 : 0;
        LostCatastropheEntity c = chanting.get();
        if (c != null && c.finaleTicks() > 0 && !c.isDefeated()) finaleHud(g, font, w, h, c, pt, seed);
        else if (c != null && c.chantLeft() > 0 && !c.isDefeated()) chantHud(g, font, w, h, c, pt, seed);
        if (glitchT >= 0) {
            float a = glitchPower * (1.0F - (glitchT + pt) / glitchLen);
            if (a > 0.02F) bands(g, w, h, a, seed);
        }
        if (crackT >= 0) crackHud(g, w, h, crackT + pt);
        if (flashT >= 0) {
            float a = 0.85F * (1.0F - (flashT + pt) / flashLen);
            if (a > 0.01F) g.fill(0, 0, w, h, ClientRulerShow.argb(a, flashColor));
        }
        if (titleT >= 0) titleHud(g, font, w, h, titleT + pt, seed);
        if (bannerT >= 0) bannerHud(g, font, w, h, bannerT + pt, seed);
        if (blowT >= 0) blowHud(g, w, h, blowT + pt);
        if (eraseT >= 0) eraseHud(g, font, w, h, eraseT + pt, seed);
        if (defeatT >= 0) {
            float a = ClientRulerShow.fade(defeatT + pt, DEFEAT_IN, DEFEAT_HOLD, DEFEAT_OUT);
            if (a > 0.02F) {
                int cy = h / 3 + 10;
                g.fill(0, cy - 18, w, cy + 30, ClientRulerShow.argb(a * 0.35F, 0x000000));
                int lw = (int) (w * 0.4F * Mth.clamp((defeatT + pt) / 30.0F, 0.0F, 1.0F));
                g.fill(w / 2 - lw, cy - 18, w / 2 + lw, cy - 17, ClientRulerShow.argb(a, 0xFFFFFF));
                g.fill(w / 2 - lw, cy + 29, w / 2 + lw, cy + 30, ClientRulerShow.argb(a, 0xFFFFFF));
                ClientRulerShow.text(g, font, Component.translatable("entity.automata_valkyries.lost_catastrophe.defeat_narration"), w / 2, cy - 4, 2.0F, ClientRulerShow.argb(a, 0xFFF4F8));
            }
        }
    }

    /** 出現の題字: 崩れた記号から組み上がり、赤と水色にずれて重なる。線は真ん中から伸び、時々横にずれる */
    static void titleHud(GuiGraphics g, Font font, int w, int h, float t, long seed) {
        float a = ClientRulerShow.fade(t, TITLE_IN, TITLE_HOLD, TITLE_OUT);
        if (a <= 0.02F) return;
        int cy = h / 3;
        float build = Mth.clamp(t / TITLE_IN, 0.0F, 1.0F);
        float jolt = (seed % 9 == 0 && t > TITLE_IN) || t < TITLE_IN ? 1.0F - build * 0.7F : 0.0F;
        int lw = (int) (w * 0.34F * Mth.clamp(t / (TITLE_IN + 10.0F), 0.0F, 1.0F));
        g.fill(0, cy - 26, w, cy + 46, ClientRulerShow.argb(a * 0.28F, 0x100006));
        g.fill(w / 2 - lw, cy - 22, w / 2 + lw, cy - 21, ClientRulerShow.argb(a * 0.9F, 0xFF2A44));
        g.fill(w / 2 - lw, cy + 32, w / 2 + lw, cy + 33, ClientRulerShow.argb(a * 0.9F, 0xFF2A44));
        glitchText(g, font, "entity.automata_valkyries.lost_catastrophe.title_rank", w / 2, cy - 16, 1.2F, a, 0xE8D8E0, build, jolt, seed);
        glitchText(g, font, "entity.automata_valkyries.lost_catastrophe.title_name", w / 2, cy - 2, 2.2F, a, 0xFF3A5A, build, jolt, seed + 1);
        glitchText(g, font, "entity.automata_valkyries.lost_catastrophe.title_sub", w / 2, cy + 36, 1.1F, a * 0.9F, 0xD8B8FF, build, jolt * 0.5F, seed + 2);
        if (t < TITLE_IN + 6) bands(g, w, h, 0.5F * (1.0F - t / (TITLE_IN + 6)), seed);
    }

    /** 形態変化の帯 */
    static void bannerHud(GuiGraphics g, Font font, int w, int h, float t, long seed) {
        float a = ClientRulerShow.fade(t, BANNER_IN, BANNER_HOLD, BANNER_OUT);
        if (a <= 0.02F) return;
        int cy = h / 4 + 6;
        int color = bannerPhase >= 3 ? 0xFF2A44 : 0xC070FF;
        float build = Mth.clamp(t / BANNER_IN, 0.0F, 1.0F);
        g.fill(0, cy - 14, w, cy + 22, ClientRulerShow.argb(a * 0.4F, 0x0A0004));
        int lw = (int) (w * 0.5F * build);
        g.fill(w / 2 - lw, cy - 14, w / 2 + lw, cy - 13, ClientRulerShow.argb(a, color));
        g.fill(w / 2 - lw, cy + 21, w / 2 + lw, cy + 22, ClientRulerShow.argb(a, color));
        glitchText(g, font, "entity.automata_valkyries.lost_catastrophe.phase" + bannerPhase, w / 2, cy, 1.8F, a, color, build, t < BANNER_IN + 4 ? 1.0F - build * 0.6F : 0.0F, seed);
    }

    /** 世界確定消滅: 赤から白へ塗りつぶし、真ん中に題字。最後は暗く落ちる */
    static void eraseHud(GuiGraphics g, Font font, int w, int h, float t, long seed) {
        float white = t < 10 ? t / 10.0F : t < 50 ? 1.0F : Math.max(0.0F, 1.0F - (t - 50) / 30.0F);
        int color = t < 6 ? 0xFF2A44 : 0xFFFFFF;
        g.fill(0, 0, w, h, ClientRulerShow.argb(white * 0.95F, color));
        if (t > 50) g.fill(0, 0, w, h, ClientRulerShow.argb(0.6F * (1.0F - (t - 50) / 30.0F), 0x000000));
        float a = t < 14 ? 0.0F : t < 60 ? 1.0F : Math.max(0.0F, 1.0F - (t - 60) / 20.0F);
        if (a > 0.02F) glitchText(g, font, "entity.automata_valkyries.lost_catastrophe.erase_title", w / 2, h / 2 - 8, 2.6F, a, 0x200010, Mth.clamp((t - 14) / 12.0F, 0.0F, 1.0F), 0.3F, seed);
        if (t < 30) bands(g, w, h, 0.8F, seed);
    }

    /** 同期過電圧ショック: 水色の閃光と、真ん中から走る水色のひび */
    static void crackHud(GuiGraphics g, int w, int h, float s) {
        float fade = 1.0F - s / CRACK_LEN;
        if (s < 10) g.fill(0, 0, w, h, ClientRulerShow.argb(0.75F * (1.0F - s / 10.0F), 0xC8F4FF));
        g.fill(0, 0, w, h, ClientRulerShow.argb(0.18F * fade, 0x40C8FF));
        java.util.Random rnd = new java.util.Random(0x5EED);
        int cx = w / 2, cy = h / 2;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + rnd.nextDouble() * 0.4, px = cx, py = cy;
            double reach = Math.max(w, h) * (0.3 + rnd.nextDouble() * 0.35) * Mth.clamp(s / 5.0F, 0.0F, 1.0F);
            for (int seg = 0; seg < 5; seg++) {
                a += (rnd.nextDouble() - 0.5) * 0.9;
                double qx = px + Math.cos(a) * reach / 5, qy = py + Math.sin(a) * reach / 5;
                ClientRulerShow.line(g, px, py, qx, qy, 3, ClientRulerShow.argb(0.45F * fade, 0x40C8FF), 0, 0);
                ClientRulerShow.line(g, px, py, qx, qy, 1, ClientRulerShow.argb(0.95F * fade, 0xF0FCFF), 0, 0);
                px = qx;
                py = qy;
            }
        }
    }

    /** 詠唱の間: 残りが少ないほど縁が赤く沈み、最後の 10 秒は大きな秒読み */
    static void chantHud(GuiGraphics g, Font font, int w, int h, LostCatastropheEntity c, float pt, long seed) {
        float k = 1.0F - c.chantLeft() / (float) LostCatastropheEntity.CHANT_TICKS;
        float beat = 0.5F + 0.5F * Mth.sin((c.tickCount + pt) * (0.2F + 0.4F * k));
        int layers = 12, step = Math.max(2, Math.min(w, h) / 48);
        for (int i = 0; i < layers; i++) {
            float a = (0.12F + 0.4F * k) * (0.7F + 0.3F * beat) * Mth.square(1.0F - i / (float) layers);
            int col = ClientRulerShow.argb(a, 0x900018), o = i * step;
            g.fill(o, o, w - o, o + step, col);
            g.fill(o, h - o - step, w - o, h - o, col);
            g.fill(o, o + step, o + step, h - o - step, col);
            g.fill(w - o - step, o + step, w - o, h - o - step, col);
        }
        int left = c.chantLeft();
        if (left <= 200 && c.shockCharge() == 0) {
            int secs = Mth.clamp((left + 19) / 20, 1, 10);
            float in = 1.0F - (left % 20) / 20.0F;                                          // 秒の頭で大きく、薄れていく
            float a = 0.25F + 0.6F * (1.0F - in);
            glitchText(g, font, Component.literal(Integer.toString(secs)), w / 2, h / 2 - 50, 4.0F + 1.2F * (1.0F - in), a, 0xFF2A44, 1.0F, 0.25F, seed);
        }
    }

    /**
     * 終幕の間（声なき葬送。文字は出さない。2026-10-26）: 画面は暗く沈み、上下から黒い帯が閉じて映画の画面になる。縁は鼓動に合わせて楽章の色（赤・青・白）で脈打つ。
     * 上の真ん中に楽章の印（3 つの菱形。済んだ楽章は灯る）。第 2 楽章は画面を青い涙の筋が伝い落ちる。鎮魂の光が降る間は楽章の色ににじむ。
     * 終楽章は一撃へ向けて画面の縁から白く染まっていく
     */
    static void finaleHud(GuiGraphics g, Font font, int w, int h, LostCatastropheEntity c, float pt, long seed) {
        int f = c.finaleTicks();
        float age = c.tickCount + pt;
        float in = Mth.clamp(f / 20.0F, 0.0F, 1.0F);
        int m = LostCatastropheEntity.movement(f), s = LostCatastropheEntity.movementTick(f);
        int color = m == 1 ? 0x2050A0 : m == 2 ? 0xC8C8D8 : 0x700010;
        g.fill(0, 0, w, h, ClientRulerShow.argb(0.3F * in, 0x000000));
        float beat = Math.max(0.0F, Mth.sin(age * (m == 2 ? 0.25F + s * 0.004F : 0.26F)));
        beat = beat * beat;
        int layers = 12, step = Math.max(2, Math.min(w, h) / 48);
        for (int i = 0; i < layers; i++) {                                                   // 鼓動に合わせて脈打つ縁
            float a = in * (0.25F + 0.3F * beat) * Mth.square(1.0F - i / (float) layers);
            int col = ClientRulerShow.argb(a, color), o = i * step;
            g.fill(o, o, w - o, o + step, col);
            g.fill(o, h - o - step, w - o, h - o, col);
            g.fill(o, o + step, o + step, h - o - step, col);
            g.fill(w - o - step, o + step, w - o, h - o - step, col);
        }
        if (m == 1) {                                                                        // 画面を伝い落ちる青い涙の筋
            java.util.Random r = new java.util.Random(77);
            for (int i = 0; i < 18; i++) {
                int x = r.nextInt(Math.max(1, w));
                float speed = 0.6F + r.nextFloat() * 1.2F, len = 20 + r.nextInt(50);
                float y = ((age * speed * 3.0F + r.nextInt(Math.max(1, h))) % (h + len)) - len;
                g.fill(x, (int) y, x + 1, (int) (y + len), ClientRulerShow.argb(0.35F * in, 0x9FD0FF));
                g.fill(x, (int) (y + len) - 2, x + 2, (int) (y + len), ClientRulerShow.argb(0.6F * in, 0xE0F0FF));
            }
        }
        if (m == 0 || m == 1) {
            if (s >= 60 && (s - 60) % 15 < 6) g.fill(0, 0, w, h, ClientRulerShow.argb(0.18F * (1.0F - ((s - 60) % 15) / 6.0F), m == 0 ? 0xFF2040 : 0x60A0FF));   // 鎮魂の光が降った瞬間
        } else if (m == 2 && s < LostCatastropheEntity.FINAL_BLOW) {                         // 終楽章: 縁から白く染まる
            float k = s / (float) LostCatastropheEntity.FINAL_BLOW;
            int wl = 16, ws = Math.max(2, (int) (Math.min(w, h) * 0.45F * k) / wl);
            for (int i = 0; i < wl; i++) {
                float a = (0.15F + 0.6F * k) * (0.7F + 0.3F * beat) * Mth.square(1.0F - i / (float) wl);
                int col = ClientRulerShow.argb(a, 0xFFFFFF), o = i * ws;
                g.fill(o, o, w - o, o + ws, col);
                g.fill(o, h - o - ws, w - o, h - o, col);
                g.fill(o, o + ws, o + ws, h - o - ws, col);
                g.fill(w - o - ws, o + ws, w - o, h - o - ws, col);
            }
        }
        int bar = (int) (h * 0.11F * Mth.clamp(f / 30.0F, 0.0F, 1.0F) * Mth.clamp((LostCatastropheEntity.FINALE_TICKS - f) / 20.0F, 0.0F, 1.0F));
        g.fill(0, 0, w, bar, 0xFF000000);                                                   // 映画の黒い帯
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (m >= 0) {                                                                        // 楽章の印: 3 つの菱形（済んだ楽章・いまの楽章は灯る）
            int cy = Math.max(10, bar / 2), gap = 18;
            for (int i = 0; i < 3; i++) {
                int cx = w / 2 + (i - 1) * gap;
                boolean lit = i <= m;
                int col = i == 0 ? 0xFF3050 : i == 1 ? 0x7FB8FF : 0xFFFFFF;
                float a = lit ? (i == m ? 0.6F + 0.4F * beat : 0.8F) : 0.18F;
                for (int d = 0; d <= 4; d++) g.fill(cx - (4 - d), cy - d, cx + (4 - d) + 1, cy - d + 1, ClientRulerShow.argb(a, col));
                for (int d = 1; d <= 4; d++) g.fill(cx - (4 - d), cy + d, cx + (4 - d) + 1, cy + d + 1, ClientRulerShow.argb(a, col));
            }
        }
    }

    /** 終楽章の一撃の後: 白が引いていくなか、真ん中から白いひびが走って残る */
    static void blowHud(GuiGraphics g, int w, int h, float s) {
        float fade = 1.0F - s / BLOW_LEN;
        java.util.Random rnd = new java.util.Random(0xB10);
        int cx = w / 2, cy = h / 2;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 + rnd.nextDouble() * 0.3, px = cx, py = cy;
            double reach = Math.max(w, h) * (0.35 + rnd.nextDouble() * 0.4) * Mth.clamp(s / 6.0F, 0.0F, 1.0F);
            for (int seg = 0; seg < 6; seg++) {
                a += (rnd.nextDouble() - 0.5) * 0.7;
                double qx = px + Math.cos(a) * reach / 6, qy = py + Math.sin(a) * reach / 6;
                ClientRulerShow.line(g, px, py, qx, qy, 3, ClientRulerShow.argb(0.35F * fade, 0xFFD0E0), 0, 0);
                ClientRulerShow.line(g, px, py, qx, qy, 1, ClientRulerShow.argb(0.9F * fade, 0xFFFFFF), 0, 0);
                px = qx;
                py = qy;
            }
        }
    }

    /** 画面を横に走るずれた帯（赤・水色・黒） */
    static void bands(GuiGraphics g, int w, int h, float power, long seed) {
        java.util.Random r = new java.util.Random(seed * 7919L);
        int n = 3 + (int) (power * 10);
        for (int i = 0; i < n; i++) {
            int y = r.nextInt(Math.max(1, h)), bh = 1 + r.nextInt(2 + (int) (power * 10));
            int x = (int) ((r.nextDouble() - 0.5) * w * 0.3 * power), bw = (int) (w * (0.3 + r.nextDouble() * 0.9));
            int color = switch (r.nextInt(4)) {
                case 0 -> 0xFF2A44;
                case 1 -> 0x40E8FF;
                case 2 -> 0x000000;
                default -> 0xFFFFFF;
            };
            g.fill(x + r.nextInt(Math.max(1, w / 2)) - w / 4, y, x + bw, y + bh, ClientRulerShow.argb(power * (0.25F + r.nextFloat() * 0.35F), color));
        }
    }

    static void glitchText(GuiGraphics g, Font font, String key, int x, int y, float scale, float a, int color, float build, float jolt, long seed) {
        glitchText(g, font, Component.translatable(key), x, y, scale, a, color, build, jolt, seed);
    }

    /** 組み上がる文字（build 0〜1: まだの文字は崩れた記号）。jolt があると赤と水色にずれて重なる */
    static void glitchText(GuiGraphics g, Font font, Component text, int x, int y, float scale, float a, int color, float build, float jolt, long seed) {
        String s = text.getString();
        Component shown = text;
        if (build < 1.0F) {
            java.util.Random r = new java.util.Random(seed * 31L);
            StringBuilder b = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                boolean done = ch == ' ' || (i + 0.5F) / s.length() < build * 1.2F - r.nextFloat() * 0.2F;
                b.append(done ? ch : NOISE.charAt(r.nextInt(NOISE.length())));
            }
            shown = Component.literal(b.toString());
        }
        if (jolt > 0.01F) {
            java.util.Random r = new java.util.Random(seed * 131L + 7);
            int dx = (int) Math.ceil(jolt * (1 + r.nextInt(3)));
            ClientRulerShow.text(g, font, shown, x - dx, y, scale, ClientRulerShow.argb(a * 0.7F, 0xFF2040));
            ClientRulerShow.text(g, font, shown, x + dx, y + (r.nextBoolean() ? 1 : 0), scale, ClientRulerShow.argb(a * 0.6F, 0x40E8FF));
        }
        ClientRulerShow.text(g, font, shown, x, y, scale, ClientRulerShow.argb(a, color));
    }
}
