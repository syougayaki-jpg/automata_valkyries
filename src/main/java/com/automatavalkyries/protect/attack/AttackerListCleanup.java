package com.automatavalkyries.protect.attack;

import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.util.StaticReferencePurger;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻撃を止めたとき、その攻撃をしてきたクラス（スタックで分かる）の static な一覧・管理表から、保護対象と UUID を取り除く。
 * 即死 MOD は「抹殺予定」の一覧に載せて後から何度も攻撃し直すことが多い（IMDS の強制死亡リスト、MoreAvaritia の死亡リスト等）。
 * <p>
 * 範囲を絞る: 保護対象（兵姫）への攻撃だけ、他 MOD のクラス（とその親クラス）だけ、同じクラスは 1 Tick に 1 回まで。
 * </p>
 */
final class AttackerListCleanup {

    /** クラス → 最後に掃除したゲーム時刻 */
    private static final Map<Class<?>, Long> LAST_CLEANUP = new ConcurrentHashMap<>();

    private AttackerListCleanup() {}

    static void afterAttack(Entity target, CallerAttribution.Attribution attribution) {
        if (!(target instanceof IEprotected) || target.level().isClientSide) return;
        // 攻撃したクラスが参照する別のクラスの抹殺予定の一覧も覚え、以後は毎 Tick 外す（フラグ辞書で名前から見分ける）
        KillLists.discoverFrom(attribution.callerClass());
        KillLists.discoverFrom(attribution.foreignClass());
        long now = target.level().getGameTime();
        Set<Class<?>> classes = new LinkedHashSet<>();
        addForeign(attribution.callerClass(), now, classes);
        addForeign(attribution.foreignClass(), now, classes);
        if (classes.isEmpty()) return;
        Set<Object> victims = Collections.newSetFromMap(new IdentityHashMap<>());
        victims.add(target);
        StaticReferencePurger.purge(classes, victims, target.getUUID(), target.getName().getString());
    }

    private static void addForeign(Class<?> type, long now, Set<Class<?>> out) {
        for (Class<?> c = type; c != null && CallerAttribution.isForeign(CallerAttribution.modOf(c)); c = c.getSuperclass()) {
            Long last = LAST_CLEANUP.put(c, now);
            if (last == null || last != now) out.add(c);
        }
        if (LAST_CLEANUP.size() > 1024) LAST_CLEANUP.clear();
    }
}
