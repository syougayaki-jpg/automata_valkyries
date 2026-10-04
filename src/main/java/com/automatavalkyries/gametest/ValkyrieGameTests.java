package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 戦術兵姫（仮）の基本: 出せる・HP・表情の切り替えと保存 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieGameTests {

    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE, batch = "valkyrie")
    public static void valkyrieSpawnsAndKeepsExpression(GameTestHelper helper) {
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 1, 2, 1);
        helper.assertTrue(v.getMaxHealth() == 40.0F, "max health is not 40: " + v.getMaxHealth());
        helper.assertTrue(v.expressionName().equals("normal"), "starts with " + v.expressionName());
        v.setExpression(v.getExpression() + 1);
        helper.assertTrue(v.expressionName().equals("smile"), "expression did not advance: " + v.expressionName());
        v.setExpression(ValkyrieEntity.EXPRESSIONS.length); // 一周して戻る
        helper.assertTrue(v.getExpression() == 0, "expression did not wrap");
        v.setExpression(3);
        CompoundTag tag = new CompoundTag();
        v.addAdditionalSaveData(tag);
        ValkyrieEntity copy = helper.spawn(ModEntities.VALKYRIE.get(), 3, 2, 1);
        copy.readAdditionalSaveData(tag);
        helper.assertTrue(copy.expressionName().equals("angry"), "expression not saved: " + copy.expressionName());
        helper.assertFalse(v.removeWhenFarAway(1000), "valkyrie would despawn");
        helper.succeed();
    }
}
