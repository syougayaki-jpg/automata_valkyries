package com.automatavalkyries.gametest.fake;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** 試験用: 倒れていないのに isAlive が偽と答えるボス（Madness Entities の「生きていない扱いのプロキシ」と同じ型） */
public class FakeDeadSeemingBoss extends Zombie {

    public FakeDeadSeemingBoss(Level level) {
        super(EntityType.ZOMBIE, level);
    }

    @Override
    public boolean isAlive() {
        return false;
    }
}
