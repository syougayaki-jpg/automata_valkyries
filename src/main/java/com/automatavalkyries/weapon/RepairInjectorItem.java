package com.automatavalkyries.weapon;

import com.automatavalkyries.registry.ModWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 戦術リペアインジェクター（§7.2 module_repair_injector、T1）: 左手支援ユニット。電力は使わず、内部のナノマシン溶液を使う。
 * 手（どちらでも）に持っている者の HP が 40% 以下になると、自動でナノマシンを噴霧して HP を 20 回復する。
 * 溶液は内部に 8 本【提案】まで入り、右クリックで持ち物から詰める。続けて噴霧するまで 5 秒空ける【提案】。
 */
public class RepairInjectorItem extends TacticalWeaponItem {

    public static final int TANK = 8, COOLDOWN = 100;
    public static final float HEAL = 20.0F, THRESHOLD = 0.4F, WEIGHT = 5.0F;

    public RepairInjectorItem(Properties properties) {
        super(properties, 0, WEIGHT, false);
    }

    public static int solution(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Nano") : 0;
    }

    public static void setSolution(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt("Nano", Math.max(0, Math.min(TANK, amount)));
    }

    @Override
    public ItemStack charged() {
        ItemStack stack = super.charged();
        setSolution(stack, TANK);
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack injector = player.getItemInHand(hand);
        int need = TANK - solution(injector);
        if (need <= 0) return InteractionResultHolder.pass(injector);
        int loaded = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && loaded < need; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModWeapons.NANO_SOLUTION.get())) {
                int taken = Math.min(need - loaded, stack.getCount());
                stack.shrink(taken);
                loaded += taken;
            }
        }
        if (loaded == 0) {
            if (!level.isClientSide()) player.displayClientMessage(Component.translatable("gui.automata_valkyries.no_solution").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(injector);
        }
        setSolution(injector, solution(injector) + loaded);
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 1.2F);
        return InteractionResultHolder.sidedSuccess(injector, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity holder)) return;
        if (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) return;
        tryInject(server, holder, stack);
    }

    /** HP が 40% 以下なら噴霧する。噴霧したら true（兵姫に持たせたときも同じ処理） */
    public static boolean tryInject(ServerLevel level, LivingEntity holder, ItemStack stack) {
        if (!holder.isAlive() || holder.getHealth() > holder.getMaxHealth() * THRESHOLD) return false;
        if (solution(stack) <= 0 || level.getGameTime() < stack.getOrCreateTag().getLong("Ready")) return false;
        setSolution(stack, solution(stack) - 1);
        stack.getOrCreateTag().putLong("Ready", level.getGameTime() + COOLDOWN);
        holder.heal(HEAL);
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(holder.getX(), holder.getY(0.5), holder.getZ());
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.GLOW.get(), 0xB0FFD0, 0.35F, 20), at, 18, 0.45, 0.6, 0.45, 0.02);
        Fx.spray(level, Fx.opt(com.automatavalkyries.registry.ModParticles.SPARK.get(), Fx.GREEN, 0.25F, 14), at, 10, 0.5, 0.8, 0.5, 0.03);
        Fx.shockwave(level, holder.position().add(0, 0.05, 0), 0x8AFFC0, 1.6F, 12);
        level.playSound(null, holder.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
        if (stack.getItem() instanceof RepairInjectorItem item) item.playUse(holder, stack);
        return true;
    }

    @Override
    protected void appendStatus(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("gui.automata_valkyries.nano_solution", solution(stack), TANK).withStyle(ChatFormatting.GREEN));
    }
}
