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
        /** 呼んだ者（承認した兵姫。クリエイティブの端末ならプレイヤー） */
        final LivingEntity source;
        final Vec3 center;
        int age;
        final int life;

        Active(ServerLevel level, LivingEntity source, Vec3 center, int life) {
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
        launch(level, best, p, target);
        return new Outcome("approved", best, 0);
    }

    /** コードを発動する（承認・支払い・クールタイムは済んだ後。クリエイティブの端末はこれを直接呼ぶ） */
    public static void launch(ServerLevel level, LivingEntity source, TacticalProtocol p, Vec3 target) {
        switch (p) {
            case CARPET -> ACTIVE.add(new Carpet(level, source, target));
            case GUNGNIR -> ACTIVE.add(new Gungnir(level, source, target));
            case ABYSS -> ACTIVE.add(new Abyss(level, source, target.add(0, 1.5, 0)));
            case IRIS -> ACTIVE.add(new Iris(level, source, target));
            case COMET -> ACTIVE.add(new Comet(level, source, target));
        }
    }

    /**
     * クリエイティブ用の端末（{@link com.automatavalkyries.item.TacOverrideItem}）から: 兵姫・同調率・電力・クールタイムなしで、すぐ発動する
     * （2026-10-29 ユーザー指定「クリエイティブ用のクールタイムのない戦術コード」）
     */
    public static void creativeLaunch(ServerPlayer player, TacticalProtocol p, Vec3 target) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.7F);
        launch(level, player, p, target);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.tactical.override", Component.translatable("tactical.automata_valkyries." + p.id))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /**
     * 着弾の派手な演出（2026-10-29 ユーザー指定「もっと派手に」）: 広がる衝撃波の輪を何重にも、まわりに演出だけの雷、閃光と画面の揺れ（{@link com.automatavalkyries.network.TacticalShakePacket}）。
     * power は 0〜1（揺れの強さ）、radius は揺れ・雷の届く広さ
     */
    static void impact(ServerLevel level, Vec3 at, int color, double radius, float power, int bolts) {
        for (int i = 0; i < 4; i++) {
            final float r = (float) (radius * (0.25 + i * 0.25));
            WeaponUtil.later(level, i * 3, () -> Fx.shockwave(level, at.add(0, 0.2, 0), color, r, 24));
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 4, radius * 0.08, 1, radius * 0.08, 0);
        for (int i = 0; i < bolts; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = radius * (0.2 + level.random.nextDouble() * 0.7);
            int x = Mth.floor(at.x + Math.cos(a) * d), z = Mth.floor(at.z + Math.sin(a) * d);
            WeaponUtil.later(level, level.random.nextInt(20), () -> {
                var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                if (bolt == null) return;
                bolt.moveTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            });
        }
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(at));
            if (d > radius * 3) continue;
            com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new com.automatavalkyries.network.TacticalShakePacket((float) (power * (1.0 - d / (radius * 3)))));
        }
    }

    static boolean griefing() {
        return ValkyrieConfig.tacticalGriefing();
    }

    // ------------------------------------------------------------------ 地形の破壊（2026-10-29 ユーザー指定「地形破壊も演出に合わせて強化して」）

    /** 1 Tick に調べるブロックの上限（大きなクレーターは数 Tick に分けて掘る。サーバーが止まらないように） */
    static final int CARVE_BUDGET = 40_000;

    /** 楕円の穴を掘る仕事（中心・半径 x/y/z。burn なら縁の地表に火を付ける） */
    private static final class Carve {
        final ServerLevel level;
        final Vec3 c;
        final double rx, ry, rz;
        final boolean burn;
        /** 円柱（縦は bottom から c.y + ry まで全部）か */
        final boolean column;
        final int bottom;
        final java.util.Iterator<BlockPos> it;

        Carve(ServerLevel level, Vec3 c, double rx, double ry, double rz, boolean burn) {
            this(level, c, rx, ry, rz, burn, false, 0);
        }

        Carve(ServerLevel level, Vec3 c, double rx, double ry, double rz, boolean burn, boolean column, int bottom) {
            this.level = level;
            this.c = c;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.burn = burn;
            this.column = column;
            this.bottom = bottom;
            BlockPos lo = column ? BlockPos.containing(c.x - rx, bottom, c.z - rz) : BlockPos.containing(c.x - rx, c.y - ry, c.z - rz);
            BlockPos hi = BlockPos.containing(c.x + rx, c.y + ry, c.z + rz);
            this.it = BlockPos.betweenClosed(lo, hi).iterator();
        }
    }

    private static final java.util.ArrayDeque<Carve> CARVES = new java.util.ArrayDeque<>();

    /** 地形を壊す設定のときだけ、楕円の穴を掘る（壊せない物・ブロックエンティティは残す。落とし物は出さない） */
    static void carve(ServerLevel level, Vec3 c, double rx, double ry, double rz, boolean burn) {
        if (griefing()) CARVES.add(new Carve(level, c, rx, ry, rz, burn));
    }

    /** 地形を壊す設定のときだけ、地表から岩盤の層まで円柱の穴を開ける（岩盤は壊せないので残る） */
    static void carveToBedrock(ServerLevel level, Vec3 c, double r, double above, boolean burn) {
        if (griefing()) CARVES.add(new Carve(level, c, r, above, r, burn, true, level.getMinBuildHeight()));
    }

    private static void runCarves() {
        int budget = CARVE_BUDGET;
        while (budget > 0 && !CARVES.isEmpty()) {
            Carve k = CARVES.peek();
            while (budget-- > 0 && k.it.hasNext()) {
                BlockPos p = k.it.next();
                double dx = (p.getX() + 0.5 - k.c.x) / k.rx, dy = (p.getY() + 0.5 - k.c.y) / k.ry, dz = (p.getZ() + 0.5 - k.c.z) / k.rz;
                double d = k.column ? dx * dx + dz * dz : dx * dx + dy * dy + dz * dz;
                if (d > 1.0) {
                    // 縁の地表を焦がして火を付ける（穴の外側のすぐ近く）
                    if (k.burn && d < 1.25 && k.level.random.nextInt(6) == 0 && k.level.isEmptyBlock(p) && net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState().canSurvive(k.level, p)) {
                        k.level.setBlock(p, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 2 | 16);
                    }
                    continue;
                }
                var st = k.level.getBlockState(p);
                if (st.isAir() || st.getDestroySpeed(k.level, p) < 0 || st.hasBlockEntity()) continue;
                k.level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            if (!k.it.hasNext()) CARVES.poll();
        }
    }

    /** 味方か（兵姫が呼んだなら兵姫の味方、プレイヤーが呼んだならそのプレイヤー・ほかのプレイヤー・その兵姫と飼っている動物） */
    static boolean ally(LivingEntity source, Entity e) {
        if (source instanceof ValkyrieEntity v) return ValkyrieCombat.isAlly(v, e);
        if (e == source || e instanceof net.minecraft.world.entity.player.Player) return true;
        if (e instanceof ValkyrieEntity v) return source.getUUID().equals(v.getOwnerUUID());
        return e instanceof net.minecraft.world.entity.OwnableEntity o && source.getUUID().equals(o.getOwnerUUID());
    }

    /** 敵か（呼んだ者から見て味方でない生き物）。ボスは設定 tactical.tacticalAffectsBosses が true のときだけ（既定は効かない） */
    static boolean foe(LivingEntity source, Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && !ally(source, l) && l != source && (ValkyrieConfig.tacticalAffectsBosses() || !isBoss(l));
    }

    /** ボスか（forge:bosses のタグ、またはこのMOD のボス＝退くボス・保護されたボス） */
    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(net.minecraftforge.common.Tags.EntityTypes.BOSSES) || e instanceof com.automatavalkyries.entity.RetreatableBoss
                || (e instanceof com.automatavalkyries.protect.IEprotected && !(e instanceof ValkyrieEntity) && !(e instanceof com.automatavalkyries.entity.ProtectDummyZombieEntity)); // 訓練用ダミーゾンビはボス扱いしない（試し撃ち用）
    }

    // ------------------------------------------------------------------ CARPET（絨毯飽和爆撃）

    /** 演出をまわりのプレイヤー（320 ブロック以内）へ送る */
    static void sendFx(ServerLevel level, Vec3 center, com.automatavalkyries.network.TacticalFxPacket packet) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center) < 320 * 320) {
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    /**
     * 4 機の無人爆撃機が異空間の裂け目から現れて上空を横切り、焼夷弾を落とす（落ちる所は {@link TacticalPatterns#carpetBombs}）。
     * 1 発 24・半径 3.5・燃やす。落ちた所に炎が 10 秒残り、中の敵を 0.5 秒ごとに 4 で焼く（演出は client/TacticalFx）
     */
    private static final class Carpet extends Active {
        final long seed;
        final java.util.List<TacticalPatterns.Bomb> bombs;

        Carpet(ServerLevel level, LivingEntity source, Vec3 center) {
            super(level, source, center, TacticalPatterns.carpetDuration());
            seed = level.random.nextLong();
            bombs = TacticalPatterns.carpetBombs(seed);
            sendFx(level, center, new com.automatavalkyries.network.TacticalFxPacket(com.automatavalkyries.network.TacticalFxPacket.CARPET,
                    center.x, center.y, center.z, (float) TacticalPatterns.CARPET_R, life, seed));
            level.playSound(null, BlockPos.containing(center), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3.0F, 0.6F);
        }

        Vec3 ground(TacticalPatterns.Bomb b) {
            int x = Mth.floor(center.x + b.x()), z = Mth.floor(center.z + b.z());
            return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
        }

        @Override
        void tick() {
            if (age == TacticalPatterns.CARPET_PORTAL) level.playSound(null, BlockPos.containing(center), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0F, 0.5F);
            if (age == TacticalPatterns.CARPET_PORTAL + TacticalPatterns.CARPET_PASS / 2) impact(level, center, 0xFF7A2A, TacticalPatterns.CARPET_R, 0.6F, 0); // 爆撃のさなかの大揺れ（雷は出さない。2026-10-29 ユーザー指定）
            for (TacticalPatterns.Bomb b : bombs) {
                if (b.landTick() == age) {
                    Vec3 hit = ground(b);
                    level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y + 0.5, hit.z, 1, 0, 0, 0, 0);
                    if (age % 4 == 0) level.playSound(null, BlockPos.containing(hit), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.8F + level.random.nextFloat() * 0.3F);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(TacticalPatterns.CARPET_BLAST_R), e -> foe(source, e))) {
                        e.invulnerableTime = 0;
                        e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), 300.0F);
                        e.setSecondsOnFire(6);
                    }
                    carve(level, hit, 4.5, 3.0, 4.5, true); // 着弾ごとのクレーターと燃える縁（2026-10-29 強化）
                    if (level.random.nextInt(6) == 0) Fx.shockwave(level, hit.add(0, 0.2, 0), 0xFF7A2A, (float) TacticalPatterns.CARPET_BLAST_R * 1.6F, 14);
                }
            }
            if (age % 10 == 0) { // 残る炎
                for (TacticalPatterns.Bomb b : bombs) {
                    if (age < b.landTick() || age >= b.landTick() + TacticalPatterns.CARPET_FIRE) continue;
                    Vec3 hit = ground(b);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(TacticalPatterns.CARPET_FIRE_R, 3, TacticalPatterns.CARPET_FIRE_R), e -> foe(source, e))) {
                        e.invulnerableTime = 0;
                        e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), 40.0F);
                        e.setSecondsOnFire(4);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ GUNGNIR（軌道衛星集束レーザー）

    /**
     * 赤黒い螺旋の光が空から落ちてきて（{@link #GUNGNIR_DESCENT} Tick）、半径 {@link #GUNGNIR_RADIUS} を 5 秒焼く
     * （2026-10-07 ユーザー指定「戦術級らしく半径 50 ブロックの赤黒い螺旋状の光で落ちてくるど派手な演出を自作して、範囲も広げて威力も倍に」。
     * 描画は client/TacticalFx）。地形を壊す設定でも、壊すのは中心の柱（半径 5）だけ
     */
    private static final class Gungnir extends Active {
        Gungnir(ServerLevel level, LivingEntity source, Vec3 center) {
            super(level, source, center, GUNGNIR_DESCENT + GUNGNIR_STRIKE);
            var packet = new com.automatavalkyries.network.TacticalFxPacket(com.automatavalkyries.network.TacticalFxPacket.GUNGNIR,
                    center.x, center.y, center.z, (float) GUNGNIR_RADIUS, GUNGNIR_DESCENT + GUNGNIR_STRIKE);
            // 世界が赤く染まる（320 ブロック以内）
            var crimson = new com.automatavalkyries.network.TwilightPacket(GUNGNIR_DESCENT + GUNGNIR_STRIKE + 20, com.automatavalkyries.network.TwilightPacket.CRIMSON);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center) >= 320 * 320) continue;
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), packet);
                com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), crimson);
            }
            level.playSound(null, BlockPos.containing(center), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 4.0F, 0.5F);
        }

        @Override
        void tick() {
            if (age < GUNGNIR_DESCENT) return; // 落ちてくる間
            if (age == GUNGNIR_DESCENT) { // 着弾
                level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0F, 0.6F);
                level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F, 0.5F);
                impact(level, center, 0xFF2040, GUNGNIR_RADIUS, 1.0F, 16);
                TacticalProtocols.carveToBedrock(level, center, 10, 12, true); // 光の柱が地下の岩盤の層まで貫く縦穴（2026-10-29 ユーザー指定「岩盤の層まで貫通」）
            }
            if (age > GUNGNIR_DESCENT && (age - GUNGNIR_DESCENT) % 40 == 0) impact(level, center, 0xFF2040, GUNGNIR_RADIUS * 0.6, 0.45F, 4); // 照射の間も脈打つ
            int st = age - GUNGNIR_DESCENT;
            if (griefing() && st % 2 == 0) carve(st); // 螺旋の腕が大地をえぐり取る
            if (st % 15 == 0) level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.5F + level.random.nextFloat() * 0.3F);
            if (age % 10 != 0) return;
            level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 4.0F, 0.8F);
            var source = new net.minecraft.world.damagesource.DamageSource(
                    level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(GUNGNIR_DAMAGE), this.source, this.source);
            double r = GUNGNIR_RADIUS;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center.x - r, center.y - 6, center.z - r, center.x + r, center.y + 80, center.z + r),
                    e -> foe(this.source, e) && Math.hypot(e.getX() - center.x, e.getZ() - center.z) <= r)) {
                e.invulnerableTime = 0;
                e.hurt(source, 1500.0F); // 毎秒 3,000（2026-10-29 に 10 倍）
            }
        }

        /**
         * 螺旋の腕の先（{@link com.automatavalkyries.network.TacticalFxPacket#gungnirArm}）の地面を、半径 6・深さ 14 で削り取る（2026-10-29 に半径 3・深さ 6 から強化）
         * （2026-10-07 ユーザー指定「螺旋が大地をえぐり取るように、やりすぎなくらいに」）。地形を壊す設定のときだけ。
         * 岩盤など壊せない物と、ブロックエンティティ（チェスト・マシン）は残す。落とし物・音は出さない（重くならないよう）
         */
        private void carve(int strikeTicks) {
            for (int arm = 0; arm < com.automatavalkyries.network.TacticalFxPacket.GUNGNIR_ARMS; arm++) {
                double[] g = com.automatavalkyries.network.TacticalFxPacket.gungnirArm(arm, strikeTicks, GUNGNIR_RADIUS);
                int cx = Mth.floor(center.x + g[0]), cz = Mth.floor(center.z + g[1]);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
                for (int dx = -6; dx <= 6; dx++) { // 2026-10-29 に半径 3 → 6・深さ 6 → 14
                    for (int dz = -6; dz <= 6; dz++) {
                        if (dx * dx + dz * dz > 36) continue;
                        int depth = 14 - (dx * dx + dz * dz) / 3; // 中ほど深く
                        for (int y = top + 1; y >= top - depth; y--) {
                            BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                            var st = level.getBlockState(p);
                            if (st.isAir() || st.getDestroySpeed(level, p) < 0 || st.hasBlockEntity()) continue;
                            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                        }
                    }
                }
            }
        }
    }

    /** GUNGNIR の範囲（半径）と、光が落ちてくる間（Tick） */
    public static final double GUNGNIR_RADIUS = 100.0;
    public static final int GUNGNIR_DESCENT = com.automatavalkyries.network.TacticalFxPacket.GUNGNIR_DESCENT,
            GUNGNIR_STRIKE = com.automatavalkyries.network.TacticalFxPacket.GUNGNIR_STRIKE;

    // ------------------------------------------------------------------ ABYSS（特異点重力崩壊球）

    /** ブロックを吸い込む範囲（半径）と、1 Tick に剥がす数 */
    static final double ABYSS_SUCK_R = 40.0;
    static final int ABYSS_SUCK_PER_TICK = 6;

    private static final class Abyss extends Active {
        /** 吸い込んでいるブロック（地形を壊す設定のとき。2026-10-29 ユーザー指定「アビスはブロックを吸い込むように」） */
        final java.util.List<net.minecraft.world.entity.item.FallingBlockEntity> sucked = new ArrayList<>();

        Abyss(ServerLevel level, LivingEntity source, Vec3 center) {
            super(level, source, center, TacticalPatterns.ABYSS_TIME);
            sendFx(level, center, new com.automatavalkyries.network.TacticalFxPacket(com.automatavalkyries.network.TacticalFxPacket.ABYSS,
                    center.x, center.y, center.z, (float) TacticalPatterns.ABYSS_END_R, life, level.random.nextLong()));
        }

        @Override
        void tick() {
            level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 6, 0.5, 0.5, 0.5, 0.01);
            for (int i = 0; i < 4; i++) {
                double a = (age * 0.3 + i * Math.PI / 2), r = 3 + (age % 20) * 0.4;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + Math.cos(a) * r, center.y + Math.sin(a * 0.7), center.z + Math.sin(a) * r, 1, 0, 0, 0, 0.05);
            }
            if (age % 20 == 0) level.playSound(null, BlockPos.containing(center), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.5F);
            double r = TacticalPatterns.abyssRadius(age); // 吸い込む範囲が広がっていく
            if (griefing()) suckBlocks(Math.min(r, ABYSS_SUCK_R));
            AABB area = new AABB(center, center).inflate(r);
            for (Entity e : level.getEntities(source, area, e -> e.distanceToSqr(center) <= r * r)) {
                if (ProjectileLike.is(e) && !ally(source, e)) { // 弾幕を飲み込む
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

        /**
         * 地表のブロックを剥がして中心へ吸い込む: 毎 Tick {@value #ABYSS_SUCK_PER_TICK} 個、範囲の中のランダムな所の一番上のブロックを、
         * 落ちないブロックの塊（落下するブロックの重力なし）にして中心へ引き寄せる。中心に届いたら砕けて消える（置かれない・落とし物も出さない）
         */
        void suckBlocks(double r) {
            for (int i = 0; i < ABYSS_SUCK_PER_TICK; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = 3 + Math.sqrt(level.random.nextDouble()) * (r - 3);
                int x = Mth.floor(center.x + Math.cos(a) * d), z = Mth.floor(center.z + Math.sin(a) * d);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos p = new BlockPos(x, y, z);
                var st = level.getBlockState(p);
                if (st.isAir() || st.getDestroySpeed(level, p) < 0 || st.hasBlockEntity() || !st.getFluidState().isEmpty()) continue;
                var fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, p, st);
                fb.setNoGravity(true);
                fb.noPhysics = true; // 地形をすり抜ける（途中で地面に触れて置かれないように）
                fb.disableDrop();
                fb.setDeltaMovement(0, 0.4, 0);
                sucked.add(fb);
            }
            for (var it = sucked.iterator(); it.hasNext(); ) {
                var fb = it.next();
                if (!fb.isAlive()) {
                    it.remove();
                    continue;
                }
                Vec3 pull = center.subtract(fb.position());
                double dist = pull.length();
                if (dist < 2.0) { // 中心で砕けて消える
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, fb.getBlockState()), fb.getX(), fb.getY(), fb.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    fb.discard();
                    it.remove();
                    continue;
                }
                fb.time = 1; // 時間切れで落とし物にしない
                Vec3 tangent = new Vec3(-pull.z, 0, pull.x).normalize().scale(0.25); // 渦を巻いて吸い込まれる
                fb.setDeltaMovement(fb.getDeltaMovement().scale(0.7).add(pull.scale(Math.min(1.6, 0.25 + 6.0 / dist) / dist)).add(tangent));
                fb.hurtMarked = true;
            }
        }

        @Override
        void end() {
            for (var fb : sucked) if (fb.isAlive()) fb.discard(); // 残りは爆縮で消える
            sucked.clear();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.6F);
            level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F, 0.4F);
            impact(level, center, 0xB060FF, TacticalPatterns.ABYSS_END_R, 1.0F, 12);
            carve(level, center, 16, 16, 16, false); // 爆縮で空間ごとえぐれる球の穴（2026-10-29）
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center) < 200 * 200) com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        new com.automatavalkyries.network.TwilightPacket(16, com.automatavalkyries.network.TwilightPacket.FLASH));
            }
            double r = TacticalPatterns.ABYSS_END_R;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r), e -> foe(source, e) && e.distanceToSqr(center) <= r * r)) {
                // 中心（半径 4）に引き込まれた相手には 500 を足す
                if (e.distanceToSqr(center) <= TacticalPatterns.ABYSS_CORE_R * TacticalPatterns.ABYSS_CORE_R) {
                    e.invulnerableTime = 0;
                    e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), TacticalPatterns.ABYSS_CORE_DAMAGE);
                }
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), e.getMaxHealth() * TacticalPatterns.ABYSS_RATIO); // 最大 HP の 50%（保護対象は上限が掛かる）
            }
        }
    }

    // ------------------------------------------------------------------ COMET（仮想彗星）

    /**
     * 青白い光の彗星が残光を引いて斜め上から落ちてくる（{@link TacticalPatterns#cometPos}、3 秒）。着弾で半径 30 m の敵に、中心 800 から縁 200 へ
     * 減っていくダメージと吹き飛ばし。地形を壊す設定のときは半径 9 のクレーター。演出は client/TacticalFx・TacticalSprites
     */
    private static final class Comet extends Active {
        final long seed;

        Comet(ServerLevel level, LivingEntity source, Vec3 center) {
            super(level, source, center, TacticalPatterns.COMET_FALL + TacticalPatterns.COMET_AFTER);
            seed = level.random.nextLong();
            sendFx(level, center, new com.automatavalkyries.network.TacticalFxPacket(com.automatavalkyries.network.TacticalFxPacket.COMET,
                    center.x, center.y, center.z, (float) TacticalPatterns.COMET_R, life, seed));
            level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 4.0F, 0.5F);
            for (ServerPlayer p : level.players()) { // 落ちてくる間、空を星空に暗くする（尾の色が映えるように。2026-10-29）
                if (p.distanceToSqr(center) < 320 * 320) com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        new com.automatavalkyries.network.TwilightPacket(TacticalPatterns.COMET_FALL + 30, com.automatavalkyries.network.TwilightPacket.STARRY));
            }
        }

        @Override
        void tick() {
            if (age == TacticalPatterns.COMET_FALL / 2) level.playSound(null, BlockPos.containing(center), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 4.0F, 0.6F);
            if (age != TacticalPatterns.COMET_FALL) return;
            // 着弾
            level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0F, 0.4F);
            level.playSound(null, BlockPos.containing(center), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 8.0F, 0.6F);
            level.playSound(null, BlockPos.containing(center), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 6.0F, 0.5F);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1, center.z, 3, 2, 1, 2, 0);
            impact(level, center, 0x9FE8FF, TacticalPatterns.COMET_R, 1.0F, 24);
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center) < 200 * 200) com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        new com.automatavalkyries.network.TwilightPacket(20, com.automatavalkyries.network.TwilightPacket.FLASH));
            }
            double r = TacticalPatterns.COMET_R;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r), e -> foe(source, e) && e.distanceToSqr(center) <= r * r)) {
                double k = Math.sqrt(e.distanceToSqr(center)) / r;
                e.invulnerableTime = 0;
                e.hurt(WeaponDamage.source(source, WeaponDamage.ENERGY, source, source), (float) (TacticalPatterns.COMET_CORE + (TacticalPatterns.COMET_EDGE - TacticalPatterns.COMET_CORE) * k));
                Vec3 push = e.position().subtract(center);
                push = push.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : push.normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(push.scale(3.5 * (1 - k))).add(0, 1.6 * (1 - k) + 0.4, 0));
                e.hurtMarked = true;
            }
            carve(level, center, 34, 20, 34, true); // 大クレーターと燃える縁（2026-10-29 に半径 18 → 34。数 Tick に分けて掘る）
        }
    }

    // ------------------------------------------------------------------ IRIS（広域ナノ防壁・野戦病院）

    private static final class Iris extends Active {
        Iris(ServerLevel level, LivingEntity source, Vec3 center) {
            super(level, source, center, TacticalPatterns.IRIS_TIME);
            sendFx(level, center, new com.automatavalkyries.network.TacticalFxPacket(com.automatavalkyries.network.TacticalFxPacket.IRIS,
                    center.x, center.y, center.z, (float) TacticalPatterns.IRIS_R, life, level.random.nextLong()));
        }

        @Override
        void tick() {
            double r = TacticalPatterns.IRIS_R;
            if (age == 0) impact(level, center, 0x7CFFD8, r, 0.3F, 0);
            // 敵の弾を消す
            for (Entity e : level.getEntities(source, new AABB(center, center).inflate(r), e -> ProjectileLike.is(e) && e.distanceToSqr(center) <= r * r)) {
                if (!ally(source, e)) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY(), e.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
                    e.discard();
                }
            }
            if (age % 10 == 0) { // ドーム
                for (int i = 0; i < 90; i++) {
                    double u = level.random.nextDouble() * Math.PI * 2, w = level.random.nextDouble() * Math.PI / 2;
                    Vec3 p = center.add(Math.cos(u) * Math.cos(w) * r, Math.sin(w) * r, Math.sin(u) * Math.cos(w) * r);
                    Fx.spray(level, Fx.opt(ModParticles.GLOW.get(), 0x7CFFD8, 0.5F, 20), p, 1, 0, 0, 0, 0);
                }
            }
            if (age % 20 == 0) { // 中の味方の HP を毎秒 10%、兵姫の充電 3 倍
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r),
                        e -> e.isAlive() && ally(source, e) && e.distanceToSqr(center) <= r * r)) {
                    if (e instanceof ValkyrieEntity v) {
                        v.repair(v.getMaxHealth() * 0.25F);
                        v.setEnergy(v.getEnergy() + 50_000); // 充電の上乗せ（毎秒）
                    } else {
                        e.heal(e.getMaxHealth() * 0.25F);
                    }
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 40, 1, true, true));
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 40, 0, true, true));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!CARVES.isEmpty()) runCarves();
        if (ACTIVE.isEmpty()) return;
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
