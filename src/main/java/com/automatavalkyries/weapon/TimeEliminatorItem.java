package com.automatavalkyries.weapon;

import com.automatavalkyries.config.WeaponConfig;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.registry.ModWeapons;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 因果切断剣『タイムエリミネイター』（§7.2 weapon_time_eliminator、T3）: 両手剣。攻撃力 75.0 / 攻撃速度 1.0、1 振り（当たり）100,000 FE。
 * 当てた相手の全バフ（有益な状態異常・衝撃吸収・無敵の印）を消し、3 秒の因果停止（行動不能）を掛ける。
 * 特性はオフハンドが空のとき（両手で持ったとき）だけ。プレイヤーへの強い状態異常は設定 {@code strongEffectsOnPlayers} のときだけ。
 * 保護対象（Eprotect のボス）は無敵の印を外さず、状態の改ざんも毎 Tick 戻されるので、一撃では倒せない（設計書 §1）。
 */
public class TimeEliminatorItem extends TacticalWeaponItem {

    public static final int CAPACITY = 2_000_000, PER_HIT = 100_000, STOP_TICKS = 60;
    public static final double DAMAGE = 400.0, DULL_DAMAGE = 50.0, SPEED = 1.0;
    public static final float WEIGHT = 15.0F;
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6F, 0.45F, 1.0F), 1.4F);

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public TimeEliminatorItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    private static Multimap<Attribute, AttributeModifier> modifiers(double damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage - 1.0, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        return com.automatavalkyries.item.ItemEnergy.get(stack) >= PER_HIT ? charged : dull;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getOffhandItem().isEmpty()) {
            if (attacker instanceof Player player && !attacker.level().isClientSide()) {
                player.displayClientMessage(Component.translatable("gui.automata_valkyries.two_handed").withStyle(ChatFormatting.RED), true);
            }
            return true;
        }
        if (!WeaponPower.use(attacker, stack, PER_HIT)) return true;
        if (attacker.level() instanceof ServerLevel level) {
            sever(level, target);
            playUse(attacker, stack);
        }
        return true;
    }

    /** 因果切断: 全バフを消し、因果停止を掛ける。兵姫に持たせたときも同じ処理 */
    public static void sever(ServerLevel level, LivingEntity target) {
        // 因果切断: 時計の数字が渦を巻いて散り、紫の衝撃波が広がる
        net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(target.getX(), target.getY(0.55), target.getZ());
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            net.minecraft.world.phys.Vec3 p = c.add(Math.cos(a) * 1.2, (i % 3 - 1) * 0.4, Math.sin(a) * 1.2);
            Fx.one(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLYPH.get(), Fx.LAVENDER, 0.28F, 30), p,
                    new net.minecraft.world.phys.Vec3(-Math.sin(a) * 0.08 + Math.cos(a) * 0.04, 0.02, Math.cos(a) * 0.08 + Math.sin(a) * 0.04));
        }
        Fx.shockwave(level, target.position().add(0, 0.05, 0), Fx.VIOLET, 4.0F, 14);
        Fx.shockwave(level, c, Fx.LAVENDER, 2.4F, 10);
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.LAVENDER, 0.35F, 10), c, 14, 0.35);
        Fx.slash(level, c, Fx.WHITE, 2.6F, 7, (float) (level.random.nextFloat() * Math.PI));
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.6F);
        if (target instanceof Player && !WeaponConfig.strongEffectsOnPlayers()) return;
        List<MobEffect> buffs = new ArrayList<>();
        for (MobEffectInstance effect : target.getActiveEffects()) {
            if (effect.getEffect().getCategory() == MobEffectCategory.BENEFICIAL) buffs.add(effect.getEffect());
        }
        buffs.forEach(target::removeEffect);
        target.setAbsorptionAmount(0);
        if (!(target instanceof IEprotected) && !(target instanceof Player)) target.setInvulnerable(false);
        target.addEffect(new MobEffectInstance(ModWeapons.CAUSAL_STOP.get(), STOP_TICKS, 0, false, true, true));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT)};
    }

    /** 因果停止: 動けない・攻撃できない・物を使えない（{@link WeaponEvents} で攻撃と使用を止める） */
    public static class CausalStop extends MobEffect {
        public CausalStop() {
            super(MobEffectCategory.HARMFUL, 0x9A8CFF);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, "4b7c1d2e-6a3f-4e8b-9c1d-2f3a4b5c6d7e", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(Attributes.FLYING_SPEED, "5c8d2e3f-7b4a-4f9c-8d2e-3a4b5c6d7e8f", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }

        @Override
        public void applyEffectTick(LivingEntity entity, int amplifier) {
            entity.setDeltaMovement(0, Math.min(0, entity.getDeltaMovement().y), 0);
            entity.hurtMarked = true;
            if (entity instanceof Mob mob) mob.getNavigation().stop();
            if (entity.isUsingItem()) entity.stopUsingItem();
            if (entity.level() instanceof ServerLevel level && entity.tickCount % 6 == 0) {
                Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLYPH.get(), Fx.LAVENDER, 0.2F, 24),
                        new net.minecraft.world.phys.Vec3(entity.getX(), entity.getY(0.7), entity.getZ()), 1, 0.5, 0.01);
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return true;
        }
    }
}
