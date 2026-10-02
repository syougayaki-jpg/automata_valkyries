package com.automatavalkyries.protect;

import com.automatavalkyries.lifeguard.hooks.LifeHooks;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「真ライフ」: LifeGuard エージェントが生死の getter の戻り際（他のエージェント・ASM の包みより外側）で呼ぶ判断。
 * 保護対象（{@link IEprotected}、保護中）だけ、getter の答えを保存場所の実際の値（{@link DirectRead}）に直す。他は素通し。
 * <p>
 * 実際の HP が 0 以下のときは素通し（このMOD の Mixin が「直前の正常値」に読み替えた値をそのまま使う）。直したときは
 * 攻撃の記録に「読み出しの偽装を実際の値に直した」と残す（同じ個体は 100 Tick に 1 回まで）。
 * </p>
 * このクラスは LifeGuard がある環境でだけ読み込む（{@link LifeGuardLink}）。
 */
final class TrueLife implements LifeHooks.LifeFilter {

    private static final int NOTE_INTERVAL_TICKS = 100;
    private final Map<UUID, Integer> lastNoted = new ConcurrentHashMap<>();

    @Override
    public float health(Object entity, float value) {
        LivingEntity living = protectedLiving(entity);
        if (living == null) return value;
        float real = DirectRead.health(living);
        if (!Float.isFinite(real) || real <= 0.0F || real == value) return value;
        note(living, "getHealth");
        return real;
    }

    @Override
    public boolean deadOrDying(Object entity, boolean value) {
        if (!value) return false;
        LivingEntity living = protectedLiving(entity);
        if (living == null || !DirectRead.hasHealth(living)) return value;
        note(living, "isDeadOrDying");
        return false;
    }

    @Override
    public boolean alive(Object entity, boolean value) {
        if (value) return true;
        LivingEntity living = protectedLiving(entity);
        if (living == null || !DirectRead.isAlive(living)) return value;
        note(living, "isAlive");
        return true;
    }

    private static LivingEntity protectedLiving(Object entity) {
        if (!(entity instanceof IEprotected provider) || !(entity instanceof LivingEntity living)) return null;
        Eprotect protect = provider.getEprotect();
        return protect != null && protect.isProtectionActive() ? living : null;
    }

    private void note(LivingEntity living, String getter) {
        if (living.level().isClientSide) return;
        Integer last = lastNoted.get(living.getUUID());
        if (last != null && living.tickCount - last < NOTE_INTERVAL_TICKS && living.tickCount >= last) return;
        lastNoted.put(living.getUUID(), living.tickCount);
        AttackLog.repaired(living, "true_life:" + getter);
    }
}
