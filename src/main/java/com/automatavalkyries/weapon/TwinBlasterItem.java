package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * ツインブラスター（T1、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の二連装の光線拳銃。
 * 右クリックを押している間、左右に並んだ 2 本の銃口から交互に 0.15 秒ごと光弾を撃つ（1 発 3・射程 32・連射なので無敵時間を無視）。1 発 800 FE。
 * 弾は持っている手の側から出る（オフハンドなら左側。利き手が左なら逆。2026-10-03 ユーザー指定）。
 */
public class TwinBlasterItem extends TacticalWeaponItem {

    public static final int CAPACITY = 120_000, PER_SHOT = 800, INTERVAL = 3;
    public static final float SHOT_DAMAGE = 10.0F, WEIGHT = 3.0F;
    public static final double RANGE = 32.0;
    public static final int COLOR = 0x6AF0FF, CORE = 0xE8FFFF;

    public TwinBlasterItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public float recoil() {
        return 0.2F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_SHOT)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int t = getUseDuration(stack) - remaining;
        if (t % INTERVAL != 0) return;
        if (level instanceof ServerLevel server) {
            if (!WeaponPower.use(user, stack, PER_SHOT)) {
                notEnoughPower(user);
                user.releaseUsingItem();
                return;
            }
            shot(server, user, handSide(user, user.getUsedItemHand()), (t / INTERVAL) % 2 == 0 ? 1 : -1);
            playUse(user, stack);
        } else {
            clientKick(user);
        }
    }

    /** 手の側（1 = 右、-1 = 左）。利き手が左なら利き手（メインハンド）が左 */
    public static int handSide(LivingEntity user, InteractionHand hand) {
        boolean rightMain = user.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        return (hand == InteractionHand.MAIN_HAND) == rightMain ? 1 : -1;
    }

    /** 1 発撃つ（hand = 持っている手の側 1 右 / -1 左、barrel = 2 本の銃口のどちらか 1 / -1。兵姫に持たせたときも同じ処理）。当たった相手（いなければ null） */
    public static @Nullable LivingEntity shot(ServerLevel level, LivingEntity user, int hand, int barrel) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();
        Vec3 muzzle = eye.add(look.scale(0.9)).add(right.scale(0.38 * hand + 0.07 * barrel)).add(up.scale(-0.22));
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, eye, look, RANGE, 0.5, 1);
        LivingEntity target = ray.hits().isEmpty() ? null : ray.hits().get(0);
        Vec3 stop = target == null ? ray.end() : new Vec3(target.getX(), target.getY(0.6), target.getZ());
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), CORE, 0.08F, 3), muzzle, stop, 0.6);
        Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.14F, 4), muzzle, stop, 0.9);
        Fx.spray(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.2F, 4), muzzle, 1, 0, 0);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.15F, 5), stop, 3, 0.15);
        if (target != null) {
            target.invulnerableTime = 0; // 連射なので 1 発ずつ入れる
            target.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), SHOT_DAMAGE);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 1.8F + level.random.nextFloat() * 0.2F);
        return target;
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
        return new Object[]{format(PER_SHOT)};
    }
}
