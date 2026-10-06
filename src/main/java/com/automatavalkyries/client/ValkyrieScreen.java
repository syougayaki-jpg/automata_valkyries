package com.automatavalkyries.client;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.menu.ValkyrieMenu;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.ValkyrieCommandPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * 戦術兵姫のメニュー画面（SPEC_VALKYRIE_GUI_AI §2。段階 2、2026-10-05）。ホログラム・発光線のテーマ（暗い半透明の地に、コアの色の発光線と角ばった枠）。
 * <ul>
 *   <li>換装: 中央に兵姫の 3D モデル（ドラッグで回転）、周りに体の位置に合わせた枠。右に HP・シールド・電力・積載・攻防速・同調率・消費 FE</li>
 *   <li>状態: 名前（変更）・素体タイプ・性格・戦績・今の気分（表情を選べる）・同調率と履歴</li>
 *   <li>作戦指示: 行動モード・拠点・交戦規定（効くのは戦闘 AI の段階 3 から）</li>
 * </ul>
 * 文言・配置・色は【仮】
 */
@OnlyIn(Dist.CLIENT)
public class ValkyrieScreen extends AbstractContainerScreen<ValkyrieMenu> {

    private static final int BG = 0xE0101624, PANEL = 0x60202B44, SLOT = 0xC00B0F17, TEXT = 0xFFE0ECF8, DIM = 0xFF7A8698,
            GOOD = 0xFF6CFF9A, BAD = 0xFFFF6A6A, HP_COLOR = 0xFFFF5A6E, SHIELD_COLOR = 0xFF59C8FF, POWER_COLOR = 0xFFFFD45A;
    private static final int MODEL_X = 28, MODEL_Y = 24, MODEL_W = 70, MODEL_H = 96, STATS_X = 130;
    private static final String[] TABS = {"equipment", "status", "orders", "items"};

    private float modelYaw = 200.0F;
    private boolean dragging;
    private EditBox nameBox;

    public ValkyrieScreen(ValkyrieMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ValkyrieMenu.WIDTH;
        imageHeight = ValkyrieMenu.HEIGHT;
    }

    private ValkyrieEntity v() {
        return menu.valkyrie;
    }

    /** コアの色（発光線）【仮】（HUD・頭上と同じ） */
    private int glow() {
        return ValkyrieHud.glow(v());
    }

    // ------------------------------------------------------------------ 組み立て

    @Override
    protected void init() {
        super.init();
        build();
    }

    private void build() {
        clearWidgets();
        nameBox = null;
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie.tab." + TABS[i]), b -> switchTab(t))
                    .bounds(leftPos + 116 + i * 51, topPos + 4, 50, 14).build()).active = menu.tab != i;
        }
        if (menu.tab == 1) buildStatus();
        if (menu.tab == 2) buildOrders();
    }

    private void switchTab(int tab) {
        menu.tab = tab;
        build();
    }

    private void send(int action, int arg, int value) {
        ModNetwork.CHANNEL.sendToServer(ValkyrieCommandPacket.of(v().getId(), action, arg, value));
    }

    private void buildStatus() {
        nameBox = new EditBox(font, leftPos + 10, topPos + 34, 140, 14, Component.translatable("gui.automata_valkyries.valkyrie.status.name"));
        nameBox.setMaxLength(ValkyrieCommandPacket.MAX_NAME);
        nameBox.setValue(v().hasCustomName() ? v().getCustomName().getString() : "");
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie.status.rename"), b ->
                ModNetwork.CHANNEL.sendToServer(new ValkyrieCommandPacket(v().getId(), ValkyrieCommandPacket.RENAME, 0, 0, nameBox.getValue())))
                .bounds(leftPos + 154, topPos + 34, 40, 14).build());
        // 今の気分（表情。機能停止の顔は選べない）
        for (int i = 0; i < ValkyrieEntity.EXPRESSIONS.length - 1; i++) {
            int e = i;
            addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie.expression." + ValkyrieEntity.EXPRESSIONS[i]),
                    b -> send(ValkyrieCommandPacket.EXPRESSION, 0, e)).bounds(leftPos + 10 + (i % 4) * 48, topPos + 196 + (i / 4) * 16, 46, 14).build());
        }
    }

    private void buildOrders() {
        int x = leftPos + 10, y = topPos + 34;
        ValkyrieEntity.Mode[] modes = ValkyrieEntity.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            int m = i;
            addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie.mode." + modes[i].name().toLowerCase(Locale.ROOT)),
                    b -> send(ValkyrieCommandPacket.MODE, 0, m)).bounds(x + i * 74, y, 72, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.automata_valkyries.valkyrie.orders.set_home"),
                b -> send(ValkyrieCommandPacket.SET_HOME, 0, 0)).bounds(x + 190, y + 22, 106, 14).build());
        int row = y + 44;
        stepper(ValkyrieOrders.Field.DEFEND_RADIUS, 2, row);
        stepper(ValkyrieOrders.Field.PATROL_RADIUS, 4, row + 16);
        stepper(ValkyrieOrders.Field.CHASE, 2, row + 32);
        stepper(ValkyrieOrders.Field.KEEP, 1, row + 48);
        stepper(ValkyrieOrders.Field.RETREAT_HEALTH, 5, row + 64);
        stepper(ValkyrieOrders.Field.RETREAT_POWER, 5, row + 80);
        cycler(ValkyrieOrders.Field.POLICY, ValkyrieOrders.Policy.values().length, row + 100);
        // お掃除の除外リスト: 「＋」でカーソル（無ければ手）に持っている物を足す、枠をクリックで外す（段階 4）
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ValkyrieCommandPacket.EXCLUDE_ADD, 0, 0))
                .bounds(EXCLUDE_X0() + 18 * ValkyrieOrders.MAX_EXCLUDED + 4, row + 143, 14, 14).build());
        cycler(ValkyrieOrders.Field.PRIORITY, ValkyrieOrders.Priority.values().length, row + 116);
        cycler(ValkyrieOrders.Field.MELEE_ON_EMPTY, 2, row + 132);
    }

    /** [-] 値 [+] の行 */
    private void stepper(ValkyrieOrders.Field field, int step, int y) {
        int x = leftPos + 150;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> send(ValkyrieCommandPacket.ORDER, field.ordinal(), menu.order(field) - step))
                .bounds(x, y, 14, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ValkyrieCommandPacket.ORDER, field.ordinal(), menu.order(field) + step))
                .bounds(x + 60, y, 14, 14).build());
    }

    /** 押すたびに次の選択肢へ変わる行 */
    private void cycler(ValkyrieOrders.Field field, int count, int y) {
        addRenderableWidget(Button.builder(Component.literal(">"), b -> send(ValkyrieCommandPacket.ORDER, field.ordinal(), (menu.order(field) + 1) % count))
                .bounds(leftPos + 150, y, 14, 14).build());
    }

    // ------------------------------------------------------------------ 描画

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (menu.tab == 3) renderTooltip(g, mouseX, mouseY); // 持ち物タブでも物の説明を出す
        if (menu.tab == 0) {
            renderTooltip(g, mouseX, mouseY);
            Slot hovered = hoveredSlot;
            if (hovered instanceof ValkyrieMenu.EquipmentSlotUi slot && !slot.hasItem() && menu.getCarried().isEmpty()) {
                Component name = Component.translatable("gui.automata_valkyries.valkyrie.slot." + slotKey(slot.index));
                if (ValkyrieInventory.unimplemented(slot.index)) {
                    g.renderComponentTooltip(font, java.util.List.of(name, Component.translatable("gui.automata_valkyries.valkyrie.slot.unimplemented")
                            .withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), mouseX, mouseY);
                } else {
                    g.renderTooltip(font, name, mouseX, mouseY);
                }
            }
        }
    }

    private static String slotKey(int index) {
        if (index >= ValkyrieInventory.AMMO) return "ammo";
        if (index >= ValkyrieInventory.CHIP) return "chip";
        return switch (index) {
            case ValkyrieInventory.HEAD -> "head";
            case ValkyrieInventory.CHEST -> "chest";
            case ValkyrieInventory.LEGS -> "legs";
            case ValkyrieInventory.BACKPACK -> "backpack";
            case ValkyrieInventory.MAIN_HAND -> "main_hand";
            case ValkyrieInventory.OFF_HAND -> "off_hand";
            default -> "core";
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, glow = glow();
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        frame(g, x, y, imageWidth, imageHeight, glow);
        g.fill(x + 6, y + 20, x + imageWidth - 6, y + 21, (glow & 0x00FFFFFF) | 0x80000000);
        g.drawString(font, v().getDisplayName(), x + 8, y + 7, glow, false);
        switch (menu.tab) {
            case 0 -> renderEquipment(g, x, y, glow, mouseX, mouseY);
            case 1 -> renderStatus(g, x, y, glow);
            case 3 -> renderItems(g, x, y, glow);
            default -> renderOrders(g, x, y, glow);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 題名は renderBg で描く
    }

    /** 角ばった枠: 細い線と、角だけ明るい短い線 */
    private static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        int dim = (color & 0x00FFFFFF) | 0x50000000;
        g.renderOutline(x, y, w, h, dim);
        int c = 8;
        g.fill(x, y, x + c, y + 1, color);
        g.fill(x, y, x + 1, y + c, color);
        g.fill(x + w - c, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + c, color);
        g.fill(x, y + h - 1, x + c, y + h, color);
        g.fill(x, y + h - c, x + 1, y + h, color);
        g.fill(x + w - c, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - c, x + w, y + h, color);
    }

    /** 見えている枠の下地と縁（換装タブ・持ち物タブ） */
    private void drawSlots(GuiGraphics g, int x, int y, int glow) {
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean locked = s instanceof ValkyrieMenu.EquipmentSlotUi e && ValkyrieInventory.unimplemented(e.index);
            g.fill(sx, sy, sx + 18, sy + 18, SLOT);
            g.renderOutline(sx, sy, 18, 18, locked ? 0x40FFFFFF : (glow & 0x00FFFFFF) | 0x90000000);
            if (locked) {
                g.fill(sx + 5, sy + 8, sx + 13, sy + 10, 0x40FFFFFF);
            }
        }
    }

    private void renderEquipment(GuiGraphics g, int x, int y, int glow, int mouseX, int mouseY) {
        drawSlots(g, x, y, glow);
        // 3D モデル（ドラッグで回転）
        g.fill(x + MODEL_X, y + MODEL_Y, x + MODEL_X + MODEL_W, y + MODEL_Y + MODEL_H, PANEL);
        frame(g, x + MODEL_X, y + MODEL_Y, MODEL_W, MODEL_H, glow);
        renderModel(g, x + MODEL_X + MODEL_W / 2, y + MODEL_Y + MODEL_H - 8);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.slot.ammo_label", v().getBody().ammoSlots), x + 8, y + ValkyrieMenu.AMMO_Y - 10, DIM, false);
        // 数値
        int sx = x + STATS_X, sy = y + 26, w = imageWidth - STATS_X - 10;
        g.fill(sx - 4, sy - 2, sx + w + 4, y + 162, PANEL);
        frame(g, sx - 4, sy - 2, w + 8, y + 162 - sy + 2, glow);
        bar(g, sx, sy, w, "hp", menu.get(ValkyrieMenu.HP), menu.get(ValkyrieMenu.MAX_HP), HP_COLOR, 10);
        bar(g, sx, sy + 20, w, "shield", menu.get(ValkyrieMenu.SHIELD), menu.get(ValkyrieMenu.MAX_SHIELD), SHIELD_COLOR, 10);
        bar(g, sx, sy + 40, w, "power", menu.get(ValkyrieMenu.ENERGY), menu.get(ValkyrieMenu.MAX_ENERGY), POWER_COLOR, 0);
        int ly = sy + 62;
        boolean over = menu.flag(ValkyrieMenu.FLAG_OVERWEIGHT);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.stat.weight", tenths(menu.get(ValkyrieMenu.WEIGHT)),
                tenths(menu.get(ValkyrieMenu.LIMIT))), sx, ly, over ? BAD : TEXT, false);
        compare(g, sx, ly + 12, "attack", menu.get(ValkyrieMenu.ATTACK), menu.get(ValkyrieMenu.BASE_ATTACK), 10);
        compare(g, sx, ly + 24, "defense", menu.get(ValkyrieMenu.DEFENSE), menu.get(ValkyrieMenu.BASE_DEFENSE), 10);
        compare(g, sx, ly + 36, "speed", menu.get(ValkyrieMenu.SPEED), menu.get(ValkyrieMenu.BASE_SPEED), 1000);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.stat.sync", tenths(menu.get(ValkyrieMenu.SYNC))), sx, ly + 50, glow, false);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.stat.fe", menu.get(ValkyrieMenu.FE)), sx + w / 2, ly + 50, DIM, false);
        String state = menu.flag(ValkyrieMenu.FLAG_SHUTDOWN) ? "shutdown" : menu.flag(ValkyrieMenu.FLAG_ASLEEP) ? "asleep" : over ? "overweight" : null;
        if (state != null) g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.state." + state), sx, ly + 64, BAD, false);
    }

    private void renderModel(GuiGraphics g, int cx, int bottom) {
        ValkyrieEntity e = v();
        float body = e.yBodyRot, yaw = e.getYRot(), pitch = e.getXRot(), head = e.yHeadRot, headO = e.yHeadRotO;
        e.yBodyRot = modelYaw;
        e.setYRot(modelYaw);
        e.setXRot(0.0F);
        e.yHeadRot = modelYaw;
        e.yHeadRotO = modelYaw;
        g.enableScissor(leftPos + MODEL_X + 1, topPos + MODEL_Y + 1, leftPos + MODEL_X + MODEL_W - 1, topPos + MODEL_Y + MODEL_H - 1);
        InventoryScreen.renderEntityInInventory(g, cx, bottom, 46, new Quaternionf().rotateZ((float) Math.PI), null, e);
        g.disableScissor();
        e.yBodyRot = body;
        e.setYRot(yaw);
        e.setXRot(pitch);
        e.yHeadRot = head;
        e.yHeadRotO = headO;
    }

    /** 名前・数値・細いバー（scale 10 なら 1/10 の値、0 なら 10 倍した値＝電力） */
    private void bar(GuiGraphics g, int x, int y, int w, String key, int value, int max, int color, int scale) {
        String cur = scale == 10 ? tenths(value) : String.format(Locale.ROOT, "%,d", value * 10);
        String top = scale == 10 ? tenths(max) : String.format(Locale.ROOT, "%,d", max * 10);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.stat." + key), x, y, DIM, false);
        String text = cur + " / " + top;
        g.drawString(font, text, x + w - font.width(text), y, TEXT, false);
        g.fill(x, y + 10, x + w, y + 13, 0x60FFFFFF & 0x30FFFFFF);
        int fill = max <= 0 ? 0 : Mth.clamp(Math.round((float) w * value / max), 0, w);
        g.fill(x, y + 10, x + fill, y + 13, color);
    }

    /** 名前・合計・装備前との差（+ は緑、- は赤） */
    private void compare(GuiGraphics g, int x, int y, String key, int value, int base, int scale) {
        String total = scale == 10 ? tenths(value) : String.format(Locale.ROOT, "%.3f", value / 1000.0F);
        Component line = Component.translatable("gui.automata_valkyries.valkyrie.stat." + key, total);
        g.drawString(font, line, x, y, TEXT, false);
        int diff = value - base;
        if (diff != 0) {
            String d = (diff > 0 ? "+" : "") + (scale == 10 ? tenths(diff) : String.format(Locale.ROOT, "%.3f", diff / 1000.0F));
            g.drawString(font, "(" + d + ")", x + font.width(line) + 4, y, diff > 0 ? GOOD : BAD, false);
        }
    }

    private static String tenths(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0F);
    }

    private void renderStatus(GuiGraphics g, int x, int y, int glow) {
        int lx = x + 10, ly = y + 26;
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.name"), lx, ly, DIM, false);
        ly += 28;
        int body = menu.get(ValkyrieMenu.BODY);
        String personality = com.automatavalkyries.entity.ValkyrieBody.of(body).personality;
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.body", Component.translatable(ValkyrieMenu.bodyKey(body))), lx, ly, TEXT, false);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.personality",
                Component.translatable("gui.automata_valkyries.valkyrie.personality." + personality)), lx, ly + 12, TEXT, false);
        g.drawWordWrap(font, Component.translatable("gui.automata_valkyries.valkyrie.personality." + personality + ".desc"), lx, ly + 24, 186, DIM);
        ly += 52;
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.record"), lx, ly, glow, false);
        int minutes = menu.get(ValkyrieMenu.UPTIME_MIN);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.kills", menu.get(ValkyrieMenu.KILLS)), lx, ly + 12, TEXT, false);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.uptime", minutes / 60, String.format(Locale.ROOT, "%02d", minutes % 60)),
                lx + 96, ly + 12, TEXT, false);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.bosses", menu.bosses.size()), lx, ly + 24, TEXT, false);
        if (menu.bosses.isEmpty()) {
            g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.no_bosses"), lx + 8, ly + 36, DIM, false);
        } else {
            for (int i = 0; i < Math.min(4, menu.bosses.size()); i++) g.drawString(font, "・" + menu.bosses.get(i), lx + 8, ly + 36 + i * 10, DIM, false);
        }
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.mood",
                Component.translatable("gui.automata_valkyries.valkyrie.expression." + ValkyrieEntity.EXPRESSIONS[Math.min(menu.get(ValkyrieMenu.EXPRESSION), ValkyrieEntity.EXPRESSIONS.length - 2)])),
                lx, y + 184, glow, false);
        // 右: 同調率と履歴
        int rx = x + 214, ry = y + 26, rw = imageWidth - 214 - 10;
        g.fill(rx - 4, ry - 2, rx + rw + 4, y + imageHeight - 8, PANEL);
        frame(g, rx - 4, ry - 2, rw + 8, y + imageHeight - 8 - ry + 2, glow);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.sync"), rx, ry, DIM, false);
        String sync = tenths(menu.get(ValkyrieMenu.SYNC)) + "%";
        g.pose().pushPose();
        g.pose().translate(rx, ry + 12, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, sync, 0, 0, glow, false);
        g.pose().popPose();
        g.fill(rx, ry + 32, rx + rw, ry + 35, 0x30FFFFFF);
        g.fill(rx, ry + 32, rx + Math.round(rw * Mth.clamp(menu.get(ValkyrieMenu.SYNC) / 1000.0F, 0.0F, 1.0F)), ry + 35, glow);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.history"), rx, ry + 44, DIM, false);
        if (menu.history.isEmpty()) {
            g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.no_history"), rx, ry + 58, DIM, false);
        }
        for (int i = 0; i < menu.history.size(); i++) {
            ValkyrieEntity.SyncEntry e = menu.history.get(i);
            int line = ry + 58 + i * 22;
            String delta = (e.delta() > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f", e.delta());
            g.drawString(font, delta, rx, line, e.delta() > 0 ? GOOD : BAD, false);
            g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.sync_reason." + e.reason()), rx + 28, line, TEXT, false);
            long minutesAgo = Math.max(0, (menu.openedAt - e.time()) / 1200L);
            g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.status.ago", minutesAgo), rx + 28, line + 10, DIM, false);
        }
    }

    /**
     * 持ち物タブ: 予備の武器（電力が減ると射撃の兵装から持ち替える）と、お掃除・ポーターで拾った物（プレイヤーの持ち物と同じ 3 行 × 9）。
     * 左の列に説明とポーターの運び方・運び先を出す（2026-10-06 ユーザー指定「見やすい UI に」）
     */
    private void renderItems(GuiGraphics g, int x, int y, int glow) {
        int ix = x + ValkyrieMenu.INV_X, right = x + ValkyrieMenu.INV_X + 9 * 18;
        // 予備の武器
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.items.spare"), ix, y + ValkyrieMenu.SPARE_Y - 11, TEXT, false);
        // 拾った物（使っている枠の数を右に）
        int used = 0;
        for (int i = 0; i < ValkyrieInventory.PICKUPS; i++) if (!menu.getSlot(ValkyrieInventory.PICKUP + i).getItem().isEmpty()) used++;
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.items.pickup"), ix, y + ValkyrieMenu.PICKUP_Y - 11, TEXT, false);
        Component count = Component.translatable("gui.automata_valkyries.valkyrie.items.pickup_count", used, ValkyrieInventory.PICKUPS);
        g.drawString(font, count, right - font.width(count), y + ValkyrieMenu.PICKUP_Y - 11, used >= ValkyrieInventory.PICKUPS ? BAD : DIM, false);
        // 拾った物の枠の帯（3 行の区切りが分かるよう、うっすら背景）
        g.fill(ix - 2, y + ValkyrieMenu.PICKUP_Y - 2, right + 1, y + ValkyrieMenu.PICKUP_Y + 3 * 18, PANEL);
        drawSlots(g, x, y, glow); // 持ち物タブにも枠を描く（無くて見づらかった）
        // 左の列: 説明とポーター
        int lx = x + 8, lw = ValkyrieMenu.INV_X - 14;
        g.drawWordWrap(font, Component.translatable("gui.automata_valkyries.valkyrie.items.spare_note"), lx, y + ValkyrieMenu.SPARE_Y - 11, lw, DIM);
        g.drawWordWrap(font, Component.translatable("gui.automata_valkyries.valkyrie.items.pickup_note"), lx, y + ValkyrieMenu.PICKUP_Y, lw, DIM);
        ItemStack porter = v().getInventory().chip(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.PORTER);
        int py = y + ValkyrieMenu.PICKUP_Y + 3 * 18 + 6;
        if (porter.isEmpty()) {
            g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.items.porter_none"), ix, py, DIM, false);
            return;
        }
        int mode = com.automatavalkyries.item.unit.ValkyrieUnits.porterMode(porter);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.items.porter",
                Component.translatable("gui.automata_valkyries.unit.chip.porter.mode." + mode)), ix, py, GOOD, false);
        BlockPos dest = com.automatavalkyries.item.unit.ValkyrieUnits.porterPos(porter, "Dest", null);
        BlockPos src = com.automatavalkyries.item.unit.ValkyrieUnits.porterPos(porter, "Source", null);
        Component where = dest == null ? Component.translatable("gui.automata_valkyries.unit.chip.porter.unset")
                : Component.translatable("gui.automata_valkyries.valkyrie.items.porter_dest", dest.getX(), dest.getY(), dest.getZ());
        if (mode == 1 && src != null) where = Component.translatable("gui.automata_valkyries.valkyrie.items.porter_source", src.getX(), src.getY(), src.getZ()).append(" → ").append(where);
        g.drawString(font, where, ix + 4, py + 10, DIM, false);
    }

    private void renderOrders(GuiGraphics g, int x, int y, int glow) {
        int lx = x + 10, ly = y + 26;
        ValkyrieEntity.Mode mode = ValkyrieEntity.Mode.values()[Mth.clamp(menu.get(ValkyrieMenu.MODE), 0, 3)];
        // 今のモードのボタンの下に線
        g.fill(lx + mode.ordinal() * 74, ly + 26, lx + mode.ordinal() * 74 + 72, ly + 28, glow);
        BlockPos home = menu.home();
        g.drawString(font, home == null ? Component.translatable("gui.automata_valkyries.valkyrie.orders.no_home")
                : Component.translatable("gui.automata_valkyries.valkyrie.orders.home", home.getX(), home.getY(), home.getZ()), lx, ly + 33, TEXT, false);
        int row = ly + 47;
        value(g, lx, row, ValkyrieOrders.Field.DEFEND_RADIUS, "m");
        value(g, lx, row + 16, ValkyrieOrders.Field.PATROL_RADIUS, "m");
        value(g, lx, row + 32, ValkyrieOrders.Field.CHASE, "m");
        value(g, lx, row + 48, ValkyrieOrders.Field.KEEP, "m");
        value(g, lx, row + 64, ValkyrieOrders.Field.RETREAT_HEALTH, "%");
        value(g, lx, row + 80, ValkyrieOrders.Field.RETREAT_POWER, "%");
        choice(g, lx, row + 100, ValkyrieOrders.Field.POLICY, "policy", ValkyrieOrders.Policy.values()[menu.order(ValkyrieOrders.Field.POLICY)].name());
        choice(g, lx, row + 116, ValkyrieOrders.Field.PRIORITY, "priority", ValkyrieOrders.Priority.values()[menu.order(ValkyrieOrders.Field.PRIORITY)].name());
        choice(g, lx, row + 132, ValkyrieOrders.Field.MELEE_ON_EMPTY, "toggle", menu.order(ValkyrieOrders.Field.MELEE_ON_EMPTY) != 0 ? "ON" : "OFF");
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.orders.cleanup"), lx, row + 152, TEXT, false);
        for (int k = 0; k < ValkyrieOrders.MAX_EXCLUDED; k++) {
            int sx = EXCLUDE_X0() + 18 * k, sy = row + 147;
            g.fill(sx, sy, sx + 16, sy + 16, SLOT);
            g.renderOutline(sx - 1, sy - 1, 18, 18, (glow & 0x00FFFFFF) | 0x60000000);
            net.minecraft.world.item.ItemStack stack = menu.excluded(k);
            if (!stack.isEmpty()) g.renderItem(stack, sx, sy);
        }
        g.drawWordWrap(font, Component.translatable("gui.automata_valkyries.valkyrie.orders.note"), x + 240, row, imageWidth - 250, DIM);
    }

    private void value(GuiGraphics g, int x, int y, ValkyrieOrders.Field field, String unit) {
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.orders." + field.name().toLowerCase(Locale.ROOT)), x, y + 3, TEXT, false);
        String text = menu.order(field) + " " + unit;
        g.drawString(font, text, leftPos + 150 + 37 - font.width(text) / 2, y + 3, glowText(), false);
    }

    private void choice(GuiGraphics g, int x, int y, ValkyrieOrders.Field field, String group, String option) {
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie.orders." + field.name().toLowerCase(Locale.ROOT)), x, y + 3, TEXT, false);
        g.drawString(font, Component.translatable("gui.automata_valkyries.valkyrie." + group + "." + option.toLowerCase(Locale.ROOT)), leftPos + 168, y + 3, glowText(), false);
    }

    private int glowText() {
        return glow();
    }

    // ------------------------------------------------------------------ 入力

    /** 除外リストの枠の左端（画面の座標） */
    private int EXCLUDE_X0() {
        return leftPos + 10 + 96;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.tab == 2) { // 除外リストの枠をクリックで外す
            int row = topPos + 26 + 47, sy = row + 147;
            for (int k = 0; k < ValkyrieOrders.MAX_EXCLUDED; k++) {
                int sx = EXCLUDE_X0() + 18 * k;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16 && !menu.excluded(k).isEmpty()) {
                    send(ValkyrieCommandPacket.EXCLUDE_REMOVE, 0, k);
                    return true;
                }
            }
        }
        if (menu.tab == 0 && inModel(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            modelYaw -= (float) dx * 2.5F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean inModel(double mx, double my) {
        return mx >= leftPos + MODEL_X && mx < leftPos + MODEL_X + MODEL_W && my >= topPos + MODEL_Y && my < topPos + MODEL_Y + MODEL_H;
    }

    /** 名前の入力中は、持ち物のキー（E）などで閉じない */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                ModNetwork.CHANNEL.sendToServer(new ValkyrieCommandPacket(v().getId(), ValkyrieCommandPacket.RENAME, 0, 0, nameBox.getValue()));
                nameBox.setFocused(false);
                return true;
            }
            return nameBox.keyPressed(key, scan, modifiers) || nameBox.canConsumeInput();
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
