package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 戦術兵姫が兵装をどう使うか（段階 3、2026-10-05。SPEC_VALKYRIE_GUI_AI §3「戦い方は持った兵装で自動に変わる」）。
 * <p>兵装ごとの「型」（{@link Art}）: 近接・射撃・支援の別、間合い、通常攻撃（{@link Art#basic}）と技（{@link Art#skill}）。
 * 戦闘 AI（{@code entity/ai/ValkyrieCombat}）は相手へ向きを合わせてから呼ぶので、兵装の処理は持ち主の視線（getLookAngle）で撃ってよい。
 * 通常攻撃が無い近接の型は、AI が振り（swing。T4 の通常攻撃も {@code onEntitySwing} で出る）とバニラの近接攻撃をする。
 * 溜めて撃つ技（{@link Art#chargedSkill}）は、AI がアイテムの使用（startUsingItem → 溜めきって離す）で撃つので、電力の引き出しも兵装の処理のまま。</p>
 * <p>電力は兵装の内蔵バッテリーから使い、兵姫は自分の電力から毎 Tick 足す（{@link WeaponPower#topUp}）。
 * 表に無い兵装は、技の兵装（{@link SkillWeaponItem}）なら技、溜めて撃つ兵装（{@link ChargedWeaponItem}）なら溜め撃ち、それ以外は近接の振りだけ</p>
 */
public final class ValkyrieArts {

    private ValkyrieArts() {}

    /** 立ち回り: 近接は詰める、射撃は作戦指示の「保つ距離」を取る、支援は持ち主のそばにいる */
    public enum Style { MELEE, RANGED, SUPPORT }

    /** 兵姫の行動（サーバー、相手へ向いた後）。使えたら true（電力が足りない・相手がいない等は false で、間隔も使わない） */
    @FunctionalInterface
    public interface Action {
        boolean run(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target);
    }

    /** 使ってよいときか（例: 回復は HP が減ったときだけ、守りは持ち主が危ないときだけ） */
    @FunctionalInterface
    public interface When {
        boolean test(LivingEntity user, @Nullable LivingEntity target);
    }

    /**
     * 兵装の型。
     *
     * @param style         立ち回り
     * @param reach         通常攻撃が届く距離（近接は 2〜4、射撃は射程）
     * @param basicInterval 通常攻撃の間隔（Tick）
     * @param basic         通常攻撃（null なら近接の振り）
     * @param skillRange    技を使う距離
     * @param skillCooldown 技の間隔（Tick、兵姫側の間隔。兵装のクールタイムとは別）
     * @param skill         技（null なら無し）
     * @param chargedSkill  技が溜め撃ち（AI がアイテムの使用で溜めて離す）
     * @param when          技を使ってよいとき（null ならいつでも）
     * @param offhand       左手でも働くか（モジュール・盾）
     */
    public record Art(Style style, double reach, int basicInterval, @Nullable Action basic,
                      double skillRange, int skillCooldown, @Nullable Action skill, boolean chargedSkill,
                      @Nullable When when, boolean offhand) {

        public static Builder melee(double reach) { return new Builder(Style.MELEE, reach); }
        public static Builder ranged(double reach) { return new Builder(Style.RANGED, reach); }
        public static Builder support(double reach) { return new Builder(Style.SUPPORT, reach); }

        public boolean wants(LivingEntity user, @Nullable LivingEntity target) {
            return when == null || when.test(user, target);
        }
    }

    public static final class Builder {
        private final Style style;
        private final double reach;
        private int basicInterval = 20;
        private @Nullable Action basic;
        private double skillRange;
        private int skillCooldown = 100;
        private @Nullable Action skill;
        private boolean charged;
        private @Nullable When when;
        private boolean offhand;

        private Builder(Style style, double reach) {
            this.style = style;
            this.reach = reach;
            this.skillRange = reach;
        }

        /** 通常攻撃（間隔 Tick） */
        public Builder basic(int interval, Action action) { basicInterval = interval; basic = action; return this; }
        /** 近接の振りの間隔（basic を使わない近接） */
        public Builder swing(int interval) { basicInterval = interval; return this; }
        /** 技（届く距離・間隔 Tick） */
        public Builder skill(double range, int cooldown, Action action) { skillRange = range; skillCooldown = cooldown; skill = action; return this; }
        /** 溜め撃ちの技（届く距離・間隔 Tick） */
        public Builder charged(double range, int cooldown) { skillRange = range; skillCooldown = cooldown; charged = true; return this; }
        public Builder when(When condition) { when = condition; return this; }
        public Builder offhand() { offhand = true; return this; }

        public Art build() {
            return new Art(style, reach, basicInterval, basic, skillRange, skillCooldown, skill, charged, when, offhand);
        }
    }

    private static final Map<Item, Art> TABLE = new HashMap<>();
    private static boolean filled;

    public static void register(Supplier<? extends Item> item, Art art) {
        TABLE.put(item.get(), art);
    }

    /** 持った物の型（兵装でなければ null。素手・普通の剣は AI がバニラの近接で戦う） */
    @Nullable
    public static Art of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!filled) fill();
        Art art = TABLE.get(stack.getItem());
        if (art != null) return art;
        Item item = stack.getItem();
        if (item instanceof SkillWeaponItem skill) {
            return Art.melee(3.0).swing(12).skill(8.0, Math.max(40, skill.cooldownTicks()), ValkyrieArts::skillOf).build();
        }
        if (item instanceof ChargedWeaponItem) return Art.ranged(24.0).charged(24.0, 60).build();
        if (item instanceof TacticalWeaponItem) return Art.melee(3.0).swing(12).build();
        return null;
    }

    /** 盾を持っているか（持ち主をかばうのは盾持ちだけ。SPEC_VALKYRIE_GUI_AI §3） */
    public static boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.is(Items.SHIELD)
                || stack.is(ModWeapons.POLAR_DEFLECTOR.get()) || stack.is(ModWeapons.AEGIS_BARRIER.get());
    }

    /** 技の兵装の技を兵姫が出す（電力を確かめて使い、効果音・動き。{@link SkillWeaponItem#use} のプレイヤー以外版） */
    public static boolean skillOf(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target) {
        return stack.getItem() instanceof SkillWeaponItem skill && skill.valkyrieSkill(level, user, stack);
    }

    // ------------------------------------------------------------------ 兵装の処理の入口（表から使う）

    /**
     * プレイヤー以外も使える兵装の入口（右クリックの処理がプレイヤー専用の兵装が持つ）。電力・弾・狙う相手を確かめ、
     * 出せなかったら false（電力も弾も使わない）。間隔は兵姫の AI が数える
     */
    public interface Usable {
        boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target);
    }

    /** {@link Usable} の兵装を使う */
    public static boolean use(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target) {
        return stack.getItem() instanceof Usable usable && usable.valkyrieUse(level, user, stack, target);
    }

    /** 振るだけ（T4 の通常攻撃・量子ブレードの斬撃は振りで出る。{@code onEntitySwing}） */
    public static boolean swing(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target) {
        user.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /**
     * 近接の一撃: 振り（T4 の通常攻撃も出る）と、hitReach 以内ならバニラの近接攻撃＋兵装の当てたときの処理（hurtEnemy）。
     * Mob の doHurtTarget は hurtEnemy を呼ばないので、電力・連撃・吸血などの当てたときの処理はここで呼ぶ
     */
    public static Action strike(double hitReach) {
        return (level, user, stack, target) -> {
            user.swing(InteractionHand.MAIN_HAND, true);
            if (target == null || !(user instanceof Mob mob) || user.distanceTo(target) > hitReach + target.getBbWidth() * 0.5) return true;
            if (mob.doHurtTarget(target) && user.getMainHandItem() == stack) stack.getItem().hurtEnemy(stack, target, user);
            return true;
        };
    }

    /** 持っている手（持っていなければ null） */
    @Nullable
    public static InteractionHand handOf(LivingEntity user, ItemStack stack) {
        if (user.getMainHandItem() == stack) return InteractionHand.MAIN_HAND;
        if (user.getOffhandItem() == stack) return InteractionHand.OFF_HAND;
        return null;
    }

    /**
     * 押し続けて使う兵装（光線・連射・障壁）を ticks の間使う: アイテムの使用を始め、毎 Tick 相手へ向き直り、時間が来たら離す。
     * 処理（電力・撃つ）はアイテムの onUseTick のまま。もう同じ物を使っている最中なら続ける（true）
     */
    public static boolean hold(ServerLevel level, LivingEntity user, ItemStack stack, @Nullable LivingEntity target, int ticks) {
        InteractionHand hand = handOf(user, stack);
        if (hand == null) return false;
        if (user.isUsingItem()) return user.getUseItem().getItem() == stack.getItem();
        user.startUsingItem(hand);
        if (!user.isUsingItem()) return false;
        keep(level, user, stack.getItem(), target, ticks);
        return true;
    }

    private static void keep(ServerLevel level, LivingEntity user, Item item, @Nullable LivingEntity target, int left) {
        WeaponUtil.later(level, 1, () -> {
            if (!user.isAlive() || !user.isUsingItem() || user.getUseItem().getItem() != item) return;
            if (left <= 1) {
                user.releaseUsingItem();
                return;
            }
            if (target != null && target.isAlive() && !target.isRemoved()) face(user, target);
            keep(level, user, item, target, left - 1);
        });
    }

    /** 相手の胸へ向き直る（視線 getLookAngle が相手へ向く） */
    public static void face(LivingEntity user, Entity target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(user.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        user.setYRot(yaw);
        user.setYHeadRot(yaw);
        user.yBodyRot = yaw;
        user.setXRot(pitch);
    }

    /**
     * 兵姫の内蔵弾薬庫（{@link com.automatavalkyries.entity.ValkyrieInventory} の弾薬の枠）から ammo を最大 max 個取り出す。取れた数を返す。
     * 兵姫以外は 0
     */
    public static int takeAmmo(LivingEntity user, Item ammo, int max) {
        if (max <= 0 || !(user instanceof com.automatavalkyries.entity.ValkyrieEntity v)) return 0;
        com.automatavalkyries.entity.ValkyrieInventory inventory = v.getInventory();
        int taken = 0;
        for (int i = 0; i < com.automatavalkyries.entity.ValkyrieInventory.MAX_AMMO && taken < max; i++) {
            int slot = com.automatavalkyries.entity.ValkyrieInventory.AMMO + i;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.is(ammo)) continue;
            int n = Math.min(max - taken, stack.getCount());
            stack.shrink(n);
            taken += n;
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        return taken;
    }

    /** 兵姫の内蔵弾薬庫に ammo があるか */
    public static boolean hasAmmo(LivingEntity user, Item ammo) {
        if (!(user instanceof com.automatavalkyries.entity.ValkyrieEntity v)) return false;
        for (int i = 0; i < com.automatavalkyries.entity.ValkyrieInventory.MAX_AMMO; i++) {
            if (v.getInventory().getItem(com.automatavalkyries.entity.ValkyrieInventory.AMMO + i).is(ammo)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 使ってよいとき（When の部品）

    /** 味方か（自分・持ち主・同じ持ち主の兵姫・持ち主のペット・同じチーム） */
    public static boolean friend(LivingEntity user, @Nullable Entity e) {
        if (e == null) return false;
        if (e == user) return true;
        UUID owner = user instanceof com.automatavalkyries.entity.ValkyrieEntity v ? v.getOwnerUUID() : null;
        if (owner != null) {
            if (e.getUUID().equals(owner)) return true;
            if (e instanceof com.automatavalkyries.entity.ValkyrieEntity other && owner.equals(other.getOwnerUUID())) return true;
            if (e instanceof TamableAnimal pet && owner.equals(pet.getOwnerUUID())) return true;
        }
        return user.isAlliedTo(e);
    }

    /** 敵か（今の相手・モンスター・味方を狙っている者） */
    public static boolean foe(LivingEntity user, @Nullable LivingEntity target, LivingEntity e) {
        if (!e.isAlive() || e.isSpectator() || friend(user, e)) return false;
        return e == target || e instanceof Enemy || (e instanceof Mob mob && friend(user, mob.getTarget()));
    }

    /** 自分のまわり radius に敵が n 体以上 */
    public static When enemiesWithin(double radius, int n) {
        return (user, target) -> count(user, target, user.position(), radius) >= n;
    }

    /** 相手の距離が [min, max] */
    public static When targetBetween(double min, double max) {
        return (user, target) -> {
            if (target == null) return false;
            double d = user.distanceTo(target);
            return d >= min && d <= max;
        };
    }

    /** 自分のまわり radius に、こちらへ飛んでくる敵の飛び道具がある */
    public static When projectilesNear(double radius) {
        return (user, target) -> !user.level().getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(radius), p -> {
            if (friend(user, p.getOwner())) return false;
            Vec3 v = p.getDeltaMovement();
            return v.lengthSqr() > 0.01 && v.dot(user.position().add(0, user.getBbHeight() * 0.5, 0).subtract(p.position())) > 0;
        }).isEmpty();
    }

    /** radius 以内の味方（自分を含む）の誰かの HP が ratio 未満 */
    public static When friendBelow(double radius, float ratio) {
        return (user, target) -> hurtFriend(user, radius, ratio) != null;
    }

    /** 自分の HP が ratio より上（体力を削る技の前に） */
    public static When healthAbove(float ratio) {
        return (user, target) -> user.getHealth() > user.getMaxHealth() * ratio;
    }

    /** 大技に見合う相手: 最大 HP が minHealth 以上か、相手のまわり crowdRadius に敵が crowd 体以上（雑魚 1 体に大技を使わない） */
    public static When worthy(float minHealth, double crowdRadius, int crowd) {
        return (user, target) -> target != null && (target.getMaxHealth() >= minHealth || count(user, target, target.position(), crowdRadius) >= crowd);
    }

    public static When any(When... conditions) {
        return (user, target) -> {
            for (When w : conditions) if (w.test(user, target)) return true;
            return false;
        };
    }

    public static When all(When... conditions) {
        return (user, target) -> {
            for (When w : conditions) if (!w.test(user, target)) return false;
            return true;
        };
    }

    /** radius 以内で HP の割合が一番低い、ratio 未満の味方（自分・持ち主・兵姫・ペット）。いなければ null */
    @Nullable
    public static LivingEntity hurtFriend(LivingEntity user, double radius, float ratio) {
        LivingEntity best = null;
        float bestRatio = ratio;
        for (LivingEntity e : user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && friend(user, e) && e.distanceTo(user) <= radius)) {
            if (e instanceof Player p && p.getAbilities().invulnerable) continue;
            float r = e.getHealth() / Math.max(1.0F, e.getMaxHealth());
            if (r < bestRatio) {
                bestRatio = r;
                best = e;
            }
        }
        return best;
    }

    /** center のまわり radius の敵の数 */
    private static int count(LivingEntity user, @Nullable LivingEntity target, Vec3 center, double radius) {
        return user.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> e != user && foe(user, target, e) && e.position().distanceTo(center) <= radius + e.getBbWidth() * 0.5).size();
    }

    /** 兵装ごとの型の表（{@link ValkyrieArtTable}）を一度だけ読む */
    private static synchronized void fill() {
        if (filled) return;
        filled = true;
        ValkyrieArtTable.fill();
    }

    /** 登録の補助（表の側から使う） */
    static void put(RegistryObject<? extends Item> item, Art art) {
        TABLE.put(item.get(), art);
    }
}
