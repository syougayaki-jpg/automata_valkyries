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
 */
public class AegisBarrierItem extends TacticalWeaponItem {

    public static final int CAPACITY = 1_000_000, PER_SECOND = 50_000, PER_TICK = PER_SECOND / 20;
    public static final float WEIGHT = 22.0F;
    public static final double RADIUS = 4.0;
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
        if (level instanceof ServerLevel) {
            playUse(player, stack, "deploy");
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.2F);
            ServerLevel server = (ServerLevel) level;
            Fx.shockwave(server, player.position().add(0, 0.05, 0), Fx.GOLD, (float) RADIUS, 16);
            Fx.shockwave(server, player.position().add(0, 1.0, 0), Fx.PALE_GOLD, (float) RADIUS * 0.8F, 12);
            Fx.burst(server, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Fx.GOLD, 0.3F, 20), center(player), 20, 0.25);
        }
        return InteractionResultHolder.consume(stack);
    }

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
