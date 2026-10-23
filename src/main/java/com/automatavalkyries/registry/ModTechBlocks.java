package com.automatavalkyries.registry;

import com.automatavalkyries.block.ChronoGateBlock;
import com.automatavalkyries.block.RulerPortalBlock;
import com.automatavalkyries.block.ZeroPortalBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 近未来的な建材と、侵食基地の転送ゲート（2026-10-15 ユーザー指定「近未来的な建材」「専用のスポナー」。質問で: 8 種・プレイヤーも作れる、壊せる転送ゲート）。
 * 建材はダンジョンの壁・床・天井・通路に使い、プレイヤーも作業台・石切台で作れる（recipes/tech_*.json）。
 * 照明パネル・空を映す照明パネルは明るさ 15、光る線の入った壁は 10、紫に光るパネルは 8。網目の床と強化ガラスは向こうが透ける
 */
public final class ModTechBlocks {

    private ModTechBlocks() {}

    /** クラスを読み込んで登録を足す（AutomataValkyries から呼ぶ） */
    public static void init() {
    }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(4.0F, 9.0F).sound(SoundType.METAL);
    }

    public static final RegistryObject<Block> TECH_PANEL_WHITE = register("tech_panel_white", () -> new Block(metal(MapColor.SNOW)));
    public static final RegistryObject<Block> TECH_PANEL_DARK = register("tech_panel_dark", () -> new Block(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> TECH_GRATING = register("tech_grating", () -> new Block(metal(MapColor.COLOR_GRAY)
            .noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
    public static final RegistryObject<Block> TECH_HAZARD = register("tech_hazard", () -> new Block(metal(MapColor.COLOR_YELLOW)));
    public static final RegistryObject<Block> TECH_GLASS = register("tech_glass", () -> new GlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
            .requiresCorrectToolForDrops().strength(3.0F, 1200.0F).sound(SoundType.GLASS).noOcclusion()
            .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final RegistryObject<Block> TECH_LIGHT_LINE = register("tech_light_line", () -> new Block(metal(MapColor.COLOR_CYAN).lightLevel(s -> 10)));
    public static final RegistryObject<Block> TECH_LIGHT_PANEL = register("tech_light_panel", () -> new Block(metal(MapColor.SNOW).lightLevel(s -> 15)));
    public static final RegistryObject<Block> TECH_PHASE_PANEL = register("tech_phase_panel", () -> new Block(metal(MapColor.COLOR_PURPLE).lightLevel(s -> 8)));
    public static final RegistryObject<Block> TECH_SKY_PANEL = register("tech_sky_panel", () -> new Block(metal(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 15)));

    /** クロノ転送ゲート（侵食基地の専用のスポナー。作れない。壊すと素材を落とす） */
    public static final RegistryObject<Block> CHRONO_GATE = register("chrono_gate", () -> new ChronoGateBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(25.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
            .lightLevel(s -> 10).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<BlockEntityType<ChronoGateBlock.Gate>> CHRONO_GATE_ENTITY = ModMachines.BLOCK_ENTITIES.register("chrono_gate", () ->
            BlockEntityType.Builder.of(ChronoGateBlock.Gate::new, CHRONO_GATE.get()).build(null));

    /** 空中庭園要塞の奥の眠れる時の門の膜（2026-10-19。ネザーポータルのような通り抜けられる膜。タイムルーラーへの門。今は眠っている。壊せない） */
    public static final RegistryObject<Block> RULER_PORTAL = register("ruler_portal", () -> new RulerPortalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE).strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST).noLootTable().noCollission().noOcclusion()
            .lightLevel(s -> s.getValue(RulerPortalBlock.AWAKE) ? 15 : 11).pushReaction(PushReaction.BLOCK)));
    /** 時の門の枠（白い大理石に金の象嵌。壊せない） */
    public static final RegistryObject<Block> RULER_GATE_FRAME = register("ruler_gate_frame", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).sound(SoundType.STONE).noLootTable().lightLevel(s -> 4).pushReaction(PushReaction.BLOCK)));
    /** 時の門の要石（金の時計の文字盤。光る。壊せない） */
    public static final RegistryObject<Block> RULER_GATE_KEYSTONE = register("ruler_gate_keystone", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().lightLevel(s -> 13).pushReaction(PushReaction.BLOCK)));

    /** 零の墓標への紅の門の膜（2026-10-20 ユーザー指定「裏ボスをつくりますか」。時の狭間の奥。タイムルーラーを倒すと目覚める。壊せない） */
    public static final RegistryObject<Block> ZERO_PORTAL = register("zero_portal", () -> new ZeroPortalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.CRIMSON_NYLIUM).strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST).noLootTable().noCollission().noOcclusion()
            .lightLevel(s -> s.getValue(ZeroPortalBlock.AWAKE) ? 15 : 9).pushReaction(PushReaction.BLOCK)));

    private static RegistryObject<Block> register(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = ModBlocks.BLOCKS.register(name, factory);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
