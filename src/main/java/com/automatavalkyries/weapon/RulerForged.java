package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 総司令機タイムルーラーの素材（時空剪定の針・クロノ・ルーラーの心臓）で作る T3 兵装は、ほかの T3 より強い
 * （2026-10-07 ユーザー指定「タイムルーラー素材を使うティア 3 はほかのティア 3 より強めに」）。持ち主が与えるダメージを ×{@value #DAMAGE_MULT}【仮】。
 * 対象: クロノ・リピーター・ノヴァ・ランス・セラフ・スタッフ・タイムエリミネイター（SPEC_WEAPON_UNITS §3.2a）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class RulerForged {

    public static final float DAMAGE_MULT = 1.5F;

    private RulerForged() {}

    public static boolean is(Item item) {
        return item == ModWeapons.CHRONO_REPEATER.get() || item == ModWeapons.NOVA_LANCE.get()
                || item == ModWeapons.SERAPH_STAFF.get() || item == ModWeapons.TIME_ELIMINATOR.get();
    }

    /** 利き手にこの兵装を持った相手の攻撃（弾・技も、持ち主が送り主なら）を上げる */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()
                && is(attacker.getMainHandItem().getItem())) {
            event.setAmount(event.getAmount() * DAMAGE_MULT);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (is(event.getItemStack().getItem())) {
            event.getToolTip().add(1, Component.translatable("tooltip.automata_valkyries.ruler_forged", Math.round((DAMAGE_MULT - 1.0F) * 100))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
