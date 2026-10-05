package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.machine.CableBlock;
import com.automatavalkyries.machine.CableBlockEntity;
import com.automatavalkyries.machine.MachineBlock;
import com.automatavalkyries.machine.MachineBlockEntity;
import com.automatavalkyries.machine.MachineKind;
import com.automatavalkyries.machine.MachineMenu;
import com.automatavalkyries.machine.ValkyrieChargerBlock;
import com.automatavalkyries.machine.ValkyrieChargerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * 基礎文明マシン（追加拡張仕様書 §11.1.2）: 燃料発電機・蓄電器・電気炉・粉砕機・合金炉・金属プレスと、電力ケーブル。
 * 部品（電線・回路・モーター・発熱コイル・マシンフレーム）は ModItems。
 */
public final class ModMachines {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AutomataValkyries.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AutomataValkyries.MODID);

    public static final Map<MachineKind, RegistryObject<Block>> MACHINES = new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind kind : MachineKind.values()) {
            RegistryObject<Block> block = ModBlocks.BLOCKS.register(kind.id, () -> new MachineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4.0F, 8.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(MachineBlock.LIT) ? 9 : 0), kind));
            ModItems.ITEMS.register(kind.id, () -> new BlockItem(block.get(), new Item.Properties()));
            MACHINES.put(kind, block);
        }
    }

    public static final RegistryObject<Block> CABLE = ModBlocks.BLOCKS.register("power_cable", () -> new CableBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.WOOL).noOcclusion()));

    static {
        ModItems.ITEMS.register("power_cable", () -> new BlockItem(CABLE.get(), new Item.Properties()));
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY = BLOCK_ENTITIES.register("machine", () ->
            BlockEntityType.Builder.of(MachineBlockEntity::new,
                    MACHINES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CableBlockEntity>> CABLE_ENTITY = BLOCK_ENTITIES.register("power_cable", () ->
            BlockEntityType.Builder.of(CableBlockEntity::new, CABLE.get()).build(null));

    // ---- 兵姫用充電台（SPEC_VALKYRIE_GUI_AI §4）: FE を蓄え、座った兵姫が充電する ----
    public static final RegistryObject<Block> VALKYRIE_CHARGER = ModBlocks.BLOCKS.register("valkyrie_charger", () -> new ValkyrieChargerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(4.0F, 8.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> state.getValue(ValkyrieChargerBlock.LIT) ? 12 : 4)));

    static {
        ModItems.ITEMS.register("valkyrie_charger", () -> new BlockItem(VALKYRIE_CHARGER.get(), new Item.Properties()));
    }

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ValkyrieChargerBlockEntity>> VALKYRIE_CHARGER_ENTITY = BLOCK_ENTITIES.register("valkyrie_charger", () ->
            BlockEntityType.Builder.of(ValkyrieChargerBlockEntity::new, VALKYRIE_CHARGER.get()).build(null));

    public static final RegistryObject<MenuType<MachineMenu>> MENU = MENUS.register("machine", () -> IForgeMenuType.create(MachineMenu::new));

    private ModMachines() {}

    public static BlockEntityType<MachineBlockEntity> blockEntityType(MachineKind kind) {
        return MACHINE_ENTITY.get();
    }

    /** クラスを読み込んで登録を済ませる（メインクラスから ITEMS / BLOCKS の登録前に呼ぶ） */
    public static void init() {}
}
