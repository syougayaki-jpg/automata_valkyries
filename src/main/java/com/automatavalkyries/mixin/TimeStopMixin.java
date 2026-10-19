package com.automatavalkyries.mixin;

import com.automatavalkyries.entity.chrono.TimeStop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * タイムルーラーの全世界時間停止（{@link TimeStop}、2026-10-19）。止まっている間に、止められている者の Tick と、世界の時の進みを止める
 */
public final class TimeStopMixin {

    private TimeStopMixin() {
    }

    /** 世界: エンティティの Tick・時刻・天気・乱数の Tick・ブロックの出来事を止め、ブロックと液体の予定の Tick は 1 Tick 後ろへずらす */
    @Mixin(ServerLevel.class)
    public abstract static class Level {

        @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeEntity(Entity entity, CallbackInfo ci) {
            if (TimeStop.frozen(entity)) ci.cancel();
        }

        @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezePassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
            if (TimeStop.frozen(passenger)) ci.cancel();
        }

        @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeTime(CallbackInfo ci) {
            if (TimeStop.active()) ci.cancel();
        }

        @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeWeather(CallbackInfo ci) {
            if (TimeStop.active()) ci.cancel();
        }

        @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeChunk(LevelChunk chunk, int randomTicks, CallbackInfo ci) {
            if (TimeStop.active()) ci.cancel();
        }

        @Inject(method = "runBlockEvents", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeBlockEvents(CallbackInfo ci) {
            if (TimeStop.active()) ci.cancel();
        }

        @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$delayBlock(BlockPos pos, Block block, CallbackInfo ci) {
            if (!TimeStop.active()) return;
            ((ServerLevel) (Object) this).scheduleTick(pos, block, 1);
            ci.cancel();
        }

        @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$delayFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
            if (!TimeStop.active()) return;
            ((ServerLevel) (Object) this).scheduleTick(pos, fluid, 1);
            ci.cancel();
        }
    }

    /** プレイヤーの体の Tick（落下・効果・空腹など） */
    @Mixin(ServerPlayer.class)
    public abstract static class Player {

        @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
        private void automata_valkyries$freezeBody(CallbackInfo ci) {
            if (TimeStop.frozen((ServerPlayer) (Object) this)) ci.cancel();
        }
    }

    /** プレイヤーの操作: 移動はその場へ戻し、使う・壊す・叩く・乗り物の操作は断る */
    @Mixin(ServerGamePacketListenerImpl.class)
    public abstract static class Packets {

        @Shadow
        public ServerPlayer player;

        @Shadow
        public abstract void teleport(double x, double y, double z, float yRot, float xRot);

        private static final String ENSURE = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

        @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdPlayer(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket packet, CallbackInfo ci) {
            if (!TimeStop.frozen(player)) return;
            if (packet.hasPosition() && player.position().distanceToSqr(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())) > 1.0e-4) {
                teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            ci.cancel();
        }

        @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdVehicle(net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }

        @Inject(method = "handlePlayerInput", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdInput(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }

        @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdUseOn(net.minecraft.network.protocol.game.ServerboundUseItemOnPacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }

        @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdUse(net.minecraft.network.protocol.game.ServerboundUseItemPacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }

        @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdAction(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }

        @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER), cancellable = true)
        private void automata_valkyries$holdInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket packet, CallbackInfo ci) {
            if (TimeStop.frozen(player)) ci.cancel();
        }
    }
}
