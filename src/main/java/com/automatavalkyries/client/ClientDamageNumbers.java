package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.ValkyrieClientConfig;
import com.automatavalkyries.entity.ProtectDummyZombieEntity;
import com.automatavalkyries.particle.DamageNumberOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * どんなダメージでも頭上に数字を出す（2026-10-29 ユーザー指定「ダメージ表記をどんなダメージでも出すかをオンオフする設定」。
 * クライアントの設定 {@code combat.allDamageNumbers}、既定は切）。自分の画面だけ: 近く {@value #RANGE} m の生き物の HP を毎 Tick 見て、
 * 減ったら減った量を {@link DamageNumberOptions} の数字で出す（どの MOD のどんなダメージでも、HP が減れば出る）。
 * 自分と、もともと数字を出す訓練用ダミーゾンビには出さない
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientDamageNumbers {

    static final double RANGE = 32.0;
    private static final Map<LivingEntity, Float> LAST = new WeakHashMap<>();

    private ClientDamageNumbers() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (!ValkyrieClientConfig.allDamageNumbers()) {
            LAST.clear();
            return;
        }
        Entity self = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == self || e == mc.player || e instanceof ProtectDummyZombieEntity) continue;
            if (e.distanceToSqr(self) > RANGE * RANGE) {
                LAST.remove(living);
                continue;
            }
            float hp = living.getHealth();
            Float before = LAST.put(living, hp);
            if (before == null || !Float.isFinite(hp) || !Float.isFinite(before)) continue; // はじめて見た
            float lost = before - hp;
            if (lost < 0.05F) continue;
            int color = lost >= 1000 ? 0xFF5050 : lost >= 200 ? 0xFF8C1A : lost >= 50 ? 0xFFD166 : 0xFFFFFF;
            double x = e.getX() + (mc.level.random.nextDouble() - 0.5) * 0.6, z = e.getZ() + (mc.level.random.nextDouble() - 0.5) * 0.6;
            double y = e.getY() + e.getBbHeight() + 0.4 + mc.level.random.nextDouble() * 0.3;
            mc.level.addParticle(new DamageNumberOptions(Math.max(1L, Math.round(lost)), color), true, x, y, z, 0, 0, 0);
        }
    }
}
