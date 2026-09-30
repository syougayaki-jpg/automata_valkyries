package com.automatavalkyries.protect;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 他 MOD（IMDS-Reborn / No-Sugar 等）のキル処理の入口に差し込む互換 Mixin 用の判定。
 * 対象が保護中の兵姫（またはその部位）なら、その MOD のキル処理自体を実行させない。
 */
public final class EprotectCompat {

    private EprotectCompat() {}

    /** この対象へのキル処理を拒否すべきか */
    public static boolean shouldShield(@Nullable Entity entity) {
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (!(entity instanceof IEprotected provider)) return false;
        Eprotect protect = provider.getEprotect();
        if (protect == null) return false;
        // 撃破・退場の状態はサーバーにしか無い。クライアントでは常に拒否し、消去はサーバーの通知に任せる
        return entity.level().isClientSide || protect.isGuardingRemoval();
    }
}
