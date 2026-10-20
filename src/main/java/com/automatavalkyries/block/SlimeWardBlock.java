package com.automatavalkyries.block;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * スライム湧かなくする君（2026-10-20 ユーザー指定「スライムわかなくする君作って」）。置いておくと、まわり {@value #RADIUS} ブロック（横の距離）では
 * スライムが自然に湧かない（自然のスポーンとチャンクの生成時のスポーン。スポーンエッグ・召喚・分裂はそのまま）。
 * 置いた場所はワールドごとに覚える（{@link Wards}。ブロックエンティティは持たない）。読み込まれていない所に置いたものも効く
 */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public class SlimeWardBlock extends Block {

    public static final int RADIUS = 128;

    public SlimeWardBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && !old.is(this)) Wards.get(server).add(pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (level instanceof ServerLevel server && !now.is(this)) Wards.get(server).remove(pos);
        super.onRemove(state, level, pos, now, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.automata_valkyries.slime_ward.desc", RADIUS).withStyle(ChatFormatting.GRAY));
    }

    /** スライムの自然な湧きを、近くに置いてあれば断る */
    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityType.SLIME) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        ServerLevel level = event.getLevel().getLevel();
        if (Wards.get(level).covers(event.getPos())) event.setResult(Event.Result.DENY);
    }

    /** 置いてある場所（ワールドの保存データ） */
    public static final class Wards extends SavedData {
        static final String NAME = AutomataValkyries.MODID + "_slime_wards";
        private final Set<Long> positions = new HashSet<>();

        public static Wards get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Wards::load, Wards::new, NAME);
        }

        static Wards load(CompoundTag tag) {
            Wards w = new Wards();
            for (Tag t : tag.getList("Pos", Tag.TAG_LONG)) w.positions.add(((LongTag) t).getAsLong());
            return w;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (long p : positions) list.add(LongTag.valueOf(p));
            tag.put("Pos", list);
            return tag;
        }

        void add(BlockPos pos) {
            if (positions.add(pos.asLong())) setDirty();
        }

        void remove(BlockPos pos) {
            if (positions.remove(pos.asLong())) setDirty();
        }

        /** pos が、どれかから横の距離 {@link SlimeWardBlock#RADIUS} 以内か */
        public boolean covers(BlockPos pos) {
            for (long p : positions) {
                BlockPos w = BlockPos.of(p);
                long dx = w.getX() - pos.getX(), dz = w.getZ() - pos.getZ();
                if (dx * dx + dz * dz <= (long) RADIUS * RADIUS) return true;
            }
            return false;
        }
    }
}
