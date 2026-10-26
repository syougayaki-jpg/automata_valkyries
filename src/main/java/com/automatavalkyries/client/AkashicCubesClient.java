package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import com.automatavalkyries.item.AkashicCubesItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 観測の立方体（{@link AkashicCubesItem}）を持っているプレイヤーのまわりを回る 3 つの立方体（2026-10-26） */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class AkashicCubesClient {

    private AkashicCubesClient() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!AkashicCubesItem.carrying(player)) return;
        float time = player.tickCount + event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        var vc = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(AkashicProbeRenderer.CRYSTAL));
        for (int i = 0; i < 3; i++) {
            Vec3 at = AkashicCubesItem.orbit(player, time, i).subtract(player.position());   // 足元からの位置（サーバーの撃つ所と同じ式）
            AkashicSeerRenderer.cube(pose, vc, (float) at.x, (float) at.y, (float) at.z, 0.09F, i == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.PINK, time, i);
        }
    }
}
