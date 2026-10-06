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
        VALKYRIE("valkyrie", 1, 12, 6, 0.2, 0.0, 9.0F, 20, 30),
        FORTRESS("fortress", 2, 22, 14, 1.0, 0.0, 25.0F, 60, 40),
        MIRAGE("mirage", 2, 8, 2, 0.0, 0.25, 6.0F, 10, 20),
        GALLIA("gallia_fortress", 4, 26, 18, 1.0, 0.0, 40.0F, 120, 100);

        public final String id;
        public final int tier;
        public final double armor, toughness, knockback, speed, health, shield;
        public final float weight;

        FrameType(String id, int tier, double armor, double toughness, double knockback, double speed, float weight, double health, double shield) {
            this.id = id;
            this.tier = tier;
            this.armor = armor;
            this.toughness = toughness;
            this.knockback = knockback;
            this.speed = speed;
            this.weight = weight;
            this.health = health;
            this.shield = shield;
        }
    }

    /** 部位: 頭・胴・脚（一式の能力の割合）。ガリア・フォートレスは全身で 1 つ（胴の枠に入れ、頭・脚の枠を塞ぐ） */
    public enum FramePart {
        HEAD("head", 0.25F), CHEST("chest", 0.45F), LEGS("legs", 0.30F), FULL("full", 1.0F);

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
        }
    }

    // ------------------------------------------------------------------ 背部ユニット（§7.3）

    /**
     * スラスターの機動: 着地までの空中ダッシュ・空中ジャンプの回数、滞空の長さ（Tick）、空中で付いて行く・飛ぶ敵と空中で戦うか。
     * 2026-10-06 ユーザー指定「ハイマニューバスラスターを各ティアに追加して、ダッシュ・浮遊・空中ジャンプでヴァルキリー要素を出す」。
     * 空中ダッシュは着地まで回数制限付きで続けて出せ、向きごとの 5 秒のクールタイムは掛けない（同日「A」）。数は【仮】
     */
    public record Maneuver(int airDashes, int airJumps, int hoverTicks, boolean airFollow, boolean airCombat) {}

    /** 背部ユニットの種類: 重さ（t）・1 回の消費 FE・ティア（スラスターは機動も） */
    public enum BackpackKind {
        THRUSTER("thruster", 1, 6.0F, 5_000, new Maneuver(3, 1, 0, false, false)),
        THRUSTER_MK2("thruster_mk2", 2, 7.0F, 6_000, new Maneuver(3, 2, 60, false, false)),
        THRUSTER_MK3("thruster_mk3", 3, 8.0F, 8_000, new Maneuver(4, 2, 120, true, false)),
        THRUSTER_MK4("thruster_mk4", 4, 6.0F, 10_000, new Maneuver(5, 3, 200, true, true)),
        FOLDING_CANNON("folding_cannon", 3, 20.0F, 50_000, null),
        ECM_POD("ecm_pod", 2, 8.0F, 80_000, null),
        TACHYON_DRIVE("tachyon_drive", 4, 4.0F, 20_000, null),
        MEMENTO_FAMILIAR("memento_familiar", 3, 7.0F, 30_000, null);

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
        }
    }

    // ------------------------------------------------------------------ ロジックチップ（§7.5）

    /** ロジックチップの種類（重さ 0.5t【仮】、電力は働くときだけ【仮】） */
    public enum ChipKind {
        PRECISION("precision", 1), INTERCEPT("intercept", 2), ABSOLUTE("absolute", 3), RESOURCE_SAVER("resource_saver", 1);

        public final String id;
        public final int tier;

        ChipKind(String id, int tier) {
            this.id = id;
            this.tier = tier;
        }
    }

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
        }
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
    }
}
