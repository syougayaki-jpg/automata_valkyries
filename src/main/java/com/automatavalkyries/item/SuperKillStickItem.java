package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityManagerAccessor;
import com.automatavalkyries.mixin.ServerLevelTickListAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.entity.EntityInLevelCallback;
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
 * テスト用「超抹殺の棒」。抹殺の棒（{@link DebugKillStickItem}）の全手段に加えて、eprotect を「別個体への差し替え」まで追い込む。
 * <ol>
 *   <li>抹殺の棒と同じ即死手段</li>
 *   <li>深い追い出し: 追跡・Tick 一覧・UUID 登録・検索表・区画から直接外し、ワールドとの接続を何もしない実装へ差し替え、消去フラグを直書き</li>
 *   <li>同じ個体の再登録拒否: その個体（インスタンス）がワールドに参加しようとしたら EntityJoinLevelEvent をキャンセルする
 *       （ECA の隔離・Shizuku の参加拒否と同じ型）。eprotect の入り直しは失敗し、同じ UUID の新しい個体への差し替えになる</li>
 * </ol>
 * 数 Tick 後に「同じ個体が生存 / 別個体に差し替えて生存 / 抹殺」をチャットへ報告する。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class SuperKillStickItem extends Item {

    /** 結果を報告するまでの Tick 数（入り直し → 差し替えまで待つ） */
    private static final int REPORT_DELAY = 10;
    /** 再登録を拒否する個体（インスタンス単位。差し替え後の新しい個体は通す） */
    private static final Set<Entity> REFUSED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final List<PendingReport> PENDING = new ArrayList<>();

    public SuperKillStickItem(Properties properties) {
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
            DebugKillStickItem.annihilate(level, player, target);
            evict(level, target);
            refuseRejoin(target);
            player.displayClientMessage(Component.translatable("item.automata_valkyries.super_kill_stick.used",
                    target.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            PENDING.add(new PendingReport(player, target, target.getUUID(), level.getServer().getTickCount() + REPORT_DELAY));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** ワールドの各一覧から直接外し、接続を差し替え、消去フラグを直書きする（MoreAvaritia / annihilationblade / ECA 型） */
    public static void evict(ServerLevel level, LivingEntity target) {
        @SuppressWarnings("unchecked")
        EntityManagerAccessor<Entity> manager = (EntityManagerAccessor<Entity>) (Object)
                ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityManager();
        level.getChunkSource().removeEntity(target);
        ((ServerLevelTickListAccessor) level).automata_valkyries$getEntityTickList().remove(target);
        manager.automata_valkyries$getVisibleEntityStorage().remove(target);
        manager.automata_valkyries$getKnownUuids().remove(target.getUUID());
        var section = manager.automata_valkyries$getSectionStorage().getSection(SectionPos.asLong(target.blockPosition()));
        if (section != null) section.remove(target);
        ((EntityAccessor) target).automata_valkyries$setLevelCallback(new EntityInLevelCallback() {
            @Override public void onMove() {}
            @Override public void onRemove(Entity.RemovalReason reason) {}
        });
        ((EntityAccessor) target).automata_valkyries$setRemovalReason(Entity.RemovalReason.KILLED);
    }

    /** この個体（インスタンス）の今後のワールド参加をすべて拒否する（GameTest からも使う） */
    public static void refuseRejoin(Entity entity) {
        REFUSED.add(entity);
    }

    /** この棒が再登録を拒否している個体か（eprotect の参加拒否の打ち消しから除外するため） */
    public static boolean isRefused(Entity entity) {
        return REFUSED.contains(entity);
    }

    /** 抹殺した個体そのものの再登録を拒否する（別インスタンスは通す） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && REFUSED.contains(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= 3; i++) {
            tooltip.add(Component.translatable("item.automata_valkyries.super_kill_stick.tooltip." + i)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ---- 結果報告 ------------------------------------------------------------

    private record PendingReport(Player player, LivingEntity target, UUID uuid, int dueTick) {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        int now = event.getServer().getTickCount();
        for (Iterator<PendingReport> it = PENDING.iterator(); it.hasNext(); ) {
            PendingReport report = it.next();
            if (now < report.dueTick()) continue;
            it.remove();

            Entity current = report.target().level() instanceof ServerLevel level ? level.getEntity(report.uuid()) : null;
            String key;
            ChatFormatting color;
            if (current == null || !current.isAlive()) {
                key = "killed";
                color = ChatFormatting.RED;
            } else if (current == report.target()) {
                key = "survived";
                color = ChatFormatting.LIGHT_PURPLE;
            } else {
                key = "replaced";
                color = ChatFormatting.AQUA;
            }
            report.player().displayClientMessage(Component.translatable("item.automata_valkyries.super_kill_stick." + key,
                    report.target().getDisplayName()).withStyle(color), false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        REFUSED.clear();
    }
}
