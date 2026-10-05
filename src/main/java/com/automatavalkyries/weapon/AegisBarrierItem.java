package com.automatavalkyries.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 絶対障壁発生器『アイギス』（§7.2 module_aegis_barrier、T3）: 左手展開型要塞盾。右クリックを押している間、持ち主を中心に半径 4 m の
 * 完全防御ドームを張る（毎秒 50,000 FE = 毎 Tick 2,500 FE）。ドームの外からの攻撃（飛び道具・爆発・ボスの即死ビームなど、
 * 外にいる者や外の位置から来るもの）は、中の全員に届かない（{@link WeaponEvents}）。中どうしの攻撃と、落下などの環境のダメージは通る。
 * 要塞なので、展開中の持ち主はほとんど動けない【提案】。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定。モジュールは左手で使うので、このアイギス自身がオーバーロード化していれば
 * 変化する）: 「アイギス・バスティオン」。半径 4 の完全防御ドームはそのままに、外側に半径 12 の外郭が開く。外郭の縁を 8 枚の金の六角の盾が
 * 回り、0.5 秒ごとに半径 12 の中の敵を外へ押し出して鈍化 III（1 秒）を掛け（ダメージは無い）、半径 12 の中へ外から飛び込む飛び道具を外郭の面で
 * 跳ね返す（撃った者へ返し、持ち主のものにする）。1 秒ごとに半径 12 の金と紅の衝撃波。外郭は爆発やビームは止めない（止めるのは内側のドーム）</p>
 */
public class AegisBarrierItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 1_000_000, PER_SECOND = 50_000, PER_TICK = PER_SECOND / 20;
    public static final float WEIGHT = 22.0F;
    public static final double RADIUS = 4.0;
    /** オーバーロード: 外郭の半径・回る盾の数 */
    public static final double OVER_RADIUS = 12.0;
    public static final int OVER_SHIELDS = 8;
    /** 展開中の持ち主（飛び道具を跳ね返す見回り用。展開をやめた者は見回りで外す） */
    public static final java.util.Set<LivingEntity> ACTIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public AegisBarrierItem(Properties properties) {
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

    /** 展開したときの演出 */
    private void deployFx(ServerLevel server, LivingEntity user, ItemStack stack) {
        playUse(user, stack, "deploy");
        server.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        server.playSound(null, user.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.2F);
        Fx.shockwave(server, user.position().add(0, 0.05, 0), Fx.GOLD, (float) RADIUS, 16);
        Fx.shockwave(server, user.position().add(0, 1.0, 0), Fx.PALE_GOLD, (float) RADIUS * 0.8F, 12);
        Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.GOLD, 0.3F, 20), center(user), 20, 0.25);
        if (overloaded(user, stack)) {
            Fx.shockwave(server, user.position().add(0, 0.05, 0), Fx.GOLD, (float) OVER_RADIUS, 20);
            Fx.beamRing(server, center(user), new Vec3(0, 1, 0), Fx.PALE_GOLD, (float) OVER_RADIUS, 18);
            OverloadArts.flourish(server, user, (float) OVER_RADIUS);
        }
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
    public static final int VALKYRIE_HOLD = 80;

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (level.isClientSide()) return;
        if (!WeaponPower.use(user, stack, PER_TICK)) {
            notEnoughPower(user);
            user.releaseUsingItem();
            return;
        }
        user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 4, false, false, false));
        ACTIVE.add(user);
        if (level instanceof ServerLevel server && overloaded(user, stack)) bastion(server, user, getUseDuration(stack) - remaining);
    }

    /** オーバーロード化しているか（このアイギス自身、または利き手のオーバーロード化した兵装） */
    public static boolean overloaded(LivingEntity user, ItemStack stack) {
        return Overload.is(stack) || OverloadArts.active(user);
    }

    /** オーバーロードの外郭の 1 Tick: 半径 12 を回る金の盾、飛び込む飛び道具の跳ね返し、0.5 秒ごとの押し出し */
    private static void bastion(ServerLevel level, LivingEntity user, int t) {
        Vec3 c = center(user);
        for (int k = 0; k < OVER_SHIELDS; k++) {
            double a = t * 0.05 + k * Math.PI * 2 / OVER_SHIELDS;
            Vec3 p = c.add(Math.cos(a) * OVER_RADIUS, Math.sin(t * 0.08 + k) * 0.6, Math.sin(a) * OVER_RADIUS);
            Fx.one(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), k % 2 == 0 ? Fx.GOLD : Overload.CRIMSON, 0.9F, 3), p, Vec3.ZERO);
            if (t % 2 == 0) Fx.one(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Fx.PALE_GOLD, 0.4F, 8), p, new Vec3(0, 0.03, 0));
        }
        // 外から飛び込む飛び道具を、外郭の面で跳ね返す
        for (Entity projectile : level.getEntities(user, user.getBoundingBox().inflate(OVER_RADIUS + 2), ProjectileLike::is)) {
            Entity owner = ProjectileLike.owner(projectile);
            if (owner == user || (owner != null && (owner.isAlliedTo(user) || c.distanceTo(center(owner)) <= OVER_RADIUS))) continue;
            Vec3 offset = projectile.position().subtract(c);
            double d = offset.length();
            Vec3 v = ProjectileLike.velocity(projectile);
            if (d > OVER_RADIUS + 0.6 || d < 0.01 || v.dot(offset) >= 0) continue;
            Vec3 n = offset.scale(1 / d);
            ProjectileLike.reflect(projectile, v.subtract(n.scale(2 * v.dot(n))), user);
            Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.GOLD, 0.35F, 14), projectile.position(), 8, 0.15);
        }
        if (t % 20 == 0) {
            Fx.shockwave(level, user.position().add(0, 0.05, 0), Fx.GOLD, (float) OVER_RADIUS, 16);
            Fx.shockwave(level, user.position().add(0, 0.1, 0), Overload.CRIMSON, (float) OVER_RADIUS * 0.7F, 14);
        }
        if (t % 10 != 0) return;
        int shown = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(OVER_RADIUS),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && e instanceof net.minecraft.world.entity.monster.Enemy
                        && e.distanceTo(user) <= OVER_RADIUS + e.getBbWidth())) {
            Vec3 push = e.position().subtract(user.position()).multiply(1, 0, 1);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
            e.setDeltaMovement(push.x * 1.2, 0.3, push.z * 1.2);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
            if (shown++ < 8) OverloadArts.impact(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (level instanceof ServerLevel) {
            playUse(user, stack, "retract");
            level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
        }
    }

    public static boolean deployed(LivingEntity user) {
        return user.isUsingItem() && user.getUseItem().getItem() instanceof AegisBarrierItem;
    }

    /** pos を守っているアイギス（展開中で、pos がドームの中）の持ち主。無ければ null */
    @Nullable
    public static LivingEntity guardianOf(Level level, Vec3 pos) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(RADIUS + 1), AegisBarrierItem::deployed)) {
            if (center(e).distanceTo(pos) <= RADIUS) return e;
        }
        return null;
    }

    public static Vec3 center(Entity holder) {
        return holder.position().add(0, holder.getBbHeight() * 0.5, 0);
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
