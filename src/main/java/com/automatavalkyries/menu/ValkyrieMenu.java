package com.automatavalkyries.menu;

import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 戦術兵姫のメニューの中身（SPEC_VALKYRIE_GUI_AI §2。段階 2、2026-10-05）。タブは画面側（換装・状態・作戦指示）で、
 * 換装タブのときだけ枠が出る。数値は {@link ContainerData} で送る（バニラは 16 ビットで送るので、大きい値は縮めるか 2 つに分ける）。
 * 状態タブの一覧（同調率の履歴・一緒に倒したボス）は開くときに一緒に送る。操作は {@code network/ValkyrieCommandPacket}
 */
public class ValkyrieMenu extends AbstractContainerMenu {

    /** 画面の大きさと枠の位置（{@code client/ValkyrieScreen} と合わせる） */
    public static final int WIDTH = 320, HEIGHT = 250, INV_X = 80, INV_Y = 170, AMMO_Y = 146;
    /** 持ち物タブの予備の武器・拾った物の行 */
    public static final int SPARE_Y = 50, PICKUP_Y = 110;
    public static final int[][] SLOT_POS = new int[ValkyrieInventory.SIZE][];

    static {
        SLOT_POS[ValkyrieInventory.HEAD] = new int[]{8, 26};
        SLOT_POS[ValkyrieInventory.CHEST] = new int[]{8, 48};
        SLOT_POS[ValkyrieInventory.LEGS] = new int[]{8, 70};
        SLOT_POS[ValkyrieInventory.BACKPACK] = new int[]{8, 92};
        SLOT_POS[ValkyrieInventory.MAIN_HAND] = new int[]{102, 26};
        SLOT_POS[ValkyrieInventory.OFF_HAND] = new int[]{102, 48};
        SLOT_POS[ValkyrieInventory.CORE] = new int[]{102, 70};
        for (int i = 0; i < ValkyrieInventory.CHIPS; i++) SLOT_POS[ValkyrieInventory.CHIP + i] = new int[]{37 + 18 * i, 124};
        for (int i = 0; i < ValkyrieInventory.MAX_AMMO; i++) SLOT_POS[ValkyrieInventory.AMMO + i] = new int[]{8 + 18 * i, AMMO_Y};
        for (int i = 0; i < ValkyrieInventory.SPARES; i++) SLOT_POS[ValkyrieInventory.SPARE + i] = new int[]{INV_X + 18 * i, SPARE_Y};
        for (int i = 0; i < ValkyrieInventory.PICKUPS; i++) SLOT_POS[ValkyrieInventory.PICKUP + i] = new int[]{INV_X + 18 * i, PICKUP_Y};
    }

    // ---- 数値の番号
    public static final int HP = 0, MAX_HP = 1, SHIELD = 2, MAX_SHIELD = 3, ENERGY = 4, MAX_ENERGY = 5, WEIGHT = 6, LIMIT = 7,
            ATTACK = 8, BASE_ATTACK = 9, DEFENSE = 10, BASE_DEFENSE = 11, SPEED = 12, BASE_SPEED = 13, SYNC = 14, FE = 15,
            MODE = 16, BODY = 17, EXPRESSION = 18, KILLS = 19, UPTIME_MIN = 20, FLAGS = 21, ORDERS = 22,
            HOME = ORDERS + ValkyrieOrders.Field.values().length, EXCLUDED = HOME + 6, DATA = EXCLUDED + ValkyrieOrders.MAX_EXCLUDED;
    public static final int FLAG_SHUTDOWN = 1, FLAG_ASLEEP = 2, FLAG_HOME = 4, FLAG_OVERWEIGHT = 8;

    public final ValkyrieEntity valkyrie;
    private final ContainerData data;
    /** 今のタブ（画面だけが使う。0 = 換装、1 = 状態、2 = 作戦指示、3 = 持ち物） */
    public int tab;
    /** 開いたときの同調率の履歴（増減 / 理由 / 何 Tick 前）と一緒に倒したボス */
    public final List<ValkyrieEntity.SyncEntry> history = new ArrayList<>();
    public final List<String> bosses = new ArrayList<>();
    public long openedAt;

    /** クライアント: 開くときのデータから */
    public ValkyrieMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, find(inventory.player, buf.readVarInt()), new net.minecraft.world.inventory.SimpleContainerData(DATA));
        openedAt = buf.readLong();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) history.add(new ValkyrieEntity.SyncEntry(buf.readFloat(), buf.readUtf(64), buf.readLong()));
        n = buf.readVarInt();
        for (int i = 0; i < n; i++) bosses.add(buf.readUtf(128));
    }

    /** サーバー */
    public ValkyrieMenu(int id, Inventory inventory, ValkyrieEntity valkyrie) {
        this(id, inventory, valkyrie, new Data(valkyrie));
    }

    private ValkyrieMenu(int id, Inventory inventory, ValkyrieEntity valkyrie, ContainerData data) {
        super(ModEntities.VALKYRIE_MENU.get(), id);
        this.valkyrie = valkyrie;
        this.data = data;
        ValkyrieInventory equipment = valkyrie.getInventory();
        for (int i = 0; i < ValkyrieInventory.SIZE; i++) addSlot(new EquipmentSlotUi(equipment, i, SLOT_POS[i][0], SLOT_POS[i][1]));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new PlayerSlotUi(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new PlayerSlotUi(inventory, col, INV_X + col * 18, INV_Y + 58));
        addDataSlots(data);
    }

    private static ValkyrieEntity find(Player player, int entityId) {
        Entity e = player.level().getEntity(entityId);
        return e instanceof ValkyrieEntity v ? v : new ValkyrieEntity(ModEntities.VALKYRIE.get(), player.level());
    }

    /** 開くときに一緒に送るもの（{@link ValkyrieEntity#openMenu}） */
    public static void writeOpenData(FriendlyByteBuf buf, ValkyrieEntity v) {
        buf.writeVarInt(v.getId());
        buf.writeLong(v.level().getGameTime());
        buf.writeVarInt(v.syncHistory.size());
        for (ValkyrieEntity.SyncEntry e : v.syncHistory) {
            buf.writeFloat(e.delta());
            buf.writeUtf(e.reason(), 64);
            buf.writeLong(e.time());
        }
        buf.writeVarInt(v.bosses.size());
        for (String b : v.bosses) buf.writeUtf(b, 128);
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean flag(int flag) {
        return (data.get(FLAGS) & flag) != 0;
    }

    /** お掃除の除外リストの k 番目（空きなら空のスタック。画面で絵を出す） */
    public net.minecraft.world.item.ItemStack excluded(int k) {
        int id = data.get(EXCLUDED + k) - 1;
        if (id < 0) return net.minecraft.world.item.ItemStack.EMPTY;
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id));
    }

    public int order(ValkyrieOrders.Field field) {
        return data.get(ORDERS + field.ordinal());
    }

    /** 拠点の位置（無ければ null） */
    public BlockPos home() {
        if (!flag(FLAG_HOME)) return null;
        return new BlockPos(join(HOME), join(HOME + 2), join(HOME + 4));
    }

    private int join(int index) {
        return (data.get(index) << 16) | (data.get(index + 1) & 0xFFFF);
    }

    @Override
    public boolean stillValid(Player player) {
        return valkyrie.getInventory().stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = ValkyrieInventory.SIZE, end = slots.size();
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, end, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = moveItemStackTo(stack, ValkyrieInventory.MAIN_HAND, ValkyrieInventory.CORE + 1, false)
                    || (tab == 3 ? moveItemStackTo(stack, ValkyrieInventory.SPARE, ValkyrieInventory.SIZE, false)
                    : moveItemStackTo(stack, ValkyrieInventory.AMMO, ValkyrieInventory.SPARE, false));
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    // ------------------------------------------------------------------ 枠

    /** 兵姫の装備の枠。換装タブのときだけ出る。弾薬庫は素体タイプの枠数まで */
    public class EquipmentSlotUi extends Slot {
        public final int index;

        EquipmentSlotUi(ValkyrieInventory container, int index, int x, int y) {
            super(container, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return valkyrie.getInventory().accepts(index, stack);
        }

        @Override
        public int getMaxStackSize() {
            if (index >= ValkyrieInventory.SPARE && index < ValkyrieInventory.PICKUP) return 1;
            return index >= ValkyrieInventory.AMMO ? 64 : 1;
        }

        @Override
        public boolean isActive() {
            if (index >= ValkyrieInventory.SPARE) return tab == 3; // 持ち物タブ
            if (tab != 0) return false;
            return index < ValkyrieInventory.AMMO || index - ValkyrieInventory.AMMO < valkyrie.getBody().ammoSlots;
        }
    }

    /** プレイヤーの持ち物の枠（換装タブのときだけ出る） */
    public class PlayerSlotUi extends Slot {
        PlayerSlotUi(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return tab == 0 || tab == 3;
        }
    }

    // ------------------------------------------------------------------ 数値（サーバー）

    private static final class Data implements ContainerData {
        private final ValkyrieEntity v;

        Data(ValkyrieEntity v) {
            this.v = v;
        }

        @Override
        public int get(int index) {
            if (index >= ORDERS && index < HOME) return v.orders.get(ValkyrieOrders.Field.values()[index - ORDERS]);
            if (index >= EXCLUDED && index < DATA) { // お掃除の除外リスト（アイテムの番号 + 1、0 = 空き）
                int k = index - EXCLUDED;
                return k < v.orders.excluded.size() ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(v.orders.excluded.get(k)) + 1 : 0;
            }
            if (index >= HOME && index < EXCLUDED) {
                BlockPos home = v.getHome();
                if (home == null) return 0;
                int k = index - HOME, value = k < 2 ? home.getX() : k < 4 ? home.getY() : home.getZ();
                return k % 2 == 0 ? value >> 16 : value & 0xFFFF;
            }
            return switch (index) {
                case HP -> tenths(DirectRead.health(v));
                case MAX_HP -> tenths(v.getMaxHealth());
                case SHIELD -> tenths(v.getShield());
                case MAX_SHIELD -> tenths(v.getMaxShield());
                case ENERGY -> v.getEnergy() / 10;
                case MAX_ENERGY -> v.maxEnergy() / 10;
                case WEIGHT -> tenths(v.carriedWeight());
                case LIMIT -> tenths(v.weightLimit());
                case ATTACK -> tenths(v.attackValue());
                case BASE_ATTACK -> tenths(v.baseAttack());
                case DEFENSE -> tenths(v.defenseValue());
                case BASE_DEFENSE -> tenths(v.baseDefense());
                case SPEED -> Math.round(v.speedValue() * 1000.0F);
                case BASE_SPEED -> Math.round(v.baseSpeed() * 1000.0F);
                case SYNC -> tenths(v.getSyncRate());
                case FE -> v.fePerTick();
                case MODE -> v.getMode().ordinal();
                case BODY -> v.getBody().ordinal();
                case EXPRESSION -> v.getExpression();
                case KILLS -> Math.min(v.kills, Short.MAX_VALUE);
                case UPTIME_MIN -> (int) Math.min(v.uptime / 1200L, Short.MAX_VALUE);
                case FLAGS -> (v.isShutdown() ? FLAG_SHUTDOWN : 0) | (v.isAsleep() ? FLAG_ASLEEP : 0)
                        | (v.getHome() != null ? FLAG_HOME : 0) | (v.overweight() ? FLAG_OVERWEIGHT : 0);
                default -> 0;
            };
        }

        private static int tenths(float value) {
            return Float.isFinite(value) ? (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value * 10.0F))) : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    }

    /** 素体タイプの表示名の翻訳キー */
    public static String bodyKey(int ordinal) {
        return "gui.automata_valkyries.valkyrie.body." + ValkyrieBody.of(ordinal).name().toLowerCase(java.util.Locale.ROOT);
    }
}
