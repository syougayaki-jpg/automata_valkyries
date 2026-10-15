package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * メメントモリの弾（2026-10-15 に種類を足した）。どれも加算の光で描く:
 * <ul>
 *   <li>小さな光弾: 色の八芒の光と白い芯、後ろへ伸びる短い光の尾（数が多いので軽く描く）</li>
 *   <li>蝶弾: 光だけの小さな機械の蝶が飛ぶ向きを向いて速く羽ばたく</li>
 *   <li>大玉: 回る大きな八芒の光が 3 重、白い芯</li>
 *   <li>大きな光の蝶（死蝶終葬だけ）: 淡い翅の面のある大きな蝶と、胸の白い八芒の光</li>
 * </ul>
 * 止まって待つ間は芯がまたたく
 */
public class MementoBulletRenderer extends EntityRenderer<MementoBulletEntity> {

    public MementoBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MementoBulletEntity bullet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = bullet.tickCount + partialTick;
        float alpha = Math.min(1.0F, age / 3.0F);
        int color = bullet.color();
        Vec3 fwd = bullet.velocity();
        byte kind = bullet.kind();
        float blink = bullet.isWaiting() ? 0.65F + 0.35F * Mth.sin(age * 1.2F) : 1.0F;
        if (kind == MementoBulletEntity.GRAND) {
            float flap = Mth.sin(age * 0.9F + bullet.getId()) * 0.8F;
            MementoFx.butterfly(buffers, pose.last().pose(), Vec3.ZERO, fwd, new Vec3(0, 1, 0), 0.8F, flap, color, 0xF4E8FF, 0.3F, 0xFFFFFF, alpha);
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            star(glow, pose.last().pose(), 0.32F, age * 0.1F, color, (int) (120 * alpha));
            star(glow, pose.last().pose(), 0.13F, 0.0F, 0xFFFFFF, (int) (255 * alpha * blink));
            pose.popPose();
        } else if (kind == MementoBulletEntity.BUTTERFLY) {
            float flap = Mth.sin(age * 1.6F + bullet.getId()) * 0.7F;
            MementoFx.butterfly(buffers, pose.last().pose(), Vec3.ZERO, fwd, new Vec3(0, 1, 0), 0.36F, flap, color, -1, 0.0F, 0xFFFFFF, alpha * blink);
        } else {
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            if (kind == MementoBulletEntity.SMALL && fwd.lengthSqr() > 1.0e-4 && !bullet.isWaiting()) { // 光の尾
                GalliaSiegeBeamRenderer.line(glow, pose.last().pose(), Vec3.ZERO, fwd.normalize().scale(-0.7), 0.05F,
                        color >> 16 & 255, color >> 8 & 255, color & 255, (int) (110 * alpha));
            }
            pose.pushPose();
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            Matrix4f m = pose.last().pose();
            float spin = age * 0.15F + bullet.getId();
            if (kind == MementoBulletEntity.LARGE) {
                star(glow, m, 0.75F, spin, color, (int) (90 * alpha));
                star(glow, m, 0.48F, -spin * 1.3F, color, (int) (170 * alpha));
                star(glow, m, 0.22F, spin * 2.0F, 0xFFFFFF, (int) (255 * alpha * blink));
            } else {
                star(glow, m, 0.3F, spin, color, (int) (150 * alpha));
                star(glow, m, 0.11F, 0.0F, 0xFFFFFF, (int) (255 * alpha * blink));
            }
            pose.popPose();
        }
        super.render(bullet, yaw, partialTick, pose, buffers, light);
    }

    /** 画面の向きの面に、半径 r の八芒の光（45 度ずらした正方形 2 枚）を表と裏から描く */
    private static void star(VertexConsumer vc, Matrix4f m, float r, float rot, int color, int alpha) {
        int cr = color >> 16 & 255, cg = color >> 8 & 255, cb = color & 255;
        for (int k = 0; k < 2; k++) {
            Vec3[] p = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                double a = rot + k * Math.PI / 4 + i * Math.PI / 2;
                p[i] = new Vec3(Math.cos(a) * r, Math.sin(a) * r, 0);
            }
            MementoFx.quad(vc, m, p[0], p[1], p[2], p[3], cr, cg, cb, alpha);
            MementoFx.quad(vc, m, p[3], p[2], p[1], p[0], cr, cg, cb, alpha);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MementoBulletEntity bullet) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
