package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 蒸気スラスター（追加拡張仕様書 §8.3 backpack_steam_thruster）: 背部ユニット。プレイヤーは胴に着ける。
 * 水入り瓶と石炭（木炭）を装填すると蒸気が溜まり、空中でジャンプキーをもう一度押すと蒸気を噴いて 1.5 ブロックほど緊急ステップする。
 * 装填はスニークしながら右クリック（瓶 1 本 + 石炭 1 個で蒸気 4 回分、最大 16 回分）。
 */
public class SteamThrusterItem extends ArmorItem {

    public static final int MAX_STEAM = 16, STEAM_PER_LOAD = 4;
    private static final String TAG = "Steam";

    public SteamThrusterItem(Properties properties) {
        super(MATERIAL, Type.CHESTPLATE, properties);
    }

    public static int steam(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().getInt(TAG);
    }

    private static void setSteam(ItemStack stack, int value) {
        stack.getOrCreateTag().putInt(TAG, Math.max(0, Math.min(MAX_STEAM, value)));
    }

    /** 胴に着けた蒸気スラスター（無ければ空） */
    public static ItemStack worn(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.getItem() instanceof SteamThrusterItem ? chest : ItemStack.EMPTY;
    }

    /** ステップの速度（向いている水平方向へ、少し上向き）。クライアントで動かし、サーバーで蒸気を減らす */
    public static Vec3 thrust(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        return new Vec3(flat.x * 0.95, 0.42, flat.z * 0.95);
    }

    /** サーバー: 蒸気を 1 回分使う（クリエイティブは減らない） */
    public static boolean consume(ServerPlayer player) {
        ItemStack stack = worn(player);
        if (stack.isEmpty()) return false;
        if (!player.getAbilities().instabuild) {
            if (steam(stack) <= 0) return false;
            setSteam(stack, steam(stack) - 1);
        }
        player.fallDistance = 0;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.9, player.getZ(), 14, 0.25, 0.2, 0.25, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        if (steam(stack) + STEAM_PER_LOAD > MAX_STEAM) return InteractionResultHolder.fail(stack);
        int water = find(player, true), coal = find(player, false);
        if (water < 0 || coal < 0) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("item.automata_valkyries.backpack_steam_thruster.need"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            player.getInventory().getItem(water).shrink(1);
            player.getInventory().getItem(coal).shrink(1);
            if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            setSteam(stack, steam(stack) + STEAM_PER_LOAD);
            level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static int find(Player player, boolean water) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (water ? stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER : stack.is(ItemTags.COALS)) return i;
        }
        return -1;
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13.0F * steam(stack) / MAX_STEAM); }
    @Override public int getBarColor(ItemStack stack) { return 0xDDE6EE; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.backpack_steam_thruster.steam", steam(stack), MAX_STEAM).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.backpack_steam_thruster.desc").withStyle(ChatFormatting.GRAY));
    }

    /** 革の胴より少し硬い程度の守り。見た目は textures/models/armor/steam_thruster_layer_1.png */
    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 200; }
        @Override public int getDefenseForType(Type type) { return 3; }
        @Override public int getEnchantmentValue() { return 8; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.COPPER_INGOT); }
        @Override public String getName() { return "automata_valkyries:steam_thruster"; }
        @Override public float getToughness() { return 0.0F; }
        @Override public float getKnockbackResistance() { return 0.0F; }
    };
}
