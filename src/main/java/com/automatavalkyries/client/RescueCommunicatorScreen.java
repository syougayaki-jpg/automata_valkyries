package com.automatavalkyries.client;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.RescueListPacket;
import com.automatavalkyries.network.RescueRecallPacket;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect.Summary;
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
 * 救済通信機の画面（2026-10-04 ユーザー指定）: 保存された兵姫のデータを一覧で見せ、選んだ兵姫を呼び出す。
 * 見た目はホログラム（暗い半透明の地にシアンの発光線。docs/SPEC_VALKYRIE_GUI_AI.md §2）。
 */
@OnlyIn(Dist.CLIENT)
public class RescueCommunicatorScreen extends Screen {

    private static final int W = 300, H = 210, ROW = 22, LIST_TOP = 34, LIST_ROWS = 6;
    private static final int GLOW = 0xFF3BE8FF, GLOW_DIM = 0xFF1A7C8C, BG = 0xE0101624, ROW_BG = 0x60202B44, ROW_SEL = 0x803BE8FF;

    private RescueListPacket data;
    private int scroll;
    @Nullable private UUID selected;
    private Button recall;
    private Button discard;

    private RescueCommunicatorScreen(RescueListPacket data) {
        super(Component.translatable("gui.automata_valkyries.rescue.title"));
        this.data = data;
    }

    /** 一覧が届いたとき: 開いていれば更新、無ければ開く */
    public static void show(RescueListPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RescueCommunicatorScreen screen) {
            screen.data = packet;
            screen.scroll = Mth.clamp(screen.scroll, 0, screen.maxScroll());
            screen.updateButton();
        } else {
            mc.setScreen(new RescueCommunicatorScreen(packet));
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
        recall = addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.rescue.recall"), b -> {
            if (selected != null) ModNetwork.CHANNEL.sendToServer(new RescueRecallPacket(selected));
        }).bounds(left() + W - 170, top() + H - 28, 80, 20).build());
        discard = addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.rescue.discard"), b -> confirmDiscard())
                .bounds(left() + 10, top() + H - 28, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + W - 86, top() + H - 28, 76, 20).build());
        updateButton();
    }

    @Nullable
    private Summary selectedSummary() {
        for (Summary s : data.entries()) if (s.id().equals(selected)) return s;
        return null;
    }

    private void updateButton() {
        if (recall == null) return;
        Summary s = selectedSummary();
        recall.active = s != null && s.state() == Summary.LOST;
        if (discard != null) discard.active = s != null;
    }

    /** 破棄の前に警告する（二度と呼び出せない。稼働中・遠くにいる兵姫は保護を外して消える） */
    private void confirmDiscard() {
        Summary s = selectedSummary();
        if (s == null) return;
        UUID id = s.id();
        String name = s.name().isEmpty() ? Component.translatable("entity.automata_valkyries.valkyrie").getString() + " #" + id.toString().substring(0, 4) : s.name();
        Component warning = Component.translatable(s.state() == Summary.LOST ? "gui.automata_valkyries.rescue.discard_warning_lost"
                : "gui.automata_valkyries.rescue.discard_warning_active", name);
        RescueCommunicatorScreen self = this;
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            Minecraft.getInstance().setScreen(self);
            if (yes) {
                ModNetwork.CHANNEL.sendToServer(new com.automatavalkyries.network.RescueDiscardPacket(id));
                selected = null;
                updateButton();
            }
        }, Component.translatable("gui.automata_valkyries.rescue.discard_title").withStyle(net.minecraft.ChatFormatting.RED), warning,
                Component.translatable("gui.automata_valkyries.rescue.discard_confirm"), Component.translatable("gui.cancel")));
    }

    private int maxScroll() {
        return Math.max(0, data.entries().size() - LIST_ROWS);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        // 枠: 暗い地・発光する縁と角の飾り
        g.fill(x, y, x + W, y + H, BG);
        g.renderOutline(x, y, W, H, GLOW_DIM);
        g.fill(x, y, x + 24, y + 2, GLOW);
        g.fill(x + W - 24, y + H - 2, x + W, y + H, GLOW);
        g.fill(x + 8, y + 26, x + W - 8, y + 27, GLOW_DIM);
        g.drawString(font, title, x + 10, y + 9, GLOW, false);
        Component count = Component.translatable("gui.automata_valkyries.rescue.count", data.registered(), data.max());
        g.drawString(font, count, x + W - 10 - font.width(count), y + 9, data.registered() >= data.max() ? 0xFFFF6060 : 0xFFB8C4D8, false);

        List<Summary> entries = data.entries();
        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.automata_valkyries.rescue.empty"), x + W / 2, y + LIST_TOP + 40, 0xFF8090A8);
        }
        for (int i = 0; i < LIST_ROWS && scroll + i < entries.size(); i++) {
            Summary s = entries.get(scroll + i);
            int ry = y + LIST_TOP + i * ROW;
            boolean hover = mouseX >= x + 8 && mouseX < x + W - 12 && mouseY >= ry && mouseY < ry + ROW - 2;
            g.fill(x + 8, ry, x + W - 12, ry + ROW - 2, s.id().equals(selected) ? ROW_SEL : hover ? 0x80303C5C : ROW_BG);
            String name = s.name().isEmpty() ? Component.translatable("entity.automata_valkyries.valkyrie").getString() + " #" + s.id().toString().substring(0, 4) : s.name();
            g.drawString(font, name + (s.omega() ? " ◆" : ""), x + 12, ry + 2, s.omega() ? 0xFFFFD86B : 0xFFFFFFFF, false);
            String stateKey = switch (s.state()) {
                case Summary.ACTIVE -> s.shutdown() ? "shutdown" : "active";
                case Summary.LOST -> "lost";
                default -> "unloaded";
            };
            int stateColor = switch (stateKey) {
                case "active" -> 0xFF60FF9C;
                case "shutdown" -> 0xFFFFC060;
                case "lost" -> 0xFFFF6060;
                default -> 0xFF9098B0;
            };
            Component state = Component.translatable("gui.automata_valkyries.rescue.state." + stateKey);
            if (!s.owned()) state = Component.translatable("gui.automata_valkyries.rescue.unclaimed").append(" ").append(state);
            g.drawString(font, state, x + W - 16 - font.width(state), ry + 2, stateColor, false);
            String where = s.dimension().replace("minecraft:", "") + "  " + s.pos().getX() + ", " + s.pos().getY() + ", " + s.pos().getZ()
                    + "   HP " + Math.round(s.health());
            g.drawString(font, where, x + 12, ry + 11, 0xFF8FA0BC, false);
        }
        // スクロールの目安
        if (maxScroll() > 0) {
            int trackTop = y + LIST_TOP, trackH = LIST_ROWS * ROW - 2;
            int barH = Math.max(10, trackH * LIST_ROWS / entries.size());
            int barY = trackTop + (trackH - barH) * scroll / maxScroll();
            g.fill(x + W - 9, trackTop, x + W - 7, trackTop + trackH, 0x40FFFFFF);
            g.fill(x + W - 9, barY, x + W - 7, barY + barH, GLOW);
        }
        Summary sel = selectedSummary();
        Component hint = sel == null ? Component.translatable("gui.automata_valkyries.rescue.hint")
                : sel.state() == Summary.LOST ? Component.translatable("gui.automata_valkyries.rescue.hint_lost")
                : Component.translatable("gui.automata_valkyries.rescue.hint_not_lost");
        g.drawString(font, hint, x + 10, y + H - 22, 0xFF8FA0BC, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left(), y = top();
        if (mouseX >= x + 8 && mouseX < x + W - 12 && mouseY >= y + LIST_TOP && mouseY < y + LIST_TOP + LIST_ROWS * ROW) {
            int i = scroll + (int) ((mouseY - y - LIST_TOP) / ROW);
            if (i < data.entries().size()) {
                selected = data.entries().get(i).id();
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
