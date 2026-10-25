package com.automatavalkyries.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** {@code BossHealthOverlay} の表示中のボスバーの一覧への getter（エレノアのボスバーを一番上に描き、他のボスバーをその下へずらす用。{@code client/ElenorBossBar}） */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> automata_valkyries$getEvents();
}
