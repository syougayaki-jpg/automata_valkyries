package com.automatavalkyries.registry;

import com.automatavalkyries.item.BossDropItem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * ボス素材（既存仕様書 §7.7 の boss_drop_*）。上位の兵装・究極コアのレシピで要求する（兵器ユニット設計書 docs/SPEC_WEAPON_UNITS.md）。
 * ボスがまだ居ないので、今は<b>クリエイティブ限定</b>（クリエイティブのタブ・コマンドでだけ手に入る。敵ドロップ素材と同じ扱い）。
 * ボスを実装したら、そのボスのドロップにする。数の上限は仕様書のとおり（ボス素材 16、裏ボス・究極の素材 1）。
 */
public final class ModBossDrops {

    // ---- ボス素材（16 まで重なる） ----
    public static final RegistryObject<Item> FTL_ACCEL = drop("boss_drop_ftl_accel", 16, ModEffects.TACHYON_ACCEL, 8, "luxion");
    public static final RegistryObject<Item> LUXION_CORE = drop("boss_drop_luxion_core", 16, ModEffects.LUMINAL, 8, "luxion");
    public static final RegistryObject<Item> GALLIA_PLATE = drop("boss_drop_gallia_plate", 16, ModEffects.CHRONO_FORTRESS, 10, "galliafort");
    public static final RegistryObject<Item> DISTORTION_GEN = drop("boss_drop_distortion_gen", 16, ModEffects.CHRONO, 8, "galliafort");
    public static final RegistryObject<Item> NECRO_COLONY = drop("boss_drop_necro_colony", 16, ModEffects.ECLIPSE_BIOMETAL, 8, "memento_mori");
    public static final RegistryObject<Item> MEMENTO_WINGS = drop("boss_drop_memento_wings", 16, ModEffects.ECLIPSE_BIOMETAL, 6, "memento_mori");
    public static final RegistryObject<Item> TIME_NEEDLE = drop("boss_drop_time_needle", 16, ModEffects.CHRONO, 6, "time_ruler");
    public static final RegistryObject<Item> ELENOR_RIBBON = drop("boss_drop_elenor_ribbon", 16, ModEffects.LUMINAL, 10, "elenor");
    /** ちっぽけな勇気（ホットバーにあれば終焉が効かない。ボスのタグのあるモブの超低確率ドロップ。docs/SPEC_ELENOR.md §3.4） */
    public static final RegistryObject<Item> TINY_COURAGE = ModItems.ITEMS.register("tiny_courage", () -> new com.automatavalkyries.item.TinyCourageItem(new Item.Properties()));
    /** 試練の砂時計（エレノアに使うと攻撃ティアが上がる。docs/SPEC_ELENOR.md §2.3.3） */
    public static final RegistryObject<Item> TRIAL_HOURGLASS = ModItems.ITEMS.register("trial_hourglass", () -> new com.automatavalkyries.item.TrialHourglassItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC)));
    // ---- 究極・裏ボス素材（1 つずつ） ----
    public static final RegistryObject<Item> RULER_HEART = drop("boss_drop_ruler_heart", 1, ModEffects.STELLACHRONODYME, 4, "time_ruler");
    public static final RegistryObject<Item> MEMORY_STELLA = drop("boss_drop_memory_stella", 1, ModEffects.LUMINAL_STERLING, 4, "lost_catastrophe");
    public static final RegistryObject<Item> PURIFIED_ZERO = drop("boss_drop_purified_zero", 1, ModEffects.STELLACHRONODYME, 3, "lost_catastrophe");
    public static final RegistryObject<Item> SINGULARITY_REMNANT = drop("boss_drop_singularity_remnant", 1, ModEffects.CHRONO, 3, "lost_catastrophe");

    private ModBossDrops() {}

    public static void init() {}

    /** boss はツールチップに出す、落とすボス（boss.automata_valkyries.<boss>） */
    private static RegistryObject<Item> drop(String name, int stack, List<ParticleOptions> particles, int interval, String boss) {
        return ModItems.ITEMS.register(name, () -> new BossDropItem(new Item.Properties().stacksTo(stack).rarity(Rarity.EPIC), particles, interval, boss));
    }
}
