package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind;
import com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind;
import com.automatavalkyries.item.unit.ValkyrieUnits.FrameItem;
import com.automatavalkyries.item.unit.ValkyrieUnits.FrameType;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.ProjectileLike;
import com.automatavalkyries.weapon.WeaponDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 戦術兵姫の装備ユニットの効き目（2026-10-06。外装フレーム §7.4・背部ユニット §7.3・ロジックチップ §7.5、兵姫だけ）。数値で仕様に無い物は【仮】
 * <ul>
 *   <li>外装: 部位ごとに装甲値・防護強度・ノックバック耐性・移動速度（一式の値を 頭 25%・胴 45%・脚 30% に分ける）。一式そろうと:
 *       ヴァルキリー = ブースト（ダッシュ・ステップ・滞空）の電力 -30%、フォートレス = 爆発・物理のダメージ 50% カット、
 *       ミラージュ = 敵が気付く距離が半分、ガリア・フォートレス（全身 1 つ）= 50 以下のダメージはすべて 1（絶対重護）</li>
 *   <li>背部: スラスター = 空中でもダッシュ（相手が上なら上へ）、フォールディングキャノン = 48m の敵へ 3 秒ごとに 2 連の支援砲撃、
 *       ECM = 飛んでくる敵の弾を電磁パルスで消し、まわりの雑魚を混乱させる、タキオンドライブ = 常時 3 倍速と残像・ボスの攻撃を 60% で避ける、
 *       黒死蝶ファミリア = 8 基の蝶が近くの敵へ突撃し、与えたダメージの 30% を回復</li>
 *   <li>チップ: プレシジョン = 会心 35%（1.5 倍）・ボス優先、インターセプト = 飛んでくる敵の弾を撃ち落とす、
 *       アブソリュート = 爆発しそうな物（膨らんだクリーパー・点火した TNT・火の玉）を見つけたら、すぐに全力で安全な所へステップ、
 *       リソースセーバー = 電力 30% 以下で維持・ブーストの電力が半分</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieUnitEffects {

    private static final UUID HEALTH_ID = UUID.fromString("6a1d2c3e-4f50-4a61-b72c-8d9e0f1a2b3f");
    private static final UUID ARMOR_ID = UUID.fromString("6a1d2c3e-4f50-4a61-b72c-8d9e0f1a2b31");
    private static final UUID TOUGH_ID = UUID.fromString("7b2e3d4f-5061-4b72-c83d-9eaf1a2b3c42");
    private static final UUID KNOCK_ID = UUID.fromString("8c3f4e50-6172-4c83-d94e-afb02b3c4d53");
    private static final UUID SPEED_ID = UUID.fromString("9d405f61-7283-4d94-ea5f-b0c13c4d5e64");
    private static final UUID TACHYON_ID = UUID.fromString("ae516072-8394-4ea5-fb60-c1d24d5e6f75");

    /** フォールディングキャノンの間隔・1 発のダメージ、黒死蝶の間隔・1 基のダメージ【仮】 */
    public static final int CANNON_INTERVAL = 60, FAMILIAR_INTERVAL = 40;
    public static final float CANNON_DAMAGE = 60.0F, FAMILIAR_SHARE = 0.2F, FAMILIAR_LIFESTEAL = 0.3F;
    /** タキオン化（HP が尽きる 1 撃を無かったことにする）のクールタイム（Tick）【仮】 */
    public static final int TACHYON_PHASE_COOLDOWN = 1200;
    /** インターセプト・アブソリュートの電力【仮】 */
    public static final int INTERCEPT_FE = 2_000, ABSOLUTE_FE = 10_000;

    private ValkyrieUnitEffects() {}

    static ValkyrieInventory inv(ValkyrieEntity v) {
        return v.getInventory();
    }

    // ------------------------------------------------------------------ 能力（10 Tick ごと）

    /** 外装の能力とタキオンドライブの速さを付け直す */
    public static void applyAttributes(ValkyrieEntity v) {
        double armor = 0, tough = 0, knock = 0, speed = 0, health = 0;
        for (int slot : new int[]{ValkyrieInventory.HEAD, ValkyrieInventory.CHEST, ValkyrieInventory.LEGS}) {
            FrameItem f = inv(v).frame(slot);
            if (f == null) continue;
            armor += f.armor();
            tough += f.toughness();
            health += f.health();
            knock += f.type.knockback * f.part.share;
            speed += f.type.speed * f.part.share;
        }
        set(v, Attributes.ARMOR, ARMOR_ID, armor, AttributeModifier.Operation.ADDITION);
        set(v, Attributes.ARMOR_TOUGHNESS, TOUGH_ID, tough, AttributeModifier.Operation.ADDITION);
        set(v, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADDITION);
        v.clampVitals(); // 外したら HP・シールドを新しい最大まで下げる
        set(v, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, Math.min(1.0, knock), AttributeModifier.Operation.ADDITION);
        set(v, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.MULTIPLY_BASE);
        set(v, Attributes.MOVEMENT_SPEED, TACHYON_ID, tachyonOn(v) ? 2.0 : 0.0, AttributeModifier.Operation.MULTIPLY_TOTAL); // 常時 3 倍速
    }

    private static void set(ValkyrieEntity v, Attribute attr, UUID id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = v.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) inst.removeModifier(id);
        if (amount != 0.0) inst.addTransientModifier(new AttributeModifier(id, "valkyrie unit", amount, op));
    }

    /** 外装の最大シールドの加算（素体の最大に足し、オメガコアの 3 倍はその後） */
    public static float frameShield(ValkyrieEntity v) {
        float s = 0;
        for (int slot : new int[]{ValkyrieInventory.HEAD, ValkyrieInventory.CHEST, ValkyrieInventory.LEGS}) {
            if (v.unitItem(slot).getItem() instanceof FrameItem f) s += (float) f.shield(); // クライアントでも（HUD の最大値）
        }
        return s;
    }

    /** 装備の重さ（外装・背部・チップ） */
    public static float unitWeight(ValkyrieEntity v) {
        float w = 0;
        for (int slot : new int[]{ValkyrieInventory.HEAD, ValkyrieInventory.CHEST, ValkyrieInventory.LEGS}) {
            FrameItem f = inv(v).frame(slot);
            if (f != null) w += f.weight();
        }
        BackpackKind b = inv(v).backpack();
        if (b != null) w += b.weight;
        for (ChipKind c : ChipKind.values()) if (inv(v).hasChip(c)) w += com.automatavalkyries.item.unit.ValkyrieUnits.CHIP_WEIGHT;
        return w;
    }

    // ------------------------------------------------------------------ 電力の割引

    /** ブーストの電力の倍率（ヴァルキリーフレーム一式 -30%〜-50%（Mk ごと）、リソースセーバー -50%） */
    public static float boostCostScale(ValkyrieEntity v) {
        FrameType set = inv(v).frameSet();
        float s = set != null ? set.boostScale : 1.0F; // ヴァルキリー系の一式（Mk ごと）
        if (saving(v)) s *= 0.5F;
        return s;
    }

    /** リソースセーバーが働いているか（電力 30% 以下） */
    public static boolean saving(ValkyrieEntity v) {
        return inv(v).hasChip(ChipKind.RESOURCE_SAVER) && v.getEnergy() <= ValkyrieEntity.MAX_ENERGY * 3 / 10;
    }

    /** 体の維持の電力（リソースセーバーなら半分） */
    public static int upkeep(ValkyrieEntity v) {
        return saving(v) ? Math.max(1, ValkyrieEntity.UPKEEP / 2) : ValkyrieEntity.UPKEEP;
    }

    // ------------------------------------------------------------------ 毎 Tick（戦闘の状態から）

    /** タキオンドライブ: 電力があって動けるとき（1 秒ごとに 20,000 FE） */
    static boolean tachyonOn(ValkyrieEntity v) {
        return inv(v).backpack() == BackpackKind.TACHYON_DRIVE && v.getEnergy() > BackpackKind.TACHYON_DRIVE.fe + ValkyrieEntity.WAKE_ENERGY && !v.isShutdown() && !v.isAsleep();
    }

    /** 兵姫の毎 Tick（{@link ValkyrieCombat.Brain#tick} から）。間隔は Brain の数で */
    static void tick(ValkyrieEntity v, ValkyrieCombat.Brain b) {
        if (!(v.level() instanceof ServerLevel level)) return;
        BackpackKind back = inv(v).backpack();
        LivingEntity target = v.getTarget();
        if (v.tickCount % 10 == 0) applyAttributes(v);
        frameTick(level, v); // 特殊なフレームの一式の効果
        ValkyrieSupportUnits.tick(level, v, b); // 追加の背部ユニット
        if (back == BackpackKind.TACHYON_DRIVE && v.tickCount % 20 == 0 && tachyonOn(v)) b.spend(BackpackKind.TACHYON_DRIVE.fe);
        if (back == BackpackKind.TACHYON_DRIVE && tachyonOn(v) && v.getDeltaMovement().horizontalDistanceSqr() > 0.01 && v.tickCount % 2 == 0) {
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.6F, 8), v.position().add(0, 0.9, 0), 2, 0.2, 0.4, 0.2, 0.0); // 残像
        }
        if (target != null && target.isAlive()) {
            if (back == BackpackKind.FOLDING_CANNON && v.tickCount % CANNON_INTERVAL == 0) cannon(level, v, b, target);
            if (back == BackpackKind.MEMENTO_FAMILIAR && v.tickCount % FAMILIAR_INTERVAL == 0) familiar(level, v, b, target);
        }
        if (back == BackpackKind.ECM_POD && v.tickCount % 5 == 0 && b.ecmCooldown <= 0) ecm(level, v, b);
        if (b.ecmCooldown > 0) b.ecmCooldown--;
        if (inv(v).hasChip(ChipKind.INTERCEPT) && v.tickCount % 4 == 0) intercept(level, v, b);
        if (inv(v).hasChip(ChipKind.ABSOLUTE) && v.tickCount % 2 == 0 && b.absoluteCooldown <= 0) absolute(level, v, b);
        if (b.absoluteCooldown > 0) b.absoluteCooldown--;
    }

    /** 背の位置（砲・蝶が出る所） */
    static Vec3 back(ValkyrieEntity v) {
        float yaw = v.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
        return v.position().add(net.minecraft.util.Mth.sin(yaw) * 0.35, v.getBbHeight() * 0.85, -net.minecraft.util.Mth.cos(yaw) * 0.35);
    }

    /** フォールディングキャノン: 48m の見えている敵へ 2 連の光線 */
    private static void cannon(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b, LivingEntity t) {
        if (v.distanceTo(t) > 48 || !v.hasLineOfSight(t) || !b.spend(BackpackKind.FOLDING_CANNON.fe)) return;
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        for (int i = -1; i <= 1; i += 2) {
            float yaw = v.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 from = back(v).add(net.minecraft.util.Mth.cos(yaw) * 0.3 * i, 0.4, net.minecraft.util.Mth.sin(yaw) * 0.3 * i);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.5F, 8), from, to, 0.4);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0xFFFFFF, 0.3F, 8), from, 6, 0.2);
        }
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0x7FE8FF, 0.4F, 10), to, 12, 0.3);
        t.invulnerableTime = 0;
        t.hurt(WeaponDamage.source(v, WeaponDamage.PARTICLE_BEAM, v, v), CANNON_DAMAGE);
        t.invulnerableTime = 0;
        t.hurt(WeaponDamage.source(v, WeaponDamage.PARTICLE_BEAM, v, v), CANNON_DAMAGE);
        level.playSound(null, v.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.NEUTRAL, 1.0F, 0.7F);
    }

    /** 黒死蝶ファミリア: 8 基の蝶が 16m の敵へ突撃し、与えたダメージの 30% を回復 */
    /** 黒死蝶 1 基のダメージ: 手持ちの攻撃力（{@link ValkyrieEntity#attackValue}）の 20%（2026-10-06 ユーザー指定「手持ちの攻撃力依存」。最低 1） */
    static float familiarDamage(ValkyrieEntity v) {
        return Math.max(1.0F, v.attackValue() * FAMILIAR_SHARE);
    }

    private static void familiar(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b, LivingEntity t) {
        if (v.distanceTo(t) > 16 || !b.spend(BackpackKind.MEMENTO_FAMILIAR.fe)) return;
        float before = t.getHealth();
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + v.tickCount * 0.1;
            Vec3 from = v.position().add(Math.cos(a) * 1.2, 1.2 + Math.sin(a * 2) * 0.3, Math.sin(a) * 1.2);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xB070FF, 0.35F, 10), from, to, 0.5);
            t.invulnerableTime = 0;
            t.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), familiarDamage(v));
        }
        Fx.burst(level, Fx.opt(ModParticles.GLOW.get(), 0xFF8AD8, 0.4F, 12), to, 16, 0.3);
        float dealt = Math.max(0, before - t.getHealth());
        if (dealt > 0) v.heal(dealt * FAMILIAR_LIFESTEAL);
        level.playSound(null, v.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 0.8F, 1.6F);
    }

    /** ECM: 飛んでくる敵の弾があれば電磁パルスで消し、まわり 8m の雑魚を混乱させる（5 秒ごとまで） */
    private static void ecm(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        var shots = level.getEntities(v, v.getBoundingBox().inflate(8.0), e -> ProjectileLike.is(e) && !ValkyrieCombat.isAlly(v, e) && approaching(v, e));
        if (shots.isEmpty() || !b.spend(BackpackKind.ECM_POD.fe)) return;
        for (Entity s : shots) {
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0x7FE8FF, 0.3F, 8), s.position(), 6, 0.2);
            s.discard();
        }
        for (Mob m : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(8.0), m -> m instanceof Enemy && !m.getType().is(Tags.EntityTypes.BOSSES))) {
            m.setTarget(null); // 混乱
            m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        }
        Fx.shockwave(level, v.position().add(0, 0.9, 0), 0x7FE8FF, 8.0F, 12);
        level.playSound(null, v.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.8F);
        b.ecmCooldown = 100;
    }

    /** インターセプト: 12m の、自分か持ち主へ向かう敵の弾を 1 つ撃ち落とす */
    private static void intercept(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        Player owner = v.getOwner();
        for (Entity s : level.getEntities(v, v.getBoundingBox().inflate(12.0), e -> ProjectileLike.is(e) && !ValkyrieCombat.isAlly(v, e))) {
            if (!approaching(v, s) && !(owner != null && approaching(owner, s))) continue;
            if (!v.hasLineOfSight(s) || !b.spend(INTERCEPT_FE)) return;
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFF5A6E, 0.3F, 6), v.getEyePosition(), s.position(), 0.4);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0xFFD166, 0.3F, 8), s.position(), 8, 0.25);
            level.playSound(null, s.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.7F, 1.8F);
            s.discard();
            return;
        }
    }

    /** アブソリュート: 爆発しそうな物を見つけたら、溜めを止めて、全力で離れる向きへステップ */
    private static void absolute(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        Vec3 danger = null;
        for (Entity e : level.getEntities(v, v.getBoundingBox().inflate(7.0))) {
            boolean boom = (e instanceof Creeper c && c.getSwelling(1.0F) > 0.2F && e.distanceTo(v) < 5.0)
                    || (e instanceof PrimedTnt tnt && tnt.getFuse() < 30)
                    || (e instanceof AbstractHurtingProjectile && !ValkyrieCombat.isAlly(v, e) && approaching(v, e));
            if (boom) {
                danger = e.position();
                break;
            }
        }
        if (danger == null || v.getEnergy() < ABSOLUTE_FE + ValkyrieEntity.WAKE_ENERGY) return;
        if (v.isUsingItem()) v.stopUsingItem(); // 攻撃を中断
        Vec3 away = v.position().subtract(danger);
        if (ValkyrieCombat.orderStep(v, away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away)) {
            b.spend(ABSOLUTE_FE);
            v.setDeltaMovement(v.getDeltaMovement().multiply(1.6, 1.0, 1.6)); // 全力
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), 0xFFD166, 0.4F, 10), v.position().add(0, 1, 0), 12, 0.3);
            b.absoluteCooldown = 40;
        }
    }

    static boolean approaching(LivingEntity g, Entity s) {
        Vec3 vel = ProjectileLike.velocity(s);
        if (vel.lengthSqr() < 1e-4) return false;
        Vec3 rel = g.position().add(0, g.getBbHeight() * 0.5, 0).subtract(s.position());
        double t = rel.dot(vel) / vel.lengthSqr();
        return t > 0 && t < 20 && rel.subtract(vel.scale(t)).lengthSqr() < 2.0 * 2.0;
    }

    // ------------------------------------------------------------------ ダメージ

    /** プレシジョン: 兵姫の攻撃が 35% で会心（1.5 倍） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ValkyrieEntity v && victim != v) {
            FrameType set = inv(v).frameSet();
            boolean melee = event.getSource().getDirectEntity() == v && !ranged(v);
            float mult = 1.0F;
            if (set == FrameType.STRIKER && melee) {
                mult *= 1.2F; // 近接 +20%
                if (v.level().getGameTime() < v.combat().dashBoostUntil) { // ダッシュ直後の 1 撃は 1.5 倍
                    mult *= 1.5F;
                    v.combat().dashBoostUntil = 0;
                }
            }
            if (set == FrameType.TEMPEST && !v.onGround()) mult *= 1.2F; // 空中で +20%
            if (set == FrameType.HELIOS && ranged(v)) mult *= 1.25F;     // 射撃 +25%
            if (overdrive(v)) mult *= 1.5F;                              // オーバードライブ
            if (mult != 1.0F) event.setAmount(event.getAmount() * mult);
        }
        if (attacker instanceof ValkyrieEntity v && victim != v && inv(v).hasChip(ChipKind.PRECISION) && v.getRandom().nextFloat() < 0.35F) {
            event.setAmount(event.getAmount() * 1.5F);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            }
        }
        // フォートレス一式: 爆発・物理のダメージ 50% カット
        if (victim instanceof ValkyrieEntity v && inv(v).frameSet() == FrameType.FORTRESS
                && (event.getSource().is(DamageTypeTags.IS_EXPLOSION) || !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR))) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    /** ガリア・フォートレス（背部ユニット。絶対重護）: 防具の後で 50 以下のダメージは 1 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ValkyrieEntity v && !v.level().isClientSide && inv(v).backpack() == BackpackKind.GALLIA_FORTRESS
                && event.getAmount() > 0 && event.getAmount() <= 50.0F) {
            event.setAmount(Math.min(event.getAmount(), 1.0F));
        }
    }

    /**
     * ガリア・フォートレスの変換を、シールドが受ける前に掛ける（{@link ValkyrieEntity#actuallyHurt} から）。
     * シールドは防具より前に受けるので、防具の後の量を見積もって 50 以下なら 1 にする
     */
    public static float galliaBarrier(ValkyrieEntity v, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0 || inv(v).backpack() != BackpackKind.GALLIA_FORTRESS) return amount;
        float after = source.is(DamageTypeTags.BYPASSES_ARMOR) ? amount
                : net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(amount, v.getArmorValue(), (float) v.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
        return after <= 50.0F ? Math.min(amount, 1.0F) : amount;
    }

    /**
     * タキオンドライブ: HP が尽きる 1 撃（シールドの後）を、タキオン化して無かったことにする（{@link #TACHYON_PHASE_COOLDOWN} ごと）。
     * {@link ValkyrieEntity#actuallyHurt} から。消したら true（2026-10-06 ユーザー指定）
     */
    public static boolean tachyonPhase(ValkyrieEntity v, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0 || !tachyonOn(v) || v.level().getGameTime() < v.combat().tachyonReadyAt) return false;
        float after = source.is(DamageTypeTags.BYPASSES_ARMOR) ? amount
                : net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(amount, v.getArmorValue(), (float) v.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (after < com.automatavalkyries.protect.DirectRead.health(v)) return false;
        v.combat().tachyonReadyAt = v.level().getGameTime() + TACHYON_PHASE_COOLDOWN;
        v.invulnerableTime = 20;
        if (v.level() instanceof ServerLevel level) {
            Vec3 at = v.position().add(0, 0.9, 0);
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.9F, 16), at, 24, 0.4, 0.7, 0.4, 0.02);
            Fx.shockwave(level, v.position().add(0, 0.05, 0), 0x7FE8FF, 2.5F, 12);
            level.playSound(null, v.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 1.4F);
        }
        v.combat().line("tachyon_evade", ValkyrieEntity.Emote.NOTE);
        return true;
    }

    /** ミラージュ一式: 敵がこの兵姫に気付けるのは、いつもの半分の距離まで */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ValkyrieEntity v) || !(event.getEntity() instanceof Mob m) || inv(v).frameSet() != FrameType.MIRAGE) return;
        if (m.getLastHurtByMob() == v) return; // 攻撃されたら気付く
        double range = m.getAttributeValue(Attributes.FOLLOW_RANGE) * 0.5;
        if (m.distanceToSqr(v) > range * range) event.setCanceled(true);
    }

    /** 背部ユニットが使えるか（ヘルパー） */
    @Nullable
    public static BackpackKind backpack(ValkyrieEntity v) {
        return inv(v).backpack();
    }

    /** 挿している役割のチップ（ヴァンガード・リアガード・メディック。無ければ null） */
    @org.jetbrains.annotations.Nullable
    public static ChipKind role(ValkyrieEntity v) {
        for (ChipKind k : new ChipKind[]{ChipKind.VANGUARD, ChipKind.REARGUARD, ChipKind.MEDIC}) if (inv(v).hasChip(k)) return k;
        return null;
    }

    /** アタックセレクト: null = 挿していない、true = チャージ攻撃だけ、false = 通常攻撃だけ */
    @org.jetbrains.annotations.Nullable
    public static Boolean chargeOnly(ValkyrieEntity v) {
        net.minecraft.world.item.ItemStack chip = inv(v).chip(ChipKind.ATTACK_MODE);
        return chip.isEmpty() ? null : com.automatavalkyries.item.unit.ValkyrieUnits.chargeOnly(chip);
    }

    /** タレット: 覚えた対象（空なら敵対モブみな）。挿していなければ null */
    @org.jetbrains.annotations.Nullable
    public static java.util.List<net.minecraft.world.entity.EntityType<?>> turretTargets(ValkyrieEntity v) {
        net.minecraft.world.item.ItemStack chip = inv(v).chip(ChipKind.TURRET);
        return chip.isEmpty() ? null : com.automatavalkyries.item.unit.ValkyrieUnits.turretTargets(chip);
    }

    /** 背負っているスラスターの機動（スラスターでなければ null）。テンペストフレーム一式で空中ダッシュ +2 回・滞空 +50% */
    @org.jetbrains.annotations.Nullable
    public static com.automatavalkyries.item.unit.ValkyrieUnits.Maneuver maneuver(ValkyrieEntity v) {
        BackpackKind k = inv(v).backpack();
        var m = k != null ? k.maneuver : null;
        if (m == null || inv(v).frameSet() != FrameType.TEMPEST) return m;
        int unlimited = com.automatavalkyries.item.unit.ValkyrieUnits.Maneuver.UNLIMITED;
        return new com.automatavalkyries.item.unit.ValkyrieUnits.Maneuver(
                m.airDashes() == unlimited ? unlimited : m.airDashes() + 2, m.airJumps(),
                m.hoverTicks() == unlimited ? unlimited : Math.round(m.hoverTicks() * 1.5F), m.airFollow(), m.airCombat(), m.evasive());
    }

    // ------------------------------------------------------------------ 特殊なフレーム（2026-10-06）

    /** 見た目の外装（クライアントでも）が 3 部位ともその型か */
    static boolean wearsSet(ValkyrieEntity v, FrameType type) {
        for (int slot : new int[]{ValkyrieInventory.HEAD, ValkyrieInventory.CHEST, ValkyrieInventory.LEGS}) {
            if (!(v.unitItem(slot).getItem() instanceof FrameItem f) || f.type != type) return false;
        }
        return true;
    }

    /** 拾い物の範囲の倍率（サブアームで 1.5） */
    public static float pickupScale(ValkyrieEntity v) {
        return backpack(v) == BackpackKind.SUB_ARM ? 1.5F : 1.0F;
    }

    /** 電力の最大（ルミナス一式で 1.5 倍、増設バッテリーで +30,000） */
    public static int maxEnergy(ValkyrieEntity v) {
        return (wearsSet(v, FrameType.LUMINOUS) ? ValkyrieEntity.MAX_ENERGY * 3 / 2 : ValkyrieEntity.MAX_ENERGY) + ValkyrieSupportUnits.extraEnergy(v);
    }

    /** 兵装・スラスター・ユニットの電力消費の倍率（ルミナス一式で 0.7） */
    public static float powerScale(ValkyrieEntity v) {
        return inv(v).frameSet() == FrameType.LUMINOUS ? 0.7F : 1.0F;
    }

    /** 充電スタンドの充電の倍率（メンテナンス一式で 1.5） */
    public static float chargeScale(ValkyrieEntity v) {
        return inv(v).frameSet() == FrameType.MAINTENANCE ? 1.5F : 1.0F;
    }

    /** メディックの回復の倍率（セラフ一式で 1.5） */
    public static float healScale(ValkyrieEntity v) {
        return inv(v).frameSet() == FrameType.SERAPH ? 1.5F : 1.0F;
    }

    /** オーバードライブ中か（ネメシス） */
    public static boolean overdrive(ValkyrieEntity v) {
        return v.level().getGameTime() < v.combat().overdriveUntil;
    }

    /** 攻撃の間隔の倍率（ヘリオスの射撃 0.8、オーバードライブ中 2/3） */
    public static float attackIntervalScale(ValkyrieEntity v) {
        float s = 1.0F;
        if (inv(v).frameSet() == FrameType.HELIOS && ranged(v)) s *= 0.8F;
        if (overdrive(v)) s /= 1.5F;
        if (ValkyrieSupportUnits.accelerated(v)) s *= 0.7F; // アクセラレーター
        return s;
    }

    /** 持った兵装が射撃の型か */
    static boolean ranged(ValkyrieEntity v) {
        var art = com.automatavalkyries.weapon.ValkyrieArts.of(v.getMainHandItem());
        return art != null && art.style() != com.automatavalkyries.weapon.ValkyrieArts.Style.MELEE;
    }

    /** 外装一式の毎 Tick の効果（スカウト・メンテナンス・セラフ・ネメシス） */
    static void frameTick(ServerLevel level, ValkyrieEntity v) {
        FrameType set = inv(v).frameSet();
        if (set == null) return;
        switch (set) {
            case SCOUT -> {
                if (v.tickCount % 20 == 0) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(SCOUT_RANGE),
                            e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive() && !ValkyrieCombat.isAlly(v, e) && v.distanceToSqr(e) <= SCOUT_RANGE * SCOUT_RANGE)) {
                        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 40, 0, false, false));
                    }
                }
            }
            case MAINTENANCE -> {
                if (v.tickCount % 20 == 0 && v.getTarget() == null && v.ticksSinceHurt() > 100 && v.getHealth() < v.getMaxHealth()) v.heal(MAINTENANCE_REGEN);
            }
            case SERAPH -> {
                if (v.tickCount % 10 == 0) {
                    for (ValkyrieEntity other : level.getEntitiesOfClass(ValkyrieEntity.class, v.getBoundingBox().inflate(SERAPH_RANGE),
                            o -> ValkyrieCombat.isAlly(v, o) && o.distanceToSqr(v) <= SERAPH_RANGE * SERAPH_RANGE)) {
                        other.bonusShieldRegen(10.0F); // 回復を倍の速さに
                    }
                    if (v.tickCount % 40 == 0) Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFFC8E8, 0.4F, 16), v.position().add(0, 1.0, 0), 4, 1.5, 0.6, 1.5, 0.0);
                }
            }
            case NEMESIS -> {
                long now = level.getGameTime();
                var b = v.combat();
                if (now >= b.overdriveReadyAt && v.getTarget() != null && v.getHealth() < v.getMaxHealth() * 0.3F) {
                    b.overdriveUntil = now + OVERDRIVE_TICKS;
                    b.overdriveReadyAt = now + OVERDRIVE_COOLDOWN;
                    b.line("overdrive", ValkyrieEntity.Emote.ALERT);
                    Fx.shockwave(level, v.position().add(0, 0.05, 0), 0xFF2040, 3.0F, 14);
                    level.playSound(null, v.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.NEUTRAL, 0.5F, 1.8F);
                }
                if (overdrive(v) && v.tickCount % 2 == 0) {
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0xFF2040, 0.5F, 10), v.position().add(0, 1.0, 0), 3, 0.3, 0.6, 0.3, 0.02);
                }
            }
            default -> {
            }
        }
    }

    /** スカウトの見つける距離・メンテナンスの毎秒の回復・セラフの届く距離・オーバードライブの長さとクールタイム（Tick）【仮】 */
    public static final double SCOUT_RANGE = 24.0, SERAPH_RANGE = 8.0, AEGIS_RANGE = 8.0;
    public static final float MAINTENANCE_REGEN = 1.0F, AEGIS_SHARE = 0.2F;
    public static final int OVERDRIVE_TICKS = 200, OVERDRIVE_COOLDOWN = 2400;

    /** イージス: 8 m 以内の持ち主が受けるダメージの 20% を、そばのイージス一式の兵姫が肩代わりする */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player owner) || owner.level().isClientSide || event.getAmount() <= 0) return;
        ValkyrieEntity guard = null;
        for (ValkyrieEntity v : owner.level().getEntitiesOfClass(ValkyrieEntity.class, owner.getBoundingBox().inflate(AEGIS_RANGE),
                v -> owner.getUUID().equals(v.getOwnerUUID()) && !v.isShutdown() && !v.isAsleep() && v.distanceToSqr(owner) <= AEGIS_RANGE * AEGIS_RANGE
                        && inv(v).frameSet() == FrameType.AEGIS)) {
            if (guard == null || v.distanceToSqr(owner) < guard.distanceToSqr(owner)) guard = v;
        }
        if (guard == null) return;
        float part = event.getAmount() * AEGIS_SHARE;
        event.setAmount(event.getAmount() - part);
        guard.hurt(guard.level().damageSources().generic(), part);
        guard.combat().line("aegis_cover", ValkyrieEntity.Emote.ALERT);
        if (guard.level() instanceof ServerLevel level) Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0x7FB8FF, 0.4F, 10),
                guard.position().add(0, 1.0, 0), owner.position().add(0, 1.0, 0), 0.4);
    }
}
