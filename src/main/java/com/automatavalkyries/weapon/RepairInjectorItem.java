package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 戦術リペアインジェクター（§7.2 module_repair_injector、T1）: 左手支援ユニット。電力は使わず、内部のナノマシン溶液を使う。
 * 手（どちらでも）に持っている者の HP が 40% 以下になると、自動でナノマシンを噴霧して HP を 20 回復する。
 * 溶液は内部に 8 本【提案】まで入り、右クリックで持ち物から詰める。続けて噴霧するまで 5 秒空ける【提案】。
 * <p>オーバーロード（{@link OverloadArts}。2026-10-05 ユーザー指定。左手でも使うので、このユニットそのものがオーバーロード化していれば変わる）:
 * ナノマシンの大群。噴霧が半径 12 に広がる緑と金のナノマシンの嵐になり、持ち主に加えて半径 12 の味方（プレイヤー・兵姫・仲間）も同じだけ回復する
 * （持ち主から味方へ光の筋が伸びる）。同じ範囲の敵にはナノマシンが取り付いて弱体化と鈍化 II（5 秒、ダメージなし）</p>
 */
public class RepairInjectorItem extends TacticalWeaponItem implements ValkyrieArts.Usable {

    public static final int TANK = 8, COOLDOWN = 100;
    public static final float HEAL = 20.0F, THRESHOLD = 0.4F, WEIGHT = 5.0F;
    /** オーバーロード: ナノマシンの嵐の半径・光の筋を描く数 */
    public static final double OVER_RADIUS = 12.0;
    public static final int OVER_LINKS = 12, NANO = 0x8AFFC0;

    public RepairInjectorItem(Properties properties) {
        super(properties, 0, WEIGHT, false);
    }

    public static int solution(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Nano") : 0;
    }

    public static void setSolution(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt("Nano", Math.max(0, Math.min(TANK, amount)));
    }

    @Override
    public ItemStack charged() {
        ItemStack stack = super.charged();
        setSolution(stack, TANK);
        return stack;
    }

    /** 遠くの味方へ撃ち込める距離（2026-10-26 ユーザー指定「リペアインジェクターを遠距離で使えるようにして」） */
    public static final double RANGE = 32.0;

    /**
     * 右クリック: 照準の先 {@value #RANGE} m 以内の味方（ほかのプレイヤー・兵姫・飼っている動物・同じチーム）へナノマシンを撃ち込んで癒やす
     * （溶液 1 つ・間隔は自動噴霧と同じ。機能停止中の兵姫は起きる）。照準に味方がいなければ溶液を詰める
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack injector = player.getItemInHand(hand);
        LivingEntity aimed = aimedFriend(player, RANGE);
        boolean needsCare = aimed != null && (aimed.getHealth() < aimed.getMaxHealth()
                || aimed instanceof com.automatavalkyries.entity.ValkyrieEntity v && v.isShutdown());   // 満タンの味方には撃たず、溶液を詰める
        if (needsCare) {
            if (level instanceof ServerLevel server && !inject(server, player, injector, aimed)) {
                player.displayClientMessage(Component.translatable(solution(injector) <= 0 ? "gui.automata_valkyries.no_solution" : "gui.automata_valkyries.injector_cooldown")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(injector);
            }
            return InteractionResultHolder.sidedSuccess(injector, level.isClientSide());
        }
        int need = TANK - solution(injector);
        if (need <= 0) return InteractionResultHolder.pass(injector);
        int loaded = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && loaded < need; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModWeapons.NANO_SOLUTION.get())) {
                int taken = Math.min(need - loaded, stack.getCount());
                stack.shrink(taken);
                loaded += taken;
            }
        }
        if (loaded == 0) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("gui.automata_valkyries.no_solution").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(injector);
        }
        setSolution(injector, solution(injector) + loaded);
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 1.2F);
        return InteractionResultHolder.sidedSuccess(injector, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity holder)) return;
        if (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) return;
        tryInject(server, holder, stack);
    }

    /** HP が 40% 以下なら噴霧する。噴霧したら true（兵姫に持たせたときも同じ処理） */
    public static boolean tryInject(ServerLevel level, LivingEntity holder, ItemStack stack) {
        if (!holder.isAlive() || holder.getHealth() > holder.getMaxHealth() * THRESHOLD) return false;
        return inject(level, holder, stack, holder);
    }

    /** 照準の先 range 以内の、壁に遮られていない味方 */
    @org.jetbrains.annotations.Nullable
    public static LivingEntity aimedFriend(Player player, double range) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(range));
        net.minecraft.world.phys.BlockHitResult wall = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = wall.getLocation();
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
                e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && friendOf(player, l), eye.distanceToSqr(end));
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    /** プレイヤーから見た味方（ほかのプレイヤー・兵姫・飼っている動物・同じチーム） */
    static boolean friendOf(Player player, LivingEntity e) {
        if (e == player) return false;
        if (e instanceof Player || e instanceof com.automatavalkyries.entity.ValkyrieEntity) return true;
        if (e instanceof net.minecraft.world.entity.TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID())) return true;
        return player.isAlliedTo(e);
    }

    /** 兵姫が見る範囲（味方へ届く距離。2026-10-26 から遠くの味方にも撃ち込む） */
    public static final double VALKYRIE_RANGE = 24.0;
    /** 兵姫が噴霧する HP の割合（これ未満の自分・持ち主・味方へ） */
    public static final float VALKYRIE_THRESHOLD = 0.5F;

    /**
     * 戦術兵姫が噴霧する（{@link ValkyrieArts}）: タンクが減っていれば内蔵弾薬庫のナノマシン溶液で満たし、
     * {@link #VALKYRIE_RANGE} 以内で HP が {@link #VALKYRIE_THRESHOLD} 未満の一番弱った味方（自分・持ち主・兵姫）を癒やす
     */
    @Override
    public boolean valkyrieUse(ServerLevel level, LivingEntity user, ItemStack stack, @org.jetbrains.annotations.Nullable LivingEntity target) {
        int need = TANK - solution(stack);
        if (need > 0) {
            int loaded = ValkyrieArts.takeAmmo(user, ModWeapons.NANO_SOLUTION.get(), need);
            if (loaded > 0) {
                setSolution(stack, solution(stack) + loaded);
                level.playSound(null, user.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
        }
        LivingEntity patient = ValkyrieArts.hurtFriend(user, VALKYRIE_RANGE, VALKYRIE_THRESHOLD);
        return patient != null && inject(level, user, stack, patient);
    }

    /** 噴霧できるか（溶液がある・間隔が明けた） */
    public static boolean ready(Level level, ItemStack stack) {
        return solution(stack) > 0 && level.getGameTime() >= stack.getOrCreateTag().getLong("Ready");
    }

    /** holder の持つインジェクターで patient を癒やす（溶液 1 つ・間隔）。噴霧したら true */
    public static boolean inject(ServerLevel level, LivingEntity holder, ItemStack stack, LivingEntity patient) {
        if (!patient.isAlive() || !ready(level, stack)) return false;
        setSolution(stack, solution(stack) - 1);
        stack.getOrCreateTag().putLong("Ready", level.getGameTime() + COOLDOWN);
        if (patient instanceof com.automatavalkyries.entity.ValkyrieEntity v) v.repair(HEAL);       // 機能停止中なら起こす
        else patient.heal(HEAL);
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(patient.getX(), patient.getY(0.5), patient.getZ());
        if (patient != holder) {                                                                // 撃ち込んだナノマシンの筋（遠くなら輪を挟む）
            net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(holder.getX(), holder.getY(0.6), holder.getZ());
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), NANO, 0.2F, 12), from, at, 0.5);
            Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), 0xFFFFFF, 0.12F, 8), from, at, 1.2);
            double dist = from.distanceTo(at);
            net.minecraft.world.phys.Vec3 axis = at.subtract(from).normalize();
            for (double d = 4.0; d < dist - 1.0; d += 4.0) Fx.beamRing(level, from.add(axis.scale(d)), axis, NANO, 0.5F, 10);
            level.playSound(null, holder.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), 0xB0FFD0, 0.35F, 20), at, 18, 0.45, 0.6, 0.45, 0.02);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.GREEN, 0.25F, 14), at, 10, 0.5, 0.8, 0.5, 0.03);
        Fx.shockwave(level, patient.position().add(0, 0.05, 0), 0x8AFFC0, 1.6F, 12);
        level.playSound(null, patient.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
        if (stack.getItem() instanceof RepairInjectorItem item) item.playUse(holder, stack);
        if (Overload.is(stack) || (holder.getMainHandItem() == stack && OverloadArts.active(holder))) swarm(level, holder);
        return true;
    }

    /** オーバーロードのナノマシンの嵐: 半径 12 の味方を回復し、敵を弱らせる */
    private static void swarm(ServerLevel level, LivingEntity holder) {
        net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(holder.getX(), holder.getY(0.5), holder.getZ());
        int links = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(OVER_RADIUS),
                e -> e != holder && e.isAlive() && !e.isSpectator() && e.distanceTo(holder) <= OVER_RADIUS)) {
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(e.getX(), e.getY(0.5), e.getZ());
            boolean enemy = e instanceof net.minecraft.world.entity.monster.Enemy && !e.isAlliedTo(holder);
            boolean ally = !enemy && (e.isAlliedTo(holder) || e instanceof net.minecraft.world.entity.player.Player
                    || e instanceof com.automatavalkyries.entity.ValkyrieEntity);
            if (ally) {
                e.heal(HEAL);
                if (links++ < OVER_LINKS) {
                    Fx.line(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), NANO, 0.25F, 14), c, at, 0.6);
                    Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), Overload.GOLD, 0.3F, 16), at, 6, 0.4, 0.6, 0.4, 0.02);
                }
            } else if (enemy) {
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 100, 0));
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                if (links++ < OVER_LINKS) Fx.burst(level, Fx.opt(com.automatavalkyries.registry.ModParticles.HEX.get(), Overload.CRIMSON, 0.2F, 14), at, 6, 0.2);
            }
        }
        net.minecraft.world.phys.Vec3 ground = holder.position().add(0, 0.05, 0);
        Fx.shockwave(level, ground, NANO, (float) OVER_RADIUS, 16);
        Fx.shockwave(level, ground.add(0, 0.1, 0), Overload.GOLD, (float) OVER_RADIUS * 0.6F, 18);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), NANO, 0.3F, 30), c.add(0, 1, 0), 80, OVER_RADIUS * 0.5, 1.5, OVER_RADIUS * 0.5, 0.04);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Overload.GOLD, 0.25F, 24), c.add(0, 1, 0), 30, OVER_RADIUS * 0.4, 1.0, OVER_RADIUS * 0.4, 0.05);
        Fx.mandala(level, ground.add(0, 0.02, 0), NANO, 4.0F, 30, false, true);
        OverloadArts.flourish(level, holder, (float) OVER_RADIUS);
        level.playSound(null, holder.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, holder.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    protected void appendStatus(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("gui.automata_valkyries.nano_solution", solution(stack), TANK).withStyle(ChatFormatting.GREEN));
    }
}
