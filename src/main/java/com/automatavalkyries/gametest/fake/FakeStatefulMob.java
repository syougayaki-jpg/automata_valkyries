package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** GameTest 用: {@link FakeKillState} を持つモブ */
public class FakeStatefulMob extends Zombie implements FakeKillState {

    private boolean markedForDeath = true;
    private float doomLevel = 5.0F;
    private boolean protectedFlag = true;
    /** 読み出しの偽装（Java エージェントが呼び出し箇所ごと isAlive を書き換えた状態の再現） */
    public boolean spoofDead;

    public FakeStatefulMob(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean isAlive() {
        return !spoofDead && super.isAlive();
    }

    @Override public boolean fakemod$isMarkedForDeath() { return markedForDeath; }
    @Override public void fakemod$setMarkedForDeath(boolean marked) { markedForDeath = marked; }
    @Override public float fakemod$getDoomLevel() { return doomLevel; }
    @Override public void fakemod$setDoomLevel(float level) { doomLevel = level; }
    @Override public boolean fakemod$isProtected() { return protectedFlag; }
    @Override public void fakemod$setProtected(boolean value) { protectedFlag = value; }
}
