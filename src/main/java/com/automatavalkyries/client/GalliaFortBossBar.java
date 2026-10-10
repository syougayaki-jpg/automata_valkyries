package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.GalliaFortEntity;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ガリアフォート専用のボスバー（2026-10-10 ユーザー指定「ガリアフォートのボスバーをルクシオンと同じように作って」。作りは {@link LuxionBossBar} と同じで、色はガリアフォートの色）。
 * <ul>
 *   <li>舗装: 角を斜めに落とした半透明の鋼色と黒の板に、紫に光る細い縁と走査線。左に、板からはみ出す大きめの横顔の枠
 *       （暗い地に紫の光の輪・銀の角金具・ゆっくり下りる走査の光。実物のモデルを横から描く: 第 1 形態は戦車の全身、第 2 形態は人型の上半身）</li>
 *   <li>HP のゲージ: 黒い溝に、薄紫から紫への帯と銀の縁、10% ごとの目盛り、削れた分の淡い紫の残り</li>
 *   <li>歪曲障壁のゲージ（HP の下の細いゲージ。2 本目のサーバーのボスバーは描かずにここへまとめる）: ふだんは白から蒼の帯の上を三原色の光が走る
 *       （障壁の枠と同じ）。砕けている間は白で張り直すまでの残り、歪曲破界砲へ注いでいる間は赤く明滅する。下に障壁の文字</li>
 *   <li>第 2 形態: 縁の光が紫と赤で脈打ち、角金具と 50% の印が赤く、HP の帯が赤みを帯び、ゲージの下に赤い線</li>
 *   <li>撃破: HP が 0 になると、閃光とノイズのような揺れの中で細かいひびが枝分かれして走り（0.8 秒）、板・ゲージ・横顔の枠が
 *       百を超える細かい破片と火花に砕けて飛び散り、回りながら落ちて消える（〜1.6 秒。ボスバーが消えた後も最後まで描く）</li>
 * </ul>
 * サーバーのボスバーはそのままで、名前の翻訳キーで見分ける
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class GalliaFortBossBar {

    private static final String KEY = "entity.automata_valkyries.galliafort", BARRIER_KEY = "bossbar.automata_valkyries.galliafort.barrier";
    /** HP のゲージの幅・高さ、障壁のゲージの高さ、横顔の枠の大きさ、ボスバー 1 本の高さ */
    private static final int W = 182, H = 5, H2 = 3, FACE = 36, INCREMENT = 50;
    private static final int WHITE = 0xFFF4F2FA, SILVER = 0xFFB8BCCC, SILVER_DARK = 0xFF4A4E5C, LILAC = 0xFFD2B4FF, VIOLET = 0xFF6A2FC0;
    private static final int STEEL = 0xC00C0E14, STEEL_DEEP = 0xD8050608, PURPLE = 0xFF9A5CFF, PURPLE_DIM = 0xFF4A2A80, RED = 0xFFE0384F,
            BLUE = 0xFF4A8CFF, BLUE_LIGHT = 0xFFC8E4FF;
    private static final int[] PRIMARY = {0xFFFF4040, 0xFF40FF70, 0xFF5080FF};
    private static final long CRACK_MS = 800, BREAK_MS = 1600;

    /** ボスバーごとの状態（削れた分の残り・砕ける演出） */
    private static final class State {
        double trail = 1.0;
        long last = Util.getMillis(), seen;
        long broken; // 砕け始めた時刻（0 = まだ）
        boolean p2;
        int cx, y;
        List<int[]> cracks;
        List<float[]> shards;
        List<float[]> sparks;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 2 本目（障壁）のサーバーのボスバーの、いちばん新しい中身（HP のボスバーより後に描かれるので、1 フレーム前の値を使う） */
    private static float barrierProgress = 1.0F;
    private static BossEvent.BossBarColor barrierColor = BossEvent.BossBarColor.BLUE;
    private static Component barrierName = Component.translatable(BARRIER_KEY);
    private static long barrierSeen;

    private GalliaFortBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc)) return;
        if (tc.getKey().startsWith(BARRIER_KEY)) { // 障壁のボスバーは描かず、中身だけ覚えて HP の板にまとめる
            barrierProgress = boss.getProgress();
            barrierColor = boss.getColor();
            barrierName = boss.getName();
            barrierSeen = Util.getMillis();
            event.setCanceled(true);
            event.setIncrement(0);
            return;
        }
        if (!KEY.equals(tc.getKey())) return;
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

    /** ボスバーが消えた後（撃破で消える）も、砕けた破片を最後まで描く */
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

    // ---- 配置（HP のゲージの左端 x0・上端 y0 から） ----

    private static int gaugeX(State st) {
        return st.cx - W / 2 + 22;
    }

    private static int gaugeY(State st) {
        return st.y + 6;
    }

    private static int barrierY(int y0) {
        return y0 + H + 2;
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
        return barrierY(y0) + H2 + 20;
    }

    private static int faceCx(int x0) {
        return plateLeft(x0) - FACE / 2 + 6;
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        boolean p2 = boss.getColor() == BossEvent.BossBarColor.RED; // サーバーは第 2 形態でボスバーを赤にする
        st.p2 = p2;
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
        int x0 = gaugeX(st), y0 = gaugeY(st);
        int jitter = since >= 0 && (now / 40) % 3 == 0 ? (int) ((Math.sin(now * 0.37) * 3)) : 0;
        g.pose().pushPose();
        g.pose().translate(jitter, 0, 0);
        float pulse = p2 ? 0.5F + 0.5F * Mth.sin(now / 260.0F) : 0.0F;
        drawPlate(g, x0, y0, p2, pulse, now);
        drawGauge(g, st, x0, y0, p, p2);
        drawBarrier(g, x0, barrierY(y0), now);
        drawTexts(g, boss, x0, y0, p, p2);
        drawFace(g, faceCx(x0), y0 + (H + 2 + H2) / 2, p2, pulse, now);
        g.pose().popPose();
        if (since >= 0) drawCracks(g, st, since / (float) CRACK_MS, now);
    }

    /** 舗装: 角を斜めに落とした半透明の鋼色と黒の板、紫に光る細い縁、走査線、端の目盛り（第 2 形態は縁が紫と赤で脈打つ） */
    private static void drawPlate(GuiGraphics g, int x0, int y0, boolean p2, float pulse, long now) {
        int l = plateLeft(x0), r = plateRight(x0), t = plateTop(y0), b = plateBottom(y0), ch = 6;
        chamferFill(g, l, t, r, b, ch, STEEL);
        chamferFill(g, l + 3, y0 - 3, r - 3, barrierY(y0) + H2 + 3, 2, STEEL_DEEP); // ゲージの下の黒い帯
        for (int yy = t + 1; yy < b - 1; yy += 2) g.fill(l + 1, yy, r - 1, yy + 1, 0x0C9A5CFF); // 走査線
        int edge = p2 ? lerpColor(PURPLE, RED, pulse) : PURPLE;
        chamferOutline(g, l, t, r, b, ch, edge);
        chamferOutline(g, l + 2, t + 2, r - 2, b - 2, ch - 1, (0x50 << 24) | (edge & 0xFFFFFF));
        for (int i = 0; i < 4; i++) g.fill(r - 7, t + 5 + i * 3, r - 4, t + 6 + i * 3, i == 0 && p2 ? RED : (0xA0 << 24) | (PURPLE & 0xFFFFFF));
        int sweep = (int) ((now / 12) % (r - l + 40)) + l - 20; // 上の縁を光が走る
        g.fill(Math.max(l + ch, sweep), t, Math.min(r, sweep + 18), t + 1, 0xC0FFFFFF);
        g.fill(l + ch + 4, b - 2, l + ch + 40, b - 1, SILVER);
        g.fill(r - 46, t + 1, r - ch - 4, t + 2, SILVER);
    }

    /** HP のゲージ: 黒い溝・削れた分の淡い紫・薄紫から紫への帯と銀の縁・10% の目盛り（第 2 形態は赤みを帯び、赤い 50% の印） */
    private static void drawGauge(GuiGraphics g, State st, int x0, int y0, float p, boolean p2) {
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, SILVER_DARK);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0000000);
        int trw = (int) (W * st.trail);
        if (trw > 0) g.fill(x0, y0, x0 + trw, y0 + H, 0x90D2B4FF);
        int fw = (int) (W * p);
        if (fw > 0) {
            g.fillGradient(x0, y0, x0 + fw, y0 + H, p2 ? 0xFFFFB4C0 : LILAC, p2 ? 0xFF9A1830 : VIOLET);
            g.fill(x0, y0, x0 + fw, y0 + 1, 0xFFFFFFFF);
            g.fill(x0 + Math.max(0, fw - 2), y0, x0 + fw, y0 + H, p2 ? 0xFFFFD0D8 : 0xFFEADCFF);
        }
        for (int i = 1; i < 10; i++) g.fill(x0 + W * i / 10, y0 + 1, x0 + W * i / 10 + 1, y0 + H - 1, 0x50000000);
        int half = p2 ? RED : SILVER;
        g.fill(x0 + W / 2, y0 - 3, x0 + W / 2 + 1, y0, half); // 50%（第 2 形態に入る所）
        g.fill(x0 + W / 2, y0 + H, x0 + W / 2 + 1, y0 + H + 2, half);
    }

    /** 歪曲障壁のゲージ（ふだんは白から蒼の帯に三原色の光が走る。砕けている間は白、歪曲破界砲へ注いでいる間は赤く明滅） */
    private static void drawBarrier(GuiGraphics g, int x0, int y, long now) {
        boolean fresh = now - barrierSeen < 1000;
        float p = fresh ? Mth.clamp(barrierProgress, 0.0F, 1.0F) : 1.0F;
        BossEvent.BossBarColor c = fresh ? barrierColor : BossEvent.BossBarColor.BLUE;
        g.fill(x0 - 1, y - 1, x0 + W + 1, y + H2 + 1, SILVER_DARK);
        g.fill(x0, y, x0 + W, y + H2, 0xF0000000);
        int fw = (int) (W * p);
        if (c == BossEvent.BossBarColor.RED) {                                     // 歪曲破界砲へ注いでいる（全ダメージが通る）
            int a = (int) (140 + 115 * (0.5F + 0.5F * Mth.sin(now / 90.0F)));
            g.fill(x0, y, x0 + W, y + H2, (a << 24) | (RED & 0xFFFFFF));
        } else if (c == BossEvent.BossBarColor.WHITE) {                            // 砕けている（張り直すまでの残り）
            if (fw > 0) g.fill(x0, y, x0 + fw, y + H2, 0xC0E8E8F0);
        } else if (fw > 0) {
            g.fillGradient(x0, y, x0 + fw, y + H2, BLUE_LIGHT, BLUE);
            for (int k = 0; k < 3; k++) {                                          // 帯の上を走る三原色の光
                int px = x0 + (int) (((now / 6 + k * W / 3) % (W + 12)) - 6);
                int l = Math.max(x0, px), r = Math.min(x0 + fw, px + 6);
                if (r > l) g.fill(l, y, r, y + H2, (0xC0 << 24) | (PRIMARY[k] & 0xFFFFFF));
            }
            g.fill(x0, y, x0 + fw, y + 1, 0x90FFFFFF);
        }
    }

    /** 名前（白に紫の影）と、下の段（格・形態 / HP）、その下に障壁の文字 */
    private static void drawTexts(GuiGraphics g, LerpingBossEvent boss, int x0, int y0, float p, boolean p2) {
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        int nx = x0 + W / 2 - font.width(name) / 2, ny = y0 - 11;
        g.drawString(font, name, nx + 1, ny + 1, p2 ? 0xFF5A1420 : 0xFF3A1A60, false);
        g.drawString(font, name, nx, ny, p2 ? 0xFFFFE6EA : WHITE, false);
        g.pose().pushPose();
        g.pose().translate(x0, barrierY(y0) + H2 + 3, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable(p2 ? "bossbar.automata_valkyries.galliafort.phase2" : "bossbar.automata_valkyries.galliafort.rank"), 0, 0,
                p2 ? 0xFFFF8A9C : 0xFFC8A8FF, false);
        String hp = Math.round(p * GalliaFortEntity.MAX_HP) + " / " + Math.round(GalliaFortEntity.MAX_HP);
        g.drawString(font, hp, (int) (W / 0.75F) - font.width(hp), 0, 0xFFEADCFF, false);
        int bc = barrierColor == BossEvent.BossBarColor.RED ? 0xFFFF8A9C : barrierColor == BossEvent.BossBarColor.WHITE ? 0xFFE8E8F0 : 0xFF9CC8FF;
        g.drawString(font, barrierName, 0, 11, bc, false);
        g.pose().popPose();
    }

    /** 横顔用の、ワールドに置かない動かないガリアフォート（同じワールドの間は使い回す） */
    private static GalliaFortEntity portraitModel;

    private static GalliaFortEntity portraitModel(boolean p2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (portraitModel == null || portraitModel.level() != mc.level) {
            portraitModel = new GalliaFortEntity(com.automatavalkyries.registry.ModEntities.GALLIAFORT.get(), mc.level);
        }
        portraitModel.portraitState(p2);
        portraitModel.yBodyRot = portraitModel.yBodyRotO = portraitModel.yHeadRot = portraitModel.yHeadRotO = 270.0F;
        portraitModel.setYRot(270.0F);
        portraitModel.setXRot(0.0F);
        portraitModel.xRotO = 0.0F;
        return portraitModel;
    }

    /**
     * 横顔の枠: 角を落とした暗い地に、紫の光の輪（2 重）・銀の角金具（第 2 形態は赤）・ゆっくり下りる走査の光。
     * 右（ゲージの方）を向いた横顔で、第 1 形態は戦車の全身、第 2 形態は人型の上半身（頭から胸）を描く
     */
    private static void drawFace(GuiGraphics g, int cx, int cy, boolean p2, float pulse, long now) {
        int x = cx - FACE / 2, y = cy - FACE / 2, ch = 6;
        chamferFill(g, x - 3, y - 3, x + FACE + 3, y + FACE + 3, ch + 2, 0xE006060C);
        int ring = p2 ? lerpColor(PURPLE, RED, pulse) : PURPLE;
        chamferOutline(g, x - 3, y - 3, x + FACE + 3, y + FACE + 3, ch + 2, ring);
        chamferOutline(g, x - 1, y - 1, x + FACE + 1, y + FACE + 1, ch, (0x80 << 24) | (ring & 0xFFFFFF));
        g.fillGradient(x, y, x + FACE, y + FACE, 0xD02A1A44, 0xD0080610);
        GalliaFortEntity fort = portraitModel(p2);
        if (fort != null) {
            g.enableScissor(x, y, x + FACE, y + FACE);
            // 第 1 形態は全身（長さ約 9 ブロック・高さ約 2 ブロックの所を真ん中に）、第 2 形態は頭と胸（約 8.5 ブロックの高さを真ん中に）
            int scale = p2 ? 5 : 4;
            float centerH = p2 ? 8.6F : 2.0F;
            InventoryScreen.renderEntityInInventory(g, cx, cy + (int) (centerH * scale), scale, new Quaternionf().rotateZ((float) Math.PI), null, fort);
            g.disableScissor();
        } else {
            diamond(g, cx, cy, 9, PURPLE);
            diamond(g, cx, cy, 6, WHITE);
            diamond(g, cx, cy, 2, PURPLE);
        }
        int scan = y + (int) ((now / 30) % FACE);
        g.fill(x, scan, x + FACE, scan + 1, 0x409A5CFF);
        int corner = p2 ? RED : SILVER;
        bracket(g, x - 4, y - 4, 1, 1, corner);
        bracket(g, x + FACE + 3, y - 4, -1, 1, corner);
        bracket(g, x - 4, y + FACE + 3, 1, -1, corner);
        bracket(g, x + FACE + 3, y + FACE + 3, -1, -1, corner);
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * 5), y, Math.max(x, x + dx * 5) + 1, y + 1, color);
        g.fill(x, Math.min(y, y + dy * 5), x + 1, Math.max(y, y + dy * 5) + 1, color);
    }

    // ---- 砕ける演出 ----

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
                if (r.nextInt(3) == 0) {
                    double sa = ang + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    st.cracks.add(new int[]{(int) nx, (int) ny, (int) (nx + Math.cos(sa) * 6), (int) (ny + Math.sin(sa) * 2), s + 1});
                }
                x = nx;
                y = ny;
            }
        }
        st.shards = new ArrayList<>();
        int cols = 30, rows = 5;
        float cw = (rt - l) / (float) cols, chh = (b - t) / (float) rows;
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                float sx = l + cw * (c + 0.5F) + (r.nextFloat() - 0.5F) * 2, sy = t + chh * (rr + 0.5F) + (r.nextFloat() - 0.5F) * 2;
                float vx = (sx - ix) * 0.003F + (r.nextFloat() - 0.5F) * 0.08F, vy = -0.04F - r.nextFloat() * 0.1F;
                int kind = sy >= y0 - 2 && sy <= y0 + H + 2 ? (r.nextBoolean() ? 1 : 2) : sy >= barrierY(y0) - 1 && sy <= barrierY(y0) + H2 + 1 ? 4 : 0;
                st.shards.add(new float[]{sx, sy, cw * (0.5F + r.nextFloat() * 0.6F), chh * (0.5F + r.nextFloat() * 0.6F), vx, vy,
                        (r.nextFloat() - 0.5F) * 0.02F, kind});
            }
        }
        int fx = faceCx(x0) - FACE / 2, fy = y0 + (H + 2 + H2) / 2 - FACE / 2;
        float fs = (FACE + 6) / 4F;
        for (int c = 0; c < 4; c++) {
            for (int rr = 0; rr < 4; rr++) {
                float sx = fx - 3 + fs * (c + 0.5F), sy = fy - 3 + fs * (rr + 0.5F);
                st.shards.add(new float[]{sx, sy, fs * (0.7F + r.nextFloat() * 0.4F), fs * (0.7F + r.nextFloat() * 0.4F),
                        -0.03F - r.nextFloat() * 0.05F, -0.05F - r.nextFloat() * 0.06F, (r.nextFloat() - 0.5F) * 0.015F, 3});
            }
        }
        st.sparks = new ArrayList<>();
        for (int i = 0; i < 56; i++) {
            double a = r.nextDouble() * Math.PI * 2, sp = 0.05 + r.nextDouble() * 0.18;
            st.sparks.add(new float[]{ix, iy, (float) (Math.cos(a) * sp * 1.8), (float) (Math.sin(a) * sp - 0.05), r.nextInt(4)});
        }
    }

    private static void drawCracks(GuiGraphics g, State st, float k, long now) {
        if (st.cracks == null) return;
        int x0 = gaugeX(st), y0 = gaugeY(st);
        if (k < 0.15F) {
            int a = (int) ((1 - k / 0.15F) * 160);
            g.fill(faceCx(x0) - FACE / 2 - 4, plateTop(y0) - 4, plateRight(x0) + 2, plateBottom(y0) + 4, (a << 24) | 0xFFFFFF);
        }
        if ((now / 50) % 2 == 0) {
            int yy = plateTop(y0) + (int) ((now / 7) % (plateBottom(y0) - plateTop(y0)));
            g.fill(plateLeft(x0), yy, plateRight(x0), yy + 1, 0x609A5CFF);
            g.fill(plateLeft(x0) + 2, yy + 1, plateRight(x0) + 2, yy + 2, 0x50E0384F);
        }
        int shown = (int) (k * 9.999F);
        int glow = st.p2 ? 0x90FF7088 : 0x90D2B4FF;
        for (int[] c : st.cracks) {
            if (c[4] > shown) continue;
            line(g, c[0], c[1] + 1, c[2], c[3] + 1, glow);
            line(g, c[0], c[1], c[2], c[3], 0xFFFFFFFF);
        }
    }

    private static void drawShards(GuiGraphics g, State st, long t) {
        if (st.shards == null) return;
        float alpha = Math.max(0.0F, 1.0F - t / (float) BREAK_MS);
        int a = (int) (alpha * 255);
        if (a <= 0) return;
        for (float[] s : st.shards) {
            float x = s[0] + s[4] * t, y = s[1] + s[5] * t + 0.00012F * t * t;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotation(s[6] * t));
            int hw = Math.max(1, (int) (s[2] / 2)), hh = Math.max(1, (int) (s[3] / 2));
            int kind = (int) s[7];
            int edge = switch (kind) {
                case 0, 3 -> st.p2 ? RED : PURPLE;
                case 4 -> BLUE;
                default -> SILVER;
            };
            int face = switch (kind) {
                case 1 -> st.p2 ? 0xFFFFB4C0 : LILAC;
                case 2 -> st.p2 ? 0xFF9A1830 : VIOLET;
                case 3 -> 0xFF100A1C;
                case 4 -> BLUE_LIGHT;
                default -> 0xFF0C0E14;
            };
            int fa = kind == 0 ? (int) (a * 0.8F) : a;
            g.fill(-hw, -hh, hw, hh, (a << 24) | (edge & 0xFFFFFF));
            if (hw > 1 && hh > 1) g.fill(-hw + 1, -hh + 1, hw - 1, hh - 1, (fa << 24) | (face & 0xFFFFFF));
            g.pose().popPose();
        }
        if (st.sparks != null && t < BREAK_MS * 0.6F) {
            float sa = Math.max(0.0F, 1.0F - t / (BREAK_MS * 0.6F));
            int spa = (int) (sa * 255);
            for (float[] s : st.sparks) {
                float x = s[0] + s[2] * t, y = s[1] + s[3] * t + 0.00008F * t * t;
                int k = (int) s[4];
                int c = k == 0 ? 0xFFFFFF : PRIMARY[k - 1] & 0xFFFFFF;                // 白と三原色の火花
                g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, (spa << 24) | c);
            }
        }
    }

    // ---- 描く道具 ----

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

    private static void diamond(GuiGraphics g, int x, int y, int r, int color) {
        g.pose().pushPose();
        g.pose().translate(x + 0.5F, y + 0.5F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        float s = r * 0.7071F;
        g.pose().scale(s, s, 1.0F);
        g.fill(-1, -1, 1, 1, color);
        g.pose().popPose();
    }
}
