package com.automatavalkyries.item;

import com.automatavalkyries.menu.ItemPocketMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * アイテムポケット（2026-10-26 ユーザー指定「持ち物を大量にしまえて中にあるアイテムを自動で使ってくれるアイテムポケットを作って
 * (ポーションやトーテムなどの消費アイテムを)ティアごとに容量を上げる」。質問で: T1〜T4 で 27/54/108/216 枠・持ち物にあるだけで動く・作業台で段階的に強化）。
 * <p>右クリックで中身の画面（1 ページ 54 枠）。持ち物のどこにあっても、中の消費アイテムを {@link PocketAutoUse} が自動で使う:
 * 不死のトーテム（死ぬ時に身代わり）・回復（HP 40% 以下で治癒・再生のポーション、無ければ金のリンゴ）・状態（燃えたら耐火、溺れかけたら水中呼吸、高く落ちたら低速落下）・
 * 食べ物（満腹度 14 以下で）。分類ごと・全体の自動使用は画面で切り替える。ポケットの中にポケット（と中身を持つ入れ物）は入れられない</p>
 */
public class ItemPocketItem extends Item {

    /** 画面の 1 ページの枠 */
    public static final int PAGE = 54;

    /** 自動で使う分類（画面のボタンの並び） */
    public enum Category { TOTEM, HEAL, STATUS, FOOD }

    private final int tier, capacity;

    public ItemPocketItem(int tier, int capacity, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
        this.capacity = capacity;
    }

    public int tier() {
        return tier;
    }

    public int capacity() {
        return capacity;
    }

    public static int capacityOf(ItemStack pocket) {
        return pocket.getItem() instanceof ItemPocketItem p ? p.capacity : 0;
    }

    /** 中身（枠の数ちょうどの並び） */
    public static NonNullList<ItemStack> load(ItemStack pocket) {
        NonNullList<ItemStack> items = NonNullList.withSize(capacityOf(pocket), ItemStack.EMPTY);
        CompoundTag tag = pocket.getTag();
        if (tag != null && tag.contains("Items")) ContainerHelper.loadAllItems(tag, items);
        return items;
    }

    public static void save(ItemStack pocket, NonNullList<ItemStack> items) {
        ContainerHelper.saveAllItems(pocket.getOrCreateTag(), items, true);
    }

    /** ポケットに入れられるか（ポケットそのものと、中身を持つ入れ物＝シュルカーボックスなどは入れない） */
    public static boolean canStore(ItemStack stack) {
        return !(stack.getItem() instanceof ItemPocketItem) && stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    // ---- 自動使用の切り替え（タグに切ったものだけ残す。既定はすべて入） ----

    public static boolean autoOn(ItemStack pocket) {
        return !pocket.hasTag() || !pocket.getTag().getBoolean("AutoOff");
    }

    public static boolean enabled(ItemStack pocket, Category c) {
        return autoOn(pocket) && !(pocket.hasTag() && pocket.getTag().getBoolean("Off" + c.name()));
    }

    /** 画面へ送る切り替えの印（bit 0 = 全体が切、bit 1+n = 分類 n が切） */
    public static int flags(ItemStack pocket) {
        int f = autoOn(pocket) ? 0 : 1;
        for (Category c : Category.values()) if (pocket.hasTag() && pocket.getTag().getBoolean("Off" + c.name())) f |= 2 << c.ordinal();
        return f;
    }

    public static void toggleAuto(ItemStack pocket) {
        pocket.getOrCreateTag().putBoolean("AutoOff", autoOn(pocket));
    }

    public static void toggle(ItemStack pocket, Category c) {
        CompoundTag tag = pocket.getOrCreateTag();
        tag.putBoolean("Off" + c.name(), !tag.getBoolean("Off" + c.name()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pocket = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new ItemPocketMenu(id, inv, hand), pocket.getHoverName()), buf -> buf.writeEnum(hand));
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        return InteractionResultHolder.sidedSuccess(pocket, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int used = 0;
        for (ItemStack s : load(stack)) if (!s.isEmpty()) used++;
        tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.stored", used, capacity).withStyle(ChatFormatting.AQUA));
        if (!autoOn(stack)) {
            tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.auto_off").withStyle(ChatFormatting.RED));
        } else {
            for (Category c : Category.values()) {
                boolean on = enabled(stack, c);
                tooltip.add(Component.translatable("gui.automata_valkyries.item_pocket." + c.name().toLowerCase(java.util.Locale.ROOT))
                        .append(Component.literal(on ? " ON" : " OFF")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.desc").withStyle(ChatFormatting.GRAY));
    }
}
