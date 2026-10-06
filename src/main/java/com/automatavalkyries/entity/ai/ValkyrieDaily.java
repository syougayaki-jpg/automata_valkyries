package com.automatavalkyries.entity.ai;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import com.automatavalkyries.machine.ValkyrieChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 戦術兵姫の日常・感情の AI（段階 4、2026-10-06。docs/SPEC_VALKYRIE_GUI_AI.md §4・拡張仕様書 §12）。
 * <ul>
 *   <li>自分で充電（{@link SelfCharge}）: 電力が 30% を割ったら、近くの兵姫用充電台へ行って座り、整備しながら充電する。満タンで同調率 +2%</li>
 *   <li>腰掛け（{@link Sit}）: 待機・拠点防衛の間、半径 12 の階段・ベッドに時々腰掛けて足をぶらぶらさせる</li>
 *   <li>お掃除（{@link Clean}）: 拠点（待機ならその場）から半径 12 の落ちている物を、除外リスト以外みな拾って（持ち物タブの拾った物の枠）、近くのチェストへ入れる</li>
 *   <li>作業の見守り（{@link WatchWork}）: 追従中、持ち主が作業台・金床・かまど等を開いていると、後ろから覗き込む</li>
 *   <li>気持ち（{@link Life}）: 場面のセリフ（朝・再会・独り言・夜・雨・低電力・持ち主の大怪我・放っておかれた）と表情・仕草、同調率の増減</li>
 * </ul>
 * 戦っている間（狙う相手がいる・後退中）はどれもしない。数値は【仮】
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieDaily {

    /** 日常の範囲（拠点・待機の場所から） */
    public static final int RANGE = 12;
    /** 自分で充電を始める・やめる電力の割合 */
    public static final float CHARGE_START = 0.3F, CHARGE_DONE = 0.99F;
    /** 充電台から 1 Tick に受け取る上限（FE） */
    public static final int CHARGE_RATE = 2_000;
    /** 好物で同調率が上がる 1 日の回数【仮】 */
    public static final int GIFTS_PER_DAY = 10;

    private ValkyrieDaily() {}

    static boolean idle(ValkyrieEntity v) {
        return !v.isShutdown() && !v.isAsleep() && v.getTarget() == null && !v.combat().retreating && !v.isPassenger();
    }

    /** 日常の中心（拠点防衛・巡回は拠点、待機は待機を始めた所、追従は自分の居る所） */
    static BlockPos center(ValkyrieEntity v) {
        BlockPos home = v.getHome();
        if (v.getMode() == Mode.WAIT) return v.life().waitSpot != null ? v.life().waitSpot : v.blockPosition();
        return home != null && v.getMode() != Mode.FOLLOW ? home : v.blockPosition();
    }

    // ------------------------------------------------------------------ 気持ち（セリフ・同調率の増減）

    /** 兵姫ごとの日常の状態 */
    public static final class Life {
        private final ValkyrieEntity v;
        private final Map<String, Long> lastTalk = new HashMap<>();
        long morningDay = -1, giftDay = -1, lastOwnerNear, sleptAt = -1;
        int giftsToday;
        boolean wasNight, wasRaining, neglected;
        /** 待機の場所（待機で腰掛け・お掃除するときの中心） */
        @Nullable public BlockPos waitSpot;

        public Life(ValkyrieEntity v) {
            this.v = v;
        }

        /** 場面ごとのセリフの間隔（Tick） */
        private static long cooldown(String situation) {
            return switch (situation) {
                case "idle" -> 6000;
                case "greet", "wake_morning", "night", "rain", "lonely" -> 2400;
                case "pat", "gift_candy", "gift_ice", "repaired" -> 200;
                case "owner_saved" -> 6000;
                case "sync_up", "revived", "boss_down", "charge_full" -> 40;
                default -> 1200;
            };
        }

        public boolean mayTalk(String situation) {
            long now = v.level().getGameTime();
            Long last = lastTalk.get(situation);
            if (last != null && now - last < cooldown(situation)) return false;
            lastTalk.put(situation, now);
            return true;
        }

        /** 毎 Tick（サーバー、機能停止していない間） */
        public void tick() {
            if (!(v.level() instanceof ServerLevel level) || v.tickCount % 20 != 0) return;
            if (v.getMode() == Mode.WAIT && waitSpot == null && v.onGround()) waitSpot = v.blockPosition(); // 待機を始めたのが空中だった
            long now = level.getGameTime();
            // 放っておかれた: 電力 0 で 10 分以上（拡張仕様書 §12.1 -5%）
            if (v.isAsleep()) {
                if (sleptAt < 0) sleptAt = now;
                if (!neglected && now - sleptAt >= 12000) {
                    neglected = true;
                    v.addSync(-5.0F, "neglect");
                    v.talk("lonely");
                    v.gesture(ValkyrieEntity.GESTURE_DEJECTED);
                }
                return;
            }
            Player owner = v.getOwner();
            boolean ownerNear = owner != null && owner.isAlive() && v.distanceToSqr(owner) < 16 * 16;
            if (!idle(v)) {
                if (ownerNear) lastOwnerNear = now;
                return;
            }
            // 朝の起動（夜明けの後、その日はじめて持ち主がそばにいるとき）
            long day = level.getDayTime() / 24000L, time = level.getDayTime() % 24000L;
            if (ownerNear && day != morningDay && time < 3000) {
                morningDay = day;
                v.talk("wake_morning");
                v.gesture(ValkyrieEntity.GESTURE_WAVE);
            }
            // 再会: 2 分以上離れていた持ち主が 8 ブロック以内へ
            if (owner != null && v.distanceToSqr(owner) < 8 * 8 && lastOwnerNear > 0 && now - lastOwnerNear > 2400) {
                v.talk("greet");
                v.gesture(ValkyrieEntity.GESTURE_WAVE);
                v.mood(1, 60);
            }
            if (ownerNear) lastOwnerNear = now;
            // 夜が来た・雨が降り出した
            boolean night = time >= 13000 && time < 23000;
            if (night && !wasNight && ownerNear) v.talk("night");
            wasNight = night;
            boolean raining = level.isRainingAt(v.blockPosition().above());
            if (raining && !wasRaining && ownerNear) v.talk("rain");
            wasRaining = raining;
            // 低電力（20% 以下）
            if (v.getEnergy() <= v.maxEnergy() / 5 && ownerNear) v.talk("low_power");
            // 独り言（持ち主がそばにいるとき、ときどき）
            if (ownerNear && v.getRandom().nextInt(90) == 0) {
                v.talk(v.isSitting() ? "sit" : "idle");
            }
        }

        /** 電力切れの休眠から起きた: 長く放っておかれた後は同調率 +10%（拡張仕様書 §12.1「スリープからの復旧」） */
        public void onWake() {
            long now = v.level().getGameTime();
            if (sleptAt >= 0 && now - sleptAt >= 600) {
                v.addSync(10.0F, "rewake");
                v.talk("sleepy");
                v.gesture(ValkyrieEntity.GESTURE_NOD);
            }
            sleptAt = -1;
            neglected = false;
        }

        /** 好物を受け取る（1 日 {@link #GIFTS_PER_DAY} 回まで同調率 +5%） */
        public void gift(boolean candy) {
            long day = v.level().getDayTime() / 24000L;
            if (day != giftDay) {
                giftDay = day;
                giftsToday = 0;
            }
            if (giftsToday < GIFTS_PER_DAY) {
                giftsToday++;
                v.addSync(5.0F, "gift");
            }
            v.talk(candy ? "gift_candy" : "gift_ice");
            v.gesture(ValkyrieEntity.GESTURE_JOY);
            v.mood(1, 80);
            if (v.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.HEART, v.getX(), v.getY(1.1), v.getZ(), 4, 0.3, 0.2, 0.3, 0);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("MorningDay", morningDay);
            tag.putLong("GiftDay", giftDay);
            tag.putInt("GiftsToday", giftsToday);
            return tag;
        }

        public void load(CompoundTag tag) {
            if (tag.contains("MorningDay")) morningDay = tag.getLong("MorningDay");
            if (tag.contains("GiftDay")) giftDay = tag.getLong("GiftDay");
            giftsToday = tag.getInt("GiftsToday");
        }
    }

    /** 持ち主が大きな傷（最大 HP の 30% 以上を 1 撃で）を受けたら、そばの兵姫が心配する */
    @SubscribeEvent
    public static void onOwnerDamaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner) || owner.level().isClientSide || event.getAmount() < owner.getMaxHealth() * 0.3F) return;
        for (ValkyrieEntity v : owner.level().getEntitiesOfClass(ValkyrieEntity.class, owner.getBoundingBox().inflate(24.0))) {
            if (owner.getUUID().equals(v.getOwnerUUID()) && !v.isShutdown() && !v.isAsleep() && v.talk("owner_hurt")) {
                v.mood(5, 40); // 驚き
            }
        }
    }

    // ------------------------------------------------------------------ 自分で充電

    /** 電力が減ったら近くの兵姫用充電台で充電する（座って整備の動き） */
    public static class SelfCharge extends Goal {
        private final ValkyrieEntity v;
        @Nullable private ValkyrieChargerBlockEntity charger;
        private int repath, search, walking;
        private boolean seated;

        public SelfCharge(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private float power() {
            return v.getEnergy() / (float) v.maxEnergy();
        }

        @Override
        public boolean canUse() {
            if (!idle(v) || power() >= CHARGE_START) return false;
            if (--search > 0 || !(v.level() instanceof ServerLevel level)) return false;
            search = 100;
            BlockPos c = v.getOwner() != null && v.getMode() == Mode.FOLLOW ? v.getOwner().blockPosition() : center(v);
            for (ValkyrieChargerBlockEntity ch : ValkyrieChargerBlockEntity.findNear(level, c, 32)) {
                if (ch.stored() > 0 && ch.claim(v.getUUID())) {
                    charger = ch;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return charger != null && !charger.isRemoved() && !v.isShutdown() && v.getTarget() == null && power() < CHARGE_DONE
                    && (seated ? charger.stored() > 0 : walking < 400); // 20 秒歩いても着かなければ諦める
        }

        @Override
        public void start() {
            seated = false;
            repath = 0;
            walking = 0;
            v.setSitting(false);
        }

        @Override
        public void stop() {
            if (charger != null) {
                charger.setCharging(false);
                charger.release(v.getUUID());
                if (seated && power() >= CHARGE_DONE) { // 満タンまで充電した（拡張仕様書 §12.1 +2%）
                    v.addSync(2.0F, "full_charge");
                    v.talk("charge_full");
                    v.gesture(ValkyrieEntity.GESTURE_JOY);
                }
            }
            charger = null;
            seated = false;
            v.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (charger == null) return;
            charger.claim(v.getUUID()); // 使っている間は取り続ける
            Vec3 seat = charger.seatPos();
            if (!seated) {
                // 2 ブロック以内か、道の終わりで 4 ブロック以内なら座る（台の上まで道が取れないことがある）
                double d2 = v.position().distanceToSqr(seat);
                walking++;
                if (d2 > 2.0 * 2.0 && !(v.getNavigation().isDone() && d2 < 4.0 * 4.0)) {
                    if (--repath <= 0) {
                        repath = 10;
                        v.getNavigation().moveTo(seat.x, seat.y, seat.z, ValkyrieGoals.pathSpeed(v, 1.1));
                    }
                    return;
                }
                seated = true;
                v.getNavigation().stop();
                v.talk("charge_start");
            }
            // 座って整備しながら充電
            v.moveTo(seat.x, seat.y, seat.z, charger.seatYaw(), 0.0F);
            v.setYBodyRot(charger.seatYaw());
            v.setYHeadRot(charger.seatYaw());
            v.setDeltaMovement(Vec3.ZERO);
            v.setCharging(true);
            charger.setCharging(true);
            int want = Math.min(Math.round(CHARGE_RATE * ValkyrieUnitEffects.chargeScale(v)), v.maxEnergy() - v.getEnergy()); // メンテナンスフレームは 1.5 倍速
            if (want > 0) v.setEnergy(v.getEnergy() + charger.drain(want));
        }
    }

    // ------------------------------------------------------------------ 腰掛け

    /** 待機・拠点防衛の間、近くの階段・ベッドに時々腰掛ける */
    public static class Sit extends Goal {
        private final ValkyrieEntity v;
        @Nullable private BlockPos seat;
        private int repath, timer;
        private boolean seated;

        public Sit(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if ((v.getMode() != Mode.WAIT && v.getMode() != Mode.DEFEND) || !idle(v) || v.getRandom().nextInt(200) != 0) return false;
            seat = findSeat();
            return seat != null;
        }

        @Nullable
        private BlockPos findSeat() {
            BlockPos c = center(v);
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE, -3, -RANGE), c.offset(RANGE, 3, RANGE))) {
                BlockState st = v.level().getBlockState(p);
                boolean stair = st.getBlock() instanceof StairBlock && st.getValue(StairBlock.HALF) == Half.BOTTOM;
                boolean bed = st.getBlock() instanceof BedBlock;
                if (!stair && !bed) continue;
                if (!v.level().getBlockState(p.above()).getCollisionShape(v.level(), p.above()).isEmpty()) continue;
                double d = p.distSqr(v.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
            return best;
        }

        @Override
        public boolean canContinueToUse() {
            return seat != null && idle(v) && (v.getMode() == Mode.WAIT || v.getMode() == Mode.DEFEND) && timer > 0;
        }

        @Override
        public void start() {
            seated = false;
            repath = 0;
            timer = 600 + v.getRandom().nextInt(1200); // 30〜90 秒
        }

        @Override
        public void stop() {
            v.setSitting(false);
            seat = null;
            seated = false;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (seat == null) return;
            BlockState st = v.level().getBlockState(seat);
            boolean stair = st.getBlock() instanceof StairBlock;
            if (!stair && !(st.getBlock() instanceof BedBlock)) {
                timer = 0;
                return;
            }
            Vec3 at = Vec3.atBottomCenterOf(seat).add(0, stair ? 0.5 : 0.5625, 0);
            if (!seated) {
                if (v.position().distanceToSqr(at) > 1.5 * 1.5) {
                    if (--repath <= 0) {
                        repath = 10;
                        v.getNavigation().moveTo(at.x, at.y, at.z, ValkyrieGoals.pathSpeed(v, 1.0));
                    }
                    if (v.getNavigation().isDone() && v.position().distanceToSqr(at) > 9) timer -= 20;
                    return;
                }
                seated = true;
                v.getNavigation().stop();
                v.talk("sit");
            }
            timer--;
            // 階段は上り口と反対（背もたれ側を背に）へ向いて座る
            float yaw = stair ? st.getValue(StairBlock.FACING).getOpposite().toYRot() : v.getYRot();
            v.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            v.setYBodyRot(yaw);
            v.setDeltaMovement(Vec3.ZERO);
            v.setSitting(true);
        }
    }

    // ------------------------------------------------------------------ お掃除

    /** 落ちている物（除外リスト以外）を拾って、近くのチェストへ入れる */
    public static class Clean extends Goal {
        private final ValkyrieEntity v;
        @Nullable private ItemEntity item;
        @Nullable private BlockPos chest;
        private int repath, scan, stuck;
        /** 道が取れなかったチェスト（しばらく選ばない） */
        private final java.util.Set<BlockPos> unreachable = new java.util.HashSet<>();

        public Clean(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean full() {
            return v.getInventory().pickupFull();
        }

        @Override
        public boolean canUse() {
            if (v.getMode() == Mode.FOLLOW || !idle(v) || --scan > 0) return false;
            scan = 40;
            if (v.getInventory().hasPickup() && (full() || findItem() == null)) {
                chest = findChest();
                if (chest != null) return true;
            }
            if (full()) return false;
            item = findItem();
            return item != null;
        }

        @Nullable
        private ItemEntity findItem() {
            BlockPos c = center(v);
            ItemEntity best = null;
            double bestD = Double.MAX_VALUE;
            double range = RANGE * ValkyrieUnitEffects.pickupScale(v); // サブアームで広く
            for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(range, 4, range),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !v.orders.excludes(e.getItem()))) {
                double d = e.distanceToSqr(v);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        @Nullable
        private BlockPos findChest() {
            BlockPos porter = ValkyriePorter.cleanDestination(v); // ロジック: ポーター（拾った物を運び先へ）
            if (porter != null && !unreachable.contains(porter)) return porter;
            BlockPos c = center(v);
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-RANGE - 4, -4, -RANGE - 4), c.offset(RANGE + 4, 4, RANGE + 4))) {
                BlockEntity be = v.level().getBlockEntity(p);
                if (be == null || !(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                        || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) || unreachable.contains(p)) continue;
                double d = p.distSqr(c); // 拠点（待機の場所）にいちばん近いチェスト（その時の自分の位置ではなく。いつも同じチェストへ運ぶ）
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
            return best;
        }

        @Override
        public boolean canContinueToUse() {
            return idle(v) && v.getMode() != Mode.FOLLOW && stuck < 200
                    && ((item != null && item.isAlive() && !full()) || (chest != null && v.getInventory().hasPickup()));
        }

        @Override
        public void start() {
            repath = 0;
            stuck = 0;
        }

        @Override
        public void stop() {
            if (chest != null && stuck >= 200) unreachable.add(chest); // 10 秒たっても届かなかった
            if (v.tickCount % 6000 == 0) unreachable.clear();
            item = null;
            chest = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            stuck++;
            if (item != null) {
                if (v.distanceToSqr(item) < 1.6 * 1.6) {
                    pickUp(item);
                    item = full() ? null : findItem();
                    if (item == null && v.getInventory().hasPickup()) chest = findChest();
                    stuck = 0;
                    return;
                }
                if (--repath <= 0) {
                    repath = 10;
                    v.getNavigation().moveTo(item, ValkyrieGoals.pathSpeed(v, 1.1));
                }
                return;
            }
            if (chest != null) {
                Vec3 c = Vec3.atCenterOf(chest);
                // 手の届く所（3.2）か、道の終わりで 5 以内なら入れる（チェストの真横まで道が取れないことがある）
                double d2 = v.position().distanceToSqr(c);
                if (d2 < 3.2 * 3.2 || (v.getNavigation().isDone() && d2 < 5.0 * 5.0)) {
                    deliver(chest);
                    chest = null;
                    stuck = 0;
                    return;
                }
                if (--repath <= 0) {
                    repath = 10;
                    BlockPos stand = standBeside(v, chest);
                    if (stand != null) v.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, ValkyrieGoals.pathSpeed(v, 1.1));
                    else v.getNavigation().moveTo(c.x, c.y, c.z, ValkyrieGoals.pathSpeed(v, 1.1));
                }
            }
        }

        private void pickUp(ItemEntity e) {
            ItemStack rest = v.getInventory().addPickup(e.getItem());
            v.take(e, e.getItem().getCount() - rest.getCount());
            if (rest.isEmpty()) e.discard();
            else e.setItem(rest);
        }

        private void deliver(BlockPos pos) {
            depositInto(v, pos);
            if (!v.getInventory().hasPickup()) {
                v.talk("clean_done");
                v.gesture(ValkyrieEntity.GESTURE_NOD);
            }
        }
    }

    /** 入れ物の横の立てる所（空気で、足元が固い）。兵姫にいちばん近い所。無ければ null（お掃除・ポーター） */
    @Nullable
    static BlockPos standBeside(ValkyrieEntity v, BlockPos chestPos) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos p = chestPos.relative(d).above(dy);
                if (!v.level().getBlockState(p).getCollisionShape(v.level(), p).isEmpty()
                        || !v.level().getBlockState(p.above()).getCollisionShape(v.level(), p.above()).isEmpty()
                        || v.level().getBlockState(p.below()).getCollisionShape(v.level(), p.below()).isEmpty()) continue;
                double dist = p.distSqr(v.blockPosition());
                if (dist < bestD) {
                    bestD = dist;
                    best = p;
                }
            }
        }
        return best;
    }

    /** 拾った物を入れ物へ入れる（入りきらない物は持ったまま）。蓋を少し開け閉めする（お掃除・ポーター） */
    static void depositInto(ValkyrieEntity v, BlockPos pos) {
        BlockEntity be = v.level().getBlockEntity(pos);
        if (be == null) return;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (handler == null) return;
        v.getInventory().deliverPickups(handler);
        v.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        v.level().blockEvent(pos, be.getBlockState().getBlock(), 1, 1);
        com.automatavalkyries.weapon.WeaponUtil.later((ServerLevel) v.level(), 20, () -> v.level().blockEvent(pos, be.getBlockState().getBlock(), 1, 0));
        v.playSound(net.minecraft.sounds.SoundEvents.CHEST_OPEN, 0.5F, 1.1F);
    }

    // ------------------------------------------------------------------ 作業の見守り

    /** 追従中、持ち主が作業台・金床・かまど等を開いていると、後ろから覗き込む */
    public static class WatchWork extends Goal {
        private final ValkyrieEntity v;
        @Nullable private Player owner;
        private int repath;

        public WatchWork(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private static boolean working(Player p) {
            var m = p.containerMenu;
            return m instanceof CraftingMenu || m instanceof AnvilMenu || m instanceof AbstractFurnaceMenu
                    || m instanceof ItemCombinerMenu || m instanceof StonecutterMenu
                    || m instanceof com.automatavalkyries.machine.MachineMenu;
        }

        @Override
        public boolean canUse() {
            if (v.getMode() != Mode.FOLLOW || !idle(v)) return false;
            Player o = v.getOwner();
            if (o == null || !working(o) || v.distanceToSqr(o) > 16 * 16) return false;
            owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && working(owner) && idle(v);
        }

        @Override
        public void start() {
            repath = 0;
            v.talk("watch_work");
            v.gesture(ValkyrieEntity.GESTURE_PEEK);
        }

        @Override
        public void stop() {
            owner = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            Vec3 look = owner.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            // 持ち主の斜め後ろ（右肩の向こう）
            Vec3 spot = owner.position().subtract(flat.scale(1.1)).add(new Vec3(-flat.z, 0, flat.x).scale(0.7));
            if (v.position().distanceToSqr(spot) > 0.6 * 0.6 && --repath <= 0) {
                repath = 10;
                v.getNavigation().moveTo(spot.x, spot.y, spot.z, ValkyrieGoals.pathSpeed(v, 1.0));
            }
            // 持ち主の手元（見ている先の少し前）を覗き込む
            Vec3 target = owner.getEyePosition().add(look.scale(1.5));
            v.getLookControl().setLookAt(target.x, target.y, target.z, 20.0F, 30.0F);
            if (v.tickCount % 60 == 0 && v.getRandom().nextBoolean()) v.gesture(ValkyrieEntity.GESTURE_PEEK);
        }
    }

    /** 兵姫が持ち主と一緒にボスを倒した（{@code ValkyrieRecords} から） */
    public static void onBossDown(ValkyrieEntity v) {
        v.talk("boss_down");
        v.gesture(ValkyrieEntity.GESTURE_JOY);
        v.mood(1, 80);
    }

    /** 持ち主をかばって、持ち主が生き延びた（{@code ValkyrieCombat.Guard} から） */
    public static void onOwnerSaved(ValkyrieEntity v) {
        if (!v.talk("owner_saved")) return; // 5 分に 1 回まで（かばうたびに上がらないよう）
        v.addSync(10.0F, "rescue"); // 拡張仕様書 §12.1「プレイヤー瀕死救助」+10%
        v.gesture(ValkyrieEntity.GESTURE_NOD);
    }
}
