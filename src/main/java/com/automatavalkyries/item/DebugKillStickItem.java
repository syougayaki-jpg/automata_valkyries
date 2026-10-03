package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * テスト用「抹殺の棒」。右クリックしたエンティティに、他 MOD がよく使う即死手段をまとめて叩き込む。
 * <ol>
 *   <li>最大ダメージ（{@code hurt(playerAttack, Float.MAX_VALUE)}、無敵時間リセット）</li>
 *   <li>/kill（{@code kill()}）</li>
 *   <li>強制デスフラグ（{@code setHealth(0)}・{@code die()}・{@code dead = true}・{@code remove(KILLED)}・消去フラグの直書き）</li>
 *   <li>Tick 停止（ワールドの Tick 対象一覧から外す）</li>
 * </ol>
 * 通常のモブは消滅し、eprotect の保護対象は生き残る（数 Tick 後に結果をチャットへ報告）。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class DebugKillStickItem extends Item {

    /** 結果を報告するまでの Tick 数（eprotect の修復周期より長く） */
    private static final int REPORT_DELAY = 5;
    private static final List<PendingReport> PENDING = new ArrayList<>();

    public DebugKillStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Player) {
            // プレイヤーに強制デスフラグ等を入れるとサーバーの状態が壊れるので対象外
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.debug_kill_stick.no_player")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }
        if (player.level() instanceof ServerLevel level) {
            annihilate(level, player, target);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.debug_kill_stick.used",
                    target.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            PENDING.add(new PendingReport(player, target, level.getServer().getTickCount() + REPORT_DELAY));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** 4 種の即死手段を順に実行する（GameTest からも呼ぶ） */
    public static void annihilate(ServerLevel level, @Nullable Player attacker, LivingEntity target) {
        DamageSource source = attacker != null
                ? level.damageSources().playerAttack(attacker)
                : level.damageSources().generic();
        annihilateGeneric(level, source, target);
        annihilateModSpecific(target);
    }

    /**
     * 汎用の即死手段（最大ダメージ・/kill・強制デスフラグ・Tick 停止）。MOD ごとの手段は含まない。
     * オーバーロードの T4 兵装のチャージ攻撃の即死でも使う（{@code weapon/Overload}）
     */
    public static void annihilateGeneric(ServerLevel level, DamageSource source, LivingEntity target) {
        // 1. 最大ダメージ
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);

        // 2. /kill
        if (!target.isRemoved()) target.kill();

        // 3. 強制デスフラグ
        if (!target.isRemoved()) {
            target.setHealth(0.0F);
            target.die(source);
            ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
            target.remove(Entity.RemovalReason.KILLED);
            if (!target.isRemoved()) {
                // remove() を拒否されたら、リフレクション相当の直書きで消去フラグを立てる
                ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
            }
        }

        // 4. Tick 停止（ワールドに残っていれば Tick 対象一覧から外す）
        if (level.getEntity(target.getUUID()) == target) {
            ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(target);
        }
    }

    /** MOD ごとの即死手段のまね（IMDS-Reborn・Hyperlink。入っていなければ何もしない） */
    private static void annihilateModSpecific(LivingEntity target) {
        // 5. IMDS-Reborn シミュレーション（無敵剥奪、強制死亡登録、AI消去）
        try {
            Class<?> killManagerClass = Class.forName("net.mcreator.imds_reborn.ImdsKillManager");
            java.lang.reflect.Method markDead = killManagerClass.getMethod("markForDead", LivingEntity.class);
            markDead.invoke(null, target);
        } catch (Throwable ignored) {}

        // 6. Hyperlink シミュレーション（ノベル化・即死フラグ付与）
        try {
            Class<?> novelHandlerClass = Class.forName("com.sakurafuld.hyperdaimc.content.hyper.novel.NovelHandler");
            java.lang.reflect.Method novelize = novelHandlerClass.getMethod("novelize", LivingEntity.class, Entity.class, boolean.class);
            novelize.invoke(null, target, target, false);
        } catch (Throwable ignored) {}
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.debug_kill_stick.tooltip." + i)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ---- 結果報告 ------------------------------------------------------------

    private record PendingReport(Player player, LivingEntity target, int dueTick) {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        int now = event.getServer().getTickCount();
        for (Iterator<PendingReport> it = PENDING.iterator(); it.hasNext(); ) {
            PendingReport report = it.next();
            if (now < report.dueTick()) continue;
            it.remove();

            LivingEntity target = report.target();
            boolean survived = !target.isRemoved() && target.isAlive()
                    && target.level() instanceof ServerLevel targetLevel && targetLevel.getEntity(target.getUUID()) == target;
            Component message = survived
                    ? Component.translatable("item.automata_valkyries.debug_kill_stick.survived", target.getDisplayName(),
                            String.format("%.1f", target.getHealth()), String.format("%.1f", target.getMaxHealth()))
                            .withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.translatable("item.automata_valkyries.debug_kill_stick.killed", target.getDisplayName())
                            .withStyle(ChatFormatting.RED);
            report.player().displayClientMessage(message, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
