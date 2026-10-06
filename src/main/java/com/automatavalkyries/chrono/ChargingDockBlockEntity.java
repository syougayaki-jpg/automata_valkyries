package com.automatavalkyries.chrono;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModChrono;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.LuminalCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 給電ドック（docs/SPEC_CHRONOLINK_MACHINES.md §4.7。2026-10-07 ユーザー指定「給電ドックを作って」）: 兵姫の給電・修復。
 * <ul>
 *   <li>ドックの上（とすぐ横）に立った兵姫へ、ドックの電力を毎 Tick ティアの送電の速さ（{@link ChronoTier#powerRate}）で送る。
 *       兵姫の電力が満タンなら、兵姫が持つルミナルコアへ</li>
 *   <li>オーブの枠に光子励起のオーブ（{@code orb_luminal}）: 立った兵姫のコアへ 5,000,000 FE を一瞬で入れる（入り切らない分は兵姫の電力へ。§9.3）</li>
 *   <li>T2 から: 溶液の枠のナノマシン溶液で、機能停止（HP 0）の兵姫を HP 50% で修復する。HP が半分を割った兵姫も 1 本で 20 回復</li>
 *   <li>コアの枠: ルミナルコアを置けば、同じ速さで充電する</li>
 * </ul>
 * ドックの電力は隣の発電機・ケーブル・空間リンク・テザー・パイロンから。兵姫は電力が減ると自分で来る（ValkyrieDaily.SelfCharge）
 */
public class ChargingDockBlockEntity extends ChronoMachineBlockEntity {

    public static final int ORB_SLOT = 0, NANO_SLOT = 1, CORE_SLOT = 2;
    public static final int ORB_FE = 5_000_000;
    public static final float NANO_HEAL = 20.0F;
    private int docked, sent;
    /** 前の Tick にドックにいた兵姫（降りたら充電の姿勢を戻す） */
    private final java.util.Set<ValkyrieEntity> lastDocked = new java.util.HashSet<>();

    public ChargingDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModChrono.DOCK_ENTITY.get(), ChronoMachineType.CHARGING_DOCK, pos, state);
    }

    @Override
    protected boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case ORB_SLOT -> stack.is(ModItems.ORB_LUMINAL.get());
            case NANO_SLOT -> tier().level >= 2 && stack.is(ModWeapons.NANO_SOLUTION.get());
            case CORE_SLOT -> stack.getItem() instanceof LuminalCoreItem;
            default -> false;
        };
    }

    /** ドックに立っている兵姫（ドックの上と、すぐ横） */
    public List<ValkyrieEntity> dockedValkyries() {
        AABB area = new AABB(worldPosition).inflate(1.2, 0, 1.2).expandTowards(0, 2.5, 0);
        return level.getEntitiesOfClass(ValkyrieEntity.class, area, ValkyrieEntity::isAlive);
    }

    @Override
    protected boolean work(ChronoTier tier) {
        sent = 0;
        int rate = tier.powerRate;
        // コアの枠
        ItemStack core = items.getStackInSlot(CORE_SLOT);
        if (core.getItem() instanceof LuminalCoreItem c && !c.grade().infinite()) {
            int room = c.grade().capacity - ItemEnergy.get(core);
            int give = Math.min(Math.min(rate, room), energy.getEnergyStored());
            if (give > 0 && energy.consume(give)) {
                ItemEnergy.set(core, ItemEnergy.get(core) + give);
                sent += give;
                setChanged();
            }
        }
        List<ValkyrieEntity> list = dockedValkyries();
        docked = list.size();
        for (ValkyrieEntity gone : lastDocked) if (!list.contains(gone) && gone.isAlive()) gone.setCharging(false);
        lastDocked.clear();
        lastDocked.addAll(list);
        for (ValkyrieEntity v : list) {
            // 修復（T2 から。ナノマシン溶液）
            ItemStack nano = items.getStackInSlot(NANO_SLOT);
            if (tier.level >= 2 && !nano.isEmpty() && ticks % 20 == 0 && (v.isShutdown() || v.getHealth() < v.getMaxHealth() * 0.5F)) {
                v.repair(v.isShutdown() ? v.getMaxHealth() * 0.5F : NANO_HEAL);
                nano.shrink(1);
                setChanged();
                if (level instanceof ServerLevel server) {
                    Fx.spray(server, Fx.opt(ModParticles.GLOW.get(), 0x7CFFB0, 0.5F, 16), v.position().add(0, 1.0, 0), 14, 0.3, 0.6, 0.3, 0.02);
                }
            }
            // オーブ: 一瞬で 5,000,000 FE（コアへ、余りは兵姫の電力へ）
            ItemStack orb = items.getStackInSlot(ORB_SLOT);
            if (!orb.isEmpty()) {
                int rest = ORB_FE;
                ItemStack vc = v.getCore();
                if (vc.getItem() instanceof LuminalCoreItem c && !c.grade().infinite()) {
                    int put = Math.min(rest, c.grade().capacity - ItemEnergy.get(vc));
                    ItemEnergy.set(vc, ItemEnergy.get(vc) + put);
                    rest -= put;
                }
                v.setEnergy(v.getEnergy() + Math.min(rest, v.maxEnergy()));
                orb.shrink(1);
                setChanged();
                level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.4F);
                if (level instanceof ServerLevel server) Fx.shockwave(server, v.position().add(0, 0.05, 0), 0xFFE45A, 2.0F, 14);
            }
            // 送電（兵姫の電力 → 満タンなら兵姫のコア）
            int give = Math.min(rate, energy.getEnergyStored());
            int room = v.maxEnergy() - v.getEnergy();
            // 充電の姿勢（座る）は兵姫自身の電力を入れている間だけ。満タンでコアだけ充電しているときは立つ
            // （ドックの枠のコアの充電まで数えていて、満タンになっても座ったままだった。2026-10-07）
            v.setCharging(give > 0 && room > 0);
            if (give <= 0) continue;
            if (room > 0) {
                int g = Math.min(give, room);
                energy.consume(g);
                v.setEnergy(v.getEnergy() + g);
                sent += g;
                give -= g;
            }
            ItemStack vc = v.getCore();
            if (give > 0 && vc.getItem() instanceof LuminalCoreItem c && !c.grade().infinite()) {
                int g = Math.min(give, c.grade().capacity - ItemEnergy.get(vc));
                if (g > 0) {
                    energy.consume(g);
                    ItemEnergy.set(vc, ItemEnergy.get(vc) + g);
                    sent += g;
                }
            }
            if (sent > 0 && ticks % 10 == 0 && level instanceof ServerLevel server) {
                Fx.line(server, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.25F, 8), worldPosition.getCenter().add(0, 0.5, 0), v.position().add(0, 1.0, 0), 0.4);
            }
        }
        return sent > 0;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> docked;
            case 1 -> Math.min(0xFFFF, sent / 100);
            default -> 0;
        };
    }
}
