package com.automatavalkyries.client;

import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.automatavalkyries.entity.seer.AkashicSeerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 事象観測姫アカシック（一般ボス。2026-10-26）の描画: 体は事象観測体アカシック・プローブと同じマイクラ風の姿とスキン（{@link AkashicProbeRenderer}）。
 * まわりに桃・薄紫・水色の正二十面体の結晶が回る（第 2 形態は速く多く）。結晶の結界の間は大きな結晶 6 つが半径 {@value AkashicSeerEntity#ORBIT_R} を回り、
 * アカシック・レコード展開の溜めの間は結晶が渦を巻いて胸へ集まる。倒れると横に倒れながらほどける
 */
public class AkashicSeerRenderer extends EntityRenderer<AkashicSeerEntity> {

    private final PlayerModel<AkashicSeerEntity> model;

    public AkashicSeerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.4F;
        this.model = new PlayerModel<>(AkashicProbeRenderer.createBody().bakeRoot(), false);
    }

    @Override
    public ResourceLocation getTextureLocation(AkashicSeerEntity entity) {
        return AkashicProbeRenderer.TEXTURE;
    }

    @Override
    public void render(AkashicSeerEntity e, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = e.tickCount + pt;
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float headYaw = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot);

        pose.pushPose();
        float spin = 0.0F, lean = 0.0F, lift = 0.0F;                                           // 体ごとの動き（回る・反る・浮く）
        float barrier = e.animAge(AkashicSeerEntity.EV_BARRIER, pt);
        if (barrier < 18.0F) spin = 360.0F * ease(barrier / 18.0F);                         // 結界: その場でくるりと回る
        float burst = e.animAge(AkashicSeerEntity.EV_BURST, pt);
        if (burst < AkashicSeerEntity.BURST_TICKS) {                                         // 全方位へ放つ: 反ってから宙に留まり、腕を広げてゆっくり一回転
            lean = -14.0F * window(burst, 0.0F, 3.0F, 8.0F, 20.0F);
            lift += 0.5F * window(burst, 0.0F, 8.0F, AkashicSeerEntity.BURST_TICKS - 12.0F, AkashicSeerEntity.BURST_TICKS);
            spin += 360.0F * ease(burst / AkashicSeerEntity.BURST_TICKS);
        }
        float phase = e.animAge(AkashicSeerEntity.EV_PHASE2, pt);
        if (phase < 36.0F) lift += 0.35F * window(phase, 0.0F, 8.0F, 24.0F, 36.0F);           // 第 2 形態: 少し浮いて光を放つ
        if (e.chargeLeft() > 0) lift += 0.15F + Mth.sin(time * 0.3F) * 0.03F;
        float lanceAge = e.animAge(AkashicSeerEntity.EV_LANCE, pt);
        if (lanceAge < 20.0F) lean += 12.0F * window(lanceAge, 6.0F, 9.0F, 14.0F, 20.0F);     // 結晶槍: 振り下ろして前へ屈む
        float blinkAge = e.animAge(AkashicSeerEntity.EV_BLINK, pt);
        if (blinkAge < 10.0F) spin -= 360.0F * ease(blinkAge / 10.0F);                        // 観測の瞬き: 現れてくるりと回る
        if (e.hurtTime > 0) lean += 6.0F * e.hurtTime / 10.0F;                                 // 受けるとのけぞる
        pose.translate(0.0F, lift, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw + spin));
        if (e.deathTime > 0) {                                                               // 倒れる（バニラと同じ）
            float f = Math.min(1.0F, Mth.sqrt((e.deathTime + pt - 1.0F) / 20.0F * 1.6F));
            pose.mulPose(Axis.ZP.rotationDegrees(f * 90.0F));
        }
        pose.mulPose(Axis.XP.rotationDegrees(lean));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(AkashicProbeRenderer.SCALE, AkashicProbeRenderer.SCALE, AkashicProbeRenderer.SCALE);
        pose.translate(0.0F, -1.501F, 0.0F);
        model.attackTime = e.getAttackAnim(pt);
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.setupAnim(e, e.walkAnimation.position(pt), Math.min(1.0F, e.walkAnimation.speed(pt)), time, Mth.clamp(Mth.wrapDegrees(headYaw - yaw), -60.0F, 60.0F), Mth.clamp(Mth.lerp(pt, e.xRotO, e.getXRot()), -35.0F, 35.0F));   // 首は左右 60 度・上下 35 度まで（2026-10-26「首が回転しすぎ」）
        animate(e, time, pt);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        float alpha = e.deathTime > 0 ? Math.max(0.0F, 1.0F - e.deathTime / (float) AkashicSeerEntity.DEATH_TICKS) : 1.0F;
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(AkashicProbeRenderer.TEXTURE)), light, LivingEntityRenderer.getOverlayCoords(e, 0.0F), 1.0F, 1.0F, 1.0F, alpha);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(AkashicProbeRenderer.GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);
        pose.popPose();

        if (e.deathTime == 0) cubes(e, time, pose, buffers);
        super.render(e, entityYaw, pt, pose, buffers, light);
    }

    /**
     * 技ごとの手足の動き（2026-10-26 ユーザー指定「いいかんじにモーションもつけて」）。歩く・首を振るのはプレイヤーと同じで、その上に重ねる。
     * どれも出だしと終わりをなめらかに混ぜる（{@link #window}）
     */
    private void animate(AkashicSeerEntity e, float time, float pt) {
        var ra = model.rightArm;
        var la = model.leftArm;
        // ふだん: 息をするように腕が少し開いて揺れ、首を傾ける
        ra.zRot += 0.08F + Mth.sin(time * 0.08F) * 0.04F;
        la.zRot -= 0.08F + Mth.sin(time * 0.08F) * 0.04F;
        model.head.zRot = Mth.sin(time * 0.05F) * 0.07F;                                    // 首の傾き（setupAnim は zRot を戻さないので、足さずに決める。2026-10-26「頭がまだぐるぐるまわる」）
        model.body.y += Mth.sin(time * 0.1F) * 0.15F;
        model.head.y += Mth.sin(time * 0.1F) * 0.15F;

        if (e.chargeLeft() > 0) {                                                            // アカシック・レコード展開の溜め: 両腕を広げて掲げ、空を仰ぐ
            float k = Math.min(1.0F, (AkashicSeerEntity.CHARGE_TICKS - e.chargeLeft() + pt) / 8.0F);
            ra.xRot = Mth.lerp(k, ra.xRot, -0.3F);
            la.xRot = Mth.lerp(k, la.xRot, -0.3F);
            ra.zRot = Mth.lerp(k, ra.zRot, 2.3F + Mth.sin(time * 0.4F) * 0.08F);
            la.zRot = Mth.lerp(k, la.zRot, -2.3F - Mth.sin(time * 0.4F) * 0.08F);
            model.head.xRot = Mth.lerp(k, model.head.xRot, -0.45F);
            return;
        }
        float shot = e.animAge(AkashicSeerEntity.EV_SHOT, pt);                              // 観測射撃: 右手で相手を指して撃ち、撃つたびに反動
        if (shot < 14.0F) {
            float w = window(shot, 0.0F, 2.0F, 9.0F, 14.0F);
            float recoil = shot < 3.0F ? (1.0F - shot / 3.0F) * 0.3F : 0.0F;
            ra.xRot = Mth.lerp(w, ra.xRot, -1.55F + model.head.xRot + recoil);
            ra.yRot = Mth.lerp(w, ra.yRot, model.head.yRot - 0.1F);
            ra.zRot = Mth.lerp(w, ra.zRot, 0.0F);
            la.zRot = Mth.lerp(w, la.zRot, -0.35F);                                         // 左手は腰に
            la.xRot = Mth.lerp(w, la.xRot, 0.25F);
        }
        float rain = e.animAge(AkashicSeerEntity.EV_RAIN, pt);                              // データの雨: 両手を掲げてから振り下ろす
        if (rain < 30.0F) {
            float w = window(rain, 0.0F, 4.0F, 22.0F, 30.0F);
            float swing = ease(Mth.clamp((rain - 10.0F) / 5.0F, 0.0F, 1.0F));
            float x = Mth.lerp(swing, -2.9F, -0.9F);
            ra.xRot = Mth.lerp(w, ra.xRot, x);
            la.xRot = Mth.lerp(w, la.xRot, x);
            ra.zRot = Mth.lerp(w, ra.zRot, 0.25F);
            la.zRot = Mth.lerp(w, la.zRot, -0.25F);
            model.head.xRot = Mth.lerp(w, model.head.xRot, Mth.lerp(swing, -0.4F, 0.2F));
        }
        float barrier = e.animAge(AkashicSeerEntity.EV_BARRIER, pt);                        // 立方体の結界: 両腕を水平に広げて回る
        if (barrier < 24.0F) {
            float w = window(barrier, 0.0F, 3.0F, 18.0F, 24.0F);
            ra.zRot = Mth.lerp(w, ra.zRot, 1.5F);
            la.zRot = Mth.lerp(w, la.zRot, -1.5F);
            ra.xRot = Mth.lerp(w, ra.xRot, 0.0F);
            la.xRot = Mth.lerp(w, la.xRot, 0.0F);
        }
        float homing = e.animAge(AkashicSeerEntity.EV_HOMING, pt);                          // 追跡立方体: 左手を横から前へ払う
        if (homing < 18.0F) {
            float w = window(homing, 0.0F, 2.0F, 12.0F, 18.0F);
            float sweep = ease(Mth.clamp(homing / 8.0F, 0.0F, 1.0F));
            la.xRot = Mth.lerp(w, la.xRot, -1.4F);
            la.yRot = Mth.lerp(w, la.yRot, Mth.lerp(sweep, 1.1F, -0.3F));
            la.zRot = Mth.lerp(w, la.zRot, 0.0F);
            model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(sweep, 0.25F, -0.2F));
        }
        float burst = e.animAge(AkashicSeerEntity.EV_BURST, pt);                            // 全方位へ放つ: 両手を突き出してから、指揮者のように大きく広げて揺らす
        if (burst < AkashicSeerEntity.BURST_TICKS) {
            float thrust = window(burst, 0.0F, 2.0F, 8.0F, 14.0F);
            float conduct = window(burst, 8.0F, 16.0F, AkashicSeerEntity.BURST_TICKS - 12.0F, AkashicSeerEntity.BURST_TICKS);
            ra.xRot = Mth.lerp(thrust, ra.xRot, -1.5F);
            la.xRot = Mth.lerp(thrust, la.xRot, -1.5F);
            ra.zRot = Mth.lerp(thrust, ra.zRot, 0.7F);
            la.zRot = Mth.lerp(thrust, la.zRot, -0.7F);
            float sway = Mth.sin(time * 0.25F) * 0.25F;
            ra.xRot = Mth.lerp(conduct, ra.xRot, -0.5F + sway);
            la.xRot = Mth.lerp(conduct, la.xRot, -0.5F - sway);
            ra.zRot = Mth.lerp(conduct, ra.zRot, 1.9F + sway * 0.6F);
            la.zRot = Mth.lerp(conduct, la.zRot, -1.9F + sway * 0.6F);
            model.head.xRot = Mth.lerp(conduct, model.head.xRot, -0.25F);
        }
        float phase = e.animAge(AkashicSeerEntity.EV_PHASE2, pt);                           // 第 2 形態: 両腕を斜め上へ広げ、空を仰ぐ
        if (phase < 36.0F) {
            float w = window(phase, 0.0F, 6.0F, 26.0F, 36.0F);
            ra.zRot = Mth.lerp(w, ra.zRot, 2.1F);
            la.zRot = Mth.lerp(w, la.zRot, -2.1F);
            ra.xRot = Mth.lerp(w, ra.xRot, -0.2F);
            la.xRot = Mth.lerp(w, la.xRot, -0.2F);
            model.head.xRot = Mth.lerp(w, model.head.xRot, -0.5F);
            model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, 0.15F);
            model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, -0.1F);
        }
        float lance = e.animAge(AkashicSeerEntity.EV_LANCE, pt);                            // 結晶槍: 右手を掲げてから地面へ振り下ろす
        if (lance < 20.0F) {
            float w = window(lance, 0.0F, 3.0F, 14.0F, 20.0F);
            float down = ease(Mth.clamp((lance - 6.0F) / 3.0F, 0.0F, 1.0F));
            ra.xRot = Mth.lerp(w, ra.xRot, Mth.lerp(down, -2.8F, -0.5F));
            ra.zRot = Mth.lerp(w, ra.zRot, 0.1F);
            la.xRot = Mth.lerp(w, la.xRot, 0.4F);
        }
        float refract = e.animAge(AkashicSeerEntity.EV_REFRACT, pt);                        // 屈折光線: 左手で空を指し、右手を胸の前に
        if (refract < 28.0F) {
            float w = window(refract, 0.0F, 4.0F, 22.0F, 28.0F);
            la.xRot = Mth.lerp(w, la.xRot, -2.4F);
            la.zRot = Mth.lerp(w, la.zRot, -0.2F);
            ra.xRot = Mth.lerp(w, ra.xRot, -1.0F);
            ra.yRot = Mth.lerp(w, ra.yRot, -0.7F);
            model.head.xRot = Mth.lerp(w, model.head.xRot, -0.3F);
        }
        float cage = e.animAge(AkashicSeerEntity.EV_CAGE, pt);                              // 結晶の檻: 両腕を大きく広げ、閉じる時に前で手を合わせる
        if (cage < 30.0F) {
            float w = window(cage, 0.0F, 4.0F, 26.0F, 30.0F);
            float close = ease(Mth.clamp((cage - 20.0F) / 4.0F, 0.0F, 1.0F));
            ra.zRot = Mth.lerp(w, ra.zRot, Mth.lerp(close, 1.6F, 0.0F));
            la.zRot = Mth.lerp(w, la.zRot, Mth.lerp(close, -1.6F, 0.0F));
            ra.xRot = Mth.lerp(w, ra.xRot, Mth.lerp(close, 0.0F, -1.5F));
            la.xRot = Mth.lerp(w, la.xRot, Mth.lerp(close, 0.0F, -1.5F));
            ra.yRot = Mth.lerp(w, ra.yRot, Mth.lerp(close, 0.0F, -0.45F));
            la.yRot = Mth.lerp(w, la.yRot, Mth.lerp(close, 0.0F, 0.45F));
        }
        float blink = e.animAge(AkashicSeerEntity.EV_BLINK, pt);                            // 観測の瞬き: 腕を体へ寄せて回る
        if (blink < 12.0F) {
            float w = window(blink, 0.0F, 1.0F, 8.0F, 12.0F);
            ra.zRot = Mth.lerp(w, ra.zRot, 0.15F);
            la.zRot = Mth.lerp(w, la.zRot, -0.15F);
            ra.xRot = Mth.lerp(w, ra.xRot, -0.6F);
            la.xRot = Mth.lerp(w, la.xRot, -0.6F);
        }
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    /** 0 → 1 のなめらかな立ち上がり */
    static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** age が a〜b で 0 → 1、b〜c は 1、c〜d で 1 → 0 */
    static float window(float age, float a, float b, float c, float d) {
        if (age < a || age > d) return 0.0F;
        if (age < b) return ease((age - a) / (b - a));
        if (age < c) return 1.0F;
        return 1.0F - ease((age - c) / (d - c));
    }

    /** まわりを回る正二十面体の結晶（2026-10-26「前の正２０面体に置き換えて」。桃・薄紫・水色。結界の間は大きな結晶 6 つ、溜めの間は渦を巻いて胸へ寄る） */
    private static void cubes(AkashicSeerEntity e, float time, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AkashicProbeRenderer.CRYSTAL));
        int[] colors = {AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET, AkashicCubeEntity.CYAN};
        boolean p2 = e.isPhase2();
        int n = p2 ? 6 : 3;
        float speed = p2 ? 0.05F : 0.03F;
        int charge = e.chargeLeft();
        for (int i = 0; i < n; i++) {
            float k = charge > 0 ? charge / (float) AkashicSeerEntity.CHARGE_TICKS : 1.0F;
            float a = time * (charge > 0 ? 0.25F : speed + (i % 3) * 0.008F) + i * Mth.TWO_PI / n;
            float r = (0.78F + (i % 3) * 0.12F) * k + 0.15F;
            float y = 1.3F + (i % 3) * 0.27F + Mth.sin(time * 0.06F + i) * 0.08F;
            crystal(pose, vc, Mth.cos(a) * r, y, Mth.sin(a) * r, 0.13F + (i % 3) * 0.04F, colors[i % 3], time, i);
        }
        if (e.barrierLeft() > 0) {
            float r = (float) AkashicSeerEntity.ORBIT_R;
            for (int i = 0; i < 6; i++) {
                float a = time * 0.12F + i * Mth.TWO_PI / 6;
                crystal(pose, vc, Mth.cos(a) * r, 0.9F + Mth.sin(time * 0.1F + i) * 0.3F, Mth.sin(a) * r, 0.28F, colors[i % 3], time, i);
            }
        }
    }

    static void cube(PoseStack pose, VertexConsumer vc, float x, float y, float z, float size, int color, float time, int i) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(time * (2.0F + i * 0.4F)));
        pose.mulPose(Axis.XP.rotationDegrees(35.0F + Mth.sin(time * 0.04F + i) * 20.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        pose.pushPose();
        pose.scale(size, size, size);
        AkashicProbeRenderer.cube(pose, vc, color, 0xC8, true);
        pose.popPose();
        pose.scale(size * 1.3F, size * 1.3F, size * 1.3F);
        AkashicProbeRenderer.cube(pose, vc, color, 0x40, false);
        pose.popPose();
    }

    // ---- 正二十面体の結晶（size は外接球の半径） ----

    private static final float PHI = (1.0F + Mth.sqrt(5.0F)) / 2.0F;
    private static final float[][] ICO_VERTS = {
            {-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0}, {0, -1, PHI}, {0, 1, PHI},
            {0, -1, -PHI}, {0, 1, -PHI}, {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}};
    private static final int[][] ICO_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    static void crystal(PoseStack pose, VertexConsumer vc, float x, float y, float z, float size, int color, float time, int i) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(time * (2.0F + i * 0.5F)));
        pose.mulPose(Axis.XP.rotationDegrees(time * (1.3F + i * 0.4F)));
        float s = size / Mth.sqrt(1.0F + PHI * PHI);
        pose.pushPose();
        pose.scale(s, s, s);
        icosahedron(pose, vc, color, 0xB8);
        pose.popPose();
        pose.scale(s * 1.3F, s * 1.3F, s * 1.3F);                                              // まわりの薄い光の殻
        icosahedron(pose, vc, color, 0x38);
        pose.popPose();
    }

    private static void icosahedron(PoseStack pose, VertexConsumer vc, int rgb, int alpha) {
        org.joml.Matrix4f m = pose.last().pose();
        org.joml.Matrix3f normal = pose.last().normal();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int f = 0; f < ICO_FACES.length; f++) {
            float[] a = ICO_VERTS[ICO_FACES[f][0]], c = ICO_VERTS[ICO_FACES[f][1]], d = ICO_VERTS[ICO_FACES[f][2]];
            float nx = a[0] + c[0] + d[0], ny = a[1] + c[1] + d[1], nz = a[2] + c[2] + d[2];
            float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            int shade = f % 2 == 0 ? 0 : 28;                                                 // 面ごとに明暗を付けて切子に見せる
            for (float[] v : new float[][]{a, c, d, d}) {
                vc.vertex(m, v[0], v[1], v[2]).color(Math.max(0, r - shade), Math.max(0, g - shade), Math.max(0, b - shade), alpha)
                        .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, nx / len, ny / len, nz / len).endVertex();
            }
        }
    }
}
