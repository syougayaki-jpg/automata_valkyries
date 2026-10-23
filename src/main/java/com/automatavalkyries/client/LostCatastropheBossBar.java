package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.zero.LostCatastropheEntity;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 零号機専用のボスバー（2026-10-20。作りは {@link TimeRulerBossBar} と同じで、色と飾りは壊れたデータ）。
 * <ul>
 *   <li>舗装: 角を斜めに落とした黒と深紅の板、赤く光る縁がときどきずれて走る（グリッチ）、走査線、板のまわりを流れる赤い欠片。左に赤い核の枠（脈打つ）</li>
 *   <li>HP のゲージ: 黒い溝に形態の色の帯（第 1 形態 紅・第 2 形態 紫・第 3 形態 白に近い桃色）、削れた分の淡い残り、66%・33% の印。
 *       上に重ねて障壁（FE を吸って張る）の白い細い帯</li>
 *   <li>下の段: 形態の名と HP。詠唱の間は「世界確定消滅まで」と残りの秒（赤く点滅）と、同期過電圧ショックの溜まりの水色の帯。動けない間は「同期過電圧」</li>
 *   <li>撃破: ひびが走り、赤い欠片が白くなりながら散って消える</li>
 * </ul>
 * 形態はサーバーがボスバーの色で伝える（赤・紫・桃色）。障壁・詠唱は近くの零号機の同期データから読む
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class LostCatastropheBossBar {

    private static final String KEY = "entity.automata_valkyries.lost_catastrophe";
    private static final int W = 182, H = 6, CORE = 30, INCREMENT = 48;
    private static final int WHITE = 0xFFFFF0F0, RED = 0xFFFF2A44, RED_DEEP = 0xFF7A0818, MAGENTA = 0xFFFF2A9A, VIOLET = 0xFFB060FF, VIOLET_DEEP = 0xFF4A1A90;
    private static final int PINK = 0xFFFFB0D8, CYAN = 0xFF7FE4FF, PLATE = 0xC8080006, PLATE_DEEP = 0xE0040003, GROOVE = 0xFF2A0810;
    private static final long CRACK_MS = 800, BREAK_MS = 1600;

    private static final class State {
        double trail = 1.0;
        long last = Util.getMillis(), seen, broken;
        int phase = 1, cx, y;
        List<int[]> cracks;
        List<float[]> shards;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private LostCatastropheBossBar() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!(boss.getName().getContents() instanceof TranslatableContents tc) || !KEY.equals(tc.getKey())) return;
        event.setCanceled(true);
        event.setIncrement(INCREMENT);
        if (STATES.size() > 16) STATES.clear();
        State st = STATES.computeIfAbsent(boss.getId(), k -> new State());
        st.cx = event.getGuiGraphics().guiWidth() / 2;
        st.y = event.getY();
        st.seen = Util.getMillis();
        draw(event.getGuiGraphics(), st, boss);
    }

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

    @Nullable
    private static LostCatastropheEntity nearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        LostCatastropheEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LostCatastropheEntity e : mc.level.getEntitiesOfClass(LostCatastropheEntity.class, mc.player.getBoundingBox().inflate(160.0))) {
            double d = e.distanceToSqr(mc.player);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static int gaugeX(State st) {
        return st.cx - W / 2 + 18;
    }

    private static int gaugeY(State st) {
        return st.y + 6;
    }

    private static int phaseOf(LerpingBossEvent boss) {
        return boss.getColor() == BossEvent.BossBarColor.PINK ? 3 : boss.getColor() == BossEvent.BossBarColor.PURPLE ? 2 : 1;
    }

    private static int phaseColor(int phase) {
        return phase == 3 ? PINK : phase == 2 ? VIOLET : RED;
    }

    private static void draw(GuiGraphics g, State st, LerpingBossEvent boss) {
        long now = Util.getMillis();
        float p = boss.getProgress() < 0.001F ? 0.0F : Math.min(1.0F, boss.getProgress());
        st.phase = phaseOf(boss);
        double dt = Math.min(0.2, (now - st.last) / 1000.0);
        st.last = now;
        st.trail = p >= st.trail ? p : Math.max(p, st.trail - 0.25 * dt);
        if (p > 0.0F) st.broken = 0;
        else if (st.broken == 0) {
            st.broken = now;
            prepareBreak(st, boss.getId());
        }
        long since = st.broken == 0 ? -1 : now - st.broken;
        if (since > CRACK_MS) {
            drawShards(g, st, since - CRACK_MS);
            return;
        }
        LostCatastropheEntity e = nearest();
        int chant = e != null ? e.chantLeft() : 0, shock = e != null ? e.shockCharge() : 0, stun = e != null ? e.stunLeft() : 0;
        float shield = e != null ? e.shield() / LostCatastropheEntity.SHIELD_MAX : 0.0F;
        int x0 = gaugeX(st), y0 = gaugeY(st);
        boolean glitch = (now / 70) % 11 == 0 || chant > 0 && (now / 50) % 5 == 0;
        int jitter = glitch ? (int) (Math.sin(now * 0.91) * 3) : 0;
        g.pose().pushPose();
        g.pose().translate(jitter, 0, 0);
        int edge = chant > 0 ? lerpColor(RED, WHITE, 0.5F + 0.5F * Mth.sin(now / 90.0F)) : stun > 0 ? CYAN : phaseColor(st.phase);
        drawPlate(g, x0, y0, st.phase, edge, now, glitch);
        drawGauge(g, st, x0, y0, p, shield, now);
        drawTexts(g, boss, x0, y0, p, st.phase, chant, shock, stun, now);
        drawCore(g, x0 - 10 - CORE / 2 + 4, y0 + H / 2, st.phase, edge, now, chant > 0);
        g.pose().popPose();
        if (since >= 0) drawCracks(g, st, since / (float) CRACK_MS);
    }

    private static void drawPlate(GuiGraphics g, int x0, int y0, int phase, int edge, long now, boolean glitch) {
        int l = x0 - 10, r = x0 + W + 12, t = y0 - 13, b = y0 + H + 18, ch = 6;
        chamferFill(g, l, t, r, b, ch, PLATE);
        chamferFill(g, l + 3, y0 - 3, r - 3, y0 + H + 3, 2, PLATE_DEEP);
        for (int yy = t + 1; yy < b - 1; yy += 2) g.fill(l + 1, yy, r - 1, yy + 1, 0x0EFF2A44);
        chamferOutline(g, l, t, r, b, ch, edge);
        chamferOutline(g, l + 2, t + 2, r - 2, b - 2, ch - 1, (0x50 << 24) | (edge & 0xFFFFFF));
        if (glitch) {                                                                       // ずれた縁と色の欠け
            RandomSource rnd = RandomSource.create(now / 70);
            for (int i = 0; i < 4; i++) {
                int gy = t + rnd.nextInt(b - t), gx = l + rnd.nextInt(r - l - 30);
                g.fill(gx, gy, gx + 10 + rnd.nextInt(30), gy + 1, i % 2 == 0 ? 0xA0FF2A9A : 0xA07FE4FF);
            }
        }
        int sweep = (int) ((now / 10) % (r - l + 40)) + l - 20;
        g.fill(Math.max(l + ch, sweep), t, Math.min(r, sweep + 22), t + 1, 0xC0FFFFFF);
        for (int i = 0; i < 14; i++) {                                                      // 流れる赤い欠片
            long seed = i * 7919L;
            int mx = l + (int) ((seed * 13 + now / (16 + i % 6)) % (r - l));
            int my = (i % 2 == 0 ? t - 2 : b) + (int) (Math.sin(now / 280.0 + i) * 2);
            int a = (int) (90 + 90 * Math.sin(now / 170.0 + i * 1.7));
            int c = i % 3 == 0 ? phaseColor(phase) : i % 3 == 1 ? MAGENTA : RED;
            if (a > 8) g.fill(mx, my, mx + 1 + i % 3, my + 1, (a << 24) | (c & 0xFFFFFF));
        }
    }

    private static void drawGauge(GuiGraphics g, State st, int x0, int y0, float p, float shield, long now) {
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, GROOVE);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0000000);
        int trw = (int) (W * st.trail);
        if (trw > 0) g.fill(x0, y0, x0 + trw, y0 + H, 0x90FFB0C0);
        int fw = (int) (W * p);
        if (fw > 0) {
            int top = st.phase == 3 ? 0xFFFFE8F4 : st.phase == 2 ? 0xFFD8B0FF : 0xFFFF7088;
            int bottom = st.phase == 3 ? MAGENTA : st.phase == 2 ? VIOLET_DEEP : RED_DEEP;
            g.fillGradient(x0, y0, x0 + fw, y0 + H, top, bottom);
            for (int k = 0; k < 3; k++) {
                int px = x0 + (int) (((now / 5 + k * 67) % (W + 8)) - 4);
                if (px >= x0 && px < x0 + fw) g.fill(px, y0, px + 2, y0 + H, 0x90FFFFFF);
            }
            g.fill(x0, y0, x0 + fw, y0 + 1, 0xFFFFFFFF);
        }
        for (float mark : new float[]{2.0F / 3.0F, 1.0F / 3.0F}) {
            int mx = x0 + (int) (W * mark);
            g.fill(mx, y0 - 3, mx + 1, y0, RED);
            g.fill(mx, y0 + H, mx + 1, y0 + H + 2, RED);
        }
        int sw = (int) (W * Mth.clamp(shield, 0.0F, 1.0F));                               // 障壁
        if (sw > 0) {
            g.fill(x0, y0 - 3, x0 + sw, y0 - 1, 0xE0FFF0F0);
            g.fill(x0, y0 - 1, x0 + sw, y0, 0x80FF2A9A);
        }
    }

    private static void drawTexts(GuiGraphics g, LerpingBossEvent boss, int x0, int y0, float p, int phase, int chant, int shock, int stun, long now) {
        Font font = Minecraft.getInstance().font;
        Component name = boss.getName();
        int nx = x0 + W / 2 - font.width(name) / 2, ny = y0 - 12;
        g.drawString(font, name, nx + 1, ny + 1, 0xFF500010, false);
        g.drawString(font, name, nx, ny, WHITE, false);
        g.pose().pushPose();
        g.pose().translate(x0, y0 + H + 4, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        Component left;
        int color;
        if (chant > 0) {
            left = Component.translatable("bossbar.automata_valkyries.lost_catastrophe.chant", (chant + 19) / 20);
            color = (now / 250) % 2 == 0 ? 0xFFFF4060 : 0xFFFFE0E0;
        } else if (stun > 0) {
            left = Component.translatable("bossbar.automata_valkyries.lost_catastrophe.stunned");
            color = 0xFFB8F4FF;
        } else {
            left = Component.translatable("bossbar.automata_valkyries.lost_catastrophe.phase" + phase);
            color = phase == 3 ? 0xFFFFD0E8 : phase == 2 ? 0xFFD8B0FF : 0xFFFF9CAC;
        }
        g.drawString(font, left, 0, 0, color, false);
        String hp = Math.round(p * LostCatastropheEntity.MAX_HP) + " / " + Math.round(LostCatastropheEntity.MAX_HP);
        g.drawString(font, hp, (int) (W / 0.75F) - font.width(hp), 0, 0xFFFFE0E8, false);
        g.pose().popPose();
        if (chant > 0) {                                                                    // 同期過電圧ショックの溜まり
            int sy = y0 + H + 13, sw = (int) (W * shock / (float) LostCatastropheEntity.SHOCK_TICKS);
            g.fill(x0, sy, x0 + W, sy + 2, 0xA0001018);
            if (sw > 0) g.fill(x0, sy, x0 + sw, sy + 2, CYAN);
        }
    }

    /** 赤い核の枠: 暗い円の地に脈打つ赤い核、まわりを回る 3 つの欠けた輪 */
    private static void drawCore(GuiGraphics g, int cx, int cy, int phase, int ring, long now, boolean chanting) {
        int x = cx - CORE / 2, y = cy - CORE / 2;
        chamferFill(g, x - 3, y - 3, x + CORE + 3, y + CORE + 3, 7, 0xE0060004);
        chamferOutline(g, x - 3, y - 3, x + CORE + 3, y + CORE + 3, 7, ring);
        float rad = CORE / 2.0F - 2;
        disc(g, cx, cy, rad, 0xD0140008);
        float beat = 0.5F + 0.5F * Mth.sin(now / (chanting ? 70.0F : 180.0F));
        disc(g, cx, cy, 3.5F + 2.0F * beat, (0xC0 << 24) | (phaseColor(phase) & 0xFFFFFF));
        disc(g, cx, cy, 2.0F, WHITE);
        for (int k = 0; k < 3; k++) {
            double base = now / (600.0 - k * 120) * (k % 2 == 0 ? 1 : -1) + k;
            float r = rad - 1 - k * 3;
            int n = 40;
            for (int i = 0; i < n; i++) {
                if (i % 10 < 2) continue;                                                   // 欠け
                double a = base + Math.PI * 2 * i / n;
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, k == 1 ? (0xC0 << 24) | (VIOLET & 0xFFFFFF) : ring);
            }
        }
    }

    // ---- 撃破 ----

    private static void prepareBreak(State st, UUID id) {
        RandomSource r = RandomSource.create(id.getLeastSignificantBits());
        int x0 = gaugeX(st), y0 = gaugeY(st);
        int l = x0 - 10, rt = x0 + W + 12, t = y0 - 13, b = y0 + H + 18;
        st.cracks = new ArrayList<>();
        int ix = x0 + W / 3 + r.nextInt(W / 3), iy = y0 + H / 2;
        for (int br = 0; br < 12; br++) {
            double ang = br * (Math.PI * 2 / 12) + r.nextDouble() * 0.5, x = ix, y = iy;
            for (int s = 0; s < 9; s++) {
                ang += (r.nextDouble() - 0.5);
                double len = 3 + r.nextDouble() * 7, nx = x + Math.cos(ang) * len * 2.4, ny = y + Math.sin(ang) * len * 0.6;
                st.cracks.add(new int[]{(int) x, (int) y, (int) nx, (int) ny, s});
                x = nx;
                y = ny;
            }
        }
        st.shards = new ArrayList<>();
        int cols = 28, rows = 4;
        float cw = (rt - l) / (float) cols, chh = (b - t) / (float) rows;
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                st.shards.add(new float[]{l + cw * (c + 0.5F), t + chh * (rr + 0.5F), cw * (0.4F + r.nextFloat() * 0.6F), (r.nextFloat() - 0.5F) * 0.07F,
                        0.03F + r.nextFloat() * 0.06F, r.nextFloat()});
            }
        }
    }

    private static void drawCracks(GuiGraphics g, State st, float k) {
        if (st.cracks == null) return;
        int x0 = gaugeX(st), y0 = gaugeY(st);
        if (k < 0.15F) {
            int a = (int) ((1 - k / 0.15F) * 170);
            g.fill(x0 - 30, y0 - 17, x0 + W + 14, y0 + H + 22, (a << 24) | 0xFFE0E8);
        }
        int shown = (int) (k * 9.999F);
        for (int[] c : st.cracks) {
            if (c[4] > shown) continue;
            line(g, c[0], c[1] + 1, c[2], c[3] + 1, 0x90FF2A44);
            line(g, c[0], c[1], c[2], c[3], 0xFFFFFFFF);
        }
    }

    /** 赤い欠片が白くなりながら、ふわりと上がって落ちて消える（怨嗟が抜けていく） */
    private static void drawShards(GuiGraphics g, State st, long t) {
        if (st.shards == null) return;
        float life = Math.max(0.0F, 1.0F - t / (float) BREAK_MS);
        int a = (int) (life * 255);
        if (a <= 0) return;
        for (float[] s : st.shards) {
            float x = s[0] + s[3] * t, y = s[1] - s[4] * t + 0.0001F * t * t;
            int c = lerpColor(s[5] > 0.5F ? RED : MAGENTA, WHITE, 1.0F - life);
            int size = Math.max(1, (int) ((life * 0.7F + 0.3F) * s[2] / 2));
            g.fill((int) x, (int) y, (int) x + size, (int) y + Math.max(1, size / 2), (a << 24) | (c & 0xFFFFFF));
        }
    }

    // ---- 描く道具 ----

    private static void disc(GuiGraphics g, int cx, int cy, float r, int color) {
        int ri = (int) Math.ceil(r);
        for (int dy = -ri; dy <= ri; dy++) {
            int half = (int) Math.floor(Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
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
