package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
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
    /**
     * ロゴ・ティアの札・禍々しさの飾りはバーの上へはみ出すので、自分の枠の中に収まるよう {@link #TOP_PAD} だけ下げて描き、
     * その分だけ次のボスバーとの間（INCREMENT）も広げる（2026-10-23。ほかのボスバーと重なっていた）
     */
    private static final int TOP_PAD = 14;
    private static final int W = 182, H = 7, INCREMENT = 72 + TOP_PAD;
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

    /** サーバーから知らされた、このプレイヤーに使われているペナルティアタックのティアと、受け取った時刻 */
    private static int tier;
    private static int entityTier;
    private static long tierAt = Long.MIN_VALUE / 2;
    private static final long TIER_STALE_MS = 12000L;

    public static void setTier(int value, int entityValue) {
        tier = Mth.clamp(value, 0, 7);
        entityTier = Mth.clamp(entityValue, 0, 7);
        tierAt = Util.getMillis();
    }

    private static final int[] TIER_COLORS = {0xFFFFB6D8, 0xFFFFB6D8, 0xFFFFA6DC, 0xFFFFC060, 0xFFFF9040, 0xFFFF5050, 0xFFFF2030, 0xFFFF1F8E};

    /** ボスバーの上に出す、ペナルティティアの札（受け取って 12 秒が過ぎたら出さない）。左 = 対エンティティ、右 = 対プレイヤー。文字は自作のローマ数字 */
    private static void drawTier(GuiGraphics g, Font font, int left, int right, int y) {
        if (Util.getMillis() - tierAt > TIER_STALE_MS) return;
        drawTag(g, font, left, y, false, entityTier, "bossbar.automata_valkyries.elenor.tier.entity");
        drawTag(g, font, right, y, true, tier, "bossbar.automata_valkyries.elenor.tier.player");
    }

    /** edge は札の外側の端（右寄せなら右端、左寄せなら左端）。上の段に見出しとローマ数字、下の段にティアの名前を出す */
    private static void drawTag(GuiGraphics g, Font font, int edge, int y, boolean alignRight, int value, String captionKey) {
        float glyphScale = 1.5F, smallScale = 0.6F;
        Component caption = Component.translatable(captionKey);
        // 名前は「II 幕間…」のように数字から始まるので、数字の部分（最初の空白まで）は外す
        String full = net.minecraft.client.resources.language.I18n.get("bossbar.automata_valkyries.elenor.tier.name." + value);
        String name = full.contains(" ") ? full.substring(full.indexOf(' ') + 1) : full;
        int captionW = (int) Math.ceil(font.width(caption) * smallScale);
        int nameW = (int) Math.ceil(font.width(name) * smallScale);
        int glyphW = (int) Math.ceil(ElenorNumeral.width(value) * glyphScale);
        int w = Math.max(captionW + glyphW + 9, nameW + 8);
        int x = alignRight ? edge - w : edge;
        boolean top = value >= 7;
        int color = TIER_COLORS[value];
        int pulse = top ? (int) (0x30 + 0x30 * (0.5F + 0.5F * Mth.sin(Util.getMillis() / 180.0F))) : 0;
        int yTop = y - 9; // バー 1 本ぶんの高さ（INCREMENT）に余裕があるので、上へ伸ばす
        g.fill(x - 1, yTop - 1, x + w + 1, y + 11, 0xFF000000 | (color & 0xFFFFFF) >> 1 & 0x7F7F7F);
        g.fill(x, yTop, x + w, y + 10, top ? 0xFF000000 | pulse << 16 : 0xFF0C070D);
        g.pose().pushPose();
        g.pose().translate(x + 3, yTop + 3, 0.0F);
        g.pose().scale(smallScale, smallScale, 1.0F);
        g.drawString(font, caption, 0, 0, 0xFFD8C8DC, false);
        g.pose().popPose();
        ElenorNumeral.draw(g, value, x + 6 + captionW, yTop + 1.0F, glyphScale, color);
        g.pose().pushPose();
        g.pose().translate(x + (w - nameW) / 2.0F, y + 4.0F, 0.0F);
        g.pose().scale(smallScale, smallScale, 1.0F);
        g.drawString(font, name, 0, 0, color, false);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(INCREMENT);
        if (STATES.size() > 16) STATES.clear();
        State st = STATES.computeIfAbsent(boss.getId(), k -> new State());
        draw(event.getGuiGraphics(), st, boss, event.getGuiGraphics().guiWidth() / 2, event.getY() + TOP_PAD);
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss, int cx, int y) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        int phase = boss.getColor() == BossEvent.BossBarColor.RED ? 3 : boss.getColor() == BossEvent.BossBarColor.PINK ? 2 : 1;
        // 禍々しさ: 攻撃ティア（対エンティティと対プレイヤーの高い方）。上がるごとに飾りが増え、色が赤黒くなる（2026-10-23 ユーザー指定）
        int menace = Util.getMillis() - tierAt > TIER_STALE_MS ? 0 : Math.max(tier, entityTier);
        int vis = Math.max(phase, menace >= 3 ? 3 : menace >= 1 ? 2 : 1); // 見た目だけ（光・脈動・色）。ラベルのフェーズ表示は変えない
        double dt = Math.min(0.2, (now - st.last) / 1000.0);
        st.last = now;
        st.trail = p >= st.trail ? p : Math.max(p, st.trail - 0.25 * dt);

        int x0 = cx - W / 2, y0 = y + 17, yb = y0 + H;
        float pulse = 0.5F + 0.5F * Mth.sin(now / (vis >= 3 ? 140.0F - 9.0F * menace : 500.0F));
        double speed = (vis >= 3 ? 1.7 : vis == 2 ? 1.25 : 1.0) * (1.0 + 0.07 * menace); // 揺れの速さ

        g.pose().pushPose();
        g.pose().scale(1.0F / R, 1.0F / R, 1.0F);
        if (menace >= 7 && (now / 70) % 9 == 0) g.pose().translate(((now / 70) % 2 == 0 ? 1 : -1) * R * 0.8F, 0.0F, 0.0F); // ティア 7: ときどき震える

        // 紫の光（バーの後ろ）
        int glow = vis >= 3 ? (int) (0x30 + 0x28 * pulse + 0x06 * menace) : vis == 2 ? 0x40 : 0x30;
        int glowColor = lerp(vis >= 3 ? 0xFF2060 : 0xB030D0, 0xFF0014, menace / 7.0F) & 0xFFFFFF;
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
            int col = menace >= 5 ? lerp(0xFFD0001E, 0xFF38002C, u) : vis >= 3 ? lerp(0xFFFF2D6A, 0xFFB01E90, u) : lerp(0xFFFF3CE0, 0xFF6A2AC0, u * u);
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

        menace(g, x0, y0, yb, menace, now);

        g.pose().popPose();

        // 文字（元の細かさで）
        Font font = Minecraft.getInstance().font;
        int ny = y0 - 14;
        // 名前は、ユーザーが用意したロゴの絵（日本語は「エレノア」、それ以外は「Eleanor」）で（2026-10-23 ユーザー指定）
        drawLogo(g, cx, y0 - 2, menace, now);
        g.pose().pushPose();
        g.pose().translate(x0 + 30, yb + 12, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable("bossbar.automata_valkyries.elenor.phase" + phase), 0, 0, phase >= 3 ? 0xFFFF7AB8 : 0xFFFFB6D8, false);
        String pct = Math.round(p * 100) + "%";
        g.drawString(font, pct, (int) ((W - 60) / 0.75F) - font.width(pct), 0, 0xFFFFD8EA, false);
        g.pose().popPose();
        drawTier(g, font, x0, x0 + W, ny - 2);
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

    // ---- 名前のロゴ（ユーザーが用意した絵。黒い背景を抜いた PNG） ----

    private static final ResourceLocation TITLE_JA = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/gui/elenor_title_ja.png");
    private static final ResourceLocation TITLE_EN = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/gui/elenor_title_en.png");
    private static final int TITLE_TEX_W = 512, TITLE_JA_H = 221, TITLE_EN_H = 205;
    /** 画面での幅（GUI の座標） */
    private static final float TITLE_WIDTH = 62.0F;

    /**
     * 名前のロゴを、bottom を下端に、cx を中心に描く。ゆっくり呼吸するように大きさが揺れ、ティアが上がるほど赤く染まる
     */
    private static void drawLogo(GuiGraphics g, int cx, int bottom, int menace, long now) {
        boolean ja = Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ja");
        ResourceLocation texture = ja ? TITLE_JA : TITLE_EN;
        int texH = ja ? TITLE_JA_H : TITLE_EN_H;
        float scale = TITLE_WIDTH / TITLE_TEX_W * (1.0F + 0.015F * Mth.sin(now / 600.0F));
        float k = menace / 7.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F - 0.35F * k, 1.0F - 0.35F * k, 1.0F);
        g.pose().pushPose();
        float height = texH * TITLE_WIDTH / TITLE_TEX_W;
        g.pose().translate(cx, Math.max(height / 2.0F + 1.0F, bottom - height / 2.0F), 0.0F); // 画面の上端で切れないように
        g.pose().scale(scale, scale, 1.0F);
        g.blit(texture, -TITLE_TEX_W / 2, -texH / 2, 0, 0.0F, 0.0F, TITLE_TEX_W, texH, TITLE_TEX_W, texH);
        g.pose().popPose();
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---- 禍々しさ（ティアが上がるごとに積み重なる） ----

    /**
     * 攻撃ティア m（0〜7）に応じて、バーを禍々しくする。1: 暗い赤の縁。2: 棘。3: ひび割れ。4: 垂れる血。5: 目。6: 角。7: 黒い炎の冠と赤い脈動
     */
    private static void menace(GuiGraphics g, int x0, int y0, int yb, int m, long now) {
        if (m <= 0) return;
        float beat = 0.5F + 0.5F * Mth.sin(now / (330.0F - 28.0F * m));
        IntBinaryOperator dark = (px, py) -> 0xFF16040C;
        IntBinaryOperator crimson = (px, py) -> 0xFFC0102C;
        IntBinaryOperator blood = (px, py) -> 0xFF8A0018;
        // 1〜: 枠の縁に、暗い赤の線
        int edge = Math.min(0xFF, 0x50 + 0x20 * m) << 24 | 0xC01030;
        fill(g, x0 - 3, y0 - 3, x0 + W + 3, y0 - 2, edge);
        fill(g, x0 - 3, yb + 2, x0 + W + 3, yb + 3, edge);
        // 2〜: 棘（上と下の縁。数と長さが増える）
        if (m >= 2) {
            int count = 5 + 3 * m;
            for (int i = 0; i < count; i++) {
                double tx = x0 + (i + 0.5) * W / count;
                double len = 2.5 + 0.55 * m + (m >= 5 ? ((i * 7 + now / 160) % 3) * 0.5 : 0.0);
                poly(g, new double[]{tx - 2.1, tx + 2.1, tx}, new double[]{y0 - 3, y0 - 3, y0 - 3 - len}, i % 3 == 0 ? crimson : dark);
                poly(g, new double[]{tx - 2.1, tx + 2.1, tx}, new double[]{yb + 3, yb + 3, yb + 3 + len * 0.8}, dark);
            }
        }
        // 3〜: ひび割れ（赤く光る亀裂。ときどきちらつく）
        if (m >= 3) {
            for (int c = 0; c < m - 2; c++) {
                if ((now / 90 + c * 3) % 7 == 0) continue;
                double cx = x0 + W * (0.12 + 0.76 * ((c * 0.381 + 0.17) % 1.0));
                int col = 0xFF000000 | (0xC0 + (int) (0x3F * beat)) << 16 | 0x1030;
                line(g, cx, y0, cx + 3, y0 + H * 0.35, col);
                line(g, cx + 3, y0 + H * 0.35, cx - 2, y0 + H * 0.65, col);
                line(g, cx - 2, y0 + H * 0.65, cx + 2, yb, col);
            }
        }
        // 4〜: 垂れる血
        if (m >= 4) {
            int drips = 3 + m;
            for (int i = 0; i < drips; i++) {
                double dx = x0 + W * (0.05 + 0.9 * ((i * 0.618 + 0.11) % 1.0));
                double len = 3.0 + ((now / 260.0 + i * 0.37) % 1.0) * (4.0 + m);
                fill(g, dx, yb + 3, dx + 1.2, yb + 3 + len, 0xFFB0102A);
                poly(g, new double[]{dx - 0.6, dx + 1.8, dx + 0.6}, new double[]{yb + 3 + len, yb + 3 + len, yb + 4.6 + len}, blood);
            }
        }
        // 5〜: 目（バーの両端の上。鼓動に合わせて見開く）
        if (m >= 5) {
            for (int side = 0; side < 2; side++) {
                double ex = side == 0 ? x0 + 14 : x0 + W - 14, ey = y0 - 9;
                double open = 1.2 + 1.4 * beat;
                poly(g, new double[]{ex - 5, ex, ex + 5, ex}, new double[]{ey, ey - open, ey, ey + open}, (px, py) -> 0xFFF0E0E8);
                fill(g, ex - 0.6, ey - open + 0.4, ex + 0.6, ey + open - 0.4, 0xFFC0001C);
                fill(g, ex - 0.25, ey - open + 0.4, ex + 0.25, ey + open - 0.4, 0xFF100004);
            }
        }
        // 6〜: 角（バーの両端から、外へ反って伸びる）
        if (m >= 6) {
            double sway = Mth.sin(now / 420.0F) * 0.8;
            poly(g, new double[]{x0 - 5, x0 + 5, x0 - 12 + sway}, new double[]{y0 - 3, y0 - 3, y0 - 19}, dark);
            poly(g, new double[]{x0 + W + 5, x0 + W - 5, x0 + W + 12 - sway}, new double[]{y0 - 3, y0 - 3, y0 - 19}, dark);
            line(g, x0 - 4, y0 - 4, x0 - 11 + sway, y0 - 18, 0xFFC0102C);
            line(g, x0 + W + 4, y0 - 4, x0 + W + 11 - sway, y0 - 18, 0xFFC0102C);
        }
        // 7: 黒い炎の冠と、赤い脈動
        if (m >= 7) {
            int flames = 16;
            for (int i = 0; i < flames; i++) {
                double fx = x0 + (i + 0.5) * W / flames;
                double len = 6.0 + 7.0 * ((Math.sin(now / 130.0 + i * 1.7) + 1.0) / 2.0);
                poly(g, new double[]{fx - 3.2, fx + 3.2, fx + Math.sin(now / 200.0 + i) * 1.5}, new double[]{y0 - 4, y0 - 4, y0 - 4 - len}, i % 2 == 0 ? dark : crimson);
            }
            int pulseA = (int) (0x18 + 0x30 * beat);
            gradient(g, x0 - 30, y0 - 22, x0 + W + 30, y0 + H / 2.0, 0x00FF0020, (pulseA << 24) | 0xFF0020);
            gradient(g, x0 - 30, y0 + H / 2.0, x0 + W + 30, yb + 22, (pulseA << 24) | 0xFF0020, 0x00FF0020);
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
