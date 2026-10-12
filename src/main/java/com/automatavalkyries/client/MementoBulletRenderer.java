package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.MementoBulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 死蝶の幾何弾幕の蝶弾: 光だけの小さな機械の蝶（冷たい青紫、第 2 形態は赤）が、飛ぶ向きを向いて速く羽ばたく。真ん中に白い芯 */
public class MementoBulletRenderer extends EntityRenderer<MementoBulletEntity> {

    public MementoBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MementoBulletEntity bullet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = bullet.tickCount + partialTick;
        Vec3 fwd = bullet.velocity();
        float flap = Mth.sin(age * 1.6F + bullet.getId()) * 0.7F;
        MementoFx.butterfly(buffers, pose.last().pose(), new Vec3(0, 0.1, 0), fwd, new Vec3(0, 1, 0), 0.32F, flap, false, bullet.isRed(), Math.min(1.0F, age / 3.0F));
        super.render(bullet, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MementoBulletEntity bullet) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
