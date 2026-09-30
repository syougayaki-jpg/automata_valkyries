package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * テスト用「確認棒」。eprotect の抜け道 3 つを順に試して、結果をチャットへ報告する。
 * <ol>
 *   <li>無敵時間を消して hurt() を 30 連打（1 Tick のうちの連打で正規撃破まで削れないか）</li>
 *   <li>チャンクのアンロードを装った消去（UNLOADED_TO_CHUNK / UNLOADED_WITH_PLAYER）</li>
 *   <li>深い追い出し＋その個体の EntityJoinLevelEvent を最優先でキャンセル（他 MOD の参加拒否と同じ型）</li>
 * </ol>
 * 1 はその場で、2 は {@link #UNLOAD_CHECK_DELAY} Tick 後、3 はさらに {@link #JOIN_CHECK_DELAY} Tick 後に報告する。
 * 1 で被弾量の時間窓（10 秒）を使い切るので、その間の通常攻撃では HP が減らない。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class VerifyStickItem extends Item {

    private static final int RAPID_HITS = 30;
    private static final int UNLOAD_CHECK_DELAY = 5;
    private static final int JOIN_CHECK_DELAY = 10;

    /** 参加を拒否する個体（インスタンス単位）。超抹殺の棒と違い、eprotect の打ち消しの対象になる */
    private static final Set<Entity> JOIN_REFUSED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<Pending> PENDING = new ArrayList<>();

    private enum Stage { UNLOAD_CHECK, JOIN_CHECK }

    private record Pending(Player player, LivingEntity target, UUID uuid, Stage stage, int dueTick) {}

    public VerifyStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof Player) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("item.automata_valkyries.debug_kill_stick.no_player")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.FAIL;
        }
        if (player.level() instanceof ServerLevel level) {
            say(player, Component.translatable("item.automata_valkyries.verify_stick.start", target.getDisplayName()), ChatFormatting.GRAY);
            rapidHits(level, player, target);
            if (!target.isRemoved()) {
                target.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                target.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                schedule(level, player, target, Stage.UNLOAD_CHECK, UNLOAD_CHECK_DELAY);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** ① 無敵時間を消して上限を超えるダメージを連打し、減った割合を報告する */
    private static void rapidHits(ServerLevel level, Player player, LivingEntity target) {
        float max = target.getMaxHealth();
        float before = target.getHealth();
        for (int i = 0; i < RAPID_HITS && target.isAlive(); i++) {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().playerAttack(player), 1000.0F);
        }
        float after = target.isAlive() ? target.getHealth() : 0.0F;
        float lossPercent = max > 0.0F ? (before - after) / max * 100.0F : 0.0F;
        boolean ok = target.isAlive() && lossPercent <= 10.1F;
        say(player, Component.translatable("item.automata_valkyries.verify_stick.hits",
                        String.format("%.1f", before), String.format("%.1f", after), String.format("%.1f", lossPercent)),
                ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void schedule(ServerLevel level, Player player, LivingEntity target, Stage stage, int delay) {
        PENDING.add(new Pending(player, target, target.getUUID(), stage, level.getServer().getTickCount() + delay));
    }

    private static void say(Player player, Component message, ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        int now = event.getServer().getTickCount();
        List<Pending> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now >= p.dueTick()) {
                due.add(p);
                it.remove();
            }
        }
        for (Pending p : due) {
            if (!(p.target().level() instanceof ServerLevel level)) continue;
            Entity current = level.getEntity(p.uuid());
            switch (p.stage()) {
                case UNLOAD_CHECK -> {
                    boolean ok = current == p.target() && !p.target().isRemoved();
                    say(p.player(), Component.translatable("item.automata_valkyries.verify_stick.unload." + (ok ? "ok" : "ng")),
                            ok ? ChatFormatting.GREEN : ChatFormatting.RED);
                    if (ok) {
                        // ③ 追い出して入り直しを強制し、その参加を最優先で拒否する
                        JOIN_REFUSED.add(p.target());
                        SuperKillStickItem.evict(level, p.target());
                        schedule(level, p.player(), p.target(), Stage.JOIN_CHECK, JOIN_CHECK_DELAY);
                    }
                }
                case JOIN_CHECK -> {
                    JOIN_REFUSED.remove(p.target());
                    String key;
                    ChatFormatting color;
                    if (current == p.target() && !p.target().isRemoved()) {
                        key = "ok";
                        color = ChatFormatting.GREEN;
                    } else if (current != null && current.isAlive()) {
                        key = "replaced";
                        color = ChatFormatting.AQUA;
                    } else {
                        key = "ng";
                        color = ChatFormatting.RED;
                    }
                    say(p.player(), Component.translatable("item.automata_valkyries.verify_stick.join." + key), color);
                }
            }
        }
    }

    /** ③ の参加拒否（他 MOD の最優先キャンセルを再現） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && JOIN_REFUSED.contains(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 4; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.verify_stick.tooltip." + i)
                    .withStyle(i == 1 ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        JOIN_REFUSED.clear();
    }
}
