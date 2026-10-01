package com.automatavalkyries.protect;

import com.automatavalkyries.protect.attack.AttackLog;
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
        if (!SpecificCountermeasures.enabled()) return false; // 準汎用の層だけを測る設定
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (PlayerWard.isActive(entity)) return true; // 守護の護符を持つプレイヤー

        if (!(entity instanceof IEprotected provider)) return false;
        Eprotect protect = provider.getEprotect();
        if (protect == null) return false;
        // 撃破・退場の状態はサーバーにしか無い。クライアントでは常に拒否し、消去はサーバーの通知に任せる
        return entity.level().isClientSide || protect.isGuardingRemoval();
    }

    /**
     * 他 MOD の攻撃（キル・消去・HP 書き換え等）の入口で使う: {@link #shouldShield} と同じ判定をし、止めるなら攻撃として記録する。
     * 判定だけの問い合わせ（「この個体は免除か」「死亡予定か」等）は常に呼ばれるので {@link #shouldShield} を使うこと。
     *
     * @param operation 記録に出す操作名（例: "eca:kill"）
     */
    public static boolean shieldAttack(@Nullable Entity entity, String operation) {
        if (!shouldShield(entity)) return false;
        Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        AttackLog.shielded(target, operation);
        if (target instanceof com.automatavalkyries.entity.ElenorEntity elenor && elenor.getTarget() == null && !target.level().isClientSide) {
            target.level().getEntities(target, target.getBoundingBox().inflate(64.0D),
                    e -> e instanceof net.minecraft.world.entity.LivingEntity living && com.automatavalkyries.entity.ElenorEntity.isHostileOrBossMob(living))
                    .stream().findFirst().ifPresent(e -> elenor.setTarget((net.minecraft.world.entity.LivingEntity) e));
        }
        return true;
    }
}
