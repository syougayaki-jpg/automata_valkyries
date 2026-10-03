package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** 兵装で共通の道具: 線上の当たり（壁で止まる・読み込まれていない所で止まる）、近接のダメージの種類、電撃の稲妻の粒子、遅れて起きる処理 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class WeaponUtil {

    private WeaponUtil() {}

    /** 線上の当たりの結果: 当たった者（近い順）と、届いた先 */
    public record Ray(List<LivingEntity> hits, Vec3 end) {}

    /** from から dir へ range まで、太さ width で線上の敵を探す（壁と、読み込まれていない所で止まる）。max 体まで */
    public static Ray ray(ServerLevel level, LivingEntity user, Vec3 from, Vec3 dir, double range, double width, int max) {
        Vec3 n = dir.normalize();
        Vec3 end = from;
        List<LivingEntity> hits = new ArrayList<>();
        for (double d = 0; d < range; d += 16) {
            Vec3 a = from.add(n.scale(d)), b = from.add(n.scale(Math.min(range, d + 16)));
            if (!level.isLoaded(BlockPos.containing(b))) { end = a; break; }
            BlockHitResult wall = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            Vec3 stop = wall.getType() != HitResult.Type.MISS ? wall.getLocation() : b;
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, new AABB(a, stop).inflate(width + 1),
                    e -> e != user && e.isAlive() && !e.isSpectator() && !hits.contains(e)
                            && e.getBoundingBox().inflate(width * 0.5).clip(a, stop).isPresent());
            found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
            for (LivingEntity e : found) if (hits.size() < max) hits.add(e);
            end = stop;
            if (wall.getType() != HitResult.Type.MISS || hits.size() >= max) break;
        }
        return new Ray(hits, end);
    }

    /** 近接（プレイヤーなら素手と同じ扱い、mob なら mob の攻撃） */
    public static DamageSource melee(LivingEntity user) {
        return user instanceof Player player ? user.damageSources().playerAttack(player) : user.damageSources().mobAttack(user);
    }

    /** a から b へ、ぎざぎざに折れる電撃の稲妻（芯と外の光） */
    public static void lightning(ServerLevel level, Vec3 a, Vec3 b, int color) {
        RandomSource r = level.random;
        Vec3 d = b.subtract(a);
        int segments = Math.max(3, (int) (d.length() * 1.5));
        Vec3 prev = a;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 p = a.add(d.scale(t));
            if (i < segments) p = p.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.12F, 5), prev, p, 0.18);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), color, 0.24F, 7), prev, p, 0.3);
            prev = p;
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), color, 0.25F, 8), b, 4, 0.25);
    }

    // ---- 遅れて起きる処理（クロノリピーターの時の残響、ジェネシス・ヘイローの軌道砲撃など） ----

    private record Task(ServerLevel level, long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    /** delay Tick 後に action を動かす（サーバーの Tick の終わりに） */
    public static void later(ServerLevel level, int delay, Runnable action) {
        TASKS.add(new Task(level, level.getServer().getTickCount() + delay, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) return;
        long now = event.getServer().getTickCount();
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.runAt() <= now) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            try {
                task.action().run();
            } catch (Exception e) {
                AutomataValkyries.LOGGER.error("[weapon] A delayed weapon task failed", e);
            }
        }
    }
}
