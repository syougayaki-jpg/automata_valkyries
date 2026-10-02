package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 電力ケーブルのネットワーク。つながったケーブル全体を 1 Tick に 1 回だけ（最初に Tick したケーブルが）まとめて処理する:
 * 発電するだけの物（燃料発電機）と蓄電器から、使うだけの物（加工機）へ配り、余った発電を蓄電器へ入れる。
 * 1 Tick に流せる量はネットワーク全体で {@link #THROUGHPUT} まで。
 * 他 MOD の蓄電ブロックは受け手として扱う（入れるだけで、引き出すのは出すだけの側の面からだけ）。
 */
public class CableBlockEntity extends BlockEntity {

    /** 1 Tick にネットワークが流せる電力（FE） */
    public static final int THROUGHPUT = 2_000;
    /** 1 つのネットワークのケーブルの上限（これを超えた分はつながっていない扱い） */
    private static final int MAX_CABLES = 1_024;

    private long lastTick = Long.MIN_VALUE;

    public CableBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.CABLE_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CableBlockEntity cable) {
        long now = level.getGameTime();
        if (cable.lastTick == now) return; // 同じネットワークの別のケーブルが、この Tick の分をもう処理した
        List<IEnergyStorage> producers = new ArrayList<>(), storages = new ArrayList<>(), consumers = new ArrayList<>();
        collect(level, pos, now, producers, storages, consumers);
        distribute(producers, storages, consumers);
    }

    /** つながったケーブルをたどり、端につながった電力の持ち主を種類ごとに集める */
    private static void collect(Level level, BlockPos start, long now, List<IEnergyStorage> producers,
                                List<IEnergyStorage> storages, List<IEnergyStorage> consumers) {
        Set<BlockPos> visited = new HashSet<>();
        Set<IEnergyStorage> endpoints = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() <= MAX_CABLES) {
            BlockPos pos = queue.poll();
            if (level.getBlockEntity(pos) instanceof CableBlockEntity cable) cable.lastTick = now;
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (!level.isLoaded(next)) continue;
                BlockEntity neighbor = level.getBlockEntity(next);
                if (neighbor instanceof CableBlockEntity) {
                    if (visited.add(next)) queue.add(next);
                    continue;
                }
                if (neighbor == null) continue;
                neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).ifPresent(storage -> {
                    if (!endpoints.add(storage)) return;
                    // 出し入れ両方できる物のうち、蓄電側として引き出すのはこのMODの蓄電器だけ。
                    // 他 MOD の物（Mekanism のエネルギーキューブ等）は受け手として入れるだけにする（勝手に吸い出さず、蓄電どうしの行き来も起きない）
                    boolean ownBattery = neighbor instanceof MachineBlockEntity machine && machine.kind().hasCharge();
                    if (storage.canExtract() && storage.canReceive()) (ownBattery ? storages : consumers).add(storage);
                    else if (storage.canExtract()) producers.add(storage);
                    else if (storage.canReceive()) consumers.add(storage);
                });
            }
        }
    }

    private static void distribute(List<IEnergyStorage> producers, List<IEnergyStorage> storages, List<IEnergyStorage> consumers) {
        int budget = THROUGHPUT;
        // ① 発電 → 加工機、② 蓄電器 → 加工機
        for (IEnergyStorage consumer : consumers) {
            budget -= move(producers, consumer, budget);
            budget -= move(storages, consumer, budget);
            if (budget <= 0) return;
        }
        // ③ 余った発電 → 蓄電器
        for (IEnergyStorage storage : storages) {
            budget -= move(producers, storage, budget);
            if (budget <= 0) return;
        }
    }

    /** sources から target へ、limit まで移す。移した量を返す */
    private static int move(List<IEnergyStorage> sources, IEnergyStorage target, int limit) {
        int moved = 0;
        for (IEnergyStorage source : sources) {
            if (source == target || moved >= limit) continue;
            int want = target.receiveEnergy(limit - moved, true);
            if (want <= 0) break;
            int got = source.extractEnergy(want, false);
            if (got <= 0) continue;
            int accepted = target.receiveEnergy(got, false);
            if (accepted < got) source.receiveEnergy(got - accepted, false); // 受け取りきれなかった分は戻す（蓄電器なら戻せる）
            moved += accepted;
        }
        return moved;
    }
}
