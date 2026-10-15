package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.MementoCurse;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
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
 * メメントモリ専用のボスバー（2026-10-15 ユーザー指定「ボスバーも他のボスと同じように」。作りは {@link GalliaFortBossBar}・{@link LuxionBossBar} と同じで、色と飾りはメメントモリ）。
 * <ul>
 *   <li>舗装: 角を斜めに落とした半透明の深い紫と黒の板に、紫に光る細い縁と走査線、板のまわりを漂うモザイクの光の四角（翅の模様と同じ）。
 *       左に、板からはみ出す大きめの横顔の枠（暗い地に紫の光の輪・銀の角金具。実物のモデルの頭と胸を横から描く）</li>
 *   <li>HP のゲージ: 黒い溝に、淡い紫から紫への帯、10% ごとの目盛り、削れた分の淡い紫の残り</li>
 *   <li>死蝶の呪いのゲージ（HP の下の細いゲージ）: 自分の最大 HP がいま何 % 削られているか（下限の 60% まで）を桃色の帯で。下に呪いの文字</li>
 *   <li>第 2 形態（光体）: 縁の光が桃色と水色で移ろい、HP の帯が桃色・水色の光の帯になって光がきらめき、角金具と 50% の印が桃色</li>
 *   <li>死蝶終葬の間: ボスバーの名が技の名に変わる（サーバーが変える）ので、その名でも見分け、縁が白く脈打つ</li>
 *   <li>撃破: 閃光の中でひびが走り（0.8 秒）、板・ゲージ・横顔の枠が細かい光の欠片になって、ひらひら揺れながら空へ舞い上がって消える（〜1.6 秒。光の蝶になって散る撃破に合わせて）</li>
 * </ul>
 * サーバーのボスバーはそのままで、名前の翻訳キーで見分ける
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class MementoBossBar {

    private static final String KEY = "entity.automata_valkyries.memento_mori", SPELL_KEY = "message.automata_valkyries.memento_mori.spell";
    /** HP のゲージの幅・高さ、呪いのゲージの高さ、横顔の枠の大きさ、ボスバー 1 本の高さ */
    private static final int W = 182, H = 5, H2 = 3, FACE = 36, INCREMENT = 50;
    private static final int WHITE = 0xFFF8F0FF, SILVER = 0xFFC0B8D8, SILVER_DARK = 0xFF4A4460, LILAC = 0xFFE0C8FF, VIOLET = 0xFF7A3AD8;
    private static final int PLATE = 0xC010081C, PLATE_DEEP = 0xD8060310, PURPLE = 0xFFA45CFF, PINK = 0xFFFF8CEB, CYAN = 0xFF8FE6FF, PALE = 0xFFE8D0FF;
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
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private MementoBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc)) return;
        boolean spell = SPELL_KEY.equals(tc.getKey());
        if (!KEY.equals(tc.getKey()) && !spell) return;
        event.setCanceled(true);
        event.setIncrement(INCREMENT);
        GuiGraphics g = event.getGuiGraphics();
        if (STATES.size() > 16) STATES.clear();
        State st = STATES.computeIfAbsent(boss.getId(), k -> new State());
        st.cx = g.guiWidth() / 2;
        st.y = event.getY();
        st.seen = Util.getMillis();
        draw(g, st, boss, spell);
    }

    /** ボスバーが消えた後（撃破で消える）も、舞い上がる欠片を最後まで描く */
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

    private static int curseY(int y0) {
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
        return curseY(y0) + H2 + 20;
    }

    private static int faceCx(int x0) {
        return plateLeft(x0) - FACE / 2 + 6;
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss, boolean spell) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        boolean p2 = boss.getColor() == BossEvent.BossBarColor.PINK || spell; // サーバーは第 2 形態でボスバーを桃色にする
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
        int edge = edgeColor(p2, spell, now);
        drawPlate(g, x0, y0, p2, edge, now);
        drawGauge(g, st, x0, y0, p, p2, now);
        drawCurse(g, x0, curseY(y0), now);
        drawTexts(g, boss, x0, y0, p, p2, spell);
        drawFace(g, faceCx(x0), y0 + (H + 2 + H2) / 2, p2, edge, now);
        g.pose().popPose();
        if (since >= 0) drawCracks(g, st, since / (float) CRACK_MS, now);
    }

    /** 縁の光の色: 第 1 形態は紫、第 2 形態は桃色と水色で移ろい、死蝶終葬の間は白く脈打つ */
    private static int edgeColor(boolean p2, boolean spell, long now) {
        if (spell) return lerpColor(PINK, 0xFFFFFFFF, 0.5F + 0.5F * Mth.sin(now / 120.0F));
        if (p2) return lerpColor(PINK, CYAN, 0.5F + 0.5F * Mth.sin(now / 400.0F));
        return PURPLE;
    }

    /** 舗装: 角を斜めに落とした半透明の深い紫と黒の板、光る細い縁、走査線、上の縁を走る光、板のまわりを漂うモザイクの光の四角 */
    private static void drawPlate(GuiGraphics g, int x0, int y0, boolean p2, int edge, long now) {
        int l = plateLeft(x0), r = plateRight(x0), t = plateTop(y0), b = plateBottom(y0), ch = 6;
        chamferFill(g, l, t, r, b, ch, PLATE);
        chamferFill(g, l + 3, y0 - 3, r - 3, curseY(y0) + H2 + 3, 2, PLATE_DEEP); // ゲージの下の黒い帯
        for (int yy = t + 1; yy < b - 1; yy += 2) g.fill(l + 1, yy, r - 1, yy + 1, 0x0CA45CFF); // 走査線
        chamferOutline(g, l, t, r, b, ch, edge);
        chamferOutline(g, l + 2, t + 2, r - 2, b - 2, ch - 1, (0x50 << 24) | (edge & 0xFFFFFF));
        int sweep = (int) ((now / 12) % (r - l + 40)) + l - 20;
        g.fill(Math.max(l + ch, sweep), t, Math.min(r, sweep + 18), t + 1, 0xC0FFFFFF);
        g.fill(l + ch + 4, b - 2, l + ch + 40, b - 1, SILVER);
        g.fill(r - 46, t + 1, r - ch - 4, t + 2, SILVER);
        // 右端の小さな翅の飾り（光る線の三角を 2 枚）
        int wx = r - 9, wy = t + 8;
        line(g, wx, wy, wx + 5, wy - 4, edge);
        line(g, wx + 5, wy - 4, wx + 6, wy + 1, edge);
        line(g, wx, wy, wx + 4, wy + 5, (0xA0 << 24) | (edge & 0xFFFFFF));
        // 漂うモザイクの光の四角（板の上下の縁のあたりを右へ流れて明滅する）
        for (int i = 0; i < 14; i++) {
            long seed = i * 7919L;
            int span = r - l;
            int mx = l + (int) ((seed * 13 + now / (18 + i % 5)) % span);
            int my = (i % 2 == 0 ? t - 2 : b - 1) + (int) (Math.sin(now / 300.0 + i) * 2);
            int a = (int) (90 + 90 * Math.sin(now / 180.0 + i * 1.7));
            int c = p2 ? (i % 3 == 0 ? PINK : i % 3 == 1 ? CYAN : PALE) : (i % 2 == 0 ? PURPLE : PALE);
            int s = 1 + i % 2;
            if (a > 8) g.fill(mx, my, mx + s, my + s, (a << 24) | (c & 0xFFFFFF));
        }
    }

    /** HP のゲージ: 黒い溝・削れた分の淡い紫・淡い紫から紫への帯（第 2 形態は桃色と水色の光の帯にきらめき）・10% の目盛り・50% の印 */
    private static void drawGauge(GuiGraphics g, State st, int x0, int y0, float p, boolean p2, long now) {
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, SILVER_DARK);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0000000);
        int trw = (int) (W * st.trail);
        if (trw > 0) g.fill(x0, y0, x0 + trw, y0 + H, 0x90E0C8FF);
        int fw = (int) (W * p);
        if (fw > 0) {
            if (p2) { // 光体: 横へ桃色と水色が流れる帯
                for (int x = 0; x < fw; x++) {
                    float k = 0.5F + 0.5F * Mth.sin((x + now / 20.0F) * 0.06F);
                    g.fill(x0 + x, y0, x0 + x + 1, y0 + H, lerpColor(PINK, CYAN, k));
                }
                for (int k = 0; k < 4; k++) { // きらめき
                    int px = x0 + (int) (((now / 5 + k * 53) % (W + 8)) - 4);
                    if (px >= x0 && px < x0 + fw) g.fill(px, y0, px + 2, y0 + H, 0xD0FFFFFF);
                }
            } else {
                g.fillGradient(x0, y0, x0 + fw, y0 + H, LILAC, VIOLET);
            }
            g.fill(x0, y0, x0 + fw, y0 + 1, 0xFFFFFFFF);
            g.fill(x0 + Math.max(0, fw - 2), y0, x0 + fw, y0 + H, 0xFFF4E8FF);
        }
        for (int i = 1; i < 10; i++) g.fill(x0 + W * i / 10, y0 + 1, x0 + W * i / 10 + 1, y0 + H - 1, 0x50000000);
        int half = p2 ? PINK : SILVER;
        g.fill(x0 + W / 2, y0 - 3, x0 + W / 2 + 1, y0, half); // 50%（第 2 形態に入る所）
        g.fill(x0 + W / 2, y0 + H, x0 + W / 2 + 1, y0 + H + 2, half);
        int spell = (int) (W * 0.15F);                     // 15%（死蝶終葬を使う所）
        g.fill(x0 + spell, y0 + H, x0 + spell + 1, y0 + H + 2, 0xFFFFFFFF);
    }

    /** 自分の最大 HP の削られ具合（死蝶の呪い） */
    private static double curseCut() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0.0 : MementoCurse.stacks(mc.player) * MementoCurse.STEP;
    }

    /** 死蝶の呪いのゲージ: 削られた分（下限まで）を桃色の帯で。受けていなければ空の溝だけ */
    private static void drawCurse(GuiGraphics g, int x0, int y, long now) {
        double cut = curseCut(), most = 1.0 - MementoCurse.FLOOR;
        g.fill(x0 - 1, y - 1, x0 + W + 1, y + H2 + 1, SILVER_DARK);
        g.fill(x0, y, x0 + W, y + H2, 0xF0000000);
        int fw = (int) (W * Math.min(1.0, cut / most));
        if (fw > 0) {
            boolean full = cut >= most - 1.0e-6;
            int a = full ? (int) (160 + 95 * (0.5F + 0.5F * Mth.sin(now / 100.0F))) : 255;
            g.fillGradient(x0, y, x0 + fw, y + H2, (a << 24) | 0xFFC8F0, (a << 24) | (PINK & 0xFFFFFF));
            g.fill(x0, y, x0 + fw, y + 1, 0x90FFFFFF);
        }
    }

    /** 名前（白に紫の影）と、下の段（格・形態 / HP）、その下に呪いの文字 */
    private static void drawTexts(GuiGraphics g, LerpingBossEvent boss, int x0, int y0, float p, boolean p2, boolean spell) {
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        int nx = x0 + W / 2 - font.width(name) / 2, ny = y0 - 11;
        g.drawString(font, name, nx + 1, ny + 1, p2 ? 0xFF5A1450 : 0xFF2A1050, false);
        g.drawString(font, name, nx, ny, spell ? 0xFFFFE8FA : WHITE, false);
        g.pose().pushPose();
        g.pose().translate(x0, curseY(y0) + H2 + 3, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable(p2 ? "bossbar.automata_valkyries.memento_mori.phase2" : "bossbar.automata_valkyries.memento_mori.rank"), 0, 0,
                p2 ? 0xFFFFB0F0 : 0xFFC8A8FF, false);
        String hp = Math.round(p * MementoMoriEntity.MAX_HP) + " / " + Math.round(MementoMoriEntity.MAX_HP);
        g.drawString(font, hp, (int) (W / 0.75F) - font.width(hp), 0, 0xFFEADCFF, false);
        int cut = (int) Math.round(curseCut() * 100);
        Component curse = cut > 0 ? Component.translatable("bossbar.automata_valkyries.memento_mori.curse", cut)
                : Component.translatable("bossbar.automata_valkyries.memento_mori.curse_none");
        g.drawString(font, curse, 0, 11, cut > 0 ? 0xFFFF9CEB : 0xFFB8A8D0, false);
        g.pose().popPose();
    }

    /** 横顔用の、ワールドに置かない動かないメメントモリ（同じワールドの間は使い回す） */
    private static MementoMoriEntity portraitModel;

    private static MementoMoriEntity portraitModel(boolean p2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (portraitModel == null || portraitModel.level() != mc.level) {
            portraitModel = new MementoMoriEntity(com.automatavalkyries.registry.ModEntities.MEMENTO_MORI.get(), mc.level);
        }
        portraitModel.portraitState(p2);
        portraitModel.yBodyRot = portraitModel.yBodyRotO = portraitModel.yHeadRot = portraitModel.yHeadRotO = 270.0F;
        portraitModel.setYRot(270.0F);
        portraitModel.setXRot(0.0F);
        portraitModel.xRotO = 0.0F;
        return portraitModel;
    }

    /** 横顔の枠: 角を落とした暗い地に、光の輪（2 重）・銀の角金具（第 2 形態は桃色）・ゆっくり下りる走査の光。右を向いた頭と胸を描く */
    private static void drawFace(GuiGraphics g, int cx, int cy, boolean p2, int ring, long now) {
        int x = cx - FACE / 2, y = cy - FACE / 2, ch = 6;
        chamferFill(g, x - 3, y - 3, x + FACE + 3, y + FACE + 3, ch + 2, 0xE008040E);
        chamferOutline(g, x - 3, y - 3, x + FACE + 3, y + FACE + 3, ch + 2, ring);
        chamferOutline(g, x - 1, y - 1, x + FACE + 1, y + FACE + 1, ch, (0x80 << 24) | (ring & 0xFFFFFF));
        g.fillGradient(x, y, x + FACE, y + FACE, p2 ? 0xD0402050 : 0xD0281444, 0xD0080410);
        MementoMoriEntity mori = portraitModel(p2);
        if (mori != null) {
            g.enableScissor(x, y, x + FACE, y + FACE);
            // 頭と胸（足元から約 4.6 ブロックの高さを真ん中に）
            int scale = 6;
            float centerH = 4.6F;
            InventoryScreen.renderEntityInInventory(g, cx, cy + (int) (centerH * scale), scale, new Quaternionf().rotateZ((float) Math.PI), null, mori);
            g.disableScissor();
        } else {
            diamond(g, cx, cy, 9, PURPLE);
            diamond(g, cx, cy, 6, WHITE);
            diamond(g, cx, cy, 2, PURPLE);
        }
        int scan = y + (int) ((now / 30) % FACE);
        g.fill(x, scan, x + FACE, scan + 1, 0x40A45CFF);
        int corner = p2 ? PINK : SILVER;
        bracket(g, x - 4, y - 4, 1, 1, corner);
        bracket(g, x + FACE + 3, y - 4, -1, 1, corner);
        bracket(g, x - 4, y + FACE + 3, 1, -1, corner);
        bracket(g, x + FACE + 3, y + FACE + 3, -1, -1, corner);
    }

    private static void bracket(GuiGraphics g, int x, int y, int dx, int dy, int color) {
        g.fill(Math.min(x, x + dx * 5), y, Math.max(x, x + dx * 5) + 1, y + 1, color);
        g.fill(x, Math.min(y, y + dy * 5), x + 1, Math.max(y, y + dy * 5) + 1, color);
    }

    // ---- 撃破の演出（ひびが走り、光の欠片になって舞い上がる） ----

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
        // 欠片: 位置・大きさ・横の速さ・上への速さ・揺れの位相・種類（0 = 板、1 = ゲージ、2 = 呪いのゲージ、3 = 横顔の枠）
        st.shards = new ArrayList<>();
        int cols = 30, rows = 5;
        float cw = (rt - l) / (float) cols, chh = (b - t) / (float) rows;
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                float sx = l + cw * (c + 0.5F) + (r.nextFloat() - 0.5F) * 2, sy = t + chh * (rr + 0.5F) + (r.nextFloat() - 0.5F) * 2;
                int kind = sy >= y0 - 2 && sy <= y0 + H + 2 ? 1 : sy >= curseY(y0) - 1 && sy <= curseY(y0) + H2 + 1 ? 2 : 0;
                st.shards.add(new float[]{sx, sy, cw * (0.4F + r.nextFloat() * 0.5F), chh * (0.4F + r.nextFloat() * 0.5F),
                        (r.nextFloat() - 0.5F) * 0.03F, 0.02F + r.nextFloat() * 0.05F, r.nextFloat() * 6.28F, kind});
            }
        }
        int fx = faceCx(x0) - FACE / 2, fy = y0 + (H + 2 + H2) / 2 - FACE / 2;
        float fs = (FACE + 6) / 5F;
        for (int c = 0; c < 5; c++) {
            for (int rr = 0; rr < 5; rr++) {
                st.shards.add(new float[]{fx - 3 + fs * (c + 0.5F), fy - 3 + fs * (rr + 0.5F), fs * (0.6F + r.nextFloat() * 0.4F), fs * (0.6F + r.nextFloat() * 0.4F),
                        (r.nextFloat() - 0.5F) * 0.03F, 0.03F + r.nextFloat() * 0.05F, r.nextFloat() * 6.28F, 3});
            }
        }
    }

    private static void drawCracks(GuiGraphics g, State st, float k, long now) {
        if (st.cracks == null) return;
        int x0 = gaugeX(st), y0 = gaugeY(st);
        if (k < 0.15F) {
            int a = (int) ((1 - k / 0.15F) * 160);
            g.fill(faceCx(x0) - FACE / 2 - 4, plateTop(y0) - 4, plateRight(x0) + 2, plateBottom(y0) + 4, (a << 24) | 0xFFFFFF);
        }
        int shown = (int) (k * 9.999F);
        int glow = st.p2 ? 0x90FF8CEB : 0x90E0C8FF;
        for (int[] c : st.cracks) {
            if (c[4] > shown) continue;
            line(g, c[0], c[1] + 1, c[2], c[3] + 1, glow);
            line(g, c[0], c[1], c[2], c[3], 0xFFFFFFFF);
        }
    }

    /** 光の欠片: ひらひらと左右に揺れながら上へ舞い、だんだん小さく透けて消える */
    private static void drawShards(GuiGraphics g, State st, long t) {
        if (st.shards == null) return;
        float life = Math.max(0.0F, 1.0F - t / (float) BREAK_MS);
        int a = (int) (life * 255);
        if (a <= 0) return;
        for (float[] s : st.shards) {
            float x = s[0] + s[4] * t + Mth.sin(t * 0.008F + s[6]) * 4.0F;
            float y = s[1] - s[5] * t - 0.00002F * t * t;
            int kind = (int) s[7];
            int c = switch (kind) {
                case 1 -> st.p2 ? (s[6] > 3.14F ? PINK : CYAN) : LILAC;
                case 2 -> PINK;
                case 3 -> PALE;
                default -> st.p2 ? (s[6] > 4.2F ? PINK : s[6] > 2.1F ? CYAN : PURPLE) : PURPLE;
            };
            float size = life * 0.7F + 0.3F;
            int hw = Math.max(1, (int) (s[2] / 2 * size)), hh = Math.max(1, (int) (s[3] / 2 * size));
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotation(Mth.sin(t * 0.004F + s[6]) * 0.6F));
            g.fill(-hw, -hh, hw, hh, ((int) (a * 0.5F) << 24) | (c & 0xFFFFFF));
            if (hw > 1 && hh > 1) g.fill(-hw + 1, -hh + 1, hw - 1, hh - 1, (a << 24) | 0xFFFFFF);
            g.pose().popPose();
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
