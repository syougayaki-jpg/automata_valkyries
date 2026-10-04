package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

/**
 * 兵装のカスタムアップグレード（2026-10-04 ユーザー指定「バニラのエンチャントのように兵装の要素として」。素材の割り当ては【提案】）。兵装強化台（{@code upgrade/UpgradeStationBlock}）で、
 * 4 つの能力を 1 回 +10% ずつ、上限 +1000%（100 回）まで上げる。印は兵装の NBT {@code Upgrades}（能力ごとの回数）。
 * <ul>
 *   <li>攻撃力: 持ち主の与えるダメージ（利き手に持っている間。兵装の攻撃・技・通常攻撃・飛び道具のすべて）</li>
 *   <li>攻撃速度: 近接の攻撃速度（属性）</li>
 *   <li>チャージ・クールタイム: チャージ時間とクールタイム（技の待ち時間・連射の間隔）を 1 / (1 + 上げた分) に</li>
 *   <li>エネルギー容量: 内蔵バッテリーの容量</li>
 * </ul>
 * 1 回の材料: 兵装のティアのオーブ・スター・コアを 1 つ（T1・T2 はオーブ、T3 はスター、T4 はコア。3 系統・連携金属のどれでも。タグ {@code upgrade_orbs} など）と、
 * ティアの金属（兵装の組み立てレシピの金属: T1 アストライト・インゴット、T2 クロノ・フォートレス鋼、T3 ステラクロノダイム・インゴット、T4 ステラクロノダイム・ブロック）を
 * 10 個から、その能力を上げるたびに 1.1 倍（切り上げ）。金属は持ち物から引く。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class WeaponUpgrades {

    public static final String TAG = "Upgrades";
    public static final int MAX_LEVEL = 100, BASE_METAL = 10;
    public static final double STEP = 0.10, METAL_GROWTH = 1.1;
    private static final UUID SPEED_ID = UUID.fromString("3d6f0c51-8a2e-4b7d-9c14-6e2a58f1b0d9");

    public enum Stat {
        DAMAGE("damage", 0xFF6A50), SPEED("speed", 0xFFD060), CHARGE("charge", 0x60D8FF), ENERGY("energy", 0x90FF70);

        public final String id;
        public final int color;

        Stat(String id, int color) {
            this.id = id;
            this.color = color;
        }

        public Component title() {
            return Component.translatable("upgrade.automata_valkyries." + id);
        }
    }

    private WeaponUpgrades() {}

    // ---- 回数と倍率 ----

    public static int level(ItemStack stack, Stat stat) {
        return stack.hasTag() ? stack.getTag().getCompound(TAG).getInt(stat.id) : 0;
    }

    public static void setLevel(ItemStack stack, Stat stat, int level) {
        CompoundTag tag = stack.getOrCreateTag().getCompound(TAG);
        tag.putInt(stat.id, Math.max(0, Math.min(MAX_LEVEL, level)));
        stack.getOrCreateTag().put(TAG, tag);
    }

    /** 倍率（1 + 0.1 × 回数。上限 11 倍） */
    public static double mult(ItemStack stack, Stat stat) {
        return 1.0 + STEP * level(stack, stat);
    }

    /** 時間を縮める（チャージ・クールタイム。1 Tick より短くはしない） */
    public static int ticks(ItemStack stack, int ticks) {
        if (ticks <= 0) return ticks;
        return Math.max(1, (int) Math.round(ticks / mult(stack, Stat.CHARGE)));
    }

    /** プレイヤーが持っている、その兵装のクールタイムを縮める（{@code player.getCooldowns().addCooldown} の前に通す） */
    public static int cooldown(Player player, Item item, int ticks) {
        ItemStack held = player.getMainHandItem().is(item) ? player.getMainHandItem() : player.getOffhandItem().is(item) ? player.getOffhandItem() : ItemStack.EMPTY;
        return held.isEmpty() ? ticks : ticks(held, ticks);
    }

    // ---- 材料 ----

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path));
    }

    public static final TagKey<Item> ORBS = tag("upgrade_orbs"), STARS = tag("upgrade_stars"), CORES = tag("upgrade_cores");
    private static final TagKey<Item> ASTRITE = TagKey.create(Registries.ITEM, ResourceLocation.parse("forge:ingots/astrite"));

    /** 兵装のティアのオーブ・スター・コア（T1・T2 はオーブ、T3 はスター、T4 はコア） */
    public static TagKey<Item> catalyst(int tier) {
        return tier >= 4 ? CORES : tier == 3 ? STARS : ORBS;
    }

    /** ティアの金属か */
    public static boolean isMetal(int tier, ItemStack stack) {
        return switch (tier) {
            case 1 -> stack.is(ASTRITE);
            default -> stack.is(metalItem(tier));
        };
    }

    /** ティアの金属（表示用。T1 はアストライト・インゴット） */
    public static Item metalItem(int tier) {
        String id = switch (tier) {
            case 2 -> "alloy_chrono_fortress";
            case 3 -> "ingot_stellachronodyme";
            case 4 -> "block_stellachronodyme";
            default -> "ingot_astrite";
        };
        return ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, id));
    }

    /** 次の 1 回に要る金属の数（10 × 1.1^今の回数、切り上げ） */
    public static int metalCost(int currentLevel) {
        return (int) Math.ceil(BASE_METAL * Math.pow(METAL_GROWTH, currentLevel) - 1.0E-9);
    }

    /** 持ち物にあるティアの金属の数 */
    public static int metalOwned(Inventory inventory, int tier) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (isMetal(tier, s)) n += s.getCount();
        }
        return n;
    }

    public static boolean upgradeable(ItemStack stack) {
        return stack.getItem() instanceof TacticalWeaponItem && !(stack.getItem() instanceof RequiemBackdropItem);
    }

    /** 上げられない理由（上げられるなら null） */
    public static Component problem(Player player, ItemStack weapon, ItemStack catalyst, Stat stat) {
        if (!upgradeable(weapon)) return Component.translatable("upgrade.automata_valkyries.need_weapon");
        int level = level(weapon, stat);
        if (level >= MAX_LEVEL) return Component.translatable("upgrade.automata_valkyries.max");
        int tier = WeaponTiers.of(weapon.getItem());
        if (player.getAbilities().instabuild) return null;
        if (!catalyst.is(catalyst(tier))) return Component.translatable("upgrade.automata_valkyries.need_catalyst." + (tier >= 4 ? "core" : tier == 3 ? "star" : "orb"));
        if (metalOwned(player.getInventory(), tier) < metalCost(level)) return Component.translatable("upgrade.automata_valkyries.need_metal", metalCost(level));
        return null;
    }

    /** 1 回上げる（材料を引く。クリエイティブは材料なし）。上げたか */
    public static boolean upgrade(Player player, ItemStack weapon, ItemStack catalyst, Stat stat) {
        if (problem(player, weapon, catalyst, stat) != null) return false;
        int level = level(weapon, stat);
        if (!player.getAbilities().instabuild) {
            int tier = WeaponTiers.of(weapon.getItem());
            catalyst.shrink(1);
            int left = metalCost(level);
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (!isMetal(tier, s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
        setLevel(weapon, stat, level + 1);
        return true;
    }

    // ---- 効き目 ----

    /** 攻撃力: 与えるダメージに掛ける */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().is(WeaponDamage.OVERLOAD_REND)) return; // 割合ダメージはそのまま
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            int level = level(attacker.getMainHandItem(), Stat.DAMAGE);
            if (level > 0) event.setAmount((float) (event.getAmount() * (1.0 + STEP * level)));
        }
    }

    /** 攻撃速度: 利き手の兵装の攻撃速度に掛ける */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;
        int level = level(event.getItemStack(), Stat.SPEED);
        if (level > 0) {
            event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, "Weapon upgrade", STEP * level, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 説明の行（上げた能力だけ） */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        for (Stat stat : Stat.values()) {
            int level = level(stack, stat);
            if (level <= 0) continue;
            tooltip.add(Component.translatable("upgrade.automata_valkyries.line", stat.title(), level * 10, level)
                    .withStyle(s -> s.withColor(stat.color)));
        }
    }

}
