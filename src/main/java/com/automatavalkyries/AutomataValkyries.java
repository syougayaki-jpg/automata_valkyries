package com.automatavalkyries;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.EprotectEcaWhitelist;
import com.automatavalkyries.protect.EprotectManager;
import com.automatavalkyries.protect.ForeignHookReport;
import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.registry.ModCreativeTabs;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModItems;
import com.mojang.logging.LogUtils;
import com.automatavalkyries.config.EprotectConfig;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AutomataValkyries.MODID)
public class AutomataValkyries {

    public static final String MODID = "automata_valkyries";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutomataValkyries(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModEntities.ENTITY_TYPES.register(modEventBus);
        // ブロックを先に読み込む（ブロックアイテムを ModItems.ITEMS に足すので、ITEMS より前に登録する）
        com.automatavalkyries.registry.ModBlocks.BLOCKS.register(modEventBus);
        com.automatavalkyries.registry.IndustrialMetals.init(); // 工業 MOD 連携の金属（アイテム・ブロックを ITEMS / BLOCKS に足す）
        com.automatavalkyries.world.ConfigurableOresBiomeModifier.SERIALIZERS.register(modEventBus);
        // 基礎文明マシン（ブロック・アイテムを BLOCKS / ITEMS に足すので、ITEMS より前に読み込む）
        com.automatavalkyries.registry.ModMachines.init();
        com.automatavalkyries.registry.ModChrono.init();
        com.automatavalkyries.registry.ModCompression.init(); // 金属の圧縮・Tier 4 コア・ティアごとの中間素材
        com.automatavalkyries.registry.ModBossDrops.init(); // ボス素材（クリエイティブ限定。兵装のレシピで要求する）
        com.automatavalkyries.registry.ModWeapons.init(); // ルミナルコア・兵装・弾薬（兵器ユニット設計書）
        com.automatavalkyries.registry.ModTrials.init(); // 回想のトライアルとオーバーロードメモリ
        com.automatavalkyries.registry.ModUpgrades.init(); // 兵装強化台（カスタムアップグレード）
        com.automatavalkyries.registry.ModWeapons.MOB_EFFECTS.register(modEventBus);
        com.automatavalkyries.registry.ModParticles.PARTICLES.register(modEventBus); // 兵装の自作エフェクトの粒子
        com.automatavalkyries.registry.ModFutureTech.init(); // 未来技術の上位回路・パーツ // クロノリンク・マシン（BLOCKS / ITEMS / BLOCK_ENTITIES / MENUS に足す）
        com.automatavalkyries.registry.ModMachines.BLOCK_ENTITIES.register(modEventBus);
        com.automatavalkyries.registry.ModMachines.MENUS.register(modEventBus);
        com.automatavalkyries.registry.ModRecipes.TYPES.register(modEventBus);
        com.automatavalkyries.registry.ModRecipes.SERIALIZERS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        context.registerConfig(ModConfig.Type.COMMON, EprotectConfig.SPEC);
        context.registerConfig(ModConfig.Type.COMMON, com.automatavalkyries.config.WorldgenConfig.SPEC, MODID + "-worldgen.toml");
        context.registerConfig(ModConfig.Type.COMMON, com.automatavalkyries.config.MachineConfig.SPEC, MODID + "-machines.toml");
        context.registerConfig(ModConfig.Type.COMMON, com.automatavalkyries.config.WeaponConfig.SPEC, MODID + "-weapons.toml");
        net.minecraftforge.common.crafting.CraftingHelper.register(com.automatavalkyries.config.MachineConfig.Condition.SERIALIZER);
        // ブラックリストは設定の読み込み・再読み込み（コマンドでの保存を含む）のたびに作り直す
        modEventBus.addListener((ModConfigEvent.Loading event) -> onConfigChanged(event.getConfig()));
        modEventBus.addListener((ModConfigEvent.Reloading event) -> onConfigChanged(event.getConfig()));

        // 保護対象の監視（ゲーム中のイベントなので Forge バス）
        MinecraftForge.EVENT_BUS.register(EprotectManager.getInstance());

        // ECA の書き換え除外リストへ登録（攻撃より前に済ませたいので構築時に行う）
        EprotectEcaWhitelist.register();
    }

    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() == EprotectConfig.SPEC) {
            ModBlacklist.reloadFromConfig();
            com.automatavalkyries.protect.flag.FlagDictionary.reloadFromConfig();
            com.automatavalkyries.protect.flag.FlagDictionary.ensureAgentProtection();
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (!com.automatavalkyries.protect.SpecificCountermeasures.enabled()) {
            LOGGER.warn("[eprotect] Mod-specific countermeasures are OFF (genericOnly); only the generic layer protects entities");
        }
        event.enqueueWork(ModNetwork::register);
        com.automatavalkyries.protect.LifeGuardLink.install();
        com.automatavalkyries.protect.valkyrie.ValkyrieAgentLink.install();
        com.automatavalkyries.protect.flag.FlagDictionary.ensureAgentProtection();
        event.enqueueWork(EprotectManager::registerChunkLoadingCallback);
        if (EprotectConfig.get(EprotectConfig.REPORT_FOREIGN_ENTITY_HOOKS)) {
            event.enqueueWork(ForeignHookReport::log);
            event.enqueueWork(com.automatavalkyries.protect.flag.FlagReport::writeAsync);
        }
    }
}
