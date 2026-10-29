package com.automatavalkyries.chrono;

import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * クロノ・スメルター（設計書 §4.2）: 鉱石・粗鉱（forge:ores/*・forge:raw_materials/*）を倍加して製錬する
 * （T1 ×2、T2 ×3、T3 ×4、T4 ×5）。それ以外の焼ける物はかまどと同じ 1 個。並列スロットごとに別々に進む。
 */
public class ChronoSmelterBlockEntity extends ChronoMachineBlockEntity {

    public static final int BASE_TIME = 100, BASE_ENERGY = 4_000;
    private static final TagKey<Item> ORES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "ores"));
    private static final TagKey<Item> RAW = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "raw_materials"));

    private final int[] progress = new int[16];
    /** 材料 → 焼いた物（1 個分。倍加の前） */
    private final Map<Item, ItemStack> cache = new HashMap<>();

    public ChronoSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.SMELTER_ENTITY.get(), ChronoMachineType.CHRONO_SMELTER, pos, state);
    }

    public static int multiplier(ChronoTier tier) { return tier.level + 1; }

    /** その材料の結果（倍加込み。焼けなければ空） */
    public ItemStack resultFor(ItemStack input, ChronoTier tier) {
        if (input.isEmpty() || level == null) return ItemStack.EMPTY;
        ItemStack base = cache.computeIfAbsent(input.getItem(), item -> level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(new ItemStack(item)), level)
                .map(r -> r.getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY));
        if (base.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = base.copy();
        if (input.is(ORES) || input.is(RAW)) out.setCount(Math.min(out.getMaxStackSize(), out.getCount() * multiplier(tier)));
        return out;
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot < 16 && slot < tier().parallel && !resultFor(stack, tier()).isEmpty();
    }

    @Override
    protected boolean work(ChronoTier tier) {
        int lanes = tier.parallel;
        if (ticks % 2 == 0) pullFromLinks(tier, 0, lanes, s -> !resultFor(s, tier).isEmpty());
        int time = scaledTime(tier, BASE_TIME);
        int perTick = Math.max(1, scaledEnergy(tier, BASE_ENERGY) / time);
        boolean active = false;
        for (int lane = 0; lane < lanes; lane++) {
            ItemStack input = items.getStackInSlot(lane);
            ItemStack result = resultFor(input, tier);
            if (result.isEmpty() || !fits(16 + lane, result)) {
                progress[lane] = 0;
                continue;
            }
            if (!energy.consume(perTick)) break;
            active = true;
            if (++progress[lane] >= time) {
                progress[lane] = 0;
                items.extractItem(lane, 1, false);
                ItemStack out = items.getStackInSlot(16 + lane);
                if (out.isEmpty()) items.setStackInSlot(16 + lane, result.copy());
                else out.grow(result.getCount());
                setChanged();
            }
        }
        if (ticks % 2 == 0) pushToLinks(tier, 16, lanes);
        return active;
    }

    private boolean fits(int slot, ItemStack result) {
        ItemStack out = items.getStackInSlot(slot);
        return out.isEmpty() || (ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    @Override
    protected int extraData(int index) {
        if (index < 16) {
            int time = scaledTime(tier(), BASE_TIME);
            return 1000 * progress[index] / Math.max(1, time);
        }
        return 0;
    }

    @Override
    protected void onTierChanged(ChronoTier tier) {
        java.util.Arrays.fill(progress, 0);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cache.clear();
    }
}
