package com.automatavalkyries.world;

import com.automatavalkyries.config.ValkyrieConfig;
import com.automatavalkyries.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * 侵食基地の置き方（2026-10-30 ユーザー指定「浸食基地は初期リスポーンから遠くにいけばいくほど湧きやすくして」）: バニラのばらまき（random_spread）と同じ所を候補にし、
 * 初めのリスポーン地点から遠いほど、その候補に本当に置く割合を上げる（{@link ValkyrieConfig#baseChance}。近くでは minChance、
 * fullChanceDistance × {@code distance_factor} で今までどおりの数）。割合は世界の種と場所から決まる乱数で引くので、同じ世界なら何度生成しても、/locate でも同じ。
 * 置き方の方で判定するので、/place structure と基地の生成機はどこでも置ける
 */
public class DistanceSpreadPlacement extends RandomSpreadStructurePlacement {

    public static final Codec<DistanceSpreadPlacement> CODEC = RecordCodecBuilder.<DistanceSpreadPlacement>mapCodec(i -> i.group(
            Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO).forGetter(DistanceSpreadPlacement::locateOffset),
            FrequencyReductionMethod.CODEC.optionalFieldOf("frequency_reduction_method", FrequencyReductionMethod.DEFAULT).forGetter(DistanceSpreadPlacement::frequencyReductionMethod),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F).forGetter(DistanceSpreadPlacement::frequency),
            net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt").forGetter(DistanceSpreadPlacement::salt),
            ExclusionZone.CODEC.optionalFieldOf("exclusion_zone").forGetter(DistanceSpreadPlacement::exclusionZone),
            Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
            Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
            RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType),
            Codec.DOUBLE.optionalFieldOf("distance_factor", 1.0).forGetter(p -> p.distanceFactor)
    ).apply(i, DistanceSpreadPlacement::new)).codec();

    /** 今までどおりの数で出るようになる距離の倍率（上のティアほど大きく、遠くで増える） */
    final double distanceFactor;

    public DistanceSpreadPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> exclusion,
                                   int spacing, int separation, RandomSpreadType spreadType, double distanceFactor) {
        super(locateOffset, method, frequency, salt, exclusion, spacing, separation, spreadType);
        this.distanceFactor = distanceFactor;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        if (!super.isPlacementChunk(state, chunkX, chunkZ)) return false;
        BlockPos spawn = BlockPos.ZERO;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.overworld() != null) spawn = server.overworld().getSharedSpawnPos();
        double dx = chunkX * 16 + 8 - spawn.getX(), dz = chunkZ * 16 + 8 - spawn.getZ();
        double chance = ValkyrieConfig.baseChance(Math.sqrt(dx * dx + dz * dz), distanceFactor);
        if (chance >= 1.0) return true;
        RandomSource roll = RandomSource.create(state.getLevelSeed() ^ (chunkX * 341873128712L + chunkZ * 132897987541L) ^ salt());
        return roll.nextDouble() < chance;
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructures.DISTANCE_SPREAD.get();
    }
}
