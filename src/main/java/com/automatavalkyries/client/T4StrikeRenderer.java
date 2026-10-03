package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.T4StrikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * T4 兵装の通常攻撃の見た目（自作の重ね描き。2026-10-04 ユーザー指定）。
 * <ul>
 *   <li>横薙ぎ（ラグナロク）: 持ち主を中心に、赤黒い帯の弧（内は黒い煙、外の縁は紅く熱い）が一気に一周する。高さを変えて 3 段重ね、紅の縁の光を足す</li>
 *   <li>落雷（レールガン）: 空から的へ、ぎざぎざに折れる青白い稲妻と枝分かれ。2 Tick ごとに形が変わってちらつき、明滅しながら消える</li>
 *   <li>放電（レールガン）: 的から近くの敵へ跳ぶ細い稲妻。毎 Tick 形が変わる</li>
 *   <li>光線（ジェネシス・ヘイロー）: 金の外の光・白い芯・流れるきらめきの帯を重ねた光線。寿命をかけて細く薄れる</li>
 * </ul>
 * 稲妻と光線の帯は、いつもこちらを向く板で描く（どこから見ても太く見える）。
 */
public class T4StrikeRenderer extends EntityRenderer<T4StrikeEntity> {

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    private static final ResourceLocation SWEEP = tex("twilight_sweep"), SWEEP_GLOW = tex("twilight_sweep_glow"), BOLT = tex("thunder_bolt"),
            BEAM = tex("punishment_beam"), SPARKLE = tex("laser_sparkle");

    public T4StrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(T4StrikeEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTick;
        float t = Math.min(1F, age / entity.lifetime());
        Vec3 here = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()), Mth.lerp(partialTick, entity.yo, entity.getY()), Mth.lerp(partialTick, entity.zo, entity.getZ()));
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(here); // 置いた位置から見たカメラ
        switch (entity.kind()) {
            case T4StrikeEntity.SWEEP -> sweep(entity, pose, buffers, t);
            case T4StrikeEntity.THUNDER -> bolt(entity, pose, buffers, cam, age, t, true);
            case T4StrikeEntity.ARC -> bolt(entity, pose, buffers, cam, age, t, false);
            case T4StrikeEntity.LASER -> laser(entity, pose, buffers, cam, age, t);
            default -> {}
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    // ---- 横薙ぎ ----

    private static void sweep(T4StrikeEntity e, PoseStack pose, MultiBufferSource buffers, float t) {
        float radius = e.width();
        float prog = 1F - (float) Math.pow(1F - Math.min(1F, t / 0.55F), 3); // 一気に回って止まる
        float fade = t < 0.55F ? 1F : Math.max(0F, 1F - (t - 0.55F) / 0.45F);
        float head = e.yaw() - 150F + 380F * prog;
        float tail = head - 230F * (0.4F + 0.6F * prog);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentEmissive(SWEEP));
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(SWEEP_GLOW));
        float[][] layers = {{-0.45F, 0.92F, 0.6F}, {0F, 1F, 1F}, {0.45F, 0.95F, 0.6F}}; // 高さ・半径の倍率・濃さ
        for (float[] l : layers) {
            arc(body, m, n, l[0], radius * l[1] * 0.3F, radius * l[1], tail, head, 255, 255, 255, Math.round(255 * fade * l[2]));
            int g = Math.round(255 * fade * l[2]);
            arc(glow, m, n, l[0] + 0.01F, radius * l[1] * 0.3F, radius * l[1] * 1.04F, tail, head, g, g, g, 255);
        }
    }

    /** 水平な弧の帯（高さ y、内の半径 r0・外の半径 r1、角度 a0（尾）〜 a1（先頭）。MC の向きの角度（度）） */
    private static void arc(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float r0, float r1, float a0, float a1, int r, int g, int b, int alpha) {
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs, u1 = (i + 1) / (float) segs;
            float t0 = (a0 + (a1 - a0) * u0) * Mth.DEG_TO_RAD, t1 = (a0 + (a1 - a0) * u1) * Mth.DEG_TO_RAD;
            float x0 = -Mth.sin(t0), z0 = Mth.cos(t0), x1 = -Mth.sin(t1), z1 = Mth.cos(t1);
            float[][] q = {{x0 * r1, z0 * r1, u0, 0}, {x1 * r1, z1 * r1, u1, 0}, {x1 * r0, z1 * r0, u1, 1}, {x0 * r0, z0 * r0, u0, 1}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] p = q[pass == 0 ? k : 3 - k];
                    vc.vertex(m, p[0], y, p[1]).color(r, g, b, alpha).uv(p[2], p[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(0xF000F0).normal(n, 0, pass == 0 ? 1 : -1, 0).endVertex();
                }
            }
        }
    }

    // ---- 落雷・放電 ----

    private static void bolt(T4StrikeEntity e, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float age, float t, boolean thunder) {
        Vec3 span = e.span();
        double length = span.length();
        if (length < 0.1) return;
        int frame = thunder ? (int) (age / 2) : (int) age;
        Random r = new Random(e.seed() * 31L + frame);
        // 明るさ: 出た瞬間がいちばん明るく、明滅しながら消える
        float flash = t < 0.15F ? 1F : (1F - t) * (frame % 2 == 0 ? 1F : 0.55F);
        float w = e.width() * (thunder ? 1F : 1F);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(BOLT));
        int segs = thunder ? 16 : Math.max(4, (int) (length / 0.8));
        double amp = thunder ? 1.4 : Math.min(0.5, length * 0.08);
        // 幹（空から地面へ。放電は始まりから終わりへ）
        Vec3 from = thunder ? span : Vec3.ZERO, to = thunder ? Vec3.ZERO : span;
        List<Vec3> trunk = jagged(r, from, to, segs, amp, thunder);
        strip(vc, m, n, trunk, cam, 1.4F * w, 0.45F * flash, 0.7F * flash, 1F * flash); // 外の青い光
        strip(vc, m, n, trunk, cam, 0.5F * w, flash, flash, flash); // 白い芯
        if (!thunder) return;
        // 枝分かれ（幹の途中から外へ、下へ）
        for (int b = 0; b < 4; b++) {
            int at = 3 + r.nextInt(segs - 6);
            Vec3 start = trunk.get(at);
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 end = start.add(Math.cos(a) * (2 + r.nextDouble() * 3), -(2 + r.nextDouble() * 4), Math.sin(a) * (2 + r.nextDouble() * 3));
            List<Vec3> branch = jagged(r, start, end, 5, 0.5, false);
            strip(vc, m, n, branch, cam, 0.7F * w, 0.3F * flash, 0.5F * flash, 0.9F * flash);
            strip(vc, m, n, branch, cam, 0.25F * w, 0.8F * flash, 0.9F * flash, flash);
        }
    }

    /** from から to へ、ぎざぎざに折れる点の列（taper = 地面側ほど揺れを小さくして的にぴたりと落とす） */
    private static List<Vec3> jagged(Random r, Vec3 from, Vec3 to, int segs, double amp, boolean taper) {
        List<Vec3> pts = new ArrayList<>();
        Vec3 d = to.subtract(from);
        Vec3 a = d.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = d.cross(a).normalize();
        for (int i = 0; i <= segs; i++) {
            double f = i / (double) segs;
            Vec3 p = from.add(d.scale(f));
            if (i > 0 && i < segs) {
                double k = amp * (taper ? Math.sin(f * Math.PI) * 0.6 + 0.4 * (1 - f) : Math.sin(f * Math.PI));
                p = p.add(a.scale((r.nextDouble() - 0.5) * 2 * k)).add(b.scale((r.nextDouble() - 0.5) * 2 * k));
            }
            pts.add(p);
        }
        return pts;
    }

    /** 点の列に沿った、こちらを向く帯（光を足す） */
    private static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, List<Vec3> pts, Vec3 cam, float width, float r, float g, float b) {
        for (int i = 0; i + 1 < pts.size(); i++) ribbon(vc, m, n, pts.get(i), pts.get(i + 1), cam, width, r, g, b, 0F, 1F);
    }

    /** a から b へ、カメラの方を向く板（幅 width、テクスチャの横が幅、縦 v0〜v1 が長さ） */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, Vec3 cam, float width, float r, float g, float bl, float v0, float v1) {
        Vec3 dir = b.subtract(a);
        Vec3 view = cam.subtract(a.add(b).scale(0.5));
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(width / 2);
        int cr = Math.round(Mth.clamp(r, 0F, 1F) * 255), cg = Math.round(Mth.clamp(g, 0F, 1F) * 255), cb = Math.round(Mth.clamp(bl, 0F, 1F) * 255);
        Vec3[] c = {a.subtract(side), a.add(side), b.add(side), b.subtract(side)};
        float[][] uv = {{0, v0}, {1, v0}, {1, v1}, {0, v1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                vc.vertex(m, (float) c[i].x, (float) c[i].y, (float) c[i].z).color(cr, cg, cb, 255).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    // ---- 光線 ----

    private static void laser(T4StrikeEntity e, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float age, float t) {
        Vec3 span = e.span();
        double length = span.length();
        if (length < 0.1) return;
        float shrink = (float) Math.pow(1 - t, 0.5);
        float bright = t < 0.15F ? 1F : (1 - t) / 0.85F;
        float w = e.width();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer beam = buffers.getBuffer(RenderType.eyes(BEAM));
        float flow = age * 0.8F, v1 = (float) (length / 6.0);
        ribbon(beam, m, n, Vec3.ZERO, span, cam, 3.0F * w * shrink, 1F * bright, 0.7F * bright, 0.2F * bright, flow, flow + v1); // 金の外の光
        ribbon(beam, m, n, Vec3.ZERO, span, cam, 1.5F * w * shrink, 1F * bright, 0.92F * bright, 0.6F * bright, flow * 1.5F, flow * 1.5F + v1);
        ribbon(beam, m, n, Vec3.ZERO, span, cam, 0.5F * w * (0.4F + 0.6F * shrink), bright, bright, bright, flow * 2F, flow * 2F + v1); // 白い芯
        // 流れるきらめきの帯（太めに、光線に沿って先へ流れる）
        VertexConsumer sparkle = buffers.getBuffer(RenderType.eyes(SPARKLE));
        float sv = (float) (length / 3.0), sflow = -age * 0.25F;
        ribbon(sparkle, m, n, Vec3.ZERO, span, cam, 2.6F * w, bright, bright, bright, sflow, sflow + sv);
        ribbon(sparkle, m, n, Vec3.ZERO, span, cam, 1.8F * w, bright, bright * 0.9F, bright * 0.7F, sflow * 1.6F + 0.37F, sflow * 1.6F + 0.37F + sv * 0.8F);
    }

    @Override
    public boolean shouldRender(T4StrikeEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBoxForCulling());
    }

    @Override
    public ResourceLocation getTextureLocation(T4StrikeEntity entity) {
        return BEAM;
    }
}
