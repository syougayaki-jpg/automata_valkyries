package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** 兵装のゲーム中のイベント（偏光ディフレクターの防御と反射） */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class WeaponEvents {

    /** 跳ね返す弾（当たった Tick のうちは弾自身の処理が向きを上書きするので、Tick の終わりに向きを変える） */
    private static final List<Reflect> REFLECTS = new ArrayList<>();

    private record Reflect(Projectile projectile, LivingEntity by, Vec3 velocity) {}

    private WeaponEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide() || !PolarDeflectorItem.deployed(defender)) return;
        DamageSource source = event.getSource();
        if (!source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)) return;
        Vec3 from = source.getSourcePosition();
        if (from == null) return;
        Vec3 look = defender.getViewVector(1.0F);
        Vec3 to = from.subtract(defender.getEyePosition());
        if (to.lengthSqr() > 1.0E-6 && to.normalize().dot(look) < PolarDeflectorItem.FRONT_DOT) return;
        event.setCanceled(true);
        if (source.getDirectEntity() instanceof Projectile projectile) {
            REFLECTS.add(new Reflect(projectile, defender, projectile.getDeltaMovement().scale(-1.0)));
        }
        if (defender.level() instanceof ServerLevel level) {
            Vec3 p = defender.getEyePosition().add(look.scale(0.8));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 10, 0.3, 0.3, 0.3, 0.2);
            level.playSound(null, defender.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || REFLECTS.isEmpty()) return;
        for (Reflect reflect : REFLECTS) {
            Projectile projectile = reflect.projectile();
            if (projectile.isRemoved()) continue;
            projectile.setOwner(reflect.by());
            projectile.setDeltaMovement(reflect.velocity());
            projectile.hasImpulse = true;
        }
        REFLECTS.clear();
    }
}
