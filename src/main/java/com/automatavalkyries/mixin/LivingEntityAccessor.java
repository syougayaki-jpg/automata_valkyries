package com.automatavalkyries.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code LivingEntity.dead}（protected）への getter / setter と {@code DATA_HEALTH_ID}（private static）への getter、死んだときの落とし物（protected）の呼び出し */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("dead")
    boolean automata_valkyries$isDead();

    @Accessor("dead")
    void automata_valkyries$setDead(boolean dead);

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> automata_valkyries$getDataHealthId() {
        throw new AssertionError();
    }

    /** 種類ごとの特別な落とし物（ウィザーのネザースターなど） */
    @Invoker("dropCustomDeathLoot")
    void automata_valkyries$dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source, int looting, boolean recentlyHit);

    /** 死亡演出の 1 Tick（他 MOD に isDeadOrDying を書き換えられて vanilla が呼ばないとき、Eprotect が代わりに進める） */
    @Invoker("tickDeath")
    void automata_valkyries$tickDeath();

    /** 装備の落とし物 */
    @Invoker("dropEquipment")
    void automata_valkyries$dropEquipment();
}
