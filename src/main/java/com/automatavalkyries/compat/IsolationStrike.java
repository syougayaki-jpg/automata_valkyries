package com.automatavalkyries.compat;

import com.automatavalkyries.annihilate.IsolationDamageEngine;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 他 MOD の武器の一撃を隔絶ダメージに置き換える共通の部品（2026-10-30。Tinkers のアビリティ「隔絶」と抜刀剣の星刻神刀「ステラ・クロノダイム」が使う）。
 * 隔絶の中身は {@link IsolationDamageEngine#ensureIsolatedAttack}（Antigravity の担当なので呼ぶだけ）
 */
public final class IsolationStrike {

    /** 隔絶ダメージを掛けている間に、その中で起きた被弾をもう一度置き換えない */
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    /** 隔絶に置き換えた一撃の数（GameTest・調査用。元の一撃の取り消し後もバニラが最後の被弾の種類を元の物で上書きするので、種類では確かめられない） */
    private static long replacedHits;

    private IsolationStrike() {}

    public static long replacedHits() {
        return replacedHits;
    }

    public static boolean applying() {
        return APPLYING.get();
    }

    /** 被弾を隔絶ダメージに置き換える。隔絶が通った時だけ元の一撃を取り消す（保護対象・プレイヤーの安全弁などで断られたら、元の一撃をそのまま通す） */
    public static void replace(LivingHurtEvent event, LivingEntity attacker) {
        if (APPLYING.get() || event.getEntity().level().isClientSide) return;
        APPLYING.set(true);
        try {
            if (IsolationDamageEngine.ensureIsolatedAttack(attacker, event.getEntity(), event.getSource(), event.getAmount())) {
                event.setCanceled(true);
                replacedHits++;
            }
        } finally {
            APPLYING.set(false);
        }
    }
}
