package com.automatavalkyries.entity;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 検証用のダミーゾンビ（{@link ProtectDummyZombieEntity}）を練習台にする（2026-10-06 ユーザー指定）。
 * <ul>
 *   <li>ノックバックしない: バニラのノックバックは打ち消し、兵装の吹き飛ばし・引き寄せ（速さを直に書く）は被弾の直後だけ横の速さを消す</li>
 *   <li>受けたダメージを頭上に数字で出す（1 撃ごと、大きいほど暖色）</li>
 *   <li>DPS チェック: 殴り始めてから、{@link #IDLE_TICKS} Tick 殴られなければ一区切り。頭上の名前に DPS・合計・時間を出し続け、
 *       区切りの後も {@link #RESULT_TICKS} Tick は結果を頭上に残す（チャットには出さない。2026-10-06 ユーザー指定「テキストを出さず頭上のみ」）</li>
 * </ul>
 * ダメージは保護（Eprotect）と別枠で数える（2026-10-06 ユーザー指定「Eprotect の上限に引っかかっているので別枠計算」）: 防具の後・保護の被弾上限の前の値で、
 * 桁外れの 1 撃を保護が 100 に置き換えていたら本来の大きさへ戻す（{@code Eprotect#unclampedDamage}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class DummyZombieTraining {

    public static final int IDLE_TICKS = 100, RESULT_TICKS = 200;

    /** 区切りごとの記録 */
    private static final class Session {
        final long start;
        long last;
        double total;
        int hits;
        float max;
        Component originalName;
        boolean hadCustomName;

        Session(long now) {
            start = now;
            last = now;
        }

        /** 殴り始めてからの時間（秒）。最低 1 秒（1 撃目で 1 Tick で割って 20 倍の DPS になっていた。2026-10-06） */
        double seconds(long now) {
            return Math.max(1.0, (Math.max(last, now) - start) / 20.0);
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DummyZombieTraining() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ProtectDummyZombieEntity) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e instanceof ProtectDummyZombieEntity) || e.level().isClientSide || e.hurtTime <= 0) return;
        Vec3 d = e.getDeltaMovement();
        if (d.x != 0.0 || d.z != 0.0 || d.y > 0.0) {
            e.setDeltaMovement(0.0, Math.min(d.y, 0.0), 0.0);
            e.hurtMarked = true; // クライアントへも速さを送る
        }
    }

    /** 1 撃ごとのダメージ: 頭上に数字、DPS の記録 */
    @SubscribeEvent(priority = EventPriority.HIGHEST) // 保護の被弾上限（LOWEST）より前
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ProtectDummyZombieEntity dummy) || !(dummy.level() instanceof ServerLevel level)) return;
        float amount = dummy.getEprotect() != null ? dummy.getEprotect().unclampedDamage(event.getAmount()) : event.getAmount();
        if (!(amount > 0.0F) || !Float.isFinite(amount)) return;
        long now = level.getGameTime();
        Session s = SESSIONS.get(dummy.getUUID());
        if (s == null || now - s.last > IDLE_TICKS) {
            Session prev = s;
            s = new Session(now);
            s.hadCustomName = prev != null ? prev.hadCustomName : dummy.hasCustomName(); // 前の結果を出している間は、その前の名前を引き継ぐ
            s.originalName = prev != null ? prev.originalName : dummy.getCustomName();
            SESSIONS.put(dummy.getUUID(), s);
        }
        s.last = now;
        s.total += amount;
        s.hits++;
        s.max = Math.max(s.max, amount);
        int color = amount >= 1000 ? 0xFF5050 : amount >= 200 ? Fx.ORANGE : amount >= 50 ? Fx.GOLD : Fx.WHITE;
        Vec3 at = dummy.position().add((level.random.nextDouble() - 0.5) * 0.6, dummy.getBbHeight() + 0.4 + level.random.nextDouble() * 0.3, (level.random.nextDouble() - 0.5) * 0.6);
        Fx.number(level, at, Math.max(1L, Math.round(amount)), color);
        showLive(dummy, s, now);
    }

    /** 区切りの間、頭上の名前に DPS・合計・時間を出す */
    private static void showLive(LivingEntity dummy, Session s, long now) {
        double sec = s.seconds(now);
        dummy.setCustomName(Component.translatable("message.automata_valkyries.dps_live",
                String.format("%,.1f", s.total / sec), String.format("%,.0f", s.total), String.format("%.1f", sec)).withStyle(ChatFormatting.GOLD));
        dummy.setCustomNameVisible(true);
    }

    /** 殴られなくなって一区切りのダミーゾンビの結果を出し、名前を戻す */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> en = it.next();
            Session s = en.getValue();
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(en.getKey()) instanceof ProtectDummyZombieEntity dummy) {
                    long idle = level.getGameTime() - s.last;
                    if (idle == IDLE_TICKS + 1) showResult(dummy, s);           // 一区切り: 結果を頭上に
                    else if (idle > IDLE_TICKS + RESULT_TICKS) {                // 結果を出し終えたら名前を戻す
                        restoreName(dummy, s);
                        it.remove();
                    }
                    break;
                }
            }
        }
    }

    /** 一区切りの結果を頭上の名前に出す */
    private static void showResult(LivingEntity dummy, Session s) {
        if (s.hits <= 0) return;
        double sec = s.seconds(s.last);
        dummy.setCustomName(Component.translatable("message.automata_valkyries.dps_check",
                String.format("%,.1f", s.total / sec), String.format("%,.0f", s.total), String.format("%.1f", sec), s.hits, String.format("%,.0f", s.max))
                .withStyle(ChatFormatting.GOLD));
        dummy.setCustomNameVisible(true);
    }

    private static void restoreName(LivingEntity dummy, Session s) {
        dummy.setCustomName(s.hadCustomName ? s.originalName : null);
        dummy.setCustomNameVisible(false);
    }
}
