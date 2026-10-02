package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * クライアント側の時間停止の対策: 保護対象（{@link IEprotected}）の Tick がクライアントで進まなければ、代わりに回す。
 * <p>
 * 時間停止の MOD はクライアントのエンティティの Tick も止める（EndingLibrary の時間停止）。サーバーでは動いているのにクライアントで
 * 止まると、位置の補間・アニメーション・演出が崩れる。サーバー側の {@code Eprotect.driveTick} のクライアント版で、包み（guardEntityTick）を通さずに回す。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientTickBypass {

    private static final Map<Entity, Integer> LAST_TICK = new WeakHashMap<>();
    private static ClientLevel lastLevel;

    private ClientTickBypass() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            LAST_TICK.clear();
            return;
        }
        if (level != lastLevel) {
            LAST_TICK.clear();
            lastLevel = level;
        }
        java.util.List<Entity> protectedEntities = new java.util.ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof IEprotected) protectedEntities.add(entity);
        }
        for (Entity entity : protectedEntities) {
            if (!(entity instanceof IEprotected) || entity.isRemoved() || entity.isPassenger()) continue;
            Integer last = LAST_TICK.put(entity, entity.tickCount);
            if (last == null || entity.tickCount != last) continue;
            try {
                level.tickNonPassenger(entity);
                LAST_TICK.put(entity, entity.tickCount);
            } catch (Throwable t) {
                AutomataValkyries.LOGGER.debug("[eprotect] Client tick bypass failed for {}: {}", entity, t.toString());
            }
        }
    }
}
