package com.automatavalkyries.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * エレノアのスケーリングとペナルティ（docs/SPEC_ELENOR.md §2.2・§2.3、2026-10-22 ユーザー指定）。
 * <ul>
 *   <li>スケーリング: 挑む側のレベル（経験値レベル。1000 超は強く）・防御力・DPS・敵対数から 1 つの係数を決め、攻撃力・受けるダメージの割り引き・技の間隔に掛ける。
 *       最大 HP の属性は Eprotect が毎 Tick 戻すので触らず、受けるダメージの割り引きで HP が増えたのと同じにする</li>
 *   <li>火力が高すぎるとき: 見込みの DPS を大きく超えたら、さらに死ににくくなり、少しずつ回復する</li>
 *   <li>ペナルティアタックのティア 0〜7: プレイヤーごとに持つ。技が通らない（防具・効果・無敵時間・ダメージの打ち消し…で削れない）たびに数え、
 *       緩めの回数（{@link #BASE_FAILS}）で一段上がる。エレノアへの過剰な攻撃・即死を感知すると、その攻撃者（分からなければ全員）の回数が減って上がりやすくなる。
 *       戦闘中は下がらない。戦い直し（新しい個体）でリセットされる</li>
 * </ul>
 * ティア 5〜7（隔絶ダメージ・守りの解体・終焉）は Antigravity 担当の入口を呼ぶだけ（依頼書 docs/HANDOFF_CLAUDE_TO_ANTIGRAVITY_ELENOR.md）。
 * 最果てのエレノアは、ティアが最初から 7 で固定（上がらない）、スケーリングなし
 */
public final class ElenorPressure {

    public static final int MAX_TIER = 7;
    /** 今の削り方で削り切るまでの見込みがこれ（秒。30 分）を超えたら、挑んでいる全員のティアを上げる */
    public static final long SLOW_KILL_SECONDS = 1800L;
    /** 敵対している（エレノアが狙っている）間、挑んだ相手が死なずに十分たったら（秒）、その人のティアを一段上げる。上がるたびに数え直す */
    public static final int SURVIVE_SECONDS = 120;
    /** 技が通らなかった回数がこれ（から、感知した分を引いた数）に達すると、ティアが一段上がる（緩め） */
    public static final int BASE_FAILS = 4;
    private static final int WINDOW_TICKS = 200;
    private static final double RANGE = 48.0D;
    /** 係数 1 のときに見込む、1 秒あたりの（試みた）ダメージ */
    private static final double EXPECTED_DPS = 12.0D;
    private static final float MAX_COEF = 8.0F;

    private record Hit(long tick, float amount) {}

    private final ElenorEntity elenor;
    private final boolean scaling;
    /** 0 以上なら、全員にそのティアで固定（最果てのエレノアは 7） */
    private final int fixedTier;

    private final List<Hit> hits = new ArrayList<>();
    private final Map<UUID, Integer> tiers = new HashMap<>();
    /** ティアを持った、プレイヤー以外の相手（対エンティティのティアを数える） */
    private final java.util.Set<UUID> nonPlayers = new java.util.HashSet<>();
    /** プレイヤー以外のすべての相手に使う、ティアの下限（砂時計で上げる。狙っている相手がいなくても上げられる） */
    private int entityBase;
    private final Map<UUID, Integer> fails = new HashMap<>();
    /** 過剰な攻撃・即死を感知した数（攻撃者ごと。攻撃者が分からないものは globalSense） */
    private final Map<UUID, Integer> sensed = new HashMap<>();
    private int globalSense;
    /** ボスバーへ最後に知らせたティアと、その時刻（プレイヤーごと） */
    private final Map<UUID, Integer> lastSentTier = new HashMap<>();
    private final Map<UUID, Long> lastSentAt = new HashMap<>();
    private final ElenorAdapt adapt;
    /** 直近 60 秒の HP（1 秒ごと）。削り切るまでの見込みを測る */
    private final java.util.ArrayDeque<Float> hpHistory = new java.util.ArrayDeque<>();
    private long lastSlowKillTier = Long.MIN_VALUE / 2;
    /** 狙われている間に、死なずにいた秒数（プレイヤーごと） */
    private final Map<UUID, Integer> survived = new HashMap<>();
    private float coef = 1.0F;
    private float excess;

    ElenorPressure(ElenorEntity elenor, boolean scaling, int fixedTier) {
        this.elenor = elenor;
        this.scaling = scaling;
        this.fixedTier = fixedTier;
        this.adapt = new ElenorAdapt(elenor);
    }

    // ---- 係数 ----------------------------------------------------------------

    /** レベルの係数: 100 までは緩やか、1000 を超えるとチート並みとみなして強く掛ける */
    public static float levelFactor(int level) {
        float lv = Math.max(0, level);
        float f = 1.0F + 0.2F * Math.min(lv, 100.0F) / 100.0F;
        f += 0.5F * Mth.clamp((lv - 100.0F) / 900.0F, 0.0F, 1.0F);
        if (lv > 1000.0F) f += 1.0F + 0.5F * Math.min((lv - 1000.0F) / 1000.0F, 2.0F);
        return f;
    }

    public static float defenseFactor(double armor, double maxHealth) {
        return (float) (1.0D + 0.5D * Mth.clamp(armor / 60.0D, 0.0D, 2.0D) + 0.25D * Mth.clamp((maxHealth - 20.0D) / 180.0D, 0.0D, 2.0D));
    }

    public static float dpsFactor(double dps) {
        return (float) (1.0D + 0.4D * Math.log10(1.0D + Math.max(0.0D, dps) / 10.0D));
    }

    public static float countFactor(int hostiles) {
        return 1.0F + 0.3F * Math.max(0, hostiles - 1);
    }

    public float coefficient() {
        return coef;
    }

    /** 技の攻撃力に掛ける */
    public float attackScale() {
        return scaling ? Mth.clamp(1.0F + (coef - 1.0F) * 0.35F, 1.0F, 3.5F) : 1.0F;
    }

    /** 技の間隔を割る */
    public float tempo() {
        return scaling ? Mth.clamp(1.0F + (coef - 1.0F) * 0.15F, 1.0F, 1.8F) : 1.0F;
    }

    /** 技の間隔を割る値（相手の守りの型を感知した数だけ、さらに縮む: AI ルーチンの強化） */
    public float tempo(@Nullable LivingEntity target) {
        return tempo() + 0.2F * Math.min(adapt.mechanisms(target), 3);
    }

    /** 受けるダメージを割る値（HP が増えたのと同じ）。火力が高すぎるときは、さらに大きくなる */
    public float incomingDivisor() {
        if (!scaling) return 1.0F;
        return Mth.clamp(1.0F + (coef - 1.0F) * 0.6F, 1.0F, 6.0F) * (1.0F + Math.min(excess, 4.0F) * 0.5F);
    }

    /** 見込みを超えた火力の割合（0 = 超えていない） */
    public float excess() {
        return excess;
    }

    public double dps(long now) {
        prune(now);
        if (hits.isEmpty()) return 0.0D;
        double sum = 0.0D;
        for (Hit hit : hits) sum += hit.amount;
        double seconds = Mth.clamp((now - hits.get(0).tick) / 20.0D + 1.0D, 2.0D, WINDOW_TICKS / 20.0D);
        return sum / seconds;
    }

    private void prune(long now) {
        for (Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            if (now - it.next().tick > WINDOW_TICKS) it.remove();
            else break;
        }
    }

    /** 毎 Tick（エンティティから）。1 秒ごとに係数を測り直し、火力が高すぎるときは回復する */
    void tick() {
        if (elenor.level() instanceof ServerLevel every) adapt.tick(every); // 毎 Tick（書き戻しの確認・細かく分けた攻撃）
        if (!(elenor.level() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        long now = level.getGameTime();
        syncTiers(level, now);
        if (fixedTier < 0) {
            checkSlowKill(level, now);
            checkSurvival(level);
        }
        if (!scaling) return;
        List<Player> players = level.getEntitiesOfClass(Player.class, new AABB(elenor.blockPosition()).inflate(RANGE),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        double dps = dps(now);
        if (!players.isEmpty()) {
            int level0 = 0;
            double armor = 0.0D, maxHealth = 20.0D;
            for (Player p : players) {
                level0 = Math.max(level0, p.experienceLevel);
                armor = Math.max(armor, p.getArmorValue() + p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                maxHealth = Math.max(maxHealth, p.getMaxHealth());
            }
            int hostiles = players.size();
            for (ValkyrieEntity v : level.getEntitiesOfClass(ValkyrieEntity.class, new AABB(elenor.blockPosition()).inflate(RANGE),
                    v -> v.isAlive() && v.getOwnerUUID() != null)) {
                hostiles++;
            }
            float next = levelFactor(level0) * defenseFactor(armor, maxHealth) * dpsFactor(dps) * countFactor(hostiles);
            coef = Mth.clamp(Math.max(coef, next), 1.0F, MAX_COEF); // 戦いの間は下げない
        }
        excess = (float) Mth.clamp(dps / (EXPECTED_DPS * coef) - 1.0D, 0.0D, 4.0D);
        if (excess > 0.0F && elenor.getHealth() > 0.0F && elenor.getHealth() < elenor.getMaxHealth()) {
            elenor.heal(elenor.getMaxHealth() * 0.004F * excess);
        }
    }

    /** 戦える相手か（プレイヤーはクリエイティブ・スペクテイター以外。それ以外のエンティティ（モブ・兵姫など）は生きていれば）。ティアはプレイヤーにもエンティティにも同じ決まりで持つ */
    static boolean fightable(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /** victim のティアを一段上げる（プレイヤーにはアクションバーで知らせる。エンティティは音だけ）。上げたら true */
    private boolean raise(LivingEntity victim, String messageKey) {
        UUID id = victim.getUUID();
        int tier = tier(victim);
        sensed.merge(id, 1, Integer::sum);
        if (tier >= MAX_TIER) return false;
        tiers.put(id, tier + 1);
        fails.put(id, 0);
        if (!(victim instanceof Player)) nonPlayers.add(id);
        if (victim instanceof Player player) {
            player.displayClientMessage(Component.translatable(messageKey, tier + 1,
                    Component.translatable("bossbar.automata_valkyries.elenor.tier.name." + (tier + 1))).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
        }
        elenor.level().playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.2F, 0.6F + 0.1F * (tier + 1));
        return true;
    }

    /** 砂時計などで、victim のティアを一段上げる。最果てのエレノア（ティア固定）や、もう最大なら false */
    public boolean boost(LivingEntity victim) {
        if (fixedTier >= 0) return false;
        return raise(victim, "message.automata_valkyries.elenor.tier");
    }

    /** 対エンティティ全体のティアを一段上げる（砂時計。狙っている相手がいなくてよい）。最果てのエレノアや、もう最大なら false */
    public boolean boostEntities() {
        if (fixedTier >= 0 || entityBase >= MAX_TIER) return false;
        entityBase++;
        elenor.level().playSound(null, elenor.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.2F, 0.6F + 0.1F * entityBase);
        return true;
    }

    /** 対エンティティのティア: プレイヤー以外の相手のうち、一番高いもの（最果てのエレノアは固定のティア） */
    public int entityTier() {
        if (fixedTier >= 0) return fixedTier;
        int best = entityBase;
        for (UUID id : nonPlayers) best = Math.max(best, tiers.getOrDefault(id, 0));
        return best;
    }

    /** 狙っている相手（プレイヤーでもエンティティでも）が、十分たっても死ななければ、そのティアを一段上げる（死んだら数え直す） */
    private void checkSurvival(ServerLevel level) {
        survived.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || !living.isAlive());
        LivingEntity target = elenor.getTarget();
        if (!fightable(target)) return;
        UUID id = target.getUUID();
        int seconds = survived.merge(id, 1, Integer::sum);
        if (seconds < SURVIVE_SECONDS) return;
        survived.put(id, 0);
        raise(target, "message.automata_valkyries.elenor.survive");
    }

    /**
     * 削れてはいるが、このペースでは削り切るまで 30 分を超える見込みなら、挑んでいる全員のティアを一段上げる（1 分に 1 回まで）。
     * 上がったあとは、その人の「通らなかった回数」の要る数も減る（ティアが高いほど過剰な攻撃を感知しやすい: {@link #sense}）
     */
    private void checkSlowKill(ServerLevel level, long now) {
        float hp = elenor.getHealth();
        hpHistory.addLast(hp);
        while (hpHistory.size() > 61) hpHistory.removeFirst();
        if (hpHistory.size() < 61 || hp <= 0.0F) return;
        float drop = hpHistory.peekFirst() - hp;
        if (drop <= 0.5F) return; // 削られていない（戦っていない）間は数えない
        double eta = hp / (drop / 60.0D);
        if (eta <= SLOW_KILL_SECONDS || now - lastSlowKillTier < 1200L) return;
        lastSlowKillTier = now;
        List<LivingEntity> fighters = new ArrayList<>(level.getEntitiesOfClass(Player.class, new AABB(elenor.blockPosition()).inflate(RANGE),
                ElenorPressure::fightable));
        LivingEntity target = elenor.getTarget();
        if (fightable(target) && !(target instanceof Player)) fighters.add(target); // モブ・兵姫などの相手も同じ決まり
        for (LivingEntity fighter : fighters) raise(fighter, "message.automata_valkyries.elenor.slowkill");
    }

    /** 近くのプレイヤーへ、その人に使っているティアを知らせる（変わったとき、と 5 秒ごと） */
    private void syncTiers(ServerLevel level, long now) {
        for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                new AABB(elenor.blockPosition()).inflate(64.0D))) {
            int tier = tier(player);
            int entityTier = entityTier();
            int key = tier * 8 + entityTier;
            Integer last = lastSentTier.get(player.getUUID());
            long at = lastSentAt.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (last != null && last == key && now - at < 100L) continue;
            lastSentTier.put(player.getUUID(), key);
            lastSentAt.put(player.getUUID(), now);
            com.automatavalkyries.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.automatavalkyries.network.ElenorTierPacket(tier, entityTier));
        }
    }

    // ---- 被弾（エンティティから） -----------------------------------------------

    /** 受けるダメージを記録し、過剰な攻撃・即死を感知して、割り引いた値を返す */
    float onHurt(DamageSource source, float amount) {
        if (!(elenor.level() instanceof ServerLevel level)) return amount;
        boolean finite = Float.isFinite(amount);
        float max = elenor.getMaxHealth();
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || !finite || amount >= 1000.0F) {
            sense(source.getEntity(), 2);
        } else if (amount > max * 0.1F) {
            sense(source.getEntity(), 1);
        }
        if (!finite) return amount;
        hits.add(new Hit(level.getGameTime(), Math.min(amount, 1000.0F)));
        if (hits.size() > 2000) hits.subList(0, 1000).clear();
        return amount / incomingDivisor();
    }

    /** die() の直接呼び出し（即死）を Eprotect が断ったとき */
    void onInstantKillRefused(@Nullable DamageSource source) {
        sense(source == null ? null : source.getEntity(), 2);
    }

    /** 過剰な攻撃・即死の感知。ティアが高いほど、同じ攻撃でも重く数える（上がりやすくなる） */
    private void sense(@Nullable Entity attacker, int weight) {
        if (attacker instanceof LivingEntity living) sensed.merge(living.getUUID(), weight + tier(living) / 2, Integer::sum);
        else globalSense += weight;
    }

    // ---- ティア ----------------------------------------------------------------

    public int tier(@Nullable Entity victim) {
        if (fixedTier >= 0) return fixedTier;
        if (victim instanceof Player) return tiers.getOrDefault(victim.getUUID(), 0);
        return victim instanceof LivingEntity ? Math.max(entityBase, tiers.getOrDefault(victim.getUUID(), 0)) : 0;
    }

    /** 次の段へ上がるのに要る、技が通らなかった回数（感知した分だけ減る。最小 1） */
    public int failThreshold(UUID victim) {
        return Math.max(1, BASE_FAILS - sensed.getOrDefault(victim, 0) - globalSense - tiers.getOrDefault(victim, 0) / 2); // ティアが高いほど上がりやすい
    }

    /** victim へ amount の攻撃を、その相手のティアの攻め方で通す。通らなかったらティアが上がる方向に数える */
    public void strike(LivingEntity victim, float amount) {
        if (!(elenor.level() instanceof ServerLevel level) || !victim.isAlive() || !Float.isFinite(amount) || amount <= 0.0F) return;
        int tier = tier(victim);
        float before = victim.getHealth();
        float judged = amount;
        try {
            judged = apply(level, victim, amount, tier);
        } catch (Throwable error) {
            victim.hurt(elenor.damageSources().mobAttack(elenor), amount);
        }
        if (judged > 0.0F && fixedTier < 0 && fightable(victim)) {
            noteStrikeResult(victim, before - victim.getHealth(), judged);
        }
    }

    /** ティアの攻め方で通す。返す値は、技が通らなかった数えに使う大きさ（負なら数えない） */
    private float apply(ServerLevel level, LivingEntity victim, float amount, int tier) {
        DamageSource normal = elenor.damageSources().mobAttack(elenor);
        DamageSource piercing = elenor.damageSources().indirectMagic(elenor, elenor); // 防具を無視する
        switch (Math.min(tier, 6)) {
            case 0 -> victim.hurt(normal, amount);
            case 1, 2, 3, 4 -> { // 相手の守りの型を調べて合わせる（ElenorAdapt）
                return adapt.strike(level, victim, amount, tier);
            }
            case 5 -> { // 隔絶ダメージ
                victim.invulnerableTime = 0;
                if (!isolation(victim, piercing, amount)) victim.hurt(piercing, amount);
            }
            default -> { // 6: 守りを解体してから隔絶ダメージ
                if (victim instanceof Player player) breakDefenses(level, player);
                victim.invulnerableTime = 0;
                if (!isolation(victim, piercing, amount)) victim.hurt(piercing, amount);
            }
        }
        return amount;
    }

    private static void stripGuardEffects(LivingEntity victim) {
        victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        victim.removeEffect(MobEffects.ABSORPTION);
        victim.setAbsorptionAmount(0.0F);
    }

    /** 技の結果を数える: amount の技で dealt しか削れなかった（四分の一未満）ら、通らなかった回数が増え、足りれば一段上がる */
    public void noteStrikeResult(LivingEntity victim, float dealt, float amount) {
        if (fixedTier >= 0) return;
        int tier = tier(victim);
        UUID id = victim.getUUID();
        if (dealt >= amount * 0.25F) {
            fails.put(id, 0);
            return;
        }
        int count = fails.merge(id, 1, Integer::sum);
        if (count < failThreshold(id) || tier >= MAX_TIER) return;
        raise(victim, "message.automata_valkyries.elenor.tier");
    }

    // ---- 終焉（ティア 7。反撃では使わず、技の中だけ。ちっぽけな勇気は Antigravity 側で効かなくなる） ----

    /** victim がティア 7 なら終焉を執行する。執行を試みたら true */
    public boolean terminus(ServerLevel level, LivingEntity victim) {
        if (tier(victim) < MAX_TIER || !victim.isAlive()) return false;
        if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        com.automatavalkyries.annihilate.TerminusEngine.executeTerminus(level, elenor, victim);
        return true;
    }

    // ---- Antigravity 担当の入口（リフレクション。無ければ通常の攻撃に戻す） ----------------------------------

    private static final Method ISOLATION = find("com.automatavalkyries.annihilate.IsolationDamageEngine", "ensureIsolatedAttack",
            LivingEntity.class, LivingEntity.class, DamageSource.class, float.class);
    private static final Method BREAK_DEFENSES = find("com.automatavalkyries.annihilate.TerminusPlayerExecutor", "breakPlayerDefensesOnly",
            ServerLevel.class, Entity.class, Player.class);

    @Nullable
    private static Method find(String className, String name, Class<?>... params) {
        try {
            return Class.forName(className).getMethod(name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isolation(LivingEntity victim, DamageSource source, float amount) {
        if (ISOLATION == null) return false;
        try {
            return Boolean.TRUE.equals(ISOLATION.invoke(null, elenor, victim, source, amount));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void breakDefenses(ServerLevel level, Player player) {
        if (BREAK_DEFENSES == null) return;
        try {
            BREAK_DEFENSES.invoke(null, level, elenor, player);
        } catch (Throwable ignored) {
            // 解体できなくても、通常の隔絶ダメージへ進む
        }
    }
}
