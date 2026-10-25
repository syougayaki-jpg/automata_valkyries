package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.item.SuperKillStickItem;
import com.automatavalkyries.item.UltraKillStickItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AutomataValkyries.MODID);

    // ---- 下位鉱石・下位金属（SPEC §7.7） ----
    public static final RegistryObject<Item> RAW_ASTRITE = simple("raw_astrite");
    public static final RegistryObject<Item> INGOT_ASTRITE = simple("ingot_astrite");
    public static final RegistryObject<Item> RAW_PHONONIUM = simple("raw_phononium");
    public static final RegistryObject<Item> INGOT_PHONONIUM = simple("ingot_phononium");
    public static final RegistryObject<Item> RAW_CERULEAS = simple("raw_ceruleas");
    public static final RegistryObject<Item> INGOT_CERULEAS = simple("ingot_ceruleas");
    public static final RegistryObject<Item> CRYSTAL_LUMINAL = ITEMS.register("crystal_luminal", () ->
            new com.automatavalkyries.item.LuminalCrystalItem(new Item.Properties()));

    // ---- 圧縮階層 Tier 2（追加拡張仕様書 §9）: ルミナルブロック 9 個 ↔ 1 個。5,000,000 FE の瞬時充填は電力の仕組みを作ってから ----
    public static final RegistryObject<Item> ORB_LUMINAL = ITEMS.register("orb_luminal", () ->
            new com.automatavalkyries.item.LuminalCrystalItem(new Item.Properties().rarity(Rarity.RARE)));

    // ---- 敵ドロップ素材（SPEC §7.7・追加拡張仕様書 §8.1）。敵を実装するまではクリエイティブで取り出すだけ ----
    public static final RegistryObject<Item> DROP_NANOMACHINE = effect("drop_nanomachine", Rarity.COMMON, ModEffects.NANO, 30, false);
    public static final RegistryObject<Item> DROP_CHRONO_SCRAP = simple("drop_chrono_scrap");
    /** 歪んだクロノ破片（§8.1 Stage 2 の解析対象。ID は仮決め） */
    public static final RegistryObject<Item> DROP_DISTORTED_CHRONO_SHARD = effect("drop_distorted_chrono_shard", Rarity.UNCOMMON, ModEffects.CHRONO, 16, false);

    // ---- 圧縮階層 Tier 2〜3（追加拡張仕様書 §9）: 9 個 ↔ 1 個。オーブ・スターは核が明滅し、粒子を出す ----
    public static final RegistryObject<Item> ORB_CHRONOS = effect("orb_chronos", Rarity.RARE, ModEffects.CHRONO, 12, false);
    public static final RegistryObject<Item> ORB_NANO = effect("orb_nano", Rarity.RARE, ModEffects.NANO, 12, false);
    public static final RegistryObject<Item> STAR_CHRONOS = effect("star_chronos", Rarity.EPIC, ModEffects.CHRONO, 6, true);
    public static final RegistryObject<Item> STAR_LUMINAL = effect("star_luminal", Rarity.EPIC, ModEffects.LUMINAL, 6, true);
    public static final RegistryObject<Item> STAR_NANO = effect("star_nano", Rarity.EPIC, ModEffects.NANO, 6, true);

    // ---- 上位合金・回路（SPEC §7.7）。レシピは暫定のクラフト（マシンができたら置き換える。SPEC §0.2-G） ----
    // 手に持つ・ドロップすると合金ごとの粒子を出す（ModEffects）。テクスチャは光の帯が走るアニメーション
    public static final RegistryObject<Item> ALLOY_LUMINAL_STERLING = effect("alloy_luminal_sterling", Rarity.UNCOMMON, ModEffects.LUMINAL_STERLING, 12, false);
    public static final RegistryObject<Item> ALLOY_CHRONO_FORTRESS = effect("alloy_chrono_fortress", Rarity.UNCOMMON, ModEffects.CHRONO_FORTRESS, 10, false);
    public static final RegistryObject<Item> ALLOY_TACHYON_ACCEL = effect("alloy_tachyon_accel", Rarity.UNCOMMON, ModEffects.TACHYON_ACCEL, 8, false);
    public static final RegistryObject<Item> ALLOY_ECLIPSE_BIOMETAL = effect("alloy_eclipse_biometal", Rarity.UNCOMMON, ModEffects.ECLIPSE_BIOMETAL, 12, false);
    public static final RegistryObject<Item> CIRCUIT_QUANTUM_TAC = effect("circuit_quantum_tac", Rarity.RARE, ModEffects.QUANTUM_CIRCUIT, 10, false);
    public static final RegistryObject<Item> INGOT_STELLACHRONODYME = effect("ingot_stellachronodyme", Rarity.EPIC, ModEffects.STELLACHRONODYME, 6, true);

    // ---- 基礎文明マシンの部品（追加拡張仕様書 §11.1.2）。連携素材を多く使う重めのレシピ ----
    public static final RegistryObject<Item> WIRE_COPPER = simple("wire_copper");
    public static final RegistryObject<Item> WIRE_GOLD = simple("wire_gold");
    public static final RegistryObject<Item> CIRCUIT_BASIC = simple("circuit_basic");
    public static final RegistryObject<Item> CIRCUIT_ADVANCED = ITEMS.register("circuit_advanced", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MOTOR = simple("motor");
    public static final RegistryObject<Item> HEATING_COIL = simple("heating_coil");
    public static final RegistryObject<Item> MACHINE_FRAME_BASIC = simple("machine_frame_basic");
    public static final RegistryObject<Item> MACHINE_FRAME_ADVANCED = ITEMS.register("machine_frame_advanced", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---- 改良型・重工業マシンの部品（追加拡張仕様書 §11.1.3〜§11.1.4）。改良型フレームまでで連携素材 14 種をすべて使う ----
    public static final RegistryObject<Item> COMPOSITE_PLATE = simple("composite_plate");
    public static final RegistryObject<Item> URANIUM_CELL = ITEMS.register("uranium_cell", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MACHINE_FRAME_IMPROVED = ITEMS.register("machine_frame_improved", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MACHINE_FRAME_HEAVY = ITEMS.register("machine_frame_heavy", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    // ---- プロトコア・初期兵装（追加拡張仕様書 §8.3・§11.1.4）。組み立て機・兵装ワークベンチで作る ----
    public static final RegistryObject<Item> CORE_PROTO = ITEMS.register("core_proto", () ->
            new com.automatavalkyries.item.ProtoCoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> WEAPON_SCRAP_SHOTGUN = ITEMS.register("weapon_scrap_shotgun", () ->
            new com.automatavalkyries.item.ScrapShotgunItem(new Item.Properties().durability(300)));
    public static final RegistryObject<Item> WEAPON_MOTOR_BLADE = ITEMS.register("weapon_motor_blade", () ->
            new com.automatavalkyries.item.MotorBladeItem(new Item.Properties()));
    public static final RegistryObject<Item> BACKPACK_STEAM_THRUSTER = ITEMS.register("backpack_steam_thruster", () ->
            new com.automatavalkyries.item.SteamThrusterItem(new Item.Properties()));
    /** 光翼の飛翔機（T3。手に持っている間、電力でクリエイティブのように飛べる。2026-10-15） */
    public static final RegistryObject<Item> TOOL_FLIGHT_WING = ITEMS.register("tool_flight_wing", () ->
            new com.automatavalkyries.item.FlightWingItem(new Item.Properties().rarity(Rarity.EPIC)));

    /** 侵食基地の生成機（クリエイティブ用。スニークで種類を切り替え、ブロックへ使うとそこに基地を置く。2026-10-19） */
    public static final RegistryObject<Item> BASE_GENERATOR = ITEMS.register("base_generator", () ->
            new com.automatavalkyries.item.BaseGeneratorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** 宿命の結び糸（クリエイティブ用。見ている 2 体を結んで戦わせる。他の MOD のボスとも。2026-10-20） */
    public static final RegistryObject<Item> FATE_THREAD = ITEMS.register("fate_thread", () ->
            new com.automatavalkyries.item.FateThreadItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** 瞬間転移の端末（見ている向きへ 10 m 先へ跳ぶ。作り方は無し。2026-10-19） */
    public static final RegistryObject<Item> BLINK_DEVICE = ITEMS.register("blink_device", () ->
            new com.automatavalkyries.item.BlinkItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 時の鍵（AEC 級 3 体の素材と時計で作る。目覚めさせた眠れる時の門から時の狭間へ。2026-10-19） */
    public static final RegistryObject<Item> CHRONO_KEY = ITEMS.register("chrono_key", () ->
            new com.automatavalkyries.item.ChronoKeyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    // ---- スポーンエッグ ----
    public static final RegistryObject<Item> ELENOR_SPAWN_EGG = ITEMS.register("elenor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ELENOR, 0x512da8, 0xe040fb, new Item.Properties()));
    public static final RegistryObject<Item> VALKYRIE_SPAWN_EGG = ITEMS.register("valkyrie_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.VALKYRIE, 0xf4f6fa, 0x3be8ff, new Item.Properties()));
    // 侵食基地の雑魚・中ボス（2026-10-15）
    public static final RegistryObject<Item> PULSE_PHALANX_SPAWN_EGG = ITEMS.register("pulse_phalanx_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.PULSE_PHALANX, 0x474c59, 0xff2a4a, new Item.Properties()));
    public static final RegistryObject<Item> SIEGE_CANNON_SPAWN_EGG = ITEMS.register("siege_cannon_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SIEGE_CANNON, 0x2c3038, 0xff8040, new Item.Properties()));
    public static final RegistryObject<Item> SIREN_EYE_SPAWN_EGG = ITEMS.register("siren_eye_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SIREN_EYE, 0x23262e, 0xff2030, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_GUARDIAN_SPAWN_EGG = ITEMS.register("chrono_guardian_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_GUARDIAN, 0x474c59, 0x5b2a9a, new Item.Properties()));
    public static final RegistryObject<Item> MIMIC_CONTAINER_SPAWN_EGG = ITEMS.register("mimic_container_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.MIMIC_CONTAINER, 0x6a5030, 0xff2a4a, new Item.Properties()));
    public static final RegistryObject<Item> CRANE_LOADER_SPAWN_EGG = ITEMS.register("crane_loader_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CRANE_LOADER, 0xd0a020, 0x23262e, new Item.Properties()));
    public static final RegistryObject<Item> OUTPOST_WATCHER_SPAWN_EGG = ITEMS.register("outpost_watcher_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.OUTPOST_WATCHER, 0x2c3038, 0xe8e4dc, new Item.Properties()));
    public static final RegistryObject<Item> FOUNDRY_MASTER_SPAWN_EGG = ITEMS.register("foundry_master_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FOUNDRY_MASTER, 0x23262e, 0xb060ff, new Item.Properties()));
    // 浮遊要塞の中ボス・雑魚（2026-10-19）
    public static final RegistryObject<Item> TIME_RULER_SPAWN_EGG = ITEMS.register("time_ruler_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.TIME_RULER, 0xf4f0e6, 0xffd166, new Item.Properties()));
    public static final RegistryObject<Item> LOST_CATASTROPHE_SPAWN_EGG = ITEMS.register("lost_catastrophe_spawn_egg", () ->   // 裏ボス 零号機（2026-10-20）
            new ForgeSpawnEggItem(ModEntities.LOST_CATASTROPHE, 0x1a0a12, 0xff2a44, new Item.Properties()));
    public static final RegistryObject<Item> BASTION_KEEPER_SPAWN_EGG = ITEMS.register("bastion_keeper_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.BASTION_KEEPER, 0xe8ecf4, 0xb060ff, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_INTERCEPTOR_SPAWN_EGG = ITEMS.register("chrono_interceptor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_INTERCEPTOR, 0x2c3038, 0x7fe4ff, new Item.Properties()));
    public static final RegistryObject<Item> DELAY_NODE_SPAWN_EGG = ITEMS.register("delay_node_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.DELAY_NODE, 0x23262e, 0xd8b0ff, new Item.Properties()));
    public static final RegistryObject<Item> PHASE_CONDENSER_SPAWN_EGG = ITEMS.register("phase_condenser_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.PHASE_CONDENSER, 0x23262e, 0xb060ff, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_SCOUT_DRONE_SPAWN_EGG = ITEMS.register("chrono_scout_drone_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_SCOUT_DRONE, 0x23262e, 0xff2a4a, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_SPIDER_BOT_SPAWN_EGG = ITEMS.register("chrono_spider_bot_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_SPIDER_BOT, 0x474c59, 0xff2a4a, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_HEAVY_WALKER_SPAWN_EGG = ITEMS.register("chrono_heavy_walker_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_HEAVY_WALKER, 0x23262e, 0x5b2a9a, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_MASK_DRONE_SPAWN_EGG = ITEMS.register("chrono_mask_drone_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_MASK_DRONE, 0xe8e4dc, 0xff2a4a, new Item.Properties()));
    public static final RegistryObject<Item> CHRONO_TROOPER_SPAWN_EGG = ITEMS.register("chrono_trooper_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.CHRONO_TROOPER, 0x474c59, 0xffe14a, new Item.Properties()));
    public static final RegistryObject<Item> VALKYRIE_DUMMY_SPAWN_EGG = ITEMS.register("valkyrie_dummy_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.VALKYRIE_DUMMY, 0xb8bcc4, 0x3be8ff, new Item.Properties()));
    public static final RegistryObject<Item> LUXION_SPAWN_EGG = ITEMS.register("luxion_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.LUXION, 0xf4f0e4, 0xff2a4a, new Item.Properties()));
    /** 英雄機の呼び声『ルクシオン』（AEC 級ボスの召喚。2026-10-07） */
    public static final RegistryObject<Item> SIGNAL_LUXION = ITEMS.register("signal_luxion", () ->
            new com.automatavalkyries.item.HeroSignalItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), ModEntities.LUXION, "luxion",
                    new org.joml.Vector3f(1.0F, 0.94F, 0.72F)));
    /** AEC 級英雄機ガリアフォート（2026-10-10）: スポーンエッグと、地面に使って呼ぶ英雄機の呼び声 */
    /** AEC 級英雄機メメントモリ（2026-10-10）: スポーンエッグと、地面に使って呼ぶ英雄機の呼び声 */
    public static final RegistryObject<Item> MEMENTO_MORI_SPAWN_EGG = ITEMS.register("memento_mori_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.MEMENTO_MORI, 0x16121e, 0x9a4aff, new Item.Properties()));
    public static final RegistryObject<Item> SIGNAL_MEMENTO_MORI = ITEMS.register("signal_memento_mori", () ->
            new com.automatavalkyries.item.HeroSignalItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), ModEntities.MEMENTO_MORI, "memento_mori",
                    new org.joml.Vector3f(0.6F, 0.29F, 1.0F)));
    public static final RegistryObject<Item> GALLIAFORT_SPAWN_EGG = ITEMS.register("galliafort_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.GALLIAFORT, 0x30343e, 0x3a6bff, new Item.Properties()));
    public static final RegistryObject<Item> SIGNAL_GALLIAFORT = ITEMS.register("signal_galliafort", () ->
            new com.automatavalkyries.item.HeroSignalItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), ModEntities.GALLIAFORT, "galliafort",
                    new org.joml.Vector3f(0.29F, 0.48F, 1.0F)));
    /** フォトン剣の欠片（クロノ・トルーパーがまれに落とす。ルクシオンの呼び出しのアイテムの材料。2026-10-07、2026-10-09 にフレーバーを足した） */
    public static final RegistryObject<Item> PHOTON_BLADE_SHARD = signalDrop("photon_blade_shard", net.minecraft.ChatFormatting.DARK_AQUA);
    /** 歪曲障壁の核片（重歩行戦車がまれに落とす。ガリアフォートの呼び出しのアイテムの材料。2026-10-15 ユーザー指定） */
    public static final RegistryObject<Item> DISTORTION_CORE_SHARD = signalDrop("distortion_core_shard", net.minecraft.ChatFormatting.AQUA);
    /** 黒死蝶の繭（仮面の観測機がまれに落とす。メメントモリの呼び出しのアイテムの材料。2026-10-15 ユーザー指定） */
    public static final RegistryObject<Item> BLACK_DEATH_COCOON = signalDrop("black_death_cocoon", net.minecraft.ChatFormatting.LIGHT_PURPLE);

    /** 英雄機の呼び出しの材料になる、雑魚のまれなドロップ: 説明（.desc）とフレーバー（.flavor、flavor の色の斜体）を出す素材 */
    private static RegistryObject<Item> signalDrop(String name, net.minecraft.ChatFormatting flavor) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()) {
            @Override
            public void appendHoverText(net.minecraft.world.item.ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level,
                                        java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.automata_valkyries." + name + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable("item.automata_valkyries." + name + ".flavor").withStyle(flavor, net.minecraft.ChatFormatting.ITALIC));
            }
        });
    }
    public static final RegistryObject<Item> SKY_BOSS_SKELETON_SPAWN_EGG = ITEMS.register("sky_boss_skeleton_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.SKY_BOSS_SKELETON, 0xc1c1c1, 0x7b2cbf, new Item.Properties()));
    public static final RegistryObject<Item> FARTHEST_ELENOR_SPAWN_EGG = ITEMS.register("farthest_elenor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FARTHEST_ELENOR, 0x120018, 0xb00020, new Item.Properties()));
    public static final RegistryObject<Item> PROTECT_DUMMY_ZOMBIE_SPAWN_EGG = ITEMS.register("protect_dummy_zombie_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.PROTECT_DUMMY_ZOMBIE, 0x00aa00, 0xffd700, new Item.Properties()));
    public static final RegistryObject<Item> AKASHIC_PROBE_SPAWN_EGG = ITEMS.register("akashic_probe_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.AKASHIC_PROBE, 0xe8ecf4, 0xff4fd8, new Item.Properties()));

    // ---- テスト用 ----
    public static final RegistryObject<Item> DEBUG_KILL_STICK = ITEMS.register("debug_kill_stick", () ->
            new DebugKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUPER_KILL_STICK = ITEMS.register("super_kill_stick", () ->
            new SuperKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ULTRA_KILL_STICK = ITEMS.register("ultra_kill_stick", () ->
            new UltraKillStickItem(UltraKillStickItem.defaultProperties()));
    public static final RegistryObject<Item> TERMINUS_STICK = ITEMS.register("terminus_stick", () ->
            new com.automatavalkyries.item.TerminusStickItem(com.automatavalkyries.item.TerminusStickItem.defaultProperties()));
    public static final RegistryObject<Item> TERMINUS_DAMAGE_LEXICON = ITEMS.register("terminus_damage_lexicon", () ->
            new com.automatavalkyries.item.AkashicLexiconItem(
                    com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.DAMAGE,
                    com.automatavalkyries.item.AkashicLexiconItem.defaultProperties()));
    public static final RegistryObject<Item> TERMINUS_INSTANT_KILL_LEXICON = ITEMS.register("terminus_instant_kill_lexicon", () ->
            new com.automatavalkyries.item.AkashicLexiconItem(
                    com.automatavalkyries.entity.akashic.AkashicResistanceAuditor.TechniqueKind.INSTANT_KILL,
                    com.automatavalkyries.item.AkashicLexiconItem.defaultProperties()));

    // ---- プレイヤー用（2026-10-04 ユーザー指定で作り直し。中身は protect/PlayerWard） ----
    /** 救済通信機（2026-10-04 ユーザー指定）: オメガコア無しで消去された戦術兵姫をバックアップから呼び出す */
    /** 指示リモコン（2026-10-06 ユーザー指定）: 自分の戦術兵姫にステップを指示する */
    public static final RegistryObject<Item> TACTICAL_REMOTE = ITEMS.register("tactical_remote", () ->
            new com.automatavalkyries.item.TacticalRemoteItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RESCUE_COMMUNICATOR = ITEMS.register("rescue_communicator", () ->
            new com.automatavalkyries.item.RescueCommunicatorItem(new Item.Properties().stacksTo(1)));
    /** 戦術兵姫用担架・緊急招集装置（2026-10-07 ユーザー指定） */
    public static final RegistryObject<Item> VALKYRIE_STRETCHER = ITEMS.register("valkyrie_stretcher", () ->
            new com.automatavalkyries.item.ValkyrieStretcherItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EMERGENCY_RECALL = ITEMS.register("emergency_recall", () ->
            new com.automatavalkyries.item.EmergencyRecallItem(new Item.Properties().stacksTo(1)));
    /** 戦術ポインター（追加拡張仕様書 §13.1）: 戦術級コードを要請する */
    public static final RegistryObject<Item> TOOL_TAC_POINTER = ITEMS.register("tool_tac_pointer", () ->
            new com.automatavalkyries.item.TacPointerItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** 同調率マキシマイザー（クリエイティブ用、2026-10-07 ユーザー指定）: 自分の兵姫の同調率を 100% に */
    public static final RegistryObject<Item> SYNC_MAXIMIZER = ITEMS.register("sync_maximizer", () ->
            new com.automatavalkyries.item.SyncMaximizerItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** 兵姫探知レーダー（2026-10-07 ユーザー指定）: 墜落ポッドの未起動の兵姫を探す。組み立て機で作る */
    public static final RegistryObject<Item> VALKYRIE_RADAR = ITEMS.register("valkyrie_radar", () ->
            new com.automatavalkyries.item.ValkyrieRadarItem(new Item.Properties().stacksTo(1)));
    /** 素体ユニット（2026-10-07 ユーザー指定「組み立て機で素体を作る」）: 地面に使うとその型の兵姫が目覚める */
    public static final RegistryObject<Item> VALKYRIE_BODY_V = valkyrieBody(com.automatavalkyries.entity.ValkyrieBody.V);
    public static final RegistryObject<Item> VALKYRIE_BODY_S = valkyrieBody(com.automatavalkyries.entity.ValkyrieBody.S);
    public static final RegistryObject<Item> VALKYRIE_BODY_H = valkyrieBody(com.automatavalkyries.entity.ValkyrieBody.H);
    public static final RegistryObject<Item> VALKYRIE_BODY_C = valkyrieBody(com.automatavalkyries.entity.ValkyrieBody.C);

    private static RegistryObject<Item> valkyrieBody(com.automatavalkyries.entity.ValkyrieBody body) {
        return ITEMS.register("valkyrie_body_" + body.name().toLowerCase(java.util.Locale.ROOT), () ->
                new com.automatavalkyries.item.ValkyrieBodyItem(body, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
    }
    /** 零号機の素体（隠しボス「マリスライズ・ロストカタストロフィー」が落とす）と零号機再起動パッチ（2026-10-20。全体仕様書 §4 の 4） */
    public static final RegistryObject<Item> ZERO_FRAME = ITEMS.register("zero_frame", () ->
            new com.automatavalkyries.item.ZeroFrameItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> ZERO_REBOOT_PATCH = ITEMS.register("item_zero_reboot_patch", () ->
            new com.automatavalkyries.item.ZeroRebootPatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    /** 退去願い（2026-10-05 ユーザー指定）: 検証用のダミーゾンビに正規の退場をしてもらう */
    public static final RegistryObject<Item> EVICTION_NOTICE = ITEMS.register("eviction_notice", () ->
            new com.automatavalkyries.item.EvictionNoticeItem(new Item.Properties().stacksTo(1)));
    /** 撤退の号令: ボスに退く演出をさせる（クリエイティブ専用・レシピ無し。2026-10-08） */
    public static final RegistryObject<Item> RETREAT_ORDER = ITEMS.register("retreat_order", () ->
            new com.automatavalkyries.item.RetreatOrderItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> WARD_CHARM = ITEMS.register("ward_charm", () ->
            new com.automatavalkyries.item.WardCharmItem(com.automatavalkyries.item.WardCharmItem.defaultProperties()));
    /** 無敵の護符（2026-10-04 ユーザー指定）: ダメージをすべて無効にし HP を最大に保つ（中身は protect/InvincibleCharm） */
    public static final RegistryObject<Item> INVINCIBLE_CHARM = ITEMS.register("invincible_charm", () ->
            new com.automatavalkyries.item.InvincibleCharmItem(com.automatavalkyries.item.InvincibleCharmItem.defaultProperties()));
    /** 特権守護の核（2026-10-04 ユーザー指定）: 魂（UUID）に Java エージェントの不可侵特権を刻印する（中身は protect/AgentPlayerProtection） */
    public static final RegistryObject<Item> PRIVILEGE_AEGIS = ITEMS.register("privilege_aegis", () ->
            new com.automatavalkyries.item.PrivilegeAegisItem(com.automatavalkyries.item.PrivilegeAegisItem.defaultProperties()));

    // ---- 兵姫の好物（追加拡張仕様書 §12.1「好物プレゼント」+5%）。兵姫へ渡す処理は兵姫側。プレイヤーもおやつとして食べられる ----
    public static final RegistryObject<Item> LUMINAL_CANDY = ITEMS.register("luminal_candy", () ->
            new com.automatavalkyries.item.ValkyrieGiftItem(new Item.Properties().food(com.automatavalkyries.item.ValkyrieGiftItem.candyFood())));
    public static final RegistryObject<Item> COOLING_ICE = ITEMS.register("cooling_ice", () ->
            new com.automatavalkyries.item.ValkyrieGiftItem(new Item.Properties().food(com.automatavalkyries.item.ValkyrieGiftItem.iceFood())));

    private static RegistryObject<Item> effect(String name, Rarity rarity, java.util.List<net.minecraft.core.particles.ParticleOptions> particles,
                                               int interval, boolean foil) {
        return ITEMS.register(name, () -> new com.automatavalkyries.item.EffectMaterialItem(new Item.Properties().rarity(rarity),
                particles, interval, foil));
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
