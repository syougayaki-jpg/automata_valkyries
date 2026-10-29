package com.automatavalkyries.registry;

import com.automatavalkyries.chrono.ChronoFabricatorBlockEntity;
import com.automatavalkyries.chrono.ChronoMachineBlock;
import com.automatavalkyries.chrono.ChronoMachineBlockEntity;
import com.automatavalkyries.chrono.ChronoMachineItem;
import com.automatavalkyries.chrono.ChronoMachineType;
import com.automatavalkyries.chrono.ChronoMenu;
import com.automatavalkyries.chrono.ChronoSmelterBlockEntity;
import com.automatavalkyries.chrono.ChronoTier;
import com.automatavalkyries.chrono.LuminalAdapterBlockEntity;
import com.automatavalkyries.chrono.LuminalReactorBlockEntity;
import com.automatavalkyries.chrono.TetherPylonBlockEntity;
import com.automatavalkyries.chrono.VoidRepositoryBlockEntity;
import com.automatavalkyries.item.BlueprintCardItem;
import com.automatavalkyries.item.ChronoTunerItem;
import com.automatavalkyries.item.TierFrameItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * クロノリンク・マシン（docs/SPEC_CHRONOLINK_MACHINES.md）: マシン 6 種（ティアはブロックの状態）、ティアフレーム 4 段、アップデートキット 3 段、
 * クロノ・チューナー、設計図カード。給電ドックは兵姫を実装してから。
 */
public final class ModChrono {

    public static final Map<ChronoMachineType, RegistryObject<Block>> MACHINES = new EnumMap<>(ChronoMachineType.class);

    public static final RegistryObject<Block> LUMINAL_REACTOR = machine(ChronoMachineType.LUMINAL_REACTOR, () -> LuminalReactorBlockEntity::new);
    public static final RegistryObject<Block> CHRONO_SMELTER = machine(ChronoMachineType.CHRONO_SMELTER, () -> ChronoSmelterBlockEntity::new);
    public static final RegistryObject<Block> LUMINAL_ADAPTER = machine(ChronoMachineType.LUMINAL_ADAPTER, () -> LuminalAdapterBlockEntity::new);
    public static final RegistryObject<Block> CHRONO_FABRICATOR = machine(ChronoMachineType.CHRONO_FABRICATOR, () -> ChronoFabricatorBlockEntity::new);
    public static final RegistryObject<Block> TETHER_PYLON = machine(ChronoMachineType.TETHER_PYLON, () -> TetherPylonBlockEntity::new);
    public static final RegistryObject<Block> VOID_REPOSITORY = machine(ChronoMachineType.VOID_REPOSITORY, () -> VoidRepositoryBlockEntity::new);
    public static final RegistryObject<Block> TACTICAL_ARMORY = machine(ChronoMachineType.TACTICAL_ARMORY,
            () -> com.automatavalkyries.chrono.TacticalArmoryBlockEntity::new);
    public static final RegistryObject<Block> CHARGING_DOCK = machine(ChronoMachineType.CHARGING_DOCK,
            () -> com.automatavalkyries.chrono.ChargingDockBlockEntity::new);
    /** 時間を扱う機械（2026-10-29 ユーザー指定「時間を扱う機械を直接追加して」） */
    public static final RegistryObject<Block> CHRONO_ACCELERATOR = machine(ChronoMachineType.CHRONO_ACCELERATOR,
            () -> com.automatavalkyries.chrono.ChronoAcceleratorBlockEntity::new);
    public static final RegistryObject<Block> CHRONO_REWINDER = machine(ChronoMachineType.CHRONO_REWINDER,
            () -> com.automatavalkyries.chrono.ChronoRewinderBlockEntity::new);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LuminalReactorBlockEntity>> REACTOR_ENTITY = ModMachines.BLOCK_ENTITIES.register("luminal_reactor",
            () -> BlockEntityType.Builder.of(LuminalReactorBlockEntity::new, LUMINAL_REACTOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ChronoSmelterBlockEntity>> SMELTER_ENTITY = ModMachines.BLOCK_ENTITIES.register("chrono_smelter",
            () -> BlockEntityType.Builder.of(ChronoSmelterBlockEntity::new, CHRONO_SMELTER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LuminalAdapterBlockEntity>> ADAPTER_ENTITY = ModMachines.BLOCK_ENTITIES.register("luminal_adapter",
            () -> BlockEntityType.Builder.of(LuminalAdapterBlockEntity::new, LUMINAL_ADAPTER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ChronoFabricatorBlockEntity>> FABRICATOR_ENTITY = ModMachines.BLOCK_ENTITIES.register("chrono_fabricator",
            () -> BlockEntityType.Builder.of(ChronoFabricatorBlockEntity::new, CHRONO_FABRICATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TetherPylonBlockEntity>> PYLON_ENTITY = ModMachines.BLOCK_ENTITIES.register("tether_pylon",
            () -> BlockEntityType.Builder.of(TetherPylonBlockEntity::new, TETHER_PYLON.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<VoidRepositoryBlockEntity>> REPOSITORY_ENTITY = ModMachines.BLOCK_ENTITIES.register("void_repository",
            () -> BlockEntityType.Builder.of(VoidRepositoryBlockEntity::new, VOID_REPOSITORY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.automatavalkyries.chrono.TacticalArmoryBlockEntity>> ARMORY_ENTITY = ModMachines.BLOCK_ENTITIES.register("tactical_armory",
            () -> BlockEntityType.Builder.of(com.automatavalkyries.chrono.TacticalArmoryBlockEntity::new, TACTICAL_ARMORY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.automatavalkyries.chrono.ChargingDockBlockEntity>> DOCK_ENTITY = ModMachines.BLOCK_ENTITIES.register("charging_dock",
            () -> BlockEntityType.Builder.of(com.automatavalkyries.chrono.ChargingDockBlockEntity::new, CHARGING_DOCK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.automatavalkyries.chrono.ChronoAcceleratorBlockEntity>> ACCELERATOR_ENTITY = ModMachines.BLOCK_ENTITIES.register("chrono_accelerator",
            () -> BlockEntityType.Builder.of(com.automatavalkyries.chrono.ChronoAcceleratorBlockEntity::new, CHRONO_ACCELERATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.automatavalkyries.chrono.ChronoRewinderBlockEntity>> REWINDER_ENTITY = ModMachines.BLOCK_ENTITIES.register("chrono_rewinder",
            () -> BlockEntityType.Builder.of(com.automatavalkyries.chrono.ChronoRewinderBlockEntity::new, CHRONO_REWINDER.get()).build(null));

    public static final RegistryObject<MenuType<ChronoMenu>> MENU = ModMachines.MENUS.register("chrono_machine", () -> IForgeMenuType.create(ChronoMenu::new));

    // ---- ティアフレーム・道具 ----
    public static final RegistryObject<Item> FRAME_HIGH_END = frame("frame_high_end", ChronoTier.HIGH_END, Rarity.UNCOMMON);
    public static final RegistryObject<Item> FRAME_FUTURE = frame("frame_future", ChronoTier.FUTURE, Rarity.RARE);
    public static final RegistryObject<Item> FRAME_BEYOND = frame("frame_beyond", ChronoTier.BEYOND, Rarity.EPIC);
    public static final RegistryObject<Item> FRAME_NEVERLAST = frame("frame_neverlast", ChronoTier.NEVERLAST, Rarity.EPIC);
    // ---- アップデートキット（その場での昇格。そのティアのアダプターで作る） ----
    public static final RegistryObject<Item> UPGRADE_KIT_FUTURE = kit("upgrade_kit_future", ChronoTier.FUTURE, Rarity.RARE);
    public static final RegistryObject<Item> UPGRADE_KIT_BEYOND = kit("upgrade_kit_beyond", ChronoTier.BEYOND, Rarity.EPIC);
    public static final RegistryObject<Item> UPGRADE_KIT_NEVERLAST = kit("upgrade_kit_neverlast", ChronoTier.NEVERLAST, Rarity.EPIC);

    public static final RegistryObject<Item> TOOL_CHRONO_TUNER = ModItems.ITEMS.register("tool_chrono_tuner", () ->
            new ChronoTunerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> BLUEPRINT_CARD = ModItems.ITEMS.register("blueprint_card", () ->
            new BlueprintCardItem(new Item.Properties().stacksTo(16)));

    private ModChrono() {}

    public static void init() {}

    private static RegistryObject<Item> kit(String name, ChronoTier tier, Rarity rarity) {
        return ModItems.ITEMS.register(name, () -> new com.automatavalkyries.item.UpgradeKitItem(new Item.Properties().rarity(rarity), tier));
    }

    private static RegistryObject<Item> frame(String name, ChronoTier tier, Rarity rarity) {
        return ModItems.ITEMS.register(name, () -> new TierFrameItem(new Item.Properties().rarity(rarity), tier));
    }

    private static RegistryObject<Block> machine(ChronoMachineType type,
                                                 Supplier<BiFunction<BlockPos, BlockState, ? extends ChronoMachineBlockEntity>> factory) {
        RegistryObject<Block> block = ModBlocks.BLOCKS.register(type.id, () -> new ChronoMachineBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
                .lightLevel(state -> state.getValue(ChronoMachineBlock.LIT) ? 7 + state.getValue(ChronoMachineBlock.TIER) * 2
                        : (state.getValue(ChronoMachineBlock.TIER) >= 3 ? 5 : 2)), type, factory.get()) {
            @Override
            public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
                // T4 のヴォイド・リポジトリは因果固定（爆発で壊れない）
                if (type == ChronoMachineType.VOID_REPOSITORY && state.getValue(TIER) == 4) return 3_600_000.0F;
                return super.getExplosionResistance(state, level, pos, explosion);
            }
        });
        ModItems.ITEMS.register(type.id, () -> new ChronoMachineItem((ChronoMachineBlock) block.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
        MACHINES.put(type, block);
        return block;
    }
}
