package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 景色の歪み（2026-10-10 ユーザー指定「歪曲破界砲は着弾地点の景色が一瞬歪むようにして」）: 世界の 1 か所のまわりの画面を、手前の手や GUI を描く前に
 * 渦・衝撃の輪・三原色のずれで歪ませる（shaders/program/rift_warp）。目印のレンダラー（{@link GalliaRiftRenderer}）がフレームごとに場所と強さを頼み、
 * ここで世界を描き終えた所（{@link RenderLevelStageEvent.Stage#AFTER_LEVEL}）で、画面の色を写した絵から歪ませて描き戻す。頼まれなかったフレームは何もしない。
 * シェーダーを読めなかったら黙ってやめる（ゲームは止めない）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiftWarp {

    private static EffectInstance warp, blit;
    private static TextureTarget swap;
    private static boolean broken;
    /** このフレームに頼まれた歪み（いちばん強いもの） */
    private static Vec3 pos;
    private static float radius, strength, phase;

    private RiftWarp() {
    }

    /** このフレームに、世界の at のまわり（半径 radiusBlocks）を強さ s（0〜1）・進み p（0〜1）で歪ませる */
    public static void request(Vec3 at, float radiusBlocks, float s, float p) {
        if (s <= 0.0F || (pos != null && s <= strength)) return;
        pos = at;
        radius = radiusBlocks;
        strength = s;
        phase = p;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || pos == null) return;
        Vec3 at = pos;
        float r = radius, s = strength, p = phase;
        pos = null;
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        // 画面の上の場所: カメラから見た向きへ直し（ビューの回転）、投影して 0〜1 に
        // AFTER_LEVEL に渡る PoseStack はビューの回転ではなく投影（揺れ込み）なので、カメラの向きからビューの回転を作る
        // （GameRenderer と同じ: X に縦の向き、Y に横の向き + 180 度。2026-10-10「特に歪んでいない」: 投影を 2 度掛けて画面の外になっていた）
        var camera = event.getCamera();
        Vec3 rel = at.subtract(camera.getPosition());
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F);
        new Matrix4f().rotation(com.mojang.math.Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(camera.getYRot() + 180.0F)).transform(v);
        float depth = -v.z;
        if (depth < 0.5F) return;                                                // 後ろ（見えない）
        Matrix4f proj = event.getProjectionMatrix();
        Vector4f clip = proj.transform(new Vector4f(v));
        if (clip.w <= 0.0F) return;
        float cx = (clip.x / clip.w + 1.0F) * 0.5F, cy = (clip.y / clip.w + 1.0F) * 0.5F;
        float screenR = r / depth * proj.m11() * 0.5F;                           // 画面の高さに対する半径
        if (cx < -screenR || cx > 1.0F + screenR || cy < -screenR || cy > 1.0F + screenR) return;
        try {
            apply(mc, cx, cy, Math.min(screenR, 0.9F), s, p);
        } catch (Exception e) {
            broken = true;
            AutomataValkyries.LOGGER.warn("Rift warp shader disabled: {}", e.toString());
        }
    }

    private static void apply(Minecraft mc, float cx, float cy, float r, float s, float p) throws Exception {
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (warp == null) {
            warp = new EffectInstance(mc.getResourceManager(), AutomataValkyries.MODID + ":rift_warp");
            blit = new EffectInstance(mc.getResourceManager(), "blit");
        }
        if (swap == null) {
            swap = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (swap.width != w || swap.height != h) {
            swap.resize(w, h, Minecraft.ON_OSX);
        }
        Matrix4f ortho = new Matrix4f().setOrtho(0.0F, w, 0.0F, h, 0.1F, 1000.0F);
        // 1: 画面の色 → swap へ歪ませて
        warp.setSampler("DiffuseSampler", main::getColorTextureId);
        warp.safeGetUniform("Center").set(cx, cy);
        warp.safeGetUniform("Radius").set(r);
        warp.safeGetUniform("Strength").set(s);
        warp.safeGetUniform("Phase").set(p);
        pass(warp, main, swap, ortho);
        // 2: swap → 画面へ写し戻す
        blit.setSampler("DiffuseSampler", swap::getColorTextureId);
        blit.safeGetUniform("ColorModulate").set(1.0F, 1.0F, 1.0F, 1.0F);
        pass(blit, swap, main, ortho);
        main.bindWrite(false);
    }

    /** バニラの後処理の 1 段と同じ描き方: in の絵を effect で out へ全画面に描く */
    private static void pass(EffectInstance effect, RenderTarget in, RenderTarget out, Matrix4f ortho) {
        in.unbindWrite();
        float w = out.width, h = out.height;
        RenderSystem.viewport(0, 0, (int) w, (int) h);
        effect.safeGetUniform("ProjMat").set(ortho);
        effect.safeGetUniform("InSize").set((float) in.width, (float) in.height);
        effect.safeGetUniform("OutSize").set(w, h);
        effect.apply();
        out.bindWrite(false);
        RenderSystem.depthFunc(519);
        RenderSystem.depthMask(false);                                         // 画面の奥行きは書き換えない
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bb.vertex(0.0D, 0.0D, 500.0D).endVertex();
        bb.vertex(w, 0.0D, 500.0D).endVertex();
        bb.vertex(w, h, 500.0D).endVertex();
        bb.vertex(0.0D, h, 500.0D).endVertex();
        BufferUploader.draw(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515);
        effect.clear();
        out.unbindWrite();
        in.unbindRead();
    }
}
