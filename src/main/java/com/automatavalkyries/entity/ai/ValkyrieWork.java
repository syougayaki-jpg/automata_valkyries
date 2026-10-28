package com.automatavalkyries.entity.ai;

import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.entity.ValkyrieEntity.Mode;
import com.automatavalkyries.entity.ValkyrieInventory;
import com.automatavalkyries.item.unit.ValkyrieUnits.BackpackKind;
import com.automatavalkyries.item.unit.ValkyrieUnits.ChipKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 非戦闘のユニット（2026-10-29 ユーザー指定「背部ユニットやロジックチップなどを非戦闘用に（付けていると戦闘できない）10 種類ほど」。
 * 質問で: 農業・畜産／採掘・伐採／釣り・料理／持ち主の補助、襲われたら逃げて持ち主の元へ。どれも数値は【仮】）。
 * <ul>
 *   <li>どれか 1 つでも付けていると戦わない（{@link #nonCombat}: 狙う相手を持たず、盾で持ち主を守ることもしない）。
 *       襲われたら仕事をやめて持ち主（いなければ拠点、それも無ければ相手から離れた所）へ逃げ、落ち着いたら仕事に戻る（{@link Flee}）</li>
 *   <li>背部ユニット（日常の範囲 {@value ValkyrieDaily#RANGE} ブロックで働く。追従中は持ち主のまわりで。逃げる時と充電のほかは、ほかの日常の行動より仕事を先にする）:
 *       農作ユニット（実った作物を穫って植え直す）・採掘アーム（むき出しの鉱石を掘る）・伐採ソー（自然の木を切り倒して苗を植える）・
 *       釣りユニット（水辺で釣る）・野外調理キット（預かった生の食べ物を焼き、お腹の空いた持ち主に食べさせる。追従中も）</li>
 *   <li>ロジックチップ: ランチャー（牧場: 近くのチェストのえさで動物を増やし、羊の毛を刈る）・ランプライター（暗い所に松明を置く）・
 *       メンテナンス（持ち主の道具・防具を少しずつ直す）・ブレッシング（持ち主に採掘速度上昇・幸運）・プロスペクター（持ち主のまわりの鉱脈を知らせる）</li>
 * </ul>
 * 落ちた物（穫れた作物・鉱石・丸太・釣果）はお掃除（{@link ValkyrieDaily.Clean}）が拾って近くのチェストへ入れる
 */
public final class ValkyrieWork {

    public static final Set<BackpackKind> WORK_BACKPACKS = EnumSet.of(BackpackKind.HARVESTER, BackpackKind.MINING_ARM, BackpackKind.LUMBER_SAW,
            BackpackKind.FISHING_RIG, BackpackKind.FIELD_KITCHEN);
    public static final Set<ChipKind> WORK_CHIPS = EnumSet.of(ChipKind.RANCHER, ChipKind.LAMPLIGHTER, ChipKind.MAINTENANCE, ChipKind.BLESSING, ChipKind.PROSPECTOR);

    /** 仕事の範囲の高さ（中心から上下） */
    static final int HEIGHT = 6;
    /** 牧場で増やすのをやめる数（同じ種類が範囲に何匹いたら） */
    static final int HERD_CAP = 12;
    /** 松明を置く: この明るさ（ブロックの光）より暗い所、兵姫から何ブロックまで */
    static final int DARK = 6, LAMP_REACH = 5;
    static final double OWNER_NEAR = 12.0;
    /** 追従中に働く間、持ち主からこれより離れたら仕事をやめて付いて行く */
    static final double FOLLOW_LEASH = 16.0;
    /** 1 回の仕事の後、次を探すまで（お掃除が落ちた物を拾う間を空ける） */
    static final int AFTER_WORK = 30;

    private ValkyrieWork() {}

    /** 非戦闘のユニットを付けているか（付けていると戦わない） */
    public static boolean nonCombat(ValkyrieEntity v) {
        ValkyrieInventory inv = v.getInventory();
        BackpackKind b = inv.backpack();
        if (b != null && WORK_BACKPACKS.contains(b)) return true;
        for (ChipKind c : WORK_CHIPS) if (inv.hasChip(c)) return true;
        return false;
    }

    static boolean has(ValkyrieEntity v, BackpackKind k) {
        return v.getInventory().backpack() == k;
    }

    static boolean has(ValkyrieEntity v, ChipKind k) {
        return v.getInventory().hasChip(k);
    }

    /**
     * 背部ユニットの仕事ができるか。2026-10-29 ユーザー指定「背部ユニットにあわせて、逃げる時以外は対応している行動を優先して」:
     * 仕事の Goal は順位 2 で、逃げる（{@link Flee}）と電力切れの充電（順位 1）のほかは、追従・拠点へ戻る・お掃除・腰掛け・うろつきより先に選ばれる。
     * 追従中も働く（持ち主のまわりで。持ち主が {@value #FOLLOW_LEASH} ブロックより離れたらやめて付いて行く）
     */
    static boolean fieldWork(ValkyrieEntity v) {
        if (!ValkyrieDaily.idle(v)) return false;
        if (v.getMode() != Mode.FOLLOW) return true;
        return ownerNear(v, FOLLOW_LEASH) != null;
    }

    /** 仕事の範囲の中心（追従中は持ち主、ほかは日常の中心＝拠点・待機の場所） */
    static BlockPos workCenter(ValkyrieEntity v) {
        if (v.getMode() == Mode.FOLLOW) {
            Player o = v.getOwner();
            if (o != null && o.level() == v.level()) return o.blockPosition();
        }
        return ValkyrieDaily.center(v);
    }

    @Nullable
    static Player ownerNear(ValkyrieEntity v, double range) {
        Player o = v.getOwner();
        return o != null && o.isAlive() && o.level() == v.level() && o.distanceToSqr(v) <= range * range ? o : null;
    }

    // ------------------------------------------------------------------ 逃げる

    /** 襲われたら（非戦闘のユニットを付けているとき）持ち主・拠点へ逃げる */
    public static class Flee extends Goal {
        private final ValkyrieEntity v;
        @Nullable private LivingEntity threat;
        private int repath, calm;

        public Flee(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private LivingEntity findThreat() {
            LivingEntity hurt = v.getLastHurtByMob();
            if (hurt != null && hurt.isAlive() && v.tickCount - v.getLastHurtByMobTimestamp() < 100 && hurt.distanceToSqr(v) < 24 * 24
                    && !ValkyrieCombat.isAlly(v, hurt)) return hurt;
            for (Mob m : v.level().getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(8.0), m -> m.isAlive() && m.getTarget() == v)) return m;
            return null;
        }

        @Override
        public boolean canUse() {
            if (!nonCombat(v) || v.isShutdown() || v.isAsleep() || v.isPassenger() || v.tickCount % 5 != 0) return false;
            threat = findThreat();
            return threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            return nonCombat(v) && !v.isShutdown() && calm < 60;
        }

        @Override
        public void start() {
            repath = 0;
            calm = 0;
            v.setTarget(null);
            v.talk("work_flee");
            v.emote(ValkyrieEntity.Emote.SWEAT);
        }

        @Override
        public void stop() {
            threat = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (v.tickCount % 10 == 0) {
                LivingEntity t = findThreat();
                if (t != null) threat = t;
                if (threat != null && (!threat.isAlive() || threat.distanceToSqr(v) > 20 * 20)) threat = null;
            }
            Player owner = ownerNear(v, 64.0);
            boolean safe = threat == null || (owner != null && v.distanceToSqr(owner) < 4.0 * 4.0);
            calm = safe ? calm + 1 : 0;
            if (--repath > 0) return;
            repath = 8;
            double speed = ValkyrieGoals.pathSpeed(v, 1.45);
            if (owner != null) {
                v.getNavigation().moveTo(owner, speed);
                v.getLookControl().setLookAt(owner, 30.0F, 30.0F);
                return;
            }
            BlockPos home = v.getHome();
            if (home != null && home.distSqr(v.blockPosition()) > 4 * 4) {
                v.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, speed);
                return;
            }
            if (threat != null) {
                Vec3 away = DefaultRandomPos.getPosAway(v, 12, 6, threat.position());
                if (away != null) v.getNavigation().moveTo(away.x, away.y, away.z, speed);
            }
        }
    }

    // ------------------------------------------------------------------ ブロックの仕事（農作・採掘・伐採）

    /** 範囲のブロックから仕事の相手を探し、歩いて行って手を動かし、少し経ったら片付ける */
    abstract static class BlockJob extends Goal {
        final ValkyrieEntity v;
        @Nullable BlockPos target;
        int scan, repath, stuck, work;
        private final Set<BlockPos> skip = new HashSet<>();

        BlockJob(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        abstract boolean equipped();

        abstract boolean wants(ServerLevel level, BlockPos pos, BlockState state);

        /** 1 回の仕事の FE */
        abstract int fe();

        /** 手を動かす長さ（Tick） */
        abstract int workTicks();

        abstract void act(ServerLevel level, BlockPos pos, BlockState state);

        @Override
        public boolean canUse() {
            if (!equipped() || !fieldWork(v) || --scan > 0) return false;
            scan = 40;
            if (v.tickCount % 1200 < 40) skip.clear(); // 届かなかった所も、しばらくしたらまた試す
            target = find();
            return target != null;
        }

        @Nullable
        private BlockPos find() {
            ServerLevel level = (ServerLevel) v.level();
            BlockPos c = workCenter(v);
            int r = ValkyrieDaily.RANGE;
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -HEIGHT, -r), c.offset(r, HEIGHT, r))) {
                if (skip.contains(p) || !level.isLoaded(p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.isAir() || !wants(level, p, s)) continue;
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
            if (target == null || !equipped() || !fieldWork(v) || stuck > 240) return false;
            ServerLevel level = (ServerLevel) v.level();
            return wants(level, target, level.getBlockState(target));
        }

        @Override
        public void start() {
            repath = 0;
            stuck = 0;
            work = 0;
        }

        @Override
        public void stop() {
            if (stuck > 240 && target != null) skip.add(target);
            target = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (target == null) return;
            Vec3 c = Vec3.atCenterOf(target);
            v.getLookControl().setLookAt(c);
            if (v.getEyePosition().distanceToSqr(c) < 3.2 * 3.2 || (v.getNavigation().isDone() && v.position().distanceToSqr(c) < 4.5 * 4.5)) {
                v.getNavigation().stop();
                if (work % 6 == 0) v.swing(InteractionHand.MAIN_HAND, true);
                if (++work < workTicks()) return;
                ServerLevel level = (ServerLevel) v.level();
                BlockState s = level.getBlockState(target);
                if (!v.combat().spend(fe())) {
                    v.talk("work_low_power");
                    target = null;
                    return;
                }
                act(level, target, s);
                work = 0;
                stuck = 0;
                target = null;
                scan = AFTER_WORK; // 落ちた物をお掃除が拾う間を空けてから次へ
                return;
            }
            stuck++;
            if (--repath <= 0) {
                repath = 10;
                v.getNavigation().moveTo(c.x, target.getY(), c.z, ValkyrieGoals.pathSpeed(v, 1.0));
            }
        }
    }

    /** 農作ユニット: 実った作物（小麦・人参・じゃがいも・ビートルート・ネザーウォート・スイートベリー・つるの実ったカボチャとスイカ）を穫り、植え直す */
    public static class Harvest extends BlockJob {
        public Harvest(ValkyrieEntity v) {
            super(v);
        }

        @Override
        boolean equipped() {
            return has(v, BackpackKind.HARVESTER);
        }

        @Override
        boolean wants(ServerLevel level, BlockPos pos, BlockState s) {
            if (s.getBlock() instanceof CropBlock crop) return crop.isMaxAge(s);
            if (s.getBlock() instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
            if (s.getBlock() instanceof SweetBerryBushBlock) return s.getValue(SweetBerryBushBlock.AGE) >= 3;
            if (s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState n = level.getBlockState(pos.relative(d));
                    if (n.getBlock() instanceof AttachedStemBlock && n.getValue(AttachedStemBlock.FACING) == d.getOpposite()) return true;
                }
            }
            return false;
        }

        @Override
        int fe() {
            return BackpackKind.HARVESTER.fe;
        }

        @Override
        int workTicks() {
            return 12;
        }

        @Override
        void act(ServerLevel level, BlockPos pos, BlockState s) {
            level.levelEvent(2001, pos, Block.getId(s)); // 壊れる粒
            if (s.getBlock() instanceof SweetBerryBushBlock) {
                Block.popResource(level, pos, new ItemStack(Items.SWEET_BERRIES, 2 + level.random.nextInt(2)));
                level.setBlock(pos, s.setValue(SweetBerryBushBlock.AGE, 1), 2);
                level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
            if (s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON)) {
                level.destroyBlock(pos, true, v);
                return;
            }
            List<ItemStack> drops = Block.getDrops(s, level, pos, null, v, ItemStack.EMPTY);
            ItemStack seed;
            BlockState replant;
            if (s.getBlock() instanceof CropBlock crop) {
                seed = crop.getCloneItemStack(level, pos, s);
                replant = crop.getStateForAge(0);
            } else {
                seed = new ItemStack(Items.NETHER_WART);
                replant = s.setValue(NetherWartBlock.AGE, 0);
            }
            boolean planted = false;
            for (ItemStack d : drops) {
                if (!planted && ItemStack.isSameItem(d, seed)) {
                    d.shrink(1); // 種を 1 つ植え直しに使う
                    planted = true;
                }
            }
            level.setBlock(pos, planted ? replant : Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, pos, d);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** 採掘アーム: 範囲のむき出しの鉱石（空気に面している物）を掘る */
    public static class Mine extends BlockJob {
        public Mine(ValkyrieEntity v) {
            super(v);
        }

        @Override
        boolean equipped() {
            return has(v, BackpackKind.MINING_ARM);
        }

        @Override
        boolean wants(ServerLevel level, BlockPos pos, BlockState s) {
            if (!s.is(Tags.Blocks.ORES)) return false;
            for (Direction d : Direction.values()) if (level.isEmptyBlock(pos.relative(d))) return true;
            return false;
        }

        @Override
        int fe() {
            return BackpackKind.MINING_ARM.fe;
        }

        @Override
        int workTicks() {
            return 30;
        }

        @Override
        void act(ServerLevel level, BlockPos pos, BlockState s) {
            level.destroyBlock(pos, true, v);
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    /** 伐採ソー: 自然の木（土の上に立ち、上に自然の葉がある丸太の幹）を根元から切り倒し、同じ種類の苗を植える */
    public static class Fell extends BlockJob {
        static final int MAX_LOGS = 96;

        public Fell(ValkyrieEntity v) {
            super(v);
        }

        @Override
        boolean equipped() {
            return has(v, BackpackKind.LUMBER_SAW);
        }

        @Override
        boolean wants(ServerLevel level, BlockPos pos, BlockState s) {
            if (!s.is(BlockTags.LOGS) || !level.getBlockState(pos.below()).is(BlockTags.DIRT)) return false;
            BlockPos top = pos;
            for (int i = 0; i < 16 && level.getBlockState(top.above()).is(BlockTags.LOGS); i++) top = top.above();
            for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
                BlockState l = level.getBlockState(p);
                if (l.getBlock() instanceof LeavesBlock && !l.getValue(LeavesBlock.PERSISTENT)) return true; // 置いた葉の家は切らない
            }
            return false;
        }

        @Override
        int fe() {
            return BackpackKind.LUMBER_SAW.fe;
        }

        @Override
        int workTicks() {
            return 40;
        }

        @Override
        void act(ServerLevel level, BlockPos pos, BlockState s) {
            Block sapling = sapling(s.getBlock());
            ArrayDeque<BlockPos> open = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            open.add(pos);
            seen.add(pos);
            int cut = 0;
            while (!open.isEmpty() && cut < MAX_LOGS) {
                BlockPos p = open.poll();
                if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
                level.destroyBlock(p, true, v);
                cut++;
                for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 1, 1))) {
                    if (Math.abs(n.getX() - pos.getX()) > 8 || Math.abs(n.getZ() - pos.getZ()) > 8) continue;
                    BlockPos im = n.immutable();
                    if (seen.add(im) && level.getBlockState(im).is(BlockTags.LOGS)) open.add(im);
                }
            }
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 0.7F);
            if (sapling != null && level.getBlockState(pos).isAir() && sapling.defaultBlockState().canSurvive(level, pos)) {
                level.setBlock(pos, sapling.defaultBlockState(), 3);
            }
        }

        /** 丸太の種類の苗（oak_log → oak_sapling、マングローブは芽。無ければ null） */
        @Nullable
        static Block sapling(Block log) {
            var id = ForgeRegistries.BLOCKS.getKey(log);
            if (id == null) return null;
            String path = id.getPath().replace("stripped_", "");
            if (path.equals("mangrove_log")) return Blocks.MANGROVE_PROPAGULE;
            if (!path.endsWith("_log")) return null;
            var sid = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - 4) + "_sapling");
            Block b = ForgeRegistries.BLOCKS.getValue(sid);
            return b == null || b == Blocks.AIR ? null : b;
        }
    }

    // ------------------------------------------------------------------ 釣り

    /** 釣りユニット: 範囲の水辺（水源の横の立てる所）へ行き、時々釣果（バニラの釣りの戦利品）を預かる。預かる枠がいっぱいになったらやめる（お掃除がしまう） */
    public static class Fish extends Goal {
        private final ValkyrieEntity v;
        @Nullable private BlockPos water, stand;
        private int scan, repath, stuck, bite;

        public Fish(ValkyrieEntity v) {
            this.v = v;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean ok() {
            return has(v, BackpackKind.FISHING_RIG) && fieldWork(v) && !v.getInventory().pickupFull();
        }

        @Override
        public boolean canUse() {
            if (!ok() || --scan > 0) return false;
            scan = 60;
            return findSpot();
        }

        private boolean findSpot() {
            ServerLevel level = (ServerLevel) v.level();
            BlockPos c = workCenter(v);
            int r = ValkyrieDaily.RANGE;
            double bestD = Double.MAX_VALUE;
            water = null;
            stand = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -HEIGHT, -r), c.offset(r, HEIGHT, r))) {
                if (!level.getFluidState(p).isSource() || !level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER) || !level.isEmptyBlock(p.above())) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos s = p.relative(d).above();
                    if (!level.isEmptyBlock(s) || !level.isEmptyBlock(s.above()) || !level.getBlockState(s.below()).isFaceSturdy(level, s.below(), Direction.UP)) continue;
                    double dist = s.distSqr(v.blockPosition());
                    if (dist < bestD) {
                        bestD = dist;
                        water = p.immutable();
                        stand = s.immutable();
                    }
                }
            }
            return stand != null;
        }

        @Override
        public boolean canContinueToUse() {
            return ok() && water != null && stuck < 300 && v.level().getFluidState(water).is(net.minecraft.tags.FluidTags.WATER);
        }

        @Override
        public void start() {
            repath = 0;
            stuck = 0;
            bite = 200 + v.getRandom().nextInt(200);
        }

        @Override
        public void stop() {
            water = null;
            stand = null;
            v.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (stand == null || water == null) return;
            ServerLevel level = (ServerLevel) v.level();
            Vec3 bob = Vec3.atCenterOf(water).add(0, 0.45, 0);
            if (v.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) > 1.5 * 1.5) {
                stuck++;
                if (--repath <= 0) {
                    repath = 10;
                    v.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, ValkyrieGoals.pathSpeed(v, 1.0));
                }
                return;
            }
            stuck = 0;
            v.getNavigation().stop();
            v.getLookControl().setLookAt(bob);
            if (v.tickCount % 40 == 0) level.sendParticles(ParticleTypes.FISHING, bob.x, bob.y, bob.z, 2, 0.1, 0.0, 0.1, 0.0);
            if (--bite > 0) return;
            bite = 200 + v.getRandom().nextInt(200);
            if (!v.combat().spend(BackpackKind.FISHING_RIG.fe)) return;
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, bob)
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, v)
                    .create(LootContextParamSets.FISHING);
            for (ItemStack loot : level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
                ItemStack rest = v.getInventory().addPickup(loot);
                if (!rest.isEmpty()) Block.popResource(level, v.blockPosition(), rest);
            }
            v.swing(InteractionHand.MAIN_HAND, true);
            level.sendParticles(ParticleTypes.SPLASH, bob.x, bob.y, bob.z, 12, 0.3, 0.1, 0.3, 0.0);
            level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
            v.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8F, 1.0F);
            if (v.getRandom().nextInt(6) == 0) v.talk("work_fish");
        }
    }

    // ------------------------------------------------------------------ 毎 Tick（調理・牧場・松明・整備・加護・探査）

    /** 兵姫の毎 Tick（{@link ValkyrieUnitEffects#tick} から） */
    static void tick(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        if (v.isShutdown() || v.isAsleep()) return;
        if (has(v, BackpackKind.FIELD_KITCHEN) && v.tickCount % 100 == 37) kitchen(level, v, b);
        if (has(v, ChipKind.RANCHER) && v.tickCount % 100 == 11 && v.getMode() != Mode.FOLLOW && ValkyrieDaily.idle(v)) ranch(level, v, b);
        if (has(v, ChipKind.LAMPLIGHTER) && v.tickCount % 40 == 23 && ValkyrieDaily.idle(v)) lamp(level, v, b);
        if (has(v, ChipKind.MAINTENANCE) && v.tickCount % 40 == 5) maintain(level, v, b);
        if (has(v, ChipKind.BLESSING) && v.tickCount % 100 == 53) bless(level, v, b);
        if (has(v, ChipKind.PROSPECTOR) && v.tickCount % 200 == 71) prospect(level, v, b);
    }

    /** 野外調理キット: 預かった生の食べ物を 1 つずつ焼く。持ち主のお腹が空いていたら、預かった食べ物を食べさせる */
    static void kitchen(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        ValkyrieInventory inv = v.getInventory();
        Player owner = ownerNear(v, 6.0);
        if (owner != null && owner.getFoodData().getFoodLevel() <= 14 && !owner.isCreative()) {
            for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
                ItemStack s = inv.getItem(i);
                var food = s.getFoodProperties(owner);
                if (s.isEmpty() || food == null || !food.getEffects().isEmpty() || cookResult(level, s) != null) continue; // 生の物・毒のある物は出さない
                owner.eat(level, s.copyWithCount(1));
                s.shrink(1);
                inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
                v.swing(InteractionHand.MAIN_HAND, true);
                v.getLookControl().setLookAt(owner, 30.0F, 30.0F);
                v.talk("work_feed");
                return;
            }
        }
        for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
            ItemStack s = inv.getItem(i);
            ItemStack cooked = s.isEmpty() ? null : cookResult(level, s);
            if (cooked == null) continue;
            if (!b.spend(BackpackKind.FIELD_KITCHEN.fe)) return;
            s.shrink(1);
            inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
            ItemStack rest = inv.addPickup(cooked);
            if (!rest.isEmpty()) Block.popResource(level, v.blockPosition(), rest);
            Vec3 at = v.position().add(0, 1.6, 0).subtract(v.getLookAngle().scale(0.4));
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.4, at.z, 1, 0.05, 0.1, 0.05, 0.01);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.1, 0.05, 0.1, 0.0);
            v.playSound(SoundEvents.CAMPFIRE_CRACKLE, 0.6F, 1.0F);
            return;
        }
    }

    /** かまどで焼くと食べ物になる物なら、焼いた物（でなければ null） */
    @Nullable
    static ItemStack cookResult(ServerLevel level, ItemStack raw) {
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(raw.copyWithCount(1)), level);
        if (recipe.isEmpty()) return null;
        ItemStack out = recipe.get().getResultItem(level.registryAccess()).copy();
        return out.isEdible() ? out : null;
    }

    /** ランチャー: 近くのチェスト（と預かった物）のえさで、増やせる大人 2 匹を恋の状態にする。毛の伸びた羊の毛を刈る */
    static void ranch(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        BlockPos c = ValkyrieDaily.center(v);
        int r = ValkyrieDaily.RANGE;
        AABB box = new AABB(c).inflate(r, HEIGHT, r);
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, box, s -> s.isAlive() && s.readyForShearing())) {
            if (!b.spend(200)) return;
            sheep.shear(SoundSource.NEUTRAL);
            v.getLookControl().setLookAt(sheep, 30.0F, 30.0F);
            v.swing(InteractionHand.MAIN_HAND, true);
            return; // 1 回に 1 匹
        }
        Map<EntityType<?>, List<Animal>> herds = new HashMap<>();
        for (Animal a : level.getEntitiesOfClass(Animal.class, box, Animal::isAlive)) herds.computeIfAbsent(a.getType(), t -> new ArrayList<>()).add(a);
        for (List<Animal> herd : herds.values()) {
            if (herd.size() >= HERD_CAP) continue;
            List<Animal> ready = new ArrayList<>();
            for (Animal a : herd) if (a.getAge() == 0 && a.canFallInLove()) ready.add(a);
            if (ready.size() < 2) continue;
            Animal first = ready.get(0), second = ready.get(1);
            Predicate<ItemStack> food = first::isFood;
            if (!takeItem(level, v, c, food, 2)) continue;
            if (!b.spend(500)) return;
            first.setInLove(null);
            second.setInLove(null);
            v.getLookControl().setLookAt(first, 30.0F, 30.0F);
            v.swing(InteractionHand.MAIN_HAND, true);
            if (v.getRandom().nextInt(4) == 0) v.talk("work_ranch");
            return; // 1 回に 1 組
        }
    }

    /** 預かった物か、範囲のチェストから、当てはまる物を count 個取る（揃わなければ取らずに false） */
    static boolean takeItem(ServerLevel level, ValkyrieEntity v, BlockPos c, Predicate<ItemStack> want, int count) {
        ValkyrieInventory inv = v.getInventory();
        int have = 0;
        for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && want.test(s)) have += s.getCount();
        }
        List<IItemHandler> chests = containers(level, c);
        for (IItemHandler h : chests) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && want.test(s)) have += s.getCount();
            }
        }
        if (have < count) return false;
        int left = count;
        for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !want.test(s)) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
            left -= n;
        }
        for (IItemHandler h : chests) {
            for (int i = 0; i < h.getSlots() && left > 0; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || !want.test(s)) continue;
                left -= h.extractItem(i, left, false).getCount();
            }
        }
        return true;
    }

    /** 範囲のチェストなどの入れ物 */
    static List<IItemHandler> containers(ServerLevel level, BlockPos c) {
        List<IItemHandler> out = new ArrayList<>();
        int r = ValkyrieDaily.RANGE;
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - c.getX()) > r || Math.abs(p.getZ() - c.getZ()) > r || Math.abs(p.getY() - c.getY()) > HEIGHT) continue;
                    if (!(be instanceof net.minecraft.world.Container)) continue; // チェスト・樽など（機械の中身には手を出さない）
                    be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(out::add);
                }
            }
        }
        return out;
    }

    /** ランプライター: 兵姫のまわりの暗い所（ブロックの光が {@value #DARK} 未満）に松明を置く。松明は預かった物、無ければ近くの持ち主の持ち物から */
    static void lamp(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos me = v.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(me.offset(-LAMP_REACH, -2, -LAMP_REACH), me.offset(LAMP_REACH, 2, LAMP_REACH))) {
            if (!level.isEmptyBlock(p) || level.getBrightness(LightLayer.BLOCK, p) >= DARK) continue;
            if (!Blocks.TORCH.defaultBlockState().canSurvive(level, p) || !level.getFluidState(p).isEmpty()) continue;
            double d = p.distSqr(me);
            if (d < bestD) {
                bestD = d;
                best = p.immutable();
            }
        }
        if (best == null) return;
        if (!takeTorch(v)) {
            v.talk("work_no_torch");
            return;
        }
        if (!b.spend(100)) return;
        level.setBlock(best, Blocks.TORCH.defaultBlockState(), 3);
        level.playSound(null, best, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        v.getLookControl().setLookAt(Vec3.atCenterOf(best));
        v.swing(InteractionHand.MAIN_HAND, true);
    }

    private static boolean takeTorch(ValkyrieEntity v) {
        ValkyrieInventory inv = v.getInventory();
        for (int i = ValkyrieInventory.PICKUP; i < ValkyrieInventory.PICKUP + ValkyrieInventory.PICKUPS; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TORCH)) {
                s.shrink(1);
                inv.setItem(i, s.isEmpty() ? ItemStack.EMPTY : s);
                return true;
            }
        }
        Player owner = ownerNear(v, 16.0);
        if (owner == null) return false;
        if (owner.getAbilities().instabuild) return true;
        var pinv = owner.getInventory();
        for (int i = 0; i < pinv.getContainerSize(); i++) {
            ItemStack s = pinv.getItem(i);
            if (s.is(Items.TORCH)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** メンテナンス: 近くの持ち主の防具・利き手と逆の手・ホットバーの傷んだ道具を、少しずつ直す（1 つにつき 2 ずつ） */
    static void maintain(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        Player owner = ownerNear(v, 8.0);
        if (owner == null) return;
        var pinv = owner.getInventory();
        List<ItemStack> items = new ArrayList<>(pinv.armor);
        items.addAll(pinv.offhand);
        for (int i = 0; i < 9; i++) items.add(pinv.items.get(i));
        boolean any = false;
        for (ItemStack s : items) {
            if (s.isEmpty() || !s.isDamageableItem() || !s.isDamaged()) continue;
            if (!b.spend(150)) break;
            s.setDamageValue(Math.max(0, s.getDamageValue() - 2));
            any = true;
        }
        if (any && v.tickCount % 200 < 40) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0, owner.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
        }
    }

    /** ブレッシング: 近くの持ち主に採掘速度上昇 I・幸運 I */
    static void bless(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        Player owner = ownerNear(v, OWNER_NEAR);
        if (owner == null || !b.spend(200)) return;
        owner.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 220, 0, true, true, true));
        owner.addEffect(new MobEffectInstance(MobEffects.LUCK, 220, 0, true, true, true));
    }

    /** プロスペクター: 近くの持ち主のまわり（半径 12）の鉱石を探し、持ち主にだけ光の粒で印を付け、数といちばん近い鉱石の向きを知らせる */
    static void prospect(ServerLevel level, ValkyrieEntity v, ValkyrieCombat.Brain b) {
        if (!(ownerNear(v, 16.0) instanceof ServerPlayer owner)) return;
        BlockPos c = owner.blockPosition();
        List<BlockPos> ores = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-12, -12, -12), c.offset(12, 12, 12))) {
            if (level.getBlockState(p).is(Tags.Blocks.ORES)) ores.add(p.immutable());
        }
        if (ores.isEmpty() || !b.spend(500)) return;
        ores.sort((a, z) -> Double.compare(a.distSqr(c), z.distSqr(c)));
        for (int i = 0; i < Math.min(16, ores.size()); i++) {
            BlockPos p = ores.get(i);
            level.sendParticles(owner, ParticleTypes.GLOW, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
        }
        BlockPos near = ores.get(0);
        double dx = near.getX() + 0.5 - owner.getX(), dz = near.getZ() + 0.5 - owner.getZ();
        String[] dirs = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
        int dir = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8);
        owner.displayClientMessage(Component.translatable("message.automata_valkyries.work.prospect", ores.size(),
                level.getBlockState(near).getBlock().getName(), Component.translatable("message.automata_valkyries.dir." + dirs[dir]),
                (int) Math.sqrt(near.distSqr(c))).withStyle(net.minecraft.ChatFormatting.AQUA), true);
    }
}
