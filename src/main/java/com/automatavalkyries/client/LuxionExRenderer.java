package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.chrono.LuxionEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * ルクシオン EX のレンダラー（2026-10-27 ユーザー指定「色違い」）: 形・動き・光のマントはルクシオンと同じで、絵だけ黒と紅金
 * （{@code textures/entity/luxion_ex.png}・{@code luxion_ex_glowmask.png}。{@code tools/gen_luxion_ex.py} でルクシオンの絵から塗り替える）
 */
public class LuxionExRenderer extends LuxionRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "textures/entity/luxion_ex.png");

    public LuxionExRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LuxionEntity lux) {
        return TEXTURE;
    }
}
