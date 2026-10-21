package com.automatavalkyries.gametest.fake;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** GameTest 用: 時間停止で止められる保護対象の模擬（{@link FakeStopSubject} を保護対象にしたもの。{@code Eprotect} の使い方の最小限） */
public class FakeFrozenBoss extends FakeStopSubject implements IEprotected {

    private final Eprotect eprotect = new Eprotect(this);

    public FakeFrozenBoss(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    public Eprotect getEprotect() {
        return eprotect;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        eprotect.onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        eprotect.onRemovedFromWorld();
        super.onRemovedFromWorld();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) eprotect.clientTick();
        else eprotect.tick();
    }
}
