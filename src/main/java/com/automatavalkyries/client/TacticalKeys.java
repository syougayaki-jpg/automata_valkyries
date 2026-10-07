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

/**
 * 「V」キー: 手に持った戦術ポインターのコードを切り替える（追加拡張仕様書 §13.1）。
 * 「H」キー: 兵姫の HUD の表示・非表示、「J」キー: HUD の配置の画面（移動・大きさ。2026-10-07 ユーザー指定）。どれも操作設定で変えられる
 */
public final class TacticalKeys {

    private TacticalKeys() {}

    public static final KeyMapping CYCLE = new KeyMapping("key.automata_valkyries.tactical_cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.automata_valkyries");
    public static final KeyMapping HUD_TOGGLE = new KeyMapping("key.automata_valkyries.hud_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.automata_valkyries");
    public static final KeyMapping HUD_EDIT = new KeyMapping("key.automata_valkyries.hud_edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.automata_valkyries");

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(CYCLE);
            event.register(HUD_TOGGLE);
            event.register(HUD_EDIT);
        }
    }

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            var player = mc.player;
            while (HUD_TOGGLE.consumeClick()) {
                boolean on = !com.automatavalkyries.config.ValkyrieClientConfig.hudEnabled();
                com.automatavalkyries.config.ValkyrieClientConfig.saveHud(on, com.automatavalkyries.config.ValkyrieClientConfig.hudAnchor(),
                        com.automatavalkyries.config.ValkyrieClientConfig.hudX(), com.automatavalkyries.config.ValkyrieClientConfig.hudY(),
                        com.automatavalkyries.config.ValkyrieClientConfig.hudScale());
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(on ? "message.automata_valkyries.hud.shown" : "message.automata_valkyries.hud.hidden"), true);
            }
            while (HUD_EDIT.consumeClick()) {
                if (mc.screen == null && player != null) mc.setScreen(new ValkyrieHudEditScreen());
            }
            while (CYCLE.consumeClick()) {
                if (player != null && (player.getMainHandItem().getItem() instanceof TacPointerItem || player.getOffhandItem().getItem() instanceof TacPointerItem)) {
                    ModNetwork.CHANNEL.sendToServer(new TacticalCyclePacket());
                }
            }
        }
    }
}
