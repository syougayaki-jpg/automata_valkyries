package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.machine.ValkyrieChargerBlock;
import com.automatavalkyries.machine.ValkyrieChargerBlockEntity;
import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 兵姫用充電台の GameTest: FE の受け取りと drain、座る権利（claim / release / 期限切れ）、近くの充電台探し */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieChargerGameTests {

    private static final String TEMPLATE = "empty";

    private static ValkyrieChargerBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModMachines.VALKYRIE_CHARGER.get());
        if (!(helper.getBlockEntity(pos) instanceof ValkyrieChargerBlockEntity charger)) throw new IllegalStateException("charger has no block entity");
        return charger;
    }

    /** どの面からも FE を受け取り、外へは出さず、drain で渡す */
    @GameTest(template = TEMPLATE)
    public static void chargerStoresAndDrainsEnergy(GameTestHelper helper) {
        ValkyrieChargerBlockEntity charger = place(helper, new BlockPos(1, 2, 1));
        int total = 0;
        for (Direction side : Direction.values()) {
            IEnergyStorage storage = charger.getCapability(ForgeCapabilities.ENERGY, side)
                    .orElseThrow(() -> new AssertionError("no energy capability on " + side));
            helper.assertTrue(storage.canReceive() && !storage.canExtract(), "charger should only receive (" + side + ")");
            total += storage.receiveEnergy(1_000, false);
        }
        helper.assertTrue(total == 6_000 && charger.stored() == 6_000, "expected 6000 FE, stored " + charger.stored());
        helper.assertTrue(charger.drain(2_500) == 2_500, "drain(2500) did not give 2500");
        helper.assertTrue(charger.drain(10_000) == 3_500, "drain should give only what is left");
        helper.assertTrue(charger.drain(10) == 0 && charger.stored() == 0, "empty charger gave energy");
        helper.succeed();
    }

    /** 1 体だけ座れる。release で空き、claim を呼ばれなくなれば期限切れで空いて光も消える */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void chargerClaimIsExclusiveAndExpires(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        ValkyrieChargerBlockEntity charger = place(helper, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        helper.assertTrue(charger.claim(a), "free charger refused a");
        helper.assertTrue(charger.claim(a), "same valkyrie should keep its claim");
        helper.assertFalse(charger.claim(b), "b took a charger a is using");
        charger.release(b); // 他の兵姫の release は効かない
        helper.assertTrue(a.equals(charger.claimant()), "release by b freed a's claim");
        charger.release(a);
        helper.assertTrue(charger.claim(b), "released charger refused b");
        charger.setCharging(true);
        helper.assertTrue(helper.getBlockState(pos).getValue(ValkyrieChargerBlock.LIT), "setCharging(true) did not light");
        helper.assertTrue(ValkyrieChargerBlockEntity.findNear(helper.getLevel(), helper.absolutePos(pos), 4).contains(charger), "findNear missed the charger");
        helper.runAfterDelay(ValkyrieChargerBlockEntity.CLAIM_TIMEOUT + 5, () -> {
            helper.assertTrue(charger.claimant() == null, "claim did not expire");
            helper.assertFalse(helper.getBlockState(pos).getValue(ValkyrieChargerBlock.LIT), "expired charger still lit");
            helper.assertTrue(charger.claim(a), "expired charger refused a");
            helper.succeed();
        });
    }
}
