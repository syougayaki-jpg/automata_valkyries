package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.TimeStopPacket;
import com.automatavalkyries.weapon.LuminalCoreItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 総司令機タイムルーラーの「全世界時間停止」（2026-10-19 ユーザー指定。質問で: 本当に世界ごと止める・8 秒・特異点コアを付けた兵姫とその持ち主は動ける）。
 * 止まっている間は、どの次元でも（{@code mixin/TimeStopMixin}）:
 * <ul>
 *   <li>エンティティの Tick（雑魚・弾・落ちている物・乗り物・プレイヤーの体）・ブロックと液体の予定の Tick（後ろへずらす）・時刻・天気・乱数の Tick・ブロックの出来事が止まる</li>
 *   <li>プレイヤーの移動・アイテムの使用・ブロックを壊す・叩くの受け付けを断る（画面は白黒、{@code client/ClientTimeStop}）</li>
 *   <li>チャンクの処理（クライアントへ動きを送る）は止めないので、動ける者の動きは見える</li>
 * </ul>
 * 動けるのは止めた者（タイムルーラー）、特異点コア以上を付けた兵姫とその持ち主、スペクテイター。時間はサーバーの Tick 数で測る（ゲームの時刻は止まるので）。
 * 止めた者が倒れても、決めた時間が来れば必ず動き出す。Eprotect の「止まった Tick を代わりに回す」はこの停止の間は働かない（{@code protect/TickStallGuard}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TimeStop {

    @Nullable
    private static MinecraftServer server;
    private static long until = -1;
    @Nullable
    private static UUID source;
    private static final Set<UUID> EXEMPT = new HashSet<>();

    private TimeStop() {
    }

    /** いま時が止まっているか */
    public static boolean active() {
        MinecraftServer s = server;
        return s != null && until >= 0 && s.getTickCount() < until;
    }

    /** この者は止められているか（サーバー側） */
    public static boolean frozen(Entity e) {
        return active() && !e.level().isClientSide && !exempt(e);
    }

    /** 残りの Tick */
    public static int remaining() {
        MinecraftServer s = server;
        return s == null || until < 0 ? 0 : (int) Math.max(0, until - s.getTickCount());
    }

    static boolean exempt(Entity e) {
        UUID id = e.getUUID();
        if (id.equals(source) || EXEMPT.contains(id)) return true;
        return e instanceof Player p && p.isSpectator();
    }

    /** 特異点コア以上を付けた兵姫か */
    public static boolean hasSingularityCore(ValkyrieEntity v) {
        return v.getCore().getItem() instanceof LuminalCoreItem core && core.grade().ordinal() >= LuminalCoreItem.Grade.SINGULARITY.ordinal();
    }

    /** 時を止める（ticks の間。止めている間にもう一度呼ばれたら、長い方に延ばす） */
    public static void start(ServerLevel level, Entity by, int ticks) {
        server = level.getServer();
        source = by.getUUID();
        until = Math.max(until, server.getTickCount() + (long) ticks);
        refreshExempt();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TimeStopPacket(remaining(), exempt(p)));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        AutomataValkyries.LOGGER.info("[time-stop] {} stopped time for {} ticks ({} exempt)", by, ticks, EXEMPT.size());
    }

    /** 時を動かす（決めた時間が来たとき・サーバーが止まるとき） */
    public static void stop() {
        MinecraftServer s = server;
        boolean was = until >= 0;
        until = -1;
        source = null;
        EXEMPT.clear();
        if (s == null || !was) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TimeStopPacket(0, true));
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 1.6F);
        }
        AutomataValkyries.LOGGER.info("[time-stop] time flows again");
    }

    /** 動ける者（特異点コアの兵姫とその持ち主）を数え直す */
    static void refreshExempt() {
        MinecraftServer s = server;
        if (s == null) return;
        EXEMPT.clear();
        for (ServerLevel level : s.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ValkyrieEntity v && v.isAlive() && hasSingularityCore(v)) {
                    EXEMPT.add(v.getUUID());
                    if (v.getOwnerUUID() != null) EXEMPT.add(v.getOwnerUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || until < 0 || server == null) return;
        if (server.getTickCount() >= until) stop();
        else if (server.getTickCount() % 20 == 0) refreshExempt();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stop();
        server = null;
    }
}
