package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.world.CrashedPods;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** ワールド生成の地物（墜落ポッド。配置は data/automata_valkyries/worldgen と forge/biome_modifier） */
public final class ModWorldgen {

    private ModWorldgen() {}

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AutomataValkyries.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CRASHED_POD = FEATURES.register("crashed_pod", CrashedPods.PodFeature::new);
}
