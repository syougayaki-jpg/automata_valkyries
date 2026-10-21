package com.automatavalkyries.entity;

import com.automatavalkyries.annihilate.TerminusEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * おまけのボス「最果てのエレノア」。エレノアと同じ見た目・保護で、終焉の権能が常に有効（切り替えられない）。
 * 攻撃されたら、ダメージが通ったかどうかに関わらず、攻撃してきた相手へ終焉で反撃する（同じ相手には 1 秒に 1 回まで）。
 */
public class FarthestElenorEntity extends ElenorEntity {

    /** 反撃で倒したプレイヤーの、倒す前のゲームモード（リスポーンで戻す） */
    private static final Map<UUID, net.minecraft.world.level.GameType> PREVIOUS_GAME_MODE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 同じ相手への反撃の間隔（Tick） */
    private static final int RETALIATE_COOLDOWN = 20;
    private static final UUID UNATTRIBUTED_ATTACK_KEY = new UUID(0L, 1L);

    private final Map<UUID, Long> lastRetaliation = new java.util.concurrent.ConcurrentHashMap<>();
    /** 反撃の最中か（反撃の処理から自分への被弾が戻ってきても、さらに反撃しない） */
    private volatile boolean retaliating = false;

    public FarthestElenorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        super.setTerminusEnabled(true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new TerminusActiveAttackGoal(this));
    }

    /**
     * 最果てのエレノアの能動的終焉攻撃ゴール。
     * 敵対ターゲットを捉えている場合、中遠距離（射程64m〜128m）からでも
     * 定期的に（30 tick = 1.5秒に1回）終焉の権能を発動し、Witherzilla や Madness Entities（月・古代ネザー遺跡の残骸等）の
     * チートMODボス（透明化・高高度飛行・非Entity型を含む）を解体・抹殺する。
     */
    static class TerminusActiveAttackGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final FarthestElenorEntity elenor;
        private int attackCooldown = 0;

        public TerminusActiveAttackGoal(FarthestElenorEntity elenor) {
            this.elenor = elenor;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private LivingEntity findOrAcquireDangerousTarget() {
            LivingEntity current = elenor.getTarget();
            if (current != null && (current.isAlive() || com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(current))
                    && !(current instanceof com.automatavalkyries.protect.IEprotected protectedEntity && !protectedEntity.isTerminusTestTarget())
                    && (current instanceof Player || com.automatavalkyries.annihilate.FlagKillPredicates.hasDangerousFlag(current))) {
                return current;
            }
            if (!(elenor.level() instanceof ServerLevel serverLevel)) return null;
            // バニラの NearestAttackableTargetGoal は上下 ±4m しか探索せず、かつ常時透明 (isInvisible=true) で防具なしの
            // Madness プロキシ (C849613: The Moon / Ancient Nether Avatar) に対して視認距離が 0.07 倍 (3.36m) に縮むため、
            // 3次元 128m 範囲の危険フラグ保持エンティティを透明化無視で直接捕捉する。
            List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    elenor.getBoundingBox().inflate(128.0D),
                    e -> e != elenor
                            && !(e instanceof FarthestElenorEntity)
                            && !(e instanceof com.automatavalkyries.protect.IEprotected p && !p.isTerminusTestTarget())
                            && (e.isAlive() || com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(e))
                            && com.automatavalkyries.annihilate.FlagKillPredicates.hasDangerousFlag(e));
            if (candidates.isEmpty()) return null;
            LivingEntity nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity candidate : candidates) {
                double d = elenor.distanceToSqr(candidate);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = candidate;
                }
            }
            if (nearest != null) {
                elenor.setTarget(nearest);
            }
            return nearest;
        }

        @Override
        public boolean canUse() {
            return findOrAcquireDangerousTarget() != null
                    || com.automatavalkyries.annihilate.MadnessAnnihilation.hasActiveNonEntityBosses();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (!(elenor.level() instanceof ServerLevel serverLevel)) return;
            LivingEntity target = findOrAcquireDangerousTarget();
            boolean hasNonEntityBosses = com.automatavalkyries.annihilate.MadnessAnnihilation.hasActiveNonEntityBosses();
            if (target == null && !hasNonEntityBosses) return;

            if (target != null) {
                elenor.getLookControl().setLookAt(target, 30.0F, 30.0F);
                double distSq = elenor.distanceToSqr(target);
                if (distSq > 9.0D) {
                    elenor.getNavigation().moveTo(target, 1.25D);
                }
            }

            if (--attackCooldown <= 0) {
                attackCooldown = 30;
                elenor.triggerAnim("action", "skirt_spread");
                serverLevel.playSound(null, elenor.getX(), elenor.getY(), elenor.getZ(),
                        SoundEvents.WITHER_SPAWN, elenor.getSoundSource(), 1.0F, 0.7F);
                if (target != null) {
                    TerminusEngine.executeTerminus(serverLevel, elenor, target);
                }
                if (hasNonEntityBosses) {
                    com.automatavalkyries.annihilate.MadnessAnnihilation.sweep(serverLevel);
                }
            }
        }
    }

    /** 終焉は常に有効 */
    @Override
    public void setTerminusEnabled(boolean enabled) {
        super.setTerminusEnabled(true);
    }

    @Override
    public boolean isTerminusEnabled() {
        return true;
    }

    /** スニーク右クリックでも終焉は止められない */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.farthest_elenor.cannot_disable")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        retaliate(source);
        return hurt;
    }

    private void retaliate(DamageSource source) {
        if (retaliating || source == null || !(this.level() instanceof ServerLevel serverLevel)) return;
        // Madness Entities (The Moon / Ancient Nether Avatar 等) は Render thread や Thread-3 から
        // ServerLevel エンティティの hurt() を直接呼び出すことがあるため、サーバースレッド外ならキューへ委譲する
        if (!serverLevel.getServer().isSameThread()) {
            serverLevel.getServer().execute(() -> retaliateOnServerThread(serverLevel, source));
            return;
        }
        retaliateOnServerThread(serverLevel, source);
    }

    private void retaliateOnServerThread(ServerLevel serverLevel, DamageSource source) {
        if (retaliating || this.level() != serverLevel) return;
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (attacker == this || attacker instanceof FarthestElenorEntity) return; // 最果て同士では撃ち合わない
        if (attacker instanceof com.automatavalkyries.protect.IEprotected p && !p.isTerminusTestTarget()) return;

        if (attacker != null) {
            if (attacker instanceof LivingEntity living && !living.isAlive()
                    && !com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(attacker)) {
                return;
            }
            long now = serverLevel.getGameTime();
            UUID key = com.automatavalkyries.annihilate.MadnessAnnihilation.uuid(attacker);
            Long last = lastRetaliation.get(key);
            if (last != null && now - last < RETALIATE_COOLDOWN) return;
            lastRetaliation.put(key, now);
            if (lastRetaliation.size() > 64) lastRetaliation.clear();

            retaliating = true;
            try {
                if (attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    // 終焉はプレイヤーをサバイバルにするので、リスポーンしたら元のゲームモードに戻す（おまけのボスなので遊びを壊さない）
                    PREVIOUS_GAME_MODE.putIfAbsent(serverPlayer.getUUID(), serverPlayer.gameMode.getGameModeForPlayer());
                }
                if (attacker instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.automata_valkyries.farthest_elenor.retaliate")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
                }
                serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, getSoundSource(), 1.0F, 0.6F);
                triggerAnim("action", "skirt_spread");
                TerminusEngine.executeTerminus(serverLevel, this, attacker);
            } finally {
                retaliating = false;
            }
            return;
        }

        // 攻撃者情報なしの無属性強制被弾 (Madness Entities の C842479$C841676.forceHurt(outOfWorld) 等):
        // 非Entity型ボスまたは周囲128mの危険フラグ保持エンティティが存在する場合に反撃する
        boolean hasMadnessBosses = com.automatavalkyries.annihilate.MadnessAnnihilation.hasActiveNonEntityBosses();
        List<Entity> dangerousNearby = serverLevel.getEntities(
                this,
                this.getBoundingBox().inflate(128.0D),
                e -> e != this
                        && !(e instanceof FarthestElenorEntity)
                        && !(e instanceof com.automatavalkyries.protect.IEprotected p && !p.isTerminusTestTarget())
                        && !(e instanceof Player)
                        && (com.automatavalkyries.annihilate.MadnessAnnihilation.isMadness(e)
                                || com.automatavalkyries.annihilate.FlagKillPredicates.hasDangerousFlag(e)));
        if (!hasMadnessBosses && dangerousNearby.isEmpty()) return;

        long now = serverLevel.getGameTime();
        Long last = lastRetaliation.get(UNATTRIBUTED_ATTACK_KEY);
        if (last != null && now - last < RETALIATE_COOLDOWN) return;
        lastRetaliation.put(UNATTRIBUTED_ATTACK_KEY, now);

        retaliating = true;
        try {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, getSoundSource(), 1.0F, 0.6F);
            triggerAnim("action", "skirt_spread");
            if (hasMadnessBosses) {
                com.automatavalkyries.annihilate.MadnessAnnihilation.sweep(serverLevel);
            }
            for (Entity dangerous : dangerousNearby) {
                TerminusEngine.executeTerminus(serverLevel, this, dangerous);
            }
        } finally {
            retaliating = false;
        }
    }

    /** リスポーンしたら、反撃で倒される前のゲームモードに戻す */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.automatavalkyries.AutomataValkyries.MODID)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || event.isEndConquered()) return;
            net.minecraft.world.level.GameType previous = PREVIOUS_GAME_MODE.remove(player.getUUID());
            if (previous != null && previous != player.gameMode.getGameModeForPlayer()) player.setGameMode(previous);
        }
    }
}
