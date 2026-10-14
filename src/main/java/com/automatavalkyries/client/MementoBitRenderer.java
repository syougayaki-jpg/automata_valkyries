package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoBitEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 黒死蝶ビットの描き方: 淡い紫の光の翅（2026-10-14 に黒から）に紫（第 2 形態は光体の水色と桃色）の光る縁・菱形の放射口の機械の蝶が、飛ぶ向きを向いて羽ばたく。突っ込む間は翅をたたんで速く震わせる */
public class MementoBitRenderer extends EntityRenderer<MementoBitEntity> {

    public MementoBitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MementoBitEntity bit, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = bit.tickCount + partialTick;
        Vec3 v = new Vec3(bit.getX() - bit.xo, bit.getY() - bit.yo, bit.getZ() - bit.zo);
        Vec3 fwd = v.lengthSqr() > 1.0e-5 ? v : new Vec3(Mth.cos(age * 0.05F), 0, Mth.sin(age * 0.05F));
        boolean charging = bit.isCharging();
        float flap = charging ? 0.9F + 0.15F * Mth.sin(age * 3.0F) : Mth.sin(age * 0.9F + bit.getId()) * 0.85F;
        float fadeIn = Math.min(1.0F, age / 5.0F);
        MementoFx.butterfly(buffers, pose.last().pose(), new Vec3(0, 0.25, 0), fwd, new Vec3(0, 1, 0), 0.6F, flap, true, bit.isRadiant(), fadeIn);
        super.render(bit, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MementoBitEntity bit) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
