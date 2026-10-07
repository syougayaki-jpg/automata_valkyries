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
            // ルクシオン: 細い脚の速い歩き、剣を低く構え、斬ると右から左へ薙ぐ。突進の構えでは身を低く沈めて剣を後ろへ引く。光の尾がなびく
            float legSwing = Mth.cos(limb * 0.7F) * 1.1F * walk;
            rotX("right_leg", legSwing);
            rotX("left_leg", -legSwing);
            float s = (age - lux.slashTick) / 8.0F;
            float swingArm = 0.0F, swingY = 0.0F;
            if (s >= 0.0F && s < 1.0F) {
                swingArm = 1.3F + 0.3F * Mth.sin(s * Mth.PI);
                swingY = 1.1F - 2.2F * s;
            }
            float w = (age - lux.windupTick) / (lux.isPhase2() ? 12.0F : 20.0F);
            float windup = w >= 0.0F && w < 1.0F ? Mth.sin(Math.min(1.0F, w * 1.5F) * Mth.HALF_PI) : 0.0F;
            CoreGeoBone arm = bone("right_arm");
            if (arm != null) {
                arm.setRotX(0.5F + legSwing * 0.3F + swingArm - 1.3F * windup);
                arm.setRotY(swingY);
            }
            rotX("left_arm", -legSwing * 0.8F + 0.3F * windup);
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setRotX(-0.15F * walk - 0.4F * windup);
                body.setRotY(swingY * 0.35F);
                body.setPosY(-2.0F * windup);
            }
            CoreGeoBone head = bone("head");
            if (head != null) head.setRotX(-pitch * 0.6F);
            CoreGeoBone tail = bone("tail");
            if (tail != null) {
                tail.setRotX(0.3F + walk * 0.6F + Mth.sin(age * 0.15F) * 0.08F + 0.5F * windup);
                tail.setRotZ(Mth.sin(age * 0.11F) * 0.1F);
            }
            CoreGeoBone thrusters = bone("thrusters");
            if (thrusters != null) thrusters.setRotX(-0.2F * walk - 0.3F * windup + (lux.isPhase2() ? Mth.sin(age * 0.8F) * 0.04F : 0.0F));
            // 光の翼: 普段はゆっくり羽ばたき、歩く・構えると後ろへ流れて大きく広がる。第 2 形態はいつも大きく広げて震わせる
            float spread = 0.25F + 0.35F * walk + 0.6F * windup + 0.55F * smooth(Mth.lerp(pt, lux.gloryO, lux.glory))
                    + Mth.sin(age * (lux.isPhase2() ? 0.5F : 0.08F)) * (lux.isPhase2() ? 0.05F : 0.08F);
            float sweep = 0.15F * walk + 0.35F * windup;
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
            float glory = smooth(Mth.lerp(pt, lux.gloryO, lux.glory));
            if (glory > 0.0F) {
                scale("wing_left", 1.0F + 0.55F * glory);
                scale("wing_right", 1.0F + 0.55F * glory);
                scale("booster", 1.0F + 0.3F * glory);
                scale("thrusters", 1.0F + 0.15F * glory);
                CoreGeoBone tailBone = bone("tail");
                if (tailBone != null) tailBone.setScaleY(1.0F + 0.6F * glory);
            }
            // 背の戦闘機型ブースター: 戦闘中は後ろへ倒れて開き、主翼と尾翼が広がる（突進の構えでさらに開く）
            float dep = smooth(Mth.lerp(pt, lux.deployO, lux.deploy));
            float open = Math.min(1.0F, dep + 0.3F * windup);
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
