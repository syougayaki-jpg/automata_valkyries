package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import com.automatavalkyries.mixin.EntityAccessor;
import com.automatavalkyries.protect.DirectRead;
import com.automatavalkyries.protect.EcaLastSwordHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤー絶対終焉執行エンジン（Terminus Player Executor）。
 * <p>
 * Eprotect+ Java エージェント（JVM レベルの特権操作）と連携し、
 * クリエイティブモード、無敵フラグ（abilities.invulnerable）、不死のトーテム、
 * チート防具（Avaritia Infinity Armor, ECA, IMDS, No-Sugar 等）の全防御フックを貫通・解除し、
 * プレイヤーを不可逆的・絶対的に即死・終焉（Terminus）させる。
 * </p>
 */
public final class TerminusPlayerExecutor {

    private static final String DEATH_COMPLETED = "automata_valkyries:terminus_death_completed";

    private TerminusPlayerExecutor() {}

    /**
     * プレイヤーに対して改良された絶対即死終焉処理を執行する。
     *
     * @param level    サーバーワールド
     * @param attacker 攻撃者（プレイヤーまたはMOB）
     * @param target   対象プレイヤー
     * @return 終焉執行が成功したか
     */
    public static boolean executeOnPlayer(ServerLevel level, Player attacker, ServerPlayer target) {
        if (target == null || level == null || target.level() != level
                || !level.getServer().isSameThread()) return false;

        if (com.automatavalkyries.protect.AgentPlayerProtection.isProtected(target)) {
            AutomataValkyries.LOGGER.info("[Terminus] Target player {} is protected by Java Agent Privilege Aegis; Terminus execution refused", target.getScoreboardName());
            return false;
        }

        UUID uuid = target.getUUID();
        String name = target.getScoreboardName();
        Vec3 pos = target.position();
        if (target.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn")) {
            return isPlayerProperlyKilled(target);
        }
        final boolean[] confirmed = {false};
        final boolean[] deathCompleted = {false};

        EprotectPlusLink.runPrivileged(() -> {
            AutomataValkyries.LOGGER.info("[Terminus] Initiating absolute player execution for {} ({})", name, uuid);

            // 1. クリエイティブモード・スペクテイターモードの強制剥奪
            try {
                target.setGameMode(GameType.SURVIVAL);
            } catch (Throwable ignored) {}

            // 2. バニラ無敵フラグ・飛行アビリティの剥奪
            try {
                target.getAbilities().invulnerable = false;
                target.getAbilities().mayfly = false;
                target.getAbilities().flying = false;
                target.onUpdateAbilities();
            } catch (Throwable ignored) {}

            // 3. チート防具・不死のトーテム・身代わりアイテムをフラグ辞書で準汎用剥奪 & インベントリ即時同期
            stripPlayerDefensiveItems(target);

            // 4. StateScanner と FlagDictionary による NBT / Interface / InjectedField / Capability の汎用防御解除
            //    ※ プレイヤーに対しては BREAK_TARGET（消滅フラグ書き込み・見えない setter 呼び出し）ではなく、
            //       BREAK_PLAYER_DEFENSES（防御・復活許可の剥奪のみ）を使用する。
            try {
                StateScanner.apply(target, StateScanner.BREAK_PLAYER_DEFENSES, EnumSet.allOf(StateScanner.Source.class));
            } catch (Throwable ignored) {}

            // プレイヤーの PersistentData 内の無敵・神モード・防御タグの完全パージ
            try {
                net.minecraft.nbt.CompoundTag tag = target.getPersistentData();
                for (String key : new ArrayList<>(tag.getAllKeys())) {
                    if (FlagKillPredicates.isDefensiveField(key)) {
                        tag.remove(key);
                    }
                }
            } catch (Throwable ignored) {}

            // 4.5. 外部 MOD のチートアビリティ解除 & 消滅リストからの保護
            neutralizeExternalModDefenses(target);

            // 5. 無敵時間の完全ゼロ化 & 全ポーション・バフ効果の即時消去
            target.invulnerableTime = 0;
            target.removeAllEffects();

            // 防御・耐性系エフェクトの確実な個別解除
            for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects())) {
                var effectKey = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                if (effectKey != null && FlagKillPredicates.protectiveOnTarget(effectKey.getPath())) {
                    target.removeEffect(effect.getEffect());
                }
            }

            // 5.5. The Last Sword / ECA / 他 MOD の拘束・保護・ヘルスロックをキル前に明示的に解除
            //      ※ clearDefence は anchor > 0 のときのみ呼び、anchor が無い時は HP 0 書き込みを避けるため直書き解除する
            //      ※ AnnihilationExecutionAgent.runWithEventSuppression によるイベント抑止下でも死亡後始末を確実に実行する
            try {
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(target);
                com.automatavalkyries.protect.EcaLastSwordHelper.releaseDeadPlayerForRespawn(target);
            } catch (Throwable ignored) {}

            // 6. HP同期データおよびフィールドの強制ゼロ化 & 死亡フラグの事前設定
            target.setHealth(0.0F);
            try {
                target.deathTime = 19;
                ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
            } catch (Throwable ignored) {}

            // 7. （重要）プレイヤーに対しては Java エージェントの真抹殺（annihilateTargetWithAgent）を実行しない。
            //    モブ専用の真抹殺（メタポテント解体、消滅リスト登録、セクション強制切断等）はプレイヤーのリスポーン機構を破壊するため。
            //    プレイヤーはアイテム剥奪・防御解除・die() で通常死亡させ、ライフサイクルは vanilla の respawn に委ねる。

            // 8. 死亡イベント発火（※ target.kill() は絶対に呼ばない。kill() は消去フラグ removalReason=KILLED を付与してリスポーンを破壊するため）
            try {
                AnnihilationExecutionAgent.runWithEventSuppression(() -> {
                    try {
                        deathCompleted[0] = invokePlayerDeath(level, target);
                    } catch (Throwable t) {
                        AutomataValkyries.LOGGER.debug("[Terminus] Suppressed exception during player die(): {}", t.getMessage());
                    }
                });
            } catch (Throwable ignored) {}

            // 念のため死亡ステータスを確実に確定
            try {
                target.setHealth(0.0F);
                target.deathTime = 19;
                ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
            } catch (Throwable ignored) {}

            // die() の呼び出しや他 MOD の Mixin で removalReason が書き換えられていた場合、即座に null にリセットする
            // （バニラのプレイヤーは死亡画面中 isRemoved==false かつ removalReason==null でなければならず、
            //  早期に removalReason が付くとバニラのリスポーン処理で levelCallback.onRemove が呼ばれずゴースト化する）
            try {
                EntityAccessor accessor = (EntityAccessor) target;
                if (accessor.automata_valkyries$getRemovalReason() != null) {
                    accessor.automata_valkyries$setRemovalReason(null);
                }
            } catch (Throwable ignored) {}

            // キル後にも確実に保護を解除しておく（vanilla respawn 時に TLS の EntityMixin が remove を阻止しないように）
            try {
                com.automatavalkyries.protect.EcaLastSwordHelper.releaseDeadPlayerForRespawn(target);
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(target);
            } catch (Throwable ignored) {}

            // 8.5. プレイヤーが正常に即死・死亡状態になっているかを判定し、異常があれば自動修復
            target.getPersistentData().putBoolean("automata_valkyries:terminus_pending_respawn", true);
            target.getPersistentData().putBoolean(DEATH_COMPLETED, deathCompleted[0]);
            if (!isPlayerProperlyKilled(target)) {
                AutomataValkyries.LOGGER.warn("[Terminus] Target player {} was not properly killed after execution; executing remediation...", name);
                verifyAndRemediatePlayerDeath(level, target, true);
            }
            confirmed[0] = isPlayerProperlyKilled(target);
            if (!confirmed[0]) {
                AutomataValkyries.LOGGER.warn("[Terminus] Player execution could not be confirmed for {} ({})", name, uuid);
                return;
            }

            // 9. 死亡パケット（CombatKillPacket）および HP=0 同期パケットの送信（死亡画面強制）
            try {
                if (target.connection != null && target.connection.connection != null && target.connection.connection.isConnected()) {
                    target.connection.send(new ClientboundPlayerCombatKillPacket(target.getId(),
                            Component.translatable("death.attack.automata_valkyries.terminus", name)
                                     .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    if (target.getEntityData().isDirty()) {
                        target.connection.send(new ClientboundSetEntityDataPacket(target.getId(), target.getEntityData().packDirty()));
                    }
                }
            } catch (Throwable ignored) {}

            // 9.5. クライアント側での安全な死亡・消滅フラグ確定パケット送信（プレイヤー消滅は行わない）
            try {
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new com.automatavalkyries.network.TerminusPlayerDeathPacket(uuid, target.getId()));
            } catch (Throwable ignored) {}

            // 10. 終焉演出エフェクトの発生
            spawnTerminusEffects(level, pos);
            try {
                target.sendSystemMessage(Component.translatable("message.automata_valkyries.terminus.executed")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            } catch (Throwable ignored) {}

            AutomataValkyries.LOGGER.info("[Terminus] Absolute player execution successful for {} ({})", name, uuid);
        });

        return confirmed[0];
    }

    private static boolean invokePlayerDeath(ServerLevel level, Player target) {
        ServerPlayer serverPlayer = target instanceof ServerPlayer sp ? sp : null;
        boolean connected = serverPlayer != null && serverPlayer.connection != null
                && serverPlayer.connection.connection != null && serverPlayer.connection.connection.isConnected();
        int previousDeaths = connected ? serverPlayer.getStats().getValue(
                net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS)) : 0;
        target.die(level.damageSources().genericKill());
        // A connected player's cancelled death hook must not pass just because HP was forced to zero.
        return !connected || serverPlayer.getStats().getValue(
                net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS)) > previousDeaths;
    }

    /**
     * プレイヤーが正常に即死・死亡状態になっているか（バニラのリスポーン処理を正常に受け入れられる状態か）を判定する。
     * <p>
     * 正常な死亡状態の要件:
     * 1. HP が 0 以下（かつ NaN や無効値でない）
     * 2. 死亡状態（isDeadOrDying または deathTime > 0）
     * 3. 早期消去フラグが付与されていない（isRemoved == false かつ removalReason == null）
     *    ※ 死亡画面中のプレイヤーに isRemoved や removalReason が付いていると、
     *       バニラの PlayerList.respawn がレベルコールバック（追跡解除・区画消去）をスキップし、
     *       透明化や古い個体の重複増殖を引き起こす。
     * 4. 無敵アビリティ（abilities.invulnerable）が無効化されている
     * 5. The Last Sword の保護フラグ（tlsIsProtected, tlsWorldAnchor）および EntityUtil.hasProtection が false
     * 6. ECA の無敵・ヘルスロック・蘇生フラグ（ecaInvulnerable, ecaResurrectionTracked 等）が無効化されている
     * 7. Hyperlink のノベル化（DATA_NOVELIZED）が無効化されている
     * </p>
     *
     * @param player 対象プレイヤー
     * @return 正常にキル確定状態になっている場合は true、何らかの防護や異常残留がある場合は false
     */
    public static boolean isPlayerProperlyKilled(Player player) {
        if (player == null) return false;
        if (player.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn")
                && !player.getPersistentData().getBoolean(DEATH_COMPLETED)) return false;

        // 1. HP チェック（0以下かつ有限数）
        float storedHp = DirectRead.health(player);
        if (!Float.isFinite(storedHp) || storedHp > 0.0F) {
            return false;
        }
        float hp = player.getHealth();
        if (!Float.isFinite(hp) || hp > 0.0F) {
            return false;
        }

        // 2. 死亡状態チェック
        if (!player.isDeadOrDying() && player.deathTime <= 0) {
            return false;
        }

        // 3. 早期消去チェック（死亡画面中での isRemoved や removalReason の存在は異常）
        if (player.isRemoved()) {
            return false;
        }
        try {
            EntityAccessor accessor = (EntityAccessor) player;
            if (accessor.automata_valkyries$getRemovalReason() != null) {
                return false;
            }
        } catch (Throwable ignored) {}

        // 4. 無敵アビリティチェック
        if (player.getAbilities().invulnerable) {
            return false;
        }

        // 5. The Last Sword 防護チェック
        try {
            CompoundTag tag = player.getPersistentData();
            if (tag.contains("tlsIsProtected") || tag.contains("tlsWorldAnchor")) {
                return false;
            }
            Class<?> entityUtil = Class.forName("net.the_last_sword.util.EntityUtil");
            Object hasProt = entityUtil.getMethod("hasProtection", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            if (Boolean.TRUE.equals(hasProt)) {
                return false;
            }
        } catch (Throwable ignored) {}

        // 6. ECA 防護・蘇生チェック
        try {
            CompoundTag tag = player.getPersistentData();
            if (tag.contains("ecaInvulnerable") || tag.contains("ecaResurrectionTracked") || tag.contains("ecaHealthLockEnc")) {
                return false;
            }
        } catch (Throwable ignored) {}

        // 7. Hyperlink ノベル化チェック
        try {
            var novelAccessor = EcaLastSwordHelper.getHyperlinkNovelizedAccessor();
            if (novelAccessor != null && Boolean.TRUE.equals(player.getEntityData().get(novelAccessor))) {
                return false;
            }
        } catch (Throwable ignored) {}

        return true;
    }

    /**
     * プレイヤーのキル状態を検証し、正常な死亡状態でない場合は即座に修復・再執行する。
     *
     * @param level  サーバーワールド
     * @param target 対象プレイヤー
     * @return 修復後に正常な死亡状態になったか
     */
    public static boolean verifyAndRemediatePlayerDeath(ServerLevel level, Player target) {
        return verifyAndRemediatePlayerDeath(level, target,
                target != null && target.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn"));
    }

    private static boolean verifyAndRemediatePlayerDeath(ServerLevel level, Player target, boolean deathAttempted) {
        if (target == null) return false;
        if (level == null && target.level() instanceof ServerLevel sl) level = sl;
        if (level == null || target.level() != level || !level.getServer().isSameThread()
                || com.automatavalkyries.protect.AgentPlayerProtection.isProtected(target)) return false;
        boolean wasDead = DirectRead.deadFlag(target);
        final boolean[] deathProcessed = {deathAttempted
                ? target.getPersistentData().getBoolean(DEATH_COMPLETED) : wasDead};

        AutomataValkyries.LOGGER.info("[Terminus] Verifying and remediating player death for {} ({})",
                target.getScoreboardName(), target.getUUID());

        try {
            // 1. サバイバル化 & 無敵・飛行剥奪
            try {
                if (target instanceof ServerPlayer sp) {
                    sp.setGameMode(GameType.SURVIVAL);
                }
                target.getAbilities().invulnerable = false;
                target.getAbilities().mayfly = false;
                target.getAbilities().flying = false;
                target.onUpdateAbilities();
            } catch (Throwable ignored) {}

            target.invulnerableTime = 0;
            target.removeAllEffects();

            // 2. 防御アイテム剥奪
            stripPlayerDefensiveItems(target);

            // 3. プレイヤー用防御解除（消滅フラグは触らない）
            try {
                StateScanner.apply(target, StateScanner.BREAK_PLAYER_DEFENSES, EnumSet.allOf(StateScanner.Source.class));
            } catch (Throwable ignored) {}

            // 4. 外部 MOD 防護解除 & 消滅リスト保護
            neutralizeExternalModDefenses(target);

            // 5. TLS & ECA & 他 MOD の拘束・保護・蘇生フラグ解除
            try {
                EcaLastSwordHelper.cleanupPlayerRestraints(target);
                EcaLastSwordHelper.releaseDeadPlayerForRespawn(target);
            } catch (Throwable ignored) {}

            // 6. 早期消去フラグ（removalReason）の解消
            //    ※ 他 MOD の kill() や Mixin で removalReason が KILLED に書き換えられていた場合、
            //       null に戻してバニラの PlayerList.respawn が正常に levelCallback.onRemove を呼べるようにする
            try {
                EntityAccessor accessor = (EntityAccessor) target;
                if (accessor.automata_valkyries$getRemovalReason() != null) {
                    AutomataValkyries.LOGGER.warn("[Terminus] Clearing premature removalReason ({}) from player {} to allow clean respawn",
                            accessor.automata_valkyries$getRemovalReason(), target.getScoreboardName());
                    accessor.automata_valkyries$setRemovalReason(null);
                }
            } catch (Throwable ignored) {}

            // 7. HP ゼロ化 & 死亡フラグ設定
            target.setHealth(0.0F);
            try {
                target.deathTime = Math.max(target.deathTime, 19);
                ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
            } catch (Throwable ignored) {}

            // 8. 死亡イベント発火（die() のみ呼び出し、kill() は絶対に呼ばない）
            // A forced HP/dead flag is not proof that die() completed. Retry a rejected death,
            // while keeping a completed death from firing its hooks twice.
            if (!deathProcessed[0]) {
                final ServerLevel lvl = level;
                try {
                    AnnihilationExecutionAgent.runWithEventSuppression(() -> {
                        try {
                            deathProcessed[0] = invokePlayerDeath(lvl, target);
                            if (deathProcessed[0] && deathAttempted) {
                                target.getPersistentData().putBoolean(DEATH_COMPLETED, true);
                            }
                        } catch (Throwable t) {
                            AutomataValkyries.LOGGER.debug("[Terminus] Exception during remediating die(): {}", t.getMessage());
                        }
                    });
                } catch (Throwable ignored) {}
            }

            // die() 後に removalReason が書き換えられていないか再チェック・修復
            try {
                EntityAccessor accessor = (EntityAccessor) target;
                if (accessor.automata_valkyries$getRemovalReason() != null) {
                    accessor.automata_valkyries$setRemovalReason(null);
                }
            } catch (Throwable ignored) {}

            // 9. 死亡パケット同期（クライアントの死亡画面確定）
            //    既に死んでいたら送らない。この修復はリスポーン待ちの間 毎 Tick 走り得るので、毎回送るとクライアントが死亡画面を
            //    毎 Tick 作り直し、リスポーンボタンが有効になる 20 Tick に届かず押せなくなる（2026-10-02）
            if (!wasDead && target instanceof ServerPlayer sp) {
                try {
                    if (sp.connection != null && sp.connection.connection != null && sp.connection.connection.isConnected()) {
                        sp.connection.send(new ClientboundPlayerCombatKillPacket(sp.getId(),
                                Component.translatable("death.attack.automata_valkyries.terminus", sp.getScoreboardName())
                                         .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                        if (sp.getEntityData().isDirty()) {
                            sp.connection.send(new ClientboundSetEntityDataPacket(sp.getId(), sp.getEntityData().packDirty()));
                        }
                    }
                } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            AutomataValkyries.LOGGER.error("[Terminus] Error during verifyAndRemediatePlayerDeath: {}", t.getMessage(), t);
        }

        boolean verified = deathProcessed[0] && isPlayerProperlyKilled(target);
        if (verified) {
            AutomataValkyries.LOGGER.info("[Terminus] Player {} is confirmed properly killed", target.getScoreboardName());
        } else {
            AutomataValkyries.LOGGER.warn("[Terminus] Player {} still could not be properly killed after remediation!", target.getScoreboardName());
        }
        return verified;
    }

    /**
     * 不死のトーテムやチート防具（Infinity Armor等）、およびインベントリ・アクセサリ内の
     * 全身代わり・防御アイテムをフラグ辞書により準汎用的に検知して完全剥奪・無力化する。
     */
    private static void stripPlayerDefensiveItems(Player player) {
        try {
            // メイン手・オフ手
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);

            // 防具スロット（頭、胸、脚、足）
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
            }

            // インベントリ全体（36スロット）内の身代わり・不死・防御アイテム（Totem等）を走査して消去
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && FlagKillPredicates.isDefensiveItem(stack)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }

            // Curios API / アクセサリー MOD のインベントリ走査（リフレクションによる準汎用対応）
            try {
                Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                java.lang.reflect.Method getCuriosHelper = curiosApi.getMethod("getCuriosHelper");
                Object helper = getCuriosHelper.invoke(null);
                if (helper != null) {
                    java.lang.reflect.Method getCuriosHandler = helper.getClass().getMethod("getCuriosHandler", net.minecraft.world.entity.LivingEntity.class);
                    Object lazyOpt = getCuriosHandler.invoke(helper, player);
                    if (lazyOpt instanceof net.minecraftforge.common.util.LazyOptional<?> lazy) {
                        lazy.ifPresent(handler -> {
                            try {
                                java.lang.reflect.Method getEquippedCurios = handler.getClass().getMethod("getEquippedCurios");
                                Object itemHandler = getEquippedCurios.invoke(handler);
                                if (itemHandler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                                    for (int slot = 0; slot < modifiable.getSlots(); slot++) {
                                        ItemStack curio = modifiable.getStackInSlot(slot);
                                        if (!curio.isEmpty() && FlagKillPredicates.isDefensiveItem(curio)) {
                                            modifiable.setStackInSlot(slot, ItemStack.EMPTY);
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                        });
                    }
                }
            } catch (Throwable ignored) {}

            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        } catch (Throwable ignored) {}
    }

    /**
     * 外部 MOD（No-Sugar 等）のチートアビリティ解除メソッド（SnackProtector.resetAbilities 等）を
     * リフレクションを用いて準汎用的に執行し、同時に静的消滅リストからプレイヤー UUID を確実に保護する。
     * ※プレイヤーに対して消滅パケット（EraseEntityPacket: true）を送ると、リスポーン時に
     *   クライアント側で cancel addEntity されてしまい停止（ゴースト化）するため、絶対に送信してはならない。
     */
    private static void neutralizeExternalModDefenses(Player target) {
        if (target == null) return;
        UUID uuid = target.getUUID();

        // 1. No-Sugar のアビリティ・飛行解除メソッド（SnackProtector.resetAbilities）を強制呼出し
        try {
            Class<?> protectorClass = Class.forName("com.test.nosugar.additional.SnackArmor$SnackProtector");
            Method reset = protectorClass.getDeclaredMethod("resetAbilities", Player.class);
            reset.setAccessible(true);
            reset.invoke(null, target);
        } catch (Throwable ignored) {}

        // 2. 外部 MOD のキル・消滅管理リストからプレイヤー UUID を確実に除外
        EprotectPlusLink.clearPlayerFromKillModLists(uuid);

        // 3. No-Sugar の消滅パケット解除（EraseEntityPacket: false）を送信してクライアント側の消滅状態を事前解除
        sendModErasePacket(uuid, false);
    }

    /**
     * No-Sugar 等の消滅パケット送信ヘルパー（消滅解除用）。
     */
    public static void sendModErasePacket(UUID uuid, boolean erased) {
        if (uuid == null) return;
        try {
            Class<?> packetClass = Class.forName("com.test.nosugar.network.packets.EraseEntityPacket");
            java.lang.reflect.Constructor<?> ctor = packetClass.getConstructor(UUID.class, boolean.class);
            Object packet = ctor.newInstance(uuid, erased);

            Class<?> handlerClass = Class.forName("com.test.nosugar.network.PacketHandler");
            Field channelField = handlerClass.getDeclaredField("CHANNEL");
            channelField.setAccessible(true);
            Object channel = channelField.get(null);
            if (channel instanceof net.minecraftforge.network.simple.SimpleChannel simpleChannel) {
                simpleChannel.send(PacketDistributor.ALL.noArg(), packet);
            }
        } catch (Throwable ignored) {}
    }

    private static void spawnTerminusEffects(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 1.0D, pos.z, 3, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0D, pos.z, 5, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0D, pos.z, 80, 0.8, 1.5, 0.8, 0.1);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D, pos.z, 100, 1.0, 2.0, 1.0, 0.15);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.8F, 0.7F);
    }

    /**
     * 終焉執行を受けたプレイヤーがリスポーンまたはクローンされる際、
     * 外部 MOD のキル管理リスト・消滅リストからの除外および状態初期化を最高優先度で執行する。
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            handlePlayerRespawnOrClone(player);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            handlePlayerRespawnOrClone(player);
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
        public static void onLivingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);
            }
        }

        private static void handlePlayerRespawnOrClone(ServerPlayer player) {
            UUID uuid = player.getUUID();
            player.getPersistentData().remove("automata_valkyries:terminus_pending_respawn");
            player.getPersistentData().remove(DEATH_COMPLETED);
            try {
                // 1. サーバー側の外部 MOD 消滅リスト（erasedUuids等）から確実に除外
                EprotectPlusLink.clearPlayerFromKillModLists(uuid);

                // 2. プレイヤー自身の防御・消滅フラグの完全初期化
                StateScanner.apply(player, StateScanner.CLEAN_PROTECTED, EnumSet.allOf(StateScanner.Source.class));

                // 3. No-Sugar の消滅パケット解除（EraseEntityPacket: false）を送信
                sendModErasePacket(uuid, false);

                // 4. クライアント側へ TerminusPlayerRespawnPacket を同報送信して完全正常化
                ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new com.automatavalkyries.network.TerminusPlayerRespawnPacket(uuid));

                // 5. ECA / 他 MOD のヘルスロック・無敵フック解除・位置固定強制解除
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);

                // 6. ECA 等の追放テレポートによる異常座標（Y < -64, |X| >= 50000, |Z| >= 50000 等）の検知と安全スポーン地点への救出
                rescueIfAbnormalPosition(player);

                // 7. 動作不能状態の解除（移動速度属性、重力等の正常化）
                normalizePlayerMovement(player);
            } catch (Throwable ignored) {}
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer player)) return;

            // 終焉執行後リスポーン待ちのプレイヤーのキル状態判定 & 異常があれば自動修復
            if (player.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn")) {
                if (player.tickCount % 20 == 0 && !isPlayerProperlyKilled(player)) {
                    verifyAndRemediatePlayerDeath(player.serverLevel(), player);
                }
                return;
            }

            // 死亡中のプレイヤーに万一 removalReason が付いていたら null に戻しておく（バニラリスポーン保証）
            if (!DirectRead.hasHealth(player) || player.deathTime > 0) {
                try {
                    EntityAccessor accessor = (EntityAccessor) player;
                    if (accessor.automata_valkyries$getRemovalReason() != null) {
                        accessor.automata_valkyries$setRemovalReason(null);
                    }
                } catch (Throwable ignored) {}
                return;
            }

            // ラストソードを手放した瞬間（持っている → 持っていない）にだけ、残存拘束（位置固定・移動不能・無敵・飛行）を解除する。
            // 毎 Tick 全員に後始末をかけると、他 MOD の正当な状態（飛行・無敵の装備など）まで消し、The Last Sword の clearDefence が
            // HP を 0 に書くなどの事故が起きる（2026-10-02 のリスポーン直後の再死亡）。死亡・リスポーン・ログインの時は別に後始末する
            if (player.tickCount % 10 == 0 && com.automatavalkyries.protect.EcaLastSwordHelper.releasedDefenseSword(player)) {
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);
            }

            // ECA 等による虚無座標（102400, -102400, 102400）または Y < -500 への追放を自動救出（毎 Tick ではなく 20 Tick ごとに判定し、頻度を制限）
            if (player.tickCount % 20 == 0) {
                rescueIfAbnormalPosition(player);
            }

            // 移動速度がゼロやマイナスに固定されている場合の自動修復（異常なときだけ動く。重力は触らない）
            if (player.tickCount % 20 == 0) {
                fixFrozenSpeed(player);
            }
        }

        private static void fixFrozenSpeed(ServerPlayer player) {
            try {
                var speedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (speedAttr != null && speedAttr.getValue() <= 0.005D) {
                    speedAttr.setBaseValue(0.1D);
                }
            } catch (Throwable ignored) {}
        }

        private static final Map<UUID, Long> LAST_RESCUE_TIME = new java.util.concurrent.ConcurrentHashMap<>();
        private static final Map<UUID, Integer> RESCUE_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

        private static void rescueIfAbnormalPosition(ServerPlayer player) {
            if (player == null || player.isRemoved()) return;
            try {
                Vec3 pos = player.position();
                boolean isEcaVoid = Math.abs(pos.x - 102400.0D) < 100.0D && Math.abs(pos.z - 102400.0D) < 100.0D;
                boolean isDeepVoid = pos.y < -500.0D;
                boolean isExtremeCoord = Math.abs(pos.x) >= 1000000.0D || Math.abs(pos.z) >= 1000000.0D;

                if (!isEcaVoid && !isDeepVoid && !isExtremeCoord) {
                    return;
                }

                UUID uuid = player.getUUID();
                long now = System.currentTimeMillis();
                Long lastRescue = LAST_RESCUE_TIME.get(uuid);
                if (lastRescue != null && (now - lastRescue) < 3000L) { // 最低3秒のクールダウン
                    return;
                }

                int count = RESCUE_COUNT.getOrDefault(uuid, 0) + 1;
                RESCUE_COUNT.put(uuid, count);
                if (count > 5) {
                    // 5回連続で追放されている場合、15秒間バックオフ（ECA との毎秒テレポート合戦によるサーバー負荷防止）
                    if (lastRescue != null && (now - lastRescue) < 15000L) {
                        return;
                    }
                    RESCUE_COUNT.put(uuid, 1);
                }
                LAST_RESCUE_TIME.put(uuid, now);

                // ECA の位置ロック・拘束を解除して再テレポートの引き戻しを防止
                com.automatavalkyries.protect.EcaLastSwordHelper.clearLocationLock(uuid);
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);

                ServerLevel level = player.serverLevel();
                net.minecraft.core.BlockPos spawnPos = level.getSharedSpawnPos();
                AutomataValkyries.LOGGER.warn("[Terminus] Rescuing player {} (count={}) from abnormal void coordinates ({}, {}, {}) to spawn ({}, {}, {})",
                        player.getScoreboardName(), count, pos.x, pos.y, pos.z, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0.0F;
            } catch (Throwable ignored) {}
        }

        private static void normalizePlayerMovement(ServerPlayer player) {
            try {
                player.setNoGravity(false);
                var speedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (speedAttr != null && speedAttr.getValue() <= 0.005D) {
                    speedAttr.setBaseValue(0.1D);
                }
            } catch (Throwable ignored) {}
        }
    }
}
