package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.GalliaQuakeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 踏みつけ・重拳の衝撃の描き方（2026-10-10 ユーザー指定「踏みつけの衝撃を自作して豪華にして」。光は足し合わせる描き方）:
 * <ul>
 *   <li>閃光: 着いた所の円い光の板と、上へ短く噴き上がる光</li>
 *   <li>地割れ: 12 本のぎざぎざの割れ目が放射状に走り（4 Tick で伸び切る）、橙赤の芯と紫のにじみで光って、ゆっくり消える</li>
 *   <li>衝撃の壁: 光の壁（内が白・外が紫、上の縁に三原色の細い輪）が当たり判定の輪と同じ速さで最大の半径まで広がり、広がるほど低く薄くなる。
 *       壁の後ろの地面には薄い光の帯</li>
 *   <li>かけら: 着いた所の地面のブロックの小さなかけら 14 個が、回りながら放物線で飛び散って落ちる</li>
 * </ul>
 * 形は目印の UUID から決めるので、見る人ごとに同じ形になる
 */
public class GalliaQuakeRenderer extends EntityRenderer<GalliaQuakeEntity> {

    private static final int CRACKS = 12, DEBRIS = 14;

    public GalliaQuakeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(GalliaQuakeEntity quake, Frustum frustum, double x, double y, double z) {
        return true; // 広がる壁は真ん中が画面の外でも見える
    }

    @Override
    public void render(GalliaQuakeEntity quake, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = quake.tickCount + partialTick, life = quake.life(), maxR = quake.maxRadius();
        if (t >= life) return;
        float fade = 1.0F - GalliaSiegeBeamRenderer.smooth((t - (life - 16.0F)) / 16.0F);
        RandomSource rnd = RandomSource.create(quake.getUUID().getLeastSignificantBits());
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        Vec3 a = ab[0], b = ab[1];
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        // 閃光
        if (t < 8.0F) {
            float f = 1.0F - t / 8.0F;
            float dr = 1.0F + maxR * 0.25F * (1.0F - f);
            GalliaSiegeBeamRenderer.ring(vc, m, up.scale(0.06), a, b, 0.0F, dr, 255, 220, 170, (int) (200 * f));
            GalliaSiegeBeamRenderer.tube(vc, m, up, a, b, 0.0F, 5.0F * (1.0F - f * 0.3F), 0.4F + 1.2F * f, 255, 200, 140, (int) (170 * f));
        }
        // 地割れ
        float crack = Math.min(1.0F, t / 4.0F);
        float crackFade = 1.0F - GalliaSiegeBeamRenderer.smooth(t / life);
        for (int k = 0; k < CRACKS; k++) {
            float ang = Mth.TWO_PI * k / CRACKS + (rnd.nextFloat() - 0.5F) * 0.4F;
            float len = maxR * (0.35F + 0.2F * rnd.nextFloat()) * crack;
            Vec3 prev = up.scale(0.05);
            int segs = 6;
            for (int i = 1; i <= segs; i++) {
                float s = len * i / segs;
                float jag = (rnd.nextFloat() - 0.5F) * 0.5F * (i < segs ? 1 : 0);
                Vec3 p = new Vec3(Mth.cos(ang) * s - Mth.sin(ang) * jag, 0.05, Mth.sin(ang) * s + Mth.cos(ang) * jag);
                float wid = 0.16F * (1.0F - (float) i / (segs + 1));
                flat(vc, m, prev, p, wid * 2.4F, 150, 60, 255, (int) (90 * crackFade));
                flat(vc, m, prev, p, wid, 255, 120, 50, (int) (230 * crackFade));
                prev = p;
            }
        }
        // 衝撃の壁
        float r = Math.min(maxR, 1.0F + GalliaQuakeEntity.SPEED * t);
        float spread = r / maxR;
        float wallFade = (1.0F - 0.7F * spread) * fade;
        float h = 0.25F + 1.5F * (1.0F - spread);
        wall(vc, m, r, h, 0.08F, 255, 245, 235, (int) (200 * wallFade));
        wall(vc, m, r + 0.25F, h * 1.25F, 0.25F, 150, 60, 255, (int) (110 * wallFade));
        for (int k = 0; k < 3; k++) {
            int[] c = GalliaSiegeBeamRenderer.PRIMARY[k];
            GalliaSiegeBeamRenderer.ring(vc, m, up.scale(h + 0.05 + 0.08 * k), a, b, r - 0.05F, r + 0.07F, c[0], c[1], c[2], (int) (220 * wallFade));
        }
        GalliaSiegeBeamRenderer.ring(vc, m, up.scale(0.04), a, b, Math.max(0.0F, r - 1.6F), r, 200, 120, 255, (int) (80 * wallFade));
        // かけら（着いた所の地面のブロック）
        BlockState ground = quake.ground();
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (int k = 0; k < DEBRIS; k++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI;
            float vy = 0.35F + 0.3F * rnd.nextFloat(), vh = 0.12F + 0.22F * rnd.nextFloat() * (maxR / 10.0F);
            float size = 0.22F + 0.18F * rnd.nextFloat();
            float spin = (rnd.nextFloat() - 0.5F) * 0.8F;
            float y = vy * t - 0.035F * t * t;
            if (y < -0.2F) continue;                                       // 落ちきった
            pose.pushPose();
            pose.translate(Mth.cos(ang) * vh * t, y + 0.2F, Mth.sin(ang) * vh * t);
            pose.mulPose(Axis.YP.rotation(spin * t + k));
            pose.mulPose(Axis.XP.rotation(spin * 1.3F * t));
            pose.scale(size, size, size);
            pose.translate(-0.5, -0.5, -0.5);
            dispatcher.renderSingleBlock(ground, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(quake, yaw, partialTick, pose, buffers, light);
    }

    /** 地面に寝かせた光の帯（p0 から p1、幅 w） */
    private static void flat(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, float w, int r, int g, int b, int al) {
        if (al <= 0) return;
        Vec3 d = p1.subtract(p0);
        if (d.lengthSqr() < 1.0e-8) return;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(w * 0.5);
        GalliaSiegeBeamRenderer.quad(vc, m, p0.add(side), p1.add(side), p1.subtract(side), p0.subtract(side), r, g, b, al);
    }

    /** 半径 r・高さ h の円い光の壁（厚み th。上ほど薄く見えるよう 2 段に分ける） */
    private static void wall(VertexConsumer vc, Matrix4f m, float r, float h, float th, int cr, int cg, int cb, int al) {
        if (al <= 0) return;
        int seg = Math.max(24, (int) (r * 6));
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 p0 = new Vec3(Mth.cos(a0) * r, 0.0, Mth.sin(a0) * r), p1 = new Vec3(Mth.cos(a1) * r, 0.0, Mth.sin(a1) * r);
            Vec3 q0 = new Vec3(Mth.cos(a0) * (r + th), h * 0.5, Mth.sin(a0) * (r + th)), q1 = new Vec3(Mth.cos(a1) * (r + th), h * 0.5, Mth.sin(a1) * (r + th));
            Vec3 t0 = new Vec3(Mth.cos(a0) * (r + th * 1.5F), h, Mth.sin(a0) * (r + th * 1.5F)), t1 = new Vec3(Mth.cos(a1) * (r + th * 1.5F), h, Mth.sin(a1) * (r + th * 1.5F));
            GalliaSiegeBeamRenderer.quad(vc, m, p0, p1, q1, q0, cr, cg, cb, al);
            GalliaSiegeBeamRenderer.quad(vc, m, q0, q1, t1, t0, cr, cg, cb, al / 2);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GalliaQuakeEntity quake) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
