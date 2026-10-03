package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** 兵装のゲーム中のイベント（偏光ディフレクターの防御と反射、アイギスのドーム、因果停止） */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class WeaponEvents {

    /** 跳ね返す弾（当たった Tick のうちは弾自身の処理が向きを上書きするので、Tick の終わりに向きを変える） */
    private static final List<Reflect> REFLECTS = new ArrayList<>();

    private record Reflect(Projectile projectile, LivingEntity by, Vec3 velocity) {}

    private WeaponEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 因果停止中の者は攻撃できない
        if (source.getEntity() instanceof LivingEntity attacker && attacker.hasEffect(ModWeapons.CAUSAL_STOP.get())) {
            event.setCanceled(true);
            return;
        }
        if (aegisBlocks(defender, source)) {
            event.setCanceled(true);
            return;
        }
        if (!PolarDeflectorItem.deployed(defender)) return;
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
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.ICE, 0.18F, 14), p, 8, 0.15);
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.CYAN, 0.25F, 8), p, 6, 0.25);
            level.playSound(null, defender.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    /** アイギスのドームの中の者への、外からの攻撃か（外にいる者・外の位置から来るもの） */
    private static boolean aegisBlocks(LivingEntity defender, DamageSource source) {
        if (AegisBarrierItem.ACTIVE.isEmpty()) return false;
        LivingEntity guardian = AegisBarrierItem.guardianOf(defender.level(), AegisBarrierItem.center(defender));
        if (guardian == null) return false;
        Vec3 c = AegisBarrierItem.center(guardian);
        Entity cause = source.getEntity(), direct = source.getDirectEntity();
        Vec3 pos = source.getSourcePosition();
        boolean outside = (cause != null && c.distanceTo(AegisBarrierItem.center(cause)) > AegisBarrierItem.RADIUS)
                || (direct != null && c.distanceTo(AegisBarrierItem.center(direct)) > AegisBarrierItem.RADIUS)
                || (pos != null && c.distanceTo(pos) > AegisBarrierItem.RADIUS);
        if (!outside) return false;
        if (defender.level() instanceof ServerLevel level) {
            Vec3 from = cause != null ? AegisBarrierItem.center(cause) : direct != null ? AegisBarrierItem.center(direct) : pos;
            Vec3 hit = from == null ? c : c.add(from.subtract(c).normalize().scale(AegisBarrierItem.RADIUS));
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.GOLD, 0.3F, 18), hit, 12, 0.18);
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.PALE_GOLD, 0.35F, 8), hit, 6, 0.3);
            level.playSound(null, BlockPos.containing(hit), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
        return true;
    }

    /** パニッシュメント・レールガンのチャージ中は跳べない。凍傷は跳躍力を下げる */
    @SubscribeEvent
    public static void onJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.isUsingItem() && entity.getUseItem().getItem() instanceof PunishmentRailgunItem) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
            return;
        }
        // 凍傷: 跳躍力が段ごとに下がる
        double factor = Frostbite.jumpFactor(entity);
        if (factor < 1) entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, factor, 1));
    }

    /** 採掘用の兵装（ドリル・チェーンソー）で殴るときは、相手の無敵時間を貫く（攻撃の前に消す） */
    @SubscribeEvent
    public static void onPlayerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof MiningToolItem && event.getTarget() instanceof LivingEntity target) {
            target.invulnerableTime = 0;
        }
    }

    /** 因果停止中の者は物を使えない */
    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity().hasEffect(ModWeapons.CAUSAL_STOP.get())) event.setCanceled(true);
    }

    /** 外で起きた爆発は、アイギスのドームの中の者を巻き込まない */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (AegisBarrierItem.ACTIVE.isEmpty()) return;
        Vec3 center = event.getExplosion().getPosition();
        event.getAffectedEntities().removeIf(e -> {
            if (!(e instanceof LivingEntity living)) return false;
            LivingEntity guardian = AegisBarrierItem.guardianOf(event.getLevel(), AegisBarrierItem.center(living));
            return guardian != null && AegisBarrierItem.center(guardian).distanceTo(center) > AegisBarrierItem.RADIUS;
        });
    }

    /** アイギスのドームへ外から入ってくる飛び道具を、ドームの面で跳ね返す */
    private static void deflectIntoAegis(ServerLevel level) {
        AegisBarrierItem.ACTIVE.removeIf(e -> e.isRemoved() || !AegisBarrierItem.deployed(e));
        for (LivingEntity guardian : List.copyOf(AegisBarrierItem.ACTIVE)) {
            if (guardian.level() != level) continue;
            Vec3 c = AegisBarrierItem.center(guardian);
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, guardian.getBoundingBox().inflate(AegisBarrierItem.RADIUS + 2))) {
                Entity owner = projectile.getOwner();
                if (owner != null && c.distanceTo(AegisBarrierItem.center(owner)) <= AegisBarrierItem.RADIUS) continue; // 中から撃ったものは通す
                Vec3 offset = projectile.position().subtract(c);
                double d = offset.length();
                Vec3 v = projectile.getDeltaMovement();
                if (d > AegisBarrierItem.RADIUS + 0.6 || d < 0.01 || v.dot(offset) >= 0) continue;
                Vec3 n = offset.scale(1 / d);
                projectile.setDeltaMovement(v.subtract(n.scale(2 * v.dot(n))));
                projectile.hasImpulse = true;
                Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.GOLD, 0.25F, 14), projectile.position(), 6, 0.12);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level && !AegisBarrierItem.ACTIVE.isEmpty()) {
            deflectIntoAegis(level);
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
