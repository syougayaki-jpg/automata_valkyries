package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.akashic.AkashicProbeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 事象観測体アカシック・プローブの描画（2026-10-25 / 2026-10-26 頭上専用観測 UI コンソール追加）。
 * 体は 2026-10-26 ユーザー指定「アカシックちゃんのモデルをマイクラ風に（見本の画像）これにして」「完全にイラストの通りにして」で、Tripo のメッシュからマイクラのプレイヤーの形（太い腕）に変えた:
 * スキンは {@code textures/entity/akashic_probe_mc.png}（{@code tools/gen_akashic_mc.py}。見本の絵の正面から色を拾う。白いフードに桃と水色の髪・黒いヘッドホン・白衣・袖口の表示・ブーツ）、
 * 目・ヘッドホンの灯り・袖口の表示・胸の印は光る（{@code _glow}）。歩く・首を振る・腕を振るはプレイヤーと同じ。
 * 周りに桃と水色の光る立方体を回し、頭上に専用の「事象観測 UI コンソール（モード・HP/不死ロック・防御/検死カウンタ・108種検証プログレス・Universal DPS 解析・リアルタイム観測ログ）」をホログラム表示する
 */
public class AkashicProbeRenderer extends EntityRenderer<AkashicProbeEntity> {

    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/akashic_probe_mc.png");
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/akashic_probe_mc_glow.png");
    static final ResourceLocation CRYSTAL = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/akashic_crystal.png");
    /** プレイヤーと同じ縮尺（高さ約 1.8 ブロック） */
    static final float SCALE = 0.9375F;
    /** 光る立方体の色（見本の桃・水色）と、回る半径・高さ・大きさ（半分の辺） */
    private static final int[] CRYSTAL_COLORS = {0xFF5AD8, 0xFF8CE8, 0x4FE3FF, 0x8EF0FF, 0x4FE3FF};
    private static final float[][] CRYSTAL_ORBITS = {{0.85F, 1.45F, 0.16F}, {0.75F, 1.05F, 0.08F}, {0.90F, 1.30F, 0.17F}, {0.70F, 0.95F, 0.07F}, {0.80F, 1.85F, 0.09F}};

    private final net.minecraft.client.model.PlayerModel<AkashicProbeEntity> model;
    /** 頭上の専用観測 UI を出す距離 */
    private static final double HOLOGRAM_RANGE = 36.0;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    public AkashicProbeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
        this.model = new net.minecraft.client.model.PlayerModel<>(createBody().bakeRoot(), false);
    }

    /**
     * プレイヤーの太い腕の形に、見本の絵の通りの箱を足す（スキンの右半分。UV は 128x64、絵は 2 倍の細かさ）:
     * 頭の左右のヘッドホンの耳当て・胴から下がる白衣の裾・袖口の表示（腕より一回り太い）・ブーツ（脚より少し太い）
     */
    static net.minecraft.client.model.geom.builders.LayerDefinition createBody() {
        var mesh = net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false);
        var root = mesh.getRoot();
        var none = net.minecraft.client.model.geom.PartPose.ZERO;
        var head = root.getChild("head");
        head.addOrReplaceChild("phone_r", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(64, 0).addBox(-6.0F, -6.0F, -2.0F, 2, 4, 4), none);
        head.addOrReplaceChild("phone_l", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(64, 8).addBox(4.0F, -6.0F, -2.0F, 2, 4, 4), none);
        root.getChild("body").addOrReplaceChild("skirt", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(64, 16).addBox(-4.5F, 12.0F, -2.5F, 9.0F, 6.0F, 5.0F), none);
        var cuff = new net.minecraft.client.model.geom.builders.CubeDeformation(0.5F);
        var boot = new net.minecraft.client.model.geom.builders.CubeDeformation(0.3F);
        root.getChild("right_arm").addOrReplaceChild("cuff", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(64, 28).addBox(-3.0F, 5.0F, -2.0F, 4.0F, 4.0F, 4.0F, cuff), none);
        root.getChild("left_arm").addOrReplaceChild("cuff", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(80, 28).addBox(-1.0F, 5.0F, -2.0F, 4.0F, 4.0F, 4.0F, cuff), none);
        root.getChild("right_leg").addOrReplaceChild("boot", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(64, 36).addBox(-2.0F, 8.0F, -2.0F, 4.0F, 4.0F, 4.0F, boot), none);
        root.getChild("left_leg").addOrReplaceChild("boot", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(80, 36).addBox(-2.0F, 8.0F, -2.0F, 4.0F, 4.0F, 4.0F, boot), none);
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(AkashicProbeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AkashicProbeEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        float bob = Mth.sin(time * 0.08F) * 0.06F;                                           // 立方体と頭上の表示だけゆっくり上下する
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);

        poseStack.pushPose();                                                                // 体（LivingEntityRenderer と同じ置き方＋ボスモーション）
        float spin = 0.0F, lean = 0.0F, lift = 0.0F;
        float barrierAge = entity.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_BARRIER, partialTick);
        if (barrierAge < 18.0F) spin = 360.0F * AkashicSeerRenderer.ease(barrierAge / 18.0F);
        float burstAge = entity.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_BURST, partialTick);
        if (burstAge < com.automatavalkyries.entity.seer.AkashicSeerEntity.BURST_TICKS) {
            lean = -14.0F * AkashicSeerRenderer.window(burstAge, 0.0F, 3.0F, 8.0F, 20.0F);
            lift += 0.5F * AkashicSeerRenderer.window(burstAge, 0.0F, 8.0F,
                    com.automatavalkyries.entity.seer.AkashicSeerEntity.BURST_TICKS - 12.0F,
                    com.automatavalkyries.entity.seer.AkashicSeerEntity.BURST_TICKS);
            spin += 360.0F * AkashicSeerRenderer.ease(burstAge / com.automatavalkyries.entity.seer.AkashicSeerEntity.BURST_TICKS);
        }
        float phaseAge = entity.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_PHASE2, partialTick);
        if (phaseAge < 36.0F) lift += 0.35F * AkashicSeerRenderer.window(phaseAge, 0.0F, 8.0F, 24.0F, 36.0F);
        if (entity.chargeLeft() > 0) lift += 0.15F + Mth.sin(time * 0.3F) * 0.03F;
        float lanceAge = entity.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_LANCE, partialTick);
        if (lanceAge < 20.0F) lean += 12.0F * AkashicSeerRenderer.window(lanceAge, 6.0F, 9.0F, 14.0F, 20.0F);
        float blinkAge = entity.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_BLINK, partialTick);
        if (blinkAge < 10.0F) spin -= 360.0F * AkashicSeerRenderer.ease(blinkAge / 10.0F);
        if (entity.hurtTime > 0) lean += 6.0F * entity.hurtTime / 10.0F;

        poseStack.translate(0.0F, lift, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw + spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(lean));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        model.attackTime = entity.getAttackAnim(partialTick);
        model.riding = false;
        model.young = false;
        model.crouching = false;
        model.setupAnim(entity, entity.walkAnimation.position(partialTick), Math.min(1.0F, entity.walkAnimation.speed(partialTick)), time,
                Mth.clamp(Mth.wrapDegrees(headYaw - yaw), -60.0F, 60.0F), Mth.clamp(Mth.lerp(partialTick, entity.xRotO, entity.getXRot()), -35.0F, 35.0F));   // 首は左右 60 度・上下 35 度まで（2026-10-26「首が回転しすぎ」）
        animateProbe(entity, time, partialTick);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(GLOW)), LIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        renderCrystals(entity, time, poseStack, buffers);
        poseStack.popPose();

        if (this.entityRenderDispatcher.distanceToSqr(entity) <= HOLOGRAM_RANGE * HOLOGRAM_RANGE) {
            renderHologram(entity, bob, poseStack, buffers);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
    }

    private void animateProbe(AkashicProbeEntity e, float time, float pt) {
        var ra = model.rightArm;
        var la = model.leftArm;
        ra.zRot += 0.06F + Mth.sin(time * 0.06F) * 0.03F;
        la.zRot -= 0.06F + Mth.sin(time * 0.06F) * 0.03F;
        model.head.zRot = Mth.sin(time * 0.05F) * 0.05F;

        if (e.chargeLeft() > 0) {
            float k = Math.min(1.0F, (com.automatavalkyries.entity.akashic.AkashicDuelEngine.CHARGE_TICKS - e.chargeLeft() + pt) / 8.0F);
            ra.xRot = Mth.lerp(k, ra.xRot, -0.3F);
            la.xRot = Mth.lerp(k, la.xRot, -0.3F);
            ra.zRot = Mth.lerp(k, ra.zRot, 2.3F + Mth.sin(time * 0.4F) * 0.08F);
            la.zRot = Mth.lerp(k, la.zRot, -2.3F - Mth.sin(time * 0.4F) * 0.08F);
            model.head.xRot = Mth.lerp(k, model.head.xRot, -0.45F);
            return;
        }
        float shot = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_SHOT, pt);
        if (shot < 14.0F) {
            float w = AkashicSeerRenderer.window(shot, 0.0F, 2.0F, 9.0F, 14.0F);
            float recoil = shot < 3.0F ? (1.0F - shot / 3.0F) * 0.3F : 0.0F;
            ra.xRot = Mth.lerp(w, ra.xRot, -1.55F + model.head.xRot + recoil);
            ra.yRot = Mth.lerp(w, ra.yRot, model.head.yRot - 0.1F);
            ra.zRot = Mth.lerp(w, ra.zRot, 0.0F);
            la.zRot = Mth.lerp(w, la.zRot, -0.35F);
            la.xRot = Mth.lerp(w, la.xRot, 0.25F);
        }
        float rain = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_RAIN, pt);
        if (rain < 30.0F) {
            float w = AkashicSeerRenderer.window(rain, 0.0F, 4.0F, 22.0F, 30.0F);
            float swing = AkashicSeerRenderer.ease(Mth.clamp((rain - 10.0F) / 5.0F, 0.0F, 1.0F));
            float x = Mth.lerp(swing, -2.9F, -0.9F);
            ra.xRot = Mth.lerp(w, ra.xRot, x);
            la.xRot = Mth.lerp(w, la.xRot, x);
            ra.zRot = Mth.lerp(w, ra.zRot, 0.25F);
            la.zRot = Mth.lerp(w, la.zRot, -0.25F);
        }
        float barrier = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_BARRIER, pt);
        if (barrier < 24.0F) {
            float w = AkashicSeerRenderer.window(barrier, 0.0F, 3.0F, 18.0F, 24.0F);
            ra.zRot = Mth.lerp(w, ra.zRot, 1.5F);
            la.zRot = Mth.lerp(w, la.zRot, -1.5F);
            ra.xRot = Mth.lerp(w, ra.xRot, 0.0F);
            la.xRot = Mth.lerp(w, la.xRot, 0.0F);
        }
        float homing = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_HOMING, pt);
        if (homing < 18.0F) {
            float w = AkashicSeerRenderer.window(homing, 0.0F, 2.0F, 12.0F, 18.0F);
            float sweep = AkashicSeerRenderer.ease(Mth.clamp(homing / 8.0F, 0.0F, 1.0F));
            la.xRot = Mth.lerp(w, la.xRot, -1.4F);
            la.yRot = Mth.lerp(w, la.yRot, Mth.lerp(sweep, 1.1F, -0.3F));
            la.zRot = Mth.lerp(w, la.zRot, 0.0F);
        }
        float lance = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_LANCE, pt);
        if (lance < 20.0F) {
            float w = AkashicSeerRenderer.window(lance, 0.0F, 3.0F, 14.0F, 20.0F);
            float down = AkashicSeerRenderer.ease(Mth.clamp((lance - 6.0F) / 3.0F, 0.0F, 1.0F));
            ra.xRot = Mth.lerp(w, ra.xRot, Mth.lerp(down, -2.8F, -0.5F));
            ra.zRot = Mth.lerp(w, ra.zRot, 0.1F);
        }
        float refract = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_REFRACT, pt);
        if (refract < 28.0F) {
            float w = AkashicSeerRenderer.window(refract, 0.0F, 4.0F, 22.0F, 28.0F);
            la.xRot = Mth.lerp(w, la.xRot, -2.4F);
            ra.xRot = Mth.lerp(w, ra.xRot, -1.0F);
            ra.yRot = Mth.lerp(w, ra.yRot, -0.7F);
        }
        float cage = e.animAge(com.automatavalkyries.entity.seer.AkashicSeerEntity.EV_CAGE, pt);
        if (cage < 30.0F) {
            float w = AkashicSeerRenderer.window(cage, 0.0F, 4.0F, 26.0F, 30.0F);
            float close = AkashicSeerRenderer.ease(Mth.clamp((cage - 20.0F) / 4.0F, 0.0F, 1.0F));
            ra.zRot = Mth.lerp(w, ra.zRot, Mth.lerp(close, 1.6F, 0.0F));
            la.zRot = Mth.lerp(w, la.zRot, Mth.lerp(close, -1.6F, 0.0F));
            ra.xRot = Mth.lerp(w, ra.xRot, Mth.lerp(close, 0.0F, -1.5F));
            la.xRot = Mth.lerp(w, la.xRot, Mth.lerp(close, 0.0F, -1.5F));
        }
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    // ---- 光る立方体・正二十面体結晶 ---------------------------------------------

    /** 立方体の 6 面（各面の 4 つの角と向き） */
    private static final float[][][] CUBE = {
            {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}, {0, -1, 0}}, {{-1, 1, 1}, {1, 1, 1}, {1, 1, -1}, {-1, 1, -1}, {0, 1, 0}},
            {{-1, -1, -1}, {-1, 1, -1}, {1, 1, -1}, {1, -1, -1}, {0, 0, -1}}, {{1, -1, 1}, {1, 1, 1}, {-1, 1, 1}, {-1, -1, 1}, {0, 0, 1}},
            {{-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}, {-1, -1, -1}, {-1, 0, 0}}, {{1, -1, -1}, {1, 1, -1}, {1, 1, 1}, {1, -1, 1}, {1, 0, 0}}};

    private static void renderCrystals(AkashicProbeEntity entity, float time, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(CRYSTAL));
        int modeOrd = entity.observationUiData().getInt("ModeOrdinal");
        boolean duelVisuals = modeOrd == 4 || modeOrd == 5 || entity.isPhase2() || entity.barrierLeft() > 0 || entity.chargeLeft() > 0;
        if (duelVisuals) {
            int[] colors = {
                    com.automatavalkyries.entity.seer.AkashicCubeEntity.PINK,
                    com.automatavalkyries.entity.seer.AkashicCubeEntity.VIOLET,
                    com.automatavalkyries.entity.seer.AkashicCubeEntity.CYAN
            };
            boolean p2 = entity.isPhase2() || modeOrd == 5;
            int n = p2 ? 8 : 5;
            float speed = p2 ? 0.055F : 0.032F;
            int charge = entity.chargeLeft();
            for (int i = 0; i < n; i++) {
                float k = charge > 0 ? charge / (float) com.automatavalkyries.entity.akashic.AkashicDuelEngine.CHARGE_TICKS : 1.0F;
                float a = time * (charge > 0 ? 0.25F : speed + (i % 3) * 0.008F) + i * Mth.TWO_PI / n;
                float r = (0.80F + (i % 3) * 0.12F) * k + 0.15F;
                float y = 1.3F + (i % 3) * 0.25F + Mth.sin(time * 0.06F + i) * 0.08F;
                AkashicSeerRenderer.crystal(poseStack, consumer, Mth.cos(a) * r, y, Mth.sin(a) * r,
                        0.14F + (i % 3) * 0.04F, colors[i % 3], time, i);
            }
            if (entity.barrierLeft() > 0) {
                float r = (float) com.automatavalkyries.entity.akashic.AkashicDuelEngine.ORBIT_R;
                for (int i = 0; i < 8; i++) {
                    float a = time * 0.14F + i * Mth.TWO_PI / 8;
                    AkashicSeerRenderer.crystal(poseStack, consumer,
                            Mth.cos(a) * r, 0.9F + Mth.sin(time * 0.1F + i) * 0.3F, Mth.sin(a) * r,
                            0.28F, colors[i % 3], time, i);
                }
            }
            return;
        }
        for (int i = 0; i < CRYSTAL_COLORS.length; i++) {
            float[] orbit = CRYSTAL_ORBITS[i];
            float angle = time * (0.022F + i * 0.006F) + i * Mth.TWO_PI / CRYSTAL_COLORS.length;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * orbit[0], orbit[1] + Mth.sin(time * 0.06F + i) * 0.08F, Mth.sin(angle) * orbit[0]);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (1.6F + i * 0.5F)));
            poseStack.mulPose(Axis.XP.rotationDegrees(35.0F + Mth.sin(time * 0.03F + i) * 20.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
            float s = orbit[2] * (1.0F + Mth.sin(time * 0.15F + i * 1.7F) * 0.06F);           // 脈打つ
            poseStack.pushPose();
            poseStack.scale(s, s, s);
            cube(poseStack, consumer, CRYSTAL_COLORS[i], 0xC8, true);
            poseStack.popPose();
            poseStack.scale(s * 1.25F, s * 1.25F, s * 1.25F);                                  // まわりの薄い光の殻
            cube(poseStack, consumer, CRYSTAL_COLORS[i], 0x40, false);
            poseStack.popPose();
        }
    }

    static void cube(PoseStack poseStack, VertexConsumer consumer, int rgb, int alpha, boolean shade) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        for (int f = 0; f < CUBE.length; f++) {
            float[][] q = CUBE[f];
            int dim = shade ? (f == 1 ? 0 : f == 0 ? 60 : f % 2 == 0 ? 25 : 40) : 0;           // 上が明るく、横・下は少し暗い（マイクラの箱の陰）
            for (int k = 0; k < 4; k++) {
                consumer.vertex(pose, q[k][0], q[k][1], q[k][2]).color(Math.max(0, r - dim), Math.max(0, g - dim), Math.max(0, b - dim), alpha)
                        .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, q[4][0], q[4][1], q[4][2]).endVertex();
            }
        }
    }

    // ---- 頭上専用観測 UI コンソール (Overhead Observation Console) ----------------

    private void renderHologram(AkashicProbeEntity entity, float bob, PoseStack poseStack, MultiBufferSource buffers) {
        Font font = getFont();
        CompoundTag ui = entity.observationUiData();
        List<Component> feedLines = entity.hologramLines();

        int modeOrdinal = ui.contains("ModeOrdinal") ? ui.getInt("ModeOrdinal") : 0;
        String modeLabel = ui.getString("ModeLabel");
        if (modeLabel.isEmpty()) {
            modeLabel = AkashicProbeEntity.Mode.OBSERVE_STANDBY.displayLabel;
        }
        String lexiconSummary = ui.getString("LexiconSummary");
        float hp = ui.contains("Hp") ? ui.getFloat("Hp") : 100.0F;
        float maxHp = ui.contains("MaxHp") ? Math.max(1.0F, ui.getFloat("MaxHp")) : 100.0F;
        boolean keepFullHp = !ui.contains("KeepFullHp") || ui.getBoolean("KeepFullHp");

        long refusedCount = ui.getLong("RefusedCount");
        long repairedCount = ui.getLong("RepairedCount");
        long detectedCount = ui.getLong("DetectedCount");
        long dmgReceivedCount = ui.getLong("DamageReceivedCount");
        long totalEvents = ui.getLong("TotalEvents");
        String suspectMod = ui.getString("SuspectMod");
        if (suspectMod.isEmpty()) suspectMod = "none";

        String targetName = ui.getString("TargetName");
        String targetMod = ui.getString("TargetMod");
        float targetHp = ui.getFloat("TargetHp");
        float targetMaxHp = Math.max(1.0F, ui.getFloat("TargetMaxHp"));
        int stageProgress = ui.getInt("StageProgress");
        int stageTotal = ui.getInt("StageTotal");
        String lastStageCode = ui.getString("LastStageCode");
        String lastStageOutcome = ui.getString("LastStageOutcome");
        String lastVerdictRank = ui.getString("LastVerdictRank");

        double dpsEff = ui.getDouble("DpsEffective");
        double dpsEwma = ui.getDouble("DpsEwma");
        double dpsEttk = ui.getDouble("DpsEttk");
        float detectedFloor = ui.getFloat("DetectedFloor");
        float detectedCap = ui.getFloat("DetectedCap");
        String bestChannel = ui.getString("BestChannel");

        int accentColor = modeAccentColor(modeOrdinal);
        Component titleComp = Component.literal("◈ ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("akashic.automata_valkyries.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" ◈").withStyle(ChatFormatting.AQUA));

        String modeText = "MODE: " + modeLabel + (lexiconSummary.isEmpty() ? "" : "  [辞書: " + lexiconSummary + "]");
        String hpBadge = keepFullHp ? " [完全不死ロック: ON]" : " [上限解除・実戦]";
        String hpText = String.format(Locale.ROOT, "HP: %.1f / %.1f%s", hp, maxHp, hpBadge);

        Component countersComp = Component.literal("遮断:")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(refusedCount)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" | 修復:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(repairedCount)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | 検知:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(detectedCount)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | 被弾:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(dmgReceivedCount)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | 干渉MOD:").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(suspectMod).withStyle(ChatFormatting.LIGHT_PURPLE));

        boolean showTargetSection = !targetName.isEmpty() || modeOrdinal > 0 || !lastVerdictRank.isEmpty();
        int penaltyTier = ui.getInt("PenaltyTier");
        int maxPenaltyTier = Math.max(1, ui.contains("MaxPenaltyTier") ? ui.getInt("MaxPenaltyTier") : 7);
        String penaltyTierLabel = ui.getString("PenaltyTierLabel");
        int penaltyFails = ui.getInt("PenaltyFails");
        int penaltyFailThreshold = Math.max(1, ui.contains("PenaltyFailThreshold") ? ui.getInt("PenaltyFailThreshold") : 8);
        String viciousLabel = ui.getString("ViciousLabel");
        int viciousWeight = ui.getInt("ViciousWeight");
        float breakthroughGauge = ui.getFloat("BreakthroughGauge");
        float adaptAttackMult = ui.contains("AdaptAttackMult") ? ui.getFloat("AdaptAttackMult") : 1.0F;
        float adaptArmorBonus = ui.getFloat("AdaptArmorBonus");

        String targetLine = "";
        String auditLine = "";
        String penaltyLine = "";
        String verdictLine = "";
        String dpsLine = "";
        if (showTargetSection) {
            if (!targetName.isEmpty()) {
                targetLine = String.format(Locale.ROOT, "対象: %s [%s]  HP: %.1f / %.1f",
                        targetName, targetMod.isEmpty() ? "vanilla" : targetMod, targetHp, targetMaxHp);
            } else {
                targetLine = "対象: 索敵・待機中 (半径16m内の検証対象または攻撃者を自動捕捉)";
            }
            if (stageTotal > 0 && (stageProgress > 0 || modeOrdinal == 1 || modeOrdinal == 2 || modeOrdinal == 3)) {
                auditLine = String.format(Locale.ROOT, "段階検証: [%d / %d] %s",
                        stageProgress, stageTotal,
                        lastStageCode.isEmpty() ? "待機中" : (lastStageCode + " -> " + lastStageOutcome));
            }
            if (modeOrdinal == 4 || modeOrdinal == 5 || penaltyTier > 0 || viciousWeight > 0) {
                String tierStr = penaltyTierLabel.isEmpty() ? ("Tier " + penaltyTier) : penaltyTierLabel;
                String vicStr = viciousLabel.isEmpty() ? "Lv0" : viciousLabel;
                penaltyLine = String.format(Locale.ROOT,
                        "対決ペナルティ: %s [耐性%d/%d] | 凶悪感知:%s(w=%d) | 突破率:%.0f%%(攻×%.2f 防+%.1f)",
                        tierStr, penaltyFails, penaltyFailThreshold, vicStr, viciousWeight,
                        breakthroughGauge, adaptAttackMult, adaptArmorBonus);
            }
            if (!lastVerdictRank.isEmpty()) {
                verdictLine = "★ 耐性判定ランク: 【" + lastVerdictRank + "】";
            }
            if (dpsEff > 0.0D || detectedFloor > 0.0F || detectedCap > 0.0F) {
                String ettkStr = dpsEttk <= 0.0D ? (targetHp <= 0.0F ? "0.0s" : "∞")
                        : String.format(Locale.ROOT, "%.1fs", dpsEttk);
                String extra = "";
                if (detectedFloor > 0.0F) extra += String.format(Locale.ROOT, " Floor:%.1f", detectedFloor);
                if (detectedCap > 0.0F) extra += String.format(Locale.ROOT, " Cap:%.1f", detectedCap);
                dpsLine = String.format(Locale.ROOT, "DPS解析: 実効 %.1f/s | 瞬間 %.1f/s | ETTK:%s | 経路:%s%s",
                        dpsEff, dpsEwma, ettkStr, bestChannel.isEmpty() ? "auto" : bestChannel, extra);
            }
        }

        String feedHeader = String.format(Locale.ROOT, "▼ リアルタイム事象観測ログ (累計観測: %d件)", totalEvents);
        List<Component> displayFeed = new ArrayList<>(feedLines);
        if (displayFeed.isEmpty()) {
            displayFeed.add(Component.literal("待機中 — 攻撃・即死技術・メモリ改ざんを自動観測します").withStyle(ChatFormatting.GRAY));
        }

        // パネル幅と高さの算出
        int contentWidth = 228;
        contentWidth = Math.max(contentWidth, font.width(titleComp));
        contentWidth = Math.max(contentWidth, font.width(modeText));
        contentWidth = Math.max(contentWidth, font.width(countersComp));
        if (!targetLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(targetLine));
        if (!auditLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(auditLine));
        if (!penaltyLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(penaltyLine) + 4);
        if (!verdictLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(verdictLine));
        if (!dpsLine.isEmpty()) contentWidth = Math.max(contentWidth, font.width(dpsLine));
        for (Component c : displayFeed) {
            contentWidth = Math.max(contentWidth, font.width(c) + 6);
        }

        float padX = 7.0F;
        float halfW = contentWidth / 2.0F + padX;
        float leftX = -halfW + padX;
        float barWidth = halfW * 2.0F - padX * 2.0F;

        int rowH = 10;
        float totalHeight = 6.0F // 上下余白
                + 13.0F          // タイトルリボン
                + 11.0F          // モード表示行
                + 12.0F          // HP バー＋数値行
                + 11.0F          // 防御・検死カウンタ行
                + (!targetLine.isEmpty() ? 12.0F : 0.0F)
                + (!auditLine.isEmpty() ? 12.0F : 0.0F)
                + (!penaltyLine.isEmpty() ? 12.0F : 0.0F)
                + (!verdictLine.isEmpty() ? 11.0F : 0.0F)
                + (!dpsLine.isEmpty() ? 11.0F : 0.0F)
                + 12.0F          // ログ見出し行
                + displayFeed.size() * rowH;

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 0.62F + bob, 0.0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.0175F, -0.0175F, 0.0175F);
        Matrix4f m = poseStack.last().pose();

        float yTop = -totalHeight;
        float yBot = 0.0F;

        // 1. パネル背景・発光外枠・四隅のサイバーブラケット
        drawPanelFrame(m, buffers, -halfW, yTop, halfW, yBot, accentColor);

        float curY = yTop + 3.0F;

        // 2. タイトルヘッダーリボン
        quad(m, buffers, -halfW + 1.5F, curY - 1.0F, halfW - 1.5F, curY + 10.5F, 0.015F, 0xE00C213B);
        quad(m, buffers, -halfW + 1.5F, curY - 1.0F, -halfW + 4.0F, curY + 10.5F, 0.008F, accentColor);
        quad(m, buffers, halfW - 4.0F, curY - 1.0F, halfW - 1.5F, curY + 10.5F, 0.008F, accentColor);
        drawCenteredText(font, titleComp.getVisualOrderText(), 0.0F, curY + 1.0F, 0xFFFFFFFF, m, buffers);
        curY += 13.0F;

        // 3. 稼働モード＆カスタム辞書ステータス行
        drawLeftText(font, Component.literal(modeText).getVisualOrderText(), leftX, curY, accentColor, m, buffers);
        curY += 11.0F;

        // 4. HP ゲージバー ＆ 不死ロック状態
        float hpRatio = Mth.clamp(hp / maxHp, 0.0F, 1.0F);
        int hpBarColor = keepFullHp ? 0xFF28E09E : (hpRatio > 0.35F ? 0xFF42B8FF : 0xFFFF5252);
        drawBar(m, buffers, leftX, curY, barWidth, 9.5F, hpRatio, 0xCC121E30, (hpBarColor & 0x00FFFFFF) | 0x88000000);
        drawCenteredText(font, Component.literal(hpText).getVisualOrderText(), 0.0F, curY + 1.0F, 0xFFFFFFFF, m, buffers);
        curY += 12.0F;

        // 5. 防御・検死カウンタ行
        drawLeftText(font, countersComp.getVisualOrderText(), leftX, curY, 0xFFFFFFFF, m, buffers);
        curY += 11.0F;

        // 6. 対峙ターゲット・段階検証プログレス・ペナルティ＆凶悪感知・判定ランク・Universal DPS 解析セクション
        if (showTargetSection) {
            quad(m, buffers, -halfW + 3.0F, curY - 1.5F, halfW - 3.0F, curY - 1.0F, 0.012F, (accentColor & 0x00FFFFFF) | 0x60000000);
            if (!targetLine.isEmpty()) {
                float tRatio = !targetName.isEmpty() ? Mth.clamp(targetHp / targetMaxHp, 0.0F, 1.0F) : 0.0F;
                drawBar(m, buffers, leftX, curY, barWidth, 9.5F, tRatio, 0xAA18122B, 0x66D94862);
                drawLeftText(font, Component.literal(targetLine).getVisualOrderText(), leftX + 2.0F, curY + 1.0F, 0xFFFFE6F0, m, buffers);
                curY += 12.0F;
            }
            if (!auditLine.isEmpty()) {
                float sRatio = stageTotal > 0 ? Mth.clamp((float) stageProgress / (float) stageTotal, 0.0F, 1.0F) : 0.0F;
                drawBar(m, buffers, leftX, curY, barWidth, 9.5F, sRatio, 0xAA142030, 0x77D488FF);
                drawLeftText(font, Component.literal(auditLine).getVisualOrderText(), leftX + 2.0F, curY + 1.0F, 0xFFF2E6FF, m, buffers);
                curY += 12.0F;
            }
            if (!penaltyLine.isEmpty()) {
                float pRatio = Mth.clamp((float) penaltyTier / (float) maxPenaltyTier, 0.0F, 1.0F);
                int pColor = penaltyTier >= 5 ? 0x88FF3870 : (viciousWeight > 0 ? 0x88FF9E3D : 0x774FC3FF);
                drawBar(m, buffers, leftX, curY, barWidth, 9.5F, pRatio, 0xAA1C1228, pColor);
                drawLeftText(font, Component.literal(penaltyLine).getVisualOrderText(), leftX + 2.0F, curY + 1.0F, 0xFFFFE0FA, m, buffers);
                curY += 12.0F;
            }
            if (!verdictLine.isEmpty()) {
                quad(m, buffers, leftX, curY - 0.5F, leftX + barWidth, curY + 9.0F, 0.014F, 0xCC2C1642);
                drawLeftText(font, Component.literal(verdictLine).getVisualOrderText(), leftX + 2.0F, curY + 0.5F, 0xFFFFD84F, m, buffers);
                curY += 11.0F;
            }
            if (!dpsLine.isEmpty()) {
                drawLeftText(font, Component.literal(dpsLine).getVisualOrderText(), leftX, curY, 0xFF7CF5FF, m, buffers);
                curY += 11.0F;
            }
        }

        // 7. リアルタイム事象観測ログ見出し＋フィード一覧
        quad(m, buffers, -halfW + 2.0F, curY - 0.5F, halfW - 2.0F, curY + 9.5F, 0.015F, 0xD00D1D33);
        drawLeftText(font, Component.literal(feedHeader).getVisualOrderText(), leftX, curY + 1.0F, 0xFF7CE8FF, m, buffers);
        curY += 12.0F;

        for (int i = 0; i < displayFeed.size(); i++) {
            if (i % 2 == 0) {
                quad(m, buffers, -halfW + 2.5F, curY - 0.5F, halfW - 2.5F, curY + rowH - 1.0F, 0.018F, 0x480E1E36);
            }
            Component line = displayFeed.get(i);
            drawLeftText(font, line.getVisualOrderText(), leftX + 1.0F, curY, 0xFFFFFFFF, m, buffers);
            curY += rowH;
        }

        poseStack.popPose();
    }

    private static int modeAccentColor(int modeOrdinal) {
        return switch (modeOrdinal) {
            case 1 -> 0xFFFFD84F; // ② ダメージ検証: ゴールドイエロー
            case 2 -> 0xFFE07CFF; // ③ 即死検証: ライトパープル
            case 3 -> 0xFFFF9E3D; // ④ 全108種検証: アンバーゴールド
            case 4 -> 0xFFFF4F5E; // ⑤ 直接対決（上限解除）: クリムゾンレッド
            case 5 -> 0xFFC84FFF; // ⑥ 直接対決（ペナルティなし最強）: ネオンバイオレット
            default -> 0xFF4FE3FF; // ① 観測待機: アクアシアン
        };
    }

    private static void drawPanelFrame(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, int accent) {
        // 半透明ダークガラス背景
        quad(m, buffers, x0, y0, x1, y1, 0.025F, 0xCC060C18);
        // 外周の細い発光ライン
        int border = (accent & 0x00FFFFFF) | 0x80000000;
        quad(m, buffers, x0, y0, x1, y0 + 0.7F, 0.010F, border);
        quad(m, buffers, x0, y1 - 0.7F, x1, y1, 0.010F, border);
        quad(m, buffers, x0, y0, x0 + 0.7F, y1, 0.010F, border);
        quad(m, buffers, x1 - 0.7F, y0, x1, y1, 0.010F, border);
        // 四隅のサイバーブラケット強調
        float c = 6.0F, t = 1.4F;
        quad(m, buffers, x0, y0, x0 + c, y0 + t, 0.004F, accent);
        quad(m, buffers, x0, y0, x0 + t, y0 + c, 0.004F, accent);
        quad(m, buffers, x1 - c, y0, x1, y0 + t, 0.004F, accent);
        quad(m, buffers, x1 - t, y0, x1, y0 + c, 0.004F, accent);
        quad(m, buffers, x0, y1 - t, x0 + c, y1, 0.004F, accent);
        quad(m, buffers, x0, y1 - c, x0 + t, y1, 0.004F, accent);
        quad(m, buffers, x1 - c, y1 - t, x1, y1, 0.004F, accent);
        quad(m, buffers, x1 - t, y1 - c, x1, y1, 0.004F, accent);
    }

    private static void drawBar(Matrix4f m, MultiBufferSource buffers, float x, float y, float w, float h, float ratio, int trackColor, int fillColor) {
        quad(m, buffers, x, y, x + w, y + h, 0.016F, trackColor);
        float fill = w * Mth.clamp(ratio, 0.0F, 1.0F);
        if (fill > 0.0F) {
            quad(m, buffers, x, y, x + fill, y + h, 0.012F, fillColor);
            quad(m, buffers, x + Math.max(0.0F, fill - 1.0F), y, x + fill, y + h, 0.008F, 0xCCFFFFFF);
        }
    }

    private static void quad(Matrix4f m, MultiBufferSource buffers, float x0, float y0, float x1, float y1, float z, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(LIGHT).endVertex();
    }

    private static void drawCenteredText(Font font, FormattedCharSequence text, float cx, float y, int color, Matrix4f m, MultiBufferSource buffers) {
        font.drawInBatch(text, cx - font.width(text) / 2.0F, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }

    private static void drawLeftText(Font font, FormattedCharSequence text, float x, float y, int color, Matrix4f m, MultiBufferSource buffers) {
        font.drawInBatch(text, x, y, color, false, m, buffers, Font.DisplayMode.NORMAL, 0, LIGHT);
    }
}
