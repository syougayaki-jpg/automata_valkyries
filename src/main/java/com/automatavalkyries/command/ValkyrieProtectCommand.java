package com.automatavalkyries.command;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieBackups;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * オートマタヴァルキリー protect の管理コマンド（OP 2）:
 * {@code /avprotect list}（保護中の兵姫）・{@code /avprotect release <兵姫>}（保護を外して消す。片付け用）・{@code /avprotect check}（見張りを今すぐ回す）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieProtectCommand {

    private ValkyrieProtectCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("avprotect").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    var all = ValkyrieBackups.get(server).all();
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.automata_valkyries.avprotect.list", all.size()), false);
                    for (var e : all.entrySet()) {
                        UUID id = e.getKey();
                        String where = e.getValue().dimension().location() + " " + BlockPos.containing(e.getValue().pos()).toShortString();
                        String state = ValkyrieProtect.findAnywhere(server, id) != null ? "loaded" : "not loaded";
                        ctx.getSource().sendSuccess(() -> Component.translatable("commands.automata_valkyries.avprotect.entry", id.toString(), where, state), false);
                    }
                    return all.size();
                }))
                .then(Commands.literal("release").then(Commands.argument("targets", EntityArgument.entities()).executes(ctx -> {
                    int n = 0;
                    for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                        if (e instanceof ValkyrieEntity v) {
                            v.releaseAndDiscard();
                            n++;
                        }
                    }
                    int count = n;
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.automata_valkyries.avprotect.released", count), true);
                    return n;
                })))
                .then(Commands.literal("check").executes(ctx -> {
                    int n = ValkyrieProtect.check(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.automata_valkyries.avprotect.restored", n), true);
                    return n;
                })));
    }
}
