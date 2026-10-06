package com.automatavalkyries.entity;

import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.LuminalCoreItem;
import com.automatavalkyries.weapon.TacticalWeaponItem;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 戦術兵姫の装備の入れ物（換装画面のハードポイント。SPEC_VALKYRIE_GUI_AI §2.2）。
 * 手の 2 つはバニラの手の装備（描画・攻撃力の属性がそのまま効く）、コアは兵姫のコアの欄へつなぐ。残りはここに持つ。
 * <p>外装（頭・胴・脚）・背部ユニット・ロジックチップはまだ入る物が無い（2026-10-05 ユーザー指定「枠だけ作る」）。
 * 内蔵弾薬庫は弾薬・修理剤専用で、枠数は素体タイプで決まる（{@link ValkyrieBody#ammoSlots}）</p>
 */
public class ValkyrieInventory implements Container {

    public static final int HEAD = 0, CHEST = 1, LEGS = 2, BACKPACK = 3, MAIN_HAND = 4, OFF_HAND = 5, CORE = 6;
    public static final int CHIP = 7, CHIPS = 3;
    public static final int AMMO = CHIP + CHIPS, MAX_AMMO = 8;
    /** 持ち物タブ（段階 4、2026-10-06 ユーザー指定「拾った物が分かるようにインベントリ」）: 予備の武器（電力が減ると持ち替える）と、お掃除で拾った物 */
    public static final int SPARE = AMMO + MAX_AMMO, SPARES = 9, PICKUP = SPARE + SPARES, PICKUPS = 9;
    public static final int SIZE = PICKUP + PICKUPS;

    private final ValkyrieEntity owner;
    /** 手とコア以外（頭・胴・脚・背部・チップ・弾薬庫）。手とコアの位置は使わない */
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public ValkyrieInventory(ValkyrieEntity owner) {
        this.owner = owner;
    }

    /** まだ入る物が無い枠（外装・背部・チップ） */
    public static boolean unimplemented(int slot) {
        return false; // 外装・背部・チップも入る（2026-10-06）
    }

    /** 外装・背部の枠（見た目に出すのでクライアントへ送る） */
    public static boolean visibleUnit(int slot) {
        return slot == HEAD || slot == CHEST || slot == LEGS || slot == BACKPACK;
    }

    /** その枠に入れてよいか（弾薬庫の枠数・両手持ちの兵装と左手の取り合いも見る） */
    public boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (unimplemented(slot)) return false;
        if (slot == MAIN_HAND) {
            if (!(stack.getItem() instanceof TacticalWeaponItem weapon)) return false;
            return !weapon.twoHanded() || getItem(OFF_HAND).isEmpty();
        }
        if (slot == OFF_HAND) {
            // 盾・リペアインジェクター・片手の兵装（全体仕様書 §3.3）。右手が両手持ちなら空けておく
            if (getItem(MAIN_HAND).getItem() instanceof TacticalWeaponItem main && main.twoHanded()) return false;
            return stack.getItem() instanceof ShieldItem || stack.is(ModWeapons.REPAIR_INJECTOR.get())
                    || (stack.getItem() instanceof TacticalWeaponItem w && !w.twoHanded());
        }
        if (slot == CORE) return stack.getItem() instanceof LuminalCoreItem;
        if (slot >= PICKUP) return true; // 拾った物（何でも。プレイヤーが入れた物も片付けに運ぶ）
        if (slot >= SPARE) return isSpareWeapon(stack);
        if (slot >= AMMO) return slot - AMMO < owner.getBody().ammoSlots && isAmmo(stack);
        // 外装フレーム: 部位の合う物
        if (slot == HEAD || slot == CHEST || slot == LEGS) {
            if (!(stack.getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem frame)) return false;
            return switch (slot) {
                case HEAD -> frame.part == com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.HEAD;
                case LEGS -> frame.part == com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.LEGS;
                default -> frame.part == com.automatavalkyries.item.unit.ValkyrieUnits.FramePart.CHEST;
            };
        }
        if (slot == BACKPACK) return stack.getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem;
        if (slot >= CHIP && slot < CHIP + CHIPS) { // ロジックチップ（同じ種類は 1 つまで）
            if (!(stack.getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem chip)) return false;
            for (int i = CHIP; i < CHIP + CHIPS; i++) {
                if (i != slot && getItem(i).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem other
                        && (other.kind == chip.kind || (other.kind.role && chip.kind.role))) return false; // 役割のチップは 1 つだけ
            }
            return true;
        }
        return false;
    }

    /** 外装（部位の枠）の物。無ければ null */
    @org.jetbrains.annotations.Nullable
    public com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem frame(int slot) {
        return getItem(slot).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem f ? f : null;
    }

    /** 外装の一式の型（頭・胴・脚が同じ型。ヴァルキリー系は Mk を混ぜても一式で、一番低い Mk。そろっていなければ null） */
    @org.jetbrains.annotations.Nullable
    public com.automatavalkyries.item.unit.ValkyrieUnits.FrameType frameSet() {
        var chest = frame(CHEST);
        if (chest == null) return null;
        var head = frame(HEAD);
        var legs = frame(LEGS);
        if (head == null || legs == null) return null;
        if (head.type.valkyrie() && chest.type.valkyrie() && legs.type.valkyrie()) {
            var low = chest.type;
            if (head.type.tier < low.tier) low = head.type;
            if (legs.type.tier < low.tier) low = legs.type;
            return low;
        }
        return head.type == chest.type && legs.type == chest.type ? chest.type : null;
    }

    @org.jetbrains.annotations.Nullable
    public com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind backpack() {
        return getItem(BACKPACK).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem b ? b.kind : null;
    }

    /** 挿しているそのロジックチップ（無ければ空） */
    public ItemStack chip(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind kind) {
        for (int i = CHIP; i < CHIP + CHIPS; i++) {
            if (getItem(i).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem c && c.kind == kind) return getItem(i);
        }
        return ItemStack.EMPTY;
    }

    /** そのロジックチップを挿しているか */
    public boolean hasChip(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind kind) {
        for (int i = CHIP; i < CHIP + CHIPS; i++) if (getItem(i).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.ChipItem c && c.kind == kind) return true;
        return false;
    }

    /** 予備の武器の枠に入る物（兵装・剣や斧などの道具・弓・盾） */
    public static boolean isSpareWeapon(ItemStack stack) {
        return stack.getItem() instanceof TacticalWeaponItem || stack.getItem() instanceof net.minecraft.world.item.TieredItem
                || stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem || stack.getItem() instanceof ShieldItem;
    }

    // ---- 拾った物（お掃除）

    /** 拾った物の枠へ入れる（同じ物に重ねてから空きへ）。入りきらなかった分を返す */
    public ItemStack addPickup(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = PICKUP; i < PICKUP + PICKUPS && !rest.isEmpty(); i++) {
            ItemStack c = items.get(i);
            if (!c.isEmpty() && ItemStack.isSameItemSameTags(c, rest) && c.getCount() < c.getMaxStackSize()) {
                int move = Math.min(rest.getCount(), c.getMaxStackSize() - c.getCount());
                c.grow(move);
                rest.shrink(move);
            }
        }
        for (int i = PICKUP; i < PICKUP + PICKUPS && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest);
                rest = ItemStack.EMPTY;
            }
        }
        return rest;
    }

    public boolean pickupFull() {
        for (int i = PICKUP; i < PICKUP + PICKUPS; i++) if (items.get(i).isEmpty()) return false;
        return true;
    }

    public boolean hasPickup() {
        for (int i = PICKUP; i < PICKUP + PICKUPS; i++) if (!items.get(i).isEmpty()) return true;
        return false;
    }

    /** 拾った物をチェスト（handler）へ入れる（入りきらない物は残す） */
    public void deliverPickups(net.minecraftforge.items.IItemHandler handler) {
        for (int i = PICKUP; i < PICKUP + PICKUPS; i++) {
            if (!items.get(i).isEmpty()) items.set(i, net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, items.get(i), false));
        }
    }

    /** 弾薬・修理剤（このMODの ammo_* とナノマシン溶液） */
    public static boolean isAmmo(ItemStack stack) {
        if (stack.is(ModWeapons.NANO_SOLUTION.get())) return true;
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(com.automatavalkyries.AutomataValkyries.MODID) && id.getPath().startsWith("ammo_");
    }

    // ------------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return switch (slot) {
            case MAIN_HAND -> owner.getItemBySlot(EquipmentSlot.MAINHAND);
            case OFF_HAND -> owner.getItemBySlot(EquipmentSlot.OFFHAND);
            case CORE -> owner.getCore();
            default -> slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        setItem(slot, current); // 手・コアは入れ直して反映させる
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        switch (slot) {
            case MAIN_HAND -> owner.setItemSlot(EquipmentSlot.MAINHAND, stack);
            case OFF_HAND -> owner.setItemSlot(EquipmentSlot.OFFHAND, stack);
            case CORE -> owner.setCore(stack);
            default -> {
                if (slot >= 0 && slot < SIZE) items.set(slot, stack);
                if (visibleUnit(slot)) owner.syncUnit(slot, stack); // 外装・背部の見た目
            }
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        owner.onEquipmentChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return owner.isAlive() && !owner.isRemoved() && player.getUUID().equals(owner.getOwnerUUID()) && player.distanceToSqr(owner) < 64.0;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SIZE; i++) setItem(i, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ 保存（手はバニラ、コアは兵姫が保存する）

    public ListTag save() {
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            if (i == MAIN_HAND || i == OFF_HAND || i == CORE || items.get(i).isEmpty()) continue;
            CompoundTag entry = items.get(i).save(new CompoundTag());
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        return list;
    }

    public void load(ListTag list) {
        for (int i = 0; i < SIZE; i++) if (i != MAIN_HAND && i != OFF_HAND && i != CORE) items.set(i, ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < SIZE && slot != MAIN_HAND && slot != OFF_HAND && slot != CORE) items.set(slot, ItemStack.of(entry));
        }
        for (int s : new int[]{HEAD, CHEST, LEGS, BACKPACK}) owner.syncUnit(s, items.get(s));
    }

    /** 弾薬庫の枠を超えた物（素体タイプが変わった等）を外へ出すための一覧 */
    public NonNullList<ItemStack> overflowAmmo() {
        NonNullList<ItemStack> out = NonNullList.create();
        for (int i = AMMO + owner.getBody().ammoSlots; i < AMMO + MAX_AMMO; i++) {
            if (!items.get(i).isEmpty()) {
                out.add(items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }
        return out;
    }
}
