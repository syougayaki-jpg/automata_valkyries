package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ミラージュダガー（T2、追加の兵装 第 2 弾。2026-10-03 こちらが考えた）: 片手の短剣。攻撃力 8 / 攻撃速度 2.4。
 * 右クリックで、見ている敵（16 ブロック以内）の背後へ瞬間移動して背中を刺す（20 ダメージ）。元の場所に残像が残る。
 * 背後が塞がっていたら移動しない（電力も使わない）。1 回 20,000 FE、間隔 3 秒。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定）: 「幻影処刑」に変わる。跳べる距離は 32 ブロック（2 倍）。
 * 背後へ跳んだ瞬間、狙った敵から 10 ブロック以内の敵（7 体まで）の背後にも紫の幻影の分身が一斉に現れ、全員の背中を同時に刺す（同じダメージ）。
 * 狙った敵のまわりに半径 10 の紫と紅の陣と衝撃波が広がり、分身から本体へ光の糸が伸びて消える</p>
 */
public class MirageDaggerItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int CAPACITY = 400_000, PER_BLINK = 20_000, COOLDOWN = 60;
    public static final double DAMAGE = 50.0, SPEED = 2.4, RANGE = 16.0;
    public static final float BACKSTAB_DAMAGE = 100.0F, WEIGHT = 1.5F;
    public static final int COLOR = 0xC890FF, CORE = 0xF4E8FF;
    /** オーバーロード: 跳べる距離・分身が現れる範囲・分身の数 */
    public static final double OVER_RANGE = 32.0, OVER_SPREAD = 10.0;
    public static final int OVER_CLONES = 7;

    private final Multimap<Attribute, AttributeModifier> modifiers = ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", DAMAGE - 1.0, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", SPEED - 4.0, AttributeModifier.Operation.ADDITION));

    public MirageDaggerItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, false);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!WeaponPower.has(player, stack, PER_BLINK)) {
            notEnoughPower(player);
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            if (blink(server, player) == null) return InteractionResultHolder.fail(stack);
            WeaponPower.use(player, stack, PER_BLINK);
            player.getCooldowns().addCooldown(this, WeaponUpgrades.cooldown(player, this, COOLDOWN));
            playUse(player, stack);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 戦術兵姫が跳ぶ。跳べる相手がいなければ false（{@link ValkyrieArts}） */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        if (!WeaponPower.has(user, stack, PER_BLINK) || blink(level, user) == null) return false;
        WeaponPower.use(user, stack, PER_BLINK);
        playUse(user, stack);
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 見ている敵の背後へ跳んで刺す（兵姫に持たせたときも同じ処理）。刺した相手（跳べなければ null） */
    public static @Nullable LivingEntity blink(ServerLevel level, LivingEntity user) {
        boolean over = OverloadArts.active(user);
        WeaponUtil.Ray ray = WeaponUtil.ray(level, user, user.getEyePosition(), user.getLookAngle(), over ? OVER_RANGE : RANGE, 1.5, 1);
        if (ray.hits().isEmpty()) return null;
        LivingEntity target = ray.hits().get(0);
        Vec3 away = new Vec3(target.getX() - user.getX(), 0, target.getZ() - user.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 behind = target.position().add(away.scale(target.getBbWidth() * 0.5 + user.getBbWidth() * 0.5 + 0.6));
        AABB box = user.getDimensions(user.getPose()).makeBoundingBox(behind);
        if (!level.noCollision(user, box)) return null;
        Vec3 from = user.position();
        // 残像（元の場所に紫の人影の粒）と、跳んだ先の閃光
        for (int i = 0; i < 12; i++) {
            Vec3 p = from.add((level.random.nextDouble() - 0.5) * 0.6, level.random.nextDouble() * user.getBbHeight(), (level.random.nextDouble() - 0.5) * 0.6);
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.35F, 20), p, new Vec3(0, 0.01, 0));
        }
        Fx.line(level, Fx.opt(ModParticles.SPARK.get(), COLOR, 0.15F, 8), from.add(0, 1, 0), behind.add(0, 1, 0), 0.6);
        float yaw = (float) (Math.toDegrees(Math.atan2(-(-away.x), -away.z))); // 相手の方を向く
        if (user instanceof net.minecraft.server.level.ServerPlayer player) {
            player.teleportTo(level, behind.x, behind.y, behind.z, yaw, player.getXRot()); // 向きも画面に届ける
        } else {
            user.teleportTo(behind.x, behind.y, behind.z);
            user.setYRot(yaw);
        }
        user.setYHeadRot(yaw);
        user.resetFallDistance();
        Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        target.hurt(WeaponUtil.melee(user), BACKSTAB_DAMAGE);
        Fx.slash(level, at, COLOR, 1.4F, 6, (float) (level.random.nextDouble() * Math.PI * 2));
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), CORE, 0.3F, 8), at, 10, 0.3);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), CORE, 1.0F, 5), behind.add(0, 1, 0), 1, 0, 0);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.6F);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (over) phantoms(level, user, target);
        return target;
    }

    /** オーバーロードの幻影処刑: 狙った敵から 10 ブロック以内の敵 7 体の背後に分身が現れて同時に刺す */
    private static void phantoms(ServerLevel level, LivingEntity user, LivingEntity target) {
        Vec3 center = new Vec3(target.getX(), target.getY(0.6), target.getZ());
        List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(OVER_SPREAD),
                e -> e != user && e != target && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && e.distanceTo(target) <= OVER_SPREAD + e.getBbWidth()));
        others.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));
        int k = 0;
        for (LivingEntity e : others) {
            if (k >= OVER_CLONES) break;
            k++;
            // 分身: 相手の背後（持ち主から見て向こう側）に立つ紫の人影
            Vec3 away = new Vec3(e.getX() - user.getX(), 0, e.getZ() - user.getZ());
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 spot = e.position().add(away.scale(e.getBbWidth() * 0.5 + 0.8));
            for (int i = 0; i < 10; i++) {
                Vec3 p = spot.add((level.random.nextDouble() - 0.5) * 0.5, level.random.nextDouble() * 1.8, (level.random.nextDouble() - 0.5) * 0.5);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 3 == 0 ? Overload.CRIMSON : COLOR, 0.4F, 18), p, new Vec3(0, 0.02, 0));
            }
            Vec3 at = new Vec3(e.getX(), e.getY(0.6), e.getZ());
            e.invulnerableTime = 0;
            e.hurt(WeaponUtil.melee(user), BACKSTAB_DAMAGE);
            Fx.slash(level, at, k % 2 == 0 ? COLOR : Overload.GOLD, 1.8F, 8, level.random.nextFloat() * 6.28F);
            Fx.line(level, Fx.opt(ModParticles.GLOW.get(), COLOR, 0.2F, 14), spot.add(0, 1, 0), center, 0.6);
            OverloadArts.impact(level, at);
        }
        Vec3 feet = target.position().add(0, 0.05, 0);
        Fx.mandala(level, feet, COLOR, (float) OVER_SPREAD * 0.6F, 24, false, true);
        Fx.shockwave(level, feet, COLOR, (float) OVER_SPREAD + 0.5F, 14);
        Fx.shockwave(level, feet.add(0, 0.05, 0), Overload.CRIMSON, (float) OVER_SPREAD * 0.6F, 18);
        Fx.slash(level, center, Overload.CRIMSON, 3.0F, 10, level.random.nextFloat() * 6.28F);
        Fx.slash(level, center, COLOR, 3.5F, 10, level.random.nextFloat() * 6.28F);
        OverloadArts.flourish(level, user, (float) OVER_SPREAD);
        level.playSound(null, target.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    @Override
    protected Object[] descArgs() {
        return new Object[]{format(PER_BLINK)};
    }
}
