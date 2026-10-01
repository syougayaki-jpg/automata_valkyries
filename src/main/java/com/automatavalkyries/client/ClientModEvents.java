package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ELENOR.get(), ElenorRenderer::new);
        // 最果てのエレノアはエレノアと同じ見た目（ElenorRenderer は ElenorEntity 用なので型だけ合わせる）
        event.registerEntityRenderer(ModEntities.FARTHEST_ELENOR.get(), context -> {
            @SuppressWarnings({"unchecked", "rawtypes"})
            net.minecraft.client.renderer.entity.EntityRenderer<com.automatavalkyries.entity.FarthestElenorEntity> renderer =
                    (net.minecraft.client.renderer.entity.EntityRenderer) new ElenorRenderer(context);
            return renderer;
        });
    }
}
