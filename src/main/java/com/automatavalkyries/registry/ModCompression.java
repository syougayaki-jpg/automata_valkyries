package com.automatavalkyries.registry;

import com.automatavalkyries.item.EffectMaterialItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 圧縮階層の拡張（docs/SPEC_CHRONOLINK_MACHINES.md §8）:
 * <ul>
 *   <li>金属の圧縮: 本 MOD の下位金属 3 種と連携金属 8 種のブロック 9 → オーブ 9 → スター 9 → コア（{@code orb_<金属>} 等）</li>
 *   <li>3 系統の Tier 4 コア（追加拡張仕様書 §9.2）: スター 9 → コア。創世ルミナルコアは T4 の反応炉で毎秒 1,000,000 FE</li>
 *   <li>ティアごとの中間素材: そのティアのアダプターで作り、次のティアのフレームとマシンに使う</li>
 * </ul>
 */
public final class ModCompression {

    /** 金属 → 粒子の色（インゴットのテクスチャの中間色） */
    public static final Map<String, Integer> METALS = new LinkedHashMap<>();

    static {
        METALS.put("astrite", 0x3d5a80);
        METALS.put("phononium", 0x9d0208);
        METALS.put("ceruleas", 0x457b9d);
        METALS.put("tin", 0xa9b6c2);
        METALS.put("lead", 0x5c6078);
        METALS.put("silver", 0xc4ccd4);
        METALS.put("nickel", 0xc4be96);
        METALS.put("zinc", 0xa6b2b0);
        METALS.put("aluminum", 0xcdd3d8);
        METALS.put("osmium", 0x8aa6b8);
        METALS.put("uranium", 0x5e9a36);
    }

    /** 段（orb / star / core）→ 金属 → アイテム */
    public static final Map<String, Map<String, RegistryObject<Item>>> COMPRESSED = new LinkedHashMap<>();

    static {
        for (String tier : new String[]{"orb", "star", "core"}) {
            Map<String, RegistryObject<Item>> byMetal = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> metal : METALS.entrySet()) {
                Rarity rarity = switch (tier) {
                    case "orb" -> Rarity.UNCOMMON;
                    case "star" -> Rarity.RARE;
                    default -> Rarity.EPIC;
                };
                List<ParticleOptions> particles = tier.equals("orb") ? List.of(dust(metal.getValue(), 0.6F))
                        : List.of(dust(metal.getValue(), 0.7F), ParticleTypes.END_ROD);
                int interval = tier.equals("orb") ? 14 : tier.equals("star") ? 8 : 5;
                byMetal.put(metal.getKey(), effect(tier + "_" + metal.getKey(), rarity, particles, interval, !tier.equals("orb")));
            }
            COMPRESSED.put(tier, byMetal);
        }
    }

    // ---- 3 系統の Tier 4 コア（追加拡張仕様書 §9.2。裏ボス素材を兼ねる） ----
    public static final RegistryObject<Item> CORE_SINGULARITY_CHRONOS = effect("core_singularity_chronos", Rarity.EPIC, ModEffects.CHRONO, 4, true);
    public static final RegistryObject<Item> CORE_GENESIS_LUMINAL = effect("core_genesis_luminal", Rarity.EPIC, ModEffects.LUMINAL, 4, true);
    public static final RegistryObject<Item> CORE_CATASTROPHE_NANO = effect("core_catastrophe_nano", Rarity.EPIC, ModEffects.NANO, 4, true);

    // ---- ティアごとの中間素材（そのティアのルミナルエナジーアダプターで作る） ----
    // T1 → フューチャー・フレームと T1 のマシン（反応炉・アダプター以外）
    public static final RegistryObject<Item> LENS_LUMINAL = effect("lens_luminal", Rarity.UNCOMMON, ModEffects.LUMINAL, 20, false);
    public static final RegistryObject<Item> COIL_CHRONO = effect("coil_chrono", Rarity.UNCOMMON, ModEffects.CHRONO, 20, false);
    public static final RegistryObject<Item> MEMBRANE_NANO = effect("membrane_nano", Rarity.UNCOMMON, ModEffects.NANO, 20, false);
    // T2 → ビヨンド・フレーム
    public static final RegistryObject<Item> DRIVE_TACHYON = effect("drive_tachyon", Rarity.RARE, ModEffects.TACHYON_ACCEL, 10, false);
    public static final RegistryObject<Item> MATRIX_PHOTON = effect("matrix_photon", Rarity.RARE, ModEffects.LUMINAL_STERLING, 10, false);
    public static final RegistryObject<Item> TISSUE_ECLIPSE = effect("tissue_eclipse", Rarity.RARE, ModEffects.ECLIPSE_BIOMETAL, 10, false);
    // T3 → ネバーラスト・フレーム
    public static final RegistryObject<Item> HEART_STELLAR = effect("heart_stellar", Rarity.EPIC, ModEffects.STELLACHRONODYME, 6, true);
    public static final RegistryObject<Item> ANCHOR_CAUSAL = effect("anchor_causal", Rarity.EPIC, ModEffects.CHRONO, 6, true);
    public static final RegistryObject<Item> SEED_GENESIS = effect("seed_genesis", Rarity.EPIC, ModEffects.NANO, 6, true);
    // T4 → 最終兵装などに使う（今は素材だけ）
    public static final RegistryObject<Item> SINGULARITY_NEVERLAST = effect("singularity_neverlast", Rarity.EPIC, ModEffects.STELLACHRONODYME, 3, true);

    private ModCompression() {}

    public static void init() {}

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private static RegistryObject<Item> effect(String name, Rarity rarity, List<ParticleOptions> particles, int interval, boolean foil) {
        return ModItems.ITEMS.register(name, () -> new EffectMaterialItem(new Item.Properties().rarity(rarity), particles, interval, foil));
    }
}
