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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 超絶抹殺の棒・終焉・隔絶ダメージで消した個体と同じ種類の「作り直し（別 UUID クローン再召喚）」を、
 * 死亡フック（{@code die()} 等）の同期実行中および消去直後の一定時間その近くで拒否する。
 * <p>
 * DOOM The Titans の {@code WitherZillaEntity}（{@code die()} 内で {@code WitherZillaEntityDiesProcedure} を呼び、
 * 同じ座標に新しい UUID の別個体を即時 {@code spawn} する）のような自己再召喚型ボスを準汎用で封殺する。
 * </p>
 */
public final class SpawnBanZone {

    public static final int DURATION_TICKS = 20;
    public static final double RADIUS = 16.0D;

    private record Zone(
            ResourceKey<Level> dimension,
            EntityType<?> type,
            @Nullable Class<?> entityClass,
            Vec3 feetCenter,
            Vec3 bodyCenter,
            double radiusSqr,
            long expiresAt
    ) {}

    private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

    /**
     * {@code die()} / {@code kill()} / 抹殺パイプラインを同期実行中のターゲットスタック。
     * 実行中に同一スレッドから同じ種類の別インスタンスが {@code EntityJoinLevelEvent} でスポーンしようとした場合は座標を問わず即座に拒否する。
     */
    private static final ThreadLocal<Deque<Entity>> ACTIVE_DYING_ENTITIES = ThreadLocal.withInitial(ArrayDeque::new);

    private SpawnBanZone() {}

    private static java.lang.reflect.Field entityTypeFactoryField;

    /**
     * 対象の死亡・消去処理（{@code die()} 等）を実行する間、同一種類・同一クラスの別個体スポーンを同期ブロックし、
     * 併せて外部 {@link EntityType} の {@code factory} を一時的に {@code (t, l) -> null} に差し替えてクローン実体化そのものを根絶し、
     * 周辺座標の {@link #banNear} も事前登録する。
     */
    public static void runWithCloneSpawnBlock(@Nullable Entity target, Runnable action) {
        if (target == null) {
            action.run();
            return;
        }
        if (target.level() instanceof ServerLevel serverLevel) {
            banNear(serverLevel, target, target.position());
        }
        EntityType<?> type = target.getType();
        Object savedFactory = null;
        boolean factoryReplaced = false;
        if (type != null && isForeignType(type)) {
            try {
                if (entityTypeFactoryField == null) {
                    entityTypeFactoryField = com.automatavalkyries.util.UnsafeHelper.findField(EntityType.class, "factory", "f_20535_");
                }
                if (entityTypeFactoryField != null) {
                    savedFactory = entityTypeFactoryField.get(type);
                    EntityType.EntityFactory<?> nullFactory = (t, l) -> null;
                    com.automatavalkyries.util.UnsafeHelper.setFieldValue(type, entityTypeFactoryField.getName(), nullFactory);
                    factoryReplaced = true;
                }
            } catch (Throwable ignored) {}
        }
        Deque<Entity> stack = ACTIVE_DYING_ENTITIES.get();
        stack.push(target);
        try {
            action.run();
        } finally {
            stack.remove(target);
            if (factoryReplaced && savedFactory != null && entityTypeFactoryField != null) {
                try {
                    com.automatavalkyries.util.UnsafeHelper.setFieldValue(type, entityTypeFactoryField.getName(), savedFactory);
                } catch (Throwable ignored) {}
            }
        }
    }

    /** 消した個体の種類が他 MOD のものなら、その地点のまわりで作り直しをしばらく拒否する */
    public static void banNear(ServerLevel level, Entity erased, Vec3 center) {
        if (level == null || erased == null) return;
        EntityType<?> type = erased.getType();
        Class<?> entityClass = erased.getClass();
        if (!isForeignEntityOrType(type, entityClass)) return;
        Vec3 feet = erased.position();
        Vec3 validFeet = (feet != null && Double.isFinite(feet.x) && Double.isFinite(feet.y) && Double.isFinite(feet.z))
                ? feet : center;
        Vec3 validCenter = (center != null && Double.isFinite(center.x) && Double.isFinite(center.y) && Double.isFinite(center.z))
                ? center : validFeet;
        if (validFeet == null || !Double.isFinite(validFeet.x) || !Double.isFinite(validFeet.y) || !Double.isFinite(validFeet.z)) {
            return;
        }
        double dynamicRadius = Math.max(RADIUS, Math.min(128.0D, erased.getBbWidth() + erased.getBbHeight() + 16.0D));
        long now = level.getGameTime();
        ZONES.removeIf(zone -> zone.expiresAt() <= now);
        ZONES.add(new Zone(level.dimension(), type, entityClass, validFeet, validCenter, dynamicRadius * dynamicRadius, now + DURATION_TICKS));
    }

    /** 種類と地点を指定して拒否する（GameTest からも使う） */
    public static void ban(ServerLevel level, EntityType<?> type, Vec3 center, int durationTicks) {
        long now = level.getGameTime();
        ZONES.removeIf(zone -> zone.expiresAt() <= now);
        ZONES.add(new Zone(level.dimension(), type, null, center, center, RADIUS * RADIUS, now + durationTicks));
    }

    /** この個体の参加を拒否すべきか（同期死亡処理中の自己再召喚、または期限内の抹殺地点周辺スポーン） */
    public static boolean shouldRefuse(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return false;

        // 1. die() / 抹殺の同期実行中に、その対象自身によって新しく召喚された同一種類・同一クラスのクローンを即時拒否
        Deque<Entity> activeStack = ACTIVE_DYING_ENTITIES.get();
        if (!activeStack.isEmpty()) {
            for (Entity dying : activeStack) {
                if (dying != null && dying != entity
                        && !dying.getUUID().equals(entity.getUUID())
                        && isForeignEntityOrType(dying.getType(), dying.getClass())
                        && (dying.getClass() == entity.getClass()
                        || (isForeignType(dying.getType()) && dying.getType() == entity.getType()))) {
                    AutomataValkyries.LOGGER.info("[AnnihilationAgent] Blocked synchronous death-hook clone spawn of {} (from dying {})",
                            entity, dying.getUUID());
                    return true;
                }
            }
        }

        // 2. 消去直後の空間スポーン禁止区域による拒否（巨人ボスの足元座標・中心座標の双方で判定）
        if (ZONES.isEmpty()) return false;
        long now = level.getGameTime();
        Vec3 pos = entity.position();
        for (Zone zone : ZONES) {
            if (zone.expiresAt() <= now) {
                ZONES.remove(zone);
                continue;
            }
            if (zone.dimension() != level.dimension()) continue;
            boolean sameKind = (zone.entityClass() == null || isForeignType(zone.type()))
                    ? (zone.type() == entity.getType() || (zone.entityClass() != null && zone.entityClass() == entity.getClass()))
                    : (zone.entityClass() == entity.getClass());
            if (!sameKind) continue;
            double distFeetSqr = pos.distanceToSqr(zone.feetCenter());
            double distBodySqr = pos.distanceToSqr(zone.bodyCenter());
            if (distFeetSqr <= zone.radiusSqr() || distBodySqr <= zone.radiusSqr()) {
                AutomataValkyries.LOGGER.info("[AnnihilationAgent] Refused re-creation of {} near an annihilation site", entity);
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        ZONES.clear();
        ACTIVE_DYING_ENTITIES.get().clear();
    }

    private static boolean isForeignEntityOrType(EntityType<?> type, @Nullable Class<?> entityClass) {
        if (isForeignType(type)) return true;
        if (entityClass == null) return false;
        String className = entityClass.getName();
        if (className.startsWith("com.automatavalkyries.gametest.")) {
            return true;
        }
        return !className.startsWith("net.minecraft.") && !className.startsWith("com.automatavalkyries.");
    }

    private static boolean isForeignType(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null && !"minecraft".equals(key.getNamespace()) && !AutomataValkyries.MODID.equals(key.getNamespace());
    }
}
