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
 *   <li>メニュー（段階 2、2026-10-05）: 持ち主が素手・普通のアイテムで右クリックで開く（{@code menu/ValkyrieMenu}）。換装・状態・作戦指示のタブ。
 *       胸のコアの入れ替え・表情の切り替えもここから</li>
 *   <li>シールド: ダメージを先に受け、最後に被弾してから 3 秒後から毎秒 5 ずつ電力で戻る【仮: 1 点 200 FE】</li>
 *   <li>電力: 内蔵 10 万 FE。胸のコアから放電レート（最大 5 万 FE/t）で補充し、維持に毎 Tick 使う【仮: 2 FE/t】。0 で休眠し、補充されたら起きる（暫定統一 C）</li>
 *   <li>同調率: 0〜100%。なでる（1 日 5 回まで【仮】）+1・手で修理 +3（拡張仕様書 §12.1）。100% で攻撃・速度 +20% まで 20% ごとに段階的に【仮】</li>
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

    /** 内蔵の電力の上限（全体仕様書 §3.1） */
    public static final int MAX_ENERGY = 100_000;
    /** コアから 1 Tick に補充できる上限（全体仕様書 §3.1 Max I/O、暫定統一 B） */
    public static final int MAX_IO = 50_000;
    /** 体の維持に毎 Tick 使う電力【仮】 */
    public static final int UPKEEP = 2;
    /** 休眠から起きる電力【仮】 */
    public static final int WAKE_ENERGY = 1_000;
    /** シールドの回復: 毎秒の量・1 点の電力【仮】・最後の被弾から待つ Tick【仮】 */
    public static final float SHIELD_REGEN = 5.0F;
    public static final int SHIELD_COST = 200;
    public static final int SHIELD_DELAY = 60;
    /** なでて同調率が上がる 1 日の回数【仮】 */
    public static final int PATS_PER_DAY = 5;
    /** 積載超過で遅くなる割合【仮】・同調率の能力上昇の最大【仮】 */
    public static final double OVERWEIGHT_SLOW = -0.3, SYNC_BONUS = 0.2;
    private static final UUID OVERWEIGHT_ID = UUID.fromString("5b6f6d3e-2c1a-4f39-9b0a-7a1e0c4d2f10");
    private static final UUID SYNC_SPEED_ID = UUID.fromString("8e2a1c55-7d3b-4f0e-a6c9-1b2d3e4f5a60");
    private static final UUID SYNC_ATTACK_ID = UUID.fromString("c41d9e70-3a5b-4c8d-9e1f-2a3b4c5d6e70");

    private static final EntityDataAccessor<Integer> EXPRESSION = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SHUTDOWN = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** なでられて喜んでいる残り Tick（表情を一時的に笑顔に） */
    private static final EntityDataAccessor<Integer> HAPPY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SYNC = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHIELD = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(ValkyrieEntity.class, EntityDataSerializers.BOOLEAN);

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
    /** 装備（換装画面のハードポイント） */
    private ValkyrieInventory inventory;
    /** 作戦指示（拠点の範囲・交戦規定） */
    public final ValkyrieOrders orders = new ValkyrieOrders();
    /** 同調率の履歴（新しい順、最大 {@link #SYNC_HISTORY}）: 増減・理由（翻訳キーの後ろ）・ゲーム時刻 */
    public final java.util.ArrayDeque<SyncEntry> syncHistory = new java.util.ArrayDeque<>();
    public static final int SYNC_HISTORY = 8;
    /** 戦績: 撃破数・稼働時間（Tick）・一緒に倒したボス（名前、最大 {@link #MAX_BOSSES}） */
    public int kills;
    public long uptime;
    public final java.util.List<String> bosses = new java.util.ArrayList<>();
    public static final int MAX_BOSSES = 20;
    /** 最後に被弾した Tick（シールドの回復を待つ） */
    private int lastHurtTick = -SHIELD_DELAY;
    /** なでた日（ゲーム内の日）とその日の回数 */
    private long patDay = -1;
    private int patsToday;

    public record SyncEntry(float delta, String reason, long time) {}

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
                .add(Attributes.ATTACK_DAMAGE, 1.0)
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
        entityData.define(BODY, ValkyrieBody.V.ordinal());
        entityData.define(SYNC, 0.0F);
        entityData.define(SHIELD, ValkyrieBody.V.maxShield);
        entityData.define(ENERGY, MAX_ENERGY);
        entityData.define(ASLEEP, false);
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
        if (isAsleep()) return "sleepy";
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
        applyOrders();
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

    // ------------------------------------------------------------------ 段階 2: 装備・素体・同調率・シールド・電力

    public ValkyrieInventory getInventory() {
        if (inventory == null) inventory = new ValkyrieInventory(this); // 親のコンストラクタから呼ばれることがある
        return inventory;
    }

    public ValkyrieBody getBody() {
        return ValkyrieBody.of(entityData.get(BODY));
    }

    /** 素体タイプを変える（HP・速度の基本値も合わせる。弾薬庫の枠を超えた物は落とす） */
    public void setBody(ValkyrieBody body) {
        entityData.set(BODY, body.ordinal());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(body.maxHealth);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(body.speed);
        if (DirectRead.health(this) > body.maxHealth) super.setHealth(body.maxHealth);
        if (getShield() > body.maxShield) entityData.set(SHIELD, body.maxShield);
        if (!level().isClientSide) for (ItemStack stack : getInventory().overflowAmmo()) spawnAtLocation(stack);
    }

    public float getSyncRate() {
        return entityData.get(SYNC);
    }

    /** 同調率を増減する（0〜100）。履歴に理由を残す */
    public void addSync(float delta, String reason) {
        float before = getSyncRate();
        float after = Mth.clamp(before + delta, 0.0F, 100.0F);
        if (after == before) return;
        entityData.set(SYNC, after);
        syncHistory.addFirst(new SyncEntry(after - before, reason, level().getGameTime()));
        while (syncHistory.size() > SYNC_HISTORY) syncHistory.removeLast();
    }

    public float getShield() {
        return entityData.get(SHIELD);
    }

    public float getMaxShield() {
        return getBody().maxShield;
    }

    public int getEnergy() {
        return entityData.get(ENERGY);
    }

    public void setEnergy(int energy) {
        entityData.set(ENERGY, Mth.clamp(energy, 0, MAX_ENERGY));
    }

    /** 電力切れで休眠しているか（動かない。補充されたら起きる） */
    public boolean isAsleep() {
        return entityData.get(ASLEEP);
    }

    /** 積んでいる重さ（t）: 手の兵装とコア（外装・背部・チップは後で） */
    public float carriedWeight() {
        float w = 0.0F;
        for (int slot : new int[]{ValkyrieInventory.MAIN_HAND, ValkyrieInventory.OFF_HAND}) {
            if (getInventory().getItem(slot).getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem weapon) w += weapon.weight();
        }
        if (getCore().getItem() instanceof com.automatavalkyries.weapon.LuminalCoreItem c) w += c.grade().weight;
        return w;
    }

    public float weightLimit() {
        return getBody().payload;
    }

    public boolean overweight() {
        return carriedWeight() > weightLimit();
    }

    /** 攻撃力（装備・同調率込み）。銃は 1 発 × 弾数 */
    public float attackValue() {
        ItemStack main = getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        float melee = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (main.getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem weapon && weapon.shotDamage() != null) {
            float[] shot = weapon.shotDamage();
            return Math.max(melee, shot[0] * shot[1] * syncMultiplier());
        }
        return melee;
    }

    public float baseAttack() {
        return (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
    }

    public float defenseValue() {
        return (float) getAttributeValue(Attributes.ARMOR);
    }

    public float baseDefense() {
        return (float) getAttributeBaseValue(Attributes.ARMOR);
    }

    public float speedValue() {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    public float baseSpeed() {
        return (float) getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
    }

    /** 同調率の能力上昇の倍率（20% ごとに段階的、100% で 1.2 倍【仮】） */
    public float syncMultiplier() {
        return 1.0F + (float) SYNC_BONUS * (int) (getSyncRate() / 20.0F) / 5.0F;
    }

    /** 装備一式の毎 Tick の消費 FE（今は体の維持だけ。兵装は撃つたび・背部ユニットは使うたびなので入れない） */
    public int fePerTick() {
        return UPKEEP;
    }

    /** 装備が変わったとき（換装画面）: 積載超過の遅さを付け直す */
    public void onEquipmentChanged() {
        if (level() == null || level().isClientSide) return;
        applyModifier(Attributes.MOVEMENT_SPEED, OVERWEIGHT_ID, "valkyrie overweight", overweight() ? OVERWEIGHT_SLOW : 0.0);
    }

    /** 作戦指示を拠点の範囲へ反映する（巡回なら巡回範囲、それ以外は防衛半径） */
    public void applyOrders() {
        homeRadius = getMode() == Mode.PATROL ? orders.patrolRadius : orders.defendRadius;
    }

    /** 今いる場所を拠点にする */
    public void setHomeHere() {
        home = blockPosition();
    }

    private void applyModifier(net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id, String name, double amount) {
        var instance = getAttribute(attribute);
        if (instance == null) return;
        var current = instance.getModifier(id);
        if (current != null && current.getAmount() == amount) return;
        if (current != null) instance.removeModifier(id);
        if (amount != 0.0) instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, name, amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 電力・シールド・休眠・同調率の能力上昇・稼働時間（毎 Tick、サーバー） */
    private void powerTick() {
        int energy = getEnergy();
        // コアから補充（放電レートと Max I/O で頭打ち）
        if (energy < MAX_ENERGY && getCore().getItem() instanceof com.automatavalkyries.weapon.LuminalCoreItem c) {
            energy += c.extract(getCore(), Math.min(MAX_ENERGY - energy, MAX_IO));
        }
        if (!isShutdown()) energy -= UPKEEP;
        // シールドの回復（最後の被弾から SHIELD_DELAY 後、毎秒 SHIELD_REGEN）
        float shield = getShield();
        if (!isShutdown() && shield < getMaxShield() && tickCount - lastHurtTick >= SHIELD_DELAY) {
            float add = Math.min(SHIELD_REGEN / 20.0F, getMaxShield() - shield);
            int cost = (int) Math.ceil(add * SHIELD_COST);
            if (energy >= cost) {
                energy -= cost;
                entityData.set(SHIELD, shield + add);
            }
        }
        setEnergy(energy);
        // 電力 0 で休眠、補充されたら起きる（暫定統一 C）
        if (!isAsleep() && getEnergy() <= 0) {
            entityData.set(ASLEEP, true);
            getNavigation().stop();
            setTarget(null);
        } else if (isAsleep() && getEnergy() >= WAKE_ENERGY) {
            entityData.set(ASLEEP, false);
        }
        if (!isShutdown() && !isAsleep()) uptime++;
        double bonus = syncMultiplier() - 1.0;
        applyModifier(Attributes.MOVEMENT_SPEED, SYNC_SPEED_ID, "valkyrie sync speed", bonus);
        applyModifier(Attributes.ATTACK_DAMAGE, SYNC_ATTACK_ID, "valkyrie sync attack", bonus);
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

    /** シールドが先に受ける（防具の後、HP の前） */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isInvulnerableTo(source)) {
            lastHurtTick = tickCount;
            float shield = getShield();
            if (shield > 0.0F && amount > 0.0F) {
                float absorbed = Math.min(shield, amount);
                entityData.set(SHIELD, shield - absorbed);
                amount -= absorbed;
                if (amount <= 0.0F) return;
            }
        }
        super.actuallyHurt(source, amount);
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
        return isShutdown() || isAsleep() || super.isImmobile();
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
            if (!client) {
                repair(REPAIR);
                addSync(3.0F, "repair"); // 手での修理（拡張仕様書 §12.1）
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isShutdown()) {
            // 機能停止中でもメニュー（換装）は開ける
            if (!player.isShiftKeyDown()) {
                if (!client) openMenu((ServerPlayer) player);
                return InteractionResult.sidedSuccess(client);
            }
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (aimsAtHead(player)) {
                // なでる: 同調率 +1（1 日 PATS_PER_DAY 回まで【仮】）
                if (!client) {
                    long day = level().getDayTime() / 24000L;
                    if (day != patDay) {
                        patDay = day;
                        patsToday = 0;
                    }
                    if (patsToday < PATS_PER_DAY) {
                        patsToday++;
                        addSync(1.0F, "pat");
                    }
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
        // 素手・普通のアイテム: メニュー（好物を渡すのは段階 4 で）
        if (!client) openMenu((ServerPlayer) player);
        return InteractionResult.sidedSuccess(client);
    }

    /** メニュー（換装・状態・作戦指示）を開く。状態タブの一覧（同調率の履歴・ボス）は開くときに一緒に送る */
    public void openMenu(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.automatavalkyries.menu.ValkyrieMenu(id, inv, this), getDisplayName()),
                buf -> com.automatavalkyries.menu.ValkyrieMenu.writeOpenData(buf, this));
        playSound(sound("ui_beep"), 0.6F, 1.0F);
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
        powerTick();
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
        tag.putString("Body", getBody().name());
        tag.put("Equipment", getInventory().save());
        tag.put("Orders", orders.save());
        tag.putFloat("Sync", getSyncRate());
        net.minecraft.nbt.ListTag history = new net.minecraft.nbt.ListTag();
        for (SyncEntry e : syncHistory) {
            CompoundTag h = new CompoundTag();
            h.putFloat("Delta", e.delta());
            h.putString("Reason", e.reason());
            h.putLong("Time", e.time());
            history.add(h);
        }
        tag.put("SyncHistory", history);
        tag.putFloat("Shield", getShield());
        tag.putInt("Energy", getEnergy());
        tag.putBoolean("Asleep", isAsleep());
        tag.putInt("Kills", kills);
        tag.putLong("Uptime", uptime);
        net.minecraft.nbt.ListTag bossList = new net.minecraft.nbt.ListTag();
        for (String b : bosses) bossList.add(net.minecraft.nbt.StringTag.valueOf(b));
        tag.put("Bosses", bossList);
        tag.putLong("PatDay", patDay);
        tag.putInt("PatsToday", patsToday);
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
        try {
            if (tag.contains("Body")) entityData.set(BODY, ValkyrieBody.valueOf(tag.getString("Body")).ordinal());
        } catch (IllegalArgumentException ignored) {
            entityData.set(BODY, ValkyrieBody.V.ordinal());
        }
        getInventory().load(tag.getList("Equipment", net.minecraft.nbt.Tag.TAG_COMPOUND));
        orders.load(tag.getCompound("Orders"));
        entityData.set(SYNC, Mth.clamp(tag.getFloat("Sync"), 0.0F, 100.0F));
        syncHistory.clear();
        net.minecraft.nbt.ListTag history = tag.getList("SyncHistory", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < history.size() && i < SYNC_HISTORY; i++) {
            CompoundTag h = history.getCompound(i);
            syncHistory.addLast(new SyncEntry(h.getFloat("Delta"), h.getString("Reason"), h.getLong("Time")));
        }
        // 段階 2 より前の兵姫（記録が無い）は満タンで始める
        entityData.set(SHIELD, tag.contains("Shield") ? Mth.clamp(tag.getFloat("Shield"), 0.0F, getMaxShield()) : getMaxShield());
        setEnergy(tag.contains("Energy") ? tag.getInt("Energy") : MAX_ENERGY);
        entityData.set(ASLEEP, tag.getBoolean("Asleep"));
        kills = tag.getInt("Kills");
        uptime = tag.getLong("Uptime");
        bosses.clear();
        net.minecraft.nbt.ListTag bossList = tag.getList("Bosses", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < bossList.size() && i < MAX_BOSSES; i++) bosses.add(bossList.getString(i));
        patDay = tag.contains("PatDay") ? tag.getLong("PatDay") : -1;
        patsToday = tag.getInt("PatsToday");
        applyOrders();
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
