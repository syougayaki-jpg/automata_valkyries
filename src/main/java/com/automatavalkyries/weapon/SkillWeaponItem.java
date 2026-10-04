package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 右クリックで技を出す兵装の共通部分（追加の兵装 第 3 弾、2026-10-03）: 近接の攻撃力・攻撃速度・間合い、技の電力・間隔、両手持ちの確認。
 * 技（{@link #skill}）が出せなかったとき（狙う相手がいない等）は電力も間隔も使わない。
 */
public abstract class SkillWeaponItem extends TacticalWeaponItem {

    private static final UUID REACH_UUID = UUID.fromString("7c8d9eaf-b0c1-4d2e-8f3a-4b5c6d7e8f90");

    protected final int perUse, cooldown;
    private final @Nullable Multimap<Attribute, AttributeModifier> modifiers;

    /** damage が 0 なら近接の能力は付けない */
    protected SkillWeaponItem(Properties properties, int capacity, float weight, boolean twoHanded,
                              double damage, double speed, double reach, int perUse, int cooldown) {
        super(properties, capacity, weight, twoHanded);
        this.perUse = perUse;
        this.cooldown = cooldown;
        if (damage <= 0) {
            this.modifiers = null;
        } else {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION));
            b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", speed - 4.0, AttributeModifier.Operation.ADDITION));
            if (reach > 0) b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID, "Weapon reach", reach, AttributeModifier.Operation.ADDITION));
            this.modifiers = b.build();
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND && modifiers != null ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    /** 技を出す（サーバーで）。出せなかったら false */
    protected abstract boolean skill(ServerLevel level, LivingEntity user, ItemStack stack);

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, perUse)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            if (!skill(server, player, stack)) return InteractionResultHolder.fail(stack);
            WeaponPower.use(player, stack, perUse);
            if (cooldown > 0) player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, cooldown));
            playUse(player, stack);
        }
        if (recoil() > 0) clientKick(player);
        else player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(perUse)};
    }
}
