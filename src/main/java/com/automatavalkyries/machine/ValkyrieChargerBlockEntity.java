package com.automatavalkyries.machine;

import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 兵姫用充電台（SPEC_VALKYRIE_GUI_AI §4）: どの面からも FE を受け取って蓄え、座った兵姫 1 体が {@link #drain} で吸う。
 * 外へは出さない（ケーブルからは使うだけの物に見える）。座る権利は {@link #claim} を {@link #CLAIM_TIMEOUT} Tick 呼ばれなければ切れる。
 */
public class ValkyrieChargerBlockEntity extends BlockEntity {

    /** 蓄えられる FE【仮】 */
    public static final int CAPACITY = 2_000_000;
    /** 1 Tick に受け取れる FE【仮】 */
    public static final int MAX_RECEIVE = 10_000;
    /** claim がこの Tick 数呼ばれなければ空きに戻す */
    public static final int CLAIM_TIMEOUT = 100;
    /** 座面の高さ（ブロックの下端から） */
    public static final double SEAT_HEIGHT = 6.0 / 16.0;

    private final MachineEnergy energy = new MachineEnergy(CAPACITY, MAX_RECEIVE, 0, this::setChanged);
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    @Nullable private UUID claimant;
    private long claimedAt;

    public ValkyrieChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.VALKYRIE_CHARGER_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ValkyrieChargerBlockEntity charger) {
        if (charger.claimant != null && charger.expired()) charger.claimant = null;
        boolean lit = state.getValue(ValkyrieChargerBlock.LIT);
        if (lit && charger.claimant == null) charger.setCharging(false); // 座っていた兵姫が居なくなった
        else if (lit && level instanceof ServerLevel server && level.getGameTime() % 4 == 0) charger.sparkle(server);
    }

    /** 充電中の光の粒（座面のまわり） */
    private void sparkle(ServerLevel level) {
        Vec3 seat = seatPos();
        var random = level.random;
        double angle = random.nextDouble() * Math.PI * 2;
        double r = 0.45;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, seat.x + Math.cos(angle) * r, seat.y + 0.1 + random.nextDouble() * 0.8,
                seat.z + Math.sin(angle) * r, 1, 0, 0.05, 0, 0.02);
        if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.GLOW, seat.x, seat.y + 0.05, seat.z, 1, 0.3, 0.02, 0.3, 0.01);
    }

    // ---- 兵姫の AI から使う -------------------------------------------------------

    public Direction facing() {
        return getBlockState().getValue(ValkyrieChargerBlock.FACING);
    }

    /** 兵姫が座る位置（ブロックの上面の中心、向きに合わせて） */
    public Vec3 seatPos() {
        Direction front = facing();
        // 背もたれの分だけ前へ寄せる
        return new Vec3(worldPosition.getX() + 0.5 + front.getStepX() * 0.0625, worldPosition.getY() + SEAT_HEIGHT,
                worldPosition.getZ() + 0.5 + front.getStepZ() * 0.0625);
    }

    /** 座る向き（ヨー、度） */
    public float seatYaw() {
        return facing().toYRot();
    }

    /** 蓄えた FE から最大 max を渡す（渡した量） */
    public int drain(int max) {
        int amount = Math.min(Math.max(0, max), energy.getEnergyStored());
        if (amount <= 0) return 0;
        energy.set(energy.getEnergyStored() - amount);
        setChanged();
        return amount;
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public MachineEnergy energy() {
        return energy;
    }

    /** 使っている兵姫の UUID（無ければ・切れていれば null） */
    @Nullable
    public UUID claimant() {
        return claimant == null || expired() ? null : claimant;
    }

    /** 1 体だけ座れる。空いていれば取って true（同じ兵姫なら延長して true）。座っている間は CLAIM_TIMEOUT より短い間隔で呼び続ける */
    public boolean claim(UUID valkyrie) {
        UUID current = claimant();
        if (current != null && !current.equals(valkyrie)) return false;
        claimant = valkyrie;
        claimedAt = now();
        return true;
    }

    public void release(UUID valkyrie) {
        if (claimant != null && claimant.equals(valkyrie)) {
            claimant = null;
            setCharging(false);
        }
    }

    /** lit を切り替える */
    public void setCharging(boolean on) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(ValkyrieChargerBlock.LIT) || state.getValue(ValkyrieChargerBlock.LIT) == on) return;
        level.setBlock(worldPosition, state.setValue(ValkyrieChargerBlock.LIT, on), 3);
    }

    /** 半径 radius の中の充電台（近い順） */
    public static List<ValkyrieChargerBlockEntity> findNear(ServerLevel level, BlockPos center, int radius) {
        List<ValkyrieChargerBlockEntity> found = new ArrayList<>();
        long limit = (long) radius * radius;
        int minX = SectionPos.blockToSectionCoord(center.getX() - radius), maxX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - radius), maxZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // 読み込まれていない区画は見ない
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ValkyrieChargerBlockEntity charger && !charger.isRemoved()
                            && charger.worldPosition.distSqr(center) <= limit) found.add(charger);
                }
            }
        }
        found.sort(Comparator.comparingDouble(c -> c.worldPosition.distSqr(center)));
        return found;
    }

    private boolean expired() {
        return now() - claimedAt > CLAIM_TIMEOUT;
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    // ---- 保存・能力 ---------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy", energy.getEnergyStored());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.set(tag.getInt("energy"));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }
}
