package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 緊急招集装置（2026-10-07 ユーザー指定「追従中の戦術兵姫を機能停止・充電切れでも呼び出せる」）。
 * 右クリックで、追従モードの自分の兵姫（読み込まれている所の子。別のディメンションも）を、機能停止・電力切れでも持ち主のまわりへ呼び寄せる。
 * 乗り物・担架から降ろしてから移す。クールタイム {@link #COOLDOWN} Tick
 */
public class EmergencyRecallItem extends Item {

    public static final int COOLDOWN = 600;

    public EmergencyRecallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel here)) return InteractionResultHolder.success(stack);
        List<ValkyrieEntity> found = new ArrayList<>();
        for (ServerLevel l : here.getServer().getAllLevels()) {
            for (Entity e : l.getAllEntities()) {
                if (e instanceof ValkyrieEntity v && v.isAlive() && player.getUUID().equals(v.getOwnerUUID()) && v.getMode() == ValkyrieEntity.Mode.FOLLOW
                        && v.getVehicle() != player) found.add(v);
            }
        }
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.recall.none").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        int n = 0;
        for (int i = 0; i < found.size(); i++) {
            ValkyrieEntity v = found.get(i);
            double a = Math.PI * 2 * i / found.size();
            Vec3 at = player.position().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8);
            v.stopRiding();
            Entity moved = v;
            if (v.level() != here) {
                moved = v.changeDimension(here, new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel dest, java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> fallback) {
                        return new net.minecraft.world.level.portal.PortalInfo(at, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                    }

                    @Override
                    public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer p, ServerLevel s, ServerLevel d) {
                        return false;
                    }
                });
                if (moved == null) continue;
            } else {
                v.getNavigation().stop();
                v.teleportTo(at.x, at.y, at.z);
            }
            moved.setDeltaMovement(Vec3.ZERO);
            moved.fallDistance = 0.0F;
            Fx.shockwave(here, at.add(0, 0.05, 0), 0x7FE8FF, 1.6F, 12);
            Fx.spray(here, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.6F, 16), at.add(0, 1.0, 0), 16, 0.3, 0.6, 0.3, 0.02);
            n++;
        }
        here.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.recall.done", n).withStyle(ChatFormatting.AQUA), true);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.emergency_recall.desc").withStyle(ChatFormatting.GRAY));
    }
}
