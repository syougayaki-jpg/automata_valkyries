package com.automatavalkyries.gametest.fake;

import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * GameTest 用の「架空の即死 MOD」。このパッケージのクラスは {@link CallerAttribution#overrideForTest} で
 * 架空の MOD ID として扱われ、ここからの呼び出しが「他 MOD からの攻撃」になる。
 */
public final class FakeKillMod {

    public static final String MOD_ID = "test_fake_killer";
    public static final String PACKAGE = "com.automatavalkyries.gametest.fake.";

    private FakeKillMod() {}

    public static void install() {
        CallerAttribution.overrideForTest(PACKAGE, MOD_ID);
    }

    public static CallerAttribution.Attribution probe(Entity target) {
        return CallerAttribution.attribute(target);
    }

    public static void relocate(Entity target, double dx) {
        target.setPos(target.getX() + dx, target.getY(), target.getZ());
    }

    public static void hit(LivingEntity target, DamageSource source, float amount) {
        target.hurt(source, amount);
    }

    public static void setHealth(LivingEntity target, float health) {
        target.setHealth(health);
    }

    public static Entity changeDimension(Entity target, ServerLevel destination) {
        return target.changeDimension(destination);
    }

    public static void die(LivingEntity target, DamageSource source) {
        target.die(source);
    }

    public static void discard(Entity target) {
        target.discard();
    }

    public static void fakeDimensionRemoval(Entity target) {
        target.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
    }
}
