package com.automatavalkyries.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 戦術兵姫（仮。2026-10-04 ユーザー指定「仮で OK、動いている所を見て判断」）。見た目と動きの確認用で、今は歩き回って見回すだけ。
 * 見た目は {@code geo/entity/valkyrie_type_v.geo.json}（docs/DESIGN_VALKYRIE_VISUAL_DB.md §11）、GUI・AI の決定事項は docs/SPEC_VALKYRIE_GUI_AI.md（未実装）。
 * <p>表情は同期するデータ。素手で右クリックすると表情が順に変わる（確認用の仮の操作。メニュー画面ができたら置き換える）。
 * 髪のなびき・瞬きはクライアントだけの状態で、{@link com.automatavalkyries.client.ValkyrieModel} が読む。</p>
 */
public class ValkyrieEntity extends PathfinderMob implements GeoEntity {

    /** 表情（テクスチャの名前の後ろ。通常は空） */
    public static final String[] EXPRESSIONS = {"normal", "smile", "shy", "angry", "sad", "surprise", "sleepy", "shutdown"};

    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // ---- クライアントだけ: 髪のなびき（ばね）と瞬き
    public float hairSwing, hairSwingO, hairVel;
    public float sideSway, sideSwayO, sideVel;
    public int blinkTicks, nextBlink = 60;
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
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public int getExpression() {
        return Mth.clamp(entityData.get(EXPRESSION), 0, EXPRESSIONS.length - 1);
    }

    public void setExpression(int index) {
        entityData.set(EXPRESSION, Math.floorMod(index, EXPRESSIONS.length));
    }

    public String expressionName() {
        return EXPRESSIONS[getExpression()];
    }

    /** 素手の右クリックで表情を順に変える（確認用の仮の操作） */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide) setExpression(getExpression() + 1);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) clientTick();
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

        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--nextBlink <= 0) {
            blinkTicks = 3;
            nextBlink = 50 + random.nextInt(90);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Expression", getExpression());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setExpression(tag.getInt("Expression"));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
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
}
