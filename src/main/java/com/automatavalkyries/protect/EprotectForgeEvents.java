package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.SuperKillStickItem;
import com.automatavalkyries.protect.attack.AttackLog;
import com.automatavalkyries.protect.attack.AttackOp;
import com.automatavalkyries.protect.attack.CallerAttribution;
import com.automatavalkyries.protect.attack.ModBlacklist;
import com.automatavalkyries.protect.flag.FlagDictionary;
import com.automatavalkyries.protect.plus.EprotectPlusLink;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge イベントバスにおける最優先（HIGHEST）防御インターセプト。
 * Mixin より前のイベント処理段階で IMDS や Hyperlink の攻撃を検知・無効化し、即死 MOD の登録・フラグを解除する。
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EprotectForgeEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof net.minecraft.world.entity.player.Player player && (PlayerWard.isActive(player) || PlayerWard.isRespawnProtected(player))) {
            DamageSource source = event.getSource();
            if (PlayerWard.isRespawnProtected(player) || PlayerWard.isInstantKill(source) || isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                recordKillDamage(target, source);
                return;
            }
        }
        if (target instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;

            // 攻撃された瞬間に即死 MOD の抹消リスト除外・フラグ解除を実行
            KillModCounters.counterAll(target);

            DamageSource source = event.getSource();
            String sourceMod = getSourceMod(source);
            boolean isBlacklisted = ModBlacklist.contains(sourceMod);
            if (isBlacklisted) {
                EprotectPlusLink.triggerBlacklistReinforcement(sourceMod);
            }

            boolean isReinforced = EprotectPlusLink.isReinforcedActive() || protect.isReinforcedActive();
            if (isReinforced || isBlacklisted) {
                event.setCanceled(true);
                AttackLog.refused(target, AttackOp.DAMAGE + ":reinforced_guard", sourceMod != null ? sourceMod : "eprotect_plus");
                return;
            }

            if (isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                recordKillDamage(target, source);
            }
        }
    }

    /** 即死ダメージの拒否を記録する。ダメージの種類と、攻撃者の持つアイテムの名前空間（アイテムによる即死ならその MOD の ID）も添える */
    /**
     * 他 MOD が保護対象の毎 Tick の処理（LivingTickEvent）をキャンセルして止めたら、最後に打ち消す。
     * 止められると修復（本体の Tick から呼ぶ）も AI も動かなくなる
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!event.isCanceled() || event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof IEprotected provider)) return;
        Eprotect protect = provider.getEprotect();
        if (protect == null || !protect.isProtectionActive()) return;
        event.setCanceled(false);
        AttackLog.repaired(event.getEntity(), "cancelled_tick");
    }

    public static void recordKillDamage(LivingEntity target, DamageSource source) {
        var typeKey = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
        String knownMod = typeKey != null && !"minecraft".equals(typeKey.getNamespace()) ? typeKey.getNamespace() : null;
        String item = "";
        if (source.getEntity() instanceof LivingEntity attacker && !attacker.getMainHandItem().isEmpty()) {
            var itemKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(attacker.getMainHandItem().getItem());
            if (itemKey != null) {
                item = ",item=" + itemKey;
                if (!"minecraft".equals(itemKey.getNamespace())) knownMod = itemKey.getNamespace();
            }
        }
        String type = typeKey != null ? typeKey.toString() : source.getMsgId();
        AttackLog.refused(target, AttackOp.KILL_DAMAGE + ":" + type + item, knownMod);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof net.minecraft.world.entity.player.Player player && (PlayerWard.isActive(player) || PlayerWard.isRespawnProtected(player))) {
            DamageSource source = event.getSource();
            if (PlayerWard.isRespawnProtected(player) || PlayerWard.isInstantKill(source) || isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                recordKillDamage(target, source);
                return;
            }
        }
        if (target instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;

            KillModCounters.counterAll(target);

            DamageSource source = event.getSource();
            String sourceMod = getSourceMod(source);
            boolean isBlacklisted = ModBlacklist.contains(sourceMod);
            if (isBlacklisted) {
                EprotectPlusLink.triggerBlacklistReinforcement(sourceMod);
            }

            boolean isReinforced = EprotectPlusLink.isReinforcedActive() || protect.isReinforcedActive();
            if (isReinforced || isBlacklisted) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                AttackLog.refused(target, AttackOp.DAMAGE + ":reinforced_guard", sourceMod != null ? sourceMod : "eprotect_plus");
                return;
            }

            if (isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                event.setAmount(0.0F);
                recordKillDamage(target, source);
                return;
            }

            float amount = event.getAmount();
            if (!Float.isFinite(amount) || amount >= Eprotect.HUGE_DAMAGE_THRESHOLD) {
                event.setAmount(Eprotect.HUGE_DAMAGE_CLAMP);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof net.minecraft.world.entity.player.Player player && (PlayerWard.isActive(player) || PlayerWard.isRespawnProtected(player))) {
            DamageSource source = event.getSource();
            if (PlayerWard.isRespawnProtected(player) || PlayerWard.isInstantKill(source) || isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                player.deathTime = 0;
                recordKillDamage(target, source);
                return;
            }
        }
        if (target instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null) return;

            KillModCounters.counterAll(target);

            if (!protect.canDie()) {
                event.setCanceled(true);
                target.setHealth(target.getMaxHealth());
                target.deathTime = 0;
            }
        }
    }

    /**
     * 他 MOD による参加拒否（EntityJoinLevelEvent のキャンセル）を最後に打ち消す。
     * 拒否されると入り直しも差し替えも失敗し、修復のたびに異常が積まれて数秒で退避に追い込まれる。
     * 優先度の高い購読者から先に呼ばれるので、他 MOD のキャンセルを覆すには最後（LOWEST）に回る必要がある。
     * 超抹殺の棒（差し替えの検証用のデバッグ道具）の拒否だけは通す。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.isCanceled() || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof IEprotected provider)) return;
        Eprotect protect = provider.getEprotect();
        if (protect == null || !protect.isProtectionActive() || SuperKillStickItem.isRefused(event.getEntity())) return;
        event.setCanceled(false);
        AutomataValkyries.LOGGER.warn("[eprotect] Overrode a cancelled world join of {}", event.getEntity());
        AttackLog.repaired(event.getEntity(), "cancelled_join");
    }

    /**
     * 他 MOD による強制テレポート・奈落落とし・異常座標移動を最優先で阻止する。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;

            double tx = event.getTargetX();
            double ty = event.getTargetY();
            double tz = event.getTargetZ();

            // NaN / 無限大 / 奈落（Y < -64）への強制テレポート
            if (!Double.isFinite(tx) || !Double.isFinite(ty) || !Double.isFinite(tz) || ty < -64.0) {
                event.setCanceled(true);
                AttackLog.refused(event.getEntity(), AttackOp.FORCED_RELOCATION, CallerAttribution.foreignClassMod(event.getEntity()));
                return;
            }

            // 外部 MOD のコードからのテレポート呼び出しを拒否
            String foreignMod = CallerAttribution.foreignClassMod(event.getEntity());
            if (foreignMod != null) {
                event.setCanceled(true);
                AttackLog.refused(event.getEntity(), AttackOp.FORCED_RELOCATION, foreignMod);
            }
        }
    }

    public static String getSourceMod(DamageSource source) {
        if (source == null) return null;
        if (source.getEntity() instanceof LivingEntity attacker && !attacker.getMainHandItem().isEmpty()) {
            net.minecraft.resources.ResourceLocation item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(attacker.getMainHandItem().getItem());
            if (item != null && !"minecraft".equals(item.getNamespace())) {
                return item.getNamespace();
            }
        }
        if (source.getDirectEntity() != null) {
            net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType());
            if (key != null && !"minecraft".equals(key.getNamespace())) {
                return key.getNamespace();
            }
            String mod = CallerAttribution.modOf(source.getDirectEntity().getClass());
            if (CallerAttribution.isForeign(mod)) return mod;
        }
        if (source.getEntity() != null) {
            net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType());
            if (key != null && !"minecraft".equals(key.getNamespace())) {
                return key.getNamespace();
            }
            String mod = CallerAttribution.modOf(source.getEntity().getClass());
            if (CallerAttribution.isForeign(mod)) return mod;
        }
        var optKey = source.typeHolder().unwrapKey();
        if (optKey.isPresent()) {
            String ns = optKey.get().location().getNamespace();
            if (!"minecraft".equals(ns)) return ns;
        }
        return null;
    }

    public static boolean isInstantKillSource(DamageSource source) {
        return FlagDictionary.isHarmfulDamageSource(source);
    }

    public static boolean isAttackerHoldingKillItem(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            return isForeignKillItem(attacker.getMainHandItem()) || isForeignKillItem(attacker.getOffhandItem());
        }
        return false;
    }

    /**
     * 他 MOD の即死武器か。このMOD 自身のテスト用の棒（抹殺の棒など）は名前に kill を含むが、保護を確かめるための道具なので
     * 被弾を丸ごと止めず、通常の被弾の上限（1 撃・時間あたり最大 HP の 10%）で受けさせる。
     */
    private static boolean isForeignKillItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key != null && com.automatavalkyries.AutomataValkyries.MODID.equals(key.getNamespace())) return false;
        return isKillItem(stack);
    }

    public static boolean isKillItem(ItemStack stack) {
        return FlagDictionary.isKillItem(stack);
    }
}
