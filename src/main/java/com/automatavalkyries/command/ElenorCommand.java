package com.automatavalkyries.command;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorArts;
import com.automatavalkyries.entity.ElenorEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * エレノアの調べの結果を見るコマンド（OP 2。2026-10-24 ユーザー指定「ダメージ調査などを精密にして」）:
 * {@code /elenor profile [相手]} — 近く（64 ブロック）のエレノアが、相手（省略すると自分）について調べたこと
 * （ティア・種類ごとの通りやすさ・足切り・上限とその間の長さ・1 発あたりの上限・回復の速さ・書き戻し・ラストスタンド）を出す。
 * {@code /elenor dismiss [半径]} — 近く（既定 128 ブロック）のエレノア（最果ても）を正規の退場で帰し、登録簿の写しも消す
 * （2026-10-25。他 MOD に UUID を書き換えられて増えた個体の片付け用）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ElenorCommand {

    private ElenorCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("elenor").requires(s -> s.hasPermission(2))
                .then(Commands.literal("profile")
                        .executes(ctx -> profile(ctx, ctx.getSource().getEntityOrException()))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> profile(ctx, EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("dismiss")
                        .executes(ctx -> dismiss(ctx, 128))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                                .executes(ctx -> dismiss(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int dismiss(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        var around = net.minecraft.world.phys.AABB.ofSize(source.getPosition(), radius * 2.0, radius * 2.0, radius * 2.0);
        List<ElenorEntity> elenors = source.getLevel().getEntitiesOfClass(ElenorEntity.class, around, e -> true);
        for (ElenorEntity elenor : elenors) elenor.retreat();
        int count = elenors.size();
        source.sendSuccess(() -> Component.translatable("commands.automata_valkyries.elenor.dismissed", count, radius), true);
        return count;
    }

    private static int profile(CommandContext<CommandSourceStack> ctx, Entity target) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable("commands.automata_valkyries.elenor.not_living"));
            return 0;
        }
        List<ElenorEntity> elenors = source.getLevel().getEntitiesOfClass(ElenorEntity.class, living.getBoundingBox().inflate(64.0D), e -> e.arts() != null);
        if (elenors.isEmpty()) {
            source.sendFailure(Component.translatable("commands.automata_valkyries.elenor.none"));
            return 0;
        }
        int shown = 0;
        for (ElenorEntity elenor : elenors) {
            ElenorArts arts = elenor.arts();
            var pressure = arts.pressure();
            source.sendSuccess(() -> Component.translatable("commands.automata_valkyries.elenor.profile",
                    elenor.getDisplayName(), living.getDisplayName(), pressure.tier(living)), false);
            List<Component> lines = pressure.describeAdapt(living.getUUID());
            if (lines.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.automata_valkyries.elenor.unprobed"), false);
            }
            for (Component line : lines) source.sendSuccess(() -> line, false);
            shown++;
        }
        return shown;
    }
}
