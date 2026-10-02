package com.automatavalkyries.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * スクラップショットガン（追加拡張仕様書 §8.3 weapon_scrap_shotgun）: 砂利か鉄塊を 1 個使い、
 * 散らばる実弾ペレット 6 発を撃つ（1 発 3.0 ダメージ、近距離特化。射程 16 ブロック）。
 * 同じ相手に当たったペレットはまとめて 1 回のダメージにする（無敵時間で 2 発目以降が消えないように）。
 */
public class ScrapShotgunItem extends Item {

    public static final int PELLETS = 6, COOLDOWN = 20;
    public static final float PELLET_DAMAGE = 3.0F;
    public static final double RANGE = 16.0, SPREAD = 0.09;

    public ScrapShotgunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && !consumeAmmo(player)) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.2F);
            return InteractionResultHolder.fail(gun);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            fire(server, player);
            gun.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide());
    }

    /** 弾: 砂利か鉄塊を持ち物から 1 個 */
    private static boolean consumeAmmo(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.GRAVEL) || stack.is(Items.IRON_NUGGET)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Map<LivingEntity, Integer> hits = new HashMap<>();
        for (int i = 0; i < PELLETS; i++) {
            Vec3 dir = look.add(player.getRandom().nextGaussian() * SPREAD, player.getRandom().nextGaussian() * SPREAD,
                    player.getRandom().nextGaussian() * SPREAD).normalize();
            Vec3 end = eye.add(dir.scale(RANGE));
            HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
            EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, player, eye, end,
                    new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && e != player);
            Vec3 stop = end;
            if (entity != null && entity.getEntity() instanceof LivingEntity target) {
                hits.merge(target, 1, Integer::sum);
                stop = entity.getLocation();
            }
            // 弾道の煙と、当たった所の火花
            double length = stop.distanceTo(eye);
            for (double d = 1.0; d < length; d += 1.5) {
                Vec3 p = eye.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 3, 0.05, 0.05, 0.05, 0.1);
        }
        for (Map.Entry<LivingEntity, Integer> hit : hits.entrySet()) {
            Entity target = hit.getKey();
            target.hurt(player.damageSources().playerAttack(player), PELLET_DAMAGE * hit.getValue());
        }
        Vec3 muzzle = eye.add(look.scale(0.8));
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 6, 0.05, 0.05, 0.05, 0.05);
        // 反動
        player.push(-look.x * 0.15, 0.02, -look.z * 0.15);
        player.hurtMarked = true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.weapon_scrap_shotgun.desc", PELLETS, PELLET_DAMAGE).withStyle(ChatFormatting.GRAY));
    }
}
