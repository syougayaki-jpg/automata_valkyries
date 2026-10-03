package com.automatavalkyries.weapon;

import com.automatavalkyries.item.DebugKillStickItem;
import com.automatavalkyries.protect.IEprotected;
import com.automatavalkyries.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 兵装のオーバーロード化（2026-10-04 ユーザー指定。数値は【提案】）。兵装とオーバーロードメモリを作業台で合わせると、その兵装に印（NBT {@code Overload}）が付く
 * （{@link OverloadRecipe}）。2026-10-04 ユーザー指定で強化:
 * <ul>
 *   <li>全兵装: T4 兵装並みの火力（想定の火力は T1 が 10〜50、T2 が 50〜100、T3 が 100〜500、T4 が 1,000 以上なので、
 *       持ち主の与えるダメージをティアごとに T1 ×40・T2 ×15・T3 ×6・T4 ×2 して、どれも T4 並みの 1,000 前後以上にする。{@link #damageMult}）、チャージ時間 ×{@link #CHARGE_MULT}、
 *       エフェクトが派手に（当たった所に紅と金の閃光・衝撃波、溜め・放つときの紅と金の光）</li>
 *   <li>T4 兵装はさらに: 攻撃範囲 ×{@link #RANGE_MULT}（チャージ攻撃・通常攻撃）、持っている間の加護の純粋強化（耐性 IV・再生 VI ほか）、
 *       チャージ攻撃が即死（{@link #kill}: 抹殺の棒と同じ汎用の軽い即死。MOD ごとの専用の対策はしない。
 *       保護対象（このMOD のボス・兵姫）には被弾上限が優先）</li>
 *   <li>見た目: エンチャントの輝き、名前に「オーバーロード」、光る部分が紅と金に脈打つ、刃の光が紅に、持っている間は紅と金の放電が散る（クライアント）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.automatavalkyries.AutomataValkyries.MODID)
public final class Overload {

    public static final String TAG = "Overload";
    /** ティアごとのダメージの倍率（添字 = ティア。T1 ×40・T2 ×15・T3 ×6・T4 ×2） */
    private static final float[] DAMAGE_MULTS = {1F, 40F, 15F, 6F, 2F};
    public static final float CHARGE_MULT = 0.5F;
    public static final double RANGE_MULT = 2.5;
    /** オーバーロードの色: 紅・金・白熱 */
    public static final int CRIMSON = 0xFF2050, GOLD = 0xFFC040, HOT = 0xFFF0E0;

    private Overload() {}

    public static boolean is(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG);
    }

    /** オーバーロード化できるか（兵装で、まだオーバーロードでなく、描くためだけのアイテムでない） */
    public static boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof TacticalWeaponItem && !(stack.getItem() instanceof RequiemBackdropItem) && !is(stack);
    }

    /** オーバーロード化した写し（電力などの NBT はそのまま） */
    public static ItemStack apply(ItemStack weapon) {
        ItemStack out = weapon.copyWithCount(1);
        out.getOrCreateTag().putBoolean(TAG, true);
        return out;
    }

    /** 利き手にオーバーロードの兵装を持っているか */
    public static boolean holding(LivingEntity entity) {
        return is(entity.getMainHandItem());
    }

    /** オーバーロードのダメージの倍率（兵装のティアで決まる。{@link WeaponTiers}） */
    public static float damageMult(Item item) {
        return DAMAGE_MULTS[Math.max(1, Math.min(4, WeaponTiers.of(item)))];
    }

    /** T4 兵装か（星葬冠・終の黄昏・極光轟雷・特異点砲・天輪砲） */
    public static boolean isT4(Item item) {
        return item instanceof LuminousRequiemItem || item instanceof RagnarokEdgeItem || item instanceof PunishmentRailgunItem
                || item instanceof CatastropheBusterItem || item instanceof GenesisHaloItem;
    }

    /** 利き手にオーバーロードの T4 兵装を持っているか */
    public static boolean holdingT4(LivingEntity entity) {
        return holding(entity) && isT4(entity.getMainHandItem().getItem());
    }

    /** 攻撃範囲の倍率（オーバーロードの T4 兵装を持っていれば {@link #RANGE_MULT}） */
    public static double range(@Nullable Entity user) {
        return user instanceof LivingEntity living && holdingT4(living) ? RANGE_MULT : 1.0;
    }

    /** チャージ攻撃の当たりの後に呼ぶ: 撃った者がオーバーロードの T4 兵装を持っていれば即死させる */
    public static void overkill(LivingEntity victim, @Nullable Entity attacker) {
        if (attacker instanceof LivingEntity living && holdingT4(living)) kill(victim, living);
    }

    /**
     * 即死（2026-10-04 ユーザー指定「専用対策はせず、普通の抹殺の棒と同じ程度の軽い即死」）: 抹殺の棒の汎用の手段
     * （最大ダメージ・/kill・強制デスフラグ・Tick 停止。{@link DebugKillStickItem#annihilateGeneric}）。MOD ごとの専用の対策はしない。
     * 保護対象（このMOD のボス・兵姫）とプレイヤーには何もしない（被弾上限を優先）
     */
    public static void kill(LivingEntity victim, LivingEntity attacker) {
        if (victim.isRemoved() || victim == attacker || victim instanceof Player || victim instanceof IEprotected) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        DebugKillStickItem.annihilateGeneric(level, WeaponDamage.source(attacker, WeaponDamage.ENERGY, attacker, attacker), victim);
    }

    /** 与えるダメージを上げ（防具などで減る前の値に掛ける）、当たった所に紅と金の閃光を出す */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity() && holding(attacker)) {
            event.setAmount(event.getAmount() * damageMult(attacker.getMainHandItem().getItem()));
            if (attacker.level() instanceof ServerLevel level) hitFlash(level, event.getEntity());
        }
    }

    /** 当たった所の派手な光: 紅と金の閃光・火花・衝撃波・斬撃の閃き（無敵時間の間に重ねて当たったときは火花だけ） */
    static void hitFlash(ServerLevel level, LivingEntity victim) {
        Vec3 at = new Vec3(victim.getX(), victim.getY(0.55), victim.getZ());
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), level.random.nextBoolean() ? CRIMSON : GOLD, 0.35F, 10), at, 10, 0.45);
        if (victim.invulnerableTime > 10) return;
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), HOT, 1.2F, 6), at, 1, 0, 0);
        Fx.slash(level, at, CRIMSON, 1.6F, 7, level.random.nextFloat() * 3.14F);
        Fx.shockwave(level, victim.position().add(0, 0.05, 0), CRIMSON, 2.0F + victim.getBbWidth(), 10);
        Fx.star(level, at, GOLD, 0.7F, 8, Vec3.ZERO);
    }

    /** 溜めるときと放つときの派手な光（{@link ChargedWeaponItem}）。f は溜まり具合 0〜1 */
    static void chargeFlash(ServerLevel level, LivingEntity user, Vec3 at, float f, boolean release) {
        if (release) {
            Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), HOT, 2.5F, 8), at, 1, 0, 0);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), CRIMSON, 0.45F, 14), at, 24, 0.7);
            Fx.burst(level, Fx.opt(ModParticles.STAR.get(), GOLD, 0.4F, 14), at, 12, 0.5);
            Fx.shockwave(level, user.position().add(0, 0.05, 0), CRIMSON, 5.0F, 14);
            Fx.shockwave(level, user.position().add(0, 0.1, 0), GOLD, 3.5F, 18);
            return;
        }
        double a = user.tickCount * 0.6;
        for (int arm = 0; arm < 2; arm++) {
            double t = a + arm * Math.PI;
            Vec3 p = at.add(Math.cos(t) * (1.4 - f), Math.sin(t * 0.5) * 0.4, Math.sin(t) * (1.4 - f));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), arm == 0 ? CRIMSON : GOLD, 0.12F + 0.1F * f, 6), p, at.subtract(p).scale(1 / 6.0));
        }
        if (user.tickCount % 6 == 0) Fx.shockwave(level, user.position().add(0, 0.05, 0), CRIMSON, 1.5F + 2.5F * f, 10);
    }

    /** 持っている間の加護を掛ける（オーバーロードの T4 兵装なら純粋強化: 耐性は IV、再生は VI、ほかは 2 段上。火炎耐性・暗視などの段の無いものはそのまま） */
    public static void bless(LivingEntity user, MobEffect effect, int duration, int amplifier) {
        int amp = amplifier;
        if (holdingT4(user)) {
            if (effect == MobEffects.DAMAGE_RESISTANCE) amp = 3;
            else if (effect == MobEffects.REGENERATION) amp = 5;
            else if (effect != MobEffects.FIRE_RESISTANCE && effect != MobEffects.NIGHT_VISION && effect != MobEffects.SLOW_FALLING
                    && effect != MobEffects.SATURATION) amp = amplifier + 2;
        }
        user.addEffect(new MobEffectInstance(effect, duration, amp, true, false, true));
    }

    /** オーバーロードの T4 兵装の加護の上乗せ: どの T4 兵装でも耐性 IV・再生 VI・火炎耐性 */
    public static void blessExtras(LivingEntity user) {
        if (!holdingT4(user)) return;
        bless(user, MobEffects.DAMAGE_RESISTANCE, 60, 0);
        bless(user, MobEffects.REGENERATION, 60, 0);
        bless(user, MobEffects.FIRE_RESISTANCE, 60, 0);
    }
}
