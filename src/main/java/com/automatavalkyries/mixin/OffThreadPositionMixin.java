package com.automatavalkyries.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * サーバースレッド以外での座標設定から、Forge の「移動先のチャンクを読み込む」処理を外す（固まり対策）。
 * <p>
 * Forge は {@code setPosRaw} で、ワールドに追加済み（isAddedToWorld）の個体なら移動先のチャンクを同期で読み込む。
 * Witherzilla の処刑竜はコンストラクタで isAddedToWorld を true にするため、保存された処刑竜をディスクから読み込むと
 * （読み込みは別スレッドで行われる）チャンクの読み込みがサーバースレッド待ちになる。終了時の保存でサーバースレッドが
 * 読み込みの完了を待っていると、互いに待ち合って固まる（実際に終了時に固まった）。
 * サーバースレッド以外ではその個体はまだワールドに入っていないので、読み込みの間だけ isAddedToWorld を下ろして戻す。
 * </p>
 */
@Mixin(Entity.class)
public abstract class OffThreadPositionMixin {

    @Unique
    private boolean automata_valkyries$restoreAddedToWorld;

    @Inject(method = "setPosRaw", at = @At("HEAD"))
    private void automata_valkyries$skipOffThreadChunkLoad(double x, double y, double z, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.isAddedToWorld() || !(self.level() instanceof ServerLevel serverLevel) || serverLevel.getServer().isSameThread()) return;
        automata_valkyries$restoreAddedToWorld = true;
        ((EntityAccessor) self).automata_valkyries$setAddedToWorld(false);
    }

    @Inject(method = "setPosRaw", at = @At("RETURN"))
    private void automata_valkyries$restoreAddedToWorld(double x, double y, double z, CallbackInfo ci) {
        if (!automata_valkyries$restoreAddedToWorld) return;
        automata_valkyries$restoreAddedToWorld = false;
        ((EntityAccessor) (Object) this).automata_valkyries$setAddedToWorld(true);
    }
}
