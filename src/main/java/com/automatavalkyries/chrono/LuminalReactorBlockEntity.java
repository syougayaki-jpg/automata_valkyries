package com.automatavalkyries.chrono;

import com.automatavalkyries.registry.ModBlocks;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * ルミナル反応炉（設計書 §4.1）: ルミナル系の圧縮素材を燃料にして発電する。オーブ 1 個 = 5,000,000 FE を基準に、段ごとに 9 倍。
 * 作った電力は隣のマシン・テザー・パイロンへ渡し、搬出リンクの先（他 MOD の蓄電器なども）へも無線で送る。
 * 燃料は搬入リンクの先からも引く。T4 は創世ルミナルコアを置くと、燃料と別に毎秒 1,000,000 FE（毎 Tick 50,000 FE）を永久に作る（§9.3）。
 */
public class LuminalReactorBlockEntity extends ChronoMachineBlockEntity {

    /** 燃料の残り（FE）と、最後に燃やした燃料の量（画面の割合） */
    private long pending, lastFuel;
    private int generatedLastTick;

    public LuminalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.REACTOR_ENTITY.get(), ChronoMachineType.LUMINAL_REACTOR, pos, state);
    }

    /** 燃料 1 個の電力（そのティアで燃やせなければ 0） */
    public static long fuelValue(ItemStack stack, ChronoTier tier) {
        if (stack.is(ModItems.CRYSTAL_LUMINAL.get())) return 60_000L;
        if (stack.is(ModBlocks.BLOCK_LUMINAL_GEM.get().asItem())) return 540_000L;
        if (stack.is(ModItems.ORB_LUMINAL.get())) return tier.level >= 2 ? 5_000_000L : 0;
        if (stack.is(ModItems.STAR_LUMINAL.get())) return tier.level >= 3 ? 50_000_000L : 0;
        // 創世ルミナルコアを燃料の枠に入れると、スターの 9 倍を燃やす（T4。2026-10-29 ユーザー指定「ルミナル反応炉をルミナルコアまで対応させて」「ティア４で単純に長く持つ」。
        // コアの枠に置いた時の永久の発電はそのまま。燃やすか置いておくかは選べる）
        if (stack.is(com.automatavalkyries.registry.ModCompression.CORE_GENESIS_LUMINAL.get())) return tier.level >= 4 ? CORE_FUEL : 0;
        return 0;
    }

    /** 発電の速さ（FE/Tick）: T1 1,000、T2 10,000、T3 100,000、T4 1,000,000 */
    public static int generation(ChronoTier tier) {
        return switch (tier) {
            case HIGH_END -> 1_000;
            case FUTURE -> 10_000;
            case BEYOND -> 100_000;
            case NEVERLAST -> 1_000_000;
        };
    }

    @Override protected int maxReceive(ChronoTier tier) { return 0; }
    @Override protected int maxExtract(ChronoTier tier) { return generation(tier) * 2; }

    /** 創世ルミナルコアを燃やした時の電力（スターの 9 倍） */
    public static final long CORE_FUEL = 450_000_000L;

    /** 創世ルミナルコアの発電（FE/Tick）: 毎秒 1,000,000 FE */
    public static final int GENESIS_PER_TICK = 50_000;

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        boolean core = stack.is(com.automatavalkyries.registry.ModCompression.CORE_GENESIS_LUMINAL.get());
        if (slot == 1) return tier() == ChronoTier.NEVERLAST && core;
        // 燃やすのはコアの枠が埋まっている時だけ（Shift クリックで永久の発電の枠より先に燃料の枠へ入って燃えないように）
        if (core && items.getStackInSlot(1).isEmpty()) return false;
        return slot == 0 && fuelValue(stack, tier()) > 0;
    }



    @Override
    protected boolean work(ChronoTier tier) {
        if (ticks % 4 == 0) pullFromLinks(tier, 0, 1, s -> fuelValue(s, tier) > 0 && !s.is(com.automatavalkyries.registry.ModCompression.CORE_GENESIS_LUMINAL.get())); // コアはリンク先から勝手に燃やさない
        generatedLastTick = 0;
        if (pending <= 0 && energy.getEnergyStored() < energy.getMaxEnergyStored()) {
            ItemStack fuel = items.getStackInSlot(0);
            long value = fuelValue(fuel, tier);
            if (value > 0) {
                items.extractItem(0, 1, false);
                pending = lastFuel = value;
            }
        }
        if (pending > 0) {
            int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
            int made = (int) Math.min(Math.min(pending, generation(tier)), room);
            if (made > 0) {
                energy.produce(made);
                pending -= made;
                generatedLastTick = made;
            }
        }
        if (tier == ChronoTier.NEVERLAST && items.getStackInSlot(1).is(com.automatavalkyries.registry.ModCompression.CORE_GENESIS_LUMINAL.get())) {
            int room = energy.getMaxEnergyStored() - energy.getEnergyStored();
            int made = Math.min(GENESIS_PER_TICK, room);
            if (made > 0) {
                energy.produce(made);
                generatedLastTick += made;
            }
        }
        int limit = generation(tier) * 2;
        pushEnergyToNeighbors(limit);
        for (ChronoLink link : activeLinks(tier)) {
            if (!link.mode().pushes() || energy.getEnergyStored() <= 0) continue;
            IEnergyStorage target = ChronoLinks.energy(level, worldPosition, tier, link);
            if (target != null && target.canReceive()) {
                int sent = target.receiveEnergy(Math.min(limit, energy.getEnergyStored()), false);
                if (sent > 0) energy.consume(sent);
            }
        }
        return generatedLastTick > 0;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> generatedLastTick & 0xFFFF;
            case 1 -> generatedLastTick >>> 16;
            case 2 -> lastFuel > 0 ? (int) (1000 * pending / lastFuel) : 0;
            default -> 0;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("pending", pending);
        tag.putLong("lastFuel", lastFuel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pending = tag.getLong("pending");
        lastFuel = tag.getLong("lastFuel");
    }
}
