package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** {@link Eprotect} の被弾の上限: 異常値の置き換えと、時間窓ごとに HP から引く量の上限（数値は {@link EprotectProfile}） */
final class DamageLimiter {

    private final Eprotect protect;
    /** 時間窓が始まっているか・その開始時刻・その窓で既に引いた量 */
    private boolean windowOpen;
    private long windowStart;
    private float inWindow;
    /** 直前の被弾の、置き換える前と後の値（DPS チェックで本来の火力を測るため。2026-10-06） */
    private float lastRaw = Float.NaN, lastSanitized = Float.NaN;

    DamageLimiter(Eprotect protect) {
        this.protect = protect;
    }

    /** hurt() の入口: 異常値（{@link EprotectProfile#isHugeDamage}）を置き換える。保護が切れていればそのまま */
    float sanitize(float amount, boolean active) {
        lastRaw = amount;
        if (active && protect.profile().isHugeDamage(amount)) return lastSanitized = protect.profile().hugeDamageClamp();
        return lastSanitized = amount;
    }

    /** 直前の被弾で置き換えていたら、amount（置き換えた値から防具などで減った後）を本来の大きさへ戻す */
    float unclamped(float amount) {
        if (Float.isFinite(lastRaw) && lastSanitized > 0.0F && lastRaw > lastSanitized) return amount * (lastRaw / lastSanitized);
        return amount;
    }

    /**
     * HP に反映される直前: 時間窓の間に引く量を、合計で最大 HP の上限の割合までに制限する。窓は最初の被弾から数え始める
     * （上限いっぱいの 1 撃を受けたら、次に削られるのは窓が明けてから）。上限なしの個体は NaN・無限大だけ置き換える
     */
    float cap(float amount, @Nullable DamageSource source) {
        EprotectProfile profile = protect.profile();
        LivingEntity owner = protect.getOwner();
        if (!profile.capDamage()) return Float.isFinite(amount) ? amount : profile.hugeDamageClamp();
        // 強化保護中でも通常攻撃は上限どおりに通す（即死系は入口で止める。EprotectForgeEvents.isLethalAttack）
        // ブラックリストの MOD からのダメージは上限を下げる（罰則）
        String penalized = source != null ? blacklistedDamageMod(owner, source) : null;
        float ratio = penalized != null ? profile.blacklistHitRatio() : profile.maxHitRatio();
        if (penalized != null) AttackLog.blocked(owner, AttackOp.DAMAGE_PENALTY, penalized);
        float cap = Math.max(1.0F, owner.getMaxHealth() * ratio);
        if (!Float.isFinite(amount)) amount = cap;
        long now = protect.serverClock();
        if (!windowOpen || now - windowStart >= profile.damageWindowTicks()) {
            windowOpen = true;
            windowStart = now;
            inWindow = 0.0F;
        }
        float allowed = Math.max(0.0F, Math.min(amount, cap - inWindow));
        inWindow += allowed;
        return allowed;
    }

    /** 時間窓を数え直す */
    void reset() {
        windowOpen = false;
        inWindow = 0.0F;
    }

    /**
     * ダメージの出どころがブラックリストの MOD ならその ID（攻撃者の持つアイテム → 直接・間接の攻撃元の種類 → ダメージの種類 →
     * 呼び出し経路の順に見る）。ブラックリストが空なら何もしない
     */
    @Nullable
    private static String blacklistedDamageMod(LivingEntity owner, DamageSource source) {
        if (!ModBlacklist.isActive()) return null;
        List<String> candidates = new ArrayList<>();
        if (source.getEntity() instanceof LivingEntity attacker && !attacker.getMainHandItem().isEmpty()) {
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(attacker.getMainHandItem().getItem());
            if (item != null) candidates.add(item.getNamespace());
        }
        if (source.getDirectEntity() != null) candidates.add(BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()).getNamespace());
        if (source.getEntity() != null) candidates.add(BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType()).getNamespace());
        source.typeHolder().unwrapKey().ifPresent(key -> candidates.add(key.location().getNamespace()));
        // このMOD のダメージ（兵装の種類・持っている兵装）は罰則にしない。呼び出し経路には、全員の hurt / setHealth を包む他 MOD の Mixin
        // （Witherzilla 等）が必ず挟まるので、経路で探すとそれがブラックリストに載っているだけで自分の兵装まで 1% にされていた（2026-10-05）
        if (candidates.contains(AutomataValkyries.MODID)) return null;
        for (String mod : candidates) {
            if (ModBlacklist.contains(mod)) return mod;
        }
        // 経路で探すのは、出どころの手がかりが何も無い（vanilla の種類だけ・攻撃者もいない）ときだけ
        boolean anyClue = candidates.stream().anyMatch(m -> !"minecraft".equals(m));
        if (anyClue) return null;
        String caller = CallerAttribution.foreignClassMod(owner);
        return ModBlacklist.contains(caller) ? caller : null;
    }
}
