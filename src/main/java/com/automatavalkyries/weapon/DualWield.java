package com.automatavalkyries.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 二刀流（2026-10-06 ユーザー指定「片手武器をオフハンド、二つ装備した場合両方使えるように。右と左クリック両方ある場合は、そのハンドのクリックとシフトクリックに変化」）。
 * <ul>
 *   <li>二刀流 = 両手に武器（兵装・剣や斧）で、左手の物が片手持ち、右手の物も両手持ちでない</li>
 *   <li>プレイヤー: 左クリック = 右手の攻撃、シフト＋左クリック = 右手の技。右クリック = 左手の攻撃（攻撃が無ければ技）、シフト＋右クリック = 左手の技
 *       （{@code client/DualWieldClient}・{@code network/DualWieldPacket}）</li>
 *   <li>戦術兵姫: 左手の武器も間合い・間隔を見て使う（{@code ValkyrieCombat.Fight}）</li>
 * </ul>
 * 兵装の処理はどれも右手（利き手）で持つ前提なので、左手の物を使う間だけ両手の物と攻撃力・攻撃速度を入れ替える（{@link #asMainHand}）
 */
public final class DualWield {

    private DualWield() {}

    private static final Map<Item, Boolean> HAS_SKILL = new ConcurrentHashMap<>();

    public static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof TacticalWeaponItem || stack.getItem() instanceof TieredItem;
    }

    private static boolean twoHanded(ItemStack stack) {
        return stack.getItem() instanceof TacticalWeaponItem w && w.twoHanded();
    }

    /** 両手に武器を持っていて、二刀流で使えるか */
    public static boolean isDual(LivingEntity e) {
        ItemStack main = e.getMainHandItem(), off = e.getOffhandItem();
        return isWeapon(main) && isWeapon(off) && !twoHanded(main) && !twoHanded(off);
    }

    /** 攻撃（左クリック・振り）がある（攻撃力が付く物） */
    public static boolean hasAttack(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem) return true;
        return stack.getItem() instanceof TacticalWeaponItem
                && stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    /** 技（右クリック）がある（use を自分で持つ兵装） */
    public static boolean hasSkill(ItemStack stack) {
        if (!(stack.getItem() instanceof TacticalWeaponItem)) return false;
        return HAS_SKILL.computeIfAbsent(stack.getItem(), item -> {
            try {
                Class<?> owner = item.getClass().getMethod("use", Level.class, Player.class, InteractionHand.class).getDeclaringClass();
                return owner != Item.class && owner != TacticalWeaponItem.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        });
    }

    /**
     * 左手の物を右手（利き手）に持ち替えた状態で action を行い、元へ戻す。攻撃力・攻撃速度などの持ち物の能力も、その間だけ左手の物のものにする
     * （装備の変化は次の Tick まで能力に反映されないため、ここで付け外しする）
     */
    public static void asMainHand(LivingEntity e, Runnable action) {
        ItemStack main = e.getMainHandItem(), off = e.getOffhandItem();
        Multimap<Attribute, AttributeModifier> offMods = off.getAttributeModifiers(EquipmentSlot.MAINHAND);
        // 右手の物の能力のうち、いま実際に付いているものだけ外して後で戻す（装備した直後でまだ付いていなければ、何も足さない）
        Multimap<Attribute, AttributeModifier> mainMods = com.google.common.collect.HashMultimap.create();
        main.getAttributeModifiers(EquipmentSlot.MAINHAND).forEach((attr, mod) -> {
            var inst = e.getAttribute(attr);
            if (inst != null && inst.hasModifier(mod)) mainMods.put(attr, mod);
        });
        e.getAttributes().removeAttributeModifiers(mainMods);
        e.getAttributes().addTransientAttributeModifiers(offMods);
        e.setItemSlot(EquipmentSlot.MAINHAND, off);
        e.setItemSlot(EquipmentSlot.OFFHAND, main);
        try {
            action.run();
        } finally {
            ItemStack nowMain = e.getMainHandItem(), nowOff = e.getOffhandItem();
            if (e.isUsingItem()) e.stopUsingItem(); // 左手の物の長押しは続けない
            e.setItemSlot(EquipmentSlot.MAINHAND, nowOff);
            e.setItemSlot(EquipmentSlot.OFFHAND, nowMain);
            e.getAttributes().removeAttributeModifiers(offMods);
            e.getAttributes().addTransientAttributeModifiers(mainMods);
        }
    }
}
