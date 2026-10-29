package com.automatavalkyries.client;

import com.automatavalkyries.chrono.ChronoLink;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoMenu;
import com.automatavalkyries.chrono.ChronoSmelterBlockEntity;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.chrono.LuminalReactorBlockEntity;
import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import com.automatavalkyries.item.ChronoTiers;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * クロノリンク・マシンの画面。背景は宇宙で、ティアが上がるほど豪華になる:
 * <ul>
 *   <li>T1 ハイエンド: 深い青の星空、白銀の縁</li>
 *   <li>T2 フューチャー: 紫と橙の星雲、金の縁と角飾り、縁を走る光</li>
 *   <li>T3 ビヨンド: 渦を巻く銀河、流れ星、十字に光る星、宝石の角の二重の金の縁</li>
 *   <li>T4 ネバーラスト: 色の移ろうオーロラ、虹色に回る縁、漂う光の粒、結晶の角</li>
 * </ul>
 * テクスチャは使わず、すべて塗りで描く。
 */
public class ChronoScreen extends AbstractContainerScreen<ChronoMenu> {

    private static final int GAUGE_X = 8, GAUGE_Y = 22, GAUGE_W = 10, GAUGE_H = 72;
    private static final int REPO_X = 48, REPO_Y = 22, REPO_COLS = 8, REPO_ROWS = 4;
    /** リポジトリの左の列（預け入れ口 22〜38 の下）の行 */
    private static final int REPO_TYPES_Y = 42, REPO_FILTER_Y = 51, REPO_PAGE_Y = 65, REPO_ARROW_Y = 75, REPO_SORT_Y = 85;

    private record Star(float x, float y, int size, int color, float phase, float speed) {}

    private final List<Star> stars = new ArrayList<>();
    private final ChronoTier tier;
    private int page;
    /** ヴォイド・リポジトリの絞り込み（0 = 全部、1 = 素材、2 = 装備） */
    private int repoFilter;
    /** ヴォイド・リポジトリの検索（2026-10-29 ユーザー指定「ヴォイドリポジトリに検索機能をつけて」） */
    @org.jetbrains.annotations.Nullable private net.minecraft.client.gui.components.EditBox search;
    private static final int SEARCH_X = 132, SEARCH_Y = 5, SEARCH_W = 62, COMPRESS_X = 104;

    /** 絞り込みと検索で見える物の番号（中身の並びの番号） */
    private List<Integer> repoVisible() {
        List<Integer> out = new ArrayList<>();
        String query = search == null ? "" : search.getValue().trim();
        for (int i = 0; i < menu.repository.size(); i++) {
            var e = menu.repository.get(i);
            if (!query.isEmpty()) {                                                             // 検索中は当たった物だけを詰めて見せる
                if (e != null && (repoFilter == 0 || (repoFilter == 1) != VoidRepositoryBlockEntity.gear(e.key)) && RepositorySearch.matches(e.key, query)) out.add(i);
            } else if (repoFilter == 0) out.add(i); // 全部は空きも場所のまま見せる（場所が動かないよう）
            else if (e != null && (repoFilter == 1) != VoidRepositoryBlockEntity.gear(e.key)) out.add(i);
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.type() != ChronoMachineType.VOID_REPOSITORY) return;
        String before = search == null ? "" : search.getValue();                                 // 画面の大きさが変わっても検索は残す
        search = new net.minecraft.client.gui.components.EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 12,
                Component.translatable("gui.automata_valkyries.chrono.repo_search"));
        search.setMaxLength(50);
        search.setValue(before);
        search.setHint(Component.translatable("gui.automata_valkyries.chrono.repo_search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setResponder(text -> page = 0);
        addRenderableWidget(search);
    }

    /** 検索の欄に打っている間は、キー（持ち物を閉じる E など）を欄に渡す。Esc は閉じる */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search != null && search.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(key, scan, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }

    public ChronoScreen(ChronoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.tier = menu.tier();
        imageWidth = ChronoMachineType.WIDTH;
        imageHeight = ChronoMachineType.HEIGHT;
        inventoryLabelX = ChronoMachineType.PLAYER_X;
        inventoryLabelY = ChronoMachineType.PLAYER_Y - 11;
        titleLabelX = 10;
        titleLabelY = 7;
        Random random = new Random(1234L + tier.level * 77L + menu.type().ordinal());
        int count = switch (tier) {
            case HIGH_END -> 60;
            case FUTURE -> 95;
            case BEYOND -> 140;
            case NEVERLAST -> 190;
        };
        int[] palette = switch (tier) {
            case HIGH_END -> new int[]{0xFFFFFF, 0xBFEFFF, 0x7DF9FF};
            case FUTURE -> new int[]{0xFFFFFF, 0xFFE6A0, 0xC9A0FF, 0xFFB070};
            case BEYOND -> new int[]{0xFFFFFF, 0xFFE6A0, 0xA0C8FF, 0xFF9EC8};
            case NEVERLAST -> new int[]{0xFFFFFF, 0xFFB3F0, 0xA0FFF0, 0xFFF0A0, 0xB0A0FF};
        };
        for (int i = 0; i < count; i++) {
            int size = random.nextFloat() < (tier.level >= 3 ? 0.12F : 0.06F) ? 2 : 1;
            stars.add(new Star(random.nextFloat() * (imageWidth - 4) + 2, random.nextFloat() * (imageHeight - 4) + 2, size,
                    palette[random.nextInt(palette.length)], random.nextFloat() * Mth.TWO_PI, 0.6F + random.nextFloat() * 2.2F));
        }
    }

    // ---- 色の道具 -------------------------------------------------------------

    private static int argb(int alpha, int rgb) { return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF); }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hue(float h) { return Mth.hsvToRgb(((h % 1) + 1) % 1, 0.55F, 1.0F); }

    private float seconds() { return (Util.getMillis() % 3_600_000L) / 1000.0F; }

    private int accent() {
        if (tier == ChronoTier.NEVERLAST) return hue(seconds() * 0.08F);
        return ChronoTiers.rgb(tier);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    // ---- 背景 -----------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        float t = seconds();
        // 宇宙のグラデーション
        int top, bottom;
        switch (tier) {
            case HIGH_END -> { top = 0x040A1C; bottom = 0x0C1C38; }
            case FUTURE -> { top = 0x07031A; bottom = 0x1C0B36; }
            case BEYOND -> { top = 0x000004; bottom = 0x0B0618; }
            default -> { top = mix(0x05001A, 0x001A1A, (Mth.sin(t * 0.3F) + 1) / 2); bottom = mix(0x1A0030, 0x002A30, (Mth.cos(t * 0.25F) + 1) / 2); }
        }
        g.fillGradient(x, y, x + w, y + h, argb(255, top), argb(255, bottom));
        g.enableScissor(x, y, x + w, y + h);
        if (tier.level >= 2) nebulae(g, x, y, t);
        if (tier == ChronoTier.NEVERLAST) aurora(g, x, y, t);
        if (tier.level >= 3) galaxy(g, x + w - 44, y + 26, t);
        drawStars(g, x, y, t);
        if (tier.level >= 3) shootingStar(g, x, y, t);
        if (tier == ChronoTier.NEVERLAST) motes(g, x, y, t);
        g.disableScissor();
        frame(g, x, y, w, h, t);
        // 持ち物の下の暗い帯
        int px = x + ChronoMachineType.PLAYER_X - 4, py = y + ChronoMachineType.PLAYER_Y - 14;
        g.fill(px, py, px + 170, py + 92, argb(120, 0x000000));
        // スロット（暗いガラスと、ティアの色の縁）
        int edge = argb(tier.level >= 3 ? 200 : 150, accent());
        for (Slot slot : menu.slots) glassSlot(g, x + slot.x - 1, y + slot.y - 1, edge);
        gauge(g, x + GAUGE_X, y + GAUGE_Y, t);
        machineWidgets(g, x, y, t);
        linkIcons(g, x, y, mouseX, mouseY);
    }

    private void nebulae(GuiGraphics g, int x, int y, float t) {
        int[][] clouds = tier == ChronoTier.FUTURE
                ? new int[][]{{40, 40, 34, 0x8040C0}, {160, 70, 30, 0xC06030}, {110, 20, 22, 0x6050D0}}
                : new int[][]{{30, 60, 40, 0x4030A0}, {150, 40, 36, 0x802060}, {100, 90, 28, 0x205090}, {180, 110, 24, 0x603080}};
        for (int[] c : clouds) {
            float drift = Mth.sin(t * 0.2F + c[0]) * 3;
            for (int r = c[2]; r > 0; r -= 4) {
                int alpha = (int) (14 + 22 * (1 - (float) r / c[2]));
                int color = tier == ChronoTier.NEVERLAST ? hue(t * 0.05F + c[0] / 200F) : c[3];
                circle(g, x + c[0] + (int) drift, y + c[1], r, argb(alpha, color));
            }
        }
    }

    private void aurora(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < imageWidth; i += 2) {
            float wave = Mth.sin(i * 0.05F + t * 0.9F) * 8 + Mth.sin(i * 0.11F - t * 0.6F) * 5;
            int base = y + 18 + (int) wave;
            int color = hue(t * 0.06F + i / 300F);
            g.fillGradient(x + i, base, x + i + 2, base + 26, argb(70, color), argb(0, color));
        }
    }

    private void galaxy(GuiGraphics g, int cx, int cy, float t) {
        float spin = t * (tier == ChronoTier.NEVERLAST ? 0.5F : 0.25F);
        circle(g, cx, cy, 4, argb(150, 0xFFE6C0));
        circle(g, cx, cy, 2, argb(230, 0xFFFFFF));
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 46; i++) {
                float r = 3 + i * 0.55F;
                float a = spin + arm * Mth.PI + i * 0.21F;
                int px = cx + (int) (Mth.cos(a) * r), py = cy + (int) (Mth.sin(a) * r * 0.55F);
                int color = tier == ChronoTier.NEVERLAST ? hue(t * 0.07F + i / 60F) : mix(0xFFE6A0, 0x8070D0, i / 46F);
                g.fill(px, py, px + 1, py + 1, argb(220 - i * 4, color));
            }
        }
    }

    private void drawStars(GuiGraphics g, int x, int y, float t) {
        for (Star s : stars) {
            float twinkle = (Mth.sin(t * s.speed + s.phase) + 1) / 2;
            int alpha = (int) (90 + 165 * twinkle);
            int sx = x + (int) s.x, sy = y + (int) s.y;
            g.fill(sx, sy, sx + s.size, sy + s.size, argb(alpha, s.color));
            if (s.size == 2 && tier.level >= 3 && twinkle > 0.8F) {
                int glint = argb((int) (alpha * 0.6F), s.color);
                g.fill(sx - 2, sy, sx, sy + 1, glint);
                g.fill(sx + 2, sy, sx + 4, sy + 1, glint);
                g.fill(sx, sy - 2, sx + 1, sy, glint);
                g.fill(sx, sy + 2, sx + 1, sy + 4, glint);
            }
        }
    }

    private void shootingStar(GuiGraphics g, int x, int y, float t) {
        float period = tier == ChronoTier.NEVERLAST ? 2.5F : 4.0F;
        float cycle = (t % period) / period;
        if (cycle > 0.35F) return;
        int seed = (int) (t / period);
        Random random = new Random(seed * 31L);
        float sx = 20 + random.nextFloat() * (imageWidth - 60), sy = 10 + random.nextFloat() * 50;
        float p = cycle / 0.35F;
        for (int i = 0; i < 14; i++) {
            float q = p * 40 - i * 1.5F;
            if (q < 0) continue;
            int px = x + (int) (sx + q), py = y + (int) (sy + q * 0.5F);
            int color = tier == ChronoTier.NEVERLAST ? hue(t * 0.2F + i / 14F) : 0xFFF6D0;
            g.fill(px, py, px + 1, py + 1, argb(255 - i * 17, color));
        }
    }

    private void motes(GuiGraphics g, int x, int y, float t) {
        for (int i = 0; i < 18; i++) {
            float px = (i * 37 + t * (6 + i % 5)) % imageWidth;
            float py = imageHeight - ((i * 53 + t * (4 + i % 3) * 3) % imageHeight);
            int alpha = (int) (120 + 100 * Mth.sin(t * 2 + i));
            g.fill(x + (int) px, y + (int) py, x + (int) px + 1, y + (int) py + 2, argb(alpha, hue(t * 0.1F + i / 18F)));
        }
    }

    /** 縁: T1 白銀、T2 金と角飾りと走る光、T3 二重の金と宝石の角、T4 虹色に回る縁と結晶の角 */
    private void frame(GuiGraphics g, int x, int y, int w, int h, float t) {
        int outer = switch (tier) {
            case HIGH_END -> 0xB8C4D0;
            case FUTURE, BEYOND -> 0xE8C060;
            case NEVERLAST -> 0xFFFFFF;
        };
        if (tier == ChronoTier.NEVERLAST) {
            // 縁の 1 画素ずつ色を回す
            int perimeter = 2 * (w + h);
            for (int i = 0; i < perimeter; i += 2) {
                int color = argb(255, hue(t * 0.15F + (float) i / perimeter));
                int[] p = perimeterPoint(i, w, h);
                g.fill(x + p[0], y + p[1], x + p[0] + 2, y + p[1] + 2, color);
            }
        } else {
            g.renderOutline(x, y, w, h, argb(255, outer));
            g.renderOutline(x + 1, y + 1, w - 2, h - 2, argb(110, accent()));
        }
        if (tier.level >= 3) g.renderOutline(x + 3, y + 3, w - 6, h - 6, argb(170, tier == ChronoTier.NEVERLAST ? accent() : 0xE8C060));
        if (tier.level >= 2) {
            // 角飾り
            int c = argb(255, tier == ChronoTier.NEVERLAST ? accent() : 0xFFD166);
            int len = tier.level >= 3 ? 12 : 8;
            int[][] corners = {{x, y, 1, 1}, {x + w - 1, y, -1, 1}, {x, y + h - 1, 1, -1}, {x + w - 1, y + h - 1, -1, -1}};
            for (int[] k : corners) {
                for (int i = 0; i < len; i++) {
                    g.fill(k[0] + i * k[2], k[1], k[0] + i * k[2] + 1, k[1] + 2 * k[3], c);
                    g.fill(k[0], k[1] + i * k[3], k[0] + 2 * k[2], k[1] + i * k[3] + 1, c);
                }
                if (tier.level >= 3) {
                    // 宝石（T3 は脈打つ赤紫、T4 は虹色の結晶）
                    float pulse = (Mth.sin(t * 2.4F) + 1) / 2;
                    int gem = tier == ChronoTier.NEVERLAST ? hue(t * 0.2F + k[0] * 0.01F) : mix(0xC0306A, 0xFF7AD9, pulse);
                    int gx = k[0] + 4 * k[2] - (k[2] < 0 ? 2 : 0), gy = k[1] + 4 * k[3] - (k[3] < 0 ? 2 : 0);
                    g.fill(gx, gy, gx + 3, gy + 3, argb(255, gem));
                    g.fill(gx + 1, gy + 1, gx + 2, gy + 2, argb(255, 0xFFFFFF));
                }
            }
        }
        if (tier == ChronoTier.FUTURE || tier == ChronoTier.BEYOND) {
            // 縁を走る光（タキオンの橙・星の金）
            int perimeter = 2 * (w + h);
            int head = (int) ((t * 80) % perimeter);
            for (int i = 0; i < 18; i++) {
                int[] p = perimeterPoint(Math.floorMod(head - i * 2, perimeter), w, h);
                g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, argb(255 - i * 13, tier == ChronoTier.FUTURE ? 0xFFB347 : 0xFFF2C0));
            }
        }
        // 題の帯
        g.fill(x + 4, y + 4, x + w - 4, y + 17, argb(tier.level >= 3 ? 140 : 100, 0x000000));
        g.fill(x + 4, y + 17, x + w - 4, y + 18, argb(160, accent()));
    }

    private static int[] perimeterPoint(int i, int w, int h) {
        if (i < w) return new int[]{i, 0};
        i -= w;
        if (i < h) return new int[]{w - 1, i};
        i -= h;
        if (i < w) return new int[]{w - 1 - i, h - 1};
        i -= w;
        return new int[]{0, h - 1 - Math.min(i, h - 1)};
    }

    private static void glassSlot(GuiGraphics g, int x, int y, int edge) {
        g.fill(x, y, x + 18, y + 18, 0xB0060A16);
        g.renderOutline(x, y, 18, 18, edge);
        g.fill(x + 1, y + 1, x + 17, y + 2, 0x30FFFFFF);
    }

    private void gauge(GuiGraphics g, int gx, int gy, float t) {
        g.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + GAUGE_H + 1, argb(200, accent()));
        g.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, 0xFF02040A);
        int cap = Math.max(1, menu.capacity());
        int filled = (int) ((long) GAUGE_H * Math.max(0, menu.energy()) / cap);
        int low = switch (tier) {
            case HIGH_END -> 0x2F7FD0;
            case FUTURE -> 0xFF8C1A;
            case BEYOND -> 0xC08020;
            case NEVERLAST -> hue(t * 0.1F + 0.5F);
        };
        int high = tier == ChronoTier.NEVERLAST ? hue(t * 0.1F) : accent();
        if (filled > 0) g.fillGradient(gx, gy + GAUGE_H - filled, gx + GAUGE_W, gy + GAUGE_H, argb(255, high), argb(255, low));
        // 流れる光
        int shine = (int) ((t * 30) % (GAUGE_H + 10)) - 10;
        if (filled > 0 && shine > GAUGE_H - filled) g.fill(gx, gy + shine, gx + GAUGE_W, gy + Math.min(GAUGE_H, shine + 3), 0x50FFFFFF);
    }

    // ---- 種類ごとの表示 ---------------------------------------------------------

    private void machineWidgets(GuiGraphics g, int x, int y, float t) {
        NumberFormat f = NumberFormat.getIntegerInstance();
        int text = argb(255, 0xE6F0FF);
        switch (menu.type()) {
            case LUMINAL_REACTOR -> {
                int gen = menu.extra(0) | (menu.extra(1) << 16);
                float burn = menu.extra(2) / 1000F;
                int cx = x + 130, cy = y + 56;
                float pulse = gen > 0 ? (Mth.sin(t * 4) + 1) / 2 : 0;
                for (int r = 22; r > 0; r -= 3) circle(g, cx, cy, r, argb((int) ((gen > 0 ? 18 : 6) + pulse * 10), 0xFFD166));
                circle(g, cx, cy, 6, argb(255, gen > 0 ? 0xFFF2C0 : 0x404860));
                if (gen > 0) for (int i = 0; i < 8; i++) {
                    float a = t * 1.5F + i * Mth.PI / 4;
                    int ex = cx + (int) (Mth.cos(a) * (10 + pulse * 8)), ey = cy + (int) (Mth.sin(a) * (10 + pulse * 8));
                    g.fill(ex, ey, ex + 2, ey + 2, argb(220, 0xFFE6A0));
                }
                g.fill(x + 56, y + 70, x + 74, y + 72, 0xFF101828);
                g.fill(x + 56, y + 70, x + 56 + (int) (18 * burn), y + 72, argb(255, 0xFFD166));
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.generating", f.format(gen)), x + 28, y + 82, text, false);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.max_generation",
                        f.format(LuminalReactorBlockEntity.generation(tier))), x + 28, y + 24, argb(200, accent()), false);
            }
            case CHRONO_SMELTER -> {
                int lanes = tier.parallel;
                int sum = 0;
                for (ChronoMachineType.SlotSpec spec : menu.type().layout(tier)) {
                    if (spec.role() != ChronoMachineType.Role.INPUT) continue;
                    int p = menu.extra(spec.index());
                    sum += p;
                    if (p > 0) g.fill(x + spec.x(), y + spec.y() + 16, x + spec.x() + p * 16 / 1000, y + spec.y() + 17, argb(255, accent()));
                }
                arrow(g, x + 104, y + 50, sum / Math.max(1, lanes) / 1000F);
                g.drawCenteredString(font, "×" + ChronoSmelterBlockEntity.multiplier(tier), x + 113, y + 38, argb(255, accent()));
            }
            case LUMINAL_ADAPTER -> {
                arrow(g, x + 102, y + 49, menu.extra(0) / 1000F);
                int stage = menu.extra(1), stages = menu.extra(2), batch = menu.extra(3);
                if (stages > 1 && stage > 0) {
                    g.drawCenteredString(font, Component.translatable("gui.automata_valkyries.chrono.stage", stage, stages), x + 112, y + 72, argb(255, accent()));
                    for (int i = 0; i < stages; i++) g.fill(x + 96 + i * 7, y + 84, x + 101 + i * 7, y + 87, argb(255, i < stage ? accent() : 0x303850));
                }
                if (batch > 1) g.drawCenteredString(font, "×" + batch, x + 112, y + 38, text);
            }
            case TACTICAL_ARMORY -> arrow(g, x + 102, y + 49, menu.extra(0) / 1000F);
            case CHARGING_DOCK -> { // 立っている兵姫の数と、送っている電力
                arrow(g, x + 102, y + 49, menu.extra(1) > 0 ? 1.0F : 0.0F);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.dock_status", menu.extra(0), menu.extra(1) * 100), x + 56, y + 80,
                        menu.extra(1) > 0 ? 0xFF6CFF9A : 0xFFB8C4D8, false);
            }
            case CHRONO_FABRICATOR -> {
                int p = menu.extra(0);
                g.fill(x + 22, y + 76, x + 184, y + 78, 0xFF101828);
                g.fill(x + 22, y + 76, x + 22 + 162 * p / 1000, y + 78, argb(255, accent()));
                // 止まっている理由: 進み具合の線の上に文（カードが 2 行まで＝T1〜T3。T4 はカードで埋まるので線の色とツールチップだけ）。
                // 止まっていれば線の地を橙に。題の帯にはティアの印があり重なっていた（2026-10-07）
                int status = menu.extra(2);
                if (status != 0) g.fill(x + 22, y + 76, x + 184, y + 78, 0x80FF9A6A);
                if (ChronoMachineType.cardSlots(tier) <= 18) {
                    g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.fab_status." + status), x + 22, y + 65,
                            status == 0 ? 0xFF6CFF9A : 0xFFFF9A6A, false);
                }
            }
            case TETHER_PYLON -> {
                int cx = x + 132, cy = y + 56;
                for (int i = 0; i < 3; i++) {
                    float r = ((t * 14 + i * 9) % 27);
                    int alpha = (int) (200 * (1 - r / 27));
                    ring(g, cx, cy, (int) r + 4, argb(alpha, accent()));
                }
                g.fill(cx - 2, cy - 10, cx + 2, cy + 10, argb(255, 0xC8D4E0));
                circle(g, cx, cy - 11, 3, argb(255, accent()));
                int sent = menu.extra(1) | (menu.extra(2) << 16);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.range",
                        tier.range < 0 ? Component.translatable("gui.automata_valkyries.chrono.unlimited") : Component.literal(tier.range + " m")), x + 28, y + 24, text, false);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.targets", menu.extra(0)), x + 28, y + 74, text, false);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.sending", f.format(sent), f.format(tier.powerRate)), x + 28, y + 84, text, false);
            }
            case VOID_REPOSITORY -> repositoryGrid(g, x, y);
            case CHRONO_ACCELERATOR -> { // 回る時計の針（速さで速く回る）と、速さ・範囲・動かしている数
                int cx = x + 103, cy = y + 52, speed = menu.extra(0), size = menu.extra(1), count = menu.extra(2);
                boolean on = count > 0;
                for (int r = 24; r > 18; r -= 2) ring(g, cx, cy, r, argb(on ? 150 : 50, accent()));
                for (int i = 0; i < 12; i++) {
                    float a = i * Mth.PI / 6;
                    g.fill(cx + (int) (Mth.cos(a) * 16) - 1, cy + (int) (Mth.sin(a) * 16) - 1, cx + (int) (Mth.cos(a) * 16) + 1, cy + (int) (Mth.sin(a) * 16) + 1, argb(200, 0xC8D4E0));
                }
                float spin = on ? t * (1.0F + speed * 0.4F) : 0.0F;
                for (int k = 0; k < 14; k++) {
                    g.fill(cx + (int) (Mth.cos(spin) * k) - 1, cy + (int) (Mth.sin(spin) * k) - 1, cx + (int) (Mth.cos(spin) * k) + 1, cy + (int) (Mth.sin(spin) * k) + 1, argb(255, accent()));
                    if (k < 9) g.fill(cx + (int) (Mth.cos(spin / 12) * k), cy + (int) (Mth.sin(spin / 12) * k), cx + (int) (Mth.cos(spin / 12) * k) + 1, cy + (int) (Mth.sin(spin / 12) * k) + 1, argb(255, 0xE6F0FF));
                }
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.accel_speed", speed), x + 22, y + 24, text, false);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.accel_range", size, size, size), x + 22, y + 84, text, false);
                g.drawString(font, Component.translatable("gui.automata_valkyries.chrono.accel_count", count), x + 120, y + 84, on ? 0xFF6CFF9A : 0xFFB8C4D8, false);
            }
            case CHRONO_REWINDER -> {
                arrow(g, x + 102, y + 49, menu.extra(0) / 1000F);
                int mode = menu.extra(1);
                if (mode > 0) g.drawCenteredString(font, Component.translatable("gui.automata_valkyries.chrono.rewind_mode." + mode), x + 112, y + 72, argb(255, accent()));
            }
        }
    }

    private void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = 0; i < 48; i++) {
            float a = i * Mth.TWO_PI / 48;
            int px = cx + (int) (Mth.cos(a) * r), py = cy + (int) (Mth.sin(a) * r * 0.6F);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void arrow(GuiGraphics g, int ax, int ay, float progress) {
        int len = 18;
        g.fill(ax, ay + 3, ax + len, ay + 6, 0xFF203048);
        int done = (int) (len * Mth.clamp(progress, 0, 1));
        if (done > 0) g.fillGradient(ax, ay + 3, ax + done, ay + 6, argb(255, accent()), argb(255, 0xFFFFFF));
        for (int i = 0; i < 5; i++) g.fill(ax + len + i, ay + i, ax + len + i + 1, ay + 9 - i, progress >= 1 ? argb(255, accent()) : 0xFF203048);
    }

    private void repositoryGrid(GuiGraphics g, int x, int y) {
        List<VoidRepositoryBlockEntity.Entry> list = menu.repository;
        List<Integer> visible = repoVisible();
        int perPage = REPO_COLS * REPO_ROWS;
        int pages = Math.max(1, (visible.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int edge = argb(120, accent());
        for (int i = 0; i < perPage; i++) {
            int sx = x + REPO_X + (i % REPO_COLS) * 18, sy = y + REPO_Y + (i / REPO_COLS) * 18;
            glassSlot(g, sx - 1, sy - 1, edge);
            int shown = page * perPage + i;
            if (shown >= visible.size()) continue;
            VoidRepositoryBlockEntity.Entry entry = list.get(visible.get(shown));
            if (entry == null) continue; // 空き
            g.renderItem(entry.key, sx, sy);
            String count = compact(entry.count);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.pose().scale(0.5F, 0.5F, 1);
            g.drawString(font, count, (sx + 17) * 2 - font.width(count), (sy + 12) * 2, 0xFFFFFFFF, true);
            int index = visible.get(shown);
            if (index < menu.repositoryStock.length && menu.repositoryStock[index] > 0) {        // 在庫の決まり（緑の数字を左上に）
                g.drawString(font, "▶" + menu.repositoryStock[index], sx * 2, sy * 2, 0xFF6CFF9A, true);
            }
            g.pose().popPose();
        }
        long max = VoidRepositoryBlockEntity.maxTypes(tier);
        int stored = 0;
        for (var e : list) if (e != null) stored++;
        String types = stored + " / " + (max == Integer.MAX_VALUE ? "∞" : Long.toString(max));
        // 左の列（預け入れ口の下）: 種類の数 → 絞り込み → ページ → 送り → 整理（題・預け入れ口と重ならないよう縦に並べる）
        g.drawString(font, types, x + 22, y + REPO_TYPES_Y, argb(255, 0xE6F0FF), false);
        button(g, x + 21, y + REPO_FILTER_Y, Component.translatable("gui.automata_valkyries.chrono.repo_filter." + repoFilter));
        g.drawString(font, (page + 1) + "/" + pages, x + 22, y + REPO_PAGE_Y, argb(255, 0xE6F0FF), false);
        g.drawString(font, "◀", x + 22, y + REPO_ARROW_Y, argb(255, page > 0 ? accent() : 0x404860), false);
        g.drawString(font, "▶", x + 36, y + REPO_ARROW_Y, argb(255, page < pages - 1 ? accent() : 0x404860), false);
        button(g, x + 21, y + REPO_SORT_Y, Component.translatable("gui.automata_valkyries.chrono.repo_sort"));
        // 自動圧縮の切り替え（入は緑）
        g.fill(x + COMPRESS_X, y + SEARCH_Y, x + COMPRESS_X + 26, y + SEARCH_Y + 12, menu.repositoryCompress ? argb(200, 0x1E6A3A) : argb(160, 0x101828));
        g.drawCenteredString(font, Component.translatable("gui.automata_valkyries.chrono.repo_compress"), x + COMPRESS_X + 13, y + SEARCH_Y + 2,
                menu.repositoryCompress ? 0xFF8CFFB0 : 0xFF808898);
    }

    /** 左の列の小さなボタン（幅 26・高さ 11） */
    private void button(GuiGraphics g, int bx, int by, Component text) {
        g.fill(bx, by, bx + 26, by + 11, argb(160, 0x101828));
        g.renderOutline(bx, by, 26, 11, argb(200, accent()));
        g.drawString(font, text, bx + 13 - font.width(text) / 2, by + 2, argb(255, 0xE6F0FF), false);
    }

    private static String compact(long count) {
        if (count < 1000) return Long.toString(count);
        if (count < 1_000_000) return String.format("%.1fk", count / 1000.0);
        if (count < 1_000_000_000L) return String.format("%.1fM", count / 1_000_000.0);
        return String.format("%.1fG", count / 1_000_000_000.0);
    }

    // ---- 空間リンクの印 ---------------------------------------------------------

    private int linkIconX(int i) { return leftPos + 22 + i * 11; }

    private void linkIcons(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int ly = y + ChronoMachineType.LINK_Y;
        g.drawString(font, "⌖", x + 10, ly + 1, argb(255, accent()), false);
        for (int i = 0; i < tier.linkSlots; i++) {
            int ix = linkIconX(i);
            if (i >= menu.linkCount()) {
                g.renderOutline(ix, ly, 10, 10, 0x40FFFFFF);
                continue;
            }
            boolean ok = menu.linkOk(i);
            int color = ok ? accent() : 0xFF5050;
            g.fill(ix, ly, ix + 10, ly + 10, argb(200, 0x000000));
            g.renderOutline(ix, ly, 10, 10, argb(255, color));
            String letter = switch (menu.linkMode(i)) {
                case IN -> "I";
                case OUT -> "O";
                case BOTH -> "B";
            };
            g.drawString(font, letter, ix + 3, ly + 1, argb(255, color), false);
        }
    }

    // ---- 文字・ツールチップ・クリック ---------------------------------------------------

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, argb(255, 0xF0F6FF), true);
        Component tierName = Component.translatable("tier.automata_valkyries." + tier.id);
        g.drawString(font, tierName, imageWidth - 10 - font.width(tierName), titleLabelY, argb(255, accent()), true);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, argb(220, 0xC8D4E8), false);
        int opSpeed = menu.data(ChronoMachineBlockEntity.DATA_OPERATOR_SPEED), opEnergy = menu.data(ChronoMachineBlockEntity.DATA_OPERATOR_ENERGY);
        if (opSpeed > 0 || opEnergy > 0) { // 兵姫のオペレーター（2026-10-29）
            Component op = Component.translatable("gui.automata_valkyries.chrono.operator", opSpeed, opEnergy);
            g.drawString(font, op, imageWidth - 10 - font.width(op), inventoryLabelY, argb(255, 0x6CFF9A), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        NumberFormat f = NumberFormat.getIntegerInstance();
        if (inside(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            if (menu.type() == ChronoMachineType.VOID_REPOSITORY) {                                // リポジトリはテザー・パイロンから受け取るか（クリックで切り替え）
                g.renderComponentTooltip(font, List.of(
                        Component.translatable("gui.automata_valkyries.energy", f.format(menu.energy()), f.format(menu.capacity())),
                        Component.translatable("gui.automata_valkyries.chrono.repo_pylon", Component.translatable(menu.repositoryPylonPower ? "options.on" : "options.off"))
                                .withStyle(menu.repositoryPylonPower ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY),
                        Component.translatable("gui.automata_valkyries.chrono.repo_pylon_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            } else {
                g.renderTooltip(font, Component.translatable("gui.automata_valkyries.energy", f.format(menu.energy()), f.format(menu.capacity())), mouseX, mouseY);
            }
        }
        int ly = topPos + ChronoMachineType.LINK_Y;
        for (int i = 0; i < Math.min(menu.linkCount(), menu.links.size()); i++) {
            if (!inside(mouseX, mouseY, linkIconX(i), ly, 10, 10)) continue;
            ChronoLink link = menu.links.get(i);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.automata_valkyries.chrono.link", i + 1, link.pos().toShortString()));
            lines.add(Component.literal(link.dimension().location().toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("link_mode.automata_valkyries." + menu.linkMode(i).name().toLowerCase()));
            lines.add(Component.translatable(menu.linkOk(i) ? "gui.automata_valkyries.chrono.link_ok" : "gui.automata_valkyries.chrono.link_bad")
                    .withStyle(menu.linkOk(i) ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
            lines.add(Component.translatable("gui.automata_valkyries.chrono.link_help").withStyle(net.minecraft.ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (menu.type() == ChronoMachineType.CHRONO_FABRICATOR && (inside(mouseX, mouseY, leftPos + 22, topPos + 74, 162, 6)
                || (ChronoMachineType.cardSlots(tier) <= 18 && inside(mouseX, mouseY, leftPos + 22, topPos + 63, 162, 11)))) { // 進み具合の線か、状態の文
            g.renderComponentTooltip(font, java.util.List.of(
                    Component.translatable("gui.automata_valkyries.chrono.fab_status." + menu.extra(2)),
                    Component.translatable("gui.automata_valkyries.chrono.fab_hint." + menu.extra(2)).withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.automata_valkyries.chrono.crafts", ChronoMachineType.craftsPerOp(tier), menu.extra(1))), mouseX, mouseY);
        }
        if (menu.type() == ChronoMachineType.VOID_REPOSITORY && inside(mouseX, mouseY, leftPos + COMPRESS_X, topPos + SEARCH_Y, 26, 12)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.automata_valkyries.chrono.repo_compress_tip.0", Component.translatable(menu.repositoryCompress ? "options.on" : "options.off")),
                    Component.translatable("gui.automata_valkyries.chrono.repo_compress_tip.1", VoidRepositoryBlockEntity.COMPRESS_KEEP).withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.translatable("gui.automata_valkyries.chrono.repo_compress_tip.2", VoidRepositoryBlockEntity.COMPRESS_COST,
                            VoidRepositoryBlockEntity.compressPerSecond(tier)).withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
        if (search != null && inside(mouseX, mouseY, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 12)) {
            List<Component> lines = new ArrayList<>();
            for (int k = 0; k < 7; k++) lines.add(Component.translatable("gui.automata_valkyries.chrono.repo_search_help." + k)
                    .withStyle(k == 0 ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (menu.type() == ChronoMachineType.VOID_REPOSITORY) {
            int index = repositoryIndexAt(mouseX, mouseY);
            if (index >= 0) {
                VoidRepositoryBlockEntity.Entry entry = menu.repository.get(index);
                List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(entry.key));
                lines.add(Component.translatable("gui.automata_valkyries.chrono.stored", f.format(entry.count)).withStyle(net.minecraft.ChatFormatting.AQUA));
                int stock = index < menu.repositoryStock.length ? menu.repositoryStock[index] : 0;
                lines.add(stock > 0 ? Component.translatable("gui.automata_valkyries.chrono.repo_stock", stock).withStyle(net.minecraft.ChatFormatting.GREEN)
                        : Component.translatable("gui.automata_valkyries.chrono.repo_stock_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    /** ボタンを押す（番号を 1 バイトに縮めない専用の通信で。{@link com.automatavalkyries.network.MenuButtonPacket}） */
    private void press(int id) {
        com.automatavalkyries.network.ModNetwork.CHANNEL.sendToServer(new com.automatavalkyries.network.MenuButtonPacket(menu.containerId, id));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int repositoryIndexAt(double mx, double my) {
        int rx = (int) mx - leftPos - REPO_X, ry = (int) my - topPos - REPO_Y;
        if (rx < 0 || ry < 0 || rx >= REPO_COLS * 18 || ry >= REPO_ROWS * 18) return -1;
        int shown = page * REPO_COLS * REPO_ROWS + (ry / 18) * REPO_COLS + rx / 18;
        List<Integer> visible = repoVisible();
        if (shown >= visible.size()) return -1;
        int index = visible.get(shown);
        return menu.repository.get(index) == null ? -1 : index;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ly = topPos + ChronoMachineType.LINK_Y;
        for (int i = 0; i < menu.linkCount(); i++) {
            if (!inside(mx, my, linkIconX(i), ly, 10, 10)) continue;
            int id = Screen.hasShiftDown() ? ChronoMenu.BUTTON_REMOVE + i : ChronoMenu.BUTTON_MODE + i;
            menu.clickMenuButton(minecraft.player, id);
            press(id);
            return true;
        }
        if (menu.type() == ChronoMachineType.VOID_REPOSITORY) {
            int index = repositoryIndexAt(mx, my);
            if (index >= 0) {
                int id = button == 1 ? (Screen.hasShiftDown() ? ChronoMenu.BUTTON_STOCK_BACK : ChronoMenu.BUTTON_STOCK) + index // 右クリックで在庫の決まり
                        : ChronoMenu.BUTTON_EXTRACT + index;
                press(id);
                return true;
            }
            if (inside(mx, my, leftPos + COMPRESS_X, topPos + SEARCH_Y, 26, 12)) {
                press(ChronoMenu.BUTTON_COMPRESS);
                return true;
            }
            if (inside(mx, my, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {           // 電力の目盛りをクリック: テザー・パイロンから受け取るかの切り替え
                press(ChronoMenu.BUTTON_PYLON);
                return true;
            }
            if (search != null && inside(mx, my, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, 12)) {
                if (button == 1) search.setValue("");                                            // 右クリックで消す
                setFocused(search);
                search.setFocused(true);
                return true;
            }
            if (search != null) search.setFocused(false);
            if (inside(mx, my, leftPos + 21, topPos + REPO_FILTER_Y, 26, 11)) { repoFilter = (repoFilter + 1) % 3; page = 0; return true; }
            if (inside(mx, my, leftPos + 21, topPos + REPO_SORT_Y, 26, 11)) {
                press(ChronoMenu.BUTTON_SORT);
                return true;
            }
            if (inside(mx, my, leftPos + 20, topPos + REPO_ARROW_Y - 2, 12, 11)) { page = Math.max(0, page - 1); return true; }
            if (inside(mx, my, leftPos + 34, topPos + REPO_ARROW_Y - 2, 12, 11)) { page++; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (menu.type() == ChronoMachineType.VOID_REPOSITORY) {
            page = Math.max(0, page - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
