package com.automatavalkyries.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ペナルティアタックのティア 1〜4 の、相手の守りを調べて合わせる攻め方（docs/SPEC_ELENOR.md §2.3.3、2026-10-23 ユーザー指定）。
 * 相手（プレイヤー）ごとに「守りの型」を覚え、感知した型に合わせて攻め方を変える:
 * <ul>
 *   <li>有効ダメージの調査: 通ったダメージの割合と、ダメージの種類（通常・魔法・ウィザー・ドラゴンブレス・音波）ごとの通りやすさ。通りやすい種類を選ぶ</li>
 *   <li>足切り（小さい攻撃は 0 にされる）: 通らなかった最小の大きさを覚え、それを超える大きさで攻める</li>
 *   <li>上限（キャップ）: 一定の間に通る量に上限がある型。上限と、その間の長さを覚え、間が空くまでは小さな探りだけにして、空いたら上限に合わせて撃つ（最適 DPS）</li>
 *   <li>条件つき無敵・何かを壊す必要がある型: 盾は構えを崩す、無敵の印は外す、被弾後の無敵時間は無視する</li>
 *   <li>ラストスタンド（経験値を消費して生き延びる型）: 致死のはずが生き延び、経験値が減ったのを感知したら、次の止めの前に経験値を空にして封じる</li>
 *   <li>内部の値を参照する型（HP を書き戻す MOD）: 削った HP が 2 Tick 後に戻ったのを感知したら、数 Tick に分けて削り続けて、書き戻しが追い付かないようにする</li>
 * </ul>
 * 感知した型の数が増えるたびに、技の間隔が縮む（AI ルーチンの強化）。ティア 5 以上は Antigravity の入口を呼ぶ別の道（{@link ElenorPressure}）
 */
final class ElenorAdapt {

    private static final int WINDOW = 200;
    private static final int BURST_TICKS = 6;
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
    /** ティアごとの使える種類（ティア 1 は少なく、上がるほど増える） */
    private static final int[] TIER1 = {0, 1, 5, 15};
    private static final int[] TIER2 = {0, 1, 2, 3, 5, 12, 15};
    private static final int[] TIER3 = {0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 15};

    private static final class Profile {
        final float[] score = new float[SOURCES];
        final int[] tries = new int[SOURCES];
        float floor;
        float cap = Float.MAX_VALUE;
        int capVotes;
        boolean capKnown;
        long windowStart = -1L;
        float windowDealt;
        int reverts;
        boolean lastStand;
        boolean fastRegen;
        /** 1 発あたりに通る量に上限がある型（Cataclysm のボスなど）。無敵を貫く種類でないと、この上限が外れない */
        boolean perHitLimit;
        float lastDealt = -1.0F;
        int denied;
        int lastIndex = -1;
        float lastHp = -1.0F;
        long lastTick;
        long totemBaitAt = -100L;
        int strikes;
    }

    private record Check(UUID id, long due, float expectedHp, float dealt) {}

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
        if (p.floor > 0.0F) n++;
        if (p.reverts > 0) n++;
        if (p.lastStand) n++;
        if (p.fastRegen) n++;
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

    /**
     * 最果てダメージ（ティア 4）: バニラで作れる一番強い貫通ダメージ。`generic_kill` の種類で、エレノアからの攻撃として当てる。
     * 無敵（無敵の印・クリエイティブ以外の無敵）・防具・耐性の効果・盾・不死のトーテムを素通りする。
     * 通る量は攻撃の大きさのまま（大きさそのものは、ここでは変えない）
     */
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

    private DamageSource farthest() {
        var type = elenor.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        return new DamageSource(type, elenor);
    }

    /**
     * 未知のダメージ属性: このMOD のデータで作った、エレノアだけのダメージの種類。他の MOD の守り（種類の名前・既知の一覧で見分ける型）に載っておらず、
     * 種類のタグ（防具・効果・耐性・エンチャント・盾・無敵時間・無敵）を組み合わせて貫く。無ければ（データが読めない）null → 呼び出し側でバニラに戻す
     */
    private DamageSource unknown(String name, @Nullable net.minecraft.world.entity.Entity direct, @Nullable net.minecraft.world.entity.Entity causing) {
        var registry = elenor.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.automatavalkyries.AutomataValkyries.MODID, name));
        var holder = registry.getHolder(key);
        return holder.isPresent() ? new DamageSource(holder.get(), direct, causing) : farthest();
    }

    private int pickSource(Profile p, int tier) {
        if (tier >= 4) return p.lastIndex == FARTHEST ? GENERIC_KILL : FARTHEST; // 同じ種類を続けず、最果てとバニラの貫通を交互に
        // 1 発あたりの上限の型（ティア 3〜）: 無敵を貫く種類（縛め・バニラの generic_kill）を交互に使う。他の種類では上限が外れない
        if (tier >= 3 && (p.perHitLimit || p.capKnown)) return p.lastIndex == LIMIT_BREAK ? GENERIC_KILL : LIMIT_BREAK;
        int[] pool = tier <= 1 ? TIER1 : tier == 2 ? TIER2 : TIER3;
        int explore = pool[(p.strikes / 5) % pool.length];
        if (p.strikes % 5 == 4 && explore != p.lastIndex) return explore; // 5 回に 1 回は他の種類も試す
        int best = -1;
        float bestRate = -1.0F;
        for (int i : pool) {
            if (i == p.lastIndex) continue; // 連鎖の切り替え: 直前と同じ種類は続けない（同じ種類を一度だけ無効にする型への備え）
            float rate = p.tries[i] == 0 ? 0.5F : p.score[i] / p.tries[i];
            if (rate > bestRate + 1.0e-4F) {
                bestRate = rate;
                best = i;
            }
        }
        return best < 0 ? pool[0] : best;
    }

    /**
     * victim へ、相手の型に合わせて攻める。返す値は「技が通らなかった数え」に使う大きさ（負なら探りだけなので数えない）
     */
    float strike(ServerLevel level, LivingEntity victim, float amount, int tier) {
        Profile p = profile(victim.getUUID());
        long now = level.getGameTime();
        Player player = victim instanceof Player pl && !pl.isCreative() && !pl.isSpectator() ? pl : null;

        // 回復の妨害（ティア 1〜）と、相手の回復の速さの測定
        victim.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        if (p.lastHp >= 0.0F && now > p.lastTick) {
            double perSecond = (victim.getHealth() - p.lastHp) / ((now - p.lastTick) / 20.0D);
            if (perSecond > amount * 0.3D) p.fastRegen = true;
        }
        if (tier >= 3 && player != null) { // 満腹度で回復する型を止める（17 以下では自然回復しない）
            player.getFoodData().setFoodLevel(Math.min(player.getFoodData().getFoodLevel(), 17));
            player.getFoodData().setSaturation(0.0F);
        }
        // 不死のトーテムの先抜き（ティア 2〜）: 先に致死の一撃を当てて使わせる。トーテムを持っているときだけ
        if (tier >= 2 && player != null && now - p.totemBaitAt > 20L
                && (player.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))) {
            p.totemBaitAt = now;
            victim.invulnerableTime = 0;
            victim.hurt(unknown(UNKNOWN[0], elenor, elenor), victim.getHealth() + victim.getAbsorptionAmount() + 1.0F);
        }

        // 守りを崩す: 効果、盾の構え、被弾後の無敵時間、無敵の印
        victim.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        victim.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        victim.setAbsorptionAmount(0.0F);
        if (tier >= 2) {
            victim.invulnerableTime = 0;
            if (player != null && player.isBlocking()) player.disableShield(true);
        }
        if (tier >= 4 && player != null) victim.setInvulnerable(false);

        // 大きさを整える: 足切りの下には撃たず、上限の間は小さな探りだけにする
        float shaped = amount;
        if (p.floor > shaped) shaped = Math.min(p.floor, amount * 8.0F);
        if (p.fastRegen) shaped *= 1.25F; // 回復の速い型には、回復を上回る大きさで
        boolean probe = false;
        if (p.capKnown) {
            if (now - p.windowStart < WINDOW) {
                shaped = amount * 0.15F;
                probe = true;
            } else {
                shaped = Math.min(shaped, p.cap * 1.1F);
                p.windowStart = now;
                p.windowDealt = 0.0F;
            }
        }

        // ラストスタンド（経験値を消費する型）は、止めの前に経験値を空にする
        if (tier >= 4 && p.lastStand && player != null && victim.getHealth() <= shaped * 1.5F && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
            player.giveExperienceLevels(-player.experienceLevel);
            player.experienceProgress = 0.0F;
            player.totalExperience = 0;
        }

        float hp0 = victim.getHealth();
        int xp0 = player != null ? player.totalExperience + player.experienceLevel * 1000 : 0;
        int index = pickSource(p, tier);
        DamageSource chosen = index == SCHOOL ? school(victim) : null;
        victim.hurt(chosen != null ? chosen : source(index == SCHOOL ? 0 : index), shaped);
        float dealt = hp0 - victim.getHealth();
        if (index == FARTHEST && victim.isAlive() && dealt < shaped * 0.25F) { // 未知の種類が止められたら、バニラで一番強い貫通ダメージ
            victim.hurt(farthest(), shaped);
            dealt = hp0 - victim.getHealth();
        }

        // 通らなければ HP を直接減らす（1 は残す。止めは上のティア）
        if (tier >= 3 && !probe && victim.isAlive() && dealt < shaped * 0.25F) {
            victim.setHealth(Math.max(1.0F, hp0 - shaped));
            dealt = hp0 - victim.getHealth();
        }

        learn(level, victim, player, p, index, shaped, dealt, probe, now, hp0, xp0, tier);
        return probe ? -1.0F : shaped;
    }

    private void learn(ServerLevel level, LivingEntity victim, @Nullable Player player, Profile p, int index, float shaped, float dealt,
                       boolean probe, long now, float hp0, int xp0, int tier) {
        p.strikes++;
        p.lastIndex = index;
        p.lastHp = victim.getHealth();
        p.lastTick = now;
        p.tries[index]++;
        p.score[index] += Math.max(0.0F, Math.min(1.0F, dealt / Math.max(shaped, 1.0e-3F)));

        // 1 発あたりの上限: 大きく撃っても、ほぼ同じ量しか通らない（2 回続けて）
        if (!probe && dealt > 0.0F && dealt < shaped * 0.5F && p.lastDealt > 0.0F && Math.abs(dealt - p.lastDealt) <= Math.max(0.5F, p.lastDealt * 0.1F)) {
            if (!p.perHitLimit) notice(player, "cap");
            p.perHitLimit = true;
        }
        if (!probe) p.lastDealt = dealt;

        // 足切り: 0 だったら、その大きさより大きく
        if (!probe && dealt <= 1.0e-3F && shaped < victim.getMaxHealth() * 0.5F && victim.isAlive()) {
            boolean first = p.floor <= 0.0F;
            p.floor = Math.max(p.floor, shaped * 1.6F);
            if (first) notice(player, "floor");
        }

        // 上限: ある間に通った量の後で通らなくなったら票を入れる。2 回で確定
        if (p.windowStart < 0L || now - p.windowStart >= WINDOW) {
            p.windowStart = now;
            p.windowDealt = 0.0F;
        }
        if (!p.capKnown) {
            if (dealt > 0.0F) p.windowDealt += dealt;
            else if (p.windowDealt > 0.0F && now - p.windowStart < WINDOW && shaped > p.windowDealt * 0.1F) {
                p.capVotes++;
                p.cap = Math.max(p.windowDealt, 1.0F);
                if (p.capVotes >= 2) {
                    p.capKnown = true;
                    notice(player, "cap");
                }
            }
        }

        // ラストスタンド: 致死のはずが生き延び、経験値が減った
        if (player != null && !p.lastStand && hp0 - shaped <= 0.0F && victim.isAlive() && victim.getHealth() > 0.0F) {
            int xp1 = player.totalExperience + player.experienceLevel * 1000;
            if (xp1 < xp0) {
                p.lastStand = true;
                notice(player, "laststand");
            }
        }

        // 通らなかった型には、直接減らすだけでなく、数 Tick に分けて削り続ける（小分けの連打。ティア 3〜）
        if (tier >= 3 && !probe && dealt < shaped * 0.25F && victim.isAlive() && ++p.denied >= 2) {
            bursts.add(new Burst(victim.getUUID(), BURST_TICKS, shaped / BURST_TICKS));
        }
        // 継続ダメージ（ティア 3〜）: 一撃の上限・足切りの型にも、時間をかけて削る
        if (tier >= 3 && victim.isAlive() && !probe) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1), elenor);
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 1), elenor);
        }
        // 逃げ場を塞ぐ（ティア 4）: 遠くにいる相手は動きを鈍らせて、エレノアの方へ引き寄せる
        if (tier >= 4 && player != null && victim.isAlive() && elenor.distanceTo(victim) > 8.0F) {
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 2), elenor);
            net.minecraft.world.phys.Vec3 pull = elenor.position().subtract(victim.position()).normalize().scale(0.8D);
            victim.setDeltaMovement(pull.x, 0.25D, pull.z);
            victim.hurtMarked = true;
        }

        // 内部の値を参照する型: 2 Tick 後に HP が戻っていないかを見る
        if (dealt > 0.0F && tier >= 3) {
            checks.add(new Check(victim.getUUID(), now + 2L, victim.getHealth(), dealt));
        }
        if (p.reverts > 0 && tier >= 3 && !probe && shaped > 0.0F) {
            bursts.add(new Burst(victim.getUUID(), BURST_TICKS, shaped / BURST_TICKS));
        }
    }

    /** 毎 Tick: 書き戻しの確認と、細かく分けた攻撃 */
    void tick(ServerLevel level) {
        if (checks.isEmpty() && bursts.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Check> it = checks.iterator(); it.hasNext(); ) {
            Check c = it.next();
            if (now < c.due) continue;
            it.remove();
            if (level.getEntity(c.id) instanceof LivingEntity living && living.isAlive() && living.getHealth() >= c.expectedHp + c.dealt * 0.5F) {
                Profile p = profile(c.id);
                if (p.reverts++ == 0) notice(living instanceof Player pl ? pl : null, "revert");
            }
        }
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            if (b.left-- <= 0 || !(level.getEntity(b.id) instanceof LivingEntity living) || !living.isAlive()) {
                it.remove();
                continue;
            }
            living.setHealth(Math.max(1.0F, living.getHealth() - b.perTick));
        }
    }

    private void notice(@Nullable Player player, String key) {
        if (player != null) player.displayClientMessage(Component.translatable("message.automata_valkyries.elenor.adapt." + key)
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
    }
}
