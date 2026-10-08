package com.automatavalkyries.protect;

import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;

/**
 * SuperSteve（PLZLiZi / SSCore / SSUtil）による即死・消去・無力化に対する特化カウンターハンドラー。
 * <p>
 * SuperSteve は以下の即死・物理排除ロジックを使用する：
 * <ol>
 *   <li>{@code SSUtil.simpleKillEntity}:
 *       <ul>
 *         <li>クライアントへ {@code RmCEntityL} パケット送信（クライアント側での物理破棄を強制）</li>
 *         <li>{@code levelCallback = EntityInLevelCallback.NULL} （イベント通知遮断・位置更新停止）</li>
 *         <li>{@code bb = new AABB(x, y, z, x, y, z)} （当たり判定を体積 0 の点に縮小・無力化）</li>
 *         <li>{@code removalReason = DISCARDED}, {@code dead = true}, {@code deathTime = 0}</li>
 *         <li>{@code invulnerable = false}, {@code canUpdate = false}, {@code noPhysics = true}</li>
 *         <li>ワールドの各一覧（ChunkMap, EntityLookup, EntitySectionStorage）から直接削除</li>
 *       </ul>
 *   </li>
 *   <li>{@code AgtCallback.afterTransform}: 他 MOD のエージェントによるバイトコード変換を強制的に {@code null} 破棄</li>
 * </ol>
 * </p>
 */
public final class EprotectSuperSteveCounter {

    private static boolean initialized = false;
    private static Class<?> ssUtilClass = null;
    private static Class<?> agtCallbackClass = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            ssUtilClass = Class.forName("plz.lizi.supersteve.api.SSUtil");
        } catch (Throwable ignored) {
            ssUtilClass = null;
        }
        try {
            agtCallbackClass = Class.forName("plz.lizi.supersteve.power.AgtCallback");
        } catch (Throwable ignored) {
            agtCallbackClass = null;
        }
    }

    /** SuperSteve の改ざん・無力化を検知して修復する */
    public static boolean counter(LivingEntity entity) {
        if (!initialized) init();

        boolean neutralized = false;

        // 1. 当たり判定（BoundingBox）の崩壊検知と復元
        AABB currentBb = entity.getBoundingBox();
        if (currentBb.getXsize() < 0.001D && currentBb.getYsize() < 0.001D && currentBb.getZsize() < 0.001D) {
            entity.refreshDimensions();
            neutralized = true;
        }

        // 2. noPhysics の改ざん復元
        if (entity.noPhysics) {
            entity.noPhysics = false;
            neutralized = true;
        }

        // 3. AgtCallback の INST 整合性維持（他エージェント破棄トラップの解除）
        if (agtCallbackClass != null) {
            neutralized |= AgentHijackGuard.ensureAgtCallbackNeutralized();
        }

        return neutralized;
    }
}
