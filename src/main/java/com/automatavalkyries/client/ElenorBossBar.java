package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntBinaryOperator;

/**
 * エレノアのボスバー（2026-10-22 ユーザー指定「黒とピンクのリボンと帯で飾り付け」、参考画像つき。「解像度をもう少し高く・リボンを揺らして」）。
 * 作りは {@link MementoBossBar} と同じで、サーバーのボスバーはそのまま、名前の翻訳キーで見分けて描き直す。
 * <ul>
 *   <li>解像度: 位置は GUI の座標（小数）で考え、{@link #R} 倍の細かさの点に直して描く（描画を 1/R に縮めるので、曲線・模様・斜めの縁が滑らか）</li>
 *   <li>バー: 黒い枠に、洋紅から紫へ移る光る帯（上に明るい筋）。まわりに紫の光と、枠の縁にドット絵のようなノイズ</li>
 *   <li>左: 黒とピンクの市松模様の大きなリボン（レースの縁）と、長い市松の垂れ・ピンクのサテンの垂れ。右: ピンクのサテンのリボンと、鋲を打った黒い垂れ</li>
 *   <li>下: ピンクのフリルを付けた黒い帯が弧を描いて垂れ（ゆるく波打つ）、真ん中に黒い蝶結びと垂れ。バーに巻きつく黒いリボン、散るリボンの切れ端</li>
 *   <li>揺れ: 垂れは付け根から先へ向かって波が伝わるように揺れ、蝶結びの輪はゆっくり羽ばたく</li>
 *   <li>フェーズ: 第 2（ボスバーがピンク）で光が強まり、第 3（赤）で赤みを帯びて脈打ち、揺れが速くなる</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ElenorBossBar {

    private static final String KEY = "entity.automata_valkyries.elenor";
    /** 描く細かさ（GUI の 1 点を R × R の点に分ける） */
    private static final int R = 2;
    private static final int W = 182, H = 7, INCREMENT = 72;
    private static final int BLACK = 0xFF0C070D, SATIN = 0xFF15101A, PINK = 0xFFFF3FB4, HOT = 0xFFFF1F8E, LIGHT = 0xFFFFA6DC, DEEP = 0xFF9A1068, FRAME = 0xFF241A28, FRAME_HI = 0xFF4A3A52;
    private static final int LACE = 0xFFE8D0E0;
    /** 市松の 1 マスの大きさ（細かい点で） */
    private static final int CELL = 3 * R;

    private static final class State {
        double trail = 1.0;
        long last = Util.getMillis();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ElenorBossBar() {}

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(INCREMENT);
        if (STATES.size() > 16) STATES.clear();
        State st = STATES.computeIfAbsent(boss.getId(), k -> new State());
        draw(event.getGuiGraphics(), st, boss, event.getGuiGraphics().guiWidth() / 2, event.getY());
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss, int cx, int y) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        int phase = boss.getColor() == BossEvent.BossBarColor.RED ? 3 : boss.getColor() == BossEvent.BossBarColor.PINK ? 2 : 1;
        double dt = Math.min(0.2, (now - st.last) / 1000.0);
        st.last = now;
        st.trail = p >= st.trail ? p : Math.max(p, st.trail - 0.25 * dt);

        int x0 = cx - W / 2, y0 = y + 17, yb = y0 + H;
        float pulse = 0.5F + 0.5F * Mth.sin(now / (phase >= 3 ? 140.0F : 500.0F));
        double speed = phase >= 3 ? 1.7 : phase == 2 ? 1.25 : 1.0; // 揺れの速さ

        g.pose().pushPose();
        g.pose().scale(1.0F / R, 1.0F / R, 1.0F);

        // 紫の光（バーの後ろ）
        int glow = phase >= 3 ? (int) (0x30 + 0x28 * pulse) : phase == 2 ? 0x40 : 0x30;
        int glowColor = phase >= 3 ? 0xFF2060 : 0xB030D0;
        gradient(g, x0 - 26, y0 - 12, x0 + W + 26, y0 + H / 2.0, glowColor, (glow << 24) | glowColor);
        gradient(g, x0 - 26, y0 + H / 2.0, x0 + W + 26, yb + 14, (glow << 24) | glowColor, glowColor);

        // 枠の上のドット絵のようなノイズ（小さな尖り）
        for (int i = 0; i < 16; i++) {
            long seed = i * 131L + (now / 400) * (i % 3 == 0 ? 1 : 0);
            int nx = x0 + 18 + (int) ((seed * 37) % (W - 36));
            int nh = 2 + (int) (seed % 4);
            fill(g, nx, y0 - 1 - nh, nx + 2, y0 - 1, i % 2 == 0 ? 0xA0FF3FB4 : 0xA0603080);
        }

        // バーの枠と帯
        fill(g, x0 - 3, y0 - 3, x0 + W + 3, yb + 3, FRAME);
        fill(g, x0 - 3, y0 - 3, x0 + W + 3, y0 - 2, FRAME_HI);
        fill(g, x0 - 2, y0 - 2, x0 + W + 2, yb + 2, BLACK);
        fill(g, x0, y0, x0 + W, yb, 0xFF14081C);
        double tw = W * st.trail;
        if (tw > 0) fill(g, x0, y0, x0 + tw, yb, 0x90FFB0E8);
        int fw = h(W * p);
        for (int x = 0; x < fw; x += 4) { // 4 点ごとに色を変える（なめらかな帯。描く回数を抑える）
            float u = x / (float) (W * R);
            int col = phase >= 3 ? lerp(0xFFFF2D6A, 0xFFB01E90, u) : lerp(0xFFFF3CE0, 0xFF6A2AC0, u * u);
            g.fill(h(x0) + x, h(y0), h(x0) + Math.min(x + 4, fw), h(yb), col);
        }
        if (fw > 0) {
            double fx = W * p;
            fill(g, x0, y0 + 1, x0 + fx, y0 + 3, 0x70FFFFFF); // 上の明るい筋
            fill(g, x0, yb - 1, x0 + fx, yb, 0x50000000);
            double shine = x0 + ((now / 7) % (W + 30)) - 12;
            if (shine < x0 + fx) fill(g, Math.max(x0, shine), y0, Math.min(x0 + fx, shine + 4), yb, 0x60FFFFFF);
        }
        for (int i = 1; i < 10; i++) fill(g, x0 + W * i / 10.0, y0, x0 + W * i / 10.0 + 0.5, yb, 0x40000000);
        // フェーズの境目（60%・30%）: 小さなピンクの結び目
        miniKnot(g, x0 + W * 0.6, yb + 3, phase >= 2);
        miniKnot(g, x0 + W * 0.3, yb + 3, phase >= 3);

        // 巻きつく黒いリボン（2 か所）
        wrap(g, x0 + W * 0.2, y0, yb, -1);
        wrap(g, x0 + W * 0.84, y0, yb, 1);

        // 下へ弧を描いて垂れる黒い帯とピンクのフリル
        swag(g, x0 - 4, x0 + W + 4, yb + 2, 9, now, speed);

        double yc = y0 + H / 2.0;
        IntBinaryOperator checker = (px, py) -> (Math.floorDiv(px + py, CELL * 2) + Math.floorDiv(px - py, CELL * 2)) % 2 == 0 ? PINK : BLACK; // 斜めの格子（菱形）
        IntBinaryOperator satinPink = (px, py) -> Math.floorMod(py, 7 * R) < R ? LIGHT : PINK;
        IntBinaryOperator satinHot = (px, py) -> Math.floorMod(py, 9 * R) < R ? PINK : HOT;
        IntBinaryOperator black = (px, py) -> SATIN;

        // 左: 市松の大きなリボンと垂れ
        tail(g, x0 - 9, yc + 2, 40, 9, 2, 3.2, checker, true, now, speed, 0.0, false);
        tail(g, x0 - 2, yc + 2, 48, 7, -3, 3.6, satinPink, false, now, speed, 1.3, false);
        bow(g, x0 - 6, yc, 1.55F, checker, LIGHT, true, now, speed, 0.0);
        // 右: ピンクのサテンのリボンと、鋲を打った黒い垂れ
        tail(g, x0 + W + 8, yc + 2, 38, 8, -2, 3.0, black, false, now, speed, 2.1, true);
        tail(g, x0 + W + 2, yc + 2, 44, 6, 3, 3.4, satinHot, false, now, speed, 3.0, false);
        bow(g, x0 + W + 7, yc - 1, 1.3F, satinPink, LIGHT, false, now, speed, 1.0);

        // 真ん中: 黒い蝶結び（ピンクの結び目と垂れ、黒いレースの垂れ）
        double bx = cx, by = yb + 9;
        tail(g, bx - 3, by + 2, 28, 7, 3, 2.6, black, true, now, speed, 4.0, false);
        tail(g, bx + 3, by + 2, 28, 7, -3, 2.6, black, true, now, speed, 5.0, false);
        tail(g, bx - 2, by + 2, 24, 6, 2, 2.4, satinPink, false, now, speed, 4.6, false);
        tail(g, bx + 2, by + 2, 24, 6, -2, 2.4, satinPink, false, now, speed, 5.6, false);
        bow(g, bx, by, 1.05F, black, PINK, true, now, speed, 2.0);

        // 散るリボンの切れ端
        scraps(g, x0, W, yb + 12, now);

        g.pose().popPose();

        // 文字（元の細かさで）
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        int nx = cx - font.width(name) / 2, ny = y0 - 14;
        g.drawString(font, name, nx + 1, ny + 1, 0xFF4A0A3A, false);
        g.drawString(font, name, nx, ny, 0xFFFFF0F8, false);
        g.pose().pushPose();
        g.pose().translate(x0 + 30, yb + 12, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("bossbar.automata_valkyries.elenor.phase" + phase), 0, 0, phase >= 3 ? 0xFFFF7AB8 : 0xFFFFB6D8, false);
        String pct = Math.round(p * 100) + "%";
        g.drawString(font, pct, (int) ((W - 60) / 0.75F) - font.width(pct), 0, 0xFFFFD8EA, false);
        g.pose().popPose();
    }

    // ---- 飾り（位置は GUI の座標の小数。描くときに R 倍の細かさの点にする） ----

    /** バーに巻きつく黒いリボン（斜めの帯。円筒のような濃淡と光の筋があり、バーに影を落とす） */
    private static void wrap(GuiGraphics g, double x, double y0, double yb, int lean) {
        double top = y0 - 4, bottom = yb + 4, w = 9;
        // バーへ落ちる影
        poly(g, new double[]{x + 2, x + w + 2.5, x + w + 2.5 + lean * 4, x + 2 + lean * 4}, new double[]{top + 1.5, top + 1.5, bottom + 1.5, bottom + 1.5}, (px, py) -> 0x66000000);
        double[] xs = {x, x + w, x + w + lean * 4, x + lean * 4};
        double[] ys = {top, top, bottom, bottom};
        poly(g, xs, ys, (px, py) -> {
            double yy = py / (double) R, t = (yy - top) / (bottom - top);
            double cxx = x + w / 2.0 + lean * 4 * t;
            double u = (px / (double) R - cxx) / (w / 2.0);
            return shade(SATIN, 0.7f + 2.2f * (float) shading(u) + (Math.abs(u + 0.4) < 0.12 ? 1.1f : 0f));
        });
        line(g, x, top, x + lean * 4, bottom, DEEP);
        line(g, x + w, top, x + w + lean * 4, bottom, DEEP);
        fill(g, x + 1, top, x + 2.5, top + 1, PINK);
        fill(g, x + 0.5, bottom - 1, x + w - 0.5, bottom, 0xFF000000);
    }

    /** 弧を描いて垂れる黒い帯と、その下のピンクのフリル（ゆるく波打つ） */
    private static void swag(GuiGraphics g, double xl, double xr, double y, double sag, long now, double speed) {
        int cols = h(xr - xl);
        for (int i = 0; i <= cols; i += 2) {
            double x = xl + i / (double) R, u = (x - xl) / (xr - xl), d = 1.0 - Math.pow(2 * u - 1, 2);
            double yy = y + sag * d + Math.sin(x * 0.35 + now / (900.0 / speed)) * 0.5 * d;
            g.fill(h(x), h(yy), h(x) + 2, h(yy + 3), SATIN);
            g.fill(h(x), h(yy), h(x) + 2, h(yy + 0.5), 0xFF3A2A44);
            double frill = 1.6 + 1.4 * Math.sin(x * 0.9 + now / (700.0 / speed)) * d + 0.4;
            g.fill(h(x), h(yy + 3), h(x) + 2, h(yy + 3 + frill), Math.floorMod((int) Math.floor(x * 1.6), 2) == 0 ? PINK : HOT);
        }
    }

    /** 円筒の断面の明るさ（u は -1〜1。左上から光が当たる）: 0.0〜1.0 */
    private static double shading(double u) {
        u = Math.max(-1.0, Math.min(1.0, u));
        return Math.max(0.0, 0.55 * Math.sqrt(1.0 - u * u) + 0.45 * (0.5 - 0.5 * u) - 0.1);
    }

    /** 色の明るさを変える（1 未満は暗く、1 を超えると白へ寄る）。段を粗くして、塗りの区切りを増やしすぎない */
    private static int shade(int c, float f) {
        f = Math.round(f * 4.0F) / 4.0F;
        int r = c >> 16 & 0xFF, gr = c >> 8 & 0xFF, b = c & 0xFF;
        if (f <= 1.0F) return 0xFF000000 | (int) (r * f) << 16 | (int) (gr * f) << 8 | (int) (b * f);
        float k = Math.min(1.0F, f - 1.0F);
        return 0xFF000000 | (int) Mth.lerp(k, r, 255) << 16 | (int) Mth.lerp(k, gr, 255) << 8 | (int) Mth.lerp(k, b, 255);
    }

    /**
     * 蝶結び: 左右の輪（五角形）・輪の内側のくぼみ・折り目・縁の光・落ちる影・結び目。scale 1.0 で幅約 24。pattern は輪の模様（無地・市松など）。
     * 輪の外側の先がゆっくり羽ばたく。lace が true のときは輪の縁にレースの点を打つ
     */
    private static void bow(GuiGraphics g, double cx, double cy, float s, IntBinaryOperator pattern, int light, boolean lace, long now, double speed, double seed) {
        double w = 12 * s, h = 8 * s;
        double flap = Math.sin(now / (520.0 / speed) + seed) * 0.09 * h;
        double knotDrop = Math.sin(now / (520.0 / speed) + seed + 1.2) * 0.25;
        // 落ちる影（下へずらした同じ形）
        for (int side = -1; side <= 1; side += 2) {
            double[][] sh = lobe(cx + 0.8, cy + 1.7, w, h, side, flap * side, 1.0, 0.0);
            poly(g, sh[0], sh[1], (px, py) -> 0x55000000);
        }
        for (int side = -1; side <= 1; side += 2) {
            double lift = flap * side; // 左右で逆位相に羽ばたく
            double[][] outer = lobe(cx, cy, w, h, side, lift, 1.0, 0.0);
            final double top = cy - h * 0.95 + lift, span = 1.9 * h;
            // 輪の表: 上の縁が明るく下が暗い、結び目の近くはくぼんで暗い、上の縁の近くに光の筋
            poly(g, outer[0], outer[1], (px, py) -> {
                double xx = px / (double) R, yy = py / (double) R;
                double v = Math.max(0.0, Math.min(1.0, (yy - top) / span));
                double d = Math.abs(xx - cx) / w;
                float f = 1.2f - 0.6f * (float) v - (d < 0.3 ? 0.55f * (float) (0.3 - d) / 0.3f : 0f);
                if (Math.abs(v - 0.22) < 0.07 && d > 0.25) f += 0.5f;
                return shade(pattern.applyAsInt(px, py), f);
            });
            // 輪の内側のくぼみ（リボンの帯の内側。奥が見えて暗く、縁に細い影）
            double[][] inner = lobe(cx, cy, w, h, side, lift * 0.8, 0.5, 0.22);
            poly(g, inner[0], inner[1], (px, py) -> shade(pattern.applyAsInt(px, py), 0.4f));
            for (int i = 0; i + 1 < inner[0].length; i++) {
                if (inner[1][i] < cy) line(g, inner[0][i], inner[1][i], inner[0][i + 1], inner[1][i + 1], 0x90000000);
            }
            // 縁の線: 上側は明るく、下側は暗く
            int n = outer[0].length;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                line(g, outer[0][i], outer[1][i], outer[0][j], outer[1][j], outer[1][i] <= cy ? light : DEEP);
            }
            if (lace) { // 外側の縁のレース（輪の先のまわりに点々）
                int half = n / 4;
                for (int i = half; i < n - half; i += 2) {
                    fill(g, outer[0][i] + side * 0.4, outer[1][i], outer[0][i] + side * 1.1, outer[1][i] + 0.6, (i / 2) % 2 == 0 ? LACE : BLACK);
                }
            }
        }
        // 結び目: 丸みのある立体（上が明るく下が暗い）と光の点、輪に落ちる影
        double k = Math.max(1.6, 2.5 * s), ky = knotDrop;
        fill(g, cx - k + 0.5, cy - k + 1.4 + ky, cx + k + 0.8, cy + k + 1.6 + ky, 0x66000000);
        gradient(g, cx - k, cy - k + ky, cx + k, cy + k + ky, light, DEEP);
        fill(g, cx - k, cy - k + ky, cx + k, cy - k + 0.5 + ky, 0xFFFFFFFF);
        fill(g, cx - k + 0.4, cy - k + 0.9 + ky, cx - k + 1.4, cy - k + 1.7 + ky, 0xFFFFFFFF);
        fill(g, cx + k - 0.5, cy - k + ky, cx + k, cy + k + ky, 0x55000000);
    }

    /**
     * 蝶結びの輪（三角形。結び目から外向きに直線で広がり、先は角が少し丸い）の外形の点（上の縁を外へ、下の縁を内へ）。
     * scale は輪の大きさ（内側のくぼみは小さく）、start は結び目からのずれ（0〜1。内側のくぼみは結び目から離す）。lift は先が持ち上がる量
     */
    private static double[][] lobe(double cx, double cy, double w, double h, int side, double lift, double scale, double start) {
        int steps = 14;
        double[] xs = new double[steps * 2 + 2], ys = new double[steps * 2 + 2];
        int k = 0;
        for (int i = 0; i <= steps; i++) {
            double t = start + (1.0 - start) * i / steps;
            double f = h * scale * (0.12 + 0.88 * t) * Math.min(1.0, Math.sqrt(Math.max(0.0, (1.0 - t) / 0.14)));
            xs[k] = cx + side * w * t;
            ys[k++] = cy - f + lift * t;
        }
        for (int i = steps; i >= 0; i--) {
            double t = start + (1.0 - start) * i / steps;
            double f = h * scale * (0.12 + 0.88 * t) * Math.min(1.0, Math.sqrt(Math.max(0.0, (1.0 - t) / 0.14)));
            xs[k] = cx + side * w * t;
            ys[k++] = cy + f + lift * t * 0.6;
        }
        return new double[][]{xs, ys};
    }

    /** 付け根からの距離 t（0〜1）での垂れの中心の x（付け根から先へ波が伝わる揺れ） */
    private static double tailCenter(double x, double lean, double amp, double t, long now, double speed, double seed) {
        return x + lean * t + Math.sin(now / (430.0 / speed) + seed - t * 2.8) * amp * t * (0.4 + 0.6 * t);
    }

    /**
     * 垂れ（細長い帯）。先は V 字に切れて、少し細くなる。円筒のような濃淡・長さ方向の折れ目・光の筋・ゆるいねじれ（ねじれの裏側は暗く幅が細る）・
     * 下へ落ちる影。pattern で模様を指定。lace が true で縁にレース、studs が true で鋲を打つ。lean は先の横ずれ、amp は揺れの振れ幅
     */
    private static void tail(GuiGraphics g, double x, double y, double len, double w, double lean, double amp, IntBinaryOperator pattern,
                             boolean lace, long now, double speed, double seed, boolean studs) {
        int rows = h(len);
        double notch = 4.0 / len;
        for (int pass = 0; pass < 2; pass++) { // 0: 影、1: 本体
            for (int k = 0; k < rows; k++) {
                double t = k / (double) rows;
                double twist = Math.cos(t * 3.4 + now / (900.0 / speed) + seed);
                double c = tailCenter(x, lean, amp, t, now, speed, seed);
                double half = w / 2.0 * (1.0 - 0.18 * t) * (0.45 + 0.55 * Math.abs(twist));
                boolean back = twist < 0;
                int hy = h(y) + k + (pass == 0 ? h(1.6) : 0);
                double cc = c + (pass == 0 ? 1.2 : 0.0);
                int a = h(cc - half), b = h(cc + half);
                int ca = b, cb = b; // 切れ込みが無い行は、a〜b をそのまま塗る
                boolean notched = t > 1.0 - notch;
                if (notched) { // V 字の切れ込み
                    double cut = half * (t - (1.0 - notch)) / notch;
                    ca = h(cc - cut);
                    cb = h(cc + cut);
                }
                if (pass == 0) {
                    runs(g, a, ca, hy, (px, py) -> 0x55000000);
                    if (notched) runs(g, cb, b, hy, (px, py) -> 0x55000000);
                    continue;
                }
                double fold = 0.16 * Math.sin(k / (double) R * 0.75 + seed * 2.0);
                final double hh = half * R;
                final int center = h(c);
                final boolean bk = back;
                IntBinaryOperator lit = (px, py) -> {
                    double u = hh <= 0 ? 0 : (px - center) / hh;
                    float f = 0.65f + 2.2f * (float) shading(bk ? -u : u) + (float) fold;
                    if (Math.abs(u + 0.4) < 0.12 && !bk) f += 0.9f;
                    if (bk) f *= 0.72f;
                    return shade(pattern.applyAsInt(px, py), f);
                };
                runs(g, a, ca, hy, lit);
                if (notched) runs(g, cb, b, hy, lit);
                if (lace && !back && k % R < 1 && Math.floorMod(k / R, 2) == 0 && !notched) g.fill(b, hy, b + R / 2 + 1, hy + 1, LACE);
                g.fill(a, hy, a + 1, hy + 1, DEEP);
                g.fill(b - 1, hy, b, hy + 1, DEEP);
            }
        }
        if (studs) { // 鋲（丸く光る）
            for (double yy = 3; yy < len - 6; yy += 5) {
                double t = yy / len, c = tailCenter(x, lean, amp, t, now, speed, seed);
                fill(g, c - 0.8, y + yy + 0.8, c + 1.4, y + yy + 2.6, 0x77000000);
                gradient(g, c - 1, y + yy, c + 1, y + yy + 2, 0xFFE8E0F0, 0xFF807890);
                fill(g, c - 1, y + yy, c - 0.2, y + yy + 0.7, 0xFFFFFFFF);
            }
        }
    }

    private static void miniKnot(GuiGraphics g, double x, double y, boolean lit) {
        int c = lit ? LIGHT : PINK;
        fill(g, x - 3, y, x, y + 3, c);
        fill(g, x + 1, y, x + 4, y + 3, c);
        fill(g, x, y + 1, x + 1, y + 2, DEEP);
    }

    /** 散るリボンの切れ端: ひらひら回りながらゆっくり落ち、下ほど透ける */
    private static void scraps(GuiGraphics g, int x0, int width, int top, long now) {
        for (int i = 0; i < 12; i++) {
            double cycle = 3200.0 + (i % 4) * 500.0;
            double t = ((now + i * 777L) % (long) cycle) / cycle;
            double x = x0 + 8 + (i * 61 % (width - 16)) + Math.sin(now / 420.0 + i) * 7.0;
            double y = top + t * 34.0;
            int a = (int) (230 * (1.0 - t));
            if (a <= 6) continue;
            int color = i % 2 == 0 ? 0xFF3FB4 : 0x2A1830;
            g.pose().pushPose();
            g.pose().translate(h(x), h(y), 0);
            g.pose().mulPose(Axis.ZP.rotation((float) (now / 350.0 + i)));
            g.fill(-3 * R, -R, 4 * R, 2 * R, (a << 24) | (i % 2 == 0 ? 0x9A1068 : 0xFF3FB4));
            g.fill(-2 * R, 0, 3 * R, R, (a << 24) | color);
            g.pose().popPose();
        }
    }

    // ---- 描く道具（GUI の座標 → R 倍の細かさの点） ----

    private static int h(double v) {
        return (int) Math.round(v * R);
    }

    private static void fill(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        int a = h(x1), b = h(y1), c = h(x2), d = h(y2);
        if (c <= a) c = a + 1;
        if (d <= b) d = b + 1;
        g.fill(a, b, c, d, color);
    }

    private static void gradient(GuiGraphics g, double x1, double y1, double x2, double y2, int from, int to) {
        g.fillGradient(h(x1), h(y1), h(x2), h(y2), from, to);
    }

    /** 細かい点の x の範囲 [a, b) を、模様の色の連なりごとに塗る */
    private static void runs(GuiGraphics g, int a, int b, int hy, IntBinaryOperator colorAt) {
        if (b <= a) return;
        int start = a, color = colorAt.applyAsInt(a, hy);
        for (int x = a + 1; x < b; x++) {
            int c = colorAt.applyAsInt(x, hy);
            if (c != color) {
                g.fill(start, hy, x, hy + 1, color);
                start = x;
                color = c;
            }
        }
        g.fill(start, hy, b, hy + 1, color);
    }

    /** 凸多角形を走査線で塗る（色は細かい点の座標で決まる模様） */
    private static void poly(GuiGraphics g, double[] xs, double[] ys, IntBinaryOperator colorAt) {
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double y : ys) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int n = xs.length;
        for (int hy = h(minY); hy <= h(maxY); hy++) {
            double y = (hy + 0.5) / R;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double y1 = ys[i], y2 = ys[j];
                if (y < Math.min(y1, y2) || y > Math.max(y1, y2) || y1 == y2) continue;
                double x = xs[i] + (y - y1) * (xs[j] - xs[i]) / (y2 - y1);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (hi >= lo) runs(g, h(lo), h(hi) + 1, hy, colorAt);
        }
    }

    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        int n = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) * R));
        for (int i = 0; i <= n; i++) {
            double x = x1 + (x2 - x1) * i / n, y = y1 + (y2 - y1) * i / n;
            g.fill(h(x), h(y), h(x) + 1, h(y) + 1, color);
        }
    }

    private static int lerp(int a, int b, float k) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (int) Mth.lerp(k, ar, br) << 16 | (int) Mth.lerp(k, ag, bg) << 8 | (int) Mth.lerp(k, ab, bb);
    }
}
