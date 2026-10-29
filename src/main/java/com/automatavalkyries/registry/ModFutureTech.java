package com.automatavalkyries.registry;

import com.automatavalkyries.item.EffectMaterialItem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 未来技術の上位回路とパーツ（docs/SPEC_CHRONOLINK_MACHINES.md §9）。どれもそのティアのルミナルエナジーアダプターで作る。
 * 回路は 基本 → 高等 → フォトニック（T1）→ 量子 TAC（T2、既存）→ 時空格子（T3）→ 因果演算（T4）の順で、ティアフレームの材料になる。
 * パーツはクロノリンク・マシンと次のティアの回路・パーツの材料（T4 のパーツは最終兵装・兵姫に使う予定）。
 */
public final class ModFutureTech {

    // ---- 上位回路 ----
    public static final RegistryObject<Item> CIRCUIT_PHOTONIC = effect("circuit_photonic", Rarity.RARE, ModEffects.LUMINAL, 16, false);
    public static final RegistryObject<Item> CIRCUIT_CHRONO_LATTICE = effect("circuit_chrono_lattice", Rarity.EPIC, ModEffects.CHRONO, 6, true);
    public static final RegistryObject<Item> CIRCUIT_CAUSAL = effect("circuit_causal", Rarity.EPIC, ModEffects.STELLACHRONODYME, 3, true);

    // ---- パーツ ----
    // T1: テザー・パイロン / ヴォイド・リポジトリの材料
    public static final RegistryObject<Item> EMITTER_SPATIAL = effect("emitter_spatial", Rarity.UNCOMMON, ModEffects.CHRONO, 18, false);
    public static final RegistryObject<Item> CAPSULE_SPATIAL = effect("capsule_spatial", Rarity.UNCOMMON, ModEffects.CHRONO, 18, false);
    // T2
    public static final RegistryObject<Item> PROCESSOR_SYNAPSE = effect("processor_synapse", Rarity.RARE, ModEffects.QUANTUM_CIRCUIT, 10, false);
    public static final RegistryObject<Item> FIELD_DISTORTION = effect("field_distortion", Rarity.RARE, ModEffects.CHRONO_FORTRESS, 10, false);
    // T3
    public static final RegistryObject<Item> GATE_WORMHOLE = effect("gate_wormhole", Rarity.EPIC, ModEffects.CHRONO, 6, true);
    public static final RegistryObject<Item> PLATING_STELLAR = effect("plating_stellar", Rarity.EPIC, ModEffects.STELLACHRONODYME, 8, true);
    // T4
    public static final RegistryObject<Item> ENGINE_PERPETUAL = effect("engine_perpetual", Rarity.EPIC, ModEffects.LUMINAL, 3, true);
    public static final RegistryObject<Item> SHELL_CAUSAL = effect("shell_causal", Rarity.EPIC, ModEffects.STELLACHRONODYME, 3, true);

    private ModFutureTech() {}

    public static void init() {}

    private static RegistryObject<Item> effect(String name, Rarity rarity, List<ParticleOptions> particles, int interval, boolean foil) {
        return ModItems.ITEMS.register(name, () -> new EffectMaterialItem(new Item.Properties().rarity(rarity), particles, interval, foil));
    }
}
