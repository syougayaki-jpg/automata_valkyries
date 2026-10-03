package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.JavelinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * サンダージャベリンの見た目: 飛ぶ向き（刺さったら刺さった向きのまま）に沿った長さ 2 ブロックの槍（金の柄・光る雷の穂先の絵）を、
 * 直角に交わる 2 枚の板で描き、穂先に雷の光を重ねる（光を足す描き方）。
 */
public class JavelinRenderer extends EntityRenderer<JavelinEntity> {

    private static final ResourceLocation BODY = tex("thunder_javelin"), GLOW = tex("frost_missile");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    public JavelinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(JavelinEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 dir = entity.renderDir; // 飛んでいる向き（刺さったら最後の向き）
        Vec3 a = dir.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = dir.cross(a).normalize();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 tip = dir.scale(0.6), butt = dir.scale(-1.4);
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(BODY));
        for (Vec3 axis : new Vec3[]{a, b}) strip(body, m, n, butt, tip, axis.scale(0.16), light);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        float flicker = 0.8F + 0.2F * (float) Math.sin((entity.tickCount + partialTick) * 2.7);
        for (Vec3 axis : new Vec3[]{a, b}) strip(glow, m, n, tip.subtract(dir.scale(0.7)), tip.add(dir.scale(0.25)), axis.scale(0.22 * flicker), 0xF000F0);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 from, Vec3 to, Vec3 w, int light) {
        Vec3[] corners = {from.subtract(w), from.add(w), to.add(w), to.subtract(w)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                Vec3 p = corners[i];
                vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(JavelinEntity entity) {
        return BODY;
    }
}
