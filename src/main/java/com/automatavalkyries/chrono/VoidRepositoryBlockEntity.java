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

    /**
     * 入っている物（1 個分の見本と数）。場所は固定で、取り出しきった所は空き（null）のまま残し、新しい種類は前の空きから埋める
     * （取り出すたびに後ろが詰まって場所が動いていた。2026-10-07 ユーザー指定「インベントリの場所が動いている」）。
     * 並べ直すのは画面の「整理」だけ（素材 → 装備、ID 順・耐久の減り順。{@link #ORDER}）
     */
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

    /** 中身（空きは null） */
    public List<Entry> entries() { return entries; }

    /** 入っている種類の数（空きを除く） */
    public int types() {
        int n = 0;
        for (Entry e : entries) if (e != null) n++;
        return n;
    }

    /** 整理: 空きを詰めて、素材 → 装備・ID 順に並べ直す（画面のボタン） */
    public void sortContents() {
        entries.removeIf(java.util.Objects::isNull);
        entries.sort(ORDER);
        changed();
    }

    /** 装備（重ならない物）か。画面の絞り込みにも使う */
    public static boolean gear(ItemStack stack) {
        return stack.getMaxStackSize() == 1;
    }

    /** 並び: 素材 → 装備、ID 順、耐久の減りの少ない順、NBT の文字列順 */
    public static final java.util.Comparator<Entry> ORDER = java.util.Comparator
            .<Entry>comparingInt(e -> gear(e.key) ? 1 : 0)
            .thenComparing(e -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.key.getItem())))
            .thenComparingInt(e -> e.key.getDamageValue())
            .thenComparing(e -> e.key.hasTag() ? e.key.getTag().toString() : "");
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
            if (entry == null || !ItemStack.isSameItemSameTags(entry.key, stack)) continue;
            long room = cap - entry.count;
            int moved = (int) Math.min(room, stack.getCount());
            if (!simulate && moved > 0) {
                entry.count += moved;
                changed();
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }
        if (types() >= maxTypes(tier)) return stack;
        int moved = (int) Math.min(cap, stack.getCount());
        if (!simulate) {
            Entry added = new Entry(stack, moved);
            int hole = entries.indexOf(null);
            if (hole >= 0) entries.set(hole, added); // 前の空きへ
            else entries.add(added);
            changed();
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    /** index 番目を最大 amount 個取り出す */
    public ItemStack extract(int index, int amount, boolean simulate) {
        if (index < 0 || index >= entries.size() || amount <= 0 || entries.get(index) == null) return ItemStack.EMPTY;
        Entry entry = entries.get(index);
        int taken = (int) Math.min(Math.min(amount, entry.key.getMaxStackSize()), entry.count);
        ItemStack out = entry.key.copyWithCount(taken);
        if (!simulate) {
            entry.count -= taken;
            if (entry.count <= 0) { // 空きのまま残す（後ろを詰めない）。末尾の空きだけ消す
                entries.set(index, null);
                while (!entries.isEmpty() && entries.get(entries.size() - 1) == null) entries.remove(entries.size() - 1);
            }
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
            case 0 -> types() & 0xFFFF;
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
                if (slot >= entries.size() || entries.get(slot) == null) return ItemStack.EMPTY;
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
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry == null) continue;
            CompoundTag e = new CompoundTag();
            e.putInt("slot", i);
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
            if (key.isEmpty() || e.getLong("count") <= 0) continue;
            int slot = e.contains("slot") ? e.getInt("slot") : entries.size(); // 場所の無い前の版は順に
            while (entries.size() <= slot) entries.add(null);
            if (entries.get(slot) == null) entries.set(slot, new Entry(key, e.getLong("count")));
            else entries.add(new Entry(key, e.getLong("count")));
        }
        while (!entries.isEmpty() && entries.get(entries.size() - 1) == null) entries.remove(entries.size() - 1);
        version++;
    }
}
