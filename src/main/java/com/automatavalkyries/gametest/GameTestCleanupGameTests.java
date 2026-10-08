package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import com.automatavalkyries.protect.EprotectRegistry;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** GameTest の片付け（試験場を置く前の discard）は保護対象も片付け、登録簿にも残さない（2026-10-08、残ったボスが溜まって偽の攻撃の記録を出していた） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class GameTestCleanupGameTests {

    @GameTest(template = "empty")
    public static void gameTestCleanupRemovesProtectedBosses(GameTestHelper helper) {
        com.automatavalkyries.protect.plus.EprotectPlusLink.resetReinforced();
        ElenorEntity elenor = helper.spawnWithNoFreeWill(ModEntities.ELENOR.get(), new BlockPos(2, 2, 2));
        EprotectRegistry registry = EprotectRegistry.get(helper.getLevel());
        registry.record(elenor, elenor.position()); // 前の試験で残ったボスの記録（ふだんは Tick ごとに写される）
        helper.assertTrue(registry.find(elenor.getUUID()) != null, "Elenor was not recorded to the registry");
        BlockPos at = elenor.blockPosition();
        StructureUtils.clearSpaceForStructure(new BoundingBox(at.getX() - 1, at.getY(), at.getZ() - 1, at.getX() + 1, at.getY() + 2, at.getZ() + 1),
                at.getY(), helper.getLevel());
        helper.assertTrue(elenor.isRemoved(), "the GameTest cleanup did not remove a protected boss");
        helper.assertTrue(registry.find(elenor.getUUID()) == null, "the cleaned-up boss is still in the registry");
        helper.succeed();
    }
}
