package com.automatavalkyries.chrono;

import com.automatavalkyries.machine.MachineRecipe;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * タクティカル・アーモリー（兵器ユニット設計書 docs/SPEC_WEAPON_UNITS.md）: 兵装・ルミナルコアを組み立てる 7 種目のクロノリンク・マシン。
 * レシピ型 {@code automata_valkyries:armory_assembling}（材料 9 + 結果 1、電力、"tier" で必要なティア）。ティアが高いほど上位の兵装を作れる
 * （T1 フォトンエッジ等 → T4 カタストロフ・バスター）。兵装は重ならないので、1 回に 1 個ずつ作る。
 */
public class TacticalArmoryBlockEntity extends ChronoMachineBlockEntity {

    public static final int INPUTS = 9, OUTPUT = 9;

    private int progress, maxProgress;

    public TacticalArmoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.ARMORY_ENTITY.get(), ChronoMachineType.TACTICAL_ARMORY, pos, state);
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot < INPUTS && isIngredient(stack);
    }

    private boolean isIngredient(ItemStack stack) {
        if (level == null) return true;
        int tier = tier().level;
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.ARMORY_ASSEMBLING.get())) {
            if (recipe.tier() > tier) continue;
            for (MachineRecipe.CountedIngredient input : recipe.inputs()) if (input.ingredient().test(stack)) return true;
        }
        return false;
    }

    /** 合うレシピのうち、いちばん高いティアの物 */
    @Nullable
    private MachineRecipe findRecipe(ChronoTier tier) {
        if (level == null) return null;
        RecipeWrapper container = new RecipeWrapper(items);
        MachineRecipe best = null;
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.ARMORY_ASSEMBLING.get())) {
            if (recipe.tier() > tier.level || (best != null && recipe.tier() <= best.tier())) continue;
            if (recipe.match(container, INPUTS) != null) best = recipe;
        }
        return best;
    }

    @Override
    protected boolean work(ChronoTier tier) {
        if (ticks % 4 == 0) pullFromLinks(tier, 0, INPUTS, this::isIngredient);
        MachineRecipe recipe = findRecipe(tier);
        ItemStack out = items.getStackInSlot(OUTPUT);
        ItemStack result = recipe != null ? recipe.getResultItem(level.registryAccess()) : ItemStack.EMPTY;
        boolean room = recipe != null && (out.isEmpty()
                || (ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize()));
        if (!room) {
            progress = 0;
            maxProgress = 0;
            if (ticks % 4 == 0) pushToLinks(tier, OUTPUT, 1);
            return false;
        }
        int[] slots = recipe.match(new RecipeWrapper(items), INPUTS);
        maxProgress = tier == ChronoTier.NEVERLAST ? 1 : scaledTime(tier, recipe.time());
        int perTick = (int) Math.max(1, (long) scaledEnergy(tier, recipe.energy()) / maxProgress);
        if (!energy.consume(perTick)) return false;
        if (++progress >= maxProgress) {
            progress = 0;
            for (int i = 0; i < slots.length; i++) items.extractItem(slots[i], recipe.inputs().get(i).count(), false);
            if (out.isEmpty()) items.setStackInSlot(OUTPUT, result.copy());
            else out.grow(result.getCount());
            setChanged();
        }
        if (ticks % 4 == 0) pushToLinks(tier, OUTPUT, 1);
        return true;
    }

    @Override
    protected int extraData(int index) {
        return index == 0 && maxProgress > 0 ? 1000 * progress / maxProgress : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("progress");
    }
}
