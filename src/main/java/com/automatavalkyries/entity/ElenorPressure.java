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
    public static final int SURVIVE_SECONDS = 180;
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
        finishCorpses(level);
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
    /**
     * 生きている相手か（2026-10-25 ユーザー指定「汎用化できないか」で、Madness 専用の見分けから広げた）。他 MOD のボスには、倒れていないのに
     * isAlive が偽と答えるもの（Madness Entities の Popbob・古代ネザー遺跡の残骸などの「生きていない扱いのプロキシ」、getter を偽る型）がいて、
     * 技が当たらず、ティアも上がらなかった。次のどれかなら生きていると見なす:
     * <ol>
     *   <li>isAlive が真</li>
     *   <li>保存場所の実際の HP が正（isAlive の getter だけ偽られている）</li>
     *   <li>Madness Entities のボス（{@code MadnessAnnihilation.isMadness}。呼ぶだけ。速い道）</li>
     *   <li>ワールドに載ったまま消されておらず、倒れる演出に入っていない（deathTime 0・死亡の印なし）うえに、戦っている
     *       （最近このMOD の守られたものを攻撃した・危険なフラグを持つ）。本当に倒れたモブは倒れる演出が進むので外れ、戦っていない「倒れた扱い」も外れる</li>
     * </ol>
     * プレイヤーは isAlive のまま（死んだプレイヤーを生き返った扱いにしない）
     */
    public static boolean living(@Nullable LivingEntity e) {
        if (e == null) return false;
        if (e.isAlive()) return true;
        if (e instanceof Player) return false;
        if (com.automatavalkyries.protect.DirectRead.isRemoved(e)) return false;
        if (com.automatavalkyries.protect.DirectRead.isAlive(e)) return true;
        if (com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(e)) return true;
        if (e.deathTime > 0 || com.automatavalkyries.protect.DirectRead.deadFlag(e)) return false;
        if (e.level().getEntity(e.getId()) != e) return false;
        return com.automatavalkyries.protect.attack.AttackLog.isRecentAttacker(e)
                || com.automatavalkyries.annihilate.FlagKillPredicates.hasDangerousFlag(e);
    }

    static boolean fightable(@Nullable LivingEntity e) {
        if (!living(e)) return false;
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
        if (elenor.level() instanceof ServerLevel level) ElenorFx.tierUp(level, victim.position(), tier + 1);
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
        survived.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || !living(living));
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
        if (eta <= SLOW_KILL_SECONDS || now - lastSlowKillTier < 2400L) return; // 2 分に 1 回まで
        lastSlowKillTier = now;
        List<LivingEntity> fighters = new ArrayList<>(level.getEntitiesOfClass(Player.class, new AABB(elenor.blockPosition()).inflate(RANGE),
                ElenorPressure::fightable));
        LivingEntity target = elenor.getTarget();
        if (fightable(target) && !(target instanceof Player)) fighters.add(target); // モブ・兵姫などの相手も同じ決まり
        for (LivingEntity fighter : fighters) raise(fighter, "message.automata_valkyries.elenor.slowkill");
    }

    /** エレノアが攻撃した、プレイヤー以外の相手（倒し切ったかを見届ける） */
    private final java.util.Set<UUID> struck = new java.util.HashSet<>();
    /** 死んだ状態になったのを最初に見た時刻（自前の死亡処理で deathTime が進まない MOD もあるので、こちらで数える） */
    private final Map<UUID, Long> deadSince = new HashMap<>();

    /**
     * 倒し切る: エレノアが攻撃した相手が、死んだ状態（HP 0・死亡中）のまま 2 秒（40 Tick）以上ワールドに残っていたら消去する。
     * Omni-Mobs などは自前の死亡処理が最後まで進まず、死体が残っていた（2026-10-23）。プレイヤーとこのMOD の守られたエンティティは対象外
     */
    private void finishCorpses(ServerLevel level) {
        if (struck.isEmpty()) return;
        for (java.util.Iterator<UUID> it = struck.iterator(); it.hasNext(); ) {
            Entity entity = level.getEntity(it.next());
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
                if (entity == null || entity.isRemoved()) deadSince.remove(entity == null ? null : entity.getUUID());
                it.remove();
                continue;
            }
            if (living instanceof Player || living instanceof com.automatavalkyries.protect.IEprotected) {
                it.remove();
                continue;
            }
            boolean dead = !(living.getHealth() > 0.0F) || living.isDeadOrDying();
            if (!dead) {
                deadSince.remove(living.getUUID());
                continue;
            }
            long since = deadSince.computeIfAbsent(living.getUUID(), k -> level.getGameTime());
            if (level.getGameTime() - since >= 40L) {
                deadSince.remove(living.getUUID());
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, living.getX(), living.getY(0.5), living.getZ(), 20, 0.4, 0.4, 0.4, 0.02);
                living.remove(Entity.RemovalReason.KILLED);
                it.remove();
            }
        }
    }

    /** 近くのプレイヤーへ、その人に使っているティアを知らせる（変わったとき、と 5 秒ごと） */
    private void syncTiers(ServerLevel level, long now) {
        java.util.Set<net.minecraft.server.level.ServerPlayer> viewers = new java.util.HashSet<>(elenor.bossBarViewers());
        viewers.addAll(level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, new AABB(elenor.blockPosition()).inflate(64.0D)));
        for (net.minecraft.server.level.ServerPlayer player : viewers) {
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
            onVicious(Vicious.LETHAL, source.getEntity());
        } else if (amount > max * 0.1F) {
            onVicious(Vicious.EXCESS, source.getEntity());
        }
        if (!finite) return amount;
        hits.add(new Hit(level.getGameTime(), Math.min(amount, 1000.0F)));
        if (hits.size() > 2000) hits.subList(0, 1000).clear();
        return amount / incomingDivisor();
    }

    /** die() の直接呼び出し（即死）を Eprotect が断ったとき */
    void onInstantKillRefused(@Nullable DamageSource source) {
        onVicious(Vicious.DEATH, source == null ? null : source.getEntity());
    }

    // ---- 凶悪技術（受けた攻撃の凶悪度。凶悪なほど、ティアが上がりやすい。2026-10-23 ユーザー指定） ----

    /**
     * 凶悪度 I〜V。重み（{@link #weight}）だけ「通らなかった回数」の要る数が減る。IV・V は、使った相手（分かったときだけ）の
     * ティアをその場で一段上げる（10 秒に 1 回まで）
     */
    public enum Vicious {
        /** I 過剰ナ一撃: 最大 HP の 10% を超える一撃 */
        EXCESS(1),
        /** II 即死ノ刃: 即死の大きさ・種類のダメージ（無限大・NaN・generic_kill・奈落）、ダメージ外の HP の減少・同期データへの書き込み・読み出しの偽装 */
        LETHAL(2),
        /** III 死ノ宣告: die() の直接呼び出し・死亡の印・Hyperlink の novelize などの即死の操作 */
        DEATH(3),
        /** IV 存在ノ抹消: 消去（remove・discard）・ワールドからの取り外し・奈落への強制移動・次元移動 */
        ERASURE(4),
        /** V 理ノ改竄: Tick の停止・時間停止・最大 HP（属性）の改ざん・入れ物の差し替え・イベントの横取り */
        TAMPER(6);

        public final int weight;

        Vicious(int weight) {
            this.weight = weight;
        }
    }

    /** 近く（64 m）の、その MOD のエンティティ（プレイヤー・エレノア自身を除く）のうち一番近いもの。Minecraft とこのMOD の名前では探さない */
    @Nullable
    private LivingEntity findByMod(ServerLevel level, String mod) {
        if (mod.isEmpty() || "minecraft".equals(mod) || "forge".equals(mod) || com.automatavalkyries.AutomataValkyries.MODID.equals(mod)) return null;
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, elenor.getBoundingBox().inflate(64.0D),
                x -> !(x instanceof Player) && x != elenor && !(x instanceof ElenorEntity)
                        && mod.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).getNamespace()))) {
            double d = elenor.distanceToSqr(e);
            if (d < bestDistance) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    /** 保護の記録（AttackLog）の操作の名前から、凶悪度を決める。数えない操作（撤退など）は null */
    @Nullable
    public static Vicious classify(String op, boolean blacklisted) {
        Vicious base = classifyOp(op.toLowerCase(java.util.Locale.ROOT));
        if (!blacklisted) return base;
        // ブラックリストの MOD は、その操作の本来の凶悪度を一段だけ上げる（一律 V にすると、精鋭の MOD はほとんど V になっていた。2026-10-23）
        if (base == null) return Vicious.LETHAL;
        return Vicious.values()[Math.min(base.ordinal() + 1, Vicious.TAMPER.ordinal())];
    }

    private static Vicious classifyOp(String o) {
        if (o.startsWith("retreat")) return null;
        // 即死 MOD 専用の対策（compat:MOD:操作）と、外した印（neutralized:種類:…）は、操作の中身で分ける
        if (o.startsWith("compat:")) {
            String[] parts = o.split(":", 3);
            return classifyCounter(parts.length >= 3 ? parts[2] : o);
        }
        if (o.startsWith("neutralized")) {
            if (o.contains("kill_list") || o.contains("remove") || o.contains("erase")) return Vicious.ERASURE;
            if (o.contains("time") || o.contains("frozen") || o.contains("stop")) return Vicious.TAMPER;
            return Vicious.DEATH;
        }
        if (o.contains("tick") || o.contains("max_health") || o.contains("entity_data_container") || o.contains("event_bus")
                || o.contains("presence")) return Vicious.TAMPER;
        if (o.startsWith("remove") || o.startsWith("discard") || o.contains("removed_from_world") || o.contains("removal")
                || o.contains("unsafe_position") || o.contains("forced_relocation") || o.contains("dimension_change")) return Vicious.ERASURE;
        if (o.startsWith("die") || o.startsWith("novelize") || o.contains("dead_flag") || o.equals("repair:health")) return Vicious.DEATH;
        if (o.startsWith("kill_damage") || o.startsWith("health") || o.startsWith("synched_data") || o.startsWith("spoofed_read")
                || o.contains("health_cut") || o.contains("health_spoof")) return Vicious.LETHAL;
        if (o.startsWith("damage")) return Vicious.EXCESS;
        return null;
    }

    /** 即死 MOD 専用の対策が止めた操作の名前（erase・instantKill・setHealth など）から凶悪度を決める */
    private static Vicious classifyCounter(String name) {
        if (name.contains("time") || name.contains("stop") || name.contains("frozen") || name.contains("freeze") || name.contains("tick")
                || name.contains("attribute") || name.contains("maxhealth") || name.contains("max_health") || name.contains("container")
                || name.contains("event")) return Vicious.TAMPER;
        if (name.contains("erase") || name.contains("remove") || name.contains("discard") || name.contains("setpos") || name.contains("teleport")
                || name.contains("position") || name.contains("dimension") || name.contains("relocat") || name.contains("void")) return Vicious.ERASURE;
        if (name.contains("health") || name.contains("damage") || name.contains("hurt")) return Vicious.LETHAL;
        return Vicious.DEATH; // instantKill・die・kill・novel・schedule など、名前で分からない即死の操作
    }

    private final long[] lastVicious = new long[Vicious.values().length];
    private final long[] lastViciousNotice = new long[Vicious.values().length];
    private long lastViciousRaise = Long.MIN_VALUE / 2;

    /** 凶悪技術を使ってきた相手（UUID → 最後の時刻）。敵対の相手に加える（{@link #isViciousAttacker}） */
    private final Map<UUID, Long> viciousAttackers = new HashMap<>();

    /** 最近（30 秒以内に）凶悪技術を使ってきた相手か */
    public boolean isViciousAttacker(Entity entity) {
        Long at = viciousAttackers.get(entity.getUUID());
        return at != null && elenor.level().getGameTime() - at < 600L;
    }

    public void onVicious(Vicious vicious, @Nullable Entity attacker) {
        onVicious(vicious, attacker, null);
    }

    /**
     * 凶悪技術を受けた。attacker が分からなければ、記録の MOD の名前（mod）から近くのその MOD のエンティティを探し、
     * それも無ければ最近エレノアを攻撃した相手、それも分からなければ全員の上がりやすさに効く。
     * 見つけた相手は敵対の相手にし、狙っている相手がいなければ狙う（即死系の操作はダメージでないので、そのままでは敵対されなかった）
     */
    public void onVicious(Vicious vicious, @Nullable Entity attacker, @Nullable String mod) {
        if (!(elenor.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        long now = level.getGameTime();
        if (attacker == null && mod != null) attacker = findByMod(level, mod);
        if (attacker instanceof LivingEntity living && !(living instanceof Player) && living != elenor) {
            viciousAttackers.put(living.getUUID(), now);
            if (viciousAttackers.size() > 64) viciousAttackers.values().removeIf(at -> now - at > 600L);
            LivingEntity current = elenor.getTarget();
            if (current == null || !current.isAlive() || current instanceof Player player && !fightable(player)) elenor.setTarget(living);
        }
        int i = vicious.ordinal();
        if (now - lastVicious[i] < 20L) return; // 同じ凶悪度は 1 秒に 1 回だけ数える（毎 Tick 起きる操作もある）
        lastVicious[i] = now;
        if (attacker == null && elenor.getLastHurtByMob() != null && elenor.tickCount - elenor.getLastHurtByMobTimestamp() < 200) {
            attacker = elenor.getLastHurtByMob();
        }
        sense(attacker, vicious.weight);
        if (vicious.ordinal() >= Vicious.DEATH.ordinal() && now - lastViciousNotice[i] > 200L) {
            lastViciousNotice[i] = now;
            ElenorFx.viciousReaction(level, elenor.position().add(0, 1.2, 0), vicious.ordinal() + 1);
            Component name = Component.translatable("message.automata_valkyries.elenor.vicious." + vicious.name().toLowerCase(java.util.Locale.ROOT));
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(elenor.blockPosition()).inflate(RANGE))) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.elenor.vicious", name)
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
            }
        }
        if (fixedTier < 0 && vicious.ordinal() >= Vicious.ERASURE.ordinal() && now - lastViciousRaise >= 200L) {
            // 使った相手が分かったときだけ上げる（分からないのに狙っている相手を上げると、無関係の相手が高いティアで倒されていた。2026-10-23）
            LivingEntity who = attacker instanceof LivingEntity living && fightable(living) ? living : null;
            if (who != null) {
                lastViciousRaise = now;
                raise(who, "message.automata_valkyries.elenor.tier");
            }
        }
    }


    /** 過剰な攻撃・即死の感知。ティアが高いほど、同じ攻撃でも重く数える（上がりやすくなる） */
    private void sense(@Nullable Entity attacker, int weight) {
        if (attacker instanceof LivingEntity living) sensed.merge(living.getUUID(), weight + tier(living) / 3, Integer::sum);
        else globalSense += weight;
    }

    // ---- ティア ----------------------------------------------------------------

    /** ティア 1〜4 の調べの結果（{@code /elenor profile}） */
    public java.util.List<net.minecraft.network.chat.Component> describeAdapt(UUID victim) {
        return adapt.describe(victim);
    }

    public int tier(@Nullable Entity victim) {
        if (fixedTier >= 0) return fixedTier;
        if (victim instanceof Player) return tiers.getOrDefault(victim.getUUID(), 0);
        return victim instanceof LivingEntity ? Math.max(entityBase, tiers.getOrDefault(victim.getUUID(), 0)) : 0;
    }

    /** 次の段へ上がるのに要る、技が通らなかった回数（感知した分だけ減る。最小 1） */
    public int failThreshold(UUID victim) {
        return Math.max(1, BASE_FAILS - sensed.getOrDefault(victim, 0) - globalSense - tiers.getOrDefault(victim, 0) / 3); // ティアが高いほど上がりやすい（最小 1）
    }

    /** victim へ amount の攻撃を、その相手のティアの攻め方で通す。通らなかったらティアが上がる方向に数える */
    public void strike(LivingEntity victim, float amount) {
        if (!(elenor.level() instanceof ServerLevel level) || !living(victim) || !Float.isFinite(amount) || amount <= 0.0F) return;
        int tier = tier(victim);
        if (!(victim instanceof Player) && struck.size() < 256) struck.add(victim.getUUID());
        float before = com.automatavalkyries.protect.DirectRead.health(victim); // getHealth を偽る MOD で測り違えないよう、実際の値で測る
        float judged = amount;
        try {
            judged = apply(level, victim, amount, tier);
        } catch (Throwable error) {
            victim.hurt(elenor.damageSources().mobAttack(elenor), amount);
        }
        if (judged > 0.0F && fixedTier < 0 && fightable(victim)) {
            noteStrikeResult(victim, before - com.automatavalkyries.protect.DirectRead.health(victim), judged);
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
        if (tier(victim) < MAX_TIER || !living(victim)) return false;
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
