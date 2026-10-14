package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoMoonEntity;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 黒死の月の描き方（2026-10-10 ユーザー指定「翼で広い円を覆い、数秒後に光の当たる安全地帯以外を死の鱗粉で納める」）:
 * <ul>
 *   <li>範囲: 地面に淡い紫の光の円（2026-10-14 ユーザー指定「黒いもやを全部幻想的な光にして」で黒い影から）と、縁の太い桃色の光の輪・水色のにじみ。残りの時間に合わせて縁から中へ閉じていく白い数え輪</li>
 *   <li>安全地帯: 影の上に明るい光の円と金の縁、空から差す光の柱（白金）。光の柱は脈打つ</li>
 *   <li>空の月: 範囲の上 20 ブロックに淡い桃色に光る円盤と桃色の縁・水色のにじみ（だんだん大きく近づく）</li>
 *   <li>納める瞬間: 範囲いっぱいの桃色の閃光が広がって消える</li>
 * </ul>
 * 色は第 2 形態の光体に合わせて赤から桃色・水色へ（2026-10-14 ユーザー指定「赤くなるというより出力を上げほぼ幻想的な光体となる演出に」）
 */
public class MementoMoonRenderer extends EntityRenderer<MementoMoonEntity> {

    public MementoMoonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(MementoMoonEntity moon, Frustum frustum, double x, double y, double z) {
        return true; // 広い範囲は真ん中が画面の外でも見える
    }

    @Override
    public void render(MementoMoonEntity moon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = moon.tickCount + partialTick, life = moon.life();
        if (t > life + 10) return;
        float r = moon.radius();
        float open = Math.min(1.0F, t / 15.0F);                                     // 現れる
        float left = Mth.clamp(1.0F - t / life, 0.0F, 1.0F);                        // 残りの時間
        float after = Math.max(0.0F, t - life);                                     // 納めた後
        float fade = after > 0.0F ? 1.0F - after / 10.0F : 1.0F;
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        Vec3 a = ab[0], b = ab[1];
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        // 淡い紫の光の円（範囲）
        VertexConsumer dark = buffers.getBuffer(RenderType.debugQuads());
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 c = new Vec3(0, 0.06, 0);
            MementoFx.quad(dark, m, c, c.add(GalliaSiegeBeamRenderer.around(a, b, a0, r * open)), c.add(GalliaSiegeBeamRenderer.around(a, b, a1, r * open)), c,
                    150, 100, 230, (int) (55 * open * fade));
        }
        // 縁の桃色の光の輪と水色のにじみ、閉じていく白い数え輪
        GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.1), a, b, r * open - 0.5F, r * open, 255, 90, 220, (int) (230 * fade));
        GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.1), a, b, r * open, r * open + 1.2F, 143, 230, 255, (int) (90 * fade));
        if (after <= 0.0F) {
            float cr = r * left;
            GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.12), a, b, Math.max(0.0F, cr - 0.25F), cr, 255, 255, 255, 200);
        }
        // 安全地帯
        float pulse = 0.8F + 0.2F * Mth.sin(t * 0.4F);
        for (Vec3 s : moon.safeZones()) {
            pose.pushPose();
            pose.translate(s.x, s.y, s.z);                                          // 安全地帯の地面へ
            Matrix4f ms = pose.last().pose();
            Vec3 c = new Vec3(0, 0.14, 0);
            float sr = MementoMoriEntity.MOON_SAFE_RADIUS * open;
            GalliaSiegeBeamRenderer.ring(glow, ms, c, a, b, 0.0F, sr, 255, 240, 200, (int) (120 * pulse * fade));
            GalliaSiegeBeamRenderer.ring(glow, ms, c, a, b, sr - 0.2F, sr, 255, 210, 110, (int) (230 * fade));
            GalliaSiegeBeamRenderer.tube(glow, ms, up, a, b, 0.14F, 22.0F, 0.5F * sr * pulse, 255, 245, 220, (int) (70 * pulse * fade));
            GalliaSiegeBeamRenderer.tube(glow, ms, up, a, b, 0.14F, 22.0F, 0.15F * sr, 255, 255, 255, (int) (160 * fade));
            pose.popPose();
        }
        // 空の月（だんだん大きく近づく）
        float mr = 4.0F + 4.0F * (1.0F - left);
        Vec3 sky = up.scale(20.0F - 4.0F * (1.0F - left));
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            MementoFx.quad(dark, m, sky, sky.add(GalliaSiegeBeamRenderer.around(a, b, a0, mr * open)), sky.add(GalliaSiegeBeamRenderer.around(a, b, a1, mr * open)), sky,
                    255, 190, 240, (int) (110 * open * fade));
        }
        GalliaSiegeBeamRenderer.ring(glow, m, sky, a, b, mr * open - 0.4F, mr * open, 255, 90, 220, (int) (240 * fade));
        GalliaSiegeBeamRenderer.ring(glow, m, sky, a, b, mr * open, mr * open + 2.0F, 143, 230, 255, (int) (80 * fade));
        // 納める瞬間の桃色の閃光
        if (after > 0.0F) {
            float f = 1.0F - after / 10.0F;
            GalliaSiegeBeamRenderer.ring(glow, m, up.scale(0.2), a, b, 0.0F, r * (0.6F + 0.4F * (after / 10.0F)), 255, 120, 230, (int) (200 * f));
        }
        super.render(moon, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MementoMoonEntity moon) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
