package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 護符の効き目の確かめ（2026-10-04 ユーザー指定「護符を持っていても死んだので調べる」）。
 * <ul>
 *   <li>守護の護符・無敵の護符が効き始めた・切れたとき: アクションバーとログ（{@code [charm-check]}）に出す</li>
 *   <li>プレイヤーが死んだとき: その瞬間に護符を持っていたか・護符の判定が有効だったか・死因・呼び出し経路にある他 MOD をログに出す</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class CharmDiagnostics {

    private CharmDiagnostics() {}

    /** 護符の判定が変わったとき（PlayerWard・InvincibleCharm の refresh から） */
    static void changed(Player player, String charm, boolean active) {
        AutomataValkyries.LOGGER.info("[charm-check] {} {} for {} (health {})", charm, active ? "ACTIVE" : "inactive", player.getGameProfile().getName(),
                DirectRead.health(player));
        player.displayClientMessage(Component.translatable("gui.automata_valkyries.charm." + charm + (active ? ".on" : ".off"))
                .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
    }

    static boolean has(Player player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) return true;
        return false;
    }

    /** 死んだ瞬間の護符の状態（死亡の取り消しの有無に関わらず、最後に見る） */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        AutomataValkyries.LOGGER.info("[charm-check] DEATH of {}: canceled={} source={} | ward: has={} active={} | invincible: has={} active={} | health={} | foreign mod in call path={}",
                player.getGameProfile().getName(), event.isCanceled(), event.getSource().getMsgId(),
                has(player, ModItems.WARD_CHARM.get()), PlayerWard.isActive(player),
                has(player, ModItems.INVINCIBLE_CHARM.get()), InvincibleCharm.isActive(player),
                DirectRead.health(player), CallerAttribution.foreignClassMod(player));
    }
}
