package com.automatavalkyries.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 光速剣『ルクシオンセイバー』（§7.2 weapon_luxion_saber、T2）: 片手剣。攻撃力 28.0 / 攻撃速度 3.5、1 振り（当たり）15,000 FE。
 * 無敵時間を無視する（当てた相手の無敵時間を消すので、続けて振ると毎回入る）。
 * 右クリックで、向いた方の 15 ブロック以内（壁の向こうは除く）の敵へ瞬時に空間跳躍して斬り抜け、相手の向こう側に出る（15,000 FE、間隔 0.5 秒【提案】）。
 */
public class LuxionSaberItem extends TacticalWeaponItem {

    public static final int CAPACITY = 300_000, PER_HIT = 15_000, BLINK_COOLDOWN = 10;
    public static final double DAMAGE = 28.0, DULL_DAMAGE = 6.0, SPEED = 3.5, BLINK_RANGE = 15.0;
    public static final float WEIGHT = 2.5F;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.55F), 1.2F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.5F);

    private final Multimap<Attribute, AttributeModifier> charged = modifiers(DAMAGE), dull = modifiers(DULL_DAMAGE);

    public LuxionSaberItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
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
        if (!WeaponPower.use(attacker, stack, PER_HIT)) return true;
        target.invulnerableTime = 0; // 無敵時間を無視
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 hit = new Vec3(target.getX(), target.getY(0.6), target.getZ());
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.PALE_GOLD, 0.3F, 8), hit, 8, 0.3);
            Fx.slash(level, hit, Fx.GOLD, 1.4F, 5, (float) (level.random.nextFloat() * Math.PI * 2));
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, 2.0F);
            playUse(attacker, stack);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_HIT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        LivingEntity target = findTarget(player);
        if (target == null) return InteractionResultHolder.pass(stack);
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, BLINK_COOLDOWN));
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_HIT);
            blink(server, player, target, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 向いた方（前方 30 度）の 15 ブロック以内で、見えている一番近い敵 */
    @Nullable
    private static LivingEntity findTarget(LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(BLINK_RANGE),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double d = to.length();
            if (d > BLINK_RANGE + e.getBbWidth() || to.normalize().dot(look) < 0.86) continue;
            if (!user.hasLineOfSight(e)) continue; // 壁の向こうへは跳ばない
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    /** 相手の向こう側へ跳び、すれ違いざまに斬る。兵姫に持たせたときも同じ処理 */
    public static void blink(ServerLevel level, LivingEntity user, LivingEntity target, ItemStack stack) {
        Vec3 from = user.position();
        Vec3 dir = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z).normalize();
        double past = target.getBbWidth() * 0.5 + user.getBbWidth() * 0.5 + 1.0;
        Vec3 to = new Vec3(target.getX(), target.getY(), target.getZ()).add(dir.scale(past));
        // 抜けた先が埋まるなら、相手の手前に出る
        if (!level.noCollision(user, user.getBoundingBox().move(to.subtract(from)))) to = target.position().subtract(dir.scale(past));
        if (!level.noCollision(user, user.getBoundingBox().move(to.subtract(from)))) to = from;
        // 光の軌跡
        Vec3 mid = new Vec3(0, user.getBbHeight() * 0.55, 0);
        double length = to.distanceTo(from);
        // 光の軌跡: 白い芯と金の帯、こぼれるきらめき、出発と到着の衝撃波
        if (length > 0.1) {
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.WHITE, 0.35F, 14), from.add(mid), to.add(mid), 0.3);
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.GOLD, 0.6F, 18), from.add(mid), to.add(mid), 0.45);
            for (double d = 0; d <= length; d += 0.7) {
                Vec3 p = from.add(to.subtract(from).normalize().scale(d)).add(mid);
                Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.PALE_GOLD, 0.2F, 12), p, 1, 0.08);
            }
        }
        Fx.shockwave(level, from.add(0, 0.05, 0), Fx.GOLD, 2.2F, 10);
        Fx.shockwave(level, to.add(0, 0.05, 0), Fx.PALE_GOLD, 3.0F, 12);
        user.teleportTo(to.x, to.y, to.z);
        target.invulnerableTime = 0;
        DamageSource source = user instanceof Player player ? user.damageSources().playerAttack(player) : user.damageSources().mobAttack(user);
        target.hurt(source, (float) DAMAGE);
        Vec3 hit = new Vec3(target.getX(), target.getY(0.55), target.getZ());
        Fx.slash(level, hit, Fx.WHITE, 2.2F, 7, (float) Math.atan2(dir.z, dir.x));
        Fx.slash(level, hit, Fx.GOLD, 2.8F, 8, (float) (Math.atan2(dir.z, dir.x) + 0.5));
        Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.WHITE, 0.35F, 10), hit, 14, 0.4);
        level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.8F);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.6F);
        if (stack.getItem() instanceof LuxionSaberItem saber) saber.playUse(user, stack);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_HIT)};
    }
}
