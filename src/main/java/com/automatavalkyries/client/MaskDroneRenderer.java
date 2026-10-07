package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MaskDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 仮面の観測機のレンダラー: 溜めている間は顔の前に赤い光が膨らみ、細い照準線が相手へ伸びて明滅する。
 * 照射中は太い赤の光線（外のにじみ・中の赤・白い芯の 3 重）を、ブロックで止まる所まで描く
 */
public class MaskDroneRenderer extends ChronoGazerRenderer<MaskDroneEntity> {

    public MaskDroneRenderer(EntityRendererProvider.Context context) {
        super(context, "chrono_mask_drone", 0.5F);
    }

    /** 光線は体の外まで伸びるので、撃っている間は体が画面の外でも描く */
    @Override
    public boolean shouldRender(MaskDroneEntity drone, Frustum frustum, double x, double y, double z) {
        return drone.getState() != MaskDroneEntity.IDLE ? drone.distanceToSqr(x, y, z) < 96 * 96 : super.shouldRender(drone, frustum, x, y, z);
    }

    @Override
    public void render(MaskDroneEntity drone, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(drone, entityYaw, partialTick, pose, buffers, light);
        int state = drone.getState();
        if (state == MaskDroneEntity.IDLE) return;
        float t = drone.tickCount - drone.stateStart + partialTick;
        Vec3 at = drone.getPosition(partialTick);
        Vec3 origin = drone.beamOrigin().subtract(drone.position());
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(at);
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        if (state == MaskDroneEntity.CHARGING) {
            float k = Mth.clamp(t / MaskDroneEntity.CHARGE, 0.0F, 1.0F);
            // 顔の前の光の玉（溜まるほど大きく）
            Vec3 face = origin.add(Vec3.directionFromRotation(drone.getXRot(), drone.getYHeadRot()).scale(0.55));
            star(vc, m, face, cam, 0.15F + 0.5F * k, 255, 40, 70, (int) (90 + 120 * k));
            star(vc, m, face, cam, 0.06F + 0.2F * k, 255, 210, 220, (int) (120 + 120 * k));
            Entity aim = drone.aimTarget();
            if (aim != null) { // 照準線（溜めの最後ほど速く明滅）
                Vec3 to = aim.getPosition(partialTick).add(0, aim.getBbHeight() * 0.55, 0).subtract(at);
                float blink = Mth.sin(t * (0.4F + k * 1.6F)) * 0.5F + 0.5F;
                ribbon(vc, m, face, to, cam, 0.03F + 0.03F * k, 255, 40, 70, (int) (60 + 150 * blink * k + 40));
            }
        } else {
            float k = Mth.clamp(Math.min(t / 3.0F, (MaskDroneEntity.FIRE - t) / 4.0F), 0.0F, 1.0F); // 撃ち始めと終わりは細く
            Vec3 end = drone.beamEnd().subtract(drone.position());
            float wobble = 1.0F + Mth.sin(t * 2.3F) * 0.08F;
            ribbon(vc, m, origin, end, cam, 0.9F * k * wobble, 255, 30, 60, 70);
            ribbon(vc, m, origin, end, cam, 0.45F * k * wobble, 255, 50, 80, 170);
            ribbon(vc, m, origin, end, cam, 0.14F * k, 255, 225, 235, 230);
            star(vc, m, origin, cam, 0.8F * k, 255, 60, 90, 180);
            star(vc, m, end, cam, 0.7F * k * wobble, 255, 80, 100, 160);
        }
    }

    /** a から b への、カメラを向いた帯 */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float width, int r, int g, int bl, int alpha) {
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(cam.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1.0e-6) return;
        side = side.normalize().scale(width / 2.0);
        quad(vc, m, a.add(side), a.subtract(side), b.subtract(side), b.add(side), r, g, bl, alpha);
    }

    /** p を中心にカメラを向いた菱形の光 */
    private static void star(VertexConsumer vc, Matrix4f m, Vec3 p, Vec3 cam, float size, int r, int g, int b, int alpha) {
        Vec3 view = cam.subtract(p).normalize();
        Vec3 up = Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = view.cross(up).normalize().scale(size);
        Vec3 top = right.cross(view).normalize().scale(size);
        quad(vc, m, p.add(top), p.add(right), p.subtract(top), p.subtract(right), r, g, b, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int alpha) {
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) d.x, (float) d.y, (float) d.z).color(r, g, bl, alpha).endVertex();
        // 裏からも見えるように
        vc.vertex(m, (float) d.x, (float) d.y, (float) d.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, alpha).endVertex();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, alpha).endVertex();
    }
}
