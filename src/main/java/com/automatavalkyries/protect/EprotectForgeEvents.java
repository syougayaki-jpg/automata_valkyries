package com.automatavalkyries.protect;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.item.SuperKillStickItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
        if (target instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;

            // 攻撃された瞬間に即死 MOD の抹消リスト除外・フラグ解除を実行
            KillModCounters.counterAll(target);

            DamageSource source = event.getSource();
            if (isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof IEprotected provider) {
            Eprotect protect = provider.getEprotect();
            if (protect == null || !protect.isProtectionActive()) return;

            KillModCounters.counterAll(target);

            DamageSource source = event.getSource();
            if (isInstantKillSource(source) || isAttackerHoldingKillItem(source)) {
                event.setCanceled(true);
                event.setAmount(0.0F);
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
    }

    private static boolean isInstantKillSource(DamageSource source) {
        if (source.is(DamageTypes.GENERIC_KILL)) return true;
        String msgId = source.getMsgId();
        if (msgId == null) return false;
        String lower = msgId.toLowerCase();
        return lower.contains("imds")
                || lower.contains("novel")
                || lower.contains("hyper")
                || lower.contains("kill")
                || lower.contains("outofworld")
                || lower.contains("felloutofworld");
    }

    private static boolean isAttackerHoldingKillItem(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            return isKillItem(attacker.getMainHandItem()) || isKillItem(attacker.getOffhandItem());
        }
        return false;
    }

    private static boolean isKillItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String className = stack.getItem().getClass().getName().toLowerCase();
        String registryName = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())).toLowerCase();
        return className.contains("imds")
                || className.contains("dominator")
                || className.contains("hyperdaimc")
                || className.contains("novel")
                || registryName.contains("imds")
                || registryName.contains("hyperdaimc")
                || registryName.contains("novel");
    }
}
