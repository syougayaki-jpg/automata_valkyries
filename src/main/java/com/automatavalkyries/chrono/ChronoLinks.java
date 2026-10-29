package com.automatavalkyries.chrono;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * 空間リンクの先を引き、アイテム・電力をテレポートで出し入れする（docs/SPEC_CHRONOLINK_MACHINES.md §3.3）。
 * 届かない（距離・ディメンション）・読み込まれていない（T4 以外）リンクは飛ばす。T4 は相手のチャンクを一時的に読み込む。
 */
public final class ChronoLinks {

    /** T4 のリンク先を読み込んでおくチケット（15 秒。使うたびに付け直す） */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("automata_valkyries_chrono_link", Comparator.comparingLong(ChunkPos::toLong), 300);

    private ChronoLinks() {}

    /** リンクの状態（画面に色で出す） */
    public enum Status { OK, OUT_OF_RANGE, UNLOADED, MISSING }

    public static Status status(Level from, BlockPos origin, ChronoTier tier, ChronoLink link) {
        if (!(from instanceof ServerLevel server)) return Status.MISSING;
        ServerLevel target = server.getServer().getLevel(link.dimension());
        if (target == null) return Status.MISSING;
        boolean same = target == server;
        if (!tier.reaches(same, same ? origin.distSqr(link.pos()) : 0)) return Status.OUT_OF_RANGE;
        if (!target.isLoaded(link.pos())) {
            if (!tier.reachUnloaded) return Status.UNLOADED;
            keepLoaded(target, link.pos());
            return Status.UNLOADED; // 読み込みを頼んだ。次の Tick から届く
        }
        return target.getBlockEntity(link.pos()) == null ? Status.MISSING : Status.OK;
    }

    @Nullable
    public static BlockEntity resolve(Level from, BlockPos origin, ChronoTier tier, ChronoLink link) {
        if (status(from, origin, tier, link) != Status.OK) return null;
        ServerLevel target = ((ServerLevel) from).getServer().getLevel(link.dimension());
        if (target == null) return null;
        if (tier.reachUnloaded) keepLoaded(target, link.pos());
        return target.getBlockEntity(link.pos());
    }

    public static void keepLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
    }

    @Nullable
    public static IItemHandler items(Level from, BlockPos origin, ChronoTier tier, ChronoLink link) {
        BlockEntity be = resolve(from, origin, tier, link);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, link.face()).resolve().orElse(null);
    }

    @Nullable
    public static IEnergyStorage energy(Level from, BlockPos origin, ChronoTier tier, ChronoLink link) {
        BlockEntity be = resolve(from, origin, tier, link);
        return be == null ? null : be.getCapability(ForgeCapabilities.ENERGY, link.face()).resolve().orElse(null);
    }

    /**
     * source から accept に合う物を最大 limit 個、into の slots へ移す（into は内部の入れ物。insertItem で入る分だけ）。
     * 移した数を返す
     */
    public static int pull(IItemHandler source, IItemHandler into, int firstSlot, int slotCount, int limit, Predicate<ItemStack> accept) {
        int moved = 0;
        for (int s = 0; s < source.getSlots() && moved < limit; s++) {
            ItemStack peek = source.extractItem(s, limit - moved, true);
            if (peek.isEmpty() || !accept.test(peek)) continue;
            ItemStack rest = peek.copy();
            for (int t = firstSlot; t < firstSlot + slotCount && !rest.isEmpty(); t++) rest = into.insertItem(t, rest, false);
            int taken = peek.getCount() - rest.getCount();
            if (taken > 0) {
                source.extractItem(s, taken, false);
                moved += taken;
            }
        }
        return moved;
    }

    /** from の slots の物を、target へ最大 limit 個押し込む（入った数を返す） */
    public static int push(IItemHandler from, int firstSlot, int slotCount, IItemHandler target, int limit) {
        int moved = 0;
        for (int s = firstSlot; s < firstSlot + slotCount && moved < limit; s++) {
            ItemStack peek = from.extractItem(s, limit - moved, true);
            if (peek.isEmpty()) continue;
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, peek, false);
            int sent = peek.getCount() - rest.getCount();
            if (sent > 0) {
                from.extractItem(s, sent, false);
                moved += sent;
            }
        }
        return moved;
    }
}
