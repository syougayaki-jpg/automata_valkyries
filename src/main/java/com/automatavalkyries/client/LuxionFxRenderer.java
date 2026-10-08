package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.LuxionFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * ルクシオンの技の自作エフェクト（{@link LuxionFxEntity}）: 光刃の波は三日月の光の刃が飛んで跡に光の帯、光速突進は道に光の帯と螺旋の光。
 * 足し算の光（白い芯・白金・金、第 2 形態は赤み）で、時間とともに細く薄れる
 */
public class LuxionFxRenderer extends EntityRenderer<LuxionFxEntity> {

    public LuxionFxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(LuxionFxEntity fx, Frustum frustum, double x, double y, double z) {
        return fx.distanceToSqr(x, y, z) < 128 * 128;
    }

    @Override
    public void render(LuxionFxEntity fx, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = fx.tickCount + partialTick;
        float life = age / fx.life();
        if (life >= 1.0F) return;
        Vec3 origin = fx.getPosition(partialTick);
        Vec3 end = fx.end().subtract(origin);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(origin);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        boolean red = fx.red();
        if (fx.kind() == LuxionFxEntity.WAVE) wave(vc, m, end, cam, age, life, red);
        else if (fx.kind() == LuxionFxEntity.RAIL) rail(vc, m, end, cam, age, life);
        else dash(vc, m, end, cam, age, life, red);
    }

    /** 光刃の波: 6 Tick で始まりから終わりへ飛ぶ三日月の刃と、その跡の光の帯 */
    private static void wave(VertexConsumer vc, Matrix4f m, Vec3 end, Vec3 cam, float age, float life, boolean red) {
        float fly = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        Vec3 dir = end.normalize();
        Vec3 head = end.scale(fly);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up);
        side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
        float fade = 1.0F - life;
        // 跡の光の帯（飛んだ所まで、だんだん薄れる）
        ribbon(vc, m, Vec3.ZERO, head, cam, 0.9F * fade, 255, 236, 180, (int) (90 * fade));
        ribbon(vc, m, Vec3.ZERO, head, cam, 0.25F * fade, 255, 255, 255, (int) (200 * fade));
        if (fly >= 1.0F && life > 0.6F) return;
        // 三日月の刃: 進む向きに凸の弧を、横へ 2.6 ブロックの幅で
        int segs = 14;
        float half = 1.3F, bulge = 0.55F;
        for (int k = 0; k < 2; k++) {
            float w = k == 0 ? 0.42F : 0.16F;
            int r = 255, g = k == 0 ? (red ? 150 : 236) : 255, b = k == 0 ? (red ? 120 : 180) : 255, a = (int) ((k == 0 ? 170 : 240) * fade);
            for (int i = 0; i < segs; i++) {
                float u0 = i / (float) segs * 2 - 1, u1 = (i + 1) / (float) segs * 2 - 1;
                Vec3 p0 = head.add(side.scale(u0 * half)).add(dir.scale(bulge * (1 - u0 * u0)));
                Vec3 p1 = head.add(side.scale(u1 * half)).add(dir.scale(bulge * (1 - u1 * u1)));
                float t0 = 1 - Math.abs(u0), t1 = 1 - Math.abs(u1); // 先ほど細く
                Vec3 back0 = dir.scale(-w * t0), back1 = dir.scale(-w * t1);
                quad(vc, m, p0, p1, p1.add(back1), p0.add(back0), r, g, b, a);
            }
        }
    }

    /** 光速突進: 道に光の帯（白い芯・白金のにじみ）と、巻き付いて回る 2 本の螺旋の光。細く薄れて消える */
    private static void dash(VertexConsumer vc, Matrix4f m, Vec3 end, Vec3 cam, float age, float life, boolean red) {
        float fade = (1.0F - life);
        float k = fade * fade;
        ribbon(vc, m, Vec3.ZERO, end, cam, 1.6F * k, 255, red ? 150 : 236, red ? 120 : 180, (int) (110 * k));
        ribbon(vc, m, Vec3.ZERO, end, cam, 0.5F * k, 255, 250, 235, (int) (220 * k));
        ribbon(vc, m, Vec3.ZERO, end, cam, 0.12F, 255, 255, 255, (int) (255 * fade));
        double len = end.length();
        if (len < 0.1) return;
        Vec3 dir = end.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
        float radius = 0.55F + 0.4F * life;
        for (int h = 0; h < 2; h++) {
            Vec3 prev = null;
            int steps = (int) (len * 4);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double ang = t * len * 2.2 + h * Math.PI + age * 0.4;
                Vec3 p = dir.scale(t * len).add(u.scale(Math.cos(ang) * radius)).add(v.scale(Math.sin(ang) * radius));
                if (prev != null) ribbon(vc, m, prev, p, cam, 0.1F * fade, 255, h == 0 ? 236 : 120, h == 0 ? 180 : 110, (int) (200 * fade));
                prev = p;
            }
        }
    }

    /**
     * 加速の光のレール（ルクシオンアクセラレータ）: 始まりから終わりへ 8 Tick で、左右 2 本の光のレールが伸び、2 ブロックおきに加速の光の輪（レールに垂直、
     * 回りながら脈打つ。赤と金を交互に）が次々に開く。真ん中に予測の赤い線。最後に細く薄れる
     */
    private static void rail(VertexConsumer vc, Matrix4f m, Vec3 end, Vec3 cam, float age, float life) {
        double len = end.length();
        if (len < 0.1) return;
        Vec3 dir = end.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
        float grow = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        float fade = life < 0.75F ? 1.0F : 1.0F - (life - 0.75F) / 0.25F;
        Vec3 head = end.scale(grow);
        // 2 本のレール（白金の芯と金のにじみ）と真ん中の予測の赤い線
        for (int side = -1; side <= 1; side += 2) {
            Vec3 off = u.scale(side * 0.9);
            ribbon(vc, m, off, head.add(off), cam, 0.35F * fade, 255, 220, 140, (int) (120 * fade));
            ribbon(vc, m, off, head.add(off), cam, 0.1F, 255, 250, 235, (int) (230 * fade));
        }
        ribbon(vc, m, Vec3.ZERO, head, cam, 0.08F, 255, 60, 80, (int) (220 * fade));
        // 加速の光の輪
        int rings = (int) (len / 2.0);
        for (int i = 1; i <= rings; i++) {
            float at = i / (float) (rings + 1);
            float open = Mth.clamp((grow - at) * 6.0F, 0.0F, 1.0F); // 先頭が通ってから開く
            if (open <= 0.0F) continue;
            Vec3 c = end.scale(at);
            float pulse = 1.0F + 0.12F * Mth.sin(age * 0.8F - i * 0.9F);
            float r = (1.0F + 0.5F * (1.0F - at)) * open * pulse;
            boolean red = i % 2 == 0;
            int g = red ? 70 : 220, b = red ? 90 : 140;
            int segs = 24;
            float spin = age * 0.15F * (red ? -1 : 1);
            for (int k = 0; k < segs; k++) {
                if (k % 6 == 5) continue; // 切れ目のある輪
                double a0 = spin + Math.PI * 2 * k / segs, a1 = spin + Math.PI * 2 * (k + 1) / segs;
                Vec3 p0 = c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r));
                Vec3 p1 = c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r));
                ribbon(vc, m, p0, p1, cam, 0.14F * fade, 255, g, b, (int) (210 * fade * open));
            }
        }
    }

    /** a から b への、カメラを向いた帯 */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float width, int r, int g, int bl, int alpha) {
        if (alpha <= 0 || width <= 0) return;
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1.0e-8) return;
        side = side.normalize().scale(width / 2.0);
        quad(vc, m, a.add(side), a.subtract(side), b.subtract(side), b.add(side), r, g, bl, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int alpha) {
        int al = Math.max(0, Math.min(255, alpha));
        for (Vec3[] q : new Vec3[][]{{a, b, c, d}, {d, c, b, a}}) {
            for (Vec3 p : q) vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, al).endVertex();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LuxionFxEntity fx) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
