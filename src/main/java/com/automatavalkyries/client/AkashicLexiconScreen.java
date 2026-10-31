package com.automatavalkyries.client;

import com.automatavalkyries.entity.akashic.AkashicCustomLexicon;
import com.automatavalkyries.entity.akashic.AkashicLexiconNetwork;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.AuditStage;
import com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind;
import com.automatavalkyries.entity.akashic.AkashicTechniqueSpecCatalog;
import com.automatavalkyries.entity.akashic.AkashicTechniqueSpecCatalog.SpecEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 「カスタムダメージ辞書（36種）」「カスタム即死辞書（72種）」の専用カスタム設定 ＆ 仕様技術解説 GUI 画面
 * （2026-10-26 ユーザー指定: 「ダメージ即死辞書に仕様技術の解説をつけて」）。
 * <p>
 * 上段で各技術の ON/OFF・ティア一括切替・順序・倍率等を設定しつつ、
 * リストの行にマウスを合わせるかクリックして選択すると、下段の「仕様技術解説コンソール（Technical Specification Console）」および
 * ホバーツールチップに以下を詳細表示する:
 * <ul>
 *   <li>技術コード・所属ティア・日本語技術名・実行式要約</li>
 *   <li><b>【仕様技術・内部機構】</b>: 具体的に呼び出すメソッド / フィールド / Unsafe / ASM / ワールド操作</li>
 *   <li><b>【突破対象の防御機構】</b>: どのような無敵・上限・自己修復・チート防御を突破するための技術か</li>
 *   <li>「体系解説切替」ボタンで辞書全体のアーキテクチャ・7段階適応調査フローチャート・10階層体系も閲覧可能</li>
 * </ul>
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class AkashicLexiconScreen extends Screen {

    private static final int PANEL_W = 410;
    private static final int PANEL_H = 266;
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_H = 18;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final AkashicCustomLexicon lexicon;
    private final AuditStage[] stages;

    private int scrollOffset = 0;
    private int selectedIndex = 0;
    private int hoveredTier = -1;
    private boolean showArchitectureGuide = false;

    private Button orderBtn;
    private Button stopBtn;
    private Button guideBtn;
    private Button multBtn;
    private Button repeatBtn;
    private Button radiusBtn;
    private final Button[] rowToggleBtns = new Button[VISIBLE_ROWS];

    public AkashicLexiconScreen(InteractionHand hand, ItemStack stack, TechniqueKind kind) {
        super(Component.literal(kind == TechniqueKind.DAMAGE
                ? "カスタムダメージ辞書 設定＆仕様技術解説 (全36種)"
                : "カスタム即死辞書 設定＆仕様技術解説 (全72種)"));
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

        // 1段目: 執行順序 / 貫通時停止 / 体系解説モード切替 / 保存して閉じる
        this.orderBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleExecutionOrder();
            syncAndRefresh();
        }).bounds(left + 10, top + 22, 138, 18).build());

        this.stopBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.setStopOnPenetrate(!lexicon.stopOnPenetrate());
            syncAndRefresh();
        }).bounds(left + 151, top + 22, 106, 18).build());

        this.guideBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            this.showArchitectureGuide = !this.showArchitectureGuide;
            refreshLabels();
        }).bounds(left + 260, top + 22, 72, 18).build());

        addRenderableWidget(Button.builder(Component.literal("保存して閉じる").withStyle(ChatFormatting.GREEN), b -> {
            syncToServer();
            onClose();
        }).bounds(left + 335, top + 22, 65, 18).build());

        // 2段目: ダメージ倍率 / 連撃回数 / 範囲半径
        this.multBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (lexicon.kind() == TechniqueKind.DAMAGE) {
                lexicon.cycleDamageMultiplier();
                syncAndRefresh();
            }
        }).bounds(left + 10, top + 42, 128, 18).build());

        this.repeatBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleRepeatCount();
            syncAndRefresh();
        }).bounds(left + 142, top + 42, 124, 18).build());

        this.radiusBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lexicon.cycleAreaRadius();
            syncAndRefresh();
        }).bounds(left + 270, top + 42, 130, 18).build());

        // 3段目: 一括 ON / OFF / 反転 / Tier 一括トグル
        addRenderableWidget(Button.builder(Component.literal("全ON"), b -> {
            lexicon.enableAll();
            syncAndRefresh();
        }).bounds(left + 10, top + 62, 38, 16).build());

        addRenderableWidget(Button.builder(Component.literal("全OFF"), b -> {
            lexicon.disableAll();
            syncAndRefresh();
        }).bounds(left + 50, top + 62, 40, 16).build());

        addRenderableWidget(Button.builder(Component.literal("反転"), b -> {
            lexicon.invertSelection();
            syncAndRefresh();
        }).bounds(left + 92, top + 62, 36, 16).build());

        if (lexicon.kind() == TechniqueKind.DAMAGE) {
            int[] tiers = {1, 2, 3};
            for (int i = 0; i < tiers.length; i++) {
                final int t = tiers[i];
                addRenderableWidget(Button.builder(Component.literal("Tier" + t), b -> {
                    lexicon.toggleTier(t);
                    syncAndRefresh();
                }).bounds(left + 134 + i * 54, top + 62, 50, 16).build());
            }
        } else {
            int[] tiers = {4, 5, 6, 7, 8, 9, 10};
            for (int i = 0; i < tiers.length; i++) {
                final int t = tiers[i];
                addRenderableWidget(Button.builder(Component.literal("T" + t), b -> {
                    lexicon.toggleTier(t);
                    syncAndRefresh();
                }).bounds(left + 132 + i * 38, top + 62, 35, 16).build());
            }
        }

        // スクロールボタン（▲ / ▼）
        int listTop = top + 82;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scrollBy(-3);
        }).bounds(left + PANEL_W - 24, listTop, 16, 20).build());

        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scrollBy(3);
        }).bounds(left + PANEL_W - 24, listTop + VISIBLE_ROWS * ROW_H - 20, 16, 20).build());

        // 各行の ON/OFF トグルボタン
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            final int r = row;
            this.rowToggleBtns[row] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                int idx = scrollOffset + r;
                if (idx >= 0 && idx < stages.length) {
                    this.selectedIndex = idx;
                    this.showArchitectureGuide = false;
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
        if (guideBtn != null) {
            guideBtn.setMessage(Component.literal(showArchitectureGuide ? "解説: 辞書体系" : "解説: 個別仕様")
                    .withStyle(showArchitectureGuide ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int listTop = top + 82;
        int listRight = left + PANEL_W - 74;

        // リスト行の左領域クリックで選択技術をピン留めして解説を表示（右クリックならON/OFFも切替）
        if (mouseX >= left + 12 && mouseX <= listRight
                && mouseY >= listTop && mouseY < listTop + VISIBLE_ROWS * ROW_H) {
            int row = (int) ((mouseY - listTop) / ROW_H);
            int idx = scrollOffset + row;
            if (idx >= 0 && idx < stages.length) {
                this.selectedIndex = idx;
                this.showArchitectureGuide = false;
                refreshLabels();
                if (button == 1) {
                    lexicon.toggleStage(stages[idx]);
                    syncAndRefresh();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEC0E121C);
        int borderColor = lexicon.kind() == TechniqueKind.DAMAGE ? 0xFFFFAA22 : 0xFFD848FF;
        g.renderOutline(left, top, PANEL_W, PANEL_H, borderColor);

        // タイトルと有効技術数
        String titleStr = this.title.getString() + String.format(Locale.ROOT, "  [有効: %d / %d]",
                lexicon.enabledCount(), lexicon.totalAvailableCount());
        g.drawString(this.font, titleStr, left + 12, top + 7, borderColor, true);

        // 技術リスト描画（5行）
        int listTop = top + 82;
        int listRight = left + PANEL_W - 28;
        g.fill(left + 10, listTop - 2, listRight, listTop + VISIBLE_ROWS * ROW_H + 2, 0xBB07090F);

        AuditStage hoveredStage = null;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int idx = scrollOffset + row;
            if (idx < 0 || idx >= stages.length) continue;
            AuditStage st = stages[idx];
            int y = listTop + row * ROW_H;
            boolean on = lexicon.isEnabled(st);
            boolean isHoveredRow = mouseX >= left + 12 && mouseX <= listRight - 46
                    && mouseY >= y && mouseY < y + ROW_H;
            if (isHoveredRow) {
                hoveredStage = st;
            }
            boolean isSelected = (idx == selectedIndex);

            int rowBg;
            if (isHoveredRow) {
                rowBg = 0x88304870;
            } else if (isSelected) {
                rowBg = 0x66243654;
            } else {
                rowBg = (row % 2 == 0) ? 0x441A2030 : 0x221A2030;
            }
            g.fill(left + 12, y, listRight - 46, y + ROW_H - 1, rowBg);
            if (isSelected) {
                g.renderOutline(left + 12, y, (listRight - 46) - (left + 12), ROW_H - 1, 0xFF55FFFF);
            }

            String tierBadge = String.format(Locale.ROOT, "[T%d]", st.tier);
            g.drawString(this.font, tierBadge, left + 15, y + 5, 0xFF55FFFF, false);

            String dispCode = st.displayCode();
            String codeShort = dispCode.length() > 22 ? dispCode.substring(0, 22) : dispCode;
            g.drawString(this.font, codeShort, left + 42, y + 5, on ? 0xFFFFFFFF : 0xFF777777, false);

            String jaTitle = st.titleJa();
            String jaShort = this.font.plainSubstrByWidth(jaTitle, 165);
            g.drawString(this.font, jaShort, left + 170, y + 5, on ? 0xFFFFE088 : 0xFF666666, false);
        }

        // Tier ボタンホバー検出
        this.hoveredTier = detectHoveredTier(left, top, mouseX, mouseY);

        // 下段：仕様技術解説コンソール（Technical Specification Console）
        int specTop = listTop + VISIBLE_ROWS * ROW_H + 5;
        int specH = 68;
        int specW = PANEL_W - 20;
        g.fill(left + 10, specTop, left + 10 + specW, specTop + specH, 0xD8080C14);
        g.renderOutline(left + 10, specTop, specW, specH, 0xFF3A6088);

        AuditStage displayStage = hoveredStage != null ? hoveredStage
                : (selectedIndex >= 0 && selectedIndex < stages.length ? stages[selectedIndex] : null);

        if (showArchitectureGuide) {
            renderArchitectureGuidePane(g, left + 14, specTop + 4, specW - 8);
        } else if (hoveredTier > 0) {
            renderTierOverviewPane(g, left + 14, specTop + 4, specW - 8, hoveredTier);
        } else if (displayStage != null) {
            renderStageSpecPane(g, left + 14, specTop + 4, specW - 8, displayStage);
        }

        // フッター案内
        g.drawString(this.font, "行クリック/ホバー: 仕様技術解説を表示  |  敵へ左/右クリック: 直接発動  |  アカシックへ右クリック: 辞書登録",
                left + 12, top + PANEL_H - 12, 0xFF88CCFF, false);

        super.render(g, mouseX, mouseY, partialTick);

        // ホバー時のマルチライン詳細ツールチップ
        if (hoveredStage != null) {
            renderStageTooltip(g, hoveredStage, mouseX, mouseY);
        } else if (hoveredTier > 0) {
            renderTierTooltip(g, hoveredTier, mouseX, mouseY);
        }
    }

    private void renderStageSpecPane(GuiGraphics g, int x, int y, int maxWidth, AuditStage st) {
        SpecEntry spec = st.spec();
        boolean on = lexicon.isEnabled(st);
        String header = String.format(Locale.ROOT, "[#%03d / Tier %d] %s  (%s)",
                st.number, st.tier, spec.titleJa(), on ? "有効:ON" : "無効:OFF");
        g.drawString(this.font, this.font.plainSubstrByWidth(header, maxWidth), x, y, 0xFF55FFFF, true);

        String exprLine = "コード: " + st.displayCode() + "  |  実行式: " + st.description;
        g.drawString(this.font, this.font.plainSubstrByWidth(exprLine, maxWidth), x, y + 11, 0xFFCCCCCC, false);

        // 仕様技術（2行折り返し）
        String mechText = "【仕様技術】" + spec.mechanismSpec();
        List<FormattedCharSequence> mechLines = this.font.split(Component.literal(mechText), maxWidth);
        int curY = y + 22;
        for (int i = 0; i < Math.min(2, mechLines.size()); i++) {
            g.drawString(this.font, mechLines.get(i), x, curY, 0xFFFFE088, false);
            curY += 10;
        }

        // 突破対象（2行折り返し）
        String bypassText = "【突破対象】" + spec.targetDefenseBypass();
        List<FormattedCharSequence> bypassLines = this.font.split(Component.literal(bypassText), maxWidth);
        for (int i = 0; i < Math.min(2, bypassLines.size()) && curY <= y + 54; i++) {
            g.drawString(this.font, bypassLines.get(i), x, curY, 0xFF99FF99, false);
            curY += 10;
        }
    }

    private void renderTierOverviewPane(GuiGraphics g, int x, int y, int maxWidth, int tier) {
        boolean allOn = lexicon.isTierAllEnabled(tier);
        String header = String.format(Locale.ROOT, "【Tier %d 仕様技術体系】 (現在: %s — クリックで一括切替)",
                tier, allOn ? "全技術ON" : "一部または全OFF");
        g.drawString(this.font, header, x, y, 0xFF55FFFF, true);

        String overview = AkashicTechniqueSpecCatalog.tierOverview(tier);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(overview), maxWidth);
        int curY = y + 13;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            g.drawString(this.font, lines.get(i), x, curY, 0xFFFFE088, false);
            curY += 11;
        }
    }

    private void renderArchitectureGuidePane(GuiGraphics g, int x, int y, int maxWidth) {
        List<String> guide = AkashicTechniqueSpecCatalog.lexiconArchitectureGuide(lexicon.kind());
        int curY = y;
        for (int i = 0; i < guide.size() && curY <= y + 54; i++) {
            String line = guide.get(i);
            int color = (i == 0) ? 0xFF55FFFF : 0xFFE0E8FF;
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(line), maxWidth);
            for (FormattedCharSequence seq : wrapped) {
                if (curY > y + 54) break;
                g.drawString(this.font, seq, x, curY, color, i == 0);
                curY += 10;
            }
        }
    }

    private void renderStageTooltip(GuiGraphics g, AuditStage st, int mouseX, int mouseY) {
        SpecEntry spec = st.spec();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(String.format(Locale.ROOT, "[#%03d / Tier %d] %s",
                st.number, st.tier, spec.titleJa())).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        lines.add(Component.literal("分類: " + spec.categoryJa()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("識別コード: " + st.displayCode() + "  |  式: " + st.description).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("【仕様技術・内部機構】").withStyle(ChatFormatting.GOLD));
        for (FormattedText part : this.font.getSplitter().splitLines(spec.mechanismSpec(), 280, net.minecraft.network.chat.Style.EMPTY)) {
            lines.add(Component.literal("  " + part.getString()).withStyle(ChatFormatting.WHITE));
        }
        lines.add(Component.literal("【突破対象の防御・耐性機構】").withStyle(ChatFormatting.GREEN));
        for (FormattedText part : this.font.getSplitter().splitLines(spec.targetDefenseBypass(), 280, net.minecraft.network.chat.Style.EMPTY)) {
            lines.add(Component.literal("  " + part.getString()).withStyle(ChatFormatting.GRAY));
        }
        g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private void renderTierTooltip(GuiGraphics g, int tier, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Tier " + tier + " 一括トグル").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (FormattedText part : this.font.getSplitter().splitLines(
                AkashicTechniqueSpecCatalog.tierOverview(tier), 260, net.minecraft.network.chat.Style.EMPTY)) {
            lines.add(Component.literal(part.getString()).withStyle(ChatFormatting.WHITE));
        }
        g.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private int detectHoveredTier(int left, int top, int mouseX, int mouseY) {
        int btnY = top + 62;
        if (mouseY < btnY || mouseY >= btnY + 16) return -1;
        if (lexicon.kind() == TechniqueKind.DAMAGE) {
            int[] tiers = {1, 2, 3};
            for (int i = 0; i < tiers.length; i++) {
                int bx = left + 134 + i * 54;
                if (mouseX >= bx && mouseX < bx + 50) return tiers[i];
            }
        } else {
            int[] tiers = {4, 5, 6, 7, 8, 9, 10};
            for (int i = 0; i < tiers.length; i++) {
                int bx = left + 132 + i * 38;
                if (mouseX >= bx && mouseX < bx + 35) return tiers[i];
            }
        }
        return -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
