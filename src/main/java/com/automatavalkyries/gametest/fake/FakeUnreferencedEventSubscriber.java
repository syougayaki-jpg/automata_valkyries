package com.automatavalkyries.gametest.fake;

import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates an EventBus subscriber class (like Witherzilla's EventHandle) that is NOT referenced
 * anywhere by the boss entity class, but belongs to the same mod and holds a static entity list.
 */
public final class FakeUnreferencedEventSubscriber {
    public static final List<Object> livingEntities = new ArrayList<>();

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        // Registered on MinecraftForge.EVENT_BUS during test
    }
}
