package com.automatavalkyries.client;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.annihilate.AnnihilationExecutionAgent;
import com.automatavalkyries.annihilate.MadnessAnnihilation;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.EntityLookupAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.mixin.client.ClientLevelAccessor;
import com.automatavalkyries.mixin.client.TransientEntitySectionManagerAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.EcaLastSwordHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * クライアント側で、超絶抹殺の棒で抹消されたエンティティを確実に取り除く。
 * <ol>
 *   <li>抹消済みとして記録する（Witherzilla の生命維持フラグを false にする既存の Mixin がクライアントでも効くように）</li>
 *   <li>バニラの消去を通す</li>
 *   <li>それでも残っていれば、検索表・区画・Tick 一覧・部位表から直接外す（何が掴んでいたかをログに出す）</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, value = Dist.CLIENT)
public final class ClientAnnihilation {

    private ClientAnnihilation() {}

    public static void purge(List<UUID> uuids, List<Integer> ids) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Minecraft mc = Minecraft.getInstance();
        UUID localPlayerUuid = mc.player != null ? mc.player.getUUID() : null;
        int localPlayerId = mc.player != null ? mc.player.getId() : -1;

        // モブのみ抹消済みマークをつける（プレイヤーおよび保護対象はリスポーンできなくなる・表示が消えるためマークしない）
        for (UUID u : uuids) {
            if (localPlayerUuid != null && localPlayerUuid.equals(u)) continue;
            boolean isProtected = false;
            for (Entity e : level.entitiesForRendering()) {
                if (e.getUUID().equals(u) && e instanceof com.automatavalkyries.protect.IEprotected) {
                    isProtected = true;
                    break;
                }
            }
            if (isProtected) continue;
            AnnihilationExecutionAgent.markAnnihilated(u);
        }

        // プレイヤー自身および保護対象エンティティは絶対にクライアントレベルから除外（removeEntity）しない
        for (int id : ids) {
            if (id == localPlayerId) continue;
            Entity entity = level.getEntity(id);
            if (entity instanceof net.minecraft.world.entity.player.Player) continue;
            if (entity instanceof com.automatavalkyries.protect.IEprotected) continue;
            if (MadnessAnnihilation.isMadness(entity)) {
                MadnessAnnihilation.removeClientEntity(entity);
                forceRemove(level, entity);
                continue;
            }
            AnnihilationExecutionAgent.runWithHyperlinkSpecialRemove(() -> {
                if (entity != null) {
                    AnnihilationExecutionAgent.evictHyperlinkShadowStructures(level, entity, id, MadnessAnnihilation.uuid(entity));
                }
                level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            });
        }

        Set<UUID> uuidSet = new HashSet<>(uuids);
        Set<Integer> idSet = new HashSet<>(ids);
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.player.Player) continue; // プレイヤーは決して forceRemove しない
            if (entity instanceof com.automatavalkyries.protect.IEprotected) continue; // 保護対象は決して forceRemove しない
            if (uuidSet.contains(MadnessAnnihilation.uuid(entity)) || idSet.contains(MadnessAnnihilation.id(entity))) leftovers.add(entity);
        }
        for (Entity entity : leftovers) {
            forceRemove(level, entity);
        }

        // クライアント専用環境（リモート接続時）のみ Flashfur Level メモリをクライアント側でパージ
        // （シングルプレイの統合サーバー環境では Server thread と metapotentFlashfurList を共有するため、
        //   Render thread から ArrayList.size を縮小すると tickMetapotentFlashfurs の Iterator と競合する）
        if (!mc.hasSingleplayerServer()) {
            AnnihilationExecutionAgent.purgeDeadFlashfurs();
            AnnihilationExecutionAgent.wipeFlashfurLevelMemory();
        }

        // 自プレイヤーが終焉対象に含まれている場合、消去ではなく死亡・消滅フラグを確定して DeathScreen を安全に表示
        if (mc.player != null && (uuidSet.contains(mc.player.getUUID()) || idSet.contains(mc.player.getId()))) {
            handlePlayerDeath(mc.player.getUUID());
        }
    }

    public static void sanitizeDeadLocalPlayer(net.minecraft.world.entity.player.Player player) {
        if (player == null
                || com.automatavalkyries.protect.AgentPlayerProtection.isProtected(player)
                || com.automatavalkyries.annihilate.TerminusEngine.isProtectedByTinyCourage(player)) return;
        try {
            com.automatavalkyries.annihilate.TerminusPlayerExecutor.markPlayerTerminusDead(player);
            com.automatavalkyries.protect.flag.GuardLists.purge(player);
            if (player.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn")) {
                com.automatavalkyries.annihilate.TerminusPlayerExecutor.stripPlayerDefensiveItems(player);
            }
            com.automatavalkyries.protect.EprotectImdsCounter.counter(player);
            EcaLastSwordHelper.forceClearEcaAndTlsProtection(player);
            EcaLastSwordHelper.cleanupPlayerRestraints(player);
            com.automatavalkyries.protect.flag.StateScanner.apply(player,
                    com.automatavalkyries.protect.flag.StateScanner.BREAK_PLAYER_DEFENSES,
                    java.util.EnumSet.allOf(com.automatavalkyries.protect.flag.StateScanner.Source.class));
            com.automatavalkyries.protect.flag.StateScanner.apply(player,
                    com.automatavalkyries.protect.flag.StateScanner.RELEASE_DEAD_PLAYER,
                    java.util.EnumSet.allOf(com.automatavalkyries.protect.flag.StateScanner.Source.class));
            com.automatavalkyries.annihilate.TerminusPlayerExecutor.forceZeroHealthDirect(player);
        } catch (Throwable ignored) {}
    }

    public static void handlePlayerDeath(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            mc.execute(() -> {
                try {
                    if (mc.player == null || com.automatavalkyries.annihilate.TerminusEngine.isProtectedByTinyCourage(mc.player)) return;
                    sanitizeDeadLocalPlayer(mc.player);
                    if (!(mc.screen instanceof DeathScreen)) {
                        mc.setScreen(new DeathScreen(
                                net.minecraft.network.chat.Component.translatable("death.attack.automata_valkyries.terminus", mc.player.getScoreboardName()),
                                false));
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !com.automatavalkyries.protect.AgentPlayerProtection.isProtected(mc.player)) {
            if (com.automatavalkyries.annihilate.IsolationDamageEngine.shouldSuppressPlayerInventoryDefense(mc.player)) {
                com.automatavalkyries.annihilate.IsolationDamageEngine.suppressPlayerActiveDefensesOnTick(mc.player);
            }
            if (mc.screen instanceof DeathScreen
                    || !DirectRead.hasHealth(mc.player)
                    || com.automatavalkyries.annihilate.IsolationDamageEngine.isIsolatedDead(mc.player)
                    || com.automatavalkyries.annihilate.TerminusPlayerExecutor.isTerminusDeadOrDying(mc.player)) {
                // 死亡画面表示中または真のHP(DATA_HEALTH_ID)<=0 の間、クライアント側の防御アイテム・GuardLists・StateScannerを Tick の前後双方で解除し、
                // DeathScreenMixin.onTick による setScreen(null) や IMDS の inventoryTick / MinecraftMixin.imds_deathscreen による死亡画面拒否を防ぐ
                sanitizeDeadLocalPlayer(mc.player);
                if (event.phase == TickEvent.Phase.START
                        && (!DirectRead.hasHealth(mc.player) || com.automatavalkyries.annihilate.IsolationDamageEngine.isIsolatedDead(mc.player))
                        && mc.player.deathTime >= 2 && !(mc.screen instanceof DeathScreen)) {
                    mc.setScreen(new DeathScreen(
                            net.minecraft.network.chat.Component.translatable("death.attack.automata_valkyries.terminus", mc.player.getScoreboardName()),
                            false));
                }
            }
        }
    }

    /**
     * サーバー側で {@code WorldPresence.replaceWithClone}（超抹殺の棒などによる再登録拒否後の同 UUID クローン差し替え）が起きた際、
     * 古い個体（{@code oldId}）の {@code ClientboundRemoveEntitiesPacket} は保護中だったため {@code EprotectRemovePacketMixin} に遮断されている。
     * そのまま新しいクローン個体（{@code newId}, 同一 {@code UUID}）の {@code ClientboundAddEntityPacket} がクライアントへ届くと、
     * バニラの {@code ClientLevel.addEntity} は {@code newId} しか消さないため {@code EntityLookup.add} が {@code "Duplicate entity UUID"} で
     * 新個体の {@code byId}/{@code byUuid} 登録を拒否し、クライアント上に古い幽霊個体（{@code oldId}）が居座って
     * 右クリック操作不能（サーバーに存在しない {@code oldId} へパケットを送る）や頭上観測 UI の同期停止を引き起こす。
     * <p>
     * そのため、クライアントワールドへ非プレイヤー個体が参加する直前に同一 UUID の古い個体が残っていれば即座に完全パージし、
     * 新しいクローン個体が正規に {@code byId}/{@code byUuid} へ登録されるようにする。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ClientLevel clientLevel)) return;
        Entity incoming = event.getEntity();
        if (incoming == null || incoming instanceof net.minecraft.world.entity.player.Player) return;
        UUID uuid = incoming.getUUID();
        if (uuid == null) return;
        try {
            ClientLevelAccessor levelAcc = (ClientLevelAccessor) clientLevel;
            @SuppressWarnings("unchecked")
            TransientEntitySectionManagerAccessor<Entity> manager =
                    (TransientEntitySectionManagerAccessor<Entity>) (Object) levelAcc.automata_valkyries$getEntityStorage();
            @SuppressWarnings("unchecked")
            EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();
            Entity existing = lookup.automata_valkyries$getByUuid().get(uuid);
            if (existing != null && existing != incoming) {
                evictStaleClientDuplicate(clientLevel, existing);
            }
        } catch (Throwable ignored) {}
    }

    private static void evictStaleClientDuplicate(ClientLevel level, Entity stale) {
        int rawId = MadnessAnnihilation.id(stale);
        try {
            ProtectedEntityKeeper.onRemoved(List.of(stale.getId(), rawId));
        } catch (Throwable ignored) {}
        try {
            level.removeEntity(stale.getId(), Entity.RemovalReason.DISCARDED);
        } catch (Throwable ignored) {}
        try {
            ClientLevelAccessor clientLevel = (ClientLevelAccessor) level;
            @SuppressWarnings("unchecked")
            TransientEntitySectionManagerAccessor<Entity> manager =
                    (TransientEntitySectionManagerAccessor<Entity>) (Object) clientLevel.automata_valkyries$getEntityStorage();
            @SuppressWarnings("unchecked")
            EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();

            EntityAccessor accessor = (EntityAccessor) stale;
            accessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);

            UUID rawUuid = MadnessAnnihilation.uuid(stale);
            try {
                manager.automata_valkyries$getEntityStorage().remove(stale);
            } catch (Throwable ignored) {}

            lookup.automata_valkyries$getById().remove(rawId, stale);
            lookup.automata_valkyries$getByUuid().remove(rawUuid, stale);

            EntitySectionStorage<Entity> sections = manager.automata_valkyries$getSectionStorage();
            for (long chunk : sections.getAllChunksWithExistingSections().toLongArray()) {
                for (EntitySection<Entity> section : sections.getExistingSectionsInChunk(chunk).toList()) {
                    AnnihilationExecutionAgent.evictFromEntitySection(section, stale);
                }
            }
            com.automatavalkyries.annihilate.TickListEviction.remove(clientLevel.automata_valkyries$getTickingEntities(), rawId);
            if (stale.isMultipartEntity() && stale.getParts() != null) {
                for (PartEntity<?> part : stale.getParts()) {
                    if (part != null) clientLevel.automata_valkyries$getPartEntities().remove(part.getId());
                }
            }
            accessor.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);
        } catch (Throwable ignored) {}
    }

    private static void forceRemove(ClientLevel level, Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return;
        if (entity instanceof com.automatavalkyries.protect.IEprotected) return;
        ClientLevelAccessor clientLevel = (ClientLevelAccessor) level;
        @SuppressWarnings("unchecked")
        TransientEntitySectionManagerAccessor<Entity> manager =
                (TransientEntitySectionManagerAccessor<Entity>) (Object) clientLevel.automata_valkyries$getEntityStorage();
        @SuppressWarnings("unchecked")
        EntityLookupAccessor<Entity> lookup = (EntityLookupAccessor<Entity>) manager.automata_valkyries$getEntityStorage();

        EntityAccessor accessor = (EntityAccessor) entity;
        accessor.automata_valkyries$setRemovalReason(Entity.RemovalReason.DISCARDED);

        int rawId = MadnessAnnihilation.id(entity);
        UUID rawUuid = MadnessAnnihilation.uuid(entity);
        AnnihilationExecutionAgent.runWithHyperlinkSpecialRemove(() -> {
            AnnihilationExecutionAgent.evictHyperlinkShadowStructures(level, entity, rawId, rawUuid);
            try {
                manager.automata_valkyries$getEntityStorage().remove(entity);
            } catch (Throwable ignored) {}
        });

        boolean inLookup = lookup.automata_valkyries$getById().remove(rawId) != null;
        lookup.automata_valkyries$getByUuid().remove(rawUuid);

        boolean inSection = false;
        EntitySectionStorage<Entity> sections = manager.automata_valkyries$getSectionStorage();
        for (long chunk : sections.getAllChunksWithExistingSections().toLongArray()) {
            for (EntitySection<Entity> section : sections.getExistingSectionsInChunk(chunk).toList()) {
                if (AnnihilationExecutionAgent.evictFromEntitySection(section, entity)) inSection = true;
            }
        }

        boolean ticking = com.automatavalkyries.annihilate.TickListEviction.remove(clientLevel.automata_valkyries$getTickingEntities(), MadnessAnnihilation.id(entity));

        if (entity.isMultipartEntity() && entity.getParts() != null) {
            for (PartEntity<?> part : entity.getParts()) {
                if (part != null) clientLevel.automata_valkyries$getPartEntities().remove(part.getId());
            }
        }
        accessor.automata_valkyries$setLevelCallback(EntityInLevelCallback.NULL);

        AutomataValkyries.LOGGER.info("[AnnihilationAgent] Force-removed leftover client entity {} (lookup={}, section={}, ticking={})",
                entity, inLookup, inSection, ticking);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AnnihilationExecutionAgent.clearMarks();
        MadnessAnnihilation.resetSwitches();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        cleanPlayerEraseState(event.getPlayer());
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        com.automatavalkyries.annihilate.TerminusPlayerExecutor.clearPlayerTerminusDead(event.getOldPlayer());
        cleanPlayerEraseState(event.getNewPlayer());
    }

    public static void handlePlayerRespawn(UUID uuid) {
        if (uuid != null) {
            com.automatavalkyries.protect.plus.EprotectPlusLink.clearPlayerFromKillModLists(uuid);
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && (uuid == null || mc.player.getUUID().equals(uuid))) {
                cleanPlayerEraseState(mc.player);
                try {
                    ((LivingEntityAccessor) mc.player).automata_valkyries$setDead(false);
                    mc.player.deathTime = 0;
                    mc.player.setPose(net.minecraft.world.entity.Pose.STANDING);
                    if (!DirectRead.hasHealth(mc.player)) {
                        com.automatavalkyries.annihilate.IsolationDamageEngine.forceWriteHealth(mc.player, mc.player.getMaxHealth());
                    }
                    if (mc.screen instanceof DeathScreen) {
                        mc.setScreen(null);
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    public static void cleanPlayerEraseState(net.minecraft.world.entity.player.Player player) {
        if (player == null) return;
        try {
            com.automatavalkyries.annihilate.TerminusPlayerExecutor.clearPlayerTerminusDead(player);
            com.automatavalkyries.annihilate.IsolationDamageEngine.clearState(player);
            // 1. クライアント側でも他 MOD の防御弱体化・消滅フラグを完全解除
            com.automatavalkyries.protect.flag.StateScanner.apply(player,
                    com.automatavalkyries.protect.flag.StateScanner.CLEAN_PROTECTED,
                    java.util.EnumSet.allOf(com.automatavalkyries.protect.flag.StateScanner.Source.class));

            // 2. No-Sugar / 他 MOD の消滅リストからプレイヤー UUID を確実に除外
            com.automatavalkyries.protect.flag.KillLists.purge(player);
            com.automatavalkyries.protect.plus.EprotectPlusLink.clearPlayerFromKillModLists(player.getUUID());

            // 3. ECA / The Last Sword の位置固定・ヘルスロック・無敵フックをクライアント側でも解除
            com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);

            // 4. Trial Monolith 等の魂ダメージ・状態異常をクライアント側でも解除
            com.automatavalkyries.protect.KillModCounters.counterAll(player);
        } catch (Throwable ignored) {}
    }
}
