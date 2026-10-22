package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Epic Core API (ECA) および The Last Sword You Never Forgot による
 * プレイヤーの強制位置固定（硬直・テレポート不能・リスポーン先からの強制引き戻し）、
 * 無敵・ヘルスロック・不正飛行状態を安全かつ確実に解放・解除するヘルパー。
 */
public final class EcaLastSwordHelper {

    private EcaLastSwordHelper() {}

    /**
     * プレイヤーが The Last Sword 系の防御剣（UltraTestSword, TheLastSword 等）を
     * インベントリ内に実際に保持しているか判定する。
     */
    public static boolean hasDefenseSword(Player player) {
        if (player == null) return false;
        try {
            Inventory inv = player.getInventory();
            if (inv == null) return false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                String className = stack.getItem().getClass().getName();
                if (className.startsWith("net.the_last_sword.")
                        || className.startsWith("net.mcreator.imds_reborn.")
                        || className.contains("UltraTestSword")
                        || className.contains("TheLastSword")
                        || className.contains("DragonArmor")
                        || className.contains("DragonCrystalArmor")
                        || className.contains("TheLastEndArmor")
                        || className.contains("DimensionExplorer")
                        || className.contains("ExtremeLifeSupport")
                        || className.contains("InfinityMatterDominatorSword")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** 最後に見たときに防御剣を持っていたか（プレイヤーの UUID → 持っていた） */
    private static final Map<UUID, Boolean> HELD = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 防御剣を「手放した瞬間」か（前に見たときは持っていて、今は持っていない）。手放した後の後始末を 1 回だけにするために使う。
     * 初めて見るプレイヤーは「持っていた」扱いにしない（ログイン・リスポーンの後始末は別に行う）
     */
    public static boolean releasedDefenseSword(Player player) {
        boolean now = hasDefenseSword(player);
        Boolean before = HELD.put(player.getUUID(), now);
        return Boolean.TRUE.equals(before) && !now;
    }

    /**
     * リスポーンの直前（{@code PlayerList.respawn} の最初）: 死んでいる古い個体から The Last Sword の保護を外し、vanilla が古い個体を
     * 消せるようにする（保護が残ると消去が止められて同じ UUID のプレイヤーが二重になる）。生きている個体（エンドからの帰還など）には触れない
     */
    public static void releaseDeadPlayerForRespawn(Player player) {
        if (player == null) return;
        if (DirectRead.hasHealth(player) && !player.getPersistentData().getBoolean("automata_valkyries:terminus_pending_respawn")) {
            return;
        }
        forceClearEcaAndTlsProtection(player);
    }

    /**
     * サーバー・クライアント双方で The Last Sword および ECA の無敵・保護・ヘルスロック・蘇生追跡・SynchedEntityData を
     * 強制的に解除する（スタッククローキング付き）。
     */
    @SuppressWarnings("unchecked")
    public static void forceClearEcaAndTlsProtection(Player player) {
        if (player == null) return;
        com.automatavalkyries.protect.plus.EprotectPlusLink.runWithCloakedStack(() -> {
            try {
                Class<?> tlsEntityUtil = Class.forName("net.the_last_sword.util.EntityUtil");
                try {
                    tlsEntityUtil.getMethod("setProtection", net.minecraft.world.entity.LivingEntity.class, boolean.class).invoke(null, player, false);
                } catch (Throwable ignored) {}
                // clearWorldAnchor は setWorldAnchor(0) → EcaAPI.setHealth(0) で HP を 0 に書く（錨が無くても）。
                // ログイン・リスポーン・手放しの後始末（生きているプレイヤー）で呼ぶと即死し続けるので（2026-10-22 に再発）、
                // 既に死んでいるときだけ呼ぶ。生きているなら錨の印だけ消して、ECA の HP 固定を外す
                try {
                    if (!DirectRead.hasHealth(player)) {
                        tlsEntityUtil.getMethod("clearWorldAnchor", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
                    } else {
                        player.getPersistentData().remove("tlsWorldAnchor");
                        player.getPersistentData().remove("tlsWorldAnchorMax");
                    }
                } catch (Throwable ignored) {}
                try {
                    Field isProtectedField = tlsEntityUtil.getDeclaredField("IS_PROTECTED");
                    isProtectedField.setAccessible(true);
                    Object acc = isProtectedField.get(null);
                    if (acc instanceof net.minecraft.network.syncher.EntityDataAccessor<?> dataAcc) {
                        player.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Boolean>) dataAcc, false);
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> ecaApi = Class.forName("net.eca.api.EcaAPI");
                try {
                    ecaApi.getMethod("setInvulnerable", net.minecraft.world.entity.Entity.class, boolean.class).invoke(null, player, false);
                } catch (Throwable ignored) {}
                try {
                    ecaApi.getMethod("disableAllReturn", net.minecraft.world.entity.Entity.class).invoke(null, player);
                } catch (Throwable ignored) {}
                try {
                    ecaApi.getMethod("removeResurrectionTarget", net.minecraft.world.entity.Entity.class).invoke(null, player);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try {
                Class<?> invulManager = Class.forName("net.eca.util.InvulnerableEntityManager");
                invulManager.getMethod("removeInvulnerable", net.minecraft.world.entity.Entity.class).invoke(null, player);
            } catch (Throwable ignored) {}

            try {
                Class<?> healthLockClass = Class.forName("net.eca.util.health.health_lock.HealthLockManager");
                healthLockClass.getMethod("removeLock", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
                healthLockClass.getMethod("removeMaxHealthLock", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
                healthLockClass.getMethod("removeHealBan", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            } catch (Throwable ignored) {}

            try {
                Class<?> ecaEntityUtil = Class.forName("net.eca.util.EntityUtil");
                for (String fieldName : new String[]{"INVULNERABLE", "RESURRECTION_TRACKED"}) {
                    try {
                        Field f = ecaEntityUtil.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        Object acc = f.get(null);
                        if (acc instanceof net.minecraft.network.syncher.EntityDataAccessor<?> dataAcc) {
                            player.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Boolean>) dataAcc, false);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            try {
                EprotectImdsCounter.counter(player);
            } catch (Throwable ignored) {}

            try {
                CompoundTag tag = player.getPersistentData();
                tag.remove("tlsIsProtected");
                tag.remove("tlsWorldAnchor");
                tag.remove("tlsWorldAnchorMax");
                tag.remove("ecaResurrectionTracked");
                tag.remove("ecaInvulnerable");
                tag.remove("ecaHealthLockEnc");
                tag.remove("ecaHealthLockKey");
                tag.remove("ecaHealthLockCheck");
                tag.remove("ecaMaxHealthLockEnc");
                tag.remove("ecaMaxHealthLockKey");
                tag.remove("ecaMaxHealthLockCheck");
            } catch (Throwable ignored) {}
        });
    }

    /** 切断時に覚えた状態を消す */
    public static void forget(UUID uuid) {
        HELD.remove(uuid);
    }

    /**
     * UUID を指定して ECA の EntityLocationManager の位置ロックから強制解除する。
     * リスポーン時やプレイヤー切断時に呼び出す。
     */
    public static void clearLocationLock(UUID uuid) {
        if (uuid == null) return;
        try {
            Class<?> locManagerClass = Class.forName("net.eca.util.EntityLocationManager");
            Field lockedMapField = locManagerClass.getDeclaredField("LOCKED_ENTITIES");
            lockedMapField.setAccessible(true);
            Object mapObj = lockedMapField.get(null);
            if (mapObj instanceof Map<?, ?> map) {
                map.remove(uuid);
            }
        } catch (Throwable ignored) {}
    }

    @org.jetbrains.annotations.Nullable
    private static volatile net.minecraft.network.syncher.EntityDataAccessor<Boolean> HYPERLINK_DATA_NOVELIZED = null;

    /**
     * Hyperlink (hyperdaimc) が Entity クラスに合成した DATA_NOVELIZED (EntityDataAccessor<Boolean>) を取得する。
     * Mixin クラス (EntityMixin) は直接ロードできないため、合成先である Entity クラスの static フィールドから検索する。
     */
    @org.jetbrains.annotations.Nullable
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<Boolean> getHyperlinkNovelizedAccessor() {
        if (HYPERLINK_DATA_NOVELIZED != null) {
            return HYPERLINK_DATA_NOVELIZED;
        }
        try {
            for (Field field : Entity.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && net.minecraft.network.syncher.EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                    String name = field.getName();
                    if (name.equals("DATA_NOVELIZED") || name.endsWith("$DATA_NOVELIZED") || name.contains("DATA_NOVELIZED")) {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof net.minecraft.network.syncher.EntityDataAccessor<?> acc) {
                            HYPERLINK_DATA_NOVELIZED = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) acc;
                            return HYPERLINK_DATA_NOVELIZED;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * プレイヤーが死んでいる、またはラストソードを手放した場合、
     * 残存する位置固定・無敵・ヘルスロック・飛行状態を完全解除・初期化する。
     */
    public static void cleanupPlayerRestraints(Player player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        // The Last Sword が付けた飛行の印（手順 9 で消す前に見ておく。印が無ければ他 MOD の飛行なので手順 10 で触らない）
        CompoundTag before = player.getPersistentData();
        boolean swordFlight = before.contains("TheLastSwordFly") || before.contains("TheLastSwordYouNeverForgotFly")
                || before.contains("PlayerFlightIntent");

        // 1. 位置固定マップからの解除
        clearLocationLock(uuid);

        // 2. ECA の unlockLocation 呼び出し
        try {
            Class<?> locManagerClass = Class.forName("net.eca.util.EntityLocationManager");
            Method unlock = locManagerClass.getMethod("unlockLocation", net.minecraft.world.entity.Entity.class);
            unlock.invoke(null, player);
        } catch (Throwable ignored) {}

        // 3. ECA の HealthLockManager 解除
        try {
            Class<?> healthLockClass = Class.forName("net.eca.util.health.health_lock.HealthLockManager");
            Method removeLock = healthLockClass.getMethod("removeLock", net.minecraft.world.entity.LivingEntity.class);
            removeLock.invoke(null, player);
            Method removeMaxLock = healthLockClass.getMethod("removeMaxHealthLock", net.minecraft.world.entity.LivingEntity.class);
            removeMaxLock.invoke(null, player);
            Method removeHealBan = healthLockClass.getMethod("removeHealBan", net.minecraft.world.entity.LivingEntity.class);
            removeHealBan.invoke(null, player);
        } catch (Throwable ignored) {}

        // 4. ECA & TLS の無敵・保護・SynchedEntityData 強制解除
        forceClearEcaAndTlsProtection(player);

        // 5. The Last Sword の防御の解除
        //    clearDefence は「世界の錨（tlsWorldAnchor）の HP」を ECA の setHealth で書き戻す。錨が無いプレイヤーでは錨が 0 なので、
        //    呼ぶと HP が 0 に書かれて即死する（毎 Tick・リスポーン直後に死に続けた原因。2026-10-02）。錨が正のときだけ呼び、
        //    それ以外は HP に触れない解除（錨の値・防御の印）だけ行う
        try {
            Class<?> entityUtil = Class.forName("net.the_last_sword.util.EntityUtil");
            CompoundTag data = player.getPersistentData();
            boolean hasAnchor = data.contains("tlsWorldAnchor");
            float anchor = 0.0F;
            if (hasAnchor) {
                Method getAnchor = entityUtil.getMethod("getWorldAnchor", net.minecraft.world.entity.LivingEntity.class);
                anchor = ((Number) getAnchor.invoke(null, player)).floatValue();
            }
            if (hasAnchor && Float.isFinite(anchor) && anchor > 0.0F) {
                entityUtil.getMethod("clearDefence", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            } else {
                data.remove("tlsWorldAnchor");
                data.remove("tlsWorldAnchorMax");
            }
            if (hasAnchor) entityUtil.getMethod("clearWorldAnchor", net.minecraft.world.entity.LivingEntity.class).invoke(null, player);
            Method setProt = entityUtil.getMethod("setProtection", net.minecraft.world.entity.LivingEntity.class, boolean.class);
            setProt.invoke(null, player, false);
        } catch (Throwable ignored) {}

        // 6. ECA AllReturn の解除（ワールド全体ではなく対象プレイヤーのみ）
        try {
            Class<?> ecaApi = Class.forName("net.eca.api.EcaAPI");
            Method disableAllReturn = ecaApi.getMethod("disableAllReturn", net.minecraft.world.entity.Entity.class);
            disableAllReturn.invoke(null, player);
        } catch (Throwable ignored) {}

        // 6.5. ECA の復活の見張り（ResurrectionManager）の対象から外す
        //      対象のままだと ECA の別スレッドが死亡中の HP を記録した値へ戻し続け、サーバーが「生きている」とみなして
        //      リスポーンの要求を無視する（死亡画面を閉じても動けない。2026-10-03）
        try {
            Class<?> ecaApi = Class.forName("net.eca.api.EcaAPI");
            Object tracked = ecaApi.getMethod("isResurrectionTracked", net.minecraft.world.entity.Entity.class).invoke(null, player);
            if (Boolean.TRUE.equals(tracked)) {
                ecaApi.getMethod("removeResurrectionTarget", net.minecraft.world.entity.Entity.class).invoke(null, player);
                AutomataValkyries.LOGGER.info("[eca] Removed {} from ECA resurrection targets", player.getScoreboardName());
            }
        } catch (Throwable ignored) {}

        // 7. Hyperlink (hyperdaimc) の Novelize 解除および VRX entries クリア
        try {
            var dataAccessor = getHyperlinkNovelizedAccessor();
            if (dataAccessor != null) {
                player.getEntityData().set(dataAccessor, false);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> vrxCapClass = Class.forName("com.sakurafuld.hyperdaimc.content.hyper.vrx.VRXCapability");
            Field tokenField = vrxCapClass.getDeclaredField("TOKEN");
            tokenField.setAccessible(true);
            Object capToken = tokenField.get(null);
            if (capToken instanceof net.minecraftforge.common.capabilities.Capability cap) {
                //noinspection unchecked
                player.getCapability(cap).ifPresent(capObj -> {
                    try {
                        Field entriesField = capObj.getClass().getDeclaredField("entries");
                        entriesField.setAccessible(true);
                        Object list = entriesField.get(capObj);
                        if (list instanceof java.util.List entriesList) {
                            entriesList.clear();
                        }
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}

        // 8. The Last Sword の属性リセット
        try {
            Class<?> modAttrs = Class.forName("net.the_last_sword.init.ModAttributes");
            Field justDefField = modAttrs.getDeclaredField("JUSTIFIED_DEFENCE");
            Field maxJustDefField = modAttrs.getDeclaredField("MAX_JUSTIFIED_DEFENCE");
            justDefField.setAccessible(true);
            maxJustDefField.setAccessible(true);
            Object justDef = justDefField.get(null);
            Object maxJustDef = maxJustDefField.get(null);
            if (justDef instanceof net.minecraftforge.registries.RegistryObject<?> ro && ro.get() instanceof net.minecraft.world.entity.ai.attributes.Attribute attr) {
                var inst = player.getAttribute(attr);
                if (inst != null) inst.setBaseValue(0.0D);
            }
            if (maxJustDef instanceof net.minecraftforge.registries.RegistryObject<?> ro && ro.get() instanceof net.minecraft.world.entity.ai.attributes.Attribute attr) {
                var inst = player.getAttribute(attr);
                if (inst != null) inst.setBaseValue(0.0D);
            }
        } catch (Throwable ignored) {}

        // 9. PersistentData 内の残存フラグパージ
        try {
            CompoundTag tag = player.getPersistentData();
            tag.remove("UltraTestSwordDefence");
            tag.remove("UltraTestSwordTLSDefence");
            tag.remove("TheLastSwordFly");
            tag.remove("TheLastSwordDefence");
            tag.remove("TheLastSwordYouNeverForgotInvul");
            tag.remove("TheLastSwordYouNeverForgotFly");
            tag.remove("TheLastSwordYouNeverForgotDefence");
            tag.remove("tlsWorldAnchor");
            tag.remove("tlsWorldAnchorMax");
            tag.remove("tlsIsProtected");
            tag.remove("tlsTempJustifiedDefence");
            tag.remove("PlayerFlightIntent");
            // ECA
            tag.remove("ecaHealBanValue");
            tag.remove("ecaHealthLockEnc");
            tag.remove("ecaHealthLockKey");
            tag.remove("ecaHealthLockCheck");
            tag.remove("ecaMaxHealthLockEnc");
            tag.remove("ecaMaxHealthLockKey");
            tag.remove("ecaMaxHealthLockCheck");
            tag.remove("ecaResurrectionTracked");
            tag.remove("ecaInvulnerable");
            // Hyperlink
            tag.remove("hyperdaimc:NovelDead");
            tag.remove("hyperdaimc:Novelized");
            // Other cheat mods
            tag.remove("the_trial_monolith:SoulDamage");
            tag.remove("the_trial_monolith:SoulProtection");
            tag.remove("imds_reborn:isImdsInvulnerable");
        } catch (Throwable ignored) {}

        // 10. サバイバル時の飛行能力（The Last Sword の飛行の印があったときだけ）および重力の正常化
        try {
            if (swordFlight && !player.isCreative() && !player.isSpectator()) {
                if (player.getAbilities().mayfly || player.getAbilities().flying) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            player.setNoGravity(false);
            var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null && speedAttr.getValue() <= 0.005D) {
                speedAttr.setBaseValue(0.1D);
            }
        } catch (Throwable ignored) {}

        // 11. 周囲の危険な即死弾（TheLastEndSwordProjectile 等）を消去
        purgeHarmfulProjectilesNear(player, 16.0D);
    }

    /**
     * プレイヤーの周囲にある危険な射出弾・有害エンティティ（TheLastEndSwordProjectile 等）を安全に消去する。
     */
    public static void purgeHarmfulProjectilesNear(Player player, double radius) {
        if (player == null || player.level().isClientSide) return;
        try {
            net.minecraft.world.phys.AABB area = player.getBoundingBox().inflate(radius);
            var nearbyEntities = player.level().getEntities(player, area);
            for (Entity entity : nearbyEntities) {
                if (entity == null || entity.isRemoved()) continue;
                String className = entity.getClass().getName();
                if (className.contains("TheLastEndSwordProjectile")
                        || className.contains("InstantKill")
                        || className.contains("NovelProjectile")
                        || className.contains("AnnihilationProjectile")) {
                    entity.discard();
                }
            }
        } catch (Throwable ignored) {}
    }
}
