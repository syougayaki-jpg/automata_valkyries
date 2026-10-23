package com.automatavalkyries.world;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持ち主のいなくなったボスバーの後片付け（2026-10-23。フラッシュファーのボスバーが消えなかった）。
 * <p>ボスバー（{@link ServerBossEvent}）は、ふつう持ち主のエンティティが見えなくなったときに見ている人から外される。
 * ところが、同じ UUID で作り直されてワールドに入れなかった複製や、強制的に消去されたエンティティでは、その片付けが走らず、
 * ボスバーが画面に残り続ける。プレイヤーが足されたボスバーを覚えておき（{@code ServerBossEventMixin}）、2 秒ごとに:
 * <ul>
 *   <li>まだ持ち主が分からないものは、読み込まれているエンティティのフィールドから持ち主を探す</li>
 *   <li>持ち主が消えた・ワールドにいない・死んで 5 秒以上たったなら、見ている人を外して隠す</li>
 * </ul>
 * 持ち主がエンティティでないボスバー（襲撃・エンダードラゴン戦・コマンド）は、持ち主が見つからないので触らない。
 * このMOD の守られたボス（エレノアなど。{@code IEprotected}）のボスバーにも触らない（自分で片付ける）。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class BossBarJanitor {

    private static final Set<ServerBossEvent> TRACKED = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static final Map<ServerBossEvent, WeakReference<Entity>> OWNERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Entity, Long> DEAD_SINCE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, List<Field>> BAR_FIELDS = new ConcurrentHashMap<>();
    private static final long DEAD_GRACE_TICKS = 100L;

    private BossBarJanitor() {}

    public static void track(ServerBossEvent event) {
        TRACKED.add(event);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 40 != 0) return;
        List<ServerBossEvent> bars;
        synchronized (TRACKED) {
            if (TRACKED.isEmpty()) return;
            bars = new ArrayList<>(TRACKED);
        }
        boolean unowned = false;
        for (ServerBossEvent bar : bars) {
            if (!bar.getPlayers().isEmpty() && OWNERS.get(bar) == null) unowned = true;
        }
        if (unowned) findOwners(server, bars);
        long now = server.getTickCount();
        for (ServerBossEvent bar : bars) {
            if (bar.getPlayers().isEmpty()) continue;
            WeakReference<Entity> ref = OWNERS.get(bar);
            if (ref == null) continue;
            Entity owner = ref.get();
            // このMOD の守られたボス（エレノアなど）は自分でボスバーを片付ける。保護が一瞬ワールドから外して戻すこともあるので触らない
            if (owner instanceof com.automatavalkyries.protect.IEprotected) continue;
            if (owner == null || isGone(owner, now)) {
                bar.removeAllPlayers();
                bar.setVisible(false);
                OWNERS.remove(bar);
                TRACKED.remove(bar);
                AutomataValkyries.LOGGER.info("[bossbar] Hid the boss bar '{}' left behind by {}", bar.getName().getString(),
                        owner == null ? "a collected entity" : owner);
            }
        }
    }

    private static boolean isGone(Entity owner, long now) {
        if (owner.isRemoved()) return true;
        if (!(owner.level() instanceof ServerLevel level) || level.getEntity(owner.getId()) != owner) return true; // ワールドに入っていない（入れなかった複製など）
        if (owner instanceof LivingEntity living && !(living.getHealth() > 0.0F)) {
            long since = DEAD_SINCE.computeIfAbsent(owner, k -> now);
            return now - since >= DEAD_GRACE_TICKS;
        }
        DEAD_SINCE.remove(owner);
        return false;
    }

    /** 読み込まれているエンティティのボスバーのフィールドから、ボスバーの持ち主を探す */
    private static void findOwners(MinecraftServer server, List<ServerBossEvent> bars) {
        Set<ServerBossEvent> wanted = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ServerBossEvent bar : bars) if (OWNERS.get(bar) == null) wanted.add(bar);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (wanted.isEmpty()) return;
                for (Field field : barFields(entity.getClass())) {
                    try {
                        Object value = field.get(entity);
                        if (value instanceof ServerBossEvent bar && wanted.remove(bar)) OWNERS.put(bar, new WeakReference<>(entity));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private static List<Field> barFields(Class<?> type) {
        return BAR_FIELDS.computeIfAbsent(type, t -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !net.minecraft.world.BossEvent.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return fields;
        });
    }
}
