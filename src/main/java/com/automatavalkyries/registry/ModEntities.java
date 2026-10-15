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
                            .sized(1.4F, 4.8F).clientTrackingRange(12).fireImmune().build("luxion")); // 身長 5 ブロック（2026-10-08 ユーザー指定）
    /** ルクシオンの技の自作エフェクト（見た目だけ。光刃の波・光速突進。2026-10-08） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.LuxionFxEntity>> LUXION_FX =
            ENTITY_TYPES.register("luxion_fx", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.LuxionFxEntity>of(com.automatavalkyries.entity.chrono.LuxionFxEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(20).noSave().build("luxion_fx"));
    /** AEC 級英雄機「特殊要塞型【重護屋 / ガリアフォート】」（全体仕様書 §4.1。2026-10-10）。逆台形の大きなキャタピラ 4 つの移動要塞（高さ約 6 ブロック） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaFortEntity>> GALLIAFORT =
            ENTITY_TYPES.register("galliafort", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.GalliaFortEntity::new, MobCategory.MONSTER)
                            .sized(3.6F, 3.6F).clientTrackingRange(12).fireImmune().build("galliafort")); // 胴を大きくした（2026-10-10）。要塞もモデルを 0.7 倍にした（2026-10-10）ので 5 → 3.6
    /** AEC 級英雄機「弾幕特化【黒死蝶 / メメントモリ】」（全体仕様書 §4.1。2026-10-10）。機械仕掛けの巨大な黒い蝶（翼を広げて約 10 ブロック） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MementoMoriEntity>> MEMENTO_MORI =
            ENTITY_TYPES.register("memento_mori", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.MementoMoriEntity::new, MobCategory.MONSTER)
                            .sized(1.6F, 5.4F).clientTrackingRange(12).fireImmune().build("memento_mori"));
    /** メメントモリの黒死蝶ビット（壊せる。当たると最大 HP を削る） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MementoBitEntity>> MEMENTO_BIT =
            ENTITY_TYPES.register("memento_bit", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.MementoBitEntity>of(com.automatavalkyries.entity.chrono.MementoBitEntity::new, MobCategory.MISC)
                            .sized(0.7F, 0.5F).clientTrackingRange(10).updateInterval(1).noSave().build("memento_bit"));
    /** メメントモリの死蝶の幾何弾幕の蝶弾（クライアントでも同じように動かす） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MementoBulletEntity>> MEMENTO_BULLET =
            ENTITY_TYPES.register("memento_bullet", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.MementoBulletEntity>of(com.automatavalkyries.entity.chrono.MementoBulletEntity::new, MobCategory.MISC)
                            .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(10).noSave().build("memento_bullet"));
    /** 黒死の月の見た目（範囲・安全地帯） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MementoMoonEntity>> MEMENTO_MOON =
            ENTITY_TYPES.register("memento_moon", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.MementoMoonEntity>of(com.automatavalkyries.entity.chrono.MementoMoonEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(12).updateInterval(20).noSave().build("memento_moon"));
    /** ガリアフォートのミサイル（撃ち上げてから降らせる） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaMissileEntity>> GALLIA_MISSILE =
            ENTITY_TYPES.register("gallia_missile", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.GalliaMissileEntity>of(com.automatavalkyries.entity.chrono.GalliaMissileEntity::new, MobCategory.MISC)
                            .sized(0.4F, 0.4F).clientTrackingRange(10).updateInterval(1).noSave().build("gallia_missile"));
    /** ガリアフォートの副砲の徹甲弾（2026-10-10「副砲とミサイルの弾を自作して搭載」。飛び方はクロノゲイザーの弾と同じ、見た目だけ専用） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaShellEntity>> GALLIA_SHELL =
            ENTITY_TYPES.register("gallia_shell", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.GalliaShellEntity>of(com.automatavalkyries.entity.chrono.GalliaShellEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(2).build("gallia_shell"));
    /** 穿孔破城砲の光の束（見た目だけ。2026-10-10「穿孔破城砲も自作して豪華にして」） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaSiegeBeamEntity>> GALLIA_SIEGE_BEAM =
            ENTITY_TYPES.register("gallia_siege_beam", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.GalliaSiegeBeamEntity>of(com.automatavalkyries.entity.chrono.GalliaSiegeBeamEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(12).updateInterval(20).noSave().build("gallia_siege_beam"));
    /** 歪曲破界砲の着弾地点の目印（見た目だけ。景色を一瞬歪ませる。2026-10-10） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaRiftEntity>> GALLIA_RIFT =
            ENTITY_TYPES.register("gallia_rift", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.GalliaRiftEntity>of(com.automatavalkyries.entity.chrono.GalliaRiftEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(12).updateInterval(20).noSave().build("gallia_rift"));
    /** 踏みつけ・重拳の衝撃の見た目（2026-10-10「踏みつけの衝撃を自作して豪華にして」） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.GalliaQuakeEntity>> GALLIA_QUAKE =
            ENTITY_TYPES.register("gallia_quake", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.GalliaQuakeEntity>of(com.automatavalkyries.entity.chrono.GalliaQuakeEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).noSave().build("gallia_quake"));
    /** クロノゲイザーの弾（ドローンのレーザー弾・戦車の砲弾） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.ChronoBolt>> CHRONO_BOLT =
            ENTITY_TYPES.register("chrono_bolt", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.ChronoBolt>of(com.automatavalkyries.entity.chrono.ChronoBolt::new, MobCategory.MISC)
                            .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(2).build("chrono_bolt"));

    /** 侵食基地の対空タレット（拡張仕様書 §10.2。2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.PulsePhalanxEntity>> PULSE_PHALANX =
            ENTITY_TYPES.register("pulse_phalanx", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.PulsePhalanxEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 1.6F).clientTrackingRange(10).build("pulse_phalanx"));
    /** 侵食基地の曲射砲（§10.2） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.SiegeCannonEntity>> SIEGE_CANNON =
            ENTITY_TYPES.register("siege_cannon", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.SiegeCannonEntity::new, MobCategory.MONSTER)
                            .sized(1.6F, 1.8F).clientTrackingRange(10).build("siege_cannon"));
    /** 侵食基地の警報ドローン（§10.2） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.SirenEyeEntity>> SIREN_EYE =
            ENTITY_TYPES.register("siren_eye", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.SirenEyeEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 0.9F).clientTrackingRange(12).build("siren_eye"));
    /** 侵食基地を巡回する 4 本脚の大型機（§10.2） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.ChronoGuardianEntity>> CHRONO_GUARDIAN =
            ENTITY_TYPES.register("chrono_guardian", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.ChronoGuardianEntity::new, MobCategory.MONSTER)
                            .sized(1.8F, 2.4F).clientTrackingRange(10).build("chrono_guardian"));
    /** 物資のコンテナに化ける罠（2026-10-15 ユーザー指定） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MimicContainerEntity>> MIMIC_CONTAINER =
            ENTITY_TYPES.register("mimic_container", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.MimicContainerEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 1.2F).clientTrackingRange(10).build("mimic_container"));
    /** 物資倉庫の中ボス（2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.CraneLoaderEntity>> CRANE_LOADER =
            ENTITY_TYPES.register("crane_loader", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.CraneLoaderEntity::new, MobCategory.MONSTER)
                            .sized(2.6F, 3.4F).clientTrackingRange(12).build("crane_loader"));
    /** 基地（クロノ・アウトリポスト）の中ボス（2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.OutpostWatcherEntity>> OUTPOST_WATCHER =
            ENTITY_TYPES.register("outpost_watcher", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.OutpostWatcherEntity::new, MobCategory.MONSTER)
                            .sized(2.0F, 4.2F).clientTrackingRange(12).build("outpost_watcher"));
    /** 工場（ディメンション・ファウンドリ）の中ボス（2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.FoundryMasterEntity>> FOUNDRY_MASTER =
            ENTITY_TYPES.register("foundry_master", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.FoundryMasterEntity::new, MobCategory.MONSTER)
                            .sized(3.0F, 4.0F).clientTrackingRange(12).build("foundry_master"));
    /** 前哨司令機の盾ドローン（2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.ShieldDroneEntity>> SHIELD_DRONE =
            ENTITY_TYPES.register("shield_drone", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.ShieldDroneEntity::new, MobCategory.MONSTER)
                            .sized(0.8F, 0.6F).clientTrackingRange(10).build("shield_drone"));
    /** 位相アンカーのサブ・コンデンサー（§10.2。工場長機のバリアの供給源） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.PhaseCondenserEntity>> PHASE_CONDENSER =
            ENTITY_TYPES.register("phase_condenser", () ->
                    EntityType.Builder.of(com.automatavalkyries.entity.chrono.PhaseCondenserEntity::new, MobCategory.MONSTER)
                            .sized(1.0F, 3.0F).clientTrackingRange(10).build("phase_condenser"));
    /** 山なりの砲弾（曲射砲・前哨司令機の砲撃要請。2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.MortarShellEntity>> MORTAR_SHELL =
            ENTITY_TYPES.register("mortar_shell", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.MortarShellEntity>of(com.automatavalkyries.entity.chrono.MortarShellEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).build("mortar_shell"));
    /** 荷役機が投げる・落とすコンテナ（2026-10-15） */
    public static final RegistryObject<EntityType<com.automatavalkyries.entity.chrono.CargoContainerEntity>> CARGO_CONTAINER =
            ENTITY_TYPES.register("cargo_container", () ->
                    EntityType.Builder.<com.automatavalkyries.entity.chrono.CargoContainerEntity>of(com.automatavalkyries.entity.chrono.CargoContainerEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(2).build("cargo_container"));

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
        // 侵食基地の中に湧く物（構造物の湧きの上書き。worldgen/structure/*.json の spawn_overrides）
        event.register(CHRONO_GUARDIAN.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
        event.register(SIREN_EYE.get(), ground, height, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules, op);
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CHRONO_SCOUT_DRONE.get(), com.automatavalkyries.entity.chrono.ScoutDroneEntity.createAttributes().build());
        event.put(CHRONO_SPIDER_BOT.get(), com.automatavalkyries.entity.chrono.SpiderBotEntity.createAttributes().build());
        event.put(CHRONO_HEAVY_WALKER.get(), com.automatavalkyries.entity.chrono.HeavyWalkerEntity.createAttributes().build());
        event.put(CHRONO_MASK_DRONE.get(), com.automatavalkyries.entity.chrono.MaskDroneEntity.createAttributes().build());
        event.put(CHRONO_TROOPER.get(), com.automatavalkyries.entity.chrono.TrooperEntity.createAttributes().build());
        event.put(LUXION.get(), com.automatavalkyries.entity.chrono.LuxionEntity.createAttributes().build());
        event.put(GALLIAFORT.get(), com.automatavalkyries.entity.chrono.GalliaFortEntity.createAttributes().build());
        event.put(MEMENTO_MORI.get(), com.automatavalkyries.entity.chrono.MementoMoriEntity.createAttributes().build());
        event.put(VALKYRIE_DUMMY.get(), com.automatavalkyries.entity.ValkyrieDummyEntity.createAttributes().build());
        event.put(SKY_BOSS_SKELETON.get(), com.automatavalkyries.entity.SkyBossSkeleton.createAttributes().build());
        event.put(VALKYRIE.get(), com.automatavalkyries.entity.ValkyrieEntity.createAttributes().build());
        event.put(ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(FARTHEST_ELENOR.get(), ElenorEntity.createAttributes().build());
        event.put(PROTECT_DUMMY_ZOMBIE.get(), com.automatavalkyries.entity.ProtectDummyZombieEntity.createAttributes().build());
        event.put(PULSE_PHALANX.get(), com.automatavalkyries.entity.chrono.PulsePhalanxEntity.createAttributes().build());
        event.put(SIEGE_CANNON.get(), com.automatavalkyries.entity.chrono.SiegeCannonEntity.createAttributes().build());
        event.put(SIREN_EYE.get(), com.automatavalkyries.entity.chrono.SirenEyeEntity.createAttributes().build());
        event.put(CHRONO_GUARDIAN.get(), com.automatavalkyries.entity.chrono.ChronoGuardianEntity.createAttributes().build());
        event.put(MIMIC_CONTAINER.get(), com.automatavalkyries.entity.chrono.MimicContainerEntity.createAttributes().build());
        event.put(CRANE_LOADER.get(), com.automatavalkyries.entity.chrono.CraneLoaderEntity.createAttributes().build());
        event.put(OUTPOST_WATCHER.get(), com.automatavalkyries.entity.chrono.OutpostWatcherEntity.createAttributes().build());
        event.put(FOUNDRY_MASTER.get(), com.automatavalkyries.entity.chrono.FoundryMasterEntity.createAttributes().build());
        event.put(SHIELD_DRONE.get(), com.automatavalkyries.entity.chrono.ShieldDroneEntity.createAttributes().build());
        event.put(PHASE_CONDENSER.get(), com.automatavalkyries.entity.chrono.PhaseCondenserEntity.createAttributes().build());
    }
}
