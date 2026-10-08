package com.automatavalkyries.client;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.StorageActionPacket;
import com.automatavalkyries.network.StorageListPacket;
import com.automatavalkyries.network.StorageListPacket.Row;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 兵姫の収納テレポーターの画面（2026-10-09 ユーザー指定）: 近くの自分の兵姫と、しまった兵姫を一覧で見せ、選んでしまう・出す。
 * 見た目は救済通信機と同じホログラム（暗い半透明の地にシアンの発光線）
 */
@OnlyIn(Dist.CLIENT)
public class ValkyrieStorageScreen extends Screen {

    private static final int W = 300, H = 210, ROW = 22, LIST_TOP = 34, LIST_ROWS = 6;
    private static final int GLOW = 0xFF3BE8FF, GLOW_DIM = 0xFF1A7C8C, BG = 0xE0101624, ROW_BG = 0x60202B44, ROW_SEL = 0x803BE8FF;

    private StorageListPacket data;
    private int scroll;
    @Nullable private UUID selected;
    private Button action;

    private ValkyrieStorageScreen(StorageListPacket data) {
        super(Component.translatable("gui.automata_valkyries.valkyrie_storage.title"));
        this.data = data;
    }

    /** 一覧が届いたとき: 開いていれば更新、無ければ開く */
    public static void show(StorageListPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ValkyrieStorageScreen screen) {
            screen.data = packet;
            screen.scroll = Mth.clamp(screen.scroll, 0, screen.maxScroll());
            if (screen.selectedRow() == null) screen.selected = null;
            screen.updateButton();
        } else {
            mc.setScreen(new ValkyrieStorageScreen(packet));
        }
        if (!packet.message().isEmpty() && mc.player != null) mc.player.displayClientMessage(Component.translatable(packet.message()), true);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        action = addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie_storage.store"), b -> {
            Row r = selectedRow();
            if (r != null) ModNetwork.CHANNEL.sendToServer(new StorageActionPacket(data.pos(), r.id(), !r.stored()));
        }).bounds(x + W - 170, y + H - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W - 86, y + H - 30, 76, 20).build());
        updateButton();
    }

    @Nullable
    private Row selectedRow() {
        if (selected == null) return null;
        for (Row r : data.rows()) if (r.id().equals(selected)) return r;
        return null;
    }

    private void updateButton() {
        if (action == null) return;
        Row r = selectedRow();
        action.active = r != null && (r.stored() || data.stored() < data.capacity());
        action.setMessage(Component.translatable(r != null && r.stored() ? "gui.automata_valkyries.valkyrie_storage.release" : "gui.automata_valkyries.valkyrie_storage.store"));
    }

    private int maxScroll() {
        return Math.max(0, data.rows().size() - LIST_ROWS);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, BG);
        g.renderOutline(x, y, W, H, GLOW_DIM);
        g.fill(x, y, x + 24, y + 2, GLOW);
        g.fill(x + W - 24, y + H - 2, x + W, y + H, GLOW);
        g.fill(x + 8, y + 26, x + W - 8, y + 27, GLOW_DIM);
        g.drawString(font, title, x + 10, y + 9, GLOW, false);
        Component count = Component.translatable("gui.automata_valkyries.valkyrie_storage.count", data.stored(), data.capacity());
        g.drawString(font, count, x + W - 10 - font.width(count), y + 9, data.stored() >= data.capacity() ? 0xFFFF6060 : 0xFFB8C4D8, false);

        List<Row> rows = data.rows();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.automata_valkyries.valkyrie_storage.empty"), x + W / 2, y + LIST_TOP + 40, 0xFF8090A8);
        }
        for (int i = 0; i < LIST_ROWS && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = y + LIST_TOP + i * ROW;
            boolean hover = mouseX >= x + 8 && mouseX < x + W - 12 && mouseY >= ry && mouseY < ry + ROW - 2;
            g.fill(x + 8, ry, x + W - 12, ry + ROW - 2, r.id().equals(selected) ? ROW_SEL : hover ? 0x80303C5C : ROW_BG);
            g.drawString(font, r.name() + (r.omega() ? " ◆" : ""), x + 12, ry + 2, r.omega() ? 0xFFFFD86B : 0xFFFFFFFF, false);
            Component state = Component.translatable("gui.automata_valkyries.valkyrie_storage.state." + (r.stored() ? "stored" : "nearby"));
            g.drawString(font, state, x + W - 16 - font.width(state), ry + 2, r.stored() ? 0xFFB08CFF : 0xFF60FF9C, false);
            String hp = "HP " + Math.round(r.health()) + (r.maxHealth() > 0 ? " / " + Math.round(r.maxHealth()) : "")
                    + (r.shutdown() ? "   " + Component.translatable("gui.automata_valkyries.rescue.state.shutdown").getString() : "");
            g.drawString(font, hp, x + 12, ry + 11, r.shutdown() ? 0xFFFFC060 : 0xFF8FA0BC, false);
        }
        if (maxScroll() > 0) {
            int trackTop = y + LIST_TOP, trackH = LIST_ROWS * ROW - 2;
            int barH = Math.max(10, trackH * LIST_ROWS / rows.size());
            int barY = trackTop + (trackH - barH) * scroll / maxScroll();
            g.fill(x + W - 9, trackTop, x + W - 7, trackTop + trackH, 0x40FFFFFF);
            g.fill(x + W - 9, barY, x + W - 7, barY + barH, GLOW);
        }
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie_storage.hint"), x + 10, y + H - 44, 0xFF8FA0BC, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left(), y = top();
        if (mouseX >= x + 8 && mouseX < x + W - 12 && mouseY >= y + LIST_TOP && mouseY < y + LIST_TOP + LIST_ROWS * ROW) {
            int i = scroll + (int) ((mouseY - y - LIST_TOP) / ROW);
            if (i < data.rows().size()) {
                selected = data.rows().get(i).id();
                updateButton();
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
    public boolean isPauseScreen() {
        return false;
    }
}
