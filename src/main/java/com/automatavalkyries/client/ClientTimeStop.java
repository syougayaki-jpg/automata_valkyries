package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 時間停止の画面（2026-10-19）: 止まっている間は世界が白黒（少し青く）になり、上に「時は止まった」と残りの秒数。
 * 動けない者は、その場から動けず（足の位置を戻す）、叩く・使うもできない。サーバーも同じく断る（{@code mixin/TimeStopMixin}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientTimeStop {

    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "shaders/post/time_stop.json");
    private static int left;
    private static boolean exempt;
    private static Vec3 hold;
    private static boolean shaderOn;

    private ClientTimeStop() {
    }

    public static void set(int ticks, boolean canMove) {
        Minecraft mc = Minecraft.getInstance();
        left = ticks;
        exempt = canMove;
        hold = mc.player != null ? mc.player.position() : null;
        if (ticks > 0 && !shaderOn) {
            mc.gameRenderer.loadEffect(SHADER);
            shaderOn = true;
        } else if (ticks <= 0 && shaderOn) {
            mc.gameRenderer.shutdownEffect();
            shaderOn = false;
        }
    }

    /** 時が止まっているか（動ける者の画面でも true。タイムルーラーの指揮のしぐさに使う） */
    public static boolean running() {
        return left > 0;
    }

    /** 時が動き出すまでの残りの Tick（タイムルーラーのボスバーが秒を出す） */
    public static int remainingTicks() {
        return Math.max(0, left);
    }

    public static boolean locked() {
        return left > 0 && !exempt;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || left <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            set(0, true);
            return;
        }
        if (--left <= 0) {
            set(0, true);
            return;
        }
        LocalPlayer p = mc.player;
        if (locked() && hold != null) { // その場に留める
            p.setDeltaMovement(Vec3.ZERO);
            if (p.position().distanceToSqr(hold) > 1.0e-4) p.setPos(hold.x, hold.y, hold.z);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!locked()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!locked()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (left <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        Component title = Component.translatable(exempt ? "message.automata_valkyries.time_stop.exempt" : "message.automata_valkyries.time_stop.frozen")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component secs = Component.literal(String.format("%.1f", left / 20.0F)).withStyle(ChatFormatting.WHITE);
        g.drawCenteredString(mc.font, title, w / 2, 24, 0xFFFFFF);
        g.drawCenteredString(mc.font, secs, w / 2, 36, 0xFFFFFF);
    }
}
