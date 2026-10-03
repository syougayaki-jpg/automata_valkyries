package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.AegisBarrierItem;
import com.automatavalkyries.weapon.PolarDeflectorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 展開中の盾の光の膜。光を足す描き方（雷と同じ）で、裏からも見える。格子の筋と、流れる走査の帯で揺らめかせ、展開の始めは広がって出る。
 * <ul>
 *   <li>偏光ディフレクター: 持ち主の前を囲む、薄い水色の半円柱</li>
 *   <li>アイギス: 持ち主を中心にした半径 4 m の、淡い金色のドーム（地面から上の半球と、少し下まで）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class DeflectorShieldRenderer {

    private static final int SEGMENTS = 24, ROWS = 12;
    private static final float RADIUS = 1.15F, BOTTOM = -0.1F, HEIGHT = 2.2F;

    private DeflectorShieldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        PoseStack pose = event.getPoseStack();
        float time = (mc.level.getGameTime() % 100000) + partialTick;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(96), e -> e instanceof LivingEntity))
            consumer = renderFor((LivingEntity) entity, mc, pose, buffers, consumer, cam, partialTick, time);
        consumer = renderFor(mc.player, mc, pose, buffers, consumer, cam, partialTick, time);
        if (consumer != null) buffers.endBatch(RenderType.lightning());
    }

    private static VertexConsumer renderFor(LivingEntity holder, Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                            VertexConsumer consumer, Vec3 cam, float partialTick, float time) {
        boolean deflector = PolarDeflectorItem.deployed(holder), aegis = AegisBarrierItem.deployed(holder);
        if (!deflector && !aegis) return consumer;
        if (consumer == null) consumer = buffers.getBuffer(RenderType.lightning());
        boolean firstPerson = holder == mc.player && mc.options.getCameraType().isFirstPerson();
        double x = Mth.lerp(partialTick, holder.xo, holder.getX()) - cam.x;
        double y = Mth.lerp(partialTick, holder.yo, holder.getY()) - cam.y;
        double z = Mth.lerp(partialTick, holder.zo, holder.getZ()) - cam.z;
        float grow = Mth.clamp((holder.getTicksUsingItem() + partialTick) / (aegis ? 8F : 5F), 0, 1);
        pose.pushPose();
        pose.translate(x, y, z);
        if (deflector) {
            float yaw = Mth.rotLerp(partialTick, holder.yHeadRotO, holder.yHeadRot);
            drawHalfCylinder(pose.last().pose(), consumer, yaw, grow, time, firstPerson ? 0.55F : 1F);
        } else {
            drawDome(pose.last().pose(), consumer, holder.getBbHeight() * 0.5F, grow, time, firstPerson ? 0.6F : 1F);
        }
        pose.popPose();
        return consumer;
    }

    /** 半円柱（向きの左右 90 度ずつ）を、格子のマスごとに明るさを変えて描く（薄い水色） */
    private static void drawHalfCylinder(Matrix4f m, VertexConsumer vc, float yaw, float grow, float time, float fade) {
        float radius = RADIUS * (0.6F + 0.4F * grow);
        float height = HEIGHT * grow;
        float base = (float) Math.toRadians(yaw);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = base + (float) Math.toRadians(-90 + 180.0 * i / SEGMENTS);
            float a1 = base + (float) Math.toRadians(-90 + 180.0 * (i + 1) / SEGMENTS);
            // 向き（yaw）の前方は (-sin, cos)
            float x0 = -Mth.sin(a0) * radius, z0 = Mth.cos(a0) * radius;
            float x1 = -Mth.sin(a1) * radius, z1 = Mth.cos(a1) * radius;
            float sideEdge = (i == 0 || i == SEGMENTS - 1) ? 0.25F : 0;
            for (int j = 0; j < ROWS; j++) {
                float v0 = (float) j / ROWS, v1 = (float) (j + 1) / ROWS;
                float y0 = BOTTOM + height * v0, y1 = BOTTOM + height * v1;
                float scan = (float) Math.max(0, Math.sin((v0 * 3 - time * 0.06) * Math.PI * 2)) * 0.10F;
                float grid = (i % 4 == 0 || j % 3 == 0) ? 0.07F : 0;
                float rim = (j == 0 || j == ROWS - 1) ? 0.22F : 0;
                float alpha = (0.07F + scan + grid + rim + sideEdge) * fade * (0.4F + 0.6F * grow);
                quad(m, vc, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0, 0.55F, 0.86F, 1.0F, alpha);
            }
        }
    }

    /** ドーム（中心 = 持ち主の体の中ほど、半径 4 m）。緯度・経度の格子で、淡い金色 */
    private static void drawDome(Matrix4f m, VertexConsumer vc, float centerY, float grow, float time, float fade) {
        float radius = (float) AegisBarrierItem.RADIUS * (0.3F + 0.7F * grow);
        int lon = 32, lat = 12;
        for (int i = 0; i < lon; i++) {
            float a0 = (float) (Math.PI * 2 * i / lon), a1 = (float) (Math.PI * 2 * (i + 1) / lon);
            for (int j = 0; j < lat; j++) {
                // 緯度は -25 度（少し下）から 90 度（てっぺん）まで
                float p0 = (float) Math.toRadians(-25 + 115.0 * j / lat), p1 = (float) Math.toRadians(-25 + 115.0 * (j + 1) / lat);
                float r0 = Mth.cos(p0) * radius, r1 = Mth.cos(p1) * radius;
                float y0 = centerY + Mth.sin(p0) * radius, y1 = centerY + Mth.sin(p1) * radius;
                float scan = (float) Math.max(0, Math.sin(((float) j / lat * 2 - time * 0.04) * Math.PI * 2)) * 0.08F;
                float grid = (i % 4 == 0 || j % 3 == 0) ? 0.08F : 0;
                float rim = j == 0 ? 0.2F : 0;
                float alpha = (0.05F + scan + grid + rim) * fade * (0.3F + 0.7F * grow);
                quad(m, vc, Mth.cos(a0) * r0, y0, Mth.sin(a0) * r0, Mth.cos(a1) * r0, y0, Mth.sin(a1) * r0,
                        Mth.cos(a1) * r1, y1, Mth.sin(a1) * r1, Mth.cos(a0) * r1, y1, Mth.sin(a0) * r1, 1.0F, 0.85F, 0.5F, alpha);
            }
        }
    }

    /** 両面（裏からも見える） */
    private static void quad(Matrix4f m, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float alpha) {
        vc.vertex(m, ax, ay, az).color(r, g, b, alpha).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, alpha).endVertex();
        vc.vertex(m, ax, ay, az).color(r, g, b, alpha).endVertex();
    }
}
