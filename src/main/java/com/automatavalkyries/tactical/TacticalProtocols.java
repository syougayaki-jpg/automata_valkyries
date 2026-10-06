package com.automatavalkyries.tactical;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.config.ValkyrieConfig;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ai.ValkyrieCombat;
import com.automatavalkyries.item.ItemEnergy;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.LuminalCoreItem;
import com.automatavalkyries.weapon.ProjectileLike;
import com.automatavalkyries.weapon.WeaponDamage;
import com.automatavalkyries.weapon.WeaponUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 戦術級コードの要請と実行（追加拡張仕様書 §13。2026-10-07 実装）。
 * <ul>
 *   <li>承認: 要請したプレイヤーから 32 m 以内の自分の兵姫で、稼働していて、同調率と電力が足りる子のうちいちばん近い子（ユーザー指定「近くの兵姫から自動」）</li>
 *   <li>電力: 承認した兵姫の胸のルミナルコアの蓄えから引き、足りなければ兵姫自身の電力も（ユーザー指定「承認する兵姫のコア」）</li>
 *   <li>クールタイムはプレイヤーごと・コードごと</li>
 *   <li>地形の破壊は設定 {@code tactical.enableTacticalGriefing}（既定 false）のときだけ。無ければモブへのダメージだけ</li>
 *   <li>eprotect の保護対象への被弾上限はそのまま掛かる（拡張仕様書 E0.2-J）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class TacticalProtocols {

    private TacticalProtocols() {}

    public static final double APPROVER_RANGE = 32.0;
    /** グングニルの無敵を貫く光の柱のダメージ */
    public static final ResourceKey<DamageType> GUNGNIR_DAMAGE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "tactical_gungnir"));

    /** 時間のかかるコード（グングニル・アビス・アイリス） */
    private abstract static class Active {
        final ServerLevel level;
        final ValkyrieEntity source;
        final Vec3 center;
        int age;
        final int life;

        Active(ServerLevel level, ValkyrieEntity source, Vec3 center, int life) {
            this.level = level;
            this.source = source;
            this.center = center;
            this.life = life;
        }

        abstract void tick();

        void end() {}
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    // ------------------------------------------------------------------ 要請

    private static String cooldownKey(TacticalProtocol p) {
        return AutomataValkyries.MODID + ":tactical_" + p.id;
    }

    /** 残りのクールタイム（Tick） */
    public static long cooldownLeft(net.minecraft.world.entity.player.Player player, TacticalProtocol p) {
        return Math.max(0, player.getPersistentData().getLong(cooldownKey(p)) - player.level().getGameTime());
    }

    /** 兵姫が出せる電力（コアの蓄え + 自分の電力の休眠の手前まで） */
    static long available(ValkyrieEntity v) {
        long sum = Math.max(0, v.getEnergy() - ValkyrieEntity.WAKE_ENERGY);
        ItemStack core = v.getCore();
        if (core.getItem() instanceof LuminalCoreItem c) sum += c.grade().infinite() ? Long.MAX_VALUE / 4 : ItemEnergy.get(core);
        return sum;
    }

    static void pay(ValkyrieEntity v, int fe) {
        int rest = fe;
        ItemStack core = v.getCore();
        if (core.getItem() instanceof LuminalCoreItem c) {
            if (c.grade().infinite()) return;
            int take = Math.min(rest, ItemEnergy.get(core));
            ItemEnergy.set(core, ItemEnergy.get(core) - take);
            rest -= take;
        }
        if (rest > 0) v.setEnergy(v.getEnergy() - rest);
    }

    /** 要請する（戦術ポインターから）。結果を持ち主に知らせる。発動したら true */
    public static boolean request(ServerPlayer player, TacticalProtocol p, Vec3 target) {
        Outcome o = tryRequest(player, p, target);
        Component msg = switch (o.result) {
            case "approved" -> Component.translatable("message.automata_valkyries.tactical.approved", o.approver.getDisplayName(),
                    Component.translatable("tactical.automata_valkyries." + p.id)).withStyle(ChatFormatting.GOLD);
            case "cooldown" -> Component.translatable("message.automata_valkyries.tactical.cooldown", Component.translatable("tactical.automata_valkyries." + p.id),
                    (o.cooldownLeft + 19) / 20).withStyle(ChatFormatting.RED);
            default -> Component.translatable("message.automata_valkyries.tactical." + o.result, p.sync, String.format("%,d", p.fe),
                    String.format("%,d", Math.min(o.bestPower, 2_000_000_000L))).withStyle(ChatFormatting.RED);
        };
        player.displayClientMessage(msg, true);
        return o.approver != null;
    }

    /** 要請の結果（result: approved / cooldown / no_valkyrie / low_sync / low_power） */
    public record Outcome(String result, @org.jetbrains.annotations.Nullable ValkyrieEntity approver, long cooldownLeft, long bestPower) {
        Outcome(String result, @org.jetbrains.annotations.Nullable ValkyrieEntity approver, long cooldownLeft) {
            this(result, approver, cooldownLeft, 0);
        }
    }

    /** 要請を判定して、承認されたら払って発動する（知らせは出さない。GameTest からも使う） */
    public static Outcome tryRequest(net.minecraft.world.entity.player.Player player, TacticalProtocol p, Vec3 target) {
        if (!(player.level() instanceof ServerLevel level)) return new Outcome("no_valkyrie", null, 0);
        long left = cooldownLeft(player, p);
        if (left > 0) return new Outcome("cooldown", null, left);
        ValkyrieEntity best = null;
        boolean anySync = false, anyNear = false;
        long bestPower = 0; // 同調率の足りる子のうち、いちばん電力のある子の電力（足りないときに知らせる）
        for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, player.getBoundingBox().inflate(APPROVER_RANGE),
                v -> player.getUUID().equals(v.getOwnerUUID()) && !v.isShutdown() && !v.isAsleep() && v.distanceToSqr(player) <= APPROVER_RANGE * APPROVER_RANGE)) {
            anyNear = true;
            if (v.getSyncRate() < p.sync) continue;
            anySync = true;
            bestPower = Math.max(bestPower, available(v));
            if (available(v) < p.fe) continue;
            if (best == null || v.distanceToSqr(player) < best.distanceToSqr(player)) best = v;
        }
        if (best == null) return new Outcome(!anyNear ? "no_valkyrie" : !anySync ? "low_sync" : "low_power", null, 0, bestPower);
        pay(best, p.fe);
        player.getPersistentData().putLong(cooldownKey(p), level.getGameTime() + p.cooldown);
        best.say("tactical_" + p.id); // 承認の声
        best.emote(ValkyrieEntity.Emote.ALERT);
        best.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
        level.playSound(null, best.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.7F);
        switch (p) {
            case CARPET -> carpet(level, best, target);
            case GUNGNIR -> ACTIVE.add(new Gungnir(level, best, target));
            case ABYSS -> ACTIVE.add(new Abyss(level, best, target.add(0, 1.5, 0)));
            case IRIS -> ACTIVE.add(new Iris(level, best, target));
        }
        return new Outcome("approved", best, 0);
    }

    static boolean griefing() {
        return ValkyrieConfig.tacticalGriefing();
    }

    /** 敵か（承認した兵姫から見て味方でない生き物） */
    static boolean foe(ValkyrieEntity v, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && !ValkyrieCombat.isAlly(v, l) && l != v;
    }

    // ------------------------------------------------------------------ CARPET（絨毯飽和爆撃）

    private static void carpet(ServerLevel level, ValkyrieEntity v, Vec3 center) {
        RandomSource r = level.random;
        for (int i = 0; i < 64; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 32.0;
            int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
            int delay = 20 + i * 100 / 64;
            WeaponUtil.later(level, delay, () -> {
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
                Vec3 hit = Vec3.atBottomCenterOf(top);
                Fx.line(level, Fx.opt(ModParticles.GLOW.get(), 0xFF8A3C, 0.3F, 6), hit.add(0, 30, 0), hit, 1.5);
                level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y + 0.5, hit.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FLAME, hit.x, hit.y + 0.5, hit.z, 20, 1.2, 0.5, 1.2, 0.04);
                level.playSound(null, top, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(3.5), e -> foe(v, e))) {
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(v, WeaponDamage.ENERGY, v, v), 24.0F);
                    e.setSecondsOnFire(4);
                }
                if (griefing()) level.explode(v, hit.x, hit.y, hit.z, 2.5F, true, Level.ExplosionInteraction.TNT);
            });
        }
    }

    // ------------------------------------------------------------------ GUNGNIR（軌道衛星集束レーザー）

    private static final class Gungnir extends Active {
        Gungnir(ServerLevel level, ValkyrieEntity source, Vec3 center) {
            super(level, source, center, 100);
        }

        @Override
        void tick() {
            // 光の柱（中心が白、外がネオンブルー）
            for (int i = 0; i < 6; i++) {
                double y = center.y + level.random.nextDouble() * 60;
                level.sendParticles(ParticleTypes.END_ROD, center.x, y, center.z, 1, 0.3, 0, 0.3, 0);
                double a = level.random.nextDouble() * Math.PI * 2;
                Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x5AD8FF, 0.6F, 8),
                        new Vec3(center.x + Math.cos(a) * 3, y, center.z + Math.sin(a) * 3), 1, 0, 0, 0, 0);
            }
            if (age % 10 == 0) {
                level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0F, 1.6F);
                var source = new net.minecraft.world.damagesource.DamageSource(
                        level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(GUNGNIR_DAMAGE), this.source, this.source);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center.x - 3, center.y - 3, center.z - 3, center.x + 3, center.y + 60, center.z + 3),
                        e -> foe(this.source, e) && Math.hypot(e.getX() - center.x, e.getZ() - center.z) <= 3.0)) {
                    e.invulnerableTime = 0;
                    e.hurt(source, 75.0F); // 毎秒 150
                }
                if (griefing()) { // 主隔壁を蒸発（岩盤など壊せない物は残す）
                    BlockPos c = BlockPos.containing(center);
                    for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 12, 3))) {
                        if (Math.hypot(p.getX() + 0.5 - center.x, p.getZ() + 0.5 - center.z) > 3.0) continue;
                        var st = level.getBlockState(p);
                        if (!st.isAir() && st.getDestroySpeed(level, p) >= 0) level.destroyBlock(p, false, source.getEntity());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ABYSS（特異点重力崩壊球）

    private static final class Abyss extends Active {
        Abyss(ServerLevel level, ValkyrieEntity source, Vec3 center) {
            super(level, source, center, 160);
        }

        @Override
        void tick() {
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 6, 0.5, 0.5, 0.5, 0.01);
            for (int i = 0; i < 4; i++) {
                double a = (age * 0.3 + i * Math.PI / 2), r = 3 + (age % 20) * 0.4;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + Math.cos(a) * r, center.y + Math.sin(a * 0.7), center.z + Math.sin(a) * r, 1, 0, 0, 0, 0.05);
            }
            if (age % 20 == 0) level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F);
            AABB area = new AABB(center, center).inflate(24);
            for (Entity e : level.getEntities(source, area, e -> e.distanceToSqr(center) <= 24 * 24)) {
                if (ProjectileLike.is(e) && !ValkyrieCombat.isAlly(source, e)) { // 弾幕を飲み込む
                    e.discard();
                    continue;
                }
                if (!foe(source, e)) continue;
                Vec3 pull = center.subtract(e.position());
                double d = pull.length();
                if (d < 1.0) {
                    e.setDeltaMovement(Vec3.ZERO);
                } else {
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.scale(Math.min(0.6, 0.12 + 2.0 / d) / d)));
                }
                e.hurtMarked = true;
                e.fallDistance = 0;
            }
        }

        @Override
        void end() {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.6F);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(24), e -> foe(source, e) && e.distanceToSqr(center) <= 24 * 24)) {
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), e.getMaxHealth() * 0.3F); // 最大 HP の 30%（保護対象は上限が掛かる）
            }
        }
    }

    // ------------------------------------------------------------------ IRIS（広域ナノ防壁・野戦病院）

    private static final class Iris extends Active {
        Iris(ServerLevel level, ValkyrieEntity source, Vec3 center) {
            super(level, source, center, 600);
        }

        @Override
        void tick() {
            double r = 16.0;
            // 敵の弾を消す
            for (Entity e : level.getEntities(source, new AABB(center, center).inflate(r), e -> ProjectileLike.is(e) && e.distanceToSqr(center) <= r * r)) {
                if (!ValkyrieCombat.isAlly(source, e)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY(), e.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
                    e.discard();
                }
            }
            if (age % 10 == 0) { // ドーム
                for (int i = 0; i < 40; i++) {
                    double u = level.random.nextDouble() * Math.PI * 2, w = level.random.nextDouble() * Math.PI / 2;
                    Vec3 p = center.add(Math.cos(u) * Math.cos(w) * r, Math.sin(w) * r, Math.sin(u) * Math.cos(w) * r);
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7CFFD8, 0.5F, 20), p, 1, 0, 0, 0, 0);
                }
            }
            if (age % 20 == 0) { // 中の味方の HP を毎秒 10%、兵姫の充電 3 倍
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r),
                        e -> e.isAlive() && ValkyrieCombat.isAlly(source, e) && e.distanceToSqr(center) <= r * r)) {
                    if (e instanceof ValkyrieEntity v) {
                        v.repair(v.getMaxHealth() * 0.1F);
                        v.setEnergy(v.getEnergy() + 10_000); // 充電の上乗せ（毎秒）
                    } else {
                        e.heal(e.getMaxHealth() * 0.1F);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (!a.source.isAlive() && a.age == 0) {
                it.remove();
                continue;
            }
            a.tick();
            if (++a.age >= a.life) {
                a.end();
                it.remove();
            }
        }
    }
}
