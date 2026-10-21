package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * エージェントが vanilla の消去・即死の入口の先頭で問い合わせる判断（エージェントがある環境でだけ読み込む。{@link ValkyrieAgentLink}）。
 * 戦術兵姫だけ: 消去はオメガコアなら断る（無ければ通して「失われた」にする）。kill / die / HP 0 への書き換えは打ち切り、
 * オメガコアなら何もしない（即死無効）、無ければ機能停止（死亡モーション）に置き換える。
 * 兵姫のクラス自身の上書き（remove / kill / die / setHealth）が先に効くので、ここに来るのは、それを飛ばして vanilla の実装を直に呼ばれたとき。
 * <p>
 * 保護対象のボス（{@link IEprotected}）も、消去と die の入口を同じ位置で守る（2026-10-21）。Mixin はバイトコードで外される・vanilla の実装を直に
 * 呼ばれると素通りするので、いちばん外側にもう一つ入口を置く。判断は Eprotect 自身のもの（{@link Eprotect#shouldBlockRemoval} /
 * {@link Eprotect#interceptDie}）で、正規の撃破・退場・次元移動は通す。HP の書き換え（setHealth）は Eprotect の HP の入口が見るのでここでは触らない。
 * </p>
 */
final class ValkyrieAgentFilter implements ValkyrieHooks.Filter {

    @Override
    public boolean refuse(Object entity, String op, Object arg) {
        if (entity instanceof ValkyrieEntity v) {
            if (v.level().isClientSide || !v.isAddedToWorld()) return false;
            return refuseValkyrie(v, op, arg);
        }
        if (entity instanceof IEprotected provider && entity instanceof LivingEntity living) {
            return refuseProtected(living, provider, op, arg);
        }
        return false;
    }

    private static boolean refuseValkyrie(ValkyrieEntity v, String op, Object arg) {
        switch (op) {
            case "remove", "setRemoved" -> {
                return arg instanceof Entity.RemovalReason reason && !ValkyrieProtect.allowRemoval(v, reason);
            }
            case "kill", "die", "setHealth" -> {
                if (v.isReleased()) return false;
                ValkyrieProtect.noteLethal(v, op + " (agent)");
                if (!v.hasOmegaCore()) v.shutdown();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 保護対象のボス: 消去は Eprotect の判断（不正な消去理由・ブラックリストの MOD の次元移動を断る）、die は被弾を通らない直接呼び出しを断る */
    private static boolean refuseProtected(LivingEntity living, IEprotected provider, String op, Object arg) {
        if (living.level().isClientSide || !living.isAddedToWorld()) return false;
        Eprotect protect = provider.getEprotect();
        if (protect == null) return false;
        switch (op) {
            case "remove", "setRemoved" -> {
                return arg instanceof Entity.RemovalReason reason && protect.shouldBlockRemoval(reason);
            }
            case "die" -> {
                // 正規の撃破（hurt() の中で HP が尽きた）なら false を返して通す。保護が終わった個体（退場・撃破済み）には触らない
                return protect.isProtectionActive() && protect.interceptDie();
            }
            default -> {
                return false;
            }
        }
    }
}
