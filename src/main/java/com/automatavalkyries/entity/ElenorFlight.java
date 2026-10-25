package com.automatavalkyries.entity;

import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 足場を失ったときの飛行（2026-10-24 ユーザー指定「モブジラやフラッシュファーに足元の岩盤を破壊されて奈落に落ちたので、飛行して近くの足場に戻るように」）。
 * <ul>
 *   <li>足場の記録: 地面に立っている間、1 秒ごとに「足もとが固い・頭の上が空いている」所を覚える</li>
 *   <li>飛び始め: 落ちていて（下向きの速さ）、下 8 ブロックに足場が無いまま 8 Tick たつか、12 ブロック以上落ちたか、ワールドの底より下に出たら</li>
 *   <li>行き先: 覚えた足場がまだ残っていればそこ。壊されていれば、そこ（無ければ今の位置）の周り 24 ブロックの地表（高さの地図）から、
 *       一番近い「足もとが固い・頭の上が空いている」所。どこにも無ければ、覚えた足場の高さで浮いて待ち、1 秒ごとに探し直す</li>
 *   <li>飛び方: 重力無しのフラグは使わない（Eprotect が他 MOD の「重力無し」を戻すので）。速さを毎 Tick 決めて行き先へ向かう（最大 0.9 ブロック/Tick）。
 *       背に桃と暗い紫のリボンの翼、足もとに光の粒。飛んでいる間は技を止める（{@link ElenorArts.ArtsGoal}）。着いたら落下の高さを数え直す</li>
 * </ul>
 * 数値は【仮】
 */
public final class ElenorFlight {

    private static final int SEARCH = 24;
    private static final double MAX_SPEED = 0.9D;

    private final ElenorEntity elenor;
    @Nullable
    private Vec3 lastSafe;
    @Nullable
    private Vec3 destination;
    private boolean flying;
    private int airborne;
    private int searchCooldown;
    private int flightTicks;

    ElenorFlight(ElenorEntity elenor) {
        this.elenor = elenor;
    }

    /** 足場へ戻る飛行の最中か（技を止める） */
    public boolean isFlying() {
        return flying;
    }

    @Nullable
    Vec3 lastSafe() {
        return lastSafe;
    }

    /** 毎 Tick（サーバー）。倒れている間は飛ばない */
    void tick(ServerLevel level) {
        // 生死は getter を通さず実際の値で見る（isAlive を偽る MOD で「倒れている」と見て、打ち上げから戻さなかった。2026-10-24）
        if (elenor.deathTime > 0 || com.automatavalkyries.protect.DirectRead.isRemoved(elenor)) {
            flying = false;
            return;
        }
        if (recoverFromLaunch(level)) return;
        if (!com.automatavalkyries.protect.DirectRead.isAlive(elenor)) {
            flying = false;
            return;
        }
        if (!flying && elenor.onGround()) {
            airborne = 0;
            if (elenor.tickCount % 20 == 0 && standable(level, elenor.blockPosition())) lastSafe = Vec3.atBottomCenterOf(elenor.blockPosition());
            return;
        }
        if (!flying) {
            airborne++;
            boolean falling = elenor.getDeltaMovement().y < -0.3D;
            boolean belowWorld = elenor.getY() < level.getMinBuildHeight() + 1;
            if (belowWorld || elenor.fallDistance > 12.0F || falling && airborne > 8 && !groundBelow(level, elenor.blockPosition(), 8)) {
                startFlying(level);
            }
            return;
        }
        fly(level);
    }

    /** 飛ばされたと見なす速さ（ブロック/Tick）。エレノア自身の動き（踏み込み 1.3・飛行 0.9）よりずっと速い */
    private static final double LAUNCH_SPEED = 8.0D;
    /** ワールドの高さの範囲から、これだけ外へ出たら飛ばされたと見なす（ブロック） */
    private static final int OUT_OF_RANGE = 16;

    /**
     * 打ち上げ・突き落としからの復帰（2026-10-24 ユーザー指定「マッドネスエンティティの皆殺しの剣の左クリックでエレノアが消えた」）。
     * 皆殺しの剣（{@code madness:crazy_sword}）は、相手をワールドの上限よりはるか上（y=5761 など）へ飛ばす。座標は数なので Eprotect の
     * 「数でない座標」の守りには掛からず、エレノアは画面の外へ消えたように見え、飛んで戻るにも数分かかった。
     * ワールドの高さの範囲から {@link #OUT_OF_RANGE} ブロック以上外へ出たら、その場で足場へ戻す（飛ばない）。
     * ありえない速さ（{@link #LAUNCH_SPEED} より速い）は止める。戻したら true
     */
    private boolean recoverFromLaunch(ServerLevel level) {
        Vec3 v = elenor.getDeltaMovement();
        if (v.lengthSqr() > LAUNCH_SPEED * LAUNCH_SPEED) {
            elenor.setDeltaMovement(Vec3.ZERO);
            elenor.hurtMarked = true;
        }
        double y = elenor.getY();
        if (y <= level.getMaxBuildHeight() + OUT_OF_RANGE && y >= level.getMinBuildHeight() - OUT_OF_RANGE) return false;
        Vec3 back = findFooting(level);
        if (back == null) back = lastSafe;
        if (back == null) { // 足場が見つからない: その列の地表（無ければワールドの底の少し上）へ
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, elenor.getBlockX(), elenor.getBlockZ());
            back = new Vec3(elenor.getX(), Math.max(top, level.getMinBuildHeight() + 2), elenor.getZ());
        }
        com.automatavalkyries.AutomataValkyries.LOGGER.warn("[elenor] {} was thrown out of the world's height range (y={}); returned to {}",
                elenor, Math.round(y), back);
        elenor.teleportTo(back.x, back.y, back.z);
        elenor.setDeltaMovement(Vec3.ZERO);
        elenor.resetFallDistance();
        elenor.hurtMarked = true;
        flying = false;
        airborne = 0;
        ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 20, 0.35, ElenorFx.PINK);
        ElenorFx.lace(level, elenor.position(), ElenorFx.LIGHT, 2.0F, 16);
        level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, 1.2F);
        return true;
    }

    private void startFlying(ServerLevel level) {
        flying = true;
        flightTicks = 0;
        destination = findFooting(level);
        searchCooldown = 20;
        elenor.getNavigation().stop();
        level.playSound(null, elenor.blockPosition(), SoundEvents.ELYTRA_FLYING, elenor.getSoundSource(), 0.6F, 1.6F);
        ElenorFx.petalBurst(level, elenor.position().add(0, 1.0, 0), 16, 0.3, ElenorFx.PINK);
        ElenorFx.ribbonSwirl(level, elenor.position(), 1.0, 2.2, 0.0);
    }

    private void fly(ServerLevel level) {
        flightTicks++;
        if (destination == null || --searchCooldown <= 0) { // 行き先が無い・壊されたかもしれない: 探し直す
            searchCooldown = 20;
            if (destination == null || !standable(level, BlockPos.containing(destination))) destination = findFooting(level);
        }
        Vec3 target = destination;
        if (target == null) { // どこにも足場が無い: 覚えた足場の高さ（無ければワールドの底の少し上）で浮いて待つ
            double y = lastSafe != null ? lastSafe.y : level.getMinBuildHeight() + 2.0D;
            target = new Vec3(lastSafe != null ? lastSafe.x : elenor.getX(), y, lastSafe != null ? lastSafe.z : elenor.getZ());
        }
        Vec3 to = target.add(0, 0.1, 0).subtract(elenor.position());
        double distance = to.length();
        if (destination != null && distance < 0.6D) { // 着いた
            elenor.setDeltaMovement(0, 0, 0);
            elenor.resetFallDistance();
            flying = false;
            airborne = 0;
            lastSafe = destination;
            ElenorFx.petalBurst(level, elenor.position().add(0, 0.4, 0), 14, 0.3, ElenorFx.PINK);
            ElenorFx.lace(level, elenor.position(), ElenorFx.LIGHT, 1.8F, 14);
            level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.2F, 1.3F);
            return;
        }
        // 行き先へ向かう（近づくほど遅く）。下に足場が無いまま上がるときは、まず上へ
        Vec3 velocity = distance < 1.0e-4 ? Vec3.ZERO : to.scale(Math.min(MAX_SPEED, Math.max(0.15D, distance * 0.3D)) / distance);
        elenor.setDeltaMovement(velocity);
        elenor.hurtMarked = true;
        elenor.resetFallDistance();
        elenor.getNavigation().stop();
        if (flightTicks % 2 == 0) wings(level);
    }

    /** 背のリボンの翼と、足もとの光の粒 */
    private void wings(ServerLevel level) {
        Vec3 back = Vec3.directionFromRotation(0, elenor.yBodyRot).scale(-0.3);
        Vec3 side = new Vec3(-back.z, 0, back.x).normalize();
        Vec3 base = elenor.position().add(back).add(0, 1.4, 0);
        double flap = Math.sin(flightTicks * 0.5) * 0.4;
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 1; i <= 4; i++) {
                Vec3 p = base.add(side.scale(s * 0.35 * i)).add(0, flap * i * 0.25 + 0.1 * i, 0);
                Fx.one(level, Fx.opt(ModParticles.RIBBON.get(), i % 2 == 0 ? ElenorFx.PINK : ElenorFx.DARK, 0.3F, 6), p, Vec3.ZERO);
            }
        }
        Fx.one(level, Fx.opt(ModParticles.GLOW.get(), ElenorFx.LIGHT, 0.25F, 10), elenor.position(), new Vec3(0, -0.05, 0));
    }

    /** 戻る足場: 覚えた足場が残っていればそこ、壊されていれば周りの地表で一番近い立てる所。無ければ null */
    @Nullable
    private Vec3 findFooting(ServerLevel level) {
        if (lastSafe != null && standable(level, BlockPos.containing(lastSafe))) return lastSafe;
        Vec3 from = lastSafe != null ? lastSafe : elenor.position();
        int cx = (int) Math.floor(from.x), cz = (int) Math.floor(from.z);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int r = 0; r <= SEARCH; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // 輪の上だけ（内側は前の輪で見た）
                    int x = cx + dx, z = cz + dz;
                    if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (top <= level.getMinBuildHeight()) continue; // 底まで抜けた柱
                    BlockPos feet = new BlockPos(x, top, z);
                    if (!standable(level, feet)) continue;
                    Vec3 spot = Vec3.atBottomCenterOf(feet);
                    double d = spot.distanceToSqr(elenor.position());
                    if (d < bestDist) {
                        bestDist = d;
                        best = spot;
                    }
                }
            }
            if (best != null) return best; // 近い輪で見つかったら、それより遠い輪は見ない
        }
        return null;
    }

    /** feet に立てるか: 足もとのブロックが固く、足と頭の高さが空いている */
    private boolean standable(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 2) return false;
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        if (!level.getFluidState(below).isEmpty()) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** from の下 depth ブロックに、立てる固いブロックがあるか */
    private static boolean groundBelow(ServerLevel level, BlockPos from, int depth) {
        for (int i = 1; i <= depth; i++) {
            BlockPos p = from.below(i);
            if (p.getY() < level.getMinBuildHeight()) return false;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }
}
