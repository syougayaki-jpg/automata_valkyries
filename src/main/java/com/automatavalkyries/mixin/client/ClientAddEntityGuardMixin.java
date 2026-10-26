package com.automatavalkyries.mixin.client;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * このMOD のエンティティの出現の知らせ（ClientboundAddEntityPacket）を受けた直後に、本当に手元の世界へ入ったかを確かめる。
 * サーバーが追跡を作り直して出現を送っているのに、ルクシオンが手元の世界に戻らなかった（2026-10-22。見ている側には入っていて、
 * エラーも警告も出ない）。入っていなければ、記録して一度だけ作り直して入れる。入っていれば何もしない
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientAddEntityGuardMixin {

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void automata_valkyries$serverRemoved(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        // vanilla はまず通信のスレッドで呼ばれ、先頭で描くスレッドへ回し直す。先頭のここは通信のスレッドでも通るので、描くスレッドの時だけ記録を消す
        // （2026-10-26: 通信のスレッドから記録を書き換え、描くスレッドの写しと重なって ConcurrentModificationException で落ちた。零号機の撃破で多くの演出のエンティティが消えた時）
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;
        com.automatavalkyries.client.ProtectedEntityKeeper.onRemoved(packet.getEntityIds());
    }

    /** サーバーから届いた同期データ（HP を含む）を覚える（手元で書き換えられたら戻す先。ProtectedEntityKeeper） */
    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void automata_valkyries$serverData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (this.level == null) return;
        int healthId = com.automatavalkyries.mixin.LivingEntityAccessor.automata_valkyries$getDataHealthId().getId();
        for (var value : packet.packedItems()) {
            if (value.id() == healthId) { // HP が入った同期だけ（入っていない同期のときに、手元で書き換えられた値を覚えないように）
                com.automatavalkyries.client.ProtectedEntityKeeper.onServerData(this.level, packet.id());
                return;
            }
        }
    }

    /**
     * 手元に同じ ID・同じ UUID のこのMOD のエンティティがもういるところへ出現の知らせが来たら、作り直さずにその個体へ当てる
     * （2026-10-25 ユーザー指定「一瞬かくっとなる」。サーバーの保護が入り直すと出現の知らせが来て、手元で新しい個体に差し替わり、
     * 動き・モデルのアニメーションが最初からになっていた）
     */
    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void automata_valkyries$reuseExisting(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (this.level == null || !net.minecraft.client.Minecraft.getInstance().isSameThread()) return; // 別のスレッドなら vanilla が回し直す
        var key = ForgeRegistries.ENTITY_TYPES.getKey(packet.getType());
        if (key == null || !AutomataValkyries.MODID.equals(key.getNamespace())) return;
        Entity existing = this.level.getEntity(packet.getId());
        if (existing == null || existing.getType() != packet.getType() || !existing.getUUID().equals(packet.getUUID()) || existing.isRemoved()) return;
        existing.recreateFromPacket(packet);
        com.automatavalkyries.client.ProtectedEntityKeeper.onAdded(this.level, packet);
        ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void automata_valkyries$verifyAdded(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (this.level != null) com.automatavalkyries.client.ProtectedEntityKeeper.onAdded(this.level, packet);
        EntityType<?> type = packet.getType();
        var key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (key == null || !AutomataValkyries.MODID.equals(key.getNamespace()) || this.level == null) return;
        if (this.level.getEntity(packet.getId()) != null) return;
        AutomataValkyries.LOGGER.warn("[client] The add-entity packet for {} (id {}) did not put it into the local world; adding it again", key, packet.getId());
        try {
            Entity entity = type.create(this.level);
            if (entity == null) return;
            entity.recreateFromPacket(packet);
            this.level.putNonPlayerEntity(packet.getId(), entity);
            AutomataValkyries.LOGGER.warn("[client] Added {} (id {}) again: now in the local world = {}", key, packet.getId(), this.level.getEntity(packet.getId()) != null);
        } catch (Throwable t) {
            AutomataValkyries.LOGGER.warn("[client] Could not add {} (id {}) again: {}", key, packet.getId(), t.toString());
        }
    }
}
