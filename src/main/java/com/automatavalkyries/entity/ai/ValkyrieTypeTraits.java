package com.automatavalkyries.entity.ai;

import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 素体タイプごとの能力と立ち回り（全体仕様書 §3.2・SPEC_VALKYRIE_GUI_AI §3。2026-10-07 ユーザー指定「能力＋立ち回り」）。数値は【仮】。
 * <ul>
 *   <li>Type-S（近接高機動）: ステップが遠く（×{@value #S_STEP_POWER}）、クールタイムが短い（×{@value #S_STEP_COOLDOWN}）＝瞬動距離特化</li>
 *   <li>Type-H（重装甲防衛）: ノックバック無効。持ち主を狙う近くの敵の注意を自分へ引きつける（前に立つ）。高い所から着地すると地面が揺れる</li>
 *   <li>Type-C（電子戦）: 索敵 {@value #C_RANGE} m。戦闘中は味方を狙う敵を光らせて位置を知らせる（マーキング）。落ちるときは滑空する</li>
 *   <li>零号機（Z）: V に近い万能型。ノックバックを半分受け流し（{@value #Z_KNOCKBACK}）、索敵は C と同じ {@value #C_RANGE} m、ステップは少し遠い（×{@value #Z_STEP_POWER}）</li>
 * </ul>
 */
public final class ValkyrieTypeTraits {
    private ValkyrieTypeTraits() {}

    public static final double S_STEP_POWER = 1.35, S_STEP_COOLDOWN = 0.6;
    public static final double H_STEP_POWER = 0.85, H_STEP_COOLDOWN = 1.25;
    public static final double BASE_RANGE = 32.0, C_RANGE = 40.0;
    public static final double Z_KNOCKBACK = 0.5, Z_STEP_POWER = 1.15;
    /** H が注意を引く範囲（m）と間隔（Tick） */
    public static final double H_TAUNT_RANGE = 10.0;
    public static final int H_TAUNT_INTERVAL = 40;
    /** C のマーキングの間隔と長さ（Tick） */
    public static final int C_MARK_INTERVAL = 20, C_MARK_TICKS = 50;
    /** C の滑空の落ちる速さの上限 */
    public static final double C_GLIDE_FALL = -0.16;

    /** 素体タイプの属性（ノックバック耐性・索敵範囲の基本値）。素体を変えたときと読み込んだときに */
    public static void applyAttributes(ValkyrieEntity v, ValkyrieBody body) {
        var knock = v.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knock != null) knock.setBaseValue(body == ValkyrieBody.H ? 1.0 : body == ValkyrieBody.Z ? Z_KNOCKBACK : 0.0);
        var range = v.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) range.setBaseValue(body == ValkyrieBody.C || body == ValkyrieBody.Z ? C_RANGE : BASE_RANGE);
    }

    public static double stepPowerScale(ValkyrieEntity v) {
        return switch (v.getBody()) {
            case S -> S_STEP_POWER;
            case H -> H_STEP_POWER;
            case Z -> Z_STEP_POWER;
            default -> 1.0;
        };
    }

    public static double stepCooldownScale(ValkyrieEntity v) {
        return switch (v.getBody()) {
            case S -> S_STEP_COOLDOWN;
            case H -> H_STEP_COOLDOWN;
            default -> 1.0;
        };
    }

    /** 毎 Tick（サーバー）。active = 機能停止・休眠でない */
    public static void tick(ValkyrieEntity v, boolean active) {
        if (!(v.level() instanceof ServerLevel level)) return;
        ValkyrieBody body = v.getBody();
        if (body == ValkyrieBody.H) heavyLanding(v, level);
        if (!active) return;
        switch (body) {
            case H -> {
                if (v.tickCount % H_TAUNT_INTERVAL == 0) taunt(v, level);
            }
            case C -> {
                glide(v);
                if (v.tickCount % C_MARK_INTERVAL == 0) mark(v, level);
            }
            default -> { }
        }
    }

    /** H: 持ち主を狙う近くの敵を自分へ向かせる */
    private static void taunt(ValkyrieEntity v, ServerLevel level) {
        Player owner = v.getOwner();
        if (owner == null || v.getMode() == ValkyrieEntity.Mode.WAIT && v.distanceToSqr(owner) > H_TAUNT_RANGE * H_TAUNT_RANGE) return;
        boolean any = false;
        for (Mob m : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(H_TAUNT_RANGE),
                m -> m.isAlive() && m instanceof Enemy && m.getTarget() == owner && !ValkyrieCombat.isAlly(v, m))) {
            m.setTarget(v);
            any = true;
        }
        if (any) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY(1.0), v.getZ(), 12, 0.6, 0.4, 0.6, 0.05);
            v.playSound(ValkyrieEntity.sound("armor_lock"), 0.8F, 0.8F);
        }
    }

    /** H: 高い所からの着地で地面が揺れる（土煙と重い音） */
    private static void heavyLanding(ValkyrieEntity v, ServerLevel level) {
        if (!v.onGround()) {
            v.typeFall = Math.max(v.typeFall, v.fallDistance);
            return;
        }
        float fell = v.typeFall;
        v.typeFall = 0.0F;
        if (fell < 2.5F) return;
        BlockState ground = level.getBlockState(v.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), v.getX(), v.getY() + 0.1, v.getZ(),
                    Math.min(60, 12 + (int) (fell * 4)), 1.2, 0.05, 1.2, 0.15);
        }
        level.sendParticles(ParticleTypes.CLOUD, v.getX(), v.getY() + 0.1, v.getZ(), 10, 0.9, 0.05, 0.9, 0.04);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, Math.min(1.0F, 0.25F + fell * 0.05F), 0.55F);
        v.playSound(ValkyrieEntity.sound("land"), 1.0F, 0.7F);
    }

    /** C: 落ちるときは滑空する（落下の速さを抑え、落下ダメージを受けない） */
    private static void glide(ValkyrieEntity v) {
        if (v.onGround() || v.isPassenger() || v.isInWater() || v.isNoGravity()) return;
        Vec3 d = v.getDeltaMovement();
        if (d.y < C_GLIDE_FALL) {
            v.setDeltaMovement(d.x, C_GLIDE_FALL, d.z);
            v.fallDistance = 0.0F;
        }
    }

    /** C: 戦闘中、自分・持ち主・味方の兵姫を狙う敵（と自分の相手）を光らせる */
    private static void mark(ValkyrieEntity v, ServerLevel level) {
        LivingEntity target = v.getTarget();
        if (target == null || !target.isAlive()) return;
        boolean any = false;
        for (Mob m : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(C_RANGE), Mob::isAlive)) {
            LivingEntity mt = m.getTarget();
            boolean hostile = m == target || (mt != null && (mt == v || ValkyrieCombat.isAlly(v, mt)) && !ValkyrieCombat.isAlly(v, m));
            if (!hostile) continue;
            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, C_MARK_TICKS, 0, false, false), v);
            any = true;
        }
        if (any && v.tickCount % (C_MARK_INTERVAL * 4) == 0) {
            level.sendParticles(ParticleTypes.WITCH, v.getX(), v.getY(1.1), v.getZ(), 8, 0.5, 0.3, 0.5, 0.02);
        }
    }
}
