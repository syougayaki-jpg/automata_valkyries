package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.item.unit.ValkyrieUnits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * メメント・ファミリア（背部ユニット）の 8 匹の紫の蝶のビット（2026-10-06）。見た目だけ（クライアントで時間から計算）。
 * 兵姫のまわりを高さを変えて回り、上下に揺れながら羽ばたく。羽はテクスチャ（{@code memento_butterfly.png}: 左半分が羽、右半分の中央が胴）の板 2 枚
 */
final class MementoButterflies {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/unit/memento_butterfly.png");
    private static final int COUNT = 8;
    /** 羽 1 枚の幅・前後の長さ（ブロック） */
    private static final float WING = 0.13F, LENGTH = 0.15F;

    private MementoButterflies() {}

    static void render(ValkyrieEntity entity, PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        if (!(entity.unitItem(ValkyrieInventory.BACKPACK).getItem() instanceof ValkyrieUnits.BackpackItem b)
                || b.kind != ValkyrieUnits.BackpackKind.MEMENTO_FAMILIAR) return;
        float time = entity.tickCount + partialTick;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float scale = entity.getBbHeight() / 1.7F; // 体の大きさに合わせる
        for (int i = 0; i < COUNT; i++) {
            float phase = i * Mth.TWO_PI / COUNT;
            float a = (time * 0.045F) % Mth.TWO_PI + phase;
            float r = (0.95F + 0.15F * Mth.sin(time * 0.03F + i * 1.7F)) * scale;
            float y = (0.45F + (i % 4) * 0.32F + 0.12F * Mth.sin(time * 0.08F + i * 2.1F)) * scale;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(a) * r, y, Mth.sin(a) * r);
            poseStack.mulPose(Axis.YP.rotation(-a));                                  // 進む向き（円の接線）へ
            poseStack.mulPose(Axis.ZP.rotation(-0.35F));                              // 内へ少し傾ける
            poseStack.mulPose(Axis.XP.rotation(0.25F * Mth.sin(time * 0.08F + i * 2.1F + 1.3F)));  // 上下の揺れに合わせて機首を振る
            float flap = (float) Math.toRadians(15.0 + 55.0 * (0.5 + 0.5 * Mth.sin(time * 1.3F + i * 0.9F)));
            Matrix4f pose;
            Matrix3f normal;
            // 左右の羽（付け根が胴の線 x = 0。+x の羽は Z 軸の回りに上へ、-x の羽は逆へ）
            for (int side = -1; side <= 1; side += 2) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.ZP.rotation(side * flap));
                pose = poseStack.last().pose();
                normal = poseStack.last().normal();
                float x = side * WING;
                vertex(vc, pose, normal, 0, 0, LENGTH / 2, 0.0F, 0.0F);
                vertex(vc, pose, normal, x, 0, LENGTH / 2, 0.5F, 0.0F);
                vertex(vc, pose, normal, x, 0, -LENGTH / 2, 0.5F, 1.0F);
                vertex(vc, pose, normal, 0, 0, -LENGTH / 2, 0.0F, 1.0F);
                poseStack.popPose();
            }
            // 胴（縦の細い板）
            pose = poseStack.last().pose();
            normal = poseStack.last().normal();
            float h = 0.012F;
            vertex(vc, pose, normal, 0, h, LENGTH * 0.45F, 0.72F, 0.125F);
            vertex(vc, pose, normal, 0, -h, LENGTH * 0.45F, 0.78F, 0.125F);
            vertex(vc, pose, normal, 0, -h, -LENGTH * 0.45F, 0.78F, 0.875F);
            vertex(vc, pose, normal, 0, h, -LENGTH * 0.45F, 0.72F, 0.875F);
            poseStack.popPose();
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        vc.vertex(pose, x, y, z).color(1.0F, 1.0F, 1.0F, 0.92F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
