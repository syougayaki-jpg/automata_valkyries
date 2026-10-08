package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.McNames;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 配布環境の名前（SRG 名）を公式名に直す表がビルドで作られて MOD に入り、引けること（2026-10-08） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class McNamesGameTests {

    @GameTest(template = "empty", batch = "mc_names")
    public static void releaseNamesTranslateToOfficialNames(GameTestHelper helper) {
        helper.assertTrue(McNames.loaded(), "the SRG name table is missing from the mod");
        helper.assertTrue("removalReason".equals(McNames.official("f_146795_")), "Entity.removalReason was not translated: " + McNames.official("f_146795_"));
        helper.assertTrue("tickNonPassenger".equals(McNames.official("m_8647_")), "ServerLevel.tickNonPassenger was not translated: " + McNames.official("m_8647_"));
        helper.assertTrue("tick".equals(McNames.official("tick")), "an official name was changed");
        helper.assertTrue("myModField".equals(McNames.official("myModField")), "another mod's name was changed");
        helper.succeed();
    }
}
