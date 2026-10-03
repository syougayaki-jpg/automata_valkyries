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
        // 銃の構え（ArmPose の追加）は、人型モデルが ArmPose の switch を使う前に作っておく（後から足すと描画で配列の外を読んで落ちる）
        WeaponClient.init();
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

    /** 兵装の自作エフェクトの粒子 */
    @SubscribeEvent
    public static void registerParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        com.automatavalkyries.client.particle.FxParticles.register(event);
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
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.BLACK_HOLE.get(), BlackHoleRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.QUANTUM_SLASH.get(), QuantumSlashRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.PUNISHMENT_BEAM.get(), PunishmentBeamRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.FROST_MISSILE.get(), FrostMissileRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.CHAKRAM.get(), ChakramRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.JAVELIN.get(), JavelinRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.RAINBOW_SLASH.get(), RainbowSlashRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.MINI_CATASTROPHE.get(), MiniCatastropheRenderer::new);
        event.registerEntityRenderer(com.automatavalkyries.registry.ModWeapons.T4_STRIKE.get(), T4StrikeRenderer::new);
    }
}
