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
            net.minecraft.client.gui.screens.MenuScreens.register(com.automatavalkyries.registry.ModUpgrades.MENU.get(), UpgradeStationScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.automatavalkyries.registry.ModEntities.VALKYRIE_MENU.get(), ValkyrieScreen::new);
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

    /** 追従中の兵姫の HUD */
    @SubscribeEvent
    public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        ValkyrieHud.register(event);
        MementoSpellLogo.register(event); // 死蝶終葬『メメント・モリ』の専用ロゴ
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ELENOR.get(), ElenorRenderer::new);
        event.registerEntityRenderer(ModEntities.VALKYRIE.get(), ValkyrieRenderer::new);
        event.registerEntityRenderer(ModEntities.CHRONO_SCOUT_DRONE.get(), c -> new ChronoGazerRenderer<>(c, "chrono_scout_drone", 0.4F));
        event.registerEntityRenderer(ModEntities.CHRONO_SPIDER_BOT.get(), c -> new ChronoGazerRenderer<>(c, "chrono_spider_bot", 0.5F));
        event.registerEntityRenderer(ModEntities.CHRONO_HEAVY_WALKER.get(), c -> new ChronoGazerRenderer<>(c, "chrono_heavy_walker", 1.0F));
        event.registerEntityRenderer(ModEntities.CHRONO_BOLT.get(), ChronoBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.CHRONO_MASK_DRONE.get(), MaskDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.CHRONO_TROOPER.get(), c -> new ChronoGazerRenderer<>(c, "chrono_trooper", 0.5F));
        event.registerEntityRenderer(ModEntities.LUXION.get(), LuxionRenderer::new);
        event.registerEntityRenderer(ModEntities.LUXION_FX.get(), LuxionFxRenderer::new);
        event.registerEntityRenderer(ModEntities.GALLIAFORT.get(), GalliaFortRenderer::new);
        event.registerEntityRenderer(ModEntities.MEMENTO_MORI.get(), MementoMoriRenderer::new);
        event.registerEntityRenderer(ModEntities.MEMENTO_BIT.get(), MementoBitRenderer::new);
        event.registerEntityRenderer(ModEntities.MEMENTO_BULLET.get(), MementoBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.MEMENTO_MOON.get(), MementoMoonRenderer::new);
        // 侵食基地の雑魚・中ボス・コンデンサーと弾（2026-10-15）
        event.registerEntityRenderer(ModEntities.PULSE_PHALANX.get(), c -> new ChronoBaseRenderer<>(c, "pulse_phalanx", 0.6F));
        event.registerEntityRenderer(ModEntities.SIEGE_CANNON.get(), c -> new ChronoBaseRenderer<>(c, "siege_cannon", 0.8F));
        event.registerEntityRenderer(ModEntities.SIREN_EYE.get(), c -> new ChronoBaseRenderer<>(c, "siren_eye", 0.4F));
        event.registerEntityRenderer(ModEntities.CHRONO_GUARDIAN.get(), c -> new ChronoBaseRenderer<>(c, "chrono_guardian", 1.0F));
        event.registerEntityRenderer(ModEntities.MIMIC_CONTAINER.get(), c -> new ChronoBaseRenderer<>(c, "mimic_container", 0.6F));
        event.registerEntityRenderer(ModEntities.PHASE_CONDENSER.get(), c -> new ChronoBaseRenderer<>(c, "phase_condenser", 0.5F));
        event.registerEntityRenderer(ModEntities.CRANE_LOADER.get(), c -> new ChronoBaseRenderer<>(c, "crane_loader", 1.6F));
        event.registerEntityRenderer(ModEntities.OUTPOST_WATCHER.get(), c -> new ChronoBaseRenderer<>(c, "outpost_watcher", 1.2F));
        event.registerEntityRenderer(ModEntities.FOUNDRY_MASTER.get(), c -> new ChronoBaseRenderer<>(c, "foundry_master", 1.6F));
        event.registerEntityRenderer(ModEntities.TIME_RULER.get(), c -> new ChronoBaseRenderer<>(c, "time_ruler", 2.0F));   // 総司令機タイムルーラー（2026-10-19）
        event.registerEntityRenderer(ModEntities.RULER_ANCHOR.get(), c -> new ChronoBaseRenderer<>(c, "ruler_anchor", 0.5F));
        event.registerEntityRenderer(ModEntities.RULER_NEEDLE.get(), RulerNeedleRenderer::new);
        event.registerEntityRenderer(ModEntities.BASTION_KEEPER.get(), c -> new ChronoBaseRenderer<>(c, "bastion_keeper", 1.4F));   // 浮遊要塞（2026-10-19）
        event.registerEntityRenderer(ModEntities.CHRONO_INTERCEPTOR.get(), c -> new ChronoBaseRenderer<>(c, "chrono_interceptor", 0.5F));
        event.registerEntityRenderer(ModEntities.DELAY_NODE.get(), c -> new ChronoBaseRenderer<>(c, "delay_node", 0.4F));
        event.registerEntityRenderer(ModEntities.SHIELD_DRONE.get(), c -> new ChronoGazerRenderer<>(c, "chrono_scout_drone", 0.3F));
        event.registerEntityRenderer(ModEntities.MORTAR_SHELL.get(), ChronoBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.CARGO_CONTAINER.get(), CargoContainerRenderer::new);
        event.registerEntityRenderer(ModEntities.GALLIA_MISSILE.get(), c -> new GalliaAmmoRenderer<>(c, "gallia_missile"));   // 3D のミサイル（2026-10-10「副砲とミサイルの弾を自作して搭載」）
        event.registerEntityRenderer(ModEntities.GALLIA_SHELL.get(), c -> new GalliaAmmoRenderer<>(c, "gallia_shell"));
        event.registerEntityRenderer(ModEntities.GALLIA_SIEGE_BEAM.get(), GalliaSiegeBeamRenderer::new);
        event.registerEntityRenderer(ModEntities.GALLIA_RIFT.get(), GalliaRiftRenderer::new);
        event.registerEntityRenderer(ModEntities.GALLIA_QUAKE.get(), GalliaQuakeRenderer::new);
        event.registerEntityRenderer(ModEntities.VALKYRIE_DUMMY.get(), c -> { // 戦術兵姫のダミーちゃん: Type-V の姿をそのまま使う
            var r = new software.bernie.geckolib.renderer.GeoEntityRenderer<com.automatavalkyries.entity.ValkyrieDummyEntity>(c,
                    new software.bernie.geckolib.model.DefaultedEntityGeoModel<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, "valkyrie_type_v")));
            r.addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(r));
            return r;
        });
        event.registerEntityRenderer(ModEntities.SKY_BOSS_SKELETON.get(), net.minecraft.client.renderer.entity.SkeletonRenderer::new);
        // 最果てのエレノアはエレノアと同じ見た目（ElenorRenderer は ElenorEntity 用なので型だけ合わせる）
        event.registerEntityRenderer(ModEntities.FARTHEST_ELENOR.get(), context -> {
            @SuppressWarnings({"unchecked", "rawtypes"})
            net.minecraft.client.renderer.entity.EntityRenderer<com.automatavalkyries.entity.FarthestElenorEntity> renderer =
                    (net.minecraft.client.renderer.entity.EntityRenderer) new ElenorRenderer(context);
            return renderer;
        });
        // 終焉検証用ダミーゾンビ（バニラゾンビモデル・テクスチャで描画）
        event.registerEntityRenderer(ModEntities.PROTECT_DUMMY_ZOMBIE.get(), net.minecraft.client.renderer.entity.ZombieRenderer::new);
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
