package com.automatavalkyries.client;

import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** データの立方体（{@link AkashicCubeEntity}）: 回りながら飛ぶ光る立方体と、その薄い光の殻。事象観測姫アカシックの弾は正二十面体の結晶 */
public class AkashicCubeRenderer extends EntityRenderer<AkashicCubeEntity> {

    public AkashicCubeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AkashicCubeEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        var vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(AkashicProbeRenderer.CRYSTAL));
        float time = (e.tickCount + pt) * 6.0F;
        if (e.isCrystal()) AkashicSeerRenderer.crystal(pose, vc, 0.0F, 0.0F, 0.0F, e.size() * 1.5F, e.color(), time, e.getId() % 5);   // ボスの弾は正二十面体
        else AkashicSeerRenderer.cube(pose, vc, 0.0F, 0.0F, 0.0F, e.size(), e.color(), time, e.getId() % 5);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AkashicCubeEntity e) {
        return AkashicProbeRenderer.CRYSTAL;
    }
}
