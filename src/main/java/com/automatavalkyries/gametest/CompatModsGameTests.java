package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.compat.curios.CuriosCompat;
import com.automatavalkyries.item.AkashicCubesItem;
import com.automatavalkyries.item.PocketAutoUse;
import com.automatavalkyries.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 情報表示・装備枠の連携（2026-10-30）。Curios が入っている時だけ確かめる（-PwithCompatMods / -PwithTechMods。無ければ素通り） */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class CompatModsGameTests {

    /** Curios: ベルトの枠のポケット・お守りの枠の観測の立方体・背中の枠の光翼の飛翔機も、持ち物・手にある時と同じように見つかる */
    @GameTest(template = "empty")
    public static void curiosSlotsAreSeen(GameTestHelper helper) {
        if (!CuriosCompat.LOADED) {
            helper.succeed();
            return;
        }
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "curios_test"));
        ItemStack pocket = new ItemStack(ModItems.ITEM_POCKET_T2.get()), cubes = new ItemStack(ModItems.AKASHIC_CUBES.get());
        helper.assertTrue(Equip.put(player, "belt", pocket) && Equip.put(player, "charm", cubes), "could not equip into the belt / charm slots");
        helper.assertTrue(PocketAutoUse.pockets(player).stream().anyMatch(s -> s == pocket), "the pocket in the belt slot was not found");
        helper.assertTrue(AkashicCubesItem.carried(player) == cubes, "the cubes in the charm slot were not found");
        ItemStack wing = ((com.automatavalkyries.item.FlightWingItem) ModItems.TOOL_FLIGHT_WING.get()).charged();
        helper.assertTrue(Equip.put(player, "back", wing), "could not equip the flyer into the back slot");
        helper.assertTrue(com.automatavalkyries.item.FlightWingItem.held(player) == wing, "the flyer in the back slot does not give flight");
        helper.succeed();
    }

    /** Curios のクラスに触るのはここだけ（無い時は読み込まれない） */
    private static final class Equip {
        static boolean put(FakePlayer player, String slot, ItemStack stack) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .resolve().flatMap(inv -> inv.getStacksHandler(slot))
                    .map(handler -> {
                        if (handler.getSlots() < 1) return false;
                        handler.getStacks().setStackInSlot(0, stack);
                        return true;
                    }).orElse(false);
        }
    }

    /**
     * AE2: ストレージバスでヴォイド・リポジトリを ME につなぐと、中身が ME から見え、ME から取り出せる（2026-10-30 ユーザー指定「AE2 / Refined Storage」「つながるか確かめる」）。
     * AE2 が無ければ素通り（-PwithTechMods で動く）
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ae2StorageBusSeesRepository(GameTestHelper helper) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) {
            helper.succeed();
            return;
        }
        net.minecraft.core.BlockPos repoPos = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 1));
        helper.setBlock(new net.minecraft.core.BlockPos(3, 2, 1), com.automatavalkyries.registry.ModChrono.MACHINES.get(com.automatavalkyries.chrono.ChronoMachineType.VOID_REPOSITORY).get());
        var repo = (com.automatavalkyries.chrono.VoidRepositoryBlockEntity) helper.getLevel().getBlockEntity(repoPos);
        repo.insert(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 64), false);
        repo.insert(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 36), false);
        Ae2.build(helper);
        helper.runAfterDelay(60, () -> {
            long seen = Ae2.count(helper);
            helper.assertTrue(seen == 100, "ME sees " + seen + " diamonds in the repository (expected 100)");
            long taken = Ae2.extract(helper, 30);
            helper.assertTrue(taken == 30 && repo.entries().get(0).count == 70, "ME took " + taken + ", repository left " + repo.entries().get(0).count);
            helper.succeed();
        });
    }

    /** AE2 のクラスに触るのはここだけ（無い時は読み込まれない）。電池 (1,2,1) ─ ケーブル (2,2,1) のストレージバスが東のリポジトリ (3,2,1) を向く */
    private static final class Ae2 {
        static void build(GameTestHelper helper) {
            helper.setBlock(new net.minecraft.core.BlockPos(1, 2, 1), net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.parse("ae2:creative_energy_cell")));
            var level = helper.getLevel();
            var cable = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 1));
            appeng.api.parts.PartHelper.setPart(level, cable, null, null, part("ae2:fluix_glass_cable"));
            appeng.api.parts.PartHelper.setPart(level, cable, net.minecraft.core.Direction.EAST, null, part("ae2:storage_bus"));
        }

        @SuppressWarnings("unchecked")
        static appeng.api.parts.IPartItem<appeng.api.parts.IPart> part(String id) {
            return (appeng.api.parts.IPartItem<appeng.api.parts.IPart>) net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(net.minecraft.resources.ResourceLocation.parse(id));
        }

        static appeng.api.storage.MEStorage storage(GameTestHelper helper) {
            var host = (appeng.api.networking.IInWorldGridNodeHost) helper.getLevel().getBlockEntity(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 1)));
            var node = host.getGridNode(null);
            return node.getGrid().getStorageService().getInventory();
        }

        static long count(GameTestHelper helper) {
            return storage(helper).getAvailableStacks().get(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND));
        }

        static long extract(GameTestHelper helper, long amount) {
            return storage(helper).extract(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND), amount, appeng.api.config.Actionable.MODULATE,
                    appeng.api.networking.security.IActionSource.empty());
        }
    }
}
