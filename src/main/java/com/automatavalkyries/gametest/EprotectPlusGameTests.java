package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class EprotectPlusGameTests {
    @GameTest(template = "empty")
    @SuppressWarnings("unchecked")
    public static void dictionaryRepairPreservesConfiguration(GameTestHelper helper) throws Exception {
        if (!EprotectPlusLink.isAgentActive()) { helper.succeed(); return; }
        var extraConfig = com.automatavalkyries.config.EprotectConfig.FLAG_EXTRA;
        var ignoreConfig = com.automatavalkyries.config.EprotectConfig.FLAG_IGNORE;
        var originalExtra = java.util.List.copyOf(com.automatavalkyries.config.EprotectConfig.getList(extraConfig));
        var originalIgnore = java.util.List.copyOf(com.automatavalkyries.config.EprotectConfig.getList(ignoreConfig));
        Class<?> dictionary = com.automatavalkyries.protect.flag.FlagDictionary.class;
        var builtInField = dictionary.getDeclaredField("BUILT_IN");
        var activeField = dictionary.getDeclaredField("active");
        builtInField.setAccessible(true);
        activeField.setAccessible(true);
        var builtIn = (java.util.Map<Object, java.util.List<String>>) builtInField.get(null);
        var saved = new java.util.LinkedHashMap<>(builtIn);
        Object kill = com.automatavalkyries.protect.flag.FlagDictionary.Category.KILL;
        try {
            extraConfig.set(java.util.List.of("KILL:*codexregression"));
            ignoreConfig.set(java.util.List.of("ignoredRegression"));
            com.automatavalkyries.protect.flag.FlagDictionary.reloadFromConfig();
            helper.assertFalse(com.automatavalkyries.protect.flag.FlagDictionaryLink.verifyAndRepair(), "Valid config falsely repaired");
            var altered = new java.util.ArrayList<>(builtIn.get(kill));
            altered.set(0, "unexpectedReplacement");
            builtIn.put(kill, altered);
            helper.assertTrue(com.automatavalkyries.protect.flag.FlagDictionaryLink.verifyAndRepair(), "Equal-length corruption missed");
            var active = (java.util.Map<Object, java.util.List<String>>) activeField.get(null);
            helper.assertTrue(active.get(kill).contains("*codexregression"), "User extra lost during repair");
            helper.assertTrue(com.automatavalkyries.protect.flag.FlagDictionary.ignored("ignoredRegression"), "User ignore lost during repair");
            helper.assertTrue(saved.equals(builtIn), "Built-in content not restored");
            helper.assertFalse(com.automatavalkyries.protect.flag.FlagDictionaryLink.verifyAndRepair(), "Repeated false repair");
        } finally {
            builtIn.clear();
            builtIn.putAll(saved);
            extraConfig.set(originalExtra);
            ignoreConfig.set(originalIgnore);
            com.automatavalkyries.protect.flag.FlagDictionary.reloadFromConfig();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exceptionAndNestedScopesRunOnceAndRestore(GameTestHelper helper) {
        int[] executions = {0};
        RuntimeException expected = new RuntimeException("scope regression");
        helper.assertFalse(EprotectPlusLink.isPrivilegedOrBypassing(), "Initial scope leaked");
        EprotectPlusLink.runPrivileged(() -> {
            try {
                EprotectPlusLink.runWithCloakedStack(() -> {
                    executions[0]++;
                    helper.assertTrue(EprotectPlusLink.isPrivilegedOrBypassing(), "Inner scope absent");
                    throw expected;
                });
                helper.fail("Action exception swallowed");
            } catch (RuntimeException actual) {
                helper.assertTrue(actual == expected, "Exception identity lost");
            }
            helper.assertTrue(EprotectPlusLink.isPrivilegedOrBypassing(), "Outer scope lost");
        });
        helper.assertTrue(executions[0] == 1, "Exceptional action executed twice");
        helper.assertFalse(EprotectPlusLink.isPrivilegedOrBypassing(), "Scope leaked after exception");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preparationDoesNotClaimRemoval(GameTestHelper helper) {
        var mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertFalse(EprotectPlusLink.isAnnihilationConfirmed(mob), "Live entity reported removed");
        helper.assertTrue("attempted".equals(EprotectPlusLink.prepareAnnihilation(mob).get("status")), "Preparation unavailable on server");
        helper.assertFalse(EprotectPlusLink.isAnnihilationConfirmed(mob), "Health write reported removal");
        helper.assertFalse(EprotectPlusLink.annihilateTargetWithAgent(null), "Null target accepted");
        helper.succeed();
    }
}
