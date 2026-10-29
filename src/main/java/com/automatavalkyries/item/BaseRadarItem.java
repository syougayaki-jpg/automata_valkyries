package com.automatavalkyries.item;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModParticles;
import com.automatavalkyries.weapon.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 侵食基地レーダー（2026-10-30 ユーザー指定「浸食基地のレーダーも作って」）。右クリックで、いちばん近い侵食基地の方角と距離を出し、その方へ赤い光の筋を伸ばす。
 * スニークしながら右クリックで、探す物を 全部 → 物資倉庫 → クロノ・アウトリポスト → ディメンション・ファウンドリ → 空中庭園要塞 と切り替える。
 * 探すのはバニラの /locate と同じ仕組み（構造物のタグ {@code automata_valkyries:radar/<種類>}。まだ生成していない所の基地も見つかる）。重いのでクールタイム 5 秒
 */
public class BaseRadarItem extends Item {

    /** 探す物（タグの名前。0 = 全部） */
    public static final String[] MODES = {"corrupted_bases", "supply_depot", "chrono_outpost", "dimension_foundry", "chrono_bastion"};
    /** 探す広さ（/locate と同じ単位。置き方の区画の数） */
    public static final int SEARCH = 64;

    public BaseRadarItem(Properties properties) {
        super(properties);
    }

    public static int mode(ItemStack stack) {
        return stack.hasTag() ? Math.floorMod(stack.getTag().getInt("Mode"), MODES.length) : 0;
    }

    static Component modeName(int mode) {
        return mode == 0 ? Component.translatable("item.automata_valkyries.base_radar.all") : Component.translatable("structure.automata_valkyries." + MODES[mode]);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {                                                           // 探す物を切り替える
            int next = (mode(stack) + 1) % MODES.length;
            stack.getOrCreateTag().putInt("Mode", next);
            player.displayClientMessage(Component.translatable("message.automata_valkyries.base_radar.mode", modeName(next)).withStyle(ChatFormatting.AQUA), true);
            server.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
            return InteractionResultHolder.success(stack);
        }
        player.getCooldowns().addCooldown(this, 100);
        int mode = mode(stack);
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "radar/" + MODES[mode]));
        BlockPos found = server.findNearestMapStructure(tag, player.blockPosition(), SEARCH, false);
        if (found == null) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.base_radar.none", modeName(mode)).withStyle(ChatFormatting.GRAY), true);
            server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
            return InteractionResultHolder.success(stack);
        }
        double dx = found.getX() + 0.5 - player.getX(), dz = found.getZ() + 0.5 - player.getZ();
        // 方角（南から時計回りに 8 方位。兵姫探知レーダーと同じ並び・同じ言葉）
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        int dir = Math.floorMod(Math.round(yaw / 45.0F), 8);
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        player.displayClientMessage(Component.translatable("message.automata_valkyries.base_radar.found", modeName(mode),
                Component.translatable("message.automata_valkyries.radar.dir." + dir), String.format("%,d", dist)).withStyle(ChatFormatting.RED), true);
        Vec3 from = player.getEyePosition().subtract(0, 0.4, 0);
        Vec3 to = from.add(new Vec3(dx, 0, dz).normalize().scale(8));
        Fx.line(server, Fx.opt(ModParticles.GLOW.get(), 0xFF4060, 0.3F, 30), from, to, 0.3);
        server.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.base_radar.target", modeName(mode(stack))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.base_radar.desc").withStyle(ChatFormatting.GRAY));
    }
}
