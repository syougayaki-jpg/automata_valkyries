package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 戦術兵姫が射撃で構えた（腕を前へ上げた）ときの、持つ物の傾きの戻し（2026-10-06、実機で合わせる用）。
 * {@code /avtilt <銃の角度> [弓の角度]}（度）。腕を上げた分（約 83°）を戻すほど銃口・弓が下を向く。決まったら値を既定値へ書き写す
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ValkyrieAimTuner {

    /** 銃・砲（射撃の兵装）と弓の戻し（ラジアン）。実機で合わせて 0°（2026-10-06 ユーザー確認「/avtilt 0 0 でいい」） */
    public static float gunTilt = 0.0F, bowTilt = 0.0F;

    private ValkyrieAimTuner() {}

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("avtilt")
                .executes(c -> {
                    c.getSource().sendSystemMessage(Component.literal(String.format("avtilt: gun %.0f° / bow %.0f°", gunTilt * net.minecraft.util.Mth.RAD_TO_DEG, bowTilt * net.minecraft.util.Mth.RAD_TO_DEG)));
                    return 1;
                })
                .then(Commands.argument("gun", FloatArgumentType.floatArg(-180, 180))
                        .executes(c -> set(c.getSource(), FloatArgumentType.getFloat(c, "gun"), bowTilt * net.minecraft.util.Mth.RAD_TO_DEG))
                        .then(Commands.argument("bow", FloatArgumentType.floatArg(-180, 180))
                                .executes(c -> set(c.getSource(), FloatArgumentType.getFloat(c, "gun"), FloatArgumentType.getFloat(c, "bow"))))));
    }

    private static int set(net.minecraft.commands.CommandSourceStack source, float gun, float bow) {
        gunTilt = gun * net.minecraft.util.Mth.DEG_TO_RAD;
        bowTilt = bow * net.minecraft.util.Mth.DEG_TO_RAD;
        source.sendSystemMessage(Component.literal(String.format("avtilt: gun %.0f° / bow %.0f°", gun, bow)));
        return 1;
    }
}
