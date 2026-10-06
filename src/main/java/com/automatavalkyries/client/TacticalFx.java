package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.network.TacticalFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 戦術級コードの演出（自作の描画。2026-10-07 ユーザー指定「戦術級らしく、半径 50 ブロックの赤黒い螺旋状の光で落ちてくるど派手な演出」）。
 * GUNGNIR:
 * <ol>
 *   <li>降下（最初の {@link #DESCENT} Tick）: 空の高み（半径 50）から、赤い光と黒い帯の螺旋 8 本が回りながら狙った所へ収束して落ちてくる</li>
 *   <li>照射: 中心に赤い光の柱（白い芯）、そのまわりを赤と黒の螺旋が高速で巻き上がり、地面には半径 50 の赤黒い輪と、外から内へ縮む衝撃の輪</li>
 *   <li>最後の 10 Tick で消えていく</li>
 * </ol>
 * 赤い光は足し算の描き方（{@link RenderType#lightning}）、黒い帯は半透明（{@link RenderType#debugQuads}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class TacticalFx {

    private TacticalFx() {}

    public static final int DESCENT = TacticalFxPacket.GUNGNIR_DESCENT;
    private static final double HEIGHT = 140.0;

    private record Effect(int kind, Vec3 pos, float radius, int duration, long start) {}

    private static final List<Effect> ACTIVE = new ArrayList<>();

    public static void start(TacticalFxPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ACTIVE.add(new Effect(p.kind(), new Vec3(p.x(), p.y(), p.z()), p.radius(), p.duration(), level.getGameTime()));
    }

    /** 腕の先から土砂・溶岩・煙を噴き上げる（えぐり取る様子。地形が壊れない設定でも見た目は出す） */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused()) return;
        var rand = level.random;
        for (Effect e : ACTIVE) {
            float st = level.getGameTime() - e.start - DESCENT;
            if (st < 0 || st > TacticalFxPacket.GUNGNIR_STRIKE) continue;
            for (int arm = 0; arm < TacticalFxPacket.GUNGNIR_ARMS; arm++) {
                double[] g = TacticalFxPacket.gungnirArm(arm, st, e.radius);
                int x = Mth.floor(e.pos.x + g[0]), z = Mth.floor(e.pos.z + g[1]);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                var below = level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z));
                var debris = below.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : below;
                for (int i = 0; i < 10; i++) {
                    level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, debris),
                            x + rand.nextDouble(), y + 0.2, z + rand.nextDouble(), (rand.nextDouble() - 0.5) * 1.2, 0.6 + rand.nextDouble() * 1.4, (rand.nextDouble() - 0.5) * 1.2);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, x + 0.5, y + 0.3, z + 0.5, 0, 0, 0);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x + rand.nextDouble(), y + 0.5, z + rand.nextDouble(), 0, 0.25, 0);
                if (((int) st + arm) % 6 == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, x + 0.5, y + 1, z + 0.5, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Iterator<Effect> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            float t = mc.level.getGameTime() - e.start + partial;
            if (t > e.duration) {
                it.remove();
                continue;
            }
            pose.pushPose();
            pose.translate(e.pos.x - cam.x, e.pos.y - cam.y, e.pos.z - cam.z);
            float fade = Mth.clamp((e.duration - t) / 10F, 0F, 1F);
            Matrix4f m = pose.last().pose();
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            VertexConsumer dark = buffers.getBuffer(RenderType.debugQuads());
            if (t < DESCENT) descent(m, glow, dark, e.radius, t / DESCENT, t);
            else strike(m, glow, dark, e.radius, t - DESCENT, fade);
            pose.popPose();
        }
        buffers.endBatch(RenderType.lightning());
        buffers.endBatch(RenderType.debugQuads());
    }

    /** 降下: 螺旋 8 本の頭が空から地面へ。半径は高さに比例（上で radius、下で 3） */
    private static void descent(Matrix4f m, VertexConsumer glow, VertexConsumer dark, float radius, float k, float t) {
        double head = HEIGHT * (1 - k * k); // だんだん速く
        for (int arm = 0; arm < 8; arm++) {
            double base = arm * Math.PI / 4;
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int s = 0; s <= 40; s++) {
                double u = s / 40.0;
                double y = head + u * 50.0;                       // 頭から上へ 50 の尾
                double r = Math.max(3.0, radius * Math.min(1.0, y / HEIGHT));
                double a = base + u * Math.PI * 3 + t * 0.25;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                if (s > 0) {
                    float alpha = (float) (1 - u);                // 尾ほど薄く
                    ribbon(glow, m, prevX, prevY, prevZ, x, y, z, 0.9, 255, 40, 50, (int) (230 * alpha));
                    ribbon(dark, m, prevX * 1.04, prevY - 0.6, prevZ * 1.04, x * 1.04, y - 0.6, z * 1.04, 1.6, 15, 0, 5, (int) (170 * alpha));
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
    }

    /** 照射: 柱・巻き上がる螺旋・地面の輪 */
    private static void strike(Matrix4f m, VertexConsumer glow, VertexConsumer dark, float radius, float t, float fade) {
        // 光の柱（赤、芯は白っぽく）
        int a = (int) (220 * fade);
        cylinder(glow, m, 4.5, 0, HEIGHT, 24, 255, 30, 40, a);
        cylinder(glow, m, 1.6, 0, HEIGHT, 16, 255, 220, 220, a);
        // 巻き上がる赤と黒の螺旋（中心から半径いっぱいまで広がりながら）
        for (int arm = 0; arm < 8; arm++) {
            double base = arm * Math.PI / 4 + t * 0.35;
            double px = 0, py = 0, pz = 0;
            for (int s = 0; s <= 48; s++) {
                double u = s / 48.0, y = u * 60.0;
                double r = 3 + (radius - 3) * Math.pow(u, 1.6) * (0.6 + 0.4 * Math.sin(t * 0.08 + arm));
                double ang = base + u * Math.PI * 4;
                double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
                if (s > 0) {
                    boolean red = arm % 2 == 0;
                    if (red) ribbon(glow, m, px, py, pz, x, y, z, 1.0, 255, 30, 40, (int) (200 * fade * (1 - u * 0.6)));
                    else ribbon(dark, m, px, py, pz, x, y, z, 1.8, 10, 0, 4, (int) (160 * fade * (1 - u * 0.5)));
                }
                px = x;
                py = y;
                pz = z;
            }
        }
        // 空から地面をえぐる螺旋の腕（腕の先がサーバーで地形を削る所。TacticalFxPacket#gungnirArm）
        for (int arm = 0; arm < TacticalFxPacket.GUNGNIR_ARMS; arm++) {
            double[] g = TacticalFxPacket.gungnirArm(arm, t, radius);
            double baseA = Math.atan2(g[1], g[0]), gr = Math.hypot(g[0], g[1]);
            double px = 0, py = 0, pz = 0;
            for (int s = 0; s <= 40; s++) {
                double u = s / 40.0;                         // 0 = 空、1 = 地面
                double y = HEIGHT * (1 - u);
                double r = radius + (gr - radius) * u;
                double ang = baseA - (1 - u) * Math.PI * 1.5;
                double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
                if (s > 0) {
                    ribbon(glow, m, px, py, pz, x, y, z, 2.2, 255, 25, 35, (int) (230 * fade * (0.4 + 0.6 * u)));
                    ribbon(dark, m, px * 1.03, py + 1.2, pz * 1.03, x * 1.03, y + 1.2, z * 1.03, 2.6, 8, 0, 2, (int) (180 * fade));
                }
                px = x;
                py = y;
                pz = z;
            }
            // 腕の先の地面に赤い渦
            ringAt(glow, m, g[0], g[1], 3.5 + Math.sin(t * 0.6 + arm) * 0.8, 0.3, 1.4, 255, 60, 40, (int) (220 * fade));
        }
        // 地面の輪（半径いっぱいの赤黒い輪と、外から内へ縮む衝撃の輪）
        ring(glow, m, radius, 0.15, 1.2, 255, 20, 30, (int) (220 * fade));
        ring(dark, m, radius + 1.5, 0.12, 1.6, 0, 0, 0, (int) (170 * fade));
        for (int w = 0; w < 3; w++) {
            float phase = ((t + w * 13) % 40) / 40F;
            ring(glow, m, radius * (1 - phase), 0.2, 1.0 + 2 * phase, 255, 60, 60, (int) (200 * fade * (1 - phase)));
        }
    }

    // ------------------------------------------------------------------ 形

    /** 4 点の面を表と裏の両方（裏から見ても消えないよう）。xyz は 4 点分、rgba は 4 点分 */
    private static void quad(VertexConsumer c, Matrix4f m, float[] p, int[] col) {
        for (int i = 0; i < 4; i++) c.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(col[i * 4], col[i * 4 + 1], col[i * 4 + 2], col[i * 4 + 3]).endVertex();
        for (int i = 3; i >= 0; i--) c.vertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(col[i * 4], col[i * 4 + 1], col[i * 4 + 2], col[i * 4 + 3]).endVertex();
    }

    /** 2 点の間の帯（上下に width の厚み） */
    private static void ribbon(VertexConsumer c, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                               double width, int r, int g, int b, int a) {
        if (a <= 0) return;
        float h = (float) (width / 2);
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1, fx2 = (float) x2, fy2 = (float) y2, fz2 = (float) z2;
        quad(c, m, new float[]{fx1, fy1 - h, fz1, fx1, fy1 + h, fz1, fx2, fy2 + h, fz2, fx2, fy2 - h, fz2},
                new int[]{r, g, b, a, r, g, b, a, r, g, b, a, r, g, b, a});
    }

    /** 中心の柱（側面だけ） */
    private static void cylinder(VertexConsumer c, Matrix4f m, double r, double y0, double y1, int seg, int cr, int cg, int cb, int a) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) (Math.cos(a0) * r), z0 = (float) (Math.sin(a0) * r), x1 = (float) (Math.cos(a1) * r), z1 = (float) (Math.sin(a1) * r);
            quad(c, m, new float[]{x0, (float) y0, z0, x0, (float) y1, z0, x1, (float) y1, z1, x1, (float) y0, z1},
                    new int[]{cr, cg, cb, a, cr, cg, cb, a / 4, cr, cg, cb, a / 4, cr, cg, cb, a});
        }
    }

    /** 中心から (cx, cz) ずれた所の輪 */
    private static void ringAt(VertexConsumer c, Matrix4f m, double cx, double cz, double r, double y, double width, int cr, int cg, int cb, int a) {
        Matrix4f shifted = new Matrix4f(m).translate((float) cx, 0, (float) cz);
        ring(c, shifted, r, y, width, cr, cg, cb, a);
    }

    /** 地面の輪（高さ y の水平な帯、幅 width） */
    private static void ring(VertexConsumer c, Matrix4f m, double r, double y, double width, int cr, int cg, int cb, int a) {
        if (a <= 0 || r <= 0.1) return;
        int seg = 96;
        double ri = Math.max(0, r - width / 2), ro = r + width / 2;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float fy = (float) y;
            quad(c, m, new float[]{(float) (Math.cos(a0) * ri), fy, (float) (Math.sin(a0) * ri), (float) (Math.cos(a0) * ro), fy, (float) (Math.sin(a0) * ro),
                    (float) (Math.cos(a1) * ro), fy, (float) (Math.sin(a1) * ro), (float) (Math.cos(a1) * ri), fy, (float) (Math.sin(a1) * ri)},
                    new int[]{cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a, cr, cg, cb, a});
        }
    }
}
