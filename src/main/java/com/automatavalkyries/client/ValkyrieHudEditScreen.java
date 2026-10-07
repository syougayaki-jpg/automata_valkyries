package com.automatavalkyries.client;

import com.automatavalkyries.config.ValkyrieClientConfig;
import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 戦術兵姫の HUD の配置の画面（2026-10-07 ユーザー指定「左側の戦術兵姫の UI を縮小拡大・移動・表示非表示にできるように」）。
 * HUD をドラッグで動かし、ホイールで大きさを変える。近い画面の角へ寄せて保存する（画面の大きさが変わっても同じ角に残る）。
 * 開くのは既定の J キー（{@link TacticalKeys#HUD_EDIT}）。表示・非表示は既定の H キー（{@link TacticalKeys#HUD_TOGGLE}）かこの画面のボタン
 */
public class ValkyrieHudEditScreen extends Screen {

    private boolean enabled;
    private float scale;
    /** HUD の左上（画面の GUI px） */
    private int hudX, hudY;
    private boolean dragging;
    private double grabX, grabY;
    private Button toggle;

    public ValkyrieHudEditScreen() {
        super(Component.translatable("screen.automata_valkyries.hud_edit"));
    }

    @Override
    protected void init() {
        enabled = ValkyrieClientConfig.hudEnabled();
        scale = (float) ValkyrieClientConfig.hudScale();
        int[] at = ValkyrieHud.place(width, height, count(), ValkyrieClientConfig.hudAnchor(), ValkyrieClientConfig.hudX(), ValkyrieClientConfig.hudY(), scale);
        hudX = at[0];
        hudY = at[1];
        int cx = width / 2, by = height - 28;
        toggle = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            enabled = !enabled;
            b.setMessage(toggleLabel());
        }).bounds(cx - 154, by, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.automata_valkyries.hud_edit.reset_scale"), b -> setScale(1.0F))
                .bounds(cx - 76, by, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.automata_valkyries.hud_edit.reset_pos"), b -> {
            hudX = 4;
            hudY = 4;
        }).bounds(cx + 2, by, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 80, by, 74, 20).build());
    }

    private Component toggleLabel() {
        return Component.translatable(enabled ? "screen.automata_valkyries.hud_edit.shown" : "screen.automata_valkyries.hud_edit.hidden");
    }

    private List<ValkyrieEntity> followers() {
        return ValkyrieHud.followers(Minecraft.getInstance());
    }

    private int count() {
        return Math.max(1, followers().size());
    }

    private int boxW() {
        return Math.round(ValkyrieHud.W * scale);
    }

    private int boxH() {
        return Math.round(ValkyrieHud.height(count()) * scale);
    }

    private void setScale(float s) {
        // 大きさを変えても HUD の中心は動かさない
        float cx = hudX + boxW() / 2.0F, cy = hudY + boxH() / 2.0F;
        scale = Mth.clamp(Math.round(s * 20.0F) / 20.0F, 0.4F, 3.0F);
        hudX = Math.round(cx - boxW() / 2.0F);
        hudY = Math.round(cy - boxH() / 2.0F);
        clampBox();
    }

    private void clampBox() {
        hudX = Mth.clamp(hudX, 0, Math.max(0, width - boxW()));
        hudY = Mth.clamp(hudY, 0, Math.max(0, height - boxH()));
    }

    private boolean over(double mx, double my) {
        return mx >= hudX && mx < hudX + boxW() && my >= hudY && my < hudY + boxH();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && over(mx, my)) {
            dragging = true;
            grabX = mx - hudX;
            grabY = my - hudY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            hudX = (int) Math.round(mx - grabX);
            hudY = (int) Math.round(my - grabY);
            clampBox();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        setScale(scale + (float) delta * 0.05F);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x60000000);
        Minecraft mc = Minecraft.getInstance();
        List<ValkyrieEntity> list = followers();
        int w = boxW(), h = boxH();
        if (!list.isEmpty()) {
            ValkyrieHud.draw(g, font, list, hudX, hudY, scale, mc.player != null ? mc.player.tickCount + partialTick : 0.0F);
        } else { // 追従中の兵姫がいなければ枠だけ
            g.fill(hudX, hudY, hudX + w, hudY + h, 0x40102030);
            g.drawCenteredString(font, Component.translatable("screen.automata_valkyries.hud_edit.empty"), hudX + w / 2, hudY + h / 2 - 4, 0xFF9BF5FF);
        }
        if (!enabled) g.fill(hudX, hudY, hudX + w, hudY + h, 0x99000000); // 非表示にする HUD は暗く
        int edge = dragging || over(mx, my) ? 0xFFFFFFFF : 0xFF3BE8FF;
        g.renderOutline(hudX - 1, hudY - 1, w + 2, h + 2, edge);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.automata_valkyries.hud_edit.help"), width / 2, 24, 0xFFB0B8C8);
        g.drawCenteredString(font, Component.translatable("screen.automata_valkyries.hud_edit.scale", Math.round(scale * 100)), width / 2, height - 42, 0xFF9BF5FF);
        super.render(g, mx, my, partialTick);
    }

    /** 閉じるときに、HUD の中心に近い画面の角へ寄せて保存する */
    @Override
    public void removed() {
        int w = boxW(), h = boxH();
        boolean right = hudX + w / 2 > width / 2, bottom = hudY + h / 2 > height / 2;
        ValkyrieClientConfig.Anchor anchor = right
                ? (bottom ? ValkyrieClientConfig.Anchor.BOTTOM_RIGHT : ValkyrieClientConfig.Anchor.TOP_RIGHT)
                : (bottom ? ValkyrieClientConfig.Anchor.BOTTOM_LEFT : ValkyrieClientConfig.Anchor.TOP_LEFT);
        int dx = right ? width - w - hudX : hudX;
        int dy = bottom ? height - h - hudY : hudY;
        ValkyrieClientConfig.saveHud(enabled, anchor, dx, dy, scale);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
