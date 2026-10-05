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

    /** おまけ: 攻撃されると終焉で反撃する「最果てのエレノア」（見た目・大きさはエレノアと同じ） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.FarthestElenorEntity>> FARTHEST_ELENOR =
            ENTITY_TYPES.register("farthest_elenor", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.FarthestElenorEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(10)
                            .build("farthest_elenor"));

    /** 戦術兵姫（仮。2026-10-04、見た目と動きの確認用。身長 約 27px） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.ValkyrieEntity>> VALKYRIE =
            ENTITY_TYPES.register("valkyrie", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.ValkyrieEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.7F)
                            .clientTrackingRange(10)
                            .build("valkyrie"));

    /** 戦術兵姫のメニュー（換装・状態・作戦指示。段階 2、2026-10-05） */
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.automatavalkyries.menu.ValkyrieMenu>> VALKYRIE_MENU =
            ModMachines.MENUS.register("valkyrie", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(com.automatavalkyries.menu.ValkyrieMenu::new));

    /** 終焉機構の保護対象外・Eprotect+ 検証用ダミーゾンビ */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.ProtectDummyZombieEntity>> PROTECT_DUMMY_ZOMBIE =
            ENTITY_TYPES.register("protect_dummy_zombie", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.ProtectDummyZombieEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("protect_dummy_zombie"));

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(VALKYRIE.get(), com.automatavalkyries.entity.ValkyrieEntity.createAttributes().build());
        event.put(ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(FARTHEST_ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(PROTECT_DUMMY_ZOMBIE.get(), com.automatavalkyries.entity.ProtectDummyZombieEntity.createAttributes().build());
    }
}
