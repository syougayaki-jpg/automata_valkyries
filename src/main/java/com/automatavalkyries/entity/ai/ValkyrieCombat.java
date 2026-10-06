package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Emote;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.entity.ValkyrieOrders;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.registry.ModWeapons;
import com.automatavalkyries.weapon.ChargedWeaponItem;
import com.automatavalkyries.weapon.Fx;
import com.automatavalkyries.weapon.ValkyrieArts;
import com.automatavalkyries.weapon.ValkyrieArts.Art;
import com.automatavalkyries.weapon.ValkyrieArts.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 戦術兵姫の戦闘 AI（段階 3、2026-10-05。docs/SPEC_VALKYRIE_GUI_AI.md §3・作戦指示の交戦規定 §2.4）。
 * <ul>
 *   <li>狙う相手（{@link Targeting}）: 交戦の方針（積極的 = 敵対モブ全部 / 反撃 = 自分・持ち主・味方を攻撃した相手だけ / 非戦闘）と狙う優先順。
 *       他のプレイヤーはサーバーの PvP 設定が有効なときだけ、自分や持ち主を攻撃した相手を狙う。味方（持ち主・同じ持ち主の兵姫・持ち主のペット）は狙わない。
 *       ほかの味方の兵姫・持ち主が狙っている相手を優先する（同じ敵を狙う連携）</li>
 *   <li>戦い方（{@link Fight}）: 持った兵装の型（{@link ValkyrieArts}）で、近接は詰めて斬り、射撃は「保つ距離」を取って撃つ（前衛がいればその後ろに立つ）。
 *       技は間合いと間隔を見て出し、溜め撃ちはアイテムの使用で溜めて離す。弾・電力切れが続いたら、指示に従って近接へ移る</li>
 *   <li>機動（{@link Brain}）: スラスターの短いダッシュ（詰める・下がる）、飛んできた矢・弾の横ステップ（確率付き。ステップは前後左右の向きごとに 5 秒のクールタイムで、最中は次をしない）、
 *       射撃中のジャンプからの滞空射撃。どれも兵姫の電力を少し使う【仮】</li>
 *   <li>後退（{@link Retreat}）: HP・電力が指示の基準を割ったら、相手から離れて持ち主・拠点の方へ下がり、弾薬庫のナノマシン溶液で自己修理する</li>
 *   <li>かばう（{@link Guard}）: 盾持ち（盾・偏光シールド・イージス）だけ。持ち主が瀕死（HP 20% 以下）のとき、持ち主と敵の間に出て障壁を張り「絶対障壁、展開！」</li>
 *   <li>味方への攻撃（{@link #onAttack}）: 兵姫の攻撃（飛び道具・範囲攻撃も）は持ち主と味方に当たらず、持ち主・味方の攻撃も兵姫に当たらない</li>
 * </ul>
 * 数値（間隔・電力・確率）は【仮】。背部ユニット（性能の違い）・攻城の防衛円陣・タレット優先は、その物ができてから
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieCombat {

    /** 持ち主が瀕死とみなす HP の割合（盾持ちがかばう） */
    public static final float OWNER_CRITICAL = 0.2F;
    /** ダッシュ・横ステップ・滞空の電力（FE）【仮】 */
    public static final int DASH_FE = 500, DODGE_FE = 300, HOVER_FE_PER_TICK = 20;
    /** 横ステップの確率・間隔、ダッシュの間隔、滞空の長さ（Tick）【仮】 */
    public static final float DODGE_CHANCE = 0.6F;
    public static final int HOVER_TICKS = 16, HOVER_COOLDOWN = 200;
    /** スラスターの空中ダッシュ・空中ジャンプを続けて出す間（Tick）【仮】 */
    public static final int AIR_GAP = 12;
    /** 電力で射撃の兵装から近接の予備へ持ち替える・戻す割合【仮】 */
    public static final float SWAP_LOW = 0.3F, SWAP_BACK = 0.6F;
    /**
     * ステップ（ダッシュ・横ステップ）: 向き（前・後ろ・左・右、体の向きから見て）ごとのクールタイムと、1 回のステップの長さ（Tick）。
     * ステップの最中（この長さの間と、着地するまで）は次のステップをしない（2026-10-06 ユーザー指定「ステップが多すぎ、前後左右それぞれ 5 秒ほど」）
     */
    public static final int STEP_COOLDOWN = 100, STEP_TICKS = 12;

    /** ステップの向き（体の向きから見て） */
    enum StepDir { FORWARD, BACK, LEFT, RIGHT }
    /** 待機中に相手を探す半径・拠点の外へ追ってよい幅 */
    private static final double WAIT_SCAN = 16.0, HOME_MARGIN = 8.0;
    /** セリフの間隔（Tick） */
    private static final int ENGAGE_LINE_COOLDOWN = 1200, BARRIER_LINE_COOLDOWN = 600, DOWN_LINE_COOLDOWN = 400;

    private ValkyrieCombat() {}

    // ------------------------------------------------------------------ 敵味方

    /** その者の陣営（持ち主の UUID）。プレイヤーは自分、兵姫・ペットは持ち主、飛び道具は撃った者の陣営。無ければ null */
    @Nullable
    public static UUID side(@Nullable Entity e) {
        for (int depth = 0; e != null && depth < 4; depth++) {
            if (e instanceof ValkyrieEntity v) return v.getOwnerUUID();
            if (e instanceof Player p) return p.getUUID();
            if (e instanceof OwnableEntity o) return o.getOwnerUUID();
            if (!(e instanceof LivingEntity)) { // 飛び道具（他 MOD の弾も。ProjectileLike）は撃った者の陣営
                Entity owner = com.automatavalkyries.weapon.ProjectileLike.owner(e);
                if (owner == null || owner == e) return null;
                e = owner;
                continue;
            }
            return null;
        }
        return null;
    }

    /** 兵姫から見て味方か（持ち主・同じ持ち主の兵姫・持ち主のペット・自分） */
    public static boolean isAlly(ValkyrieEntity v, @Nullable Entity e) {
        if (e == null) return false;
        if (e == v) return true;
        UUID owner = v.getOwnerUUID();
        return owner != null && owner.equals(side(e));
    }

    /** 撃った者・殴った者の大もと（飛び道具なら撃った者） */
    @Nullable
    private static Entity root(@Nullable Entity e) {
        for (int depth = 0; e != null && !(e instanceof LivingEntity) && depth < 4; depth++) {
            Entity owner = com.automatavalkyries.weapon.ProjectileLike.owner(e);
            if (owner == null || owner == e) return e;
            e = owner;
        }
        return e;
    }

    /** 味方への攻撃を止める: 兵姫の攻撃は持ち主・味方に当たらず、持ち主・味方の攻撃は兵姫に当たらない */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity attacker = root(event.getSource().getEntity());
        if (attacker == null) attacker = root(event.getSource().getDirectEntity());
        if (attacker == null) return;
        boolean valkyrieInvolved = attacker instanceof ValkyrieEntity || victim instanceof ValkyrieEntity;
        if (!valkyrieInvolved) return;
        if (attacker == victim) {
            if (victim instanceof ValkyrieEntity) event.setCanceled(true); // 自分の範囲攻撃に巻き込まれない
            return;
        }
        UUID a = side(attacker), b = side(victim);
        if (a != null && a.equals(b)) event.setCanceled(true);
    }

    /** オメガコアの兵姫の与えるダメージは 3 倍（全ステータス 3 倍。兵装の技・飛び道具・近接すべて） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = root(event.getSource().getEntity());
        if (attacker == null) attacker = root(event.getSource().getDirectEntity());
        if (attacker instanceof ValkyrieEntity v && v.hasOmegaCore() && event.getEntity() != v) {
            event.setAmount(event.getAmount() * ValkyrieEntity.OMEGA_MULTIPLIER);
        }
        // 近接のステップの封印を解く: 狙っている相手を一度殴った / 狙っている相手以外のモブか、飛び道具に撃たれた
        if (attacker instanceof ValkyrieEntity v && event.getEntity() == v.getTarget()) v.combat().stepLocked = false;
        if (event.getEntity() instanceof ValkyrieEntity v) {
            boolean ranged = event.getSource().getDirectEntity() instanceof Projectile
                    || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
            if (ranged || (attacker instanceof LivingEntity && attacker != v.getTarget())) v.combat().stepLocked = false;
        }
    }

    // ------------------------------------------------------------------ 味方へのデバフの無効（2026-10-06 ユーザー指定「戦術兵姫のデバフをマスターが食らわないように」）

    /** いま動いている兵姫（AI・手の兵装の毎 Tick・その兵姫が予約した遅れの処理・その兵姫の飛び道具の着弾の間。サーバーのスレッドだけ） */
    @Nullable private static ValkyrieEntity acting;

    @Nullable
    public static ValkyrieEntity acting() {
        return acting;
    }

    /** いま動いている兵姫を決める（前の値を返す。戻すときにもう一度呼ぶ） */
    @Nullable
    public static ValkyrieEntity setActing(@Nullable ValkyrieEntity v) {
        ValkyrieEntity prev = acting;
        acting = v;
        return prev;
    }

    /** いま動いている兵姫の味方（持ち主・同じ持ち主の兵姫・ペット。本人は除く）か: 兵装のデバフを受けない */
    public static boolean shieldedFromActing(LivingEntity target) {
        ValkyrieEntity v = acting;
        return v != null && target != v && !target.level().isClientSide && isAlly(v, target);
    }

    /** 兵姫が動いている間に味方へ付こうとした悪い効果を断る */
    @SubscribeEvent
    public static void onEffectApplicable(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL && shieldedFromActing(event.getEntity())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** 付け元が味方の兵姫と分かる悪い効果は、付いた直後（次の Tick）に外す（いま動いている兵姫の外で付いたもの） */
    @SubscribeEvent
    public static void onEffectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !(target.level() instanceof ServerLevel level)) return;
        var effect = event.getEffectInstance().getEffect();
        if (effect.getCategory() != net.minecraft.world.effect.MobEffectCategory.HARMFUL) return;
        Entity source = root(event.getEffectSource());
        if (source instanceof ValkyrieEntity v && source != target && isAlly(v, target)) {
            com.automatavalkyries.weapon.WeaponUtil.later(level, 1, () -> target.removeEffect(effect));
        }
    }

    /** 兵姫の飛び道具の着弾の間は、その兵姫が動いている扱い（その Tick の終わりまで） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!event.getProjectile().level().isClientSide && root(event.getProjectile()) instanceof ValkyrieEntity v) acting = v;
    }

    @SubscribeEvent
    public static void onServerTickEnd(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) acting = null;
    }

    // ------------------------------------------------------------------ 兵姫ごとの戦闘の状態

    /** 兵姫ごとの戦闘の状態（ダッシュ・横ステップ・滞空・セリフの間隔）。毎 Tick {@link #tick} を兵姫から呼ぶ */
    public static final class Brain {
        final ValkyrieEntity v;
        int dodgeScan, hoverTicks, hoverCooldown, stepTicks;
        /** ステップの最中に地面を離れたか（着地するまでステップの最中） */
        boolean stepAirborne;
        /** タキオンドライブのタキオン化をまた使える時刻（ゲーム時刻） */
        public long tachyonReadyAt;
        /** タレットで射程に入って足を止めている（ステップ・回避・空中ダッシュをしない） */
        boolean anchored;
        /** スラスター: 着地してから使った空中ダッシュ・空中ジャンプの回数と、次を出すまでの間（Tick） */
        int airDashes, airJumps, airGap;
        /** 向きごとのステップのクールタイム（{@link StepDir} の順） */
        final int[] stepCooldown = new int[StepDir.values().length];
        long lastEngageLine = -ENGAGE_LINE_COOLDOWN, lastBarrierLine = -BARRIER_LINE_COOLDOWN, lastDownLine = -DOWN_LINE_COOLDOWN;
        int lastDodged = -1;
        /** 後退中か */
        boolean retreating;
        /**
         * 近接で相手を見つけたら、一度殴るか、ほかのモブ・飛び道具から攻撃を受けるまでステップしない（2026-10-06 ユーザー指定
         * 「近接だとまだ変な方向にステップするので、敵を認識したら目線をその方に固定して、一度殴るか他のモブまたは遠距離攻撃を食らうまでステップしない」）
         */
        boolean stepLocked;
        @Nullable LivingEntity lockedOn;

        /** 戦う相手が決まった（近接なら、新しい相手ごとにステップを封じる） */
        void engage(LivingEntity target, boolean melee) {
            if (target != lockedOn) {
                lockedOn = target;
                stepLocked = melee;
            } else if (!melee) {
                stepLocked = false;
            }
        }

        public Brain(ValkyrieEntity v) {
            this.v = v;
        }

        /** 毎 Tick（サーバー、稼働中だけ）: 間隔を数え、滞空を保ち、飛んできた物を避ける */
        public void tick() {
            for (int i = 0; i < stepCooldown.length; i++) if (stepCooldown[i] > 0) stepCooldown[i]--;
            if (stepTicks > 0) {
                stepTicks--;
                if (!v.onGround()) stepAirborne = true;
            } else if (stepAirborne && v.onGround()) {
                stepAirborne = false;
            }
            if (airGap > 0) airGap--;
            if (v.onGround()) {
                airDashes = 0;
                airJumps = 0;
                airHoverUsed = 0;
            } else if (maneuver() != null && v.getDeltaMovement().y < -0.6 && hoverTicks == 0 && spend(HOVER_FE_PER_TICK)) {
                // スラスターで落ちる速さを抑えて降りる（高い所からの着地で落下ダメージを受けない）
                Vec3 d = v.getDeltaMovement();
                v.setDeltaMovement(d.x, -0.6, d.z);
                v.fallDistance = 0.0F;
                if (v.tickCount % 3 == 0) thrust(v.position(), 1);
            }
            if (dodgeScan > 0) dodgeScan--;
            if (hoverCooldown > 0) hoverCooldown--;
            if (hoverTicks > 0) {
                hoverTicks--;
                if (!v.onGround() && airHoverUsed < 100000) airHoverUsed++;
                if (spend(HOVER_FE_PER_TICK)) {
                    Vec3 d = v.getDeltaMovement();
                    v.setDeltaMovement(d.x * 0.85, Math.max(d.y * 0.5, 0.0), d.z * 0.85);
                    v.fallDistance = 0.0F;
                    if (v.tickCount % 2 == 0) thrust(v.position(), 2);
                } else {
                    hoverTicks = 0;
                }
            }
            if (dodgeScan == 0 && !stepping() && !anchored && v.tickCount % 2 == 0) dodge();
            if (v.tickCount % 20 == 0) swapForPower();
            if (v.tickCount % 4 == 0) rangedShield();
            ValkyrieUnitEffects.tick(v, this); // 外装・背部ユニット・ロジックチップ
        }

        /** ECM・アブソリュートの間隔 */
        int ecmCooldown, absoluteCooldown;

        /** 遠距離を防ぐ盾の間隔・構えている残り（バニラの盾） */
        int shieldCooldown, shieldRaised;

        /**
         * 遠距離を防ぐ盾は、遠距離攻撃されるときだけ張る（2026-10-06 ユーザー指定）: 自分か持ち主へ向かって飛んでくる矢・弾があるか、
         * 自分・持ち主・味方を狙って弓などを構えている相手がいるとき。左手の偏光シールド・イージスを張り、バニラの盾は構える。来なくなったら下ろす
         */
        private void rangedShield() {
            if (!(v.level() instanceof ServerLevel level)) return;
            if (shieldCooldown > 0) shieldCooldown -= 4;
            ItemStack off = v.getOffhandItem();
            if (!rangedShieldItem(off)) return;
            boolean threat = rangedThreat(v);
            if (off.getItem() instanceof ShieldItem) {
                if (threat) {
                    if (!v.isUsingItem()) v.startUsingItem(InteractionHand.OFF_HAND);
                    shieldRaised = 30;
                } else if (shieldRaised > 0 && (shieldRaised -= 4) <= 0 && v.isUsingItem() && v.getUsedItemHand() == InteractionHand.OFF_HAND) {
                    v.stopUsingItem();
                }
                return;
            }
            Art art = ValkyrieArts.of(off);
            if (threat && shieldCooldown <= 0 && art != null && art.skill() != null && (!v.isUsingItem() || v.getUseItem() == off)) {
                art.skill().run(level, v, off, v.getTarget());
                shieldCooldown = 20;
            }
        }

        /** 電力で持ち替えた予備の武器の枠（-1 = 持ち替えていない） */
        int swappedSlot = -1;

        public int swappedSlotForTest() {
            return swappedSlot;
        }

        /**
         * 電力が {@link #SWAP_LOW} を割ったら、射撃の兵装を持ち物の予備の近接の兵装と持ち替え、{@link #SWAP_BACK} まで戻ったら元へ戻す
         * （2026-10-06 ユーザー指定「電力低下で遠距離から近距離の武器に持ち替える」。射撃は 1 発ごとの電力が大きいため）
         */
        private void swapForPower() {
            if (v.isUsingItem()) return;
            float power = v.getEnergy() / (float) ValkyrieEntity.MAX_ENERGY;
            ItemStack main = v.getMainHandItem();
            if (swappedSlot < 0 && power < SWAP_LOW && ranged(main)) {
                swapToMelee(true);
            } else if (swappedSlot >= 0 && power >= SWAP_BACK && !forAmmo) {
                swapBack();
            } else if (swappedSlot >= 0 && forAmmo && v.level().getGameTime() >= ammoSwapUntil && power >= SWAP_BACK) {
                swapBack(); // 弾切れで持ち替えてから 30 秒たった: 射撃へ戻ってみる（まだ撃てなければまた持ち替える）
            }
        }

        /** 弾切れで持ち替えたか（電力ではなく）・射撃へ戻ってみる時刻 */
        boolean forAmmo;
        long ammoSwapUntil;

        /** 射撃の兵装を持ち物の予備の近接の兵装と持ち替える（power = 電力で、でなければ弾切れで）。持ち替えたら true */
        public boolean swapToMelee(boolean power) {
            if (swappedSlot >= 0 || v.isUsingItem() || !ranged(v.getMainHandItem())) return false;
            var inv = v.getInventory();
            ItemStack main = v.getMainHandItem();
            for (int i = ValkyrieInventory.SPARE; i < ValkyrieInventory.SPARE + ValkyrieInventory.SPARES; i++) {
                ItemStack spare = inv.getItem(i);
                if (spare.isEmpty() || ranged(spare) || !(spare.getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem w)) continue;
                if (w.twoHanded() && !v.getOffhandItem().isEmpty()) continue;
                inv.setItem(i, main.copy());
                inv.setItem(ValkyrieInventory.MAIN_HAND, spare.copy());
                swappedSlot = i;
                forAmmo = !power;
                ammoSwapUntil = v.level().getGameTime() + 600;
                if (power) v.talk("low_power");
                v.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.7F, 1.2F);
                return true;
            }
            return false;
        }

        /** 持ち替えた予備の射撃の兵装へ戻す */
        private void swapBack() {
            var inv = v.getInventory();
            ItemStack main = v.getMainHandItem();
            ItemStack back = inv.getItem(swappedSlot);
            if (!back.isEmpty() && ranged(back) && back.getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem w
                    && (!w.twoHanded() || v.getOffhandItem().isEmpty())) {
                inv.setItem(swappedSlot, main.copy());
                inv.setItem(ValkyrieInventory.MAIN_HAND, back.copy());
                v.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.7F, 1.0F);
            }
            swappedSlot = -1;
            forAmmo = false;
        }

        private static boolean ranged(ItemStack stack) {
            Art art = ValkyrieArts.of(stack);
            return art != null && art.style() != Style.MELEE && !bladeOf(stack);
        }

        /** 兵姫の電力を使う（休眠の手前は残す）。使えたら true */
        boolean spend(int fe) {
            if (v.getEnergy() < fe + ValkyrieEntity.WAKE_ENERGY) return false;
            v.setEnergy(v.getEnergy() - fe);
            return true;
        }

        /** スラスターの噴射の光 */
        void thrust(Vec3 at, int count) {
            if (v.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.1, at.z, count, 0.15, 0.05, 0.15, 0.02);
                server.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 1, 0.1, 0.02, 0.1, 0.01);
            }
        }

        /** ステップの最中か（ステップの長さの間と、ステップで跳んでから着地するまで） */
        boolean stepping() {
            return stepTicks > 0 || stepAirborne;
        }

        /** 水平の向き dir が、体の向きから見て前・後ろ・左・右のどれか */
        StepDir classify(Vec3 dir) {
            float yaw = v.yBodyRot * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // 前
            double forward = dir.x * fx + dir.z * fz;
            double right = dir.x * -fz + dir.z * fx;           // 右（前を時計回りに 90°）
            if (Math.abs(forward) >= Math.abs(right)) return forward >= 0 ? StepDir.FORWARD : StepDir.BACK;
            return right >= 0 ? StepDir.RIGHT : StepDir.LEFT;
        }

        /** その向きへステップできるか（最中でなく、封じられておらず、その向きのクールタイムが明けている） */
        boolean canStep(Vec3 dir) {
            return !stepping() && !stepLocked && !anchored && stepCooldown[classify(dir).ordinal()] == 0;
        }

        /** ステップ（水平の向き dir へ、強さ power、上へ up）。できたら true。その向きのクールタイムを始める */
        boolean step(Vec3 dir, double power, double up, int fe, float pitch) {
            Vec3 h = new Vec3(dir.x, 0, dir.z);
            if (h.lengthSqr() < 1.0e-4 || !canStep(h) || !spend(Math.round(fe * ValkyrieUnitEffects.boostCostScale(v)))) return false;
            StepDir d = classify(h);
            stepCooldown[d.ordinal()] = STEP_COOLDOWN;
            v.playStep(d.ordinal()); // ステップの動き（モデル）
            h = h.normalize().scale(power);
            v.setDeltaMovement(h.x, up, h.z);
            v.hasImpulse = true;
            stepTicks = STEP_TICKS;
            stepAirborne = false;
            thrust(v.position(), 8);
            v.playSound(ValkyrieEntity.sound("boost"), 0.7F, pitch);
            return true;
        }

        /** 短いダッシュ（地上から、水平の向き dir へ。スラスターがあれば空中でも）。できたら true */
        boolean dash(Vec3 dir, double power) {
            if (v.onGround()) return step(dir, power, dir.y > 2.0 ? 0.45 : 0.18, DASH_FE, 1.1F);
            return airDash(dir, power);
        }

        /** 背負っているスラスターの機動（無ければ null） */
        @Nullable
        com.automatavalkyries.item.unit.ValkyrieUnits.Maneuver maneuver() {
            return ValkyrieUnitEffects.maneuver(v);
        }

        private int thrusterFe() {
            var k = ValkyrieUnitEffects.backpack(v);
            return Math.round((k != null ? k.fe : DASH_FE) * ValkyrieUnitEffects.boostCostScale(v));
        }

        /**
         * ハイマニューバスラスターの空中ダッシュ（相手が上なら上へ）。着地までスラスターの回数だけ続けて出せ、向きごとのクールタイムは掛けない
         * （2026-10-06 ユーザー指定「A」）。続けて出すときは少し間を空ける
         */
        public boolean airDash(Vec3 dir, double power) {
            var m = maneuver();
            Vec3 h = new Vec3(dir.x, 0, dir.z);
            if (m == null || v.onGround() || stepLocked || anchored || airGap > 0 || airDashes >= m.airDashes() || h.lengthSqr() < 1.0e-4 || !spend(thrusterFe())) return false;
            airDashes++;
            airGap = AIR_GAP;
            StepDir d = classify(h);
            v.playStep(d.ordinal());
            h = h.normalize().scale(power);
            v.setDeltaMovement(h.x, dir.y > 1.0 ? 0.5 : dir.y < -1.0 ? -0.2 : 0.12, h.z);
            v.hasImpulse = true;
            v.fallDistance = 0.0F;
            stepTicks = STEP_TICKS;
            thrust(v.position(), 10);
            wingBurst(0x7FE8FF);
            v.playSound(ValkyrieEntity.sound("boost"), 0.7F, 1.3F + 0.1F * airDashes);
            return true;
        }

        /** 空中ジャンプ（空中でもう一度上へ跳ぶ）。着地までスラスターの回数だけ。できたら true */
        public boolean airJump() {
            var m = maneuver();
            if (m == null || v.onGround() || airGap > 0 || airJumps >= m.airJumps() || !spend(thrusterFe())) return false;
            airJumps++;
            airGap = AIR_GAP;
            Vec3 d = v.getDeltaMovement();
            v.setDeltaMovement(d.x * 1.1, 0.62, d.z * 1.1);
            v.hasImpulse = true;
            v.fallDistance = 0.0F;
            thrust(v.position(), 14);
            wingBurst(0xFFFFFF);
            v.playSound(ValkyrieEntity.sound("boost"), 0.8F, 1.5F);
            return true;
        }

        /** 地上なら跳び上がり、空中なら空中ジャンプで上へ（相手・持ち主が上にいるとき） */
        boolean climb() {
            if (v.onGround()) {
                if (maneuver() == null || airGap > 0 || !spend(Math.round(DASH_FE * ValkyrieUnitEffects.boostCostScale(v)))) return false;
                Vec3 d = v.getDeltaMovement();
                v.setDeltaMovement(d.x, 0.6, d.z);
                v.hasImpulse = true;
                airGap = AIR_GAP;
                thrust(v.position(), 10);
                v.playSound(ValkyrieEntity.sound("boost"), 0.6F, 1.2F);
                return true;
            }
            return v.getDeltaMovement().y < 0.1 && airJump();
        }

        /** スラスターの滞空を続ける（空中で、スラスターの滞空の長さまで。保てたら true） */
        boolean airHover(int ticks) {
            var m = maneuver();
            if (m == null || m.hoverTicks() <= 0 || v.onGround() || v.getEnergy() < HOVER_FE_PER_TICK + ValkyrieEntity.WAKE_ENERGY) return false;
            int left = m.hoverTicks() - airHoverUsed;
            if (left <= 0) return false;
            hoverTicks = Math.max(hoverTicks, Math.min(ticks, left));
            return true;
        }

        /** 着地してから使った滞空の長さ（スラスターの滞空の長さまで） */
        int airHoverUsed;

        /**
         * 空中で飛んで付いて行く・寄る（Mk-III 以上）。to へまっすぐ向かい、高さを保つ。電力は滞空と同じ毎 Tick。できたら true。
         * 兵姫は常時は飛ばない（§3）ので、飛ぶのはスラスターの機動が許すときだけ
         */
        boolean fly(Vec3 to, double speed) {
            if (v.getEnergy() < HOVER_FE_PER_TICK * 2 + ValkyrieEntity.WAKE_ENERGY) return false;
            Vec3 d = to.subtract(v.position());
            double len = d.length();
            Vec3 vel = len < 1.0e-3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
            if (!spend(Math.round(HOVER_FE_PER_TICK * ValkyrieUnitEffects.boostCostScale(v)))) return false;
            v.setDeltaMovement(vel.x, vel.y + 0.08, vel.z); // 重力の分だけ少し上へ（毎 Tick 引かれる）
            v.hasImpulse = true;
            v.fallDistance = 0.0F;
            v.getNavigation().stop();
            if (v.tickCount % 2 == 0) thrust(v.position(), 2);
            if (v.tickCount % 6 == 0) wingBurst(0x9FE8FF);
            return true;
        }

        /** 背中から光の羽のように広がる噴射（空中ダッシュ・空中ジャンプ・飛行） */
        void wingBurst(int color) {
            if (!(v.level() instanceof ServerLevel server)) return;
            float yaw = v.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 back = v.position().add(Mth.sin(yaw) * 0.3, v.getBbHeight() * 0.7, -Mth.cos(yaw) * 0.3);
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            var opt = Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), color, 0.5F, 10);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 1; i <= 4; i++) {
                    Vec3 p = back.add(right.scale(side * 0.18 * i)).add(0, 0.12 * i - 0.1 * (i * i) / 4.0, 0);
                    server.sendParticles(opt, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
        }

        /** ジャンプからの滞空（射撃中） */
        boolean hover() {
            if (hoverCooldown > 0 || stepping() || !v.onGround() || !spend(Math.round(HOVER_FE_PER_TICK * 4 * ValkyrieUnitEffects.boostCostScale(v)))) return false;
            v.setDeltaMovement(v.getDeltaMovement().x, 0.55, v.getDeltaMovement().z);
            v.hasImpulse = true;
            var m = maneuver();
            hoverTicks = m != null ? Math.max(HOVER_TICKS, m.hoverTicks()) : HOVER_TICKS;
            hoverCooldown = m != null && m.hoverTicks() > 0 ? HOVER_COOLDOWN / 2 : HOVER_COOLDOWN;
            thrust(v.position(), 10);
            v.playSound(ValkyrieEntity.sound("boost"), 0.6F, 1.3F);
            return true;
        }

        /** 飛んできた矢・弾を横ステップで避ける（当たりそうな物だけ、確率付き） */
        private void dodge() {
            Vec3 center = v.position().add(0, v.getBbHeight() * 0.5, 0);
            // 飛び道具らしい物みな（他 MOD の弾も。ProjectileLike）
            List<Entity> list = v.level().getEntities(v, v.getBoundingBox().inflate(8.0),
                    p -> com.automatavalkyries.weapon.ProjectileLike.is(p) && !isAlly(v, p) && p.getId() != lastDodged && !(p instanceof AbstractArrow a && a.isNoPhysics()));
            for (Entity p : list) {
                Vec3 vel = com.automatavalkyries.weapon.ProjectileLike.velocity(p);
                double speed2 = vel.lengthSqr();
                if (speed2 < 0.04) continue; // 止まっている（刺さった矢など）
                Vec3 rel = center.subtract(p.position());
                double t = Mth.clamp(rel.dot(vel) / speed2, 0.0, 12.0);
                if (t <= 0.0 || rel.subtract(vel.scale(t)).lengthSqr() > 1.3 * 1.3) continue;
                lastDodged = p.getId();
                if (v.getRandom().nextFloat() >= DODGE_CHANCE) {
                    dodgeScan = 10;
                    return;
                }
                // 弾の向きに対して横へ。片側がクールタイムなら反対側、両方なら避けない
                Vec3 side = vel.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1.0e-4) side = new Vec3(1, 0, 0);
                side = side.normalize();
                if (v.getRandom().nextBoolean()) side = side.scale(-1);
                double up = v.onGround() ? 0.22 : v.getDeltaMovement().y;
                if (!step(side, 0.9, up, DODGE_FE, 1.4F)) step(side.scale(-1), 0.9, up, DODGE_FE, 1.4F);
                dodgeScan = 10;
                return;
            }
        }

        /** 同じセリフはこの間隔（Tick）より短く繰り返さない */
        static final int SAME_LINE_COOLDOWN = 600;
        private final java.util.Map<String, Long> lastLine = new java.util.HashMap<>();

        /** セリフと感情アイコン（同じセリフは 30 秒に 1 回まで。電力低下が出続けていた、2026-10-05） */
        void line(String key, Emote emote) {
            long now = v.level().getGameTime();
            Long last = lastLine.get(key);
            if (last != null && now - last < SAME_LINE_COOLDOWN) return;
            lastLine.put(key, now);
            v.say(key);
            v.emote(emote);
        }
    }

    // ------------------------------------------------------------------ 近接の攻撃の動き

    /**
     * その近接の兵装の攻撃の動き（2026-10-06 ユーザー指定「斬る・突く・叩く・薙ぎ払う」）。槍・杭・針・拳は突く、槌・斧は叩く、
     * 大鎌・鎌・鞭・扇・花弁は薙ぎ払う、剣は斬る → 薙ぎ払う → 突くの連撃。技は叩く武器なら叩く、突く武器なら突く、ほかは薙ぎ払う
     */
    public static int attackMotion(ItemStack stack, int combo, boolean skill) {
        // 素手（剣・兵装以外の物も）と拳の兵装は殴る: 右・左の交互
        boolean bare = stack.isEmpty() || !(stack.getItem() instanceof com.automatavalkyries.weapon.TacticalWeaponItem
                || stack.getItem() instanceof net.minecraft.world.item.TieredItem);
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        String n = id == null ? "" : id.getPath();
        if (bare || n.contains("knuckle")) return combo % 2 == 0 ? ValkyrieEntity.ATTACK_PUNCH_RIGHT : ValkyrieEntity.ATTACK_PUNCH_LEFT;
        if (n.contains("lance") || n.contains("javelin") || n.contains("pile") || n.contains("stinger") || n.contains("needle")
                || n.contains("spear")) return ValkyrieEntity.ATTACK_THRUST;
        if (n.contains("hammer") || n.contains("axe") || n.contains("maul") || n.contains("drill")) return ValkyrieEntity.ATTACK_SMASH;
        if (skill || n.contains("scythe") || n.contains("sickle") || n.contains("whip") || n.contains("fan") || n.contains("lotus")
                || n.contains("chakram") || n.contains("chainsaw")) return ValkyrieEntity.ATTACK_SWEEP;
        if (n.contains("dagger")) return combo % 2 == 0 ? ValkyrieEntity.ATTACK_THRUST : ValkyrieEntity.ATTACK_SLASH;
        return switch (combo % 3) {
            case 0 -> ValkyrieEntity.ATTACK_SLASH;
            case 1 -> ValkyrieEntity.ATTACK_SWEEP;
            default -> ValkyrieEntity.ATTACK_THRUST;
        };
    }

    /**
     * 射撃の立ち回りでも、通常攻撃は剣で振る兵装（2026-10-06 ユーザー指定「ルミナス・レクイエムとラグナロクの通常攻撃に剣のモーション」）。
     * 構えと攻撃の動きを剣のものにする
     */
    public static boolean bladeOf(ItemStack stack) {
        return stack.is(com.automatavalkyries.registry.ModWeapons.LUMINOUS_REQUIEM.get()) || stack.is(com.automatavalkyries.registry.ModWeapons.RAGNAROK_EDGE.get());
    }

    // ------------------------------------------------------------------ 指示リモコンのステップ

    /**
     * 指示リモコンでステップさせる（2026-10-06 ユーザー指定）。向き dir（水平）へ、最中でなければ封じ・クールタイムを無視してステップする
     * （クールタイムはその向きに付く）。できたら true
     */
    public static boolean orderStep(ValkyrieEntity v, Vec3 dir) {
        Brain b = v.combat();
        if (!active(v) || b.stepping()) return false;
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        if (h.lengthSqr() < 1.0e-4) return false;
        boolean locked = b.stepLocked;
        int idx = b.classify(h).ordinal();
        int cd = b.stepCooldown[idx];
        b.stepLocked = false;
        b.stepCooldown[idx] = 0;
        boolean done = b.step(h, 1.0, v.onGround() ? 0.2 : v.getDeltaMovement().y, DASH_FE, 1.2F);
        b.stepLocked = locked;
        if (!done) b.stepCooldown[idx] = cd;
        return done;
    }

    // ------------------------------------------------------------------ 遠距離攻撃を防ぐ盾

    /** 遠距離を防ぐ盾（偏光シールド・イージス・バニラの盾）か */
    public static boolean rangedShieldItem(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.is(ModWeapons.POLAR_DEFLECTOR.get()) || stack.is(ModWeapons.AEGIS_BARRIER.get());
    }

    /** 自分か持ち主が遠距離攻撃されそうか: 向かって飛んでくる矢・弾（他 MOD の弾も）、こちらを狙って弓などを構えている相手 */
    public static boolean rangedThreat(ValkyrieEntity v) {
        Player owner = v.getOwner();
        List<LivingEntity> guarded = owner != null && owner.distanceToSqr(v) < 16 * 16 ? List.of(v, owner) : List.of(v);
        for (Entity p : v.level().getEntities(v, v.getBoundingBox().inflate(16.0),
                p -> com.automatavalkyries.weapon.ProjectileLike.is(p) && !isAlly(v, p) && !(p instanceof AbstractArrow a && a.isNoPhysics()))) {
            Vec3 vel = com.automatavalkyries.weapon.ProjectileLike.velocity(p);
            for (LivingEntity g : guarded) {
                Vec3 rel = g.position().add(0, g.getBbHeight() * 0.5, 0).subtract(p.position());
                double t = rel.dot(vel) / vel.lengthSqr();
                if (t > 0 && t < 20 && rel.subtract(vel.scale(t)).lengthSqr() < 2.0 * 2.0) return true;
            }
        }
        for (Mob m : v.level().getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(24.0),
                m -> m.isAlive() && !isAlly(v, m) && (m instanceof net.minecraft.world.entity.monster.RangedAttackMob || m instanceof net.minecraft.world.entity.monster.CrossbowAttackMob))) {
            LivingEntity mt = m.getTarget();
            if (mt != null && (mt == v || isAlly(v, mt)) && (m.isUsingItem() || m.isAggressive()) && m.hasLineOfSight(mt)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 共通

    /** 相手の胸へ向きを合わせる（兵装は持ち主の視線で撃つので、撃つ直前に呼ぶ） */
    public static void aim(Mob v, Entity target) {
        Vec3 eye = v.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
        v.setYRot(yaw);
        v.setYHeadRot(yaw);
        v.yBodyRot = yaw;
        v.setXRot(pitch);
        v.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    static float healthRatio(LivingEntity e) {
        return e.getMaxHealth() <= 0 ? 1.0F : DirectRead.health(e) / e.getMaxHealth();
    }

    /** 戦ってよい状態か */
    static boolean active(ValkyrieEntity v) {
        return !v.isShutdown() && !v.isAsleep() && v.isAlive();
    }

    /** 相手を探す中心と半径（追従は持ち主から「追う距離」、拠点防衛・巡回は拠点から半径＋余白、待機は自分のまわり） */
    static Vec3 anchor(ValkyrieEntity v) {
        Player owner = v.getOwner();
        BlockPos home = v.getHome();
        return switch (v.getMode()) {
            case FOLLOW -> owner != null ? owner.position() : v.position();
            case DEFEND, PATROL -> home != null ? Vec3.atBottomCenterOf(home) : v.position();
            case WAIT -> v.position();
        };
    }

    static double leash(ValkyrieEntity v) {
        double r = switch (v.getMode()) {
            case FOLLOW -> v.orders.chase;
            case DEFEND, PATROL -> v.homeRadius + HOME_MARGIN;
            case WAIT -> WAIT_SCAN;
        };
        // ロジック: ヴァンガードは持ち主のそば（10 m）から離れない。タレットは兵装の最大射程まで探す
        if (v.getMode() == Mode.FOLLOW && ValkyrieUnitEffects.role(v) == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.VANGUARD) r = Math.min(r, VANGUARD_LEASH);
        if (ValkyrieUnitEffects.turretTargets(v) != null) r = Math.max(r, maxRange(v));
        return r;
    }

    /** ヴァンガードが持ち主から離れてよい距離・持ち主の前に立つ距離、リアガードが持ち主の後ろに立つ距離【仮】 */
    static final double VANGUARD_LEASH = 10.0, VANGUARD_FRONT = 2.5, REARGUARD_BACK = 3.0;

    /** 持った兵装の通常攻撃・チャージ攻撃（技）の最大射程（素手・技の無い近接は 2.5） */
    static double maxRange(ValkyrieEntity v) {
        Art art = ValkyrieArts.of(v.getMainHandItem());
        if (art == null) return 2.5;
        return Math.max(2.5, Math.max(art.reach(), art.skill() != null || art.chargedSkill() ? art.skillRange() : 0.0));
    }

    /** 相手がまだ追ってよい範囲にいるか */
    static boolean withinLeash(ValkyrieEntity v, LivingEntity target) {
        double r = leash(v) + 4.0;
        return target.position().distanceToSqr(anchor(v)) <= r * r;
    }

    // ------------------------------------------------------------------ 狙う相手

    /** 交戦規定に従って狙う相手を決める（10 Tick ごと） */
    public static class Targeting extends Goal {
        private final ValkyrieEntity v;
        private int next;

        public Targeting(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!active(v)) {
                if (v.getTarget() != null) v.setTarget(null);
                return false;
            }
            if (--next > 0) return false;
            next = 10;
            LivingEntity current = v.getTarget();
            if (current != null && (!valid(current) || !withinLeash(v, current))) {
                if (!current.isAlive()) onTargetDown();
                v.setTarget(null);
                current = null;
            }
            LivingEntity best = choose();
            if (best != null && best != current) {
                if (current == null && v.level().getGameTime() - brain().lastEngageLine > ENGAGE_LINE_COOLDOWN) {
                    brain().lastEngageLine = v.level().getGameTime();
                    brain().line("engage", Emote.ALERT);
                } else if (current == null) {
                    v.emote(Emote.ALERT);
                }
                v.setTarget(best);
            }
            return false; // 決めるだけ（続けて回す必要は無い）
        }

        private Brain brain() {
            return v.combat();
        }

        private void onTargetDown() {
            Brain b = brain();
            if (v.level().getGameTime() - b.lastDownLine > DOWN_LINE_COOLDOWN && v.getRandom().nextFloat() < 0.4F) {
                b.lastDownLine = v.level().getGameTime();
                b.line("target_down", Emote.NOTE);
            }
        }

        private boolean valid(LivingEntity e) {
            if (!e.isAlive() || e.isSpectator() || isAlly(v, e)) return false;
            if (e instanceof ValkyrieEntity other && other.isShutdown()) return false; // 機能停止した兵姫は狙わない（§0.2-C）
            if (e instanceof Player p) {
                if (p.isCreative() || v.level().getServer() == null || !v.level().getServer().isPvpAllowed()) return false;
                return attackedUs(e);
            }
            return true;
        }

        /** 自分・持ち主・味方を攻撃した相手か */
        private boolean attackedUs(LivingEntity e) {
            if (v.getLastHurtByMob() == e) return true;
            Player owner = v.getOwner();
            if (owner != null && (owner.getLastHurtByMob() == e || (e instanceof Mob m && m.getTarget() == owner))) return true;
            if (e instanceof Mob m && m.getTarget() != null && isAlly(v, m.getTarget())) return true;
            return false;
        }

        @Nullable
        private LivingEntity choose() {
            ValkyrieOrders o = v.orders;
            if (o.policy == ValkyrieOrders.Policy.PASSIVE) return null;
            Vec3 center = anchor(v);
            double r = leash(v);
            List<LivingEntity> list = v.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r, Math.max(8.0, r * 0.5), r),
                    e -> e != v && valid(e) && e.position().distanceToSqr(center) <= r * r);
            boolean aggressive = o.policy == ValkyrieOrders.Policy.AGGRESSIVE;
            // ロジック: タレットは覚えた対象（無ければ敵対モブみな）を探す。ヴァンガード・リアガードは持ち主を狙う敵を優先する
            List<net.minecraft.world.entity.EntityType<?>> turret = ValkyrieUnitEffects.turretTargets(v);
            var role = ValkyrieUnitEffects.role(v);
            Player owner = v.getOwner();
            LivingEntity ownerFocus = owner != null ? owner.getLastHurtMob() : null;
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity e : list) {
                boolean hostile = aggressive && e instanceof Enemy && !(e instanceof Player);
                if (turret != null) hostile = !(e instanceof Player) && (turret.isEmpty() ? e instanceof Enemy : turret.contains(e.getType()));
                if (!hostile && !attackedUs(e)) continue;
                if (!v.hasLineOfSight(e) && v.distanceToSqr(e) > 16.0) continue;
                // ロジック: プレシジョン はボスを優先して狙う
                ValkyrieOrders.Priority priority = v.getInventory().hasChip(com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.PRECISION)
                        ? ValkyrieOrders.Priority.BOSS_FIRST : o.priority;
                double score = switch (priority) {
                    case NEAREST, TURRET_FIRST -> v.distanceToSqr(e); // タレット・警報は侵食基地ができてから
                    case LOWEST_HEALTH -> DirectRead.health(e) * 100.0 + v.distanceToSqr(e) * 0.01;
                    case BOSS_FIRST -> (e.getType().is(Tags.EntityTypes.BOSSES) ? 0.0 : 1.0e6) + v.distanceToSqr(e);
                };
                // 連携: 持ち主・ほかの味方の兵姫が狙っている相手を優先する
                if (e == ownerFocus || focusedByAllies(e)) score *= 0.35;
                boolean onOwner = owner != null && e instanceof Mob mob && mob.getTarget() == owner;
                if (role == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.VANGUARD && onOwner) score *= 0.2;
                else if (role == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.REARGUARD && onOwner) score *= 0.4;
                if (turret != null && !turret.isEmpty() && turret.contains(e.getType())) score *= 0.1; // 覚えた対象を先に
                if (e == v.getTarget()) score *= 0.5; // すぐに乗り換えない（敵が動くたびに乗り換えてジグザグに動いていた。2026-10-06）
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            return best;
        }

        private boolean focusedByAllies(LivingEntity e) {
            for (ValkyrieEntity other : v.level().getEntitiesOfClass(ValkyrieEntity.class, v.getBoundingBox().inflate(32.0))) {
                if (other != v && isAlly(v, other) && other.getTarget() == e) return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ 戦う

    /** 持った兵装の型で戦う（近接は詰めて斬る、射撃は距離を取って撃つ、技・溜め撃ち） */
    public static class Fight extends Goal {
        private final ValkyrieEntity v;
        private int basicTimer, skillTimer, offhandTimer, repath, strafeTimer, charging, emptyTicks, failures;
        /** 左手の武器の技の間隔 */
        private int offSkillTimer, offCombo;
        private boolean strafeLeft, strafing, saidEmpty;
        /** 連撃の数（剣の斬る → 薙ぎ払う → 突く） */
        private int combo;
        /** 動き出しているか（止まる・動くの境目に幅を持たせる） */
        private boolean moving;

        public Fight(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = v.getTarget();
            return active(v) && t != null && t.isAlive() && !v.combat().retreating && !v.isPassenger();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
            basicTimer = Math.min(basicTimer, 5);
        }

        @Override
        public void stop() {
            stopCharging(false);
            v.getNavigation().stop();
            v.setCombatPose(0);
            v.combat().anchored = false;
        }

        /**
         * ロジック: タレット。相手が通常・チャージ攻撃の最大射程に入って見えていれば、その場で足を止めて撃つ（ステップ・回避もしない）。
         * 止めたら true（立ち回りをしない）。射程の外なら普段どおり詰める
         */
        private boolean turretHold(LivingEntity t, double dist) {
            Brain b = v.combat();
            boolean hold = ValkyrieUnitEffects.turretTargets(v) != null && dist <= maxRange(v) && v.hasLineOfSight(t);
            if (hold && !b.anchored) b.line("turret_lock", Emote.ALERT);
            b.anchored = hold;
            if (hold) {
                v.getNavigation().stop();
                moving = false;
            }
            return hold;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void stopCharging(boolean release) {
            if (charging > 0 || v.isUsingItem()) {
                if (release) v.releaseUsingItem();
                else v.stopUsingItem();
            }
            charging = 0;
        }

        @Override
        public void tick() {
            LivingEntity t = v.getTarget();
            if (t == null || !(v.level() instanceof ServerLevel level)) return;
            ItemStack main = v.getMainHandItem();
            Art art = ValkyrieArts.of(main);
            double dist = v.distanceTo(t);
            if (basicTimer > 0) basicTimer--;
            if (skillTimer > 0) skillTimer--;
            if (offhandTimer > 0) offhandTimer--;
            if (offSkillTimer > 0) offSkillTimer--;
            Boolean chargeOnly = ValkyrieUnitEffects.chargeOnly(v); // ロジック: アタックセレクト
            var role = ValkyrieUnitEffects.role(v);
            if (chargeOnly == null || chargeOnly) offhandWeapon(level, t, dist);
            if (emptyTicks > 0 && --emptyTicks == 0) {
                saidEmpty = false;
                failures = 0;
            }
            boolean empty = emptyTicks > 0;
            Style style = art == null ? Style.MELEE : art.style();
            if (empty && v.orders.meleeOnEmpty && role != com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.REARGUARD) style = Style.MELEE; // リアガードは近接へ移らない
            double reach = art == null || (empty && v.orders.meleeOnEmpty && art.style() != Style.MELEE) ? 2.5 : Math.max(2.0, art.reach());

            v.combat().engage(t, style == Style.MELEE);
            boolean blade = style == Style.MELEE || bladeOf(main);
            v.setCombatPose(blade ? 1 : 2); // 構え（モデルの動き）。剣の T4 は射撃の立ち回りでも剣の構え
            // 近接は相手に目線を固定する。射撃は動いている間は進む向きを見る（相手を見たまま歩くと、後ろ向きに歩いていた。2026-10-06）。止まっていれば相手を見る
            if (style == Style.MELEE || !moving || dist <= Math.max(reach, 4.0)) v.getLookControl().setLookAt(t, 30.0F, 30.0F);

            // 溜め撃ちの最中: 向きを合わせ続け、溜めきったら離す
            if (charging > 0) {
                aim(v, t);
                if (!v.isUsingItem() || v.getUseItem() != main) {
                    charging = 0;
                } else if (v.getTicksUsingItem() >= charging) {
                    stopCharging(true);
                    skillTimer = art != null ? art.skillCooldown() : 60;
                } else {
                    v.getNavigation().stop();
                    return;
                }
            }

            if (!turretHold(t, dist)) move(t, style, dist, reach);

            // 左手のモジュール（回復・障壁など）
            Art off = ValkyrieArts.of(v.getOffhandItem());
            if (off != null && off.offhand() && off.skill() != null && offhandTimer == 0 && !rangedShieldItem(v.getOffhandItem()) && off.wants(v, t)) {
                if (off.skill().run(level, v, v.getOffhandItem(), t)) offhandTimer = off.skillCooldown();
                else offhandTimer = 20;
            }

            // 技（溜め撃ちはアイテムの使用で）。アタックセレクトが通常攻撃だけなら出さない
            if (art != null && skillTimer == 0 && (chargeOnly == null || chargeOnly) && dist <= art.skillRange() && art.wants(v, t) && v.hasLineOfSight(t)) {
                if (art.chargedSkill() && main.getItem() instanceof ChargedWeaponItem charged) {
                    aim(v, t);
                    v.startUsingItem(InteractionHand.MAIN_HAND);
                    if (v.isUsingItem()) {
                        charging = Math.max(1, charged.chargeTicks(main));
                        return;
                    }
                    skillTimer = 20;
                } else if (art.skill() != null) {
                    aim(v, t);
                    if (art.style() == Style.MELEE) v.playAttack(attackMotion(main, combo, true));
                    if (art.skill().run(level, v, main, t)) {
                        skillTimer = art.skillCooldown();
                        failures = 0;
                        return;
                    }
                    skillTimer = 20;
                    noteFailure(art);
                }
            }

            // 通常攻撃。アタックセレクトがチャージ攻撃だけなら出さない
            if (basicTimer > 0 || Boolean.TRUE.equals(chargeOnly)) return;
            if (art != null && art.basic() != null && !(empty && v.orders.meleeOnEmpty)) {
                if (dist <= art.reach() && v.hasLineOfSight(t)) {
                    aim(v, t);
                    if (blade) v.playAttack(attackMotion(main, combo++, false));
                    if (art.basic().run(level, v, main, t)) {
                        basicTimer = art.basicInterval();
                        failures = 0;
                        if (!blade) v.swing(InteractionHand.MAIN_HAND, true); // 撃った反動の動き（モデル）
                    } else {
                        basicTimer = 10;
                        noteFailure(art);
                    }
                }
                return;
            }
            if (style == Style.MELEE || dist < 2.5) {
                double melee = Math.max(2.0, reach) + t.getBbWidth() * 0.5;
                if (dist <= melee) {
                    aim(v, t);
                    v.playAttack(attackMotion(main, combo++, false));
                    ValkyrieArts.strike(melee).run(level, v, main, t); // バニラの近接攻撃は hurtEnemy を呼ばないので、当てたときの処理もここで
                    basicTimer = art != null ? art.basicInterval() : 12;
                }
            }
        }

        /**
         * 片手の兵装を 2 つ持っているとき: 右手の武器を主に使い、左手の武器はチャージ攻撃（技）だけ使う（盾は今まで通り {@link Guard}・左手のモジュール）。
         * 2026-10-06 ユーザー指定「うまく行かないので、片手武器を二つ持っている戦術兵姫は一つの武器をメインに、サブ武器にあるチャージ攻撃と盾だけを使用する」。
         * 左手の物を右手に持ち替えた状態で技を出し、左手で振る動きを出す（{@link com.automatavalkyries.weapon.DualWield#asMainHand}）
         */
        private void offhandWeapon(ServerLevel level, LivingEntity t, double dist) {
            ItemStack off = v.getOffhandItem();
            if (!com.automatavalkyries.weapon.DualWield.isDual(v) || charging > 0 || v.isUsingItem() || offSkillTimer > 0) return;
            Art art = ValkyrieArts.of(off);
            if (art == null || art.offhand() || art.skill() == null || art.chargedSkill()) return; // 盾・モジュールは今まで通り、技の無い武器は使わない
            if (dist > art.skillRange() || !art.wants(v, t) || !v.hasLineOfSight(t)) return;
            boolean[] done = {false};
            aim(v, t);
            if (art.style() == Style.MELEE) v.playAttack(attackMotion(off, offCombo++, true), true);
            com.automatavalkyries.weapon.DualWield.asMainHand(v, () -> done[0] = art.skill().run(level, v, off, t));
            offSkillTimer = done[0] ? art.skillCooldown() : 20;
            if (done[0]) v.swing(InteractionHand.OFF_HAND, true);
        }

        /** 撃てない（弾・電力切れ）が続いたら、しばらく弾切れとして扱う（指示があれば近接へ） */
        private void noteFailure(Art art) {
            if (art.style() == Style.MELEE) return;
            if (++failures >= 3 && emptyTicks == 0) {
                emptyTicks = 200;
                if (ValkyrieUnitEffects.role(v) != com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.REARGUARD) v.combat().swapToMelee(false); // リアガードは近接へ持ち替えない // 予備の近接の兵装があれば持ち替える（2026-10-06 ユーザー指定）
                if (!saidEmpty && v.orders.meleeOnEmpty && ValkyrieUnitEffects.role(v) != com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.REARGUARD) {
                    saidEmpty = true;
                    v.combat().line("out_of_ammo", Emote.SWEAT);
                }
            }
        }

        /** 立ち回り: 近接は詰める（遠ければダッシュ）、射撃は保つ距離（前衛・持ち主の後ろ側）で横に動きつつ、時々滞空、支援は持ち主のそば */
        private void move(LivingEntity t, Style style, double dist, double reach) {
            Brain brain = v.combat();
            if (v.getMode() == Mode.WAIT) {
                v.getNavigation().stop(); // 待機はその場から（届くときだけ攻撃）
                return;
            }
            var m = brain.maneuver();
            var role = ValkyrieUnitEffects.role(v);
            Player roleOwner = v.getOwner();
            if (role == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.REARGUARD && roleOwner != null && v.getMode() == Mode.FOLLOW) {
                rearguardMove(t, roleOwner, dist, reach);
                return;
            }
            if (role == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.VANGUARD && roleOwner != null && style != Style.MELEE && v.getMode() == Mode.FOLLOW) {
                // ヴァンガードの射撃: 持ち主と相手の間（持ち主の前）に立って撃つ
                Vec3 to = new Vec3(t.getX() - roleOwner.getX(), 0, t.getZ() - roleOwner.getZ());
                Vec3 spot = to.lengthSqr() < 1.0e-4 ? roleOwner.position() : roleOwner.position().add(to.normalize().scale(Math.min(VANGUARD_FRONT + 1.0, to.length() * 0.5)));
                holdSpot(t, spot, 1.3);
                return;
            }
            switch (style) {
                case MELEE -> {
                    // Mk-IV: 飛んでいる相手には空中で寄って戦う（間合いの 6 割の所、相手と同じ高さ）
                    if (m != null && m.airCombat() && flyingTarget(t) && dist < 32.0) {
                        Vec3 away = new Vec3(v.getX() - t.getX(), 0, v.getZ() - t.getZ());
                        away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(reach * 0.6);
                        if (brain.fly(new Vec3(t.getX() + away.x, t.getY(), t.getZ() + away.z), dist > 8 ? 0.9 : 0.5)) {
                            moving = dist > reach;
                            return;
                        }
                    }
                    // 届かなくなってから詰め始め、間合いの 8 割まで入ったら止まる（境目で動く・止まるを繰り返さないよう幅を持たせる）
                    if (dist > reach) moving = true;
                    else if (dist <= reach * 0.8) moving = false;
                    if (!moving && m != null && !v.onGround() && !t.onGround()) brain.airHover(20); // 空中の相手とは浮いたまま打ち合う
                    if (moving) {
                        // ダッシュはまっすぐ行けるとき（見えていて、高さがほぼ同じ）だけ。障害物や段差へ突っ込んで道を探し直していた
                        if (dist > 5.0 && dist < 16.0 && v.hasLineOfSight(t) && Math.abs(t.getY() - v.getY()) < 1.0) {
                            brain.dash(t.position().subtract(v.position()), 1.1);
                        }
                        // スラスター: 上にいる相手へは跳んで空中ジャンプ、空中からは空中ダッシュで詰める
                        if (m != null && dist < 16.0 && v.hasLineOfSight(t)) {
                            if (t.getY() - v.getY() > 1.5) brain.climb();
                            if (!v.onGround() && dist > 2.5) brain.airDash(t.position().subtract(v.position()), 1.0);
                        }
                        if (--repath <= 0) {
                            repath = 8;
                            v.getNavigation().moveTo(t, ValkyrieGoals.pathSpeed(v, 1.3));
                        }
                    } else {
                        v.getNavigation().stop();
                    }
                }
                case RANGED -> {
                    double keep = Math.min(v.orders.keep, reach * 0.8);
                    Vec3 spot = rangedSpot(t, keep);
                    // 近すぎたら下がる: 立ち止まって撃っているときに相手が詰めてきたときだけ（向かう途中に後ろへステップしていた。2026-10-06）
                    if (!moving && dist < keep * 0.5 && dist < 4.0) brain.dash(v.position().subtract(t.position()), 0.9);
                    // 立ち位置へ動くのは、届かない・近すぎる・見えない・立ち位置から 6 ブロック以上ずれたときだけ。動き出したら立ち位置の 2 ブロック以内まで行く
                    // （3 ブロックずれるたびに歩き直して、ちょろちょろ動いていた。2026-10-06）
                    boolean off = dist > reach * 0.9 || dist < keep * 0.5 || !v.hasLineOfSight(t) || v.position().distanceToSqr(spot) > 36.0;
                    if (off) moving = true;
                    else if (v.position().distanceToSqr(spot) < 4.0) moving = false;
                    if (moving) {
                        if (--repath <= 0) {
                            repath = 10;
                            v.getNavigation().moveTo(spot.x, spot.y, spot.z, ValkyrieGoals.pathSpeed(v, 1.2));
                        }
                    } else {
                        v.getNavigation().stop();
                        strafe(t);
                        boolean canHover = m != null && m.hoverTicks() > 0;
                        if (v.getRandom().nextFloat() < (canHover ? 0.02F : 0.005F)) brain.hover();
                        if (canHover && !v.onGround()) {
                            // スラスターの滞空: 浮いたまま撃ち、時々空中で横へ跳び、下がってきたら空中ジャンプで高さを取り戻す
                            brain.airHover(20);
                            if (v.getRandom().nextFloat() < 0.03F) {
                                Vec3 to = t.position().subtract(v.position());
                                Vec3 side = new Vec3(-to.z, 0, to.x);
                                brain.airDash(v.getRandom().nextBoolean() ? side : side.reverse(), 0.7);
                            }
                            if (v.getDeltaMovement().y < -0.1 && v.getRandom().nextFloat() < 0.05F) brain.airJump();
                        }
                    }
                }
                case SUPPORT -> {
                    Player owner = v.getOwner();
                    Entity anchor = owner != null ? owner : t;
                    if (v.distanceTo(anchor) > 4.0 && --repath <= 0) {
                        repath = 10;
                        v.getNavigation().moveTo(anchor, ValkyrieGoals.pathSpeed(v, 1.2));
                    }
                }
            }
        }

        /** 決めた立ち位置へ（2 ブロックずれたら歩き、着いたら止まって相手を見る） */
        private void holdSpot(LivingEntity t, Vec3 spot, double speed) {
            if (v.position().distanceToSqr(spot) > 4.0) {
                moving = true;
                if (--repath <= 0) {
                    repath = 8;
                    v.getNavigation().moveTo(spot.x, spot.y, spot.z, ValkyrieGoals.pathSpeed(v, speed));
                }
            } else {
                moving = false;
                v.getNavigation().stop();
                v.getLookControl().setLookAt(t, 30.0F, 30.0F);
            }
        }

        /**
         * ロジック: リアガード。持ち主の後ろ（相手と反対側）に立ち、そこから撃つ。相手が届かなければ届く所まで寄るが、持ち主より前へは出ない。
         * 近接の兵装なら相手が寄ってきたときだけ殴る
         */
        private void rearguardMove(LivingEntity t, Player owner, double dist, double reach) {
            Vec3 away = new Vec3(owner.getX() - t.getX(), 0, owner.getZ() - t.getZ());
            if (away.lengthSqr() < 1.0e-4) away = owner.getLookAngle().reverse();
            away = new Vec3(away.x, 0, away.z).normalize();
            Vec3 spot = owner.position().add(away.scale(REARGUARD_BACK));
            double toTarget = Math.sqrt(spot.distanceToSqr(t.position()));
            if (toTarget > reach * 0.85) spot = owner.position().add(away.scale(Math.max(0.0, REARGUARD_BACK - (toTarget - reach * 0.85))));
            holdSpot(t, spot, 1.25);
            if (!moving && v.combat().maneuver() != null && v.combat().maneuver().hoverTicks() > 0 && v.getRandom().nextFloat() < 0.02F) v.combat().hover();
        }

        /** 射撃の立ち位置: 相手から見て、前衛（近接の味方の兵姫）がいればその側、いなければ自分のいる側に、保つ距離だけ離れた所 */
        private Vec3 rangedSpot(LivingEntity t, double keep) {
            Entity front = null;
            double best = Double.MAX_VALUE;
            for (ValkyrieEntity other : v.level().getEntitiesOfClass(ValkyrieEntity.class, v.getBoundingBox().inflate(24.0))) {
                if (other == v || !isAlly(v, other) || other.getTarget() != t) continue;
                Art a = ValkyrieArts.of(other.getMainHandItem());
                if (a != null && a.style() != Style.MELEE) continue;
                double d = other.distanceToSqr(t);
                if (d < best) {
                    best = d;
                    front = other;
                }
            }
            // 前衛がいなければ自分のいる側（持ち主の側にすると、相手の向こうへ回り込んで相手のまわりを走っていた。2026-10-06）
            Vec3 dir = (front != null ? front.position() : v.position()).subtract(t.position());
            if (dir.horizontalDistanceSqr() < 0.5) dir = v.position().subtract(t.position());
            dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() < 1.0e-4) dir = new Vec3(1, 0, 0);
            return t.position().add(dir.normalize().scale(keep + (front != null ? 2.0 : 0.0)));
        }

        /** 撃ちながら時々横へ動く: 0.5〜1 秒動いて、2〜4 秒は立ち止まって撃つ（ずっと横へ動き続けて落ち着きがなかった。2026-10-06） */
        /** 空を飛んでいる相手か（足が地面になく、3 ブロック以内の下に地面が無い） */
        private boolean flyingTarget(LivingEntity t) {
            if (t.onGround() || t.isInWater() || t.isPassenger()) return false;
            BlockPos p = t.blockPosition();
            for (int i = 0; i < 3; i++) {
                p = p.below();
                if (!t.level().getBlockState(p).getCollisionShape(t.level(), p).isEmpty()) return false;
            }
            return true;
        }

        private void strafe(LivingEntity t) {
            if (--strafeTimer <= 0) {
                strafing = !strafing;
                strafeTimer = strafing ? 10 + v.getRandom().nextInt(10) : 40 + v.getRandom().nextInt(40);
                strafeLeft = v.getRandom().nextBoolean();
            }
            if (strafing) v.getMoveControl().strafe(0.0F, strafeLeft ? 0.4F : -0.4F);
        }
    }

    // ------------------------------------------------------------------ 後退

    /** HP・電力が基準を割ったら下がって自己修理（弾薬庫のナノマシン溶液）。HP は基準 +20%、電力は +10% まで戻ったら戦いへ戻る */
    public static class Retreat extends Goal {
        private final ValkyrieEntity v;
        private int repath, repairTimer;

        public Retreat(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private float hp() {
            return healthRatio(v) * 100.0F;
        }

        private float power() {
            return v.getEnergy() * 100.0F / ValkyrieEntity.MAX_ENERGY;
        }

        @Override
        public boolean canUse() {
            if (!active(v) || v.getTarget() == null) return false;
            boolean lowHp = v.orders.retreatHealth > 0 && hp() < v.orders.retreatHealth;
            boolean lowPower = v.orders.retreatPower > 0 && power() < v.orders.retreatPower;
            return lowHp || lowPower;
        }

        @Override
        public boolean canContinueToUse() {
            if (!active(v)) return false;
            boolean hpOk = v.orders.retreatHealth <= 0 || hp() >= Math.min(100, v.orders.retreatHealth + 20);
            boolean powerOk = v.orders.retreatPower <= 0 || power() >= Math.min(100, v.orders.retreatPower + 10);
            return !(hpOk && powerOk);
        }

        @Override
        public void start() {
            v.combat().line(v.orders.retreatHealth > 0 && hp() < v.orders.retreatHealth ? "retreat" : "low_power", Emote.SWEAT);
            v.combat().retreating = true;
            repath = 0;
            LivingEntity t = v.getTarget();
            if (t != null) v.combat().dash(v.position().subtract(t.position()), 1.0);
        }

        @Override
        public void stop() {
            v.combat().retreating = false;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--repairTimer <= 0) {
                repairTimer = 40;
                if (healthRatio(v) < 0.95F && takeNano()) v.repair(ValkyrieEntity.REPAIR);
            }
            if (--repath > 0) return;
            repath = 15;
            LivingEntity t = v.getTarget();
            Player owner = v.getOwner();
            Vec3 safe;
            if (owner != null && v.getMode() == Mode.FOLLOW) {
                safe = owner.position();
                if (t != null) safe = safe.add(owner.position().subtract(t.position()).normalize().scale(3.0)); // 持ち主の向こう側
            } else if (v.getHome() != null) {
                safe = Vec3.atBottomCenterOf(v.getHome());
            } else if (t != null) {
                safe = v.position().add(v.position().subtract(t.position()).normalize().scale(12.0));
            } else {
                return;
            }
            v.getNavigation().moveTo(safe.x, safe.y, safe.z, ValkyrieGoals.pathSpeed(v, 1.35));
        }

        /** 弾薬庫のナノマシン溶液を 1 本使う */
        private boolean takeNano() {
            ValkyrieInventory inv = v.getInventory();
            for (int i = ValkyrieInventory.AMMO; i < ValkyrieInventory.AMMO + v.getBody().ammoSlots; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(ModWeapons.NANO_SOLUTION.get())) {
                    s.shrink(1);
                    inv.setChanged();
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ かばう

    /** 盾持ちだけ: 持ち主が瀕死なら持ち主と敵の間に出て障壁を張る */
    public static class Guard extends Goal {
        private final ValkyrieEntity v;
        @Nullable private LivingEntity threat;
        private int repath, barrierTimer;

        public Guard(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean shieldBearer() {
            return ValkyrieArts.isShield(v.getOffhandItem()) || ValkyrieArts.isShield(v.getMainHandItem());
        }

        @Nullable
        private LivingEntity findThreat(Player owner) {
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (Mob m : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12.0),
                    m -> m.isAlive() && !isAlly(v, m) && (m.getTarget() == owner || m instanceof Enemy))) {
                double d = m.distanceToSqr(owner);
                if (d < bestD) {
                    bestD = d;
                    best = m;
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (!active(v) || !shieldBearer()) return false;
            Player owner = v.getOwner();
            if (owner == null || !owner.isAlive() || healthRatio(owner) > OWNER_CRITICAL || v.distanceToSqr(owner) > 24 * 24) return false;
            threat = findThreat(owner);
            return threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            Player owner = v.getOwner();
            return active(v) && owner != null && owner.isAlive() && healthRatio(owner) <= OWNER_CRITICAL + 0.1F
                    && threat != null && threat.isAlive() && shieldBearer();
        }

        @Override
        public void start() {
            repath = 0;
            barrierTimer = 0;
            Brain b = v.combat();
            if (v.level().getGameTime() - b.lastBarrierLine > BARRIER_LINE_COOLDOWN) {
                b.lastBarrierLine = v.level().getGameTime();
                b.line("barrier", Emote.ALERT);
            }
        }

        @Override
        public void stop() {
            if (v.isUsingItem() && v.getUsedItemHand() == InteractionHand.OFF_HAND) v.stopUsingItem();
            Player owner = v.getOwner();
            if (owner != null && owner.isAlive() && threat != null && active(v)) ValkyrieDaily.onOwnerSaved(v); // かばいきった
            threat = null;
            v.setCombatPose(0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player owner = v.getOwner();
            if (owner == null || threat == null || !(v.level() instanceof ServerLevel level)) return;
            Vec3 dir = threat.position().subtract(owner.position());
            dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() < 1.0e-4) dir = owner.getLookAngle();
            Vec3 spot = owner.position().add(dir.normalize().scale(1.6));
            if (v.position().distanceToSqr(spot) > 1.0) {
                if (v.distanceToSqr(spot) > 25.0) {
                    Brain b = v.combat();
                    boolean locked = b.stepLocked;
                    b.stepLocked = false; // 持ち主をかばうダッシュは封じない
                    b.dash(spot.subtract(v.position()), 1.2);
                    b.stepLocked = locked;
                }
                if (--repath <= 0) {
                    repath = 5;
                    v.getNavigation().moveTo(spot.x, spot.y, spot.z, ValkyrieGoals.pathSpeed(v, 1.5));
                }
            } else {
                v.getNavigation().stop();
            }
            aim(v, threat);
            v.setCombatPose(1); // 盾を構える（モデルの動き）
            if (barrierTimer > 0) barrierTimer--;
            ItemStack off = v.getOffhandItem();
            Art art = ValkyrieArts.of(off);
            if (barrierTimer == 0) {
                barrierTimer = 40;
                if (art != null && art.skill() != null) art.skill().run(level, v, off, threat); // 偏光シールド・イージスを張る
                // 障壁の光（持ち主の前の光の壁）
                Fx.shockwave(level, v.position().add(0, 0.05, 0), 0x7FE8FF, 2.2F, 10);
                level.sendParticles(ParticleTypes.END_ROD, spot.x, spot.y + 1.0, spot.z, 16, 0.6, 0.7, 0.6, 0.01);
            }
            if (off.getItem() instanceof ShieldItem && !v.isUsingItem()) v.startUsingItem(InteractionHand.OFF_HAND); // 盾を構える
        }
    }

    // ------------------------------------------------------------------ 救護（ロジック: メディック）

    /**
     * ロジック: メディック（2026-10-06 ユーザー指定「体力を見て回復や、弾が無いなら物拾いインベントリから弾薬やナノマシンを渡す救護チップ」）。
     * 持ち主・味方の兵姫の HP が {@link #MEDIC_HEAL} を割ったら、駆け寄ってナノマシン溶液を 1 本使って回復する
     * （兵姫は修理、プレイヤーは回復＋再生）。持ち主が持った武器の弾を持っていなければ、拾った物・弾薬庫から渡す（自分の兵装の弾は弾薬庫から渡さない）
     */
    public static class Medic extends Goal {
        /** 回復する HP の割合・プレイヤーへの回復量・再生の長さ・手当ての間隔・渡す弾の数【仮】 */
        public static final float MEDIC_HEAL = 0.5F, PLAYER_HEAL = 8.0F;
        public static final int REGEN_TICKS = 100, CARE_COOLDOWN = 60, HAND_OVER = 32;
        private final ValkyrieEntity v;
        @Nullable private LivingEntity patient;
        /** 何をするか: 0 = 回復、1 = 弾を渡す */
        private int task;
        private int scan, cooldown, repath;

        public Medic(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean medic() {
            return ValkyrieUnitEffects.role(v) == com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind.MEDIC;
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) cooldown--;
            if (!active(v) || v.combat().retreating || v.isPassenger() || cooldown > 0 || !medic()) return false;
            if (--scan > 0) return false;
            scan = 10;
            return find();
        }

        @Override
        public boolean canContinueToUse() {
            return active(v) && patient != null && patient.isAlive() && medic() && stillNeeds() && v.distanceToSqr(patient) < 32 * 32;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            patient = null;
            v.getNavigation().stop();
        }

        /** 手当てする相手を探す（持ち主が先、回復が先） */
        private boolean find() {
            double reach = v.getMode() == Mode.WAIT ? 6.0 : 24.0;
            Player owner = v.getOwner();
            List<LivingEntity> patients = new java.util.ArrayList<>();
            if (owner != null && owner.isAlive() && !owner.isSpectator() && v.distanceToSqr(owner) < reach * reach) patients.add(owner);
            for (ValkyrieEntity other : v.level().getEntitiesOfClass(ValkyrieEntity.class, v.getBoundingBox().inflate(Math.min(reach, 16.0)))) {
                if (other != v && isAlly(v, other) && !other.isShutdown()) patients.add(other);
            }
            for (LivingEntity p : patients) {
                if (healthRatio(p) < MEDIC_HEAL) {
                    if (findNano(false) < 0) {
                        v.combat().line("medic_empty", Emote.SWEAT);
                        continue;
                    }
                    patient = p;
                    task = 0;
                    return true;
                }
            }
            for (LivingEntity p : patients) {
                if (ammoNeeded(p) != null && supply(ammoNeeded(p), false) >= 0) {
                    patient = p;
                    task = 1;
                    return true;
                }
            }
            return false;
        }

        private boolean stillNeeds() {
            if (patient == null) return false;
            if (task == 0) return healthRatio(patient) < MEDIC_HEAL + 0.1F && findNano(false) >= 0;
            var need = ammoNeeded(patient);
            return need != null && supply(need, false) >= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (patient == null) return;
            v.getLookControl().setLookAt(patient, 30.0F, 30.0F);
            if (v.distanceToSqr(patient) > 2.5 * 2.5) {
                if (--repath <= 0) {
                    repath = 6;
                    v.getNavigation().moveTo(patient, ValkyrieGoals.pathSpeed(v, 1.45));
                }
                return;
            }
            v.getNavigation().stop();
            if (task == 0) heal(patient);
            else handOver(patient);
            cooldown = CARE_COOLDOWN;
            patient = null;
        }

        /** ナノマシン溶液を 1 本使って回復する */
        private void heal(LivingEntity p) {
            int slot = findNano(true);
            if (slot < 0) return;
            if (p instanceof ValkyrieEntity other) {
                other.repair(ValkyrieEntity.REPAIR);
                v.combat().line("medic_repair", Emote.NOTE);
            } else {
                p.heal(PLAYER_HEAL);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, REGEN_TICKS, 1));
                v.combat().line("medic_heal", Emote.NOTE);
            }
            v.swing(InteractionHand.MAIN_HAND, true);
            v.gesture(ValkyrieEntity.GESTURE_NOD);
            if (v.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
                Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), 0x7FFFB0, 0.5F, 14),
                        p.position().add(0, p.getBbHeight() * 0.5, 0), 12, 0.4, 0.6, 0.4, 0.01);
            }
            v.playSound(net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, 0.6F, 1.6F);
        }

        /** 足りない弾を渡す（プレイヤーは持ち物へ、兵姫は弾薬庫へ。入りきらなければ足元に落とす） */
        private void handOver(LivingEntity p) {
            var need = ammoNeeded(p);
            if (need == null) return;
            int left = HAND_OVER;
            boolean any = false;
            while (left > 0) {
                int slot = supply(need, true);
                if (slot < 0) break;
                ItemStack from = v.getInventory().getItem(slot);
                ItemStack give = from.split(Math.min(left, from.getCount()));
                v.getInventory().setItem(slot, from.isEmpty() ? ItemStack.EMPTY : from);
                left -= give.getCount();
                any = true;
                if (p instanceof Player player) {
                    if (!player.getInventory().add(give)) player.drop(give, false);
                } else if (p instanceof ValkyrieEntity other) {
                    for (int i = ValkyrieInventory.AMMO; i < ValkyrieInventory.AMMO + other.getBody().ammoSlots && !give.isEmpty(); i++) {
                        ItemStack cur = other.getInventory().getItem(i);
                        if (cur.isEmpty()) {
                            other.getInventory().setItem(i, give.copy());
                            give = ItemStack.EMPTY;
                        } else if (ItemStack.isSameItemSameTags(cur, give) && cur.getCount() < cur.getMaxStackSize()) {
                            int n = Math.min(give.getCount(), cur.getMaxStackSize() - cur.getCount());
                            cur.grow(n);
                            give.shrink(n);
                            other.getInventory().setChanged();
                        }
                    }
                    if (!give.isEmpty()) other.spawnAtLocation(give);
                }
            }
            if (!any) return;
            v.combat().line("medic_ammo", Emote.NOTE);
            v.swing(InteractionHand.MAIN_HAND, true);
            v.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
        }

        /** ナノマシン溶液のある枠（弾薬庫 → 拾った物）。take でなければ探すだけ。1 本使う。無ければ -1 */
        private int findNano(boolean take) {
            var inv = v.getInventory();
            int[][] ranges = {{ValkyrieInventory.AMMO, ValkyrieInventory.AMMO + v.getBody().ammoSlots}, {ValkyrieInventory.PICKUP, ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS}};
            for (int[] r : ranges) {
                for (int i = r[0]; i < r[1]; i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.is(ModWeapons.NANO_SOLUTION.get())) continue;
                    if (take) {
                        s.shrink(1);
                        inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
                    }
                    return i;
                }
            }
            return -1;
        }

        /** その弾を渡せる枠（拾った物 → 弾薬庫。弾薬庫からは自分の兵装の弾は渡さない）。無ければ -1 */
        private int supply(java.util.function.Predicate<ItemStack> need, boolean forTake) {
            var inv = v.getInventory();
            for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
                if (!inv.getItem(i).isEmpty() && need.test(inv.getItem(i))) return i;
            }
            var mine = ammoFor(v.getMainHandItem());
            for (int i = ValkyrieInventory.AMMO; i < ValkyrieInventory.AMMO + v.getBody().ammoSlots; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !need.test(s) || (mine != null && mine.test(s))) continue;
                return i;
            }
            return -1;
        }

        /** その人が持った武器の弾を持っていなければ、その弾の条件（足りていれば null）。プレイヤーは持ち物、兵姫は弾薬庫を見る */
        @Nullable
        static java.util.function.Predicate<ItemStack> ammoNeeded(LivingEntity p) {
            if (p instanceof Player player && player.getAbilities().instabuild) return null;
            for (ItemStack held : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
                var need = ammoFor(held);
                if (need == null) continue;
                boolean has = false;
                if (p instanceof Player player) {
                    for (int i = 0; i < player.getInventory().getContainerSize() && !has; i++) has = need.test(player.getInventory().getItem(i));
                } else if (p instanceof ValkyrieEntity other) {
                    for (int i = ValkyrieInventory.AMMO; i < ValkyrieInventory.AMMO + other.getBody().ammoSlots && !has; i++) has = need.test(other.getInventory().getItem(i));
                }
                if (!has) return need;
            }
            return null;
        }

        /** その武器の弾（レールライフルは徹甲弾、リペアインジェクターはナノマシン溶液、弓・クロスボウは矢など）。弾を使わなければ null */
        @Nullable
        static java.util.function.Predicate<ItemStack> ammoFor(ItemStack weapon) {
            if (weapon.isEmpty()) return null;
            if (weapon.getItem() instanceof com.automatavalkyries.weapon.RailRifleItem) return s -> s.is(ModWeapons.AMMO_AP_ROUND.get());
            if (weapon.getItem() instanceof com.automatavalkyries.weapon.RepairInjectorItem) return s -> s.is(ModWeapons.NANO_SOLUTION.get());
            if (weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem pw) return pw.getAllSupportedProjectiles();
            return null;
        }
    }
}
