package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.menu.ItemPocketMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * アイテムポケットのティアで増える機能（2026-10-29 ユーザー指定「アイテムポケットを更にティアごとに便利に」。中身は【提案】）:
 * <ul>
 *   <li>自動回収（T3 から。{@link ItemPocketItem.Category#COLLECT}）: 拾った物が、もうポケットに入っている種類なら、持ち物でなくポケットへ入れる。入りきらない分は持ち物へ</li>
 *   <li>補充（T4。{@link ItemPocketItem.Category#REFILL}）: 手（利き手・左手）の重なる物（ブロック・矢・食べ物など）を使い切ったら、ポケットの同じ物で補充する。
 *       ほかの枠へ動かしただけ（持ち物にまだある）なら補充しない</li>
 * </ul>
 * ポケットの画面を開いている間はどちらも動かない（開いた画面の中身で上書きされないように）
 * <p>T4 のチート級の機能（同日ユーザー指定「ティア４は素材が素材だけにもっとチート級の便利効果があっていい」。質問で決めた 3 つのうち 2 つ。残りのどこでもリポジトリは画面のボタン）:</p>
 * <ul>
 *   <li>磁石（{@link ItemPocketItem.Category#MAGNET}）: 周り {@value #MAGNET_RANGE} ブロックの落ちている物と経験値を足元へ引き寄せる（拾うのはふつうと同じなので、入っている種類は自動回収でポケットへ）。
 *       投げたばかりの物（拾えない間）は引き寄せない</li>
 *   <li>死んでも残る: T4 のポケットを持って死ぬと、持ち物（ポケットも中身も）と経験値を落とさず、生き返った時に戻す。ゲームルールの keepInventory が入なら何もしない。
 *       控えは死んだプレイヤーの永続データに書くので、死んだままログアウトしても残る</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class PocketPerks {

    /** 前の Tick に手に持っていた物（利き手の枠の番号・利き手・左手） */
    private record Held(int slot, ItemStack main, ItemStack off) {}

    private static final Map<UUID, Held> LAST = new HashMap<>();

    /** 磁石の届く範囲（ブロック） */
    public static final double MAGNET_RANGE = 16.0;
    /** 死んだ時の持ち物と経験値の控え（プレイヤーの永続データのキー） */
    static final String KEEP_TAG = "automata_valkyries_pocket_keep";

    private PocketPerks() {}

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.containerMenu instanceof ItemPocketMenu || player.isSpectator()) return;
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || !ItemPocketItem.canStore(stack)) return;
        int before = stack.getCount();
        for (ItemStack pocket : PocketAutoUse.pockets(player)) {
            if (!ItemPocketItem.enabled(pocket, ItemPocketItem.Category.COLLECT)) continue;
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            if (items.stream().noneMatch(s -> ItemStack.isSameItemSameTags(s, stack))) continue; // 入っている種類だけ
            ItemStack rest = stack.copy();
            ItemPocketItem.insert(items, rest, ItemPocketItem.tierOf(pocket));
            if (rest.getCount() == stack.getCount()) continue;
            ItemPocketItem.save(pocket, items);
            stack.setCount(rest.getCount());
            if (stack.isEmpty()) break;
        }
        int taken = before - stack.getCount();
        if (taken <= 0) return;
        player.take(entity, taken);                                                             // 拾う動きと音
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), taken);
        if (stack.isEmpty()) {
            entity.discard();
            event.setCanceled(true);
        } else {
            entity.setItem(stack);                                                               // 残りはふつうに持ち物へ
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Held last = LAST.get(player.getUUID());
        int slot = player.getInventory().selected;
        if (last != null && player.isAlive() && !player.isSpectator() && !(player.containerMenu instanceof ItemPocketMenu)) {
            if (last.slot == slot) refill(player, InteractionHand.MAIN_HAND, last.main);
            refill(player, InteractionHand.OFF_HAND, last.off);
        }
        LAST.put(player.getUUID(), new Held(slot, player.getMainHandItem().copy(), player.getOffhandItem().copy()));
        if (player.isAlive() && player.deathTime == 0 && player.getPersistentData().contains(KEEP_TAG)) player.getPersistentData().remove(KEEP_TAG); // 死ぬのが取り消された（控えを残すと次に増える）
        if (player.tickCount % 2 == 0 && player.isAlive() && !player.isSpectator() && hasEnabled(player, ItemPocketItem.Category.MAGNET)) magnet(player);
    }

    static boolean hasEnabled(ServerPlayer player, ItemPocketItem.Category category) {
        for (ItemStack pocket : PocketAutoUse.pockets(player)) if (ItemPocketItem.enabled(pocket, category)) return true;
        return false;
    }

    /** 周りの落ちている物と経験値を足元へ（拾えない間の物は動かさない） */
    public static void magnet(ServerPlayer player) {
        var box = player.getBoundingBox().inflate(MAGNET_RANGE);
        double r2 = MAGNET_RANGE * MAGNET_RANGE;
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay() && e.distanceToSqr(player) <= r2)) {
            if (item.distanceToSqr(player) > 1.0) item.setPos(player.getX(), player.getY() + 0.1, player.getZ());
        }
        for (var orb : player.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, box, e -> e.isAlive() && e.distanceToSqr(player) <= r2)) {
            if (orb.distanceToSqr(player) > 1.0) orb.setPos(player.getX(), player.getY() + 0.1, player.getZ());
        }
    }

    // ---- 死んでも残る（T4） ----

    static boolean keepsOnDeath(ServerPlayer player) {
        if (player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)) return false;
        for (ItemStack pocket : PocketAutoUse.pockets(player)) if (ItemPocketItem.tierOf(pocket) >= 4) return true;
        return false;
    }

    /** 死ぬことが決まった時（トーテムなどで防がれなかった時）に、持ち物と経験値の控えを取る */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !keepsOnDeath(player)) return;
        var inv = player.getInventory();
        net.minecraft.nbt.CompoundTag keep = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag e = s.save(new net.minecraft.nbt.CompoundTag());
            e.putInt("Slot", i);
            list.add(e);
        }
        keep.put("Items", list);
        keep.putInt("Level", player.experienceLevel);
        keep.putFloat("Progress", player.experienceProgress);
        keep.putInt("Total", player.totalExperience);
        player.getPersistentData().put(KEEP_TAG, keep);
    }

    /** 控えを取った物は落とさない（同じ物を 1 つずつ消す。ほかの MOD が足した落とし物は残す） */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(KEEP_TAG)) return;
        java.util.List<ItemStack> kept = new java.util.ArrayList<>();
        for (var t : player.getPersistentData().getCompound(KEEP_TAG).getList("Items", 10)) kept.add(ItemStack.of((net.minecraft.nbt.CompoundTag) t));
        event.getDrops().removeIf(drop -> {
            for (int i = 0; i < kept.size(); i++) {
                if (ItemStack.matches(kept.get(i), drop.getItem())) {
                    kept.remove(i);
                    return true;
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(KEEP_TAG)) event.setCanceled(true);
    }

    /** 生き返った時に戻す */
    @SubscribeEvent
    public static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !event.getOriginal().getPersistentData().contains(KEEP_TAG)) return;
        net.minecraft.nbt.CompoundTag keep = event.getOriginal().getPersistentData().getCompound(KEEP_TAG);
        event.getOriginal().getPersistentData().remove(KEEP_TAG);
        var player = event.getEntity();
        var inv = player.getInventory();
        for (var t : keep.getList("Items", 10)) {
            net.minecraft.nbt.CompoundTag e = (net.minecraft.nbt.CompoundTag) t;
            int slot = e.getInt("Slot");
            ItemStack s = ItemStack.of(e);
            if (slot >= 0 && slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) inv.setItem(slot, s);
            else if (!inv.add(s)) player.drop(s, false);
        }
        player.experienceLevel = keep.getInt("Level");
        player.experienceProgress = keep.getFloat("Progress");
        player.totalExperience = keep.getInt("Total");
    }

    /** 手が空になっていて、前に持っていた重なる物が持ち物のどこにも無ければ、ポケットから補充する。補充したら true */
    public static boolean refill(ServerPlayer player, InteractionHand hand, ItemStack was) {
        if (was.isEmpty() || !was.isStackable() || !player.getItemInHand(hand).isEmpty()) return false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (ItemStack.isSameItemSameTags(inv.getItem(i), was)) return false; // 動かしただけ
        for (ItemStack pocket : PocketAutoUse.pockets(player)) {
            if (!ItemPocketItem.enabled(pocket, ItemPocketItem.Category.REFILL)) continue;
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            for (ItemStack s : items) {
                if (!ItemStack.isSameItemSameTags(s, was)) continue;
                player.setItemInHand(hand, s.split(Math.min(s.getCount(), s.getMaxStackSize())));
                ItemPocketItem.save(pocket, items);
                return true;
            }
        }
        return false;
    }
}
