package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.RulerNeedleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** タイムルーラーの時空剪定の針（2026-10-19。切っ先を +Y に作ったモデル tools/gen_chrono_base.py を、狙う向きへ回して描く。光る所は *_glowmask.png） */
public class RulerNeedleRenderer extends GeoEntityRenderer<RulerNeedleEntity> {

    public RulerNeedleRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "ruler_needle"), false));
        this.shadowRadius = 0.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected void applyRotations(RulerNeedleEntity entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        Vec3 d = entity.aim();
        double len = d.length();
        if (len < 1.0e-5) return;
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
        pose.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(d.y / len, -1.0, 1.0))));
        if (entity.holding()) pose.mulPose(Axis.YP.rotation(ageInTicks * 0.3F)); // 止まっている間は切っ先を軸に回る
    }

    /** 光の尾（2026-10-19「攻撃も自作の演出で超豪華に」）: 飛ぶ間は後ろへ金の帯と白い芯、止まっている間は切っ先の前に狙いの細い線 */
    @Override
    public void render(RulerNeedleEntity needle, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(needle, yaw, pt, pose, buffers, light);
        Vec3 d = needle.aim();
        if (d.lengthSqr() < 1.0e-8) return;
        d = d.normalize();
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(needle.getPosition(pt));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 mid = new Vec3(0, needle.getBbHeight() / 2.0, 0);
        if (needle.holding()) {
            float blink = 0.5F + 0.5F * Mth.sin((needle.tickCount + pt) * 0.8F);
            RulerFxRenderer.ribbon(vc, m, mid, mid.add(d.scale(6.0)), cam, 0.04F, 255, 120, 90, (int) (90 * blink));
            return;
        }
        Vec3 tail = mid.subtract(d.scale(5.0));
        RulerFxRenderer.ribbon(vc, m, mid, tail, cam, 0.7F, 255, 200, 90, 70);
        RulerFxRenderer.ribbon(vc, m, mid, mid.subtract(d.scale(3.5)), cam, 0.28F, 255, 236, 170, 170);
        RulerFxRenderer.ribbon(vc, m, mid, mid.subtract(d.scale(2.0)), cam, 0.1F, 255, 255, 255, 230);
    }
}
