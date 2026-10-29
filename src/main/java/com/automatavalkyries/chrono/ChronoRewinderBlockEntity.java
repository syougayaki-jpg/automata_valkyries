package com.automatavalkyries.chrono;

import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * 時間巻き戻し機（2026-10-29 ユーザー指定「時間を扱う機械を直接追加して」。質問で: 時間加速器・時間巻き戻し。数値は【仮】）: 入れた物の時間を巻き戻す。
 * <ul>
 *   <li>傷んだ道具・防具・兵装（耐久のある物）: 耐久を元に戻す（1 Tick に ティア×2 ずつ、1 ずつに電力）。直り切ったら結果の枠へ</li>
 *   <li>かまどで焼いた物: 焼く前に戻す（焼き鳥 → 生の鶏肉、ガラス → 砂、石 → 丸石など。かまどのレシピを逆にたどる）。
 *       鉱石・原石へ戻す逆は、既定では無し（クロノ・スメルターの倍加と合わせて素材が増え続けるので）。
 *       設定（automata_valkyries-machines.toml の [rewinder] ores）で許せる（2026-10-29 ユーザー指定）</li>
 *   <li>空間リンクで入れ・出しでき、兵姫のオペレーターで速く・電力が少なくなる</li>
 * </ul>
 */
public class ChronoRewinderBlockEntity extends ChronoMachineBlockEntity {

    public static final int REVERSE_TIME = 200, REVERSE_ENERGY = 20_000, REPAIR_ENERGY = 100;
    public static final int MODE_NONE = 0, MODE_REPAIR = 1, MODE_REVERSE = 2;
    private static final TagKey<Item> ORES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "ores"));
    private static final TagKey<Item> RAW = ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "raw_materials"));

    private int progress, maxProgress, mode;
    /** 焼いた物 → 焼く前（無ければ EMPTY） */
    private final Map<Item, ItemStack> reverse = new HashMap<>();
    /** 覚えた逆の表が、鉱石へ戻すのを許した設定のときの物か（設定が変わったら作り直す） */
    private boolean reverseWithOres;

    public ChronoRewinderBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.REWINDER_ENTITY.get(), ChronoMachineType.CHRONO_REWINDER, pos, state);
    }

    static boolean repairable(ItemStack s) {
        return !s.isEmpty() && s.isDamageableItem() && s.isDamaged();
    }

    /** 焼く前の物（かまどのレシピを逆にたどる。鉱石・原石へは設定で許したときだけ戻す。無ければ EMPTY） */
    public ItemStack reverseOf(ItemStack cooked) {
        if (level == null || cooked.isEmpty()) return ItemStack.EMPTY;
        boolean ores = com.automatavalkyries.config.MachineConfig.rewindOres();
        if (ores != reverseWithOres) {
            reverse.clear();
            reverseWithOres = ores;
        }
        return reverse.computeIfAbsent(cooked.getItem(), item -> {
            for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                if (recipe.getResultItem(level.registryAccess()).getItem() != item || recipe.getIngredients().isEmpty()) continue;
                Ingredient in = recipe.getIngredients().get(0);
                for (ItemStack raw : in.getItems()) {
                    if (raw.isEmpty() || raw.getItem() == item || (!ores && (raw.is(ORES) || raw.is(RAW)))) continue;
                    return new ItemStack(raw.getItem());
                }
            }
            return ItemStack.EMPTY;
        }).copy();
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot == 0 && (repairable(stack) || !reverseOf(stack).isEmpty());
    }

    @Override
    protected boolean work(ChronoTier tier) {
        if (ticks % 4 == 0) pullFromLinks(tier, 0, 1, s -> repairable(s) || !reverseOf(s).isEmpty());
        if (ticks % 4 == 0) pushToLinks(tier, 1, 1);
        ItemStack in = items.getStackInSlot(0);
        if (repairable(in)) return repair(tier, in);
        ItemStack raw = reverseOf(in);
        if (!raw.isEmpty()) return rewind(tier, raw);
        if (!in.isEmpty() && in.isDamageableItem() && items.getStackInSlot(1).isEmpty()) { // 直り切った物は結果の枠へ
            items.setStackInSlot(1, items.extractItem(0, 1, false));
            setChanged();
        }
        progress = 0;
        maxProgress = 0;
        mode = MODE_NONE;
        return false;
    }

    private boolean repair(ChronoTier tier, ItemStack in) {
        mode = MODE_REPAIR;
        int damage = in.getDamageValue();
        int step = Math.max(1, Math.round(tier.level * 2 * operator().speed()));
        step = Math.min(step, damage);
        if (!energy.consume(scaledEnergy(tier, REPAIR_ENERGY) * step)) return false;
        in.setDamageValue(damage - step);
        maxProgress = in.getMaxDamage();
        progress = maxProgress - in.getDamageValue();
        if (!in.isDamaged() && items.getStackInSlot(1).isEmpty()) {
            items.setStackInSlot(1, items.extractItem(0, 1, false));
            progress = 0;
        }
        setChanged();
        return true;
    }

    private boolean rewind(ChronoTier tier, ItemStack raw) {
        mode = MODE_REVERSE;
        ItemStack out = items.getStackInSlot(1);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameTags(out, raw) || out.getCount() >= out.getMaxStackSize())) {
            progress = 0;
            return false;
        }
        maxProgress = scaledTime(tier, REVERSE_TIME);
        int perTick = Math.max(1, scaledEnergy(tier, REVERSE_ENERGY) / maxProgress);
        if (!energy.consume(perTick)) return false;
        if (++progress >= maxProgress) {
            progress = 0;
            items.extractItem(0, 1, false);
            if (out.isEmpty()) items.setStackInSlot(1, raw);
            else out.grow(1);
            setChanged();
        }
        return true;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> maxProgress > 0 ? (int) (1000L * progress / maxProgress) : 0;
            case 1 -> mode;
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
