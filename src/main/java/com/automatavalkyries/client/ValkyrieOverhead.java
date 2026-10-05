package com.automatavalkyries.client;

import com.automatavalkyries.config.ValkyrieClientConfig;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 戦術兵姫の頭上の表示（SPEC_VALKYRIE_GUI_AI §2.5・§4。段階 3、2026-10-05）。カメラを向く板に、ホログラムのテーマで描く。
 * <ul>
 *   <li>近いとき（設定 overhead.distance、既定 8 ブロック）だけ: 名前＋HP / シールドの細いバー。機能停止中はバーの代わりに「機能停止」</li>
 *   <li>感情アイコン（♥ ！ など）: 名前の右上に短く（ポンと出て、終わりに薄れる）</li>
 *   <li>吹き出し: 名前の上に短く（折り返し、出始めと終わりに薄れる。同じ文はサーバーがチャットにも送る）</li>
 * </ul>
 * 寸法の単位はバニラの名札と同じ 1/40 ブロック。色・寸法は【仮】
 */
@OnlyIn(Dist.CLIENT)
public final class ValkyrieOverhead {

    /** 吹き出し・感情アイコンを出す距離（ブロック） */
    private static final double BUBBLE_DISTANCE = 24.0;
    private static final int BAR_W = 40, BUBBLE_W = 120;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private ValkyrieOverhead() {}

    /** 兵姫のバニラの名札を出すか（頭上の表示が出る距離では出さない。二重にしない） */
    public static boolean suppressNameTag(ValkyrieEntity entity, EntityRenderDispatcher dispatcher) {
        double d = ValkyrieClientConfig.overheadDistance();
        return ValkyrieClientConfig.overheadEnabled() && dispatcher.distanceToSqr(entity) <= d * d;
    }

    public static void render(ValkyrieEntity v, PoseStack pose, MultiBufferSource buffers, float partialTick, EntityRenderDispatcher dispatcher, Font font) {
        Minecraft mc = Minecraft.getInstance();
        if (v.isInvisible() || !Minecraft.renderNames() || v == mc.getCameraEntity()) return;
        double distSq = dispatcher.distanceToSqr(v);
        double near = ValkyrieClientConfig.overheadDistance();
        boolean panel = ValkyrieClientConfig.overheadEnabled() && distSq <= near * near;
        boolean bubble = distSq <= BUBBLE_DISTANCE * BUBBLE_DISTANCE;
        if (!panel && !bubble) return;

        pose.pushPose();
        pose.translate(0.0F, v.getBbHeight() + 0.5F, 0.0F);
        pose.mulPose(dispatcher.cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = pose.last().pose();
        MultiBufferSource quads = buffers; // 文字と交互に描くので、四角は毎回バッファを取り直す
        float time = v.tickCount + partialTick;
        int glow = v.isShutdown() ? ValkyrieHud.DIM : ValkyrieHud.glow(v);

        // ---- 名前とバー（下端が y 0、上へ伸びる）
        float top = 0.0F;
        float halfW = BAR_W / 2.0F;
        if (panel) {
            Component name = v.getDisplayName();
            halfW = Math.max(font.width(name), BAR_W) / 2.0F + 3.0F;
            if (v.isShutdown()) {
                Component tag = Component.translatable("gui.automata_valkyries.valkyrie.overhead.shutdown");
                halfW = Math.max(halfW, font.width(tag) / 2.0F + 3.0F);
                top = -21.0F;
                backing(m, quads, -halfW, top, halfW, 0.0F, glow);
                text(font, name.getVisualOrderText(), 0.0F, -19.0F, ValkyrieHud.DIM, m, buffers);
                boolean blink = Mth.sin(time * 0.25F) < 0;
                text(font, tag.getVisualOrderText(), 0.0F, -9.5F, blink ? 0xFF8A3A3A : ValkyrieHud.BAD, m, buffers);
            } else {
                top = -18.0F;
                backing(m, quads, -halfW, top, halfW, 0.0F, glow);
                text(font, name.getVisualOrderText(), 0.0F, -16.0F, v.isAsleep() ? ValkyrieHud.DIM : ValkyrieHud.TEXT, m, buffers);
                float bx = -BAR_W / 2.0F;
                float hp = ValkyrieHud.ratio(ValkyrieHud.health(v), v.getMaxHealth());
                int hpColor = hp < 0.25F && Mth.sin(time * 0.4F) < 0 ? 0xFFFFB0B8 : ValkyrieHud.HP_COLOR;
                bar(m, quads, bx, -6.0F, BAR_W, 2.0F, hp, hpColor);
                bar(m, quads, bx, -3.0F, BAR_W, 1.0F, ValkyrieHud.ratio(v.getShield(), v.getMaxShield()), ValkyrieHud.SHIELD_COLOR);
            }
        }

        // ---- 感情アイコン（名前の右上。休眠中で何も無ければ薄い Zz）
        if (bubble) emote(v, m, quads, buffers, font, panel ? halfW + 5.0F : 6.0F, top - 4.0F, time, partialTick);

        // ---- 吹き出し（名前の上）
        if (bubble) speech(v, m, quads, buffers, font, top - (panel ? 4.0F : 2.0F), glow, partialTick);
        pose.popPose();
    }

    /** 感情アイコン: 出始めに少し大きく弾み、終わりの 8 Tick で薄れる */
    private static void emote(ValkyrieEntity v, Matrix4f base, MultiBufferSource quads, MultiBufferSource buffers, Font font,
                              float x, float y, float time, float partialTick) {
        ValkyrieEntity.Emote emote = v.getEmote();
        float left = v.getEmoteTicks() - partialTick;
        float alpha;
        float scale;
        if (emote == ValkyrieEntity.Emote.NONE) {
            if (!v.isAsleep() || v.isShutdown()) return;
            emote = ValkyrieEntity.Emote.SLEEP;
            alpha = 0.6F;
            scale = 1.0F;
        } else {
            float age = ValkyrieEntity.EMOTE_DURATION - left;
            alpha = Mth.clamp(left / 8.0F, 0.0F, 1.0F);
            scale = age < 3.0F ? 0.4F + age / 3.0F * 0.9F : age < 6.0F ? 1.3F - (age - 3.0F) / 3.0F * 0.3F : 1.0F;
        }
        if (alpha <= 0.05F) return;
        String glyph = switch (emote) {
            case HEART -> "♥";
            case ALERT -> "!";
            case ANGRY -> "╬";
            case SWEAT -> "；";
            case NOTE -> "♪";
            case QUESTION -> "?";
            case SLEEP -> "Zz";
            default -> "";
        };
        int color = switch (emote) {
            case HEART -> 0xFFFF6E9E;
            case ALERT, QUESTION -> 0xFFFFE14A;
            case ANGRY -> 0xFFFF4A4A;
            case SWEAT -> 0xFF7CC8FF;
            case NOTE -> 0xFF9CFF8A;
            default -> 0xFFB8C4FF;
        };
        float bob = Mth.sin(time * 0.3F) * 1.0F;
        Matrix4f m = new Matrix4f(base).translate(x, y + bob, 0.0F).scale(scale * 1.4F, scale * 1.4F, 1.0F);
        float w = font.width(glyph) / 2.0F + 2.0F;
        quad(m, quads, -w, -6.0F, w, 5.0F, 0.02F, withAlpha(0xC0101624, alpha * 0.75F));
        quad(m, quads, -w, 5.0F, w, 5.6F, 0.01F, withAlpha(color, alpha * 0.8F));
        text(font, Component.literal(glyph).getVisualOrderText(), 0.0F, -4.5F, withAlpha(color, alpha), m, buffers);
    }

    /** 吹き出し: 折り返した文を暗い地と発光線の枠で囲み、下に小さな尾。出始め 4 Tick・終わり 10 Tick で薄れる */
    private static void speech(ValkyrieEntity v, Matrix4f m, MultiBufferSource quads, MultiBufferSource buffers, Font font,
                               float bottom, int glow, float partialTick) {
        String key = v.getSpeech();
        if (key.isEmpty()) return;
        float left = v.getSpeechTicks() - partialTick;
        float age = ValkyrieEntity.SPEECH_DURATION - left;
        float alpha = Math.min(Mth.clamp(age / 4.0F, 0.0F, 1.0F), Mth.clamp(left / 10.0F, 0.0F, 1.0F));
        if (alpha <= 0.05F) return;
        List<FormattedCharSequence> lines = font.split(Component.translatable(ValkyrieEntity.speechKey(key)), BUBBLE_W);
        if (lines.isEmpty()) return;
        int widest = 0;
        for (FormattedCharSequence line : lines) widest = Math.max(widest, font.width(line));
        float hw = widest / 2.0F + 4.0F;
        float tail = 4.0F;
        float y1 = bottom - tail;
        float y0 = y1 - lines.size() * 10.0F - 4.0F;
        float rise = (1.0F - Mth.clamp(age / 4.0F, 0.0F, 1.0F)) * 3.0F; // 出始めに少し浮き上がる
        y0 += rise;
        y1 += rise;
        int bg = withAlpha(0xD0101624, alpha);
        int line = withAlpha(glow, alpha);
        int dim = withAlpha(glow, alpha * 0.45F);
        quad(m, quads, -hw, y0, hw, y1, 0.02F, bg);
        // 尾（3 段の階段で三角に見せる）
        for (int i = 0; i < 3; i++) {
            float s = 3.0F - i;
            quad(m, quads, -s, y1 + i * 1.3F, s, y1 + (i + 1) * 1.3F, 0.02F, bg);
        }
        // 枠: 細い線と角の明るい線
        quad(m, quads, -hw, y0, hw, y0 + 0.5F, 0.01F, dim);
        quad(m, quads, -hw, y1 - 0.5F, hw, y1, 0.01F, dim);
        quad(m, quads, -hw, y0, -hw + 0.5F, y1, 0.01F, dim);
        quad(m, quads, hw - 0.5F, y0, hw, y1, 0.01F, dim);
        corners(m, quads, -hw, y0, hw, y1, line);
        float ty = y0 + 3.0F;
        int color = withAlpha(ValkyrieHud.TEXT, alpha);
        for (FormattedCharSequence l : lines) {
            text(font, l, 0.0F, ty, color, m, buffers);
            ty += 10.0F;
        }
    }

    // ------------------------------------------------------------------ 描く部品

    /** 名前の板: 暗い半透明の地、細い枠、角の明るい線 */
    private static void backing(Matrix4f m, MultiBufferSource quads, float x0, float y0, float x1, float y1, int glow) {
        quad(m, quads, x0, y0, x1, y1, 0.02F, ValkyrieHud.BG);
        int dim = (glow & 0x00FFFFFF) | 0x50000000;
        quad(m, quads, x0, y0, x1, y0 + 0.5F, 0.01F, dim);
        quad(m, quads, x0, y1 - 0.5F, x1, y1, 0.01F, dim);
        corners(m, quads, x0, y0, x1, y1, glow);
    }

    private static void corners(Matrix4f m, MultiBufferSource quads, float x0, float y0, float x1, float y1, int color) {
        float c = 4.0F, t = 0.75F;
        quad(m, quads, x0, y0, x0 + c, y0 + t, 0.005F, color);
        quad(m, quads, x0, y0, x0 + t, y0 + c, 0.005F, color);
        quad(m, quads, x1 - c, y0, x1, y0 + t, 0.005F, color);
        quad(m, quads, x1 - t, y0, x1, y0 + c, 0.005F, color);
        quad(m, quads, x0, y1 - t, x0 + c, y1, 0.005F, color);
        quad(m, quads, x0, y1 - c, x0 + t, y1, 0.005F, color);
        quad(m, quads, x1 - c, y1 - t, x1, y1, 0.005F, color);
        quad(m, quads, x1 - t, y1 - c, x1, y1, 0.005F, color);
    }

    private static void bar(Matrix4f m, MultiBufferSource quads, float x, float y, float w, float h, float ratio, int color) {
        quad(m, quads, x, y, x + w, y + h, 0.01F, ValkyrieHud.TRACK);
        float fill = w * ratio;
        if (fill <= 0.0F) return;
        quad(m, quads, x, y, x + fill, y + h, 0.005F, color);
        quad(m, quads, x + Math.max(0.0F, fill - 0.75F), y, x + fill, y + h, 0.0F, 0xFFFFFFFF);
    }

    /** z は奥へ（地 0.02、枠・溝 0.01、上に乗る物 0）。文字は z 0 */
    private static void quad(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    /** 中央揃えの文字（光っているように明るさは最大） */
    private static void text(Font font, FormattedCharSequence text, float cx, float y, int color, Matrix4f m, MultiBufferSource buffers) {
        font.drawInBatch(text, cx - font.width(text) / 2.0F, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }

    /** 色の透明度に掛ける（文字は透明度が 4 未満だと不透明に扱われるので 8 以上にする） */
    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp(Math.round((argb >>> 24) * alpha), 8, 255);
        return (argb & 0x00FFFFFF) | a << 24;
    }
}
