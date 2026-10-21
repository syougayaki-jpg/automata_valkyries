package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.flag.FlagDictionaryLink;
import com.automatavalkyries.protect.plus.BundledAgents;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public final class AgentConfigGameTests {
    @GameTest(template = "empty")
    public static void configuredAgentsAreConnectedAndReportActualActivation(GameTestHelper helper) throws Exception {
        boolean general = BundledAgents.hasInstrumentation("com.automatavalkyries.eprotectplus.EprotectPlusAgent");
        boolean flags = BundledAgents.hasInstrumentation("com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent");
        helper.assertTrue(EprotectPlusLink.isAgentActive() == general, "General bridge disagrees with actual activation");
        helper.assertTrue(FlagDictionaryLink.isAgentActive() == flags, "Dictionary class visibility was mistaken for activation");
        if (BundledAgents.EPROTECT_PLUS.get()) helper.assertTrue(general, "Configured Eprotect+ was not attached");
        if (BundledAgents.FLAG_DICTIONARY.get()) helper.assertTrue(flags, "Configured dictionary agent was not attached");
        boolean startupAgent = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.startsWith("-javaagent:") && (arg.contains("eprotect-plus") || arg.contains("flag-agent")));
        if (!startupAgent) {
            helper.assertTrue(general == BundledAgents.EPROTECT_PLUS.get(), "Disabled general agent was unexpectedly started");
            helper.assertTrue(flags == BundledAgents.FLAG_DICTIONARY.get(), "Disabled dictionary agent was unexpectedly started");
        }
        if (BundledAgents.EPROTECT_PLUS.get()) assertObserved(helper,
                "com.automatavalkyries.eprotectplus.EprotectPlusAgent", BundledAgents.class);
        if (BundledAgents.FLAG_DICTIONARY.get()) assertObserved(helper,
                "com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent",
                com.automatavalkyries.protect.flag.FlagDictionary.class);
        helper.succeed();
    }

    private static void assertObserved(GameTestHelper helper, String agentName, Class<?> target) throws Exception {
        Class<?> agent = Class.forName(agentName, false, ClassLoader.getSystemClassLoader());
        var instrumentation = agent.getDeclaredField("instrumentation");
        instrumentation.setAccessible(true);
        if (!((java.lang.instrument.Instrumentation) instrumentation.get(null)).isRetransformClassesSupported()) return;
        var field = agent.getDeclaredField("BASELINE_BYTECODE");
        field.setAccessible(true);
        Object store = field.get(null);
        Object bytes = store.getClass().getMethod("get", ClassLoader.class, String.class)
                .invoke(store, target.getClassLoader(), target.getName().replace('.', '/'));
        helper.assertTrue(bytes instanceof byte[], "Already-loaded class was not observed after attachment: " + target.getName());
    }
}
