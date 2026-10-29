package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.menu.ItemPocketMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * アイテムポケット（{@link ItemPocketItem}）の自動使用（2026-10-26 ユーザー指定。質問で: トーテム・回復ポーション・状態ポーション・食べ物の 4 つすべて）。
 * 持ち物のどこかにあるポケットの中から、分類が入になっている物を使う。使った物の名前はアクションバーに出す:
 * <ul>
 *   <li>トーテム: 死ぬ時に身代わり（手のトーテムが先。/kill・奈落など無敵を通す死は防がない＝バニラのトーテムと同じ）</li>
 *   <li>回復: HP {@value #HEAL_AT} 以下で、治癒・再生のポーション → 金のリンゴ → エンチャントされた金のリンゴ（{@value #HEAL_LAST} 以下のときだけ）</li>
 *   <li>状態: 燃えている・溶岩の中で耐火、空気が 1/3 未満で水中呼吸、{@value #FALL_AT} ブロック以上落ちていて低速落下（もう効いていれば飲まない）</li>
 *   <li>食べ物: 満腹度 {@value #HUNGRY_AT} 以下で、満腹を超えない一番大きい物（足りなければ一番小さい物）。毒などの悪い効果がある物・コーラスフルーツ・金のリンゴは食べない</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class PocketAutoUse {

    public static final float HEAL_AT = 0.4F, HEAL_LAST = 0.25F;
    public static final int HUNGRY_AT = 14;
    public static final float FALL_AT = 6.0F;
    /** 分類ごとに次に使えるまでの Tick */
    static final int HEAL_GAP = 40, STATUS_GAP = 20, FOOD_GAP = 30;

    private static final Map<UUID, long[]> NEXT = new HashMap<>();

    private PocketAutoUse() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        tick(player);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled() && saveWithTotem(player, event.getSource())) event.setCanceled(true);
    }

    /** 回復・状態・食べ物を見て、要る物を使う（5 Tick ごと） */
    public static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().instabuild) return;
        long now = player.level().getGameTime();
        long[] next = NEXT.computeIfAbsent(player.getUUID(), k -> new long[3]);
        if (now >= next[0] && player.getHealth() <= player.getMaxHealth() * HEAL_AT) {
            boolean last = player.getHealth() <= player.getMaxHealth() * HEAL_LAST;
            if (use(player, ItemPocketItem.Category.HEAL, s -> drinkable(s) && hasEffect(s, MobEffects.HEAL, MobEffects.REGENERATION))
                    || use(player, ItemPocketItem.Category.HEAL, s -> s.is(Items.GOLDEN_APPLE))
                    || last && use(player, ItemPocketItem.Category.HEAL, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE))) {
                next[0] = now + HEAL_GAP;
            }
        }
        if (now >= next[1]) {
            MobEffect want = null;
            if ((player.isOnFire() || player.isInLava()) && !player.hasEffect(MobEffects.FIRE_RESISTANCE)) want = MobEffects.FIRE_RESISTANCE;
            else if (player.getAirSupply() < player.getMaxAirSupply() / 3 && !player.hasEffect(MobEffects.WATER_BREATHING)) want = MobEffects.WATER_BREATHING;
            else if (player.fallDistance >= FALL_AT && !player.onGround() && !player.isFallFlying() && !player.hasEffect(MobEffects.SLOW_FALLING)) want = MobEffects.SLOW_FALLING;
            MobEffect w = want;
            if (w != null && use(player, ItemPocketItem.Category.STATUS, s -> drinkable(s) && hasEffect(s, w))) next[1] = now + STATUS_GAP;
        }
        if (now >= next[2] && player.getFoodData().getFoodLevel() <= HUNGRY_AT && eat(player)) next[2] = now + FOOD_GAP;
    }

    /** ポケットのトーテムで死を防ぐ。防いだら true */
    public static boolean saveWithTotem(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        for (ItemStack pocket : pockets(player)) {
            if (!ItemPocketItem.enabled(pocket, ItemPocketItem.Category.TOTEM)) continue;
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            for (ItemStack s : items) {
                if (!s.is(Items.TOTEM_OF_UNDYING)) continue;
                closeIfOpen(player, pocket);                                                    // 開いた画面の中身は変わるたびに書き込み済みなので、読んだ物のままでよい
                ItemStack used = s.copyWithCount(1);
                s.shrink(1);
                ItemPocketItem.save(pocket, items);
                player.setHealth(1.0F);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                CriteriaTriggers.USED_TOTEM.trigger(player, used);
                player.level().broadcastEntityEvent(player, (byte) 35);
                notice(player, used);
                return true;
            }
        }
        return false;
    }

    /** 満腹を超えない一番大きい食べ物（無ければ一番小さい物）を食べる */
    static boolean eat(ServerPlayer player) {
        int room = 20 - player.getFoodData().getFoodLevel();
        ItemStack bestPocket = null;
        int bestIndex = -1, bestScore = Integer.MIN_VALUE;
        for (ItemStack pocket : pockets(player)) {
            if (!ItemPocketItem.enabled(pocket, ItemPocketItem.Category.FOOD)) continue;
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            for (int i = 0; i < items.size(); i++) {
                FoodProperties food = safeFood(player, items.get(i));
                if (food == null) continue;
                int n = food.getNutrition();
                int score = n <= room ? 100 + n : -n;                                           // 超えない物を大きい順、無ければ小さい順
                if (score > bestScore) {
                    bestScore = score;
                    bestPocket = pocket;
                    bestIndex = i;
                }
            }
        }
        return bestPocket != null && consume(player, bestPocket, bestIndex);
    }

    static FoodProperties safeFood(ServerPlayer player, ItemStack s) {
        if (s.isEmpty() || !s.isEdible() || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.CHORUS_FRUIT)) return null;
        FoodProperties food = s.getFoodProperties(player);
        if (food == null) return null;
        for (var e : food.getEffects()) if (e.getFirst() != null && e.getFirst().getEffect().getCategory() == MobEffectCategory.HARMFUL) return null;
        return food;
    }

    static boolean drinkable(ItemStack s) {
        return s.getItem() instanceof PotionItem && !(s.getItem() instanceof ThrowablePotionItem);
    }

    static boolean hasEffect(ItemStack s, MobEffect... wanted) {
        for (MobEffectInstance e : PotionUtils.getMobEffects(s)) for (MobEffect w : wanted) if (e.getEffect() == w) return true;
        return false;
    }

    /** 分類が入のポケットから、条件に合う最初の物を使う */
    static boolean use(ServerPlayer player, ItemPocketItem.Category category, Predicate<ItemStack> match) {
        for (ItemStack pocket : pockets(player)) {
            if (!ItemPocketItem.enabled(pocket, category)) continue;
            NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
            for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty() && match.test(items.get(i))) return consume(player, pocket, i);
        }
        return false;
    }

    /** ポケットの index 番目を飲む・食べる（バニラの使い終わりと同じ処理。空き瓶・ボウルはポケットへ戻し、入らなければ持ち物へ） */
    static boolean consume(ServerPlayer player, ItemStack pocket, int index) {
        closeIfOpen(player, pocket);
        NonNullList<ItemStack> items = ItemPocketItem.load(pocket);
        ItemStack stack = items.get(index);
        if (stack.isEmpty()) return false;
        ItemStack shown = stack.copyWithCount(1);
        boolean potion = drinkable(stack);
        ItemStack result = stack.getItem().finishUsingItem(stack, player.level(), player);
        if (result != stack) {
            if (stack.isEmpty()) items.set(index, result);
            else if (!result.isEmpty() && !ItemPocketItem.insert(items, result, ItemPocketItem.tierOf(pocket))) player.getInventory().placeItemBackInInventory(result);
        }
        ItemPocketItem.save(pocket, items);
        if (potion) player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.0F);
        notice(player, shown);
        return true;
    }

    /** 中身を書き換えるので、そのポケットの画面が開いていれば閉じる（開いた画面の中身で上書きされないように） */
    static void closeIfOpen(ServerPlayer player, ItemStack pocket) {
        if (player.containerMenu instanceof ItemPocketMenu menu && menu.holds(pocket)) player.closeContainer();
    }

    static void notice(ServerPlayer player, ItemStack used) {
        player.displayClientMessage(Component.translatable("message.automata_valkyries.item_pocket.used", used.getHoverName()).withStyle(ChatFormatting.GREEN), true);
    }

    /** 持ち物（左手も含むすべての枠）のポケット */
    static java.util.List<ItemStack> pockets(ServerPlayer player) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ItemPocketItem) out.add(s);
        }
        return out;
    }
}
