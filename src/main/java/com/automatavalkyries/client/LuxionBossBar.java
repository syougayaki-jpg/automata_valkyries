package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.LuxionEntity;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * ルクシオン専用のボスバー（2026-10-08 ユーザー指定）。ゲージは控えめにして、左にルクシオンの横顔（実物のモデルを横から描く）、
 * まわりを白と金の板で舗装する。HP が 0 になると、撃破の演出（膝をつく → 光に崩れる、2 秒）に合わせて、ゲージと板にひびが走り（0.8 秒）、
 * 砕けて破片が落ちて消える（〜2 秒。ボスバーが消えた後も破片は最後まで描く）。
 * サーバーのボスバーはそのままで、名前の翻訳キーで見分ける
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class LuxionBossBar {

    private static final String KEY = "entity.automata_valkyries.luxion";
    /** ゲージの幅・高さ、横顔の枠の大きさ */
    private static final int W = 182, H = 5, FACE = 28;
    private static final int WHITE = 0xFFF7F4EC, IVORY = 0xFFE9E2D0, GOLD = 0xFFC9A24A, GOLD_LIGHT = 0xFFF0D890, GOLD_DARK = 0xFF7A5C24, SLOT = 0xFF2B2732;
    private static final long CRACK_MS = 800, BREAK_MS = 1200;

    /** ボスバーごとの状態（削れた分の残り・砕ける演出） */
    private static final class State {
        double trail = 1.0;
        long last = Util.getMillis(), seen;
        long broken; // 砕け始めた時刻（0 = まだ）
        int cx, y;
        List<int[]> cracks; // ひびの線（x1, y1, x2, y2, 何番目の線か）
        List<float[]> shards; // 破片（中心 x, y, 幅, 高さ, 速さ x, y, 回る速さ, 色）
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private LuxionBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(36);
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
            if (now - st.seen < 100) continue; // まだボスバーがある（上で描いた）
            if (st.broken == 0 || now - st.broken > CRACK_MS + BREAK_MS) {
                it.remove();
                continue;
            }
            if (now - st.broken > CRACK_MS) drawShards(event.getGuiGraphics(), st, now - st.broken - CRACK_MS);
        }
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        boolean p2 = boss.getColor() == BossEvent.BossBarColor.RED; // サーバーは第 2 形態でボスバーを赤にする
        double dt = Math.min(0.2, (now - st.last) / 1000.0);
        st.last = now;
        st.trail = p >= st.trail ? p : Math.max(p, st.trail - 0.25 * dt);
        if (p > 0.0F) {
            st.broken = 0;
        } else if (st.broken == 0) { // HP 0: 砕け始める
            st.broken = now;
            prepareBreak(st, boss.getId());
        }
        long since = st.broken == 0 ? -1 : now - st.broken;
        if (since > CRACK_MS) {
            drawShards(g, st, since - CRACK_MS);
            return;
        }
        int x0 = st.cx - W / 2 + 14, y0 = st.y + 2;
        drawPlate(g, x0, y0);
        // ゲージ（控えめ）: 暗い溝・削れた分の白い残り・白から淡い金への帯（第 2 形態は淡い紅）
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, GOLD_DARK);
        g.fill(x0, y0, x0 + W, y0 + H, SLOT);
        int trw = (int) (W * st.trail);
        if (trw > 0) g.fill(x0, y0, x0 + trw, y0 + H, 0x80FFFFFF);
        int fw = (int) (W * p);
        if (fw > 0) {
            g.fillGradient(x0, y0, x0 + fw, y0 + H, p2 ? 0xFFFFE6EA : 0xFFFFFFFF, p2 ? 0xFFD04A5C : 0xFFE6CC86);
            g.fill(x0, y0, x0 + fw, y0 + 1, 0x60FFFFFF);
        }
        g.fill(x0 + W / 2, y0 - 3, x0 + W / 2 + 1, y0, GOLD); // 50%（第 2 形態に入る所）
        g.fill(x0 + W / 2, y0 + H, x0 + W / 2 + 1, y0 + H + 3, GOLD);
        // 名前と、下の段（格・形態 / HP）
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        g.drawString(font, name, x0 + W / 2 - font.width(name) / 2, y0 - 11, p2 ? 0xFF9A2034 : 0xFF6B5222, false);
        g.pose().pushPose();
        g.pose().translate(x0, y0 + H + 3, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable(p2 ? "bossbar.automata_valkyries.luxion.phase2" : "bossbar.automata_valkyries.luxion.rank"), 0, 0, p2 ? 0xFFB03A4E : 0xFF8A7040, false);
        String hp = Math.round(p * LuxionEntity.MAX_HP) + " / " + Math.round(LuxionEntity.MAX_HP);
        g.drawString(font, hp, (int) (W / 0.75F) - font.width(hp), 0, 0xFF8A7040, false);
        g.pose().popPose();
        drawFace(g, x0 - FACE / 2 - 10, y0 + H / 2, p2);
        if (since >= 0) drawCracks(g, st, since / (float) CRACK_MS);
    }

    /** 白と金の板（ゲージのまわりの舗装）: 白い板に金の縁と内側の細い金線、右端に金の菱形 */
    private static void drawPlate(GuiGraphics g, int x0, int y0) {
        int left = x0 - 10, right = x0 + W + 8, top = y0 - 13, bottom = y0 + H + 11;
        g.fill(left, top, right, bottom, 0xF0F7F4EC);
        g.fill(left, top, right, top + 1, GOLD);
        g.fill(left, bottom - 1, right, bottom, GOLD);
        g.fill(left, top + 2, right - 2, top + 3, 0x70C9A24A);
        g.fill(left, bottom - 3, right - 2, bottom - 2, 0x70C9A24A);
        g.fill(right - 1, top, right, bottom, GOLD);
        diamond(g, right, (top + bottom) / 2, 5, GOLD);
        diamond(g, right, (top + bottom) / 2, 3, WHITE);
        diamond(g, right, (top + bottom) / 2, 1, GOLD);
    }

    /** 横顔用の、ワールドに置かない動かないルクシオン（同じワールドの間は使い回す） */
    private static LuxionEntity portraitModel;

    private static LuxionEntity portraitModel(boolean p2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (portraitModel == null || portraitModel.level() != mc.level) {
            portraitModel = new LuxionEntity(com.automatavalkyries.registry.ModEntities.LUXION.get(), mc.level);
        }
        // いつも同じ姿（構えず・歩かず・頭はまっすぐ）。第 2 形態は背の機構が豪華な姿
        portraitModel.glory = portraitModel.gloryO = p2 ? 1.0F : 0.0F;
        portraitModel.deploy = portraitModel.deployO = 0.0F;
        portraitModel.yBodyRot = portraitModel.yBodyRotO = portraitModel.yHeadRot = portraitModel.yHeadRotO = 270.0F;
        portraitModel.setYRot(270.0F);
        portraitModel.setXRot(0.0F);
        portraitModel.xRotO = 0.0F;
        return portraitModel;
    }

    /** 横顔の枠: 金の縁の白い枠に、ルクシオンを右（ゲージの方）を向いた横顔で、頭だけ見えるように描く（いつも同じ姿で固定）。四隅に金の鋲 */
    private static void drawFace(GuiGraphics g, int cx, int cy, boolean p2) {
        int x = cx - FACE / 2, y = cy - FACE / 2;
        g.fill(x - 2, y - 2, x + FACE + 2, y + FACE + 2, GOLD);
        g.fill(x - 1, y - 1, x + FACE + 1, y + FACE + 1, GOLD_LIGHT);
        g.fillGradient(x, y, x + FACE, y + FACE, WHITE, p2 ? 0xFFF0D0D4 : IVORY);
        LuxionEntity lux = portraitModel(p2);
        if (lux != null) {
            g.enableScissor(x, y, x + FACE, y + FACE);
            try {
                LuxionRenderer.portrait = true;
                // 頭（足から約 4.6 ブロック上）が枠の真ん中に来るように
                int scale = 18;
                InventoryScreen.renderEntityInInventory(g, cx, cy + (int) (4.55F * scale), scale, new Quaternionf().rotateZ((float) Math.PI), null, lux);
            } finally {
                LuxionRenderer.portrait = false;
            }
            g.disableScissor();
        } else { // ワールドが無いときは紋章（金の菱形）
            diamond(g, cx, cy, 8, GOLD);
            diamond(g, cx, cy, 5, WHITE);
            diamond(g, cx, cy, 2, GOLD);
        }
        diamond(g, x - 1, y - 1, 2, GOLD_DARK);
        diamond(g, x + FACE, y - 1, 2, GOLD_DARK);
        diamond(g, x - 1, y + FACE, 2, GOLD_DARK);
        diamond(g, x + FACE, y + FACE, 2, GOLD_DARK);
    }

    // ---- 砕ける演出 ----

    /** ひびの線と破片を、ボスバーごとに決まった形で用意する */
    private static void prepareBreak(State st, UUID id) {
        RandomSource r = RandomSource.create(id.getLeastSignificantBits());
        int x0 = st.cx - W / 2 + 14, y0 = st.y + 2;
        st.cracks = new ArrayList<>();
        // ゲージの右寄り（最後に削れた所）から、板と横顔へ枝分かれして走る
        int ix = x0 + W / 3 + r.nextInt(W / 3), iy = y0 + H / 2;
        for (int b = 0; b < 7; b++) {
            double ang = b * (Math.PI * 2 / 7) + r.nextDouble() * 0.6;
            double x = ix, y = iy;
            for (int s = 0; s < 7; s++) {
                ang += (r.nextDouble() - 0.5) * 0.9;
                double len = 5 + r.nextDouble() * 9;
                double nx = x + Math.cos(ang) * len * 2.2, ny = y + Math.sin(ang) * len * 0.55; // 横長の板に沿って伸びる
                st.cracks.add(new int[]{(int) x, (int) y, (int) nx, (int) ny, s});
                x = nx;
                y = ny;
            }
        }
        // 破片: 板を横 9 × 縦 2 に、横顔の枠を 1 つ
        st.shards = new ArrayList<>();
        int left = x0 - 10, right = x0 + W + 8, top = y0 - 13, bottom = y0 + H + 11;
        int cols = 9, rows = 2;
        float cw = (right - left) / (float) cols, ch = (bottom - top) / (float) rows;
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                float sx = left + cw * (c + 0.5F), sy = top + ch * (rr + 0.5F);
                float vx = (sx - ix) * 0.004F + (r.nextFloat() - 0.5F) * 0.06F, vy = -0.05F - r.nextFloat() * 0.08F;
                st.shards.add(new float[]{sx, sy, cw * (0.8F + r.nextFloat() * 0.3F), ch * (0.8F + r.nextFloat() * 0.3F), vx, vy, (r.nextFloat() - 0.5F) * 0.012F, rr == 0 ? 0 : 1});
            }
        }
        st.shards.add(new float[]{x0 - FACE / 2F - 10, y0 + H / 2F, FACE + 4, FACE + 4, -0.03F, -0.06F, -0.004F, 2});
    }

    /** ひび（0〜1 で伸びる）。最初に白く光る */
    private static void drawCracks(GuiGraphics g, State st, float k) {
        if (st.cracks == null) return;
        if (k < 0.15F) {
            int a = (int) ((1 - k / 0.15F) * 140);
            g.fill(st.cx - W / 2 - 4, st.y - 11, st.cx + W / 2 + 22, st.y + H + 13, (a << 24) | 0xFFFFFF);
        }
        int shown = (int) (k * 7.999F);
        for (int[] c : st.cracks) {
            if (c[4] > shown) continue;
            line(g, c[0], c[1] + 1, c[2], c[3] + 1, 0x70FFFFFF); // 光る縁
            line(g, c[0], c[1], c[2], c[3], 0xFF3A2E1C);
        }
    }

    /** 破片が飛び散って落ち、回りながら薄れて消える（t = 砕けてからのミリ秒） */
    private static void drawShards(GuiGraphics g, State st, long t) {
        if (st.shards == null) return;
        float alpha = Math.max(0.0F, 1.0F - t / (float) BREAK_MS);
        for (float[] s : st.shards) {
            float x = s[0] + s[4] * t, y = s[1] + s[5] * t + 0.00012F * t * t;
            float rot = s[6] * t;
            int a = (int) (alpha * 255);
            if (a <= 0) continue;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotation(rot));
            int hw = (int) (s[2] / 2), hh = (int) (s[3] / 2);
            int face = s[7] == 2 ? GOLD_LIGHT : WHITE;
            g.fill(-hw, -hh, hw, hh, (a << 24) | (GOLD & 0xFFFFFF));
            g.fill(-hw + 1, -hh + 1, hw - 1, hh - 1, (a << 24) | (face & 0xFFFFFF));
            if (s[7] == 1) g.fill(-hw + 1, -hh + 1, hw - 1, -hh + 3, (a << 24) | (SLOT & 0xFFFFFF)); // ゲージの溝のかけら
            g.pose().popPose();
            // 光の粒（崩れる光）
            if (t % 3 == 0) g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, (a << 24) | 0xFFF0B8);
        }
    }

    /** (x1, y1) から (x2, y2) への 1 ピクセルの線 */
    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x1 : x1 + (x2 - x1) * i / n, y = n == 0 ? y1 : y1 + (y2 - y1) * i / n;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** (x, y) を中心に、半径 r の菱形 */
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
