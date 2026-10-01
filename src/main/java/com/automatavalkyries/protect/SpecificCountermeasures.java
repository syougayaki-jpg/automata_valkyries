package com.automatavalkyries.protect;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import net.minecraft.world.entity.Entity;

/**
 * MOD ごとの個別対策（互換 Mixin・{@link KillModCounters}・超絶抹殺の棒のボス固有の無力化）の入り切り。
 * <p>
 * 通常は常に有効。システムプロパティ {@code automata_valkyries.genericOnly=true}（{@code ./gradlew runGameTestServer -PgenericOnly}）
 * のときだけ切り、準汎用の層（フラグ辞書・入口の拒否・修復・入り直し）だけでどこまで守れる・倒せるかを測る。
 * </p>
 */
public final class SpecificCountermeasures {

    private static final boolean ENABLED = !Boolean.getBoolean("automata_valkyries.genericOnly");

    private SpecificCountermeasures() {}

    public static boolean enabled() {
        return ENABLED;
    }

    /** 互換 Mixin 用: 超絶抹殺の棒で消した個体として、その MOD の生命維持・免除を外してよいか */
    public static boolean annihilatedForCompat(Entity entity) {
        return ENABLED && AnnihilationExecutionAgent.isAnnihilated(entity);
    }
}
