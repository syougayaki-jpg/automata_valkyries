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
