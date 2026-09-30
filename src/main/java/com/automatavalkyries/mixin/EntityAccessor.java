package com.automatavalkyries.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
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

    @Accessor("levelCallback")
    EntityInLevelCallback automata_valkyries$getLevelCallback();

    @Accessor("levelCallback")
    void automata_valkyries$setLevelCallback(EntityInLevelCallback callback);

    /** Forge が追加したフィールド（難読化されないので remap = false） */
    @Accessor(value = "isAddedToWorld", remap = false)
    void automata_valkyries$setAddedToWorld(boolean added);
}
