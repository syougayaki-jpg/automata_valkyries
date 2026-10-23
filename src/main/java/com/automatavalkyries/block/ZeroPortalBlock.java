package com.automatavalkyries.block;

import com.automatavalkyries.world.ZeroGrave;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * 零の墓標への「紅の門」（2026-10-20 ユーザー指定「裏ボスをつくりますか」。時の狭間の奥）。
 * 眠れる時の門（{@link RulerPortalBlock}）と同じ薄い膜（{@link #AXIS} の向きの面。通り抜けられる）で、黒い闇の上を紅と紫の渦が逆向きに回る（textures/block/zero_portal.png、半透明、光る）。
 * 縁から紅の光の粒と灰が舞い、ときどき低く唸る。壊せない。
 * <ul>
 *   <li>時の狭間の北の紅の門: タイムルーラーを倒すと目覚め（{@link #AWAKE}）、入ると零の墓標へ（{@link ZeroGrave#enter}）。次のタイムルーラーと戦う間は眠る</li>
 *   <li>零の墓標の帰りの門: 墓標の主を倒すと目覚め、入ると来た所（時の狭間の紅の門の手前）へ帰す（{@link ZeroGrave#leave}）</li>
 * </ul>
 */
public class ZeroPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
    private static final VoxelShape X_AXIS = Block.box(0, 0, 6, 16, 16, 10), Z_AXIS = Block.box(6, 0, 0, 10, 16, 16);
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.22F), 1.0F);

    public ZeroPortalBlock(Properties properties) {
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
        if (!state.getValue(AWAKE)) dormant(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** 入ると: 目覚めていれば向こうへ、眠っていれば知らせる（2 秒に 1 度まで） */
    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (!state.getValue(AWAKE)) {
            if (player.tickCount % 40 == 0) dormant(level, pos, player);
            return;
        }
        if (player.isOnPortalCooldown() || player.isPassenger() || ZeroGrave.onGateCooldown(player)) return;   // 出入りした直後は入らない
        if (ZeroGrave.isGrave(level)) {
            ZeroGrave.leave(player);
        } else {
            ZeroGrave.enter(player);
        }
    }

    static void dormant(Level level, BlockPos pos, Player player) {
        String key = ZeroGrave.isGrave(level) ? "message.automata_valkyries.zero_portal.return_dormant" : "message.automata_valkyries.zero_portal.dormant";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    /** 紅の粒と灰が膜から舞い、ときどき門が低く唸る */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if (rand.nextInt(120) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.4F + rand.nextFloat() * 0.15F, false);
        }
        boolean x = state.getValue(AXIS) == Direction.Axis.X;
        for (int i = 0; i < (state.getValue(AWAKE) ? 6 : 3); i++) {
            double px = pos.getX() + (x ? rand.nextDouble() : 0.5), py = pos.getY() + rand.nextDouble(), pz = pos.getZ() + (x ? 0.5 : rand.nextDouble());
            double out = (rand.nextBoolean() ? 1 : -1) * (0.05 + rand.nextDouble() * 0.1);
            switch (rand.nextInt(4)) {
                case 0 -> level.addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz, x ? 0 : out * 2, 0, x ? out * 2 : 0);
                case 1 -> level.addParticle(CRIMSON, px, py, pz, 0, 0.02, 0);
                case 2 -> level.addParticle(ParticleTypes.CRIMSON_SPORE, px, py, pz, 0, 0, 0);
                default -> level.addParticle(ParticleTypes.ASH, px, py, pz, 0, 0, 0);
            }
        }
    }
}
