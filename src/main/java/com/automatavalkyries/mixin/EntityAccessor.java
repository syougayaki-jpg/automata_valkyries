package com.automatavalkyries.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Entity} の private フィールドへの getter / setter。リフレクションは使わない。
 * 他 MOD の ASM は getRemovalReason() 等の本体と呼び出し箇所の戻り値を書き換えるが、Accessor による直接読み出しは対象外なので、
 * バニラのメソッドと同名にしないこと（同名だとバニラの getRemovalReason() がそのまま呼ばれ、書き換えの対象になる）。
 * 修復の判定はこちらで行う。
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("removalReason")
    Entity.RemovalReason automata_valkyries$getRemovalReason();

    @Accessor("removalReason")
    void automata_valkyries$setRemovalReason(Entity.RemovalReason removalReason);

    /** 同期データの入れ物のフィールドを直接読む（{@code getEntityData()} は上書きされうる） */
    @Accessor("entityData")
    SynchedEntityData automata_valkyries$getEntityDataField();

    /** 他 MOD に型を作り替えられた入れ物を、元の型の新しい入れ物へ付け替える（final を外す） */
    @Mutable
    @Accessor("entityData")
    void automata_valkyries$setEntityData(SynchedEntityData data);

    @Accessor("levelCallback")
    EntityInLevelCallback automata_valkyries$getLevelCallback();

    @Accessor("levelCallback")
    void automata_valkyries$setLevelCallback(EntityInLevelCallback callback);

    /** Forge が追加したフィールド（難読化されないので remap = false） */
    @Accessor(value = "isAddedToWorld", remap = false)
    void automata_valkyries$setAddedToWorld(boolean added);

    @Accessor("position")
    void automata_valkyries$setPosition(net.minecraft.world.phys.Vec3 position);

    @Accessor("bb")
    void automata_valkyries$setBoundingBoxField(net.minecraft.world.phys.AABB bb);

    /** UUID・ID を getter（{@code getUUID} は他 MOD に書き換えられうる）を通さずに読み書きする */
    @Accessor("uuid")
    java.util.UUID automata_valkyries$getUuidField();

    @Accessor("uuid")
    void automata_valkyries$setUuidField(java.util.UUID uuid);

    @Accessor("stringUUID")
    void automata_valkyries$setStringUuidField(String stringUuid);

    @Accessor("id")
    int automata_valkyries$getIdField();

    @Accessor("id")
    void automata_valkyries$setIdField(int id);
}
