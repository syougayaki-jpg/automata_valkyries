package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 守護の護符（{@link ModItems#WARD_CHARM}）を持つプレイヤーへの保護（2026-10-04 ユーザー指定で作り直し。前の護符と同じ強さ）。持ち物のどこかにあれば効く。
 * <ul>
 *   <li>他 MOD による強制テレポート（遠くへの移動・ワールド外への飛ばし・別の次元への移動）を拒否</li>
 *   <li>即死を拒否: /kill 型のダメージ（フラグ辞書で即死と分かる種類も）、die() の直接呼び出し、被弾以外での HP 0 化、他 MOD による生きているうちの消去</li>
 * </ul>
 * 通常のダメージでは死ぬ（ダメージの上限はかけない）。vanilla の移動（エンダーパール・/tp・ポータル・リスポーン）や、自然に奈落へ落ちての死亡もそのまま。
 * 死んでも護符は手元に残る。判定はサーバー側だけで、持っているかは毎 Tick 確かめて覚えておく。
 * 前の護符（2026-10-02 に、エレノア戦・ECA と The Last Sword などとの干渉で廃止）にあった、リスポーン後 10 秒の保護・即死 MOD ごとの後始末・
 * クライアント側の HP の扱いは入れない。保護の中心の Mixin（EprotectDamageMixin 等）にも手を入れず、別の Mixin（{@code mixin/PlayerWard*Mixin}）だけで見る
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class PlayerWard {

    private static final Set<UUID> WARDED = ConcurrentHashMap.newKeySet();
    /** {@link #isActive} が HP を読んでいる最中か（HP の読み出しに他 MOD の処理が挟まり、そこからまた呼ばれて無限に再帰しないため） */
    private static final ThreadLocal<Boolean> READING_HEALTH = ThreadLocal.withInitial(() -> false);

    /** 守られているプレイヤーの hurt() の入れ子の深さ（この間の HP の減少・0 化・死亡は正規の被弾） */
    private static final Map<UUID, Integer> HURT_DEPTH = new ConcurrentHashMap<>();

    private PlayerWard() {}

    public static void enterHurt(Player player) {
        HURT_DEPTH.merge(player.getUUID(), 1, Integer::sum);
    }

    public static void exitHurt(Player player) {
        HURT_DEPTH.computeIfPresent(player.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
    }

    /** 正規の被弾（hurt() の中）か */
    public static boolean inHurt(Player player) {
        return HURT_DEPTH.containsKey(player.getUUID());
    }

    /**
     * 死亡処理を止めるか（LivingEntity.die と、それを上書きする ServerPlayer.die の両方の入口から）。
     * 無敵の護符を持っていれば止める。守護の護符は、正規の被弾の外からの死亡だけ止める（命に届く通常のダメージでは倒れる）
     */
    public static boolean refusesDeath(Player player) {
        if (InvincibleCharm.isActive(player)) return true;
        return isActive(player) && !inHurt(player);
    }

    /** 護符で守られている、生きているプレイヤーか（サーバー側） */
    public static boolean isActive(@Nullable Entity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide || !WARDED.contains(player.getUUID())) return false;
        if (READING_HEALTH.get()) return true;
        READING_HEALTH.set(true);
        try {
            return DirectRead.hasHealth(player); // isDeadOrDying() は他 MOD に偽装されうる
        } finally {
            READING_HEALTH.set(false);
        }
    }

    /** 持ち物を見て覚え直す（毎 Tick。GameTest からも呼ぶ） */
    public static void refresh(Player player) {
        boolean changed = hasCharm(player) ? WARDED.add(player.getUUID()) : WARDED.remove(player.getUUID());
        if (changed && !player.level().isClientSide) CharmDiagnostics.changed(player, "ward", WARDED.contains(player.getUUID()));
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
     * vanilla・Forge・このMOD だけの経路（エンダーパール・/tp・ポータル等）なら false（通す）
     */
    public static boolean refuseFromForeignMod(Entity player, String op) {
        String mod = CallerAttribution.foreignClassMod(player);
        if (mod == null) return false;
        AttackLog.refused(player, op, mod);
        return true;
    }

    /** プレイヤーへの即死ダメージか（/kill 型・フラグ辞書で即死と分かる種類）。自然に奈落へ落ちたときは通す */
    public static boolean isInstantKill(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) return true;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
        String msgId = source.getMsgId();
        if (msgId != null && FlagDictionary.harmfulOnProtected(msgId)) return true;
        var key = source.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse(null);
        return key != null && FlagDictionary.harmfulOnProtected(key);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) refresh(event.player);
    }

    // ---- 死んでも手放さない ----

    /** 死亡時に落とさずに預かった護符（リスポーン後の個体へ戻す） */
    private static final Map<UUID, List<ItemStack>> KEPT_ON_DEATH = new ConcurrentHashMap<>();

    /** 死亡時、護符を落とし物から外して預かる（優先度は最後: 他 MOD が落とし物を増減させた後） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        List<ItemStack> kept = new ArrayList<>();
        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            if (!stack.is(ModItems.WARD_CHARM.get())) return false;
            kept.add(stack.copy());
            drop.discard();
            return true;
        });
        if (!kept.isEmpty()) KEPT_ON_DEATH.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).addAll(kept);
    }

    /** リスポーン後の個体へ、預かった護符を戻す */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || event.getEntity().level().isClientSide) return;
        Player player = event.getEntity();
        List<ItemStack> kept = KEPT_ON_DEATH.remove(event.getOriginal().getUUID());
        if (kept != null) {
            for (ItemStack stack : kept) if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        refresh(player);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WARDED.remove(event.getEntity().getUUID());
        HURT_DEPTH.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WARDED.clear();
        KEPT_ON_DEATH.clear();
    }
}
