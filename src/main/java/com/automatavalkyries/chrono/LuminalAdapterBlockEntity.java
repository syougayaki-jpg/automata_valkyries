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
 * ルミナルエナジーアダプター（設計書 §4.3）: 大量の FE で原子相転移（automata_valkyries:phase_transition）。
 * レシピの "tier" 以上のティアでだけ動く。並列スロットの数まで、材料が揃う分をまとめて一度に相転移する。
 * "stages" のあるレシピ（ステラ・クロノダイムの多段アロイ）は、1 回の加工の中で段を順に進める（画面に段を出す）。T4 は 1 Tick で終わる。
 */
public class LuminalAdapterBlockEntity extends ChronoMachineBlockEntity {

    private int progress, maxProgress, batch, stages = 1;

    public LuminalAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.ADAPTER_ENTITY.get(), ChronoMachineType.LUMINAL_ADAPTER, pos, state);
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot < 6 && isIngredient(stack);
    }

    private boolean isIngredient(ItemStack stack) {
        if (level == null) return true;
        int tier = tier().level;
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.PHASE_TRANSITION.get())) {
            if (recipe.tier() > tier) continue;
            for (MachineRecipe.CountedIngredient input : recipe.inputs()) if (input.ingredient().test(stack)) return true;
        }
        return false;
    }

    @Nullable
    private MachineRecipe findRecipe(ChronoTier tier) {
        if (level == null) return null;
        // 合うレシピのうち、いちばん高いティアの物（T3 の超時空圧縮が T2 の圧縮より先）
        RecipeWrapper container = new RecipeWrapper(items);
        MachineRecipe best = null;
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.PHASE_TRANSITION.get())) {
            if (recipe.tier() > tier.level || (best != null && recipe.tier() <= best.tier())) continue;
            if (recipe.match(container, 6) != null) best = recipe;
        }
        return best;
    }

    @Override
    protected boolean work(ChronoTier tier) {
        if (ticks % 4 == 0) pullFromLinks(tier, 0, 6, this::isIngredient);
        MachineRecipe recipe = findRecipe(tier);
        if (recipe == null) {
            progress = 0;
            maxProgress = 0;
            if (ticks % 4 == 0) pushToLinks(tier, 6, 1);
            return false;
        }
        int[] slots = recipe.match(new RecipeWrapper(items), 6);
        // 一度に相転移する数: 並列の数まで、材料と結果の置き場が足りる分
        int times = tier.parallel;
        for (int i = 0; i < slots.length; i++) times = Math.min(times, items.getStackInSlot(slots[i]).getCount() / recipe.inputs().get(i).count());
        ItemStack result = recipe.getResultItem(level.registryAccess());
        ItemStack out = items.getStackInSlot(6);
        int room = out.isEmpty() ? result.getMaxStackSize() : (ItemStack.isSameItemSameTags(out, result) ? out.getMaxStackSize() - out.getCount() : 0);
        times = Math.min(times, room / Math.max(1, result.getCount()));
        if (times <= 0) {
            progress = 0;
            if (ticks % 4 == 0) pushToLinks(tier, 6, 1);
            return false;
        }
        batch = times;
        stages = recipe.stages();
        maxProgress = tier == ChronoTier.NEVERLAST ? 1 : tier.scaleTime(recipe.time());
        long total = (long) tier.scaleEnergy(recipe.energy()) * times;
        int perTick = (int) Math.max(1, total / maxProgress);
        if (!energy.consume(perTick)) return false;
        if (++progress >= maxProgress) {
            progress = 0;
            for (int i = 0; i < slots.length; i++) items.extractItem(slots[i], recipe.inputs().get(i).count() * times, false);
            ItemStack made = result.copy();
            made.setCount(result.getCount() * times);
            if (out.isEmpty()) items.setStackInSlot(6, made);
            else out.grow(made.getCount());
            setChanged();
        }
        if (ticks % 4 == 0) pushToLinks(tier, 6, 1);
        return true;
    }

    /** 今の段（1 から。動いていなければ 0） */
    public int stage() {
        return maxProgress <= 0 ? 0 : Math.min(stages, 1 + progress * stages / maxProgress);
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> maxProgress > 0 ? 1000 * progress / maxProgress : 0;
            case 1 -> stage();
            case 2 -> stages;
            case 3 -> batch;
            default -> 0;
        };
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
