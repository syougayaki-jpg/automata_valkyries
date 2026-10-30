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
 * レシピを選べる（2026-10-31 ユーザー指定「ルミナスエナジーアダプターにはレシピを選べる機能をつけて」）: 自動（合うレシピのうちいちばん高いティア）か、
 * 画面で選んだ 1 つ。選んでいる間はそのレシピだけを作り、そのレシピの材料だけを、材料の順に決まったスロットへ受け取る（ほかの材料で詰まらない）。
 */
public class LuminalAdapterBlockEntity extends ChronoMachineBlockEntity {

    private int progress, maxProgress, batch, stages = 1;
    /** 選んだレシピ（null = 自動） */
    @Nullable
    private net.minecraft.resources.ResourceLocation selected;

    public LuminalAdapterBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.ADAPTER_ENTITY.get(), ChronoMachineType.LUMINAL_ADAPTER, pos, state);
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        if (slot >= 6) return false;
        // レシピを選んでいる時は、レシピの材料の順にスロットを決める（1 番目の材料は 1 番目のスロットだけ…。2026-10-31 ユーザー指定
        // 「レシピを選んだらそのレシピ通りのアイテムスロットに入れるようにして」）。Shift クリック・搬入リンク・ホッパーも同じ
        MachineRecipe chosen = chosen();
        if (chosen != null) return slot < chosen.inputs().size() && chosen.inputs().get(slot).ingredient().test(stack);
        return isIngredient(stack);
    }

    private boolean isIngredient(ItemStack stack) {
        if (level == null) return true;
        int tier = tier().level;
        MachineRecipe chosen = chosen();
        if (chosen != null) {
            for (MachineRecipe.CountedIngredient input : chosen.inputs()) if (input.ingredient().test(stack)) return true;
            return false;
        }
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.PHASE_TRANSITION.get())) {
            if (recipe.tier() > tier) continue;
            for (MachineRecipe.CountedIngredient input : recipe.inputs()) if (input.ingredient().test(stack)) return true;
        }
        return false;
    }

    /** 選べるレシピ（このティアで作れる相転移を ID の順に。画面も同じ並びを使う） */
    public static java.util.List<MachineRecipe> choices(net.minecraft.world.level.Level level, int tier) {
        java.util.List<MachineRecipe> list = new java.util.ArrayList<>();
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.PHASE_TRANSITION.get())) {
            if (recipe.tier() <= tier) list.add(recipe);
        }
        list.sort(java.util.Comparator.comparing(r -> r.getId().toString()));
        return list;
    }

    /** 選んだレシピ（自動・今のティアで作れない・消えたレシピなら null） */
    @Nullable
    public MachineRecipe chosen() {
        if (selected == null || level == null) return null;
        return level.getRecipeManager().byKey(selected)
                .filter(r -> r instanceof MachineRecipe m && m.getType() == ModRecipes.PHASE_TRANSITION.get() && m.tier() <= tier().level)
                .map(r -> (MachineRecipe) r).orElse(null);
    }

    /** 画面のボタン: 次・前のレシピへ（自動 → 1 番目 → … → 最後 → 自動） */
    public void cycleRecipe(boolean back) {
        if (level == null) return;
        java.util.List<MachineRecipe> list = choices(level, tier().level);
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).getId().equals(selected)) index = i;
        int n = list.size() + 1;
        int next = Math.floorMod(index + 1 + (back ? -1 : 1), n) - 1;
        selected = next < 0 ? null : list.get(next).getId();
        progress = 0;
        setChanged();
    }

    /** 画面のボタン: 自動に戻す */
    public void autoRecipe() {
        selected = null;
        progress = 0;
        setChanged();
    }

    @Nullable
    private MachineRecipe findRecipe(ChronoTier tier) {
        if (level == null) return null;
        if (selected != null) {           // 選んだレシピだけ（材料が揃わなければ待つ）
            MachineRecipe chosen = chosen();
            return chosen != null && chosen.match(new RecipeWrapper(items), 6) != null ? chosen : null;
        }
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
        maxProgress = tier == ChronoTier.NEVERLAST ? 1 : scaledTime(tier, recipe.time());
        long total = (long) scaledEnergy(tier, recipe.energy()) * times;
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
            case 4 -> selectedIndex() + 1;   // 選んだレシピ（0 = 自動、n = choices の n 番目）
            default -> 0;
        };
    }

    private int selectedIndex() {
        if (selected == null || level == null) return -1;
        java.util.List<MachineRecipe> list = choices(level, tier().level);
        for (int i = 0; i < list.size(); i++) if (list.get(i).getId().equals(selected)) return i;
        return -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("progress", progress);
        if (selected != null) tag.putString("recipe", selected.toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = tag.getInt("progress");
        selected = tag.contains("recipe") ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }
}
