package com.automatavalkyries.chrono;

import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ヴォイド・リポジトリ（設計書 §4.6）: 空間圧縮ストレージ。種類の数と 1 種類あたりの数はティアごと
 * （T1 27 × 4,096、T2 81 × 65,536、T3 243 × 16,777,216、T4 無制限）。
 * 他のマシンの空間リンク先・ホッパー・他 MOD（{@link IItemHandler}）から出し入れできる。壊すと中身ごとアイテムになる。
 */
public class VoidRepositoryBlockEntity extends ChronoMachineBlockEntity {

    /** 入っている物（1 個分の見本と数）。並びは入れた順（画面と同じ） */
    public static final class Entry {
        public final ItemStack key;
        public long count;

        public Entry(ItemStack key, long count) {
            this.key = key.copyWithCount(1);
            this.count = count;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    /** 中身が変わるたびに増える（開いている画面へ送り直す） */
    private int version;

    public VoidRepositoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.REPOSITORY_ENTITY.get(), ChronoMachineType.VOID_REPOSITORY, pos, state);
    }

    public static int maxTypes(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 27;
            case FUTURE -> 81;
            case BEYOND -> 243;
            case NEVERLAST -> Integer.MAX_VALUE;
        };
    }

    public static long maxPerType(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 4_096L;
            case FUTURE -> 65_536L;
            case BEYOND -> 16_777_216L;
            case NEVERLAST -> Long.MAX_VALUE;
        };
    }

    public List<Entry> entries() { return entries; }
    public int version() { return version; }

    @Override protected int maxReceive(ChronoTier tier) { return 0; }
    @Override protected boolean exposesEnergy() { return false; }
    @Override protected boolean accepts(int slot, ItemStack stack) { return true; }

    /** 入れる（入らなかった残りを返す） */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ChronoTier tier = tier();
        long cap = maxPerType(tier);
        for (Entry entry : entries) {
            if (!ItemStack.isSameItemSameTags(entry.key, stack)) continue;
            long room = cap - entry.count;
            int moved = (int) Math.min(room, stack.getCount());
            if (!simulate && moved > 0) {
                entry.count += moved;
                changed();
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }
        if (entries.size() >= maxTypes(tier)) return stack;
        int moved = (int) Math.min(cap, stack.getCount());
        if (!simulate) {
            entries.add(new Entry(stack, moved));
            changed();
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    /** index 番目を最大 amount 個取り出す */
    public ItemStack extract(int index, int amount, boolean simulate) {
        if (index < 0 || index >= entries.size() || amount <= 0) return ItemStack.EMPTY;
        Entry entry = entries.get(index);
        int taken = (int) Math.min(Math.min(amount, entry.key.getMaxStackSize()), entry.count);
        ItemStack out = entry.key.copyWithCount(taken);
        if (!simulate) {
            entry.count -= taken;
            if (entry.count <= 0) entries.remove(index);
            changed();
        }
        return out;
    }

    private void changed() {
        version++;
        setChanged();
    }

    @Override
    protected boolean work(ChronoTier tier) {
        // 預け入れ口に置かれた物を中へ
        ItemStack deposit = items.getStackInSlot(0);
        if (!deposit.isEmpty()) items.setStackInSlot(0, insert(deposit, false));
        // 搬入リンクの先から吸い込み、搬出リンクの先へは出さない（取り出しは他のマシン・画面から）
        if (ticks % 4 == 0) {
            for (ChronoLink link : activeLinks(tier)) {
                if (!link.mode().pulls()) continue;
                IItemHandler source = ChronoLinks.items(level, worldPosition, tier, link);
                if (source == null) continue;
                int budget = tier.transferPerTick;
                for (int s = 0; s < source.getSlots() && budget > 0; s++) {
                    ItemStack peek = source.extractItem(s, budget, true);
                    if (peek.isEmpty()) continue;
                    int fits = peek.getCount() - insert(peek, true).getCount();
                    if (fits <= 0) continue;
                    insert(source.extractItem(s, fits, false), false);
                    budget -= fits;
                }
            }
        }
        return false;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> entries.size() & 0xFFFF;
            case 1 -> version & 0xFFFF;
            default -> 0;
        };
    }

    /** 他のマシン・ホッパー・他 MOD から: スロット = 入っている物 + 空き 1 つ（入れる口） */
    @Override
    protected IItemHandler automation() {
        return new IItemHandler() {
            @Override public int getSlots() { return entries.size() + 1; }

            @Override
            public @NotNull ItemStack getStackInSlot(int slot) {
                if (slot >= entries.size()) return ItemStack.EMPTY;
                Entry entry = entries.get(slot);
                return entry.key.copyWithCount((int) Math.min(entry.count, entry.key.getMaxStackSize()));
            }

            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
            @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
            @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return insert(stack, simulate); }
            @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return extract(slot, amount, simulate); }
        };
    }

    @Override
    public void dropContents() {
        // 中身はアイテムに残す（ルートの copy_nbt）。預け入れ口の物だけ落とす
        if (level != null) net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(0));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.put("item", entry.key.save(new CompoundTag()));
            e.putLong("count", entry.count);
            list.add(e);
        }
        tag.put("Contents", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        entries.clear();
        for (Tag t : tag.getList("Contents", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ItemStack key = ItemStack.of(e.getCompound("item"));
            if (!key.isEmpty() && e.getLong("count") > 0) entries.add(new Entry(key, e.getLong("count")));
        }
        version++;
    }
}
