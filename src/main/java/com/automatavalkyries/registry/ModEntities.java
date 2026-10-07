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

    /** テスト用: 30 ブロック上に浮く HP 1 万のボススケルトン（スラスター・回避の確認用。2026-10-06） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.SkyBossSkeleton>> SKY_BOSS_SKELETON =
            ENTITY_TYPES.register("sky_boss_skeleton", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.SkyBossSkeleton::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.99F)
                            .clientTrackingRange(10)
                            .fireImmune()
                            .build("sky_boss_skeleton"));

    // ---- クロノゲイザーの汎用機（全体仕様書 §4.1。2026-10-07 ユーザー指定「クロノゲイザーの雑魚」） ----
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.ScoutDroneEntity>> CHRONO_SCOUT_DRONE =
            ENTITY_TYPES.register("chrono_scout_drone", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.ScoutDroneEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 0.6F).clientTrackingRange(10).build("chrono_scout_drone"));
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.SpiderBotEntity>> CHRONO_SPIDER_BOT =
            ENTITY_TYPES.register("chrono_spider_bot", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.SpiderBotEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 0.6F).clientTrackingRange(8).build("chrono_spider_bot"));
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.HeavyWalkerEntity>> CHRONO_HEAVY_WALKER =
            ENTITY_TYPES.register("chrono_heavy_walker", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.HeavyWalkerEntity::new, MobCategory.MONSTER)
                            .sized(1.6F, 2.2F).clientTrackingRange(10).build("chrono_heavy_walker"));
    /** 上位機: 人のお面の姿で高威力の光線を撃つ（2026-10-07） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MaskDroneEntity>> CHRONO_MASK_DRONE =
            ENTITY_TYPES.register("chrono_mask_drone", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.MaskDroneEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 1.0F).clientTrackingRange(12).build("chrono_mask_drone"));
    /** 兵士: 腹のガトリングと黄色のフォトン剣（2026-10-07） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.TrooperEntity>> CHRONO_TROOPER =
            ENTITY_TYPES.register("chrono_trooper", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.TrooperEntity::new, MobCategory.MONSTER)
                            .sized(0.7F, 2.1F).clientTrackingRange(10).build("chrono_trooper"));
    /** 戦術兵姫のダミーちゃん（敵に狙わせる身代わりの練習台。2026-10-08） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.ValkyrieDummyEntity>> VALKYRIE_DUMMY =
            ENTITY_TYPES.register("valkyrie_dummy", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.ValkyrieDummyEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.7F).clientTrackingRange(10).build("valkyrie_dummy"));
    /** AEC 級英雄機「最光速【ルクシオン】」（全体仕様書 §4.1。2026-10-07） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.LuxionEntity>> LUXION =
            ENTITY_TYPES.register("luxion", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.LuxionEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 2.4F).clientTrackingRange(12).fireImmune().build("luxion"));
    /** クロノゲイザーの弾（ドローンのレーザー弾・戦車の砲弾） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.ChronoBolt>> CHRONO_BOLT =
            ENTITY_TYPES.register("chrono_bolt", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.ChronoBolt>of(com.automatavalkyries.entity.chrono.ChronoBolt::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(2).build("chrono_bolt"));

    /** クロノゲイザーは夜の地上に湧く（暗い所・地面の上。2026-10-07 ユーザー指定。湧く場所と重みは forge/biome_modifier/add_chrono_gazers.json） */
    @SubscribeEvent
    public static void registerSpawnPlacements(net.minecraftforge.event.entity.SpawnPlacementRegisterEvent event) {
        var ground = net.minecraft.world.entity.SpawnPlacements.Type.ON_GROUND;
        var height = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        var op = net.minecraftforge.event.entity.SpawnPlacementRegisterEvent.Operation.REPLACE;
        event.register(CHRONO_SCOUT_DRONE.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
        event.register(CHRONO_SPIDER_BOT.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
        event.register(CHRONO_HEAVY_WALKER.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
        event.register(CHRONO_MASK_DRONE.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
        event.register(CHRONO_TROOPER.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CHRONO_SCOUT_DRONE.get(), com.automatavalkyries.entity.chrono.ScoutDroneEntity.createAttributes().build());
        event.put(CHRONO_SPIDER_BOT.get(), com.automatavalkyries.entity.chrono.SpiderBotEntity.createAttributes().build());
        event.put(CHRONO_HEAVY_WALKER.get(), com.automatavalkyries.entity.chrono.HeavyWalkerEntity.createAttributes().build());
        event.put(CHRONO_MASK_DRONE.get(), com.automatavalkyries.entity.chrono.MaskDroneEntity.createAttributes().build());
        event.put(CHRONO_TROOPER.get(), com.automatavalkyries.entity.chrono.TrooperEntity.createAttributes().build());
        event.put(LUXION.get(), com.automatavalkyries.entity.chrono.LuxionEntity.createAttributes().build());
        event.put(VALKYRIE_DUMMY.get(), com.automatavalkyries.entity.ValkyrieDummyEntity.createAttributes().build());
        event.put(SKY_BOSS_SKELETON.get(), com.automatavalkyries.entity.SkyBossSkeleton.createAttributes().build());
        event.put(VALKYRIE.get(), com.automatavalkyries.entity.ValkyrieEntity.createAttributes().build());
        event.put(ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(FARTHEST_ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(PROTECT_DUMMY_ZOMBIE.get(), com.automatavalkyries.entity.ProtectDummyZombieEntity.createAttributes().build());
    }
}
