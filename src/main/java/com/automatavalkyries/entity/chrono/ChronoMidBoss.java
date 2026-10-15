package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 侵食基地の中ボスの共通（2026-10-15 ユーザー指定「3 体の中ボス」。荷役機クレーン・ローダー・前哨司令機ウォッチャー・工場長機ファウンドリ・マスター）。
 * <ul>
 *   <li>即死系の守り（Eprotect）は付けるが、被弾の上限は無し（ルクシオンと同じ。普通の攻撃で倒せる）</li>
 *   <li>ボスバー（{@link #barColor}）。HP 50% で第 2 形態（{@link #onPhase2}。同期の印 {@link #isPhase2}）</li>
 *   <li>撃破: {@value #DEFEAT_TICKS} Tick の間、火花と煙を噴いて崩れ、爆発して消える。消える時に、ボス部屋の宝箱の場所（{@link #setRewardChest}。無ければ自分の足元）に
 *       宝箱を置く（報酬は宝箱だけ。2026-10-15 ユーザー指定）。中身は {@link #rewardLoot}</li>
 *   <li>ダンジョンの奥の部屋に置かれ、遠くても消えず、保存される</li>
 * </ul>
 */
public abstract class ChronoMidBoss extends Monster implements GeoEntity, ChronoGazer, IProviderWithSpeech {

    public static final int DEFEAT_TICKS = 40;
    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(ChronoMidBoss.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEFEATED = SynchedEntityData.defineId(ChronoMidBoss.class, EntityDataSerializers.BOOLEAN);

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    protected final ServerBossEvent bossEvent;
    @Nullable
    private BlockPos rewardChest;
    private boolean rewardPlaced;

    protected ChronoMidBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossEvent = new ServerBossEvent(Component.translatable(type.getDescriptionId()), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
        setPersistenceRequired();
    }

    /** ボスバーの色 */
    protected abstract BossEvent.BossBarColor barColor();

    /** 撃破で置く宝箱の中身 */
    protected abstract ResourceLocation rewardLoot();

    /** 第 2 形態に入った時（サーバー。HP 50%） */
    protected void onPhase2() {
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
        entityData.define(DEFEATED, false);
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    public boolean isDefeated() {
        return entityData.get(DEFEATED);
    }

    /** 撃破で宝箱を置く所（ダンジョンのボス部屋の奥） */
    public void setRewardChest(BlockPos pos) {
        this.rewardChest = pos;
    }

    /** 技の間隔: 第 2 形態は 3/4 */
    protected int cd(int ticks) {
        return isPhase2() ? ticks * 3 / 4 : ticks;
    }

    // ---- 毎 Tick ----

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            protect.clientTick();
            return;
        }
        protect.tick();
        float ratio = protect.healthRatio();
        bossEvent.setProgress(isDefeated() || deathTime > 0 ? 0.0F : ratio);
        if (!isPhase2() && ratio <= 0.5F && isAlive() && !isDefeated()) {
            entityData.set(PHASE2, true);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
            if (level() instanceof ServerLevel server) {
                for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
                    p.displayClientMessage(Component.translatable(getType().getDescriptionId() + ".phase2").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
                server.sendParticles(ParticleTypes.FLASH, getX(), getY(0.6), getZ(), 1, 0, 0, 0, 0);
            }
            onPhase2();
        }
    }

    // ---- Eprotect（即死系の守り。被弾の上限は無し） ----

    @Override
    public Eprotect getEprotect() {
        return protect;
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (goalSelector.getAvailableGoals().isEmpty()) registerGoals();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        protect.onAddedToWorld();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        protect.onRemovedFromWorld();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return; // 被弾を通らない die() の直接呼び出しは即死攻撃として断る
        entityData.set(DEFEATED, true);
        super.die(source);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDefeated();
    }

    /** 撃破: 火花と煙を噴いて崩れ、最後に爆発して消え、宝箱を置く */
    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide) {
            protect.markDefeated();
            if (deathTime % 2 == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.05, 0);
            if (deathTime % 3 == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.1, 0);
            return;
        }
        if (!isDefeated()) entityData.set(DEFEATED, true);
        if (deathTime % 8 == 0) playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.4F);
        if (deathTime >= DEFEAT_TICKS && !DirectRead.isRemoved(this) && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(0.5), getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.GENERIC_EXPLODE, 3.0F, 0.7F);
            placeReward(server);
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    /** 宝箱を置く（置いてよい所が無ければ上へずらす） */
    void placeReward(ServerLevel server) {
        if (rewardPlaced) return;
        rewardPlaced = true;
        BlockPos pos = rewardChest != null ? rewardChest : blockPosition();
        for (int i = 0; i < 4 && !server.getBlockState(pos).canBeReplaced(); i++) pos = pos.above();
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, getDirection().getOpposite());
        server.setBlock(pos, chest, 3);
        if (server.getBlockEntity(pos) instanceof ChestBlockEntity be) be.setLootTable(rewardLoot(), random.nextLong());
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
        server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
            p.displayClientMessage(Component.translatable("message.automata_valkyries.midboss.reward").withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public Component getDefeatSpeech() {
        return null;
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.GENERIC_EXPLODE;
    }

    // ---- ボスバー・保存 ----

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void onRetired() {
        bossEvent.removeAllPlayers();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Phase2", isPhase2());
        if (rewardChest != null) tag.put("RewardChest", NbtUtils.writeBlockPos(rewardChest));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Phase2")) entityData.set(PHASE2, true);
        if (tag.contains("RewardChest")) rewardChest = NbtUtils.readBlockPos(tag.getCompound("RewardChest"));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 体の向きでの位置: 上へ up、前へ fwd、左へ left（ブロック） */
    protected Vec3 local(double up, double fwd, double left) {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, up, 0).add(f.scale(fwd)).add(new Vec3(-f.z, 0, f.x).scale(left));
    }

    static ResourceLocation loot(String path) {
        return ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, path);
    }
}
