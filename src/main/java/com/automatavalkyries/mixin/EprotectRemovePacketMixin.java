package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.EprotectManager;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 手元（クライアント）へ送る「消した」知らせ（{@link ClientboundRemoveEntitiesPacket}）から、保護中で、そのプレイヤーにまだ見えているはずの
 * 個体の ID を抜く（2026-10-25 ユーザー指定「一瞬消えるので汎用的な処理でイベントをはじけないだろうか」）。
 * The King Sword の消去はこの知らせを全員へ直に送り、サーバーでは保護が断って残っていても、手元からは入り直しまで消えて見えた。
 * 保護の入り直し（{@code WorldPresence.rejoin}）が追跡を外すときの知らせも抜けるので、入り直しでもちらつかない（続く出現の知らせで、
 * 手元は同じ ID の個体をその場で差し替える）。誰が送ったかは問わない。撃破を認めた後・正規の退場・遠く離れた・別の次元にいるときは通す
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EprotectRemovePacketMixin {

    @Shadow
    public net.minecraft.server.level.ServerPlayer player;

    @Shadow
    public abstract void send(Packet<?> packet, @Nullable PacketSendListener listener);

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$keepProtectedVisible(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
        if (!(packet instanceof ClientboundRemoveEntitiesPacket remove) || player == null) return;
        IntArrayList kept = null;
        for (int id : remove.getEntityIds()) {
            Eprotect protect = EprotectManager.getInstance().findVisibleTo(player, id);
            if (protect == null) {
                if (kept != null) kept.add(id);
                continue;
            }
            if (kept == null) { // 抜く ID が出たところで、それまでの ID を写す
                kept = new IntArrayList();
                for (int earlier : remove.getEntityIds()) {
                    if (earlier == id) break;
                    kept.add(earlier);
                }
            }
            AttackLog.refused(protect.getOwner(), AttackOp.REMOVE + ":client_packet");
        }
        if (kept == null) return;
        ci.cancel();
        if (!kept.isEmpty()) send(new ClientboundRemoveEntitiesPacket(kept), listener);
    }
}
