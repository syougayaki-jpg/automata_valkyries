package com.automatavalkyries.gametest;

import com.automatavalkyries.AutomataValkyries;
import com.automatavalkyries.entity.ValkyrieEntity;
import com.automatavalkyries.machine.ValkyrieStorageBlockEntity;
import com.automatavalkyries.protect.valkyrie.ValkyrieProtect;
import com.automatavalkyries.registry.ModEntities;
import com.automatavalkyries.registry.ModMachines;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** 兵姫の収納テレポーター（2026-10-09）: 近くの自分の兵姫をしまうと世界から消え（勝手に戻されない）、出すと同じ兵姫が前に戻って保護に載る */
@GameTestHolder(AutomataValkyries.MODID)
@PrefixGameTestTemplate(false)
public class ValkyrieStorageGameTests {

    @GameTest(template = "empty", batch = "valkyrie_storage", timeoutTicks = 100)
    public static void storageTeleporterStoresAndReleasesValkyries(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModMachines.VALKYRIE_STORAGE.get());
        ValkyrieStorageBlockEntity storage = (ValkyrieStorageBlockEntity) helper.getBlockEntity(at);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "storage_test"));
        Vec3 p = helper.absoluteVec(new Vec3(1, 2, 1));
        player.moveTo(p.x, p.y, p.z);
        ValkyrieEntity v = helper.spawn(ModEntities.VALKYRIE.get(), 4, 2, 4);
        v.setOwnerUUID(player.getUUID());
        v.setCustomName(net.minecraft.network.chat.Component.literal("Stored One"));
        UUID id = v.getUUID();
        ValkyrieEntity stranger = helper.spawn(ModEntities.VALKYRIE.get(), 4, 2, 1); // 他人の兵姫はしまえない
        stranger.setOwnerUUID(UUID.randomUUID());
        helper.assertTrue(storage.inReach(player), "the player is not in reach of the teleporter");
        helper.assertTrue("not_found".equals(storage.store(player, stranger.getUUID())), "someone else's valkyrie was stored");
        helper.assertTrue("stored".equals(storage.store(player, id)), "the valkyrie was not stored");
        helper.assertTrue(v.isRemoved() && storage.storedCount() == 1, "the stored valkyrie is still in the world");
        helper.runAfterDelay(60, () -> { // 見張り（20 Tick ごと）に勝手に戻されない
            helper.assertTrue(ValkyrieProtect.findAnywhere(helper.getLevel().getServer(), id) == null, "the stored valkyrie was restored by the protection");
            helper.assertTrue("released".equals(storage.release(player, id)), "the valkyrie was not released");
            ValkyrieEntity back = ValkyrieProtect.findAnywhere(helper.getLevel().getServer(), id);
            helper.assertTrue(back != null && "Stored One".equals(back.getCustomName().getString()) && player.getUUID().equals(back.getOwnerUUID()),
                    "the released valkyrie is not the same one: " + back);
            helper.assertTrue(storage.storedCount() == 0, "the released valkyrie is still listed");
            back.releaseAndDiscard();
            stranger.releaseAndDiscard();
            helper.succeed();
        });
    }
}
