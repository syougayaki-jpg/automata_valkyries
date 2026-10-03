package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * プリズムビームライフル（T2、追加の兵装。2026-10-03 こちらが考えた）: 両手の連続照射ライフル。右クリックを押している間、虹色の光線を出し続ける
 * （射程 40、線上 8 体まで、毎 Tick 1,500 FE）。0.1 秒ごとに 2 ダメージで、照射し続けるほど強くなる（3 秒で 3 倍）。
 * 最初に当たった敵で光が分かれ（プリズム）、8 ブロック以内の別の敵 3 体へ半分のダメージの光が走る。
 */
public class PrismBeamRifleItem extends TacticalWeaponItem {

    public static final int CAPACITY = 1_500_000, PER_TICK = 1_500, PULSE = 2, RAMP_TICKS = 60, SPLITS = 3;
    public static final float DAMAGE = 2.0F, WEIGHT = 9.0F;
    public static final double RANGE = 40.0, WIDTH = 0.6, SPLIT_RANGE = 8.0;

    public PrismBeamRifleItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, true);
    }

    @Override
    public float recoil() {
        return 0.12F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canWield(player, hand)) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_TICK)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (level instanceof ServerLevel) level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int t = getUseDuration(stack) - remaining;
        if (!(level instanceof ServerLevel server)) {
            if (t % 4 == 0) clientKick(user);
            return;
        }
        if (!WeaponPower.use(user, stack, PER_TICK)) {
            notEnoughPower(user);
            user.releaseUsingItem();
            return;
        }
        beam(server, user, t);
        if (t % 20 == 0 && user instanceof Player player) {
            int ramp = Math.round(rampOf(t) * 100);
            player.displayClientMessage(Component.translatable("gui.automata_valkyries.prism_ramp", ramp).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        if (t % 8 == 0) playUse(user, stack);
    }

    /** 照射の強まり（1 → 3 倍） */
    public static float rampOf(int t) {
        return 1F + 2F * Math.min(1F, (float) t / RAMP_TICKS);
    }

    /** 照射の 1 Tick（兵姫に持たせたときも同じ処理） */
    public static void beam(ServerLevel level, LivingEntity user, int t) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.9)).add(0, -0.2, 0);
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, RANGE, WIDTH, 8);
        float hue = (t * 0.035F) % 1F;
        int color = Color.HSBtoRGB(hue, 0.75F, 1F) & 0xFFFFFF;
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.14F, 2), muzzle, ray.end(), 0.5);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), color, 0.3F, 3), muzzle, ray.end(), 0.7);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), color, 0.22F, 6), ray.end(), 2, 0.2);
        boolean pulse = t % PULSE == 0;
        float damage = DAMAGE * rampOf(t);
        for (LivingEntity target : ray.hits()) {
            if (!pulse) break;
            target.invulnerableTime = 0;
            target.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage);
        }
        // プリズム: 最初に当たった敵で光が分かれる
        if (!ray.hits().isEmpty()) {
            LivingEntity first = ray.hits().get(0);
            Vec3 from = new Vec3(first.getX(), first.getY(0.6), first.getZ());
            List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(SPLIT_RANGE),
                    e -> e != user && !ray.hits().contains(e) && e.isAlive() && e instanceof Enemy));
            others.sort(Comparator.comparingDouble(e -> e.distanceToSqr(first)));
            for (int i = 0; i < Math.min(SPLITS, others.size()); i++) {
                LivingEntity other = others.get(i);
                int split = Color.HSBtoRGB((hue + 0.25F * (i + 1)) % 1F, 0.7F, 1F) & 0xFFFFFF;
                Vec3 to = new Vec3(other.getX(), other.getY(0.6), other.getZ());
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), split, 0.18F, 3), from, to, 0.6);
                if (pulse) {
                    other.invulnerableTime = 0;
                    other.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), damage / 2);
                }
            }
            if (t % 4 == 0) Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFFFFF, 0.8F, 4), from, 1, 0, 0);
        }
        if (t % 10 == 0) level.playSound(null, user.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.9F, 1.0F + rampOf(t) * 0.33F);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_TICK * 20)};
    }
}
