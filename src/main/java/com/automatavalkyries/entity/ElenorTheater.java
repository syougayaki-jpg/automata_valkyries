package com.automatavalkyries.entity;

import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * エレノアの劇場（2026-10-23 ユーザー指定「劇場を展開し、結んでいないリボンを使って攻撃する」）。
 * <ul>
 *   <li>開幕: 戦いが始まると、エレノアの足もとを中心に半径 {@link #RADIUS} の舞台が現れる（床のレースの輪・周りに吊られた緞帳・上からの光）</li>
 *   <li>幕: フェーズに合わせて第一幕（桃）・第二幕（紅）・第三幕（赤黒）と色が変わる。幕が替わるとき、緞帳が一度降りて上がる</li>
 *   <li>舞台の結界: 戦っている相手（プレイヤー・狙っている相手・攻撃してきた相手）が舞台の外（半径 + 1）へ出ようとすると、緞帳に押し戻される。遠く（半径の 2 倍まで）へ瞬間移動した相手も引き戻す</li>
 *   <li>閉幕: 戦う相手がいなくなって 10 秒たつか、エレノアが倒れると、緞帳が降りて消える</li>
 * </ul>
 * 見た目は粒子だけ（ブロックは置かない）。
 */
public final class ElenorTheater {

    public static final double RADIUS = 22.0D;
    private static final int[] ACT_COLORS = {ElenorFx.PINK, ElenorFx.HOT, ElenorFx.CRIMSON};

    private final ElenorEntity elenor;
    @Nullable
    private Vec3 center;
    private int act = 1;
    private long openedAt;
    private long lastFight;

    ElenorTheater(ElenorEntity elenor) {
        this.elenor = elenor;
    }

    public boolean isOpen() {
        return center != null;
    }

    @Nullable
    public Vec3 center() {
        return center;
    }

    private int color() {
        return ACT_COLORS[Math.max(0, Math.min(2, act - 1))];
    }

    /** 開幕（もう開いていれば何もしない） */
    void open(ServerLevel level, int act) {
        lastFight = level.getGameTime();
        if (center != null) return;
        center = elenor.position();
        this.act = act;
        openedAt = level.getGameTime();
        level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_BLOCK, elenor.getSoundSource(), 2.0F, 0.8F);
        curtainSweep(level, 1.0); // 緞帳が上がる
        ElenorFx.lace(level, center, color(), (float) RADIUS, 40);
        Fx.shockwave(level, center.add(0, 0.1, 0), color(), (float) RADIUS, 30);
    }

    /** 幕が替わる（フェーズが進んだ） */
    void nextAct(ServerLevel level, int act) {
        if (center == null || act == this.act) return;
        this.act = act;
        level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 2.0F, 0.7F + 0.1F * act);
        curtainSweep(level, -1.0); // 一度降りて
        ElenorFx.lace(level, center, color(), (float) RADIUS, 40);
        ElenorFx.petals(level, center.add(0, 6.0, 0), 40, RADIUS * 0.6, color());
    }

    /** 閉幕 */
    void close(ServerLevel level) {
        if (center == null) return;
        curtainSweep(level, -1.0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 1.5F, 0.6F);
        center = null;
    }

    /** 毎 Tick（エンティティから）。fighting = 今、狙っている相手がいるか */
    void tick(ServerLevel level, boolean fighting) {
        if (center == null) return;
        long now = level.getGameTime();
        if (fighting) lastFight = now;
        if (!elenor.isAlive() || now - lastFight > 200L) {
            close(level);
            return;
        }
        long age = now - openedAt;
        // 舞台: 床のレースの輪（40 Tick ごとに張り直す）、縁の光の点、周りに吊られた緞帳、上からの光
        if (age % 40 == 0) ElenorFx.lace(level, center, color(), (float) RADIUS, 44);
        if (age % 10 == 0) ElenorFx.floorRing(level, center, RADIUS, 48, color());
        if (age % 20 == 0) {
            for (int i = 0; i < 20; i++) {
                double a = Math.PI * 2 * i / 20 + age * 0.002;
                Vec3 at = new Vec3(center.x + Math.cos(a) * (RADIUS + 1.0), center.y + 4.0, center.z + Math.sin(a) * (RADIUS + 1.0));
                Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), i % 2 == 0 ? ElenorFx.DARK : color(), 2.6F, 22), at, Vec3.ZERO);
            }
        }
        if (age % 8 == 0) { // 主役（エレノア）を照らす光
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.LIGHT, 0.5F, 10), elenor.position().add(0, 3.0, 0), 3, 0.4, 1.5, 0.4, 0.0);
        }
        guard(level);
    }

    /** 舞台の結界: 外へ出ようとする相手を押し戻す。エレノア自身も舞台へ戻る */
    private void guard(ServerLevel level) {
        Vec3 c = center;
        if (c == null) return;
        double limit = RADIUS + 1.0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(RADIUS * 2.0, 24.0, RADIUS * 2.0),
                x -> x != elenor && ElenorPressure.fightable(x) && isEngaged(x))) {
            Vec3 flat = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double dist = flat.length();
            if (dist <= limit || dist < 1.0e-3) continue;
            Vec3 in = flat.scale(-1.0 / dist);
            if (dist > RADIUS * 1.6) { // 遠くへ逃げた（瞬間移動した）相手は、舞台の縁へ引き戻す
                Vec3 to = c.add(flat.scale((RADIUS - 2.0) / dist));
                e.teleportTo(to.x, e.getY(), to.z);
            } else {
                e.setDeltaMovement(in.x * 0.7, 0.25, in.z * 0.7);
                e.hurtMarked = true;
            }
            Vec3 at = new Vec3(e.getX(), e.getY() + 1.0, e.getZ());
            Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), color(), 1.6F, 14), at.subtract(in.scale(0.8)), Vec3.ZERO);
        }
        Vec3 self = new Vec3(elenor.getX() - c.x, 0, elenor.getZ() - c.z);
        if (self.length() > RADIUS && elenor.hasFreeWill()) elenor.getNavigation().moveTo(c.x, c.y, c.z, 1.2D);
    }

    /**
     * 結界が押し戻す相手: 戦えるプレイヤーと、実際にエレノアと戦っている相手（狙っている・最近攻撃してきた・凶悪技術を使ってきた）だけ。
     * 危険なフラグを持つだけのモブまで舞台へ引き寄せると、無関係のモブを遠くから引きずっていた（2026-10-24）
     */
    private boolean isEngaged(LivingEntity e) {
        if (e instanceof Player) return true;
        if (e == elenor.getTarget() || e == elenor.getLastHurtByMob()) return true;
        return elenor.arts() != null && elenor.arts().pressure().isViciousAttacker(e);
    }

    /** 緞帳が舞台の周りを上がる（direction = 1）・降りる（-1） */
    private void curtainSweep(ServerLevel level, double direction) {
        Vec3 c = center;
        if (c == null) return;
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            Vec3 at = new Vec3(c.x + Math.cos(a) * (RADIUS + 1.0), c.y + (direction > 0 ? 1.0 : 7.0), c.z + Math.sin(a) * (RADIUS + 1.0));
            Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), i % 2 == 0 ? ElenorFx.DARK : color(), 3.0F, 30), at, new Vec3(0, 0.18 * direction, 0));
        }
    }
}
