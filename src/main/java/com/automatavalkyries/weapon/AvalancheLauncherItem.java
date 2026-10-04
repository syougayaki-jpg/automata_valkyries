package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 64 連装誘導式フロストミサイルランチャー『アバランチ』（T3、元仕様に無い追加の兵装。2026-10-03 ユーザー指定）: 片手持ちの大型ミサイルポッド。
 * 両手に 1 つずつ持つと 128 連装になる（右クリック 1 回で両方が同時に斉射。2026-10-03 ユーザー指定）。
 * 1 つにつき 64 発の誘導冷凍ミサイルを約 1.6 秒かけて次々に撃ち出す（1 斉射 1,500,000 FE）。
 * 撃つときに前方 128 ブロック（ユーザー指定で 32 の 4 倍）・左右 60 度の敵を選び、ミサイルを順に割り振る
 * （{@link FrostMissileEntity}、1 発 6 ダメージで無敵時間を無視）。当たるたびに凍傷（移動速度と跳躍力が下がる、最大 V・5 秒）が重なる。
 * 斉射のあと 5 秒の再装填。
 */
public class AvalancheLauncherItem extends TacticalWeaponItem {

    public static final int CAPACITY = 15_000_000, PER_VOLLEY = 1_500_000, MISSILES = 64, PER_TICK = 2, RELOAD = 100;
    public static final double RANGE = 128.0;
    public static final float WEIGHT = 18.0F;

    public AvalancheLauncherItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public float recoil() {
        return 0.3F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (volley(stack) > 0) return InteractionResultHolder.fail(stack);
        if (!WeaponPower.has(player, stack, PER_VOLLEY)) {
            notEnoughPower(player);
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 0.8F);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, MISSILES / PER_TICK + RELOAD));
        if (level instanceof ServerLevel server) {
            start(server, player, stack, hand);
            // もう片方の手にもアバランチがあれば、同時に斉射（128 連装）
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack second = player.getItemInHand(other);
            if (second.getItem() instanceof AvalancheLauncherItem && volley(second) == 0 && WeaponPower.has(player, second, PER_VOLLEY)) {
                start(server, player, second, other);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    private void start(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
        WeaponPower.use(player, stack, PER_VOLLEY);
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        stack.getOrCreateTag().putInt("Volley", MISSILES);
        stack.getOrCreateTag().putInt("Side", arm == HumanoidArm.RIGHT ? 1 : -1);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.6F); // ポッドの蓋が開く
        playUse(player, stack);
    }

    private static int volley(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Volley") : 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        CompoundTag tag = stack.getTag();
        if (!(level instanceof ServerLevel server) || tag == null || tag.getInt("Volley") <= 0) return;
        if (!(entity instanceof LivingEntity user) || (user.getMainHandItem() != stack && user.getOffhandItem() != stack)) {
            tag.remove("Volley"); // 手から離したら斉射をやめる
            return;
        }
        int left = tag.getInt("Volley"), side = tag.getInt("Side") == 0 ? 1 : tag.getInt("Side");
        List<LivingEntity> targets = targets(user); // 1 Tick に 1 回だけ探す（128 ブロックの範囲は広いので）
        for (int i = 0; i < PER_TICK && left > 0; i++, left--) launch(server, user, MISSILES - left, side, targets);
        tag.putInt("Volley", left);
        if (user.tickCount % 2 == 0 && user instanceof Player) clientKick(user);
    }

    /** n 発目を撃つ（兵姫に持たせたときも同じ処理）。side = 右肩 1 / 左肩 -1。目標は前方の敵へ順に割り振る */
    public static FrostMissileEntity launch(ServerLevel level, LivingEntity user, int n, int side) {
        return launch(level, user, n, side, targets(user));
    }

    public static FrostMissileEntity launch(ServerLevel level, LivingEntity user, int n, int side, List<LivingEntity> targets) {
        LivingEntity target = targets.isEmpty() ? null : targets.get((n + (side > 0 ? 0 : 1)) % targets.size());
        FrostMissileEntity missile = new FrostMissileEntity(level, user, target);
        Vec3 look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        // 8×8 の発射管: 肩の上のポッドから、上と外へ広がって飛び出す
        int col = n % 8, row = (n / 8) % 8;
        Vec3 pod = user.getEyePosition().add(look.scale(0.7)).add(right.scale(side * (0.55 + col * 0.045))).add(0, 0.2 + (row - 3.5) * 0.045, 0);
        missile.setPos(pod);
        Vec3 spread = look.scale(0.45).add(right.scale(side * (0.15 + col * 0.06) + (level.random.nextDouble() - 0.5) * 0.25))
                .add(0, 0.5 + level.random.nextDouble() * 0.35, 0);
        missile.setDeltaMovement(spread.normalize().scale(0.6));
        level.addFreshEntity(missile);
        // 発射管ごとの閃光・噴煙・火花
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xD8F4FF, 0.45F, 6), pod, 1, 0, 0);
        level.sendParticles(ParticleTypes.CLOUD, pod.x, pod.y, pod.z, 2, 0.08, 0.08, 0.08, 0.02);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), Fx.ICE, 0.18F, 6), pod, 2, 0.15);
        if (n % 8 == 0) Fx.shockwave(level, pod, 0xB8F4FF, 1.2F, 6);
        if (n % 3 == 0) level.playSound(null, user.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.55F, 1.2F + level.random.nextFloat() * 0.5F);
        return missile;
    }

    /** 前方 128 ブロック・左右 60 度の敵（撃った者が最後に殴った相手を先に）、近い順 */
    public static List<LivingEntity> targets(LivingEntity user) {
        Vec3 eye = user.getEyePosition(), look = user.getLookAngle();
        LivingEntity preferred = user.getLastHurtMob();
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(RANGE),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && (e instanceof Enemy || e == preferred))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            if (to.length() <= RANGE && to.normalize().dot(look) >= 0.5) list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> e == preferred ? -1 : e.distanceToSqr(user)));
        return list;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_VOLLEY)};
    }
}
