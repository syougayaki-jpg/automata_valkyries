package com.automatavalkyries.weapon;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飛び道具らしいエンティティ（2026-10-06 ユーザー指定「汎用的に他の MOD の投射エンティティにも対応」）。
 * バニラの {@link Projectile} を継がない他 MOD の弾（Entity を直に継いで自分で動く物）も、形と動きで見分ける:
 * 生き物・落ちている物・経験値・乗り物・飾り・雲・雷などを除いた小さな（幅・高さ 1.5 以下）エンティティが速く（1 Tick に 0.3 ブロック以上）動いている。
 * 速さは速度（deltaMovement）か、速度を使わず位置を直に動かす物は前の Tick からの移動から取る。
 * 撃った者は Projectile・TraceableEntity・OwnableEntity、それでも分からなければ getOwner / getShooter / getThrower という名前のメソッドから
 */
public final class ProjectileLike {

    /** 速いとみなす 1 Tick の移動（ブロック）・小さいとみなす大きさ */
    public static final double MIN_SPEED = 0.3, MAX_SIZE = 1.5;

    private static final Map<Class<?>, Optional<Method>> OWNER_GETTERS = new ConcurrentHashMap<>();

    private ProjectileLike() {}

    /** 飛び道具らしいか */
    public static boolean is(Entity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Projectile) return velocity(e).lengthSqr() > 0.04; // 刺さった矢などの止まっている物は除く
        if (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Boat || e instanceof AbstractMinecart
                || e instanceof HangingEntity || e instanceof AreaEffectCloud || e instanceof LightningBolt || e instanceof Marker
                || e instanceof net.minecraft.world.entity.Display || e instanceof net.minecraft.world.entity.Interaction || e.isVehicle() || e.isPassenger()) {
            return false;
        }
        if (e.getBbWidth() > MAX_SIZE || e.getBbHeight() > MAX_SIZE) return false;
        return velocity(e).lengthSqr() >= MIN_SPEED * MIN_SPEED;
    }

    /** 1 Tick の動き（速度が無ければ前の Tick からの移動） */
    public static Vec3 velocity(Entity e) {
        Vec3 d = e.getDeltaMovement();
        if (d.lengthSqr() > 1.0e-4) return d;
        return e.position().subtract(e.xo, e.yo, e.zo);
    }

    /** 撃った者（分からなければ null） */
    @Nullable
    public static Entity owner(Entity e) {
        if (e instanceof Projectile p) return p.getOwner();
        if (e instanceof TraceableEntity t) return t.getOwner();
        if (e instanceof OwnableEntity o) return o.getOwner();
        Optional<Method> m = OWNER_GETTERS.computeIfAbsent(e.getClass(), ProjectileLike::findGetter);
        if (m.isEmpty()) return null;
        try {
            Object owner = m.get().invoke(e);
            return owner instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static Optional<Method> findGetter(Class<?> c) {
        for (String name : new String[]{"getOwner", "getShooter", "getThrower", "getCaster", "getSummoner"}) {
            for (Class<?> k = c; k != null && k != Entity.class && k != Object.class; k = k.getSuperclass()) {
                try {
                    Method m = k.getDeclaredMethod(name);
                    if (m.getParameterCount() == 0 && Entity.class.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return Optional.of(m);
                    }
                } catch (NoSuchMethodException | RuntimeException ignored) {
                    // 上のクラスを探す
                }
            }
        }
        return Optional.empty();
    }

    /** 向きを反対へ（跳ね返す）。撃った者を変えられる物は返した者にする */
    public static void reflect(Entity e, Vec3 velocity, @Nullable Entity by) {
        if (e instanceof Projectile p && by != null) p.setOwner(by);
        e.setDeltaMovement(velocity);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }
}
