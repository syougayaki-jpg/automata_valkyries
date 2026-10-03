package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    /** 基礎文明マシンの画面 */
    @SubscribeEvent
    public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(com.automatavalkyries.registry.ModMachines.MENU.get(), MachineScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.automatavalkyries.registry.ModChrono.MENU.get(), ChronoScreen::new);
            // クロノリンク・マシンのアイテムは、ブロックと同じくティアごとの見た目（models/item/<id>.json の overrides）
            net.minecraft.resources.ResourceLocation tier = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "tier");
            for (var block : com.automatavalkyries.registry.ModChrono.MACHINES.values()) {
                net.minecraft.client.renderer.item.ItemProperties.register(block.get().asItem(), tier,
                        (stack, level, entity, seed) -> com.automatavalkyries.chrono.ChronoMachineItem.tierOf(stack).level);
            }
        });
    }

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
        // ネクロシス・ニードルガンの針は粒子だけで見せる
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.NECROSIS_NEEDLE.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
}
