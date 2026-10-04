package com.automatavalkyries.protect.valkyrie;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.valkyrieprotect.hooks.ValkyrieHooks;
import net.minecraft.world.entity.Entity;

/**
 * エージェントが vanilla の消去・即死の入口の先頭で問い合わせる判断（エージェントがある環境でだけ読み込む。{@link ValkyrieAgentLink}）。
 * 戦術兵姫だけ: 消去はオメガコアなら断る（無ければ通して「失われた」にする）。kill / die / HP 0 への書き換えは打ち切り、
 * オメガコアなら何もしない（即死無効）、無ければ機能停止（死亡モーション）に置き換える。他は素通し。
 * 兵姫のクラス自身の上書き（remove / kill / die / setHealth）が先に効くので、ここに来るのは、それを飛ばして vanilla の実装を直に呼ばれたとき
 */
final class ValkyrieAgentFilter implements ValkyrieHooks.Filter {

    @Override
    public boolean refuse(Object entity, String op, Object arg) {
        if (!(entity instanceof ValkyrieEntity v) || v.level().isClientSide || !v.isAddedToWorld()) return false;
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
}
