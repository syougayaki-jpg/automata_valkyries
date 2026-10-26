package com.automatavalkyries.entity.seer;

import com.automatavalkyries.entity.chrono.ChronoGazer;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.entity.chrono.RulerFxEntity;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.Eprotect;
import com.automatavalkyries.protect.IProviderWithSpeech;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 事象観測姫アカシック（2026-10-26 ユーザー指定「アカシックちゃんを一般ボスとしておまけとして実装する」。質問で: 中盤（AEC 級の手前）・観測とデータの立方体の技・
 * 呼び出しアイテム・専用素材と立方体の装備。数値は【仮】）。見た目は事象観測体アカシック・プローブと同じマイクラ風の姿（{@code client/AkashicSeerRenderer}）。
 * 2026-10-26 ユーザー指定「一般アカシックちゃんの弾とかを前の正２０面体に置き換えて技も置き換えて」で、弾とまわりを回るものを立方体から
 * 桃・薄紫・水色の正二十面体の結晶にし、技も結晶の技にした（結晶の雨の大きな結晶は砕けると欠片が散る）。
 * <ul>
 *   <li>HP {@value #MAX_HP}・2 形態（50% で第 2 形態）。即死系の守りは付けるが被弾の上限は無し（ふつうに倒せる）。相手から 7〜11 m を保って横へ回り込む</li>
 *   <li>論理突破率（2 本目のボスバー）: 立方体を当てる・攻撃を受けるたびに上がる（第 1 形態は {@value #PHASE1_GAUGE_CAP}% まで）。第 2 形態で 100% になると大技</li>
 *   <li>観測射撃: 相手の行く先を読んで水色の立方体を 3 発（第 2 形態 5 発）。{@value #SHOT}</li>
 *   <li>データの雨: 相手の行く先のまわりに桃色の予告の円を 5 つ（第 2 形態 8 つ）、1 秒後に大きな立方体が落ちて砕ける（半径 2.2・{@value #RAIN}）</li>
 *   <li>立方体の結界: 8 秒、まわりを 6 つの立方体が回り、触れた者に 0.5 秒ごとに {@value #ORBIT}、受けるダメージは {@value #BARRIER_CUT} 倍</li>
 *   <li>追跡立方体（第 2 形態）: 桃色の立方体を 4 つ扇に放ち、2 秒追いかける（{@value #HOMING}）</li>
 *   <li>結晶槍: 相手への道に地面から結晶の棘が 12 本、手前から順に突き出す（{@value #LANCE}・跳ね上げる）</li>
 *   <li>屈折光線: 相手のまわり 3 か所に結晶を置き、1.2 秒後に胸から結晶へ光が折れ曲がって走る（{@value #REFRACT}）</li>
 *   <li>結晶の檻（第 2 形態）: 相手のまわりに結晶が 8 つ並び、1.2 秒後に一斉に真ん中へ（{@value #CAGE}）</li>
 *   <li>観測の瞬き（第 2 形態）: 相手の背後へ瞬間移動し、残した結晶の残像が砕ける（半径 2.5・{@value #BLINK}）</li>
 *   <li>アカシック・レコード展開（第 2 形態・論理突破率 100%）: 2 秒浮かんで光を集め、宙に留まって三次元の結晶の弾幕を重ねる（{@link #recordBurst}）</li>
 * </ul>
 * 観測の印（2026-10-26 ユーザー指定「敵対エンティティやプレイヤーにマーキングエフェクトがつくようにして」。質問で: ボスのアカシックが付ける・頭上と足元の印）:
 * {@value #OBSERVE_INTERVAL} Tick ごとに 20 m 以内の見えている相手（プレイヤー・兵姫・ほかの敵対モブ）すべてと、結晶を当てた相手に {@value #MARK_TICKS} Tick の印を付ける。
 * 印の付いた相手には技のダメージが {@value #MARK_BONUS} 倍、観測結晶・射が少し追いかける。印は描く側（client/AkashicMarkClient）が頭上の照準と足元の輪で壁越しにも見せ、
 * 印の付いたプレイヤーの画面には警告を出す
 * 論理突破率による適応（2026-10-26 ユーザー指定「論理突破率が大技だけは寂しいので敵対しているものの防御力攻撃力HP量回復量に応じてステータスが上がって行く
 * 相手のステータスに応じた上限あり」）: 1 秒ごとに戦っている相手（狙っている相手・近くのプレイヤーと兵姫・自分を狙うモブ）の中で一番強い値を観測し、
 * 上限 × 論理突破率で強くなる。攻撃: 相手の防具と HP が高いほど与えるダメージの倍率の上限が上がる（最大 ×3）。防御: 相手の攻撃力が高いほど防具（最大 +20）と
 * 防具強度（最大 +8）。回復: 相手の 1 秒の回復量まで（最大は自分の最大 HP の 0.3%）、毎秒 HP が戻る。今の上がり方は 2 本目のボスバーに出す。大技の後は 40% に下がる
 * 倒すと観測の核（{@code boss_drop_akashic_core}）を落とす（{@code loot_tables/entities/akashic_seer}）
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.automatavalkyries.AutomataValkyries.MODID)
public class AkashicSeerEntity extends Monster implements ChronoGazer, IProviderWithSpeech {

    public static final float MAX_HP = 3000.0F;
    public static final float SHOT = 6.0F, RAIN = 10.0F, ORBIT = 4.0F, HOMING = 7.0F, BREACH = 8.0F, BARRIER_CUT = 0.7F;
    public static final float PHASE1_GAUGE_CAP = 60.0F;
    public static final int BARRIER_TICKS = 160, CHARGE_TICKS = 40, DEATH_TICKS = 30;
    public static final double ORBIT_R = 2.0;

    private static final EntityDataAccessor<Boolean> PHASE2 = SynchedEntityData.defineId(AkashicSeerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> GAUGE = SynchedEntityData.defineId(AkashicSeerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BARRIER = SynchedEntityData.defineId(AkashicSeerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(AkashicSeerEntity.class, EntityDataSerializers.INT);

    /** 描く側の動き（2026-10-26 ユーザー指定「いいかんじにモーションもつけて」）: 技ごとの出来事と、その時刻（クライアントの tickCount） */
    public static final byte EV_SHOT = 60, EV_RAIN = 61, EV_BARRIER = 62, EV_HOMING = 63, EV_BURST = 64, EV_PHASE2 = 65,
            EV_LANCE = 66, EV_REFRACT = 67, EV_CAGE = 68, EV_BLINK = 69;
    public final int[] animTick = {-1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000, -1000};
    /** 増やした技（2026-10-26 ユーザー指定「技も増やして」）のダメージ */
    public static final float LANCE = 8.0F, REFRACT = 9.0F, CAGE = 6.0F, BLINK = 8.0F;

    /** 観測の印: 長さ・付け直す間・ダメージの倍率・届く距離 */
    public static final int MARK_TICKS = 200, OBSERVE_INTERVAL = 160;
    public static final float MARK_BONUS = 1.2F;
    public static final double OBSERVE_RANGE = 20.0;
    private static final EntityDataAccessor<CompoundTag> MARKS = SynchedEntityData.defineId(AkashicSeerEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** サーバー: 印を付けた相手（エンティティの番号 → 残り Tick） */
    private final java.util.Map<Integer, Integer> marks = new java.util.HashMap<>();
    private boolean marksDirty;

    /**
     * 弾の色ごとのダメージの種類（2026-10-26 ユーザー指定「弾の色にあわせてダメージの種類を変えて」。質問で: 物理・魔法・虚数）:
     * 水色 = 物理の飛び道具（防具・飛び道具耐性・盾で防げる）、桃 = 魔法（防具を通す。防護のエンチャントと盾は効く）、薄紫 = 虚数（盾を通す。防具は効く）。
     * どれも専用のキルログ（{@code death.attack.automata_valkyries.akashic_*}）
     */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> PHYSICAL = damageKey("akashic_physical"),
            MAGIC = damageKey("akashic_magic"), IMAGINARY = damageKey("akashic_imaginary");

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> damageKey(String name) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, name));
    }

    /** 色に合ったダメージの種類（direct は弾。無ければ自分が直に） */
    public DamageSource damageOf(int color, @Nullable net.minecraft.world.entity.Entity direct) {
        var key = color == AkashicCubeEntity.PINK ? MAGIC : color == AkashicCubeEntity.VIOLET ? IMAGINARY : PHYSICAL;
        var holder = level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return direct != null ? new DamageSource(holder, direct, this) : new DamageSource(holder, this);
    }

    /** 論理突破率による適応の上限（相手の値からの決め方の係数と、自分の上限） */
    public static final float ADAPT_ATTACK_MAX = 3.0F, ADAPT_ARMOR_MAX = 20.0F, ADAPT_TOUGHNESS_MAX = 8.0F, ADAPT_REGEN_RATIO = 0.003F, GAUGE_AFTER_RECORD = 40.0F;
    private static final java.util.UUID ADAPT_ARMOR_ID = java.util.UUID.fromString("5d1f0a3b-7c2e-4b6a-9e1d-3a8c4f2b6e71");
    private static final java.util.UUID ADAPT_TOUGHNESS_ID = java.util.UUID.fromString("6e2a1b4c-8d3f-4c7b-af2e-4b9d5a3c7f82");
    /** 観測した相手の一番強い値（防具・防具強度・攻撃力・最大 HP・1 秒の回復量） */
    private float oppArmor, oppToughness, oppAttack, oppMaxHp, oppHeal;
    /** 相手が回復した量（LivingHealEvent から。アカシックが居る間だけ数える） */
    private static final java.util.Map<LivingEntity, Float> HEALED = new java.util.WeakHashMap<>();
    private static int alive;

    private final Eprotect protect = new Eprotect(this, com.automatavalkyries.protect.EprotectProfile.UNCAPPED);
    private final ServerBossEvent bossEvent;
    private final ServerBossEvent gaugeEvent;
    private int castLock;

    /** 時間差の技（予告の後に落ちる立方体など） */
    private record Delayed(int at, Runnable act) {}

    private final List<Delayed> delayed = new ArrayList<>();

    public AkashicSeerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 400;
        this.bossEvent = new ServerBossEvent(Component.translatable(type.getDescriptionId()), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_10);
        this.gaugeEvent = new ServerBossEvent(Component.translatable("entity.automata_valkyries.akashic_seer.gauge", 0, "1.00", "0.0", "0.0"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        gaugeEvent.setProgress(0.0F);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SeerGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        ChronoGazer.addTargets(this);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
        entityData.define(GAUGE, 0.0F);
        entityData.define(BARRIER, 0);
        entityData.define(CHARGE, 0);
        entityData.define(MARKS, new CompoundTag());
    }

    public boolean isPhase2() {
        return entityData.get(PHASE2);
    }

    public float gauge() {
        return entityData.get(GAUGE);
    }

    public int barrierLeft() {
        return entityData.get(BARRIER);
    }

    public int chargeLeft() {
        return entityData.get(CHARGE);
    }

    /** 観測の印を付ける（付け直すと長さが戻る） */
    public void mark(LivingEntity e) {
        if (level().isClientSide || e == this || !e.isAlive()) return;
        if (marks.put(e.getId(), MARK_TICKS) == null) {
            marksDirty = true;
            if (level() instanceof ServerLevel server) {
                Fx.shockwave(server, e.position().add(0, 0.1, 0), AkashicCubeEntity.PINK, 1.6F, 12);
                server.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 1.2F, 1.8F);
            }
        }
    }

    /** 印が付いているか（サーバーは自分の記録、描く側は届いた番号の並びから） */
    public boolean isMarked(net.minecraft.world.entity.Entity e) {
        if (!level().isClientSide) return marks.containsKey(e.getId());
        for (int id : entityData.get(MARKS).getIntArray("Ids")) if (id == e.getId()) return true;
        return false;
    }

    /** 描く側: 印の付いた相手の番号 */
    public int[] markedIds() {
        return entityData.get(MARKS).getIntArray("Ids");
    }

    /** 印を数え、消えた相手を外し、変わったら描く側へ送る */
    void tickMarks(ServerLevel server) {
        for (var it = marks.entrySet().iterator(); it.hasNext(); ) {
            var m = it.next();
            net.minecraft.world.entity.Entity e = server.getEntity(m.getKey());
            int left = m.getValue() - 1;
            if (left <= 0 || !(e instanceof LivingEntity l) || !l.isAlive() || e.distanceToSqr(this) > 64 * 64) {
                it.remove();
                marksDirty = true;
            } else {
                m.setValue(left);
            }
        }
        if (tickCount % OBSERVE_INTERVAL == 0 && getTarget() != null) observeAround(server);
        if (marksDirty && tickCount % 4 == 0) {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("Ids", marks.keySet().stream().mapToInt(Integer::intValue).toArray());
            entityData.set(MARKS, tag);
            marksDirty = false;
        }
    }

    /** 観測: 20 m 以内の見えている相手すべてに印を付ける（目から桃色の光の線） */
    void observeAround(ServerLevel server) {
        Vec3 eye = getEyePosition();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(OBSERVE_RANGE), this::foe)) {
            if (e.distanceTo(this) > OBSERVE_RANGE || !hasLineOfSight(e)) continue;
            Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.PINK, 0.12F, 10), eye, e.position().add(0, e.getBbHeight() * 0.6, 0), 0.6);
            mark(e);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
    }

    /** 印の付いた相手には、このボス（と結晶）からのダメージが {@value #MARK_BONUS} 倍 */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof AkashicSeerEntity seer && event.getEntity() != seer) {
            float k = seer.attackMultiplier();                                                // 論理突破率による適応
            if (seer.isMarked(event.getEntity())) k *= MARK_BONUS;
            event.setAmount(event.getAmount() * k);
        }
    }

    // ------------------------------------------------------------------ 論理突破率による適応

    /** 相手の防具と HP から決まる、与えるダメージの倍率の上限（1〜{@value #ADAPT_ATTACK_MAX}） */
    public float attackCap() {
        float k = (oppArmor + oppToughness * 0.5F) / 40.0F + Math.max(0.0F, oppMaxHp - 20.0F) / 40.0F;
        return 1.0F + Math.min(ADAPT_ATTACK_MAX - 1.0F, k);
    }

    /** 今の与えるダメージの倍率（上限 × 論理突破率） */
    public float attackMultiplier() {
        return 1.0F + (attackCap() - 1.0F) * gauge() / 100.0F;
    }

    public float armorBonus() {
        return Math.min(ADAPT_ARMOR_MAX, oppAttack * 0.8F) * gauge() / 100.0F;
    }

    public float toughnessBonus() {
        return Math.min(ADAPT_TOUGHNESS_MAX, oppAttack * 0.3F) * gauge() / 100.0F;
    }

    /** 1 秒に戻る HP（相手の 1 秒の回復量まで、最大は自分の最大 HP の {@value #ADAPT_REGEN_RATIO}） */
    public float regenPerSecond() {
        return Math.min(oppHeal, getMaxHealth() * ADAPT_REGEN_RATIO) * gauge() / 100.0F;
    }

    /** 戦っている相手: 狙っている相手・32 m 以内のプレイヤーと兵姫・自分を狙っているモブ */
    List<LivingEntity> opponents(ServerLevel server) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(32.0), this::foe)) {
            if (e == getTarget() || e instanceof Player || e instanceof com.automatavalkyries.entity.ValkyrieEntity
                    || e instanceof net.minecraft.world.entity.Mob m && m.getTarget() == this) out.add(e);
        }
        return out;
    }

    /** 1 秒ごと: 相手の一番強い値を観測し、防具を付け直し、HP を戻す */
    void adapt(ServerLevel server) {
        float armor = 0, tough = 0, attack = 0, hp = 0, heal = 0;
        for (LivingEntity e : opponents(server)) {
            armor = Math.max(armor, e.getArmorValue());
            tough = Math.max(tough, (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            var atk = e.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) attack = Math.max(attack, (float) atk.getValue());
            hp = Math.max(hp, e.getMaxHealth());
            Float h = HEALED.remove(e);
            if (h != null) heal = Math.max(heal, h);
        }
        oppArmor = armor;
        oppToughness = tough;
        oppAttack = attack;
        oppMaxHp = hp;
        oppHeal = oppHeal * 0.6F + heal * 0.4F;                                              // 回復はならして見る
        modifier(Attributes.ARMOR, ADAPT_ARMOR_ID, armorBonus());
        modifier(Attributes.ARMOR_TOUGHNESS, ADAPT_TOUGHNESS_ID, toughnessBonus());
        float regen = regenPerSecond();
        if (regen > 0.01F && getHealth() < getMaxHealth()) heal(regen);
    }

    private void modifier(net.minecraft.world.entity.ai.attributes.Attribute attr, java.util.UUID id, float amount) {
        var inst = getAttribute(attr);
        if (inst == null) return;
        var old = inst.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - amount) < 0.05) return;
        if (old != null) inst.removeModifier(id);
        if (amount > 0.05F) inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "akashic adapt", amount,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }

    /** 相手の回復量を数える（アカシックが 1 体でも居る間だけ） */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (alive <= 0 || event.getEntity().level().isClientSide || event.getEntity() instanceof AkashicSeerEntity) return;
        HEALED.merge(event.getEntity(), event.getAmount(), Float::sum);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        protect.onAddedToWorld();
        if (!level().isClientSide) alive++;
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        protect.onRemovedFromWorld();
        if (!level().isClientSide) {
            alive = Math.max(0, alive - 1);
            if (alive == 0) HEALED.clear();
        }
    }

    /** 論理突破率を上げる（立方体を当てた・攻撃を受けた） */
    public void observed(float amount) {
        float cap = isPhase2() ? 100.0F : PHASE1_GAUGE_CAP;
        entityData.set(GAUGE, Math.min(cap, gauge() + amount));
    }

    int cd(int ticks) {
        return isPhase2() ? ticks * 3 / 4 : ticks;
    }

    void say(String key, ChatFormatting... style) {
        if (!(level() instanceof ServerLevel server)) return;
        for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 64 * 64)) {
            p.displayClientMessage(Component.translatable("entity.automata_valkyries.akashic_seer." + key).withStyle(style), true);
        }
    }

    // ------------------------------------------------------------------ 毎 Tick

    @Override
    public void baseTick() {
        super.baseTick();
        if (level().isClientSide) {
            protect.clientTick();
            return;
        }
        protect.tick();
        float ratio = protect.healthRatio();
        bossEvent.setProgress(deathTime > 0 ? 0.0F : ratio);
        gaugeEvent.setProgress(gauge() / 100.0F);
        gaugeEvent.setName(Component.translatable("entity.automata_valkyries.akashic_seer.gauge", Math.round(gauge()),
                String.format(java.util.Locale.ROOT, "%.2f", attackMultiplier()), String.format(java.util.Locale.ROOT, "%.1f", armorBonus()),
                String.format(java.util.Locale.ROOT, "%.1f", regenPerSecond())));
        if (!isPhase2() && ratio <= 0.5F && isAlive()) {
            entityData.set(PHASE2, true);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 1.2F);
            say("phase2", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            level().broadcastEntityEvent(this, EV_PHASE2);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.FLASH, getX(), getY(1.0), getZ(), 1, 0, 0, 0, 0);
                Fx.shockwave(server, position().add(0, 1.0, 0), AkashicCubeEntity.PINK, 8.0F, 20);
                Fx.shockwave(server, position().add(0, 0.2, 0), AkashicCubeEntity.CYAN, 12.0F, 24);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || deathTime > 0) return;
        tickMarks(server);
        if (tickCount % 20 == 0) adapt(server);
        if (castLock > 0) castLock--;
        for (Delayed d : new ArrayList<>(delayed)) {
            if (tickCount >= d.at()) {
                delayed.remove(d);
                d.act().run();
            }
        }
        int barrier = barrierLeft();
        if (barrier > 0) {
            entityData.set(BARRIER, barrier - 1);
            if (barrier % 10 == 0) {                                                       // 結界の立方体に触れた者
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(ORBIT_R + 0.6, 0.5, ORBIT_R + 0.6), this::foe)) {
                    if (e.distanceTo(this) <= ORBIT_R + 0.8) {
                        e.hurt(damageOf(AkashicCubeEntity.VIOLET, null), ORBIT);                   // 結界の結晶は虚数（盾で受けられない）
                        observed(1.0F);
                    }
                }
            }
        }
        int charge = chargeLeft();
        if (charge > 0) {
            entityData.set(CHARGE, charge - 1);
            setDeltaMovement(0, 0.04, 0);                                                    // 浮かんで光を集める
            if (charge % 4 == 0) Fx.gather(server, MementoMoriEntity.glow(charge % 8 == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN, 0.35F, 12), position().add(0, 1.2, 0), 10, 5.0, 12);
            if (charge == 1) recordBurst(server);
        }
        if (burstLeft > 0) {                                                                 // 放っている間は宙に留まる
            burstLeft--;
            setNoGravity(burstLeft > 0);
            setDeltaMovement(getDeltaMovement().multiply(0.5, 0.0, 0.5));
            getNavigation().stop();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EV_SHOT && id <= EV_BLINK) {
            animTick[id - EV_SHOT] = tickCount;
            return;
        }
        super.handleEntityEvent(id);
    }

    /** 描く側: その動きが始まってからの Tick（始まっていなければ大きな数） */
    public float animAge(byte ev, float pt) {
        return tickCount + pt - animTick[ev - EV_SHOT];
    }

    boolean foe(LivingEntity e) {
        return e != this && ChronoGazer.canHurt(e);
    }

    // ------------------------------------------------------------------ 技

    /** 相手の行く先（ticks 後） */
    Vec3 predict(LivingEntity t, double ticks) {
        Vec3 v = t.getDeltaMovement().multiply(1, 0, 1);
        return t.position().add(v.scale(ticks));
    }

    Vec3 hand() {
        Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
        return position().add(0, 1.3, 0).add(f.scale(0.6));
    }

    /** 観測射撃: 行く先を読んで水色の立方体を 3 発（第 2 形態 5 発） */
    void observeShot(LivingEntity t) {
        int n = isPhase2() ? 5 : 3;
        for (int i = 0; i < n; i++) {
            int k = i;
            delayed.add(new Delayed(tickCount + i * 5, () -> {
                if (!t.isAlive()) return;
                Vec3 from = hand();
                double dist = from.distanceTo(t.position());
                Vec3 to = predict(t, dist / 0.9).add(0, t.getBbHeight() * 0.5, 0);
                AkashicCubeEntity shot = AkashicCubeEntity.shoot(this, from, to.subtract(from), 0.9F, SHOT, AkashicCubeEntity.CYAN, 0.16F).crystal();
                if (isMarked(t)) shot.homing(t, 12);                                   // 印の付いた相手へは少し追いかける
                level().broadcastEntityEvent(this, EV_SHOT);                          // 1 発ごとに右手で撃つ
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.4F + k * 0.1F);
            }));
        }
        castLock = n * 5 + 10;
        say("shot", ChatFormatting.AQUA);
    }

    /** データの雨: 行く先のまわりに予告の円、1 秒後に大きな立方体が落ちる */
    void dataRain(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        int n = isPhase2() ? 8 : 5;
        Vec3 c = predict(t, 20);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = i == 0 ? 0 : 1.5 + random.nextDouble() * 4.0;
            Vec3 at = new Vec3(c.x + Math.cos(a) * r, t.getY(), c.z + Math.sin(a) * r);
            RulerFxEntity.spawn(server, RulerFxEntity.CLOCK, RulerFxEntity.FLAT | RulerFxEntity.FILL | RulerFxEntity.HANDS_FAST, at.add(0, 0.08, 0), 2.2F, 0, 0, 22, AkashicCubeEntity.PINK);
            delayed.add(new Delayed(tickCount + 6 + i * 2, () -> AkashicCubeEntity.shoot(this, at.add(0, 12.0, 0), new Vec3(0, -1, 0), 0.9F, RAIN, AkashicCubeEntity.PINK, 0.45F).crystal()
                    .blast(2.2F).life(40)));
        }
        castLock = 30;
        level().broadcastEntityEvent(this, EV_RAIN);
        playSound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.6F);
        say("rain", ChatFormatting.LIGHT_PURPLE);
    }

    /** 立方体の結界 */
    void barrier() {
        entityData.set(BARRIER, BARRIER_TICKS);
        castLock = 20;
        level().broadcastEntityEvent(this, EV_BARRIER);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.2F);
        say("barrier", ChatFormatting.AQUA);
    }

    /** 追跡立方体（第 2 形態）: 4 つを扇に放ち、追いかけさせる */
    void homing(LivingEntity t) {
        Vec3 from = hand();
        Vec3 dir = t.position().add(0, 1.0, 0).subtract(from).normalize();
        for (int i = 0; i < 4; i++) {
            double spread = (i - 1.5) * 0.5;
            Vec3 d = new Vec3(dir.x * Math.cos(spread) - dir.z * Math.sin(spread), 0.35, dir.x * Math.sin(spread) + dir.z * Math.cos(spread));
            AkashicCubeEntity.shoot(this, from, d, 0.5F, HOMING, AkashicCubeEntity.VIOLET, 0.2F).crystal().homing(t, 40);
        }
        castLock = 20;
        level().broadcastEntityEvent(this, EV_HOMING);
        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 1.4F);
        say("homing", ChatFormatting.LIGHT_PURPLE);
    }

    /** アカシック・レコード展開（第 2 形態・論理突破率 100%）: 浮かんで光を集める */
    public void record() {
        entityData.set(CHARGE, CHARGE_TICKS);
        entityData.set(GAUGE, GAUGE_AFTER_RECORD);                                           // 大技の後も、見たものの一部は覚えている
        castLock = CHARGE_TICKS + BURST_TICKS + 10;
        playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
        say("record", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
    }

    /** 集めた光を立方体の輪 3 重にして全方位へ */
    /** 大技の弾幕の長さ（宙に留まって腕を広げ、ゆっくり一回転しながら 5 つの模様を重ねる） */
    public static final int BURST_TICKS = 90;
    public static final float BURST_SMALL = 6.0F;
    private int burstLeft;

    public boolean bursting() {
        return burstLeft > 0;
    }

    /**
     * アカシック・レコード展開の放出（2026-10-26 ユーザー指定「大技はもっとスタイリッシュに弾幕を三次元にはって」）: 宙に留まり、
     * ① 結晶の球（48 個が上下も含め全方位へゆっくり）→ ② 二重螺旋（2 本の腕が回りながら上下に振れる）→ ③ 傾いた 3 つの輪（縦・前後に傾いた輪）→
     * ④ 観測の照準（12 個が光輪のように止まり、溜めてから一斉に相手の行く先へ。{@value #BREACH}）。小さな結晶は 1 つ {@value #BURST_SMALL}。
     * 足元と正面に桃と水色の魔法陣が回る
     */
    void recordBurst(ServerLevel server) {
        server.sendParticles(ParticleTypes.FLASH, getX(), getY(1.0), getZ(), 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 1.4F);
        playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 1.6F);
        level().broadcastEntityEvent(this, EV_BURST);
        burstLeft = BURST_TICKS;
        Vec3 c0 = position().add(0, 1.2, 0);
        Fx.mandala(server, position().add(0, 0.1, 0), AkashicCubeEntity.PINK, 5.0F, BURST_TICKS, false, true);
        Fx.mandala(server, c0, AkashicCubeEntity.CYAN, 3.0F, BURST_TICKS, true, false);
        Fx.shockwave(server, c0, AkashicCubeEntity.VIOLET, 10.0F, 20);
        int[] colors = {AkashicCubeEntity.PINK, AkashicCubeEntity.VIOLET, AkashicCubeEntity.CYAN};

        // ① 結晶の球: フィボナッチ球の 48 方向へ（上は桃・真ん中は薄紫・下は水色）
        int n = 48;
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / n, r = Math.sqrt(1.0 - y * y), a = golden * i;
            Vec3 d = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
            AkashicCubeEntity.shoot(this, c0.add(d.scale(0.8)), d, 0.32F, BURST_SMALL, colors[y > 0.33 ? 0 : y > -0.33 ? 1 : 2], 0.17F).crystal().life(110);
        }
        // ② 二重螺旋: 2 本の腕が回りながら、上下にも振れる
        for (int k = 0; k < 16; k++) {
            int step = k;
            delayed.add(new Delayed(tickCount + 10 + k * 2, () -> {
                Vec3 c = position().add(0, 1.2, 0);
                for (int arm = 0; arm < 2; arm++) {
                    double a = step * 0.4 + arm * Math.PI, p = Math.sin(step * 0.35) * 0.65;
                    Vec3 d = new Vec3(Math.cos(a) * Math.cos(p), Math.sin(p), Math.sin(a) * Math.cos(p));
                    AkashicCubeEntity.shoot(this, c.add(d.scale(0.6)), d, 0.5F, BURST_SMALL, arm == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN, 0.16F).crystal().life(80);
                }
                if (step % 4 == 0) playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 1.0F + step * 0.05F);
            }));
        }
        // ③ 傾いた 3 つの輪: 縦の輪と、前後に 35 度傾いた輪（3 つの面）
        delayed.add(new Delayed(tickCount + 46, () -> {
            Vec3 c = position().add(0, 1.2, 0);
            double tilt = Math.toRadians(35);
            Vec3[] normals = {new Vec3(0, Math.cos(tilt), Math.sin(tilt)), new Vec3(0, Math.cos(tilt), -Math.sin(tilt)), new Vec3(1, 0, 0)};
            for (int ring = 0; ring < 3; ring++) {
                Vec3 nrm = normals[ring].normalize();
                Vec3 u = nrm.cross(Math.abs(nrm.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), v = nrm.cross(u);
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 * i / 20;
                    Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                    AkashicCubeEntity.shoot(this, c.add(d.scale(0.8)), d, 0.42F, BURST_SMALL, colors[ring], 0.17F).crystal().life(90);
                }
                Fx.shockwave(server, c, colors[ring], 5.0F, 12);
            }
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.4F);
        }));
        // ④ 観測の照準: 12 個が光輪のように止まり、溜めてから一斉に相手の行く先へ
        List<AkashicCubeEntity> halo = new ArrayList<>();
        delayed.add(new Delayed(tickCount + 60, () -> {
            Vec3 c = position().add(0, 2.4, 0);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                halo.add(AkashicCubeEntity.shoot(this, c.add(Math.cos(a) * 1.8, Math.sin(a * 2) * 0.3, Math.sin(a) * 1.8), new Vec3(0, 1, 0), 0.001F, 0.0F,
                        colors[i % 3], 0.22F).crystal().life(40));
            }
            playSound(SoundEvents.BEACON_POWER_SELECT, 2.5F, 1.8F);
        }));
        delayed.add(new Delayed(tickCount + 78, () -> {
            LivingEntity t = getTarget();
            for (AkashicCubeEntity h : halo) {
                Vec3 from = h.position();
                h.discard();
                if (t == null || !t.isAlive()) continue;
                Vec3 to = predict(t, from.distanceTo(t.position()) / 1.1).add(0, t.getBbHeight() * 0.5, 0);
                AkashicCubeEntity.shoot(this, from, to.subtract(from), 1.1F, BREACH, AkashicCubeEntity.PINK, 0.22F).crystal().life(40);
            }
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(1.4), getZ(), 1, 0, 0, 0, 0);
            playSound(SoundEvents.TRIDENT_THUNDER, 1.5F, 1.8F);
        }));
    }

    /** 地面の point で結晶の棘が突き出す（見た目と当たり） */
    void spike(ServerLevel server, Vec3 point, float damage) {
        int bit = random.nextInt(2);                                                         // 棘は結晶の房（2026-10-26「結晶槍自体も結晶らしくして」。桃は魔法、水色は物理）
        RulerFxEntity.spawn(server, RulerFxEntity.Z_SPIKE, 0, point, 1.8F + random.nextFloat() * 0.6F, 0, 0, 16, bit == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.PINK);
        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState()),
                point.x, point.y + 0.3, point.z, 10, 0.3, 0.2, 0.3, 0.1);                   // 地面を割って出る結晶の欠片
        Fx.burst(server, MementoMoriEntity.glow(random.nextBoolean() ? AkashicCubeEntity.PINK : AkashicCubeEntity.VIOLET, 0.3F, 12), point.add(0, 0.6, 0), 8, 0.12);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(1.2, 2.0, 1.2), this::foe)) {
            long shown = Math.round(damage * (isMarked(e) ? MARK_BONUS : 1.0F));
            if (e.hurt(damageOf(bit == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.PINK, null), damage)) {   // 棘の色（0 は桃の魔法、1 は水色の物理）
                Fx.segmentNumber(server, e.position().add(0, e.getBbHeight() + 0.4, 0), shown, AkashicCubeEntity.CYAN);   // 当たった数字は 7 セグ（ふつうの 10 進数）
                e.push(0, 0.45, 0);
                e.hurtMarked = true;
                observed(2.0F);
            }
        }
        server.playSound(null, BlockPos.containing(point), SoundEvents.AMETHYST_CLUSTER_PLACE, getSoundSource(), 1.2F, 0.8F + random.nextFloat() * 0.4F);
    }

    /** 結晶槍: 相手への道に予告の線、地面から結晶の棘が 12 本、手前から順に突き出す（{@value #LANCE}・跳ね上げる） */
    public void lance(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 from = new Vec3(getX(), t.getY(), getZ());
        Vec3 dir = t.position().subtract(from).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0);
        dir = dir.normalize();
        double yaw = Math.atan2(dir.z, dir.x);
        RulerFxEntity.spawn(server, RulerFxEntity.LANE, 18 | RulerFxEntity.LANE_SEGMENTS, from.add(0, 0.05, 0), 20.0F, (float) yaw, 0, 18 + 30, AkashicCubeEntity.PINK);   // 道に立つ数字は 7 セグの 0 と 1
        for (int k = 0; k < 12; k++) {
            Vec3 p = from.add(dir.scale(1.5 + k * 1.5));
            delayed.add(new Delayed(tickCount + 18 + k * 2, () -> spike(server, p, LANCE)));
        }
        castLock = 30;
        level().broadcastEntityEvent(this, EV_LANCE);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.7F);
        say("lance", ChatFormatting.LIGHT_PURPLE);
    }

    /** 屈折光線: 相手のまわりの 3 か所に結晶を置き、1.2 秒後に胸から結晶へ光が折れ曲がって走る（{@value #REFRACT}。線の上にいなければ当たらない） */
    void refract(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        double base = random.nextDouble() * Math.PI * 2;
        Vec3[] pts = new Vec3[4];
        pts[0] = hand();
        for (int i = 0; i < 3; i++) {
            double a = base + i * Math.PI * 2 / 3;
            pts[i + 1] = t.position().add(Math.cos(a) * 3.5, 1.0 + i * 0.4, Math.sin(a) * 3.5);
            AkashicCubeEntity.shoot(this, pts[i + 1], new Vec3(0, 1, 0), 0.001F, 0.0F, AkashicCubeEntity.VIOLET, 0.3F).crystal().life(32);
        }
        for (int i = 0; i < 3; i++) Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.VIOLET, 0.08F, 24), pts[i], pts[i + 1], 0.5);   // 予告の細い光
        delayed.add(new Delayed(tickCount + 24, () -> {
            for (int i = 0; i < 3; i++) {
                Vec3 a = pts[i], b = pts[i + 1];
                Fx.line(server, MementoMoriEntity.glow(AkashicCubeEntity.CYAN, 0.5F, 12), a, b, 0.3);
                Fx.line(server, MementoMoriEntity.glow(0xFFFFFF, 0.2F, 10), a, b, 0.6);
                for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.0), this::foe)) {
                    if (segmentDistance(e.position().add(0, e.getBbHeight() * 0.5, 0), a, b) <= 0.9 + e.getBbWidth() / 2 && e.hurt(damageOf(AkashicCubeEntity.CYAN, null), REFRACT)) {
                        observed(3.0F);
                    }
                }
            }
            playSound(SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.8F);
        }));
        castLock = 30;
        level().broadcastEntityEvent(this, EV_REFRACT);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
        say("refract", ChatFormatting.AQUA);
    }

    static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double k = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr())));
        return p.distanceTo(a.add(ab.scale(k)));
    }

    /** 結晶の檻（第 2 形態）: 相手のまわり半径 4 に結晶が 8 つ並び、1.2 秒後に一斉に真ん中へ（1 つ {@value #CAGE}。輪の外へ出れば避けられる） */
    void cage(LivingEntity t) {
        Vec3 c = t.position().add(0, 1.0, 0);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            Vec3 p = c.add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0);
            int color = i % 2 == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN;
            AkashicCubeEntity.shoot(this, p, new Vec3(0, 1, 0), 0.001F, 0.0F, color, 0.22F).crystal().life(24);
            delayed.add(new Delayed(tickCount + 24, () -> AkashicCubeEntity.shoot(this, p, c.subtract(p), 0.55F, CAGE, color, 0.22F).crystal().life(16)));
        }
        castLock = 30;
        level().broadcastEntityEvent(this, EV_CAGE);
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.0F);
        say("cage", ChatFormatting.LIGHT_PURPLE);
    }

    /** 観測の瞬き（第 2 形態）: 相手の背後へ瞬間移動し、元の所に残した結晶の残像が 0.8 秒後に砕ける（半径 2.5・{@value #BLINK}） */
    void blink(LivingEntity t) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 back = Vec3.directionFromRotation(0, t.getYRot()).multiply(1, 0, 1).normalize().scale(-3.5);
        Vec3 to = t.position().add(back);
        Vec3 old = position();
        if (!level().noCollision(this, getBoundingBox().move(to.subtract(old)))) return;   // 背後に入れないときはやめる
        Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.VIOLET, 0.35F, 16), old.add(0, 1.0, 0), 20, 0.2);
        AkashicCubeEntity.shoot(this, old.add(0, 1.0, 0), new Vec3(0, 1, 0), 0.001F, 0.0F, AkashicCubeEntity.VIOLET, 0.45F).crystal().life(16);
        delayed.add(new Delayed(tickCount + 16, () -> {
            Fx.shockwave(server, old.add(0, 0.5, 0), AkashicCubeEntity.VIOLET, 3.0F, 14);
            Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.PINK, 0.3F, 14), old.add(0, 1.0, 0), 24, 0.3);
            for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, new AABB(old, old).inflate(2.5, 2.0, 2.5), this::foe)) {
                if (e.distanceToSqr(old) <= 2.5 * 2.5 + 1 && e.hurt(damageOf(AkashicCubeEntity.VIOLET, null), BLINK)) observed(3.0F);
            }
            server.playSound(null, BlockPos.containing(old), SoundEvents.AMETHYST_CLUSTER_BREAK, getSoundSource(), 2.0F, 0.6F);
        }));
        teleportTo(to.x, to.y, to.z);
        setYRot((float) Math.toDegrees(Math.atan2(t.getZ() - to.z, t.getX() - to.x)) - 90.0F);
        yBodyRot = getYRot();
        Fx.burst(server, MementoMoriEntity.glow(AkashicCubeEntity.CYAN, 0.3F, 14), to.add(0, 1.0, 0), 16, 0.2);
        castLock = 16;
        level().broadcastEntityEvent(this, EV_BLINK);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5F, 1.4F);
        say("blink", ChatFormatting.LIGHT_PURPLE);
    }

    // ------------------------------------------------------------------ 受ける・倒れる

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && barrierLeft() > 0) amount *= BARRIER_CUT;
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) observed(1.5F);
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide) {
            protect.markDefeated();
            setHealth(0.0F);
            return;
        }
        if (protect.interceptDie()) return;                                                  // 被弾を通らない die() の直接呼び出しは即死攻撃として断る
        delayed.clear();
        burstLeft = 0;
        setNoGravity(false);
        marks.clear();
        entityData.set(MARKS, new CompoundTag());
        entityData.set(BARRIER, 0);
        entityData.set(CHARGE, 0);
        super.die(source);
    }

    /** 撃破: 立方体の欠片になってほどけ、光って消える */
    @Override
    protected void tickDeath() {
        if (protect.refuseDeathTick()) return;
        ++deathTime;
        if (level().isClientSide) {
            protect.markDefeated();
            return;
        }
        if (level() instanceof ServerLevel server && deathTime % 4 == 0) {
            Fx.burst(server, MementoMoriEntity.glow(deathTime % 8 == 0 ? AkashicCubeEntity.PINK : AkashicCubeEntity.CYAN, 0.3F, 16), position().add(0, 1.0, 0), 12, 0.15);
        }
        if (deathTime >= DEATH_TICKS && !DirectRead.isRemoved(this) && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, getX(), getY(1.0), getZ(), 1, 0, 0, 0, 0);
            Fx.shockwave(server, position().add(0, 1.0, 0), AkashicCubeEntity.CYAN, 6.0F, 18);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 1.6F);
            protect.finishDeath();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public Eprotect getEprotect() {
        return protect;
    }

    @Override
    public void restoreGoalsIfEmpty() {
        if (goalSelector.getAvailableGoals().isEmpty()) registerGoals();
    }

    @Override
    public List<ServerBossEvent> bossBars() {
        return List.of(bossEvent, gaugeEvent);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (protect.shouldBlockRemoval(reason)) return;
        super.remove(reason);
    }

    @Override
    @Nullable
    public Component getDefeatSpeech() {
        return null;
    }

    @Override
    public SoundEvent getDefeatSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
        gaugeEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        gaugeEvent.removePlayer(player);
    }

    @Override
    public void onRetired() {
        bossEvent.removeAllPlayers();
        gaugeEvent.removeAllPlayers();
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
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Phase2", isPhase2());
        tag.putFloat("Gauge", gauge());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Phase2")) {
            entityData.set(PHASE2, true);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
        }
        entityData.set(GAUGE, tag.getFloat("Gauge"));
    }

    // ------------------------------------------------------------------ 戦い方

    /** 相手から 7〜11 m を保って横へ回り込み、間の明けた技のうち一番長く使っていないものを選ぶ */
    static class SeerGoal extends Goal {
        private final AkashicSeerEntity seer;
        private int repath, gap, strafe = 1;
        /** 0 観測結晶・射・1 結晶の雨・2 結晶の結界・3 追跡結晶（第 2）・4 結晶槍・5 屈折光線・6 結晶の檻（第 2）・7 観測の瞬き（第 2） */
        private final int[] cd = {20, 80, 200, 60, 100, 140, 90, 120};
        private final int[] last = new int[8];
        private static final boolean[] PHASE2_ONLY = {false, false, false, true, false, false, true, true};

        SeerGoal(AkashicSeerEntity seer) {
            this.seer = seer;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = seer.getTarget();
            return t != null && t.isAlive() && seer.deathTime == 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            seer.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = seer.getTarget();
            if (t == null) return;
            seer.getLookControl().setLookAt(t, 30.0F, 30.0F);
            for (int i = 0; i < cd.length; i++) cd[i]--;
            if (--repath <= 0 && seer.chargeLeft() == 0 && !seer.bursting()) {
                repath = 15;
                if (seer.random.nextInt(4) == 0) strafe = -strafe;
                Vec3 away = seer.position().subtract(t.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
                away = away.normalize();
                double d = seer.distanceTo(t);
                double want = d < 7.0 ? 10.0 : d > 11.0 ? 8.5 : d;
                Vec3 side = new Vec3(-away.z, 0, away.x).scale(strafe * 3.0);
                Vec3 spot = t.position().add(away.scale(want)).add(side);
                seer.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.1);
            }
            if (seer.castLock > 0 || --gap > 0 || seer.chargeLeft() > 0) return;
            if (!seer.getSensing().hasLineOfSight(t)) return;
            if (seer.isPhase2() && seer.gauge() >= 100.0F) {
                seer.record();
                gap = 20;
                return;
            }
            int pick = -1;
            for (int i = 0; i < cd.length; i++) {
                if (cd[i] > 0 || PHASE2_ONLY[i] && !seer.isPhase2()) continue;
                if (pick < 0 || last[i] < last[pick]) pick = i;
            }
            if (pick < 0) return;
            last[pick] = seer.tickCount;
            gap = seer.cd(20);
            switch (pick) {
                case 0 -> {
                    seer.observeShot(t);
                    cd[0] = seer.cd(50);
                }
                case 1 -> {
                    seer.dataRain(t);
                    cd[1] = seer.cd(160);
                }
                case 2 -> {
                    seer.barrier();
                    cd[2] = seer.cd(300);
                }
                case 4 -> {
                    seer.lance(t);
                    cd[4] = seer.cd(180);
                }
                case 5 -> {
                    seer.refract(t);
                    cd[5] = seer.cd(220);
                }
                case 6 -> {
                    seer.cage(t);
                    cd[6] = seer.cd(200);
                }
                case 7 -> {
                    seer.blink(t);
                    cd[7] = seer.cd(260);
                }
                default -> {
                    seer.homing(t);
                    cd[3] = seer.cd(120);
                }
            }
        }
    }
}
