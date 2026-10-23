package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.annihilate.TerminusPlayerExecutor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.flag.GuardLists;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.protect.flag.StateScanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * リスポーンまわりの準汎用の片付け（MOD ごとの個別対策に頼らず、フラグ辞書で見分ける）。
 * <p>
 * 即死 MOD・保護 MOD の状態がプレイヤーに残ると、リスポーンが壊れる（2026-10-02〜03 に The Last Sword・ECA・Hyperlink で起きたこと）:
 * </p>
 * <ul>
 *   <li>死んだ古い個体が守られたまま・復活の対象のまま → 古い個体が消えずに残り二重になる・死亡中に HP を戻されてリスポーンの要求が無視される</li>
 *   <li>UUID で持つ死亡・停止の印・引き継がれた永続データ → 新しい個体まで死亡扱い・停止のまま</li>
 * </ul>
 * 片付け:
 * <ol>
 *   <li>死亡時とリスポーンの直前（{@code PlayerList.respawn} の最初）: 古い個体の守り・復活の許可・停止・HP の固定・リスポーンの妨害を下ろす
 *       （{@link StateScanner#RELEASE_DEAD_PLAYER}）</li>
 *   <li>リスポーン直後から 2 秒: 新しい個体の死亡・停止・HP 改ざんの印とリスポーンの妨害を毎 Tick 戻し（{@link StateScanner#CLEAN_RESPAWNED_PLAYER}）、
 *       覚えている抹殺予定の一覧（{@link KillLists}）から外す。他 MOD が UUID を見て付け直すので 1 回では足りない</li>
 *   <li>リスポーンの要求: 死亡が確定している（{@code dead}）のに HP が戻されていたら HP を 0 にして要求を通す（{@link #allowRespawnOfDeadPlayer}）</li>
 * </ol>
 * 準汎用の層なので {@code -PgenericOnly} でも有効。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class RespawnSanitizer {

    /** リスポーン後に新しい個体を見張る Tick 数 */
    static final int GRACE_TICKS = 40;

    private static final Map<UUID, Integer> GRACE = new ConcurrentHashMap<>();

    private RespawnSanitizer() {}

    /** リスポーン要求に対して死亡が確定している個体か判定する */
    public static boolean isConfirmedDeadForRespawn(Player player) {
        if (player == null || (player instanceof ServerPlayer sp && sp.wonGame)) return false;
        try {
            if (((LivingEntityAccessor) player).automata_valkyries$isDead()) return true;
        } catch (Throwable ignored) {}
        return !DirectRead.hasHealth(player)
                || IsolationDamageEngine.isIsolatedDead(player)
                || TerminusPlayerExecutor.isTerminusDeadOrDying(player);
    }

    /** 死んだ古い個体から、他 MOD に生かされ続ける・復活させられる状態を下ろす。@return 書き換えた件数 */
    public static int releaseDeadPlayer(Player player) {
        if (player == null || (DirectRead.hasHealth(player) && !isConfirmedDeadForRespawn(player))) return 0;
        try {
            GuardLists.purge(player);
        } catch (Throwable ignored) {}
        if (SpecificCountermeasures.enabled()) {
            try {
                KillModCounters.counterAll(player);
            } catch (Throwable ignored) {}
        }
        List<StateScanner.Change> changes = apply(player, StateScanner.RELEASE_DEAD_PLAYER);
        if (!changes.isEmpty()) {
            AutomataValkyries.LOGGER.info("[respawn] Released {} foreign state(s) from dead player {}: {}",
                    changes.size(), player.getScoreboardName(), summarize(changes));
        }
        return changes.size();
    }

    /** リスポーンした新しい個体から、死亡・停止の印とリスポーンの妨害を戻す。@return 書き換え・除去の件数 */
    public static int cleanRespawnedPlayer(Player player, boolean log) {
        if (player == null) return 0;
        List<StateScanner.Change> changes = apply(player, StateScanner.CLEAN_RESPAWNED_PLAYER);
        int purged = 0;
        try {
            purged = KillLists.purge(player);
        } catch (Throwable ignored) {}
        if (log && (!changes.isEmpty() || purged > 0)) {
            AutomataValkyries.LOGGER.info("[respawn] Cleaned respawned player {}: {} state(s) {}, {} kill-list entr(ies)",
                    player.getScoreboardName(), changes.size(), summarize(changes), purged);
        }
        return changes.size() + purged;
    }

    /**
     * リスポーンの要求を受けたときに呼ぶ。vanilla は {@code getHealth() > 0} だと要求を無視するが、死亡が確定した（{@code dead}）後に
     * 他 MOD が HP を戻している（復活の見張り・HP の固定）と、死亡画面を閉じても戻れなくなる。そのときは HP を 0 にして要求を通す。
     * 不死のトーテム等の正規の復活は {@code die()} 自体を止めるので {@code dead} は立たず、ここには掛からない。
     *
     * @return HP を 0 に戻したか
     */
    public static boolean allowRespawnOfDeadPlayer(Player player) {
        if (!isConfirmedDeadForRespawn(player)) return false;
        float restored = player.getHealth();
        releaseDeadPlayer(player);
        try {
            ((LivingEntityAccessor) player).automata_valkyries$setDead(true);
            IsolationDamageEngine.forceWriteHealth(player, 0.0F);
        } catch (Throwable ignored) {}
        if (restored > 0.0F) {
            AutomataValkyries.LOGGER.warn("[respawn] Respawn request from {} would have been ignored (health restored to {} after death); allowing it",
                    player.getScoreboardName(), restored);
        }
        return player.getHealth() <= 0.0F;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        // die() の途中（HP はまだ 0 でないことがある）。保護・復活の印だけ下ろす
        List<StateScanner.Change> changes = apply(player, StateScanner.RELEASE_DEAD_PLAYER);
        if (!changes.isEmpty()) {
            AutomataValkyries.LOGGER.info("[respawn] Released {} foreign state(s) from dying player {}: {}",
                    changes.size(), player.getScoreboardName(), summarize(changes));
        }
        // 隔絶ダメージを受けて死亡（die()）に入った場合、ClientboundPlayerCombatKillPacket が送られる前に
        // DATA_HEALTH_ID = 0.0F の書き戻しとクライアントへの HP 0 同期を確定させる
        if (IsolationDamageEngine.getState(player) != null) {
            IsolationDamageEngine.markPlayerIsolationDead(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || event.getEntity().level().isClientSide) return;
        cleanRespawnedPlayer(event.getEntity(), true);
        GRACE.put(event.getEntity().getUUID(), GRACE_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        UUID uuid = event.player.getUUID();
        Integer left = GRACE.get(uuid);
        if (left == null) return;
        if (left <= 1) GRACE.remove(uuid);
        else GRACE.put(uuid, left - 1);
        cleanRespawnedPlayer(event.player, false);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        GRACE.remove(event.getEntity().getUUID());
    }

    private static List<StateScanner.Change> apply(Player player, StateScanner.Policy policy) {
        try {
            return StateScanner.apply(player, policy, EnumSet.allOf(StateScanner.Source.class));
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.debug("[respawn] State scan failed on {}: {}", player.getScoreboardName(), t.toString());
            return List.of();
        }
    }

    private static String summarize(List<StateScanner.Change> changes) {
        if (changes.size() <= 6) return changes.toString();
        return changes.subList(0, 6) + " +" + (changes.size() - 6);
    }
}
