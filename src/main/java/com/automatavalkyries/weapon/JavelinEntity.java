package com.automatavalkyries.weapon;

import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * サンダージャベリンの投げ槍。弧を描いて飛び、当たった相手に 18 ダメージで刺さる（相手と一緒に動く）か、地面・壁に刺さる。
 * 刺さって 1 秒後に雷（見た目だけの落雷＋半径 4 の敵へ 25 ダメージ。撃った者には当たらない、火は付かない）。
 * <p>オーバーロードで投げた槍（{@link #setJudgement}）は雷神の裁き: 飛ぶ間は紅と金の稲光の尾、刺さった所に雷の陣、
 * 落雷は半径 12 で 9 本（中心と半径 8 の輪に 8 本）</p>
 */
public class JavelinEntity extends ThrowableProjectile {

    public static final float SPEED = 2.4F, DAMAGE = 70.0F, STRIKE_DAMAGE = 100.0F;
    public static final double STRIKE_RADIUS = 4.0, OVER_STRIKE_RADIUS = 12.0, OVER_RING = 8.0;
    public static final int FUSE = 20, LIFETIME = 120;
    public static final int COLOR = 0xFFE860, CORE = 0xFFFFF0;
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(JavelinEntity.class, EntityDataSerializers.BOOLEAN);

    /** 見た目の向き（最後に飛んでいた向き。刺さった後もこの向きのまま描く） */
    public Vec3 renderDir = new Vec3(0, 0, 1);
    private int stuckTicks = -1;
    /** オーバーロードで投げた（雷神の裁き） */
    private boolean judgement;
    private Entity stuckIn;
    private Vec3 stuckOffset = Vec3.ZERO;

    public JavelinEntity(EntityType<? extends JavelinEntity> type, Level level) {
        super(type, level);
    }

    public JavelinEntity(Level level, LivingEntity owner) {
        super(ModWeapons.JAVELIN.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STUCK, false);
    }

    /** オーバーロードの雷神の裁きにする（投げる前に） */
    public void setJudgement() {
        judgement = true;
    }

    public boolean stuck() {
        return entityData.get(STUCK);
    }

    @Override
    protected float getGravity() {
        return 0.03F;
    }

    @Override
    public void tick() {
        if (getDeltaMovement().lengthSqr() > 1.0E-4) renderDir = getDeltaMovement().normalize();
        if (stuck()) {
            baseTick();
            setDeltaMovement(Vec3.ZERO);
            if (level().isClientSide()) {
                if (tickCount % 2 == 0) level().addParticle(new FxOptions(ModParticles.SPARK.get(), COLOR, 0.18F, 8, 0), getX(), getY(), getZ(), 0, 0.02, 0);
                return;
            }
            if (stuckIn != null && stuckIn.isAlive()) setPos(stuckIn.position().add(stuckOffset));
            if (judgement && level() instanceof ServerLevel server && stuckTicks % 5 == 0) { // 雷の陣が開いていく
                float grow = Math.min(1F, (stuckTicks + 5) / (float) FUSE);
                Fx.shockwave(server, position().add(0, 0.05, 0), Overload.GOLD, (float) OVER_STRIKE_RADIUS * grow, 8);
                Fx.spray(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 10), position().add(0, 0.5, 0), 8, OVER_STRIKE_RADIUS * 0.4 * grow, 0.3, OVER_STRIKE_RADIUS * 0.4 * grow, 0.05);
                if (stuckTicks == 0) Fx.mandala(server, position().add(0, 0.05, 0), COLOR, (float) OVER_STRIKE_RADIUS * 0.6F, FUSE + 10, false, true);
            }
            if (++stuckTicks >= FUSE) strike();
            return;
        }
        if (!level().isClientSide() && tickCount > LIFETIME) {
            discard();
            return;
        }
        if (judgement && level() instanceof ServerLevel server) { // 紅と金の稲光の尾
            Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), tickCount % 2 == 0 ? Overload.CRIMSON : Overload.GOLD, 0.5F, 12), position(), 2, 0.15, 0.01);
            Fx.spray(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.3F, 10), position(), 3, 0.4, 0.06);
        }
        if (level().isClientSide()) {
            level().addParticle(new FxOptions(ModParticles.GLOW.get(), COLOR, 0.22F, 8, 0), getX(), getY(), getZ(), 0, 0, 0);
            if (tickCount % 2 == 0) level().addParticle(new FxOptions(ModParticles.SPARK.get(), CORE, 0.14F, 8, 0), getX(), getY(), getZ(), 0, 0, 0);
        }
        super.tick();
    }

    private void stick(Entity in) {
        entityData.set(STUCK, true);
        stuckTicks = 0;
        stuckIn = in;
        if (in != null) stuckOffset = position().subtract(in.position());
        setDeltaMovement(Vec3.ZERO);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** 落雷 */
    private void strike() {
        if (!(level() instanceof ServerLevel server)) return;
        if (judgement) {
            judgementStrike(server);
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(getX(), getY(), getZ());
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        Entity owner = getOwner();
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(STRIKE_RADIUS),
                e -> e != owner && e.isAlive() && e.distanceTo(this) <= STRIKE_RADIUS + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(this, WeaponDamage.ARC, this, owner), STRIKE_DAMAGE);
        }
        Vec3 c = position();
        Fx.shockwave(server, c.add(0, 0.05, 0), COLOR, (float) STRIKE_RADIUS + 1, 12);
        Fx.shockwave(server, c.add(0, 0.05, 0), 0xFFFFFF, (float) STRIKE_RADIUS, 8);
        Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), CORE, 2.0F, 8), c.add(0, 1, 0), 1, 0, 0);
        Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.4F, 12), c.add(0, 0.5, 0), 24, 0.6);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            WeaponUtil.lightning(server, c.add(0, 0.3, 0), c.add(Math.cos(a) * STRIKE_RADIUS, 0.2, Math.sin(a) * STRIKE_RADIUS), COLOR);
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.0F);
        discard();
    }

    /** オーバーロードの雷神の裁き: 9 本の落雷（見た目だけ）、半径 12 の敵すべてへ稲妻 */
    private void judgementStrike(ServerLevel server) {
        Vec3 c = position();
        for (int i = 0; i <= 8; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt == null) continue;
            if (i == 0) bolt.moveTo(c.x, c.y, c.z);
            else {
                double a = (i - 1) * Math.PI / 4;
                bolt.moveTo(c.x + Math.cos(a) * OVER_RING, c.y, c.z + Math.sin(a) * OVER_RING);
            }
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        Entity owner = getOwner();
        int n = 0;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(OVER_STRIKE_RADIUS),
                e -> e != owner && e.isAlive() && e.distanceTo(this) <= OVER_STRIKE_RADIUS + e.getBbWidth())) {
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(this, WeaponDamage.ARC, this, owner), STRIKE_DAMAGE);
            if (n++ < 8) {
                Vec3 at = new Vec3(target.getX(), target.getY(0.5), target.getZ());
                Batch3T2.bolt(server, at.add(0, 8, 0), at, n % 2 == 0 ? COLOR : Overload.GOLD);
                OverloadArts.impact(server, at);
            }
        }
        Fx.shockwave(server, c.add(0, 0.05, 0), COLOR, (float) OVER_STRIKE_RADIUS + 1, 16);
        Fx.shockwave(server, c.add(0, 0.08, 0), 0xFFFFFF, (float) OVER_STRIKE_RADIUS * 0.7F, 12);
        Fx.shockwave(server, c.add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_STRIKE_RADIUS * 0.4F, 20);
        Fx.mandala(server, c.add(0, 0.05, 0), Overload.GOLD, (float) OVER_STRIKE_RADIUS * 0.6F, 24, false, false);
        Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), CORE, 5.0F, 8), c.add(0, 1.5, 0), 1, 0, 0);
        Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.45F, 14), c.add(0, 0.5, 0), 40, 0.9);
        WeaponUtil.later(server, 1, () -> {
            for (int i = 0; i < 8; i++) { // 放射状に地を這う稲妻
                double a = i * Math.PI / 4 + Math.PI / 8;
                Batch3T2.bolt(server, c.add(0, 0.3, 0), c.add(Math.cos(a) * OVER_STRIKE_RADIUS, 0.2, Math.sin(a) * OVER_STRIKE_RADIUS), i % 2 == 0 ? COLOR : Overload.CRIMSON);
            }
        });
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.8F);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.8F);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide() || stuck()) return;
        Entity target = result.getEntity();
        target.hurt(WeaponDamage.source(this, WeaponDamage.ENERGY, this, getOwner()), DAMAGE);
        setPos(result.getLocation());
        stick(target);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide() || stuck()) return;
        setPos(result.getLocation());
        stick(null);
    }
}
