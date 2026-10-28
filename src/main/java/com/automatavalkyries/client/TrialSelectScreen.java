package com.automatavalkyries.client;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.TrialListPacket;
import com.automatavalkyries.network.TrialStartPacket;
import com.automatavalkyries.trial.Trial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 回想を選ぶ画面（2026-10-27 ユーザー指定「選択画面を作る」）: ルミナルステラの追憶メモリを右クリックすると開く。
 * 左に回想の一覧（クリア済みの印とランク）、右に選んだ回想のボス・条件・いちばん良いランクと最速・ランクの基準。「挑む」で回想の空間へ。
 * 見た目は回想の空間に合わせた古い写真（暗いセピアの地に金の線）。
 */
@OnlyIn(Dist.CLIENT)
public class TrialSelectScreen extends Screen {

    private static final int W = 380, H = 240, ROW = 17, LIST_TOP = 30, LIST_W = 140, LIST_ROWS = 10;
    private static final int GOLD = 0xFFE8C878, GOLD_DIM = 0xFF8A7448, BG = 0xE81C140C, PANEL = 0x70302214, ROW_SEL = 0x90A07840, TEXT = 0xFFE8D8B0, MUTED = 0xFFA89878;

    private TrialListPacket data;
    private int selected, scroll;
    private Button start;

    private TrialSelectScreen(TrialListPacket data) {
        super(Component.translatable("gui.automata_valkyries.trial.title"));
        this.data = data;
        this.selected = Mth.clamp(data.selected(), 0, Trial.ALL.size() - 1);
        this.scroll = Mth.clamp(selected - LIST_ROWS / 2, 0, maxScroll());
    }

    /** 一覧が届いたとき: 開いていれば更新、無ければ開く */
    public static void show(TrialListPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TrialSelectScreen screen) screen.data = packet;
        else mc.setScreen(new TrialSelectScreen(packet));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int maxScroll() {
        return Math.max(0, Trial.ALL.size() - LIST_ROWS);
    }

    @Override
    protected void init() {
        start = addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.trial.start"), b -> {
            ModNetwork.CHANNEL.sendToServer(new TrialStartPacket(selected));
            onClose();
        }).bounds(left() + W - 170, top() + H - 28, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + W - 86, top() + H - 28, 76, 20).build());
    }

    private boolean cleared(int i) {
        return i < data.cleared().length && data.cleared()[i];
    }

    private Trial.Rank rank(int i) {
        int r = i < data.ranks().length ? data.ranks()[i] : 0;
        return Trial.Rank.values()[Mth.clamp(r, 0, Trial.Rank.values().length - 1)];
    }

    private int time(int i) {
        return i < data.times().length ? data.times()[i] : 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, BG);
        g.renderOutline(x, y, W, H, GOLD);
        g.renderOutline(x + 2, y + 2, W - 4, H - 4, GOLD_DIM);
        g.drawCenteredString(font, title, x + W / 2, y + 10, GOLD);
        int cleared = 0;
        for (int i = 0; i < Trial.ALL.size(); i++) if (cleared(i)) cleared++;
        g.drawString(font, Component.translatable("gui.automata_valkyries.trial.progress", cleared, Trial.ALL.size()), x + 10, y + 10, MUTED, false);

        // 一覧
        int lx = x + 8, ly = y + LIST_TOP;
        g.fill(lx, ly, lx + LIST_W, ly + LIST_ROWS * ROW, PANEL);
        for (int row = 0; row < LIST_ROWS; row++) {
            int i = scroll + row;
            if (i >= Trial.ALL.size()) break;
            int ry = ly + row * ROW;
            boolean hover = mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ry && mouseY < ry + ROW;
            if (i == selected) g.fill(lx, ry, lx + LIST_W, ry + ROW, ROW_SEL);
            else if (hover) g.fill(lx, ry, lx + LIST_W, ry + ROW, 0x40A07840);
            Trial t = Trial.ALL.get(i);
            Trial.Rank r = rank(i);
            g.drawString(font, cleared(i) ? "✔" : "・", lx + 4, ry + 5, cleared(i) ? 0xFF80D080 : MUTED, false);
            String name = font.plainSubstrByWidth(t.title().getString(), LIST_W - 34);
            g.drawString(font, name, lx + 14, ry + 5, i == selected ? 0xFFFFF4D8 : TEXT, false);
            g.drawString(font, r.letter(), lx + LIST_W - 12, ry + 5, 0xFF000000 | r.color(), false);
        }
        if (maxScroll() > 0) { // スクロールの位置
            int barH = LIST_ROWS * ROW * LIST_ROWS / Trial.ALL.size();
            int barY = ly + (LIST_ROWS * ROW - barH) * scroll / maxScroll();
            g.fill(lx + LIST_W + 1, barY, lx + LIST_W + 3, barY + barH, GOLD_DIM);
        }

        // 詳しく
        Trial t = Trial.ALL.get(selected);
        int dx = x + LIST_W + 18, dy = y + LIST_TOP, dw = W - LIST_W - 28;
        g.drawString(font, t.title(), dx, dy, GOLD, false);
        dy += 13;
        dy = wrapped(g, Component.translatable("gui.automata_valkyries.trial.boss", t.bossName()), dx, dy, dw, TEXT);
        dy += 3;
        dy = wrapped(g, t.conditions(), dx, dy, dw, MUTED);
        dy += 3;
        dy = wrapped(g, Component.translatable("gui.automata_valkyries.trial.attack", String.format("%.1f", t.bossAttack())), dx, dy, dw, MUTED);
        dy += 8;
        Trial.Rank r = rank(selected);
        g.pose().pushPose();
        g.pose().translate(dx, dy, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, r.letter(), 0, 0, 0xFF000000 | r.color(), true);
        g.pose().popPose();
        g.drawString(font, cleared(selected) ? Component.translatable("gui.automata_valkyries.trial.cleared") : Component.translatable("gui.automata_valkyries.trial.not_cleared"),
                dx + 22, dy, cleared(selected) ? 0xFF80D080 : MUTED, false);
        int best = time(selected);
        g.drawString(font, Component.translatable("gui.automata_valkyries.trial.best", best > 0 ? Trial.clock(best) : "-"), dx + 22, dy + 10, TEXT, false);
        dy += 26;
        dy = wrapped(g, Component.translatable("gui.automata_valkyries.trial.rank_rule", Math.round(Trial.S_TIME * 100), Trial.S_HITS,
                Math.round(Trial.A_TIME * 100), Trial.A_HITS), dx, dy, dw, MUTED);
        wrapped(g, Component.translatable(cleared(selected) ? "gui.automata_valkyries.trial.reward_done" : "gui.automata_valkyries.trial.reward_first"), dx, dy + 2, dw, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 幅 w で折り返して描き、次の行の y を返す */
    private int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(text, w);
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = left() + 8, ly = top() + LIST_TOP;
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + LIST_ROWS * ROW) {
            int i = scroll + (int) ((mouseY - ly) / ROW);
            if (i < Trial.ALL.size()) {
                selected = i;
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F + i * 0.05F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            selected = Mth.clamp(selected + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1), 0, Trial.ALL.size() - 1);
            if (selected < scroll) scroll = selected;
            if (selected >= scroll + LIST_ROWS) scroll = selected - LIST_ROWS + 1;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && start != null) {
            start.onPress();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
