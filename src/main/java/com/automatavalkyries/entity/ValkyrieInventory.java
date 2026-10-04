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
    public static final int SIZE = AMMO + MAX_AMMO;

    private final ValkyrieEntity owner;
    /** 手とコア以外（頭・胴・脚・背部・チップ・弾薬庫）。手とコアの位置は使わない */
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public ValkyrieInventory(ValkyrieEntity owner) {
        this.owner = owner;
    }

    /** まだ入る物が無い枠（外装・背部・チップ） */
    public static boolean unimplemented(int slot) {
        return slot == HEAD || slot == CHEST || slot == LEGS || slot == BACKPACK || (slot >= CHIP && slot < CHIP + CHIPS);
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
        if (slot >= AMMO) return slot - AMMO < owner.getBody().ammoSlots && isAmmo(stack);
        return false;
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
    }

    /** 弾薬庫の枠を超えた物（素体タイプが変わった等）を外へ出すための一覧 */
    public NonNullList<ItemStack> overflowAmmo() {
        NonNullList<ItemStack> out = NonNullList.create();
        for (int i = AMMO + owner.getBody().ammoSlots; i < SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                out.add(items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }
        return out;
    }
}
