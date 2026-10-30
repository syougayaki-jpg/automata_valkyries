package com.automatavalkyries.compat.slashblade;

import com.automatavalkyries.AutomataValkyries;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.slasharts.JudgementCut;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Consumer;

/**
 * このMODの刀の独自の抜刀術（2026-10-30。【仮】で私が決めた）。どれも抜刀剣の次元斬の振りの動き（VMD のコマ 1923–1928）を借り、最初の Tick に技の中身を出す。
 * 技の色は金属の色。巻き込むのは敵（{@link Enemy}）と、使い手が最後に攻撃した相手だけ（味方・ペット・兵姫は巻き込まない）
 */
public final class SlashBladeArts {

    public static final DeferredRegister<ComboState> COMBO_STATES = DeferredRegister.create(ComboState.REGISTRY_KEY, AutomataValkyries.MODID);
    public static final DeferredRegister<SlashArts> SLASH_ARTS = DeferredRegister.create(SlashArts.REGISTRY_KEY, AutomataValkyries.MODID);

    /** 光刃「ルミナル・スターリング」: 光華乱舞 — 光の花びらの斬撃を周りに 8 方向へ散らす。当たった相手は光る */
    public static final RegistryObject<SlashArts> LUMINAL_BLOOM = art("luminal_bloom", SlashBladeArts::luminalBloom, 20);
    /** 城刀「クロノ・フォートレス」: 城壁断 — 重い衝撃の壁（3 枚のドライブ）を前へ押し出す */
    public static final RegistryObject<SlashArts> BASTION_BREAK = art("bastion_break", SlashBladeArts::bastionBreak, 20);
    /** 迅刀「タキオン・アクセル」: 超光速斬 — 見ている方へ最大 10 ブロック駆け抜け、通り道の敵をすべて斬る */
    public static final RegistryObject<SlashArts> SUPERLUMINAL_CUT = art("superluminal_cut", SlashBladeArts::superluminalCut, 20);
    /** 蝕刀「エクリプス・バイオメタル」: 蝕月 — 黒い月の爆ぜで周りの敵を枯らし、命を吸う */
    public static final RegistryObject<SlashArts> ECLIPSE_MOON = art("eclipse_moon", SlashBladeArts::eclipseMoon, 20);
    /** 星刻神刀「ステラ・クロノダイム」: 星刻次元斬 — 次元斬の上位（周りの敵すべてに）＋ 周りの敵の時を 3 秒止める */
    public static final RegistryObject<SlashArts> STELLAR_JUDGEMENT = art("stellar_judgement", SlashBladeArts::stellarJudgement, 50);

    private SlashBladeArts() {}

    private static RegistryObject<SlashArts> art(String name, Consumer<LivingEntity> action, int proudSoulCost) {
        RegistryObject<ComboState> state = COMBO_STATES.register(name, () -> ComboState.Builder.newInstance()
                .startAndEnd(1923, 1928).speed(0.4F).priority(50)
                .next(e -> ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, name))
                .nextOfTimeout(e -> ComboStateRegistry.JUDGEMENT_CUT_SHEATH.getId())
                .addTickAction(ComboState.TimeLineTickAction.getBuilder().put(0, action).build())
                .build());
        return SLASH_ARTS.register(name, () -> new SlashArts(e -> state.getId()).setProudSoulCost(proudSoulCost));
    }

    // ---- 技の中身（サーバーだけ） ----

    static void luminalBloom(LivingEntity user) {
        if (user.level().isClientSide) return;
        for (int i = 0; i < 8; i++) {
            SakuraEnd.doSlash(user, i * 45.0F, 0xF4E7A0, Vec3.ZERO, i > 0, true, 1.2, KnockBacks.cancel);
        }
        for (LivingEntity target : targets(user, 6.0)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        }
    }

    static void bastionBreak(LivingEntity user) {
        if (user.level().isClientSide) return;
        for (float yaw : new float[]{-18.0F, 0.0F, 18.0F}) {
            Drive.doSlash(user, -90.0F, yaw, 20, 0x8A6CC8, Vec3.ZERO, true, 2.0, KnockBacks.smash, 1.1F);
        }
        user.level().playSound(null, user.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    static void superluminalCut(LivingEntity user) {
        if (user.level().isClientSide) return;
        Vec3 look = user.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 start = user.position();
        Vec3 end = start;
        // 壁の手前まで、0.5 ずつ進めて確かめる
        for (double d = 0.5; d <= 10.0; d += 0.5) {
            Vec3 next = start.add(dir.scale(d));
            if (!user.level().noCollision(user, user.getBoundingBox().move(next.subtract(user.position())))) break;
            end = next;
        }
        AABB path = user.getBoundingBox().minmax(user.getBoundingBox().move(end.subtract(start))).inflate(1.5);
        float damage = attack(user) * 2.5F;
        for (LivingEntity target : user.level().getEntitiesOfClass(LivingEntity.class, path, e -> hostileTo(user, e))) {
            hit(user, target, damage);
        }
        user.teleportTo(end.x, end.y, end.z);
        user.level().playSound(null, user.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.8F);
        AttackManager.doSlash(user, 0.0F, 0x40E0FF, Vec3.ZERO, false, true, 0.5, KnockBacks.cancel);
        AttackManager.doSlash(user, 180.0F, 0x40E0FF, Vec3.ZERO, true, true, 0.5, KnockBacks.cancel);
    }

    static void eclipseMoon(LivingEntity user) {
        if (user.level().isClientSide) return;
        for (int i = 0; i < 6; i++) {
            SakuraEnd.doSlash(user, i * 60.0F + 15.0F, 0x3A0A4E, Vec3.ZERO, i > 0, true, 1.0, KnockBacks.cancel);
        }
        float damage = attack(user) * 1.5F, drained = 0;
        for (LivingEntity target : targets(user, 7.0)) {
            float before = target.getHealth();
            hit(user, target, damage);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
            drained += Math.max(0, before - target.getHealth());
        }
        if (drained > 0) user.heal(Math.min(8.0F, drained * 0.1F));
        user.level().playSound(null, user.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    static void stellarJudgement(LivingEntity user) {
        if (user.level().isClientSide) return;
        EntityJudgementCut cut = JudgementCut.doJudgementCut(user);
        if (cut != null) cut.setColor(0xFFC8EE);
        JudgementCut.doJudgementCutSuper(user);
        for (LivingEntity target : targets(user, 10.0)) {
            // 時を止める（ほぼ動けず・攻撃も鈍る）
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9));
            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 2));
            target.setDeltaMovement(Vec3.ZERO);
        }
        user.level().playSound(null, user.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    // ---- 共通 ----

    static List<LivingEntity> targets(LivingEntity user, double radius) {
        return user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius), e -> hostileTo(user, e));
    }

    /** 巻き込んでよい相手（敵、または使い手が最後に攻撃した相手。自分・味方・同じチームは除く） */
    static boolean hostileTo(LivingEntity user, LivingEntity target) {
        if (target == user || !target.isAlive() || target.isAlliedTo(user)) return false;
        return target instanceof Enemy || target == user.getLastHurtMob();
    }

    static float attack(LivingEntity user) {
        return user.getAttribute(Attributes.ATTACK_DAMAGE) != null ? (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE) : 4.0F;
    }

    static void hit(LivingEntity user, LivingEntity target, float damage) {
        DamageSource source = user instanceof Player player ? user.damageSources().playerAttack(player) : user.damageSources().mobAttack(user);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }
}
