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
 * 縁から金の光の粒と星が舞い、ときどき門の唸り。枠は {@link com.automatavalkyries.registry.ModTechBlocks#RULER_GATE_FRAME}。壊せない。
 * <p>総司令機タイムルーラーへの門（2026-10-19 ユーザー指定「三種の AEC 級の素材で時の鍵と、守護機を倒して開ける」）:
 * <ul>
 *   <li>空中庭園要塞の門: 要塞守護機が近く（{@value #KEEPER_RANGE} m）に生きていなければ、【時の鍵】を使うと目覚める（{@link #AWAKE}、金の渦。鍵はなくなる）。
 *       目覚めた門に入ると時の狭間へ（{@link com.automatavalkyries.world.TimeRift}）。最初の者が入ってから 10 秒で眠る（もう一度は新しい鍵で）</li>
 *   <li>時の狭間の帰りの門: タイムルーラーを倒すと目覚め、入ると来た所へ帰す</li>
 * </ul>
 */
public class RulerPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty AWAKE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("awake");
    public static final double KEEPER_RANGE = 128.0;
    public static final int OPEN_TICKS = 200;
    private static final VoxelShape X_AXIS = Block.box(0, 0, 6, 16, 16, 10), Z_AXIS = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.4F), 1.0F);

    public RulerPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(AWAKE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, AWAKE);
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
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        boolean rift = com.automatavalkyries.world.TimeRift.isRift(level);
        if (!state.getValue(AWAKE) && !rift && held.is(com.automatavalkyries.registry.ModItems.CHRONO_KEY.get())) {
            if (!level.getEntitiesOfClass(com.automatavalkyries.entity.chrono.BastionKeeperEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(KEEPER_RANGE),
                    e -> e.isAlive() && !e.isDefeated()).isEmpty()) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.ruler_portal.keeper").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) held.shrink(1);
            setAll(level, pos, true);
            player.displayClientMessage(Component.translatable("message.automata_valkyries.ruler_portal.awaken").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.5F, 1.2F);
            return InteractionResult.CONSUME;
        }
        if (!state.getValue(AWAKE)) dormant(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** 入ると: 目覚めていれば向こうへ、眠っていれば知らせる（2 秒に 1 度まで） */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (!state.getValue(AWAKE)) {
            if (player.tickCount % 40 == 0) dormant(level, pos, player);
            return;
        }
        if (player.isOnPortalCooldown() || player.isPassenger()) return;
        if (com.automatavalkyries.world.TimeRift.isRift(level)) {
            com.automatavalkyries.world.TimeRift.leave(player);
        } else {
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, OPEN_TICKS); // 最初の者が入ってから 10 秒で眠る
            com.automatavalkyries.world.TimeRift.enter(player);
        }
    }

    /** 開いていた門が眠る */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource rand) {
        if (state.getValue(AWAKE) && !com.automatavalkyries.world.TimeRift.isRift(level)) {
            setAll(level, pos, false);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        }
    }

    /** つながった膜（64 個まで）をまとめて目覚めさせる・眠らせる */
    static void setAll(Level level, BlockPos start, boolean awake) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < 64) {
            BlockPos p = queue.poll();
            if (!seen.add(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof RulerPortalBlock)) continue;
            level.setBlock(p, s.setValue(AWAKE, awake), 3);
            for (Direction d : Direction.values()) queue.add(p.relative(d));
        }
    }

    static void dormant(Level level, BlockPos pos, Player player) {
        String key = com.automatavalkyries.world.TimeRift.isRift(level) ? "message.automata_valkyries.ruler_portal.return_dormant" : "message.automata_valkyries.ruler_portal.dormant";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.5F);
    }

    /** 星と金の粒が膜から舞い、ときどき門が唸る */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.6F + rand.nextFloat() * 0.2F, false);
        }
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < (state.getValue(AWAKE) ? 6 : 3); i++) {
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
