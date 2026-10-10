package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.GalliaMissileEntity;
import com.automatavalkyries.entity.chrono.GalliaShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * ガリアフォートの弾のレンダラー（2026-10-10 ユーザー指定「副砲とミサイルの弾を自作して搭載」）: 機首を +Y に向けて作ったモデル（tools/gen_gallia_ammo.py）を、
 * 飛ぶ向きへ回して描く。光る所は *_glowmask.png で光らせる。
 * 副砲の徹甲弾は速さの向きへ。ミサイルは撃ち上がる間は真上、頂点で止まっている間に機首を降らせる所へゆっくり向け、降りる間はそこへ向ける。噴射の炎はちらつき、止まっている間は小さい
 */
public class GalliaAmmoRenderer<T extends Entity & GeoEntity> extends GeoEntityRenderer<T> {

    public GalliaAmmoRenderer(EntityRendererProvider.Context context, String name) {
        super(context, new DefaultedEntityGeoModel<T>(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name), false) {
            @Override
            public void setCustomAnimations(T entity, long instanceId, AnimationState<T> state) {
                CoreGeoBone flame = getAnimationProcessor().getBone("flame");
                if (flame == null || !(entity instanceof GalliaMissileEntity missile)) return;
                float age = missile.tickCount + state.getPartialTick();
                boolean holding = !missile.homing() && age >= GalliaMissileEntity.ASCEND && age < GalliaMissileEntity.ASCEND + GalliaMissileEntity.HOLD;
                float s = (holding ? 0.45F : 1.0F) * (0.85F + 0.15F * Mth.sin(age * 2.7F) + 0.08F * Mth.sin(age * 5.3F));
                flame.setScaleX(s);
                flame.setScaleZ(s);
                flame.setScaleY(s * (holding ? 0.8F : 1.3F));
            }
        });
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(T entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 d = direction(entity, partialTick);
        double len = d.length();
        if (len < 1.0e-5) return;
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));          // 機首（+Y）を d へ: 先に前（+Z）へ倒し、Y で向きを回す
        pose.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(d.y / len, -1.0, 1.0))));
    }

    /** 機首を向ける向き */
    private static Vec3 direction(Entity entity, float pt) {
        if (entity instanceof GalliaShellEntity shell) {
            Vec3 v = shell.getDeltaMovement();
            return v.lengthSqr() > 1.0e-6 ? v : new Vec3(0, 1, 0);
        }
        if (entity instanceof GalliaMissileEntity missile && missile.homing()) {     // 追尾するミサイルは進んだ向きへ
            Vec3 v = new Vec3(missile.getX() - missile.xo, missile.getY() - missile.yo, missile.getZ() - missile.zo);
            return v.lengthSqr() > 1.0e-6 ? v : new Vec3(0, 1, 0);
        }
        if (entity instanceof GalliaMissileEntity missile) {
            float age = missile.tickCount + pt;
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 to = missile.aim().subtract(missile.position());
            if (to.lengthSqr() < 1.0e-4) return up;
            to = to.normalize();
            float turn = Mth.clamp((age - GalliaMissileEntity.ASCEND) / (GalliaMissileEntity.HOLD * 0.6F), 0.0F, 1.0F);
            turn = turn * turn * (3.0F - 2.0F * turn);
            Vec3 d = up.lerp(to, turn);
            return d.lengthSqr() < 1.0e-6 ? to : d;                              // 真下を向くときは途中で 0 にならないように
        }
        return new Vec3(0, 1, 0);
    }
}
