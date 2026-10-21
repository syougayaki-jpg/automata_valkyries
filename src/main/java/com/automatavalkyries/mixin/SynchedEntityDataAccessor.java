package com.automatavalkyries.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code SynchedEntityData} の private フィールドへの getter / setter（リフレクションは使わない）。
 * 他 MOD に型を作り替えられた入れ物を、元の型の新しい入れ物へ付け替えるとき（{@code protect/EntityDataContainer}）に、
 * データの置き場（itemsById）と更新済みの印（isDirty）を引き継ぐために使う。バニラのメソッドと同名にしないこと。
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> automata_valkyries$getItemsById();

    @Accessor("isDirty")
    boolean automata_valkyries$getDirty();

    @Accessor("isDirty")
    void automata_valkyries$setDirty(boolean dirty);
}
