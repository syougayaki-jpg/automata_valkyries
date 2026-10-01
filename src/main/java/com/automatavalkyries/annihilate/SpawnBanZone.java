package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 超絶抹殺の棒で消した個体と同じ種類の「作り直し」を、しばらくの間その近くで拒否する。
 * 復活は多くの場合「別の UUID の新しい個体」を作り直す形なので、同じ個体・同じ UUID の再参加拒否だけでは止まらない。
 * <p>
 * 範囲を絞る: 他 MOD の種類だけ（vanilla・このMOD の種類は対象外）、消した地点から {@link #RADIUS} ブロック以内、
 * {@link #DURATION_TICKS} Tick の間だけ。判定は EntityJoinLevelEvent（抹殺側のハンドラー）で行う。
 * </p>
 */
public final class SpawnBanZone {

    public static final int DURATION_TICKS = 20;
    public static final double RADIUS = 16.0D;

    private record Zone(ResourceKey<Level> dimension, EntityType<?> type, Vec3 center, long expiresAt) {}

    private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

    private SpawnBanZone() {}

    /** 消した個体の種類が他 MOD のものなら、その地点のまわりで作り直しをしばらく拒否する */
    public static void banNear(ServerLevel level, Entity erased, Vec3 center) {
        EntityType<?> type = erased.getType();
        if (!isForeignType(type) || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return;
        ban(level, type, center, DURATION_TICKS);
    }

    /** 種類と地点を指定して拒否する（GameTest からも使う） */
    public static void ban(ServerLevel level, EntityType<?> type, Vec3 center, int durationTicks) {
        long now = level.getGameTime();
        ZONES.removeIf(zone -> zone.expiresAt() <= now);
        ZONES.add(new Zone(level.dimension(), type, center, now + durationTicks));
    }

    /** この個体の参加を拒否すべきか（期限切れの区域は捨てる） */
    public static boolean shouldRefuse(Entity entity) {
        if (ZONES.isEmpty() || !(entity.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        for (Zone zone : ZONES) {
            if (zone.expiresAt() <= now) {
                ZONES.remove(zone);
                continue;
            }
            if (zone.type() == entity.getType() && zone.dimension() == level.dimension()
                    && entity.position().distanceToSqr(zone.center()) <= RADIUS * RADIUS) {
                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Refused re-creation of {} near an annihilation site", entity);
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        ZONES.clear();
    }

    private static boolean isForeignType(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && !"minecraft".equals(key.getNamespace()) && !AutomataValkyries.MODID.equals(key.getNamespace());
    }
}
