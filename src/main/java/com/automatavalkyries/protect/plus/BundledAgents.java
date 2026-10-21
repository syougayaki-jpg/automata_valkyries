package com.automatavalkyries.protect.plus;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.AgentSelfAttach;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.FlagDictionaryLink;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

/** Config registration and the CODEX-owned self-attachment extension. */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BundledAgents {
    public static final String CONFIG_FILE = "automata_valkyries-eprotect-plus.toml";
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue EPROTECT_PLUS = BUILDER
            .comment("Load the bundled Eprotect+ Java agent without -javaagent. Requires a full game/server restart.",
                    "Late attachment observes current transformer input; it cannot undo earlier interference.")
            .worldRestart().define("agents.eprotectPlus.enabled", false);
    public static final ForgeConfigSpec.BooleanValue FLAG_DICTIONARY = BUILDER
            .comment("Load the bundled flag dictionary Java agent without -javaagent. Requires a full restart.",
                    "Startup -javaagent options take precedence; false does not unload an already running agent.")
            .worldRestart().define("agents.flagDictionary.enabled", false);
    public static final ForgeConfigSpec.BooleanValue MADNESS_PASSIVE = BUILDER
            .comment("When Madness Entities is loaded, attach the bundled agents without transformers or retransformation.",
                    "This avoids interference with Madness; it cannot prevent a halt caused solely by agent presence.")
            .worldRestart().define("agents.madnessCompatibility.passive", true);
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private BundledAgents() {}

    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, CONFIG_FILE);
    }

    public static void register() {
        AgentSelfAttach.register(new AgentSelfAttach.BundledAgent("Eprotect+",
                "automata_valkyries-eprotect-plus.jar", null,
                "com.automatavalkyries.eprotectplus.EprotectPlusInstalled", false,
                EPROTECT_PLUS::get,
                () -> options(!FLAG_DICTIONARY.get()),
                () -> connected("com.automatavalkyries.eprotectplus.EprotectPlusAgent", true)));
        AgentSelfAttach.register(new AgentSelfAttach.BundledAgent("flag dictionary",
                "automata_valkyries-flag-agent.jar", null,
                "com.automatavalkyries.eprotectplus.flag.FlagDictionaryInstalled", false,
                () -> FLAG_DICTIONARY.get() && !hasInstrumentation(
                        "com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent"),
                () -> options(false),
                () -> connected("com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent", false)));
    }

    private static String options(boolean skipBundledFlagAgent) {
        boolean passive = MADNESS_PASSIVE.get() && ModList.get().isLoaded("madness");
        if (passive) {
            AutomataValkyries.LOGGER.warn("[agents] Madness Entities is loaded; attaching CODEX agents in passive compatibility mode");
        }
        if (passive && skipBundledFlagAgent) return "flagDictionary=false,passive";
        if (passive) return "passive";
        return skipBundledFlagAgent ? "flagDictionary=false" : null;
    }

    public static boolean hasInstrumentation(String agentClass) {
        try {
            Class<?> type = Class.forName(agentClass, false, ClassLoader.getSystemClassLoader());
            return Boolean.TRUE.equals(type.getMethod("hasInstrumentation").invoke(null));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    private static void connected(String agentClass, boolean generalAgent) {
        if (!hasInstrumentation(agentClass)) {
            AutomataValkyries.LOGGER.warn("[agents] {} is visible but has no Instrumentation; attachment is not active", agentClass);
            return;
        }
        if (generalAgent) EprotectPlusLink.refreshAfterAttach();
        if (hasInstrumentation("com.automatavalkyries.eprotectplus.flag.FlagDictionaryAgent")) {
            FlagDictionaryLink.refreshAfterAttach();
            com.automatavalkyries.annihilate.FlagKillPredicates.ensureAgentProtection();
        }
        AutomataValkyries.LOGGER.info("[agents] Connected {} to the MOD", agentClass);
    }
}
