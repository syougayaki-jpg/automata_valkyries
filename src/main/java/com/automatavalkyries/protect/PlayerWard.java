package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守護の護符（{@link ModItems#WARD_CHARM}）を持つプレイヤーへの保護。持ち物（手持ち・防具・オフハンド含む）のどこかにあれば効く。
 * <ul>
 *   <li>他 MOD による強制テレポート（遠くへの移動・奈落／ワールド外への飛ばし・別の次元への移動）を拒否</li>
 *   <li>即死を拒否: /kill 型のダメージ、die() の直接呼び出し、被弾以外での HP 0 化、生きているうちの消去、即死 MOD の各経路</li>
 * </ul>
 * 通常のダメージでは死ぬ（ダメージの上限はかけない）。vanilla の移動（エンダーパール・/tp・ポータル・リスポーン）や、
 * 自然に奈落へ落ちての死亡もそのまま。判定はサーバー側だけで、持っているかは毎 Tick 確かめて覚えておく。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class PlayerWard {

    private static final Set<UUID> WARDED = ConcurrentHashMap.newKeySet();

    private PlayerWard() {}

    /** 護符で守られている、生きているプレイヤーか（サーバー側） */
    public static boolean isActive(@Nullable Entity entity) {
        return entity instanceof Player player && !player.level().isClientSide && WARDED.contains(player.getUUID())
                && !player.isDeadOrDying();
    }

    /** 持ち物を見て覚え直す（毎 Tick 呼ぶ。GameTest からも呼ぶ） */
    public static void refresh(Player player) {
        if (hasCharm(player)) WARDED.add(player.getUUID());
        else WARDED.remove(player.getUUID());
    }

    private static boolean hasCharm(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.WARD_CHARM.get())) return true;
        }
        return false;
    }

    /**
     * 呼び出し経路に他 MOD のコードがあれば、その MOD からの攻撃として記録して true（拒否する）。
     * vanilla・Forge・このMOD だけの経路（エンダーパール・/tp・ポータル等）なら false（通す）。
     */
    public static boolean refuseFromForeignMod(Entity player, String op) {
        String mod = CallerAttribution.foreignClassMod(player);
        if (mod == null) return false;
        AttackLog.refused(player, op, mod);
        return true;
    }

    /**
     * プレイヤーへの即死ダメージか。自然に奈落へ落ちたときの outOfWorld は通す
     * （他 MOD による奈落への飛ばしはテレポートの拒否で止める）
     */
    public static boolean isInstantKill(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) return true;
        String msgId = source.getMsgId();
        if (msgId == null) return false;
        String lower = msgId.toLowerCase();
        return lower.contains("imds") || lower.contains("novel") || lower.contains("hyper") || lower.contains("kill");
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) refresh(event.player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WARDED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WARDED.clear();
    }
}
