package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * 浮遊要塞（クロノ・バスティオン）の中ボス「要塞守護機 バスティオン・キーパー」（2026-10-19 ユーザー指定「ティア３」。HP {@value #MAX_HP}、数値は【仮】）。
 * 光輪を背負い、4 枚の盾板を周りに浮かべた浮遊の守護機。
 * <ul>
 *   <li>時の檻: 相手の足元に半径 {@value #STASIS_R} の紫の輪。4 秒の間、中は鈍化 III・採掘速度低下。終わりに中へ {@value #STASIS}</li>
 *   <li>天の槍: 相手の所と近くの 2 か所に赤い輪、1.5 秒後に空から光の槍（1 つ {@value #LANCE}・半径 2.5）</li>
 *   <li>扇の弾幕: 9 発の扇を 3 回（1 発 {@value #BOLT}）</li>
 *   <li>重力の錨: 14 m より近い相手に 1 秒光を集め、16 m の相手を自分へ引き寄せ、0.6 秒後に半径 5 の衝撃（{@value #ANCHOR}）。
 *       空の上で落とさない向き（外へ飛ばさず、内へ引く）</li>
 *   <li>第 2 形態（HP 50%）: 迎撃機 2 機と時間干渉機 1 機を呼び、技の間隔が 3/4</li>
 * </ul>
 */
public class BastionKeeperEntity extends ChronoMidBoss {

    public static final float MAX_HP = 6000.0F, STASIS = 20.0F, LANCE = 22.0F, BOLT = 5.0F, ANCHOR = 14.0F;
    public static final double STASIS_R = 5.0, LANCE_R = 2.5, ANCHOR_R = 16.0;
    public static final byte EVENT_STASIS = 80, EVENT_LANCE = 81, EVENT_VOLLEY = 82, EVENT_ANCHOR = 83;
    static final int VIOLET = 0xB060FF, RED = 0xFF3040, GOLD = 0xFFD166;

    public int stasisTick = -100, lanceTick = -100, volleyTick = -100, anchorTick = -100;

    /** 時間差で起きる技（檻の持続と爆発・槍の落下・錨の引き寄せと衝撃） */
    private record Strike(int kind, Vec3 at, int start, int at2) {}

    private static final int STASIS_FIELD = 0, LANCE_FALL = 1, ANCHOR_PULL = 2, ANCHOR_BLAST = 3;
    private final List<Strike> strikes = new ArrayList<>();

    public BastionKeeperEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 16.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    protected ResourceLocation rewardLoot() {
        return loot("chests/chrono_bastion_reward");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new KeeperGoal(this));
        ChronoGazer.addTargets(this, 16.0);
    }

    @Override
    protected void onPhase2() {
        if (!(level() instanceof ServerLevel server)) return;
        summon(server, ModEntities.CHRONO_INTERCEPTOR.get(), local(4.0, 0, 3.0));
        summon(server, ModEntities.CHRONO_INTERCEPTOR.get(), local(4.0, 0, -3.0));
        summon(server, ModEntities.DELAY_NODE.get(), local(2.5, -3.0, 0));
        playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.4F);
    }

    private void summon(ServerLevel server, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(server);
        if (mob == null) return;
        mob.moveTo(at.x, at.y, at.z, getYRot(), 0.0F);
        mob.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        mob.setTarget(getTarget());
        server.addFreshEntity(mob);
        Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.3F, 14), at, 20, 0.3);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_STASIS -> stasisTick = tickCount;
            case EVENT_LANCE -> lanceTick = tickCount;
            case EVENT_VOLLEY -> volleyTick = tickCount;
            case EVENT_ANCHOR -> anchorTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    // ---- 技 ----

    /** 時の檻: 相手の足元に紫の輪 */
    void stasis(LivingEntity t) {
        strikes.add(new Strike(STASIS_FIELD, t.position(), tickCount, tickCount + 80));
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_STASIS);
    }

    /** 天の槍: 相手の所と近くの 2 か所 */
    void lance(LivingEntity t) {
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 2.5 + random.nextDouble() * 2.0;
            strikes.add(new Strike(LANCE_FALL, t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), tickCount, tickCount + 30 + i * 4));
        }
        playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_LANCE);
    }

    /** 扇の弾幕の 1 回（9 発、60 度） */
    void fan(LivingEntity t) {
        Vec3 from = local(2.6, 1.0, 0);
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        for (int i = -4; i <= 4; i++) {
            double ang = Math.toRadians(i * 7.5);
            Vec3 dir = new Vec3(aim.x * Math.cos(ang) - aim.z * Math.sin(ang), aim.y, aim.x * Math.sin(ang) + aim.z * Math.cos(ang));
            ChronoBolt.fireDir(this, from, dir, BOLT, 1.6F, 0.0F, false);
        }
        playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 1.6F);
        level().broadcastEntityEvent(this, EVENT_VOLLEY);
    }

    /** 重力の錨: 光を集めてから引き寄せ、衝撃 */
    void anchor() {
        strikes.add(new Strike(ANCHOR_PULL, position(), tickCount, tickCount + 20));
        strikes.add(new Strike(ANCHOR_BLAST, position(), tickCount, tickCount + 32));
        playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.3F);
        level().broadcastEntityEvent(this, EVENT_ANCHOR);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || strikes.isEmpty()) return;
        if (isDefeated() || !isAlive()) {
            strikes.clear();
            return;
        }
        for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            int left = s.at2() - tickCount;
            switch (s.kind()) {
                case STASIS_FIELD -> {
                    if (tickCount % 5 == 0) {
                        ring(server, s.at().add(0, 0.15, 0), STASIS_R, VIOLET, 32);
                        for (LivingEntity e : victims(s.at(), STASIS_R)) {
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2), this);
                            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 1), this);
                        }
                    }
                    if (left <= 0) {
                        hit(s.at(), STASIS_R, STASIS);
                        Fx.shockwave(server, s.at().add(0, 0.2, 0), VIOLET, (float) STASIS_R + 1.0F, 14);
                        Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.35F, 16), s.at().add(0, 1, 0), 40, 0.5);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.GLASS_BREAK, getSoundSource(), 2.0F, 0.5F);
                    }
                }
                case LANCE_FALL -> {
                    if (left > 0 && tickCount % 3 == 0) ring(server, s.at().add(0, 0.15, 0), LANCE_R, RED, 16);
                    if (left <= 0) {
                        Fx.line(server, MementoMoriEntity.glow(GOLD, 0.45F, 12), s.at().add(0, 24, 0), s.at(), 0.6);
                        Fx.shockwave(server, s.at().add(0, 0.2, 0), GOLD, (float) LANCE_R + 1.0F, 12);
                        hit(s.at(), LANCE_R, LANCE);
                        server.playSound(null, BlockPos.containing(s.at()), SoundEvents.LIGHTNING_BOLT_IMPACT, getSoundSource(), 1.5F, 1.4F);
                    }
                }
                case ANCHOR_PULL -> {
                    if (left > 0 && tickCount % 2 == 0) Fx.gather(server, MementoMoriEntity.glow(VIOLET, 0.3F, 10), local(2.0, 0, 0), 6, 6.0, 10);
                    if (left <= 0) {
                        for (LivingEntity e : victims(position(), ANCHOR_R)) {
                            Vec3 in = position().subtract(e.position()).multiply(1, 0, 1);
                            if (in.lengthSqr() < 1.0) continue;
                            e.setDeltaMovement(in.normalize().scale(Math.min(1.6, in.length() * 0.12)).add(0, 0.35, 0));
                            e.hurtMarked = true;
                        }
                        Fx.shockwave(server, position().add(0, 0.3, 0), VIOLET, (float) ANCHOR_R, 10);
                        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.2F);
                    }
                }
                case ANCHOR_BLAST -> {
                    if (left <= 0) {
                        hit(position(), 5.0, ANCHOR);
                        Fx.shockwave(server, position().add(0, 0.3, 0), 0xFFFFFF, 6.0F, 12);
                        Fx.burst(server, MementoMoriEntity.glow(VIOLET, 0.4F, 14), position().add(0, 1.5, 0), 50, 0.6);
                        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 1.3F);
                    }
                }
            }
            if (left <= 0) it.remove();
        }
    }

    /** 当ててよい相手のうち、at から半径 r（水平。上下は 3 ブロックまで）の中の者 */
    List<LivingEntity> victims(Vec3 at, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 3.0, r),
                e -> ChronoGazer.canHurt(e) && e.position().multiply(1, 0, 1).distanceToSqr(at.multiply(1, 0, 1)) <= r * r);
    }

    void hit(Vec3 at, double r, float damage) {
        for (LivingEntity e : victims(at, r)) e.hurt(damageSources().mobAttack(this), damage);
    }

    /** 地面の輪（count 点の光） */
    static void ring(ServerLevel server, Vec3 at, double r, int color, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Fx.one(server, MementoMoriEntity.glow(color, 0.25F, 6), at.add(Math.cos(a) * r, 0, Math.sin(a) * r), Vec3.ZERO);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel server) Fx.spray(server, MementoMoriEntity.glow(VIOLET, 0.2F, 8), position().add(0, 2.0, 0), 4, 0.8, 0.1);
        return hurt;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    /** 戦い方: 8〜16 m を保ちながら、近ければ重力の錨、ほかは 時の檻 → 天の槍 → 扇の弾幕 */
    static class KeeperGoal extends Goal {
        private final BastionKeeperEntity boss;
        private int stasisCd = 60, lanceCd = 110, volleyCd = 30, anchorCd = 140, volleys, repath;

        KeeperGoal(BastionKeeperEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return boss.getTarget() != null && boss.getTarget().isAlive() && !boss.isDefeated();
        }

        @Override
        public void stop() {
            boss.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null) return;
            stasisCd--;
            lanceCd--;
            volleyCd--;
            anchorCd--;
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = boss.distanceTo(t);
            if (--repath <= 0) {
                repath = 15;
                if (d > 16.0) boss.getNavigation().moveTo(t, 1.0);
                else if (d < 8.0 && anchorCd > 20) { // 下がる
                    Vec3 away = boss.position().subtract(t.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1.0e-4) {
                        Vec3 p = boss.position().add(away.normalize().scale(5.0));
                        boss.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
                    }
                } else boss.getNavigation().stop();
            }
            if (volleys > 0) {
                if (boss.tickCount % 8 == 0) {
                    volleys--;
                    boss.fan(t);
                }
                return;
            }
            if (anchorCd <= 0 && d < 14.0) {
                anchorCd = boss.cd(220);
                boss.anchor();
            } else if (stasisCd <= 0) {
                stasisCd = boss.cd(160);
                boss.stasis(t);
            } else if (lanceCd <= 0) {
                lanceCd = boss.cd(120);
                boss.lance(t);
            } else if (volleyCd <= 0 && boss.hasLineOfSight(t)) {
                volleyCd = boss.cd(70);
                volleys = 3;
            }
        }
    }
}
