package com.automatavalkyries.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 攻撃ティア 0〜7 の、自作のローマ数字（N I II III IV V VI VII）。フォントを使わず、高さ 7 の点で描く（2026-10-23 ユーザー指定。最初はギリシャ文字だった）。
 * ローマ数字に 0 は無いので、0 は「無し」の N（nulla）とする
 */
public final class ElenorNumeral {

    public static final int HEIGHT = 7;
    private static final int GAP = 1;

    private static final String[] I = {"###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"};
    private static final String[] V = {"#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#..", "..#.."};
    private static final String[] N = {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"};
    private static final String[] TEXT = {"N", "I", "II", "III", "IV", "V", "VI", "VII"};

    private ElenorNumeral() {}

    private static String[] glyph(char c) {
        return c == 'V' ? V : c == 'N' ? N : I;
    }

    /** tier の文字（N・I・II・III・IV・V・VI・VII） */
    public static String text(int tier) {
        return TEXT[Math.max(0, Math.min(7, tier))];
    }

    /** 描いたときの幅（点の数。1 点の大きさを掛けて使う） */
    public static int width(int tier) {
        String text = text(tier);
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += glyph(text.charAt(i))[0].length() + (i > 0 ? GAP : 0);
        }
        return width;
    }

    /** tier の文字を、(x, y) を左上に、1 点を scale 倍の大きさで描く */
    public static void draw(GuiGraphics g, int tier, float x, float y, float scale, int color) {
        String text = text(tier);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int left = 0;
        for (int i = 0; i < text.length(); i++) {
            String[] rows = glyph(text.charAt(i));
            for (int row = 0; row < HEIGHT; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    if (rows[row].charAt(col) == '#') g.fill(left + col, row, left + col + 1, row + 1, color);
                }
            }
            left += rows[0].length() + GAP;
        }
        g.pose().popPose();
    }
}
