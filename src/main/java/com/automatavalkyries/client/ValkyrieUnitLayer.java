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
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 着けた外装フレーム・背部ユニットを兵姫の骨に重ねて描く（2026-10-06）。
 * 形は {@code geo/entity/unit/<frame_型_部位 | backpack_種類>.geo.json}（{@code tools/gen_valkyrie_units.py}）。座標は兵姫のモデルと同じで、
 * 一番上の骨の名前（head / body / *_arm / *_leg）の兵姫の骨を描く途中で、その骨の向きのまま描く（動きに付いて来る）。
 * テクスチャは全ユニット共通の見本帳 1 枚と発光マスク（{@link RenderType#eyes}）。
 * 子の骨の名前: {@code fx_*} は発光だけ、{@code spin_z_* / spin_y_*} は時間で回る（ghost 付きは残像を重ねる）。
 * HUD の顔（{@link ValkyrieRenderer#portraitPass}）では外装だけ描き、背部は描かない。透明なら何も描かない
 */
public class ValkyrieUnitLayer extends GeoRenderLayer<ValkyrieEntity> {
    static final ResourceLocation TEXTURE = rl("textures/entity/unit/valkyrie_units.png");
    static final ResourceLocation GLOWMASK = rl("textures/entity/unit/valkyrie_units_glowmask.png");
    private static final int[] SLOTS = {ValkyrieInventory.HEAD, ValkyrieInventory.CHEST, ValkyrieInventory.LEGS, ValkyrieInventory.BACKPACK};
    private static final Map<String, ResourceLocation> MODEL_IDS = new HashMap<>();

    public ValkyrieUnitLayer(GeoRenderer<ValkyrieEntity> renderer) {
        super(renderer);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path);
    }

    /** 物の見た目のモデル名（無ければ null） */
    static String modelName(ItemStack stack) {
        if (stack.getItem() instanceof ValkyrieUnits.FrameItem f) return "frame_" + f.type.id + "_" + f.part.id;
        if (stack.getItem() instanceof ValkyrieUnits.BackpackItem b) return "backpack_" + b.kind.id;
        return null;
    }

    static BakedGeoModel model(ItemStack stack) {
        String name = modelName(stack);
        if (name == null) return null;
        ResourceLocation id = MODEL_IDS.computeIfAbsent(name, n -> rl("geo/entity/unit/" + n + ".geo.json"));
        return GeckoLibCache.getBakedModels().get(id); // 読み直し（F3+T）後も引き直す
    }

    private static boolean attachBone(String name) {
        return switch (name) {
            case "head", "body", "right_arm", "left_arm", "right_leg", "left_leg" -> true;
            default -> false;
        };
    }

    @Override
    public void renderForBone(PoseStack poseStack, ValkyrieEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (animatable.isInvisible() || bone.isHidden() || !attachBone(bone.getName())) return;
        List<GeoBone> parts = null;
        for (int slot : SLOTS) {
            if (slot == ValkyrieInventory.BACKPACK && ValkyrieRenderer.portraitPass) continue; // 顔のアイコンには背部を出さない
            BakedGeoModel m = model(animatable.unitItem(slot));
            if (m == null) continue;
            GeoBone part = m.getBone(bone.getName()).orElse(null);
            if (part == null) continue;
            if (parts == null) parts = new ArrayList<>(4);
            parts.add(part);
        }
        if (parts == null) return;
        float time = animatable.tickCount + partialTick;
        // 地の色（光の当たり方は兵姫と同じ）→ 発光（明滅は少しだけ）。最後に兵姫の描き方のバッファへ戻す（GeckoLib は骨を描く途中でこの層を呼ぶ）
        VertexConsumer solid = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (GeoBone p : parts) renderPart(poseStack, p, solid, packedLight, packedOverlay, 1F, time, false);
        VertexConsumer glow = bufferSource.getBuffer(RenderType.eyes(GLOWMASK));
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.15F);
        for (GeoBone p : parts) renderPart(poseStack, p, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pulse, time, true);
        bufferSource.getBuffer(renderType);
    }

    /** 骨の箱と子の骨（回した部品・回る部品）。一番上の骨は兵姫の骨の向きのまま（自分の向きは持たない） */
    private void renderPart(PoseStack poseStack, GeoBone part, VertexConsumer buffer, int light, int overlay, float bright, float time, boolean glowPass) {
        String name = part.getName();
        boolean fx = name.startsWith("fx_");
        if (fx && !glowPass) return; // 噴射炎などは発光だけ
        float b = fx ? bright * (0.75F + 0.25F * Mth.sin(time * 1.7F)) : bright;
        getRenderer().renderCubesOfBone(poseStack, part, buffer, light, overlay, b, b, b, 1F);
        for (GeoBone child : part.getChildBones()) {
            poseStack.pushPose();
            animate(child, time);
            RenderUtils.prepMatrixForBone(poseStack, child);
            renderPart(poseStack, child, buffer, light, overlay, bright, time, glowPass);
            poseStack.popPose();
            if (glowPass && child.getName().contains("ghost")) renderGhost(poseStack, child, buffer, bright, time);
        }
    }

    /** 回る部品の残像（少し遅れた角度・少し大きく・暗く）。発光の描き方は足し算なので暗くすると薄く見える */
    private void renderGhost(PoseStack poseStack, GeoBone child, VertexConsumer buffer, float bright, float time) {
        for (int k = 1; k <= 2; k++) {
            poseStack.pushPose();
            RenderUtils.translateMatrixToBone(poseStack, child);
            RenderUtils.translateToPivotPoint(poseStack, child);
            poseStack.mulPose(Axis.ZP.rotation(child.getRotZ() - 0.22F * k));
            float s = 1.0F + 0.06F * k;
            poseStack.scale(s, s, 1.0F);
            RenderUtils.translateAwayFromPivotPoint(poseStack, child);
            renderGhostCubes(poseStack, child, buffer, bright * (0.5F / k));
            poseStack.popPose();
        }
    }

    private void renderGhostCubes(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, float b) {
        getRenderer().renderCubesOfBone(poseStack, bone, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, b, b, b, 1F);
        for (GeoBone c : bone.getChildBones()) {
            poseStack.pushPose();
            RenderUtils.prepMatrixForBone(poseStack, c);
            renderGhostCubes(poseStack, c, buffer, b);
            poseStack.popPose();
        }
    }

    /**
     * 骨の geo の初期の向き（初めて描くときに覚える）。この部品の模型は GeckoLib のアニメーションを通らないので
     * {@code getInitialSnapshot()} が null のまま（2026-10-06、それで落ちていた）
     */
    private static final java.util.Map<GeoBone, float[]> BASE_ROT = new java.util.WeakHashMap<>();

    /** 名前で決まる動き（geo の初期の向きに足す） */
    private static void animate(GeoBone bone, float time) {
        String n = bone.getName();
        if (!n.startsWith("spin_z_") && !n.startsWith("spin_y_")) return;
        float[] base = BASE_ROT.computeIfAbsent(bone, b -> new float[]{b.getRotX(), b.getRotY(), b.getRotZ()});
        if (n.startsWith("spin_z_")) {
            float speed = n.contains("inner") ? -0.11F : 0.06F; // 外の輪と内の輪は逆へ
            bone.setRotZ(base[2] + (time * speed) % Mth.TWO_PI);
        } else {
            bone.setRotY(base[1] + (time * 0.035F) % Mth.TWO_PI);
        }
    }
}
