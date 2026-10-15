package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * メメントモリの大技「死蝶終葬『メメント・モリ』」の専用ロゴ（2026-10-15 ユーザー指定「専用ロゴを作って表示して」。画像は tools/gen_memento_logo.py）。
 * 時間割（大技の始まりから。Tick）:
 * <ul>
 *   <li>0〜8: 画面を横切る光の線とともに、横に引き伸ばされた形から縮んで現れる</li>
 *   <li>8〜60: 画面の上寄りの真ん中に大きく。下に「最後の舞踏――60 秒、耐え抜け」</li>
 *   <li>60〜75: 右上へ小さくなりながら移る</li>
 *   <li>75〜終わり: 右上に小さく残り、下に残りの秒数。終わりの 10 Tick で消える。メメントモリが倒れたら 10 Tick で消える</li>
 * </ul>
 */
public final class MementoSpellLogo {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/gui/memento_spell_logo.png");
    private static final int TW = 1024, TH = 320;
    private static final float APPEAR = 8, HOLD = 60, MOVE = 15;
    /** 今見せている大技の始まりの Tick と、メメントモリが倒れた・消えた時の経過（-1 = まだ） */
    private static int shownSpell = Integer.MIN_VALUE;
    private static float goneAt = -1;
    private static boolean filtered;

    private MementoSpellLogo() {
    }

    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("memento_spell_logo", MementoSpellLogo::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        MementoMoriEntity mori = MementoMoriEntity.lastSpellCaster();
        Minecraft mc = Minecraft.getInstance();
        if (mori == null || mc.level == null || mori.level() != mc.level || mc.options.hideGui) return;
        float t = mori.tickCount - mori.spellTick + partialTick;
        if (t < 0 || t > MementoMoriEntity.SPELL_TICKS) return;
        if (shownSpell != mori.spellTick) {
            shownSpell = mori.spellTick;
            goneAt = -1;
        }
        if (goneAt < 0 && (mori.isRemoved() || mori.isDefeatedSynced())) goneAt = t;
        float fade = goneAt >= 0 ? 1.0F - (t - goneAt) / 10.0F : Math.min(1.0F, (MementoMoriEntity.SPELL_TICKS - t) / 10.0F);
        if (fade <= 0) return;
        if (!filtered) { // 縮めて描くので、なめらかに
            mc.getTextureManager().getTexture(TEX).setFilter(true, false);
            filtered = true;
        }

        float bigW = Math.min(sw * 0.8F, 420.0F), smallW = Math.min(sw * 0.3F, 150.0F);
        float bigX = sw / 2.0F, bigY = sh * 0.28F;
        float smallX = sw - smallW / 2.0F - 6.0F, smallY = 6.0F + smallW * TH / TW / 2.0F;
        float k = Mth.clamp((t - HOLD) / MOVE, 0.0F, 1.0F);
        k = k * k * (3 - 2 * k);
        float w = Mth.lerp(k, bigW, smallW), cx = Mth.lerp(k, bigX, smallX), cy = Mth.lerp(k, bigY, smallY);
        float h = w * TH / TW;
        float sx = 1.0F, sy = 1.0F, alpha = fade;
        if (t < APPEAR) { // 横に引き伸ばされた形から縮んで現れる
            float a = t / APPEAR, inv = 1.0F - a;
            sx = 1.0F + 1.5F * inv * inv;
            sy = 1.0F - 0.6F * inv;
            alpha *= a;
            int line = (int) (255 * inv * fade);
            if (line > 4) g.fill(0, (int) cy - 1, sw, (int) cy + 1, line << 24 | 0xFFE8FF);
        }
        float pulse = t < HOLD ? 1.0F + 0.015F * Mth.sin(t * 0.25F) : 1.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(w / TW * sx * pulse, h / TH * sy * pulse, 1.0F);
        g.blit(TEX, -TW / 2, -TH / 2, 0.0F, 0.0F, TW, TH, TW, TH);
        g.pose().popPose();
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (t >= 10 && t < HOLD + 6) { // 名の下の添え書き
            float a = Math.min(1.0F, (t - 10) / 8.0F) * Math.min(1.0F, (HOLD + 6 - t) / 6.0F) * fade;
            text(g, Component.translatable("message.automata_valkyries.memento_mori.spell_sub"), (int) bigX, (int) (bigY + h / 2 + 4), 0xF4E8FF, a);
        }
        if (t >= HOLD + MOVE) { // 残りの秒数
            float left = Math.max(0.0F, (MementoMoriEntity.SPELL_TICKS - t) / 20.0F);
            text(g, Component.translatable("message.automata_valkyries.memento_mori.spell_time", String.format("%.1f", left)),
                    (int) smallX, (int) (smallY + h / 2 + 2), left < 5 ? 0xFF8CEB : 0xE8D0FF, fade);
        }
    }

    private static void text(GuiGraphics g, Component c, int x, int y, int rgb, float alpha) {
        int a = (int) (255 * Mth.clamp(alpha, 0.0F, 1.0F));
        if (a < 8) return; // 透けすぎると文字の描画は不透明に戻すので描かない
        g.drawCenteredString(Minecraft.getInstance().font, c, x, y, a << 24 | rgb);
    }
}
