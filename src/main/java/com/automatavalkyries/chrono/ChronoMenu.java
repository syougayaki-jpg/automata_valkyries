package com.automatavalkyries.chrono;

import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.RepositoryContentsPacket;
import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * クロノリンク・マシンの画面（全種類で共通）。スロットは {@link ChronoMachineType#layout} のとおり。
 * ボタン（{@link #clickMenuButton}）: 100 + i = リンク i の向きを切り替え、200 + i = リンク i を外す、1000 + i = リポジトリの i 番目を 1 スタック取り出す。
 */
public class ChronoMenu extends AbstractContainerMenu {

    public static final int BUTTON_MODE = 100, BUTTON_REMOVE = 200, BUTTON_EXTRACT = 1000, BUTTON_SORT = 99;
    /** ヴォイド・リポジトリ: 自動圧縮の入切・在庫の決まりを次の段へ（+ 番号）・前の段へ（+ 番号）（2026-10-29） */
    public static final int BUTTON_COMPRESS = 98, BUTTON_PYLON = 97, BUTTON_STOCK = 1_000_000, BUTTON_STOCK_BACK = 2_000_000;

    private final ChronoMachineType type;
    private final ChronoTier tier;
    private final ContainerData data;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    @Nullable private final ChronoMachineBlockEntity machine;
    /** リンク（ツールチップ用。クライアントは開いたときの物を持ち、外したら自分でも消す） */
    public final List<ChronoLink> links;
    /** リポジトリの中身（クライアント側の写し） */
    public final List<VoidRepositoryBlockEntity.Entry> repository = new ArrayList<>();
    /** リポジトリの在庫の決まりの数（中身の番号ごと）と自動圧縮の入切（クライアント側の写し） */
    public int[] repositoryStock = new int[0];
    public boolean repositoryCompress, repositoryPylonPower;
    private int sentVersion = -1;
    private final int machineSlots;
    /** 開いたプレイヤー（リポジトリの中身を送る相手） */
    private final Player owner;
    /** T4 のアイテムポケットから遠くのリポジトリを開いた（距離・ディメンションを見ず、開いている間チャンクを読み込んでおく。2026-10-29） */
    private boolean remote;

    /** クライアント */
    public ChronoMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(ChronoMachineType.class), ChronoTier.of(buf.readVarInt()), readLinks(buf));
    }

    private static List<ChronoLink> readLinks(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ChronoLink> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(ChronoLink.read(buf));
        return list;
    }

    private ChronoMenu(int id, Inventory inventory, BlockPos pos, ChronoMachineType type, ChronoTier tier, List<ChronoLink> links) {
        this(id, inventory, pos, type, tier, links, new ItemStackHandler(type.slotCount), new SimpleContainerData(ChronoMachineBlockEntity.DATA_COUNT), null);
    }

    /** サーバー: 遠くから開く（T4 のアイテムポケットの「どこでもリポジトリ」） */
    public static ChronoMenu remote(int id, Inventory inventory, ChronoMachineBlockEntity machine) {
        ChronoMenu menu = new ChronoMenu(id, inventory, machine);
        menu.remote = true;
        return menu;
    }

    /** サーバー */
    public ChronoMenu(int id, Inventory inventory, ChronoMachineBlockEntity machine) {
        this(id, inventory, machine.getBlockPos(), machine.type(), machine.tier(), new ArrayList<>(machine.links()),
                machine.items(), machine.data(), machine);
    }

    private ChronoMenu(int id, Inventory inventory, BlockPos pos, ChronoMachineType type, ChronoTier tier, List<ChronoLink> links,
                       IItemHandler items, ContainerData data, @Nullable ChronoMachineBlockEntity machine) {
        super(ModChrono.MENU.get(), id);
        this.type = type;
        this.tier = tier;
        this.data = data;
        this.pos = pos;
        this.links = new ArrayList<>(links);
        this.machine = machine;
        this.owner = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        List<ChronoMachineType.SlotSpec> layout = type.layout(tier);
        for (ChronoMachineType.SlotSpec spec : layout) {
            boolean output = spec.role() == ChronoMachineType.Role.OUTPUT;
            addSlot(new SlotItemHandler(items, spec.index(), spec.x(), spec.y()) {
                @Override public boolean mayPlace(ItemStack stack) { return !output && super.mayPlace(stack); }
            });
        }
        machineSlots = layout.size();
        int px = ChronoMachineType.PLAYER_X, py = ChronoMachineType.PLAYER_Y;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, px + col * 18, py + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, px + col * 18, py + 58));
        addDataSlots(data);
    }

    public ChronoMachineType type() { return type; }
    /** サーバーの機械（クライアントは null） */
    @Nullable public ChronoMachineBlockEntity machine() { return machine; }
    public ChronoTier tier() { return tier; }

    public int energy() { return (data.get(0) & 0xFFFF) | (data.get(1) << 16); }
    public int capacity() { return (data.get(2) & 0xFFFF) | (data.get(3) << 16); }
    public int linkCount() { return data.get(5); }
    public boolean linkOk(int i) { return ((data.get(6) & 0xFFFF) >> i & 1) != 0; }
    public ChronoLink.Mode linkMode(int i) {
        int bits = (i < 8 ? data.get(7) : data.get(8)) & 0xFFFF;
        return ChronoLink.Mode.values()[Math.min(2, (bits >> ((i % 8) * 2)) & 3)];
    }
    public int extra(int i) { return data.get(ChronoMachineBlockEntity.EXTRA + i) & 0xFFFF; }
    /** 共通の数値をそのまま（兵姫のオペレーターの強化など） */
    public int data(int index) { return data.get(index); }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            // クライアント: 外したリンクを写しからも消す
            if (id >= BUTTON_REMOVE && id < BUTTON_REMOVE + 16 && id - BUTTON_REMOVE < links.size()) links.remove(id - BUTTON_REMOVE);
            return true;
        }
        if (id == BUTTON_SORT && machine instanceof VoidRepositoryBlockEntity repo) repo.sortContents(); // 整理
        else if (id == BUTTON_COMPRESS && machine instanceof VoidRepositoryBlockEntity repo) repo.toggleCompress();
        else if (id == BUTTON_PYLON && machine instanceof VoidRepositoryBlockEntity repo) repo.togglePylonPower();
        else if (id >= BUTTON_STOCK_BACK && machine instanceof VoidRepositoryBlockEntity repo) repo.cycleStock(id - BUTTON_STOCK_BACK, true);
        else if (id >= BUTTON_STOCK && machine instanceof VoidRepositoryBlockEntity repo) repo.cycleStock(id - BUTTON_STOCK, false);
        else if (id >= BUTTON_MODE && id < BUTTON_MODE + 16) machine.cycleLinkMode(id - BUTTON_MODE);
        else if (id >= BUTTON_REMOVE && id < BUTTON_REMOVE + 16) {
            int index = id - BUTTON_REMOVE;
            ChronoLink removed = index < machine.links().size() ? machine.links().get(index) : null;
            machine.removeLink(index);
            if (removed != null) forgetInTuners(player, removed);
        }
        else if (id >= BUTTON_EXTRACT && id < BUTTON_STOCK && machine instanceof VoidRepositoryBlockEntity repo) {
            ItemStack taken = repo.extract(id - BUTTON_EXTRACT, 64, false);
            if (!taken.isEmpty() && !player.getInventory().add(taken)) player.drop(taken, false);
        }
        return true;
    }

    /**
     * 外したリンクの先を、持ち物のクロノ・チューナーが覚えていれば忘れさせる
     * （2026-10-07 ユーザー指定「機械側でリンクを消したとき、チューナーが覚えた場所も消す」）
     */
    private static void forgetInTuners(Player player, ChronoLink removed) {
        String dim = removed.dimension().location().toString();
        for (ItemStack stack : player.getInventory().items) forget(stack, removed, dim);
        for (ItemStack stack : player.getInventory().offhand) forget(stack, removed, dim);
    }

    private static void forget(ItemStack stack, ChronoLink removed, String dim) {
        if (!(stack.getItem() instanceof com.automatavalkyries.item.ChronoTunerItem) || stack.getTag() == null || !stack.getTag().contains("Target")) return;
        var target = stack.getTag().getCompound("Target");
        if (target.getLong("pos") == removed.pos().asLong() && dim.equals(target.getString("dim"))) stack.removeTagKey("Target");
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (remote && machine != null && machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level && owner.tickCount % 20 == 0) {
            ChronoLinks.keepLoaded(level, pos);
        }
        if (machine instanceof VoidRepositoryBlockEntity repo && repo.version() != sentVersion) {
            sentVersion = repo.version();
            if (owner instanceof ServerPlayer player) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), RepositoryContentsPacket.of(containerId, repo));
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (remote) return machine != null && !machine.isRemoved();
        return stillValid(access, player, player.level().getBlockState(pos).getBlock());
    }

    /** Shift クリック: マシン → 持ち物、持ち物 → 入れられるスロット（リポジトリは中へ） */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (machine instanceof VoidRepositoryBlockEntity repo) {
            slot.set(repo.insert(stack, false));
            return ItemStack.EMPTY; // 入れた分で終わり（同じスロットを繰り返さない）
        } else {
            if (!moveItemStackTo(stack, 0, machineSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
