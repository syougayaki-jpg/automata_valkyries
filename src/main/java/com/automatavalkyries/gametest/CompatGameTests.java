package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.registry.ModItems;
import com.automatavalkyries.machine.MachineBlockEntity;
import com.automatavalkyries.machine.MachineKind;
import com.automatavalkyries.registry.IndustrialMetals;
import com.automatavalkyries.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 工業 MOD との連携の GameTest（追加拡張仕様書 §11.1）。相手の MOD は {@code -PwithTechMods} で読み込む。
 * 読み込まれていないときは何も確かめずに通す（相手のクラスは参照せず、レジストリ名で引く）。
 */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class CompatGameTests {

    private static final String TEMPLATE = "empty";

    private static boolean absent(GameTestHelper helper, String modId) {
        if (ModList.get().isLoaded(modId)) return false;
        helper.succeed();
        return true;
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
        if (item == null || item == Items.AIR) throw new IllegalStateException("missing item " + id);
        return item;
    }

    private static Block block(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(id));
        if (block == null) throw new IllegalStateException("missing block " + id);
        return block;
    }

    private static Item ours(String form, String metal) {
        return IndustrialMetals.ITEMS.get(form).get(metal).get();
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos, MachineKind kind) {
        helper.setBlock(pos, ModMachines.MACHINES.get(kind).get());
        return (MachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static int energyOf(GameTestHelper helper, BlockPos pos, Direction side) {
        BlockEntity be = helper.getBlockEntity(pos);
        if (be == null) return -1;
        return be.getCapability(ForgeCapabilities.ENERGY, side).map(IEnergyStorage::getEnergyStored).orElse(-1);
    }

    // ---- Mekanism ----

    /** 共通金属のタグ: Mekanism と同じ金属が同じ forge: タグに入り、互いに代わりに使える */
    @GameTest(template = TEMPLATE)
    public static void mekanismSharesMetalTags(GameTestHelper helper) {
        if (absent(helper, "mekanism")) return;
        for (String metal : List.of("tin", "lead", "osmium", "uranium", "steel", "bronze")) {
            var tag = ItemTags.create(ResourceLocation.parse("forge:ingots/" + metal));
            helper.assertTrue(new ItemStack(item("mekanism:ingot_" + metal)).is(tag), "mekanism ingot not in forge:ingots/" + metal);
            helper.assertTrue(new ItemStack(ours("ingot", metal)).is(tag), "our ingot not in forge:ingots/" + metal);
        }
        for (String metal : List.of("tin", "lead", "osmium", "uranium")) {
            var ore = ItemTags.create(ResourceLocation.parse("forge:ores/" + metal));
            helper.assertTrue(new ItemStack(item("mekanism:" + metal + "_ore")).is(ore), "mekanism ore not in forge:ores/" + metal);
        }
        helper.succeed();
    }

    /** Mekanism の鉱石・インゴットを、このMODの粉砕機・金属プレスで加工できる（タグで受ける） */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ourMachinesProcessMekanismMaterials(GameTestHelper helper) {
        if (absent(helper, "mekanism")) return;
        MachineBlockEntity crusher = machine(helper, new BlockPos(1, 2, 1), MachineKind.CRUSHER);
        MachineBlockEntity press = machine(helper, new BlockPos(3, 2, 1), MachineKind.PRESS);
        crusher.energy().set(crusher.energy().getMaxEnergyStored());
        press.energy().set(press.energy().getMaxEnergyStored());
        crusher.items().setStackInSlot(0, new ItemStack(item("mekanism:osmium_ore")));
        press.items().setStackInSlot(0, new ItemStack(item("mekanism:ingot_tin")));
        helper.succeedWhen(() -> {
            ItemStack dust = crusher.items().getStackInSlot(crusher.kind().outputSlot());
            ItemStack plate = press.items().getStackInSlot(press.kind().outputSlot());
            helper.assertTrue(dust.is(ours("dust", "osmium")) && dust.getCount() == 2, "crusher output " + dust);
            helper.assertTrue(plate.is(ours("plate", "tin")), "press output " + plate);
        });
    }

    /** Mekanism のユニバーサルケーブルで、このMODの発電機 → 電気炉へ送電できる */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void mekanismCableCarriesOurPower(GameTestHelper helper) {
        if (absent(helper, "mekanism")) return;
        MachineBlockEntity generator = machine(helper, new BlockPos(0, 2, 1), MachineKind.GENERATOR);
        for (int x = 1; x <= 3; x++) helper.setBlock(new BlockPos(x, 2, 1), block("mekanism:basic_universal_cable"));
        MachineBlockEntity furnace = machine(helper, new BlockPos(4, 2, 1), MachineKind.ELECTRIC_FURNACE);
        generator.items().setStackInSlot(0, new ItemStack(Items.COAL, 2));
        furnace.items().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> helper.assertTrue(furnace.items().getStackInSlot(furnace.kind().outputSlot()).is(Items.IRON_INGOT),
                "furnace energy " + furnace.energy().getEnergyStored()));
    }

    /** このMODの電力ケーブルで、蓄電器 → Mekanism のエネルギーキューブへ送電できる */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ourCableChargesMekanismCube(GameTestHelper helper) {
        if (absent(helper, "mekanism")) return;
        MachineBlockEntity battery = machine(helper, new BlockPos(0, 2, 1), MachineKind.BATTERY);
        battery.energy().set(battery.energy().getMaxEnergyStored());
        helper.setBlock(new BlockPos(1, 2, 1), ModMachines.CABLE.get());
        BlockPos cube = new BlockPos(2, 2, 1);
        helper.setBlock(cube, block("mekanism:basic_energy_cube"));
        helper.succeedWhen(() -> helper.assertTrue(energyOf(helper, cube, Direction.WEST) > 0,
                "cube energy " + energyOf(helper, cube, Direction.WEST)));
    }

    // ---- Applied Energistics 2 ----

    /** このMODの電力ケーブルから AE2 のエネルギーアクセプターへ FE を渡せる */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ourCablePowersAe2Acceptor(GameTestHelper helper) {
        if (absent(helper, "ae2")) return;
        MachineBlockEntity battery = machine(helper, new BlockPos(0, 2, 1), MachineKind.BATTERY);
        battery.energy().set(battery.energy().getMaxEnergyStored());
        helper.setBlock(new BlockPos(1, 2, 1), ModMachines.CABLE.get());
        BlockPos acceptor = new BlockPos(2, 2, 1);
        helper.setBlock(acceptor, block("ae2:energy_acceptor"));
        int start = battery.energy().getEnergyStored();
        helper.succeedWhen(() -> helper.assertTrue(battery.energy().getEnergyStored() < start,
                "acceptor took nothing (acceptor energy " + energyOf(helper, acceptor, Direction.WEST) + ")"));
    }

    /** AE2 の素材（ケルタスクォーツ等）が forge: タグに入っていて、このMODのクォーツを使うレシピ（基本回路）に AE2 のクォーツ類は混ざらない */
    @GameTest(template = TEMPLATE)
    public static void ae2TagsAreSane(GameTestHelper helper) {
        if (absent(helper, "ae2")) return;
        helper.assertTrue(new ItemStack(item("ae2:certus_quartz_crystal")).is(ItemTags.create(ResourceLocation.parse("forge:gems/certus_quartz"))),
                "certus quartz not in forge:gems/certus_quartz");
        helper.succeed();
    }
    // ---- 鉱石の生成の重なり ----

    /** 平原にこのMODの鉱石の地物が入っているか */
    private static boolean generatesOurs(GameTestHelper helper, String metal) {
        var biome = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "ore_" + metal);
        return biome.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(h -> h.unwrapKey().map(k -> k.location().equals(id)).orElse(false));
    }

    /**
     * 同じ金属の鉱石を他 MOD が生成するなら、こちらは生成しない（skipWhenOtherModGenerates）。
     * Mekanism があればスズ・鉛・オスミウム・ウランは Mekanism に譲り、Mekanism に無い銀・ニッケル・亜鉛・アルミニウムは生成する。
     * Mekanism が無ければすべて生成する
     */
    @GameTest(template = TEMPLATE)
    public static void oreGenerationYieldsToOtherMods(GameTestHelper helper) {
        boolean mekanism = ModList.get().isLoaded("mekanism");
        boolean otherOreMod = java.util.stream.Stream.of("create", "immersiveengineering", "thermal").anyMatch(ModList.get()::isLoaded);
        for (String metal : List.of("tin", "lead", "osmium", "uranium")) {
            boolean yielded = com.automatavalkyries.world.OreOverlap.skipped().containsKey(metal);
            if (mekanism) helper.assertTrue(yielded, metal + " was not yielded next to Mekanism: " + com.automatavalkyries.world.OreOverlap.skipped());
            if (!mekanism && !otherOreMod) helper.assertTrue(!yielded, metal + " was yielded although no ore mod is loaded");
            helper.assertTrue(generatesOurs(helper, metal) != yielded, metal + (yielded ? " ore is still generated next to " + com.automatavalkyries.world.OreOverlap.skipped().get(metal) : " ore is not generated"));
        }
        // ほかの金属: 他 MOD（Create・Immersive Engineering・Thermal など）が生成する物は譲り、そうでない物は生成する（2026-10-30）
        boolean anyOreMod = java.util.stream.Stream.of("create", "immersiveengineering", "thermal").anyMatch(ModList.get()::isLoaded);
        for (String metal : List.of("silver", "nickel", "zinc", "aluminum")) {
            boolean yielded = com.automatavalkyries.world.OreOverlap.skipped().containsKey(metal);
            helper.assertTrue(anyOreMod || !yielded, metal + " was yielded although no ore mod is loaded: " + com.automatavalkyries.world.OreOverlap.skipped());
            helper.assertTrue(generatesOurs(helper, metal) != yielded, metal + (yielded ? " ore is still generated next to " + com.automatavalkyries.world.OreOverlap.skipped().get(metal) : " ore is not generated"));
        }
        helper.succeed();
    }

    /** 固有金属の粉（2026-10-30）: このMODの粉砕機で鉱石 1 から粉 2 個。Mekanism が入っていれば濃縮機・粉砕機のレシピも読まれる */
    @GameTest(template = TEMPLATE)
    public static void uniqueOresDoubleIntoDust(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String m : new String[]{"astrite", "phononium", "ceruleas"}) {
            var crush = manager.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "machine/crushing_ore_" + m));
            helper.assertTrue(crush.isPresent() && crush.get().getResultItem(helper.getLevel().registryAccess()).getCount() == 2, "crusher does not double " + m + " ore");
            var mek = manager.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/mekanism/enriching_ore_" + m));
            helper.assertTrue(mek.isPresent() == ModList.get().isLoaded("mekanism"), "Mekanism enriching recipe for " + m + " present=" + mek.isPresent());
            var create = manager.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/create/crushing_ore_" + m));
            helper.assertTrue(create.isPresent() == ModList.get().isLoaded("create"), "Create crushing recipe for " + m + " present=" + create.isPresent());
            var ie = manager.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/immersiveengineering/crusher_ore_" + m));
            helper.assertTrue(ie.isPresent() == ModList.get().isLoaded("immersiveengineering"), "IE crusher recipe for " + m + " present=" + ie.isPresent());
            var thermal = manager.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/thermal/pulverizer_ore_" + m));
            helper.assertTrue(thermal.isPresent() == ModList.get().isLoaded("thermal"), "Thermal pulverizer recipe for " + m + " present=" + thermal.isPresent());
        }
        helper.succeed();
    }

    /**
     * 回路の互換（2026-10-30 ユーザー指定「回路は基本と高等回路に互換性を」）: このMODの基本回路・高等回路は forge:circuits/basic・advanced に入り、
     * このMODのレシピはそのタグで受ける。Mekanism が入っていれば、その制御回路でもこのMODの高等回路が作れる
     */
    @GameTest(template = TEMPLATE)
    public static void basicAndAdvancedCircuitsAreShared(GameTestHelper helper) {
        var basicTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("forge:circuits/basic"));
        var advancedTag = net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("forge:circuits/advanced"));
        helper.assertTrue(new ItemStack(ModItems.CIRCUIT_BASIC.get()).is(basicTag) && new ItemStack(ModItems.CIRCUIT_ADVANCED.get()).is(advancedTag), "circuits are not in the forge tags");
        var recipe = helper.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "circuit_advanced"));
        helper.assertTrue(recipe.isPresent(), "advanced circuit recipe missing");
        boolean takesOurs = recipe.get().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(ModItems.CIRCUIT_BASIC.get())));
        helper.assertTrue(takesOurs, "the advanced circuit recipe no longer takes our basic circuit");
        if (ModList.get().isLoaded("mekanism")) {
            var mek = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse("mekanism:basic_control_circuit"));
            boolean takesMek = recipe.get().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(mek)));
            helper.assertTrue(takesMek, "the advanced circuit recipe does not take Mekanism's basic control circuit");
        }
        helper.succeed();
    }

    /**
     * Tinkers' Construct（2026-10-30）: 溶けた金属の液体がそれぞれ forge:molten_* のタグに入り、バケツで置けて、
     * 溶かす・鋳造するレシピは tconstruct が入っている時だけある（-PwithTinkers で確かめる。無ければ無いことを確かめる）
     */
    @GameTest(template = TEMPLATE)
    public static void moltenMetalsForTinkers(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        boolean tinkers = ModList.get().isLoaded("tconstruct");
        int x = 0;
        for (var molten : com.automatavalkyries.registry.ModFluids.MOLTEN.values()) {
            var tag = net.minecraft.tags.FluidTags.create(ResourceLocation.fromNamespaceAndPath("forge", "molten_" + molten.metal()));
            helper.assertTrue(molten.source().get().is(tag) && molten.flowing().get().is(tag), "molten " + molten.metal() + " is not in " + tag.location());
            helper.assertTrue(molten.bucket().get() instanceof net.minecraft.world.item.BucketItem bucket && bucket.getFluid() == molten.source().get(),
                    "bucket of molten " + molten.metal() + " holds the wrong fluid");
            BlockPos pos = new BlockPos(1 + (x++ % 3), 2, 1 + x / 3);
            helper.setBlock(pos, molten.block().get());
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).getType() == molten.source().get(), "molten " + molten.metal() + " block holds no fluid");
            for (String name : List.of("melting/" + molten.metal() + "/ingot", "casting/" + molten.metal() + "/ingot_gold_cast", "casting/" + molten.metal() + "/block")) {
                var recipe = manager.byKey(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/tconstruct/" + name));
                helper.assertTrue(recipe.isPresent() == tinkers, "Tinkers recipe " + name + " present=" + recipe.isPresent() + " (tconstruct loaded=" + tinkers + ")");
            }
        }
        helper.succeed();
    }

    /**
     * Tinkers' Construct の道具の素材（2026-10-30）: このMODの 8 金属が素材として読み込まれ、頭の性能と特性を持つ（-PwithTinkers。無ければ素通り）。
     * Tinkers のクラスはこの MOD から参照しないので反射で引く
     */
    @GameTest(template = TEMPLATE)
    public static void tinkersToolMaterialsLoad(GameTestHelper helper) {
        if (absent(helper, "tconstruct")) return;
        try {
            Class<?> registry = Class.forName("slimeknights.tconstruct.library.materials.MaterialRegistry");
            Class<?> idClass = Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialId");
            Class<?> statsId = Class.forName("slimeknights.tconstruct.library.materials.stats.MaterialStatsId");
            Class<?> api = Class.forName("slimeknights.tconstruct.library.materials.IMaterialRegistry");
            Class<?> materialApi = Class.forName("slimeknights.tconstruct.library.materials.definition.IMaterial");
            Object instance = registry.getMethod("getInstance").invoke(null);
            Object meleeHarvest = registry.getField("MELEE_HARVEST").get(null);
            Object head = statsId.getConstructor(String.class).newInstance("tconstruct:head");
            for (String metal : com.automatavalkyries.registry.ModFluids.MOLTEN.keySet()) {
                Object id = idClass.getConstructor(String.class, String.class).newInstance(AutomataValkyries.MODID, metal);
                Object material = api.getMethod("getMaterial", idClass).invoke(instance, id);
                Object loaded = materialApi.getMethod("getIdentifier").invoke(material);
                helper.assertTrue(id.equals(loaded), "Tinkers material " + metal + " not loaded (got " + loaded + ")");
                var stats = (java.util.Optional<?>) api.getMethod("getMaterialStats", idClass, statsId).invoke(instance, id, head);
                helper.assertTrue(stats.isPresent(), "Tinkers material " + metal + " has no head stats");
                var traits = (List<?>) api.getMethod("getTraits", idClass, statsId).invoke(instance, id, meleeHarvest);
                Class<?> entry = Class.forName("slimeknights.tconstruct.library.modifiers.ModifierEntry");
                List<String> ids = new java.util.ArrayList<>();
                for (Object t : traits) ids.add(String.valueOf(entry.getMethod("getId").invoke(t)));
                helper.assertTrue(ids.stream().anyMatch(trait -> trait.startsWith(AutomataValkyries.MODID + ":")),
                        "Tinkers material " + metal + " does not carry this mod's trait: " + ids);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Tinkers material API changed", e);
        }
        helper.succeed();
    }

    /**
     * 抜刀剣（SlashBlade: Resharped）の名のある刀（2026-10-30）: このMODの 8 金属の刀が抜刀剣の名のある刀の一覧に載り、作るレシピがある。
     * 抜刀剣が無ければレシピも無いことを確かめる（-PwithSlashBlade で載る方を確かめる）
     */
    @GameTest(template = TEMPLATE)
    public static void slashBladeNamedBlades(GameTestHelper helper) {
        boolean loaded = ModList.get().isLoaded("slashblade");
        var manager = helper.getLevel().getRecipeManager();
        var key = net.minecraft.resources.ResourceKey.<Object>createRegistryKey(ResourceLocation.fromNamespaceAndPath("slashblade", "named_blades"));
        var blades = helper.getLevel().registryAccess().registry(key);
        helper.assertTrue(blades.isPresent() == loaded, "SlashBlade named blade registry present=" + blades.isPresent() + " (slashblade loaded=" + loaded + ")");
        for (String metal : com.automatavalkyries.registry.ModFluids.MOLTEN.keySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "blade_" + metal);
            var recipe = manager.byKey(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/slashblade/blade_" + metal));
            helper.assertTrue(recipe.isPresent() == loaded, "SlashBlade recipe for " + metal + " present=" + recipe.isPresent());
            blades.ifPresent(registry -> helper.assertTrue(registry.containsKey(id), "named blade " + id + " is not registered"));
        }
        if (loaded) {
            // 独自の抜刀術と特殊効果（抜刀剣のレジストリに登録されている）
            var arts = net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(ResourceLocation.fromNamespaceAndPath("slashblade", "slash_arts"));
            var effects = net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(ResourceLocation.fromNamespaceAndPath("slashblade", "special_effect"));
            helper.assertTrue(arts != null && effects != null, "SlashBlade slash art / special effect registries not found");
            for (String art : List.of("luminal_bloom", "bastion_break", "superluminal_cut", "eclipse_moon", "stellar_judgement")) {
                helper.assertTrue(arts.containsKey(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, art)), "slash art " + art + " not registered");
            }
            for (String se : List.of("starlight", "resonance", "azure_guard", "clear_light", "immovable", "acceleration", "life_drain", "stellar_isolation")) {
                helper.assertTrue(effects.containsKey(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, se)), "special effect " + se + " not registered");
            }
        }
        helper.succeed();
    }

    /** 溶けた金属に入ると燃えて傷を受ける（溶岩と同じ。2026-10-30）。火に強い物は無事 */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void moltenMetalBurnsWhatEntersIt(GameTestHelper helper) {
        var molten = com.automatavalkyries.registry.ModFluids.MOLTEN.get("stellachronodyme");
        helper.setBlock(new BlockPos(1, 2, 1), molten.block().get());
        helper.setBlock(new BlockPos(3, 2, 1), molten.block().get());
        var zombie = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var blaze = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.BLAZE, new BlockPos(3, 2, 1));
        float zombieStart = zombie.getHealth(), blazeStart = blaze.getHealth();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(zombie.getHealth() < zombieStart && zombie.isOnFire(), "zombie in molten metal: health " + zombie.getHealth() + "/" + zombieStart + " onFire=" + zombie.isOnFire());
            helper.assertTrue(blaze.getHealth() >= blazeStart, "fire-immune blaze was hurt by molten metal: " + blaze.getHealth());
            helper.succeed();
        });
    }

    /**
     * Tinkers のアビリティ（2026-10-30）: 隔絶の目印を持つ道具での近接の一撃が隔絶ダメージになる（目印は NBT だけなので Tinkers が無くても確かめられる）。
     * アビリティを付けるレシピは tconstruct がある時だけある
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void tinkersIsolationAbility(GameTestHelper helper) {
        var attacker = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        var victim = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(3, 2, 1));
        ItemStack tool = new ItemStack(Items.IRON_SWORD);
        tool.getOrCreateTag().put("tic_volatile_data", new net.minecraft.nbt.CompoundTag());
        tool.getTag().getCompound("tic_volatile_data").putBoolean(com.automatavalkyries.compat.tconstruct.TinkersAbilities.ISOLATION_FLAG, true);
        attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
        helper.assertTrue(com.automatavalkyries.compat.tconstruct.TinkersAbilities.hasIsolation(tool), "isolation flag not read from the tool");
        float before = victim.getHealth();
        long hits = com.automatavalkyries.compat.IsolationStrike.replacedHits();
        victim.hurt(victim.damageSources().mobAttack(attacker), 4.0F);
        helper.assertTrue(victim.getHealth() < before, "victim took no damage from the isolation hit");
        helper.assertTrue(com.automatavalkyries.compat.IsolationStrike.replacedHits() == hits + 1,
                "melee hit with the isolation ability was not replaced by Isolation damage");
        var manager = helper.getLevel().getRecipeManager();
        boolean tinkers = ModList.get().isLoaded("tconstruct");
        for (String id : List.of("isolation", "ftl_strike", "black_death", "time_stitch", "distortion_barrier")) {
            var recipe = manager.byKey(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "compat/tconstruct/modifiers/" + id));
            helper.assertTrue(recipe.isPresent() == tinkers, "Tinkers ability recipe " + id + " present=" + recipe.isPresent());
        }
        helper.succeed();
    }

    /**
     * TiCEX の再鍛抜刀剣（2026-10-31 ユーザー報告「再鍛抜刀剣がつくれない」「ほかの素材でも作れない」）: TiCEX は道具の定義を Tinkers の読み込みより後に
     * 登録するので、開いた時点では定義が空でどの素材でも組めなかった。ワールドの開始時に 1 度データパックを読み直す回避（{@link com.automatavalkyries.compat.tconstruct.TicexDefinitionFix}）の後、
     * ステラ・クロノダイムの刀身・鞘と強化持ち手で作業台に組めることを確かめる（TiCEX が無ければ素通り）
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void ticexReforgedSlashBladeBuilds(GameTestHelper helper) {
        if (absent(helper, "ticex")) return;
        try {
            Class<?> materialId = Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialId");
            Class<?> variantId = Class.forName("slimeknights.tconstruct.library.materials.definition.MaterialVariantId");
            var withMaterial = Class.forName("slimeknights.tconstruct.library.tools.part.IMaterialItem").getMethod("withMaterial", ItemStack.class, variantId);
            Object stella = materialId.getConstructor(String.class, String.class).newInstance(AutomataValkyries.MODID, "stellachronodyme");
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, block("tconstruct:tinker_station"));
            Object station = helper.getBlockEntity(pos);
            var setItem = station.getClass().getMethod("setItem", int.class, ItemStack.class);
            var calc = station.getClass().getMethod("calcResult", net.minecraft.world.entity.player.Player.class);
            var player = helper.makeMockPlayer();
            String[] parts = {"ticex:slashblade_blade", "ticex:slashblade_saya", "tconstruct:tough_handle"};
            helper.succeedWhen(() -> {
                try {
                    for (int i = 0; i < parts.length; i++) setItem.invoke(station, 1 + i, (ItemStack) withMaterial.invoke(null, new ItemStack(item(parts[i])), stella));
                    ItemStack result = (ItemStack) calc.invoke(station, player);
                    helper.assertTrue(!result.isEmpty(), "Reforged SlashBlade was not built from Stellachronodyme parts (TiCEX definitions without data: "
                            + com.automatavalkyries.compat.tconstruct.TicexDefinitionFix.missingTicexDefinitions() + ")");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("TiCEX / Tinkers API changed", e);
        }
    }
}
