package com.automatavalkyries.entity;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宿命の結び糸（{@code item/FateThreadItem}）で結んだ 2 体の対決（2026-10-20 ユーザー指定「他の MOD のボスと戦わせたい」「戦わせるアイテムを作る」）。
 * 結ばれた 2 体は、どちらかが倒れる・消える・別の次元へ行くまで互いだけを狙う:
 * <ul>
 *   <li>狙いを変えようとしたら（{@link LivingChangeTargetEvent}。HurtByTarget・近くのプレイヤーを狙う目標・他の MOD の AI が setTarget した時）相手へ戻す</li>
 *   <li>それでも別の相手を狙っていたら、0.5 秒ごとに相手へ向け直す（setTarget を通さない AI のため）</li>
 *   <li>ボスの「挑む相手がいなくなったら退く」見張り（{@link BossRetreatWatch}）は対決の間は働かない</li>
 * </ul>
 * 狙いを {@link Mob#setTarget} で決めない他の MOD の AI（MCreator の手続きで一番近いプレイヤーを追うなど）は向けられないことがある。
 * 決着がつくと、結んだプレイヤーと近くの者に勝者を知らせる。保存しない（ワールドを開き直すと解ける）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class FateDuels {

    private record Duel(UUID a, UUID b, @Nullable UUID by) {}

    /** 結ばれた者 → その対決 */
    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    private FateDuels() {
    }

    /** a と b を結ぶ（by = 結んだプレイヤー）。どちらかが別の対決に居れば、それを解いてから */
    public static void bind(LivingEntity a, LivingEntity b, @Nullable ServerPlayer by) {
        release(a);
        release(b);
        Duel d = new Duel(a.getUUID(), b.getUUID(), by == null ? null : by.getUUID());
        DUELS.put(a.getUUID(), d);
        DUELS.put(b.getUUID(), d);
        aim(a, b);
        aim(b, a);
        AutomataValkyries.LOGGER.info("[fate-duel] {} vs {}", a, b);
    }

    /** e の対決を解く。解いたら true */
    public static boolean release(Entity e) {
        Duel d = DUELS.remove(e.getUUID());
        if (d == null) return false;
        DUELS.remove(d.a());
        DUELS.remove(d.b());
        return true;
    }

    /** by が結んだ対決を全て解く。解いた数 */
    public static int releaseAllBy(ServerPlayer by) {
        int before = DUELS.size();
        DUELS.values().removeIf(d -> by.getUUID().equals(d.by()));
        return (before - DUELS.size()) / 2;
    }

    /** e が対決の中に居るか（サーバー側） */
    public static boolean dueling(Entity e) {
        return !e.level().isClientSide && DUELS.containsKey(e.getUUID());
    }

    /** e の対決の相手（居なければ null） */
    @Nullable
    public static LivingEntity foe(Entity e) {
        Duel d = DUELS.get(e.getUUID());
        if (d == null || !(e.level() instanceof ServerLevel level)) return null;
        Entity other = level.getEntity(e.getUUID().equals(d.a()) ? d.b() : d.a());
        return other instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    static void aim(LivingEntity who, LivingEntity at) {
        if (who instanceof Mob mob && mob.getTarget() != at) mob.setTarget(at);
        who.setLastHurtByMob(at);                                                          // 反撃の相手としても覚えさせる（HurtByTarget を使う AI 向け）
    }

    /** 狙いを変えようとしたら相手へ戻す */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || DUELS.isEmpty() || !DUELS.containsKey(e.getUUID())) return;
        LivingEntity foe = foe(e);
        if (foe != null && event.getNewTarget() != foe) event.setNewTarget(foe);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DUELS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        for (Duel d : new java.util.HashSet<>(DUELS.values())) {
            LivingEntity a = find(server, d.a()), b = find(server, d.b());
            if (a != null && b != null && a.isAlive() && b.isAlive() && a.level() == b.level()) {
                aim(a, b);
                aim(b, a);
                continue;
            }
            DUELS.remove(d.a());                                                        // 決着（倒れた・消えた・次元が分かれた）
            DUELS.remove(d.b());
            LivingEntity winner = a != null && a.isAlive() ? a : b != null && b.isAlive() ? b : null;
            LivingEntity loser = winner == a ? b : a;
            Component msg = winner != null && (loser == null || !loser.isAlive() || DirectDeath.dead(loser))
                    ? Component.translatable("item.automata_valkyries.fate_thread.won", winner.getDisplayName()).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("item.automata_valkyries.fate_thread.ended").withStyle(ChatFormatting.GRAY);
            ServerPlayer by = d.by() == null ? null : server.getPlayerList().getPlayer(d.by());
            if (by != null) by.sendSystemMessage(msg);
            Entity at = winner != null ? winner : by;
            if (at != null && at.level() instanceof ServerLevel level) {
                for (ServerPlayer p : level.getPlayers(p -> p != by && p.distanceToSqr(at) < 96 * 96)) p.sendSystemMessage(msg);
            }
        }
    }

    /** 倒れたかを見る（消えただけ・別の次元へ行っただけと分ける） */
    static final class DirectDeath {
        static boolean dead(LivingEntity e) {
            return e.isDeadOrDying() || e.isRemoved() && e.getRemovalReason() == Entity.RemovalReason.KILLED;
        }
    }

    @Nullable
    static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity l) return l;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DUELS.clear();
    }
}
