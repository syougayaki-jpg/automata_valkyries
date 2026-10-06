package com.automatavalkyries.chrono;

import com.automatavalkyries.machine.CableBlockEntity;
import com.automatavalkyries.machine.MachineEnergy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * クロノリンク・マシンの共通部分（docs/SPEC_CHRONOLINK_MACHINES.md §3）: ティア（ブロックの状態から読む）、電力、空間リンク、
 * 画面への同期。種類ごとの働きは {@link #work} を各サブクラスが書く。
 */
public abstract class ChronoMachineBlockEntity extends BlockEntity implements MenuProvider {

    /** 読み込まれているクロノリンク・マシン（テザー・パイロンの給電先を探す） */
    public static final Set<ChronoMachineBlockEntity> LOADED = ConcurrentHashMap.newKeySet();
    /** クライアントで読み込まれているマシン（チューナーを持っている間のリンクの表示。client/ChronoLinkOverlay） */
    public static final Set<ChronoMachineBlockEntity> CLIENT_LOADED = ConcurrentHashMap.newKeySet();

    /** 画面へ送る数値の数（共通 9 + 種類ごと） */
    public static final int DATA_COUNT = 48;
    protected static final int EXTRA = 9;

    protected final ChronoMachineType type;
    protected final ItemStackHandler items;
    protected final MachineEnergy energy;
    protected final List<ChronoLink> links = new ArrayList<>();
    private final LazyOptional<IEnergyStorage> energyCap;
    private final LazyOptional<IItemHandler> itemCap;
    /** 最後に見たティア（変わったら上限を合わせる） */
    private ChronoTier appliedTier;
    /** リンクの状態（届くリンクのビット）。20 Tick ごとに見直す */
    private int linkOkMask;
    protected int ticks;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> energy.getEnergyStored() >>> 16;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> energy.getMaxEnergyStored() >>> 16;
                case 4 -> tier().level;
                case 5 -> links.size();
                case 6 -> linkOkMask & 0xFFFF;
                case 7 -> modeBits(0);
                case 8 -> modeBits(8);
                default -> extraData(index - EXTRA) & 0xFFFF;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };

    protected ChronoMachineBlockEntity(BlockEntityType<?> beType, ChronoMachineType type, BlockPos pos, BlockState state) {
        super(beType, pos, state);
        this.type = type;
        this.items = new ItemStackHandler(type.slotCount) {
            @Override protected void onContentsChanged(int slot) { setChanged(); }
            @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return accepts(slot, stack); }
        };
        ChronoTier tier = tierFromState(state);
        this.energy = new MachineEnergy(tier.capacity, maxReceive(tier), maxExtract(tier), this::setChanged);
        this.appliedTier = tier;
        this.energyCap = LazyOptional.of(() -> energy);
        this.itemCap = LazyOptional.of(this::automation);
    }

    private static ChronoTier tierFromState(BlockState state) {
        return state.hasProperty(ChronoMachineBlock.TIER) ? ChronoTier.of(state.getValue(ChronoMachineBlock.TIER)) : ChronoTier.HIGH_END;
    }

    public ChronoMachineType type() { return type; }
    public ChronoTier tier() { return tierFromState(getBlockState()); }
    public ItemStackHandler items() { return items; }
    public MachineEnergy energy() { return energy; }
    public List<ChronoLink> links() { return Collections.unmodifiableList(links); }
    public ContainerData data() { return data; }

    // ---- 種類ごと ---------------------------------------------------------------

    /** 毎 Tick の働き（サーバー）。動いていれば true（前面が光る） */
    protected abstract boolean work(ChronoTier tier);

    /** 電力の受け取り・取り出しの上限 */
    protected int maxReceive(ChronoTier tier) { return tier.powerRate * 4; }
    protected int maxExtract(ChronoTier tier) { return 0; }

    /** その内部スロットに入れてよい物 */
    protected boolean accepts(int slot, ItemStack stack) {
        for (ChronoMachineType.SlotSpec spec : type.layout(tier())) {
            if (spec.index() == slot) return spec.role() != ChronoMachineType.Role.OUTPUT;
        }
        return false;
    }

    /** 画面へ送る種類ごとの数値（16 ビットまで） */
    protected int extraData(int index) { return 0; }

    /** ティアが変わったとき（電力の上限は共通で合わせる） */
    protected void onTierChanged(ChronoTier tier) {}

    // ---- 毎 Tick ---------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChronoMachineBlockEntity machine) {
        machine.ticks++;
        ChronoTier tier = machine.tier();
        if (tier != machine.appliedTier) {
            machine.appliedTier = tier;
            machine.energy.setLimits(tier.capacity, machine.maxReceive(tier), machine.maxExtract(tier));
            machine.onTierChanged(tier);
            machine.setChanged();
        }
        if (machine.ticks % 20 == 1) machine.refreshLinks(tier);
        boolean active = machine.work(tier);
        if (state.hasProperty(ChronoMachineBlock.LIT) && state.getValue(ChronoMachineBlock.LIT) != active) {
            level.setBlock(pos, state.setValue(ChronoMachineBlock.LIT, active), 3);
        }
    }

    private void refreshLinks(ChronoTier tier) {
        int mask = 0;
        for (int i = 0; i < links.size(); i++) {
            if (ChronoLinks.status(level, worldPosition, tier, links.get(i)) == ChronoLinks.Status.OK) mask |= 1 << i;
        }
        linkOkMask = mask;
    }

    private int modeBits(int from) {
        int bits = 0;
        for (int i = from; i < Math.min(links.size(), from + 8); i++) bits |= links.get(i).mode().ordinal() << ((i - from) * 2);
        return bits;
    }

    /** 使えるリンク（届く範囲で、ティアの枠の中の物） */
    protected List<ChronoLink> activeLinks(ChronoTier tier) {
        return links.subList(0, Math.min(links.size(), tier.linkSlots));
    }

    /** 搬入リンクから、内部のスロットへ引く（accept に合う物だけ） */
    protected void pullFromLinks(ChronoTier tier, int firstSlot, int slotCount, java.util.function.Predicate<ItemStack> accept) {
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pulls()) continue;
            IItemHandler source = ChronoLinks.items(level, worldPosition, tier, link);
            if (source != null) ChronoLinks.pull(source, items, firstSlot, slotCount, tier.transferPerTick, accept);
        }
    }

    /** 内部のスロットの物を、搬出リンクへ押し込む */
    protected void pushToLinks(ChronoTier tier, int firstSlot, int slotCount) {
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pushes()) continue;
            IItemHandler target = ChronoLinks.items(level, worldPosition, tier, link);
            if (target != null) ChronoLinks.push(items, firstSlot, slotCount, target, tier.transferPerTick);
        }
    }

    /** 隣の受け手（ケーブルを除く。ケーブルはケーブル側が引き取る）へ電力を渡す */
    protected void pushEnergyToNeighbors(int limit) {
        if (level == null || energy.getEnergyStored() <= 0) return;
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null || neighbor instanceof CableBlockEntity) continue;
            neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(target -> {
                if (!target.canReceive()) return;
                int accepted = target.receiveEnergy(Math.min(limit, energy.getEnergyStored()), false);
                if (accepted > 0) energy.consume(accepted);
            });
        }
    }

    // ---- 空間リンクの登録（クロノ・チューナー・画面のボタン） ----------------------------

    /** 登録できたら true（枠が埋まっている・同じ先が登録済みなら false） */
    public boolean addLink(ChronoLink link) {
        if (links.size() >= tier().linkSlots) return false;
        for (ChronoLink existing : links) {
            if (existing.pos().equals(link.pos()) && existing.dimension().equals(link.dimension()) && existing.face() == link.face()) return false;
        }
        links.add(link);
        linksChanged();
        return true;
    }

    /**
     * リンクを外す。リンク先もクロノリンク・マシンなら、そちらからこのマシンへのリンクも外す
     * （2026-10-07 ユーザー指定「機械側でリンクを消したとき、リンク先のリンクを消す」）
     */
    public void removeLink(int index) {
        if (index < 0 || index >= links.size()) return;
        ChronoLink removed = links.remove(index);
        linksChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.server.level.ServerLevel there = server.getServer().getLevel(removed.dimension());
            if (there != null && there.isLoaded(removed.pos()) && there.getBlockEntity(removed.pos()) instanceof ChronoMachineBlockEntity other) {
                boolean any = other.links.removeIf(l -> l.pos().equals(worldPosition) && l.dimension().equals(level.dimension()));
                if (any) other.linksChanged();
            }
        }
    }

    public void cycleLinkMode(int index) {
        if (index < 0 || index >= links.size()) return;
        links.set(index, links.get(index).withMode(links.get(index).mode().next()));
        linksChanged();
    }

    /** リンクが変わった: 保存し、クライアントへ送る（チューナーを持っている間の表示） */
    private void linksChanged() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** クライアントへ送るのはリンクだけ（中身・電力は画面が送る） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ChronoLink link : links) list.add(link.save());
        tag.put("links", list);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readLinks(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) readLinks(packet.getTag());
    }

    private void readLinks(CompoundTag tag) {
        links.clear();
        for (Tag t : tag.getList("links", Tag.TAG_COMPOUND)) links.add(ChronoLink.load((CompoundTag) t));
    }

    // ---- 保存 -----------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("items", items.serializeNBT());
        tag.putInt("energy", energy.getEnergyStored());
        ListTag list = new ListTag();
        for (ChronoLink link : links) list.add(link.save());
        tag.put("links", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(tag.getCompound("items"));
        for (int i = 0; i < items.getSlots(); i++) items.setStackInSlot(i, i < saved.getSlots() ? saved.getStackInSlot(i) : ItemStack.EMPTY);
        ChronoTier tier = tier();
        energy.setLimits(tier.capacity, maxReceive(tier), maxExtract(tier));
        energy.set(tag.getInt("energy"));
        links.clear();
        for (Tag t : tag.getList("links", Tag.TAG_COMPOUND)) links.add(ChronoLink.load((CompoundTag) t));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
        else if (level != null) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        CLIENT_LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
        CLIENT_LOADED.remove(this);
    }

    // ---- 能力 -----------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && exposesEnergy()) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    protected boolean exposesEnergy() { return true; }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    /** ホッパー・他 MOD からの出し入れ: 入れる役割のスロットへ入れ、結果のスロットからだけ出す */
    protected IItemHandler automation() {
        return new IItemHandler() {
            @Override public int getSlots() { return items.getSlots(); }
            @Override public @NotNull ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return items.isItemValid(slot, stack); }

            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return items.insertItem(slot, stack, simulate);
            }

            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                for (ChronoMachineType.SlotSpec spec : type.layout(tier())) {
                    if (spec.index() == slot && spec.role() == ChronoMachineType.Role.OUTPUT) return items.extractItem(slot, amount, simulate);
                }
                return ItemStack.EMPTY;
            }
        };
    }

    // ---- 画面 -----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automata_valkyries." + type.id);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChronoMenu(id, inventory, this);
    }

    /** 画面を開くときに送る: 位置・種類・ティア・リンク（ツールチップ用） */
    public void writeMenuData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeEnum(type);
        buf.writeVarInt(tier().level);
        buf.writeVarInt(links.size());
        for (ChronoLink link : links) link.write(buf);
    }

    /** 中身をばらまく（壊されたとき。ヴォイド・リポジトリは中身をアイテムに残すので何もしない） */
    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < items.getSlots(); i++) {
            net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
        }
    }
}
