package com.automatavalkyries.item;

import com.automatavalkyries.entity.FateDuels;
import com.automatavalkyries.entity.chrono.MementoMoriEntity;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 宿命の結び糸（2026-10-20 ユーザー指定「他の MOD のボスと戦わせたい」で、質問で「戦わせるアイテムを作る」。クリエイティブかコマンドの権限が要る）。
 * 見ている生き物（{@value #REACH} m まで。巨大なボスにも届く）へ右クリックで 1 体目に印を付け（光る）、2 体目へ右クリックでその 2 体を結ぶ。
 * 結ばれた 2 体はどちらかが倒れるまで互いだけを狙う（{@link FateDuels}）。タイムルーラーと他の MOD のボス、他の MOD のボスどうしなど、どの生き物でも（プレイヤーは除く）。
 * 同じ相手へもう一度で印を外す。スニークしながら右クリックで、自分が結んだ対決を全て解く
 */
public class FateThreadItem extends Item {

    public static final double REACH = 64.0;

    public FateThreadItem(Properties properties) {
        super(properties);
    }

    static boolean allowed(Player player) {
        return player.getAbilities().instabuild || player.hasPermissions(2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!allowed(player)) {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.denied").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {                                                  // 自分が結んだ対決を全て解く
            int n = player instanceof ServerPlayer sp ? FateDuels.releaseAllBy(sp) : 0;
            stack.removeTagKey("First");
            player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.released", n).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(stack);
        }
        LivingEntity target = looked(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.none").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(stack);
        }
        select(stack, player, target);
        return InteractionResultHolder.success(stack);
    }

    /** 近くの生き物へ直に右クリックしたとき（その生き物が右クリックを受け取らなければ） */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!allowed(player) || target instanceof Player) return InteractionResult.PASS;
        select(player.getItemInHand(hand), player, target);
        return InteractionResult.SUCCESS;
    }

    /** 見ている生き物（ブロックの向こうは見ない。プレイヤーは除く） */
    @Nullable
    static LivingEntity looked(Player player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));
        HitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(2.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity l && l.isAlive() && !(e instanceof Player) && !e.isSpectator(), REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }

    /** 1 体目に印を付ける、または 2 体目を選んで結ぶ */
    static void select(ItemStack stack, Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) return;
        UUID first = stack.hasTag() && stack.getTag().hasUUID("First") ? stack.getTag().getUUID("First") : null;
        Entity marked = first == null ? null : level.getEntity(first);
        if (!(marked instanceof LivingEntity a) || !a.isAlive()) {                     // 1 体目
            stack.getOrCreateTag().putUUID("First", target.getUUID());
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            Fx.burst(level, MementoMoriEntity.glow(0xFF4060, 0.35F, 16), target.position().add(0, target.getBbHeight() * 0.5, 0), 20, 0.2);
            level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.5F, 0.8F);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.first", target.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        stack.removeTagKey("First");
        if (a == target) {                                                             // 同じ相手: 印を外す
            target.removeEffect(MobEffects.GLOWING);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.unmarked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        FateDuels.bind(a, target, player instanceof ServerPlayer sp ? sp : null);
        Vec3 pa = a.position().add(0, a.getBbHeight() * 0.5, 0), pb = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Fx.line(level, MementoMoriEntity.glow(0xFF4060, 0.45F, 30), pa, pb, 0.4);           // 結ばれた赤い糸
        Fx.line(level, MementoMoriEntity.glow(0xFFD166, 0.25F, 24), pa, pb, 0.6);
        Fx.burst(level, MementoMoriEntity.glow(0xFFD166, 0.4F, 20), pb, 24, 0.25);
        level.playSound(null, target.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0F, 0.7F);
        player.displayClientMessage(Component.translatable("item.automata_valkyries.fate_thread.bound", a.getDisplayName(), target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.fate_thread.desc").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID("First");
    }
}
