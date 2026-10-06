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
    public static final float CANNON_DAMAGE = 60.0F, FAMILIAR_DAMAGE = 6.0F, FAMILIAR_LIFESTEAL = 0.3F;
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

    /** ブーストの電力の倍率（ヴァルキリーフレーム一式 -30%、リソースセーバー -50%） */
    public static float boostCostScale(ValkyrieEntity v) {
        float s = inv(v).frameSet() == FrameType.VALKYRIE ? 0.7F : 1.0F;
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
    private static void familiar(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b, LivingEntity t) {
        if (v.distanceTo(t) > 16 || !b.spend(BackpackKind.MEMENTO_FAMILIAR.fe)) return;
        float before = t.getHealth();
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + v.tickCount * 0.1;
            Vec3 from = v.position().add(Math.cos(a) * 1.2, 1.2 + Math.sin(a * 2) * 0.3, Math.sin(a) * 1.2);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xB070FF, 0.35F, 10), from, to, 0.5);
            t.invulnerableTime = 0;
            t.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), FAMILIAR_DAMAGE);
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

    /** ガリア・フォートレス（絶対重護）: 防具の後で 50 以下のダメージは 1 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ValkyrieEntity v && !v.level().isClientSide && inv(v).frameSet() == FrameType.GALLIA
                && event.getAmount() > 0 && event.getAmount() <= 50.0F) {
            event.setAmount(Math.min(event.getAmount(), 1.0F));
        }
    }

    /** タキオンドライブ: ボスの攻撃を 60% で避ける（残像を残して） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ValkyrieEntity v) || v.level().isClientSide || !tachyonOn(v)) return;
        Entity attacker = event.getSource().getEntity();
        boolean boss = attacker != null && (attacker.getType().is(Tags.EntityTypes.BOSSES) || attacker instanceof com.automatavalkyries.protect.IEprotected);
        if (boss && v.getRandom().nextFloat() < 0.6F) {
            event.setCanceled(true);
            if (v.level() instanceof ServerLevel level) Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7FE8FF, 0.8F, 12), v.position().add(0, 0.9, 0), 8, 0.3, 0.5, 0.3, 0.0);
        }
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

    /** 背負っているスラスターの機動（スラスターでなければ null） */
    @org.jetbrains.annotations.Nullable
    public static com.automatavalkyries.item.unit.ValkyrieUnits.Maneuver maneuver(ValkyrieEntity v) {
        BackpackKind k = inv(v).backpack();
        return k != null ? k.maneuver : null;
    }
}
