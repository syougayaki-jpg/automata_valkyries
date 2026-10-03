package com.automatavalkyries.weapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * プラズマチャクラム（T2、追加の兵装。2026-10-03 こちらが考えた）: 片手の投擲円盤。右クリックで回転するプラズマの円盤を投げる
 * （{@link ChakramEntity}）。約 17 ブロック飛んで（壁に当たればそこで）持ち主のもとへ戻り、行きと帰りで線上の敵をそれぞれ 1 回ずつ斬る
 * （1 回 12 ダメージ、貫通）。1 投 15,000 FE、戻るまで投げられない（最大 3 秒）。
 */
public class PlasmaChakramItem extends TacticalWeaponItem {

    public static final int CAPACITY = 300_000, PER_THROW = 15_000, COOLDOWN = 40;
    public static final float WEIGHT = 3.0F;

    public PlasmaChakramItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_THROW)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (level instanceof ServerLevel server) {
            WeaponPower.use(player, stack, PER_THROW);
            toss(server, player);
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 兵姫に持たせたときも同じ処理 */
    public static ChakramEntity toss(ServerLevel level, LivingEntity user) {
        ChakramEntity disc = new ChakramEntity(level, user);
        Vec3 look = user.getLookAngle();
        disc.setPos(user.getX() + look.x * 0.8, user.getEyeY() - 0.3, user.getZ() + look.z * 0.8);
        disc.setDeltaMovement(look.scale(ChakramEntity.SPEED));
        level.addFreshEntity(disc);
        level.playSound(null, user.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.4F);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);
        return disc;
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_THROW)};
    }
}
