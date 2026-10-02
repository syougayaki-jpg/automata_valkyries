package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 基礎文明マシンの本体（種類は MachineKind）。電力（FE）を蓄え、燃料発電機は燃料を燃やして作り、
 * 加工機は電力を使って材料を加工する。燃料発電機・蓄電器は隣のマシンへ電力を渡す（ケーブルはケーブル側が引き取る）。
 */
public class MachineBlockEntity extends BlockEntity implements MenuProvider {

    private final MachineKind kind;
    private final ItemStackHandler items;
    private final MachineEnergy energy;
    private final LazyOptional<IEnergyStorage> energyCap;
    private final LazyOptional<IItemHandler> itemCap;

    private int progress, maxProgress;
    private int burn, burnMax;

    /** 画面への同期: 電力は 16 ビットずつに分けて送る（ContainerData は short で送られる） */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> energy.getEnergyStored() >>> 16;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> energy.getMaxEnergyStored() >>> 16;
                case 4 -> progress;
                case 5 -> maxProgress;
                case 6 -> burn;
                case 7 -> burnMax;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() { return DATA_COUNT; }
    };
    public static final int DATA_COUNT = 8;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.blockEntityType(((MachineBlock) state.getBlock()).kind()), pos, state);
        this.kind = ((MachineBlock) state.getBlock()).kind();
        this.items = new ItemStackHandler(kind.slots()) {
            @Override
            protected void onContentsChanged(int slot) { setChanged(); }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                if (kind.hasFuel()) return ForgeHooks.getBurnTime(stack, null) > 0;
                if (kind.hasCharge()) return stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
                return slot != kind.outputSlot();
            }
        };
        this.energy = new MachineEnergy(kind.capacity, kind.maxReceive, kind.maxExtract, this::setChanged);
        this.energyCap = LazyOptional.of(() -> energy);
        this.itemCap = LazyOptional.of(() -> new AutomationItemHandler(items, kind));
    }

    public MachineKind kind() { return kind; }
    public ItemStackHandler items() { return items; }
    public MachineEnergy energy() { return energy; }
    public ContainerData data() { return data; }

    // ---- 毎 Tick（サーバー） ---------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        boolean active = switch (machine.kind.role) {
            case GENERATOR -> machine.tickGenerator();
            case BATTERY -> machine.tickCharging();
            default -> machine.tickProcessing();
        };
        if (machine.kind.pushes()) machine.pushToNeighbors();
        if (state.hasProperty(MachineBlock.LIT) && state.getValue(MachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(MachineBlock.LIT, active), 3);
        }
    }

    private boolean tickGenerator() {
        if (burn > 0) {
            burn--;
            energy.produce(kind.generation);
            return true;
        }
        if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) return false;
        ItemStack fuel = items.getStackInSlot(0);
        int time = ForgeHooks.getBurnTime(fuel, null);
        if (time <= 0) return false;
        burn = burnMax = time;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) items.setStackInSlot(0, remainder);
        setChanged();
        return true;
    }

    /** 蓄電器: 充電スロットのアイテム（FE を持つ物）へ充電する。充電している間は光る */
    private boolean tickCharging() {
        ItemStack stack = items.getStackInSlot(0);
        if (stack.isEmpty() || energy.getEnergyStored() <= 0) return false;
        return stack.getCapability(ForgeCapabilities.ENERGY).map(target -> {
            int offered = energy.extractEnergy(kind.maxExtract, true);
            int accepted = target.receiveEnergy(offered, false);
            energy.extractEnergy(accepted, false);
            return accepted > 0;
        }).orElse(false);
    }

    private boolean tickProcessing() {
        Recipe current = findRecipe();
        if (current == null || !outputFits(current.result())) {
            if (progress != 0) { progress = 0; setChanged(); }
            maxProgress = 0;
            return false;
        }
        maxProgress = current.time();
        int perTick = Math.max(1, current.energy() / Math.max(1, current.time()));
        if (!energy.consume(perTick)) return false;
        progress++;
        if (progress >= maxProgress) {
            for (int i = 0; i < current.slots().length; i++) items.extractItem(current.slots()[i], current.counts()[i], false);
            // 結果のスロットは「入れられない」設定（isItemValid）なので、insertItem を通さず直接置く
            ItemStack out = items.getStackInSlot(kind.outputSlot());
            if (out.isEmpty()) items.setStackInSlot(kind.outputSlot(), current.result().copy());
            else out.grow(current.result().getCount());
            progress = 0;
        }
        setChanged();
        return true;
    }

    /** 今の材料で作れる物（材料のスロットと個数・結果・電力・時間） */
    private record Recipe(int[] slots, int[] counts, ItemStack result, int energy, int time) {}

    @Nullable
    private Recipe findRecipe() {
        if (level == null) return null;
        if (kind.role == MachineKind.Role.FURNACE) {
            ItemStack input = items.getStackInSlot(0);
            if (input.isEmpty()) return null;
            return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level)
                    .map(r -> new Recipe(new int[]{0}, new int[]{1}, r.getResultItem(level.registryAccess()),
                            kind.scaleEnergy(MachineKind.FURNACE_USE * MachineKind.FURNACE_TIME), kind.scaleTime(MachineKind.FURNACE_TIME)))
                    .orElse(null);
        }
        RecipeWrapper container = new RecipeWrapper(items);
        for (MachineRecipe recipe : level.getRecipeManager().getAllRecipesFor(kind.recipeType.get())) {
            int[] slots = recipe.match(container, kind.inputs);
            if (slots == null) continue;
            int[] counts = new int[slots.length];
            for (int i = 0; i < counts.length; i++) counts[i] = recipe.inputs().get(i).count();
            return new Recipe(slots, counts, recipe.result(), kind.scaleEnergy(recipe.energy()), kind.scaleTime(recipe.time()));
        }
        return null;
    }

    private boolean outputFits(ItemStack result) {
        ItemStack out = items.getStackInSlot(kind.outputSlot());
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** 燃料発電機・蓄電器: 隣のマシン（ケーブルを除く）へ電力を渡す。蓄電器同士では渡さない（行ったり来たりするので） */
    private void pushToNeighbors() {
        if (level == null || energy.getEnergyStored() <= 0) return;
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null || neighbor instanceof CableBlockEntity) continue;
            if (kind.hasCharge() && neighbor instanceof MachineBlockEntity other && other.kind.hasCharge()) continue;
            neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(target -> {
                if (!target.canReceive()) return;
                int offered = energy.extractEnergy(kind.maxExtract, true);
                int accepted = target.receiveEnergy(offered, false);
                energy.extractEnergy(accepted, false);
            });
        }
    }

    // ---- 保存 -----------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("energy", energy.getEnergyStored());
        tag.putInt("progress", progress);
        tag.putInt("burn", burn);
        tag.putInt("burnMax", burnMax);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 保存時とスロットの数が違っても（古い蓄電器は 0 枠だった）今の数のまま、入る分だけ戻す
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(tag.getCompound("items"));
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        energy.set(tag.getInt("energy"));
        progress = tag.getInt("progress");
        burn = tag.getInt("burn");
        burnMax = tag.getInt("burnMax");
    }

    // ---- 能力（電力・アイテム） --------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    // ---- 画面 -----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automata_valkyries." + kind.id);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MachineMenu(id, inventory, this);
    }

    /**
     * ホッパー等からの出し入れ: 材料・燃料・充電のスロットへだけ入れられ、結果のスロットからだけ取り出せる。
     * 充電スロットは満タン（それ以上受け取れない）になった物だけ取り出せる
     */
    private record AutomationItemHandler(ItemStackHandler inner, MachineKind kind) implements IItemHandler {
        @Override public int getSlots() { return inner.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return inner.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return inner.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return inner.isItemValid(slot, stack); }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (kind.processes() && slot == kind.outputSlot()) return stack;
            return inner.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (kind.hasCharge()) {
                ItemStack stack = inner.getStackInSlot(slot);
                boolean full = stack.getCapability(ForgeCapabilities.ENERGY)
                        .map(e -> e.getEnergyStored() >= e.getMaxEnergyStored()).orElse(true);
                return full ? inner.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }
            if (slot != kind.outputSlot()) return ItemStack.EMPTY;
            return inner.extractItem(slot, amount, simulate);
        }
    }
}
