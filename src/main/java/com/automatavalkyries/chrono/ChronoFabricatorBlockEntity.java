package com.automatavalkyries.chrono;

import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.machine.MachineRecipe;
import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * クロノ・ファブリケータ（設計書 §4.4）: 設計図カードのレシピ（作業台・組み立て機・兵装ワークベンチ）を、
 * 搬入リンクの先（チェスト・ヴォイド・リポジトリ）から材料をまとめて集めて組み立て、結果の置き場へ入れる（搬出リンクへ送る）。
 * カードの枠は T1 3〜T4 27、1 回に作る数は T1 1〜T4 64。
 */
public class ChronoFabricatorBlockEntity extends ChronoMachineBlockEntity {

    public static final int BASE_TIME = 40, ENERGY_PER_CRAFT = 1_000;
    private int progress, craftedLast;
    /**
     * 止まっている理由（画面に出す。2026-10-07 ユーザー「動いてない」）: 0 = 動いている、1 = カードが無い、2 = カードのレシピが見つからない、
     * 3 = 届く搬入リンクが無い、4 = 材料が足りない、5 = 電力が足りない、6 = 置き場がいっぱい
     */
    private int status = 1;
    public static final int OK = 0, NO_CARD = 1, NO_RECIPE = 2, NO_SOURCE = 3, NO_MATERIALS = 4, NO_POWER = 5, FULL = 6;

    public ChronoFabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.FABRICATOR_ENTITY.get(), ChronoMachineType.CHRONO_FABRICATOR, pos, state);
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        if (slot >= 27) return true; // 結果の置き場（内部で入れる。画面・自動化の入口からは入れられない）
        return slot < ChronoMachineType.cardSlots(tier()) && stack.getItem() instanceof BlueprintCardItem;
    }

    /** 材料の 1 つ分（材料 × 個数） */
    private record Need(Ingredient ingredient, int count) {}

    private List<Need> needs(Recipe<?> recipe) {
        List<Need> out = new ArrayList<>();
        if (recipe instanceof MachineRecipe machine) {
            for (MachineRecipe.CountedIngredient input : machine.inputs()) out.add(new Need(input.ingredient(), input.count()));
        } else {
            for (Ingredient ingredient : recipe.getIngredients()) if (!ingredient.isEmpty()) out.add(new Need(ingredient, 1));
        }
        return out;
    }

    @Override
    protected boolean work(ChronoTier tier) {
        if (ticks % 4 == 0) pushToLinks(tier, 27, 9);
        int time = scaledTime(tier, BASE_TIME);
        if (++progress < time) return craftedLast > 0;
        progress = 0;
        craftedLast = 0;
        List<IItemHandler> sources = new ArrayList<>();
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pulls()) continue;
            IItemHandler source = ChronoLinks.items(level, worldPosition, tier, link);
            if (source != null) sources.add(source);
        }
        int perCard = ChronoMachineType.craftsPerOp(tier);
        int perCraft = scaledEnergy(tier, ENERGY_PER_CRAFT);
        int why = NO_CARD;
        for (int c = 0; c < ChronoMachineType.cardSlots(tier); c++) {
            ResourceLocation id = BlueprintCardItem.recipeId(items.getStackInSlot(c));
            if (id == null) continue;
            Recipe<?> recipe = level.getRecipeManager().byKey(id).orElse(null);
            if (recipe == null) {
                why = Math.max(why, NO_RECIPE);
                continue;
            }
            if (sources.isEmpty()) {
                why = Math.max(why, NO_SOURCE);
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            List<Need> needs = needs(recipe);
            for (int n = 0; n < perCard; n++) {
                if (energy.getEnergyStored() < perCraft) {
                    why = Math.max(why, NO_POWER);
                    break;
                }
                if (!bufferFits(result)) {
                    why = Math.max(why, FULL);
                    break;
                }
                if (!take(sources, needs, true)) {
                    why = Math.max(why, NO_MATERIALS);
                    break;
                }
                take(sources, needs, false);
                energy.consume(perCraft);
                insertBuffer(result.copy());
                craftedLast++;
            }
        }
        status = craftedLast > 0 ? OK : why;
        if (craftedLast > 0) setChanged();
        return craftedLast > 0;
    }

    /** 材料を集める（simulate なら取らずに足りるかだけ見る）。容器の残り（バケツ等）は置き場へ */
    private boolean take(List<IItemHandler> sources, List<Need> needs, boolean simulate) {
        // 同じ材料を 2 回数えないよう、試しのときはスロットごとの使った数を覚える
        java.util.Map<IItemHandler, int[]> used = new java.util.IdentityHashMap<>();
        for (Need need : needs) {
            int left = need.count();
            for (IItemHandler source : sources) {
                int[] counts = used.computeIfAbsent(source, s -> new int[s.getSlots()]);
                for (int s = 0; s < source.getSlots() && left > 0; s++) {
                    ItemStack stack = source.getStackInSlot(s);
                    if (stack.isEmpty() || !need.ingredient().test(stack)) continue;
                    int available = stack.getCount() - counts[s];
                    if (available <= 0) continue;
                    int amount = Math.min(left, available);
                    ItemStack got = source.extractItem(s, amount, true);
                    amount = Math.min(amount, got.getCount());
                    if (amount <= 0) continue;
                    if (!simulate) {
                        ItemStack taken = source.extractItem(s, amount, false);
                        ItemStack remainder = taken.getCraftingRemainingItem();
                        if (!remainder.isEmpty()) {
                            remainder.setCount(taken.getCount());
                            insertBuffer(remainder);
                        }
                    } else {
                        counts[s] += amount;
                    }
                    left -= amount;
                }
                if (left <= 0) break;
            }
            if (left > 0) return false;
        }
        return true;
    }

    private boolean bufferFits(ItemStack result) {
        ItemStack rest = result.copy();
        for (int s = 27; s < 36 && !rest.isEmpty(); s++) rest = items.insertItem(s, rest, true);
        return rest.isEmpty();
    }

    private void insertBuffer(ItemStack stack) {
        ItemStack rest = stack;
        for (int s = 27; s < 36 && !rest.isEmpty(); s++) rest = items.insertItem(s, rest, false);
        if (!rest.isEmpty() && level != null) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, rest);
        }
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> 1000 * progress / Math.max(1, scaledTime(tier(), BASE_TIME));
            case 1 -> craftedLast;
            case 2 -> status;
            default -> 0;
        };
    }

    /** 自動化の入口からは置き場（27〜35）へ入れられない */
    @Override
    protected net.minecraftforge.items.IItemHandler automation() {
        IItemHandler base = super.automation();
        return new IItemHandler() {
            @Override public int getSlots() { return base.getSlots(); }
            @Override public ItemStack getStackInSlot(int slot) { return base.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return base.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < 27 && base.isItemValid(slot, stack); }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot < 27 ? base.insertItem(slot, stack, simulate) : stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return base.extractItem(slot, amount, simulate); }
        };
    }
}
