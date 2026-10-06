package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.TacPointerItem;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.network.TacticalCyclePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 「V」キー: 手に持った戦術ポインターのコードを切り替える（追加拡張仕様書 §13.1） */
public final class TacticalKeys {

    private TacticalKeys() {}

    public static final KeyMapping CYCLE = new KeyMapping("key.automata_valkyries.tactical_cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.automata_valkyries");

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(CYCLE);
        }
    }

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            var player = Minecraft.getInstance().player;
            while (CYCLE.consumeClick()) {
                if (player != null && (player.getMainHandItem().getItem() instanceof TacPointerItem || player.getOffhandItem().getItem() instanceof TacPointerItem)) {
                    ModNetwork.CHANNEL.sendToServer(new TacticalCyclePacket());
                }
            }
        }
    }
}
