package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * メメントモリの死蝶の呪い（2026-10-10 ユーザー指定「蝶ビットに当たるたびに最大 HP が 1 回 5%・下限 40%」「戦闘中だけ減り、倒すと戻る」。
 * 2026-10-15 ユーザー指定で弾も 1 発 1% 削るようにしたので、重なりの 1 つを 1% にした。蝶ビットは {@link #BIT} つ＝5%）。
 * 当たるたびに最大 HP を元（ほかの効果を入れた値）の {@link #STEP} ずつ減らし（掛け算の修飾子。保存されない一時のもの）、元の {@link #FLOOR} より下にはしない。
 * メメントモリを倒す・メメントモリから {@link #RELEASE_RANGE} ブロックより離れる・死ぬ・ログアウトすると元に戻る
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class MementoCurse {

    public static final double STEP = 0.01, FLOOR = 0.40, RELEASE_RANGE = 96.0;
    /** 蝶ビット 1 匹・弾 1 発で重ねる数（5% と 1%） */
    public static final int BIT = 5, BULLET = 1;
    public static final int MAX_STACKS = (int) Math.round((1.0 - FLOOR) / STEP);
    private static final UUID ID = UUID.fromString("6b1f3c52-9e4d-4a7a-8c21-d0f5e7a9b3c4");
    /** 深まった呪い（メメントモリ EX の第 3 形態。2026-10-27 ユーザー指定「呪いの深化」）: 最大 HP は {@link #DEEP_FLOOR} まで、移動速度も 1 つにつき {@link #SLOW_STEP} ずつ（{@link #SLOW_MAX} まで） */
    public static final double DEEP_FLOOR = 0.25, SLOW_STEP = 0.005, SLOW_MAX = 0.30;
    public static final int DEEP_MAX_STACKS = (int) Math.round((1.0 - DEEP_FLOOR) / STEP);
    private static final UUID SLOW_ID = UUID.fromString("0d7c2e94-3f61-4b8a-9e15-a6c4d2f8b371");
    /** 呪いを受けている生き物（UUID → いた世界）。毎秒、近くにメメントモリがいるか確かめる */
    private static final Map<UUID, ServerLevel> CURSED = new HashMap<>();

    private MementoCurse() {
    }

    /** 今の重なりの数（0〜{@link #MAX_STACKS}） */
    public static int stacks(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier m = a == null ? null : a.getModifier(ID);
        return m == null ? 0 : (int) Math.round(-m.getAmount() / STEP);
    }

    /** n だけ重ねる（上限まで）。今の HP が新しい最大より多ければ切り詰める */
    public static void add(LivingEntity e, int n) {
        add(e, n, false);
    }

    /** source（弾・蝶の持ち主）のメメントモリの呪いを n だけ重ねる。EX の第 3 形態なら深まった呪い */
    public static void add(LivingEntity e, int n, @org.jetbrains.annotations.Nullable Entity source) {
        add(e, n, source instanceof MementoMoriEntity m && m.deepCurse());
    }

    private static void add(LivingEntity e, int n, boolean deep) {
        if (!(e.level() instanceof ServerLevel level) || n <= 0) return;
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        int next = Math.min(deep ? DEEP_MAX_STACKS : Math.max(MAX_STACKS, stacks(e)), stacks(e) + n); // 深まった呪いで重ねた分は、ふつうの呪いでは減らさない
        if (deep) {
            AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SLOW_ID);
                speed.addTransientModifier(new AttributeModifier(SLOW_ID, "memento_mori_deep_curse", -Math.min(SLOW_MAX, SLOW_STEP * next), AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        a.removeModifier(ID);
        a.addTransientModifier(new AttributeModifier(ID, "memento_mori_curse", -STEP * next, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
        CURSED.put(e.getUUID(), level);
    }

    /** 元に戻す */
    public static void clear(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attributes.MAX_HEALTH);
        if (a != null && a.getModifier(ID) != null) a.removeModifier(ID);
        removeSlow(e);
        CURSED.remove(e.getUUID());
    }

    /** メメントモリが倒れた: まわり（{@link #RELEASE_RANGE}）の呪いをすべて解く */
    public static void releaseAround(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
        for (Iterator<Map.Entry<UUID, ServerLevel>> it = CURSED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ServerLevel> en = it.next();
            if (en.getValue() != level) continue;
            Entity e = level.getEntity(en.getKey());
            if (e instanceof LivingEntity living && living.position().distanceTo(at) <= RELEASE_RANGE * 1.5) {
                AttributeInstance a = living.getAttribute(Attributes.MAX_HEALTH);
                if (a != null) a.removeModifier(ID);
                removeSlow(living);
                it.remove();
            }
        }
    }

    /** 毎秒: 近くに生きたメメントモリがいない（離れた・倒した・退いた）なら元に戻す。いなくなった生き物は忘れる */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CURSED.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, ServerLevel>> it = CURSED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ServerLevel> en = it.next();
            Entity e = en.getValue().getEntity(en.getKey());
            if (!(e instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            boolean near = !living.level().getEntitiesOfClass(MementoMoriEntity.class, living.getBoundingBox().inflate(RELEASE_RANGE),
                    m -> m.isAlive() && !m.isDefeatedSynced()).isEmpty();
            if (!near) {
                AttributeInstance a = living.getAttribute(Attributes.MAX_HEALTH);
                if (a != null) a.removeModifier(ID);
                removeSlow(living);
                it.remove();
            }
        }
    }

    private static void removeSlow(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW_ID) != null) speed.removeModifier(SLOW_ID);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity());
    }
}
