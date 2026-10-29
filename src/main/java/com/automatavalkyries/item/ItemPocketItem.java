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
 * <p>ティアごとの便利な機能（2026-10-29 ユーザー指定「アイテムポケットを更にティアごとに便利に同じポーションやトーテムがスタックできるなど」。中身は【提案】）:
 * T2 から重ならない物（ポーション・トーテム・シチューなど）が中で {@value #T2_STACK} まで重なり、整理のボタン。T3 から何でも {@value #T3_STACK} まで重なり、
 * 入っている種類の物を拾うとポケットへ（{@link PocketPerks}）。T4 は手の物が尽きたらポケットから補充。耐久のある物は重ねない</p>
 * <p>T4 のチート級の機能（同日ユーザー指定「ティア４は素材が素材だけにもっとチート級の便利効果があっていい」。質問で 3 つ）: 周り {@value PocketPerks#MAGNET_RANGE} ブロックの
 * 落ちている物と経験値を吸い寄せる・死んでも持ち物と経験値が残る・リンクしたヴォイド・リポジトリを距離もディメンションも関係なく画面から開ける（スニークしながら右クリックでリンク）</p>
 */
public class ItemPocketItem extends Item {

    /** 画面の 1 ページの枠 */
    public static final int PAGE = 54;

    /** 中で重なる数: T2 から重ならない物が 16 まで、T3 から何でも 64 まで（1 枠に入る数はバニラの通信と保存の都合で 64 まで） */
    public static final int T2_STACK = 16, T3_STACK = 64;
    /** 整理のボタンが使えるティア */
    public static final int SORT_TIER = 2;

    /** 自動で使う分類と、ティアで増える機能（画面のボタンの並び）。minTier のティアから */
    public enum Category {
        TOTEM(1), HEAL(1), STATUS(1), FOOD(1), COLLECT(3), REFILL(4), MAGNET(4);

        public final int minTier;

        Category(int minTier) {
            this.minTier = minTier;
        }
    }

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

    public static int tierOf(ItemStack pocket) {
        return pocket.getItem() instanceof ItemPocketItem p ? p.tier : 0;
    }

    /** ティア tier のポケットの 1 枠に、その物が何個まで重なるか（耐久のある物はふつうのまま） */
    public static int limit(int tier, ItemStack stack) {
        int base = stack.getMaxStackSize();
        if (stack.isEmpty() || stack.isDamageableItem()) return base;
        if (tier >= 3) return Math.max(base, T3_STACK);
        if (tier >= 2 && base == 1) return T2_STACK;
        return base;
    }

    /** 中身に add をできるだけ入れる（同じ物に重ねてから空きへ）。全部入ったら true（add は入らなかった分が残る） */
    public static boolean insert(NonNullList<ItemStack> items, ItemStack add, int tier) {
        for (ItemStack s : items) {
            if (add.isEmpty()) return true;
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, add)) {
                int moved = Math.min(add.getCount(), limit(tier, s) - s.getCount());
                if (moved <= 0) continue;
                s.grow(moved);
                add.shrink(moved);
            }
        }
        for (int i = 0; i < items.size() && !add.isEmpty(); i++) {
            if (items.get(i).isEmpty()) items.set(i, add.split(limit(tier, add)));
        }
        return add.isEmpty();
    }

    /** 整理: 同じ物をまとめて、種類（登録名）の順に前から詰める */
    public static void sort(NonNullList<ItemStack> items, int tier) {
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        for (ItemStack s : items) if (!s.isEmpty()) all.add(s.copy());
        all.sort(java.util.Comparator.comparing((ItemStack s) -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem())))
                .thenComparing(s -> s.getHoverName().getString()));
        java.util.Collections.fill(items, ItemStack.EMPTY);
        NonNullList<ItemStack> packed = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (ItemStack s : all) insert(packed, s, tier);
        for (int i = 0; i < items.size(); i++) items.set(i, packed.get(i));
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
        return tierOf(pocket) >= c.minTier && autoOn(pocket) && !(pocket.hasTag() && pocket.getTag().getBoolean("Off" + c.name()));
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

    // ---- どこでもリポジトリ（T4） ----

    /** リンクしたヴォイド・リポジトリ（無ければ null）: ディメンションと位置 */
    @Nullable
    public static net.minecraft.core.GlobalPos repository(ItemStack pocket) {
        if (!pocket.hasTag() || !pocket.getTag().contains("Repository")) return null;
        CompoundTag t = pocket.getTag().getCompound("Repository");
        var dim = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.parse(t.getString("dim")));
        return net.minecraft.core.GlobalPos.of(dim, net.minecraft.core.BlockPos.of(t.getLong("pos")));
    }

    public static void link(ItemStack pocket, net.minecraft.core.GlobalPos at) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", at.dimension().location().toString());
        t.putLong("pos", at.pos().asLong());
        pocket.getOrCreateTag().put("Repository", t);
    }

    /** T4: スニークしながらヴォイド・リポジトリを右クリックするとリンクする */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        if (tier < 4 || player == null || !player.isShiftKeyDown()
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof com.automatavalkyries.chrono.VoidRepositoryBlockEntity)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide) {
            link(context.getItemInHand(), net.minecraft.core.GlobalPos.of(context.getLevel().dimension(), context.getClickedPos()));
            player.displayClientMessage(Component.translatable("message.automata_valkyries.item_pocket.linked").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
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
                if (tier < c.minTier) continue;
                boolean on = enabled(stack, c);
                tooltip.add(Component.translatable("gui.automata_valkyries.item_pocket." + c.name().toLowerCase(java.util.Locale.ROOT))
                        .append(Component.literal(on ? " ON" : " OFF")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.desc").withStyle(ChatFormatting.GRAY));
        for (int t = 2; t <= 4; t++) {                                                          // ティアごとの機能（持っている物は明るく、上のティアの物は暗く）
            tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.perk" + t).withStyle(tier >= t ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY));
        }
        if (tier >= 4) {
            tooltip.add(Component.translatable("tooltip.automata_valkyries.item_pocket.perk4b").withStyle(ChatFormatting.GOLD));
            var repo = repository(stack);
            tooltip.add(repo == null ? Component.translatable("tooltip.automata_valkyries.item_pocket.no_repository").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("tooltip.automata_valkyries.item_pocket.repository", repo.dimension().location().toString(),
                    repo.pos().getX(), repo.pos().getY(), repo.pos().getZ()).withStyle(ChatFormatting.AQUA));
        }
    }
}
