package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * 英雄機の呼び声（AEC 級ボスの召喚アイテム。2026-10-07 ユーザー指定「召喚アイテムで呼ぶ」）。地面に使うと、そこに光の柱が立ち、
 * {@value #DELAY} Tick 後にボスが降りてくる。使うとなくなる。ボスごとに名前（lang の接頭）と色を変えて使い回す
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class HeroSignalItem extends Item {

    public static final int DELAY = 100;

    private final Supplier<? extends EntityType<? extends Mob>> boss;
    private final String name;
    private final Vector3f color;

    public HeroSignalItem(Properties properties, Supplier<? extends EntityType<? extends Mob>> boss, String name, Vector3f color) {
        super(properties);
        this.boss = boss;
        this.name = name;
        this.color = color;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        summon(server, ctx.getClickedPos().relative(ctx.getClickedFace()));
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /** at に光の柱を立て、{@value #DELAY} Tick 後にこのボスを降ろす（浮遊要塞の封印の召喚台からも呼ぶ） */
    public void summon(ServerLevel server, BlockPos at) {
        PENDING.add(new Pending(server.dimension(), at, server.getGameTime() + DELAY, this));
        server.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 1.4F);
        for (ServerPlayer p : server.getPlayers(p -> p.blockPosition().distSqr(at) < 64 * 64)) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries." + name + ".incoming").withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.signal_" + name + ".desc").withStyle(ChatFormatting.GRAY));
    }

    // ---- 降りてくるまで ----

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, long at, HeroSignalItem item) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            ServerLevel level = event.getServer().getLevel(p.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            long left = p.at() - level.getGameTime();
            double x = p.pos().getX() + 0.5, y = p.pos().getY(), z = p.pos().getZ() + 0.5;
            if (left > 0) { // 光の柱（だんだん太く）と、地面の輪
                float k = 1.0F - left / (float) DELAY;
                if (level.getGameTime() % 2 == 0) {
                    for (int i = 0; i < 12; i++) {
                        level.sendParticles(new DustParticleOptions(p.item().color, 1.5F + k * 2.0F), x, y + i * 2.5, z, 2, 0.15 + k * 0.5, 1.0, 0.15 + k * 0.5, 0);
                    }
                    for (int i = 0; i < 16; i++) {
                        double a = Math.PI * 2 * i / 16 + level.getGameTime() * 0.05;
                        level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(a) * 3.0, y + 0.1, z + Math.sin(a) * 3.0, 1, 0, 0, 0, 0);
                    }
                }
                if (left % 20 == 0) level.playSound(null, p.pos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5F, 0.8F + k);
                continue;
            }
            it.remove();
            Mob boss = p.item().boss.get().create(level);
            if (boss == null) continue;
            boss.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(p.pos()), MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(boss);
            level.sendParticles(ParticleTypes.FLASH, x, y + 1, z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(p.item().color, 3.0F), x, y + 1, z, 120, 1.5, 2.0, 1.5, 0);
            level.playSound(null, p.pos(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 1.6F);
            for (ServerPlayer pl : level.getPlayers(pl -> pl.blockPosition().distSqr(p.pos()) < 96 * 96)) {
                pl.displayClientMessage(Component.translatable("message.automata_valkyries." + p.item().name + ".arrive").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
    }
}
