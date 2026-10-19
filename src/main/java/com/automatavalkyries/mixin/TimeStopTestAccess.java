package com.automatavalkyries.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 試験用: ServerLevel の時刻の進み（tickTime）を直に呼ぶ（時間停止で時刻が止まるかを確かめる。2026-10-19） */
@Mixin(ServerLevel.class)
public interface TimeStopTestAccess {

    @Invoker("tickTime")
    void automata_valkyries$tickTime();
}
