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

    // ---- スポーンエッグ ----
    public static final RegistryObject<Item> ELENOR_SPAWN_EGG = ITEMS.register("elenor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.ELENOR, 0x512da8, 0xe040fb, new Item.Properties()));
    public static final RegistryObject<Item> FARTHEST_ELENOR_SPAWN_EGG = ITEMS.register("farthest_elenor_spawn_egg", () ->
            new ForgeSpawnEggItem(ModEntities.FARTHEST_ELENOR, 0x120018, 0xb00020, new Item.Properties()));

    // ---- テスト用 ----
    public static final RegistryObject<Item> DEBUG_KILL_STICK = ITEMS.register("debug_kill_stick", () ->
            new DebugKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SUPER_KILL_STICK = ITEMS.register("super_kill_stick", () ->
            new SuperKillStickItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ULTRA_KILL_STICK = ITEMS.register("ultra_kill_stick", () ->
            new UltraKillStickItem(UltraKillStickItem.defaultProperties()));
    public static final RegistryObject<Item> TERMINUS_STICK = ITEMS.register("terminus_stick", () ->
            new com.automatavalkyries.item.TerminusStickItem(com.automatavalkyries.item.TerminusStickItem.defaultProperties()));

    // ---- プレイヤー用（2026-10-04 ユーザー指定で作り直し。中身は protect/PlayerWard） ----
    public static final RegistryObject<Item> WARD_CHARM = ITEMS.register("ward_charm", () ->
            new com.automatavalkyries.item.WardCharmItem(com.automatavalkyries.item.WardCharmItem.defaultProperties()));
    /** 無敵の護符（2026-10-04 ユーザー指定）: ダメージをすべて無効にし HP を最大に保つ（中身は protect/InvincibleCharm） */
    public static final RegistryObject<Item> INVINCIBLE_CHARM = ITEMS.register("invincible_charm", () ->
            new com.automatavalkyries.item.InvincibleCharmItem(com.automatavalkyries.item.InvincibleCharmItem.defaultProperties()));

    private static RegistryObject<Item> effect(String name, Rarity rarity, java.util.List<net.minecraft.core.particles.ParticleOptions> particles,
                                               int interval, boolean foil) {
        return ITEMS.register(name, () -> new com.automatavalkyries.item.EffectMaterialItem(new Item.Properties().rarity(rarity),
                particles, interval, foil));
    }

    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
