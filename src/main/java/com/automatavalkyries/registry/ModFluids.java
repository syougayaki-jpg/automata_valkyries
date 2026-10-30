package com.automatavalkyries.registry;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 溶けた金属（2026-10-30 ユーザー指定「他MODとの連携要素を増やす」→ Tinkers' Construct の溶鉱炉で溶かす・鋳造する）。
 * このMOD独自の金属（固有金属 3 種・上位合金 4 種・ステラクロノダイム）を液体にする。連携金属（スズなど）は Tinkers' Construct が自分の液体を持つので作らない。
 * 液体・置ける液体のブロック・バケツ。液体のタグ {@code forge:molten_<金属>}（Tinkers' Construct のレシピがこれで受ける）。
 * 見た目はバニラの水の絵（灰色）を金属の色に染めて光らせる
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, AutomataValkyries.MODID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, AutomataValkyries.MODID);

    /** 溶けた金属 1 種類 */
    public record Molten(String metal, int color, int temperature, RegistryObject<FluidType> type, RegistryObject<FlowingFluid> source,
                         RegistryObject<FlowingFluid> flowing, RegistryObject<LiquidBlock> block, RegistryObject<Item> bucket) {}

    /** 金属 → 溶けた金属（並びは表示の順） */
    public static final Map<String, Molten> MOLTEN = new LinkedHashMap<>();

    static {
        molten("astrite", 0x6E9BD8, 600);
        molten("phononium", 0xE0303A, 750);
        molten("ceruleas", 0x5FB0D8, 750);
        molten("luminal_sterling", 0xF4E7A0, 900);
        molten("chrono_fortress", 0x8A6CC8, 950);
        molten("tachyon_accel", 0x40E0FF, 1000);
        molten("eclipse_biometal", 0x7A3A8E, 950);
        molten("stellachronodyme", 0xFFC8EE, 1400);
    }

    private ModFluids() {}

    /** クラスを読み込んで登録を済ませる（メインクラスから ITEMS / BLOCKS の登録前に呼ぶ） */
    public static void init() {}

    /** temperature: Tinkers' Construct の溶かす温度（℃）。液体の温度はそれに 300 足したケルビン */
    private static void molten(String metal, int color, int temperature) {
        String name = "molten_" + metal;
        RegistryObject<FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type." + AutomataValkyries.MODID + "." + name)
                .temperature(temperature + 300).lightLevel(12).density(3000).viscosity(6000)
                .canSwim(false).canDrown(false).canPushEntity(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    private static final ResourceLocation STILL = ResourceLocation.parse("minecraft:block/water_still");
                    private static final ResourceLocation FLOW = ResourceLocation.parse("minecraft:block/water_flow");

                    @Override public ResourceLocation getStillTexture() { return STILL; }
                    @Override public ResourceLocation getFlowingTexture() { return FLOW; }
                    @Override public int getTintColor() { return 0xFF000000 | color; }
                });
            }
        });
        RegistryObject<FlowingFluid>[] source = new RegistryObject[1], flowing = new RegistryObject[1];
        RegistryObject<LiquidBlock>[] block = new RegistryObject[1];
        RegistryObject<Item>[] bucket = new RegistryObject[1];
        java.util.function.Supplier<ForgeFlowingFluid.Properties> props = () -> new ForgeFlowingFluid.Properties(type, source[0], flowing[0])
                .bucket(bucket[0]).block(block[0]).slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30);
        source[0] = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(props.get()));
        flowing[0] = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(props.get()));
        block[0] = ModBlocks.BLOCKS.register(name, () -> new LiquidBlock(source[0], BlockBehaviour.Properties.copy(Blocks.LAVA).lightLevel(s -> 12)));
        bucket[0] = ModItems.ITEMS.register(name + "_bucket", () -> new BucketItem(source[0], new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        MOLTEN.put(metal, new Molten(metal, color, temperature, type, source[0], flowing[0], block[0], bucket[0]));
    }
}
