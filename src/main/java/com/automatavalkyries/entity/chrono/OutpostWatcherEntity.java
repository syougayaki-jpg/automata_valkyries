package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 基地（クロノ・アウトリポスト）の中ボス「前哨司令機 ウォッチャー」（2026-10-15 ユーザー指定。HP {@value #MAX_HP}、数値は【仮】）。
 * 背に通信塔（回るレーダーの皿）を背負った 2 本脚の指揮官機。両腕に連装機銃。
 * <ul>
 *   <li>レーダー照射: 相手を捉えて光らせ（発光 8 秒）、半径 {@value #COMMAND} m の仲間に相手を教える。捉えている間は砲撃の狙いが正確になる</li>
 *   <li>砲撃要請: 相手のまわり 5 か所に、空から山なりの砲弾を落とす（1 つ {@value #ARTILLERY}・半径 2.5。落ちる所に赤い印。捉えていれば狙いが狭い）</li>
 *   <li>連装機銃: 4〜24 m の見えている相手へ 12 連射（1 発 {@value #GUN}・無敵時間を貫く）</li>
 *   <li>盾ドローン: まわりを回る盾ドローン 3 機を出す。1 機でも残っている間は受けるダメージが半分（ドローンから本体へ光の線）。全部落とされると 30 秒後に出し直す</li>
 *   <li>第 2 形態（HP 50%）: サイレン・アイを 2 機呼び、技の間隔が 3/4</li>
 * </ul>
 */
public class OutpostWatcherEntity extends ChronoMidBoss {

    public static final float MAX_HP = 1500.0F, ARTILLERY = 12.0F, GUN = 3.0F;
    public static final double COMMAND = 48.0;
    public static final byte EVENT_RADAR = 70, EVENT_GUN = 71, EVENT_CALL = 72;

    public int radarTick = -100, gunTick = -100, callTick = -100;
    private int markedUntil = -1;

    public OutpostWatcherEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.WHITE;
    }

    @Override
    protected ResourceLocation rewardLoot() {
        return loot("chests/chrono_outpost_reward");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new WatcherGoal(this));
        ChronoGazer.addTargets(this, 12.0);
    }

    /** まわりの自分の盾ドローンの数 */
    int shieldDrones() {
        return level().getEntitiesOfClass(ShieldDroneEntity.class, getBoundingBox().inflate(16.0), d -> d.isAlive() && d.owner() == this).size();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && shieldDrones() > 0) {
            amount *= 0.5F; // 盾ドローンが守る
            if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(0.6), getZ(), 8, 0.6, 0.8, 0.6, 0.1);
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void onPhase2() {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 2; i++) {
            SirenEyeEntity eye = ModEntities.SIREN_EYE.get().create(server);
            if (eye == null) continue;
            Vec3 p = local(4.5, 0, i == 0 ? 2.0 : -2.0);
            eye.moveTo(p.x, p.y, p.z, getYRot(), 0.0F);
            eye.finalizeSpawn(server, server.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            eye.setTarget(getTarget());
            server.addFreshEntity(eye);
        }
        playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 3.0F, 0.5F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_RADAR -> radarTick = tickCount;
            case EVENT_GUN -> gunTick = tickCount;
            case EVENT_CALL -> callTick = tickCount;
            default -> super.handleEntityEvent(id);
        }
    }

    boolean marked() {
        return tickCount < markedUntil;
    }

    /** レーダー照射: 相手を光らせ、まわりの仲間に教える */
    void radar(LivingEntity t) {
        markedUntil = tickCount + 160;
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), this);
        ChronoGazer.alert(this, t, COMMAND);
        for (net.minecraft.world.entity.Mob m : level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(COMMAND),
                m -> m != this && m instanceof ChronoGazer && m.isAlive())) {
            m.setTarget(t); // 捉えた相手へ一斉に
        }
        if (level() instanceof ServerLevel server) {
            Fx.line(server, MementoMoriEntity.glow(0xFF3040, 0.3F, 10), local(4.6, 0, 0), t.position().add(0, 1.0, 0), 0.7);
            Fx.shockwave(server, local(4.6, 0, 0), 0xFF3040, 6.0F, 16);
            if (t instanceof ServerPlayer p) p.displayClientMessage(Component.translatable("message.automata_valkyries.outpost_watcher.marked").withStyle(ChatFormatting.RED), true);
        }
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.8F);
        level().broadcastEntityEvent(this, EVENT_RADAR);
    }

    /** 砲撃要請: 相手のまわり 5 か所に空から砲弾 */
    void artillery(LivingEntity t) {
        double spread = marked() ? 1.5 : 4.0;
        for (int i = 0; i < 5; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : spread * (0.4 + random.nextDouble() * 0.6);
            Vec3 to = t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Vec3 from = to.add(random.nextGaussian() * 3, 22, random.nextGaussian() * 3);
            MortarShellEntity.lob(this, from, to, 30 + i * 4, ARTILLERY, 2.5F);
        }
        playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_CALL);
    }

    /** 盾ドローンを 3 機出す */
    void deployDrones() {
        if (!(level() instanceof ServerLevel server)) return;
        for (int i = 0; i < 3; i++) {
            ShieldDroneEntity drone = ModEntities.SHIELD_DRONE.get().create(server);
            if (drone == null) continue;
            Vec3 p = local(4.0, 0, 0).add(Math.cos(i * 2.09) * 2, 0, Math.sin(i * 2.09) * 2);
            drone.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
            drone.bind(this, i * 2.09F);
            server.addFreshEntity(drone);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.4F);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    /** 戦い方: 距離を保ちながら（12 m より近ければ下がる）、盾ドローン → レーダー → 砲撃 → 機銃 */
    static class WatcherGoal extends Goal {
        private final OutpostWatcherEntity boss;
        private int radarCd = 40, artilleryCd = 100, gunCd = 30, droneCd = 0, shots, repath;

        WatcherGoal(OutpostWatcherEntity boss) {
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
            radarCd--;
            artilleryCd--;
            gunCd--;
            droneCd--;
            boss.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = boss.distanceTo(t);
            if (--repath <= 0) {
                repath = 15;
                if (d > 20.0) boss.getNavigation().moveTo(t, 1.0);
                else if (d < 10.0) { // 下がる
                    Vec3 away = boss.position().subtract(t.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() > 1.0e-4) {
                        Vec3 p = boss.position().add(away.normalize().scale(6.0));
                        boss.getNavigation().moveTo(p.x, p.y, p.z, 1.0);
                    }
                } else boss.getNavigation().stop();
            }
            if (shots > 0) { // 連装機銃
                if (boss.tickCount % 2 == 0) {
                    shots--;
                    boolean left = shots % 2 == 0;
                    Vec3 from = boss.local(2.6, 1.2, left ? 1.3 : -1.3);
                    ChronoBolt.fire(boss, from, t, GUN, 0.0F, 2.2F, 3.0F, true);
                    boss.playSound(SoundEvents.CROSSBOW_SHOOT, 0.8F, 1.6F);
                    boss.level().broadcastEntityEvent(boss, EVENT_GUN);
                }
                return;
            }
            if (droneCd <= 0 && boss.shieldDrones() == 0) {
                droneCd = 600;
                boss.deployDrones();
            } else if (radarCd <= 0) {
                radarCd = boss.cd(200);
                boss.radar(t);
            } else if (artilleryCd <= 0) {
                artilleryCd = boss.cd(120);
                boss.artillery(t);
            } else if (gunCd <= 0 && d >= 4.0 && d <= 24.0 && boss.hasLineOfSight(t)) {
                gunCd = boss.cd(60);
                shots = 12;
            }
        }
    }
}
