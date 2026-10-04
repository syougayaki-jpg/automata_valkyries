package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.compat.OmniMobsExplosion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Omni-Mobs の不安定さの対策（2026-10-05 ユーザー「不安定なオムニモブズをどうにかしましょう」）。
 * <p>{@code LevelUtil.explode(x, y, z, power, level)} は、光線の向きを決めるためだけに「強さ × 100」体の Husk を作り（ワールドには入れない）、
 * 当たったブロックごとに {@code setblock ~ ~ ~ air} のコマンドを実行する。作った Husk がどこかの集合に握られて残り続けてメモリを埋め、
 * 終了時の保存が止まった（2026-10-05 のヒープダンプの調査）。コマンドもブロックごとに解釈し直すので重い。</p>
 * <p>同じ結果を Husk を作らずに出す {@link OmniMobsExplosion} に置き換える。爆発の範囲・壊れ方は変えない。
 * 処理の本体は Mixin のパッケージの外に置く（このクラスの外側のクラスは実行中に読み込めない）</p>
 */
public final class OmniMobsStabilityMixins {

    private OmniMobsStabilityMixins() {}

    @Pseudo
    @Mixin(targets = "flashfur.omnimobs.util.LevelUtil", remap = false)
    public abstract static class Explode {
        @Inject(method = "explode(DDDFLnet/minecraft/world/level/Level;)V", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$explodeWithoutProbes(double x, double y, double z, float power, Level level, CallbackInfo ci) {
            ci.cancel();
            if (level instanceof ServerLevel server) OmniMobsExplosion.explode(x, y, z, power, server); // 元もコマンドなのでサーバーだけで効く
        }
    }
}
