package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ElenorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AutomataValkyries.MODID);

    public static final RegistryObject<EntityType<ElenorEntity>> ELENOR = ENTITY_TYPES.register("elenor", () ->
            EntityType.Builder.of(ElenorEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F) // デフォルメ体型（頭頂 30.85px）
                    .clientTrackingRange(10)
                    .build("elenor"));

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ELENOR.get(), ElenorEntity.createAttributes().build());
    }
}
