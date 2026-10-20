package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.TimeRulerEntity;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 総司令機タイムルーラー専用のボスバー（2026-10-20 ユーザー指定「専用のボスバー」。作りは {@link MementoBossBar}・{@link GalliaFortBossBar}・{@link LuxionBossBar} と同じで、色と飾りは時計）。
 * <ul>
 *   <li>舗装: 角を斜めに落とした半透明の深い藍と黒の板に、金に光る細い縁と走査線、上の縁を走る光、板のまわりを漂う金の歯車の粒。
 *       左に、板からはみ出す丸い文字盤の枠（XII・III・VI・IX と 60 の刻み、長針と短針が回る。時間停止の間は針が止まる）</li>
 *   <li>HP のゲージ: 黒い溝に、形態の色の帯（第 1 形態 金・第 2 形態 紫・第 3 形態 紅）、12 の時の目盛り、削れた分の淡い残り。
 *       66%・33%（形態が変わる所）と 10%（刻の終焉）の印</li>
 *   <li>下の段: 形態の名（第一刻・第二刻・第三刻）と HP。時が止まっている間は「― 時は止まった ―」と残りの秒、縁が白く脈打つ</li>
 *   <li>撃破: 閃光の中でひびが走り（0.8 秒）、板・ゲージ・文字盤が金の歯車の欠片になって回りながら散り、落ちて消える（〜1.6 秒）</li>
 * </ul>
 * サーバーのボスバーはそのままで、名前の翻訳キーで見分ける。形態はサーバーがボスバーの色で伝える（黄・紫・赤）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class TimeRulerBossBar {

    private static final String KEY = "entity.automata_valkyries.time_ruler";
    /** HP のゲージの幅・高さ、文字盤の大きさ、ボスバー 1 本の高さ */
    private static final int W = 182, H = 6, DIAL = 36, INCREMENT = 46;
    private static final int WHITE = 0xFFFFF8E8, GOLD = 0xFFFFD166, GOLD_DEEP = 0xFFB07A20, PALE = 0xFFFFF0B0, SILVER_DARK = 0xFF3A3450;
    private static final int VIOLET = 0xFFB070FF, VIOLET_DEEP = 0xFF5A2AB0, RED = 0xFFFF4060, RED_DEEP = 0xFF901830, CYAN = 0xFF7FE4FF;
    private static final int PLATE = 0xC00A0818, PLATE_DEEP = 0xD805040C;
    private static final long CRACK_MS = 800, BREAK_MS = 1600;

    private static final class State {
        double trail = 1.0;
        long last = Util.getMillis(), seen;
        long broken;
        int phase = 1;
        int cx, y;
        List<int[]> cracks;
        List<float[]> shards;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private TimeRulerBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(INCREMENT);
        GuiGraphics g = event.getGuiGraphics();
        if (STATES.size() > 16) STATES.clear();
        State st = STATES.computeIfAbsent(boss.getId(), k -> new State());
        st.cx = g.guiWidth() / 2;
        st.y = event.getY();
        st.seen = Util.getMillis();
        draw(g, st, boss);
    }

    /** ボスバーが消えた後も、散る欠片を最後まで描く */
    @SubscribeEvent
    public static void afterBossBars(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) return;
        long now = Util.getMillis();
        for (Iterator<State> it = STATES.values().iterator(); it.hasNext(); ) {
            State st = it.next();
            if (now - st.seen < 100) continue;
            if (st.broken == 0 || now - st.broken > CRACK_MS + BREAK_MS) {
                it.remove();
                continue;
            }
            if (now - st.broken > CRACK_MS) drawShards(event.getGuiGraphics(), st, now - st.broken - CRACK_MS);
        }
    }

    // ---- 配置 ----

    private static int gaugeX(State st) {
        return st.cx - W / 2 + 22;
    }

    private static int gaugeY(State st) {
        return st.y + 6;
    }

    private static int plateLeft(int x0) {
        return x0 - 10;
    }

    private static int plateRight(int x0) {
        return x0 + W + 12;
    }

    private static int plateTop(int y0) {
        return y0 - 13;
    }

    private static int plateBottom(int y0) {
        return y0 + H + 16;
    }

    private static int dialCx(int x0) {
        return plateLeft(x0) - DIAL / 2 + 6;
    }

    private static int phaseOf(LerpingBossEvent boss) {
        return boss.getColor() == BossEvent.BossBarColor.RED ? 3 : boss.getColor() == BossEvent.BossBarColor.PURPLE ? 2 : 1;
    }

    private static int phaseColor(int phase) {
        return phase == 3 ? RED : phase == 2 ? VIOLET : GOLD;
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        st.phase = phaseOf(boss);
        double dt = Math.min(0.2, (now - st.last) / 1000.0);
        st.last = now;
        st.trail = p >= st.trail ? p : Math.max(p, st.trail - 0.25 * dt);
        if (p > 0.0F) {
            st.broken = 0;
        } else if (st.broken == 0) {
            st.broken = now;
            prepareBreak(st, boss.getId());
        }
        long since = st.broken == 0 ? -1 : now - st.broken;
        if (since > CRACK_MS) {
            drawShards(g, st, since - CRACK_MS);
            return;
        }
        boolean stopped = ClientTimeStop.running();
        int x0 = gaugeX(st), y0 = gaugeY(st);
        int jitter = since >= 0 && (now / 40) % 3 == 0 ? (int) (Math.sin(now * 0.37) * 3) : 0;
        g.pose().pushPose();
        g.pose().translate(jitter, 0, 0);
        int edge = stopped ? lerpColor(WHITE, CYAN, 0.5F + 0.5F * Mth.sin(now / 120.0F)) : st.phase == 3 ? lerpColor(RED, GOLD, 0.5F + 0.5F * Mth.sin(now / 300.0F)) : phaseColor(st.phase);
        drawPlate(g, x0, y0, st.phase, edge, now);
        drawGauge(g, st, x0, y0, p, now);
        drawTexts(g, boss, x0, y0, p, st.phase, stopped);
        drawDial(g, dialCx(x0), y0 + H / 2, st.phase, edge, now, stopped);
        g.pose().popPose();
        if (since >= 0) drawCracks(g, st, since / (float) CRACK_MS);
    }

    /** 舗装: 角を斜めに落とした板、光る縁、走査線、上の縁を走る光、板のまわりを漂う金の歯車の粒、右端の小さな歯車 */
    private static void drawPlate(GuiGraphics g, int x0, int y0, int phase, int edge, long now) {
        int l = plateLeft(x0), r = plateRight(x0), t = plateTop(y0), b = plateBottom(y0), ch = 6;
        chamferFill(g, l, t, r, b, ch, PLATE);
        chamferFill(g, l + 3, y0 - 3, r - 3, y0 + H + 3, 2, PLATE_DEEP);
        for (int yy = t + 1; yy < b - 1; yy += 2) g.fill(l + 1, yy, r - 1, yy + 1, 0x0CFFD166);
        chamferOutline(g, l, t, r, b, ch, edge);
        chamferOutline(g, l + 2, t + 2, r - 2, b - 2, ch - 1, (0x50 << 24) | (edge & 0xFFFFFF));
        int sweep = (int) ((now / 12) % (r - l + 40)) + l - 20;
        g.fill(Math.max(l + ch, sweep), t, Math.min(r, sweep + 18), t + 1, 0xC0FFFFFF);
        g.fill(l + ch + 4, b - 2, l + ch + 40, b - 1, GOLD_DEEP);
        g.fill(r - 46, t + 1, r - ch - 4, t + 2, GOLD_DEEP);
        gear(g, r - 8, t + 7, 4, now / 400.0F, edge);                                    // 右上の小さな歯車
        for (int i = 0; i < 12; i++) {                                                     // 漂う金の歯車の粒
            long seed = i * 6151L;
            int span = r - l;
            int mx = l + (int) ((seed * 11 + now / (20 + i % 5)) % span);
            int my = (i % 2 == 0 ? t - 2 : b - 1) + (int) (Math.sin(now / 320.0 + i) * 2);
            int a = (int) (90 + 90 * Math.sin(now / 200.0 + i * 1.3));
            int c = i % 3 == 0 ? phaseColor(phase) : i % 3 == 1 ? PALE : GOLD;
            if (a > 8) g.fill(mx, my, mx + 1 + i % 2, my + 1 + i % 2, (a << 24) | (c & 0xFFFFFF));
        }
    }

    /** HP のゲージ: 形態の色の帯、削れた分の残り、12 の時の目盛り、66%・33%・10% の印 */
    private static void drawGauge(GuiGraphics g, State st, int x0, int y0, float p, long now) {
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, SILVER_DARK);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0000000);
        int trw = (int) (W * st.trail);
        if (trw > 0) g.fill(x0, y0, x0 + trw, y0 + H, 0x90FFF0B0);
        int fw = (int) (W * p);
        if (fw > 0) {
            int top = st.phase == 3 ? 0xFFFF8090 : st.phase == 2 ? 0xFFD8B0FF : PALE;
            int bottom = st.phase == 3 ? RED_DEEP : st.phase == 2 ? VIOLET_DEEP : GOLD_DEEP;
            g.fillGradient(x0, y0, x0 + fw, y0 + H, top, bottom);
            for (int k = 0; k < 3; k++) {                                                   // 流れる光
                int px = x0 + (int) (((now / 6 + k * 71) % (W + 8)) - 4);
                if (px >= x0 && px < x0 + fw) g.fill(px, y0, px + 2, y0 + H, 0x90FFFFFF);
            }
            g.fill(x0, y0, x0 + fw, y0 + 1, 0xFFFFFFFF);
            g.fill(x0 + Math.max(0, fw - 2), y0, x0 + fw, y0 + H, 0xFFFFF8E8);
        }
        for (int i = 1; i < 12; i++) g.fill(x0 + W * i / 12, y0 + 1, x0 + W * i / 12 + 1, y0 + H - 1, 0x60000000);   // 12 の時の目盛り
        for (float mark : new float[]{2.0F / 3.0F, 1.0F / 3.0F}) {                          // 形態が変わる所
            int mx = x0 + (int) (W * mark);
            g.fill(mx, y0 - 3, mx + 1, y0, GOLD);
            g.fill(mx, y0 + H, mx + 1, y0 + H + 2, GOLD);
        }
        int fin = x0 + (int) (W * 0.1F);                                                     // 刻の終焉
        g.fill(fin, y0 + H, fin + 1, y0 + H + 3, RED);
    }

    /** 名前（白に金の影）と、下の段（形態の名 / HP。時が止まっている間は残りの秒） */
    private static void drawTexts(GuiGraphics g, LerpingBossEvent boss, int x0, int y0, float p, int phase, boolean stopped) {
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        int nx = x0 + W / 2 - font.width(name) / 2, ny = y0 - 11;
        g.drawString(font, name, nx + 1, ny + 1, 0xFF4A3010, false);
        g.drawString(font, name, nx, ny, WHITE, false);
        g.pose().pushPose();
        g.pose().translate(x0, y0 + H + 4, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        Component left = stopped
                ? Component.translatable("bossbar.automata_valkyries.time_ruler.stopped", (ClientTimeStop.remainingTicks() + 19) / 20)
                : Component.translatable("bossbar.automata_valkyries.time_ruler.phase" + phase);
        g.drawString(font, left, 0, 0, stopped ? 0xFFB8F4FF : phase == 3 ? 0xFFFF9CAC : phase == 2 ? 0xFFD8B0FF : 0xFFFFE0A0, false);
        String hp = Math.round(p * TimeRulerEntity.MAX_HP) + " / " + Math.round(TimeRulerEntity.MAX_HP);
        g.drawString(font, hp, (int) (W / 0.75F) - font.width(hp), 0, 0xFFFFF0D0, false);
        g.pose().popPose();
    }

    /** 文字盤の枠: 暗い円の地、金の 2 重の輪、60 の刻みと XII・III・VI・IX、回る長針と短針（時が止まると止まる）、角金具 */
    private static void drawDial(GuiGraphics g, int cx, int cy, int phase, int ring, long now, boolean stopped) {
        int x = cx - DIAL / 2, y = cy - DIAL / 2, ch = 6;
        chamferFill(g, x - 3, y - 3, x + DIAL + 3, y + DIAL + 3, ch + 2, 0xE006040C);
        chamferOutline(g, x - 3, y - 3, x + DIAL + 3, y + DIAL + 3, ch + 2, ring);
        float rad = DIAL / 2.0F - 2;
        disc(g, cx, cy, rad, 0xD0141028);
        circle(g, cx, cy, rad, ring);
        circle(g, cx, cy, rad - 2, (0x80 << 24) | (GOLD & 0xFFFFFF));
        for (int k = 0; k < 60; k++) {
            double a = Math.PI * 2 * k / 60;
            float r0 = k % 5 == 0 ? rad - 5 : rad - 3.5F;
            line(g, cx + (int) Math.round(Math.sin(a) * r0), cy - (int) Math.round(Math.cos(a) * r0),
                    cx + (int) Math.round(Math.sin(a) * (rad - 2.5F)), cy - (int) Math.round(Math.cos(a) * (rad - 2.5F)), k % 5 == 0 ? GOLD : 0x80FFD166);
        }
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.5F, 0.5F, 1.0F);
        String[] marks = {"XII", "III", "VI", "IX"};
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k;
            int tx = (int) Math.round(Math.sin(a) * (rad - 9) * 2), ty = (int) Math.round(-Math.cos(a) * (rad - 9) * 2);
            g.drawString(font, marks[k], tx - font.width(marks[k]) / 2, ty - 4, PALE, false);
        }
        g.pose().popPose();
        float t = stopped ? 0.0F : now / 1000.0F;
        double minute = stopped ? 0.0 : t * (phase == 3 ? 3.0 : phase == 2 ? 2.0 : 1.2);   // 時が止まると長針は XII で止まる
        double hour = stopped ? 0.0 : t * 0.1;
        hand(g, cx, cy, hour, rad * 0.5F, 0xFFFFF0D0);
        hand(g, cx, cy, minute, rad * 0.78F, phaseColor(phase));
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, WHITE);
        int corner = phaseColor(phase);
        bracket(g, x - 4, y - 4, 1, 1, corner);
        bracket(g, x + DIAL + 3, y - 4, -1, 1, corner);
        bracket(g, x - 4, y + DIAL + 3, 1, -1, corner);
        bracket(g, x + DIAL + 3, y + DIAL + 3, -1, -1, corner);
    }

    private static void hand(GuiGraphics g, int cx, int cy, double ang, float len, int color) {
        int ex = cx + (int) Math.round(Math.sin(ang) * len), ey = cy - (int) Math.round(Math.cos(ang) * len);
        line(g, cx, cy, ex, ey, color);
        line(g, cx + 1, cy, ex + 1, ey, (0x80 << 24) | (color & 0xFFFFFF));
    }

    // ---- 撃破の演出（ひびが走り、金の歯車の欠片になって回りながら散り、落ちる） ----

    private static void prepareBreak(State st, UUID id) {
        RandomSource r = RandomSource.create(id.getLeastSignificantBits());
        int x0 = gaugeX(st), y0 = gaugeY(st);
        int l = plateLeft(x0), rt = plateRight(x0), t = plateTop(y0), b = plateBottom(y0);
        st.cracks = new ArrayList<>();
        int ix = x0 + W / 3 + r.nextInt(W / 3), iy = y0 + H / 2;
        for (int br = 0; br < 12; br++) {
            double ang = br * (Math.PI * 2 / 12) + r.nextDouble() * 0.5;
            double x = ix, y = iy;
            for (int s = 0; s < 9; s++) {
                ang += (r.nextDouble() - 0.5) * 1.0;
                double len = 3 + r.nextDouble() * 7;
                double nx = x + Math.cos(ang) * len * 2.4, ny = y + Math.sin(ang) * len * 0.6;
                st.cracks.add(new int[]{(int) x, (int) y, (int) nx, (int) ny, s});
                x = nx;
                y = ny;
            }
        }
        // 欠片: 位置・大きさ・横の速さ・上への速さ・回りの位相・種類（0 = 板、1 = ゲージ、2 = 文字盤）
        st.shards = new ArrayList<>();
        int cols = 26, rows = 4;
        float cw = (rt - l) / (float) cols, chh = (b - t) / (float) rows;
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                float sx = l + cw * (c + 0.5F), sy = t + chh * (rr + 0.5F);
                int kind = sy >= y0 - 2 && sy <= y0 + H + 2 ? 1 : 0;
                st.shards.add(new float[]{sx, sy, cw * (0.5F + r.nextFloat() * 0.5F), (r.nextFloat() - 0.5F) * 0.06F,
                        0.03F + r.nextFloat() * 0.06F, r.nextFloat() * 6.28F, kind});
            }
        }
        int dcx = dialCx(x0), dcy = y0 + H / 2;
        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16;
            st.shards.add(new float[]{dcx + (float) Math.cos(a) * 10, dcy + (float) Math.sin(a) * 10, 5 + r.nextFloat() * 3,
                    (float) Math.cos(a) * 0.05F, 0.04F + r.nextFloat() * 0.05F, r.nextFloat() * 6.28F, 2});
        }
    }

    private static void drawCracks(GuiGraphics g, State st, float k) {
        if (st.cracks == null) return;
        int x0 = gaugeX(st), y0 = gaugeY(st);
        if (k < 0.15F) {
            int a = (int) ((1 - k / 0.15F) * 160);
            g.fill(dialCx(x0) - DIAL / 2 - 4, plateTop(y0) - 4, plateRight(x0) + 2, plateBottom(y0) + 4, (a << 24) | 0xFFF0B0);
        }
        int shown = (int) (k * 9.999F);
        for (int[] c : st.cracks) {
            if (c[4] > shown) continue;
            line(g, c[0], c[1] + 1, c[2], c[3] + 1, 0x90FFD166);
            line(g, c[0], c[1], c[2], c[3], 0xFFFFFFFF);
        }
    }

    /** 金の歯車の欠片: 回りながら一度ふわりと上がり、落ちていき、小さく透けて消える */
    private static void drawShards(GuiGraphics g, State st, long t) {
        if (st.shards == null) return;
        float life = Math.max(0.0F, 1.0F - t / (float) BREAK_MS);
        int a = (int) (life * 255);
        if (a <= 0) return;
        for (float[] s : st.shards) {
            float x = s[0] + s[3] * t;
            float y = s[1] - s[4] * t + 0.00012F * t * t;
            int kind = (int) s[6];
            int c = kind == 1 ? phaseColor(st.phase) : kind == 2 ? PALE : (s[5] > 3.14F ? GOLD : GOLD_DEEP);
            float size = (life * 0.7F + 0.3F) * s[2] / 2;
            gear(g, (int) x, (int) y, Math.max(1, (int) size), t * 0.01F + s[5], (a << 24) | (c & 0xFFFFFF));
        }
    }

    // ---- 描く道具 ----

    /** 小さな歯車（輪と 6 つの歯） */
    private static void gear(GuiGraphics g, int cx, int cy, int r, float rot, int color) {
        g.pose().pushPose();
        g.pose().translate(cx + 0.5F, cy + 0.5F, 0);
        g.pose().mulPose(Axis.ZP.rotation(rot));
        g.fill(-r, -r / 2, r + 1, r / 2 + 1, color);
        g.fill(-r / 2, -r, r / 2 + 1, r + 1, color);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        g.fill(-r, -Math.max(1, r / 3), r + 1, Math.max(1, r / 3) + 1, color);
        g.fill(-Math.max(1, r / 3), -r, Math.max(1, r / 3) + 1, r + 1, color);
        g.pose().popPose();
        if (r >= 3) g.fill(cx, cy, cx + 1, cy + 1, 0xFF000000);
    }

    private static void disc(GuiGraphics g, int cx, int cy, float r, int color) {
        int ri = (int) Math.ceil(r);
        for (int dy = -ri; dy <= ri; dy++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void circle(GuiGraphics g, int cx, int cy, float r, int color) {
        int n = Math.max(24, (int) (r * 6));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * 5), y, Math.max(x, x + dx * 5) + 1, y + 1, color);
        g.fill(x, Math.min(y, y + dy * 5), x + 1, Math.max(y, y + dy * 5) + 1, color);
    }

    private static void chamferFill(GuiGraphics g, int l, int t, int r, int b, int ch, int color) {
        for (int y = t; y < b; y++) {
            int top = Math.max(0, ch - (y - t)), bottom = Math.max(0, ch - (b - 1 - y));
            g.fill(l + top, y, r - bottom, y + 1, color);
        }
    }

    private static void chamferOutline(GuiGraphics g, int l, int t, int r, int b, int ch, int color) {
        g.fill(l + ch, t, r, t + 1, color);
        g.fill(l, b - 1, r - ch, b, color);
        g.fill(l, t + ch, l + 1, b, color);
        g.fill(r - 1, t, r, b - ch, color);
        line(g, l, t + ch, l + ch, t, color);
        line(g, r - 1 - ch, b - 1, r - 1, b - 1 - ch, color);
    }

    private static int lerpColor(int a, int b, float k) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (int) Mth.lerp(k, ar, br) << 16 | (int) Mth.lerp(k, ag, bg) << 8 | (int) Mth.lerp(k, ab, bb);
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x1 : x1 + (x2 - x1) * i / n, y = n == 0 ? y1 : y1 + (y2 - y1) * i / n;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
