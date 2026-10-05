package com.automatavalkyries.weapon;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 抹殺した相手の落とし物の補い（2026-10-04 ユーザー指定「超抹殺した後もドロップ品や経験値が落ちるように汎用改良」）。
 * 自分の HP が 0 にならないと死亡処理（落とし物）をしない作りの相手（The Trial Monolith など）は、抹殺で死亡処理を通らずに消えて何も落とさない。
 * 抹殺の前に印を付け、ふつうの死亡処理の落とし物（{@link LivingDropsEvent}）が出なかったら、バニラの死んだときの落とし物と同じことを、
 * 相手の作り（生きている間は落とさない、という上書き）を通さずに行う: 戦利品テーブル・種類ごとの特別な落とし物・装備・
 * {@link LivingDropsEvent}（他の MOD が足す・止める）・経験値（プレイヤーが倒したときだけ。バニラと同じ）。
 * ゲームルール doMobLoot に従う。MOD ごとの専用の処理はしない
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class DeathLootFallback {

    /** ふつうの死亡処理の落とし物が出た個体 */
    private static final Set<LivingEntity> DROPPED = Collections.newSetFromMap(new WeakHashMap<>());

    private DeathLootFallback() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        DROPPED.add(event.getEntity());
    }

    /** 抹殺の前に呼ぶ（印を消す） */
    public static void before(LivingEntity victim) {
        DROPPED.remove(victim);
    }

    /** 抹殺の後に呼ぶ: ふつうの落とし物が出ていなければ補う */
    public static void after(ServerLevel level, LivingEntity victim, DamageSource source, @Nullable LivingEntity killer) {
        if (DROPPED.remove(victim)) return;
        drop(level, victim, source, killer);
    }

    /** バニラの死んだときの落とし物と同じこと（相手の上書きを通さない）。GameTest からも呼ぶ */
    public static void drop(ServerLevel level, LivingEntity victim, DamageSource source, @Nullable LivingEntity killer) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        Player player = killer instanceof Player p ? p : null;
        int looting = killer != null ? ForgeHooks.getLootingLevel(victim, killer, source) : 0;
        victim.captureDrops(new ArrayList<>());
        try {
            LootTable table = level.getServer().getLootData().getLootTable(victim.getLootTable());
            LootParams.Builder params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, victim)
                    .withParameter(LootContextParams.ORIGIN, victim.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
            if (player != null) params = params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
            table.getRandomItems(params.create(LootContextParamSets.ENTITY), victim.getLootTableSeed(), victim::spawnAtLocation);
            ((LivingEntityAccessor) victim).automata_valkyries$dropCustomDeathLoot(source, looting, player != null);
            ((LivingEntityAccessor) victim).automata_valkyries$dropEquipment();
        } catch (RuntimeException e) {
            AutomataValkyries.LOGGER.debug("Death loot fallback failed for {}", victim, e);
        }
        Collection<ItemEntity> drops = victim.captureDrops(null);
        if (drops != null && !ForgeHooks.onLivingDrops(victim, source, drops, looting, player != null)) drops.forEach(level::addFreshEntity);
        if (player != null) {
            int xp = ForgeEventFactory.getExperienceDrop(victim, player, victim.getExperienceReward());
            if (xp > 0) ExperienceOrb.award(level, victim.position(), xp);
        }
    }
}
