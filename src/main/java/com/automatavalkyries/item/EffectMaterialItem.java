package com.automatavalkyries.item;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 上位合金・回路などの、見た目の派手な素材（SPEC §7.7）。手に持っているとき・ドロップしているとき、決まった間隔で
 * その素材の粒子を出す（クライアントだけの見た目）。foil は付与のきらめき（エンチャントの光）を常に出すか。
 */
public class EffectMaterialItem extends Item {

    private final List<ParticleOptions> particles;
    private final int interval;
    private final boolean foil;

    public EffectMaterialItem(Properties properties, List<ParticleOptions> particles, int interval, boolean foil) {
        super(properties);
        this.particles = particles;
        this.interval = interval;
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || entity.tickCount % interval != 0) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (!selected && living.getOffhandItem() != stack) return;
        var look = living.getLookAngle();
        spawn(level, living.getX() + look.x * 0.5, living.getEyeY() - 0.45, living.getZ() + look.z * 0.5, 0.25);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide && entity.tickCount % interval == 0) spawn(level, entity.getX(), entity.getY() + 0.3, entity.getZ(), 0.3);
        return false; // 通常のアイテムの処理は続ける
    }

    private void spawn(Level level, double x, double y, double z, double spread) {
        var random = level.random;
        for (ParticleOptions particle : particles) {
            level.addParticle(particle, x + (random.nextDouble() - 0.5) * spread, y + random.nextDouble() * 0.15,
                    z + (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * 0.02, 0.03D, (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
