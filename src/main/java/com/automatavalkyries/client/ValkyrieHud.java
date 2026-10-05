package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.ValkyrieClientConfig;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.joml.Quaternionf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 追従中の兵姫の HUD（SPEC_VALKYRIE_GUI_AI §2.5。段階 3、2026-10-05）。画面の角に最大 4 体を縦に並べ、
 * 顔アイコン＋HP / シールド / 電力の細いバー。ホログラムのテーマ（暗い半透明の地に、コアの色の発光線と角ばった枠）。
 * 表示・位置は {@link ValkyrieClientConfig}。色・寸法は【仮】
 */
@OnlyIn(Dist.CLIENT)
public final class ValkyrieHud {

    public static final String OVERLAY_ID = "valkyrie_hud";
    static final int BG = 0xB0101624, TEXT = 0xFFE0ECF8, DIM = 0xFF7A8698, BAD = 0xFFFF6A6A,
            HP_COLOR = 0xFFFF5A6E, SHIELD_COLOR = 0xFF59C8FF, POWER_COLOR = 0xFFFFD45A, TRACK = 0x30FFFFFF;
    private static final int MAX = 4, W = 150, H = 46, GAP = 3, FACE = 36;

    private ValkyrieHud() {}

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), OVERLAY_ID, ValkyrieHud::render);
    }

    // ------------------------------------------------------------------ 共通（メニュー画面・頭上と同じ色と枠）

    /** コアの色（発光線）【仮】。コアはメニューを開いたときにだけ同期されるので、それまでは既定の水色 */
    public static int glow(ValkyrieEntity v) {
        ItemStack core = v.getCore();
        if (core.is(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get())) return 0xFFFFF4C8;
        if (core.is(ModWeapons.CORE_CHRONO_GENESIS.get())) return 0xFFFFC24A;
        if (core.is(ModWeapons.CORE_SINGULARITY.get())) return 0xFFC07CFF;
        if (core.is(ModWeapons.CORE_HIGH_DENSITY.get())) return 0xFF4A8CFF;
        return 0xFF3BE8FF;
    }

    /** 角ばった枠: 細い線と、角だけ明るい短い線 */
    static void frame(GuiGraphics g, int x, int y, int w, int h, int color, int corner) {
        int dim = (color & 0x00FFFFFF) | 0x50000000;
        g.renderOutline(x, y, w, h, dim);
        int c = corner;
        g.fill(x, y, x + c, y + 1, color);
        g.fill(x, y, x + 1, y + c, color);
        g.fill(x + w - c, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + c, color);
        g.fill(x, y + h - 1, x + c, y + h, color);
        g.fill(x, y + h - c, x + 1, y + h, color);
        g.fill(x + w - c, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - c, x + w, y + h, color);
    }

    /** HP は getter を通さず実際の値（保護の読み替え・他 MOD の嘘を避ける） */
    static float health(ValkyrieEntity v) {
        float hp = DirectRead.health(v);
        return Float.isFinite(hp) ? Math.max(hp, 0.0F) : 0.0F;
    }

    static float ratio(float value, float max) {
        return max <= 0.0F ? 0.0F : Mth.clamp(value / max, 0.0F, 1.0F);
    }

    /**
     * 顔アイコン: その兵姫のモデルそのものを正面から描き、頭のまわりだけ切り取る（2026-10-05 ユーザー指定「忠実に正面からの顔を再現して」。
     * 描いた絵の顔は可愛くないとのことでやめた）。表情・瞬き・コア・髪のなびきもモデルのまま。背の飾り・頭上の表示・手の物は描かない。
     * 頭の中心は足元から {@link #HEAD_CENTER} ブロック（頭の骨のピボット 21.3px＋頭の半分）、{@link #HEAD_SPAN} ブロックが枠いっぱい
     */
    static void drawFace(GuiGraphics g, ValkyrieEntity v, int x, int y, int size, boolean grey) {
        int scale = Math.round(size / HEAD_SPAN);
        float body = v.yBodyRot, bodyO = v.yBodyRotO, yaw = v.getYRot(), yawO = v.yRotO, pitch = v.getXRot(), pitchO = v.xRotO,
                head = v.yHeadRot, headO = v.yHeadRotO;
        v.yBodyRot = v.yBodyRotO = 180.0F;
        v.setYRot(180.0F);
        v.yRotO = 180.0F;
        v.setXRot(0.0F);
        v.xRotO = 0.0F;
        v.yHeadRot = v.yHeadRotO = 180.0F;
        g.enableScissor(x, y, x + size, y + size);
        ValkyrieRenderer.portraitPass = true;
        try {
            InventoryScreen.renderEntityInInventory(g, x + size / 2, y + size / 2 + Math.round(HEAD_CENTER * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), null, v);
        } finally {
            ValkyrieRenderer.portraitPass = false;
            g.disableScissor();
            v.yBodyRot = body;
            v.yBodyRotO = bodyO;
            v.setYRot(yaw);
            v.yRotO = yawO;
            v.setXRot(pitch);
            v.xRotO = pitchO;
            v.yHeadRot = head;
            v.yHeadRotO = headO;
        }
        if (grey) g.fill(x, y, x + size, y + size, 0x99202430); // 機能停止は暗く
    }

    /** 足元から頭の中心までの高さ・枠いっぱいに映す幅（ブロック） */
    static final float HEAD_CENTER = 1.50F, HEAD_SPAN = 0.46F;

    // ------------------------------------------------------------------ HUD

    /** 持ち主が自分で、追従している兵姫（読み込まれている範囲。id 順に最大 4 体） */
    static List<ValkyrieEntity> followers(Minecraft mc) {
        List<ValkyrieEntity> list = new ArrayList<>();
        if (mc.level == null || mc.player == null) return list;
        UUID me = mc.player.getUUID();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ValkyrieEntity v && !v.isRemoved() && me.equals(v.getOwnerUUID()) && v.getMode() == ValkyrieEntity.Mode.FOLLOW) list.add(v);
        }
        list.sort(Comparator.comparingInt(Entity::getId));
        return list.size() > MAX ? list.subList(0, MAX) : list;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (!ValkyrieClientConfig.hudEnabled() || mc.options.hideGui || mc.options.renderDebug || mc.player == null) return;
        List<ValkyrieEntity> list = followers(mc);
        if (list.isEmpty()) return;
        int total = list.size() * H + (list.size() - 1) * GAP;
        ValkyrieClientConfig.Anchor anchor = ValkyrieClientConfig.hudAnchor();
        boolean right = anchor == ValkyrieClientConfig.Anchor.TOP_RIGHT || anchor == ValkyrieClientConfig.Anchor.BOTTOM_RIGHT;
        boolean bottom = anchor == ValkyrieClientConfig.Anchor.BOTTOM_LEFT || anchor == ValkyrieClientConfig.Anchor.BOTTOM_RIGHT;
        int x = right ? screenWidth - W - ValkyrieClientConfig.hudX() : ValkyrieClientConfig.hudX();
        int y = bottom ? screenHeight - total - ValkyrieClientConfig.hudY() : ValkyrieClientConfig.hudY();
        x = Mth.clamp(x, 0, Math.max(0, screenWidth - W));
        y = Mth.clamp(y, 0, Math.max(0, screenHeight - total));
        float time = mc.player.tickCount + partialTick;
        for (ValkyrieEntity v : list) {
            entry(g, mc.font, v, x, y, time);
            y += H + GAP;
        }
    }

    /** 1 体分: 枠つきの顔、名前と状態、HP（太め）・シールド・電力の細いバー */
    private static void entry(GuiGraphics g, Font font, ValkyrieEntity v, int x, int y, float time) {
        boolean down = v.isShutdown();
        boolean asleep = v.isAsleep();
        int glow = down ? DIM : glow(v);
        int line = (glow & 0x00FFFFFF) | 0x80000000;
        g.fill(x, y, x + W, y + H, BG);
        frame(g, x, y, W, H, glow, 5);
        // 顔
        int fx = x + 4, fy = y + 5;
        g.fill(fx - 1, fy - 1, fx + FACE + 1, fy + FACE + 1, 0xC00B0F17);
        drawFace(g, v, fx, fy, FACE, down);
        g.renderOutline(fx - 1, fy - 1, FACE + 2, FACE + 2, line);
        if (asleep && !down) {
            float bob = Mth.sin(time * 0.15F) * 1.5F;
            g.pose().pushPose();
            g.pose().translate(0, bob, 0);
            g.drawString(font, "z", fx + FACE - 4, fy - 2, POWER_COLOR, true);
            g.pose().popPose();
        }
        // 名前と状態
        int tx = fx + FACE + 5, tw = x + W - 4 - tx;
        Component tag = down ? Component.translatable("gui.automata_valkyries.valkyrie.hud.shutdown")
                : asleep ? Component.translatable("gui.automata_valkyries.valkyrie.hud.asleep") : null;
        int tagW = tag == null ? 0 : font.width(tag) + 3;
        FormattedCharSequence name = clip(font, v.getDisplayName(), tw - tagW);
        g.drawString(font, name, tx, y + 4, down ? DIM : TEXT, false);
        if (tag != null) {
            boolean blink = down && Mth.sin(time * 0.25F) < 0;
            g.drawString(font, tag, x + W - 4 - font.width(tag), y + 4, down ? (blink ? 0xFF8A3A3A : BAD) : POWER_COLOR, false);
        }
        // シールド・HP・バッテリー: 小さな印・細いバー・デジタルの 7 セグの数字（2026-10-05 ユーザー指定「近未来にデジタルセグのような数字で」）
        int digitsW = SEG_W * 4 + 5; // 4 桁か、3 桁と %
        int barX = tx + 9, barW = x + W - 5 - digitsW - 3 - barX;
        float hpValue = health(v), hp = ratio(hpValue, v.getMaxHealth());
        boolean hpBlink = hp < 0.25F && Mth.sin(time * 0.4F) < 0;
        int energyPct = Math.min(100, (int) ((long) v.getEnergy() * 100 / ValkyrieEntity.MAX_ENERGY));
        int[] rowY = {y + 16, y + 26, y + 36};
        int[] colors = {down ? DIM : SHIELD_COLOR, down ? DIM : hpBlink ? 0xFFFFB0B8 : HP_COLOR, down ? DIM : POWER_COLOR};
        float[] ratios = {ratio(v.getShield(), v.getMaxShield()), hp, ratio(v.getEnergy(), ValkyrieEntity.MAX_ENERGY)};
        int[] values = {(int) Math.ceil(v.getShield()), (int) Math.ceil(hpValue), energyPct};
        for (int i = 0; i < 3; i++) {
            int ry = rowY[i], c = colors[i];
            icon(g, i, tx, ry + 1, c);
            bar(g, barX, ry + 3, barW, 2, ratios[i], c);
            sevenSeg(g, x + W - 5, ry, Math.min(values[i], 9999), i == 2, c);
        }
        // 発光線の差し色（左端の縦線）
        g.fill(x + 1, y + 4, x + 2, y + H - 4, line);
    }

    // ------------------------------------------------------------------ 7 セグの数字

    /** 1 桁の大きさ（幅・高さ、GUI の px）と桁の間 */
    static final int SEG_W = 5, SEG_H = 7, SEG_GAP = 1;
    /** 0〜9 の点く画: a(上) b(右上) c(右下) d(下) e(左下) f(左上) g(中) */
    private static final int[] SEGMENTS = {0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011};

    /**
     * 右端 right にそろえて、value を 7 セグの数字で描く（percent なら後ろに %）。点いていない画も薄く出し（液晶の地）、
     * 点いた画には薄い光のにじみを重ねる。上の桁の 0 は出さず、地だけ（4 桁ぶん）
     */
    static void sevenSeg(GuiGraphics g, int right, int y, int value, boolean percent, int color) {
        int ghost = (color & 0x00FFFFFF) | 0x22000000, bloom = (color & 0x00FFFFFF) | 0x30000000;
        int x = right;
        if (percent) {
            x -= 5;
            // %: 左上と右下の点と斜めの線
            g.fill(x, y, x + 1, y + 1, color);
            g.fill(x + 3, y + 6, x + 4, y + 7, color);
            for (int k = 0; k < 5; k++) g.fill(x + 3 - (k * 3 / 4), y + 1 + k, x + 4 - (k * 3 / 4), y + 2 + k, color);
            x -= SEG_GAP;
        }
        int digits = percent ? 3 : 4;
        String text = Integer.toString(Math.max(0, value));
        for (int i = 0; i < digits; i++) {
            x -= SEG_W;
            int idx = text.length() - 1 - i;
            int mask = idx >= 0 ? SEGMENTS[text.charAt(idx) - '0'] : 0;
            digit(g, x, y, 0b1111111, ghost); // 液晶の地
            if (mask != 0) {
                digit(g, x - 1, y, mask, bloom); // にじみ（左右に 1px ずらして薄く）
                digit(g, x + 1, y, mask, bloom);
                digit(g, x, y, mask, color);
            }
            x -= SEG_GAP;
        }
    }

    /** 1 桁（SEG_W × SEG_H。横の画は角を落として 7 セグらしく） */
    private static void digit(GuiGraphics g, int x, int y, int mask, int color) {
        int w = SEG_W, h = SEG_H, mid = h / 2;
        if ((mask & 0b1000000) != 0) g.fill(x + 1, y, x + w - 1, y + 1, color);               // a
        if ((mask & 0b0100000) != 0) g.fill(x + w - 1, y + 1, x + w, y + mid, color);         // b
        if ((mask & 0b0010000) != 0) g.fill(x + w - 1, y + mid + 1, x + w, y + h - 1, color); // c
        if ((mask & 0b0001000) != 0) g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);       // d
        if ((mask & 0b0000100) != 0) g.fill(x, y + mid + 1, x + 1, y + h - 1, color);         // e
        if ((mask & 0b0000010) != 0) g.fill(x, y + 1, x + 1, y + mid, color);                 // f
        if ((mask & 0b0000001) != 0) g.fill(x + 1, y + mid, x + w - 1, y + mid + 1, color);   // g
    }

    /** 行の印（5×5）: 0 = シールド（盾の形）、1 = HP（十字）、2 = バッテリー（電池） */
    private static void icon(GuiGraphics g, int kind, int x, int y, int c) {
        switch (kind) {
            case 0 -> { // 盾: 上が平らで下がすぼまる
                g.fill(x, y, x + 5, y + 3, c);
                g.fill(x + 1, y + 3, x + 4, y + 4, c);
                g.fill(x + 2, y + 4, x + 3, y + 5, c);
                g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF101624);
            }
            case 1 -> { // 十字
                g.fill(x + 2, y, x + 3, y + 5, c);
                g.fill(x, y + 2, x + 5, y + 3, c);
                g.fill(x + 1, y + 1, x + 4, y + 4, c);
            }
            default -> { // 電池: 枠と端子
                g.renderOutline(x, y + 1, 5, 4, c);
                g.fill(x + 5, y + 2, x + 6, y + 4, c);
                g.fill(x + 1, y + 2, x + 3, y + 4, c);
            }
        }
    }

    /** 細いバー: 薄い溝に色を満たし、先端だけ明るく光らせる */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float ratio, int color) {
        g.fill(x, y, x + w, y + h, TRACK);
        int fill = Math.round(w * ratio);
        if (fill <= 0) return;
        g.fill(x, y, x + fill, y + h, color);
        g.fill(x + fill - 1, y, x + fill, y + h, 0xFFFFFFFF);
    }

    /** 幅に収まらない名前は「…」で切る */
    private static FormattedCharSequence clip(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        String ellipsis = "…";
        var head = font.substrByWidth(text, Math.max(0, width - font.width(ellipsis)));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(head, net.minecraft.network.chat.FormattedText.of(ellipsis)));
    }
}
