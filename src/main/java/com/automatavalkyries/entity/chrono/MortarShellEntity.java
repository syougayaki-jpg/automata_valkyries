package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 山なりに飛ぶ砲弾（2026-10-15 侵食基地の追加。ヘビー・シージキャノンの曲射と、前哨司令機ウォッチャーの砲撃要請）。
 * 遮蔽物を越えて落ちる。撃った時に落ちる所へ赤い印（{@link #mark}）を出すので、見て避けられる。落ちると炸裂（地形は壊さない。{@link ChronoBolt}）
 */
public class MortarShellEntity extends ChronoBolt {

    public static final float GRAVITY = 0.05F;

    public MortarShellEntity(EntityType<? extends MortarShellEntity> type, Level level) {
        super(type, level);
    }

    /** from から to へ flight Tick で落ちる砲弾を撃つ（炸裂の半径 blast・ダメージ damage） */
    public static MortarShellEntity lob(LivingEntity shooter, Vec3 from, Vec3 to, int flight, float damage, float blast) {
        return lob(new MortarShellEntity(ModEntities.MORTAR_SHELL.get(), shooter.level()), shooter, from, to, flight, damage, blast);
    }

    /** 作った弾 shell（投げるコンテナも）を、from から to へ flight Tick で落ちるように撃つ */
    static <S extends MortarShellEntity> S lob(S shell, LivingEntity shooter, Vec3 from, Vec3 to, int flight, float damage, float blast) {
        prepare(shell, shooter, from, new Vec3(0, 1, 0), damage, 0.1F, 0.0F, false);
        shell.setBlast(blast);
        shell.setDeltaMovement(launchVelocity(from, to, flight, shell.getGravity())); // 世界に出す前に決める（手元の見た目が初めからこの道を飛ぶ）
        shooter.level().addFreshEntity(shell);
        mark(shooter, to, blast, flight);
        return shell;
    }

    /**
     * from から to へ、重力 g・空気の抵抗（毎 Tick 0.99 倍）の下で t Tick で届く初めの速さ。
     * 投げた物は毎 Tick「速さの分だけ動き、速さを 0.99 倍して g を引く」ので、i Tick 目の速さは v0·0.99^i − g·S_i（S_i = Σ_{j<i} 0.99^j）。t Tick の動いた分を足し合わせて解く
     */
    public static Vec3 launchVelocity(Vec3 from, Vec3 to, int t, float g) {
        double drag = 0.99, sum = 0, gravSum = 0, pow = 1.0, s = 0.0;
        for (int i = 0; i < t; i++) {
            sum += pow;
            gravSum += g * s;
            s += pow;
            pow *= drag;
        }
        Vec3 d = to.subtract(from);
        return new Vec3(d.x / sum, (d.y + gravSum) / sum, d.z / sum);
    }

    /** 落ちる所に赤い印を出す（life Tick） */
    static void mark(LivingEntity shooter, Vec3 at, float radius, int life) {
        if (shooter.level() instanceof ServerLevel server) {
            Fx.mandala(server, at.add(0, 0.1, 0), 0xFF3040, Math.max(1.5F, radius), life, false, false);
        }
    }

    @Override
    protected float getGravity() {
        return GRAVITY;
    }
}
