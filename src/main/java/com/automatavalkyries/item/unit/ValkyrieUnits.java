package com.automatavalkyries.item.unit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 戦術兵姫の装備ユニット（2026-10-06 ユーザー指定「外装と背部ユニットとロジカルチップを作る」。全体仕様書 §7.3〜§7.5）。
 * 兵姫だけが使う（換装画面の 頭・胴・脚・背部・チップ ×3 の枠）。作るのはタクティカル・アーモリー（ティア付き）。数値は全体仕様書の物、無い物は【仮】
 */
public final class ValkyrieUnits {

    private ValkyrieUnits() {}

    // ------------------------------------------------------------------ 外装フレーム（§7.4）

    /**
     * 外装の型: 一式の装甲値・防護強度・ノックバック耐性・移動速度・重さ（t）・最大 HP・最大シールド。部位ごとに {@link FramePart#share} で分ける。
     * 装甲値・防護強度はバニラの上限（30 / 20）に素体の値（4 / 2）と足して収まるように抑え、硬さの差は HP とシールドで付ける
     * （2026-10-06 ユーザー指定「装甲値と防具強度は上限に抑えてシールドと体力を盛る」。HP・シールドの数は【仮】）
     */
    public enum FrameType {
        VALKYRIE("valkyrie", 1, 12, 6, 0.2, 0.0, 9.0F, 20, 30, 0.7F),
        // ヴァルキリーフレームを T2〜T4 へ上げていく基本装備（2026-10-06 ユーザー指定。前の Mk の同じ部位から作る。数は【仮】）
        VALKYRIE_MK2("valkyrie_mk2", 2, 16, 9, 0.3, 0.0, 9.0F, 40, 60, 0.65F),
        VALKYRIE_MK3("valkyrie_mk3", 3, 20, 13, 0.4, 0.0, 8.0F, 70, 100, 0.6F),
        VALKYRIE_MK4("valkyrie_mk4", 4, 24, 16, 0.5, 0.0, 7.0F, 110, 160, 0.5F),
        FORTRESS("fortress", 2, 22, 14, 1.0, 0.0, 25.0F, 60, 40, 1.0F),
        MIRAGE("mirage", 2, 8, 2, 0.0, 0.25, 6.0F, 10, 20, 1.0F),
        // 特殊なフレーム（2026-10-06 ユーザー指定「ヴァルキリーフレーム以外の特殊なフレームも各ティアに」。クロノスは保留。数・効果は【仮】）
        SCOUT("scout", 1, 6, 2, 0.0, 0.10, 5.0F, 10, 20, 1.0F),
        MAINTENANCE("maintenance", 1, 10, 4, 0.1, 0.0, 8.0F, 20, 10, 1.0F),
        SERAPH("seraph", 2, 10, 6, 0.1, 0.0, 7.0F, 40, 50, 1.0F),
        STRIKER("striker", 2, 14, 8, 0.5, 0.0, 10.0F, 30, 30, 1.0F),
        AEGIS("aegis", 3, 22, 14, 0.8, 0.0, 18.0F, 80, 150, 1.0F),
        TEMPEST("tempest", 3, 12, 6, 0.2, 0.0, 6.0F, 40, 80, 1.0F),
        HELIOS("helios", 3, 18, 10, 0.4, 0.0, 12.0F, 60, 100, 1.0F),
        NEMESIS("nemesis", 4, 26, 18, 0.6, 0.0, 14.0F, 150, 120, 1.0F),
        LUMINOUS("luminous", 4, 22, 16, 0.4, 0.0, 10.0F, 120, 140, 1.0F);

        public final String id;
        public final int tier;
        public final double armor, toughness, knockback, speed, health, shield;
        public final float weight;
        /** 一式のブースト移動の消費電力の倍率（ヴァルキリー系だけ） */
        public final float boostScale;

        FrameType(String id, int tier, double armor, double toughness, double knockback, double speed, float weight, double health, double shield, float boostScale) {
            this.id = id;
            this.tier = tier;
            this.armor = armor;
            this.toughness = toughness;
            this.knockback = knockback;
            this.speed = speed;
            this.weight = weight;
            this.health = health;
            this.shield = shield;
            this.boostScale = boostScale;
        }

        /** ヴァルキリーフレームの系統（Mk を混ぜても一式になる） */
        public boolean valkyrie() {
            return this == VALKYRIE || this == VALKYRIE_MK2 || this == VALKYRIE_MK3 || this == VALKYRIE_MK4;
        }
    }

    /** 部位: 頭・胴・脚（一式の能力の割合） */
    public enum FramePart {
        HEAD("head", 0.25F), CHEST("chest", 0.45F), LEGS("legs", 0.30F);

        public final String id;
        public final float share;

        FramePart(String id, float share) {
            this.id = id;
            this.share = share;
        }
    }

    public static class FrameItem extends Item {
        public final FrameType type;
        public final FramePart part;

        public FrameItem(Properties properties, FrameType type, FramePart part) {
            super(properties.stacksTo(1).rarity(type.tier >= 4 ? Rarity.EPIC : type.tier >= 3 ? Rarity.RARE : Rarity.UNCOMMON));
            this.type = type;
            this.part = part;
        }

        public double armor() { return type.armor * part.share; }
        public double toughness() { return type.toughness * part.share; }
        public double health() { return type.health * part.share; }
        public double shield() { return type.shield * part.share; }
        public float weight() { return type.weight * part.share; }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.valkyrie_only").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.frame.stats", fmt(armor()), fmt(toughness()), fmt(weight())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.frame.vitals", fmt(health()), fmt(shield())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.frame.set." + type.id).withStyle(ChatFormatting.GOLD));
            if (type.valkyrie()) tooltip.add(Component.translatable("gui.automata_valkyries.unit.frame.family_set").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------------ 背部ユニット（§7.3）

    /**
     * スラスターの機動: 着地までの空中ダッシュ・空中ジャンプの回数、滞空の長さ（Tick）、空中で付いて行く・飛ぶ敵と空中で戦うか。
     * 2026-10-06 ユーザー指定「ハイマニューバスラスターを各ティアに追加して、ダッシュ・浮遊・空中ジャンプでヴァルキリー要素を出す」。
     * 空中ダッシュは着地まで回数制限付きで続けて出せ、向きごとの 5 秒のクールタイムは掛けない（同日「A」）。数は【仮】
     */
    public record Maneuver(int airDashes, int airJumps, int hoverTicks, boolean airFollow, boolean airCombat, boolean evasive) {
        /** 回数・長さの制限なし（T4） */
        public static final int UNLIMITED = Integer.MAX_VALUE;
    }

    /** 背部ユニットの種類: 重さ（t）・1 回の消費 FE・ティア（スラスターは機動も） */
    public enum BackpackKind {
        // 2026-10-06 ユーザー指定で T1〜T3 を 1 つ上の性能へ上げ、T4 は空中ダッシュ・空中ジャンプ・滞空が無制限で、止まらずに敵を攪乱しながら飛ぶ
        THRUSTER("thruster", 1, 6.0F, 5_000, new Maneuver(3, 2, 60, false, false, false)),
        THRUSTER_MK2("thruster_mk2", 2, 7.0F, 6_000, new Maneuver(4, 2, 120, true, false, false)),
        THRUSTER_MK3("thruster_mk3", 3, 8.0F, 8_000, new Maneuver(5, 3, 200, true, true, false)),
        THRUSTER_MK4("thruster_mk4", 4, 6.0F, 10_000, new Maneuver(Maneuver.UNLIMITED, Maneuver.UNLIMITED, Maneuver.UNLIMITED, true, true, true)),
        FOLDING_CANNON("folding_cannon", 3, 20.0F, 50_000, null),
        ECM_POD("ecm_pod", 2, 8.0F, 80_000, null),
        // タキオンドライブは T3（2026-10-06 ユーザー指定。T4 から下げ、ボスの攻撃 60% 回避を「HP が尽きる 1 撃を無かったことにする」へ）
        TACHYON_DRIVE("tachyon_drive", 3, 4.0F, 20_000, null),
        MEMENTO_FAMILIAR("memento_familiar", 3, 7.0F, 30_000, null),
        // ガリア・フォートレスは全身の外装から、まわりを盾が回る背部ユニット（T3）へ（2026-10-06 ユーザー指定。重さは【仮】）
        GALLIA_FORTRESS("gallia_fortress", 3, 20.0F, 0, null),
        // 追加の背部ユニット（2026-10-06 ユーザー指定。T4 は強い補助。entity/ai/ValkyrieSupportUnits。数は【仮】）
        SUB_ARM("sub_arm", 1, 5.0F, 0, null),
        RECON_DRONE("recon_drone", 1, 3.0F, 500, null),
        SPARE_BATTERY("spare_battery", 1, 6.0F, 0, null),
        REPAIR_DRONE("repair_drone", 2, 6.0F, 2_000, null),
        MICRO_MISSILE("micro_missile", 2, 10.0F, 20_000, null),
        GRAPPLE_ANCHOR("grapple_anchor", 2, 7.0F, 3_000, null),
        PRISM_REFLECTOR("prism_reflector", 3, 9.0F, 10_000, null),
        ASTRAL_HALO("astral_halo", 4, 10.0F, 3_000, null),
        RESURRECTION_CORE("resurrection_core", 4, 8.0F, 50_000, null),
        ACCELERATOR("accelerator", 4, 7.0F, 4_000, null),
        COMMAND_LINK("command_link", 4, 6.0F, 0, null),
        // 非戦闘の背部ユニット（2026-10-29 ユーザー指定。付けていると戦わない。entity/ai/ValkyrieWork。数は【仮】）: fe は 1 回の仕事の消費
        HARVESTER("harvester", 1, 5.0F, 200, null),
        MINING_ARM("mining_arm", 2, 9.0F, 1_000, null),
        LUMBER_SAW("lumber_saw", 1, 7.0F, 800, null),
        FISHING_RIG("fishing_rig", 1, 4.0F, 300, null),
        FIELD_KITCHEN("field_kitchen", 2, 8.0F, 500, null);

        public final String id;
        public final int tier;
        public final float weight;
        /** 1 回の消費 FE（スラスターは空中ダッシュ・空中ジャンプ 1 回） */
        public final int fe;
        /** スラスターの機動（スラスターでなければ null） */
        @Nullable public final Maneuver maneuver;

        BackpackKind(String id, int tier, float weight, int fe, @Nullable Maneuver maneuver) {
            this.id = id;
            this.tier = tier;
            this.weight = weight;
            this.fe = fe;
            this.maneuver = maneuver;
        }
    }

    public static class BackpackItem extends Item {
        public final BackpackKind kind;

        public BackpackItem(Properties properties, BackpackKind kind) {
            super(properties.stacksTo(1).rarity(kind.tier >= 4 ? Rarity.EPIC : kind.tier >= 3 ? Rarity.RARE : Rarity.UNCOMMON));
            this.kind = kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.valkyrie_only").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.backpack.stats", fmt(kind.weight), String.format(Locale.ROOT, "%,d", kind.fe)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.backpack." + kind.id).withStyle(ChatFormatting.GOLD));
            if (com.automatavalkyries.entity.ai.ValkyrieWork.WORK_BACKPACKS.contains(kind)) tooltip.add(Component.translatable("gui.automata_valkyries.unit.non_combat").withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ ロジックチップ（§7.5）

    /**
     * ロジックチップの種類（重さ 0.5t【仮】、電力は働くときだけ【仮】）。
     * 役割のチップ（ヴァンガード・リアガード・メディック）は同時に 1 つだけ（2026-10-06 ユーザー指定）
     */
    public enum ChipKind {
        PRECISION("precision", 1, false), INTERCEPT("intercept", 2, false), ABSOLUTE("absolute", 3, false), RESOURCE_SAVER("resource_saver", 1, false),
        /** 前衛: 持ち主の前に立ち、持ち主を狙う敵を優先する */
        VANGUARD("vanguard", 1, true),
        /** 後衛: 持ち主の後ろから援護射撃に徹する（近接へ詰めない） */
        REARGUARD("rearguard", 1, true),
        /** 救護: 持ち主・味方の HP を見てナノマシン溶液で回復し、弾が無ければ弾薬・溶液を渡す */
        MEDIC("medic", 2, true),
        /** 通常攻撃だけ／チャージ攻撃だけ（しゃがみ右クリックで切り替え） */
        ATTACK_MODE("attack_mode", 1, false),
        /** 固定砲台: 覚えた対象を探し、通常・チャージ攻撃の最大射程に入ったら動かずに撃つ */
        TURRET("turret", 2, false),
        /** ポーター: 運搬のお手伝い（拾った物を運び先へ・チェスト間・持ち主の荷物持ち。entity/ai/ValkyriePorter） */
        PORTER("porter", 1, false),
        /** ノータッチ: 落ちている物を拾わない（2026-10-07 ユーザー指定「物を拾わない設定のチップ」。預かっている物の片付け・チェスト間の運搬はする） */
        NO_PICKUP("no_pickup", 1, false),
        /** スカベンジャー: 落ちている物を探して拾う（お掃除の範囲 2 倍・追従中も持ち主のまわりの物を拾う。2026-10-07 ユーザー指定「ノータッチの逆」）。ノータッチとは同時に挿せない */
        SCAVENGER("scavenger", 1, false),
        // 非戦闘のチップ（2026-10-29 ユーザー指定。付けていると戦わない。entity/ai/ValkyrieWork）
        /** ランチャー: 近くのチェストのえさで動物を増やし、羊の毛を刈る */
        RANCHER("rancher", 1, false),
        /** ランプライター: 暗い所に松明を置く */
        LAMPLIGHTER("lamplighter", 1, false),
        /** メンテナンス: 近くの持ち主の道具・防具を少しずつ直す */
        MAINTENANCE("maintenance", 2, false),
        /** ブレッシング: 近くの持ち主に採掘速度上昇・幸運 */
        BLESSING("blessing", 2, false),
        /** プロスペクター: 持ち主のまわりの鉱脈を知らせる */
        PROSPECTOR("prospector", 3, false);

        public final String id;
        public final int tier;
        /** 役割のチップ（同時に 1 つだけ） */
        public final boolean role;

        ChipKind(String id, int tier, boolean role) {
            this.id = id;
            this.tier = tier;
            this.role = role;
        }
    }

    /** 固定砲台のチップが覚えられる対象の数 */
    public static final int TURRET_TARGETS = 8;

    public static final float CHIP_WEIGHT = 0.5F;

    public static class ChipItem extends Item {
        public final ChipKind kind;

        public ChipItem(Properties properties, ChipKind kind) {
            super(properties.stacksTo(1).rarity(kind.tier >= 3 ? Rarity.RARE : Rarity.UNCOMMON));
            this.kind = kind;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.valkyrie_only").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.stats", fmt(CHIP_WEIGHT)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip." + kind.id).withStyle(ChatFormatting.GOLD));
            if (kind.role) tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.role_exclusive").withStyle(ChatFormatting.DARK_GRAY));
            if (com.automatavalkyries.entity.ai.ValkyrieWork.WORK_CHIPS.contains(kind)) tooltip.add(Component.translatable("gui.automata_valkyries.unit.non_combat").withStyle(ChatFormatting.RED));
            if (kind == ChipKind.ATTACK_MODE) {
                tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.attack_mode." + (chargeOnly(stack) ? "charge" : "normal")).withStyle(ChatFormatting.AQUA));
            }
            if (kind == ChipKind.PORTER) {
                int mode = porterMode(stack);
                tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.porter.mode",
                        Component.translatable("gui.automata_valkyries.unit.chip.porter.mode." + mode)).withStyle(ChatFormatting.AQUA));
                net.minecraft.core.BlockPos dest = porterPos(stack, "Dest", null), src = porterPos(stack, "Source", null);
                tooltip.add(dest == null ? Component.translatable("gui.automata_valkyries.unit.chip.porter.unset").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.translatable("gui.automata_valkyries.unit.chip.porter.dest", dest.getX(), dest.getY(), dest.getZ()).withStyle(ChatFormatting.GRAY));
                if (mode == 1 && src != null) tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.porter.source", src.getX(), src.getY(), src.getZ()).withStyle(ChatFormatting.GRAY));
            }
            if (kind == ChipKind.TURRET) {
                List<net.minecraft.world.entity.EntityType<?>> types = turretTargets(stack);
                if (types.isEmpty()) {
                    tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.turret.any").withStyle(ChatFormatting.AQUA));
                } else {
                    net.minecraft.network.chat.MutableComponent names = Component.empty();
                    for (int i = 0; i < types.size(); i++) {
                        if (i > 0) names.append(", ");
                        names.append(types.get(i).getDescription());
                    }
                    tooltip.add(Component.translatable("gui.automata_valkyries.unit.chip.turret.targets", names).withStyle(ChatFormatting.AQUA));
                }
            }
        }

        /** アタックセレクト: しゃがみ右クリックで 通常攻撃だけ ⇔ チャージ攻撃だけ。タレット: しゃがみ右クリックで覚えた対象を消す */
        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isShiftKeyDown() || (kind != ChipKind.ATTACK_MODE && kind != ChipKind.TURRET && kind != ChipKind.PORTER)) return super.use(level, player, hand);
            if (!level.isClientSide) {
                if (kind == ChipKind.PORTER) {
                    stack.getOrCreateTag().putInt("PorterMode", (porterMode(stack) + 1) % 3);
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.porter.mode",
                            Component.translatable("gui.automata_valkyries.unit.chip.porter.mode." + porterMode(stack))).withStyle(ChatFormatting.AQUA), true);
                } else if (kind == ChipKind.ATTACK_MODE) {
                    stack.getOrCreateTag().putBoolean("ChargeOnly", !chargeOnly(stack));
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.attack_mode." + (chargeOnly(stack) ? "charge" : "normal")).withStyle(ChatFormatting.AQUA), true);
                } else {
                    stack.removeTagKey("Targets");
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.turret.cleared").withStyle(ChatFormatting.AQUA), true);
                }
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        /**
         * ポーター: 入れ物を右クリックで運び先を覚える（チェスト間のモードでは、しゃがみで運び先・普通で運び元）。
         * ブロックの右クリック（チェストを開く）より先に呼ばれる onItemUseFirst で受ける（useOn では先にチェストが開いていた。2026-10-07）
         */
        @Override
        public net.minecraft.world.InteractionResult onItemUseFirst(ItemStack stack, net.minecraft.world.item.context.UseOnContext ctx) {
            if (kind != ChipKind.PORTER || ctx.getPlayer() == null) return net.minecraft.world.InteractionResult.PASS;
            Level level = ctx.getLevel();
            var player = ctx.getPlayer();
            if (!level.isClientSide) {
                var be = level.getBlockEntity(ctx.getClickedPos());
                if (com.automatavalkyries.entity.ai.ValkyriePorter.handlerOf(be) == null) { // どの面でも物を受け取れる入れ物（他 MOD の倉庫も。2026-10-30）
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.porter.not_container").withStyle(ChatFormatting.RED), true);
                } else {
                    ItemStack held = player.getItemInHand(ctx.getHand());
                    boolean source = porterMode(held) == 1 && !player.isShiftKeyDown();
                    String key = source ? "Source" : "Dest";
                    held.getOrCreateTag().putLong(key, ctx.getClickedPos().asLong());
                    held.getOrCreateTag().putString(key + "Dim", level.dimension().location().toString());
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.porter." + (source ? "set_source" : "set_dest")).withStyle(ChatFormatting.AQUA), true);
                    level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.8F);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }

        /** タレット: モブを右クリックでその種類を対象に覚える（プレイヤー・兵姫は覚えない） */
        @Override
        public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                                                         net.minecraft.world.entity.LivingEntity target, net.minecraft.world.InteractionHand hand) {
            if (kind != ChipKind.TURRET || player.isShiftKeyDown() || target instanceof net.minecraft.world.entity.player.Player
                    || target instanceof com.automatavalkyries.entity.ValkyrieEntity) return net.minecraft.world.InteractionResult.PASS;
            if (!player.level().isClientSide) {
                ItemStack held = player.getItemInHand(hand); // クリエイティブでは stack が写しなので、手の物を直接書き換える
                var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
                net.minecraft.nbt.ListTag list = held.getOrCreateTag().getList("Targets", net.minecraft.nbt.Tag.TAG_STRING);
                boolean known = false;
                for (int i = 0; i < list.size(); i++) known |= list.getString(i).equals(String.valueOf(id));
                if (!known && list.size() >= TURRET_TARGETS) {
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.turret.full").withStyle(ChatFormatting.RED), true);
                } else {
                    if (!known && id != null) list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
                    held.getOrCreateTag().put("Targets", list);
                    player.displayClientMessage(Component.translatable("gui.automata_valkyries.unit.chip.turret.added", target.getType().getDescription()).withStyle(ChatFormatting.AQUA), true);
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.8F);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide);
        }
    }

    /** ポーターの運び方（0 = 拾った物を運び先へ、1 = チェスト間、2 = 持ち主の荷物持ち） */
    public static int porterMode(ItemStack stack) {
        return stack.hasTag() ? Math.floorMod(stack.getTag().getInt("PorterMode"), 3) : 0;
    }

    /** ポーターが覚えた運び先（key = "Dest"）・運び元（"Source"）。level を渡せば同じディメンションの物だけ。無ければ null */
    @Nullable
    public static net.minecraft.core.BlockPos porterPos(ItemStack stack, String key, @Nullable Level level) {
        if (!stack.hasTag() || !stack.getTag().contains(key)) return null;
        if (level != null && !level.dimension().location().toString().equals(stack.getTag().getString(key + "Dim"))) return null;
        return net.minecraft.core.BlockPos.of(stack.getTag().getLong(key));
    }

    /** アタックセレクトがチャージ攻撃だけか（でなければ通常攻撃だけ） */
    public static boolean chargeOnly(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("ChargeOnly");
    }

    /** タレットが覚えた対象の種類（空なら敵対モブみな） */
    public static List<net.minecraft.world.entity.EntityType<?>> turretTargets(ItemStack stack) {
        List<net.minecraft.world.entity.EntityType<?>> out = new java.util.ArrayList<>();
        if (!stack.hasTag()) return out;
        net.minecraft.nbt.ListTag list = stack.getTag().getList("Targets", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(list.getString(i));
            var type = id == null ? null : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type != null && !out.contains(type)) out.add(type);
        }
        return out;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
    }
}
