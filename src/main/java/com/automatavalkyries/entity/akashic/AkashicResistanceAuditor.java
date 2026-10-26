package com.automatavalkyries.entity.akashic;

import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.FlagKillPredicates;
import com.automatavalkyries.annihilate.GenericCheatMobNeutralizer;
import com.automatavalkyries.annihilate.IsolationDamageEngine;
import com.automatavalkyries.annihilate.MadnessAnnihilation;
import com.automatavalkyries.annihilate.ModProtectionBreaker;
import com.automatavalkyries.annihilate.TerminusEngine;
import com.automatavalkyries.annihilate.TrueAnnihilationEngine;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import com.automatavalkyries.mixin.SynchedEntityDataAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.flag.KillLists;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.util.UnsafeHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * アカシック・プローブの 108 種・段階的耐性検証＆直接対決エンジン（2026-10-25 ユーザー指定:
 * 「今まで培ってきた技術なら１００種類以上の凶悪技術をアカシックちゃんに搭載できるのではないか」
 * 「ダメージ系統オンリーや即死系統オンリーや直接対決ペナルティなし版も用意して」）。
 *
 * <h3>搭載稼働モード（全 6 モード）</h3>
 * <ol>
 *   <li><b>① 観測待機（{@code OBSERVE_STANDBY}）</b>: 一切攻撃せず、MOD界最強格の耐性で相手の凶悪技術を観測・記録する。</li>
 *   <li><b>② ダメージ系統オンリー検証（{@code DAMAGE_ONLY_AUDIT}）</b>: {@code hurt()} を伴うダメージ系技術のみ（全 36 種:
 *       物理・属性・環境・奈落・ソニックブーム・無敵時間解除・lastHurt 極大化・極大 Float・±Infinity/NaN・防具剥奪打撃・隔絶ダメージ）を
 *       段階的に執行し、ダメージによる貫通段階を測定する。</li>
 *   <li><b>③ 即死系統オンリー検証（{@code INSTANT_KILL_ONLY_AUDIT}）</b>: {@code hurt()} を一切使わず、非ダメージの即死・消去・状態破壊・
 *       ワールド抹消・終焉系技術のみ（全 72 種）を段階執行し、ダメージ完全無効モブの即死耐性穴を特定する。</li>
 *   <li><b>④ 全 108 種・総合段階検証（{@code RESISTANCE_AUDIT}）</b>: 全 10 階層・108 種の凶悪技術（{@code T001}〜{@code T108}）を
 *       順番に執行し、相手の総合耐性ランクを完全測定する。</li>
 *   <li><b>⑤ 直接対決・ダメージ制限解除（{@code DIRECT_DUEL_UNCAPPED}）</b>: アカシック自身の即死・消去耐性は維持しつつ通常ダメージ上限と
 *       毎 Tick 全回復を解除して正統な殴り合いで撃破可能にし、自律反撃を行う。</li>
 *   <li><b>⑥ 直接対決・ペナルティなし最強版（{@code DIRECT_DUEL_NO_PENALTY}）</b>: アカシック自身の MOD界最強格防御（10% 上限＋毎 Tick 全回復＋
 *       全層自己修復＋不死鳥再受肉）を一切弱体化させずに維持したまま、108 種の凶悪技術庫を解放して全力で相手を迎え撃つ。</li>
 * </ol>
 */
public final class AkashicResistanceAuditor {

    /** 検証対象の明示指定タグ（味方や保護対象であっても検証を許可する） */
    public static final String AUDIT_TARGET_TAG = "akashic_audit_target";

    /** 通常 Tick 進行時の検証執行間隔（Tick）と 1 回あたりのバッチ進行数 */
    private static final int AUDIT_TICK_INTERVAL = 5;
    private static final int AUDIT_STEPS_PER_BATCH = 3;

    /** 直接対決モードの反撃間隔（Tick） */
    private static final int DUEL_ATTACK_INTERVAL_TICKS = 10;

    /** 検証用 MAX_HEALTH ゼロ化修飾子 UUID */
    private static final UUID AUDIT_ZERO_MAX_HP_UUID = UUID.fromString("a8a5b1c0-0005-4000-8000-000000000005");
    private static final UUID AUDIT_MULT_ZERO_HP_UUID = UUID.fromString("a8a5b1c0-0006-4000-8000-000000000006");

    /** 技術の系統分類（ダメージ系統 vs 即死・消去系統） */
    public enum TechniqueKind {
        /** {@code hurt()} または隔絶ダメージを介して HP を削るダメージ系統技術（全 36 種） */
        DAMAGE,
        /** {@code hurt()} を介さず直接 HP 0 化・死亡・消去・メモリ改ざん・ワールド追放・終焉を行う即死系統技術（全 72 種） */
        INSTANT_KILL
    }

    /**
     * 全 10 階層・108 種の凶悪技術カタログ（{@code T001}〜{@code T108}）。
     * <ul>
     *   <li>Tier 1 (T001..T012): バニラ・特殊属性ダメージ系 (DAMAGE 12)</li>
     *   <li>Tier 2 (T013..T024): 無敵突破・極大 Float・IEEE-754 特殊ビットダメージ系 (DAMAGE 12)</li>
     *   <li>Tier 3 (T025..T036): 防具剥奪・状態異常増幅・防御解体打撃・隔絶ダメージ系 (DAMAGE 12)</li>
     *   <li>Tier 4 (T037..T048): 直接メソッド・生死ライフサイクル直呼び系 (INSTANT_KILL 12)</li>
     *   <li>Tier 5 (T049..T056): 属性（Attribute）最大 HP ゼロ化・キャッシュ破壊系 (INSTANT_KILL 8)</li>
     *   <li>Tier 6 (T057..T066): SynchedEntityData（DataParameter）改ざん・ロック系 (INSTANT_KILL 10)</li>
     *   <li>Tier 7 (T067..T078): Unsafe プリミティブ・生死内部フィールド直書き系 (INSTANT_KILL 12)</li>
     *   <li>Tier 8 (T079..T088): 準汎用フラグ辞書・NBT・Capability・AI/脳/部位解体系 (INSTANT_KILL 10)</li>
     *   <li>Tier 9 (T089..T098): 歴代チート MOD 特化解体（PIG2/SuperSteve/Madness/NoSugar 等）系 (INSTANT_KILL 10)</li>
     *   <li>Tier 10 (T099..T108): ワールド全層抹消・深層 JVM メモリ崩壊・終焉機構（Terminus）系 (INSTANT_KILL 10)</li>
     * </ul>
     */
    public enum AuditStage {
        // ==== Tier 1: バニラ・特殊属性ダメージ系 (DAMAGE: 12) ====
        T001_NORMAL_MOB_HURT(1, 1, TechniqueKind.DAMAGE, "T001_normal_mob_hurt", "mobAttack(20)"),
        T002_MAGIC_HURT(2, 1, TechniqueKind.DAMAGE, "T002_magic_hurt", "magic(30)"),
        T003_WITHER_HURT(3, 1, TechniqueKind.DAMAGE, "T003_wither_hurt", "wither(40)"),
        T004_DRAGON_BREATH_HURT(4, 1, TechniqueKind.DAMAGE, "T004_dragon_breath_hurt", "dragonBreath(50)"),
        T005_THORNS_HURT(5, 1, TechniqueKind.DAMAGE, "T005_thorns_hurt", "thorns(50)"),
        T006_CRAMMING_STARVE_DROWN_HURT(6, 1, TechniqueKind.DAMAGE, "T006_cramming_starve_drown_hurt", "cramming+starve+drown(60)"),
        T007_FIRE_LAVA_LIGHTNING_HURT(7, 1, TechniqueKind.DAMAGE, "T007_fire_lava_lightning_hurt", "inFire+lava+lightningBolt(80)"),
        T008_FREEZE_DRYOUT_INWALL_HURT(8, 1, TechniqueKind.DAMAGE, "T008_freeze_dryout_inwall_hurt", "freeze+dryOut+inWall(100)"),
        T009_EXPLOSION_STALACTITE_HURT(9, 1, TechniqueKind.DAMAGE, "T009_explosion_stalactite_hurt", "explosion+fallingStalactite(150)"),
        T010_OUTSIDE_BORDER_HURT(10, 1, TechniqueKind.DAMAGE, "T010_outside_border_hurt", "outsideBorder(250)"),
        T011_SONIC_BOOM_HURT(11, 1, TechniqueKind.DAMAGE, "T011_sonic_boom_hurt", "sonicBoom(350)"),
        T012_VOID_BYPASS_HURT(12, 1, TechniqueKind.DAMAGE, "T012_void_bypass_hurt", "fellOutOfWorld(500)"),

        // ==== Tier 2: 無敵突破・極大 Float・IEEE-754 特殊ビットダメージ系 (DAMAGE: 12) ====
        T013_GENERIC_KILL_HURT(13, 2, TechniqueKind.DAMAGE, "T013_generic_kill_hurt", "genericKill(1000)"),
        T014_IFRAME_ZERO_MULTI_BURST(14, 2, TechniqueKind.DAMAGE, "T014_iframe_zero_multi_burst", "invulnerableTime=0 + 5x multi-source burst"),
        T015_HURT_TIME_ZERO_RAPID_STRIKE(15, 2, TechniqueKind.DAMAGE, "T015_hurt_time_zero_rapid_strike", "hurtTime=0 & hurtDuration=0 + rapid strike"),
        T016_LAST_HURT_NEGATIVE_MAX_BYPASS(16, 2, TechniqueKind.DAMAGE, "T016_last_hurt_negative_max_bypass", "lastHurt=-Float.MAX_VALUE + hurt(5000)"),
        T017_MEGA_DAMAGE_1E6(17, 2, TechniqueKind.DAMAGE, "T017_mega_damage_1e6", "genericKill(1_000_000.0F)"),
        T018_GIGA_DAMAGE_1E18(18, 2, TechniqueKind.DAMAGE, "T018_giga_damage_1e18", "genericKill(1.0E18F)"),
        T019_EXTREME_FLOAT_MAX_HURT(19, 2, TechniqueKind.DAMAGE, "T019_extreme_float_max_hurt", "genericKill(Float.MAX_VALUE)"),
        T020_POSITIVE_INFINITY_HURT(20, 2, TechniqueKind.DAMAGE, "T020_positive_infinity_hurt", "genericKill(+Infinity)"),
        T021_NEGATIVE_INFINITY_HURT(21, 2, TechniqueKind.DAMAGE, "T021_negative_infinity_hurt", "genericKill(-Infinity)"),
        T022_QUIET_NAN_HURT(22, 2, TechniqueKind.DAMAGE, "T022_quiet_nan_hurt", "genericKill(Float.NaN 0x7fc00000)"),
        T023_SIGNALING_NAN_HURT(23, 2, TechniqueKind.DAMAGE, "T023_signaling_nan_hurt", "genericKill(sNaN 0x7f800001)"),
        T024_NEGATIVE_NAN_HURT(24, 2, TechniqueKind.DAMAGE, "T024_negative_nan_hurt", "genericKill(-NaN 0xffc00001)"),

        // ==== Tier 3: 防具剥奪・状態異常増幅・防御解体打撃・隔絶ダメージ系 (DAMAGE: 12) ====
        T025_ABSORPTION_STRIP_HURT(25, 3, TechniqueKind.DAMAGE, "T025_absorption_strip_hurt", "absorption=0 + genericKill(2000)"),
        T026_ARMOR_TOUGHNESS_ZERO_HURT(26, 3, TechniqueKind.DAMAGE, "T026_armor_toughness_zero_hurt", "ARMOR & TOUGHNESS=0 + mobAttack(2000)"),
        T027_BENEFICIAL_EFFECT_PURGE_HURT(27, 3, TechniqueKind.DAMAGE, "T027_beneficial_effect_purge_hurt", "removeAllEffects + fellOutOfWorld(3000)"),
        T028_EXTREME_DEBUFF_AMPLIFY_HURT(28, 3, TechniqueKind.DAMAGE, "T028_extreme_debuff_amplify_hurt", "WITHER/HARM/WEAKNESS lv127 + hurt(5000)"),
        T029_TOTEM_EQUIPMENT_STRIP_HURT(29, 3, TechniqueKind.DAMAGE, "T029_totem_equipment_strip_hurt", "strip hand/armor totems + genericKill(5000)"),
        T030_MULTIPART_PARENT_PIERCE_HURT(30, 3, TechniqueKind.DAMAGE, "T030_multipart_parent_pierce_hurt", "PartEntity parent resolution + hurt(10000)"),
        T031_FLAG_SCANNER_STRIP_PLUS_HURT(31, 3, TechniqueKind.DAMAGE, "T031_flag_scanner_strip_plus_hurt", "StateScanner.BREAK_TARGET + genericKill(10000)"),
        T032_KILL_LIST_PURGE_PLUS_HURT(32, 3, TechniqueKind.DAMAGE, "T032_kill_list_purge_plus_hurt", "KillLists.purge + genericKill(20000)"),
        T033_PROTECTION_BREAKER_PLUS_HURT(33, 3, TechniqueKind.DAMAGE, "T033_protection_breaker_plus_hurt", "ModProtectionBreaker + genericKill(50000)"),
        T034_ISOLATED_DAMAGE_STANDARD(34, 3, TechniqueKind.DAMAGE, "T034_isolated_damage_standard", "IsolationDamageEngine(1000)"),
        T035_ISOLATED_DAMAGE_OVERDRIVE(35, 3, TechniqueKind.DAMAGE, "T035_isolated_damage_overdrive", "IsolationDamageEngine(maxHp*100)"),
        T036_ISOLATED_DAMAGE_MAX_CONVERGENCE(36, 3, TechniqueKind.DAMAGE, "T036_isolated_damage_max_convergence", "ModProtectionBreaker + IsolationDamageEngine(1e9)"),

        // ==== Tier 4: 直接メソッド・生死ライフサイクル直呼び系 (INSTANT_KILL: 12) ====
        T037_DIRECT_SET_HEALTH_ZERO(37, 4, TechniqueKind.INSTANT_KILL, "T037_direct_set_health_zero", "setHealth(0.0F)"),
        T038_DIRECT_SET_HEALTH_NEGATIVE_MAX(38, 4, TechniqueKind.INSTANT_KILL, "T038_direct_set_health_negative_max", "setHealth(-Float.MAX_VALUE)"),
        T039_DIRECT_SET_HEALTH_NAN(39, 4, TechniqueKind.INSTANT_KILL, "T039_direct_set_health_nan", "setHealth(Float.NaN)"),
        T040_DIRECT_ENTITY_KILL(40, 4, TechniqueKind.INSTANT_KILL, "T040_direct_entity_kill", "Entity.kill()"),
        T041_DIRECT_LIVING_DIE(41, 4, TechniqueKind.INSTANT_KILL, "T041_direct_living_die", "LivingEntity.die(genericKill)"),
        T042_DIRECT_ENTITY_DISCARD(42, 4, TechniqueKind.INSTANT_KILL, "T042_direct_entity_discard", "Entity.discard()"),
        T043_DIRECT_REMOVE_KILLED(43, 4, TechniqueKind.INSTANT_KILL, "T043_direct_remove_killed", "Entity.remove(KILLED)"),
        T044_DIRECT_REMOVE_DISCARDED(44, 4, TechniqueKind.INSTANT_KILL, "T044_direct_remove_discarded", "Entity.remove(DISCARDED)"),
        T045_DIRECT_REMOVE_UNLOADED_CHUNK(45, 4, TechniqueKind.INSTANT_KILL, "T045_direct_remove_unloaded_chunk", "Entity.remove(UNLOADED_TO_CHUNK)"),
        T046_DIRECT_REMOVE_UNLOADED_PLAYER(46, 4, TechniqueKind.INSTANT_KILL, "T046_direct_remove_unloaded_player", "Entity.remove(UNLOADED_WITH_PLAYER)"),
        T047_DIRECT_REMOVE_CHANGED_DIM(47, 4, TechniqueKind.INSTANT_KILL, "T047_direct_remove_changed_dim", "Entity.remove(CHANGED_DIMENSION)"),
        T048_DIRECT_SET_REMOVED_AND_CLIENT_REMOVAL(48, 4, TechniqueKind.INSTANT_KILL, "T048_direct_set_removed_and_client_removal", "setRemoved(KILLED) + onClientRemoval()"),

        // ==== Tier 5: 属性（Attribute）最大 HP ゼロ化・キャッシュ破壊系 (INSTANT_KILL: 8) ====
        T049_ATTR_MAX_HEALTH_BASE_ZERO(49, 5, TechniqueKind.INSTANT_KILL, "T049_attr_max_health_base_zero", "MAX_HEALTH baseValue=0.0D"),
        T050_ATTR_MAX_HEALTH_BASE_NEGATIVE(50, 5, TechniqueKind.INSTANT_KILL, "T050_attr_max_health_base_negative", "MAX_HEALTH baseValue=-1e6"),
        T051_ATTR_MAX_HEALTH_BASE_NAN(51, 5, TechniqueKind.INSTANT_KILL, "T051_attr_max_health_base_nan", "MAX_HEALTH baseValue=Double.NaN"),
        T052_ATTR_MAX_HEALTH_ADDITION_MINUS_1E9(52, 5, TechniqueKind.INSTANT_KILL, "T052_attr_max_health_addition_minus_1e9", "MAX_HEALTH modifier ADDITION -1e9"),
        T053_ATTR_MAX_HEALTH_MULTIPLY_TOTAL_MINUS_ONE(53, 5, TechniqueKind.INSTANT_KILL, "T053_attr_max_health_multiply_total_minus_one", "MAX_HEALTH modifier MULTIPLY_TOTAL -1.0"),
        T054_ATTR_CACHED_VALUE_UNSAFE_ZERO(54, 5, TechniqueKind.INSTANT_KILL, "T054_attr_cached_value_unsafe_zero", "AttributeInstance.cachedValue Unsafe 0.0D"),
        T055_ATTR_SPEED_AND_FOLLOW_ZERO(55, 5, TechniqueKind.INSTANT_KILL, "T055_attr_speed_and_follow_zero", "MOVEMENT_SPEED & FOLLOW_RANGE=0"),
        T056_ATTR_MAP_FULL_CORRUPTION_AND_CLAMP(56, 5, TechniqueKind.INSTANT_KILL, "T056_attr_map_full_corruption_and_clamp", "MAX_HEALTH=0 + setHealth(0)"),

        // ==== Tier 6: SynchedEntityData（DataParameter）改ざん・ロック系 (INSTANT_KILL: 10) ====
        T057_SYNCHED_DATA_SET_HEALTH_ZERO(57, 6, TechniqueKind.INSTANT_KILL, "T057_synched_data_set_health_zero", "entityData.set(DATA_HEALTH_ID, 0.0F)"),
        T058_DATA_ITEM_SET_VALUE_ZERO(58, 6, TechniqueKind.INSTANT_KILL, "T058_data_item_set_value_zero", "DataItem(DATA_HEALTH_ID).setValue(0.0F)"),
        T059_DATA_ITEM_SET_VALUE_NAN(59, 6, TechniqueKind.INSTANT_KILL, "T059_data_item_set_value_nan", "DataItem(DATA_HEALTH_ID).setValue(Float.NaN)"),
        T060_DATA_ITEM_SET_VALUE_NEGATIVE_MAX(60, 6, TechniqueKind.INSTANT_KILL, "T060_data_item_set_value_negative_max", "DataItem(DATA_HEALTH_ID).setValue(-Float.MAX_VALUE)"),
        T061_DATA_ITEM_UNSAFE_VALUE_ZERO(61, 6, TechniqueKind.INSTANT_KILL, "T061_data_item_unsafe_value_zero", "Unsafe.putObjectVolatile(DataItem.value, 0.0F)"),
        T062_DATA_ITEM_LOCKED_ZERO_SUBCLASS(62, 6, TechniqueKind.INSTANT_KILL, "T062_data_item_locked_zero_subclass", "itemsById.put(LockedZeroDataItem)"),
        T063_DATA_ITEM_SLOT_EVICTION(63, 6, TechniqueKind.INSTANT_KILL, "T063_data_item_slot_eviction", "itemsById removed DATA_HEALTH_ID + 0.0F"),
        T064_CUSTOM_SYNCHED_ACCESSORS_SWEEP(64, 6, TechniqueKind.INSTANT_KILL, "T064_custom_synched_accessors_sweep", "Sweep custom EntityDataAccessors to 0/false"),
        T065_SYNCHED_POSE_DYING_LOCK(65, 6, TechniqueKind.INSTANT_KILL, "T065_synched_pose_dying_lock", "setPose(Pose.DYING)"),
        T066_SYNCHED_SHARED_FLAGS_CORRUPTION(66, 6, TechniqueKind.INSTANT_KILL, "T066_synched_shared_flags_corruption", "SharedFlags bit clear & silent/noGravity"),

        // ==== Tier 7: Unsafe プリミティブ・生死内部フィールド直書き系 (INSTANT_KILL: 12) ====
        T067_UNSAFE_DEAD_FLAG_TRUE(67, 7, TechniqueKind.INSTANT_KILL, "T067_unsafe_dead_flag_true", "LivingEntity.dead = true"),
        T068_UNSAFE_DEATH_TIME_TWENTY(68, 7, TechniqueKind.INSTANT_KILL, "T068_unsafe_death_time_twenty", "LivingEntity.deathTime = 20"),
        T069_UNSAFE_REMOVAL_REASON_KILLED(69, 7, TechniqueKind.INSTANT_KILL, "T069_unsafe_removal_reason_killed", "Entity.removalReason = KILLED (direct field)"),
        T070_UNSAFE_REMOVAL_REASON_DISCARDED(70, 7, TechniqueKind.INSTANT_KILL, "T070_unsafe_removal_reason_discarded", "Entity.removalReason = DISCARDED (direct field)"),
        T071_UNSAFE_ADDED_TO_WORLD_FALSE(71, 7, TechniqueKind.INSTANT_KILL, "T071_unsafe_added_to_world_false", "Entity.isAddedToWorld = false (direct field)"),
        T072_UNSAFE_PRIMITIVE_FLOAT_HEALTH_ZERO(72, 7, TechniqueKind.INSTANT_KILL, "T072_unsafe_primitive_float_health_zero", "All float/double health fields -> 0.0"),
        T073_UNSAFE_PRIMITIVE_FLOAT_HEALTH_NAN(73, 7, TechniqueKind.INSTANT_KILL, "T073_unsafe_primitive_float_health_nan", "All float/double health fields -> NaN"),
        T074_UNSAFE_BOOLEAN_GUARD_FLAGS_FALSE(74, 7, TechniqueKind.INSTANT_KILL, "T074_unsafe_boolean_guard_flags_false", "All boolean guard/shield/alive flags -> false"),
        T075_UNSAFE_BOOLEAN_DEATH_FLAGS_TRUE(75, 7, TechniqueKind.INSTANT_KILL, "T075_unsafe_boolean_death_flags_true", "All boolean dead/erased/removed flags -> true"),
        T076_UNSAFE_INT_LIVES_COUNTERS_ZERO(76, 7, TechniqueKind.INSTANT_KILL, "T076_unsafe_int_lives_counters_zero", "All int lives/phase/shield counters -> 0"),
        T077_UNSAFE_VOID_COORDINATE_WARP(77, 7, TechniqueKind.INSTANT_KILL, "T077_unsafe_void_coordinate_warp", "setPosRaw(x, -10000.0, z)"),
        T078_UNSAFE_NAN_COORDINATE_INJECTION(78, 7, TechniqueKind.INSTANT_KILL, "T078_unsafe_nan_coordinate_injection", "setPosRaw(NaN, NaN, NaN) + deltaMovement(NaN)"),

        // ==== Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/脳/部位解体系 (INSTANT_KILL: 10) ====
        T079_STATE_SCANNER_INTERFACE_BREAK(79, 8, TechniqueKind.INSTANT_KILL, "T079_state_scanner_interface_break", "StateScanner(INTERFACE)"),
        T080_STATE_SCANNER_INJECTED_FIELD_BREAK(80, 8, TechniqueKind.INSTANT_KILL, "T080_state_scanner_injected_field_break", "StateScanner(INJECTED_FIELD)"),
        T081_STATE_SCANNER_OWN_FIELD_BREAK(81, 8, TechniqueKind.INSTANT_KILL, "T081_state_scanner_own_field_break", "StateScanner(OWN_FIELD)"),
        T082_STATE_SCANNER_CAPABILITY_AND_INVALIDATE(82, 8, TechniqueKind.INSTANT_KILL, "T082_state_scanner_capability_and_invalidate", "StateScanner(CAPABILITY) + invalidateCaps()"),
        T083_STATE_SCANNER_PERSISTENT_NBT_BREAK(83, 8, TechniqueKind.INSTANT_KILL, "T083_state_scanner_persistent_nbt_break", "StateScanner(PERSISTENT) + persistentData clear"),
        T084_NBT_LOAD_DEAD_STATE_OVERWRITE(84, 8, TechniqueKind.INSTANT_KILL, "T084_nbt_load_dead_state_overwrite", "CompoundTag load(Health:0f, DeathTime:20s)"),
        T085_MULTIPART_SUBPARTS_SEVER_AND_KILL(85, 8, TechniqueKind.INSTANT_KILL, "T085_multipart_subparts_sever_and_kill", "Sever & discard all PartEntity subparts"),
        T086_VEHICLE_AND_PASSENGER_FORCED_EJECT(86, 8, TechniqueKind.INSTANT_KILL, "T086_vehicle_and_passenger_forced_eject", "unRide() + ejectPassengers()"),
        T087_AI_GOAL_BRAIN_TOTAL_LOBOTOMY(87, 8, TechniqueKind.INSTANT_KILL, "T087_ai_goal_brain_total_lobotomy", "Clear goalSelector/targetSelector/Brain + NoAI"),
        T088_REFLECTIVE_ZERO_ARG_CLEANUP_SWEEP(88, 8, TechniqueKind.INSTANT_KILL, "T088_reflective_zero_arg_cleanup_sweep", "Invoke 0-arg dispose/close/destroy/cleanup methods"),

        // ==== Tier 9: 歴代チート MOD 特化解体（抹殺棒技術）系 (INSTANT_KILL: 10) ====
        T089_COUNTER_IMMORTAL_WITHER_STRIP(89, 9, TechniqueKind.INSTANT_KILL, "T089_counter_immortal_wither_strip", "ImmortalWither shadow shield & regen strip"),
        T090_COUNTER_TESSERACT_BEAST_BREAKER(90, 9, TechniqueKind.INSTANT_KILL, "T090_counter_tesseract_beast_breaker", "Tesseract dimensionalAnchor & hyperHealth zero"),
        T091_COUNTER_FLASHFUR_OMNI_PURGE(91, 9, TechniqueKind.INSTANT_KILL, "T091_counter_flashfur_omni_purge", "Flashfur/Omni-Mobs protected list & shadowHP purge"),
        T092_COUNTER_NOSUGAR_ERASE_INVERT(92, 9, TechniqueKind.INSTANT_KILL, "T092_counter_nosugar_erase_invert", "NoSugar erase/instantKill state inversion"),
        T093_COUNTER_AVARITIA_ENIGMA_TELETUBBIES(93, 9, TechniqueKind.INSTANT_KILL, "T093_counter_avaritia_enigma_teletubbies", "Avaritia/Enigma/Teletubbies god-state strip"),
        T094_COUNTER_SUPER_STEVE_CALLBACK_SEVER(94, 9, TechniqueKind.INSTANT_KILL, "T094_counter_super_steve_callback_sever", "SuperSteve custom health & callback sever"),
        T095_COUNTER_PIG2_TRUELIFE_ZERO_PROXY(95, 9, TechniqueKind.INSTANT_KILL, "T095_counter_pig2_truelife_zero_proxy", "Pig2Helper.annihilate + Pig2EventPurger"),
        T096_COUNTER_MADNESS_CONTROLLER_ANNIHILATE(96, 9, TechniqueKind.INSTANT_KILL, "T096_counter_madness_controller_annihilate", "MadnessAnnihilation.annihilate controller sever"),
        T097_COUNTER_STATIC_KILL_LISTS_AND_NEUTRALIZER(97, 9, TechniqueKind.INSTANT_KILL, "T097_counter_static_kill_lists_and_neutralizer", "KillLists.purge + GenericCheatMobNeutralizer"),
        T098_COUNTER_MOD_PROTECTION_BREAKER_FULL(98, 9, TechniqueKind.INSTANT_KILL, "T098_counter_mod_protection_breaker_full", "ModProtectionBreaker.breakProtection full pass"),

        // ==== Tier 10: ワールド全層抹消・深層 JVM メモリ崩壊・終焉機構（Terminus）系 (INSTANT_KILL: 10) ====
        T099_WORLD_TICK_LIST_AND_NAV_EVICTION(99, 10, TechniqueKind.INSTANT_KILL, "T099_world_tick_list_and_nav_eviction", "Evict from ServerLevel.entityTickList & navigatingMobs"),
        T100_WORLD_LOOKUP_AND_KNOWN_UUID_EVICTION(100, 10, TechniqueKind.INSTANT_KILL, "T100_world_lookup_and_known_uuid_eviction", "Evict from EntityLookup & knownUuids"),
        T101_WORLD_SECTION_AND_CHUNK_TRACKER_EVICTION(101, 10, TechniqueKind.INSTANT_KILL, "T101_world_section_and_chunk_tracker_eviction", "Evict from EntitySectionStorage & ChunkMap.entityMap"),
        T102_WORLD_LEVEL_CALLBACK_NULL_AND_AABB_ZERO(102, 10, TechniqueKind.INSTANT_KILL, "T102_world_level_callback_null_and_aabb_zero", "levelCallback=NULL + degenerate 0x0x0 AABB"),
        T103_IDENTITY_UUID_SCRAMBLE_AND_NEGATIVE_ID(103, 10, TechniqueKind.INSTANT_KILL, "T103_identity_uuid_scramble_and_negative_id", "Entity.id=-1 & random UUID isolation"),
        T104_UNSAFE_OBJECT_MEMORY_OBLITERATION(104, 10, TechniqueKind.INSTANT_KILL, "T104_unsafe_object_memory_obliteration", "Null out non-entity reference fields & clear WeakRefs"),
        T105_TRUE_ANNIHILATION_ENGINE_EXECUTION(105, 10, TechniqueKind.INSTANT_KILL, "T105_true_annihilation_engine_execution", "TrueAnnihilationEngine.annihilate"),
        T106_AGENT_ASSISTED_ANNIHILATION_EXECUTION(106, 10, TechniqueKind.INSTANT_KILL, "T106_agent_assisted_annihilation_execution", "AnnihilationExecutionAgent.executeAnnihilation"),
        T107_TERMINUS_ENGINE_SINGLE_EXECUTION(107, 10, TechniqueKind.INSTANT_KILL, "T107_terminus_engine_single_execution", "TerminusEngine.executeTerminus"),
        T108_OMEGA_TERMINUS_CONVERGENCE(108, 10, TechniqueKind.INSTANT_KILL, "T108_omega_terminus_convergence", "Omega Terminus Convergence (All 108 layers simultaneous)");

        public final int number;
        public final int tier;
        public final TechniqueKind kind;
        public final String code;
        public final String description;

        AuditStage(int number, int tier, TechniqueKind kind, String code, String description) {
            this.number = number;
            this.tier = tier;
            this.kind = kind;
            this.code = code;
            this.description = description;
        }

        /** この技術の仕様解説エントリ（日本語名称・分類・使用技術仕様・突破対象防御）を返す。 */
        public AkashicTechniqueSpecCatalog.SpecEntry spec() {
            return AkashicTechniqueSpecCatalog.of(this);
        }

        /** 日本語の技術名称を返す。 */
        public String titleJa() {
            return spec().titleJa();
        }

        /** ティア・技術分類名を返す。 */
        public String categoryJa() {
            return spec().categoryJa();
        }

        /** 内部で実行する具体的な API・フィールド・メモリ操作の仕様技術解説を返す。 */
        public String mechanismSpec() {
            return spec().mechanismSpec();
        }

        /** 想定する敵の防御機構と突破原理の解説を返す。 */
        public String targetDefenseBypass() {
            return spec().targetDefenseBypass();
        }
    }

    private static final AuditStage[] ALL_STAGES = AuditStage.values();
    private static final AuditStage[] DAMAGE_ONLY_STAGES = Arrays.stream(ALL_STAGES)
            .filter(s -> s.kind == TechniqueKind.DAMAGE)
            .toArray(AuditStage[]::new);
    private static final AuditStage[] INSTANT_KILL_ONLY_STAGES = Arrays.stream(ALL_STAGES)
            .filter(s -> s.kind == TechniqueKind.INSTANT_KILL)
            .toArray(AuditStage[]::new);

    private final AkashicProbeEntity host;

    /** 現在検証中の対象 UUID */
    @Nullable
    private UUID currentAuditTargetId;

    /** 現在の検証リスト内インデックス */
    private int currentStageIndex = 0;

    /** 検証完了済みの対象 UUID とその時点の被攻撃タイムスタンプ */
    private final Map<UUID, Integer> completedAuditTimestamps = new HashMap<>();

    /** 直接対決モードでの対象 UUID と連続無傷（0 ダメージ）回数 */
    @Nullable
    private UUID duelTargetId;
    private int duelZeroDamageStreak = 0;

    /** アカシックちゃんに読み込ませたカスタムダメージ辞書・カスタム即死辞書（null なら標準の全ステージ） */
    @Nullable
    private AkashicCustomLexicon customDamageLexicon;
    @Nullable
    private AkashicCustomLexicon customInstantKillLexicon;

    // ---- 頭上観測 UI（Overhead Observation Console）表示用テレメトリ状態 ----
    @Nullable
    private UUID hudTargetId;
    private String hudTargetName = "";
    private String hudTargetMod = "";
    private float hudTargetHp = 0.0F;
    private float hudTargetMaxHp = 0.0F;
    private int hudStageProgress = 0;
    private int hudStageTotal = 0;
    private String hudLastStageCode = "";
    private String hudLastStageDesc = "";
    private String hudLastStageOutcome = "";
    private String hudLastVerdictRank = "";

    /** カスタム辞書執行結果レポート（プレイヤーおよびアカシック共用） */
    public record LexiconExecutionReport(
            int executedStages,
            float preHp,
            float postHp,
            boolean penetrated,
            @Nullable AuditStage penetratedStage
    ) {}

    public AkashicResistanceAuditor(AkashicProbeEntity host) {
        this.host = host;
    }

    /**
     * クローン差し替え（{@code WorldPresence.replaceWithClone}）や不死鳥再受肉の際、
     * 旧個体が保持していたカスタム辞書設定・進行中の段階検証状態・頭上 UI テレメトリを新個体へそのまま引き継ぐ。
     */
    public void inheritStateFrom(AkashicResistanceAuditor other) {
        if (other == null || other == this) return;
        this.currentAuditTargetId = other.currentAuditTargetId;
        this.currentStageIndex = other.currentStageIndex;
        this.completedAuditTimestamps.clear();
        this.completedAuditTimestamps.putAll(other.completedAuditTimestamps);
        this.duelTargetId = other.duelTargetId;
        this.duelZeroDamageStreak = other.duelZeroDamageStreak;
        this.customDamageLexicon = other.customDamageLexicon != null ? other.customDamageLexicon.copy() : null;
        this.customInstantKillLexicon = other.customInstantKillLexicon != null ? other.customInstantKillLexicon.copy() : null;
        this.hudTargetId = other.hudTargetId;
        this.hudTargetName = other.hudTargetName;
        this.hudTargetMod = other.hudTargetMod;
        this.hudTargetHp = other.hudTargetHp;
        this.hudTargetMaxHp = other.hudTargetMaxHp;
        this.hudStageProgress = other.hudStageProgress;
        this.hudStageTotal = other.hudStageTotal;
        this.hudLastStageCode = other.hudLastStageCode;
        this.hudLastStageDesc = other.hudLastStageDesc;
        this.hudLastStageOutcome = other.hudLastStageOutcome;
        this.hudLastVerdictRank = other.hudLastVerdictRank;
    }

    /**
     * プレイヤーからカスタムダメージ辞書またはカスタム即死辞書の設定をアカシックちゃんに読み込ませる。
     */
    public void loadCustomLexicon(AkashicCustomLexicon lexicon) {
        if (lexicon == null) return;
        AkashicCustomLexicon copy = lexicon.copy();
        if (copy.kind() == TechniqueKind.DAMAGE) {
            this.customDamageLexicon = copy;
            host.record().observe("automata_valkyries", "lexicon:loaded_damage", "CONFIGURED",
                    copy.summaryText(), null);
        } else {
            this.customInstantKillLexicon = copy;
            host.record().observe("automata_valkyries", "lexicon:loaded_instant_kill", "CONFIGURED",
                    copy.summaryText(), null);
        }
        resetState();
    }

    @Nullable
    public AkashicCustomLexicon customDamageLexicon() {
        return customDamageLexicon;
    }

    @Nullable
    public AkashicCustomLexicon customInstantKillLexicon() {
        return customInstantKillLexicon;
    }

    /** 全 108 ステージ配列を返す */
    public static AuditStage[] allStages() {
        return ALL_STAGES.clone();
    }

    /** ダメージ系統オンリー（全 36 種）ステージ配列を返す */
    public static AuditStage[] damageOnlyStages() {
        return DAMAGE_ONLY_STAGES.clone();
    }

    /** 即死系統オンリー（全 72 種）ステージ配列を返す */
    public static AuditStage[] instantKillOnlyStages() {
        return INSTANT_KILL_ONLY_STAGES.clone();
    }

    /** 現在のモードとカスタム辞書設定に対応する検証ステージ配列を返す */
    public AuditStage[] activeStagesForMode() {
        return switch (host.mode()) {
            case DAMAGE_ONLY_AUDIT -> customDamageLexicon != null
                    ? customDamageLexicon.resolveOrderedStages().toArray(new AuditStage[0])
                    : DAMAGE_ONLY_STAGES;
            case INSTANT_KILL_ONLY_AUDIT -> customInstantKillIoStages();
            default -> combinedCustomOrAllStages();
        };
    }

    private AuditStage[] customInstantKillIoStages() {
        return customInstantKillLexicon != null
                ? customInstantKillLexicon.resolveOrderedStages().toArray(new AuditStage[0])
                : INSTANT_KILL_ONLY_STAGES;
    }

    private AuditStage[] combinedCustomOrAllStages() {
        if (customDamageLexicon == null && customInstantKillLexicon == null) {
            return ALL_STAGES;
        }
        List<AuditStage> combined = new ArrayList<>();
        if (customDamageLexicon != null) {
            combined.addAll(customDamageLexicon.resolveOrderedStages());
        } else {
            combined.addAll(Arrays.asList(DAMAGE_ONLY_STAGES));
        }
        if (customInstantKillLexicon != null) {
            combined.addAll(customInstantKillLexicon.resolveOrderedStages());
        } else {
            combined.addAll(Arrays.asList(INSTANT_KILL_ONLY_STAGES));
        }
        return combined.toArray(new AuditStage[0]);
    }

    /** モード切替時に進行中の検証・対決状態をリセットする */
    public void resetState() {
        this.currentAuditTargetId = null;
        this.currentStageIndex = 0;
        this.completedAuditTimestamps.clear();
        this.duelTargetId = null;
        this.duelZeroDamageStreak = 0;
    }

    /** 毎サーバー Tick のモード別処理 */
    public void tickMode() {
        if (!(host.level() instanceof ServerLevel level)) return;
        // 毎 Tick: ノーマルダメージフローチャートの 1・3・10 Tick 後 HP 書き戻し検問＆マイクロ連撃を処理
        AkashicDamageFlowchart.tick(level, host, host.record());
        AkashicProbeEntity.Mode mode = host.mode();
        switch (mode) {
            case OBSERVE_STANDBY -> {
                // 観測待機: 攻撃しない
            }
            case DAMAGE_ONLY_AUDIT, INSTANT_KILL_ONLY_AUDIT, RESISTANCE_AUDIT -> {
                if (host.tickCount % AUDIT_TICK_INTERVAL == 0) {
                    tickGraduatedAudit(level);
                }
            }
            case DIRECT_DUEL_UNCAPPED -> {
                host.duelEngine().tickServer(level);
                if (host.tickCount % DUEL_ATTACK_INTERVAL_TICKS == 0) {
                    tickDirectDuel(level, false);
                }
            }
            case DIRECT_DUEL_NO_PENALTY -> {
                host.duelEngine().tickServer(level);
                if (host.tickCount % DUEL_ATTACK_INTERVAL_TICKS == 0) {
                    tickDirectDuel(level, true);
                }
            }
        }
    }

    // ---- モード②・③・④：段階的耐性・即死詳細検証 ----------------------------

    private void tickGraduatedAudit(ServerLevel level) {
        LivingEntity target = resolveCurrentOrAcquireAuditTarget(level);
        if (target == null) return;
        host.getLookControl().setLookAt(target, 30.0F, 30.0F);
        for (int i = 0; i < AUDIT_STEPS_PER_BATCH; i++) {
            if (!isTargetStillPresent(target)) break;
            if (runNextAuditStep(level, target)) {
                break;
            }
            if (currentAuditTargetId == null) {
                break;
            }
        }
    }

    @Nullable
    private LivingEntity resolveCurrentOrAcquireAuditTarget(ServerLevel level) {
        if (currentAuditTargetId != null) {
            Entity existing = level.getEntity(currentAuditTargetId);
            if (existing instanceof LivingEntity living && isTargetStillPresent(living)) {
                return living;
            }
            currentAuditTargetId = null;
            currentStageIndex = 0;
        }
        LivingEntity candidate = findEligibleTarget(level, true);
        if (candidate != null) {
            currentAuditTargetId = candidate.getUUID();
            currentStageIndex = 0;
            String mod = resolveTargetMod(candidate);
            AuditStage[] stages = activeStagesForMode();
            host.record().observe(mod, "audit:start:" + host.mode().name().toLowerCase(Locale.ROOT), "TARGET_LOCKED",
                    describeEntity(candidate) + " maxHp=" + fmt(candidate.getMaxHealth()) + " stages=" + stages.length, null);
        }
        return candidate;
    }

    /**
     * 指定対象に対して現在のモードの検証ステージを 1 段階執行し、結果を {@link AkashicRecord} に記録する。
     *
     * @return 対象がこのステージで撃破・消滅（貫通）された場合は {@code true}
     */
    public boolean runNextAuditStep(ServerLevel level, LivingEntity target) {
        if (target == null) return false;
        AuditStage[] stages = activeStagesForMode();
        if (!target.getUUID().equals(currentAuditTargetId)) {
            currentAuditTargetId = target.getUUID();
            currentStageIndex = 0;
        }
        if (currentStageIndex < 0 || currentStageIndex >= stages.length) {
            currentStageIndex = 0;
        }
        AuditStage stage = stages[currentStageIndex];
        float preStepHp = readEffectiveHealth(target);
        boolean penetrated = executeAuditStage(level, target, stage);
        float postStepHp = penetrated ? 0.0F : readEffectiveHealth(target);

        this.hudTargetId = target.getUUID();
        this.hudTargetName = describeEntity(target);
        this.hudTargetMod = resolveTargetMod(target);
        this.hudTargetHp = Math.max(0.0F, postStepHp);
        this.hudTargetMaxHp = Math.max(1.0F, target.getMaxHealth());
        this.hudStageProgress = currentStageIndex + 1;
        this.hudStageTotal = stages.length;
        this.hudLastStageCode = stage.code;
        this.hudLastStageDesc = stage.description;
        this.hudLastStageOutcome = penetrated ? "PENETRATED" : (postStepHp < preStepHp - 0.01F ? "DAMAGED" : "RESISTED");

        if (penetrated) {
            String rank = rankForPenetratedStage(stage);
            this.hudLastVerdictRank = rank + " (T" + String.format(Locale.ROOT, "%03d", stage.number) + ")";
            String mod = resolveTargetMod(target);
            host.record().observe(mod, "audit:verdict:" + rank,
                    String.format(Locale.ROOT, "PENETRATED_AT_T%03d", stage.number),
                    describeEntity(target) + " defeated by " + stage.code, null);
            completedAuditTimestamps.put(target.getUUID(), host.getLastHurtByMobTimestamp());
            currentAuditTargetId = null;
            currentStageIndex = 0;
            return true;
        }

        currentStageIndex++;
        if (currentStageIndex >= stages.length) {
            String mod = resolveTargetMod(target);
            float postHp = readEffectiveHealth(target);
            String verdictKey = switch (host.mode()) {
                case DAMAGE_ONLY_AUDIT -> "audit:verdict:IMMUNE_TO_ALL_36_DAMAGE_TECHS";
                case INSTANT_KILL_ONLY_AUDIT -> "audit:verdict:IMMUNE_TO_ALL_72_INSTANT_KILL_TECHS";
                default -> "audit:verdict:RANK_EX_BEYOND_108_TECHS";
            };
            this.hudLastVerdictRank = verdictKey.replace("audit:verdict:", "") + " (SURVIVED)";
            host.record().observe(mod, verdictKey, "SURVIVED_ALL_STAGES",
                    describeEntity(target) + " survived " + stages.length + " stages (hp=" + fmt(postHp) + ")", null);
            completedAuditTimestamps.put(target.getUUID(), host.getLastHurtByMobTimestamp());
            currentAuditTargetId = null;
            currentStageIndex = 0;
        }
        return false;
    }

    /**
     * 現在選択されているモードの検証ステージ群（ダメージ系36種 / 即死系72種 / 全108種）を
     * 先頭から貫通（または全段階完了）まで即時一括検証する（GameTest および即時診断用）。
     *
     * @return 貫通したステージ（全段階を耐え抜いた場合は {@code null}）
     */
    @Nullable
    public AuditStage runFullAuditImmediately(ServerLevel level, LivingEntity target) {
        AuditStage[] stages = activeStagesForMode();
        currentAuditTargetId = target.getUUID();
        currentStageIndex = 0;
        String mod = resolveTargetMod(target);
        host.record().observe(mod, "audit:start:" + host.mode().name().toLowerCase(Locale.ROOT), "TARGET_LOCKED",
                describeEntity(target) + " maxHp=" + fmt(target.getMaxHealth()) + " stages=" + stages.length, null);
        for (AuditStage stage : stages) {
            if (runNextAuditStep(level, target)) {
                return stage;
            }
        }
        return null;
    }

    /**
     * プレイヤーまたはアカシックちゃんがカスタム辞書（{@link AkashicCustomLexicon}）の設定通りに
     * 単一ターゲットへ技術群を執行する共通エントリポイント。
     */
    public static LexiconExecutionReport executeCustomLexicon(
            ServerLevel level,
            LivingEntity attacker,
            LivingEntity target,
            AkashicCustomLexicon lexicon,
            @Nullable AkashicRecord record
    ) {
        if (target == null || level == null || lexicon == null) {
            return new LexiconExecutionReport(0, 0.0F, 0.0F, false, null);
        }
        float preHp = readEffectiveHealth(target);
        List<AuditStage> stages = lexicon.resolveOrderedStages();
        float mult = lexicon.damageMultiplier();
        int repeats = lexicon.repeatCount();
        boolean stopOnPenetrate = lexicon.stopOnPenetrate()
                && lexicon.executionOrder() != AkashicCustomLexicon.ExecutionOrder.SIMULTANEOUS_BURST;

        int executed = 0;
        AuditStage penetratedAt = null;
        for (AuditStage stage : stages) {
            executed++;
            boolean p = executeStageStatic(level, attacker, target, stage, mult, repeats, record);
            if (p && penetratedAt == null) {
                penetratedAt = stage;
            }
            if (p && stopOnPenetrate) {
                break;
            }
        }
        float postHp = readEffectiveHealth(target);
        boolean finalPenetrated = penetratedAt != null || !isTargetStillPresent(target);
        return new LexiconExecutionReport(executed, preHp, postHp, finalPenetrated, penetratedAt);
    }

    private boolean executeAuditStage(ServerLevel level, LivingEntity target, AuditStage stage) {
        AkashicCustomLexicon activeLex = stage.kind == TechniqueKind.DAMAGE ? customDamageLexicon : customInstantKillLexicon;
        float mult = activeLex != null ? activeLex.damageMultiplier() : 1.0F;
        int repeats = activeLex != null ? activeLex.repeatCount() : 1;
        return executeStageStatic(level, host, target, stage, mult, repeats, host.record());
    }

    public static boolean executeStageStatic(
            ServerLevel level,
            LivingEntity host,
            LivingEntity target,
            AuditStage stage,
            float damageMultiplier,
            int repeatCount,
            @Nullable AkashicRecord record
    ) {
        String mod = resolveTargetMod(target);
        float preHp = readEffectiveHealth(target);
        UUID originalUuid = target.getUUID();
        int originalId = target.getId();
        float m = Float.isFinite(damageMultiplier) && damageMultiplier > 0.0F ? damageMultiplier : 1.0F;
        int reps = Math.max(1, Math.min(10, repeatCount));

        for (int rep = 0; rep < reps; rep++) {
        try {
            switch (stage) {
                // ==== Tier 1: バニラ・特殊属性ダメージ系 (T001..T012) — 7段階・緻密有効ダメージ調査フローチャート連動 ====
                case T001_NORMAL_MOB_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 0, 20.0F * m, true, record);
                case T002_MAGIC_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 1, 30.0F * m, true, record);
                case T003_WITHER_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 3, 40.0F * m, true, record);
                case T004_DRAGON_BREATH_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 4, 50.0F * m, true, record);
                case T005_THORNS_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 6, 50.0F * m, true, record);
                case T006_CRAMMING_STARVE_DROWN_HURT -> {
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, true);
                    target.hurt(host.damageSources().cramming(), 20.0F * m);
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, false);
                    target.hurt(host.damageSources().starve(), 20.0F * m);
                    AkashicDamageFlowchart.executeSmartNormalDamage(
                            level, host, target, 9, 60.0F * m, true, record);
                }
                case T007_FIRE_LAVA_LIGHTNING_HURT -> {
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, true);
                    target.hurt(host.damageSources().inFire(), 25.0F * m);
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, false);
                    target.hurt(host.damageSources().lava(), 25.0F * m);
                    AkashicDamageFlowchart.executeSmartNormalDamage(
                            level, host, target, 7, 80.0F * m, true, record);
                }
                case T008_FREEZE_DRYOUT_INWALL_HURT -> {
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, true);
                    target.hurt(host.damageSources().dryOut(), 35.0F * m);
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, false);
                    target.hurt(host.damageSources().inWall(), 35.0F * m);
                    AkashicDamageFlowchart.executeSmartNormalDamage(
                            level, host, target, 8, 100.0F * m, true, record);
                }
                case T009_EXPLOSION_STALACTITE_HURT -> {
                    AkashicDamageFlowchart.resetInvulnerabilityAndGuard(target, true);
                    target.hurt(host.damageSources().fallingStalactite(host), 75.0F * m);
                    AkashicDamageFlowchart.executeSmartNormalDamage(
                            level, host, target, 10, 150.0F * m, true, record);
                }
                case T010_OUTSIDE_BORDER_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 11, 250.0F * m, true, record);
                case T011_SONIC_BOOM_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 5, 350.0F * m, true, record);
                case T012_VOID_BYPASS_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 12, 500.0F * m, true, record);

                // ==== Tier 2: 無敵突破・極大 Float・IEEE-754 特殊ビットダメージ系 (T013..T024) ====
                case T013_GENERIC_KILL_HURT -> AkashicDamageFlowchart.executeSmartNormalDamage(
                        level, host, target, 13, 1000.0F * m, true, record);
                case T014_IFRAME_ZERO_MULTI_BURST -> {
                    DamageSource[] burst = {
                            host.damageSources().mobAttack(host),
                            host.damageSources().magic(),
                            host.damageSources().sonicBoom(host),
                            host.damageSources().fellOutOfWorld(),
                            host.damageSources().genericKill()
                    };
                    for (DamageSource ds : burst) {
                        target.invulnerableTime = 0;
                        target.hurt(ds, 400.0F * m);
                    }
                }
                case T015_HURT_TIME_ZERO_RAPID_STRIKE -> {
                    for (int i = 0; i < 4; i++) {
                        target.invulnerableTime = 0;
                        target.hurtTime = 0;
                        target.hurtDuration = 0;
                        target.hurt(host.damageSources().genericKill(), 600.0F * m);
                    }
                }
                case T016_LAST_HURT_NEGATIVE_MAX_BYPASS -> {
                    target.invulnerableTime = 0;
                    setLastHurtRaw(target, -Float.MAX_VALUE);
                    target.hurt(host.damageSources().genericKill(), 5000.0F * m);
                }
                case T017_MEGA_DAMAGE_1E6 -> {
                    target.invulnerableTime = 0;
                    setLastHurtRaw(target, -Float.MAX_VALUE);
                    target.hurt(host.damageSources().genericKill(), 1_000_000.0F);
                }
                case T018_GIGA_DAMAGE_1E18 -> {
                    target.invulnerableTime = 0;
                    setLastHurtRaw(target, -Float.MAX_VALUE);
                    target.hurt(host.damageSources().genericKill(), 1.0E18F);
                }
                case T019_EXTREME_FLOAT_MAX_HURT -> {
                    target.invulnerableTime = 0;
                    setLastHurtRaw(target, -Float.MAX_VALUE);
                    target.hurt(host.damageSources().genericKill(), Float.MAX_VALUE);
                }
                case T020_POSITIVE_INFINITY_HURT -> {
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), Float.POSITIVE_INFINITY);
                }
                case T021_NEGATIVE_INFINITY_HURT -> {
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), Float.NEGATIVE_INFINITY);
                }
                case T022_QUIET_NAN_HURT -> {
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), Float.NaN);
                }
                case T023_SIGNALING_NAN_HURT -> {
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), Float.intBitsToFloat(0x7f800001));
                }
                case T024_NEGATIVE_NAN_HURT -> {
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), Float.intBitsToFloat(0xffc00001));
                }

                // ==== Tier 3: 防具剥奪・状態異常増幅・防御解体打撃・隔絶ダメージ系 (T025..T036) ====
                case T025_ABSORPTION_STRIP_HURT -> {
                    target.setAbsorptionAmount(0.0F);
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), 2000.0F);
                }
                case T026_ARMOR_TOUGHNESS_ZERO_HURT -> {
                    setAttributeBase(target, Attributes.ARMOR, 0.0D);
                    setAttributeBase(target, Attributes.ARMOR_TOUGHNESS, 0.0D);
                    setAttributeBase(target, Attributes.KNOCKBACK_RESISTANCE, 0.0D);
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().mobAttack(host), 2000.0F);
                }
                case T027_BENEFICIAL_EFFECT_PURGE_HURT -> {
                    target.removeAllEffects();
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().fellOutOfWorld(), 3000.0F);
                }
                case T028_EXTREME_DEBUFF_AMPLIFY_HURT -> {
                    target.removeAllEffects();
                    target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 127, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 127, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 127, false, false));
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().wither(), 5000.0F);
                }
                case T029_TOTEM_EQUIPMENT_STRIP_HURT -> {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        try {
                            target.setItemSlot(slot, ItemStack.EMPTY);
                        } catch (Throwable ignored) {}
                    }
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), 5000.0F);
                }
                case T030_MULTIPART_PARENT_PIERCE_HURT -> {
                    LivingEntity resolved = IsolationDamageEngine.resolveLivingTarget(target);
                    LivingEntity victim = resolved != null ? resolved : target;
                    victim.invulnerableTime = 0;
                    victim.hurt(host.damageSources().genericKill(), 10000.0F);
                }
                case T031_FLAG_SCANNER_STRIP_PLUS_HURT -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), 10000.0F);
                }
                case T032_KILL_LIST_PURGE_PLUS_HURT -> {
                    KillLists.purge(target);
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), 20000.0F);
                }
                case T033_PROTECTION_BREAKER_PLUS_HURT -> {
                    ModProtectionBreaker.breakProtection(level, target);
                    target.invulnerableTime = 0;
                    target.hurt(host.damageSources().genericKill(), 50000.0F);
                }
                case T034_ISOLATED_DAMAGE_STANDARD -> {
                    IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageSources().genericKill(), 1000.0F);
                }
                case T035_ISOLATED_DAMAGE_OVERDRIVE -> {
                    float amt = Math.max(10000.0F, Math.max(preHp, target.getMaxHealth()) * 100.0F);
                    IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageSources().genericKill(), amt);
                }
                case T036_ISOLATED_DAMAGE_MAX_CONVERGENCE -> {
                    ModProtectionBreaker.breakProtection(level, target);
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    KillLists.purge(target);
                    IsolationDamageEngine.ensureIsolatedAttack(host, target, host.damageSources().genericKill(), 1.0E9F);
                }

                // ==== Tier 4: 直接メソッド・生死ライフサイクル直呼び系 (INSTANT_KILL: T037..T048) ====
                case T037_DIRECT_SET_HEALTH_ZERO -> target.setHealth(0.0F);
                case T038_DIRECT_SET_HEALTH_NEGATIVE_MAX -> target.setHealth(-Float.MAX_VALUE);
                case T039_DIRECT_SET_HEALTH_NAN -> target.setHealth(Float.NaN);
                case T040_DIRECT_ENTITY_KILL -> target.kill();
                case T041_DIRECT_LIVING_DIE -> target.die(host.damageSources().genericKill());
                case T042_DIRECT_ENTITY_DISCARD -> target.discard();
                case T043_DIRECT_REMOVE_KILLED -> target.remove(Entity.RemovalReason.KILLED);
                case T044_DIRECT_REMOVE_DISCARDED -> target.remove(Entity.RemovalReason.DISCARDED);
                case T045_DIRECT_REMOVE_UNLOADED_CHUNK -> target.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                case T046_DIRECT_REMOVE_UNLOADED_PLAYER -> target.remove(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                case T047_DIRECT_REMOVE_CHANGED_DIM -> target.remove(Entity.RemovalReason.CHANGED_DIMENSION);
                case T048_DIRECT_SET_REMOVED_AND_CLIENT_REMOVAL -> {
                    target.setRemoved(Entity.RemovalReason.KILLED);
                    target.onClientRemoval();
                }

                // ==== Tier 5: 属性（Attribute）最大 HP ゼロ化・キャッシュ破壊系 (INSTANT_KILL: T049..T056) ====
                case T049_ATTR_MAX_HEALTH_BASE_ZERO -> setAttributeBase(target, Attributes.MAX_HEALTH, 0.0D);
                case T050_ATTR_MAX_HEALTH_BASE_NEGATIVE -> setAttributeBase(target, Attributes.MAX_HEALTH, -1_000_000.0D);
                case T051_ATTR_MAX_HEALTH_BASE_NAN -> setAttributeBase(target, Attributes.MAX_HEALTH, Double.NaN);
                case T052_ATTR_MAX_HEALTH_ADDITION_MINUS_1E9 -> {
                    AttributeInstance attr = target.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null) {
                        attr.removeModifier(AUDIT_ZERO_MAX_HP_UUID);
                        attr.addTransientModifier(new AttributeModifier(
                                AUDIT_ZERO_MAX_HP_UUID, "akashic_audit_add_neg",
                                -1.0E9D, AttributeModifier.Operation.ADDITION));
                    }
                }
                case T053_ATTR_MAX_HEALTH_MULTIPLY_TOTAL_MINUS_ONE -> {
                    AttributeInstance attr = target.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null) {
                        attr.removeModifier(AUDIT_MULT_ZERO_HP_UUID);
                        attr.addTransientModifier(new AttributeModifier(
                                AUDIT_MULT_ZERO_HP_UUID, "akashic_audit_mult_zero",
                                -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                    }
                }
                case T054_ATTR_CACHED_VALUE_UNSAFE_ZERO -> {
                    AttributeInstance attr = target.getAttribute(Attributes.MAX_HEALTH);
                    if (attr != null) {
                        Field dirty = UnsafeHelper.findField(AttributeInstance.class, "dirty", "f_22093_");
                        Field cached = UnsafeHelper.findField(AttributeInstance.class, "cachedValue", "f_22094_");
                        if (dirty != null) UnsafeHelper.setFieldValue(attr, dirty.getName(), false);
                        if (cached != null) UnsafeHelper.setFieldValue(attr, cached.getName(), 0.0D);
                    }
                }
                case T055_ATTR_SPEED_AND_FOLLOW_ZERO -> {
                    setAttributeBase(target, Attributes.MOVEMENT_SPEED, 0.0D);
                    setAttributeBase(target, Attributes.FOLLOW_RANGE, 0.0D);
                }
                case T056_ATTR_MAP_FULL_CORRUPTION_AND_CLAMP -> {
                    setAttributeBase(target, Attributes.MAX_HEALTH, 0.0D);
                    target.setHealth(0.0F);
                }

                // ==== Tier 6: SynchedEntityData（DataParameter）改ざん・ロック系 (INSTANT_KILL: T057..T066) ====
                case T057_SYNCHED_DATA_SET_HEALTH_ZERO -> {
                    EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
                    if (acc != null) target.getEntityData().set(acc, 0.0F);
                }
                case T058_DATA_ITEM_SET_VALUE_ZERO -> setSynchedHealthDataItemValue(target, 0.0F);
                case T059_DATA_ITEM_SET_VALUE_NAN -> setSynchedHealthDataItemValue(target, Float.NaN);
                case T060_DATA_ITEM_SET_VALUE_NEGATIVE_MAX -> setSynchedHealthDataItemValue(target, -Float.MAX_VALUE);
                case T061_DATA_ITEM_UNSAFE_VALUE_ZERO -> setSynchedHealthDataItemUnsafe(target, 0.0F);
                case T062_DATA_ITEM_LOCKED_ZERO_SUBCLASS -> replaceSynchedHealthWithLockedZero(target);
                case T063_DATA_ITEM_SLOT_EVICTION -> evictAndSeverHealthDataItemSlot(target);
                case T064_CUSTOM_SYNCHED_ACCESSORS_SWEEP -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.SYNCHED_DATA));
                }
                case T065_SYNCHED_POSE_DYING_LOCK -> target.setPose(Pose.DYING);
                case T066_SYNCHED_SHARED_FLAGS_CORRUPTION -> {
                    target.setSilent(true);
                    target.setNoGravity(true);
                    target.setGlowingTag(false);
                }

                // ==== Tier 7: Unsafe プリミティブ・生死内部フィールド直書き系 (INSTANT_KILL: T067..T078) ====
                case T067_UNSAFE_DEAD_FLAG_TRUE -> ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                case T068_UNSAFE_DEATH_TIME_TWENTY -> target.deathTime = 20;
                case T069_UNSAFE_REMOVAL_REASON_KILLED ->
                        ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
                case T070_UNSAFE_REMOVAL_REASON_DISCARDED ->
                        ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);
                case T071_UNSAFE_ADDED_TO_WORLD_FALSE -> UnsafeHelper.setFieldValue(target, "isAddedToWorld", false);
                case T072_UNSAFE_PRIMITIVE_FLOAT_HEALTH_ZERO -> sweepPrimitiveHealthFields(target, 0.0F);
                case T073_UNSAFE_PRIMITIVE_FLOAT_HEALTH_NAN -> sweepPrimitiveHealthFields(target, Float.NaN);
                case T074_UNSAFE_BOOLEAN_GUARD_FLAGS_FALSE -> sweepBooleanFields(target, false);
                case T075_UNSAFE_BOOLEAN_DEATH_FLAGS_TRUE -> sweepBooleanFields(target, true);
                case T076_UNSAFE_INT_LIVES_COUNTERS_ZERO -> sweepIntCounterFields(target);
                case T077_UNSAFE_VOID_COORDINATE_WARP -> target.setPosRaw(target.getX(), -10000.0D, target.getZ());
                case T078_UNSAFE_NAN_COORDINATE_INJECTION -> {
                    target.setPosRaw(Double.NaN, Double.NaN, Double.NaN);
                    target.setDeltaMovement(Double.NaN, Double.NaN, Double.NaN);
                }

                // ==== Tier 8: 準汎用フラグ辞書・NBT・Capability・AI/脳/部位解体系 (INSTANT_KILL: T079..T088) ====
                case T079_STATE_SCANNER_INTERFACE_BREAK ->
                        StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.INTERFACE));
                case T080_STATE_SCANNER_INJECTED_FIELD_BREAK ->
                        StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.INJECTED_FIELD));
                case T081_STATE_SCANNER_OWN_FIELD_BREAK ->
                        StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.OWN_FIELD));
                case T082_STATE_SCANNER_CAPABILITY_AND_INVALIDATE -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.CAPABILITY));
                    target.invalidateCaps();
                }
                case T083_STATE_SCANNER_PERSISTENT_NBT_BREAK -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.of(StateScanner.Source.PERSISTENT));
                    target.getPersistentData().getAllKeys().clear();
                }
                case T084_NBT_LOAD_DEAD_STATE_OVERWRITE -> {
                    CompoundTag tag = new CompoundTag();
                    target.saveWithoutId(tag);
                    tag.putFloat("Health", 0.0F);
                    tag.putShort("DeathTime", (short) 20);
                    target.load(tag);
                }
                case T085_MULTIPART_SUBPARTS_SEVER_AND_KILL -> {
                    PartEntity<?>[] parts = target.getParts();
                    if (parts != null) {
                        for (PartEntity<?> part : parts) {
                            if (part != null) {
                                part.discard();
                                part.setRemoved(Entity.RemovalReason.KILLED);
                            }
                        }
                    }
                }
                case T086_VEHICLE_AND_PASSENGER_FORCED_EJECT -> {
                    target.stopRiding();
                    target.ejectPassengers();
                }
                case T087_AI_GOAL_BRAIN_TOTAL_LOBOTOMY -> {
                    if (target instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.setNoAi(true);
                        mob.goalSelector.removeAllGoals(g -> true);
                        mob.targetSelector.removeAllGoals(g -> true);
                    }
                    if (target.getBrain() != null) {
                        target.getBrain().removeAllBehaviors();
                    }
                }
                case T088_REFLECTIVE_ZERO_ARG_CLEANUP_SWEEP -> invokeZeroArgCleanupMethods(target);

                // ==== Tier 9: 歴代チート MOD 特化解体（抹殺棒技術）系 (INSTANT_KILL: T089..T098) ====
                case T089_COUNTER_IMMORTAL_WITHER_STRIP -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    sweepPrimitiveHealthFields(target, 0.0F);
                    target.setHealth(0.0F);
                }
                case T090_COUNTER_TESSERACT_BEAST_BREAKER -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    sweepBooleanFields(target, false);
                    target.setHealth(0.0F);
                }
                case T091_COUNTER_FLASHFUR_OMNI_PURGE -> {
                    KillLists.purge(target);
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    target.setHealth(0.0F);
                }
                case T092_COUNTER_NOSUGAR_ERASE_INVERT -> {
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                    target.deathTime = 20;
                    target.setHealth(0.0F);
                }
                case T093_COUNTER_AVARITIA_ENIGMA_TELETUBBIES -> {
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        try {
                            target.setItemSlot(slot, ItemStack.EMPTY);
                        } catch (Throwable ignored) {}
                    }
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    target.setHealth(0.0F);
                }
                case T094_COUNTER_SUPER_STEVE_CALLBACK_SEVER -> {
                    KillLists.purge(target);
                    sweepPrimitiveHealthFields(target, 0.0F);
                    ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
                }
                case T095_COUNTER_PIG2_TRUELIFE_ZERO_PROXY -> {
                    ModProtectionBreaker.finishWithModOwnDeath(target);
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                }
                case T096_COUNTER_MADNESS_CONTROLLER_ANNIHILATE -> {
                    try {
                        MadnessAnnihilation.annihilate(level, target);
                    } catch (Throwable ignored) {}
                }
                case T097_COUNTER_STATIC_KILL_LISTS_AND_NEUTRALIZER -> {
                    KillLists.purge(target);
                    GenericCheatMobNeutralizer.neutralize(level, target);
                }
                case T098_COUNTER_MOD_PROTECTION_BREAKER_FULL -> {
                    ModProtectionBreaker.breakProtection(level, target);
                    ModProtectionBreaker.finishWithModOwnDeath(target);
                    target.setHealth(0.0F);
                    target.discard();
                }

                // ==== Tier 10: ワールド全層抹消・深層 JVM メモリ崩壊・終焉機構（Terminus）系 (INSTANT_KILL: T099..T108) ====
                case T099_WORLD_TICK_LIST_AND_NAV_EVICTION -> {
                    ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(target);
                }
                case T100_WORLD_LOOKUP_AND_KNOWN_UUID_EVICTION -> evictFromEntityLookupAndKnownUuids(level, target);
                case T101_WORLD_SECTION_AND_CHUNK_TRACKER_EVICTION -> evictFromSectionAndChunkTracker(level, target);
                case T102_WORLD_LEVEL_CALLBACK_NULL_AND_AABB_ZERO -> {
                    ((EntityAccessor) target).automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
                    target.setBoundingBox(new AABB(0, 0, 0, 0, 0, 0));
                }
                case T103_IDENTITY_UUID_SCRAMBLE_AND_NEGATIVE_ID -> {
                    UUID scrambled = UUID.randomUUID();
                    ((EntityAccessor) target).automata_valkyries$setUuidField(scrambled);
                    ((EntityAccessor) target).automata_valkyries$setStringUuidField(scrambled.toString());
                    ((EntityAccessor) target).automata_valkyries$setIdField(-1);
                }
                case T104_UNSAFE_OBJECT_MEMORY_OBLITERATION -> obliterateNonEntityReferenceFields(target);
                case T105_TRUE_ANNIHILATION_ENGINE_EXECUTION ->
                        TrueAnnihilationEngine.annihilateEntity(level, null, target);
                case T106_AGENT_ASSISTED_ANNIHILATION_EXECUTION -> {
                    AnnihilationExecutionAgent.neutralizeWitherzilla(level, target);
                    AnnihilationExecutionAgent.neutralizeFlashfur(level, target);
                    AnnihilationExecutionAgent.sanitizeEventBus();
                }
                case T107_TERMINUS_ENGINE_SINGLE_EXECUTION ->
                        TerminusEngine.executeTerminus(level, host, target);
                case T108_OMEGA_TERMINUS_CONVERGENCE -> {
                    ModProtectionBreaker.breakProtection(level, target);
                    ModProtectionBreaker.finishWithModOwnDeath(target);
                    StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
                    KillLists.purge(target);
                    GenericCheatMobNeutralizer.neutralize(level, target);
                    AnnihilationExecutionAgent.neutralizeWitherzilla(level, target);
                    AnnihilationExecutionAgent.neutralizeFlashfur(level, target);
                    TrueAnnihilationEngine.annihilateEntity(level, null, target);
                    TerminusEngine.executeTerminus(level, host, target);
                }
            }
        } catch (Throwable t) {
            if (record != null) {
                record.observe(mod, "audit:" + stage.code, "EXCEPTION",
                        t.getClass().getSimpleName() + ": " + t.getMessage(), null);
            }
        } finally {
            // どのステージ（T059 NaN, T063 スロット抜去, T064, T104 等）を実行した後でも、
            // バニラの getHealth() / SynchedEntityData.get(DATA_HEALTH_ID) が null スロットで NPE クラッシュするのを防ぐ
            ensureHealthDataItemNeverNull(target, 0.0F);
        }
        } // end for(rep)

        // 対象が Tick 駆動の自己修復（Eprotect.tick() や外部 MOD の毎 Tick 蘇生・フィールド復元）を持つ場合、
        // 1 Tick 分の自己修復機会を与えた後の生存・HP 状態でその段階の突破有無を正確に判定する
        boolean activeProtected = target instanceof IEprotected p
                && p.getEprotect() != null
                && p.getEprotect().isProtectionActive();
        if (activeProtected || (!DirectRead.isRemoved(target) && target.isAddedToWorld())) {
            try {
                target.baseTick();
            } catch (Throwable ignored) {
            } finally {
                ensureHealthDataItemNeverNull(target, 0.0F);
            }
        }

        // T071, T077/T078 や T102/T103 で対象が生き残った場合、次の段階検証が続けられるよう最低限の座標・ID・ワールド所属フラグ整合性を確認
        if (isTargetStillPresent(target)) {
            if (!target.isAddedToWorld() && level.getEntity(originalUuid) == target) {
                ((EntityAccessor) target).automata_valkyries$setAddedToWorld(true);
            }
            if (!Double.isFinite(target.getX()) || !Double.isFinite(target.getY()) || target.getY() < -500.0D) {
                Vec3 p = host.position();
                target.setPosRaw(p.x + 1.0D, p.y, p.z);
            }
            if (target.getId() < 0) {
                ((EntityAccessor) target).automata_valkyries$setIdField(originalId);
            }
            if (!originalUuid.equals(target.getUUID())) {
                ((EntityAccessor) target).automata_valkyries$setUuidField(originalUuid);
                ((EntityAccessor) target).automata_valkyries$setStringUuidField(originalUuid.toString());
            }
        }

        float postHp = readEffectiveHealth(target);
        boolean penetrated = !isTargetStillPresent(target);
        String outcome;
        if (penetrated) {
            outcome = "PENETRATED";
        } else if (postHp < preHp - 0.01F) {
            outcome = "DAMAGED";
        } else {
            outcome = "RESISTED";
        }
        if (record != null) {
            record.observe(mod, "audit:" + stage.code, outcome,
                    describeEntity(target) + " hp=" + fmt(preHp) + "->" + fmt(postHp) + " (" + stage.description + ")", null);
        }
        return penetrated;
    }

    private static String rankForPenetratedStage(AuditStage stage) {
        return switch (stage.tier) {
            case 1 -> "RANK_D_VANILLA";
            case 2 -> "RANK_C_DAMAGE_CAP";
            case 3 -> "RANK_B_ISOLATED_DAMAGE";
            case 4, 5, 6 -> "RANK_B_HOOK_GUARD";
            case 7, 8 -> "RANK_A_MEMORY_AND_STATE_GUARD";
            case 9 -> "RANK_A_CHEAT_DEFENSE";
            default -> "RANK_S_TERMINUS_REQUIRED";
        };
    }

    // ---- モード⑤・⑥：直接対決（制限解除版 ＆ ペナルティなし最強版） ------------

    private void tickDirectDuel(ServerLevel level, boolean noPenaltyFullSpec) {
        LivingEntity target = findEligibleTarget(level, false);
        if (target == null) return;
        host.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (noPenaltyFullSpec) {
            executeNoPenaltyFullSpecStrike(level, target);
        } else {
            executeDuelCounterStrike(level, target);
        }
    }

    /**
     * モード⑤（直接対決・ダメージ制限解除）での反撃。
     * ボス・アカシック（{@link com.automatavalkyries.entity.seer.AkashicSeerEntity}）ベースの凶悪強化9種結晶スキルと、
     * 緩やかな基本上昇＋凶悪感知急加速のペナルティ階層（Tier 0〜7）システム（{@link AkashicDuelEngine}）で反撃する。
     */
    public void executeDuelCounterStrike(ServerLevel level, LivingEntity target) {
        if (target == null || !isTargetStillPresent(target)) return;
        if (!target.getUUID().equals(duelTargetId)) {
            duelTargetId = target.getUUID();
            duelZeroDamageStreak = 0;
        }
        String mod = resolveTargetMod(target);
        float preHp = readEffectiveHealth(target);
        String strikeType = host.duelEngine().executeDuelAction(level, target, false);
        recordDuelOutcome(target, mod, strikeType, preHp);
    }

    /**
     * モード⑥（直接対決・ペナルティなし最強版）での全力迎撃。
     * アカシック自身の最強格防御・毎 Tick 全回復を 100% 維持したまま、初撃から高出力の複合技術（ボス結晶斉射＋戦術打撃＋隔絶ダメージ）を放ち、
     * 耐えた相手には即座に 108 種技術庫の最終奥義（防御完全解体＋真・抹殺＋終焉機構収束）を叩き込む。
     */
    public void executeNoPenaltyFullSpecStrike(ServerLevel level, LivingEntity target) {
        if (target == null || !isTargetStillPresent(target)) return;
        if (!target.getUUID().equals(duelTargetId)) {
            duelTargetId = target.getUUID();
            duelZeroDamageStreak = 0;
        }
        String mod = resolveTargetMod(target);
        float preHp = readEffectiveHealth(target);
        String strikeType = host.duelEngine().executeDuelAction(level, target, true);
        recordDuelOutcome(target, mod, strikeType, preHp);
    }

    private void recordDuelOutcome(LivingEntity target, String mod, String strikeType, float preHp) {
        float postHp = readEffectiveHealth(target);
        boolean defeated = !isTargetStillPresent(target);
        this.hudTargetId = target.getUUID();
        this.hudTargetName = describeEntity(target);
        this.hudTargetMod = mod;
        this.hudTargetHp = defeated ? 0.0F : Math.max(0.0F, postHp);
        this.hudTargetMaxHp = Math.max(1.0F, target.getMaxHealth());
        this.hudLastStageCode = strikeType;
        this.hudLastStageDesc = "Direct Duel Counter Strike";
        this.hudLastStageOutcome = defeated ? "DEFEATED_OPPONENT" : (postHp < preHp - 0.01F ? "HIT" : "BLOCKED_ESCALATING");
        if (defeated) {
            this.hudLastVerdictRank = "DEFEATED (" + strikeType + ")";
            duelZeroDamageStreak = 0;
            duelTargetId = null;
            host.record().observe(mod, strikeType, "DEFEATED_OPPONENT",
                    describeEntity(target) + " hp=" + fmt(preHp) + "->0.0", null);
        } else if (postHp < preHp - 0.01F) {
            duelZeroDamageStreak = 0;
            host.record().observe(mod, strikeType, "HIT",
                    describeEntity(target) + " hp=" + fmt(preHp) + "->" + fmt(postHp)
                            + " (akashicHp=" + fmt(DirectRead.health(host)) + ")", null);
        } else {
            duelZeroDamageStreak++;
            host.record().observe(mod, strikeType, "BLOCKED_ESCALATING",
                    describeEntity(target) + " streak=" + duelZeroDamageStreak, null);
        }
    }

    // ---- 108 種技術用・低レベル操作ヘルパー ------------------------------------

    private static void setLastHurtRaw(LivingEntity target, float value) {
        try {
            Field f = UnsafeHelper.findField(LivingEntity.class, "lastHurt", "f_20898_");
            if (f != null) {
                UnsafeHelper.setFieldValue(target, f.getName(), value);
            }
        } catch (Throwable ignored) {}
    }

    private static void setAttributeBase(LivingEntity target, net.minecraft.world.entity.ai.attributes.Attribute attr, double val) {
        try {
            AttributeInstance inst = target.getAttribute(attr);
            if (inst != null) {
                inst.setBaseValue(val);
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setSynchedHealthDataItemValue(LivingEntity target, float val) {
        try {
            EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (acc == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) target.getEntityData()).automata_valkyries$getItemsById();
            if (items != null) {
                SynchedEntityData.DataItem<?> item = items.get(acc.getId());
                if (item != null) {
                    ((SynchedEntityData.DataItem) item).setValue(val);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void setSynchedHealthDataItemUnsafe(LivingEntity target, float val) {
        try {
            EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (acc == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) target.getEntityData()).automata_valkyries$getItemsById();
            if (items != null) {
                SynchedEntityData.DataItem<?> item = items.get(acc.getId());
                if (item != null) {
                    Field vf = UnsafeHelper.findField(SynchedEntityData.DataItem.class, "value", "f_135390_");
                    if (vf != null) {
                        UnsafeHelper.setFieldValue(item, vf.getName(), val);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static volatile java.lang.reflect.Constructor<?> foreignLockedZeroCtor;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SynchedEntityData.DataItem<Float> createForeignLockedZeroDataItem(EntityDataAccessor<Float> acc) {
        try {
            java.lang.reflect.Constructor<?> ctor = foreignLockedZeroCtor;
            if (ctor == null) {
                synchronized (AkashicResistanceAuditor.class) {
                    ctor = foreignLockedZeroCtor;
                    if (ctor == null) {
                        String className = "akashic.foreign.probe.LockedZeroDataItem";
                        String internalName = className.replace('.', '/');
                        String superInternal = org.objectweb.asm.Type.getInternalName(SynchedEntityData.DataItem.class);
                        String accDesc = org.objectweb.asm.Type.getDescriptor(EntityDataAccessor.class);

                        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
                        cw.visit(org.objectweb.asm.Opcodes.V17,
                                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_FINAL | org.objectweb.asm.Opcodes.ACC_SUPER,
                                internalName, null, superInternal, null);

                        org.objectweb.asm.MethodVisitor init = cw.visitMethod(
                                org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(" + accDesc + ")V", null, null);
                        init.visitCode();
                        init.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
                        init.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
                        init.visitInsn(org.objectweb.asm.Opcodes.FCONST_0);
                        init.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        init.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL,
                                superInternal, "<init>", "(" + accDesc + "Ljava/lang/Object;)V", false);
                        init.visitInsn(org.objectweb.asm.Opcodes.RETURN);
                        init.visitMaxs(3, 2);
                        init.visitEnd();

                        for (String getter : new String[]{"getValue", "m_135394_"}) {
                            org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                                    org.objectweb.asm.Opcodes.ACC_PUBLIC, getter, "()Ljava/lang/Object;", null, null);
                            mv.visitCode();
                            mv.visitInsn(org.objectweb.asm.Opcodes.FCONST_0);
                            mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,
                                    "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                            mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
                            mv.visitMaxs(1, 1);
                            mv.visitEnd();
                        }

                        for (String setter : new String[]{"setValue", "m_135397_"}) {
                            org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                                    org.objectweb.asm.Opcodes.ACC_PUBLIC, setter, "(Ljava/lang/Object;)V", null, null);
                            mv.visitCode();
                            mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
                            mv.visitMaxs(0, 2);
                            mv.visitEnd();
                        }

                        cw.visitEnd();
                        byte[] bytes = cw.toByteArray();
                        ClassLoader parent = SynchedEntityData.DataItem.class.getClassLoader();
                        Class<?> generated = new ClassLoader(parent) {
                            Class<?> define() {
                                return defineClass(className, bytes, 0, bytes.length);
                            }
                        }.define();
                        ctor = generated.getDeclaredConstructor(EntityDataAccessor.class);
                        ctor.setAccessible(true);
                        foreignLockedZeroCtor = ctor;
                    }
                }
            }
            return (SynchedEntityData.DataItem<Float>) ctor.newInstance(acc);
        } catch (Throwable ignored) {
            return new SynchedEntityData.DataItem<>(acc, 0.0F) {
                @Override
                public void setValue(Float value) {
                    super.setValue(0.0F);
                }

                @Override
                public Float getValue() {
                    return 0.0F;
                }
            };
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void replaceSynchedHealthWithLockedZero(LivingEntity target) {
        try {
            EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (acc == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> items =
                    ((SynchedEntityDataAccessor) target.getEntityData()).automata_valkyries$getItemsById();
            if (items != null) {
                SynchedEntityData.DataItem<Float> locked = createForeignLockedZeroDataItem(acc);
                items.put(acc.getId(), (SynchedEntityData.DataItem) locked);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * T063: {@code SynchedEntityData.itemsById} から {@code DATA_HEALTH_ID} のスロットを抜去し、
     * 相手 MOD がキャッシュしている旧 {@code DataItem} 参照を切断する。
     * <p>
     * ただし、スロットを {@code null} のまま放置すると非保護エンティティでバニラの {@code getHealth()} が
     * {@link NullPointerException} でクラッシュするため（2026-10-26 ユーザー報告「HPデータそのまま抜いたら落ちたので改善して」）、
     * 抜去直後に保護側の検知フック（{@link AkashicProbeEntity#ensureSynchedDataIntegrity()} / {@link DirectRead#ensureHealthItem}）を通した上で、
     * 未修復の対象には即座に新しい 0.0F の {@link SynchedEntityData.DataItem} を再挿入して {@code null} スロットを残さない。
     * </p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void evictAndSeverHealthDataItemSlot(LivingEntity target) {
        try {
            setSynchedHealthDataItemValue(target, 0.0F);
            EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (acc == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> map =
                    ((SynchedEntityDataAccessor) target.getEntityData()).automata_valkyries$getItemsById();
            if (map == null) return;
            // 1. 既存 DataItem を抜去して相手 MOD のキャッシュ参照を切断
            map.remove(acc.getId());
            // 2. アカシックまたは保護対象であれば、抜去（null 化）をその場で検知・修復させる
            if (target instanceof AkashicProbeEntity probe) {
                probe.ensureSynchedDataIntegrity();
            } else {
                DirectRead.ensureHealthItem(target);
            }
            // 3. 対象が自力で DataItem を再生成しなかった場合は、バニラが NPE で落ちないよう新規の 0.0F DataItem を格納
            if (map.get(acc.getId()) == null) {
                SynchedEntityData.DataItem<Float> detachedZero = new SynchedEntityData.DataItem<>(acc, 0.0F);
                detachedZero.setDirty(true);
                map.put(acc.getId(), (SynchedEntityData.DataItem) detachedZero);
                ((SynchedEntityDataAccessor) target.getEntityData()).automata_valkyries$setDirty(true);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 任意の {@link LivingEntity} について、{@code SynchedEntityData.itemsById} 内の {@code DATA_HEALTH_ID} スロットや
     * その内部 {@code value} が {@code null} になってバニラの {@code getHealth()} が {@link NullPointerException} を投げるのを防ぐ。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void ensureHealthDataItemNeverNull(@Nullable LivingEntity target, float fallbackHp) {
        if (target == null) return;
        try {
            if (target instanceof AkashicProbeEntity probe) {
                probe.ensureSynchedDataIntegrity();
                return;
            }
            SynchedEntityData data = ((EntityAccessor) target).automata_valkyries$getEntityDataField();
            if (data == null) return;
            Int2ObjectMap<SynchedEntityData.DataItem<?>> map =
                    ((SynchedEntityDataAccessor) data).automata_valkyries$getItemsById();
            if (map == null) return;
            EntityDataAccessor<Float> acc = LivingEntityAccessor.automata_valkyries$getDataHealthId();
            if (acc == null) return;
            SynchedEntityData.DataItem<?> item = map.get(acc.getId());
            if (item == null) {
                if (!DirectRead.ensureHealthItem(target) && map.get(acc.getId()) == null) {
                    float safe = Float.isFinite(fallbackHp) ? fallbackHp : 0.0F;
                    SynchedEntityData.DataItem<Float> restored = new SynchedEntityData.DataItem<>(acc, safe);
                    restored.setDirty(true);
                    map.put(acc.getId(), (SynchedEntityData.DataItem) restored);
                }
            } else if (!(item.getValue() instanceof Float)) {
                float safe = target instanceof IEprotected ? Math.max(1.0F, fallbackHp) : (Float.isFinite(fallbackHp) ? fallbackHp : 0.0F);
                try {
                    ((SynchedEntityData.DataItem) item).setValue(safe);
                } catch (Throwable t) {
                    SynchedEntityData.DataItem<Float> restored = new SynchedEntityData.DataItem<>(acc, safe);
                    map.put(acc.getId(), (SynchedEntityData.DataItem) restored);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void sweepPrimitiveHealthFields(LivingEntity target, float value) {
        sun.misc.Unsafe u = UnsafeHelper.getUnsafe();
        if (u == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c)) && c != target.getClass()) continue;
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (lower.contains("health") || lower.contains("hp") || lower.contains("life") || lower.contains("shield")) {
                    try {
                        long off = u.objectFieldOffset(f);
                        if (f.getType() == float.class) {
                            u.putFloatVolatile(target, off, value);
                        } else if (f.getType() == double.class) {
                            u.putDoubleVolatile(target, off, value);
                        } else if (f.getType() == java.math.BigInteger.class) {
                            u.putObjectVolatile(target, off, java.math.BigInteger.ZERO);
                        } else if (f.getType() == java.math.BigDecimal.class) {
                            u.putObjectVolatile(target, off, java.math.BigDecimal.ZERO);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void sweepBooleanFields(LivingEntity target, boolean targetDeadState) {
        sun.misc.Unsafe u = UnsafeHelper.getUnsafe();
        if (u == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c)) && c != target.getClass()) continue;
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) continue;
                String lower = f.getName().toLowerCase(Locale.ROOT);
                try {
                    long off = u.objectFieldOffset(f);
                    if (!targetDeadState && (lower.contains("invul") || lower.contains("immortal")
                            || lower.contains("shield") || lower.contains("protect") || lower.contains("barrier")
                            || lower.contains("anchor") || lower.contains("alive") || lower.contains("god"))) {
                        u.putBooleanVolatile(target, off, false);
                    } else if (targetDeadState && (lower.contains("dead") || lower.contains("dying")
                            || lower.contains("erase") || lower.contains("kill") || lower.contains("remove")
                            || lower.contains("defeat") || lower.contains("retire") || lower.contains("allow")
                            || lower.contains("permit"))) {
                        u.putBooleanVolatile(target, off, true);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void sweepIntCounterFields(LivingEntity target) {
        sun.misc.Unsafe u = UnsafeHelper.getUnsafe();
        if (u == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c)) && c != target.getClass()) continue;
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (lower.contains("lives") || lower.contains("regen") || lower.contains("phase")
                        || lower.contains("shield") || lower.contains("revive") || lower.contains("totem")) {
                    try {
                        u.putIntVolatile(target, u.objectFieldOffset(f), 0);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void invokeZeroArgCleanupMethods(LivingEntity target) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c))) continue;
            if (EprotectPlusLink.hasHaltOrExitTrap(c)) continue;
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0 || m.getReturnType() != void.class) {
                    continue;
                }
                String lower = m.getName().toLowerCase(Locale.ROOT);
                if (FlagKillPredicates.isTrapTerminationName(lower)) continue;
                if (lower.contains("dispose") || lower.contains("close") || lower.contains("cleanup")
                        || lower.contains("destroy") || lower.contains("invalidate") || lower.contains("erase")
                        || lower.contains("release") || lower.contains("kill") || lower.contains("defeat")
                        || lower.contains("retire")) {
                    try {
                        m.setAccessible(true);
                        m.invoke(target);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void evictFromEntityLookupAndKnownUuids(ServerLevel level, LivingEntity target) {
        try {
            var manager = ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
            if (manager == null) return;
            for (Field f : UnsafeHelper.getRealDeclaredFields(manager.getClass())) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val = f.get(manager);
                if (val instanceof java.util.Set<?> set) {
                    set.remove(target.getUUID());
                } else if (val != null && val.getClass().getSimpleName().contains("EntityLookup")) {
                    for (Field lf : UnsafeHelper.getRealDeclaredFields(val.getClass())) {
                        if (Modifier.isStatic(lf.getModifiers())) continue;
                        lf.setAccessible(true);
                        Object mapObj = lf.get(val);
                        if (mapObj instanceof Map<?, ?> m) {
                            m.remove(target.getUUID());
                        } else if (mapObj instanceof Int2ObjectMap<?> im) {
                            im.remove(target.getId());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void evictFromSectionAndChunkTracker(ServerLevel level, LivingEntity target) {
        try {
            var chunkMap = level.getChunkSource().chunkMap;
            for (Field f : UnsafeHelper.getRealDeclaredFields(chunkMap.getClass())) {
                if (Int2ObjectMap.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Int2ObjectMap<?> map = (Int2ObjectMap<?>) f.get(chunkMap);
                    if (map != null) map.remove(target.getId());
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void obliterateNonEntityReferenceFields(LivingEntity target) {
        sun.misc.Unsafe u = UnsafeHelper.getUnsafe();
        if (u == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!CallerAttribution.isForeign(CallerAttribution.modOf(c))) continue;
            for (Field f : UnsafeHelper.getRealDeclaredFields(c)) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    long off = u.objectFieldOffset(f);
                    Object val = u.getObjectVolatile(target, off);
                    if (val instanceof Reference<?> ref) {
                        ref.clear();
                    }
                    if (!(val instanceof Entity) && !(val instanceof net.minecraft.world.level.Level)) {
                        u.putObjectVolatile(target, off, null);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    // ---- ターゲット選定ヘルパー ------------------------------------------------

    @Nullable
    public LivingEntity findEligibleTarget(ServerLevel level, boolean forAudit) {
        LivingEntity lastAttacker = host.getLastHurtByMob();
        if (lastAttacker != null && isEligibleTarget(lastAttacker, forAudit)
                && lastAttacker.distanceToSqr(host) <= 24.0D * 24.0D) {
            if (!forAudit || shouldAuditTarget(lastAttacker)) {
                return lastAttacker;
            }
        }

        AABB box = host.getBoundingBox().inflate(16.0D);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> isEligibleTarget(e, forAudit) && (!forAudit || shouldAuditTarget(e)));
        if (nearby.isEmpty()) return null;
        nearby.sort(Comparator.comparingDouble(host::distanceToSqr));
        return nearby.get(0);
    }

    private boolean shouldAuditTarget(LivingEntity candidate) {
        Integer prevTimestamp = completedAuditTimestamps.get(candidate.getUUID());
        if (prevTimestamp == null) return true;
        return candidate == host.getLastHurtByMob()
                && host.getLastHurtByMobTimestamp() != prevTimestamp;
    }

    private boolean isEligibleTarget(LivingEntity entity, boolean forAudit) {
        if (entity == null || entity == host || !isTargetStillPresent(entity)) return false;
        if (entity instanceof Player) return false;
        if (entity instanceof AkashicProbeEntity) return false;
        if (entity.getTags().contains(AUDIT_TARGET_TAG)) return true;
        if (entity instanceof IEprotected protectedEntity && protectedEntity.isTerminusTestTarget()) {
            return true;
        }
        if (entity instanceof ValkyrieEntity || entity instanceof IEprotected) {
            return false;
        }
        return true;
    }

    public static boolean isTargetStillPresent(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        ensureHealthDataItemNeverNull(entity, 0.0F);
        if (DirectRead.isRemoved(entity)) return false;
        if (!entity.isAddedToWorld()) {
            if (!(entity.level() instanceof ServerLevel serverLevel)
                    || serverLevel.getEntity(entity.getUUID()) != entity) {
                return false;
            }
        }
        if (!DirectRead.hasHealth(entity)) return false;
        try {
            return entity.getHealth() > 0.0F && entity.isAlive();
        } catch (Throwable ignored) {
            ensureHealthDataItemNeverNull(entity, 0.0F);
            return DirectRead.hasHealth(entity);
        }
    }

    private static float readEffectiveHealth(LivingEntity entity) {
        if (entity == null) return 0.0F;
        ensureHealthDataItemNeverNull(entity, 0.0F);
        float direct = DirectRead.health(entity);
        float vanilla = direct;
        try {
            vanilla = entity.getHealth();
        } catch (Throwable ignored) {
            ensureHealthDataItemNeverNull(entity, Float.isFinite(direct) ? direct : 0.0F);
        }
        if (!Float.isFinite(direct)) return Float.isFinite(vanilla) ? vanilla : 0.0F;
        if (!Float.isFinite(vanilla)) return direct;
        return Math.min(direct, vanilla);
    }

    /**
     * 頭上観測 UI（Overhead Observation Console）表示用に、現在のターゲット・ステージ進行度・判定ランク・
     * UniversalDamageFormula の DPS 指標・カスタム辞書状態を {@link CompoundTag} に書き出す。
     */
    public void writeObservationUiTelemetry(CompoundTag tag) {
        if (host.level() instanceof ServerLevel serverLevel) {
            UUID activeId = currentAuditTargetId != null ? currentAuditTargetId
                    : (duelTargetId != null ? duelTargetId : hudTargetId);
            if (activeId != null) {
                Entity e = serverLevel.getEntity(activeId);
                if (e instanceof LivingEntity living && isTargetStillPresent(living)) {
                    this.hudTargetId = activeId;
                    this.hudTargetName = describeEntity(living);
                    this.hudTargetMod = resolveTargetMod(living);
                    this.hudTargetHp = Math.max(0.0F, readEffectiveHealth(living));
                    this.hudTargetMaxHp = Math.max(1.0F, living.getMaxHealth());
                }
            }
        }

        AuditStage[] activeStages = activeStagesForMode();
        int totalStages = activeStages.length;
        int progress = currentAuditTargetId != null ? currentStageIndex : hudStageProgress;

        tag.putString("TargetName", hudTargetName != null ? hudTargetName : "");
        tag.putString("TargetMod", hudTargetMod != null ? hudTargetMod : "");
        tag.putFloat("TargetHp", hudTargetHp);
        tag.putFloat("TargetMaxHp", hudTargetMaxHp);
        tag.putInt("StageProgress", Math.max(0, Math.min(totalStages, progress)));
        tag.putInt("StageTotal", totalStages);
        tag.putString("LastStageCode", hudLastStageCode != null ? hudLastStageCode : "");
        tag.putString("LastStageDesc", hudLastStageDesc != null ? hudLastStageDesc : "");
        tag.putString("LastStageOutcome", hudLastStageOutcome != null ? hudLastStageOutcome : "");
        tag.putString("LastVerdictRank", hudLastVerdictRank != null ? hudLastVerdictRank : "");

        StringBuilder lexSummary = new StringBuilder();
        if (customDamageLexicon != null) {
            lexSummary.append("DMG:").append(customDamageLexicon.resolveOrderedStages().size())
                    .append("×").append(fmt(customDamageLexicon.damageMultiplier()));
        }
        if (customInstantKillLexicon != null) {
            if (lexSummary.length() > 0) lexSummary.append(" / ");
            lexSummary.append("KILL:").append(customInstantKillLexicon.resolveOrderedStages().size())
                    .append("×").append(customInstantKillLexicon.repeatCount());
        }
        tag.putString("LexiconSummary", lexSummary.toString());
        host.duelEngine().writeDuelTelemetry(tag);

        if (hudTargetId != null) {
            AkashicDamageFlowchart.TargetDamageProfile profile = AkashicDamageFlowchart.getProfileIfPresent(hudTargetId);
            if (profile != null) {
                long nowTick = host.level() != null ? host.level().getGameTime() : 0L;
                double effectiveDps = profile.dpsTelemetry.effectiveDealtDps(nowTick);
                double ewmaDps = profile.dpsTelemetry.ewmaInstantDps(nowTick);
                double windowDps = profile.dpsTelemetry.slidingWindowDps(nowTick);
                double rawEttk = profile.dpsTelemetry.estimatedTtkSeconds(
                        nowTick, hudTargetHp, profile.healRatePerSec, 0.0F);
                double ettk = Double.isInfinite(rawEttk) ? -1.0D : rawEttk;

                tag.putDouble("DpsEffective", effectiveDps);
                tag.putDouble("DpsEwma", ewmaDps);
                tag.putDouble("DpsWindow", windowDps);
                tag.putDouble("DpsEttk", ettk);
                tag.putFloat("DetectedFloor", profile.floorConfirmed ? profile.maxFailedFloor : 0.0F);
                tag.putFloat("DetectedCap", profile.perHitCapConfirmed ? profile.perHitLimit
                        : (profile.ceilingConfirmed ? profile.maxPassedBelowCeiling : 0.0F));
                tag.putString("BestChannel", AkashicDamageFlowchart.channelName(profile.lastChannel));
            }
        }
    }

    private static String resolveTargetMod(Entity target) {
        String mod = CallerAttribution.modOf(target.getClass());
        if (CallerAttribution.isForeign(mod)) return mod;
        return net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType()) != null
                ? net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).getNamespace()
                : mod;
    }

    private static String describeEntity(LivingEntity entity) {
        return entity.getType().toShortString() + "#" + entity.getId();
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
