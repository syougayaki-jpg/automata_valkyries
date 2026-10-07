package com.automatavalkyries.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * テスト用の浮遊ボススケルトン（2026-10-06 ユーザー指定「スラスターのテストに、地面から 30 ブロック浮いている HP 1 万のボススケルトン。
 * 矢やファイヤーボールを避けるか確かめるため、戦術兵姫を優先的に遠距離攻撃する」）。
 * <ul>
 *   <li>出したらその場から 30 ブロック上へ上がり、その高さ・位置に留まる（動かない、落ちない、ノックバックしない）</li>
 *   <li>HP 10,000（最大 HP の上限は mixin/HealthCapMixin で上げてある）。forge:bosses</li>
 *   <li>狙う順: 戦術兵姫 → プレイヤー。矢 3 連射・小さな火の玉 3 連・大きな火の玉（地形を壊さない）を順に撃つ</li>
 * </ul>
 */
public class SkyBossSkeleton extends Skeleton {

    /** HP 1 万（最大 HP の上限は mixin/HealthCapMixin で上げてあるので、前の「実際は 1,000 でダメージを 1/10」はやめた。2026-10-08） */
    public static final float MAX_HEALTH = 10_000.0F;
    /** 出した所から上がる高さ・狙う距離 */
    public static final double HOVER = 30.0, RANGE = 64.0;

    private final ServerBossEvent bossBar = (ServerBossEvent) new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(false);
    /** 留まる位置（初めの Tick に、出した所の 30 ブロック上に決める） */
    private Vec3 anchor;

    public SkyBossSkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Skeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.FOLLOW_RANGE, RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Barrage(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, (float) RANGE));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, ValkyrieEntity.class, 10, false, false, v -> !((ValkyrieEntity) v).isShutdown()));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override
    public void reassessWeaponGoal() {
        // 弓の立ち回り（歩いて撃つ）は使わない。撃つのは Barrage
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 浮いたまま動かない
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (anchor == null) anchor = position().add(0, HOVER, 0);
        setPos(anchor.x, anchor.y, anchor.z);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0F;
        bossBar.setProgress(getHealth() / getMaxHealth());
        bossBar.setName(getDisplayName().copy().append(String.format(" %,.0f / %,.0f", getHealth(), getMaxHealth())));
        // 戦術兵姫を優先: プレイヤーを狙っていても近くに兵姫がいれば乗り換える
        if (tickCount % 20 == 0 && !(getTarget() instanceof ValkyrieEntity)) {
            ValkyrieEntity v = level().getNearestEntity(ValkyrieEntity.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(RANGE).ignoreLineOfSight(),
                    this, getX(), getY(), getZ(), getBoundingBox().inflate(RANGE));
            if (v != null && !v.isShutdown()) setTarget(v);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchor != null) {
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("AnchorY")) anchor = new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ"));
    }

    /** 撃つ: 矢 3 連射 → 小さな火の玉 3 連 → 大きな火の玉、を 1.5 秒ごとに順に */
    static class Barrage extends Goal {
        private final SkyBossSkeleton boss;
        private int timer = 40, step;

        Barrage(SkyBossSkeleton boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive() && boss.distanceTo(t) <= RANGE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null || !(boss.level() instanceof ServerLevel level)) return;
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (--timer > 0) return;
            timer = 30;
            switch (step++ % 3) {
                case 0 -> {
                    for (int i = 0; i < 3; i++) arrow(level, t, i * 4.0F - 4.0F);
                    boss.playSound(SoundEvents.SKELETON_SHOOT, 2.0F, 0.8F);
                }
                case 1 -> {
                    for (int i = 0; i < 3; i++) {
                        Vec3 d = aim(t).add(boss.getRandom().nextGaussian() * 0.6, 0, boss.getRandom().nextGaussian() * 0.6);
                        SmallFireball f = new SmallFireball(level, boss, d.x, d.y, d.z);
                        f.setPos(boss.getX(), boss.getEyeY() - 0.2, boss.getZ());
                        level.addFreshEntity(f);
                    }
                    boss.playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 1.0F);
                }
                default -> {
                    Vec3 d = aim(t);
                    LargeFireball f = new LargeFireball(level, boss, d.x, d.y, d.z, 0); // 爆発の強さ 0（地形を壊さない。当たれば火の玉のダメージ）
                    f.setPos(boss.getX(), boss.getEyeY() - 0.2, boss.getZ());
                    level.addFreshEntity(f);
                    boss.playSound(SoundEvents.GHAST_SHOOT, 2.0F, 1.0F);
                }
            }
        }

        private Vec3 aim(LivingEntity t) {
            return new Vec3(t.getX() - boss.getX(), t.getY(0.5) - boss.getEyeY(), t.getZ() - boss.getZ());
        }

        private void arrow(ServerLevel level, LivingEntity t, float spreadYaw) {
            AbstractArrow arrow = ProjectileUtil.getMobArrow(boss, new ItemStack(Items.ARROW), 1.0F);
            arrow.setPos(boss.getX(), boss.getEyeY() - 0.1, boss.getZ());
            Vec3 d = aim(t).yRot(spreadYaw * net.minecraft.util.Mth.DEG_TO_RAD);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            arrow.shoot(d.x, d.y + h * 0.05, d.z, 2.4F, 2.0F);
            arrow.setBaseDamage(4.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(arrow);
        }
    }
}
