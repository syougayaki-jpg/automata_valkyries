package com.automatavalkyries.item;

import com.automatavalkyries.chrono.ChronoLink;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 空間調律器『クロノ・チューナー』（既存仕様書 §7.6、設計書 §3.3）。
 * <ul>
 *   <li>スニーク＋右クリック（ブロック）: 搬入出先（チェスト・タンク・給電ドック・他のマシン等）の座標と面を覚える</li>
 *   <li>右クリック（クロノリンク・マシン）: 覚えた先をマシンの空間リンクに登録する（クロノスクラップを使う。T1 1、T2 4、T3 16、T4 不要）</li>
 *   <li>空中でスニーク＋右クリック: 覚えた先を消す。空中で右クリック: 登録する向き（搬入 / 搬出 / 両方）を切り替える</li>
 * </ul>
 */
public class ChronoTunerItem extends Item {

    public ChronoTunerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (player == null || be == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            Direction face = context.getClickedFace();
            boolean usable = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).isPresent() || be.getCapability(ForgeCapabilities.ENERGY, face).isPresent();
            if (!usable) {
                player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.unusable"), true);
                return InteractionResult.FAIL;
            }
            CompoundTag target = new CompoundTag();
            target.putLong("pos", pos.asLong());
            target.putString("dim", level.dimension().location().toString());
            target.putByte("face", (byte) face.get3DDataValue());
            stack.getOrCreateTag().put("Target", target);
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.bound", pos.toShortString(),
                    Component.translatable("direction.automata_valkyries." + face.getName())), true);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        if (!(be instanceof ChronoMachineBlockEntity machine)) return InteractionResult.PASS;
        ChronoLink link = target(stack);
        if (link == null) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.no_target"), true);
            return InteractionResult.FAIL;
        }
        ChronoTier tier = machine.tier();
        boolean same = link.dimension().equals(level.dimension());
        if (!tier.reaches(same, same ? pos.distSqr(link.pos()) : 0)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.out_of_range"), true);
            return InteractionResult.FAIL;
        }
        if (machine.links().size() >= tier.linkSlots) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.full", tier.linkSlots), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild && tier.linkCost > 0 && !consumeScrap(player, tier.linkCost)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.need_scrap", tier.linkCost), true);
            return InteractionResult.FAIL;
        }
        if (!machine.addLink(link)) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.duplicate"), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.linked", link.pos().toShortString(),
                Component.translatable("link_mode.automata_valkyries." + link.mode().name().toLowerCase())), true);
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            stack.removeTagKey("Target");
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.cleared"), true);
        } else {
            ChronoLink.Mode mode = mode(stack).next();
            stack.getOrCreateTag().putByte("Mode", (byte) mode.ordinal());
            player.displayClientMessage(Component.translatable("message.automata_valkyries.tuner.mode",
                    Component.translatable("link_mode.automata_valkyries." + mode.name().toLowerCase())), true);
        }
        return InteractionResultHolder.success(stack);
    }

    public static ChronoLink.Mode mode(ItemStack stack) {
        int ordinal = stack.getTag() == null ? 2 : (stack.getTag().contains("Mode") ? stack.getTag().getByte("Mode") : 2);
        return ChronoLink.Mode.values()[Math.max(0, Math.min(2, ordinal))];
    }

    @Nullable
    public static ChronoLink target(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Target")) return null;
        CompoundTag t = tag.getCompound("Target");
        return new ChronoLink(BlockPos.of(t.getLong("pos")), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))),
                Direction.from3DDataValue(t.getByte("face")), mode(stack));
    }

    private static boolean consumeScrap(Player player, int count) {
        int have = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(ModItems.DROP_CHRONO_SCRAP.get())) have += s.getCount();
        if (have < count) return false;
        int left = count;
        for (ItemStack s : player.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(ModItems.DROP_CHRONO_SCRAP.get())) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return target(stack) != null; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ChronoLink link = target(stack);
        tooltip.add(link == null ? Component.translatable("item.automata_valkyries.tool_chrono_tuner.empty").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("item.automata_valkyries.tool_chrono_tuner.target", link.pos().toShortString(),
                link.dimension().location().toString()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.tool_chrono_tuner.mode",
                Component.translatable("link_mode.automata_valkyries." + mode(stack).name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.automata_valkyries.tool_chrono_tuner.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
