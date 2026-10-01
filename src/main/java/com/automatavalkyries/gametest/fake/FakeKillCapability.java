package com.automatavalkyries.gametest.fake;

import com.automatavalkyries.AutomataValkyries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** GameTest 用: 架空の MOD が {@link FakeStatefulMob} に付ける Capability（死亡の印・守り・中身のデータ） */
@Mod.EventBusSubscriber(modid = AutomataValkyries.MODID)
public final class FakeKillCapability implements ICapabilityProvider {

    public boolean soulMarked = true;
    public boolean shielded = true;
    public final Data data = new Data();

    /** 提供元が持つ中身のデータ */
    public static final class Data {
        public int doomCounter = 3;
        public int livesLeft = 2;
    }

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof FakeStatefulMob) {
            event.addCapability(ResourceLocation.fromNamespaceAndPath(AutomataValkyries.MODID, "test_fake_kill_state"), new FakeKillCapability());
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return LazyOptional.empty();
    }
}
