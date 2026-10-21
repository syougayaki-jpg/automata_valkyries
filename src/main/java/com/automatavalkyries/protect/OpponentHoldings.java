package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.util.UnsafeHelper;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 戦っている相手の MOD の static な一覧・管理表に、保護対象が載っていたら外す（準汎用。名前は見ず、載っているかで判断する。2026-10-22）。
 * <p>
 * SuperSteve は消去した個体を {@code SSUtil.D_ENTITIES}（名前から死亡の一覧とは分からない）に入れ、描画の一覧（{@code entitiesForRendering}）と
 * 範囲の検索から、その一覧にある個体を除く。{@link Entity#equals} は ID の比較なので、サーバーで入り直した個体も、手元で作り直した個体も、
 * 同じ ID である限り一覧に居る扱いになり、当たり判定ごと見えなくなる。除く先は一覧の「要素」なので、同じ UUID の個体はどの実体でも外す。
 * </p>
 * 範囲を絞る: 相手のクラス階層とそれが参照する他 MOD のクラスだけ、初期化済みのクラスだけ（static を読むと初期化が走る）、{@link #MAX_SIZE} より
 * 大きい一覧は触らない
 */
public final class OpponentHoldings {

    public static final int MAX_SIZE = 10_000;
    private static final long REFRESH_MS = 10_000L;

    private OpponentHoldings() {}

    private record Cached(long at, List<Field> fields) {}

    private static final Map<Class<?>, Cached> CACHE = new ConcurrentHashMap<>();

    /**
     * @param victim   保護対象
     * @param opponent 戦っている相手（クラス階層を調べる）
     * @return 外した件数
     */
    public static int purge(Entity victim, Entity opponent) {
        Class<?> type = opponent.getClass();
        if (!CallerAttribution.isForeign(CallerAttribution.modOf(type))) return 0;
        Cached cached = CACHE.get(type);
        long now = System.currentTimeMillis();
        if (cached == null || now - cached.at() > REFRESH_MS) {
            cached = new Cached(now, collectFields(type));
            if (CACHE.size() > 256) CACHE.clear();
            CACHE.put(type, cached);
        }
        int removed = 0;
        for (Field field : cached.fields()) {
            int n = purgeField(field, victim);
            if (n > 0) {
                removed += n;
                AutomataValkyries.LOGGER.warn("[eprotect] Removed {} reference(s) to {} from {}.{} (held by the opponent's mod)", n, victim, field.getDeclaringClass().getName(), field.getName());
                AttackLog.neutralized(victim, CallerAttribution.modOf(field.getDeclaringClass()), "held_by:" + field.getDeclaringClass().getSimpleName() + "." + field.getName());
            }
        }
        return removed;
    }

    /** GameTest 用 */
    public static void clearForTest() {
        CACHE.clear();
    }

    private static List<Field> collectFields(Class<?> type) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && CallerAttribution.isForeign(CallerAttribution.modOf(c)); c = c.getSuperclass()) {
            classes.add(c);
            classes.addAll(KillLists.relatedClasses(c));
        }
        List<Field> out = new ArrayList<>();
        for (Class<?> c : classes) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c)) || !UnsafeHelper.isInitialized(c)) continue;
            try {
                for (Field field : UnsafeHelper.getRealDeclaredFields(c)) {
                    if (!Modifier.isStatic(field.getModifiers())) continue;
                    if (!Collection.class.isAssignableFrom(field.getType()) && !Map.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        out.add(field);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static int purgeField(Field field, Entity victim) {
        try {
            Object value = field.get(null);
            if (value == null) return 0;
            if (value instanceof Collection<?> collection) return purgeCollection(collection, victim);
            if (value instanceof Map<?, ?> map) return purgeCollection(map.keySet(), victim);
        } catch (Throwable ignored) {}
        return 0;
    }

    /** 同じ UUID の個体の要素を、1 件ずつ remove で外す（実体が違っても外す。一覧の remove は equals で外すので、ID が同じ実体が要素にいれば外れる） */
    private static int purgeCollection(Collection<?> collection, Entity victim) {
        List<Object> hits = new ArrayList<>();
        try {
            if (collection.size() > MAX_SIZE) return 0;
            for (Object element : new ArrayList<>(collection)) {
                if (element instanceof Entity e && e.getUUID().equals(victim.getUUID())) hits.add(element);
            }
        } catch (Throwable t) {
            return 0;
        }
        int removed = 0;
        for (Object hit : hits) {
            try {
                if (collection.remove(hit)) removed++;
            } catch (Throwable ignored) {}
        }
        return removed;
    }
}
