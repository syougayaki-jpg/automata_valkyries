package com.automatavalkyries.entity;

import com.automatavalkyries.protect.DirectRead;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ペナルティアタックのティア 1〜4 の、相手の守りを調べて合わせる攻め方（docs/SPEC_ELENOR.md §2.3.3、2026-10-23 ユーザー指定。
 * 2026-10-24 ユーザー指定「ティア 1 から 4 のペナルティを調整してダメージ調査などを精密にして」で作り直した）。
 * 相手ごとに「守りの型」を覚え、感知した型に合わせて攻め方を変える。調べ方は:
 * <ul>
 *   <li>測り方: HP は getHealth を通さず、同期データの実際の値（{@link DirectRead#health}）で測る（getHealth を偽る MOD で測り違えない）。
 *       被弾後の無敵時間の最中（ティア 1。無敵時間を崩すのはティア 2 から）・盾で防がれたときの結果は、守りの型の調べにも、ティアの数え（技が通らなかった）にも使わない</li>
 *   <li>有効ダメージ: ダメージの種類ごとに、通った割合のなめらかな平均（新しい結果ほど重い。守りを替えられても追い付く）と試した回数を持ち、
 *       「通りやすさ + 試した回数が少ないほどの上乗せ」（UCB）で選ぶ。まだ試していない種類は先に一度試す</li>
 *   <li>通らなかったときの見分け: hurt が受け付けたのに 0 で、その大きさがもう通ったことがあれば「上限」、通ったことのない大きさなら「足切り」</li>
 *   <li>足切り: 通らなかった一番大きな量と、通った一番小さな量の間を半分ずつ詰めて（二分探索）、足切りの境目を求める。2 回確かめて確定</li>
 *   <li>上限（一定の間に通る量）: 通った量と時刻を覚え、通らなくなったときの直前の合計を上限、また通り始めた時刻との差をその間の長さとして測る
 *       （決め打ちの 200 Tick ではなく、測った長さ）。使い切っている間は小さな探り（数えない）だけにし、空いたら残りの量ぴったりで撃つ</li>
 *   <li>1 発あたりの上限: 大きく撃っても同じ量しか通らない（2 回、1 割以内）。上限の量を覚え、ティア 3 から無敵を貫く種類を、上限の量に分けて続けて当てる</li>
 *   <li>回復: 前の攻撃のあとの HP と次の攻撃の前の HP から回復の速さ（HP/秒）を測る（なめらかな平均）。エレノアの与える速さと比べ、
 *       上回るほど大きく撃つ（最大 1.6 倍）</li>
 *   <li>書き戻し: 削った HP が戻っていないかを 1・3・10 Tick 後に見る。戻されたら、その削りは「通らなかった」と数え直し（ティアの数え）、
 *       戻すまでの速さに合わせて分けて削る</li>
 * </ul>
 * ペナルティ（崩し）は、ティアの範囲の中で、感知した型にだけ使う（調べずに一律で使うのをやめた）。ティア 5 以上は Antigravity の入口を呼ぶ別の道（{@link ElenorPressure}）
 */
public final class ElenorAdapt {

    /** 上限の間の長さの、測る前の見込み（Tick） */
    private static final int DEFAULT_WINDOW = 200;
    private static final int BURST_TICKS = 6;
    /** 通った量の記録を残す長さ（Tick） */
    private static final int HISTORY = 1200;
    /**
     * 攻撃の種類の番号。0〜4 はバニラ、5〜7 はエレノアだけの未知の種類、8 は最果てダメージ（ティア 4 の本命）、
     * 9〜11 は環境のダメージ（炎・凍結・窒息。攻撃者なし）、12・13 は攻撃者を外した未知の種類、14 はバニラで一番強い貫通（generic_kill）
     */
    private static final int SOURCES = 16;
    /** Iron's Spells 'n Spellbooks が入っているとき、相手の学派ごとの耐性が一番低い学派の魔法ダメージ（無ければ 0 番に戻す） */
    private static final int SCHOOL = 15;
    private static final String[] SCHOOLS = {"fire", "ice", "lightning", "holy", "ender", "blood", "evocation", "nature", "eldritch"};
    private static final int FARTHEST = 8;
    private static final int GENERIC_KILL = 14;
    /** 無敵を貫く、エレノアだけの種類（縛め） */
    private static final int LIMIT_BREAK = 6;
    private static final String[] UNKNOWN = {"elenor_hollow", "elenor_ribbon", "elenor_afterimage", "elenor_farthest"};
    private static final String[] SOURCE_NAMES = {"magic(armor-piercing)", "magic", "wither", "dragon_breath", "sonic_boom", "hollow", "ribbon", "afterimage",
            "farthest", "fire", "freeze", "drown", "hollow(no attacker)", "afterimage(owner)", "generic_kill", "spell_school"};
    /** ティアごとの使える種類（ティア 1 は少なく、上がるほど増える） */
    private static final int[] TIER1 = {0, 1, 5, 15};
    private static final int[] TIER2 = {0, 1, 2, 3, 5, 12, 15};
    private static final int[] TIER3 = {0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 15};
    /** UCB の上乗せの強さ */
    private static final float EXPLORE = 0.35F;
    /** 通った割合の平均の重み（新しい結果） */
    private static final float EMA = 0.35F;

    private record Dealt(long tick, float amount) {}

    private static final class Profile {
        final float[] rate = new float[SOURCES];
        final int[] tries = new int[SOURCES];
        int totalTries;
        // 足切り
        float maxFail;
        float minPass = Float.MAX_VALUE;
        int floorVotes;
        boolean floorKnown;
        // 上限（一定の間）
        final ArrayDeque<Dealt> history = new ArrayDeque<>();
        float cap = Float.MAX_VALUE;
        int window = DEFAULT_WINDOW;
        int capVotes;
        boolean capKnown;
        long blockedSince = -1L;
        // 1 発あたりの上限
        boolean perHitLimit;
        float hitLimit;
        float lastDealt = -1.0F;
        // 回復
        float healRate;
        float dealRate;
        float hpAfter = -1.0F;
        long lastTick = -1L;
        // 書き戻し
        int reverts;
        int revertDelay = 3;
        // その他
        boolean lastStand;
        int denied;
        int invalid;
        int lastIndex = -1;
        long totemBaitAt = -1000L;
        long dotAt = -1000L;
        float lastDistance = -1.0F;
        int strikes;

        boolean fastRegen() {
            return healRate > 0.5F && healRate > dealRate * 0.5F;
        }
    }

    private record Check(UUID id, long due, float expectedHp, float dealt, int delay) {}

    private static final class Burst {
        final UUID id;
        int left;
        final float perTick;

        Burst(UUID id, int left, float perTick) {
            this.id = id;
            this.left = left;
            this.perTick = perTick;
        }
    }

    private final ElenorEntity elenor;
    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final List<Check> checks = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();

    ElenorAdapt(ElenorEntity elenor) {
        this.elenor = elenor;
    }

    private Profile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> new Profile());
    }

    /** 感知した型の数（技の間隔の縮みに使う） */
    int mechanisms(@Nullable LivingEntity victim) {
        if (victim == null) return 0;
        Profile p = profiles.get(victim.getUUID());
        if (p == null) return 0;
        int n = 0;
        if (p.capKnown) n++;
        if (p.floorKnown) n++;
        if (p.reverts > 0) n++;
        if (p.lastStand) n++;
        if (p.fastRegen()) n++;
        if (p.perHitLimit) n++;
        return n;
    }

    private DamageSource source(int index) {
        var sources = elenor.damageSources();
        return switch (index) {
            case 0 -> sources.indirectMagic(elenor, elenor); // 防具を無視する
            case 1 -> sources.magic();
            case 2 -> sources.wither();
            case 3 -> sources.dragonBreath();
            case 4 -> sources.sonicBoom(elenor);
            case 5, 6, 7, 8 -> unknown(UNKNOWN[index - 5], elenor, elenor);
            case 9 -> sources.onFire();
            case 10 -> sources.freeze();
            case 11 -> sources.drown();
            case 12 -> unknown(UNKNOWN[0], null, null); // 攻撃者なし
            case 13 -> unknown(UNKNOWN[2], null, elenor); // 持ち主だけ
            default -> farthest();
        };
    }

    /** Iron's Spells の学派の魔法ダメージのうち、相手の耐性が一番低いもの（無ければ null） */
    @Nullable
    private DamageSource school(LivingEntity victim) {
        var registry = elenor.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> bestType = null;
        double bestResist = Double.MAX_VALUE;
        for (String school : SCHOOLS) {
            var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", school + "_magic")));
            if (holder.isEmpty()) continue;
            var attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", school + "_magic_resist"));
            double resist = attribute != null && victim.getAttributes().hasAttribute(attribute) ? victim.getAttributeValue(attribute) : 1.0D;
            if (resist < bestResist) {
                bestResist = resist;
                bestType = holder.get();
            }
        }
        return bestType == null ? null : new DamageSource(bestType, elenor, elenor);
    }

    /**
     * 最果てダメージ（ティア 4）: バニラで作れる一番強い貫通ダメージ。`generic_kill` の種類で、エレノアからの攻撃として当てる。
     * 無敵（無敵の印・クリエイティブ以外の無敵）・防具・耐性の効果・盾・不死のトーテムを素通りする。通る量は攻撃の大きさのまま
     */
    private DamageSource farthest() {
        var type = elenor.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        return new DamageSource(type, elenor);
    }

    /**
     * 未知のダメージ属性: このMOD のデータで作った、エレノアだけのダメージの種類。他の MOD の守り（種類の名前・既知の一覧で見分ける型）に載っておらず、
     * 種類のタグ（防具・効果・耐性・エンチャント・盾・無敵時間・無敵）を組み合わせて貫く。データが読めなければ最果てダメージに戻す
     */
    private DamageSource unknown(String name, @Nullable net.minecraft.world.entity.Entity direct, @Nullable net.minecraft.world.entity.Entity causing) {
        var registry = elenor.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, name));
        var holder = registry.getHolder(key);
        return holder.isPresent() ? new DamageSource(holder.get(), direct, causing) : farthest();
    }

    /**
     * 種類を選ぶ: 通りやすさの平均に、試した回数が少ないほど大きい上乗せ（UCB）を足した点で一番高い物。まだ試していない種類は先に一度試す。
     * 直前と同じ種類は続けない（同じ種類を一度だけ無効にする型への備え）
     */
    private int pickSource(Profile p, int tier) {
        if (tier >= 4) return p.lastIndex == FARTHEST ? GENERIC_KILL : FARTHEST; // 同じ種類を続けず、最果てとバニラの貫通を交互に
        // 1 発あたりの上限・一定の間の上限の型（ティア 3〜）: 無敵を貫く種類（縛め・バニラの generic_kill）を交互に使う。他の種類では上限が外れない
        if (tier >= 3 && (p.perHitLimit || p.capKnown)) return p.lastIndex == LIMIT_BREAK ? GENERIC_KILL : LIMIT_BREAK;
        int[] pool = tier <= 1 ? TIER1 : tier == 2 ? TIER2 : TIER3;
        for (int i : pool) {
            if (p.tries[i] == 0 && i != p.lastIndex) return i;
        }
        int best = -1;
        float bestScore = -1.0F;
        double log = Math.log(p.totalTries + 1.0);
        for (int i : pool) {
            if (i == p.lastIndex && pool.length > 1) continue;
            float score = p.rate[i] + EXPLORE * (float) Math.sqrt(log / (p.tries[i] + 1.0));
            if (score > bestScore + 1.0e-4F) {
                bestScore = score;
                best = i;
            }
        }
        return best < 0 ? pool[0] : best;
    }

    /** 足切りを越える大きさ: 境目が分かっていれば少し上、探している間は「通らなかった一番大きな量」と「通った一番小さな量」の真ん中 */
    public static float aboveFloor(float amount, float maxFail, float minPass, boolean floorKnown) {
        if (maxFail <= 0.0F || amount > maxFail * 1.05F) return amount;
        float next = minPass < Float.MAX_VALUE ? (floorKnown ? minPass * 1.02F : (maxFail + minPass) * 0.5F) : maxFail * 1.6F;
        return Math.min(Math.max(amount, next), amount * 8.0F);
    }

    /** 割合ダメージの、最大 HP に対する割合（ティア 4） */
    public static final float PERCENT = 0.03F;
    /** 割合ダメージで上乗せする量の、1 発あたりの上限（2026-10-24 ユーザー指定「上限 100 万くらいでいい」） */
    public static final float PERCENT_CAP = 1_000_000.0F;

    /**
     * ティア 4 の割合ダメージ（2026-10-24 ユーザー指定「その案で」: ティア 4 から解禁）: 技の大きさが最大 HP の 1% に満たない相手（HP を盛ったプレイヤー・
     * HP が数千〜数万の他 MOD のボス）にだけ、最大 HP の {@link #PERCENT} を上乗せする（1 発 {@link #PERCENT_CAP} まで）。普通の相手には何も足さない。
     * 削るだけで、死亡の取り消しは止めない（死亡を確定させるのはティア 5 の隔絶ダメージ）。上限の型には、この後の「残りの量ぴったり」が優先する
     */
    public static float percentBonus(float amount, float maxHealth) {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0.0F || amount >= maxHealth * 0.01F) return 0.0F;
        return Math.min(PERCENT_CAP, maxHealth * PERCENT);
    }

    /** 上限の間のうち、今までに通った量（測った長さの間） */
    private static float dealtWithin(Profile p, long now, int window) {
        float sum = 0.0F;
        for (Dealt d : p.history) if (now - d.tick < window) sum += d.amount;
        return sum;
    }

    /**
     * victim へ、相手の型に合わせて攻める。返す値は「技が通らなかった数え」に使う大きさ（負なら探り・測れなかったので数えない）
     */
    float strike(ServerLevel level, LivingEntity victim, float amount, int tier) {
        Profile p = profile(victim.getUUID());
        long now = level.getGameTime();
        Player player = victim instanceof Player pl && !pl.isCreative() && !pl.isSpectator() ? pl : null;
        float hpNow = DirectRead.health(victim);

        // 回復の速さを測る: 前の攻撃のあとの HP から今の HP までの増え（他の削りで減っていれば 0 と数える）
        if (p.hpAfter >= 0.0F && p.lastTick >= 0L && now > p.lastTick) {
            float seconds = (now - p.lastTick) / 20.0F;
            float healed = Math.max(0.0F, hpNow - p.hpAfter) / seconds;
            p.healRate = p.healRate * 0.6F + healed * 0.4F;
        }

        // ---- 崩し（ティアの範囲の中で、感知した型にだけ） ----
        // ティア 1〜: 効果の守り（耐性・吸収）を剥がす。回復の効果は、回復していると分かった相手からだけ剥がす
        victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        victim.removeEffect(MobEffects.ABSORPTION);
        victim.setAbsorptionAmount(0.0F);
        if (p.fastRegen() || victim.hasEffect(MobEffects.REGENERATION) && p.healRate > 0.2F) victim.removeEffect(MobEffects.REGENERATION);
        // ティア 3〜: 自然回復で戻す型（回復が速い）にだけ、満腹度を 17 以下にして自然回復を止める
        if (tier >= 3 && player != null && p.fastRegen()) {
            player.getFoodData().setFoodLevel(Math.min(player.getFoodData().getFoodLevel(), 17));
            player.getFoodData().setSaturation(0.0F);
        }
        // ティア 2〜: 不死のトーテムを持っている相手には、先に致死の一撃で使わせる（10 秒に 1 回）
        if (tier >= 2 && player != null && now - p.totemBaitAt > 200L
                && (player.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))) {
            p.totemBaitAt = now;
            victim.invulnerableTime = 0;
            victim.hurt(unknown(UNKNOWN[0], elenor, elenor), hpNow + victim.getAbsorptionAmount() + 1.0F);
        }
        // ティア 2〜: 被弾後の無敵時間と盾の構えを崩す。ティア 1 では崩さないので、無敵時間の最中・盾で防がれた結果は測らない
        boolean measurable = true;
        if (tier >= 2) {
            victim.invulnerableTime = 0;
            if (player != null && player.isBlocking()) player.disableShield(true);
        } else if (victim.invulnerableTime > 10 || player != null && player.isBlocking()) {
            measurable = false;
        }
        // ティア 4: 無敵の印を外す（付いているときだけ）
        if (tier >= 4 && player != null && victim.isInvulnerable()) victim.setInvulnerable(false);

        // ---- 大きさを整える ----
        float shaped = aboveFloor(amount, p.maxFail, p.minPass, p.floorKnown); // 足切りの下には撃たない
        if (p.fastRegen()) shaped *= Math.min(1.6F, 1.0F + p.healRate / Math.max(1.0F, p.dealRate)); // 回復を上回る大きさで
        if (tier >= 4) shaped += percentBonus(amount, victim.getMaxHealth()); // 割合ダメージ（HP を盛った相手にだけ）
        boolean probe = false;
        if (p.capKnown) {
            float used = dealtWithin(p, now, p.window);
            if (used >= p.cap * 0.9F) {
                shaped = Math.max(0.5F, amount * 0.1F); // 使い切っている間は小さな探り（数えない）
                probe = true;
            } else {
                shaped = Math.min(shaped, Math.max(1.0F, p.cap - used)); // 残りの量ぴったりで
            }
        }

        // ティア 4: ラストスタンド（経験値を消費する型）は、止めの前に経験値を空にする
        if (tier >= 4 && p.lastStand && player != null && DirectRead.health(victim) <= shaped * 1.5F && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
            player.giveExperienceLevels(-player.experienceLevel);
            player.experienceProgress = 0.0F;
            player.totalExperience = 0;
        }

        // ---- 当てる ----
        float hp0 = DirectRead.health(victim);
        int xp0 = player != null ? player.totalExperience + player.experienceLevel * 1000 : 0;
        int index = pickSource(p, tier);
        DamageSource chosen = index == SCHOOL ? school(victim) : null;
        boolean accepted = victim.hurt(chosen != null ? chosen : source(index == SCHOOL ? 0 : index), shaped);
        float dealt = Math.max(0.0F, hp0 - DirectRead.health(victim));
        if (index == FARTHEST && ElenorPressure.living(victim) && dealt < shaped * 0.25F) { // 未知の種類が止められたら、バニラで一番強い貫通ダメージ
            victim.invulnerableTime = 0;
            accepted |= victim.hurt(farthest(), shaped);
            dealt = Math.max(0.0F, hp0 - DirectRead.health(victim));
        }
        // ティア 3〜: 1 発あたりの上限の型には、上限の量に分けて続けて当てる（最大 4 発。無敵時間は毎回崩す）
        if (tier >= 3 && p.perHitLimit && p.hitLimit > 0.0F && !probe && ElenorPressure.living(victim)) {
            int extra = Math.min(3, (int) Math.ceil(shaped / p.hitLimit) - 1);
            for (int i = 0; i < extra && ElenorPressure.living(victim); i++) {
                victim.invulnerableTime = 0;
                victim.hurt(source(i % 2 == 0 ? GENERIC_KILL : LIMIT_BREAK), p.hitLimit * 1.05F);
            }
            dealt = Math.max(0.0F, hp0 - DirectRead.health(victim));
        }
        // ティア 3〜: 通らなければ HP を直接減らす（1 は残す。止めは上のティア）。測れた結果のときだけ
        if (tier >= 3 && !probe && measurable && ElenorPressure.living(victim) && dealt < shaped * 0.25F) {
            victim.setHealth(Math.max(1.0F, hp0 - shaped));
            dealt = Math.max(0.0F, hp0 - DirectRead.health(victim));
        }

        if (!measurable) { // 無敵時間・盾の最中（ティア 1）: 調べにも、ティアの数えにも使わない
            p.invalid++;
            p.hpAfter = DirectRead.health(victim);
            p.lastTick = now;
            return -1.0F;
        }
        learn(victim, player, p, index, shaped, dealt, probe, accepted, now, hp0, xp0, tier);
        return probe ? -1.0F : shaped;
    }

    private void learn(LivingEntity victim, @Nullable Player player, Profile p, int index, float shaped, float dealt,
                       boolean probe, boolean accepted, long now, float hp0, int xp0, int tier) {
        p.strikes++;
        p.lastIndex = index;
        float hpAfter = DirectRead.health(victim);
        p.hpAfter = hpAfter;
        float ratio = Math.max(0.0F, Math.min(1.0F, dealt / Math.max(shaped, 1.0e-3F)));
        if (!probe) {
            p.rate[index] = p.tries[index] == 0 ? ratio : p.rate[index] * (1.0F - EMA) + ratio * EMA;
            p.tries[index]++;
            p.totalTries++;
            if (p.lastTick >= 0L && now > p.lastTick) { // エレノアの与える速さ（HP/秒）
                p.dealRate = p.dealRate * 0.6F + dealt / ((now - p.lastTick) / 20.0F) * 0.4F;
            }
        }
        p.lastTick = now;

        // 通った量の記録（上限の間の測りに使う）
        while (!p.history.isEmpty() && now - p.history.peekFirst().tick > HISTORY) p.history.pollFirst();
        if (dealt > 0.0F) p.history.addLast(new Dealt(now, dealt));

        // 1 発あたりの上限: 大きく撃っても、ほぼ同じ量しか通らない（2 回続けて、1 割以内）
        if (!probe && dealt > 0.0F && dealt < shaped * 0.6F && p.lastDealt > 0.0F && Math.abs(dealt - p.lastDealt) <= Math.max(0.5F, p.lastDealt * 0.1F)) {
            if (!p.perHitLimit) notice(player, "cap");
            p.perHitLimit = true;
            p.hitLimit = Math.max(p.hitLimit, Math.max(dealt, p.lastDealt));
        }
        if (!probe) p.lastDealt = dealt;

        // 通らなかった（hurt は受け付けたのに 0）ときの見分け: この大きさがもう通ったことがあれば「上限」（一定の間に通る量を使い切った）、
        // 通ったことのない大きさなら「足切り」（量のせいで消された）
        boolean nothing = dealt <= 1.0e-3F;
        boolean sizePassedBefore = p.minPass < Float.MAX_VALUE && shaped >= p.minPass * 0.95F;
        if (!probe && nothing && accepted && ElenorPressure.living(victim)) {
            if (sizePassedBefore && !p.history.isEmpty()) {
                // 上限: 直前の合計が上限（間の長さは、また通ったときに測る）。2 回で確定
                if (p.blockedSince < 0L) {
                    p.blockedSince = now;
                    float total = dealtWithin(p, now, Math.max(p.window, DEFAULT_WINDOW));
                    if (total > 0.0F) {
                        p.cap = p.capVotes == 0 ? total : Math.min(p.cap, total);
                        if (++p.capVotes >= 2 && !p.capKnown) {
                            p.capKnown = true;
                            notice(player, "cap");
                        }
                    }
                }
            } else if (shaped < victim.getMaxHealth() * 0.5F) {
                // 足切り（二分探索）: 通らなかった一番大きな量と、通った一番小さな量の間を詰める。2 回通らず、境目が 1 割以内なら確定
                p.maxFail = Math.max(p.maxFail, shaped);
                if (++p.floorVotes >= 2 && !p.floorKnown && (p.minPass == Float.MAX_VALUE || p.minPass <= p.maxFail * 1.1F)) {
                    p.floorKnown = true;
                    notice(player, "floor");
                }
            }
        } else if (dealt > 0.0F) {
            if (shaped < p.minPass) {
                p.minPass = shaped;
                if (p.minPass <= p.maxFail) p.maxFail = p.minPass * 0.9F; // 小さくても通るようになった: 境目を下げる
            }
            if (p.blockedSince >= 0L) { // 止まっていたのがまた通った: 予算が戻るまでの長さ（使い切る前の最初の通りから、また通るまで）を測る
                Dealt first = null;
                for (Dealt d : p.history) {
                    if (d.tick < p.blockedSince) {
                        first = d;
                        break;
                    }
                }
                if (first != null) {
                    int measured = (int) Math.max(20L, Math.min(2400L, now - first.tick));
                    p.window = p.capVotes <= 1 ? measured : (int) (p.window * 0.6F + measured * 0.4F);
                }
                p.blockedSince = -1L;
            }
        }

        // ラストスタンド: 致死のはずが生き延び、経験値が減った
        if (player != null && !p.lastStand && hp0 - shaped <= 0.0F && ElenorPressure.living(victim) && hpAfter > 0.0F) {
            int xp1 = player.totalExperience + player.experienceLevel * 1000;
            if (xp1 < xp0) {
                p.lastStand = true;
                notice(player, "laststand");
            }
        }

        // ティア 3〜: 通らなかった型には、数 Tick に分けて削り続ける（小分けの連打。2 回続けて通らなかったら）
        if (tier >= 3 && !probe && dealt < shaped * 0.25F && ElenorPressure.living(victim)) {
            if (++p.denied >= 2) bursts.add(new Burst(victim.getUUID(), BURST_TICKS, shaped / BURST_TICKS));
        } else if (dealt >= shaped * 0.25F) {
            p.denied = 0;
        }
        // ティア 3〜: 継続ダメージは、一撃が通りにくい型（上限・足切り・書き戻し）にだけ、5 秒に 1 回付け直す
        if (tier >= 3 && ElenorPressure.living(victim) && !probe && (p.perHitLimit || p.capKnown || p.floorKnown || p.reverts > 0) && now - p.dotAt >= 100L) {
            p.dotAt = now;
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), elenor);
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1), elenor);
        }
        // ティア 4: 逃げ場を塞ぐ。遠く（8 ブロックより先）にいて、前の攻撃のときより離れていった相手だけ、鈍らせて引き寄せる
        float distance = elenor.distanceTo(victim);
        if (tier >= 4 && player != null && ElenorPressure.living(victim) && distance > 8.0F && p.lastDistance > 0.0F && distance > p.lastDistance + 0.5F) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), elenor);
            net.minecraft.world.phys.Vec3 pull = elenor.position().subtract(victim.position()).normalize().scale(0.8D);
            victim.setDeltaMovement(pull.x, 0.25D, pull.z);
            victim.hurtMarked = true;
        }
        p.lastDistance = distance;

        // 書き戻し: 削った HP が戻っていないかを 1・3・10 Tick 後に見る（ティア 1〜で調べ、分けて削るのはティア 3〜）
        if (dealt > 0.0F) {
            for (int delay : new int[]{1, 3, 10}) checks.add(new Check(victim.getUUID(), now + delay, hpAfter, dealt, delay));
        }
        if (p.reverts > 0 && tier >= 3 && !probe && shaped > 0.0F) {
            int ticks = Math.max(2, Math.min(BURST_TICKS * 2, p.revertDelay * 2)); // 戻すより速く、細かく
            bursts.add(new Burst(victim.getUUID(), ticks, shaped / ticks));
        }
    }

    /** 毎 Tick: 書き戻しの確認と、細かく分けた攻撃 */
    void tick(ServerLevel level) {
        if (checks.isEmpty() && bursts.isEmpty()) return;
        long now = level.getGameTime();
        List<Check> due = new ArrayList<>();
        for (Iterator<Check> it = checks.iterator(); it.hasNext(); ) {
            Check c = it.next();
            if (now < c.due) continue;
            it.remove();
            due.add(c);
        }
        for (Check c : due) {
            if (!(level.getEntity(c.id) instanceof LivingEntity living) || !ElenorPressure.living(living)) continue;
            if (DirectRead.health(living) < c.expectedHp + c.dealt * 0.5F) continue;
            // 戻された: 同じ削りの残りの確認は要らない
            checks.removeIf(other -> other.id.equals(c.id) && other.expectedHp == c.expectedHp && other.dealt == c.dealt);
            Profile p = profile(c.id);
            if (p.reverts++ == 0) notice(living instanceof Player pl ? pl : null, "revert");
            p.revertDelay = p.reverts == 1 ? c.delay : Math.min(p.revertDelay, c.delay);
            // 戻された削りは「通らなかった」と数え直す（ティアの数え）
            ElenorArts arts = elenor.arts();
            if (arts != null) arts.pressure().noteStrikeResult(living, 0.0F, c.dealt);
        }
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            if (b.left-- <= 0 || !(level.getEntity(b.id) instanceof LivingEntity living) || !ElenorPressure.living(living)) {
                it.remove();
                continue;
            }
            living.setHealth(Math.max(1.0F, DirectRead.health(living) - b.perTick));
        }
    }

    /** 調べた結果（{@code /elenor profile}）。調べていなければ空 */
    List<Component> describe(UUID id) {
        List<Component> lines = new ArrayList<>();
        Profile p = profiles.get(id);
        if (p == null) return lines;
        lines.add(Component.literal(String.format(Locale.ROOT, "strikes %d (unmeasurable %d), deal %.2f HP/s, heal %.2f HP/s%s",
                p.strikes, p.invalid, p.dealRate, p.healRate, p.fastRegen() ? " [fast regen]" : "")));
        StringBuilder rates = new StringBuilder("damage types:");
        for (int i = 0; i < SOURCES; i++) {
            if (p.tries[i] > 0) rates.append(String.format(Locale.ROOT, " %s=%.0f%%(%d)", SOURCE_NAMES[i], p.rate[i] * 100.0F, p.tries[i]));
        }
        lines.add(Component.literal(rates.toString()));
        lines.add(Component.literal(String.format(Locale.ROOT, "floor %s (fail<=%.2f, pass>=%s)",
                p.floorKnown ? "known" : p.maxFail > 0 ? "searching" : "-", p.maxFail,
                p.minPass == Float.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", p.minPass))));
        lines.add(Component.literal(String.format(Locale.ROOT, "cap %s (%s per %d ticks, votes %d), per-hit limit %s",
                p.capKnown ? "known" : p.capVotes > 0 ? "suspected" : "-", p.cap == Float.MAX_VALUE ? "-" : String.format(Locale.ROOT, "%.2f", p.cap),
                p.window, p.capVotes, p.perHitLimit ? String.format(Locale.ROOT, "%.2f", p.hitLimit) : "-")));
        lines.add(Component.literal(String.format(Locale.ROOT, "reverts %d (after %d ticks), last stand %s", p.reverts, p.revertDelay, p.lastStand ? "yes" : "no")));
        return lines;
    }

    private void notice(@Nullable Player player, String key) {
        if (player != null) player.displayClientMessage(Component.translatable("message.automata_valkyries.elenor.adapt." + key)
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
    }
}
