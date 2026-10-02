package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.WorldgenConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 設定（WorldgenConfig）で切れる鉱石の追加。Forge の add_features と同じ書き方に、金属の名前（"metal"）を足したもの:
 * <pre>
 * { "type": "automata_valkyries:configurable_ores", "biomes": "#minecraft:is_overworld",
 *   "features": "automata_valkyries:ore_tin", "step": "underground_ores", "metal": "tin" }
 * </pre>
 * 既定では、同じ金属の鉱石を他 MOD がそのバイオームに生成していれば外す（{@link OreOverlap}、設定 skipWhenOtherModGenerates）。
 */
public record ConfigurableOresBiomeModifier(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features,
                                            GenerationStep.Decoration step, String metal) implements BiomeModifier {

    public static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AutomataValkyries.MODID);

    public static final RegistryObject<Codec<ConfigurableOresBiomeModifier>> CODEC = SERIALIZERS.register("configurable_ores", () ->
            RecordCodecBuilder.create(instance -> instance.group(
                    Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfigurableOresBiomeModifier::biomes),
                    PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(ConfigurableOresBiomeModifier::features),
                    GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(ConfigurableOresBiomeModifier::step),
                    Codec.STRING.fieldOf("metal").forGetter(ConfigurableOresBiomeModifier::metal)
            ).apply(instance, ConfigurableOresBiomeModifier::new)));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!biomes.contains(biome) || !WorldgenConfig.oreEnabled(metal)) return;
        if (phase == Phase.ADD) {
            features.forEach(feature -> builder.getGenerationSettings().addFeature(step, feature));
        } else if (phase == Phase.AFTER_EVERYTHING && WorldgenConfig.skipIfOtherMod()) {
            // 他の MOD がすべて足し終えた後に見て、同じ金属の鉱石を他 MOD がこのバイオームに生成していれば、こちらを外す
            for (GenerationStep.Decoration decoration : GenerationStep.Decoration.values()) {
                String other = OreOverlap.findOther(builder.getGenerationSettings().getFeatures(decoration), metal);
                if (other == null) continue;
                builder.getGenerationSettings().getFeatures(step).removeIf(features::contains);
                OreOverlap.noteSkipped(metal, other);
                return;
            }
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() {
        return CODEC.get();
    }
}
