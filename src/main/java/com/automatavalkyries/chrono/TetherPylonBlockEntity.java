package com.automatavalkyries.chrono;

import com.automatavalkyries.registry.ModChrono;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * テザー・パイロン（設計書 §4.5）: 拠点の電力（隣の発電機・蓄電器、搬入リンクの先）を吸い上げ、範囲内のクロノリンク・マシンへ無線で送る。
 * 搬出リンクの先（他 MOD の機械など）へも送り、充電スロットの FE アイテムも充電する。範囲は空間リンクと同じ（T3 から異次元）。
 * 1 台の送電の上限は T1 5,000〜T4 500,000 FE/Tick（コアの放電レートにそろえる）。兵姫への給電は兵姫を実装してからつなぐ。
 */
public class TetherPylonBlockEntity extends ChronoMachineBlockEntity {

    private int targetsLast, sentLast;

    public TetherPylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.PYLON_ENTITY.get(), ChronoMachineType.TETHER_PYLON, pos, state);
    }

    @Override protected int maxReceive(ChronoTier tier) { return tier.powerRate * 8; }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return slot == 0 && stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
    }

    @Override
    protected boolean work(ChronoTier tier) {
        int rate = tier.powerRate;
        // 吸い上げ: 隣の出せる物（他のパイロンは除く）と、搬入リンクの先
        int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
        for (Direction side : Direction.values()) {
            if (room <= 0) break;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null || neighbor instanceof TetherPylonBlockEntity) continue;
            IEnergyStorage source = neighbor.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve().orElse(null);
            if (source == null || !source.canExtract()) continue;
            int got = source.extractEnergy(Math.min(room, rate * 2), false);
            energy.produce(got);
            room -= got;
        }
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pulls() || room <= 0) continue;
            IEnergyStorage source = ChronoLinks.energy(level, worldPosition, tier, link);
            if (source == null || !source.canExtract()) continue;
            int got = source.extractEnergy(Math.min(room, rate * 2), false);
            energy.produce(got);
            room -= got;
        }
        // 送電: 範囲内のクロノリンク・マシン（受け取れる物）・搬出リンクの先・充電スロット
        List<IEnergyStorage> targets = new ArrayList<>();
        for (ChronoMachineBlockEntity machine : ChronoMachineBlockEntity.LOADED) {
            if (machine == this || machine instanceof TetherPylonBlockEntity || machine.isRemoved() || machine.getLevel() == null) continue;
            boolean same = machine.getLevel() == level;
            if (!tier.reaches(same, same ? worldPosition.distSqr(machine.getBlockPos()) : 0)) continue;
            if (machine.energy().canReceive() && machine.energy().getEnergyStored() < machine.energy().getMaxEnergyStored()) targets.add(machine.energy());
        }
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pushes()) continue;
            IEnergyStorage target = ChronoLinks.energy(level, worldPosition, tier, link);
            if (target != null && target.canReceive() && !targets.contains(target)) targets.add(target);
        }
        items.getStackInSlot(0).getCapability(ForgeCapabilities.ENERGY).ifPresent(targets::add);
        int budget = Math.min(rate, energy.getEnergyStored());
        int sent = 0;
        for (int i = 0; i < targets.size() && budget > 0; i++) {
            int share = Math.max(1, budget / (targets.size() - i));
            int accepted = targets.get(i).receiveEnergy(share, false);
            budget -= accepted;
            sent += accepted;
        }
        if (sent > 0) energy.consume(sent);
        targetsLast = targets.size();
        sentLast = sent;
        return sent > 0;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> targetsLast;
            case 1 -> sentLast & 0xFFFF;
            case 2 -> sentLast >>> 16;
            default -> 0;
        };
    }
}
