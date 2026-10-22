package com.automatavalkyries.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * エレノアの戦い方（3 フェーズ。2026-10-22）。通常のエレノアだけが使う（最果てのエレノアは継承しても使わない: {@link ElenorEntity#usesBossArts()}）。
 * <ul>
 *   <li>第 1（HP 60% 超）: 近接と、前兆のある踏み込み、近づかれたときの衝撃波</li>
 *   <li>第 2（60% 以下）: 瞬間移動と、紫の光弾の扇撃ちが加わる</li>
 *   <li>第 3（30% 以下）: 光弾の輪と、足元の魔法陣が加わり、間隔が詰まる</li>
 * </ul>
 * どの技にも前兆（動きを止めて光る）があり、避けられる。ダメージは攻撃力属性（既定 12）に比例する（回想のトライアルは攻撃力を絞って呼ぶ）
 */
public final class ElenorArts {

    public static final float PHASE2_RATIO = 0.6F;
    public static final float PHASE3_RATIO = 0.3F;

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.7F, 0.25F, 1.0F), 1.2F);

    private enum Move { NONE, LUNGE, VOLLEY, NOVA, BLINK, RING, SIGIL }

    private static final class Orb {
        Vec3 pos;
        final Vec3 vel;
        int life = 80;

        Orb(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
        }
    }

    private static final class Sigil {
        final Vec3 at;
        int ticks = 40;

        Sigil(Vec3 at) {
            this.at = at;
        }
    }

    private final ElenorEntity elenor;
    private final List<Orb> orbs = new ArrayList<>();
    private final List<Sigil> sigils = new ArrayList<>();

    private Move move = Move.NONE;
    private int moveTick;
    private int cooldown = 40;
    private int meleeCooldown;
    private int phase = 1;
    private boolean introDone;
    private Vec3 lungeDir = Vec3.ZERO;
    private int ringsLeft;

    ElenorArts(ElenorEntity elenor) {
        this.elenor = elenor;
    }

    public int phase() {
        return phase;
    }

    public static int phaseFor(float ratio) {
        return ratio <= PHASE3_RATIO ? 3 : ratio <= PHASE2_RATIO ? 2 : 1;
    }

    private double scale() {
        return elenor.getAttributeValue(Attributes.ATTACK_DAMAGE) / 12.0D;
    }

    private ServerLevel level() {
        return (ServerLevel) elenor.level();
    }

    // ---- 毎 Tick（エンティティから）：光弾と魔法陣は、ゴールが止まっていても進む ----

    public void tickProjectiles() {
        if (!(elenor.level() instanceof ServerLevel level)) return;
        for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
            Orb orb = it.next();
            orb.pos = orb.pos.add(orb.vel);
            if (orb.life % 2 == 0) { // 粒子の送信を半分に（光弾は多いと 30 発を超える）
                level.sendParticles(PURPLE, orb.pos.x, orb.pos.y, orb.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, orb.pos.x, orb.pos.y, orb.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            boolean hit = false;
            for (Player player : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(orb.pos, orb.pos).inflate(0.7))) {
                if (player.isSpectator() || player.isCreative()) continue;
                hurt(player, 6.0F);
                hit = true;
                break;
            }
            BlockPos block = BlockPos.containing(orb.pos);
            if (hit || --orb.life <= 0 || !level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                level.sendParticles(ParticleTypes.WITCH, orb.pos.x, orb.pos.y, orb.pos.z, 8, 0.2, 0.2, 0.2, 0.05);
                it.remove();
            }
        }
        for (Iterator<Sigil> it = sigils.iterator(); it.hasNext(); ) {
            Sigil sigil = it.next();
            if (sigil.ticks % 3 == 0) ring(level, sigil.at, 2.5, 14);
            if (--sigil.ticks > 0) continue;
            level.sendParticles(ParticleTypes.EXPLOSION, sigil.at.x, sigil.at.y + 0.3, sigil.at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.WITCH, sigil.at.x, sigil.at.y + 0.5, sigil.at.z, 30, 1.2, 0.6, 1.2, 0.1);
            level.playSound(null, sigil.at.x, sigil.at.y, sigil.at.z, SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.8F, 1.4F);
            for (Player player : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(sigil.at, sigil.at).inflate(2.5, 2.0, 2.5))) {
                if (!player.isSpectator() && !player.isCreative()) hurt(player, 9.0F);
            }
            it.remove();
        }
    }

    /** 床に光の輪を描く（前兆・魔法陣） */
    private static void ring(ServerLevel level, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(PURPLE, center.x + Math.cos(angle) * radius, center.y + 0.1, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private void hurt(LivingEntity victim, float base) {
        victim.hurt(elenor.damageSources().mobAttack(elenor), (float) (base * scale()));
    }

    // ---- 戦いの流れ ----

    /** ゴールの 1 Tick。標的がいるときだけ呼ばれる */
    public void tick(LivingEntity target) {
        if (!(elenor.level() instanceof ServerLevel level)) return;
        int now = phaseFor(elenor.getEprotect().healthRatio());
        if (!introDone) {
            introDone = true;
            phase = now;
            elenor.setBarPhase(now);
            if (now == 1) say("message.automata_valkyries.elenor.intro");
        } else if (now > phase) {
            phase = now;
            enterPhase(level);
        }
        elenor.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (meleeCooldown > 0) meleeCooldown--;
        if (move != Move.NONE) {
            runMove(level, target);
            return;
        }
        if (cooldown > 0) cooldown--;
        double distance = elenor.distanceTo(target);
        if (cooldown <= 0) {
            choose(distance);
            if (move != Move.NONE) return;
        }
        // 技の合間: 近接
        if (distance > 2.2D) {
            elenor.getNavigation().moveTo(target, 1.2D);
        } else if (meleeCooldown <= 0) {
            elenor.doHurtTarget(target);
            meleeCooldown = 24;
        }
    }

    private void enterPhase(ServerLevel level) {
        say(phase == 2 ? "message.automata_valkyries.elenor.phase2" : "message.automata_valkyries.elenor.phase3");
        elenor.setBarPhase(phase);
        level.sendParticles(ParticleTypes.WITCH, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 60, 0.8, 1.0, 0.8, 0.2);
        level.sendParticles(ParticleTypes.END_ROD, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 30, 0.5, 1.0, 0.5, 0.15);
        level.playSound(null, elenor.blockPosition(), SoundEvents.WITHER_SPAWN, elenor.getSoundSource(), 0.7F, phase == 2 ? 1.4F : 0.9F);
        elenor.triggerAnim("action", "skirt_spread");
        cooldown = 30;
    }

    private void choose(double distance) {
        net.minecraft.util.RandomSource random = elenor.getRandom();
        List<Move> pool = new ArrayList<>();
        if (distance <= 4.0D) {
            pool.add(Move.NOVA);
            pool.add(Move.NOVA);
            if (phase >= 2) pool.add(Move.BLINK);
        } else {
            pool.add(Move.LUNGE);
            if (phase >= 2) {
                pool.add(Move.VOLLEY);
                pool.add(Move.VOLLEY);
                pool.add(Move.BLINK);
            }
            if (phase >= 3) {
                pool.add(Move.RING);
                pool.add(Move.SIGIL);
            }
        }
        move = pool.get(random.nextInt(pool.size()));
        moveTick = 0;
        ringsLeft = move == Move.RING ? 2 : 0;
        elenor.getNavigation().stop();
    }

    private void finish() {
        move = Move.NONE;
        moveTick = 0;
        cooldown = phase == 1 ? 60 : phase == 2 ? 45 : 32;
    }

    private void runMove(ServerLevel level, LivingEntity target) {
        moveTick++;
        Vec3 eye = elenor.position().add(0, 1.0, 0);
        switch (move) {
            case LUNGE -> {
                if (moveTick == 1) {
                    Vec3 flat = target.position().subtract(elenor.position());
                    lungeDir = new Vec3(flat.x, 0, flat.z).normalize();
                    level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, elenor.getSoundSource(), 1.2F, 0.7F);
                }
                if (moveTick <= 18) { // 前兆: 進む先に光の筋
                    for (int i = 1; i <= 6; i++) {
                        Vec3 p = elenor.position().add(lungeDir.scale(i));
                        level.sendParticles(PURPLE, p.x, p.y + 0.2, p.z, 1, 0.1, 0, 0.1, 0);
                    }
                } else if (moveTick <= 24) { // 踏み込み
                    elenor.setDeltaMovement(lungeDir.x * 1.2, elenor.getDeltaMovement().y, lungeDir.z * 1.2);
                    elenor.hurtMarked = true;
                    for (Player player : level.getEntitiesOfClass(Player.class, elenor.getBoundingBox().inflate(0.8))) {
                        if (!player.isSpectator() && !player.isCreative() && player.invulnerableTime <= 10) hurt(player, 14.0F);
                    }
                } else {
                    finish();
                }
            }
            case NOVA -> {
                if (moveTick == 1) level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_ACTIVATE, elenor.getSoundSource(), 1.0F, 1.5F);
                if (moveTick < 20) {
                    ring(level, elenor.position(), 5.0 * moveTick / 20.0, 20);
                    ring(level, elenor.position(), 5.0, 24);
                } else if (moveTick == 20) {
                    elenor.triggerAnim("action", "skirt_spread");
                    level.sendParticles(ParticleTypes.EXPLOSION, elenor.getX(), elenor.getY() + 0.5, elenor.getZ(), 3, 1.5, 0.2, 1.5, 0);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.GENERIC_EXPLODE, elenor.getSoundSource(), 0.9F, 1.2F);
                    for (Player player : level.getEntitiesOfClass(Player.class, elenor.getBoundingBox().inflate(5.0, 2.0, 5.0))) {
                        if (player.isSpectator() || player.isCreative()) continue;
                        hurt(player, 8.0F);
                        Vec3 away = player.position().subtract(elenor.position()).normalize();
                        player.push(away.x * 1.1, 0.5, away.z * 1.1);
                        player.hurtMarked = true;
                    }
                } else if (moveTick > 26) {
                    finish();
                }
            }
            case VOLLEY -> {
                if (moveTick < 16) {
                    level.sendParticles(ParticleTypes.WITCH, eye.x, eye.y + 0.6, eye.z, 4, 0.4, 0.3, 0.4, 0.02);
                } else if (moveTick == 16) {
                    int count = phase >= 3 ? 7 : 5;
                    Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye).normalize();
                    double base = Math.atan2(aim.z, aim.x);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 1.5F);
                    for (int i = 0; i < count; i++) {
                        double angle = base + Math.toRadians((i - (count - 1) / 2.0) * 12.0);
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.7, aim.y * 0.7, Math.sin(angle) * 0.7)));
                    }
                } else if (moveTick > 24) {
                    finish();
                }
            }
            case BLINK -> {
                if (moveTick == 1) {
                    level.sendParticles(ParticleTypes.PORTAL, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
                    blinkBehind(level, target);
                    level.sendParticles(ParticleTypes.PORTAL, elenor.getX(), elenor.getY() + 1.0, elenor.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, elenor.getSoundSource(), 1.0F, 1.2F);
                } else if (moveTick >= 12) { // 背後から一撃
                    if (elenor.distanceTo(target) <= 3.0D) elenor.doHurtTarget(target);
                    meleeCooldown = 20;
                    finish();
                }
            }
            case RING -> {
                if (moveTick < 25 && ringsLeft == 2) {
                    ring(level, elenor.position(), 1.0 + moveTick * 0.12, 20);
                } else if (moveTick == 25 || (moveTick == 45 && ringsLeft == 1)) {
                    double offset = ringsLeft == 1 ? Math.PI / 16.0 : 0.0;
                    level.playSound(null, elenor.blockPosition(), SoundEvents.BLAZE_SHOOT, elenor.getSoundSource(), 1.0F, 0.8F);
                    for (int i = 0; i < 16; i++) {
                        double angle = Math.PI * 2 * i / 16.0 + offset;
                        orbs.add(new Orb(eye, new Vec3(Math.cos(angle) * 0.45, 0.0, Math.sin(angle) * 0.45)));
                    }
                    ringsLeft--;
                } else if (moveTick > 50) {
                    finish();
                }
            }
            case SIGIL -> {
                if (moveTick == 12) {
                    level.playSound(null, elenor.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, elenor.getSoundSource(), 1.2F, 0.8F);
                    sigils.add(new Sigil(target.position()));
                    for (int i = 0; i < 2; i++) {
                        double angle = elenor.getRandom().nextDouble() * Math.PI * 2;
                        sigils.add(new Sigil(target.position().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0)));
                    }
                } else if (moveTick > 24) {
                    finish();
                }
            }
            default -> finish();
        }
    }

    /** 標的の背後（見ている向きの反対）3 ブロックへ。置ける場所が無ければその場で終わる */
    private void blinkBehind(ServerLevel level, LivingEntity target) {
        Vec3 back = target.getLookAngle().multiply(1, 0, 1).normalize().scale(-3.0);
        for (double scale : new double[]{1.0, 0.6, 0.3}) {
            Vec3 to = target.position().add(back.scale(scale));
            if (level.noCollision(elenor, elenor.getBoundingBox().move(to.subtract(elenor.position())))) {
                elenor.teleportTo(to.x, to.y, to.z);
                return;
            }
        }
    }

    private void say(String key) {
        Component message = Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer player : level().players()) {
            if (player.distanceToSqr(elenor) <= 48.0D * 48.0D) player.displayClientMessage(message, false);
        }
    }

    // ---- ゴール ----

    /** 標的がいる間、技と近接を回す。エンティティの構築中（registerGoals）には技の状態がまだ無いので、毎回エンティティから引く */
    public static final class ArtsGoal extends Goal {

        private final ElenorEntity elenor;

        public ArtsGoal(ElenorEntity elenor) {
            this.elenor = elenor;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = elenor.getTarget();
            return elenor.arts() != null && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = elenor.getTarget();
            ElenorArts arts = elenor.arts();
            if (target != null && arts != null) arts.tick(target);
        }

        @Override
        public void stop() {
            ElenorArts arts = elenor.arts();
            if (arts != null) arts.move = Move.NONE;
        }
    }
}
