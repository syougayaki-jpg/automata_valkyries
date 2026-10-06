package com.automatavalkyries.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * 作戦指示タブの設定（SPEC_VALKYRIE_GUI_AI §2.4）。拠点の範囲と交戦規定。効くのは戦闘 AI（段階 3）から。
 * 既定値・上限は【仮】。画面からは {@link #set} で 1 項目ずつ変える（値はサーバーで範囲に収める）
 */
public class ValkyrieOrders {

    /** 交戦の方針 */
    public enum Policy { AGGRESSIVE, RETALIATE, PASSIVE }

    /** 狙う優先順 */
    public enum Priority { NEAREST, LOWEST_HEALTH, BOSS_FIRST, TURRET_FIRST }

    /** 画面から変える項目（順番はパケットの番号） */
    public enum Field {
        DEFEND_RADIUS(4, 48), PATROL_RADIUS(8, 96), POLICY(0, 2), PRIORITY(0, 3), CHASE(4, 64), KEEP(2, 24),
        RETREAT_HEALTH(0, 90), RETREAT_POWER(0, 90), MELEE_ON_EMPTY(0, 1);

        public final int min, max;

        Field(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    public int defendRadius = 12;
    public int patrolRadius = 24;
    public Policy policy = Policy.RETALIATE;
    public Priority priority = Priority.NEAREST;
    /** 持ち主から何 m まで追うか */
    public int chase = 16;
    /** 射撃時に保つ距離の下限（実際は射程の 7 割と比べて長い方。ValkyrieCombat#rangedKeep） */
    public int keep = 6;
    /** HP がこの % を割ったら後退 */
    public int retreatHealth = 30;
    /** 電力がこの % を割ったら後退 */
    public int retreatPower = 20;
    /** 弾切れ時に近接へ移るか */
    public boolean meleeOnEmpty = true;

    public int get(Field field) {
        return switch (field) {
            case DEFEND_RADIUS -> defendRadius;
            case PATROL_RADIUS -> patrolRadius;
            case POLICY -> policy.ordinal();
            case PRIORITY -> priority.ordinal();
            case CHASE -> chase;
            case KEEP -> keep;
            case RETREAT_HEALTH -> retreatHealth;
            case RETREAT_POWER -> retreatPower;
            case MELEE_ON_EMPTY -> meleeOnEmpty ? 1 : 0;
        };
    }

    public void set(Field field, int value) {
        int v = Mth.clamp(value, field.min, field.max);
        switch (field) {
            case DEFEND_RADIUS -> defendRadius = v;
            case PATROL_RADIUS -> patrolRadius = v;
            case POLICY -> policy = Policy.values()[v];
            case PRIORITY -> priority = Priority.values()[v];
            case CHASE -> chase = v;
            case KEEP -> keep = v;
            case RETREAT_HEALTH -> retreatHealth = v;
            case RETREAT_POWER -> retreatPower = v;
            case MELEE_ON_EMPTY -> meleeOnEmpty = v != 0;
        }
    }

    // ---- お掃除の除外リスト（段階 4、2026-10-06。SPEC_VALKYRIE_GUI_AI §2.4「お掃除の除外リスト」）

    /** 除外リストの数の上限 */
    public static final int MAX_EXCLUDED = 9;
    /** お掃除で拾わないアイテム */
    public final java.util.List<net.minecraft.world.item.Item> excluded = new java.util.ArrayList<>();

    public boolean excludes(net.minecraft.world.item.ItemStack stack) {
        return excluded.contains(stack.getItem());
    }

    /** 足す（既にあるか、いっぱいなら false） */
    public boolean exclude(net.minecraft.world.item.Item item) {
        if (item == net.minecraft.world.item.Items.AIR || excluded.contains(item) || excluded.size() >= MAX_EXCLUDED) return false;
        excluded.add(item);
        return true;
    }

    public void unexclude(int index) {
        if (index >= 0 && index < excluded.size()) excluded.remove(index);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Field f : Field.values()) tag.putInt(f.name(), get(f));
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (net.minecraft.world.item.Item item : excluded) {
            net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
            if (id != null) list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        }
        tag.put("Excluded", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        for (Field f : Field.values()) if (tag.contains(f.name())) set(f, tag.getInt(f.name()));
        excluded.clear();
        net.minecraft.nbt.ListTag list = tag.getList("Excluded", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size() && excluded.size() < MAX_EXCLUDED; i++) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(list.getString(i));
            net.minecraft.world.item.Item item = id == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR && !excluded.contains(item)) excluded.add(item);
        }
    }
}
