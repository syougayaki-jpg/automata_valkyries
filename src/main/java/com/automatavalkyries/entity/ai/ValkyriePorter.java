package com.automatavalkyries.entity.ai;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.item.unit.ValkyrieUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * ロジック: ポーター（運搬のお手伝い。2026-10-06 ユーザー指定「アイテムを運んでくれるお手伝いしてくれるチップ」、運び方は「全部（モード切り替え）」）。
 * <ul>
 *   <li>モード 0「拾った物を指定のチェストへ」: お掃除の運び先を、チップで覚えたチェストにする（{@link ValkyrieDaily.Clean}）</li>
 *   <li>モード 1「チェスト間の運搬」: 運び元のチェストの中身を、拾った物の枠に入るだけ取り出して運び先へ入れる、を繰り返す（追従のとき以外）</li>
 *   <li>モード 2「持ち主の荷物持ち」: 追従中、持ち主の持ち物がいっぱいなら、持ち主のまわりの落ちている物を代わりに拾って預かる</li>
 *   <li>指示リモコンの「運んで」で運び先へ預かった物を入れに行き、「渡して」で持ち主に渡す（どのモードでも）</li>
 * </ul>
 * 運び先・運び元は同じディメンションで、兵姫から {@link #REACH} ブロック以内の物だけ使う
 */
public class ValkyriePorter extends Goal {

    public static final double REACH = 64.0, OWNER_PICKUP = 8.0, SCAVENGE_RANGE = 10.0;
    /** リモコンの指示（0 = 無し、1 = 運び先へ、2 = 持ち主へ） */
    public static final int TRIP_NONE = 0, TRIP_HOME = 1, TRIP_OWNER = 2;

    private final ValkyrieEntity v;
    /** 今の用事: 0 = 運び先へ入れる、1 = 運び元から取る、2 = 持ち主に渡す、3 = 持ち主のまわりの物を拾う */
    private int job;
    @Nullable private BlockPos target;
    @Nullable private ItemEntity item;
    private int repath, scan, stuck;

    public ValkyriePorter(ValkyrieEntity v) {
        this.v = v;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** 挿しているポーターのチップ（無ければ空） */
    static ItemStack chip(ValkyrieEntity v) {
        return v.getInventory().chip(ValkyrieUnits.ChipKind.PORTER);
    }

    /** チップで覚えた運び先・運び元（使えなければ null） */
    @Nullable
    static BlockPos spot(ValkyrieEntity v, ItemStack chip, String key) {
        BlockPos p = ValkyrieUnits.porterPos(chip, key, v.level());
        if (p == null || p.distSqr(v.blockPosition()) > REACH * REACH || !v.level().isLoaded(p)) return null;
        return handler(v, p) != null ? p : null;
    }

    @Nullable
    static IItemHandler handler(ValkyrieEntity v, BlockPos p) {
        BlockEntity be = v.level().getBlockEntity(p);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
    }

    /** お掃除（モード 0）の運び先。モード 0 でなければ null */
    @Nullable
    public static BlockPos cleanDestination(ValkyrieEntity v) {
        ItemStack chip = chip(v);
        return !chip.isEmpty() && ValkyrieUnits.porterMode(chip) == 0 ? spot(v, chip, "Dest") : null;
    }

    private boolean ownerFull(Player owner) {
        return !owner.isCreative() && owner.getInventory().getFreeSlot() < 0;
    }

    @Nullable
    private ItemEntity itemNearOwner(Player owner) {
        return itemNearOwner(owner, OWNER_PICKUP);
    }

    @Nullable
    private ItemEntity itemNearOwner(Player owner, double range) {
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new AABB(owner.blockPosition()).inflate(range, 4, range),
                e -> e.isAlive() && !e.hasPickUpDelay() && !v.orders.excludes(e.getItem()))) {
            double d = e.distanceToSqr(v);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** 運び元に取り出せる物があるか */
    private boolean sourceHasItems(BlockPos src) {
        IItemHandler h = handler(v, src);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (!h.extractItem(i, 64, true).isEmpty()) return true;
        return false;
    }

    /** 今やることを決める。やることがあれば true */
    private boolean plan() {
        ValkyrieCombat.Brain b = v.combat();
        ItemStack chip = chip(v);
        Player owner = v.getOwner();
        item = null;
        target = null;
        if (b.porterTrip == TRIP_OWNER) {
            if (owner != null && v.getInventory().hasPickup()) {
                job = 2;
                return true;
            }
            b.porterTrip = TRIP_NONE;
        }
        if (b.porterTrip == TRIP_HOME) {
            BlockPos dest = chip.isEmpty() ? null : spot(v, chip, "Dest");
            if (dest != null && v.getInventory().hasPickup()) {
                job = 0;
                target = dest;
                return true;
            }
            b.porterTrip = TRIP_NONE;
            if (dest == null) v.talk("porter_no_dest");
        }
        // ロジック: スカベンジャー: 追従中も、持ち主のまわりの落ちている物を拾う（持ち主の持ち物がいっぱいでなくても）
        if (v.getMode() == Mode.FOLLOW && owner != null && !v.getInventory().pickupFull() && !ValkyrieDaily.noPickup(v)
                && v.getInventory().hasChip(ValkyrieUnits.ChipKind.SCAVENGER)) {
            item = itemNearOwner(owner, SCAVENGE_RANGE);
            if (item != null) {
                job = 3;
                return true;
            }
        }
        if (chip.isEmpty()) return false;
        int mode = ValkyrieUnits.porterMode(chip);
        if (mode == 1 && v.getMode() != Mode.FOLLOW) {
            BlockPos src = spot(v, chip, "Source"), dest = spot(v, chip, "Dest");
            if (src == null || dest == null) return false;
            if (v.getInventory().hasPickup() && (v.getInventory().pickupFull() || !sourceHasItems(src))) {
                job = 0;
                target = dest;
                return true;
            }
            if (!v.getInventory().pickupFull() && sourceHasItems(src)) {
                job = 1;
                target = src;
                return true;
            }
            return false;
        }
        if (mode == 2 && v.getMode() == Mode.FOLLOW && owner != null && ownerFull(owner) && !v.getInventory().pickupFull() && !ValkyrieDaily.noPickup(v)) {
            item = itemNearOwner(owner);
            if (item != null) {
                job = 3;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canUse() {
        if (!ValkyrieDaily.idle(v)) return false;
        if (v.combat().porterTrip == TRIP_NONE && --scan > 0) return false;
        scan = 20;
        return plan();
    }

    @Override
    public boolean canContinueToUse() {
        if (!ValkyrieDaily.idle(v) || stuck > 400) return false;
        return switch (job) {
            case 0 -> target != null && v.getInventory().hasPickup();
            case 1 -> target != null && !v.getInventory().pickupFull();
            case 2 -> v.getOwner() != null && v.getInventory().hasPickup();
            default -> item != null && item.isAlive() && !v.getInventory().pickupFull();
        };
    }

    @Override
    public void start() {
        repath = 0;
        stuck = 0;
    }

    @Override
    public void stop() {
        if (stuck > 400) v.combat().porterTrip = TRIP_NONE; // 届かなかった
        target = null;
        item = null;
        v.getNavigation().stop();
    }

    @Override
    public void tick() {
        stuck++;
        switch (job) {
            case 0, 1 -> {
                if (target == null) return;
                Vec3 c = Vec3.atCenterOf(target);
                double d2 = v.position().distanceToSqr(c);
                if (d2 < 3.2 * 3.2 || (v.getNavigation().isDone() && d2 < 5.0 * 5.0)) {
                    if (job == 0) deliver(target);
                    else take(target);
                    stuck = 0;
                    if (!plan()) target = null;
                    return;
                }
                if (--repath <= 0) {
                    repath = 10;
                    BlockPos stand = ValkyrieDaily.standBeside(v, target);
                    if (stand != null) v.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, ValkyrieGoals.pathSpeed(v, 1.15));
                    else v.getNavigation().moveTo(c.x, c.y, c.z, ValkyrieGoals.pathSpeed(v, 1.15));
                }
            }
            case 2 -> {
                Player owner = v.getOwner();
                if (owner == null) return;
                v.getLookControl().setLookAt(owner, 30.0F, 30.0F);
                if (v.distanceTo(owner) < 2.5) {
                    handOver(owner);
                    v.combat().porterTrip = TRIP_NONE;
                    return;
                }
                if (--repath <= 0) {
                    repath = 8;
                    v.getNavigation().moveTo(owner, ValkyrieGoals.pathSpeed(v, 1.2));
                }
            }
            default -> {
                if (item == null) return;
                if (v.distanceToSqr(item) < 1.6 * 1.6) {
                    ItemStack rest = v.getInventory().addPickup(item.getItem());
                    v.take(item, item.getItem().getCount() - rest.getCount());
                    if (rest.isEmpty()) item.discard();
                    else item.setItem(rest);
                    stuck = 0;
                    Player owner = v.getOwner();
                    item = owner != null && !v.getInventory().pickupFull() && !ValkyrieDaily.noPickup(v)
                            ? itemNearOwner(owner, v.getInventory().hasChip(ValkyrieUnits.ChipKind.SCAVENGER) ? SCAVENGE_RANGE : OWNER_PICKUP) : null;
                    return;
                }
                if (--repath <= 0) {
                    repath = 8;
                    v.getNavigation().moveTo(item, ValkyrieGoals.pathSpeed(v, 1.2));
                }
            }
        }
    }

    /** 運び先へ入れる */
    private void deliver(BlockPos pos) {
        ValkyrieDaily.depositInto(v, pos);
        if (v.combat().porterTrip == TRIP_HOME && !v.getInventory().hasPickup()) {
            v.combat().porterTrip = TRIP_NONE;
            v.talk("porter_done");
        }
    }

    /** 運び元から拾った物の枠に入るだけ取り出す */
    private void take(BlockPos pos) {
        IItemHandler h = handler(v, pos);
        if (h == null) return;
        for (int i = 0; i < h.getSlots() && !v.getInventory().pickupFull(); i++) {
            ItemStack peek = h.extractItem(i, 64, true);
            if (peek.isEmpty()) continue;
            ItemStack rest = v.getInventory().addPickup(peek);
            int moved = peek.getCount() - rest.getCount();
            if (moved > 0) h.extractItem(i, moved, false);
        }
        v.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        v.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        v.playSound(net.minecraft.sounds.SoundEvents.CHEST_OPEN, 0.5F, 1.2F);
    }

    /** 預かった物を持ち主の持ち物へ（入らない分は持ったまま） */
    private void handOver(Player owner) {
        var inv = v.getInventory();
        boolean any = false;
        for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int before = s.getCount();
            owner.getInventory().add(s);
            if (s.getCount() != before) any = true;
            inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
        }
        v.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (any) {
            v.talk("porter_hand_over");
            v.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
        }
        if (inv.hasPickup()) v.talk("porter_owner_full");
        if (v.level() instanceof ServerLevel) v.gesture(ValkyrieEntity.GESTURE_NOD);
    }
}
