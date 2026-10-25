package com.automatavalkyries.entity;

import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * エレノアの劇場（2026-10-23 ユーザー指定「劇場を展開し、結んでいないリボンを使って攻撃する」）。
 * <ul>
 *   <li>開幕: 戦いが始まると、エレノアの足もとを中心に半径 {@link #RADIUS} の舞台が現れる（床のレースの輪・周りに吊られた緞帳・上からの光）。
 *       見ている人に題目（名前と幕の名前）が出る</li>
 *   <li>幕: フェーズに合わせて第一幕（桃）・第二幕（紅）・第三幕（赤黒）と色が変わる。幕が替わるとき、緞帳が一度降りて上がり、幕の名前が出る</li>
 *   <li>舞台の結界: 戦っている相手（プレイヤー・狙っている相手・攻撃してきた相手）が舞台の外（半径 + 1）へ出ようとすると、緞帳に押し戻される。遠く（半径の 2 倍まで）へ瞬間移動した相手も引き戻す</li>
 *   <li>閉幕: 戦う相手がいなくなって 10 秒たつと、緞帳が降りて消える。エレノアが倒れたときは、撃破の演出（{@link #finale}）の終わりに緞帳が降りる</li>
 *   <li>最果ての劇場（2026-10-24 ユーザー指定）: 最果てのエレノアの舞台は、黒と白の虚空の劇場。幕は「終幕」だけで、緞帳は破れていて、裂け目から虚空の欠片がこぼれる</li>
 * </ul>
 * 見た目は粒子だけ（ブロックは置かない）。
 */
public final class ElenorTheater {

    public static final double RADIUS = 22.0D;
    private static final int[] ACT_COLORS = {ElenorFx.PINK, ElenorFx.HOT, ElenorFx.CRIMSON};
    /** 最果ての劇場の色（虚空の白・黒） */
    static final int VOID_WHITE = 0xECE6F4, VOID_BLACK = 0x0E0A12;
    /** 題目を見せる距離 */
    private static final double AUDIENCE = 48.0D;

    private final ElenorEntity elenor;
    /** 最果ての劇場か（最果てのエレノア） */
    private final boolean farthest;
    @Nullable
    private Vec3 center;
    private int act = 1;
    private long openedAt;
    private long lastFight;

    ElenorTheater(ElenorEntity elenor) {
        this.elenor = elenor;
        this.farthest = elenor.fullPower();
    }

    public boolean isOpen() {
        return center != null;
    }

    @Nullable
    public Vec3 center() {
        return center;
    }

    /** 最果ての劇場か */
    public boolean isFarthest() {
        return farthest;
    }

    /** 今の幕の色（最果ては虚空の白） */
    int color() {
        if (farthest) return VOID_WHITE;
        return ACT_COLORS[Math.max(0, Math.min(2, act - 1))];
    }

    /** 緞帳の裏地の色（最果ては黒） */
    private int shade() {
        return farthest ? VOID_BLACK : ElenorFx.DARK;
    }

    /** 開幕（もう開いていれば何もしない）。題目（名前と幕の名前）を見ている人に出す */
    void open(ServerLevel level, int act) {
        lastFight = level.getGameTime();
        if (center != null) return;
        center = elenor.position();
        this.act = act;
        openedAt = level.getGameTime();
        if (farthest) {
            level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_BLOCK, elenor.getSoundSource(), 2.0F, 0.5F);
            level.playSound(null, elenor.blockPosition(), SoundEvents.BEACON_DEACTIVATE, elenor.getSoundSource(), 1.5F, 0.6F);
        } else {
            level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_BLOCK, elenor.getSoundSource(), 2.0F, 0.8F);
        }
        curtainSweep(level, 1.0); // 緞帳が上がる
        ElenorFx.lace(level, center, color(), (float) RADIUS, 40);
        Fx.shockwave(level, center.add(0, 0.1, 0), farthest ? VOID_BLACK : color(), (float) RADIUS, 30);
        showTitle(level, true);
    }

    /** 幕が替わる（フェーズが進んだ） */
    void nextAct(ServerLevel level, int act) {
        if (center == null || act == this.act || farthest) return; // 最果ては「終幕」だけ
        this.act = act;
        level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 2.0F, 0.7F + 0.1F * act);
        curtainSweep(level, -1.0); // 一度降りて
        ElenorFx.lace(level, center, color(), (float) RADIUS, 40);
        ElenorFx.petals(level, center.add(0, 6.0, 0), 40, RADIUS * 0.6, color());
        showTitle(level, false);
    }

    /** 閉幕 */
    void close(ServerLevel level) {
        if (center == null) return;
        curtainSweep(level, -1.0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 1.5F, farthest ? 0.4F : 0.6F);
        center = null;
    }

    /**
     * 題目: 開幕では名前と幕の名前、幕が替わるときは幕の名前だけ（名前は小さく残す）。舞台の近くで見ている人に出す
     */
    private void showTitle(ServerLevel level, boolean opening) {
        Component name = elenor.getDisplayName().copy().withStyle(farthest ? ChatFormatting.WHITE : ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        Component actName = Component.translatable(farthest ? "message.automata_valkyries.farthest_elenor.act" : "bossbar.automata_valkyries.elenor.phase" + act)
                .withStyle(farthest ? ChatFormatting.DARK_GRAY : act >= 3 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.connection == null || viewer.distanceToSqr(elenor) > AUDIENCE * AUDIENCE) continue;
            viewer.connection.send(new ClientboundSetTitlesAnimationPacket(opening ? 20 : 10, opening ? 50 : 40, 20));
            viewer.connection.send(new ClientboundSetTitleTextPacket(opening ? name : Component.empty()));
            viewer.connection.send(new ClientboundSetSubtitleTextPacket(actName));
        }
    }

    /** 毎 Tick（エンティティから）。fighting = 今、狙っている相手がいるか、tier = 戦っている相手のうち一番高いティア（舞台の荒れ方） */
    void tick(ServerLevel level, boolean fighting, int tier) {
        if (center == null) return;
        if (elenor.deathTime > 0) return; // 倒れた後は撃破の演出（finale）が幕を引く
        long now = level.getGameTime();
        if (fighting) lastFight = now;
        if (!elenor.isAlive() || now - lastFight > 200L) {
            close(level);
            return;
        }
        long age = now - openedAt;
        if (farthest) {
            tickVoidStage(level, age);
        } else {
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
            if (age % 8 == 0) spotlight(level, ElenorFx.LIGHT, 3);
            tickTierStage(level, age, tier);
        }
        guard(level);
    }

    /**
     * 舞台がティアに合わせて荒れる（2026-10-24 ユーザー指定「ティアごとに攻撃演出も派手にして」）: 3〜 縁の光がティアの色に、
     * 5〜 緞帳から虚空の欠片がこぼれ、6〜 床の縁を赤い紋章が巡り、7 は緞帳が黒と紅になり、舞台の縁に黒い稲妻が落ちる
     */
    private void tickTierStage(ServerLevel level, long age, int tier) {
        Vec3 c = center;
        if (c == null || tier < 3) return;
        if (age % 10 == 5) ElenorFx.floorRing(level, c, RADIUS - 0.6, 40, ElenorFx.tierColor(tier));
        if (tier >= 5 && age % 10 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                Vec3 at = new Vec3(c.x + Math.cos(a) * (RADIUS + 1.0), c.y + 4.5, c.z + Math.sin(a) * (RADIUS + 1.0));
                Fx.one(level, Fx.opt(ModParticles.VOID_SHARD.get(), ElenorFx.shade(tier), 0.32F, 34), at, new Vec3(0, -0.06, 0));
            }
        }
        if (tier >= 6 && age % 6 == 0) {
            double a = age * 0.05;
            for (int i = 0; i < 3; i++) {
                double b = a + Math.PI * 2 * i / 3;
                Fx.one(level, Fx.opt(ModParticles.GLYPH.get(), ElenorFx.CRIMSON, 0.5F, 14), new Vec3(c.x + Math.cos(b) * RADIUS, c.y + 0.4, c.z + Math.sin(b) * RADIUS), Vec3.ZERO);
            }
        }
        if (tier >= 7) {
            if (age % 20 == 10) {
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 * i / 20 + age * 0.002;
                    Vec3 at = new Vec3(c.x + Math.cos(a) * (RADIUS + 1.0), c.y + 4.0, c.z + Math.sin(a) * (RADIUS + 1.0));
                    Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), i % 2 == 0 ? ElenorFx.shade(7) : ElenorFx.CRIMSON, 2.7F, 22), at, Vec3.ZERO);
                }
            }
            if (age % 50 == 0) {
                double a = level.random.nextDouble() * Math.PI * 2;
                Vec3 at = new Vec3(c.x + Math.cos(a) * RADIUS, c.y, c.z + Math.sin(a) * RADIUS);
                Fx.line(level, Fx.opt(ModParticles.STREAK.get(), 0x1A0008, 0.9F, 8), at.add(0, 14.0, 0), at, 0.6);
                Fx.shockwave(level, at.add(0, 0.1, 0), ElenorFx.CRIMSON, 2.5F, 10);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, elenor.getSoundSource(), 0.4F, 1.6F);
            }
        }
    }

    /**
     * 最果ての舞台: 黒と白が交互のレースの床、白い縁、破れた緞帳（ところどころ裂けて短い）、裂け目からこぼれる虚空の欠片、
     * 舞台に漂う白い塵。主役を照らす光は白く細い
     */
    private void tickVoidStage(ServerLevel level, long age) {
        Vec3 c = center;
        if (c == null) return;
        if (age % 40 == 0) ElenorFx.lace(level, c, (age / 40) % 2 == 0 ? VOID_WHITE : VOID_BLACK, (float) RADIUS, 44);
        if (age % 10 == 0) ElenorFx.floorRing(level, c, RADIUS, 48, VOID_WHITE);
        if (age % 20 == 0) {
            for (int i = 0; i < 20; i++) {
                if (torn(i) == 2) continue; // 裂けて垂れていない
                double a = Math.PI * 2 * i / 20;
                double y = torn(i) == 1 ? 5.4 : 4.0; // 裂けて短い緞帳は高いところに残る
                float size = torn(i) == 1 ? 1.6F : 2.6F;
                Vec3 at = new Vec3(c.x + Math.cos(a) * (RADIUS + 1.0), c.y + y, c.z + Math.sin(a) * (RADIUS + 1.0));
                Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), i % 2 == 0 ? VOID_BLACK : VOID_WHITE, size, 22), at, Vec3.ZERO);
                if (torn(i) != 0) { // 裂け目から虚空の欠片がこぼれる
                    Fx.one(level, Fx.opt(ModParticles.VOID_SHARD.get(), VOID_BLACK, 0.3F, 30), at.add(0, -1.2, 0), new Vec3(0, -0.05, 0));
                }
            }
        }
        if (age % 6 == 0) { // 舞台に漂う白い塵
            for (int k = 0; k < 3; k++) {
                double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * RADIUS;
                Vec3 at = new Vec3(c.x + Math.cos(a) * r, c.y + 0.3 + level.random.nextDouble() * 3.0, c.z + Math.sin(a) * r);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), VOID_WHITE, 0.14F, 30), at, new Vec3(0, 0.02, 0));
            }
        }
        if (age % 8 == 0) spotlight(level, VOID_WHITE, 2);
        if (age % 60 == 0) Fx.mandala(level, elenor.position().add(0, 0.05, 0), VOID_BLACK, 2.2F, 40, false, true);
    }

    /** 緞帳 i の破れ方: 0 = 無事、1 = 裂けて短い、2 = 落ちて無い（決まった並び） */
    private static int torn(int i) {
        return switch (i % 7) {
            case 2 -> 2;
            case 4, 5 -> 1;
            default -> 0;
        };
    }

    /** 主役（エレノア）を照らす光 */
    private void spotlight(ServerLevel level, int color, int count) {
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), color, 0.5F, 10), elenor.position().add(0, 3.0, 0), count, 0.4, 1.5, 0.4, 0.0);
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

    /** 緞帳が舞台の周りを上がる（direction = 1）・降りる（-1）。最果ては破れた緞帳 */
    private void curtainSweep(ServerLevel level, double direction) {
        Vec3 c = center;
        if (c == null) return;
        for (int i = 0; i < 28; i++) {
            if (farthest && torn(i) == 2) continue;
            double a = Math.PI * 2 * i / 28;
            Vec3 at = new Vec3(c.x + Math.cos(a) * (RADIUS + 1.0), c.y + (direction > 0 ? 1.0 : 7.0), c.z + Math.sin(a) * (RADIUS + 1.0));
            int color = i % 2 == 0 ? shade() : color();
            Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), color, farthest && torn(i) == 1 ? 1.8F : 3.0F, 30), at, new Vec3(0, 0.18 * direction, 0));
        }
    }

    // ---- 撃破の演出（2026-10-24 ユーザー指定「撃破演出の作り直し」） ----

    /** 撃破の演出の長さ（Tick）。{@link ElenorEntity#DEATH_TICKS} と同じ */
    static final int FINALE_TICKS = ElenorEntity.DEATH_TICKS;

    /**
     * 撃破の演出（サーバー側、倒れてからの Tick ごと）。舞台が開いていなくても、エレノアの周りの演出は出る:
     * <ol>
     *   <li>0〜: 座り込む（模型）。花びらが散り、上からの光が細く絞られる</li>
     *   <li>6: 最後のセリフ</li>
     *   <li>12〜50: 頭のリボンがほどけ、2 本の帯が螺旋を描いて舞い上がる。舞台に花びらが降る</li>
     *   <li>50: ほどけたリボンが四方へ放たれ、花が開く</li>
     *   <li>56〜70: エレノアの周りに小さな緞帳が降り、舞台の緞帳も降りる（閉幕）</li>
     *   <li>{@link #FINALE_TICKS} - 4: 花びらの嵐とレースの輪。ここで消える</li>
     * </ol>
     * 最果ては、黒と白・虚空の欠片・紋章で同じ流れ
     */
    void finale(ServerLevel level, int t) {
        Vec3 at = elenor.position();
        int main = farthest ? VOID_WHITE : ElenorFx.PINK;
        int sub = farthest ? VOID_BLACK : ElenorFx.DARK;
        if (t == 1) {
            ElenorFx.petalBurst(level, at.add(0, 1.0, 0), 18, 0.35, main);
            if (farthest) Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), VOID_BLACK, 0.35F, 24), at.add(0, 1.0, 0), 10, 0.25);
            level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, elenor.getSoundSource(), 1.5F, farthest ? 0.5F : 0.8F);
        }
        if (t == 6) sayFinale(level);
        if (t < 56 && t % 4 == 0) spotlight(level, farthest ? VOID_WHITE : ElenorFx.LIGHT, 2); // 細く絞られた光
        if (t >= 12 && t < 50) { // リボンがほどけて舞い上がる
            double rise = (t - 12) / 38.0;
            for (int strand = 0; strand < 2; strand++) {
                double angle = t * 0.35 + strand * Math.PI;
                double radius = 0.35 + rise * 0.9;
                Vec3 p = new Vec3(at.x + Math.cos(angle) * radius, at.y + 1.3 + rise * 3.2, at.z + Math.sin(angle) * radius);
                Fx.one(level, Fx.opt(ModParticles.RIBBON.get(), strand == 0 ? main : sub, 0.36F, 20), p, new Vec3(0, 0.03, 0));
            }
            if (t % 5 == 0) {
                Vec3 sky = center != null ? center.add(0, 7.0, 0) : at.add(0, 5.0, 0);
                ElenorFx.petals(level, sky, 10, center != null ? RADIUS * 0.5 : 4.0, farthest ? VOID_WHITE : ElenorFx.LIGHT);
            }
            if (farthest && t % 6 == 0) Fx.spray(level, Fx.opt(ModParticles.GLYPH.get(), VOID_WHITE, 0.4F, 30), at.add(0, 1.4, 0), 2, 0.8, 0.02);
        }
        if (t == 50) {
            ElenorFx.ribbonBurst(level, at, 10, 0.4);
            Fx.one(level, Fx.opt(ModParticles.BLOOM.get(), farthest ? VOID_WHITE : ElenorFx.HOT, 2.6F, 18), at.add(0, 1.0, 0), Vec3.ZERO);
            level.playSound(null, elenor.blockPosition(), SoundEvents.BELL_RESONATE, elenor.getSoundSource(), 2.0F, farthest ? 0.5F : 1.0F);
        }
        if (t >= 56 && t <= 70 && t % 2 == 0) { // エレノアの周りに小さな緞帳が降りる
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + t * 0.02;
                Vec3 p = new Vec3(at.x + Math.cos(a) * 1.6, at.y + 3.2 - (t - 56) * 0.18, at.z + Math.sin(a) * 1.6);
                Fx.one(level, Fx.opt(ModParticles.CURTAIN.get(), i % 2 == 0 ? sub : (farthest ? VOID_WHITE : ElenorFx.CRIMSON), 1.4F, 10), p, Vec3.ZERO);
            }
        }
        if (t == 60) close(level); // 舞台の緞帳も降りる（閉幕）
        if (t == FINALE_TICKS - 4) {
            ElenorFx.petalBurst(level, at.add(0, 0.8, 0), 30, 0.5, main);
            ElenorFx.petalBurst(level, at.add(0, 0.8, 0), 14, 0.35, sub);
            ElenorFx.lace(level, at, main, 4.0F, 20);
            if (farthest) Fx.mandala(level, at.add(0, 0.05, 0), VOID_WHITE, 3.0F, 30, false, false);
            level.playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, elenor.getSoundSource(), 1.5F, farthest ? 0.6F : 1.2F);
        }
    }

    /** 撃破の最後のセリフ（近くのプレイヤーへ） */
    private void sayFinale(ServerLevel level) {
        Component message = Component.translatable(farthest ? "message.automata_valkyries.farthest_elenor.finale" : "message.automata_valkyries.elenor.finale")
                .withStyle(farthest ? ChatFormatting.GRAY : ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(elenor) <= AUDIENCE * AUDIENCE) player.displayClientMessage(message, false);
        }
    }
}
