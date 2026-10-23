package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.block.ZeroPortalBlock;
import com.automatavalkyries.registry.ModTechBlocks;
import com.automatavalkyries.world.TimeRift;
import com.automatavalkyries.world.ZeroGrave;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 零の墓標（2026-10-20 ユーザー指定「裏ボスをつくりますか」。時の狭間の奥）: 次元があり、闘技場（真ん中・着く所の甲板・帰りの門の眠った膜 3×5）が描け、
 * 着く所の床は穴が開いても入る前に埋まる。時の狭間の紅の門はタイムルーラーを倒すと目覚め、次の戦いの間は眠る。
 * 区画によっては世界の側でエンティティが動かないので、Tick を待たずに直に確かめる
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ZeroGraveGameTests {

    /** 闘技場が描け、着く所・真ん中・帰りの門があり、描いた直後は壊れていない */
    @GameTest(template = "empty", batch = "zero_grave", timeoutTicks = 20)
    public static void zeroGraveArenaIsBuilt(GameTestHelper helper) {
        ServerLevel grave = helper.getLevel().getServer().getLevel(ZeroGrave.GRAVE);
        helper.assertTrue(grave != null, "the zero grave dimension is missing");
        ZeroGrave.ensureArena(grave);
        int y = ZeroGrave.Y;
        helper.assertTrue(grave.getBlockState(new BlockPos(0, y, 0)).is(Blocks.CRYING_OBSIDIAN), "no crying obsidian at the arena centre");
        helper.assertFalse(grave.getBlockState(new BlockPos(0, y, ZeroGrave.LAND_Z)).isAir(), "no floor where players land");
        helper.assertFalse(grave.getBlockState(new BlockPos(0, y, ZeroGrave.GATE_Z)).isAir(), "no deck under the return gate");
        helper.assertFalse(grave.getBlockState(new BlockPos(0, y, -17)).isAir(), "no floor under where the boss appears");
        int membrane = 0;
        for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 5; h++) {
            if (grave.getBlockState(new BlockPos(dx, y + h, ZeroGrave.GATE_Z)).is(ModTechBlocks.ZERO_PORTAL.get())) membrane++;
        }
        helper.assertTrue(membrane == 15, "the return gate should be 15 gate blocks, found " + membrane);
        helper.succeed();
    }

    /** 着く所の床に穴が開いていても、入る前に埋まる（奈落に落ちない） */
    @GameTest(template = "empty", batch = "zero_grave", timeoutTicks = 20)
    public static void zeroGraveLandingIsNeverVoid(GameTestHelper helper) {
        ServerLevel grave = helper.getLevel().getServer().getLevel(ZeroGrave.GRAVE);
        helper.assertTrue(grave != null, "no zero grave dimension");
        ZeroGrave.ensureArena(grave);
        BlockPos landing = new BlockPos(0, ZeroGrave.Y, ZeroGrave.LAND_Z);
        BlockPos beside = landing.offset(-1, 0, 2);
        BlockState oldLanding = grave.getBlockState(landing), oldBeside = grave.getBlockState(beside);
        grave.setBlock(landing, Blocks.AIR.defaultBlockState(), 2);
        grave.setBlock(beside, Blocks.AIR.defaultBlockState(), 2);
        try {
            ZeroGrave.ensureLanding(grave);
            helper.assertFalse(grave.getBlockState(landing).isAir(), "the landing spot was not filled");
            helper.assertFalse(grave.getBlockState(beside).isAir(), "a hole next to the landing spot was not filled");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                helper.assertFalse(grave.getBlockState(landing.offset(dx, 0, dz)).isAir(), "void in the landing 5x5 at " + dx + "," + dz);
            }
        } finally {
            grave.setBlock(landing, oldLanding, 2);
            grave.setBlock(beside, oldBeside, 2);
        }
        helper.succeed();
    }

    /** 時の狭間の紅の門: 開けると枠と目覚めた膜 3×5 が北に立ち、眠らせると膜は眠る */
    @GameTest(template = "empty", batch = "zero_grave", timeoutTicks = 20)
    public static void zeroGraveEntryGateInTimeRift(GameTestHelper helper) {
        ServerLevel rift = helper.getLevel().getServer().getLevel(TimeRift.RIFT);
        helper.assertTrue(rift != null, "no time rift dimension");
        TimeRift.ensureArena(rift);
        ZeroGrave.openEntryGate(rift);
        try {
            int awake = 0;
            for (int dx = -1; dx <= 1; dx++) for (int h = 1; h <= 5; h++) {
                BlockState s = rift.getBlockState(new BlockPos(dx, TimeRift.Y + h, ZeroGrave.ENTRY_Z));
                if (s.is(ModTechBlocks.ZERO_PORTAL.get()) && s.getValue(ZeroPortalBlock.AWAKE)) awake++;
            }
            helper.assertTrue(awake == 15, "the crimson gate should be 15 awake gate blocks, found " + awake);
            helper.assertFalse(rift.getBlockState(new BlockPos(2, TimeRift.Y + 1, ZeroGrave.ENTRY_Z)).isAir(), "the crimson gate has no frame");
        } finally {
            ZeroGrave.setEntryGate(rift, false);   // 試しの世界の門は眠らせておく
        }
        helper.assertFalse(rift.getBlockState(new BlockPos(0, TimeRift.Y + 1, ZeroGrave.ENTRY_Z)).getValue(ZeroPortalBlock.AWAKE), "the crimson gate did not go back to sleep");
        helper.succeed();
    }
}
