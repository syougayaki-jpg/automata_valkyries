package com.automatavalkyries.entity;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 戦術兵姫の戦績（状態タブ。SPEC_VALKYRIE_GUI_AI §2.3）: 撃破数と、一緒に倒したボス。
 * 一緒に倒した = ボス（{@code forge:bosses}）が倒れたとき、そばに持ち主と稼働中の兵姫がいた【仮: 兵姫はボスから 32m・持ち主は 64m 以内】
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class ValkyrieRecords {

    private static final double VALKYRIE_RANGE = 32.0, OWNER_RANGE = 64.0;

    private ValkyrieRecords() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof ValkyrieEntity) return;
        if (event.getSource().getEntity() instanceof ValkyrieEntity killer) killer.kills++;
        if (!victim.getType().is(Tags.EntityTypes.BOSSES)) return;
        String name = victim.getDisplayName().getString();
        for (ValkyrieEntity v : victim.level().getEntitiesOfClass(ValkyrieEntity.class, victim.getBoundingBox().inflate(VALKYRIE_RANGE))) {
            if (v.isShutdown() || v.getOwnerUUID() == null || v.bosses.contains(name)) continue;
            Player owner = v.getOwner();
            if (owner == null || owner.distanceToSqr(victim) > OWNER_RANGE * OWNER_RANGE) continue;
            v.bosses.add(0, name);
            while (v.bosses.size() > ValkyrieEntity.MAX_BOSSES) v.bosses.remove(v.bosses.size() - 1);
        }
    }
}
