package com.automatavalkyries.client;

import com.automatavalkyries.entity.chrono.RulerEchoEntity;
import com.automatavalkyries.entity.chrono.TimeRulerEntity;
import com.automatavalkyries.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * タイムルーラーの残像の分身（{@link RulerEchoEntity}、2026-10-20）: ルーラーの姿（同じモデルと動き）を、半透明の紫に光らせて描く。
 * 残像ごとに世界に置かない描くだけのルーラーを持ち、位置・向き・技の構えの時刻を写してから描く。現れる時と消える時は薄れる
 */
public class RulerEchoRenderer extends EntityRenderer<RulerEchoEntity> {

    private final Ghost ghost;
    private final Map<RulerEchoEntity, TimeRulerEntity> puppets = new WeakHashMap<>();

    public RulerEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.ghost = new Ghost(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RulerEchoEntity echo, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        TimeRulerEntity p = puppets.computeIfAbsent(echo, e -> ModEntities.TIME_RULER.get().create(e.level()));
        if (p == null) return;
        float age = echo.tickCount + pt;
        ghost.alpha = Mth.clamp(age / 8.0F, 0.0F, 1.0F) * Mth.clamp((echo.life() - age) / 12.0F, 0.0F, 1.0F);
        if (ghost.alpha <= 0.02F) return;
        p.tickCount = echo.tickCount;
        p.needleTick = echo.needleTick;
        p.slashTick = echo.slashTick;
        p.setPos(echo.getX(), echo.getY(), echo.getZ());
        float body = echo.getViewYRot(pt);
        p.yBodyRot = p.yBodyRotO = p.yHeadRot = p.yHeadRotO = body;
        p.setXRot(echo.getViewXRot(pt));
        p.xRotO = p.getXRot();
        ghost.render(p, yaw, pt, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    @Override
    public ResourceLocation getTextureLocation(RulerEchoEntity echo) {
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }

    /** 半透明の紫に光るルーラー */
    static final class Ghost extends GeoEntityRenderer<TimeRulerEntity> {
        float alpha = 1.0F;

        Ghost(EntityRendererProvider.Context context) {
            super(context, new ChronoBaseModel<>("time_ruler"));
        }

        @Override
        public RenderType getRenderType(TimeRulerEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucentEmissive(texture);
        }

        @Override
        public Color getRenderColor(TimeRulerEntity animatable, float partialTick, int packedLight) {
            return Color.ofRGBA(190, 150, 255, (int) (150 * alpha));
        }
    }
}
