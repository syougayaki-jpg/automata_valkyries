package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.attack.CallerAttribution;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 同期データの入れ物（{@link SynchedEntityData}）の差し替えへの対策（2026-10-21 ユーザー指定「入れ物の差し替えを実装して」）。
 * <p>
 * 他 MOD（SuperSteve）は、エンティティの入れ物を自分の継承クラスに作り替え（入れ物の型をその場で書き換える）、HP の読み書きを
 * 「最大 HP − 差分」以下に切り詰める。{@code setHealth}・{@code die}・同期データの入口の Mixin のどれにも届かず、HP の読み出し
 * （{@link DirectRead}、ボスバー、LifeGuard の真ライフ）まで切り詰められた値になる。入れ物の型は元に戻せないので、
 * 元の型の新しい入れ物を作り、データの置き場を引き継いで付け替える。
 * </p>
 * 検出は入れ物のクラスで見る（参照はその場で型だけが変わるので、参照の比較では分からない）。他 MOD のクラスだけを対象にする。
 */
public final class EntityDataContainer {

    private EntityDataContainer() {}

    /**
     * その個体の入れ物、または中身（{@code SynchedEntityData.DataItem}）が他 MOD の継承クラスに作り替えられていれば、そのクラス（フィールドを直接読む）。
     */
    @Nullable
    public static Class<?> foreignContainerClass(Entity entity) {
        SynchedEntityData data = ((EntityAccessor) entity).automata_valkyries$getEntityDataField();
        if (data == null) return null;
        if (data.getClass() != SynchedEntityData.class
                && CallerAttribution.isForeign(CallerAttribution.modOf(data.getClass()))) {
            return data.getClass();
        }
        var items = ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
        if (items != null) {
            for (SynchedEntityData.DataItem<?> item : items.values()) {
                if (item != null && item.getClass() != SynchedEntityData.DataItem.class
                        && CallerAttribution.isForeign(CallerAttribution.modOf(item.getClass()))) {
                    return item.getClass();
                }
            }
        }
        return null;
    }

    /** 作り替えた MOD の名前（{@link #foreignContainerClass} が返したクラスから） */
    public static String modOf(Class<?> containerClass) {
        return CallerAttribution.modOf(containerClass);
    }

    /**
     * 元の型の新しい入れ物を作り、データの置き場（itemsById）と更新済みの印を引き継いで、個体に付け替える。
     * 中身（{@code DataItem}）は同じものを指すので、値は変わらない。切り詰められて書かれた値は、呼び出し側が戻す。
     */
    public static void replaceWithVanilla(Entity entity) {
        SynchedEntityData old = ((EntityAccessor) entity).automata_valkyries$getEntityDataField();
        SynchedEntityData fresh = new SynchedEntityData(entity);
        copyState(old, fresh);
        ((EntityAccessor) entity).automata_valkyries$setEntityData(fresh);
        AutomataValkyries.LOGGER.warn("[eprotect] Replaced the synched-data container of {} ({}) with a vanilla one", entity, old.getClass().getName());
    }

    /**
     * データの置き場と更新済みの印を、{@code from} から {@code to} へ引き継ぐ（テストからも使う）。
     * 中身の {@code DataItem} 自体が他 MOD の継承クラスに差し替えられていた場合は、vanilla の {@code DataItem} で作り直す。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void copyState(SynchedEntityData from, SynchedEntityData to) {
        var targetItems = ((SynchedEntityDataAccessor) to).automata_valkyries$getItemsById();
        var sourceItems = ((SynchedEntityDataAccessor) from).automata_valkyries$getItemsById();
        for (var entry : sourceItems.int2ObjectEntrySet()) {
            SynchedEntityData.DataItem<?> item = entry.getValue();
            if (item != null && item.getClass() != SynchedEntityData.DataItem.class) {
                SynchedEntityData.DataItem replacement = new SynchedEntityData.DataItem(item.getAccessor(), item.getValue());
                replacement.setDirty(item.isDirty());
                targetItems.put(entry.getIntKey(), replacement);
            } else {
                targetItems.put(entry.getIntKey(), item);
            }
        }
        ((SynchedEntityDataAccessor) to).automata_valkyries$setDirty(((SynchedEntityDataAccessor) from).automata_valkyries$getDirty());
    }
}
