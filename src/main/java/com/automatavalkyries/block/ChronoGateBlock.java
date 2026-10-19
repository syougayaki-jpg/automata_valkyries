package com.automatavalkyries.block;

import com.automatavalkyries.entity.chrono.ChronoGazer;
import com.automatavalkyries.entity.chrono.ChronoMidBoss;
import com.automatavalkyries.entity.chrono.MimicContainerEntity;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModTechBlocks;
import com.automatavalkyries.world.CorruptedBases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 侵食基地の専用のスポナー「クロノ転送ゲート」（2026-10-15 ユーザー指定「専用のスポナー」。質問で: 壊せる転送ゲート。数値は【仮】）。
 * プレイヤー（クリエイティブ・スペクテイターは除く）が {@value #RANGE} m に入ると起動し、そのダンジョンの雑魚を {@value #MIN_DELAY}〜{@value #MAX_DELAY} Tick ごとに
 * 光の中から送り出す（まわり 12 m に {@value #CAP} 体まで。明るくても出る）。壊せば止まり、素材を落とす（loot_tables/blocks/chrono_gate）
 */
public class ChronoGateBlock extends BaseEntityBlock {

    public static final double RANGE = 16.0;
    public static final int CAP = 4, MIN_DELAY = 100, MAX_DELAY = 160;

    public ChronoGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Gate(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModTechBlocks.CHRONO_GATE_ENTITY.get(), (l, p, s, g) -> g.serverTick((ServerLevel) l, p));
    }

    /** 起動している見た目（いつも渦の粒を少し、ゲートの上へ） */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + rand.nextDouble(), pos.getY() + 1.0, pos.getZ() + rand.nextDouble(), 0, 0.05, 0);
        }
        if (rand.nextInt(4) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.1, 0);
    }

    /** ゲートの中身: どのダンジョンのものか（送り出す雑魚の顔ぶれ）と、次に送り出すまでの Tick */
    public static class Gate extends BlockEntity {
        private String kind = "";
        private int delay = 40;

        public Gate(BlockPos pos, BlockState state) {
            super(ModTechBlocks.CHRONO_GATE_ENTITY.get(), pos, state);
        }

        /** どのダンジョンのゲートか（supply_depot / chrono_outpost / dimension_foundry） */
        public void setKind(String kind) {
            this.kind = kind;
            setChanged();
        }

        public String kind() {
            return kind;
        }

        void serverTick(ServerLevel level, BlockPos pos) {
            if (level.getGameTime() % 10 != 0) return;
            Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            if (near == null) return;
            delay -= 10;
            if (delay > 0) return;
            delay = MIN_DELAY + level.random.nextInt(MAX_DELAY - MIN_DELAY + 1);
            int around = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(12.0), m -> m.isAlive() && m instanceof ChronoGazer && !(m instanceof ChronoMidBoss)).size();
            if (around >= CAP) return;
            Mob mob = spawn(level, pos, near);
            if (mob != null) setChanged();
        }

        /** 1 体を送り出す（ゲートのまわり 3 ブロックの、立てる所へ）。試験からも呼ぶ */
        @Nullable
        public Mob spawn(ServerLevel level, BlockPos pos, @Nullable Player target) {
            RandomSource rand = level.random;
            EntityType<? extends Mob> type = pick(rand);
            Mob mob = type.create(level);
            if (mob == null) return null;
            for (int tries = 0; tries < 12; tries++) {
                double x = pos.getX() + 0.5 + rand.nextInt(7) - 3, z = pos.getZ() + 0.5 + rand.nextInt(7) - 3;
                for (int dy = 1; dy >= -1; dy--) {
                    double y = pos.getY() + dy;
                    mob.moveTo(x, y, z, rand.nextFloat() * 360.0F, 0.0F);
                    BlockPos below = BlockPos.containing(x, y - 1, z);
                    if (level.noCollision(mob) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWNER, null, null);
                        CorruptedBases.Kind k = CorruptedBases.Kind.byId(kind);
                        if (k != null) k.scaleHealth(mob);
                        if (target != null && ChronoGazer.canHurt(target)) mob.setTarget(target);
                        level.addFreshEntity(mob);
                        if (mob instanceof MimicContainerEntity mimic) mimic.wake(target);
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0, z, 40, 0.4, 0.8, 0.4, 0.05);
                        level.sendParticles(ParticleTypes.FLASH, x, y + 1.0, z, 1, 0, 0, 0, 0);
                        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
                        return mob;
                    }
                }
            }
            return null;
        }

        /** ダンジョンごとの顔ぶれから 1 種（重み付き） */
        EntityType<? extends Mob> pick(RandomSource rand) {
            Object[][] table = switch (kind) {
                case "supply_depot" -> new Object[][]{{ModEntities.CHRONO_TROOPER, 4}, {ModEntities.CHRONO_SCOUT_DRONE, 4}, {ModEntities.CHRONO_SPIDER_BOT, 3}, {ModEntities.MIMIC_CONTAINER, 1}};
                case "chrono_outpost" -> new Object[][]{{ModEntities.CHRONO_TROOPER, 5}, {ModEntities.CHRONO_GUARDIAN, 3}, {ModEntities.CHRONO_SCOUT_DRONE, 3}};
                case "dimension_foundry" -> new Object[][]{{ModEntities.CHRONO_GUARDIAN, 4}, {ModEntities.CHRONO_TROOPER, 4}, {ModEntities.CHRONO_HEAVY_WALKER, 2}, {ModEntities.CHRONO_MASK_DRONE, 2}};
                default -> new Object[][]{{ModEntities.CHRONO_TROOPER, 1}, {ModEntities.CHRONO_SCOUT_DRONE, 1}};
            };
            int total = 0;
            for (Object[] e : table) total += (Integer) e[1];
            int r = rand.nextInt(total);
            for (Object[] e : table) {
                r -= (Integer) e[1];
                @SuppressWarnings("unchecked")
                Supplier<? extends EntityType<? extends Mob>> s = (Supplier<? extends EntityType<? extends Mob>>) e[0];
                if (r < 0) return s.get();
            }
            return ModEntities.CHRONO_TROOPER.get();
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putString("Kind", kind);
            tag.putInt("Delay", delay);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            kind = tag.getString("Kind");
            delay = tag.getInt("Delay");
        }
    }
}
