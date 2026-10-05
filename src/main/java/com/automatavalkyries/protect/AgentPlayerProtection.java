package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特権守護コア・Javaエージェント連携プレイヤー保護（Privilege Aegis System）。
 * <p>
 * プレイヤーの魂（UUID）にJVMエージェント（LifeGuard / Eprotect+）の不可侵特権を刻印し、
 * インベントリ剥奪、Unsafeメモリ改ざん、getHealth偽装、強制消去、虚無追放、即死イベントを
 * JVM最外郭（Outermost Layer）で完全無効化する。
 * </p>
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class AgentPlayerProtection {

    public static final String NBT_BOUND_KEY = "automata_valkyries:privilege_aegis_bound";
    public static final String NBT_DISABLED_KEY = "automata_valkyries:privilege_aegis_disabled";

    /** JVM静的メモリで保持される刻印済みプレイヤーUUID（アイテムが剥奪されても持続） */
    private static final Set<UUID> BOUND_PLAYERS = ConcurrentHashMap.newKeySet();
    /** スニーク右クリックで明示的に解除（無効化）されたプレイヤーUUID */
    private static final Set<UUID> DISABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    private AgentPlayerProtection() {}

    /**
     * 指定エンティティが特権エージェント保護下にあるプレイヤーか判定する。
     * <p>
     * 1. スニーク右クリックで明示的に解除（Disabled）されている場合は保護しない（被弾・即死検証用）。
     * 2. 魂（UUID）に刻印されている場合（アイテム剥奪時も持続）は常に保護。
     * 3. インベントリまたは手持ちに「特権守護の核」を所持している場合（自動発動）も保護。
     * </p>
     */
    public static boolean isProtected(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) return false;
        UUID uuid = player.getUUID();
        if (DISABLED_PLAYERS.contains(uuid)) return false;
        try {
            if (player.getPersistentData().getBoolean(NBT_DISABLED_KEY)) return false;
        } catch (Throwable ignored) {}

        if (BOUND_PLAYERS.contains(uuid)) return true;
        try {
            if (player.getPersistentData().getBoolean(NBT_BOUND_KEY)) return true;
        } catch (Throwable ignored) {}

        return hasAegisItem(player);
    }

    /**
     * プレイヤーが「特権守護の核」をインベントリまたは手持ちに所持しているか。
     */
    public static boolean hasAegisItem(Player player) {
        if (player.containerMenu != null && player.containerMenu.getCarried().is(ModItems.PRIVILEGE_AEGIS.get())) {
            return true;
        }
        Inventory inv = player.getInventory();
        if (inv == null) return false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.PRIVILEGE_AEGIS.get())) return true;
        }
        return false;
    }

    /**
     * プレイヤーの魂（UUID）にJavaエージェントの不可侵特権を刻印する。
     */
    public static void bind(ServerPlayer player) {
        bind((Player) player);
    }

    public static void bind(Player player) {
        UUID uuid = player.getUUID();
        DISABLED_PLAYERS.remove(uuid);
        try {
            player.getPersistentData().remove(NBT_DISABLED_KEY);
        } catch (Throwable ignored) {}
        BOUND_PLAYERS.add(uuid);
        try {
            player.getPersistentData().putBoolean(NBT_BOUND_KEY, true);
        } catch (Throwable ignored) {}

        // LifeGuard および Eprotect+ の特権エージェントを最外郭へ接続
        LifeGuardLink.install();
        LifeGuardLink.ensureLast();
        EprotectPlusLink.clearPlayerFromKillModLists(uuid);
        KillModCounters.counterAll(player);

        // 演出 & メッセージ
        if (player.level() instanceof ServerLevel level) {
            Vec3 pos = player.position();
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0D, pos.z, 50, 0.5, 0.8, 0.5, 0.15);
            level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0D, pos.z, 2, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.2F);

            player.sendSystemMessage(Component.translatable("message.automata_valkyries.privilege_aegis.bound")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            player.displayClientMessage(Component.translatable("message.automata_valkyries.privilege_aegis.bound_actionbar")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        }

        AutomataValkyries.LOGGER.info("[PrivilegeAegis] Bound player {} ({}) to JVM Agent Protection", player.getScoreboardName(), uuid);
    }

    /**
     * プレイヤーの特権刻印を解除する（安全弁）。
     */
    public static void unbind(ServerPlayer player) {
        unbind((Player) player);
    }

    public static void unbind(Player player) {
        UUID uuid = player.getUUID();
        BOUND_PLAYERS.remove(uuid);
        DISABLED_PLAYERS.add(uuid);
        try {
            player.getPersistentData().remove(NBT_BOUND_KEY);
            player.getPersistentData().putBoolean(NBT_DISABLED_KEY, true);
        } catch (Throwable ignored) {}

        // クリエイティブ/スペクテイターでなければ無敵アビリティを即時解除し、無敵時間もリセット
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
        }
        player.invulnerableTime = 0;

        if (player.level() instanceof ServerLevel level) {
            Vec3 pos = player.position();
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);

            player.sendSystemMessage(Component.translatable("message.automata_valkyries.privilege_aegis.unbound")
                    .withStyle(ChatFormatting.YELLOW));
            player.displayClientMessage(Component.translatable("message.automata_valkyries.privilege_aegis.unbound_actionbar")
                    .withStyle(ChatFormatting.RED), true);
        }

        AutomataValkyries.LOGGER.info("[PrivilegeAegis] Unbound player {} ({}) from JVM Agent Protection", player.getScoreboardName(), uuid);
    }

    /**
     * プレイヤーが刻印されているか。
     */
    public static boolean isBound(Player player) {
        return BOUND_PLAYERS.contains(player.getUUID()) || player.getPersistentData().getBoolean(NBT_BOUND_KEY);
    }

    // ---- 常時監視・自己修復・イベントフック ------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (!isProtected(player)) return;

        // 1. 最大HPの不可侵保証 & HP の完全維持
        var maxAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxAttr != null) {
            if (maxAttr.getBaseValue() < 20.0D) {
                maxAttr.setBaseValue(20.0D);
            }
            if (player.getMaxHealth() < 20.0F || !Float.isFinite(player.getMaxHealth())) {
                for (var modifier : new java.util.ArrayList<>(maxAttr.getModifiers())) {
                    if (modifier.getAmount() < 0) {
                        maxAttr.removeModifier(modifier);
                    }
                }
            }
        }
        float maxHealth = Math.max(player.getMaxHealth(), 20.0F);
        if (player.getHealth() < maxHealth) {
            player.setHealth(maxHealth);
        }

        // 2. バニラ無敵フラグ & 無敵時間の維持
        player.getAbilities().invulnerable = true;
        player.invulnerableTime = 20;

        // 3. Unsafe / メモリ直書きによる死亡・消滅フラグの強制復旧
        try {
            ((LivingEntityAccessor) player).automata_valkyries$setDead(false);
            player.deathTime = 0;
            ((EntityAccessor) player).automata_valkyries$setRemovalReason(null);
        } catch (Throwable ignored) {}

        // 4. 他 MOD による有害タグ・拘束のパージ
        try {
            CompoundTag tag = player.getPersistentData();
            tag.remove("tlsWorldAnchor");
            tag.remove("tlsWorldAnchorMax");
            tag.remove("tlsIsProtected");
            tag.remove("ecaInvulnerable");
            tag.remove("ecaResurrectionTracked");
            tag.remove("ecaHealthLockEnc");
            tag.remove("death");
            tag.remove("remove");
            tag.remove("DATA_NOVELIZED");
            EcaLastSwordHelper.cleanupPlayerRestraints(player);
            EprotectPlusLink.clearPlayerFromKillModLists(player.getUUID());
            KillModCounters.counterAll(player);
        } catch (Throwable ignored) {}

        // 5. 空間アンカー（虚無座標追放・奈落落下・異常テレポートからの自動救出）
        if (player instanceof ServerPlayer sp) {
            Vec3 pos = sp.position();
            boolean isEcaVoid = Math.abs(pos.x - 102400.0D) < 100.0D && Math.abs(pos.z - 102400.0D) < 100.0D;
            boolean isBelowMin = pos.y < (double) (sp.level().getMinBuildHeight() - 16);
            boolean isDeepVoid = pos.y < -100.0D;
            boolean isExtremeCoord = Math.abs(pos.x) >= 500000.0D || Math.abs(pos.z) >= 500000.0D;

            if (isEcaVoid || isBelowMin || isDeepVoid || isExtremeCoord) {
                ServerLevel sl = sp.serverLevel();
                BlockPos spawnPos = sl.getSharedSpawnPos();
                sp.teleportTo(sl, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, sp.getYRot(), sp.getXRot());
                sp.setDeltaMovement(Vec3.ZERO);
                sp.fallDistance = 0.0F;
                AutomataValkyries.LOGGER.warn("[PrivilegeAegis] Rescued player {} from abnormal void to spawn", sp.getScoreboardName());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.getPersistentData().getBoolean(NBT_BOUND_KEY)) {
            BOUND_PLAYERS.add(player.getUUID());
            LifeGuardLink.install();
            LifeGuardLink.ensureLast();
            EprotectPlusLink.clearPlayerFromKillModLists(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();
        if (oldPlayer.getPersistentData().getBoolean(NBT_BOUND_KEY) || BOUND_PLAYERS.contains(oldPlayer.getUUID())) {
            BOUND_PLAYERS.add(newPlayer.getUUID());
            newPlayer.getPersistentData().putBoolean(NBT_BOUND_KEY, true);
            LifeGuardLink.install();
            LifeGuardLink.ensureLast();
            EprotectPlusLink.clearPlayerFromKillModLists(newPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BOUND_PLAYERS.clear();
        DISABLED_PLAYERS.clear();
    }
}
