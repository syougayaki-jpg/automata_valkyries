package com.automatavalkyries.annihilate;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.mixin.LivingEntityAccessor;
import com.automatavalkyries.network.AnnihilatedEntitiesPacket;
import com.automatavalkyries.network.ModNetwork;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.flag.StateScanner;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
        if (target == null) return false;

        UUID uuid = target.getUUID();
        String name = target.getScoreboardName();
        Vec3 pos = target.position();

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

            // 4. StateScanner と FlagDictionary による NBT / Interface / InjectedField / Capability の汎用防御解除 & 消滅状態強制書き込み
            try {
                StateScanner.apply(target, StateScanner.BREAK_TARGET, EnumSet.allOf(StateScanner.Source.class));
            } catch (Throwable ignored) {}

            // プレイヤーの PersistentData 内の無敵・神モード・防御タグの完全パージ
            try {
                net.minecraft.nbt.CompoundTag tag = target.getPersistentData();
                for (String key : new ArrayList<>(tag.getAllKeys())) {
                    if (FlagDictionary.isDefensiveField(key)) {
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
                if (effectKey != null && FlagDictionary.protectiveOnTarget(effectKey.getPath())) {
                    target.removeEffect(effect.getEffect());
                }
            }

            // 6. HP同期データおよびフィールドの強制ゼロ化 & 死亡フラグの事前設定
            target.setHealth(0.0F);
            try {
                target.deathTime = 19;
                ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
            } catch (Throwable ignored) {}

            // 7. Java エージェント特権空間による外部 MOD のキル管理リスト・防御フックの解除 & 消滅登録
            EprotectPlusLink.annihilateTargetWithAgent(target);

            // 8. 死亡イベント発火 & 強制キル執行
            try {
                AnnihilationExecutionAgent.runWithEventSuppression(() -> {
                    try {
                        target.die(level.damageSources().genericKill());
                    } catch (Throwable t) {
                        AutomataValkyries.LOGGER.debug("[Terminus] Suppressed exception during player die(): {}", t.getMessage());
                    }
                    try {
                        target.kill();
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}

            // 念のため死亡ステータスを確実に確定
            try {
                target.setHealth(0.0F);
                target.deathTime = 19;
                ((LivingEntityAccessor) target).automata_valkyries$setDead(true);
                target.getEntityData().set(LivingEntityAccessor.automata_valkyries$getDataHealthId(), 0.0F);
            } catch (Throwable ignored) {}

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

        return true;
    }

    /**
     * 不死のトーテムやチート防具（Infinity Armor等）、およびインベントリ・アクセサリ内の
     * 全身代わり・防御アイテムをフラグ辞書により準汎用的に検知して完全剥奪・無力化する。
     */
    private static void stripPlayerDefensiveItems(ServerPlayer player) {
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
                if (!stack.isEmpty() && FlagDictionary.isDefensiveItem(stack)) {
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
                                        if (!curio.isEmpty() && FlagDictionary.isDefensiveItem(curio)) {
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
    private static void neutralizeExternalModDefenses(ServerPlayer target) {
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

            // ラストソードを手放した場合や死亡後の残存拘束（位置固定・移動不能・無敵・飛行）を自動解除
            if (!com.automatavalkyries.protect.EcaLastSwordHelper.hasDefenseSword(player)) {
                com.automatavalkyries.protect.EcaLastSwordHelper.cleanupPlayerRestraints(player);
            }

            // ECA 等による虚無座標（102400, -102400, 102400）または Y < -500 への追放を自動救出
            rescueIfAbnormalPosition(player);

            // 移動速度がゼロやマイナスに固定されている場合の自動修復
            if (player.tickCount % 20 == 0) {
                normalizePlayerMovement(player);
            }
        }

        private static void rescueIfAbnormalPosition(ServerPlayer player) {
            try {
                Vec3 pos = player.position();
                if (pos.y < -64.0D || Math.abs(pos.x) >= 50000.0D || Math.abs(pos.z) >= 50000.0D
                        || (Math.abs(pos.x - 102400.0D) < 2.0D && Math.abs(pos.z - 102400.0D) < 2.0D)) {
                    ServerLevel level = player.serverLevel();
                    net.minecraft.core.BlockPos spawnPos = level.getSharedSpawnPos();
                    AutomataValkyries.LOGGER.warn("[Terminus] Rescuing player {} from abnormal void coordinates ({}, {}, {}) to spawn ({}, {}, {})",
                            player.getScoreboardName(), pos.x, pos.y, pos.z, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                    player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0.0F;
                }
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
