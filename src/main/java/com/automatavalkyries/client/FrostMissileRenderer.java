package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.FrostMissileEntity;
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
 * 冷凍ミサイルの見た目: 進む向きに沿った機体（白い胴・青い帯・光る氷の弾頭・暗い尾翼の絵、textures/entity/frost_missile_body.png）を
 * 直角に交わる 2 枚の板で描き、後ろに青白い噴射炎（光を足す描き方、揺らめく）を伸ばす。機体の長さは 1 ブロック。
 */
public class FrostMissileRenderer extends EntityRenderer<FrostMissileEntity> {

    private static final ResourceLocation BODY = tex("frost_missile_body"), FLAME = tex("frost_missile");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/" + name + ".png");
    }

    public FrostMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FrostMissileEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 motion = entity.getDeltaMovement();
        Vec3 dir = motion.lengthSqr() > 1.0E-6 ? motion.normalize() : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(new Vec3(0, 1, 0));
        if (a.lengthSqr() < 1.0E-4) a = new Vec3(1, 0, 0);
        a = a.normalize();
        Vec3 b = dir.cross(a).normalize();
        // 機体の回転（ゆっくり回りながら飛ぶ）
        double spin = (entity.tickCount + partialTick) * 0.35 + entity.getId();
        Vec3 ra = a.scale(Math.cos(spin)).add(b.scale(Math.sin(spin)));
        Vec3 rb = dir.cross(ra).normalize();
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 nose = dir.scale(0.5), tail = dir.scale(-0.5);
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(BODY));
        for (Vec3 axis : new Vec3[]{ra, rb}) strip(body, m, n, tail, nose, axis.scale(0.2), 255, 255, 255, 255, light);
        // 噴射炎（揺らめく長さ）
        float flicker = 0.85F + 0.15F * (float) Math.sin((entity.tickCount + partialTick) * 3.1 + entity.getId());
        Vec3 flameEnd = tail.subtract(dir.scale((0.7 + Math.min(1.0, motion.length()) * 0.6) * flicker));
        VertexConsumer flame = buffers.getBuffer(RenderType.eyes(FLAME));
        for (Vec3 axis : new Vec3[]{a, b}) strip(flame, m, n, flameEnd, tail.add(dir.scale(0.05)), axis.scale(0.17 * flicker), 255, 255, 255, 255, 0xF000F0);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** from（絵の下）から to（絵の上）へ、幅 w の板を両面で */
    private static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 from, Vec3 to, Vec3 w, int r, int g, int b, int alpha, int light) {
        Vec3[] corners = {from.subtract(w), from.add(w), to.add(w), to.subtract(w)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                Vec3 p = corners[i];
                vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, alpha).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(FrostMissileEntity entity) {
        return BODY;
    }
}
