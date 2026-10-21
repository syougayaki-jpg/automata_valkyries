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
        com.automatavalkyries.client.ProtectedEntityKeeper.onRemoved(packet.getEntityIds());
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
