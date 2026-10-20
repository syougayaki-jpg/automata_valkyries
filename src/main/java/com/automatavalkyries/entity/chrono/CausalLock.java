package com.automatavalkyries.entity.chrono;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * タイムルーラーの「因果停止」（2026-10-20 ユーザー指定「遅いが喰らってはいけない因果停止＋超貫通ダメージの即死級の技」。質問で: 不死のトーテムだけは効く）。
 * 止められた者は {@link #lock} の Tick の間、その場に縫い留められ（動けない・跳べない・使う・叩く・壊すもできない）、回復もしない。
 * 時が満ちると、防具・盾・エンチャント・耐性・衝撃吸収・無敵時間を全て通す {@link #SEVERANCE} の、最大 HP の {@value #MULTIPLIER} 倍のダメージを受ける。
 * 不死のトーテム（{@code bypasses_invulnerability} を付けないので効く）だけが 1 度だけ耐えさせる。Eprotect の守りのある者（兵姫）はその上限のまま。
 * 数えるのはその者の Tick なので、全世界時間停止の間は数えも止まる。止めた者が倒れたり退いたりしたら解く（{@link #releaseBy}）
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class CausalLock {

    public static final ResourceKey<DamageType> SEVERANCE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "causal_severance"));
    public static final float MULTIPLIER = 2.0F, MIN_DAMAGE = 200.0F;

    private static final class Lock {
        final Vec3 at;
        final float yRot, xRot;
        @Nullable
        final UUID by;
        int left;
        /** 因果停止と因果断絶の演出（{@link RulerFxEntity#CAUSAL}） */
        @Nullable
        RulerFxEntity fx;

        Lock(Vec3 at, float yRot, float xRot, @Nullable UUID by, int left) {
            this.at = at;
            this.yRot = yRot;
            this.xRot = xRot;
            this.by = by;
            this.left = left;
        }

        void dropFx() {
            if (fx != null) fx.discard();
            fx = null;
        }
    }

    private static final Map<UUID, Lock> LOCKS = new HashMap<>();

    private CausalLock() {
    }

    /** e の因果を ticks の間止め、明けたら超貫通のダメージ（by が与えた者。居なくてもよい）。もう止まっていれば何もしない */
    public static void lock(LivingEntity e, int ticks, @Nullable Entity by) {
        if (!(e.level() instanceof ServerLevel server) || LOCKS.containsKey(e.getUUID())) return;
        Lock l = new Lock(e.position(), e.getYRot(), e.getXRot(), by == null ? null : by.getUUID(), ticks);
        LOCKS.put(e.getUUID(), l);
        e.setDeltaMovement(Vec3.ZERO);
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 5, 255, false, false, false));
        // 演出は自作（client/RulerFxRenderer.causal と、止まった者の画面は client/ClientRulerShow.causalHud）
        l.fx = RulerFxEntity.spawn(server, RulerFxEntity.CAUSAL, ticks, e.position(), e.getBbHeight(), (float) (e.getRandom().nextDouble() * Math.PI * 2), 0,
                ticks + RulerFxEntity.SEVER_TICKS, RulerFxEntity.RED);
        server.playSound(null, e.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        server.playSound(null, e.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0F, 0.5F);
        server.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.5F, 0.5F);   // 糸が張る
        if (e instanceof ServerPlayer p) {
            p.displayClientMessage(Component.translatable("entity.automata_valkyries.time_ruler.causal_locked").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        }
    }

    /** e の因果が止まっているか（サーバー側） */
    public static boolean locked(Entity e) {
        return !e.level().isClientSide && LOCKS.containsKey(e.getUUID());
    }

    /** by が止めた因果を全て解く（ダメージは無し） */
    public static void releaseBy(Entity by) {
        UUID id = by.getUUID();
        LOCKS.values().removeIf(l -> {
            if (!id.equals(l.by)) return false;
            l.dropFx();
            return true;
        });
    }

    /** 明けた: 超貫通のダメージ（衝撃吸収を消し、無敵時間を外してから） */
    public static void sever(LivingEntity e, @Nullable UUID by) {
        if (!(e.level() instanceof ServerLevel server)) return;
        Entity src = by == null ? null : server.getEntity(by);
        DamageSource ds = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SEVERANCE), src);
        Vec3 c = e.position();
        Fx.shockwave(server, c.add(0, 0.3, 0), 0xFF4060, 5.0F, 18);                     // 一閃・断たれる糸・砕ける殻は RulerFxEntity.CAUSAL が描く
        Fx.burst(server, MementoMoriEntity.glow(0xFFF0F0, 0.3F, 16), c.add(0, e.getBbHeight() * 0.55, 0), 24, 0.25);
        server.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        server.playSound(null, BlockPos.containing(c), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0F, 0.6F);
        server.playSound(null, BlockPos.containing(c), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        server.playSound(null, BlockPos.containing(c), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3.0F, 0.4F);
        e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        e.setAbsorptionAmount(0.0F);
        e.invulnerableTime = 0;
        e.hurt(ds, Math.max(MIN_DAMAGE, e.getMaxHealth() * MULTIPLIER));
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || LOCKS.isEmpty()) return;
        Lock l = LOCKS.get(e.getUUID());
        if (l == null) return;
        if (!e.isAlive()) {
            LOCKS.remove(e.getUUID());
            l.dropFx();
            return;
        }
        e.setDeltaMovement(Vec3.ZERO);                                                // その場に縫い留める
        if (e.position().distanceToSqr(l.at) > 0.01) {
            if (e instanceof ServerPlayer p) p.connection.teleport(l.at.x, l.at.y, l.at.z, l.yRot, l.xRot);
            else e.teleportTo(l.at.x, l.at.y, l.at.z);
        }
        if (e.level() instanceof ServerLevel server && l.left % 4 == 0) {
            Fx.burst(server, MementoMoriEntity.glow(0xFF4060, 0.2F, 10), e.position().add(0, e.getBbHeight() * 0.5, 0), 6, 0.05);
            if (l.left % 20 == 0) server.playSound(null, e.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 2.0F, 0.5F);   // 鼓動（どくん、どくん）
            if (l.left % 20 == 16) server.playSound(null, e.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if (e.level() instanceof ServerLevel server && l.left < 10 && l.left % 2 == 0) {                   // 明ける間際: 上がっていく音
            server.playSound(null, e.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 1.5F, 0.5F + (10 - l.left) * 0.15F);
        }
        if (--l.left <= 0) {
            LOCKS.remove(e.getUUID());
            sever(e, l.by);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOCKS.clear();
    }
}
