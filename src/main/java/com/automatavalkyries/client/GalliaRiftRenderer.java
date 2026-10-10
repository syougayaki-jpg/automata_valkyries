package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.GalliaRiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 歪曲破界砲の着弾地点の目印のレンダラー: 景色の歪み（{@link RiftWarp}）にこのフレームの場所と強さを頼み（2 Tick で強まり、残りの間に弱まって消える一瞬の歪み）、
 * 着弾の演出を描く（2026-10-10 ユーザー指定「演出を自作して豪華にして」。光を足し合わせる描き方）: 空へ伸びて細る光の柱、地面に広がる三原色の 3 重の輪、
 * 広がって薄れる白い光の網の半球（緯線と経線）、回りながら外へ飛び散る 8 つの立方体の枠（障壁と同じ形）
 */
public class GalliaRiftRenderer extends EntityRenderer<GalliaRiftEntity> {

    public GalliaRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(GalliaRiftEntity rift, Frustum frustum, double x, double y, double z) {
        return true; // 歪みは画面の外へも広がる（真ん中が画面の外ならRiftWarpが描かない）
    }

    @Override
    public void render(GalliaRiftEntity rift, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = rift.tickCount + partialTick, life = GalliaRiftEntity.LIFE;
        if (t >= life) return;
        float strength = t < 2.0F ? t / 2.0F : 1.0F - (t - 2.0F) / (life - 2.0F);
        RiftWarp.request(rift.position(), rift.radius(), Mth.clamp(strength, 0.0F, 1.0F) * 0.9F, Mth.clamp(t / life, 0.0F, 1.0F));
        float f = 1.0F - t / life;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] ab = GalliaSiegeBeamRenderer.basis(up);
        Vec3 a = ab[0], b = ab[1];
        // 空へ伸びて細る光の柱（白い芯と紫のにじみ）
        float pillar = 1.0F - GalliaSiegeBeamRenderer.smooth(t / 16.0F);
        float pr = 0.3F + 2.4F * pillar, height = 34.0F * Math.min(1.0F, t / 3.0F);
        GalliaSiegeBeamRenderer.tube(vc, m, up, a, b, 0.0F, height, pr * 1.7F, 150, 60, 255, (int) (80 * pillar));
        GalliaSiegeBeamRenderer.tube(vc, m, up, a, b, 0.0F, height, pr, 255, 245, 255, (int) (220 * pillar));
        // 地面に広がる三原色の 3 重の輪
        float gr = 1.0F + 13.0F * GalliaSiegeBeamRenderer.smooth(t / 20.0F);
        for (int k = 0; k < 3; k++) {
            int[] c = GalliaSiegeBeamRenderer.PRIMARY[k];
            float rr = gr * (1.0F - 0.06F * k);
            GalliaSiegeBeamRenderer.ring(vc, m, up.scale(0.15 + 0.02 * k), a, b, rr, rr + 0.35F, c[0], c[1], c[2], (int) (210 * f));
        }
        // 広がって薄れる白い光の網の半球
        float sr = 1.0F + 8.0F * GalliaSiegeBeamRenderer.smooth(t / 18.0F);
        int sal = (int) (170 * f);
        for (int la = 1; la <= 4; la++) {
            float th = Mth.HALF_PI * la / 5.0F;
            float rr = sr * Mth.sin(th);
            GalliaSiegeBeamRenderer.ring(vc, m, up.scale(sr * Mth.cos(th)), a, b, rr, rr + 0.09F, 235, 242, 255, sal);
        }
        for (int lo = 0; lo < 8; lo++) {
            float ph = Mth.TWO_PI * lo / 8.0F + t * 0.03F;
            Vec3 prev = null;
            for (int i = 0; i <= 10; i++) {
                float th = Mth.HALF_PI * i / 10.0F;
                Vec3 p = new Vec3(Mth.sin(th) * Mth.cos(ph) * sr, Mth.cos(th) * sr, Mth.sin(th) * Mth.sin(ph) * sr);
                if (prev != null) GalliaSiegeBeamRenderer.line(vc, m, prev, p, 0.05F, 235, 242, 255, sal);
                prev = p;
            }
        }
        // 回りながら外へ飛び散る 8 つの立方体の枠
        for (int k = 0; k < 8; k++) {
            float ang = Mth.TWO_PI * k / 8.0F;
            Vec3 out = new Vec3(Mth.cos(ang), 0.55 + 0.25 * (k % 2), Mth.sin(ang)).normalize().scale(t * 0.55F);
            Matrix3f rot = new Matrix3f().rotateY(t * 0.25F + k).rotateX(t * 0.18F + k * 0.5F);
            GalliaSiegeBeamRenderer.cubeFrame(vc, m, out.add(0, 0.6, 0), rot, 0.7F, 0.045F, (int) (230 * f), t + k * 4.0F);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GalliaRiftEntity rift) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
