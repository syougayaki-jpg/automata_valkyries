package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.BastionKeeperEntity;
import com.automatavalkyries.entity.chrono.ChronoGuardianEntity;
import com.automatavalkyries.entity.chrono.DelayNodeEntity;
import com.automatavalkyries.entity.chrono.InterceptorEntity;
import com.automatavalkyries.entity.chrono.CraneLoaderEntity;
import com.automatavalkyries.entity.chrono.FoundryMasterEntity;
import com.automatavalkyries.entity.chrono.MimicContainerEntity;
import com.automatavalkyries.entity.chrono.OutpostWatcherEntity;
import com.automatavalkyries.entity.chrono.PhaseCondenserEntity;
import com.automatavalkyries.entity.chrono.PulsePhalanxEntity;
import com.automatavalkyries.entity.chrono.SiegeCannonEntity;
import com.automatavalkyries.entity.chrono.SirenEyeEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * 侵食基地の雑魚・中ボス・コンデンサーのモデル（2026-10-15。geo / テクスチャは tools/gen_chrono_base.py）。アニメーションのファイルは持たず、ここで骨を動かす:
 * タレットの砲塔の向き・連射の反動とレーダーの回転、曲射砲の砲身の仰角と反動、警報ドローンの一つ目と回る警告灯、ガーディアンの 4 本脚の歩き・盾で受ける・ロッドを振る、
 * ミミックの化けている間（脚をしまう）と目覚め（蓋が開いて脚が伸び、噛む）、荷役機の履帯・クレーン・フォーク、司令機の歩き・レーダー・機銃、
 * 工場長機の台座の上の胴の向き・4 本の腕の技・胸の核、コンデンサーの核の脈と回る輪
 */
public class ChronoBaseModel<T extends Mob & GeoEntity> extends DefaultedEntityGeoModel<T> {

    public ChronoBaseModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name), false);
    }

    @Override
    public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
        float pt = state.getPartialTick();
        float age = entity.tickCount + pt;
        float walk = Math.min(state.getLimbSwingAmount(), 1.0F);
        float limb = state.getLimbSwing();
        float pitch = Mth.lerp(pt, entity.xRotO, entity.getXRot()) * Mth.DEG_TO_RAD;
        float yaw = Mth.wrapDegrees(Mth.lerp(pt, entity.yHeadRotO, entity.yHeadRot) - Mth.lerp(pt, entity.yBodyRotO, entity.yBodyRot)) * Mth.DEG_TO_RAD;

        if (entity instanceof PulsePhalanxEntity turret) {
            rotY("turret", yaw);
            rotX("guns", -pitch);
            float fire = age - turret.fireTick;
            CoreGeoBone barrels = bone("barrels");
            if (barrels != null) barrels.setPosZ(fire >= 0 && fire < 3 ? 0.8F * (1.0F - fire / 3.0F) : 0.0F);
            rotY("dish", age * 0.15F);
        } else if (entity instanceof SiegeCannonEntity cannon) {
            rotY("mount", yaw);
            float fire = age - cannon.fireTick;
            float recoil = fire >= 0 && fire < 10 ? 1.0F - fire / 10.0F : 0.0F;
            CoreGeoBone tube = bone("tube");
            if (tube != null) {
                tube.setRotX(Mth.clamp(-pitch, 0.2F, 1.1F)); // いつも上を向く（曲射）
                tube.setPosZ(3.0F * recoil);
            }
        } else if (entity instanceof SirenEyeEntity eye) {
            CoreGeoBone body = bone("body");
            if (body != null) {
                body.setPosY(Mth.sin(age * 0.12F) * 0.8F);
                body.setRotX(-pitch * 0.8F);
            }
            rotY("siren", age * (eye.isSounding() ? 0.6F : 0.05F));
            CoreGeoBone ball = bone("eye");
            if (ball != null) {
                float s = eye.isSounding() ? 1.0F + 0.08F * Mth.sin(age * 1.2F) : 1.0F;
                ball.setScaleX(s);
                ball.setScaleY(s);
            }
            rotZ("fins", Mth.sin(age * 0.3F) * 0.15F);
        } else if (entity instanceof ChronoGuardianEntity guardian) {
            for (int i = 0; i < 4; i++) { // 対角の 2 本ずつ交互に
                float phase = (i == 0 || i == 3) ? 0.0F : Mth.PI;
                rotX("leg_" + i, Mth.cos(limb * 0.7F + phase) * 0.5F * walk);
            }
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Math.abs(Mth.sin(limb * 0.7F)) * 0.6F * walk);
            float block = age - guardian.blockTick;
            CoreGeoBone shield = bone("shield");
            if (shield != null) shield.setPosZ(block >= 0 && block < 6 ? -1.2F * (1.0F - block / 6.0F) : 0.0F); // 盾を押し出して受ける
            float swing = (age - guardian.swingTick) / 8.0F;
            rotX("rod", swing >= 0 && swing < 1 ? 1.6F * Mth.sin(swing * Mth.PI) : 0.25F);
        } else if (entity instanceof MimicContainerEntity mimic) {
            boolean dormant = mimic.isDormant();
            float open = dormant ? 0.0F : Math.min(1.0F, (age - mimic.wakeTick) / 6.0F);
            float chew = dormant ? 0.0F : 0.35F + 0.25F * Mth.sin(age * 0.6F);
            rotX("lid", -open * (0.6F + chew));                              // 蓋が後ろの蝶番で開く
            for (int i = 0; i < 4; i++) {
                CoreGeoBone leg = bone("leg_" + i);
                if (leg == null) continue;
                float out = open;                                             // 化けている間は脚をしまう
                leg.setScaleY(Math.max(0.01F, out));
                leg.setPosY((1.0F - out) * 6.0F);
                float phase = (i == 0 || i == 3) ? 0.0F : Mth.PI;
                leg.setRotX(Mth.cos(limb * 1.1F + phase) * 0.6F * walk * out);
            }
            CoreGeoBone crate = bone("crate");
            if (crate != null) crate.setPosY(open * 6.0F + Math.abs(Mth.sin(limb * 1.1F)) * walk * 1.0F);
        } else if (entity instanceof CraneLoaderEntity loader) {
            float charge = age - loader.chargeTick;
            float thrown = (age - loader.throwTick) / 12.0F;
            float drop = (age - loader.dropTick) / 16.0F;
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Mth.sin(age * 0.5F) * 0.15F * walk);
            rotY("boom", yaw + (thrown >= 0 && thrown < 1 ? -1.2F * Mth.sin(thrown * Mth.PI) : 0.0F)); // クレーンを振って投げる
            rotX("arm", drop >= 0 && drop < 1 ? -0.35F * Mth.sin(drop * Mth.PI) : 0.0F);
            CoreGeoBone forks = bone("forks");
            if (forks != null) forks.setPosY(charge >= 0 && charge < 40 ? -4.0F : 0.0F); // 突進ではフォークを下げる
            CoreGeoBone hook = bone("hook");
            if (hook != null) hook.setRotX(Mth.sin(age * 0.1F) * 0.15F + walk * 0.2F);
        } else if (entity instanceof OutpostWatcherEntity watcher) {
            float swing = Mth.cos(limb * 0.5F) * 0.5F * walk;
            rotX("leg_left", swing);
            rotX("leg_right", -swing);
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Math.abs(Mth.sin(limb * 0.5F)) * 0.8F * walk);
            CoreGeoBone head = bone("head");
            if (head != null) {
                head.setRotY(yaw);
                head.setRotX(-pitch * 0.6F);
            }
            float radar = age - watcher.radarTick;
            rotY("dish", age * (radar >= 0 && radar < 40 ? 0.8F : 0.08F));
            float gun = age - watcher.gunTick;
            float kick = gun >= 0 && gun < 3 ? 0.6F : 0.0F;
            for (String g : new String[]{"gun_left", "gun_right"}) {
                CoreGeoBone b = bone(g);
                if (b == null) continue;
                b.setRotX(-pitch * 0.8F);
                b.setPosZ(kick);
            }
        } else if (entity instanceof FoundryMasterEntity master) {
            rotY("torso", yaw);
            CoreGeoBone core = bone("core");
            if (core != null) {
                float s = master.isShielded() ? 1.0F + 0.1F * Mth.sin(age * 0.3F) : 0.8F;
                core.setScaleX(s);
                core.setScaleY(s);
            }
            float spin = (age - master.spinTick) / 12.0F;
            CoreGeoBone torso = bone("torso");
            if (torso != null && spin >= 0 && spin < 1) torso.setRotY(yaw + spin * Mth.TWO_PI); // 回転アーム
            float arc = (age - master.arcTick) / 10.0F;
            rotX("arm_0", arc >= 0 && arc < 1 ? -0.3F : Mth.sin(age * 0.07F) * 0.1F);
            float press = (age - master.pressTick) / 10.0F;
            rotX("arm_1", press >= 0 && press < 1 ? -1.2F + 1.2F * press : -0.4F + Mth.sin(age * 0.05F) * 0.05F); // 振り上げた槌を下ろす
            float build = (age - master.buildTick) / 30.0F;
            float busy = build >= 0 && build < 1 ? Mth.sin(age * 0.8F) * 0.4F : Mth.sin(age * 0.09F + 1.0F) * 0.1F;
            rotX("arm_2", busy);
            rotX("arm_3", -busy * 0.8F); // ドリルの腕は掴み爪と逆へ
        } else if (entity instanceof BastionKeeperEntity keeper) { // 浮遊の守護機: 体が上下に揺れ、光輪が回り（錨では速く）、4 枚の盾板が周りを回る（弾幕では開く）
            CoreGeoBone body = bone("body");
            if (body != null) body.setPosY(Mth.sin(age * 0.08F) * 1.2F);
            float anchor = age - keeper.anchorTick;
            rotZ("halo", age * (anchor >= 0 && anchor < 32 ? 0.5F : 0.04F));
            float volley = age - keeper.volleyTick;
            float open = volley >= 0 && volley < 30 ? 1.0F : 0.0F;
            for (int i = 0; i < 4; i++) {
                CoreGeoBone plate = bone("plate_" + i);
                if (plate == null) continue;
                plate.setRotY(age * 0.03F + i * Mth.HALF_PI);
                plate.setPosY(Mth.sin(age * 0.1F + i) * 0.8F + open * 2.0F);
            }
            rotY("head", yaw);
            CoreGeoBone eye = bone("eye");
            if (eye != null) {
                float stasis = age - keeper.stasisTick, lance = age - keeper.lanceTick;
                float s = (stasis >= 0 && stasis < 20) || (lance >= 0 && lance < 20) ? 1.35F : 1.0F + 0.05F * Mth.sin(age * 0.2F);
                eye.setScaleX(s);
                eye.setScaleY(s);
            }
        } else if (entity instanceof InterceptorEntity jet) { // 三角翼: 旋回で傾き、急降下で翼をたたむ
            CoreGeoBone body = bone("body");
            float dive = age - jet.diveTick;
            boolean diving = dive >= 0 && dive < InterceptorEntity.DIVE_TICKS;
            if (body != null) {
                body.setRotZ(Mth.sin(age * 0.07F) * 0.35F);
                body.setRotX(diving ? -0.35F : -pitch * 0.5F);
            }
            rotZ("wing_left", diving ? -0.3F : 0.0F);
            rotZ("wing_right", diving ? 0.3F : 0.0F);
            CoreGeoBone engine = bone("engine");
            if (engine != null) {
                float s = diving ? 1.4F : 1.0F + 0.1F * Mth.sin(age * 1.3F);
                engine.setScaleX(s);
                engine.setScaleY(s);
            }
        } else if (entity instanceof DelayNodeEntity) { // 砂時計: 核の脈と、逆へ回る 2 つの輪
            CoreGeoBone core = bone("core");
            if (core != null) {
                core.setPosY(Mth.sin(age * 0.06F) * 0.6F);
                core.setRotY(age * 0.02F);
            }
            rotY("ring_a", age * 0.06F);
            rotY("ring_b", -age * 0.045F);
            CoreGeoBone ringB = bone("ring_b");
            if (ringB != null) ringB.setRotX(0.4F);
        } else if (entity instanceof PhaseCondenserEntity) {
            CoreGeoBone core = bone("core");
            if (core != null) {
                float s = 1.0F + 0.12F * Mth.sin(age * 0.25F);
                core.setScaleX(s);
                core.setScaleY(s);
                core.setScaleZ(s);
            }
            rotY("ring", age * 0.08F);
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

    private void rotZ(String name, float v) {
        CoreGeoBone b = bone(name);
        if (b != null) b.setRotZ(v);
    }

    private CoreGeoBone bone(String name) {
        return getAnimationProcessor().getBone(name);
    }
}
