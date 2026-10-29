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

    // ---- 電力を使う便利な機能（2026-10-29 ユーザー指定「電力を使って便利機能でもなにかあるか」。質問で: 自動圧縮・在庫を保って配る。数値は【提案】） ----
    // しまう・取り出すは今までどおり電力なしで使える。電力が足りないとこの 2 つだけ止まる

    /** 自動圧縮で残す元の物の数（これを超えた分を 9 個ずつ圧縮する） */
    public static final int COMPRESS_KEEP = 64;
    /** 圧縮 1 回（9 → 1）の電力と、在庫を配る 1 個の電力 */
    public static final int COMPRESS_COST = 100, STOCK_COST = 10;
    /** 在庫の決まりの数の段（画面で右クリックするたびに次へ。0 = 決まり無し） */
    public static final int[] STOCK_STEPS = {0, 16, 64, 256, 1024};
    /** 在庫の決まりの数の上限（種類） */
    public static final int MAX_STOCK_RULES = 27;

    /** 自動圧縮が入か */
    private boolean compress;
    /** テザー・パイロンから電力を受け取るか（既定は切。2026-10-29 ユーザー指定「テザーパイロンからの電力供給はデフォルトでオフにして」。隣のマシンなどからはいつでも受け取る） */
    private boolean pylonPower;
    /** 在庫の決まり: 物 → 搬出リンクの先に置いておく数 */
    private final List<Entry> stockRules = new ArrayList<>();

    /** 1 秒にできる圧縮の回数（T1 16、T2 128、T3 1,024、T4 8,192） */
    public static int compressPerSecond(ChronoTier tier) {
        return 16 << ((tier.level - 1) * 3);
    }

    public boolean compressing() { return compress; }

    public boolean acceptsPylonPower() { return pylonPower; }

    public void togglePylonPower() {
        pylonPower = !pylonPower;
        changed();
    }

    public void toggleCompress() {
        compress = !compress;
        changed();
    }

    /** その物の在庫の決まり（無ければ 0） */
    public int stockOf(ItemStack key) {
        for (Entry rule : stockRules) if (ItemStack.isSameItemSameTags(rule.key, key)) return (int) rule.count;
        return 0;
    }

    /** index 番目の物の在庫の決まりを次（back なら前）の段へ */
    public void cycleStock(int index, boolean back) {
        if (index < 0 || index >= entries.size() || entries.get(index) == null) return;
        ItemStack key = entries.get(index).key;
        int now = stockOf(key), at = 0;
        for (int i = 0; i < STOCK_STEPS.length; i++) if (STOCK_STEPS[i] == now) at = i;
        int next = STOCK_STEPS[Math.floorMod(at + (back ? -1 : 1), STOCK_STEPS.length)];
        stockRules.removeIf(rule -> ItemStack.isSameItemSameTags(rule.key, key));
        if (next > 0 && stockRules.size() < MAX_STOCK_RULES) stockRules.add(new Entry(key, next));
        changed();
    }

    /** 9 個をまとめて 1 個にする作業台のレシピの結果（無ければ空）。レシピの読み直しで作り直す */
    private static final java.util.Map<net.minecraft.world.item.Item, ItemStack> COMPRESSED = new java.util.HashMap<>();
    private static net.minecraft.world.item.crafting.RecipeManager compressedFor;

    static ItemStack compressed(net.minecraft.world.level.Level level, ItemStack key) {
        var manager = level.getRecipeManager();
        if (manager != compressedFor) {
            COMPRESSED.clear();
            compressedFor = manager;
        }
        if (key.hasTag()) return ItemStack.EMPTY;
        return COMPRESSED.computeIfAbsent(key.getItem(), item -> {
            ItemStack probe = new ItemStack(item);
            for (var recipe : manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                var ingredients = recipe.getIngredients();
                if (ingredients.size() != 9 || recipe.isSpecial()) continue;
                boolean all = true;
                for (var ingredient : ingredients) if (ingredient.isEmpty() || !ingredient.test(probe)) { all = false; break; }
                if (!all) continue;
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (result.getCount() == 1 && !result.is(item)) return result.copy();
            }
            return ItemStack.EMPTY;
        });
    }

    /** 自動圧縮（1 秒に 1 回）: 残す数を超えた分を 9 個ずつ、入る分だけ、電力のある分だけ */
    public void compressContents(ChronoTier tier) {
        int budget = compressPerSecond(tier);
        for (int i = 0; i < entries.size() && budget > 0; i++) {
            Entry entry = entries.get(i);
            if (entry == null || entry.count < COMPRESS_KEEP + 9) continue;
            ItemStack result = compressed(level, entry.key);
            if (result.isEmpty()) continue;
            long ops = Math.min(Math.min((entry.count - COMPRESS_KEEP) / 9, budget), energy.getEnergyStored() / COMPRESS_COST);
            if (ops <= 0) return;
            ItemStack out = result.copyWithCount((int) Math.min(ops, Integer.MAX_VALUE));
            int fits = out.getCount() - insert(out, true).getCount();
            if (fits <= 0) continue;
            insert(result.copyWithCount(fits), false);
            entry.count -= 9L * fits;
            energy.consume(fits * COMPRESS_COST);
            budget -= fits;
            changed();
        }
    }

    /** 在庫を保って配る（1 秒に 1 回）: 搬出リンクの先それぞれに、決まりの物が決まりの数あるよう足す（1 個ごとに電力） */
    public void stockLinks(ChronoTier tier) {
        if (stockRules.isEmpty()) return;
        int budget = tier.transferPerTick * 20;
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pushes() || budget <= 0) continue;
            IItemHandler target = ChronoLinks.items(level, worldPosition, tier, link);
            if (target == null) continue;
            for (Entry rule : stockRules) {
                if (budget <= 0) break;
                long have = 0;
                for (int s = 0; s < target.getSlots(); s++) {
                    ItemStack in = target.getStackInSlot(s);
                    if (ItemStack.isSameItemSameTags(in, rule.key)) have += in.getCount();
                }
                int index = -1;
                for (int k = 0; k < entries.size(); k++) if (entries.get(k) != null && ItemStack.isSameItemSameTags(entries.get(k).key, rule.key)) index = k;
                while (have < rule.count && budget > 0 && index >= 0 && entries.get(index) != null) {
                    int want = (int) Math.min(Math.min(rule.count - have, budget), energy.getEnergyStored() / STOCK_COST);
                    if (want <= 0) return;
                    ItemStack piece = extract(index, want, true);
                    if (piece.isEmpty()) break;
                    ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(target, piece, true);
                    int moved = piece.getCount() - rest.getCount();
                    if (moved <= 0) break;
                    net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(target, extract(index, moved, false), false);
                    energy.consume(moved * STOCK_COST);
                    have += moved;
                    budget -= moved;
                }
            }
        }
    }

    @Override protected int maxReceive(ChronoTier tier) { return tier.powerRate * 4; }
    @Override protected boolean exposesEnergy() { return true; }
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
        if (ticks % 20 == 0) {                                                                     // 電力を使う便利な機能
            if (compress) compressContents(tier);
            stockLinks(tier);
        }
        // 搬入リンクの先から吸い込む。搬出リンクの先へは在庫の決まりの物だけ配る（取り出しは他のマシン・画面から）
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
        tag.putBoolean("Compress", compress);
        tag.putBoolean("PylonPower", pylonPower);
        ListTag rules = new ListTag();
        for (Entry rule : stockRules) {
            CompoundTag r = new CompoundTag();
            r.put("item", rule.key.save(new CompoundTag()));
            r.putLong("count", rule.count);
            rules.add(r);
        }
        tag.put("Stock", rules);
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
        compress = tag.getBoolean("Compress");
        pylonPower = tag.getBoolean("PylonPower");
        stockRules.clear();
        for (Tag t : tag.getList("Stock", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) t;
            ItemStack key = ItemStack.of(r.getCompound("item"));
            if (!key.isEmpty() && r.getLong("count") > 0) stockRules.add(new Entry(key, r.getLong("count")));
        }
        version++;
    }
}
