package com.automatavalkyries.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * モーターブレード（追加拡張仕様書 §8.3 weapon_motor_blade）: 鉄の剣 + モーター。
 * 電力があれば攻撃力 9.0 の高周波切削で、1 振り（当たり）ごとに 50 FE を使う。電力が無いとただの鈍い刃（攻撃力 3.0）。
 * 耐久は減らず、電力のバーを出す。蓄電器の充電スロットで充電する。
 */
public class MotorBladeItem extends SwordItem {

    public static final int CAPACITY = 10_000, TRANSFER = 500, PER_HIT = 50;
    /** 攻撃力（プレイヤーの素の 1 を含む合計）と攻撃速度 */
    public static final double CHARGED_DAMAGE = 9.0, DULL_DAMAGE = 3.0, SPEED = 1.6;

    private final Multimap<Attribute, AttributeModifier> charged, dull;

    public MotorBladeItem(Properties properties) {
        super(Tiers.IRON, 0, -2.4F, properties);
        charged = modifiers(CHARGED_DAMAGE);
        dull = modifiers(DULL_DAMAGE);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    public static boolean isCharged(ItemStack stack) {
        return ItemEnergy.get(stack) >= PER_HIT;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return isCharged(stack) ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean creative = attacker instanceof Player player && player.getAbilities().instabuild;
        if (creative || ItemEnergy.use(stack, PER_HIT)) {
            if (attacker.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                level.playSound(null, target.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
            }
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ItemEnergy.provider(stack, CAPACITY, TRANSFER);
    }

    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return ItemEnergy.barWidth(stack, CAPACITY); }
    @Override public int getBarColor(ItemStack stack) { return ItemEnergy.barColor(stack, CAPACITY); }
    @Override public boolean isValidRepairItem(ItemStack stack, ItemStack repair) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ItemEnergy.tooltip(stack, CAPACITY));
        tooltip.add(Component.translatable("item.automata_valkyries.weapon_motor_blade.desc", PER_HIT).withStyle(ChatFormatting.GRAY));
    }
}
