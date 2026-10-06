package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.ProjectileLike;
import com.automatavalkyries.weapon.WeaponDamage;
import com.automatavalkyries.weapon.WeaponUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 背部ユニットの追加分（2026-10-06 ユーザー指定「背後ユニットを各ティアに考えて」→「全部実装して」。T4 は「兵装が強いので強い補助用に」）。
 * T1 サブアーム・偵察ドローン・増設バッテリー、T2 リペアドローン・マイクロミサイルポッド・グラップルアンカー、T3 プリズムリフレクター、
 * T4 アストラルハロー・リザレクションコア・アクセラレーター・コマンドリンク。数は【仮】
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieSupportUnits {

    private ValkyrieSupportUnits() {}

    // 範囲・間隔・量【仮】
    public static final double DRONE_RANGE = 16.0, REPAIR_RANGE = 8.0, MISSILE_RANGE = 24.0, HALO_RANGE = 12.0, REVIVE_RANGE = 16.0,
            ACCEL_RANGE = 12.0, LINK_RANGE = 24.0, GRAPPLE_MIN = 6.0, GRAPPLE_MAX = 16.0;
    public static final float DRONE_DAMAGE = 2.0F, REPAIR_HEAL = 2.0F, MISSILE_DAMAGE = 15.0F, MISSILE_SPLASH = 5.0F,
            SUB_ARM_CUT = 0.2F, HALO_CUT = 0.3F, REFLECT_CHANCE = 0.3F;
    public static final int BATTERY_EXTRA = 30_000, BATTERY_COOLDOWN = 6000, MISSILE_INTERVAL = 200, GRAPPLE_COOLDOWN = 200,
            REFLECT_COOLDOWN = 20, REVIVE_COOLDOWN = 3600, OWNER_SAVE_COOLDOWN = 12000, LINK_TRANSFER = 2000;

    static BackpackKind back(ValkyrieEntity v) {
        return ValkyrieUnitEffects.backpack(v);
    }

    static boolean working(ValkyrieEntity v) {
        return !v.isShutdown() && !v.isAsleep() && v.isAlive();
    }

    /** 敵らしい相手か（敵対モブか、自分・持ち主を狙っている） */
    static boolean foe(ValkyrieEntity v, LivingEntity e) {
        if (!e.isAlive() || ValkyrieCombat.isAlly(v, e) || e instanceof Player) return false;
        return e instanceof Enemy || e == v.getTarget() || (e instanceof net.minecraft.world.entity.Mob m && (m.getTarget() == v || m.getTarget() == v.getOwner()));
    }

    /** 毎 Tick（{@link ValkyrieUnitEffects#tick} から） */
    static void tick(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        BackpackKind k = back(v);
        if (k == null) return;
        long now = level.getGameTime();
        switch (k) {
            case RECON_DRONE -> {
                if (v.tickCount % 20 == 0) reconDrone(level, v, b);
            }
            case SPARE_BATTERY -> {
                if (v.getEnergy() < ValkyrieEntity.WAKE_ENERGY * 3 && now >= b.batteryReadyAt) {
                    b.batteryReadyAt = now + BATTERY_COOLDOWN;
                    v.setEnergy(v.getEnergy() + v.maxEnergy() / 5);
                    b.line("battery_reserve", ValkyrieEntity.Emote.NOTE);
                    v.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
                }
            }
            case REPAIR_DRONE -> {
                if (v.tickCount % 20 == 0) repairDrone(level, v, b);
            }
            case MICRO_MISSILE -> {
                if (v.getTarget() != null && now >= b.missileReadyAt) {
                    b.missileReadyAt = now + MISSILE_INTERVAL;
                    missiles(level, v, b);
                }
            }
            case GRAPPLE_ANCHOR -> {
                LivingEntity t = v.getTarget();
                if (t != null && now >= b.grappleReadyAt && !ValkyrieUnitEffects.ranged(v)) grapple(level, v, b, t, now);
            }
            case ASTRAL_HALO -> {
                if (v.tickCount % 20 == 0) b.haloOn = b.spend(BackpackKind.ASTRAL_HALO.fe);
                if (b.haloOn && v.tickCount % 100 == 0) {
                    Player owner = v.getOwner();
                    if (owner != null && owner.distanceToSqr(v) <= HALO_RANGE * HALO_RANGE) {
                        owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 1, false, false)); // ハート 4 個分
                    }
                }
                if (b.haloOn && v.tickCount % 10 == 0) Fx.shockwave(level, v.position().add(0, 0.05, 0), 0xFFE08A, (float) HALO_RANGE, 12);
            }
            case RESURRECTION_CORE -> {
                if (v.tickCount % 20 == 0) revive(level, v, b, now);
            }
            case ACCELERATOR -> {
                if (v.tickCount % 20 == 0 && b.spend(BackpackKind.ACCELERATOR.fe)) {
                    for (LivingEntity a : allies(v, ACCEL_RANGE)) {
                        a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
                        a.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, 1, false, false)); // 兵姫は間隔 0.7 倍の目印、プレイヤーは攻撃速度
                    }
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x5AB8FF, 0.4F, 10), v.position().add(0, 1.0, 0), 6, 1.2, 0.5, 1.2, 0.05);
                }
            }
            case COMMAND_LINK -> {
                if (v.tickCount % 20 == 0) commandLink(level, v, now);
            }
            default -> {
            }
        }
    }

    /** 自分・持ち主・同じ持ち主の兵姫（範囲内） */
    static List<LivingEntity> allies(ValkyrieEntity v, double range) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : v.level().getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(range),
                e -> e.isAlive() && (e == v || e == v.getOwner() || (e instanceof ValkyrieEntity o && ValkyrieCombat.isAlly(v, o)))
                        && e.distanceToSqr(v) <= range * range)) {
            out.add(e);
        }
        return out;
    }

    /** 偵察ドローン: 16 m 以内の一番近い見える敵を撃ち、発光させる */
    private static void reconDrone(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(DRONE_RANGE), e -> foe(v, e) && v.hasLineOfSight(e))) {
            double d = e.distanceToSqr(v);
            if (d < bestD && d <= DRONE_RANGE * DRONE_RANGE) {
                bestD = d;
                best = e;
            }
        }
        if (best == null || !b.spend(BackpackKind.RECON_DRONE.fe)) return;
        double a = v.tickCount * 0.06;
        Vec3 drone = v.position().add(Math.cos(a) * 0.9, v.getBbHeight() + 0.2, Math.sin(a) * 0.9);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0x7CFF7C, 0.25F, 6), drone, best.position().add(0, best.getBbHeight() * 0.5, 0), 0.5);
        best.invulnerableTime = 0;
        best.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), DRONE_DAMAGE);
        best.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
    }

    /** リペアドローン: 8 m 以内で HP の減った味方を毎秒 2 回復（ナノマシン溶液は使わない） */
    private static void repairDrone(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        List<LivingEntity> hurt = new ArrayList<>();
        for (LivingEntity a : allies(v, REPAIR_RANGE)) {
            if (!(a instanceof ValkyrieEntity o && o.isShutdown()) && DirectRead.health(a) < a.getMaxHealth()) hurt.add(a);
        }
        if (hurt.isEmpty() || !b.spend(BackpackKind.REPAIR_DRONE.fe)) return;
        for (LivingEntity a : hurt) {
            if (a instanceof ValkyrieEntity o) o.repair(REPAIR_HEAL);
            else a.heal(REPAIR_HEAL);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7CFFB0, 0.35F, 12), a.position().add(0, a.getBbHeight() * 0.6, 0), 4, 0.3, 0.4, 0.3, 0.01);
        }
    }

    /** マイクロミサイル: 24 m 以内の敵最大 4 体へ 2 発ずつ（少し遅れて当たる。爆風は周囲 2 m の敵へ） */
    private static void missiles(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        List<LivingEntity> foes = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(MISSILE_RANGE),
                e -> foe(v, e) && e.distanceToSqr(v) <= MISSILE_RANGE * MISSILE_RANGE && v.hasLineOfSight(e)));
        if (foes.isEmpty() || !b.spend(BackpackKind.MICRO_MISSILE.fe)) return;
        foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(v)));
        float yaw = v.yBodyRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < Math.min(4, foes.size()); i++) {
            LivingEntity t = foes.get(i);
            for (int n = 0; n < 2; n++) {
                int side = n == 0 ? 1 : -1;
                Vec3 from = v.position().add(-Mth.cos(yaw) * 0.35 * side, v.getBbHeight() * 0.95, -Mth.sin(yaw) * 0.35 * side);
                int delay = 6 + i * 3 + n * 2 + (int) (Math.sqrt(t.distanceToSqr(v)) / 2.0);
                Vec3 apex = from.add(t.position().subtract(from).scale(0.5)).add(0, 2.5 + n, 0);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFF9050, 0.25F, delay), from, apex, 0.6);
                WeaponUtil.later(level, delay, () -> {
                    if (!t.isAlive()) return;
                    Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFF9050, 0.25F, 6), apex, at, 0.6);
                    t.invulnerableTime = 0;
                    t.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), MISSILE_DAMAGE);
                    for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(2.0), o -> o != t && foe(v, o))) {
                        o.invulnerableTime = 0;
                        o.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), MISSILE_SPLASH);
                    }
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                    level.playSound(null, t.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.5F, 1.6F);
                });
            }
        }
        v.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.9F);
    }

    /** グラップルアンカー: 離れた小さな敵は引き寄せ、大きな敵・ボス・高い所の敵へは自分が飛び付く */
    private static void grapple(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b, LivingEntity t, long now) {
        double d = v.distanceTo(t);
        if (d < GRAPPLE_MIN || d > GRAPPLE_MAX || !v.hasLineOfSight(t) || !b.spend(BackpackKind.GRAPPLE_ANCHOR.fe)) return;
        b.grappleReadyAt = now + GRAPPLE_COOLDOWN;
        Vec3 from = v.position().add(0, v.getBbHeight() * 0.6, 0), to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFA040, 0.25F, 12), from, to, 0.3);
        boolean big = t.getType().is(Tags.EntityTypes.BOSSES) || t.getBbWidth() > 1.4F || t.getBbHeight() > 2.6F || t.getY() - v.getY() > 2.5;
        Vec3 dir = to.subtract(from).normalize();
        if (big) { // 自分が飛び付く
            v.setDeltaMovement(dir.scale(Math.min(2.2, d * 0.18)).add(0, 0.35, 0));
            v.hasImpulse = true;
            v.fallDistance = 0.0F;
        } else {   // 引き寄せる
            t.setDeltaMovement(dir.scale(-Math.min(1.8, d * 0.15)).add(0, 0.3, 0));
            t.hasImpulse = true;
            t.hurtMarked = true;
        }
        v.playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
    }

    /** リザレクションコア: 16 m 以内の機能停止した味方の兵姫を HP 50% で再起動（1 体ごとに 3 分） */
    private static void revive(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b, long now) {
        for (ValkyrieEntity o : level.getEntitiesOfClass(ValkyrieEntity.class, v.getBoundingBox().inflate(REVIVE_RANGE),
                o -> o != v && o.isShutdown() && ValkyrieCombat.isAlly(v, o) && o.distanceToSqr(v) <= REVIVE_RANGE * REVIVE_RANGE)) {
            if (now < o.combat().reviveReadyAt || !b.spend(BackpackKind.RESURRECTION_CORE.fe)) continue;
            o.combat().reviveReadyAt = now + REVIVE_COOLDOWN;
            o.repair(o.getMaxHealth() * 0.5F);
            b.line("revive_ally", ValkyrieEntity.Emote.ALERT);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFB0D8, 0.4F, 14), v.position().add(0, 1.0, 0), o.position().add(0, 1.0, 0), 0.4);
            Fx.shockwave(level, o.position().add(0, 0.05, 0), 0xFFB0D8, 2.5F, 14);
            level.playSound(null, o.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.4F);
        }
    }

    /** コマンドリンク: 24 m 以内の味方の兵姫の電力を、多い子から一番少ない子へ毎秒 2,000 FE。つないだ子に印（同調率 +1 段・集中） */
    private static void commandLink(ServerLevel level, ValkyrieEntity v, long now) {
        List<ValkyrieEntity> team = new ArrayList<>();
        for (LivingEntity e : allies(v, LINK_RANGE)) if (e instanceof ValkyrieEntity o && working(o)) team.add(o);
        for (ValkyrieEntity o : team) o.commandLinkUntil = now + 40;
        if (team.size() < 2) return;
        team.sort(Comparator.comparingDouble(o -> o.getEnergy() / (double) o.maxEnergy()));
        ValkyrieEntity poor = team.get(0), rich = team.get(team.size() - 1);
        double gap = rich.getEnergy() / (double) rich.maxEnergy() - poor.getEnergy() / (double) poor.maxEnergy();
        if (gap < 0.1) return;
        int amount = Math.min(LINK_TRANSFER, Math.min(rich.getEnergy() - ValkyrieEntity.WAKE_ENERGY, poor.maxEnergy() - poor.getEnergy()));
        if (amount <= 0) return;
        rich.setEnergy(rich.getEnergy() - amount);
        poor.setEnergy(poor.getEnergy() + amount);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFD27F, 0.25F, 8), rich.position().add(0, 1.2, 0), poor.position().add(0, 1.2, 0), 0.6);
    }

    // ------------------------------------------------------------------ 増設バッテリー

    /** 電力の最大の加算（増設バッテリー。クライアントでも見た目の背部から） */
    public static int extraEnergy(ValkyrieEntity v) {
        return v.unitItem(com.automatavalkyries.entity.ValkyrieInventory.BACKPACK).getItem() instanceof com.automatavalkyries.item.unit.ValkyrieUnits.BackpackItem bi
                && bi.kind == BackpackKind.SPARE_BATTERY ? BATTERY_EXTRA : 0;
    }

    /** アクセラレーターの効果を受けているか（兵姫の攻撃・技の間隔 0.7 倍） */
    public static boolean accelerated(ValkyrieEntity v) {
        return v.hasEffect(MobEffects.DIG_SPEED);
    }

    // ------------------------------------------------------------------ 被弾

    /** サブアーム（正面の遠距離 -20%）・アストラルハロー（12 m 以内の味方 -30%） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0) return;
        float amount = event.getAmount();
        Entity direct = event.getSource().getDirectEntity();
        if (victim instanceof ValkyrieEntity v && back(v) == BackpackKind.SUB_ARM && working(v) && direct != null && ProjectileLike.is(direct)) {
            Vec3 from = direct.position().subtract(v.position());
            Vec3 look = Vec3.directionFromRotation(0, v.yBodyRot);
            if (from.x * look.x + from.z * look.z > 0) amount *= 1.0F - SUB_ARM_CUT;
        }
        for (ValkyrieEntity h : victim.level().getEntitiesOfClass(ValkyrieEntity.class, victim.getBoundingBox().inflate(HALO_RANGE),
                h -> back(h) == BackpackKind.ASTRAL_HALO && h.combat().haloOn && working(h) && h.distanceToSqr(victim) <= HALO_RANGE * HALO_RANGE)) {
            if (victim == h || victim == h.getOwner() || (victim instanceof ValkyrieEntity o && ValkyrieCombat.isAlly(h, o))) {
                amount *= 1.0F - HALO_CUT;
                break;
            }
        }
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    /** プリズムリフレクター: 飛んでくる弾を 30% で撃った相手へ跳ね返す（1 秒に 1 回まで） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ValkyrieEntity v) || v.level().isClientSide || back(v) != BackpackKind.PRISM_REFLECTOR || !working(v)) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null || !ProjectileLike.is(direct)) return;
        ValkyrieCombat.Brain b = v.combat();
        long now = v.level().getGameTime();
        if (now < b.reflectReadyAt || v.getRandom().nextFloat() >= REFLECT_CHANCE || !b.spend(BackpackKind.PRISM_REFLECTOR.fe)) return;
        b.reflectReadyAt = now + REFLECT_COOLDOWN;
        event.setCanceled(true);
        direct.discard();
        Entity shooter = event.getSource().getEntity();
        if (v.level() instanceof ServerLevel level) {
            Vec3 at = v.position().add(0, v.getBbHeight() * 0.6, 0);
            Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), 0xC8A0FF, 0.5F, 10), at, 10, 0.2);
            if (shooter instanceof LivingEntity s && s.isAlive() && !ValkyrieCombat.isAlly(v, s)) {
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xC8A0FF, 0.35F, 10), at, s.position().add(0, s.getBbHeight() * 0.5, 0), 0.4);
                s.invulnerableTime = 0;
                s.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), event.getAmount());
            }
        }
        b.line("reflect", ValkyrieEntity.Emote.NOTE);
        v.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
    }

    /** リザレクションコア: 持ち主が倒れる 1 撃を 1 回耐えさせ、3 秒無敵（10 分ごと） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOwnerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player owner) || owner.level().isClientSide) return;
        long now = owner.level().getGameTime();
        for (ValkyrieEntity v : owner.level().getEntitiesOfClass(ValkyrieEntity.class, owner.getBoundingBox().inflate(REVIVE_RANGE),
                v -> owner.getUUID().equals(v.getOwnerUUID()) && back(v) == BackpackKind.RESURRECTION_CORE && working(v))) {
            ValkyrieCombat.Brain b = v.combat();
            if (now < b.ownerSaveReadyAt || !b.spend(BackpackKind.RESURRECTION_CORE.fe)) continue;
            b.ownerSaveReadyAt = now + OWNER_SAVE_COOLDOWN;
            event.setCanceled(true);
            owner.setHealth(1.0F);
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, true));
            owner.invulnerableTime = 60;
            b.line("owner_saved", ValkyrieEntity.Emote.ALERT);
            if (owner.level() instanceof ServerLevel level) {
                Fx.shockwave(level, owner.position().add(0, 0.05, 0), 0xFFB0D8, 3.0F, 16);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFB0D8, 0.4F, 14), v.position().add(0, 1.0, 0), owner.position().add(0, 1.0, 0), 0.4);
                level.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
            }
            return;
        }
    }
}
