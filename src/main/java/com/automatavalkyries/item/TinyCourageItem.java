package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModBossDrops;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ちっぽけな勇気（2026-10-22 ユーザー指定。仕様は docs/SPEC_ELENOR.md §3.4）。
 * <ul>
 *   <li>ホットバー（0〜8 番）にあれば、このMOD のすべての終焉が効かなくなる（その他のダメージは受ける）。手に持たなくてよい</li>
 *   <li>使っても減らない。持ち主が死んだら無くなる（その場に落ちない）</li>
 *   <li>サバイバルでは、ボスのタグ（{@code forge:bosses}）のあるモブが倒されたとき {@link #DROP_CHANCE} の確率で落ちる</li>
 * </ul>
 * 終焉側の確認（{@code TerminusEngine.isProtectedByTinyCourage}）はこのクラスの {@link #protects(Entity)} へ委譲する（Antigravity 担当。依頼書 §5）
 */
public class TinyCourageItem extends Item {

    /** ボスを倒したときに落ちる確率（0.01%。幸運などのドロップ増加は効かせない） */
    public static final double DROP_CHANCE = 0.0001D;

    public TinyCourageItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    /** ホットバー（0〜8 番）に「ちっぽけな勇気」がある、生きたプレイヤーなら true（サーバーでもクライアントでも呼べる） */
    public static boolean protects(@Nullable Entity entity) {
        if (!(entity instanceof Player player) || !player.isAlive()) return false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).getItem() instanceof TinyCourageItem) return true;
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.tiny_courage.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.automata_valkyries.tiny_courage.usage").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.tiny_courage.lose").withStyle(ChatFormatting.DARK_RED));
    }

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
    public static final class Events {
        private Events() {}

        /** 持ち主が死んだら無くなる（死亡が取り消された場合は消さない。持ち物を残す設定でも消える） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onPlayerDeath(LivingDeathEvent event) {
            if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Player player)) return;
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof TinyCourageItem) inventory.setItem(slot, ItemStack.EMPTY);
            }
        }

        /** 念のため、落とし物の中からも取り除く（ほかの MOD が先に外へ出していた場合） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onPlayerDrops(LivingDropsEvent event) {
            if (!(event.getEntity() instanceof Player)) return;
            event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof TinyCourageItem);
        }

        /** ボスのタグがあるモブを倒したら、超低確率で落とす */
        @SubscribeEvent
        public static void onBossDrops(LivingDropsEvent event) {
            LivingEntity boss = event.getEntity();
            if (boss.level().isClientSide() || boss instanceof Player || !boss.getType().is(Tags.EntityTypes.BOSSES)) return;
            if (boss.getRandom().nextDouble() >= DROP_CHANCE) return;
            ItemEntity drop = new ItemEntity(boss.level(), boss.getX(), boss.getY() + 0.5D, boss.getZ(),
                    new ItemStack(ModBossDrops.TINY_COURAGE.get()));
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }
}
