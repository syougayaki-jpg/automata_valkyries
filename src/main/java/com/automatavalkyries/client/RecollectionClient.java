package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModTrials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 回想の空間の見た目（2026-10-04 ユーザー指定「過去の回想という設定」）: 空も霧もセピア色の薄明かり（太陽も月も雲も無い）、
 * 画面の縁が古い写真のように暗く褪せ、全体にうっすらセピアが掛かる。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class RecollectionClient {

    private static final float R = 0.62F, G = 0.52F, B = 0.38F;

    private RecollectionClient() {}

    static boolean inRecollection() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == ModTrials.RECOLLECTION;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inRecollection()) return;
        event.setRed(R);
        event.setGreen(G);
        event.setBlue(B);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!inRecollection()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float time = Minecraft.getInstance().level.getGameTime() + event.getPartialTick();
        int tint = (int) (34 + 6 * Mth.sin(time * 0.05F));
        g.fill(0, 0, w, h, (tint << 24) | 0x6A4A28); // うっすらセピア
        int edge = 110; // 褪せた縁
        g.fillGradient(0, 0, w, h / 6, (edge << 24) | 0x2A1C10, 0x002A1C10);
        g.fillGradient(0, h - h / 6, w, h, 0x002A1C10, (edge << 24) | 0x2A1C10);
        int side = w / 8;
        for (int x = 0; x < side; x++) {
            int a = (int) (edge * Math.pow(1 - x / (float) side, 2));
            g.fill(x, 0, x + 1, h, (a << 24) | 0x2A1C10);
            g.fill(w - 1 - x, 0, w - x, h, (a << 24) | 0x2A1C10);
        }
    }

    /** ルミナルステラの追憶メモリの説明に、回想のトライアルの使い方を足す */
    @SubscribeEvent
    public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (event.getItemStack().is(com.automatavalkyries.registry.ModBossDrops.MEMORY_STELLA.get())) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("trial.automata_valkyries.memory_hint")
                    .withStyle(s -> s.withColor(0xE8D8B0)));
        }
    }

    /** 空と霧の色（セピアの薄明かり。太陽・月・雲・星は描かない） */
    static final class Effects extends DimensionSpecialEffects {
        Effects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
            return new Vec3(R, G, B);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    }

    @Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "recollection"), new Effects());
        }
    }
}
