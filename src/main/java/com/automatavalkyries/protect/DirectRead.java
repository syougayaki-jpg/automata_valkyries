package com.automatavalkyries.protect;

import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 生死の状態を、getter を通さずに保存場所（同期データ・フィールド）から直接読む。
 * <p>
 * {@code getHealth} / {@code isAlive} / {@code isRemoved} / {@code isDeadOrDying} は、他 MOD の Java エージェント・ASM が
 * 呼び出し箇所ごと書き換えて嘘を返させることがあり（No-Sugar・Omni-Mobs・Trial Monolith 等）、このMOD自身の Mixin も
 * 保護対象の HP 0 を「直前の正常値」に読み替えている。判定・修復はここで読んだ実際の値で行う。
 * </p>
 */
public final class DirectRead {

    private DirectRead() {}

    /**
     * 同期データにある実際の HP。HP の項目そのものを同期データから抜かれていたら（The King Sword と組んだとき。vanilla の読み書きも
     * 空を読んで落ち、サーバーが落ちた。2026-10-26）、保護対象なら最後に確かめた健全な値で項目を作り直してから読む（{@link #ensureHealthItem}）。
     * 保護対象でない相手は書き換えず、HP 0 として読む
     */
    public static float health(LivingEntity entity) {
        SynchedEntityData.DataItem<?> item = healthItem(entity);
        if (item == null && ensureHealthItem(entity)) item = healthItem(entity);
        return item != null && item.getValue() instanceof Float value ? value : 0.0F;
    }

    @Nullable
    private static SynchedEntityData.DataItem<?> healthItem(LivingEntity entity) {
        SynchedEntityData data = ((EntityAccessor) entity).automata_valkyries$getEntityDataField();
        if (data == null) return null;
        var items = ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
        return items != null ? items.get(LivingEntityAccessor.automata_valkyries$getDataHealthId().getId()) : null;
    }

    /**
     * 保護対象の同期データから HP の項目が抜かれていたら、最後に確かめた健全な値（無ければ最大 HP）で作り直す。作り直したら true
     */
    public static boolean ensureHealthItem(LivingEntity entity) {
        if (!(entity instanceof IEprotected provider)) return false;
        SynchedEntityData data = ((EntityAccessor) entity).automata_valkyries$getEntityDataField();
        if (data == null) return false;
        var items = ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
        var key = LivingEntityAccessor.automata_valkyries$getDataHealthId();
        if (items == null || items.get(key.getId()) != null) return false;
        Eprotect protect = provider.getEprotect();
        float value = protect != null && protect.getLastValidHealth() > 0.0F ? protect.getLastValidHealth() : 0.0F;
        if (!(value > 0.0F)) {
            var attribute = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            value = attribute != null ? (float) attribute.getValue() : 20.0F;
        }
        SynchedEntityData.DataItem<Float> item = new SynchedEntityData.DataItem<>(key, value);
        item.setDirty(true);
        items.put(key.getId(), item);
        ((SynchedEntityDataAccessor) data).automata_valkyries$setDirty(true);
        // 毎 Tick 抜かれることがあるので、記録は攻撃の記録だけ（同じ物はまとめて数える）
        if (!entity.level().isClientSide) com.automatavalkyries.protect.attack.AttackLog.repaired(entity, "synched_data:health_entry_missing");
        return true;
    }

    @Nullable
    public static Entity.RemovalReason removalReason(Entity entity) {
        return ((EntityAccessor) entity).automata_valkyries$getRemovalReason();
    }

    public static boolean isRemoved(Entity entity) {
        return removalReason(entity) != null;
    }

    /** {@code LivingEntity.dead}（死亡処理済みの印） */
    public static boolean deadFlag(LivingEntity entity) {
        return ((LivingEntityAccessor) entity).automata_valkyries$isDead();
    }

    /** 実際に生きているか（消去されておらず、HP が正の有限値） */
    public static boolean isAlive(LivingEntity entity) {
        float health = health(entity);
        return !isRemoved(entity) && Float.isFinite(health) && health > 0.0F;
    }

    /** HP が正の有限値か */
    public static boolean hasHealth(LivingEntity entity) {
        float health = health(entity);
        return Float.isFinite(health) && health > 0.0F;
    }

    /**
     * 読み出しの偽装: 実際は生きているのに、getter が「死亡・消去済み」と答える（呼び出し箇所の書き換え・getter への差し込み）。
     * このMOD の Mixin は保護対象の読み出しを「生きている」側へしか読み替えないので、逆向きの食い違いは他 MOD の仕業。
     *
     * @return 偽装されていた getter の名前（無ければ null）
     */
    @Nullable
    public static String spoofedRead(LivingEntity entity) {
        if (!isAlive(entity)) return null;
        if (entity.isRemoved()) return "isRemoved";
        if (!(entity.getHealth() > 0.0F)) return "getHealth";
        if (!entity.isAlive()) return "isAlive";
        if (entity.isDeadOrDying()) return "isDeadOrDying";
        return null;
    }
}
