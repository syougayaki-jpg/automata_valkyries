package com.automatavalkyries.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * ルミナルクリスタル。手に持っているとき・ドロップしているとき、15 Tick に 1 回、ごく小さな金色の光の粒がふわりと立ち昇る
 * （見た目だけ。クライアントで出す）。テクスチャは核が明滅するアニメーション（crystal_luminal.png.mcmeta）。
 */
public class LuminalCrystalItem extends Item {

    /** ソーラーゴールド（#FFD166）の小さな光の粒 */
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.4F), 0.55F);
    private static final int INTERVAL = 15;

    public LuminalCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || entity.tickCount % INTERVAL != 0) return;
        if (!(entity instanceof LivingEntity living)) return;
        boolean held = selected || living.getOffhandItem() == stack;
        if (!held) return;
        // 手の前あたり（視線の少し下）
        var look = living.getLookAngle();
        double x = living.getX() + look.x * 0.5;
        double y = living.getEyeY() - 0.45;
        double z = living.getZ() + look.z * 0.5;
        spawn(level, x, y, z);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide && entity.tickCount % INTERVAL == 0) {
            spawn(level, entity.getX(), entity.getY() + 0.3, entity.getZ());
        }
        return false; // 通常のアイテムの処理は続ける
    }

    private static void spawn(Level level, double x, double y, double z) {
        var random = level.random;
        level.addParticle(GOLD_DUST, x + (random.nextDouble() - 0.5) * 0.2, y + random.nextDouble() * 0.1,
                z + (random.nextDouble() - 0.5) * 0.2, 0.0D, 0.04D, 0.0D);
    }
}
