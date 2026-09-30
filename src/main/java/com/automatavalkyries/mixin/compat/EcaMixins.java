package com.automatavalkyries.mixin.compat;

import com.automatavalkyries.protect.EprotectCompat;
import com.automatavalkyries.protect.IEprotected;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Epic Core API（ECA）。他の即死 MOD（The Last Sword You Never Forgot 等）が使う基盤ライブラリ。
 * {@code EntityUtil.remove} は removalReason の直書き・遠方テレポート・AI とボスバーの消去・ワールドの一覧からの直接削除を行い、
 * さらに {@code EntityRemovalQuarantine} で同じ個体の再登録を拒否するため、eprotect の「消されたら戻す」が効かない。入口で止める。
 * 既定設定の経路だけが対象。「Radical Logic」（メモリ直書き・クラス再変換）は JVM 乗っ取り級なので対象外。
 */
public final class EcaMixins {

    private EcaMixins() {}

    @Pseudo
    @Mixin(targets = "net.eca.util.EntityUtil", remap = false)
    public abstract static class Util {
        @Inject(method = "kill(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardKill(LivingEntity entity, DamageSource source, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "remove(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardRemove(Entity entity, Entity.RemovalReason reason, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "removeFromServerContainers(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardContainers(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = {"cleanupAI(Lnet/minecraft/world/entity/Entity;)V", "cleanupBossBar(Lnet/minecraft/world/entity/Entity;)V"},
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardCleanup(Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        /** HP の多経路書き込み（同期データ直書き・データフロー逆算・外部スキャン）。減らす方向だけ拒否する */
        @Inject(method = "setHealth(Lnet/minecraft/world/entity/LivingEntity;F)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetHealth(LivingEntity entity, float health, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity) && !(health >= entity.getHealth())) cir.setReturnValue(false);
        }

        /** 遠方（102400, -102400, 102400）への強制テレポート */
        @Inject(method = "teleport(Lnet/minecraft/world/entity/Entity;DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardTeleport(Entity entity, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /**
     * AllReturn: 対象エンティティのクラスを持つ MOD の jar 全体を Java エージェントで再変換し、全メソッドを即 return させる。
     * The Last Sword You Never Forgot が攻撃のたびに約 13 秒有効にし、その間 eprotect（このMOD丸ごと）が止まる。
     * 対象が兵姫なら有効化させない。除外リストへの登録（{@link com.automatavalkyries.protect.EprotectEcaWhitelist}）と二重にしておく。
     */
    @Pseudo
    @Mixin(targets = "net.eca.api.EcaAPI", remap = false)
    public abstract static class Api {
        @Inject(method = "enableAllReturn(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardEnableAllReturn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }

        @Inject(method = "setEntityModAllReturn(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardSetAllReturn(Entity entity, boolean enable, CallbackInfoReturnable<Boolean> cir) {
            if (enable && EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }

    /**
     * 種類単位の出現禁止（{@code EcaAPI.banSpawn}）。The Last Sword You Never Forgot が撃破時に kill の直前に掛ける。
     * 禁止中の種類は ECA の Mixin で検索表・区画の問い合わせから隠され、追加（入り直しを含む）もすべて拒否されるので、
     * kill を止めても兵姫はワールドから見えなくなり、戻せずに退避していた。兵姫は禁止の対象外にする（他の種類の禁止はそのまま効く）。
     */
    @Pseudo
    @Mixin(targets = "net.eca.util.spawn_ban.SpawnBanHook", remap = false)
    public abstract static class SpawnBan {
        // shouldShield ではなく種類で判定する: 禁止はワールドへの追加も止めるので、まだ保護が始まっていない
        // （ワールドに入る前の）個体まで対象になる。消去の許可（撃破・退場）とは関係しない

        /** 検索表・区画・Tick 一覧・追跡からの隠蔽と追加拒否（ECA の各 Mixin が使う） */
        @Inject(method = "shouldBlockSpawn(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$exempt(Object entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof IEprotected) cir.setReturnValue(false);
        }

        /** ServerLevel.addFreshEntity / addEntity の拒否（入り直し・差し替えもここを通る） */
        @Inject(method = "shouldBlockSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$exemptAdd(Level level, Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (entity instanceof IEprotected) cir.setReturnValue(false);
        }
    }

    /** 消去中の個体の再登録を拒否する隔離。兵姫を隔離対象にさせない */
    @Pseudo
    @Mixin(targets = "net.eca.util.EntityRemovalQuarantine", remap = false)
    public abstract static class Quarantine {
        @Inject(method = "begin(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
                at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$guardBegin(ServerLevel level, Entity entity, CallbackInfo ci) {
            if (EprotectCompat.shouldShield(entity)) ci.cancel();
        }

        @Inject(method = "shouldBlockAdd(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private static void automata_valkyries$allowAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (EprotectCompat.shouldShield(entity)) cir.setReturnValue(false);
        }
    }
}
