package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * パニッシュメント・レールガン（T4、元仕様に無い追加の兵装。2026-10-03 ユーザー指定）: 両手超兵器。
 * 右クリック長押しで 30 秒チャージ（そのあいだ足が止まる）、離すと 1,024 ブロック先まで届く極光のレールを撃つ。
 * 線上の全員を貫き、防具・エンチャント・状態異常・耐性・無敵時間を無視するカンストダメージ（2,147,483,647、ダメージの数字付き）。
 * 1 射 5,000,000 FE（チャージの間に分けて引き出し、途中で離すと戻す）。保護対象（Eprotect のボス）には上限が掛かる。
 * 読み込まれていない所の先へは届かない（遠くの地形を読み込ませないため）。
 */
public class PunishmentRailgunItem extends ChargedWeaponItem {

    public static final int CAPACITY = 25_000_000, PER_SHOT = 5_000_000, CHARGE = 600;
    public static final long DAMAGE_VALUE = Integer.MAX_VALUE;
    public static final float DAMAGE = (float) DAMAGE_VALUE, WEIGHT = 40.0F;
    public static final double RANGE = 1024.0, WIDTH = 1.0, SEGMENT = 16.0;
    /** 光子プラズマの色（2026-10-04 再デザイン: シアンブルー〜エレクトリックパープル、放熱はセルリアンの蒼白光） */
    public static final int COLOR_CORE = 0xE0F4FF, COLOR_GOLD = 0x40E0FF, COLOR_CRIMSON = 0xA050FF, CERULEAN = 0x9FE0FF;

    public PunishmentRailgunItem(Properties properties) {
        super(properties, CAPACITY, WEIGHT, CHARGE, PER_SHOT);
    }

    @Override
    public float recoil() {
        return 2.0F;
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide() && selected && entity instanceof LivingEntity user && level.getGameTime() % 20 == 0) blessHolder(user);
    }

    /** 手に持っている間の加護（2026-10-04 ユーザー指定。ルミナス・レクイエムと同じく 1 秒ごとに掛け直す）。極光の疾走: 移動速度上昇 III・跳躍力上昇 II・耐性 III・暗視。兵姫に持たせたときも同じ処理（暗視は切れかけの点滅が出ないよう長めに掛ける） */
    public static void blessHolder(LivingEntity user) {
        Overload.bless(user, net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 60, 2);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.JUMP, 60, 1);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 2);
        Overload.bless(user, net.minecraft.world.effect.MobEffects.NIGHT_VISION, 260, 0);
        Overload.blessExtras(user); // オーバーロードなら耐性 IV・再生 VI
    }

    /** 通常攻撃の間隔（Tick） */
    @Override
    public int cooldownTicks() {
        return 15;
    }

    /** 通常攻撃（左クリック。空振りでも。0.75 秒に 1 回・100,000 FE）: 狙った敵に青白い落雷と放電（{@link T4NormalAttacks}） */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level && T4NormalAttacks.ready(entity, stack, cooldownTicks(), 100_000)) {
            T4NormalAttacks.thunder(level, entity);
        }
        return false;
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return Fx.opt(ModParticles.GLOW.get(), COLOR_GOLD, 0.3F, 10);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (!(level instanceof ServerLevel server)) return;
        int t = elapsed(stack, remaining);
        // チャージ中は足が止まる
        user.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
        if (t > chargeTicks(stack)) {
            if (t % 10 == 0) Fx.spray(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.4F, 8), muzzle(user), 2, 0.1, 0.02);
            rings(server, user, 1F, t);
            vent(server, user, 3, t);
            return;
        }
        float f = (float) t / chargeTicks(stack);
        // 1 秒ごとに足元から輪が広がり、進み具合を知らせる
        if (t % 20 == 0) {
            Fx.shockwave(server, user.position().add(0, 0.05, 0), f < 0.66F ? COLOR_GOLD : COLOR_CRIMSON, 1.5F + 3.5F * f, 16);
            if (user instanceof Player player) {
                player.displayClientMessage(Component.translatable("gui.automata_valkyries.charge", Math.round(f * 100)).withStyle(ChatFormatting.AQUA), true);
            }
            server.playSound(null, user.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 0.5F + f);
        }
        int stage = Math.min(3, t / (chargeTicks(stack) / 4)); // 放熱フィンが開いた段の数（25%・50%・75% で 1 段ずつ）
        if (t > 0 && t % (chargeTicks(stack) / 4) == 0 && t < chargeTicks(stack)) purge(server, user, stage);
        rings(server, user, f, t);
        vent(server, user, stage, t);
        if (t % 20 == 10) Fx.mandala(server, user.getEyePosition().add(user.getLookAngle().scale(4.0)), HOLO, 0.55F + 0.2F * f, 22, true, t % 40 == 10); // 幾何学ホログラム照準
        // 終わりが近づくほど、集まる光が増える
        if (t > chargeTicks(stack) * 2 / 3) Fx.gather(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CRIMSON, 0.25F, 10), muzzle(user), 2, 3.0, 10);
        if (t == chargeTicks(stack)) {
            Fx.burst(server, Fx.opt(ModParticles.SPARK.get(), COLOR_CORE, 0.6F, 14), muzzle(user), 30, 0.5);
            Fx.shockwave(server, user.position().add(0, 0.05, 0), COLOR_CORE, 8.0F, 20);
            server.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.5F);
            if (user instanceof Player player) {
                player.displayClientMessage(Component.translatable("gui.automata_valkyries.charge_ready").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            }
        }
    }

    private static Vec3 muzzle(LivingEntity user) {
        return user.getEyePosition().add(user.getLookAngle().scale(2.2)).add(0, -0.3, 0);
    }

    static final int HOLO = 0x70F0FF;

    /** 砲身の向きと横（右）の向き */
    private static Vec3[] frame(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        return new Vec3[]{look, right, up};
    }

    /** 浮遊加速リング: 銃口の先の 3 つの光の輪が、溜まるほど速く回る */
    static void rings(ServerLevel level, LivingEntity user, float f, int t) {
        if (t % 2 != 0) return;
        Vec3[] fr = frame(user);
        Vec3 m = muzzle(user);
        for (int k = 0; k < 3; k++) {
            Vec3 c = m.add(fr[0].scale(0.25 + k * 0.3 + f * 0.15 * k));
            double r = 0.32 - k * 0.04 + 0.06 * f;
            double spin = t * (0.08 + 0.5 * f) * (k % 2 == 0 ? 1 : -1.4) + k;
            for (int i = 0; i < 6; i++) {
                double a = spin + i * Math.PI / 3;
                Vec3 p = c.add(fr[1].scale(Math.cos(a) * r)).add(fr[2].scale(Math.sin(a) * r));
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), k == 1 ? COLOR_CRIMSON : COLOR_GOLD, 0.08F + 0.06F * f, 3), p, Vec3.ZERO);
            }
        }
    }

    /** フィンの段が開いたとき: 砲身の左右から過剰熱をセルリアンの蒼白光として一気に逃がす */
    static void purge(ServerLevel level, LivingEntity user, int stage) {
        Vec3[] fr = frame(user);
        Vec3 base = user.getEyePosition().add(0, -0.35, 0);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 14; i++) {
                Vec3 p = base.add(fr[0].scale(0.6 + stage * 0.35 + level.random.nextDouble() * 0.4));
                Vec3 v = fr[1].scale(s * (0.25 + level.random.nextDouble() * 0.2)).add(0, 0.04 + level.random.nextDouble() * 0.05, 0);
                Fx.one(level, Fx.opt(ModParticles.GLOW.get(), i % 3 == 0 ? 0xFFFFFF : CERULEAN, 0.3F, 14), p, v);
            }
            Vec3 side = base.add(fr[0].scale(0.9 + stage * 0.35)).add(fr[1].scale(s * 0.4));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, side.x, side.y, side.z, 8, 0.1, 0.1, 0.1, 0.08);
        }
        level.playSound(null, user.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.6F + stage * 0.15F);
        level.playSound(null, user.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 0.5F + stage * 0.2F);
    }

    /** 開いたフィンから、細く蒼白光が漏れ続ける */
    static void vent(ServerLevel level, LivingEntity user, int stage, int t) {
        if (stage <= 0 || t % 3 != 0) return;
        Vec3[] fr = frame(user);
        Vec3 base = user.getEyePosition().add(0, -0.35, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 p = base.add(fr[0].scale(0.6 + level.random.nextInt(stage) * 0.35 + level.random.nextDouble() * 0.3)).add(fr[1].scale(s * 0.22));
            Fx.one(level, Fx.opt(ModParticles.GLOW.get(), CERULEAN, 0.14F, 10), p, fr[1].scale(s * 0.08).add(0, 0.03, 0));
        }
    }

    @Override
    protected void fire(ServerLevel level, LivingEntity user, ItemStack stack) {
        fire(level, user);
    }

    /** 余波（2026-10-04 ユーザー指定）: 射線のまわり半径 30 ブロックに、壁を通して 1 万の素のダメージ */
    public static final double WAVE_RADIUS = 30.0;
    public static final float WAVE_DAMAGE = 10_000F;
    /** 発射の閃光が画面に届く範囲（ブロック） */
    public static final double FLASH_RANGE = 64.0;

    /** 撃つ（兵姫に持たせたときも同じ処理）。線上の全員を貫き（壁もすべて貫く）、射線のまわりに余波を起こす。届いた先の位置を返す */
    public static Vec3 fire(ServerLevel level, LivingEntity user) {
        return fire(level, user, RANGE, WAVE_RADIUS * Overload.range(user));
    }

    /** 射程 range を指定して撃つ（余波なし。GameTest では隣のテストに届かないよう短くする） */
    public static Vec3 fire(ServerLevel level, LivingEntity user, double range) {
        return fire(level, user, range, 0);
    }

    /** 射程 range、余波の半径 waveRadius（0 なら余波なし）で撃つ */
    public static Vec3 fire(ServerLevel level, LivingEntity user, double range, double waveRadius) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle();
        Vec3 start = muzzle(user);
        // 16 ブロックずつ進め、壁はすべて貫く。読み込まれていない所でだけ止まる（区画を読み込ませない）
        Vec3 end = eye;
        Set<Integer> hit = new HashSet<>();
        double width = WIDTH * Overload.range(user); // オーバーロードの T4 はレールが太い
        for (double d = 0; d < range; d += SEGMENT) {
            Vec3 a = eye.add(look.scale(d)), b = eye.add(look.scale(Math.min(range, d + SEGMENT)));
            if (!level.isLoaded(BlockPos.containing(b))) { end = a; break; }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(width + 1),
                    e -> e != user && e.isAlive() && !e.isSpectator() && !hit.contains(e.getId())
                            && e.getBoundingBox().inflate(width * 0.5).clip(a, b).isPresent())) {
                hit.add(target.getId());
                target.invulnerableTime = 0;
                target.hurt(WeaponDamage.source(user, WeaponDamage.PUNISHMENT, user, user), DAMAGE);
                Overload.overkill(target, user);
                Vec3 at = new Vec3(target.getX(), target.getY(0.6), target.getZ());
                Fx.number(level, new Vec3(target.getX(), target.getY() + target.getBbHeight() + 0.6, target.getZ()), DAMAGE_VALUE, 0x9FE8FF);
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_CORE, 1.6F, 10), at, 1, 0, 0);
                Fx.burst(level, Fx.opt(ModParticles.STAR.get(), COLOR_GOLD, 0.5F, 14), at, 10, 0.5);
                Fx.beamRing(level, at, look, COLOR_CRIMSON, 3.0F, 12);
            }
            end = b;
        }
        Vec3 finalEnd = end;
        // 光のレールそのもの（クライアントで長く描く）
        level.addFreshEntity(new PunishmentBeamEntity(level, start, end, PunishmentBeamEntity.SCHEME_PUNISHMENT, (float) Overload.range(user), PunishmentBeamEntity.LIFETIME));
        muzzleBlast(level, user, start, look);
        // 射線に沿って、手前から先へ走る演出と余波（16 ブロックずつ、0.5 Tick ずつ遅らせる）
        double length = end.distanceTo(eye);
        Set<Integer> waved = new HashSet<>(hit);
        int segments = (int) Math.ceil(length / SEGMENT);
        for (int k = 0; k < segments; k++) {
            int seg = k;
            WeaponUtil.later(level, 1 + k / 2, () -> along(level, user, eye, look, seg, length, waveRadius, waved));
        }
        WeaponUtil.later(level, 1 + segments / 2, () -> impact(level, finalEnd, look));
        // 近くのプレイヤーの画面に閃光
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(user) <= FLASH_RANGE * FLASH_RANGE) {
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.automatavalkyries.network.TwilightPacket(14, com.automatavalkyries.network.TwilightPacket.FLASH));
            }
        }
        level.playSound(null, user.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
        level.playSound(null, user.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5F, 0.7F);
        level.playSound(null, user.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        user.push(-look.x * 0.9, 0.15, -look.z * 0.9); // 大きな反動
        user.hurtMarked = true;
        return end;
    }

    /** 銃口の大爆発: 白い閃光、銃口から前へ連なって広がる光線の輪、加速リングの星の陣、足元の衝撃波、飛び散る星 */
    static void muzzleBlast(ServerLevel level, LivingEntity user, Vec3 start, Vec3 look) {
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_CORE, 3.2F, 8), start, 1, 0, 0);
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_GOLD, 5.0F, 12), start, 1, 0, 0);
        for (int k = 0; k < 6; k++) { // 前へ連なる輪（円錐状に大きく）
            Fx.beamRing(level, start.add(look.scale(0.4 + k * 0.7)), look, k % 2 == 0 ? COLOR_GOLD : COLOR_CRIMSON, 1.2F + k * 0.55F, 10 + k * 2);
        }
        Fx.beamRing(level, start, look, COLOR_CORE, 6.0F, 16);
        for (int k = 0; k < 3; k++) { // 浮遊加速リングが弾ける
            Fx.mandala(level, start.add(look.scale(0.6 + k * 0.6)), k == 1 ? COLOR_CRIMSON : COLOR_GOLD, 1.0F + k * 0.5F, 12 + k * 3, true, k % 2 == 1);
        }
        Fx.burst(level, Fx.opt(ModParticles.STAR.get(), COLOR_CORE, 0.6F, 16), start, 24, 0.7);
        Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR_GOLD, 0.5F, 12), start, 30, 0.8);
        // 撃った反動の衝撃波（足元に二重の輪と星の陣）、左右のフィンから熱を一気に逃がす
        Fx.shockwave(level, user.position().add(0, 0.05, 0), COLOR_GOLD, 10.0F, 20);
        Fx.shockwave(level, user.position().add(0, 0.1, 0), COLOR_CRIMSON, 6.0F, 16);
        Fx.mandala(level, user.position().add(0, 0.08, 0), COLOR_GOLD, 5.0F, 24, false, false);
        purge(level, user, 3);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, start.x, start.y, start.z, 20, 0.4, 0.4, 0.4, 0.15);
    }

    /** 射線の k 番目の区間（16 ブロック）: 光線の輪・巻きつく光子プラズマの電光・余波の大きな輪と、余波のダメージ */
    static void along(ServerLevel level, LivingEntity user, Vec3 eye, Vec3 look, int k, double length, double waveRadius, Set<Integer> waved) {
        double from = k * SEGMENT, to = Math.min(length, from + SEGMENT);
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        // 光線の輪（4 ブロックごと、シアンと紫を交互に）
        for (double d = from + 2; d < to; d += 4) {
            Fx.beamRing(level, eye.add(look.scale(d)), look, ((int) (d / 4)) % 2 == 0 ? COLOR_GOLD : COLOR_CRIMSON, 1.6F, 12);
        }
        // 巻きつく光子プラズマの電光（2 重の螺旋。手前 128 ブロックまで）
        if (from < 128) {
            for (double d = from; d < to; d += 0.5) {
                double a = d * 1.4;
                for (int h = 0; h < 2; h++) {
                    double r = h == 0 ? 0.55 : 0.85, ang = h == 0 ? a : -a * 0.8;
                    Vec3 p = eye.add(look.scale(d)).add(side.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
                    Fx.one(level, Fx.opt(ModParticles.GLOW.get(), h == 0 ? COLOR_GOLD : COLOR_CRIMSON, 0.16F, 10 + (int) (d % 6)), p, Vec3.ZERO);
                }
            }
        }
        if (waveRadius <= 0) return;
        // 余波: 区間 2 つおきに大きな輪（半径 = 余波の半径）、区間の中の白い星
        Vec3 mid = eye.add(look.scale((from + to) / 2));
        if (k % 2 == 0) {
            Fx.beamRing(level, mid, look, COLOR_CORE, (float) waveRadius, 22);
            Fx.beamRing(level, mid, look, COLOR_CRIMSON, (float) waveRadius * 0.6F, 18);
        }
        Fx.star(level, mid, COLOR_CORE, 1.2F, 16, Vec3.ZERO);
        // 余波のダメージ: 射線（この区間）からの距離が半径以内の者すべて（壁を通す）。撃った者と仲間、もう当たった者は除く
        Vec3 a = eye.add(look.scale(from)), b = eye.add(look.scale(to));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(waveRadius + 2),
                e -> e != user && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(user) && !waved.contains(e.getId()))) {
            Vec3 c = new Vec3(e.getX(), e.getY(0.5), e.getZ());
            double t = Math.max(from, Math.min(to, c.subtract(eye).dot(look)));
            if (c.distanceTo(eye.add(look.scale(t))) > waveRadius + e.getBbWidth() * 0.5) continue;
            waved.add(e.getId());
            e.invulnerableTime = 0;
            e.hurt(WeaponDamage.source(user, WeaponDamage.ENERGY, user, user), WAVE_DAMAGE);
            Fx.number(level, c.add(0, e.getBbHeight() * 0.5 + 0.5, 0), (long) WAVE_DAMAGE, COLOR_CRIMSON);
            Fx.burst(level, Fx.opt(ModParticles.SPARK.get(), COLOR_CRIMSON, 0.3F, 10), c, 6, 0.3);
        }
        if (k % 3 == 0) level.playSound(null, BlockPos.containing(mid), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.6F);
    }

    /** 届いた先: 大きな光、光線の輪と星の陣、空間の破片 */
    static void impact(ServerLevel level, Vec3 end, Vec3 look) {
        Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), COLOR_CORE, 4.0F, 14), end, 1, 0, 0);
        for (int k = 0; k < 3; k++) Fx.beamRing(level, end, look, k == 1 ? COLOR_CRIMSON : COLOR_GOLD, 4.0F + k * 4F, 16 + k * 4);
        Fx.mandala(level, end, COLOR_GOLD, 5.0F, 30, true, false);
        Fx.mandala(level, end, COLOR_CRIMSON, 3.0F, 30, true, true);
        Fx.burst(level, Fx.opt(ModParticles.STAR.get(), COLOR_CORE, 0.8F, 20), end, 24, 0.9);
        Fx.burst(level, Fx.opt(ModParticles.VOID_SHARD.get(), COLOR_CRIMSON, 0.3F, 24), end, 30, 0.7);
        level.playSound(null, BlockPos.containing(end), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.6F);
    }
}
