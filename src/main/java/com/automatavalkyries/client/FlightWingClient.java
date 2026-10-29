package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.FlightWingItem;
import com.automatavalkyries.particle.FxOptions;
import com.automatavalkyries.registry.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 光翼の飛翔機を持っている間の背の光の翼（2026-10-29 ユーザー指定「光翼の飛翔機にも持っている時の見た目を作って」）。
 * アイテムの絵と同じく、付け根の紫から先の水色へ移る光の羽が、左右に扇のように 3 段（風切り羽・次の羽・雨覆い）重なる。羽の縁は明るく光る。
 * <ul>
 *   <li>地面にいる間: 翼を半ば畳み、ゆっくり呼吸するように揺れる</li>
 *   <li>空にいる間: 大きく広げてゆったり羽ばたき、羽の先から光の粒がこぼれる</li>
 * </ul>
 * 持ち主（プレイヤー・兵姫）の背に描く（{@link T4BackdropClient} と同じ置き方。兵姫は {@code ValkyrieRenderer} から呼ぶ）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class FlightWingClient {

    private static final int[] ROOT = {150, 92, 255}, MID = {150, 170, 255}, TIP = {143, 230, 255}, EDGE = {226, 248, 255};

    private FlightWingClient() {}

    static boolean holding(LivingEntity e) {
        return e.getMainHandItem().getItem() instanceof FlightWingItem || e.getOffhandItem().getItem() instanceof FlightWingItem;
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        render(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
    }

    public static void render(LivingEntity e, PoseStack pose, MultiBufferSource buffers, float pt) {
        if (e.isInvisible() || e.isSleeping() || !holding(e)) return;
        float time = e.tickCount + pt;
        boolean air = !e.onGround() && !e.isPassenger();
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float s = e.getBbHeight() / 1.8F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw)); // +Z が背中側
        float height = e.getBbHeight() * (e.getPose() == Pose.CROUCHING ? 0.62F : 0.72F);
        pose.translate(0, height, 0.2F);
        pose.scale(s, s, s);
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        // 広げ具合と羽ばたき（空では大きく広げて羽ばたく、地面では半ば畳んで呼吸）
        float open = air ? 1.0F : 0.45F;
        float flap = air ? Mth.sin(time * 0.22F) * 0.35F : Mth.sin(time * 0.06F) * 0.06F;
        for (int side = -1; side <= 1; side += 2) wing(m, glow, side, open, flap, time, air);
        pose.popPose();
        if (air && e.level().random.nextInt(3) == 0) sparkle(e, bodyYaw, s, height, open);
    }

    /** 片方の翼（side -1 = 右、1 = 左）: 3 段の羽を扇に並べる */
    private static void wing(Matrix4f m, VertexConsumer c, int side, float open, float flap, float time, boolean air) {
        // 段ごと: 羽の数・付け根からの長さ・扇の始まりと終わりの角度（度。0 = 真横、90 = 真上）・幅・明るさ
        float[][] rows = {{7, 1.55F, -30F, 75F, 0.16F, 1.0F}, {6, 1.05F, -10F, 65F, 0.18F, 0.85F}, {5, 0.6F, 5F, 55F, 0.2F, 0.7F}};
        for (int r = 0; r < rows.length; r++) {
            float[] row = rows[r];
            int n = (int) row[0];
            for (int i = 0; i < n; i++) {
                float u = n == 1 ? 0 : (float) i / (n - 1);
                float deg = Mth.lerp(u, row[2], row[3]) * (0.55F + 0.45F * open) + flap * 40F * (1 - u * 0.5F);
                float len = row[1] * (0.85F + 0.25F * (1 - Math.abs(u - 0.35F))) * (0.7F + 0.3F * open);
                double a = Math.toRadians(deg);
                // 付け根（肩甲骨のあたり）。羽は横（x）と上（y）へ伸び、少し後ろ（z）へ反る
                Vec3 root = new Vec3(side * (0.12 + r * 0.05), 0.05 - r * 0.04, 0.05 + r * 0.03);
                Vec3 dir = new Vec3(side * Math.cos(a), Math.sin(a), 0.35 + 0.25 * (1 - open)).normalize();
                Vec3 tip = root.add(dir.scale(len * (1.0 + 0.04 * Mth.sin(time * 0.3F + i + r))));
                Vec3 widthDir = new Vec3(-dir.y * side, dir.x * side, 0).normalize();
                float w0 = row[4], w1 = row[4] * 0.25F;
                int al = (int) (170 * row[5] * (air ? 1.0F : 0.75F));
                feather(m, c, root, tip, widthDir, w0, w1, al);
                feather(m, c, root.add(0, 0, -0.01), tip.add(0, 0, -0.01), widthDir, w0 * 0.25F, w1 * 0.4F, -1); // 光る羽の筋
            }
        }
    }

    /** 羽 1 枚: 付け根の紫から先の水色へ。al = -1 なら縁の明るい光（細い筋） */
    private static void feather(Matrix4f m, VertexConsumer c, Vec3 root, Vec3 tip, Vec3 wd, float w0, float w1, int al) {
        Vec3 mid = root.add(tip).scale(0.5);
        int[] c0 = al < 0 ? EDGE : ROOT, c1 = al < 0 ? EDGE : MID, c2 = al < 0 ? EDGE : TIP;
        int a0 = al < 0 ? 220 : al, a1 = al < 0 ? 200 : (int) (al * 0.9F), a2 = al < 0 ? 120 : (int) (al * 0.55F);
        float wm = (w0 + w1) * 0.6F;
        quad(m, c, root.add(wd.scale(-w0)), root.add(wd.scale(w0)), mid.add(wd.scale(wm)), mid.add(wd.scale(-wm)), c0, a0, c1, a1);
        quad(m, c, mid.add(wd.scale(-wm)), mid.add(wd.scale(wm)), tip.add(wd.scale(w1)), tip.add(wd.scale(-w1)), c1, a1, c2, a2);
    }

    private static void quad(Matrix4f m, VertexConsumer c, Vec3 a, Vec3 b, Vec3 cc, Vec3 d, int[] col0, int a0, int[] col1, int a1) {
        v(m, c, a, col0, a0);
        v(m, c, b, col0, a0);
        v(m, c, cc, col1, a1);
        v(m, c, d, col1, a1);
        // 裏からも見えるように逆向きも
        v(m, c, d, col1, a1);
        v(m, c, cc, col1, a1);
        v(m, c, b, col0, a0);
        v(m, c, a, col0, a0);
    }

    private static void v(Matrix4f m, VertexConsumer c, Vec3 p, int[] col, int a) {
        c.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(col[0], col[1], col[2], Math.max(0, Math.min(255, a))).endVertex();
    }

    /** 空にいる間、羽の先あたりから光の粒がこぼれる（世界の座標で） */
    private static void sparkle(LivingEntity e, float bodyYaw, float s, float height, float open) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        double yaw = Math.toRadians(180F - bodyYaw);
        int side = level.random.nextBoolean() ? 1 : -1;
        double lx = side * (0.8 + level.random.nextDouble() * 0.9) * s * open, ly = height + (0.3 + level.random.nextDouble() * 0.9) * s, lz = 0.6 * s;
        double x = e.getX() + lx * Math.cos(yaw) + lz * Math.sin(yaw), z = e.getZ() - lx * Math.sin(yaw) + lz * Math.cos(yaw);
        int col = level.random.nextInt(3) == 0 ? 0x9A5CFF : 0x8FE6FF;
        level.addParticle(new FxOptions(ModParticles.GLOW.get(), col, 0.08F, 14, 0F), x, e.getY() + ly, z, 0, -0.02, 0);
    }
}
