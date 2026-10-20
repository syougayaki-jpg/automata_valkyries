package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 無敵の護符（{@link ModItems#INVINCIBLE_CHARM}、2026-10-04 ユーザー指定）: 持ち物のどこかにあれば、そのプレイヤーへのダメージをすべて無効にし、HP を最大に保つ。
 * <ul>
 *   <li>ダメージ: 攻撃の入口（{@link LivingAttackEvent}）・被弾（{@link LivingHurtEvent}・{@link LivingDamageEvent}）のすべてで止める</li>
 *   <li>HP: 毎 Tick 最大まで戻し、外から HP を減らされても最大のまま（{@code mixin/PlayerWardLivingMixin} の setHealth）</li>
 *   <li>被ダメージの動きを切る: ノックバック・外から押されること・爆発の吹き飛びが無く、被弾の赤い点滅・画面の揺れ・音も出ない（{@code mixin/InvincibleCharmMixin}）</li>
 *   <li>死亡処理も止める（{@link PlayerWard#refusesDeath}）。護符が持ち物から無くなっても {@link #LINGER_TICKS} の間は無敵が続き、
 *       捨てていない護符は持ち物へ戻す（{@link CharmKeeper}）</li>
 * </ul>
 * サーバー側だけで見る。持っているかは毎 Tick 確かめて覚えておく
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class InvincibleCharm {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    /** 護符が持ち物から無くなった後も無敵が続く残り Tick（2026-10-04 ユーザー指定） */
    private static final java.util.Map<UUID, Integer> LINGER = new ConcurrentHashMap<>();
    public static final int LINGER_TICKS = 200;

    private InvincibleCharm() {}

    /** 無敵の護符で守られているプレイヤーか（サーバー側） */
    public static boolean isActive(@Nullable Entity entity) {
        if (AgentPlayerProtection.isProtected(entity)) return true;
        return entity instanceof Player player && !player.level().isClientSide
                && (ACTIVE.contains(player.getUUID()) || LINGER.getOrDefault(player.getUUID(), 0) > 0);
    }

    /** クライアントで、そのプレイヤー（自分）が無敵の護符を持っているか（被弾の動きを切るのに使う。クライアントには自分の持ち物しか届かない） */
    public static boolean holdsOnClient(@Nullable Entity entity) {
        if (AgentPlayerProtection.isProtected(entity)) return true;
        return entity instanceof Player player && player.level().isClientSide && hasCharm(player);
    }

    /** ノックバックしない（2026-10-04 ユーザー指定） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockBack(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (isActive(event.getEntity()) || holdsOnClient(event.getEntity())) event.setCanceled(true);
    }

    /** 爆発に巻き込まれない（ダメージも吹き飛びも無し） */
    @SubscribeEvent
    public static void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(InvincibleCharm::isActive);
    }

    /** 持ち物を見て覚え直す（毎 Tick。GameTest からも呼ぶ） */
    public static void refresh(Player player) {
        boolean has = hasCharm(player);
        boolean changed = has ? ACTIVE.add(player.getUUID()) : ACTIVE.remove(player.getUUID());
        if (has) LINGER.put(player.getUUID(), LINGER_TICKS);
        else LINGER.computeIfPresent(player.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
        if (changed && !player.level().isClientSide) CharmDiagnostics.changed(player, "invincible", ACTIVE.contains(player.getUUID()));
    }

    /** 護符を切った: 無敵の名残（{@link #LINGER_TICKS}）も待たずに外す（持ち物に入った他の護符があれば次の refresh で戻る） */
    public static void disableNow(Player player) {
        ACTIVE.remove(player.getUUID());
        LINGER.remove(player.getUUID());
        refresh(player);
    }

    private static boolean hasCharm(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.INVINCIBLE_CHARM.get()) && com.automatavalkyries.item.InvincibleCharmItem.enabled(stack)) return true;   // 切った護符は数えない
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (isActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (isActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingDamageEvent event) {
        if (isActive(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        refresh(player);
        if (isActive(player)) {
            if (player.getHealth() < player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            player.fallDistance = 0;
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
        LINGER.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        LINGER.clear();
    }
}
