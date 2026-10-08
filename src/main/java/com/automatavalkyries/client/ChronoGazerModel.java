package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.HeavyWalkerEntity;
import com.automatavalkyries.entity.chrono.ScoutDroneEntity;
import com.automatavalkyries.entity.chrono.SpiderBotEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * クロノゲイザーの汎用機のモデル（geo / テクスチャは tools/gen_chrono_gazers.py）。アニメーションのファイルは持たず、ここで骨を動かす:
 * ドローンの回転翼と浮遊、クモボットの 6 本脚の歩き（3 本ずつ交互）と導火線でのコアの脈、戦車の逆関節の歩き・砲の向き・撃った反動・踏み鳴らしの沈み込み
 */
public class ChronoGazerModel<T extends Mob & GeoEntity> extends DefaultedEntityGeoModel<T> {

    /**
     * ルクシオンの背のブースターの展開の角度（ラジアン。GeckoLib の向きなので geo の X・Y の回転とは符号が逆）:
     * 本体は後ろへ 85 度倒れて真後ろを向き（geo の rotX +85。3 つに分かれた噴射口が後ろから正三角形に見える）、主翼は 80 度、尾翼は 40 度外へ開く（左は負、右は正）
     */
    static final float BOOSTER_PITCH = -1.484F, BOOSTER_WING = -1.396F, BOOSTER_FIN = -0.698F;
    /** 3 つに分かれるときに開く角度（ラジアン）: 正四面体（底面の外接円の半径 / 高さ = 1/√2 → 約 35.3 度） */
    static final float BOOSTER_SPLIT = 0.6155F;

    public ChronoGazerModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name), false);
    }

    @Override
    public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float pitch = Mth.lerp(pt, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;

        if (entity instanceof ScoutDroneEntity) {
            rotY("rotor_left", age * 1.4F);
            rotY("rotor_right", -age * 1.4F);
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Mth.sin(age * 0.15F) * 0.6F);
                body.setRotX(-pitch * 0.6F - walk * 0.25F);
                body.setRotZ(Mth.sin(age * 0.07F) * 0.05F);
            }
            CoreGeoBone antenna = bone("antenna");
            if (antenna != null) antenna.setRotZ(Mth.sin(age * 0.3F) * 0.15F);
        } else if (entity instanceof SpiderBotEntity bot) {
            float swell = Mth.clamp(bot.getSwelling(pt), 0.0F, 1.0F);
            for (int i = 0; i < 6; i++) {
                CoreGeoBone leg = bone("leg_" + i);
                if (leg == null) continue;
                boolean right = i < 3;
                // 3 本ずつ交互（右の前・後ろと左の真ん中、その逆）
                float phase = ((i % 3) + (right ? 0 : 1)) % 2 == 0 ? 0.0F : Mth.PI;
                float swing = Mth.cos(limb * 1.2F + phase) * 0.45F * walk;
                float lift = Math.max(0.0F, Mth.sin(limb * 1.2F + phase)) * 0.35F * walk;
                leg.setRotY(swing);
                leg.setRotZ((right ? -1 : 1) * lift + (right ? 1 : -1) * Mth.sin(age * 2.5F + i) * 0.04F * swell);
            }
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Math.abs(Mth.sin(limb * 1.2F)) * 0.3F * walk);
                body.setRotZ(swell > 0 ? Mth.sin(age * 3.0F) * 0.04F * swell : 0.0F);
            }
            CoreGeoBone core = bone("core");
            if (core != null) {
                float pulse = 1.0F + swell * 0.45F + (swell > 0 ? Mth.sin(age * (1.0F + swell * 3.0F)) * 0.12F * swell : Mth.sin(age * 0.1F) * 0.04F);
                core.setScaleX(pulse);
                core.setScaleY(pulse);
                core.setScaleZ(pulse);
            }
        } else if (entity instanceof com.automatavalkyries.entity.chrono.MaskDroneEntity mask) {
            // 仮面: ゆっくり首をかしげて漂う（溜めている間はぴたりと止まって相手を見据え、震える）
            boolean busy = mask.getState() != com.automatavalkyries.entity.chrono.MaskDroneEntity.IDLE;
            CoreGeoBone head = bone("head");
            if (head != null) {
                head.setRotX(-pitch);
                head.setRotZ(busy ? Mth.sin(age * 4.0F) * 0.015F : Mth.sin(age * 0.05F) * 0.35F);
                head.setPosY(busy ? 0.0F : Mth.sin(age * 0.09F) * 0.8F);
            }
            CoreGeoBone ring = bone("ring");
            if (ring != null) ring.setRotZ(age * (busy ? 0.6F : 0.08F));
            CoreGeoBone cables = bone("cables");
            if (cables != null) cables.setRotX(Mth.sin(age * 0.11F) * 0.12F + walk * 0.4F);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.TrooperEntity trooper) {
            // 兵士: 歩き・剣の横薙ぎと突き・腹のガトリング（常に露出。撃つと銃身が回る）
            float legSwing = Mth.cos(limb * 0.6662F) * 1.0F * walk;
            rotX("right_leg", legSwing);
            rotX("left_leg", -legSwing);
            rotX("left_arm", -legSwing * 0.8F);
            float s = (age - trooper.swingTick) / 10.0F;
            float swingArm = 0.0F, swingY = 0.0F;
            if (s >= 0.0F && s < 1.0F) {
                float up = Mth.sin(Math.min(1.0F, s * 2.0F) * Mth.PI);
                if (trooper.swingThrust) swingArm = 1.5F * Mth.sin(s * Mth.PI); // まっすぐ突き出す
                else { // 右上から左へ薙ぐ
                    swingArm = 1.2F + 0.4F * up;
                    swingY = 1.0F - 2.0F * s;
                }
            }
            boolean firing = trooper.isFiring();
            float ready = firing ? 0.0F : 0.5F; // 剣を前へ構える
            CoreGeoBone arm = bone("right_arm");
            if (arm != null) {
                arm.setRotX(legSwing * 0.8F * (1.0F - ready) + ready * 0.6F + swingArm);
                arm.setRotY(swingY);
            }
            CoreGeoBone body = bone("body");
            if (body != null) body.setRotY(swingY * 0.3F);
            float spin = Mth.lerp(pt, trooper.spinO, trooper.spin);
            CoreGeoBone gun = bone("belly_gun");
            if (gun != null) gun.setRotZ(spin);
            CoreGeoBone head = bone("head");
            if (head != null) head.setRotX(-pitch * 0.7F);
        } else if (entity instanceof com.automatavalkyries.entity.chrono.LuxionEntity lux) {
            animateLuxion(lux, pt, age, walk, limb, pitch);
        } else if (entity instanceof HeavyWalkerEntity walker) {
            float swing = Mth.cos(limb * 0.55F) * 0.55F * walk;
            rotX("leg_left", swing);
            rotX("leg_right", -swing);
            float stomp = age - walker.stompTick;
            float sink = stomp >= 0 && stomp < 10 ? Mth.sin(stomp / 10.0F * Mth.PI) : 0.0F;
            CoreGeoBone hull = bone("hull");
            if (hull != null) {
                hull.setPosY(Math.abs(Mth.sin(limb * 0.55F)) * 0.6F * walk - 2.0F * sink);
                hull.setRotZ(Mth.sin(limb * 0.55F) * 0.04F * walk);
            }
            CoreGeoBone cannon = bone("cannon");
            if (cannon != null) {
                float fire = age - walker.fireTick;
                float recoil = fire >= 0 && fire < 8 ? 1.0F - fire / 8.0F : 0.0F;
                cannon.setRotX(-pitch);
                cannon.setPosZ(2.0F * recoil);
            }
        }
    }

    // ---- ルクシオンのモーション（2026-10-08 ユーザー指定「全部作って」） ----

    /** 骨の姿勢（ラジアン・ピクセル）。a = 右腕（剣）、l = 左腕、b = 胴、r = 全体の上下、h = 頭、脚は前へ振るのが正 */
    private static final class Pose {
        float aX, aY, aZ, lX, lY, lZ, bX, bY, bZ, rY, hX, hY, hZ, legR, legL;

        Pose arm(float x, float y, float z) { aX = x; aY = y; aZ = z; return this; }
        Pose larm(float x, float y, float z) { lX = x; lY = y; lZ = z; return this; }
        Pose body(float x, float y, float z) { bX = x; bY = y; bZ = z; return this; }
        Pose root(float y) { rY = y; return this; }
        Pose head(float x, float y, float z) { hX = x; hY = y; hZ = z; return this; }
        Pose legs(float r, float l) { legR = r; legL = l; return this; }

        Pose copy() {
            Pose p = new Pose();
            p.mix(this, 1.0F);
            return p;
        }

        /** this を o へ w だけ寄せる */
        Pose mix(Pose o, float w) {
            if (w <= 0.0F) return this;
            aX = Mth.lerp(w, aX, o.aX); aY = Mth.lerp(w, aY, o.aY); aZ = Mth.lerp(w, aZ, o.aZ);
            lX = Mth.lerp(w, lX, o.lX); lY = Mth.lerp(w, lY, o.lY); lZ = Mth.lerp(w, lZ, o.lZ);
            bX = Mth.lerp(w, bX, o.bX); bY = Mth.lerp(w, bY, o.bY); bZ = Mth.lerp(w, bZ, o.bZ);
            rY = Mth.lerp(w, rY, o.rY);
            hX = Mth.lerp(w, hX, o.hX); hY = Mth.lerp(w, hY, o.hY); hZ = Mth.lerp(w, hZ, o.hZ);
            legR = Mth.lerp(w, legR, o.legR); legL = Mth.lerp(w, legL, o.legL);
            return this;
        }

        static Pose between(Pose a, Pose b, float k) {
            return a.copy().mix(b, k);
        }
    }

    private static Pose pose() {
        return new Pose();
    }

    /** 膝をつく（右脚を立て、左膝を地に。剣は前の地面へ突き立てる） */
    private static final Pose KNEEL = pose().root(-12.0F).legs(1.1F, -1.2F).body(-0.45F, 0.0F, 0.0F).head(-0.3F, 0.0F, 0.0F)
            .arm(0.75F, 0.0F, 0.05F).larm(0.35F, 0.0F, -0.15F);
    /** 撃破: 剣を地に突き立てて両手で柄を握り、膝をついてうなだれる */
    private static final Pose DEFEAT = pose().root(-12.0F).legs(1.1F, -1.2F).body(-0.5F, 0.15F, 0.0F).head(-0.45F, 0.0F, 0.1F)
            .arm(0.8F, -0.15F, 0.0F).larm(0.8F, 0.2F, 0.35F);
    // 斬撃の型（始め → 終わり）。踏み込み（右脚を前へ）と胴のひねりつき
    private static final Pose KESA_A = pose().arm(2.6F, 0.3F, 0.6F).body(0.1F, 0.45F, 0.0F).legs(0.2F, -0.1F).root(-0.5F).larm(0.3F, 0.0F, -0.3F);
    private static final Pose KESA_B = pose().arm(0.5F, -0.9F, -0.5F).body(-0.3F, -0.45F, 0.0F).legs(0.6F, -0.45F).root(-1.8F).larm(0.1F, 0.0F, -0.6F).head(0.2F, 0.3F, 0.0F);
    private static final Pose RISING_A = pose().arm(0.4F, -0.9F, -0.6F).body(-0.2F, -0.45F, 0.0F).legs(0.3F, -0.2F).root(-1.5F).larm(0.2F, 0.0F, -0.4F);
    private static final Pose RISING_B = pose().arm(2.4F, 0.4F, 0.6F).body(0.05F, 0.45F, 0.0F).legs(0.6F, -0.45F).root(-0.8F).larm(0.0F, 0.0F, -0.7F).head(-0.1F, -0.3F, 0.0F);
    private static final Pose FLAT_A = pose().arm(1.5F, 1.2F, 0.2F).body(-0.05F, 0.6F, 0.0F).legs(0.2F, -0.2F).root(-1.5F).larm(0.4F, 0.0F, -0.3F).head(0.0F, -0.5F, 0.0F);
    private static final Pose FLAT_B = pose().arm(1.5F, -1.3F, 0.2F).body(-0.2F, -0.6F, 0.0F).legs(0.65F, -0.5F).root(-2.2F).larm(0.2F, 0.0F, -0.8F).head(0.1F, 0.5F, 0.0F);
    /** 三連閃の締め: 頭上へ振りかぶる → 唐竹に振り下ろす */
    private static final Pose RAISE = pose().arm(3.0F, 0.0F, 0.05F).larm(2.7F, 0.0F, 0.3F).body(0.25F, 0.0F, 0.0F).head(0.25F, 0.0F, 0.0F).legs(0.15F, -0.1F).root(0.5F);
    private static final Pose DOWN = pose().arm(0.35F, 0.0F, 0.0F).larm(0.4F, 0.0F, 0.3F).body(-0.55F, 0.0F, 0.0F).head(0.35F, 0.0F, 0.0F).legs(0.8F, -0.6F).root(-3.5F);
    /** 瞬動: 消える前に身を反らせる → 現れた直後は居合の振り抜き */
    private static final Pose BLINK_LEAN = pose().body(0.4F, 0.1F, 0.0F).arm(-0.3F, 0.2F, 0.4F).larm(-0.2F, 0.0F, -0.3F).head(-0.2F, 0.0F, 0.0F).legs(0.3F, -0.4F).root(-1.0F);
    private static final Pose IAI = pose().arm(1.5F, -1.3F, -0.1F).larm(0.6F, 0.3F, -0.6F).body(-0.3F, -0.55F, 0.0F).head(0.25F, 0.55F, 0.0F).legs(0.9F, -0.7F).root(-3.0F);
    /** 光刃の波: 大きく引く → 全身で横に振り抜き、残心 */
    private static final Pose WAVE_PULL = pose().arm(1.2F, 1.7F, 0.4F).larm(1.0F, 0.0F, -0.9F).body(0.05F, 0.85F, 0.0F).head(0.0F, -0.7F, 0.0F).legs(-0.6F, 0.7F).root(-2.5F);
    private static final Pose WAVE_SWING = pose().arm(1.4F, -1.6F, 0.2F).larm(0.3F, 0.0F, -1.0F).body(-0.25F, -0.9F, 0.0F).head(0.15F, 0.6F, 0.0F).legs(0.8F, -0.6F).root(-3.0F);
    /** 光速突進: 深く沈み、剣を後ろへ流す → 駆け抜けた後に滑って止まる */
    private static final Pose DASH_CROUCH = pose().arm(-0.9F, 0.2F, 0.35F).larm(-0.5F, 0.0F, -0.3F).body(-0.75F, 0.1F, 0.0F).head(0.55F, 0.0F, 0.0F).legs(-0.8F, 0.9F).root(-5.0F);
    private static final Pose SLIDE_STOP = pose().arm(0.4F, 0.3F, 1.0F).larm(0.3F, 0.0F, -0.9F).body(0.3F, -0.2F, 0.0F).head(-0.2F, 0.2F, 0.0F).legs(1.0F, -0.5F).root(-4.0F);
    /** 第 2 形態: 剣を天へ掲げ、左腕を広げて仰ぐ */
    private static final Pose ASCEND = pose().arm(3.05F, 0.0F, 0.1F).larm(0.3F, 0.0F, -1.0F).body(0.18F, 0.0F, 0.0F).head(0.45F, 0.0F, 0.0F).legs(0.15F, -0.15F);
    /** 退く: 剣を左腰の鞘へ納める */
    private static final Pose SHEATHE = pose().arm(0.7F, -1.0F, -0.6F).larm(0.6F, 0.5F, 0.2F).body(-0.05F, -0.25F, 0.0F).head(-0.15F, 0.2F, 0.0F);
    private static final Pose SHEATHED = pose().arm(0.2F, -0.6F, -0.3F).larm(0.3F, 0.4F, 0.2F).head(-0.3F, 0.0F, 0.0F);

    /** 始め a に t の 2 Tick で入り、3 Tick で b へ振り、hold まで留めて len で戻る（斬撃の形） */
    private static void strike(Pose p, Pose a, Pose b, float t, float len, float hold) {
        if (t < 0.0F || t >= len) return;
        Pose target = Pose.between(a, b, smooth((t - 2.0F) / 3.0F));
        float w = t < 2.0F ? smooth(t / 2.0F) : t < hold ? 1.0F : 1.0F - smooth((t - hold) / (len - hold));
        p.mix(target, w);
    }

    /** t の in Tick で入り、hold まで留めて len で戻る */
    private static float envelope(float t, float in, float hold, float len) {
        if (t < 0.0F || t >= len) return 0.0F;
        return t < in ? smooth(t / in) : t < hold ? 1.0F : 1.0F - smooth((t - hold) / (len - hold));
    }

    private void animateLuxion(com.automatavalkyries.entity.chrono.LuxionEntity lux, float pt, float age, float walk, float limb, float pitch) {
        if (lux.isDefeatedSynced()) { // 撃破後は歩く脚の振り・首の向きを止める
            walk = 0.0F;
            pitch = 0.0F;
        }
        if (LuxionRenderer.portrait) { // ボスバーの横顔: いつも同じ姿で止める
            pt = 0.0F;
            age = 0.0F;
            walk = 0.0F;
            pitch = 0.0F;
        }
        boolean hovering = lux.isHovering();
        if (hovering) walk *= 0.2F; // 空中では歩かない
        float legSwing = Mth.cos(limb * 0.7F) * 1.1F * walk;
        float aggro = smooth(Mth.lerp(pt, lux.deployO, lux.deploy));
        float glory = smooth(Mth.lerp(pt, lux.gloryO, lux.glory));
        float breath = Mth.sin(age * 0.1F);
        // 平常待機: 剣を下ろし、時々首をかしげて観察する
        float cyc = (age % 140.0F) / 140.0F;
        float tilt = smooth((cyc - 0.45F) / 0.1F) * (1.0F - smooth((cyc - 0.8F) / 0.1F));
        Pose p = pose().arm(0.15F, 0.0F, 0.1F).larm(0.05F, 0.0F, 0.0F).root(breath * 0.15F).head(-0.08F * tilt, 0.2F * tilt, 0.28F * tilt);
        // 戦闘待機: 低く構えて剣を後ろ斜めへ、半身で呼吸に合わせて上下する
        p.mix(pose().arm(0.55F, -0.45F, 0.35F).larm(0.5F, 0.0F, -0.25F).body(-0.18F, 0.4F, 0.0F).head(0.0F, -0.35F, 0.0F)
                .legs(-0.35F, 0.3F).root(-1.2F + breath * 0.35F), aggro);
        // 歩き
        p.legR = Mth.lerp(walk, p.legR, legSwing);
        p.legL = Mth.lerp(walk, p.legL, -legSwing);
        p.rY *= 1.0F - walk * 0.7F;
        p.bY *= 1.0F - walk * 0.6F;
        p.hY *= 1.0F - walk * 0.6F;
        p.aX += legSwing * 0.3F;
        p.lX -= legSwing * 0.8F;
        p.bX -= 0.15F * walk;
        // 光の翼: 普段はゆっくり羽ばたき（戦闘中はさらにゆっくり大きく）、歩く・構えると後ろへ流れて広がる。第 2 形態はいつも大きく広げて震わせる
        float spread = 0.25F + 0.35F * walk + 0.55F * glory + (lux.isPhase2() ? Mth.sin(age * 0.5F) * 0.05F
                : Mth.sin(age * Mth.lerp(aggro, 0.08F, 0.05F)) * Mth.lerp(aggro, 0.08F, 0.14F));
        float sweep = 0.15F * walk;
        // 空中にとどまる: 脚を垂らして少し後ろへ流し、翼を大きく広げてゆっくり上下する
        if (hovering) {
            p.mix(pose().arm(p.aX, p.aY, p.aZ).larm(p.lX, p.lY, p.lZ).body(-0.1F, p.bY, 0.0F).head(p.hX, p.hY, p.hZ)
                    .legs(-0.15F, -0.4F).root(Mth.sin(age * 0.12F) * 0.8F), 1.0F);
            spread += 0.45F + Mth.sin(age * 0.2F) * 0.08F;
            sweep += 0.2F;
        }
        // 突進の構え: 深く沈み、剣を後ろへ流し、翼を後ろへ
        float w = (age - lux.windupTick) / (lux.isPhase2() ? 12.0F : 20.0F);
        float windup = w >= 0.0F && w < 1.0F ? Mth.sin(Math.min(1.0F, w * 1.5F) * Mth.HALF_PI) : 0.0F;
        p.mix(DASH_CROUCH, windup);
        spread += 0.6F * windup;
        sweep += 0.8F * windup;
        // 技の動き
        float t = age - lux.actionTick;
        switch (lux.action) {
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH -> strike(p, KESA_A, KESA_B, t, 10.0F, 5.0F);
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_RISING -> strike(p, RISING_A, RISING_B, t, 10.0F, 5.0F);
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_FLAT -> strike(p, FLAT_A, FLAT_B, t, 10.0F, 5.0F);
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_SLASH_DOWN -> strike(p, RAISE, DOWN, t, 16.0F, 9.0F);
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_FLURRY_RAISE -> p.mix(RAISE, envelope(t, 4.0F, 10.0F, 14.0F));
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_BLINK_PREP -> p.mix(BLINK_LEAN, envelope(t, 3.0F, 6.0F, 9.0F));
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_BLINK_ARRIVE -> {
                p.mix(IAI, envelope(t, 0.5F, 6.0F, 14.0F));
                spread += 0.3F * envelope(t, 0.5F, 4.0F, 12.0F);
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_WAVE_CHARGE -> {
                float e = envelope(t, 6.0F, 10.0F, 14.0F);
                p.mix(WAVE_PULL, e);
                sweep += 0.3F * e;
                spread += 0.2F * e;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_WAVE_RELEASE -> {
                if (t >= 0.0F && t < 22.0F) {
                    Pose target = Pose.between(WAVE_PULL, WAVE_SWING, smooth(t / 4.0F));
                    p.mix(target, t < 14.0F ? 1.0F : 1.0F - smooth((t - 14.0F) / 8.0F)); // 振り抜いて残心
                    spread += 0.35F * envelope(t, 2.0F, 8.0F, 18.0F);
                }
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.EVENT_DASH_END -> {
                float e = envelope(t, 0.5F, 4.0F, 14.0F);
                p.mix(SLIDE_STOP, e);
                p.bZ += Mth.sin(age * 2.2F) * 0.04F * e; // 滑る揺れ
                sweep += 0.6F * e;
            }
            default -> {
            }
        }
        // 大きな被弾: 上体をのけぞらせる（見た目だけ）
        float hb = age - lux.bigHurtTick;
        if (hb >= 0.0F && hb < 10.0F) {
            float b = hb < 2.0F ? smooth(hb / 2.0F) : 1.0F - smooth((hb - 2.0F) / 8.0F);
            p.bX += 0.45F * b;
            p.hX += 0.35F * b;
            p.aX -= 0.3F * b;
            p.lZ -= 0.4F * b;
            p.rY -= 0.5F * b;
        }
        // 演出の段階
        float fold = 0.0F, close = 0.0F, ascend = 0.0F;
        float st = age - lux.stageStart;
        switch (lux.stage()) {
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_INTRO -> {
                // 光の柱から膝をついて降り（5 Tick）、立ち上がり（18〜28）、剣を払い（24〜40）、翼を開く（26〜40）
                Pose k = KNEEL.copy();
                if (st < 5.0F) k.rY += 48.0F * (1.0F - st / 5.0F) * (1.0F - st / 5.0F);
                p.mix(k, st < 18.0F ? 1.0F : 1.0F - smooth((st - 18.0F) / 10.0F));
                float sw = envelope(st - 24.0F, 2.0F, 10.0F, 16.0F);
                float k2 = smooth((st - 26.0F) / 6.0F);
                p.mix(pose().arm(1.5F, Mth.lerp(k2, 1.2F, -1.3F), 0.2F).body(-0.1F, Mth.lerp(k2, 0.5F, -0.5F), 0.0F)
                        .larm(0.3F, 0.0F, -0.6F).head(0.0F, Mth.lerp(k2, -0.4F, 0.4F), 0.0F).legs(0.4F, -0.3F).root(-1.5F), sw);
                fold = 1.0F - smooth((st - 26.0F) / 6.0F);
                spread += 0.5F * envelope(st - 30.0F, 3.0F, 4.0F, 10.0F);
                close = fold;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_TRANSFORM -> {
                ascend = envelope(st, 6.0F, 26.0F, 32.0F);
                Pose a = ASCEND.copy();
                if (st > 10.0F && st < 20.0F) a.bZ += Mth.sin(age * 3.0F) * 0.03F; // 力がみなぎって震える
                p.mix(a, ascend);
                spread += 0.6F * smooth((st - 8.0F) / 12.0F) * ascend;
            }
            case com.automatavalkyries.entity.chrono.LuxionEntity.STAGE_RETREAT -> {
                // 剣を納め（0〜14）、翼で身を包み（8〜22）、光に溶けて少し浮かぶ
                p.mix(Pose.between(SHEATHE, SHEATHED, smooth((st - 9.0F) / 5.0F)), smooth(st / 5.0F));
                float wrap = smooth((st - 8.0F) / 12.0F);
                spread = Mth.lerp(wrap, spread, 0.5F);
                sweep = Mth.lerp(wrap, sweep, -1.2F);
                p.hX -= 0.3F * wrap;
                p.rY += 4.0F * smooth((st - 18.0F) / 12.0F);
                close = Math.max(close, wrap);
            }
            default -> {
            }
        }
        // 撃破: 剣を突き立てて膝をつき、光になって崩れていく
        if (lux.defeatTicks > 0) {
            float dt = lux.defeatProgress(pt);
            float dw = smooth(dt / 10.0F);
            Pose d = DEFEAT.copy();
            d.bZ += Mth.sin(age * 1.7F) * 0.02F * Math.min(1.0F, dt / 40.0F);
            p.mix(d, dw);
            spread = Mth.lerp(dw, spread, -0.05F);
            sweep = Mth.lerp(dw, sweep, 0.25F);
            close = Math.max(close, dw);
        }
        if (fold > 0.0F) { // 降りてくる間は翼を畳む
            spread = Mth.lerp(fold, spread, -0.15F);
            sweep = Mth.lerp(fold, sweep, -0.2F);
        }

        CoreGeoBone root = bone("root");
        if (root != null) root.setPosY(p.rY);
        rotX("right_leg", p.legR);
        rotX("left_leg", p.legL);
        CoreGeoBone arm = bone("right_arm");
        if (arm != null) {
            arm.setRotX(p.aX);
            arm.setRotY(p.aY);
            arm.setRotZ(p.aZ);
        }
        CoreGeoBone larm = bone("left_arm");
        if (larm != null) {
            larm.setRotX(p.lX);
            larm.setRotY(p.lY);
            larm.setRotZ(p.lZ);
        }
        CoreGeoBone body = bone("body");
        if (body != null) {
            body.setRotX(p.bX);
            body.setRotY(p.bY);
            body.setRotZ(p.bZ);
        }
        CoreGeoBone head = bone("head");
        if (head != null) {
            head.setRotX(p.hX - pitch * 0.6F * (1.0F - ascend));
            head.setRotY(p.hY);
            head.setRotZ(p.hZ);
        }
        CoreGeoBone tail = bone("tail");
        if (tail != null) {
            tail.setRotX(0.3F + walk * 0.6F + Mth.sin(age * 0.15F) * 0.08F + 0.5F * windup + 0.4F * ascend);
            tail.setRotZ(Mth.sin(age * 0.11F) * 0.1F);
        }
        CoreGeoBone thrusters = bone("thrusters");
        if (thrusters != null) thrusters.setRotX(-0.2F * walk - 0.3F * windup + (lux.isPhase2() ? Mth.sin(age * 0.8F) * 0.04F : 0.0F));
        CoreGeoBone wl = bone("wing_left");
        if (wl != null) {
            wl.setRotZ(-spread);
            wl.setRotY(-sweep);
        }
        CoreGeoBone wr = bone("wing_right");
        if (wr != null) {
            wr.setRotZ(spread);
            wr.setRotY(sweep);
        }
        CoreGeoBone cannon = bone("cannon");
        if (cannon != null) cannon.setRotX(-pitch * 0.5F - 0.2F * windup);
        // 第 2 形態: 背の機構がまるごと大きく豪華に（光の翼の骨組みが大きく広がり、ブースターと推進器も一回り大きく）
        if (glory > 0.0F) {
            scale("wing_left", 1.0F + 0.55F * glory);
            scale("wing_right", 1.0F + 0.55F * glory);
            scale("booster", 1.0F + 0.3F * glory);
            scale("thrusters", 1.0F + 0.15F * glory);
            if (tail != null) tail.setScaleY(1.0F + 0.6F * glory);
        }
        // 背の戦闘機型ブースター: 戦闘中は後ろへ倒れて開き、主翼と尾翼が広がる（突進の構え・変身でさらに開く。降臨・退く・撃破では畳む）
        float open = Math.max(Math.min(1.0F, aggro + 0.3F * windup), Math.max(ascend, hovering ? 1.0F : 0.0F)) * (1.0F - close);
        CoreGeoBone booster = bone("booster");
        if (booster != null) booster.setRotX(BOOSTER_PITCH * open);
        CoreGeoBone bwl = bone("booster_wing_left");
        if (bwl != null) bwl.setRotZ(BOOSTER_WING * open);
        CoreGeoBone bwr = bone("booster_wing_right");
        if (bwr != null) bwr.setRotZ(-BOOSTER_WING * open);
        CoreGeoBone bfl = bone("booster_fin_left");
        if (bfl != null) bfl.setRotZ(BOOSTER_FIN * open);
        CoreGeoBone bfr = bone("booster_fin_right");
        if (bfr != null) bfr.setRotZ(-BOOSTER_FIN * open);
        // 3 つに分かれ、機首の先（根本）を 1 点に集めたまま噴射口の側を外へ開く（正四面体: 後ろから見て噴射口が上・左下・右下の正三角形）。開き始めてから少し遅れて分かれる
        float split = smooth((open - 0.35F) / 0.65F) * BOOSTER_SPLIT;
        pod("booster_pod_top", 0.0F, 1.0F, split);
        pod("booster_pod_left", 0.866F, -0.5F, split);
        pod("booster_pod_right", -0.866F, -0.5F, split);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * ブースターの部品を、機首の先（骨のピボット）を中心に angle だけ傾ける。GeckoLib の回転の向きの取り方で、噴射口の側は (dx, dz) と逆へ動き、
     * 3 つの部品が途中で交差して反対側へ開く（後ろから見て上 2・下 1 の三角）。この見た目のままにする（2026-10-08 ユーザー指定「修正しなくていい」）
     */
    private void pod(String name, float dx, float dz, float angle) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setRotX(angle * dz);
        b.setRotZ(angle * dx);
    }

    private void scale(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b == null) return;
        b.setScaleX(v);
        b.setScaleY(v);
        b.setScaleZ(v);
    }

    private void rotX(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotX(v);
    }

    private void rotY(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotY(v);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
