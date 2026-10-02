package com.automatavalkyries.command;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackLogFile;
import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.config.EprotectConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 攻撃の記録とブラックリストの管理（権限 2）。
 * <ul>
 *   <li>/eprotect attacks [clear]</li>
 *   <li>/eprotect blacklist list | clear | add &lt;modid&gt; | remove &lt;modid&gt;</li>
 *   <li>/eprotect blacklist auto [on | off | threshold &lt;回数&gt; | window &lt;Tick&gt;]（設定ファイルへ保存）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class EprotectCommands {

    private static final int SHOWN_ENTRIES = 20;
    private static final String KEY = "commands." + AutomataValkyries.MODID + ".";

    private EprotectCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("eprotect")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("plus")
                        .executes(EprotectCommands::showPlus)
                        .then(Commands.literal("clear").executes(EprotectCommands::clearPlus)))
                .then(Commands.literal("observer")
                        .executes(EprotectCommands::showObserver)
                        .then(Commands.literal("capabilities").executes(EprotectCommands::showCapabilities)))
                .then(Commands.literal("attacks")
                        .executes(EprotectCommands::showAttacks)
                        .then(Commands.literal("clear").executes(EprotectCommands::clearAttacks)))
                .then(Commands.literal("blacklist")
                        .then(Commands.literal("list").executes(EprotectCommands::listBlacklist))
                        .then(Commands.literal("clear").executes(EprotectCommands::clearBlacklist))
                        .then(Commands.literal("auto")
                                .executes(EprotectCommands::showAuto)
                                .then(Commands.literal("on").executes(ctx -> setAuto(ctx, EprotectConfig.BLACKLIST_AUTO_ENABLED, true)))
                                .then(Commands.literal("off").executes(ctx -> setAuto(ctx, EprotectConfig.BLACKLIST_AUTO_ENABLED, false)))
                                .then(Commands.literal("threshold")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                                .executes(ctx -> setAuto(ctx, EprotectConfig.BLACKLIST_AUTO_THRESHOLD, IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(Commands.literal("window")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 72000))
                                                .executes(ctx -> setAuto(ctx, EprotectConfig.BLACKLIST_AUTO_WINDOW_TICKS, IntegerArgumentType.getInteger(ctx, "ticks"))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(addCandidates(), builder))
                                        .executes(ctx -> addToBlacklist(ctx, StringArgumentType.getString(ctx, "modid")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("modid", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ModBlacklist.list(), builder))
                                        .executes(ctx -> removeFromBlacklist(ctx, StringArgumentType.getString(ctx, "modid"))))))
                .then(Commands.literal("flags")
                        .executes(EprotectCommands::showFlagDictionary)
                        .then(Commands.literal("status").executes(EprotectCommands::showFlagAgentStatus))
                        .then(Commands.literal("sync").executes(EprotectCommands::syncFlagDictionary))
                        .then(Commands.literal("repair").executes(EprotectCommands::syncFlagDictionary))
                        .then(Commands.literal("test")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> testFlagName(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("watch")
                                .executes(EprotectCommands::showFlagWatch)
                                .then(Commands.literal("clear").executes(EprotectCommands::clearFlagWatch))))
                .then(Commands.literal("scope")
                        .executes(EprotectCommands::showScope)
                        .then(Commands.literal("on").executes(ctx -> setScope(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setScope(ctx, false)))
                        .then(Commands.literal("vehicles")
                                .then(Commands.literal("on").executes(ctx -> setScopeVehicles(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setScopeVehicles(ctx, false))))
                        .then(Commands.literal("aura")
                                .then(Commands.literal("on").executes(ctx -> setScopeAura(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setScopeAura(ctx, false))))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(1.0, 128.0))
                                        .executes(ctx -> setScopeRadius(ctx, DoubleArgumentType.getDouble(ctx, "distance")))))));
    }

    /** 辞書の分類ごとの項目数 */
    private static int showFlagDictionary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(KEY + "flags.header").withStyle(ChatFormatting.GOLD), false);
        FlagDictionary.entries().forEach((category, entries) -> source.sendSuccess(() -> Component.translatable(KEY + "flags.category",
                category.name().toLowerCase(java.util.Locale.ROOT), entries.size(), String.join(", ", entries)), false));
        return FlagDictionary.entries().size();
    }

    /** フラグ辞書防護 Java エージェントのステータスと統計の表示 */
    private static int showFlagAgentStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean active = com.automatavalkyries.protect.flag.FlagDictionaryLink.isAgentActive();
        var stats = com.automatavalkyries.protect.flag.FlagDictionaryLink.getAgentStats();
        source.sendSuccess(() -> Component.translatable(KEY + "flags.agent_status",
                active ? Component.translatable(KEY + "flags.yes").withStyle(ChatFormatting.GREEN)
                       : Component.translatable(KEY + "flags.no").withStyle(ChatFormatting.RED),
                String.valueOf(stats)).withStyle(ChatFormatting.GOLD), false);
        return active ? 1 : 0;
    }

    /** フラグ辞書と JVM エージェント不変マスターデータの整合性検証・修復 */
    private static int syncFlagDictionary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean repaired = com.automatavalkyries.protect.flag.FlagDictionary.ensureAgentProtection();
        source.sendSuccess(() -> Component.translatable(KEY + "flags.sync_result",
                repaired ? Component.translatable(KEY + "flags.repaired").withStyle(ChatFormatting.GREEN)
                         : Component.translatable(KEY + "flags.healthy").withStyle(ChatFormatting.AQUA)), true);
        return repaired ? 1 : 0;
    }

    /** 名前を辞書で引いた結果（保護対象なら戻すか・抹殺なら外すか） */
    private static int testFlagName(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        Set<FlagDictionary.Category> categories = FlagDictionary.classify(name);
        String words = String.join(" ", FlagDictionary.words(name));
        String found = categories.isEmpty() ? "-" : categories.stream().map(c -> c.name().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable(KEY + "flags.test", name, words, found), false);
        source.sendSuccess(() -> Component.translatable(KEY + "flags.verdict",
                yesNo(FlagDictionary.harmfulOnProtected(name)), yesNo(FlagDictionary.protectiveOnTarget(name)),
                yesNo(FlagDictionary.livesCounter(name))), false);
        return categories.size();
    }

    /** Eprotect+ Java エージェントのステータスと記録の表示 */
    private static int showPlus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var stats = com.automatavalkyries.protect.plus.EprotectPlusLink.getStats();
        source.sendSuccess(() -> Component.translatable(KEY + "plus.header", String.valueOf(stats)).withStyle(ChatFormatting.GOLD), false);

        var events = com.automatavalkyries.protect.plus.EprotectPlusLink.getEvents();
        if (events.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "plus.empty").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        int shown = 0;
        for (int i = events.size() - 1; i >= 0 && shown < 10; i--) {
            var e = events.get(i);
            shown++;
            source.sendSuccess(() -> Component.translatable(KEY + "plus.entry",
                    e.getOrDefault("time", ""),
                    e.getOrDefault("kind", ""),
                    e.getOrDefault("target", ""),
                    e.getOrDefault("caller", ""),
                    e.getOrDefault("action", "")), false);
            String note = e.getOrDefault("note", "");
            if (!note.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  " + note).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return events.size();
    }

    private static int clearPlus(CommandContext<CommandSourceStack> ctx) {
        com.automatavalkyries.protect.plus.EprotectPlusLink.clearEvents();
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "plus.cleared"), true);
        return 1;
    }

    /** 能力の地図: 他 MOD のクラスが何をできる作りか（MOD・能力ごと。実際にしたかは別） */
    private static int showCapabilities(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!com.automatavalkyries.protect.ObserverLink.active()) {
            source.sendSuccess(() -> Component.translatable(KEY + "observer.inactive").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        var rows = com.automatavalkyries.protect.ObserverLink.capabilities();
        source.sendSuccess(() -> Component.translatable(KEY + "observer.capabilities.header", rows.size()).withStyle(ChatFormatting.GOLD), false);
        java.util.Set<String> hitsShown = new java.util.HashSet<>();
        for (var row : rows) {
            source.sendSuccess(() -> Component.translatable(KEY + "observer.capabilities.entry", row.mod(), row.capability(),
                    row.classes(), row.samples()), false);
            // その MOD の名前がフラグ辞書のどの分類に当たるか（MOD ごとに 1 度）
            if (!hitsShown.add(row.mod())) continue;
            row.dictionaryHits().forEach((category, names) -> source.sendSuccess(() -> Component.translatable(
                    KEY + "observer.capabilities.hits", category.name(),
                    String.join(", ", names.subList(0, Math.min(8, names.size())))).withStyle(ChatFormatting.AQUA), false));
        }
        return rows.size();
    }

    /** 観察用 Java エージェントの記録（新しい順に 10 件。クラスの書き換えと、新しく見つけた変換器） */
    private static int showObserver(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!com.automatavalkyries.protect.ObserverLink.active()) {
            source.sendSuccess(() -> Component.translatable(KEY + "observer.inactive").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        var events = com.automatavalkyries.protect.ObserverLink.events();
        source.sendSuccess(() -> Component.translatable(KEY + "observer.header",
                String.valueOf(com.automatavalkyries.protect.ObserverLink.status())).withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (int i = events.size() - 1; i >= 0 && shown < 10; i--) {
            var e = events.get(i);
            shown++;
            source.sendSuccess(() -> Component.translatable(KEY + "observer.entry", e.get("time"), e.get("kind"), e.get("class"), e.classMod(),
                    e.callerMod(), e.get("caller").isEmpty() ? "-" : e.get("caller")), false);
            if (!e.get("neutered").isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "observer.neutered", e.get("neutered")).withStyle(ChatFormatting.RED), false);
            }
            if (!e.get("changed").isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "observer.changed", e.get("changed")).withStyle(ChatFormatting.GRAY), false);
            }
            var known = e.knownAddedNames();
            if (!known.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "observer.known", String.join(", ", known)).withStyle(ChatFormatting.YELLOW), false);
            }
            var unknown = e.unknownAddedNames();
            if (!unknown.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "observer.unknown", String.join(", ", unknown)).withStyle(ChatFormatting.AQUA), false);
            }
            if (!e.get("note").isEmpty()) {
                source.sendSuccess(() -> Component.literal("  " + e.get("note")).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return events.size();
    }

    /** 辞書補助の監視が挙げた候補（点数の高い順に 10 件） */
    private static int showFlagWatch(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        com.automatavalkyries.protect.flag.FlagWatchAgent agent = com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance();
        agent.drain();
        var candidates = agent.candidates();
        source.sendSuccess(() -> Component.translatable(KEY + "flags.watch.header", agent.isRunning() ? "on" : "off",
                agent.sampleCount(), agent.eventCount(), candidates.size()).withStyle(ChatFormatting.GOLD), false);
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "flags.watch.none").withStyle(ChatFormatting.GRAY), false);
        }
        for (var c : candidates.subList(0, Math.min(10, candidates.size()))) {
            source.sendSuccess(() -> Component.translatable(KEY + "flags.watch.entry", String.format("%.2f", c.score()), c.key(),
                    c.eventChanges(), c.eventsSeen(), c.normalChanges(), c.samples(), c.kinds(), c.example()), false);
            source.sendSuccess(() -> Component.translatable(KEY + "flags.watch.suggestion", c.suggestion()).withStyle(ChatFormatting.AQUA), false);
        }
        source.sendSuccess(() -> Component.translatable(KEY + "flags.watch.file",
                com.automatavalkyries.protect.flag.FlagWatchAgent.path().toString()).withStyle(ChatFormatting.GRAY), false);
        return candidates.size();
    }

    private static int clearFlagWatch(CommandContext<CommandSourceStack> ctx) {
        com.automatavalkyries.protect.flag.FlagWatchAgent.getInstance().clear();
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "flags.watch.cleared"), true);
        return 1;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(KEY + "flags." + (value ? "yes" : "no")).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private static int showAttacks(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<AttackLog.Entry> entries = AttackLog.entries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "attacks.empty").withStyle(ChatFormatting.GRAY), false);
        } else {
            int shown = Math.min(SHOWN_ENTRIES, entries.size());
            source.sendSuccess(() -> Component.translatable(KEY + "attacks.header", entries.size(), shown).withStyle(ChatFormatting.GOLD), false);
            long now = source.getLevel().getGameTime();
            for (AttackLog.Entry entry : entries.subList(0, shown)) {
                ChatFormatting color = switch (entry.outcome) {
                    case BLOCKED_BLACKLIST -> ChatFormatting.LIGHT_PURPLE;
                    case REPAIRED -> ChatFormatting.GRAY;
                    case DETECTED -> ChatFormatting.YELLOW;
                    default -> ChatFormatting.RED;
                };
                String mark = ModBlacklist.contains(entry.mod) ? " [BL]" : "";
                source.sendSuccess(() -> Component.translatable(KEY + "attacks.entry", entry.mod + mark, entry.op, entry.outcome.name(),
                        entry.count(), now - entry.lastTime(), entry.target()).withStyle(color), false);
                if (entry.via() != null || !"?".equals(entry.frame())) {
                    source.sendSuccess(() -> Component.translatable(KEY + "attacks.entry_via",
                            entry.via() != null ? entry.via() : "-", entry.frame()).withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        }
        source.sendSuccess(() -> Component.translatable(KEY + "attacks.file", AttackLogFile.path().toString()).withStyle(ChatFormatting.DARK_GRAY), false);
        return entries.size();
    }

    private static int clearAttacks(CommandContext<CommandSourceStack> ctx) {
        AttackLog.clear();
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "attacks.cleared"), true);
        return 1;
    }

    private static int listBlacklist(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<String> list = ModBlacklist.list();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "blacklist.empty").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "blacklist.header", list.size()).withStyle(ChatFormatting.GOLD), false);
        for (String mod : list.stream().sorted().toList()) {
            ModBlacklist.Source origin = ModBlacklist.sourceOf(mod);
            String state = origin == ModBlacklist.Source.AUTO ? "auto" : "manual";
            Component detail = Component.translatable(KEY + "blacklist.source." + state);
            if (!ModList.get().isLoaded(mod)) {
                detail = detail.copy().append(", ").append(Component.translatable(KEY + "blacklist.source.not_loaded"));
            }
            Component line = Component.translatable(KEY + "blacklist.entry", mod, detail);
            source.sendSuccess(() -> line, false);
        }
        return list.size();
    }

    private static int addToBlacklist(CommandContext<CommandSourceStack> ctx, String mod) {
        CommandSourceStack source = ctx.getSource();
        ModBlacklist.Result result = ModBlacklist.add(mod, ModBlacklist.Source.MANUAL, true);
        switch (result) {
            case ADDED -> {
                source.sendSuccess(() -> Component.translatable(KEY + "blacklist.added", mod), true);
                if (!ModList.get().isLoaded(mod)) {
                    source.sendSuccess(() -> Component.translatable(KEY + "blacklist.not_loaded", mod).withStyle(ChatFormatting.YELLOW), false);
                }
                return 1;
            }
            case ALREADY -> source.sendFailure(Component.translatable(KEY + "blacklist.already", mod));
            case PROTECTED -> source.sendFailure(Component.translatable(KEY + "blacklist.protected", mod));
            default -> source.sendFailure(Component.translatable(KEY + "blacklist.invalid", mod));
        }
        return 0;
    }

    private static int removeFromBlacklist(CommandContext<CommandSourceStack> ctx, String mod) {
        CommandSourceStack source = ctx.getSource();
        if (ModBlacklist.remove(mod, true) == ModBlacklist.Result.REMOVED) {
            source.sendSuccess(() -> Component.translatable(KEY + "blacklist.removed", mod), true);
            return 1;
        }
        source.sendFailure(Component.translatable(KEY + "blacklist.not_listed", mod));
        return 0;
    }

    private static int clearBlacklist(CommandContext<CommandSourceStack> ctx) {
        int count = ModBlacklist.clear(true);
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "blacklist.cleared", count), true);
        return count;
    }

    private static int showAuto(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_ENABLED);
        Component state = Component.translatable(KEY + "blacklist.auto." + (enabled ? "on" : "off"))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "blacklist.auto.status", state,
                EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_THRESHOLD),
                EprotectConfig.get(EprotectConfig.BLACKLIST_AUTO_WINDOW_TICKS)), false);
        return enabled ? 1 : 0;
    }

    /** 自動登録の設定を変えて設定ファイルへ保存する（回数は数え直す） */
    private static <T> int setAuto(CommandContext<CommandSourceStack> ctx, ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        try {
            value.set(newValue);
            value.save();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "blacklist.auto.not_loaded"));
            return 0;
        }
        ModBlacklist.resetAutoCounters();
        showAuto(ctx);
        return 1;
    }

    /** 追加の候補: 攻撃の記録に出てきた MOD を先に、次に読み込まれている MOD（保護された ID と登録済みは除く） */
    private static Set<String> addCandidates() {
        Set<String> candidates = new LinkedHashSet<>(AttackLog.recordedMods());
        for (IModInfo info : ModList.get().getMods()) candidates.add(info.getModId());
        candidates.removeIf(id -> ModBlacklist.isProtected(id) || ModBlacklist.contains(id));
        return candidates;
    }

    private static int showScope(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        boolean expanded = EprotectConfig.isExpandedScopeEnabled();
        boolean vehicles = EprotectConfig.isProtectVehiclesAndPassengersEnabled();
        boolean aura = EprotectConfig.isReinforcedAuraEnabled();
        double radius = EprotectConfig.get(EprotectConfig.REINFORCED_AURA_RADIUS);
        source.sendSuccess(() -> Component.translatable(KEY + "scope.status",
                yesNo(expanded), yesNo(vehicles), yesNo(aura), String.format("%.1f", radius)).withStyle(ChatFormatting.GOLD), false);
        return expanded ? 1 : 0;
    }

    private static int setScope(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            EprotectConfig.EXPANDED_SCOPE_ENABLED.set(enabled);
            EprotectConfig.EXPANDED_SCOPE_ENABLED.save();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "scope.not_loaded"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "scope.set", yesNo(enabled)), true);
        showScope(ctx);
        return 1;
    }

    private static int setScopeVehicles(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            EprotectConfig.PROTECT_VEHICLES_AND_PASSENGERS.set(enabled);
            EprotectConfig.PROTECT_VEHICLES_AND_PASSENGERS.save();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "scope.not_loaded"));
            return 0;
        }
        showScope(ctx);
        return 1;
    }

    private static int setScopeAura(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            EprotectConfig.REINFORCED_AURA_ENABLED.set(enabled);
            EprotectConfig.REINFORCED_AURA_ENABLED.save();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "scope.not_loaded"));
            return 0;
        }
        showScope(ctx);
        return 1;
    }

    private static int setScopeRadius(CommandContext<CommandSourceStack> ctx, double radius) {
        try {
            EprotectConfig.REINFORCED_AURA_RADIUS.set(radius);
            EprotectConfig.REINFORCED_AURA_RADIUS.save();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "scope.not_loaded"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "scope.radius_set", String.format("%.1f", radius)), true);
        showScope(ctx);
        return 1;
    }
}
