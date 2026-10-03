package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * パニッシュメント・レールガン（T4、元仕様に無い追加の兵装。2026-10-03 ユーザー指定）: 両手超兵器。
 * 右クリック長押しで 30 秒チャージ（そのあいだ足が止まる）、離すと 1,024 ブロック先まで届く極光のレールを撃つ。
 * 線上の全員を貫き、防具・エンチャント・状態異常・耐性・無敵時間を無視するカンストダメージ（2,147,483,647、ダメージの数字付き）。
 * 1 射 5,000,000 FE（チャージの間に分けて引き出し、途中で離すと戻す）。保護対象（Eprotect のボス）には上限が掛かる。
 * 読み込まれていない所の先へは届かない（遠くの地形を読み込ませないため）。
 */
public class PunishmentRailgunItem extends ChargedWeaponItem {

    public static final int CAPACITY = 25_000_000, PER_SHOT = 5_000_000, CHARGE = 600;
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float DAMAGE = (float) DAMAGE_VALUE, WEIGHT = 40.0F;
    public static final double RANGE = 1024.0, WIDTH = 1.0, SEGMENT = 16.0;
    public static final int COLOR_CORE = 0xFFF6D8, COLOR_GOLD = 0xFFC040, COLOR_CRIMSON = 0xE02040;

    public PunishmentRailgunItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_SHOT);
    }

    @Override
    public float recoil() {
        return 2.0F;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.GLOW.get(), COLOR_GOLD, 0.3F, 10);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (!(level instanceof ServerLevel server)) return;
        int t = elapsed(stack, remaining);
        // チャージ中は足が止まる
        user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
        if (t > chargeTicks) {
            if (t % 10 == 0) Fx.spray(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.4F, 8), muzzle(user), 2, 0.1, 0.02);
            return;
        }
        // 1 秒ごとに足元から輪が広がり、進み具合を知らせる
        if (t % 20 == 0) {
            float f = (float) t / chargeTicks;
            Fx.shockwave(server, user.position().add(0, 0.05, 0), f < 0.66F ? COLOR_GOLD : COLOR_CRIMSON, 1.5F + 3.5F * f, 16);
            if (user instanceof Player player) {
                player.displayClientMessage(Component.translatable("gui.automata_valkyries.charge", Math.round(f * 100)).withStyle(ChatFormatting.GOLD), true);
            }
            server.playSound(null, user.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 0.5F + f);
        }
        // 終わりが近づくほど、集まる光が増える
        if (t > chargeTicks * 2 / 3) Fx.gather(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CRIMSON, 0.25F, 10), muzzle(user), 2, 3.0, 10);
        if (t == chargeTicks) {
            Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.6F, 14), muzzle(user), 30, 0.5);
            Fx.shockwave(server, user.position().add(0, 0.05, 0), COLOR_CORE, 8.0F, 20);
            server.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.5F);
            if (user instanceof Player player) {
                player.displayClientMessage(Component.translatable("gui.automata_valkyries.charge_ready").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
    }

    private static Vec3 muzzle(LivingEntity user) {
        return user.getEyePosition().add(user.getLookAngle().scale(1.6)).add(0, -0.25, 0);
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        fire(level, user);
    }

    /** 撃つ（兵姫に持たせたときも同じ処理）。線上の全員を貫き、届いた先の位置を返す */
    public static Vec3 fire(ServerLevel level, LivingEntity user) {
        return fire(level, user, RANGE);
    }

    /** 射程 range を指定して撃つ（GameTest では隣のテストに届かないよう短くする） */
    public static Vec3 fire(ServerLevel level, LivingEntity user, double range) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 start = eye.add(look.scale(1.0)).add(0, -0.2, 0);
        // 16 ブロックずつ進め、壁か、読み込まれていない所で止まる
        Vec3 end = eye;
        Set<Integer> hit = new HashSet<>();
        int hits = 0;
        for (double d = 0; d < range; d += SEGMENT) {
            Vec3 a = eye.add(look.scale(d)), b = eye.add(look.scale(Math.min(range, d + SEGMENT)));
            if (!level.isLoaded(BlockPos.containing(b))) { end = a; break; }
            BlockHitResult wall = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
            Vec3 stop = wall.getType() != HitResult.Type.MISS ? wall.getLocation() : b;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, stop).inflate(WIDTH + 1),
                    e -> e != user && e.isAlive() && !e.isSpectator() && !hit.contains(e.getId())
                            && e.getBoundingBox().inflate(WIDTH * 0.5).clip(a, stop).isPresent())) {
                hit.add(target.getId());
                hits++;
                target.invulnerableTime = 0;
                target.hurt(WeaponDamage.source(user, WeaponDamage.PUNISHMENT, user, user), DAMAGE);
                Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
                Fx.number(level, new Vec3(target.getX(), target.getY() + target.getBbHeight() + 0.6, target.getZ()), DAMAGE_VALUE, 0xFFD166);
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_CORE, 1.6F, 10), at, 1, 0, 0);
                Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR_GOLD, 0.45F, 12), at, 16, 0.5);
                Fx.shockwave(level, at, COLOR_CRIMSON, 3.5F, 12);
            }
            end = stop;
            if (wall.getType() != HitResult.Type.MISS) break;
        }
        // 光のレールそのもの（クライアントで長く描く）
        PunishmentBeamEntity beam = new PunishmentBeamEntity(level, start, end);
        level.addFreshEntity(beam);
        // 撃った所の閃光と衝撃波、着弾点の大きな光
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_CORE, 2.4F, 8), start, 1, 0, 0);
        Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR_GOLD, 10.0F, 20);
        Fx.shockwave(level, start, COLOR_CRIMSON, 5.0F, 14);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.5F, 12), start, 24, 0.6);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_GOLD, 3.0F, 14), end, 2, 0.5, 0);
        Fx.shockwave(level, end, COLOR_CORE, 9.0F, 18);
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), COLOR_GOLD, 0.3F, 24), end, 30, 0.7);
        level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.8F);
        level.playSound(null, BlockPos.containing(end), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.7F);
        user.push(-look.x * 0.9, 0.15, -look.z * 0.9); // 大きな反動
        user.hurtMarked = true;
        return end;
    }
}
