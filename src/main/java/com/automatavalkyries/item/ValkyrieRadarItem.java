package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.world.CrashedPods;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 兵姫探知レーダー（2026-10-07 ユーザー指定「組み立て機で、未起動の戦術兵姫を探すレーダーを作れるように」）。
 * 右クリックで、まだ目覚めていない兵姫がいる墜落ポッド（{@link CrashedPods.Registry} の記録）のいちばん近い物の方角と距離を出し、
 * その方へ光の筋を伸ばす。{@link #RANGE} ブロックまで・同じディメンション。クールタイム 5 秒。
 * 読み込まれている場所で眠っている兵姫がもういなければ記録から外す
 */
public class ValkyrieRadarItem extends Item {

    public static final double RANGE = 2000.0;

    public ValkyrieRadarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        player.getCooldowns().addCooldown(this, 100);
        BlockPos best = null;
        double bestD = RANGE * RANGE;
        var reg = CrashedPods.registry(server);
        // 読み込まれている眠った兵姫も記録へ（記録を始める前に置かれたポッドの分）
        for (var e : server.getAllEntities()) {
            if (e instanceof ValkyrieEntity v && v.isPodSleeper() && reg.pods.add(v.blockPosition().asLong())) reg.setDirty();
        }
        var it = reg.pods.iterator();
        while (it.hasNext()) {
            BlockPos p = BlockPos.of(it.next());
            if (server.isLoaded(p) && server.getEntitiesOfClass(ValkyrieEntity.class, new AABB(p).inflate(8), ValkyrieEntity::isPodSleeper).isEmpty()) {
                it.remove();
                reg.setDirty();
                continue;
            }
            double d = p.distToCenterSqr(player.position());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.radar.none").withStyle(ChatFormatting.GRAY), true);
            server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
            return InteractionResultHolder.success(stack);
        }
        double dx = best.getX() + 0.5 - player.getX(), dz = best.getZ() + 0.5 - player.getZ();
        // 方角（南から時計回りに 8 方位。マイクラの向き（yaw）と同じ並び）
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        int dir = Math.floorMod(Math.round(yaw / 45.0F), 8);
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        player.displayClientMessage(Component.translatable("message.automata_valkyries.radar.found",
                Component.translatable("message.automata_valkyries.radar.dir." + dir), dist).withStyle(ChatFormatting.GREEN), true);
        // その方へ光の筋（8 ブロック）
        Vec3 from = player.getEyePosition().subtract(0, 0.4, 0);
        Vec3 to = from.add(new Vec3(dx, 0, dz).normalize().scale(8));
        Fx.line(server, Fx.opt(ModParticles.GLOW.get(), 0x7CFF7C, 0.3F, 30), from, to, 0.3);
        server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_radar.desc").withStyle(ChatFormatting.GRAY));
    }
}
