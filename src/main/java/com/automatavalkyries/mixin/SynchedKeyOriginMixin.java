package com.automatavalkyries.mixin;

import com.automatavalkyries.protect.flag.SynchedKeyOrigins;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 同期データの鍵が作られた場所を覚える（{@link SynchedKeyOrigins}。他 MOD が鍵を補助クラスに置いても名前を引けるように） */
@Mixin(SynchedEntityData.class)
public abstract class SynchedKeyOriginMixin {
    @Inject(method = "defineId", at = @At("RETURN"))
    private static <T> void automata_valkyries$recordOrigin(Class<? extends Entity> owner, EntityDataSerializer<T> serializer,
                                                            CallbackInfoReturnable<EntityDataAccessor<T>> cir) {
        SynchedKeyOrigins.record(owner, cir.getReturnValue());
    }
}
