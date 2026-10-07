package com.automatavalkyries.item;

import com.automatavalkyries.entity.ValkyrieBody;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 素体ユニット（2026-10-07 ユーザー指定「組み立て機で素体を作る」。SPEC_VALKYRIE_GUI_AI §6「素体パーツをマシンで組み立て、型は使う素材で決まる」）。
 * 組み立て機でプロトコアと型ごとの合金から作り、地面に使うとその型の兵姫が目覚めて使った人の兵姫になる（電力はプロトコアの分）
 */
public class ValkyrieBodyItem extends Item {

    private final ValkyrieBody body;

    public ValkyrieBodyItem(ValkyrieBody body, Properties properties) {
        super(properties);
        this.body = body;
    }

    public ValkyrieBody body() {
        return body;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie_body.no_room").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (ValkyrieProtect.ownedCount(server.getServer(), player.getUUID()) >= ValkyrieProtect.MAX_REGISTERED) {
            player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie.registry_full", ValkyrieProtect.MAX_REGISTERED).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ValkyrieEntity v = spawn(server, at, player.getYRot() + 180.0F, body);
        if (v == null) return InteractionResult.FAIL;
        if (!v.wakeFromPod(player, ProtoCoreItem.CAPACITY)) {
            v.discard();
            return InteractionResult.FAIL;
        }
        server.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY(0.6), v.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
        if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        player.displayClientMessage(Component.translatable("message.automata_valkyries.valkyrie_body.woke", v.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.CONSUME;
    }

    /** 眠った（ポッドの深い休眠の）兵姫をその型で、HP 満タンで置く。wakeFromPod で起こす */
    @Nullable
    public static ValkyrieEntity spawn(ServerLevel level, BlockPos at, float yaw, ValkyrieBody body) {
        ValkyrieEntity v = ModEntities.VALKYRIE.get().create(level);
        if (v == null) return null;
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
        v.setBody(body);
        v.makePodSleeper();
        v.setHealth(v.getMaxHealth()); // 組み立てたばかりの体は無傷（ポッドの休眠は HP 5 にするので後で戻す。HP 5 のままだと撤退の HP を下回って戦わなかった。2026-10-07）
        level.addFreshEntity(v);
        return v;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_body_" + body.name().toLowerCase(Locale.ROOT) + ".type").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.automata_valkyries.valkyrie_body.desc").withStyle(ChatFormatting.GRAY));
    }
}
