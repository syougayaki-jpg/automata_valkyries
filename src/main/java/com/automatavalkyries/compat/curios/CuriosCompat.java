package com.automatavalkyries.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.function.Predicate;

/**
 * Curios の連携（2026-10-30 ユーザー指定「他MODとの連携要素を増やす」。質問で: 情報表示・装備枠系）。
 * アイテムポケットはベルトの枠、観測の立方体はお守りの枠に付けられる（{@code data/curios/tags/items/belt.json}・{@code charm.json}、枠は {@code data/automata_valkyries/curios/}）。
 * 付けている物も、持ち物にある時と同じように動く（自動使用・ティアの機能・立方体の射撃）。
 * Curios が無い時は何もしない（Curios のクラスは {@link Impl} の中だけで触るので、無くても読み込まれない）
 */
public final class CuriosCompat {

    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("curios");

    private CuriosCompat() {}

    /** Curios の枠に付けている物のうち、条件に合う物（Curios が無ければ空） */
    public static List<ItemStack> find(LivingEntity entity, Predicate<ItemStack> match) {
        return LOADED ? Impl.find(entity, match) : List.of();
    }

    private static final class Impl {
        static List<ItemStack> find(LivingEntity entity, Predicate<ItemStack> match) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .map(inv -> inv.findCurios(match).stream().map(top.theillusivec4.curios.api.SlotResult::stack).toList())
                    .orElse(List.of());
        }
    }
}
