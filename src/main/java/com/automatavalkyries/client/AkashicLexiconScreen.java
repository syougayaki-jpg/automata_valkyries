package com.automatavalkyries.client;

import com.automatavalkyries.entity.akashic.AkashicCustomLexicon;
import com.automatavalkyries.entity.akashic.AkashicLexiconNetwork;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * 「カスタムダメージ辞書（36種）」「カスタム即死辞書（72種）」の専用カスタム設定 GUI 画面。
 */
@OnlyIn(Dist.CLIENT)
public final class AkashicLexiconScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 236;
    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_H = 18;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final AkashicCustomLexicon lexicon;
    private final AuditStage[] stages;

    private int scrollOffset = 0;

    private Button orderBtn;
    private Button stopBtn;
    private Button multBtn;
    private Button repeatBtn;
    private Button radiusBtn;
    private final Button[] rowToggleBtns = new Button[VISIBLE_ROWS];

    public AkashicLexiconScreen(InteractionHand hand, ItemStack stack, TechniqueKind kind) {
        super(Component.literal(kind == TechniqueKind.DAMAGE
                ? "カスタムダメージ辞書 設定 (全36種)"
                : "カスタム即死辞書 設定 (全72種)"));
        this.hand = hand;
        this.stack = stack;
        this.lexicon = AkashicCustomLexicon.fromStack(stack, kind);
        this.stages = this.lexicon.availableStages();
    }

    public static void open(InteractionHand hand, ItemStack stack, TechniqueKind kind) {
        Minecraft.getInstance().setScreen(new AkashicLexiconScreen(hand, stack, kind));
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // 1段目: 執行順序 / 貫通時停止 / 保存して閉じる
        this.orderBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleExecutionOrder();
            syncAndRefresh();
        }).bounds(left + 10, top + 24, 145, 18).build());

        this.stopBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.setStopOnPenetrate(!lexicon.stopOnPenetrate());
            syncAndRefresh();
        }).bounds(left + 158, top + 24, 120, 18).build());

        addRenderableWidget(Button.builder(Component.literal("保存して閉じる").withStyle(ChatFormatting.GREEN), b -> {
            syncToServer();
            onClose();
        }).bounds(left + 282, top + 24, 68, 18).build());

        // 2段目: ダメージ倍率 / 連撃回数 / 範囲半径
        this.multBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (lexicon.kind() == TechniqueKind.DAMAGE) {
                lexicon.cycleDamageMultiplier();
                syncAndRefresh();
            }
        }).bounds(left + 10, top + 45, 112, 18).build());

        this.repeatBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleRepeatCount();
            syncAndRefresh();
        }).bounds(left + 126, top + 45, 108, 18).build());

        this.radiusBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleAreaRadius();
            syncAndRefresh();
        }).bounds(left + 238, top + 45, 112, 18).build());

        // 3段目: 一括 ON / OFF / 反転 / Tier 一括トグル
        addRenderableWidget(Button.builder(Component.literal("全ON"), b -> {
            lexicon.enableAll();
            syncAndRefresh();
        }).bounds(left + 10, top + 66, 36, 16).build());

        addRenderableWidget(Button.builder(Component.literal("全OFF"), b -> {
            lexicon.disableAll();
            syncAndRefresh();
        }).bounds(left + 48, top + 66, 38, 16).build());

        addRenderableWidget(Button.builder(Component.literal("反転"), b -> {
            lexicon.invertSelection();
            syncAndRefresh();
        }).bounds(left + 88, top + 66, 34, 16).build());

        if (lexicon.kind() == TechniqueKind.DAMAGE) {
            int[] tiers = {1, 2, 3};
            for (int i = 0; i < tiers.length; i++) {
                final int t = tiers[i];
                addRenderableWidget(Button.builder(Component.literal("Tier" + t), b -> {
                    lexicon.toggleTier(t);
                    syncAndRefresh();
                }).bounds(left + 128 + i * 48, top + 66, 45, 16).build());
            }
        } else {
            int[] tiers = {4, 5, 6, 7, 8, 9, 10};
            for (int i = 0; i < tiers.length; i++) {
                final int t = tiers[i];
                addRenderableWidget(Button.builder(Component.literal("T" + t), b -> {
                    lexicon.toggleTier(t);
                    syncAndRefresh();
                }).bounds(left + 126 + i * 32, top + 66, 30, 16).build());
            }
        }

        // スクロールボタン（▲ / ▼）
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scrollBy(-3);
        }).bounds(left + PANEL_W - 24, top + 88, 16, 20).build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scrollBy(3);
        }).bounds(left + PANEL_W - 24, top + 88 + VISIBLE_ROWS * ROW_H - 20, 16, 20).build());

        // 各行の ON/OFF トグルボタン
        int listTop = top + 88;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            final int r = row;
            this.rowToggleBtns[row] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                int idx = scrollOffset + r;
                if (idx >= 0 && idx < stages.length) {
                    lexicon.toggleStage(stages[idx]);
                    syncAndRefresh();
                }
            }).bounds(left + PANEL_W - 72, listTop + row * ROW_H + 1, 44, 16).build());
        }

        refreshLabels();
    }

    private void scrollBy(int delta) {
        int maxScroll = Math.max(0, stages.length - VISIBLE_ROWS);
        this.scrollOffset = Mth.clamp(this.scrollOffset + delta, 0, maxScroll);
        refreshLabels();
    }

    private void syncAndRefresh() {
        syncToServer();
        refreshLabels();
    }

    private void syncToServer() {
        lexicon.saveToStack(stack);
        AkashicLexiconNetwork.sendToServer(hand, lexicon);
    }

    private void refreshLabels() {
        if (orderBtn != null) {
            orderBtn.setMessage(Component.literal("順序: " + lexicon.executionOrder().label));
        }
        if (stopBtn != null) {
            stopBtn.setMessage(Component.literal(lexicon.stopOnPenetrate() ? "貫通時: 停止(検証)" : "貫通時: 全段執行"));
        }
        if (multBtn != null) {
            if (lexicon.kind() == TechniqueKind.DAMAGE) {
                float m = lexicon.damageMultiplier();
                String ms = m >= 1e6F ? String.format(Locale.ROOT, "x%.0e", m) : String.format(Locale.ROOT, "x%.1f", m);
                multBtn.setMessage(Component.literal("威力倍率: " + ms));
                multBtn.active = true;
            } else {
                multBtn.setMessage(Component.literal("威力倍率: (即死固定)"));
                multBtn.active = false;
            }
        }
        if (repeatBtn != null) {
            repeatBtn.setMessage(Component.literal("連撃回数: " + lexicon.repeatCount() + "回"));
        }
        if (radiusBtn != null) {
            float r = lexicon.areaRadius();
            radiusBtn.setMessage(Component.literal(r <= 0.0F ? "範囲: 単体(0m)" : String.format(Locale.ROOT, "範囲: 半径%.0fm", r)));
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            Button btn = rowToggleBtns[row];
            if (btn == null) continue;
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < stages.length) {
                btn.visible = true;
                boolean on = lexicon.isEnabled(stages[idx]);
                btn.setMessage(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else {
                btn.visible = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            scrollBy(delta > 0 ? -2 : 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        syncToServer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;

        // 背景パネルと枠線
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE8101420);
        int borderColor = lexicon.kind() == TechniqueKind.DAMAGE ? 0xFFFFAA22 : 0xFFD848FF;
        g.renderOutline(left, top, PANEL_W, PANEL_H, borderColor);

        // タイトルと有効技術数
        String titleStr = this.title.getString() + String.format(Locale.ROOT, "  [有効: %d / %d]",
                lexicon.enabledCount(), lexicon.totalAvailableCount());
        g.drawString(this.font, titleStr, left + 12, top + 8, borderColor, true);

        // 技術リスト描画
        int listTop = top + 88;
        int listRight = left + PANEL_W - 28;
        g.fill(left + 10, listTop - 2, listRight, listTop + VISIBLE_ROWS * ROW_H + 2, 0xAA080A10);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx < 0 || idx >= stages.length) continue;
            AuditStage st = stages[idx];
            int y = listTop + row * ROW_H;
            boolean on = lexicon.isEnabled(st);

            int rowBg = (row % 2 == 0) ? 0x441A2030 : 0x221A2030;
            g.fill(left + 12, y, listRight - 46, y + ROW_H - 1, rowBg);

            String tierBadge = String.format(Locale.ROOT, "[T%d]", st.tier);
            g.drawString(this.font, tierBadge, left + 15, y + 5, 0xFF55FFFF, false);

            String codeShort = st.code.length() > 26 ? st.code.substring(0, 26) : st.code;
            g.drawString(this.font, codeShort, left + 42, y + 5, on ? 0xFFFFFFFF : 0xFF777777, false);

            String descShort = st.description.length() > 22 ? st.description.substring(0, 22) : st.description;
            g.drawString(this.font, descShort, left + 178, y + 5, on ? 0xFFAAAAAA : 0xFF555555, false);
        }

        // フッター案内
        g.drawString(this.font, "左クリック/右クリック: プレイヤーが直接発動  |  アカシックへ右クリック: 辞書設定を登録",
                left + 12, top + PANEL_H - 14, 0xFF88CCFF, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
