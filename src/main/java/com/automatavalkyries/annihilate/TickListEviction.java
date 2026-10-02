package com.automatavalkyries.annihilate;

import com.automatavalkyries.mixin.EntityTickListAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;

/**
 * Tick 対象一覧（{@link EntityTickList}）から、他 MOD の remove 拒否を迂回して個体を外す。
 * <p>
 * 一覧が回されている最中（{@code iterated == active}）に active のマップを直接いじると、回している途中の反復が壊れ、
 * 次に受け取る側（No-Sugar の時間停止の Mixin 等）へ null が渡って落ちる（2026-10-02 に実際に落ちた）。
 * 回されている最中は、vanilla の {@code ensureActiveIsNotIterated} と同じく写しを作って差し替え、写しから外す
 * （回している途中の反復は元のマップのまま最後まで進む。外した個体は消去済みなので Tick されない）。
 * </p>
 */
public final class TickListEviction {

    private TickListEviction() {}

    /** 外せたら（入っていたら）true */
    public static boolean remove(EntityTickList tickList, Entity entity) {
        return remove(tickList, entity.getId());
    }

    public static boolean remove(EntityTickList tickList, int id) {
        EntityTickListAccessor accessor = (EntityTickListAccessor) tickList;
        Int2ObjectMap<Entity> active = accessor.automata_valkyries$getActive();
        if (active == null || !active.containsKey(id)) return false;
        if (accessor.automata_valkyries$getIterated() == active) {
            Int2ObjectMap<Entity> copy = new Int2ObjectLinkedOpenHashMap<>(active);
            copy.remove(id);
            accessor.automata_valkyries$setActive(copy);
        } else {
            active.remove(id);
        }
        return true;
    }
}
