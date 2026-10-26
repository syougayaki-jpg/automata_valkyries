package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.seer.AkashicCubeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * 観測の立方体（2026-10-26 ユーザー指定「アカシックちゃんを一般ボスとしておまけとして実装する」。質問で: 専用素材と立方体の装備）。
 * ホットバーか左手にあると、まわりを桃・水色の 3 つの立方体が回り（描く側 client/AkashicCubesClient）、{@value #RANGE} m 以内の一番近い敵のモブへ
 * {@value #INTERVAL} Tick ごとに立方体を撃つ（{@value #DAMAGE}。プレイヤー・兵姫・おとなしいモブには当たらない）。事象観測姫アカシックの観測の核から作る
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class AkashicCubesItem extends Item {

    public static final double RANGE = 12.0;
    public static final int INTERVAL = 30;
    public static final float DAMAGE = 5.0F;

    public AkashicCubesItem(Properties properties) {
        super(properties);
    }

    /** ホットバーか左手に持っているか */
    public static boolean carrying(Player player) {
        if (player.getOffhandItem().getItem() instanceof AkashicCubesItem) return true;
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).getItem() instanceof AkashicCubesItem) return true;
        return false;
    }

    /** 立方体の回る所（描く側と同じ式。i は 0〜2） */
    public static Vec3 orbit(Player player, float time, int i) {
        double a = time * 0.06 + i * Math.PI * 2 / 3;
        return player.position().add(Math.cos(a) * 0.9, 1.3 + Math.sin(time * 0.08 + i) * 0.12, Math.sin(a) * 0.9);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || !player.isAlive() || player.tickCount % INTERVAL != 0 || !carrying(player)) return;
        LivingEntity target = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                        e -> e instanceof Enemy && e.isAlive() && e.distanceTo(player) <= RANGE && player.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        if (target == null) return;
        int i = (player.tickCount / INTERVAL) % 3;
        Vec3 from = orbit(player, player.tickCount, i);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        AkashicCubeEntity.shoot(player, from, to.subtract(from), 1.0F, DAMAGE, i == 1 ? AkashicCubeEntity.CYAN : AkashicCubeEntity.PINK, 0.12F).life(40);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.akashic_cubes.desc").withStyle(ChatFormatting.GRAY));
    }
}
