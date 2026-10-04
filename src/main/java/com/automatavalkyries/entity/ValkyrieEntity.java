package com.automatavalkyries.entity;

import com.automatavalkyries.entity.ai.ValkyrieGoals;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.RepairInjectorItem;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * 戦術兵姫（2026-10-04、段階 1: 持ち主・作戦指示・追従・機能停止・保護。docs/SPEC_VALKYRIE_GUI_AI.md）。
 * 見た目は {@code geo/entity/valkyrie_type_v.geo.json}（docs/DESIGN_VALKYRIE_VISUAL_DB.md §11）。
 *
 * <ul>
 *   <li>持ち主: 持ち主のいない兵姫を右クリックすると起動して持ち主になる（墜落ポッドはまだ無い）</li>
 *   <li>行動モード: 追従・待機・拠点防衛・巡回（{@link Mode}）。しゃがみ右クリックで、頭を狙うとなでる、体を狙うと追従 ↔ 待機。追従は 4 体まで</li>
 *   <li>機能停止: HP が 0 になると、死なずに崩れ落ちて（死亡モーション）その場に座り込んで止まる。
 *       ナノマシン溶液かリペアインジェクターを持って右クリックで直す（20 回復して起きる）</li>
 *   <li>即死（kill / die / HP 0 への書き換え / 桁外れのダメージ）: 胸のコアがステラ・ジェネシス・オメガなら受け付けない。
 *       それ以外は通常の HP 0 と同じく死亡モーションから機能停止（2026-10-04 ユーザー指定）</li>
 *   <li>保護: オートマタヴァルキリー protect（{@link ValkyrieProtect}）。データを二重に残す。オメガコアなら消去も断り自動で戻す、
 *       無ければ消去は通り救済通信機で呼び出す</li>
 *   <li>胸のコア: 今は持ち主がルミナルコアを持って右クリックで入れ替える（仮の操作。換装画面ができたら置き換える）</li>
 *   <li>素手の右クリック（持ち主）: 表情を順に変える（確認用の仮の操作。メニュー画面ができたら置き換える）</li>
 * </ul>
 */
public class ValkyrieEntity extends PathfinderMob implements GeoEntity {

    /** 表情（テクスチャの名前の後ろ。通常は空） */
    public static final String[] EXPRESSIONS = {"normal", "smile", "shy", "angry", "sad", "surprise", "sleepy", "shutdown"};
    /** 同時に追従させられる数 */
    public static final int MAX_FOLLOWERS = 4;
    /** 修理 1 回の回復量 */
    public static final float REPAIR = 20.0F;
    /** これ以上の 1 撃は「即死」とみなす（オメガコアなら受け付けない） */
    public static final float INSTANT_KILL_DAMAGE = 1.0e6F;

    public enum Mode { FOLLOW, WAIT, DEFEND, PATROL }

    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHUTDOWN = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** なでられて喜んでいる残り Tick（表情を一時的に笑顔に） */
    private static final EntityDataAccessor<Integer> HAPPY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos home;
    public int homeRadius = 12;
    /** 保護を外して消してよい（{@link ValkyrieProtect#release} と組で使う） */
    private boolean released;
    /** 胸のコア（ルミナルコア） */
    private ItemStack core = ItemStack.EMPTY;
    /** 通常のダメージの処理の最中か（HP 0 への書き換えが、ダメージによるものか外からの即死かを見分ける） */
    private boolean inHurt;
    /** 直前の正常な HP（オメガコアで、HP を直に 0 以下に書き換えられたときに戻す値） */
    private float lastGoodHealth = 40.0F;

    // ---- クライアントだけ: 髪のなびき（ばね）と瞬き
    public float hairSwing, hairSwingO, hairVel;
    public float sideSway, sideSwayO, sideVel;
    public int blinkTicks, nextBlink = 60;
    /** 機能停止の座り込みの進み（0〜1、なめらかに） */
    public float downAnim, downAnimO;
    private float lastBodyRot;

    public ValkyrieEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 全体仕様書 §3.1 Type-V: HP 40、外装 4 / 2、速度 0.28
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(EXPRESSION, 0);
        entityData.define(MODE, Mode.WAIT.ordinal());
        entityData.define(SHUTDOWN, false);
        entityData.define(OWNER, Optional.empty());
        entityData.define(HAPPY, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ValkyrieGoals.FollowOwner(this));
        goalSelector.addGoal(3, new ValkyrieGoals.KeepHome(this));
        goalSelector.addGoal(5, new ValkyrieGoals.Wander(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ 状態

    public int getExpression() {
        return Mth.clamp(entityData.get(EXPRESSION), 0, EXPRESSIONS.length - 1);
    }

    public void setExpression(int index) {
        entityData.set(EXPRESSION, Math.floorMod(index, EXPRESSIONS.length));
    }

    /** 今の表情（機能停止中は目を閉じた顔、なでられた直後は笑顔） */
    public String expressionName() {
        if (isShutdown()) return "shutdown";
        if (entityData.get(HAPPY) > 0) return "smile";
        return EXPRESSIONS[getExpression()];
    }

    public Mode getMode() {
        return Mode.values()[Mth.clamp(entityData.get(MODE), 0, Mode.values().length - 1)];
    }

    /** 行動モードを変える。追従は持ち主ごとに {@link #MAX_FOLLOWERS} 体まで（超えるなら false）。拠点防衛・巡回は今いる場所を拠点にする */
    public boolean setMode(Mode mode) {
        if (mode == Mode.FOLLOW && getMode() != Mode.FOLLOW && level() instanceof ServerLevel server && followers(server, getOwnerUUID()) >= MAX_FOLLOWERS) return false;
        if (mode == Mode.DEFEND || mode == Mode.PATROL) home = blockPosition();
        entityData.set(MODE, mode.ordinal());
        getNavigation().stop();
        return true;
    }

    /** その持ち主に追従している兵姫の数（読み込まれている全部の次元） */
    public static int followers(ServerLevel any, @Nullable UUID owner) {
        if (owner == null) return 0;
        int n = 0;
        for (ServerLevel level : any.getServer().getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ValkyrieEntity v && owner.equals(v.getOwnerUUID()) && v.getMode() == Mode.FOLLOW) n++;
            }
        }
        return n;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return entityData.get(OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        entityData.set(OWNER, Optional.ofNullable(owner));
    }

    @Nullable
    public Player getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public boolean isShutdown() {
        return entityData.get(SHUTDOWN);
    }

    public boolean isReleased() {
        return released;
    }

    public ItemStack getCore() {
        return core == null ? ItemStack.EMPTY : core; // 構築の途中（親のコンストラクタから呼ばれたとき）はまだ null
    }

    public void setCore(ItemStack stack) {
        core = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /** 胸のコアがステラ・ジェネシス・オメガか（即死・消去を完全に無効にする） */
    public boolean hasOmegaCore() {
        return getCore().is(ModWeapons.CORE_STELLA_GENESIS_OMEGA.get());
    }

    /** GameTest 用: 保護を通らない消去（他 MOD が無理やり消した）を真似る。バックアップは残すので見張りが戻す */
    public void eraseBypassingProtectForTest() {
        released = true;
        discard();
    }

    /** 保護を外して消す（持ち主が手放す・管理者のコマンド） */
    public void releaseAndDiscard() {
        released = true;
        if (level() instanceof ServerLevel server) ValkyrieProtect.release(server.getServer(), getUUID());
        discard();
    }

    // ------------------------------------------------------------------ 機能停止と修理

    /** 機能停止: 死なずに崩れ落ちて（死亡モーションはモデル側）その場に座り込んで止まる（HP は 1 で持つ。直すまで動かず、狙われず、ダメージも受けない） */
    public void shutdown() {
        if (level().isClientSide) return;
        super.setHealth(1.0F);
        deathTime = 0;
        if (!isShutdown()) {
            entityData.set(SHUTDOWN, true);
            getNavigation().stop();
            setTarget(null);
            stopRiding();
            playSound(sound("shutdown"), 1.0F, 1.0F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(1.0), getZ(), 14, 0.3, 0.4, 0.3, 0.15);
                server.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.6), getZ(), 6, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    /** 外からの即死（kill / die / HP 0 への書き換え / 桁外れのダメージ）: オメガコアなら受け付けない（false）、無ければ機能停止（true） */
    private boolean takeInstantKill(String what) {
        ValkyrieProtect.noteLethal(this, what);
        if (hasOmegaCore()) return false;
        shutdown();
        return true;
    }

    /** 修理（REPAIR 回復）。機能停止中なら起きる */
    public void repair(float amount) {
        super.setHealth(Math.min(getMaxHealth(), DirectRead.health(this) + amount));
        if (isShutdown()) {
            entityData.set(SHUTDOWN, false);
            playSound(sound("wake"), 1.0F, 1.0F);
        }
        if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(1.0), getZ(), 8, 0.3, 0.3, 0.3, 0);
    }

    @Override
    public void setHealth(float health) {
        if (health <= 0.0F && !level().isClientSide && isAddedToWorld()) {
            if (inHurt) shutdown(); // 通常のダメージで HP 0 → 死亡モーションから機能停止
            else takeInstantKill("setHealth(" + health + ")");
            return;
        }
        super.setHealth(health);
    }

    @Override
    public void die(DamageSource source) {
        takeInstantKill("die(" + source.getMsgId() + ")");
    }

    @Override
    public void kill() {
        takeInstantKill("kill()");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isShutdown()) return false;
        if (!Float.isFinite(amount) || amount >= INSTANT_KILL_DAMAGE) {
            // 桁外れの 1 撃は即死の扱い
            if (level().isClientSide) return false;
            takeInstantKill("hurt(" + source.getMsgId() + ", " + amount + ")");
            return !hasOmegaCore();
        }
        inHurt = true;
        try {
            return super.hurt(source, amount);
        } finally {
            inHurt = false;
        }
    }

    @Override
    public boolean isDeadOrDying() {
        return false; // HP 0 でも死なない（機能停止）
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isShutdown() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isPushable() {
        return !isShutdown() && super.isPushable();
    }

    /** 機能停止中は動かない（LivingEntity#aiStep が AI の処理ごと飛ばす） */
    @Override
    protected boolean isImmobile() {
        return isShutdown() || super.isImmobile();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!ValkyrieProtect.allowRemoval(this, reason)) return;
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 操作

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        boolean client = level().isClientSide;
        UUID owner = getOwnerUUID();
        ItemStack held = player.getItemInHand(hand);
        if (owner == null) {
            if (!client && level() instanceof ServerLevel sl && ValkyrieProtect.ownedCount(sl.getServer(), player.getUUID()) >= ValkyrieProtect.MAX_REGISTERED) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.registry_full", ValkyrieProtect.MAX_REGISTERED).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            if (!client) {
                setOwnerUUID(player.getUUID());
                setMode(Mode.FOLLOW);
                playSound(sound("core_startup"), 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.claimed", getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!owner.equals(player.getUUID())) return InteractionResult.PASS;
        // 修理（ナノマシン溶液・リペアインジェクター）
        if (held.is(ModWeapons.NANO_SOLUTION.get()) || held.is(ModWeapons.REPAIR_INJECTOR.get())) {
            if (DirectRead.health(this) >= getMaxHealth() && !isShutdown()) return InteractionResult.PASS;
            if (held.is(ModWeapons.REPAIR_INJECTOR.get())) {
                if (RepairInjectorItem.solution(held) <= 0) return InteractionResult.FAIL;
                if (!client) RepairInjectorItem.setSolution(held, RepairInjectorItem.solution(held) - 1);
            } else if (!client && !player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!client) repair(REPAIR);
            return InteractionResult.sidedSuccess(client);
        }
        // 胸のコアの入れ替え（仮の操作。換装画面ができたら置き換える）
        if (held.getItem() instanceof com.automatavalkyries.weapon.LuminalCoreItem) {
            if (!client) {
                ItemStack old = getCore();
                setCore(held.split(1));
                if (!old.isEmpty() && !player.getInventory().add(old)) spawnAtLocation(old);
                playSound(sound("armor_lock"), 0.9F, 1.0F);
                player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.core", getDisplayName(), getCore().getHoverName()), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isShutdown()) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (aimsAtHead(player)) {
                // なでる（同調率は段階 4 で）
                if (!client) {
                    entityData.set(HAPPY, 40);
                    playSound(sound("emote_happy"), 0.8F, 1.0F);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY(1.1), getZ(), 1, 0.1, 0.1, 0.1, 0);
                }
            } else if (!client) {
                Mode next = getMode() == Mode.FOLLOW ? Mode.WAIT : Mode.FOLLOW;
                if (setMode(next)) {
                    playSound(sound("ui_confirm"), 0.8F, 1.0F);
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.mode." + next.name().toLowerCase(java.util.Locale.ROOT), getDisplayName()), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.too_many", MAX_FOLLOWERS).withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (held.isEmpty()) {
            if (!client) setExpression(getExpression() + 1); // 確認用の仮の操作
            return InteractionResult.sidedSuccess(client);
        }
        return super.mobInteract(player, hand);
    }

    /** 視線が頭（体の高さの上 22%）に当たっているか */
    public boolean aimsAtHead(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(6.0));
        AABB box = getBoundingBox().inflate(0.1);
        Optional<Vec3> hit = box.clip(eye, end);
        double y = hit.map(h -> h.y).orElse(eye.y);
        return y >= getY() + getBbHeight() * 0.78;
    }

    // ------------------------------------------------------------------ Tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        // HP が直接 0 以下に書き換えられていたら（setHealth を通らない書き換え）即死の扱い。オメガコアなら直前の HP へ戻す
        float hp = DirectRead.health(this);
        if (hp <= 0.0F || !Float.isFinite(hp)) {
            if (!takeInstantKill("health written to " + hp)) super.setHealth(lastGoodHealth > 0.0F ? lastGoodHealth : getMaxHealth());
        } else if (!isShutdown()) {
            lastGoodHealth = hp;
        }
        if (entityData.get(HAPPY) > 0) entityData.set(HAPPY, entityData.get(HAPPY) - 1);
        // 持ち主が降りたら一緒に降りる
        if (isPassenger() && getMode() == Mode.FOLLOW) {
            Player owner = getOwner();
            if (owner == null || owner.getVehicle() != getVehicle()) stopRiding();
        }
        if (tickCount % ValkyrieProtect.INTERVAL == 0) ValkyrieProtect.snapshot(this);
    }

    /** 髪のなびき: 前へ進むほど後ろへ流れ、止まると揺れ戻る（減衰するばね）。体の向きが変わると横髪が振れる。瞬きの間合いもここで数える */
    private void clientTick() {
        Vec3 v = getDeltaMovement();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        float forward = (float) (-v.x * Mth.sin(yaw) + v.z * Mth.cos(yaw));
        float target = Mth.clamp(0.06F + Math.max(0F, forward) * 3.2F + Math.max(0F, (float) v.y) * 1.5F, 0F, 1.0F);
        hairSwingO = hairSwing;
        hairVel = (hairVel + (target - hairSwing) * 0.22F) * 0.72F;
        hairSwing += hairVel;

        float turn = Mth.wrapDegrees(yBodyRot - lastBodyRot) * Mth.DEG_TO_RAD;
        lastBodyRot = yBodyRot;
        sideSwayO = sideSway;
        sideVel = (sideVel + (-turn * 0.8F - sideSway) * 0.25F) * 0.75F;
        sideSway = Mth.clamp(sideSway + sideVel, -0.5F, 0.5F);

        downAnimO = downAnim;
        downAnim = Mth.approach(downAnim, isShutdown() ? 1.0F : 0.0F, isShutdown() ? 0.055F : 0.1F); // 崩れ落ちるのに約 0.9 秒

        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--nextBlink <= 0) {
            blinkTicks = 3;
            nextBlink = 50 + random.nextInt(90);
        }
    }

    /** 合成した効果音（sounds.json のイベント。tools/gen_valkyrie_sounds.py） */
    public static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, "valkyrie." + name));
    }

    // ------------------------------------------------------------------ 保存

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Expression", getExpression());
        tag.putString("Mode", getMode().name());
        tag.putBoolean("Shutdown", isShutdown());
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putInt("HomeRadius", homeRadius);
        if (!getCore().isEmpty()) tag.put("Core", getCore().save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setExpression(tag.getInt("Expression"));
        try {
            entityData.set(MODE, Mode.valueOf(tag.getString("Mode")).ordinal());
        } catch (IllegalArgumentException ignored) {
            entityData.set(MODE, Mode.WAIT.ordinal());
        }
        entityData.set(SHUTDOWN, tag.getBoolean("Shutdown"));
        setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        home = tag.contains("Home") ? NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        if (tag.contains("HomeRadius")) homeRadius = tag.getInt("HomeRadius");
        core = tag.contains("Core") ? ItemStack.of(tag.getCompound("Core")) : ItemStack.EMPTY;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 動きは今はすべて ValkyrieModel#setCustomAnimations で計算する（キーフレームのアニメーションは後で足す）
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** 持ち主の近くへテレポートする（足場があって体が埋まらない所）。できたら true */
    public boolean teleportNear(LivingEntity target) {
        BlockPos base = target.blockPosition();
        for (int i = 0; i < 16; i++) {
            int dx = random.nextInt(5) - 2, dz = random.nextInt(5) - 2;
            if (Math.abs(dx) < 1 && Math.abs(dz) < 1) continue;
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos p = base.offset(dx, dy, dz);
                if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)
                        && level().noCollision(this, getBoundingBox().move(p.getX() + 0.5 - getX(), p.getY() - getY(), p.getZ() + 0.5 - getZ()))) {
                    moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
                    getNavigation().stop();
                    if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.END_ROD, getX(), getY(0.5), getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                    playSound(sound("boost"), 0.7F, 1.2F);
                    return true;
                }
            }
        }
        return false;
    }

    /** 持ち主が次元を移ったとき、追従中なら一緒に移る（{@link ValkyrieGoals#onOwnerChangedDimension}） */
    @Nullable
    public ValkyrieEntity followTo(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel to)) return null;
        Entity moved = changeDimension(to, new net.minecraftforge.common.util.ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, java.util.function.Function<Boolean, Entity> repositionEntity) {
                Entity e = repositionEntity.apply(false);
                e.moveTo(owner.getX(), owner.getY(), owner.getZ(), yaw, e.getXRot());
                return e;
            }

            @Override
            public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, java.util.function.Function<ServerLevel, net.minecraft.world.level.portal.PortalInfo> defaultPortalInfo) {
                return new net.minecraft.world.level.portal.PortalInfo(owner.position(), Vec3.ZERO, entity.getYRot(), entity.getXRot());
            }
        });
        return moved instanceof ValkyrieEntity v ? v : null;
    }
}
