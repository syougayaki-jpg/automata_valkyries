package com.automatavalkyries.protect;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Epic Core API (ECA) および The Last Sword You Never Forgot による
 * プレイヤーの強制位置固定（硬直・テレポート不能・リスポーン先からの強制引き戻し）、
 * 無敵・ヘルスロック・不正飛行状態を安全かつ確実に解放・解除するヘルパー。
 */
public final class EcaLastSwordHelper {

    private EcaLastSwordHelper() {}

    /**
     * プレイヤーが The Last Sword 系の防御剣（UltraTestSword, TheLastSword 等）を
     * インベントリ内に実際に保持しているか判定する。
     */
    public static boolean hasDefenseSword(Player player) {
        if (player == null) return false;
        try {
            Inventory inv = player.getInventory();
            if (inv == null) return false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                String className = stack.getItem().getClass().getName();
                if (className.contains("UltraTestSword") || className.contains("TheLastSword")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * UUID を指定して ECA の EntityLocationManager の位置ロックから強制解除する。
     * リスポーン時やプレイヤー切断時に呼び出す。
     */
    public static void clearLocationLock(UUID uuid) {
        if (uuid == null) return;
        try {
            Class<?> locManagerClass = Class.forName("net.eca.util.EntityLocationManager");
            Field lockedMapField = locManagerClass.getDeclaredField("LOCKED_ENTITIES");
            lockedMapField.setAccessible(true);
            Object mapObj = lockedMapField.get(null);
            if (mapObj instanceof Map<?, ?> map) {
                map.remove(uuid);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * プレイヤーが死んでいる、またはラストソードを手放した場合、
     * 残存する位置固定・無敵・ヘルスロック・飛行状態を完全解除・初期化する。
     */
    public static void cleanupPlayerRestraints(Player player) {
        if (player == null) return;
        UUID uuid = player.getUUID();

        // 1. 位置固定マップからの解除
        clearLocationLock(uuid);

        // 2. ECA の unlockLocation 呼び出し
        try {
            Class<?> locManagerClass = Class.forName("net.eca.util.EntityLocationManager");
            Method unlock = locManagerClass.getMethod("unlockLocation", net.minecraft.world.entity.Entity.class);
            unlock.invoke(null, player);
        } catch (Throwable ignored) {}

        // 3. ECA の HealthLockManager 解除
        try {
            Class<?> healthLockClass = Class.forName("net.eca.util.health.health_lock.HealthLockManager");
            Method removeLock = healthLockClass.getMethod("removeLock", net.minecraft.world.entity.LivingEntity.class);
            removeLock.invoke(null, player);
            Method removeMaxLock = healthLockClass.getMethod("removeMaxHealthLock", net.minecraft.world.entity.LivingEntity.class);
            removeMaxLock.invoke(null, player);
            Method removeHealBan = healthLockClass.getMethod("removeHealBan", net.minecraft.world.entity.LivingEntity.class);
            removeHealBan.invoke(null, player);
        } catch (Throwable ignored) {}

        // 4. ECA の setInvulnerable(false)
        try {
            Class<?> ecaApi = Class.forName("net.eca.api.EcaAPI");
            Method setInvul = ecaApi.getMethod("setInvulnerable", net.minecraft.world.entity.Entity.class, boolean.class);
            setInvul.invoke(null, player, false);
        } catch (Throwable ignored) {}

        // 5. The Last Sword の clearDefence / clearWorldAnchor
        try {
            Class<?> entityUtil = Class.forName("net.the_last_sword.util.EntityUtil");
            Method clearDef = entityUtil.getMethod("clearDefence", net.minecraft.world.entity.LivingEntity.class);
            clearDef.invoke(null, player);
            Method clearAnchor = entityUtil.getMethod("clearWorldAnchor", net.minecraft.world.entity.LivingEntity.class);
            clearAnchor.invoke(null, player);
            Method setProt = entityUtil.getMethod("setProtection", net.minecraft.world.entity.LivingEntity.class, boolean.class);
            setProt.invoke(null, player, false);
        } catch (Throwable ignored) {}

        // 6. PersistentData 内の残存フラグパージ
        try {
            CompoundTag tag = player.getPersistentData();
            tag.remove("UltraTestSwordDefence");
            tag.remove("UltraTestSwordTLSDefence");
            tag.remove("TheLastSwordFly");
            tag.remove("TheLastSwordDefence");
            tag.remove("TheLastSwordYouNeverForgotInvul");
            tag.remove("TheLastSwordYouNeverForgotFly");
            tag.remove("TheLastSwordYouNeverForgotDefence");
            tag.remove("tlsWorldAnchor");
            tag.remove("tlsWorldAnchorMax");
            tag.remove("tlsIsProtected");
            tag.remove("PlayerFlightIntent");
        } catch (Throwable ignored) {}

        // 7. サバイバル時の飛行能力および重力の正常化
        try {
            if (!player.isCreative() && !player.isSpectator()) {
                if (player.getAbilities().mayfly || player.getAbilities().flying) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            player.setNoGravity(false);
            var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getValue() <= 0.005D) {
                speedAttr.setBaseValue(0.1D);
            }
        } catch (Throwable ignored) {}
    }
}
