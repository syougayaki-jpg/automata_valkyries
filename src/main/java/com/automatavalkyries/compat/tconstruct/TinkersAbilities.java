package com.automatavalkyries.compat.tconstruct;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.compat.IsolationStrike;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tinkers' Construct のアビリティ「隔絶」（2026-10-30 ユーザー指定「オーバーロードメモリで隔絶ダメージを追加」）。
 * 改造はデータ（data/automata_valkyries/tinkering/modifiers/isolation.json）で、道具の一時データに目印 {@code automata_valkyries:isolation} を立てるだけ。
 * ここでその道具を主の手に持った近接の一撃を隔絶ダメージに置き換える（{@link IsolationStrike}）。
 * Tinkers のクラスは参照しない（道具の NBT を読むだけ）ので、Tinkers が無くても読み込める
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TinkersAbilities {

    /** 道具の一時データ（Tinkers の ToolStack.TAG_VOLATILE_MOD_DATA） */
    private static final String VOLATILE = "tic_volatile_data";
    public static final String ISOLATION_FLAG = AutomataValkyries.MODID + ":isolation";

    private TinkersAbilities() {}

    public static boolean hasIsolation(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getCompound(VOLATILE).getBoolean(ISOLATION_FLAG);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (IsolationStrike.applying()) return;
        var source = event.getSource();
        // 近接だけ（飛び道具・魔法の間接の攻撃は除く）
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return;
        if (!hasIsolation(attacker.getMainHandItem())) return;
        IsolationStrike.replace(event, attacker);
    }
}
