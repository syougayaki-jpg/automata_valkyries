package com.automatavalkyries.weapon;

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

/**
 * 偏光ディフレクター（§7.2 module_polar_deflector、T1）: 左手シールド。右クリックを押している間だけ展開し、
 * 展開中は毎秒 15,000 FE（毎 Tick 750 FE）を使う。前方 120 度からの射撃・爆発のダメージを 100% 止め、
 * 飛んできた弾は向きを返して跳ね返す（{@link WeaponEvents}）。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定。左手で使う盾なので、この盾そのものがオーバーロード化していれば変わる）:
 * 極光の要塞。展開すると持ち主を包む半径 8 の水色の半球の結界が立つ（高さを変えた 4 本の光の輪と、球面に散る六角の光、足元の金の陣）。
 * 結界の中へ入ってきた敵の飛び道具は向きを問わずすべて跳ね返して持ち主のものにし、0.5 秒ごとに結界の中の敵を外へ弾き出す（ダメージなし）。
 * 前方 120 度の射撃・爆発を止めるのは今まで通り</p>
 */
public class PolarDeflectorItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 300_000, PER_SECOND = 15_000, PER_TICK = PER_SECOND / 20;
    public static final float WEIGHT = 8.0F;
    /** 前方 120 度（向きとの内積が cos 60° 以上） */
    public static final double FRONT_DOT = 0.5;
    /** オーバーロード: 結界の半径・敵を弾き出す間隔（Tick）・展開の演出の間隔（Tick） */
    public static final double OVER_RADIUS = 8.0;
    public static final int OVER_REPEL_RATE = 10, OVER_FLOURISH_RATE = 100;
    public static final int ICE = 0x9FE8FF;

    public PolarDeflectorItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    protected String[] extraAnimations() {
        return new String[]{"deploy", "retract"};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_TICK)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (level instanceof ServerLevel server) deployFx(server, player, stack);
        return InteractionResultHolder.consume(stack);
    }

    /** 展開したときの演出（オーバーロードなら結界の始まり） */
    private void deployFx(ServerLevel server, LivingEntity user, ItemStack stack) {
        if (overloaded(user, stack)) {
            stack.getOrCreateTag().putLong("FortressAt", server.getGameTime());
            OverloadArts.flourish(server, user, (float) OVER_RADIUS);
        }
        playUse(user, stack, "deploy");
        server.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    /** 戦術兵姫が展開する（{@link ValkyrieArts}）。{@link #VALKYRIE_HOLD} Tick 構えて閉じる。電力は展開中の Tick ごと */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (deployed(user)) return user.getUseItem() == stack;
        if (!WeaponPower.has(user, stack, PER_TICK) || !ValkyrieArts.hold(level, user, stack, target, VALKYRIE_HOLD)) return false;
        deployFx(level, user, stack);
        return true;
    }

    /** 兵姫が 1 回に構える長さ（Tick） */
    public static final int VALKYRIE_HOLD = 60;

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        if (!WeaponPower.use(user, stack, PER_TICK)) {
            notEnoughPower(user);
            user.releaseUsingItem();
            return;
        }
        if (level instanceof ServerLevel server && overloaded(user, stack)) fortress(server, user, stack, getUseDuration(stack) - remaining);
    }

    /** この盾がオーバーロード化しているか（左手で使うので、利き手の判定 {@link OverloadArts#active} に加えて盾そのものを見る） */
    public static boolean overloaded(LivingEntity user, ItemStack stack) {
        return Overload.is(stack) || (user.getMainHandItem() == stack && OverloadArts.active(user));
    }

    /** オーバーロードの結界の 1 Tick（兵姫に持たせたときも同じ処理）: 飛び道具を跳ね返し、敵を弾き出す。跳ね返した数 */
    public static int fortress(ServerLevel level, LivingEntity user, ItemStack stack, int t) {
        Vec3 c = user.position();
        long now = level.getGameTime();
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (now - tag.getLong("FortressAt") >= OVER_FLOURISH_RATE) {
            tag.putLong("FortressAt", now);
            OverloadArts.flourish(level, user, (float) OVER_RADIUS);
            Fx.shockwave(level, c.add(0, 0.05, 0), ICE, (float) OVER_RADIUS, 14);
        }
        // 結界の中の敵の飛び道具を、向きを問わずすべて跳ね返す
        int reflected = 0;
        for (net.minecraft.world.entity.projectile.Projectile p : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                user.getBoundingBox().inflate(OVER_RADIUS), p -> p.getOwner() != user && !(p.getOwner() != null && p.getOwner().isAlliedTo(user)))) {
            Vec3 v = p.getDeltaMovement();
            Vec3 toUser = c.add(0, user.getBbHeight() * 0.5, 0).subtract(p.position());
            if (toUser.length() > OVER_RADIUS || v.lengthSqr() < 0.01 || v.dot(toUser) <= 0) continue;
            p.setOwner(user);
            p.setDeltaMovement(v.scale(-1.2));
            p.hurtMarked = true;
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), ICE, 0.25F, 14), p.position(), 8, 0.25);
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Overload.GOLD, 0.25F, 10), p.position(), 6, 0.3);
            reflected++;
        }
        if (reflected > 0) level.playSound(null, user.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 1.4F);
        // 0.5 秒ごとに結界の中の敵を外へ弾き出す
        if (t % OVER_REPEL_RATE == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RADIUS),
                    e -> e != user && e.isAlive() && !e.isAlliedTo(user) && !e.isSpectator() && e instanceof net.minecraft.world.entity.monster.Enemy
                            && e.distanceTo(user) <= OVER_RADIUS)) {
                Vec3 out = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(out.x * 1.2, 0.3, out.z * 1.2));
                e.hurtMarked = true;
                Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), ICE, 0.25F, 12), new Vec3(e.getX(), e.getY(0.5), e.getZ()), 5, 0.2);
            }
            Fx.shockwave(level, c.add(0, 0.05, 0), Overload.CRIMSON, (float) OVER_RADIUS, 10);
        }
        // 半球の結界: 高さを変えた 4 本の光の輪、球面に散る六角の光、足元の金の陣
        if (t % 4 == 0) {
            for (int k = 0; k < 4; k++) {
                double y = k * OVER_RADIUS / 4.0;
                double r = Math.sqrt(OVER_RADIUS * OVER_RADIUS - y * y);
                Fx.beamRing(level, c.add(0, y + 0.1, 0), new Vec3(0, 1, 0), k % 2 == 0 ? ICE : 0xFFFFFF, (float) r, 6);
            }
        }
        for (int i = 0; i < 16; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2, phi = Math.acos(level.random.nextDouble());
            Vec3 p = c.add(Math.sin(phi) * Math.cos(theta) * OVER_RADIUS, Math.cos(phi) * OVER_RADIUS, Math.sin(phi) * Math.sin(theta) * OVER_RADIUS);
            Fx.one(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), i % 5 == 0 ? Overload.GOLD : ICE, 0.3F, 12), p, Vec3.ZERO);
        }
        if (t % 20 == 0) Fx.mandala(level, c.add(0, 0.04, 0), Overload.GOLD, (float) OVER_RADIUS * 0.6F, 22, false, (t / 20) % 2 == 0);
        return reflected;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level instanceof ServerLevel) {
            playUse(user, stack, "retract");
            level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        }
    }

    /** user が今この盾を展開しているか */
    public static boolean deployed(LivingEntity user) {
        return user.isUsingItem() && user.getUseItem().getItem() instanceof PolarDeflectorItem;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_SECOND)};
    }
}
