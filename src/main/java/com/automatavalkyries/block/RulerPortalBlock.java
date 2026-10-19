package com.automatavalkyries.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * 空中庭園要塞の奥の中枢の「眠れる時の門」（2026-10-19 ユーザー指定「ゲートをもっと豪華にネザーポータルやエンドポータルのようにして」）。
 * ネザーポータルのような薄い膜（{@link #AXIS} の向きの面。通り抜けられる）で、藍の星空の上を金と紫の渦がゆっくり回る（textures/block/ruler_portal.png のアニメーション、半透明、光る）。
 * 縁から金の光の粒と星が舞い、ときどき門の唸り。総司令機タイムルーラーへの門だが、まだ居ないので、入っても触れても「まだ時が満ちていない」と返すだけ。
 * タイムルーラーを作る時に、ここから向こうへ送るようにする。枠は {@link com.automatavalkyries.registry.ModTechBlocks#RULER_GATE_FRAME}。壊せない
 */
public class RulerPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_AXIS = Block.box(0, 0, 6, 16, 16, 10), Z_AXIS = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.4F), 1.0F);

    public RulerPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(AXIS, ctx.getHorizontalDirection().getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS : X_AXIS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        dormant(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** 入ると、眠っていると知らせる（2 秒に 1 度まで） */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof Player player && player.tickCount % 40 == 0) dormant(level, pos, player);
    }

    static void dormant(Level level, BlockPos pos, Player player) {
        player.displayClientMessage(Component.translatable("message.automata_valkyries.ruler_portal.dormant").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.5F);
    }

    /** 星と金の粒が膜から舞い、ときどき門が唸る */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.6F + rand.nextFloat() * 0.2F, false);
        }
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < 3; i++) {
            double px = pos.getX() + (x ? rand.nextDouble() : 0.5), py = pos.getY() + rand.nextDouble(), pz = pos.getZ() + (x ? 0.5 : rand.nextDouble());
            double out = (rand.nextBoolean() ? 1 : -1) * (0.05 + rand.nextDouble() * 0.1);
            switch (rand.nextInt(4)) {
                case 0 -> level.addParticle(ParticleTypes.PORTAL, px, py, pz, x ? 0 : out * 4, 0, x ? out * 4 : 0);
                case 1 -> level.addParticle(GOLD, px, py, pz, 0, 0.02, 0);
                case 2 -> level.addParticle(ParticleTypes.END_ROD, px, py, pz, x ? 0 : out * 0.3, 0.01, x ? out * 0.3 : 0);
                default -> level.addParticle(ParticleTypes.ENCHANT, px, py, pz, (rand.nextDouble() - 0.5) * 0.4, 0.3, (rand.nextDouble() - 0.5) * 0.4);
            }
        }
    }
}
