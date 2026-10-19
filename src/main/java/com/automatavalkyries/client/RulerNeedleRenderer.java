package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.RulerNeedleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
}
