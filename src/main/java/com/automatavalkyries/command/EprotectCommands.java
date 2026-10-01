package com.automatavalkyries.command;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackLogFile;
import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.config.EprotectConfig;
import com.mojang.brigadier.CommandDispatcher;
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
                        .then(Commands.literal("test")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> testFlagName(ctx, StringArgumentType.getString(ctx, "name")))))));
    }

    /** 辞書の分類ごとの項目数 */
    private static int showFlagDictionary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable(KEY + "flags.header").withStyle(ChatFormatting.GOLD), false);
        FlagDictionary.entries().forEach((category, entries) -> source.sendSuccess(() -> Component.translatable(KEY + "flags.category",
                category.name().toLowerCase(java.util.Locale.ROOT), entries.size(), String.join(", ", entries)), false));
        return FlagDictionary.entries().size();
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
}
