package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModRecipes;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

/**
 * マシンの種類（追加拡張仕様書 §11.1.2〜§11.1.3）。スロットの並び・電力の大きさ・使うレシピを決める。
 * スロットは [燃料]、[充電]、[材料 × inputs, 結果] のどれか。
 */
public enum MachineKind {
    // ---- 基礎文明マシン（§11.1.2） ----
    /** 燃料発電機: かまどの燃料を燃やして毎 Tick 40 FE を作る */
    GENERATOR("generator", Role.GENERATOR, 0, 50_000, 0, 400, null, 40, 1, 100),
    /** 蓄電器: 1,000,000 FE を蓄える（ケーブル・隣のマシンと出し入れ、充電スロットのアイテムへ充電） */
    BATTERY("battery", Role.BATTERY, 0, 1_000_000, 1_000, 1_000, null, 0, 1, 100),
    /** 電気炉: かまどのレシピを電力で（毎 Tick 20 FE、100 Tick） */
    ELECTRIC_FURNACE("electric_furnace", Role.FURNACE, 1, 20_000, 1_000, 0, null, 0, 1, 100),
    /** 粉砕機: 鉱石 → 粉 2 個 など（automata_valkyries:crushing） */
    CRUSHER("crusher", Role.PROCESS, 1, 20_000, 1_000, 0, ModRecipes.CRUSHING, 0, 1, 100),
    /** 合金炉: 2 種の材料 → 合金（automata_valkyries:alloying） */
    ALLOY_SMELTER("alloy_smelter", Role.PROCESS, 2, 40_000, 2_000, 0, ModRecipes.ALLOYING, 0, 1, 100),
    /** 金属プレス: インゴット → 板 など（automata_valkyries:pressing） */
    PRESS("press", Role.PROCESS, 1, 20_000, 1_000, 0, ModRecipes.PRESSING, 0, 1, 100),

    // ---- 改良型マシン（§11.1.3）: 連携素材をすべて使う。4 倍の速さで、電力は 75% ----
    IMPROVED_GENERATOR("improved_generator", Role.GENERATOR, 0, 200_000, 0, 1_600, null, 120, 1, 100),
    IMPROVED_BATTERY("improved_battery", Role.BATTERY, 0, 10_000_000, 8_000, 8_000, null, 0, 1, 100),
    IMPROVED_ELECTRIC_FURNACE("improved_electric_furnace", Role.FURNACE, 1, 80_000, 4_000, 0, null, 0, 4, 75),
    IMPROVED_CRUSHER("improved_crusher", Role.PROCESS, 1, 80_000, 4_000, 0, ModRecipes.CRUSHING, 0, 4, 75),
    IMPROVED_ALLOY_SMELTER("improved_alloy_smelter", Role.PROCESS, 2, 160_000, 8_000, 0, ModRecipes.ALLOYING, 0, 4, 75),
    IMPROVED_PRESS("improved_press", Role.PROCESS, 1, 80_000, 4_000, 0, ModRecipes.PRESSING, 0, 4, 75),

    // ---- 重工業マシン（§11.1.4）: 材料 6 枠の組み立て ----
    /** 組み立て機: プロトコア・回路のまとめ作り（automata_valkyries:assembling） */
    ASSEMBLER("assembler", Role.PROCESS, 6, 500_000, 10_000, 0, ModRecipes.ASSEMBLING, 0, 1, 100),
    /** 兵装ワークベンチ: 初期兵姫の兵装（automata_valkyries:weapon_assembling） */
    WEAPON_WORKBENCH("weapon_workbench", Role.PROCESS, 6, 200_000, 5_000, 0, ModRecipes.WEAPON_ASSEMBLING, 0, 1, 100);

    /** 何をするマシンか */
    public enum Role { GENERATOR, BATTERY, FURNACE, PROCESS }

    /** 電気炉の消費（FE/Tick）と加工時間（速さ 1 のとき） */
    public static final int FURNACE_USE = 20, FURNACE_TIME = 100;

    public final String id;
    public final Role role;
    public final int inputs;
    public final int capacity, maxReceive, maxExtract;
    public final Supplier<RecipeType<MachineRecipe>> recipeType;
    /** 発電機の発電量（FE/Tick） */
    public final int generation;
    /** 加工の速さ（時間をこの数で割る）と、使う電力の割合（%） */
    public final int speed, energyPercent;

    MachineKind(String id, Role role, int inputs, int capacity, int maxReceive, int maxExtract,
                Supplier<RecipeType<MachineRecipe>> recipeType, int generation, int speed, int energyPercent) {
        this.id = id;
        this.role = role;
        this.inputs = inputs;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.recipeType = recipeType;
        this.generation = generation;
        this.speed = speed;
        this.energyPercent = energyPercent;
    }

    public boolean hasFuel() { return role == Role.GENERATOR; }

    public boolean hasCharge() { return role == Role.BATTERY; }

    public boolean processes() { return role == Role.FURNACE || role == Role.PROCESS; }

    public int slots() {
        return processes() ? inputs + 1 : 1;
    }

    public int outputSlot() {
        return processes() ? inputs : -1;
    }

    /** 隣へ電力を送る側（発電機・蓄電器） */
    public boolean pushes() { return role == Role.GENERATOR || role == Role.BATTERY; }

    /** 加工時間と電力をこのマシンの速さ・効率に合わせる */
    public int scaleTime(int time) { return Math.max(1, time / speed); }

    public int scaleEnergy(int energy) { return (int) ((long) energy * energyPercent / 100); }
}
