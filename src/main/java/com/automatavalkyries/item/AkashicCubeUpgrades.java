package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 観測の立方体の強化（2026-10-29 ユーザー指定「観測の立方体に強化要素を追加して強化限界ティアごとにMOD独自の鉱石以外の各鉱石のオーブやスターやコアに応じて強化できる
 * 射程ダメージ攻撃間隔追尾性など」。能力と金属の割り当て・数値は【提案】）。
 * 金床の左に観測の立方体、右に連携金属（このMOD独自でない 8 種の鉱石）のオーブ・スター・コアを置くと、金属ごとの能力が 1 個につき 1 段上がる。
 * 強化の限界はティアで決まる: オーブで {@value #ORB_CAP} 段まで、スターで {@value #STAR_CAP} 段まで、コアで {@value #MAX_LEVEL} 段まで（上のティアで下の段を上げてもよい）。
 * 印は立方体の NBT {@code CubeUpgrades}（能力ごとの段）。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class AkashicCubeUpgrades {

    public static final String TAG = "CubeUpgrades";
    public static final int ORB_CAP = 5, STAR_CAP = 10, MAX_LEVEL = 15;
    /** 1 段の金床の経験値 */
    public static final int XP_PER_LEVEL = 2;

    /** 能力（金属・色）。数値は {@link AkashicCubesItem} の基本値から */
    public enum Stat {
        RANGE("range", "tin", 0x9FD8FF),       // 射程
        DAMAGE("damage", "silver", 0xFF6A80),  // ダメージ
        RATE("rate", "zinc", 0xFFD060),        // 攻撃間隔
        HOMING("homing", "nickel", 0xB07CFF),  // 追尾性
        SPEED("speed", "aluminum", 0x80FFE0),  // 弾の速さ
        PIERCE("pierce", "osmium", 0x6FA8FF),  // 貫通
        SPLASH("splash", "lead", 0xC0A0FF),    // 着弾の範囲
        MULTI("multi", "uranium", 0x90FF70);   // 同時に狙う数

        public final String id, metal;
        public final int color;

        Stat(String id, String metal, int color) {
            this.id = id;
            this.metal = metal;
            this.color = color;
        }

        public Component title() {
            return Component.translatable("cube_upgrade.automata_valkyries." + id);
        }
    }

    private AkashicCubeUpgrades() {}

    // ---- 段 ----

    public static int level(ItemStack stack, Stat stat) {
        return stack.hasTag() ? Math.min(MAX_LEVEL, stack.getTag().getCompound(TAG).getInt(stat.id)) : 0;
    }

    public static void setLevel(ItemStack stack, Stat stat, int level) {
        CompoundTag tag = stack.getOrCreateTag().getCompound(TAG);
        tag.putInt(stat.id, Math.max(0, Math.min(MAX_LEVEL, level)));
        stack.getOrCreateTag().put(TAG, tag);
    }

    // ---- 数値（段 → 実際の値） ----

    /** 射程: 12 m から 1 段 +2 m（最大 42 m） */
    public static double range(ItemStack s) {
        return AkashicCubesItem.RANGE + 2.0 * level(s, Stat.RANGE);
    }

    /** ダメージ: 5 から、オーブの段 +3・スターの段 +8・コアの段 +25（5 段で 20、10 段で 60、15 段で 185） */
    public static float damage(ItemStack s) {
        int lv = level(s, Stat.DAMAGE);
        return AkashicCubesItem.DAMAGE + 3 * Math.min(lv, ORB_CAP) + 8 * Math.max(0, Math.min(lv, STAR_CAP) - ORB_CAP) + 25 * Math.max(0, lv - STAR_CAP);
    }

    /** 攻撃間隔（Tick）: 30 Tick を 1 / (1 + 0.2 × 段) に（最大で 8 Tick） */
    public static int interval(ItemStack s) {
        return Math.max(4, Math.round(AkashicCubesItem.INTERVAL / (1.0F + 0.2F * level(s, Stat.RATE))));
    }

    /** 追尾性: 1 Tick に向きを相手へ曲げる割合（0 = 追わない。1 段 0.04、最大 0.6） */
    public static float homing(ItemStack s) {
        int lv = level(s, Stat.HOMING);
        return lv <= 0 ? 0.0F : Math.min(0.6F, 0.04F * lv);
    }

    /** 弾の速さ: 1.0 から 1 段 +0.15（最大 3.25） */
    public static float speed(ItemStack s) {
        return 1.0F + 0.15F * level(s, Stat.SPEED);
    }

    /** 貫通: 何体まで突き抜けるか（1 段 1 体） */
    public static int pierce(ItemStack s) {
        return level(s, Stat.PIERCE);
    }

    /** 着弾の範囲: 当たった所のまわり半径（1 段 0.4 m、最大 6 m）の敵にも半分のダメージ */
    public static float splash(ItemStack s) {
        return 0.4F * level(s, Stat.SPLASH);
    }

    /** 同時に狙う数: 1 体から 3 段ごとに +1（最大 6 体） */
    public static int targets(ItemStack s) {
        return 1 + level(s, Stat.MULTI) / 3;
    }

    // ---- 材料 ----

    /** 右に置いた物の金属の能力（連携金属のオーブ・スター・コアでなければ null） */
    @Nullable
    public static Stat statOf(ItemStack catalyst) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(catalyst.getItem());
        if (id == null || !id.getNamespace().equals(AutomataValkyries.MODID)) return null;
        for (Stat stat : Stat.values()) {
            for (String kind : new String[] {"orb", "star", "core"}) if (id.getPath().equals(kind + "_" + stat.metal)) return stat;
        }
        return null;
    }

    /** 右に置いた物で上げられる段の上限（オーブ 5・スター 10・コア 15。違う物なら 0） */
    public static int capOf(ItemStack catalyst) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(catalyst.getItem());
        if (id == null || statOf(catalyst) == null) return 0;
        return id.getPath().startsWith("core_") ? MAX_LEVEL : id.getPath().startsWith("star_") ? STAR_CAP : ORB_CAP;
    }

    /** 金属のオーブ・スター・コア（kind は orb / star / core） */
    public static Item catalyst(String kind, Stat stat) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, kind + "_" + stat.metal));
        return item == null ? Items.AIR : item;
    }

    /** 金床の結果（上げられなければ空）。右の物を何個使うかは {@link #consumed} */
    public static ItemStack result(ItemStack cubes, ItemStack catalyst) {
        int n = consumed(cubes, catalyst);
        if (n <= 0) return ItemStack.EMPTY;
        Stat stat = statOf(catalyst);
        ItemStack out = cubes.copy();
        out.setCount(1);
        setLevel(out, stat, level(cubes, stat) + n);
        return out;
    }

    /** 右の物を何個使うか（1 個 1 段。ティアの上限まで） */
    public static int consumed(ItemStack cubes, ItemStack catalyst) {
        if (!(cubes.getItem() instanceof AkashicCubesItem) || cubes.getCount() != 1) return 0;
        Stat stat = statOf(catalyst);
        if (stat == null) return 0;
        return Math.max(0, Math.min(catalyst.getCount(), capOf(catalyst) - level(cubes, stat)));
    }

    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        int n = consumed(event.getLeft(), event.getRight());
        if (n <= 0) return;
        ItemStack out = result(event.getLeft(), event.getRight());
        String name = event.getName();
        if (name != null && !name.isBlank() && !name.equals(out.getHoverName().getString())) out.setHoverName(Component.literal(name));
        event.setOutput(out);
        event.setMaterialCost(n);
        event.setCost(Math.max(1, XP_PER_LEVEL * n));
    }

    // ---- 説明 ----

    public static void appendTooltip(ItemStack s, List<Component> tooltip) {
        tooltip.add(Component.translatable("cube_upgrade.automata_valkyries.stats",
                String.format("%.0f", range(s)), String.format("%.0f", damage(s)), String.format("%.2f", interval(s) / 20.0)).withStyle(ChatFormatting.AQUA));
        for (Stat stat : Stat.values()) {
            int lv = level(s, stat);
            if (lv <= 0) continue;
            tooltip.add(Component.translatable("cube_upgrade.automata_valkyries.line", stat.title(), lv, MAX_LEVEL, value(s, stat)).withStyle(st -> st.withColor(stat.color)));
        }
        tooltip.add(Component.translatable("cube_upgrade.automata_valkyries.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 能力の今の値（説明用） */
    public static String value(ItemStack s, Stat stat) {
        return switch (stat) {
            case RANGE -> String.format("%.0f m", range(s));
            case DAMAGE -> String.format("%.0f", damage(s));
            case RATE -> String.format("%.2f s", interval(s) / 20.0);
            case HOMING -> String.format("%.0f%%", homing(s) * 100);
            case SPEED -> String.format("x%.2f", speed(s));
            case PIERCE -> String.valueOf(pierce(s));
            case SPLASH -> String.format("%.1f m", splash(s));
            case MULTI -> String.valueOf(targets(s));
        };
    }
}
