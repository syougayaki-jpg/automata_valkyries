package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 「戦術兵姫の手引き」の画面（{@link com.automatavalkyries.item.GuideBookItem}）: 左に章の一覧（アイコン付き）、右に章の中身（折り返し・ホイールで送る）。
 * 中身は {@code assets/automata_valkyries/guide/<言語>.json}。今の言語の物が無ければ英語、それも無ければ日本語を読む。行の書き方は {@code tools/gen_guide.py}
 */
public class GuideScreen extends Screen {

    private record Chapter(String title, ItemStack icon, List<String> lines) {}

    /** 折り返した後の 1 行（種類: 0 = ふつう、1 = 見出し、2 = 項目、3 = 補足、4 = アイテム付き） */
    private record Row(FormattedCharSequence text, int kind, ItemStack icon, boolean first) {}

    private static final int BG = 0xE0101820, PANEL = 0xC0182230, EDGE = 0xFF4FA8D8, LIST_W = 112, ROW_H = 11, CH_H = 18;
    private static int lastChapter;

    private final List<Chapter> chapters = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int chapter, scroll, listScroll;
    private int left, top, w, h;

    public GuideScreen() {
        super(Component.translatable("item.automata_valkyries.guide_book"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 400);
        h = Math.min(height - 16, 240);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (chapters.isEmpty()) load();
        chapter = Mth.clamp(lastChapter, 0, Math.max(0, chapters.size() - 1));
        layout();
    }

    private void load() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        for (String code : new String[]{lang, "en_us", "ja_jp"}) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "guide/" + code + ".json"));
            if (resource.isEmpty()) continue;
            try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement e : json.getAsJsonArray("chapters")) {
                    JsonObject c = e.getAsJsonObject();
                    List<String> lines = new ArrayList<>();
                    for (JsonElement l : c.getAsJsonArray("lines")) lines.add(l.getAsString());
                    chapters.add(new Chapter(c.get("title").getAsString(), stack(c.get("icon").getAsString()), lines));
                }
                return;
            } catch (Exception ex) {
                AutomataValkyries.LOGGER.warn("Could not read the guide {}", code, ex);
                chapters.clear();
            }
        }
    }

    private static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return item == null || item == Items.AIR ? new ItemStack(Items.BOOK) : new ItemStack(item);
    }

    /** 今の章の行を、右の欄の幅で折り返して並べる */
    private void layout() {
        rows.clear();
        scroll = 0;
        if (chapters.isEmpty()) return;
        int textW = w - LIST_W - 28;
        for (String line : chapters.get(chapter).lines()) {
            int kind = 0;
            ItemStack icon = ItemStack.EMPTY;
            String text = line;
            int indent = 0;
            if (line.startsWith("## ")) {
                kind = 1;
                text = line.substring(3);
            } else if (line.startsWith("- ")) {
                kind = 2;
                text = line.substring(2);
                indent = 10;
            } else if (line.startsWith("> ")) {
                kind = 3;
                text = line.substring(2);
                indent = 6;
            } else if (line.startsWith("@")) {
                int sp = line.indexOf(' ');
                kind = 4;
                icon = stack(sp < 0 ? line.substring(1) : line.substring(1, sp));
                text = sp < 0 ? "" : line.substring(sp + 1);
                indent = 20;
            }
            if (text.isEmpty()) {
                rows.add(new Row(FormattedCharSequence.EMPTY, 0, ItemStack.EMPTY, true));
                continue;
            }
            List<FormattedCharSequence> wrapped = font.split(Component.literal(text), textW - indent);
            for (int i = 0; i < wrapped.size(); i++) rows.add(new Row(wrapped.get(i), kind, i == 0 ? icon : ItemStack.EMPTY, i == 0));
            if (kind == 4 && wrapped.size() == 1) rows.add(new Row(FormattedCharSequence.EMPTY, -1, ItemStack.EMPTY, false)); // アイコンの高さの分あける
        }
    }

    private int textTop() {
        return top + 26;
    }

    private int visibleRows() {
        return (h - 34) / ROW_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + w, top + h, BG);
        g.renderOutline(left, top, w, h, EDGE);
        g.drawString(font, title, left + 8, top + 7, 0xFFE8C860, false);
        // 左: 章の一覧
        int listTop = top + 22, listH = h - 28;
        g.fill(left + 4, listTop, left + LIST_W, listTop + listH, PANEL);
        g.enableScissor(left + 4, listTop, left + LIST_W, listTop + listH);
        for (int i = 0; i < chapters.size(); i++) {
            int y = listTop + 2 + i * CH_H - listScroll;
            boolean hover = mouseX >= left + 4 && mouseX < left + LIST_W && mouseY >= y && mouseY < y + CH_H && mouseY >= listTop && mouseY < listTop + listH;
            if (i == chapter) g.fill(left + 5, y, left + LIST_W - 1, y + CH_H, 0x904FA8D8);
            else if (hover) g.fill(left + 5, y, left + LIST_W - 1, y + CH_H, 0x40FFFFFF);
            g.renderItem(chapters.get(i).icon(), left + 7, y + 1);
            String name = font.plainSubstrByWidth(chapters.get(i).title(), LIST_W - 30);
            g.drawString(font, name, left + 26, y + 5, i == chapter ? 0xFFFFFFFF : 0xFFC8D4E0, false);
        }
        g.disableScissor();
        // 右: 中身
        int x0 = left + LIST_W + 8, x1 = left + w - 8;
        g.fill(x0 - 2, listTop, x1 + 2, listTop + listH, PANEL);
        if (!chapters.isEmpty()) {
            g.drawString(font, chapters.get(chapter).title(), x0 + 2, top + 12, 0xFF8CE0FF, false);
            int y = textTop();
            int end = Math.min(rows.size(), scroll + visibleRows());
            for (int i = scroll; i < end; i++, y += ROW_H) {
                Row r = rows.get(i);
                switch (r.kind()) {
                    case 1 -> g.drawString(font, r.text(), x0 + 2, y, 0xFFE8C860, false);
                    case 2 -> {
                        if (r.first()) g.drawString(font, "・", x0 + 2, y, 0xFF8CE0FF, false);
                        g.drawString(font, r.text(), x0 + 12, y, 0xFFE0E8F0, false);
                    }
                    case 3 -> {
                        g.fill(x0 + 2, y - 1, x0 + 4, y + ROW_H - 1, 0xFF4FE3FF);
                        g.drawString(font, r.text(), x0 + 8, y, 0xFF8CE0FF, false);
                    }
                    case 4 -> {
                        if (!r.icon().isEmpty()) g.renderItem(r.icon(), x0 + 2, y - 2);
                        g.drawString(font, r.text(), x0 + 22, y, 0xFFE0E8F0, false);
                    }
                    case 0 -> g.drawString(font, r.text(), x0 + 2, y, 0xFFD0DAE4, false);
                    default -> { }
                }
            }
            // スクロールの目盛り
            int total = rows.size(), vis = visibleRows();
            if (total > vis) {
                int barH = Math.max(12, (listH - 4) * vis / total);
                int barY = listTop + 2 + (listH - 4 - barH) * scroll / Math.max(1, total - vis);
                g.fill(x1, listTop + 2, x1 + 2, listTop + listH - 2, 0x40FFFFFF);
                g.fill(x1, barY, x1 + 2, barY + barH, EDGE);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int listTop = top + 22, listH = h - 28;
        if (mx >= left + 4 && mx < left + LIST_W && my >= listTop && my < listTop + listH) {
            int i = (int) ((my - listTop - 2 + listScroll) / CH_H);
            if (i >= 0 && i < chapters.size() && i != chapter) {
                chapter = lastChapter = i;
                layout();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < left + LIST_W) {                                                               // 章の一覧
            int max = Math.max(0, chapters.size() * CH_H + 4 - (h - 28));
            listScroll = Mth.clamp(listScroll - (int) (delta * CH_H), 0, max);
        } else {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 3, 0, Math.max(0, rows.size() - visibleRows()));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        int max = Math.max(0, rows.size() - visibleRows());
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.min(max, scroll + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : visibleRows()));
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.max(0, scroll - (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? 1 : visibleRows()));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
