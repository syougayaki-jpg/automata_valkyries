package com.automatavalkyries.machine;

import net.minecraftforge.energy.EnergyStorage;

/** マシンの電力の蓄え（FE）。変わったら保存の印を付ける */
public class MachineEnergy extends EnergyStorage {

    private final Runnable onChanged;

    public MachineEnergy(int capacity, int maxReceive, int maxExtract, Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);
        if (received > 0 && !simulate) onChanged.run();
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);
        if (extracted > 0 && !simulate) onChanged.run();
        return extracted;
    }

    /** マシン自身が作る・使う（受け取り・取り出しの上限に関係なく） */
    public void produce(int amount) {
        int before = energy;
        energy = Math.min(capacity, energy + amount);
        if (energy != before) onChanged.run();
    }

    public boolean consume(int amount) {
        if (energy < amount) return false;
        energy -= amount;
        onChanged.run();
        return true;
    }

    public void set(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
