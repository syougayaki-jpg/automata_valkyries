package com.automatavalkyries.protect;

import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 保護中のプレイヤーの持ち物を、ブラックリストの MOD が直接別のアイテムに差し替えようとしたら止める（2026-10-22 ユーザー指定「アイテム変換鬱陶しい」）。
 * Madness Entities の Notch は、主手・副手のアイテムを本に置き換える（言語ファイルの {@code commands.notch.replaceitem}。Player.setItemSlot）。
 * 別の Madness のエンティティ（暗号化されたクラス {@code C846371}）は、全プレイヤーの持ち物（主・副手・防具）を、
 * 持ち物の一覧（{@code Inventory.items} など）へ直接（NonNullList.set）ランダムなアイテムに書き換える。setItem を通らないので、
 * 一覧にも持ち主の印を付けて見張る（{@code PlayerItemGuardMixin.ListSet}、2026-10-24 の調べ）。
 * <p>
 * 範囲を絞る: サーバー側・保護中のプレイヤー（護符・特権守護）だけ、元が空でなく別の種類のアイテムへの差し替えだけ（個数や NBT の変化は通す）、
 * 直接の呼び出し元が敵対する MOD（ブラックリスト、またはこのMOD の守りが攻撃を断った記録がある MOD）のときだけ（プレイヤー自身の操作・他の MOD のふつうの付与は通す）。
 * 呼び出し経路を辿るのは最後（種類が変わり、ブラックリストが空でないときだけ）
 * </p>
 */
public final class PlayerItemGuard {

    private PlayerItemGuard() {}

    /** 止めるなら true（記録も残す） */
    public static boolean shouldBlock(Player player, ItemStack current, ItemStack incoming, String op) {
        if (!(player instanceof ServerPlayer) || player.level().isClientSide) return false;
        if (current.isEmpty() || ItemStack.isSameItem(current, incoming)) return false;
        if (!EprotectCompat.shouldShield(player)) return false;
        Writer writer = itemWriter(player);
        if (writer.ownItem() || !CallerAttribution.isForeign(writer.mod()) || !isHostile(writer.mod())) return false;
        AttackLog.shielded(player, "item_replace:" + op);
        noteAttack(player);
        return true;
    }

    // ---- 持ち物の束そのものの書き換え（2026-10-24 の調べ） ----

    /** 敵対する MOD の持ち物への書き込みを止めた Tick（サーバーの Tick 数）と、そのプレイヤー */
    private static volatile int attackTick = Integer.MIN_VALUE;
    private static final java.util.Map<Player, Integer> ATTACKED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** 束の書き換えを見張る長さ（Tick） */
    private static final int STACK_WINDOW = 2;

    private static void noteAttack(Player player) {
        int tick = player.getServer() != null ? player.getServer().getTickCount() : 0;
        attackTick = tick;
        ATTACKED.put(player, tick);
    }

    /**
     * 持ち物の束そのもの（ItemStack）の書き換え（数・NBT・名前）を止めるなら true。Madness Entities の持ち物のランダム化は、一覧への書き込みを
     * 止められた後、続けて持ち物の束をその場で書き換える（数をランダムに・NBT を空に・名前を付け替える。エンチャントなどが消える）。
     * 毎回スタックを辿ると重いので、敵対する MOD の持ち物への書き込みを止めた直後（{@link #STACK_WINDOW} Tick）だけ、その相手の持ち物にある束か・
     * 書き換えたのが敵対する MOD（アイテム自身の処理を除く）かを確かめる
     */
    public static boolean shouldBlockStackEdit(ItemStack stack, String op) {
        int since = attackTick;
        if (since == Integer.MIN_VALUE) return false;
        Player owner = null;
        synchronized (ATTACKED) {
            for (var e : ATTACKED.entrySet()) {
                Player p = e.getKey();
                if (p.getServer() == null || p.getServer().getTickCount() - e.getValue() > STACK_WINDOW) continue;
                if (holds(p, stack)) {
                    owner = p;
                    break;
                }
            }
        }
        if (owner == null || !EprotectCompat.shouldShield(owner)) return false;
        Writer writer = itemWriter(owner);
        if (writer.ownItem() || !CallerAttribution.isForeign(writer.mod()) || !isHostile(writer.mod())) return false;
        AttackLog.shielded(owner, "item_replace:" + op);
        return true;
    }

    /** その束（同じもの）がプレイヤーの持ち物にあるか */
    private static boolean holds(Player player, ItemStack stack) {
        var inv = player.getInventory();
        for (ItemStack s : inv.items) if (s == stack) return true;
        for (ItemStack s : inv.armor) if (s == stack) return true;
        for (ItemStack s : inv.offhand) if (s == stack) return true;
        return false;
    }

    /**
     * 持ち物をまとめて捨てる・消す操作（dropAll・clearContent・removeItem）を止めるなら true（2026-10-24 の調べ: Madness Entities の
     * 倒す処理 {@code C848560.killTarget} と 2 つの状態異常が dropAll → clearContent で持ち物を全部消していた。dropAll は先にアイテムを
     * 落としてから枠を空けるので、枠への書き込みだけ止めると、落ちたアイテムと持ち物が二重になる。入口でまとめて止める）
     */
    public static boolean shouldBlockBulk(Player player, boolean empty, String op) {
        if (!(player instanceof ServerPlayer) || player.level().isClientSide || empty) return false;
        if (!EprotectCompat.shouldShield(player)) return false;
        Writer writer = itemWriter(player);
        if (writer.ownItem() || !CallerAttribution.isForeign(writer.mod()) || !isHostile(writer.mod())) return false;
        AttackLog.shielded(player, "item_replace:" + op);
        noteAttack(player);
        return true;
    }

    /**
     * 書き換えた MOD と、それがアイテム自身の処理か（アイテムのクラスの inventoryTick・use などが、持ち主の持ち物を扱う。皆殺しの剣は持ち主の
     * 持ち物で自分を入れ替える。アイテム自身の決まった動きは攻撃ではないので通す。2026-10-24）
     */
    record Writer(String mod, boolean ownItem) {}

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * 持ち物を書き換えた MOD（直接の呼び出し元）。この守り自身・差し込み・持ち物の入れ物（Inventory・NonNullList）・プレイヤーのクラスは飛ばし、
     * 「書き換えを頼んだ側」を返す（2026-10-24。以前は CallerAttribution に任せ、この守り自身か Inventory を呼び出し元と見て、一度も止められていなかった）
     */
    static Writer itemWriter(Player player) {
        Class<?> playerClass = player.getClass();
        return WALKER.walk(frames -> frames.limit(48).filter(f -> {
            Class<?> type = f.getDeclaringClass();
            String name = type.getName();
            if (type == PlayerItemGuard.class || f.getMethodName().contains("automata_valkyries$")) return false;
            if (name.startsWith("org.spongepowered.") || name.startsWith("com.automatavalkyries.protect.attack.")) return false;
            if (type == net.minecraft.core.NonNullList.class || type == net.minecraft.world.entity.player.Inventory.class
                    || type == ItemStack.class) return false;
            if (type.isAssignableFrom(playerClass)) return false;
            return !CallerAttribution.PLATFORM.equals(CallerAttribution.modOf(type));
        }).findFirst().map(f -> new Writer(CallerAttribution.modOf(f.getDeclaringClass()),
                net.minecraft.world.item.Item.class.isAssignableFrom(f.getDeclaringClass())))
                .orElse(new Writer(CallerAttribution.UNKNOWN, false)));
    }

    /**
     * 敵対する MOD か: ブラックリストに載っている、または、このMOD の守りがその MOD の攻撃（即死・消去など）を断った記録がある
     * （2026-10-24。自動のブラックリストに載る前の Madness Entities の差し替えを止められなかった）。記録の検索は 1 秒ごとに見直す
     */
    static boolean isHostile(String mod) {
        if (ModBlacklist.contains(mod)) return true;
        long now = System.currentTimeMillis();
        Long[] cached = HOSTILE.get(mod);
        if (cached != null && now - cached[0] < 1000L) return cached[1] != 0L;
        boolean hostile = false;
        for (AttackLog.Entry e : AttackLog.entries()) {
            if (e.outcome == AttackLog.Outcome.REFUSED && e.count() > 0 && related(e.mod, mod)) {
                hostile = true;
                break;
            }
        }
        HOSTILE.put(mod, new Long[]{now, hostile ? 1L : 0L});
        return hostile;
    }

    /** 同じ MOD の別の名前（Madness Entities は madness と madness_events の 2 つの名前で記録される） */
    private static boolean related(String a, String b) {
        return a.equals(b) || a.startsWith(b + "_") || b.startsWith(a + "_");
    }

    private static final java.util.Map<String, Long[]> HOSTILE = new java.util.concurrent.ConcurrentHashMap<>();
}
