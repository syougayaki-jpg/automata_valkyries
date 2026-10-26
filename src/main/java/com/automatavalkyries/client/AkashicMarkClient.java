package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.automatavalkyries.entity.seer.AkashicSeerEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 事象観測姫アカシックの観測の印（2026-10-26 ユーザー指定「敵対エンティティやプレイヤーにマーキングエフェクトがつくようにして」。質問で: 頭上と足元の印）。
 * 印の付いた相手の頭上に回る桃色の菱形の照準と 4 つの水色の刻み、足元に回る水色の破線の輪を、壁越しにも（深さを見ずに）描く。
 * 自分に印が付いていれば、画面の上に警告を出す
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class AkashicMarkClient {

    private AkashicMarkClient() {
    }

    /** いま印の付いている相手（近くのアカシックすべてから） */
    static List<Entity> marked(Minecraft mc) {
        List<Entity> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!e.isAlive() || e.distanceToSqr(mc.player) > 96 * 96) continue;
            int[] ids = null;
            if (e instanceof AkashicSeerEntity seer) {
                ids = seer.markedIds();
            } else if (e instanceof com.automatavalkyries.entity.akashic.AkashicProbeEntity probe) {
                ids = probe.markedIds();
            }
            if (ids == null) continue;
            for (int id : ids) {
                Entity t = mc.level.getEntity(id);
                if (t != null && t.isAlive() && !out.contains(t)) out.add(t);
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        List<Entity> targets = marked(mc);
        if (targets.isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float pt = event.getPartialTick();
        float time = (mc.level.getGameTime() % 100000) + pt;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z()), upv = new Vec3(up.x(), up.y(), up.z());
        Matrix4f m = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();                                                     // 壁越しにも見える
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Entity t : targets) {
            Vec3 base = t.getPosition(pt).subtract(cam);
            float bob = Mth.sin(time * 0.15F + t.getId()) * 0.06F;
            Vec3 head = base.add(0, t.getBbHeight() + 0.55 + bob, 0);
            float pulse = 0.75F + 0.25F * Mth.sin(time * 0.3F + t.getId());
            reticle(bb, m, head, right, upv, time, pulse);
            ring(bb, m, base.add(0, 0.05, 0), t.getBbWidth() * 0.8F + 0.35F, time, pulse);
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** 頭上の照準: 回る桃色の菱形の枠と小さな芯、外を逆へ回る 4 つの水色の刻み */
    private static void reticle(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float time, float pulse) {
        float a0 = time * 0.06F, s = 0.26F;
        Vec3[] corner = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            float a = a0 + k * Mth.HALF_PI;
            corner[k] = c.add(right.scale(Mth.cos(a) * s)).add(up.scale(Mth.sin(a) * s));
        }
        int pink = AkashicCubeEntity.PINK, cyan = AkashicCubeEntity.CYAN;
        for (int k = 0; k < 4; k++) line(bb, m, corner[k], corner[(k + 1) % 4], right, up, 0.035F, pink, (int) (230 * pulse));
        Vec3 core = right.scale(0.07), coreUp = up.scale(0.07);
        quad(bb, m, c.add(core), c.add(coreUp), c.subtract(core), c.subtract(coreUp), pink, (int) (200 * pulse));
        for (int k = 0; k < 4; k++) {
            float a = -time * 0.09F + k * Mth.HALF_PI;
            Vec3 d = right.scale(Mth.cos(a)).add(up.scale(Mth.sin(a)));
            line(bb, m, c.add(d.scale(0.36)), c.add(d.scale(0.48)), right, up, 0.04F, cyan, (int) (220 * pulse));
        }
    }

    /** 足元の輪: 水色の破線が回る */
    private static void ring(BufferBuilder bb, Matrix4f m, Vec3 c, float r, float time, float pulse) {
        int segs = 24;
        float rot = time * 0.05F;
        for (int i = 0; i < segs; i += 2) {
            float a0 = rot + Mth.TWO_PI * i / segs, a1 = rot + Mth.TWO_PI * (i + 1) / segs;
            Vec3 i0 = c.add(Mth.cos(a0) * (r - 0.05F), 0, Mth.sin(a0) * (r - 0.05F)), i1 = c.add(Mth.cos(a1) * (r - 0.05F), 0, Mth.sin(a1) * (r - 0.05F));
            Vec3 o0 = c.add(Mth.cos(a0) * (r + 0.05F), 0, Mth.sin(a0) * (r + 0.05F)), o1 = c.add(Mth.cos(a1) * (r + 0.05F), 0, Mth.sin(a1) * (r + 0.05F));
            quad(bb, m, i0, i1, o1, o0, i % 4 == 0 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.PINK, (int) (210 * pulse));
        }
    }

    /** a から b への、画面に向いた太さ w の線 */
    private static void line(BufferBuilder bb, Matrix4f m, Vec3 a, Vec3 b, Vec3 right, Vec3 up, float w, int rgb, int alpha) {
        Vec3 d = b.subtract(a);
        Vec3 n = right.scale(-d.dot(up)).add(up.scale(d.dot(right)));                       // 画面の中で線に直交する向き
        if (n.lengthSqr() < 1.0e-8) return;
        n = n.normalize().scale(w / 2);
        quad(bb, m, a.add(n), b.add(n), b.subtract(n), a.subtract(n), rgb, alpha);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, al = Mth.clamp(alpha, 0, 255);
        for (Vec3 p : new Vec3[]{a, b, c, d}) bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, bl, al).endVertex();
    }

    /** 自分に印が付いていれば、画面の上に警告 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !marked(mc).contains(mc.player)) return;
        float pulse = 0.6F + 0.4F * Mth.sin((mc.player.tickCount + event.getPartialTick()) * 0.3F);
        int alpha = (int) (255 * pulse) << 24;
        Component text = Component.translatable("message.automata_valkyries.akashic_seer.marked").withStyle(ChatFormatting.BOLD);
        var g = event.getGuiGraphics();
        g.drawCenteredString(mc.font, text, g.guiWidth() / 2, 62, 0xFF5AD8 | alpha);
    }
}
