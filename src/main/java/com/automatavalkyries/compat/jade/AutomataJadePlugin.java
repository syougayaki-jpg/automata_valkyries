package com.automatavalkyries.compat.jade;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.chrono.ChronoMachineBlock;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import com.automatavalkyries.entity.ValkyrieEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade の連携（2026-10-30 ユーザー指定「他MODとの連携要素を増やす」。質問で: 情報表示・装備枠系）。Jade が入っている時だけ Jade から読み込まれる（このMODの他のクラスからは参照しない）。
 * 電力のバーは Jade が自分で出すので、ここでは Jade が知らない物を足す:
 * <ul>
 *   <li>クロノリンク・マシン: ティア・使えるリンクの数（ヴォイド・リポジトリは入っている種類の数も）</li>
 *   <li>戦術兵姫: 素体タイプ・持ち主・行動モード・機能停止／休眠・電力・シールド・同調率</li>
 * </ul>
 */
@WailaPlugin
public class AutomataJadePlugin implements IWailaPlugin {

    static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "chrono_machine");
    static final ResourceLocation VALKYRIE = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "valkyrie");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineProvider.INSTANCE, ChronoMachineBlockEntity.class);
        registration.registerEntityDataProvider(ValkyrieProvider.INSTANCE, ValkyrieEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineProvider.INSTANCE, ChronoMachineBlock.class);
        registration.registerEntityComponent(ValkyrieProvider.INSTANCE, ValkyrieEntity.class);
    }

    /** クロノリンク・マシン */
    enum MachineProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ChronoMachineBlockEntity machine)) return;
            data.putString("Tier", machine.tier().id);
            data.putInt("Links", machine.links().size());
            data.putInt("LinkSlots", machine.tier().linkSlots);
            if (machine instanceof VoidRepositoryBlockEntity repo) {
                data.putInt("Types", repo.types());
                data.putInt("MaxTypes", VoidRepositoryBlockEntity.maxTypes(machine.tier()));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Tier")) return;
            tooltip.add(Component.translatable("tier." + AutomataValkyries.MODID + "." + data.getString("Tier")).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("jade.automata_valkyries.links", data.getInt("Links"), data.getInt("LinkSlots")).withStyle(ChatFormatting.GRAY));
            if (data.contains("Types")) {
                int max = data.getInt("MaxTypes");
                tooltip.add(Component.translatable("jade.automata_valkyries.types", data.getInt("Types"), max == Integer.MAX_VALUE ? "∞" : String.valueOf(max)).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return MACHINE;
        }
    }

    /** 戦術兵姫 */
    enum ValkyrieProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof ValkyrieEntity v)) return;
            data.putString("Body", v.getBody().name().toLowerCase(java.util.Locale.ROOT));
            data.putString("Mode", v.getMode().name().toLowerCase(java.util.Locale.ROOT));
            data.putInt("Energy", v.getEnergy());
            data.putInt("MaxEnergy", v.maxEnergy());
            data.putFloat("Shield", v.getShield());
            data.putFloat("MaxShield", v.getMaxShield());
            data.putFloat("Sync", v.getSyncRate());
            data.putBoolean("Shutdown", v.isShutdown());
            data.putBoolean("Asleep", v.isAsleep());
            var owner = v.getOwner();
            if (owner != null) data.putString("Owner", owner.getGameProfile().getName());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Body")) return;
            tooltip.add(Component.translatable("gui.automata_valkyries.valkyrie.body." + data.getString("Body")).withStyle(ChatFormatting.AQUA));
            if (data.contains("Owner")) tooltip.add(Component.translatable("jade.automata_valkyries.owner", data.getString("Owner")).withStyle(ChatFormatting.GRAY));
            Component state = data.getBoolean("Shutdown") ? Component.translatable("jade.automata_valkyries.shutdown").withStyle(ChatFormatting.RED)
                    : data.getBoolean("Asleep") ? Component.translatable("jade.automata_valkyries.asleep").withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("gui.automata_valkyries.valkyrie.mode." + data.getString("Mode")).withStyle(ChatFormatting.GREEN);
            tooltip.add(Component.translatable("jade.automata_valkyries.state", state));
            tooltip.add(Component.translatable("jade.automata_valkyries.energy", String.format("%,d", data.getInt("Energy")), String.format("%,d", data.getInt("MaxEnergy")))
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("jade.automata_valkyries.shield", String.format("%.1f", data.getFloat("Shield")), String.format("%.1f", data.getFloat("MaxShield")))
                    .withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("jade.automata_valkyries.sync", String.format("%.1f", data.getFloat("Sync"))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        @Override
        public ResourceLocation getUid() {
            return VALKYRIE;
        }
    }
}
