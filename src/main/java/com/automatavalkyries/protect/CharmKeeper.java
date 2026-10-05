package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 護符を手放させない（2026-10-04 ユーザー指定）: 守護の護符・無敵の護符が、プレイヤー自身が捨てた（Q キー）・容器へ移した以外の理由で
 * 持ち物から無くなったら、すぐ持ち物へ戻す。地面に落とされていれば、その落ちている護符を拾い直す（近く 16 ブロック）。
 * 捨てた操作の呼び出し経路に他 MOD があれば、プレイヤー自身が捨てたとはみなさない。容器を開いている間（自分の持ち物の画面以外）は、移し替えとみなして戻さない
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class CharmKeeper {

    /** 前の Tick に持っていた護符（プレイヤーごと） */
    private static final Map<UUID, Set<Item>> HELD = new ConcurrentHashMap<>();
    /** この Tick にプレイヤーが自分で捨てた護符 */
    private static final Map<UUID, Set<Item>> TOSSED = new ConcurrentHashMap<>();

    private CharmKeeper() {}

    static Item[] charms() {
        return new Item[]{ModItems.WARD_CHARM.get(), ModItems.INVINCIBLE_CHARM.get(), ModItems.PRIVILEGE_AEGIS.get()};
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        // 他 MOD に落とされた（呼び出し経路に他 MOD がある）なら、プレイヤー自身が捨てたことにしない（後で拾い直す）
        if (CallerAttribution.foreignClassMod(event.getPlayer()) != null) return;
        for (Item charm : charms()) {
            if (stack.is(charm)) TOSSED.computeIfAbsent(event.getPlayer().getUUID(), k -> ConcurrentHashMap.newKeySet()).add(charm);
        }
    }

    /** 近くに落ちているその護符を拾い直す（拾えたら true） */
    private static boolean pickUp(Player player, Item charm) {
        for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16),
                e -> e.isAlive() && e.getItem().is(charm))) {
            ItemStack stack = drop.getItem().copy();
            drop.discard();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            return true;
        }
        return false;
    }

    /** 護符の判定（PlayerWard・InvincibleCharm の毎 Tick の見直し）より先に戻す */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        keep(event.player);
    }

    /** 持ち物を見て、無くなった護符を戻す（GameTest からも呼ぶ） */
    public static void keep(Player player) {
        UUID id = player.getUUID();
        Set<Item> held = HELD.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        Set<Item> tossed = TOSSED.remove(id);
        boolean moving = player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty();
        for (Item charm : charms()) {
            boolean has = CharmDiagnostics.has(player, charm) || player.containerMenu.getCarried().is(charm);
            if (has) {
                held.add(charm);
            } else if (held.contains(charm)) {
                // 生死は実際の HP で見る（isAlive() は他の処理に偽にされることがある。2026-10-04: HP 20 のまま偽になり、護符が戻らなかった）
                boolean alive = DirectRead.hasHealth(player);
                if ((tossed != null && tossed.contains(charm)) || moving || !alive) {
                    held.remove(charm); // 自分で手放した（死んだときは PlayerWard が預かって戻す）
                    AutomataValkyries.LOGGER.info("[charm-check] {} left {} and was NOT returned (tossed by the player={}, container open={}, alive={})",
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(charm), player.getGameProfile().getName(),
                            tossed != null && tossed.contains(charm), moving, alive);
                } else {
                    boolean picked = pickUp(player, charm);
                    if (!picked) {
                        ItemStack back = new ItemStack(charm);
                        if (!player.getInventory().add(back)) player.drop(back, false);
                    }
                    AutomataValkyries.LOGGER.info("[charm-check] {} returned to {} ({})", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(charm),
                            player.getGameProfile().getName(), picked ? "picked up from the ground" : "left the inventory without being dropped");
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
        TOSSED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HELD.clear();
        TOSSED.clear();
    }
}
