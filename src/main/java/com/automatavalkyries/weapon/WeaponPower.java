package com.automatavalkyries.weapon;

import com.automatavalkyries.item.ItemEnergy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 兵装の電力（兵器ユニット設計書 §1「内蔵バッテリー＋コア」）。兵装は内蔵バッテリー（スタックの NBT「Energy」）から使い、
 * 足りない分はプレイヤーの持ち物のルミナルコアから放電レートまで毎 Tick 流し込む。兵姫はコアスロットから引く（兵姫の実装後）。
 * クリエイティブのプレイヤーは電力を使わない。
 */
public final class WeaponPower {

    private WeaponPower() {}

    /** 内蔵バッテリーが満タンでなければ、持ち物のルミナルコアから足す */
    public static void topUp(Entity holder, ItemStack weapon, int capacity) {
        int missing = capacity - ItemEnergy.get(weapon);
        if (missing <= 0) return;
        // 戦術兵姫は自分の電力から（段階 3、2026-10-05。兵姫の電力はコアから補充される）。
        // 兵姫の電力の半分より上の分だけ使う（容量の大きい兵装が兵姫の電力を毎 Tick 空近くまで吸い、「電力低下」で後退し続けていた）
        if (holder instanceof com.automatavalkyries.entity.ValkyrieEntity v) {
            int taken = Math.min(missing, Math.max(0, v.getEnergy() - v.maxEnergy() / 2));
            if (taken > 0) {
                v.setEnergy(v.getEnergy() - Math.round(taken * com.automatavalkyries.entity.ai.ValkyrieUnitEffects.powerScale(v))); // ルミナスフレームは -30%
                ItemEnergy.set(weapon, ItemEnergy.get(weapon) + taken);
            }
            return;
        }
        if (!(holder instanceof Player player)) return;
        for (int i = 0; i < player.getInventory().getContainerSize() && missing > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LuminalCoreItem core) {
                int taken = core.extract(stack, missing);
                if (taken > 0) {
                    ItemEnergy.set(weapon, ItemEnergy.get(weapon) + taken);
                    missing -= taken;
                }
            }
        }
    }

    /** amount を使えたら true（クリエイティブは常に true で減らさない） */
    public static boolean use(LivingEntity user, ItemStack weapon, int amount) {
        if (user instanceof Player player && player.getAbilities().instabuild) return true;
        return ItemEnergy.use(weapon, amount);
    }

    public static boolean has(LivingEntity user, ItemStack weapon, int amount) {
        if (user instanceof Player player && player.getAbilities().instabuild) return true;
        return ItemEnergy.get(weapon) >= amount;
    }
}
