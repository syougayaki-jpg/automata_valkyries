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

    private final Map<UUID, Long> lastRetaliation = new HashMap<>();
    /** 反撃の最中か（反撃の処理から自分への被弾が戻ってきても、さらに反撃しない） */
    private boolean retaliating = false;

    public FarthestElenorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        super.setTerminusEnabled(true);
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

    /** 最果てのエレノアはチート武器等の過剰攻撃による異常修復過多でも絶対に退避・消滅しない */
    @Override
    public boolean canRetreatOnExcessiveAnomalies() {
        return false;
    }

    /** 最果てのエレノアはプレイヤーが倒されても勝手に撤収（ディスポーン）しない */
    @Override
    protected void tickRetreat() {
        // 退避・ディスポーンを行わない
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
        if (retaliating || !(this.level() instanceof ServerLevel serverLevel)) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living) || attacker == this || !living.isAlive()) return;
        if (attacker instanceof FarthestElenorEntity) return; // 最果て同士では撃ち合わない
        long now = serverLevel.getGameTime();
        Long last = lastRetaliation.get(attacker.getUUID());
        if (last != null && now - last < RETALIATE_COOLDOWN) return;
        lastRetaliation.put(attacker.getUUID(), now);
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
