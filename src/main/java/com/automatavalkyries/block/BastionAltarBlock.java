package com.automatavalkyries.block;

import com.automatavalkyries.entity.chrono.BastionKeeperEntity;
import com.automatavalkyries.item.HeroSignalItem;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 浮遊要塞（クロノ・バスティオン）の奥の中枢の「封印の召喚台」（2026-10-19 ユーザー指定「ティア３」。質問で: 新しい中ボス＋AEC 級を封じた召喚台）。
 * 要塞守護機（{@link BastionKeeperEntity}）が {@value #SEAL_RANGE} m 以内に生きている間は封じられている。倒してから右クリックすると、
 * AEC 級（ルクシオン・ガリアフォート・メメントモリのどれか）が英雄機の呼び声と同じ光の柱から降りてくる。1 度だけ（使うと {@link #SPENT}）。壊せない
 */
public class BastionAltarBlock extends Block {

    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    public static final double SEAL_RANGE = 96.0;
    private static final List<RegistryObject<Item>> SIGNALS = List.of(ModItems.SIGNAL_LUXION, ModItems.SIGNAL_GALLIAFORT, ModItems.SIGNAL_MEMENTO_MORI);

    public BastionAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SPENT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SPENT);
    }

    /** 要塞守護機がまだ近くに生きているか */
    public static boolean sealed(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(BastionKeeperEntity.class, new AABB(pos).inflate(SEAL_RANGE), e -> e.isAlive() && !e.isDefeated()).isEmpty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(SPENT)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.bastion_altar.spent").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (sealed(level, pos)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.bastion_altar.sealed").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
            return InteractionResult.CONSUME;
        }
        awaken((ServerLevel) level, pos, state);
        return InteractionResult.CONSUME;
    }

    /** 封印を解く: AEC 級のどれかを呼び、召喚台は使い終わりになる */
    public static void awaken(ServerLevel level, BlockPos pos, BlockState state) {
        Item signal = SIGNALS.get(level.random.nextInt(SIGNALS.size())).get();
        if (signal instanceof HeroSignalItem hero) hero.summon(level, pos.above());
        level.setBlock(pos, state.setValue(SPENT, true), 3);
        level.sendParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 1.2F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (state.getValue(SPENT)) return;
        boolean sealed = rand.nextInt(4) == 0 && sealed(level, pos); // 封じている間は紫の鎖の粒、解けたら金の光が昇る
        for (int i = 0; i < 2; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            level.addParticle(sealed ? ParticleTypes.WITCH : ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + Math.cos(a) * 0.7, pos.getY() + 1.0 + rand.nextDouble() * 0.5, pos.getZ() + 0.5 + Math.sin(a) * 0.7, 0, 0.04, 0);
        }
    }
}
